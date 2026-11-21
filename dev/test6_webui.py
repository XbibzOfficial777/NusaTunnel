#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UJI 6: WebUI cross-platform — END-TO-END NYATA (tanpa simulasi).

Arsitektur uji:
  main.pyw (backend WebUI di 127.0.0.1:17888)
      /api/import  <- link vmess://
      /api/connect <- TunnelManager -> xray client -> SOCKS 10808
  curl -> SOCKS5 127.0.0.1:10808 -> VMess -> 127.0.0.1:10001 (xray server) -> INTERNET NYATA

Bukti definitif: fetch internet sukses saat server VMess hidup,
GAGAL setelah server VMess dimatikan.
"""
import base64
import json
import os
import signal
import subprocess
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

APP = "/home/z/my-project/build/cross/app"
XRAY = f"{APP}/bin/xray"
ASSET = f"{APP}/assets"
HOME = "/tmp/nt2-home"

os.environ["NUSATUNNEL_HOME"] = HOME
os.environ["NUSATUNNEL_PORT"] = "17888"
Path(HOME).mkdir(exist_ok=True)

PORT = 17888
UUID = "b831381d-6324-4d53-ad4f-8cda48b30811"
SERVER_PORT = 10001
env = {**os.environ, "XRAY_LOCATION_ASSET": ASSET}

passed, failed = [], []


def check(name, cond, detail=""):
    (passed if cond else failed).append(name)
    print(f"[{'LULUS' if cond else 'GAGAL'}] {name}" + (f" — {detail}" if detail else ""))


def api(path, payload=None, headers=None):
    """Panggil API WebUI; header X-NusaTunnel otomatis (bisa dimatikan utk uji)."""
    h = {"Content-Type": "application/json"}
    if headers is None:
        h["X-NusaTunnel"] = "1"
    else:
        h.update(headers)
    req = urllib.request.Request(
        f"http://127.0.0.1:{PORT}{path}",
        data=json.dumps(payload).encode() if payload is not None else None,
        headers=h, method="POST" if payload is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, {}


def state():
    code, s = api("/api/state")
    if s is None:
        raise RuntimeError("state kosong")
    return s


def fetch_via_socks(url, socks_port=10808, timeout=20):
    """Fetch via SOCKS5 pakai curl (urllib tidak mendukung socks5h tanpa PySocks)."""
    r = subprocess.run(
        ["curl", "-sf", "--max-time", str(timeout),
         "--socks5-hostname", f"127.0.0.1:{socks_port}", "-o", "/dev/null",
         "-w", "%{http_code}", url],
        capture_output=True, text=True, timeout=timeout + 5)
    return r.stdout.strip() == "200"


# ---------- 1. Server VMess nyata (loopback) ----------
server_cfg = {
    "inbounds": [{
        "port": SERVER_PORT, "listen": "127.0.0.1", "protocol": "vmess",
        "settings": {"clients": [{"id": UUID, "alterId": 64}]},
    }],
    "outbounds": [{"protocol": "freedom"}],
}
Path("/tmp/nt2-server.json").write_text(json.dumps(server_cfg))
srv = subprocess.Popen([XRAY, "run", "-c", "/tmp/nt2-server.json"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env)
time.sleep(1.0)
check("1. Server VMess lokal berjalan", srv.poll() is None)

# ---------- 2. Backend WebUI berjalan ----------
webui_log = open("/tmp/nt2-webui.log", "w")
webui_proc = subprocess.Popen(
    ["/usr/bin/python3", f"{APP}/main.pyw", "--no-browser"],
    stdout=webui_log, stderr=subprocess.STDOUT, text=True,
    env={**os.environ})
time.sleep(1.5)
check("2. Backend WebUI hidup (127.0.0.1:17888)", webui_proc.poll() is None)

try:
    # ---------- 3. State awal & file statis ----------
    s = state()
    check("3. /api/state JSON valid", s.get("app", {}).get("version") == "2.1.0",
          f"versi={s.get('app', {}).get('version')}")
    check("   platform terdeteksi", s.get("app", {}).get("platform") == "linux")
    check("   belum tersambung", s.get("connected") is False)
    for path, frag in (("/", "NusaTunnel"), ("/style.css", "aurora"),
                       ("/app.js", "api")):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}{path}", timeout=10) as r:
                body = r.read().decode()
                ok = r.status == 200 and frag in body
        except Exception:
            ok = False
        check(f"   static {path} tersaji", ok)

    # ---------- 4. Keamanan lokal ----------
    code, _ = api("/api/connect", {"name": "x"}, headers={})
    check("4. POST tanpa header lokal DITOLAK (403)", code == 403, f"code={code}")
    code, _ = api("/api/state", headers={"Host": "evil.example.com"})
    check("   Host asing DITOLAK (403)", code == 403, f"code={code}")

    # ---------- 5. Impor link via API ----------
    link = "vmess://" + base64.b64encode(json.dumps({
        "v": "2", "ps": "Server Uji Lokal", "add": "127.0.0.1",
        "port": str(SERVER_PORT), "id": UUID, "aid": "64", "scy": "auto",
        "net": "tcp", "type": "none", "tls": "",
    }).encode()).decode()
    _, r = api("/api/import", {"text": link + "\n" + "vmess://invalid-link"})
    check("5. /api/import sukses (1 server valid)", r.get("added") == 1,
          f"r={r}")
    check("   link tidak valid terdeteksi", len(r.get("errors", [])) >= 1)

    # ---------- 6. Connect nyata via API ----------
    _, r = api("/api/connect", {"name": "Server Uji Lokal"})
    check("6. /api/connect diterima", r.get("ok") is True)
    deadline = time.time() + 15
    connected = False
    while time.time() < deadline:
        s = state()
        if s.get("connected"):
            connected = True
            break
        if not s.get("connecting") and s.get("connect_error"):
            break
        time.sleep(0.5)
    check("   tunnel TERSAMBUNG (proses xray + proxy siap)", connected,
          s.get("connect_error", ""))
    check("   server aktif benar", s.get("server", {}).get("name") == "Server Uji Lokal")

    # ---------- 7. Trafik internet NYATA via tunnel ----------
    ok = fetch_via_socks("http://example.com") or fetch_via_socks("http://ip-api.com/json")
    check("7. fetch internet NYATA via SOCKS 10808 sukses", ok)

    # ---------- 8. Pengaturan & latensi ----------
    _, r = api("/api/settings", {"routing_mode": "bypass-id"})
    s = state()
    check("8. /api/settings routing berubah", s["settings"]["routing_mode"] == "bypass-id")
    code, r = api("/api/latency", {})
    check("   /api/latency diterima", code == 200 and r.get("ok") is True,
          f"code={code} r={r}")
    deadline = time.time() + 15
    lat = None
    while time.time() < deadline:
        s = state()
        lat = (s.get("servers") or [{}])[0].get("latency")
        if lat is not None:
            break
        time.sleep(0.5)
    check("   latensi server terisi", lat is not None, f"lat={lat}")
    api("/api/settings", {"routing_mode": "global"})

    # ---------- 9. Bukti definitif: server dimatikan -> trafik gagal ----------
    srv.send_signal(signal.SIGTERM)
    srv.wait(timeout=5)
    time.sleep(1.0)
    ok = fetch_via_socks("http://example.com", timeout=12)
    check("9. Setelah server VMess dimatikan, SOCKS gagal (bukti nyata)",
          ok is False)

    # ---------- 10. Disconnect ----------
    code, r = api("/api/disconnect", {})
    check("10. /api/disconnect diterima", code == 200, f"code={code} r={r}")
    deadline = time.time() + 10
    while time.time() < deadline:
        s = state()
        if not s.get("connected"):
            break
        time.sleep(0.4)
    check("    tunnel terputus bersih", s.get("connected") is False,
          f"connected={s.get('connected')} backend_hidup={webui_proc.poll() is None}")

finally:
    try:
        api("/api/quit", {})
    except Exception:
        pass
    time.sleep(1.5)
    if webui_proc.poll() is None:
        webui_proc.terminate()
    webui_log.close()
    print("--- log backend (bila ada traceback) ---")
    print(open("/tmp/nt2-webui.log").read()[-1200:])
    if srv.poll() is None:
        srv.terminate()

print()
print(f"HASIL: {len(passed)} lulus, {len(failed)} gagal")
if failed:
    print("GAGAL:", failed)
    sys.exit(1)
print("SEMUA UJI WEBUI LULUS — backend berfungsi nyata lintas platform.")
