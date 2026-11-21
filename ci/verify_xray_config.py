#!/usr/bin/env python3
"""
Validasi config Xray NusaTunnel dengan engine ASLI (xray run -test).

Menghasilkan config persis seperti XrayConfigBuilder.kt untuk semua
protokol & varian transport, lalu memvalidasi dengan Xray-core versi
yang sama dengan AndroidLibXrayLite yang dipakai aplikasi.

Pakai:  python3 ci/verify_xray_config.py [path-xray]

Exit code 0 = semua lolos, non-zero = ada config ditolak Xray-core.
"""
import json
import os
import subprocess
import sys
import tempfile
import base64
import secrets

XRAY = sys.argv[1] if len(sys.argv) > 1 else "xray"

SOCKS_PORT = 10808


def profile_base():
    return dict(encryption="", network="tcp", tls=False, sni="", alpn="",
                fingerprint="", flow="", wsPath="/", wsHost="",
                grpcServiceName="", allowInsecure=False)


def sniffing():
    return {"enabled": True, "destOverride": ["http", "tls", "quic"]}


def stream_settings(p):
    s = {"network": p["network"] or "tcp"}
    if p["network"] == "ws":
        s["wsSettings"] = {"path": p["wsPath"] or "/"}
        if p["wsHost"]:
            s["wsSettings"]["host"] = p["wsHost"]
    elif p["network"] == "grpc":
        s["grpcSettings"] = {"serviceName": p["grpcServiceName"], "multiMode": False}
    s["security"] = "tls" if p["tls"] else "none"
    if p["tls"]:
        tls = {"serverName": p["sni"] or p["host"], "allowInsecure": p["allowInsecure"]}
        if p["alpn"]:
            tls["alpn"] = [a.strip() for a in p["alpn"].split(",")]
        if p["fingerprint"]:
            tls["fingerprint"] = p["fingerprint"]
        s["tlsSettings"] = tls
    return s


def outbound(p):
    if p["proto"] == "vmess":
        settings = {"vnext": [{"address": p["host"], "port": p["port"], "users": [
            {"id": p["secret"], "security": p["encryption"] or "auto", "alterId": 0, "level": 8}]}]}
    elif p["proto"] == "vless":
        settings = {"vnext": [{"address": p["host"], "port": p["port"], "users": [
            {"id": p["secret"], "encryption": "none", "flow": p["flow"], "level": 8}]}]}
    elif p["proto"] == "trojan":
        # Xray >= v26: password di level server
        settings = {"servers": [{"address": p["host"], "port": p["port"],
                                 "password": p["secret"], "level": 8}]}
    else:  # shadowsocks
        settings = {"servers": [{"address": p["host"], "port": p["port"],
                                 "method": p["encryption"] or "aes-256-gcm",
                                 "password": p["secret"], "uot": True, "level": 8}]}
    return {
        "tag": "proxy",
        "protocol": p["proto"],
        "settings": settings,
        "streamSettings": stream_settings(p),
        "mux": {"enabled": False, "concurrency": -1},
    }


def build_config(p):
    """Replika persis XrayConfigBuilder.build()."""
    inbounds = [
        {"tag": "tun", "protocol": "tun",
         "settings": {"name": "nusatun0", "MTU": 1500, "userLevel": 8},
         "sniffing": sniffing()},
        {"tag": "socks", "port": SOCKS_PORT, "listen": "127.0.0.1", "protocol": "socks",
         "settings": {"auth": "noauth", "udp": True, "userLevel": 8},
         "sniffing": sniffing()},
    ]
    outbounds = [outbound(p),
                 {"tag": "direct", "protocol": "freedom"},
                 {"tag": "block", "protocol": "blackhole"}]
    return {
        "stats": {},
        "log": {"loglevel": "warning"},
        "policy": {
            "levels": {"8": {"handshake": 4, "connIdle": 300, "uplinkOnly": 1, "downlinkOnly": 1}},
            "system": {"statsOutboundUplink": True, "statsOutboundDownlink": True},
        },
        "inbounds": inbounds,
        "outbounds": outbounds,
        "dns": {"servers": ["1.1.1.1", "8.8.8.8", "localhost"]},
        "routing": {"domainStrategy": "IPIfNonMatch", "rules": [
            {"type": "field", "outboundTag": "proxy", "port": "0-65535"}]},
        "remarks": p["remarks"],
    }


CASES = [
    ("vmess_tcp_tls", dict(profile_base(), proto="vmess", remarks="vmess-tcp",
                           host="vm.example.com", port=443,
                           secret="b831381d-6324-4d53-ad4f-8cda48b30811",
                           encryption="auto", tls=True, sni="vm.example.com")),
    ("vmess_ws_tls_alpn_fp", dict(profile_base(), proto="vmess", remarks="vmess-ws",
                                  host="sg.example.com", port=443,
                                  secret="b831381d-6324-4d53-ad4f-8cda48b30811",
                                  encryption="auto", network="ws", tls=True,
                                  sni="cdn.example.com", wsPath="/nt",
                                  wsHost="cdn.example.com", alpn="h2,http/1.1",
                                  fingerprint="chrome")),
    ("vless_grpc_tls_flow", dict(profile_base(), proto="vless", remarks="vless-grpc",
                                 host="vl.example.com", port=443, secret="uuid-vl",
                                 network="grpc", grpcServiceName="nt-svc", tls=True,
                                 sni="vl.example.com", flow="xtls-rprx-vision")),
    ("trojan_tcp_tls_serverlevel", dict(profile_base(), proto="trojan", remarks="trojan",
                                        host="premiuk.vpnjantit.com", port=4443,
                                        secret="test-password-123", tls=True,
                                        sni="premiuk.vpnjantit.com")),
    ("ss_tcp_plain", dict(profile_base(), proto="shadowsocks", remarks="ss",
                          host="ss.example.com", port=8388, secret="pw",
                          encryption="aes-256-gcm")),
    ("ss_2022", dict(profile_base(), proto="shadowsocks", remarks="ss2022",
                     host="ss2.example.com", port=8389,
                     secret=base64.b64encode(secrets.token_bytes(16)).decode(),
                     encryption="2022-blake3-aes-128-gcm")),
]

# kunci XUDP valid & tidak valid (regression test bug v2.2.1)
VALID_KEY = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
INVALID_KEY = "2996cc49-2a3d-428c-8ce7-3d56e8112d2e"  # UUID era v2.2.1


def run_xray_test(cfg, env_extra=None):
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(cfg, f)
        path = f.name
    env = dict(os.environ)
    if env_extra:
        env.update(env_extra)
    try:
        r = subprocess.run([XRAY, "run", "-test", "-c", path],
                           capture_output=True, text=True, timeout=60, env=env)
        return r.returncode == 0, (r.stderr or "") + (r.stdout or "")
    finally:
        os.unlink(path)


def main():
    print(f"Engine : {XRAY}")
    v = subprocess.run([XRAY, "version"], capture_output=True, text=True)
    print(v.stdout.splitlines()[0] if v.stdout else "(versi tidak diketahui)")
    print()

    failures = []

    # --- 1. Semua kombinasi protokol/transport ---
    print("== 1. Config builder: semua protokol & varian ==")
    for name, p in CASES:
        ok, out = run_xray_test(build_config(p))
        status = "LOLOS" if ok else "GAGAL"
        print(f"  [{status}] {name}")
        if not ok:
            failures.append((name, out))

    # --- 2. Regression: bug lapangan v2.2.0 (outboundTag/port array) ---
    print("== 2. Regression bug lama HARUS ditolak ==")
    bad = build_config(CASES[3][1])
    bad["routing"]["rules"] = [{"type": "field", "outboundTag": ["proxy"],
                                "port": ["0-65535"], "balancerTag": ""}]
    ok, out = run_xray_test(bad)
    if ok:
        failures.append(("bug-lama-masih-diterima", "outboundTag array seharusnya ditolak"))
        print("  [GAGAL] config buggy lama diterima (seharusnya ditolak)")
    else:
        print("  [LOLOS] config buggy lama ditolak Xray-core (sesuai harapan)")

    # --- 3. Regression: xudp basekey (bug v2.2.1 di lapangan) ---
    print("== 3. Regression xray.xudp.basekey ==")
    ok, out = run_xray_test(build_config(CASES[3][1]),
                            env_extra={"xray.xudp.basekey": VALID_KEY})
    print(f"  [{'LOLOS' if ok else 'GAGAL'}] base64url 32-byte valid")
    if not ok:
        failures.append(("xudp-key-valid", out))

    ok, out = run_xray_test(build_config(CASES[3][1]),
                            env_extra={"xray.xudp.basekey": INVALID_KEY})
    if ok:
        failures.append(("xudp-key-invalid", "UUID seharusnya ditolak (BaseKey must be 32 bytes)"))
        print("  [GAGAL] UUID lolos — seharusnya ditolak Xray-core")
    else:
        print("  [LOLOS] UUID ditolak (BaseKey must be 32 bytes) — key valid wajib dipakai")

    print()
    if failures:
        print(f"TOTAL: {len(failures)} kegagalan:")
        for name, out in failures:
            print(f"\n----- {name} -----\n{out.strip()[:1500]}")
        return 1
    print("SEMUA TES LOLOS — config valid untuk engine Xray-core.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
