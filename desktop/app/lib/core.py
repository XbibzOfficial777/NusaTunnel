#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NusaTunnel - core.py (cross-platform: Linux / Windows / macOS)
Manajer koneksi nyata: proses Xray, file data, siklus hidup, dan
state untuk WebUI. Integrasi proxy-sistem & TUN hanya aktif di Linux.
"""
import json
import os
import subprocess
import threading
import time
from pathlib import Path

try:
    from . import xraygen, nettest, ipcheck, platform_util as P
    from . import subscription as submod
except ImportError:
    import xraygen
    import nettest
    import ipcheck
    import platform_util as P
    import subscription as submod

CONFIG_DIR = P.config_dir()
SERVERS_FILE = CONFIG_DIR / "servers.json"
SETTINGS_FILE = CONFIG_DIR / "settings.json"
RUNTIME_CONFIG = CONFIG_DIR / "config.json"
PID_FILE = CONFIG_DIR / "xray.pid"
LOG_FILE = CONFIG_DIR / "nusatunnel.log"

DEFAULT_SETTINGS = {
    "subscription_url": "",
    "socks_port": xraygen.DEFAULT_SOCKS_PORT,
    "http_port": xraygen.DEFAULT_HTTP_PORT,
    "last_server": "",
    "routing_mode": "global",        # global | bypass-id
    "auto_system_proxy": True,        # setel proxy sistem otomatis (Linux)
    "tun_mode": False,                # Mode TUN: VPN level sistem (Linux)
}

# ---------- fitur opsional khusus Linux ----------
IS_LINUX = P.os_name() == "linux"


def _linux_mod(name):
    if not IS_LINUX:
        return None
    try:
        if name == "sysproxy":
            import sysproxy
            return sysproxy
        if name == "tunmgr":
            import tunmgr
            return tunmgr
    except Exception:
        return None
    return None


class TunnelError(Exception):
    pass


class TunnelManager:
    """Mengelola satu proses Xray client + state yang dipantau WebUI."""

    def __init__(self):
        self._proc = None
        self._lock = threading.RLock()
        self.connecting = False
        self.connect_error = ""
        self.exit_ip = None          # cache {"ip","isp","country","city"}
        self._started_at = 0.0
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        self.sysproxy = _linux_mod("sysproxy")
        self.tunmgr = _linux_mod("tunmgr")

    # ---------- persistence ----------
    def load_settings(self) -> dict:
        data = dict(DEFAULT_SETTINGS)
        if SETTINGS_FILE.exists():
            try:
                data.update(json.loads(SETTINGS_FILE.read_text("utf-8")))
            except Exception:
                pass
        return data

    def save_settings(self, settings: dict):
        SETTINGS_FILE.write_text(
            json.dumps(settings, indent=2, ensure_ascii=False), "utf-8")

    def load_servers(self) -> list:
        if SERVERS_FILE.exists():
            try:
                return json.loads(SERVERS_FILE.read_text("utf-8"))
            except Exception:
                return []
        return []

    def save_servers(self, servers: list):
        SERVERS_FILE.write_text(
            json.dumps(servers, indent=2, ensure_ascii=False), "utf-8")

    # ---------- state ----------
    def is_running(self) -> bool:
        with self._lock:
            if self._proc and self._proc.poll() is None:
                return True
            pid = self._read_pid()
            if pid and P.pid_alive(pid):
                return True
            return False

    def _read_pid(self):
        try:
            return int(PID_FILE.read_text().strip())
        except Exception:
            return None

    def connected_since(self):
        return self._started_at if (self.is_running() and self._started_at) else 0

    # ---------- lifecycle ----------
    def start(self, server: dict, settings: dict):
        """Jalankan tunnel nyata: pre-check, tulis config, validasi, spawn."""
        with self._lock:
            if self.is_running():
                self.stop()
        addr, port = server.get("address", ""), server.get("port", 0)
        if nettest.tcp_latency(addr, int(port or 0), timeout=6) is None:
            raise TunnelError(
                f"Server {addr}:{port} tidak dapat dihubungi. "
                "Periksa koneksi internet atau validitas server.")
        xray = P.xray_bin()
        if not Path(xray).exists():
            raise TunnelError(f"Binary Xray tidak ditemukan: {xray}")
        cfg = xraygen.build_client_config(
            server,
            socks_port=int(settings.get("socks_port", 10808)),
            http_port=int(settings.get("http_port", 10809)),
            routing_mode=settings.get("routing_mode", "global"),
        )
        RUNTIME_CONFIG.write_text(xraygen.config_to_json(cfg), "utf-8")

        # validasi oleh Xray sendiri — bukan simulasi
        test = subprocess.run(
            [str(xray), "run", "-test", "-c", str(RUNTIME_CONFIG)],
            capture_output=True, text=True, timeout=30,
            env=self._env())
        if test.returncode != 0:
            raise TunnelError("Konfigurasi Xray ditolak:\n"
                              + (test.stderr or test.stdout)[-800:])

        with open(LOG_FILE, "ab") as logfh:
            logfh.write(f"\n===== start {time.strftime('%Y-%m-%d %H:%M:%S')} "
                        f"server={server.get('name')} =====\n".encode())
        self._proc = subprocess.Popen(
            [str(xray), "run", "-c", str(RUNTIME_CONFIG)],
            stdout=open(LOG_FILE, "ab"), stderr=subprocess.STDOUT,
            env=self._env(), **P.popen_kwargs(detach=True))
        PID_FILE.write_text(str(self._proc.pid), "utf-8")

        # tunggu proxy lokal siap (maks 8 detik)
        deadline = time.time() + 8
        try:
            while time.time() < deadline:
                if self._proc.poll() is not None:
                    raise TunnelError(
                        "Xray berhenti sendiri. Lihat log: " + str(LOG_FILE))
                if nettest.check_local_proxy(int(settings.get("socks_port", 10808))):
                    self._started_at = time.time()
                    self.exit_ip = None
                    return True
                time.sleep(0.25)
            raise TunnelError(
                "Proxy lokal tidak siap dalam 8 detik. Cek log: " + str(LOG_FILE))
        except Exception:
            self.stop()
            raise

    def stop(self):
        """Putuskan tunnel. Perbaikan 2.2 ("gamau putus"):

        1. Bunuh Xray DULU — cepat, di dalam lock — supaya status
           'Terhubung' turun seketika dan UI tidak membeku.
        2. Teardown lambat (TUN via pkexec, sysproxy) dikerjakan
           SETELAH lock dilepas — polling /api/state tidak pernah
           diblokir oleh dialog password polkit.
        3. Eskalasi SIGTERM -> tunggu -> SIGKILL, plus verifikasi
           PID benar-benar milik Xray (hindari membunuh PID reused).
        """
        slow = []          # pekerjaan teardown yang boleh lambat
        with self._lock:
            pid = self._read_pid()
            if pid and self._pid_is_xray(pid):
                try:
                    if P.os_name() == "windows":
                        subprocess.run(["taskkill", "/F", "/PID", str(pid)],
                                       capture_output=True, timeout=10)
                    else:
                        os.kill(pid, 15)          # SIGTERM dulu (sopan)
                        for _ in range(12):        # tunggu <= 3 dtk
                            if not P.pid_alive(pid):
                                break
                            time.sleep(0.25)
                        if P.pid_alive(pid):
                            try:
                                os.kill(pid, 9)    # SIGKILL (pasti mati)
                            except OSError:
                                pass
                except Exception:
                    pass
            if self._proc and self._proc.poll() is None:
                try:
                    self._proc.terminate()
                    self._proc.wait(timeout=5)
                except Exception:
                    try:
                        self._proc.kill()
                    except Exception:
                        pass
            self._proc = None
            self._started_at = 0.0
            try:
                PID_FILE.unlink()
            except FileNotFoundError:
                pass
            # teardown lambat dikumpulkan, dijalankan TANPA lock
            if self.tunmgr and self._tun_active_snapshot():
                slow.append("tun")
        for what in slow:
            try:
                if what == "tun":
                    self.tunmgr.disable(timeout=20)   # pendek, bukan 90 dtk
            except Exception:
                pass
        return True

    def _pid_is_xray(self, pid: int) -> bool:
        """Pastikan PID di pid-file memang proses Xray (bukan PID reused)."""
        if self._proc and self._proc.pid == pid:
            return True
        try:
            with open(f"/proc/{pid}/comm") as f:      # Linux
                return f.read().strip().startswith("xray")
        except OSError:
            return P.os_name() != "linux"             # non-Linux: percaya pid-file

    def _tun_active_snapshot(self) -> bool:
        try:
            return bool(self.tunmgr.is_active())
        except Exception:
            return False

    def _env(self):
        env = dict(os.environ)
        env["XRAY_LOCATION_ASSET"] = str(P.ASSET_DIR)
        return env

    # ---------- integrasi Linux: proxy sistem & TUN ----------
    def sysproxy_supported(self) -> bool:
        return self.sysproxy is not None

    def tun_supported(self) -> bool:
        return self.tunmgr is not None

    def apply_sysproxy(self, settings):
        if not self.sysproxy:
            return
        try:
            if self.is_running() and settings.get("auto_system_proxy"):
                self.sysproxy.apply(int(settings.get("socks_port", 10808)),
                                    CONFIG_DIR)
            else:
                self.sysproxy.restore(CONFIG_DIR)
        except Exception:
            pass

    def _tun_off(self):
        if self.tunmgr:
            try:
                if self.tunmgr.is_active():
                    self.tunmgr.disable()
            except Exception:
                pass

    def tun_on(self, settings, server) -> tuple:
        if not self.tunmgr:
            return None, "Mode TUN hanya tersedia di Linux."
        return self.tunmgr.enable(int(settings.get("socks_port", 10808)),
                                  (server or {}).get("address", ""))

    def tun_off(self) -> tuple:
        if not self.tunmgr:
            return None, "Mode TUN hanya tersedia di Linux."
        return self.tunmgr.disable()

    def tun_info(self) -> dict:
        if not self.tunmgr:
            return {"supported": False, "active": False}
        info = self.tunmgr.info()
        info["supported"] = True
        return info

    # ---------- util ----------
    def tail_log(self, lines: int = 60) -> str:
        """Baca N baris terakhir log — murni Python (tanpa binary tail)."""
        try:
            if not LOG_FILE.exists():
                return "(log belum tersedia)"
            with open(LOG_FILE, "rb") as f:
                f.seek(0, 2)
                size = f.tell()
                chunk = min(size, 64 * 1024)
                f.seek(max(0, size - chunk))
                data = f.read().decode("utf-8", errors="replace")
            return "\n".join(data.splitlines()[-lines:])
        except Exception:
            return "(log tidak bisa dibaca)"

    def refresh_exit_ip(self, settings):
        """Ambil IP keluar via tunnel (thread latar)."""
        def work():
            self.exit_ip = ipcheck.fetch(
                socks_port=int(settings.get("socks_port", 10808))) \
                if self.is_running() else None
        threading.Thread(target=work, daemon=True).start()

    # ---------- state untuk WebUI ----------
    def snapshot(self) -> dict:
        settings = self.load_settings()
        servers = self.load_servers()
        running = self.is_running()
        last = settings.get("last_server", "")
        active = next((s for s in servers if s.get("name") == last), None)
        return {
            "app": {
                "name": P.APP_NAME, "version": P.APP_VERSION,
                "platform": P.os_name(), "platform_label": P.platform_label(),
                "config_dir": str(CONFIG_DIR),
                "xray_bin": str(P.xray_bin()),
                "curl_ok": P.curl_available(),
            },
            "connected": running,
            "connecting": self.connecting,
            "connect_error": self.connect_error,
            "server": active if running else None,
            "connected_since": int(self.connected_since()),
            "settings": settings,
            "servers": servers,
            "features": {
                "tun": self.tun_supported(),
                "sysproxy": self.sysproxy_supported(),
            },
            "tun": self.tun_info(),
            "exit_ip": self.exit_ip,
        }
