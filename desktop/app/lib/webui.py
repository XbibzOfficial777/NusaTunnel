#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NusaTunnel - webui.py (cross-platform)
Backend UI modern: HTTP server (murni stdlib, tanpa dependensi eksternal)
yang menyajikan SPA statis + JSON API. Browser default dibuka otomatis.

Keamanan lokal:
  - Server HANYA mendengarkan 127.0.0.1.
  - POST wajib membawa header X-NusaTunnel (situs lain di browser tidak
    bisa mengirim header custom tanpa preflight CORS, yang kami tolak).
  - Header Host wajib 127.0.0.1 / localhost (anti DNS-rebinding).
"""
import json
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

try:
    from . import core as core_mod
    from . import nettest, platform_util as P
    from . import subscription as submod
    from . import linkparser
except ImportError:
    import core as core_mod
    import nettest
    import platform_util as P
    import subscription as submod
    import linkparser

WEBUI_DIR = Path(__file__).resolve().parent / "webui"
DEFAULT_PORTS = (17500, 17501, 17510, 17999)

_mgr = core_mod.TunnelManager()
_app = {
    "latency_running": False,
    "sub_running": False,
    "sub_last_result": "",
    "started_at": time.time(),
}
_quit_evt = threading.Event()


# ================= aksi (dijalankan di thread) =================

def _find_server(name):
    servers = _mgr.load_servers()
    return next((s for s in servers if s.get("name") == name), None), servers


def action_connect(name: str):
    settings = _mgr.load_settings()
    server, servers = _find_server(name)
    if not server:
        _mgr.connect_error = f"Server '{name}' tidak ditemukan."
        return
    _mgr.connecting = True
    _mgr.connect_error = ""
    try:
        _mgr.start(server, settings)
        settings["last_server"] = name
        _mgr.save_settings(settings)
        _mgr.apply_sysproxy(settings)
        if settings.get("tun_mode"):
            out, err = _mgr.tun_on(settings, server)
            if err:
                _mgr.connect_error = f"TUN gagal: {err} (tunnel tetap aktif)"
        _mgr.refresh_exit_ip(settings)
    except core_mod.TunnelError as e:
        _mgr.connect_error = str(e)
    except Exception as e:
        _mgr.connect_error = f"{type(e).__name__}: {e}"
    finally:
        _mgr.connecting = False


def action_disconnect():
    settings = _mgr.load_settings()
    _mgr.stop()
    if _mgr.sysproxy:
        try:
            _mgr.sysproxy.restore(core_mod.CONFIG_DIR)
        except Exception:
            pass
    _mgr.exit_ip = None


def action_latency():
    if _app["latency_running"]:
        return
    _app["latency_running"] = True
    try:
        servers = _mgr.load_servers()
        for s in servers:
            s["latency"] = nettest.tcp_latency(
                s.get("address", ""), int(s.get("port", 0) or 0), timeout=3)
        _mgr.save_servers(servers)
    finally:
        _app["latency_running"] = False


def action_import(text: str):
    servers, errors = linkparser.parse_links(text or "")
    if servers:
        existing = _mgr.load_servers()
        names = {s.get("name") for s in existing}
        added = 0
        for s in servers:
            base = s["name"]
            n = 2
            while s["name"] in names:
                s["name"] = f"{base} #{n}"
                n += 1
            names.add(s["name"])
            existing.append(s)
            added += 1
        _mgr.save_servers(existing)
    return {"added": len(servers), "errors": errors}


def action_subscription(url: str):
    if _app["sub_running"]:
        return {"error": "Pembaruan subscription sedang berjalan."}
    _app["sub_running"] = True
    try:
        res = submod.fetch_subscription(url.strip())
        _mgr.save_servers(res["servers"])
        settings = _mgr.load_settings()
        settings["subscription_url"] = url.strip()
        _mgr.save_settings(settings)
        _app["sub_last_result"] = (
            f"{len(res['servers'])} server dimuat"
            + (f", {len(res['errors'])} link tidak valid" if res["errors"] else ""))
        return {"count": len(res["servers"]),
                "errors": res["errors"][:5], "msg": _app["sub_last_result"]}
    except Exception as e:
        _app["sub_last_result"] = f"Gagal: {e}"
        return {"error": str(e)}
    finally:
        _app["sub_running"] = False


def action_settings(patch: dict):
    settings = _mgr.load_settings()
    for k in ("routing_mode", "subscription_url", "last_server"):
        if k in patch and isinstance(patch[k], str):
            settings[k] = patch[k]
    for k in ("auto_system_proxy", "tun_mode"):
        if k in patch and isinstance(patch[k], bool):
            settings[k] = patch[k]
    for k in ("socks_port", "http_port"):
        if k in patch:
            try:
                v = int(patch[k])
                if 1024 <= v <= 65535:
                    settings[k] = v
            except (TypeError, ValueError):
                pass
    if settings.get("routing_mode") not in ("global", "bypass-id"):
        settings["routing_mode"] = "global"
    _mgr.save_settings(settings)
    # terapkan efek langsung bila tunnel sedang aktif
    if _mgr.is_running():
        _mgr.apply_sysproxy(settings)
        if settings.get("tun_mode") and not _mgr.tun_info().get("active"):
            out, err = _mgr.tun_on(settings, _find_server(settings.get("last_server"))[0])
            if err:
                settings["tun_mode"] = False
                _mgr.save_settings(settings)
                return {"warning": f"TUN gagal diaktifkan: {err}"}
        elif not settings.get("tun_mode") and _mgr.tun_info().get("active"):
            _mgr.tun_off()
    return {"ok": True}


def action_tun(on: bool):
    settings = _mgr.load_settings()
    if on:
        if not _mgr.is_running():
            return {"error": "Sambungkan tunnel dulu sebelum menyalakan TUN."}
        out, err = _mgr.tun_on(settings, _find_server(settings.get("last_server"))[0])
        if err:
            return {"error": err}
        settings["tun_mode"] = True
    else:
        out, err = _mgr.tun_off()
        if err:
            return {"error": err}
        settings["tun_mode"] = False
    _mgr.save_settings(settings)
    return {"ok": True, "detail": out or ""}


def action_delete_server(name: str):
    servers = _mgr.load_servers()
    servers = [s for s in servers if s.get("name") != name]
    _mgr.save_servers(servers)
    if _mgr.is_running() and _mgr.load_settings().get("last_server") == name:
        action_disconnect()
    return {"ok": True}


def action_quit():
    """Berhenti bersih: matikan TUN, pulihkan proxy sistem, hentikan xray."""
    def work():
        time.sleep(0.3)
        try:
            action_disconnect()
        finally:
            _quit_evt.set()
    threading.Thread(target=work, daemon=True).start()
    return {"ok": True}


# ================= HTTP handler =================

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "NusaTunnel/" + P.APP_VERSION

    # ---------- util ----------
    def log_message(self, fmt, *args):   # senyapkan log akses
        pass

    def _host_ok(self) -> bool:
        host = (self.headers.get("Host") or "").split(":")[0].lower()
        return host in ("127.0.0.1", "localhost", "[::1]", "::1")

    def _send_json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _send_file(self, path: Path, ctype: str):
        try:
            body = path.read_bytes()
        except FileNotFoundError:
            self._send_json({"error": "not found"}, 404)
            return
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    # ---------- GET ----------
    _MIME = {
        ".html": "text/html; charset=utf-8",
        ".css": "text/css; charset=utf-8",
        ".js": "application/javascript; charset=utf-8",
        ".mjs": "application/javascript; charset=utf-8",
        ".json": "application/json; charset=utf-8",
        ".svg": "image/svg+xml",
        ".png": "image/png",
        ".ico": "image/x-icon",
        ".woff2": "font/woff2",
        ".map": "application/json",
    }

    def _send_static(self, rel: str):
        """Kirim file statis dari webui/ dengan sanitasi path."""
        rel = rel.lstrip("/")
        if not rel or ".." in rel or rel.startswith("/"):
            return self._send_json({"error": "not found"}, 404)
        fpath = (WEBUI_DIR / rel).resolve()
        try:
            fpath.relative_to(WEBUI_DIR.resolve())
        except ValueError:
            return self._send_json({"error": "not found"}, 404)
        ctype = self._MIME.get(fpath.suffix.lower())
        if not ctype:
            return self._send_json({"error": "not found"}, 404)
        return self._send_file(fpath, ctype)

    def do_GET(self):
        if not self._host_ok():
            return self._send_json({"error": "host ditolak"}, 403)
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            return self._send_file(WEBUI_DIR / "index.html",
                                   "text/html; charset=utf-8")
        # aset build React (assets/*.js, assets/*.css) + font Montserrat
        if path.startswith("/assets/") or path.startswith("/fonts/"):
            return self._send_static(path)
        if path == "/api/state":
            snap = _mgr.snapshot()
            snap["ui"] = {
                "latency_running": _app["latency_running"],
                "sub_running": _app["sub_running"],
                "sub_last_result": _app["sub_last_result"],
                "uptime": int(time.time() - _app["started_at"]),
            }
            return self._send_json(snap)
        if path == "/api/log":
            return self._send_json({"log": _mgr.tail_log(120)})
        return self._send_json({"error": "not found"}, 404)

    # ---------- POST ----------
    def do_POST(self):
        if not self._host_ok():
            return self._send_json({"error": "host ditolak"}, 403)
        if self.headers.get("X-NusaTunnel") != "1":
            return self._send_json({"error": "header lokal hilang"}, 403)
        try:
            length = int(self.headers.get("Content-Length") or 0)
            payload = json.loads(self.rfile.read(length).decode("utf-8")
                                 or "{}") if length else {}
        except Exception:
            return self._send_json({"error": "JSON tidak valid"}, 400)
        path = self.path.split("?")[0]

        if path == "/api/connect":
            name = str(payload.get("name") or "")
            threading.Thread(target=action_connect, args=(name,),
                             daemon=True).start()
            return self._send_json({"ok": True, "connecting": True})
        if path == "/api/disconnect":
            threading.Thread(target=action_disconnect, daemon=True).start()
            return self._send_json({"ok": True})
        if path == "/api/latency":
            threading.Thread(target=action_latency, daemon=True).start()
            return self._send_json({"ok": True, "running": True})
        if path == "/api/import":
            return self._send_json(action_import(str(payload.get("text") or "")))
        if path == "/api/subscription":
            url = str(payload.get("url") or "").strip()
            if not re.match(r"^https?://", url):
                return self._send_json({"error": "URL harus diawali http:// atau https://"})
            threading.Thread(target=action_subscription, args=(url,),
                             daemon=True).start()
            return self._send_json({"ok": True, "running": True})
        if path == "/api/settings":
            return self._send_json(action_settings(payload))
        if path == "/api/tun":
            return self._send_json(action_tun(bool(payload.get("on"))))
        if path == "/api/delete-server":
            return self._send_json(
                action_delete_server(str(payload.get("name") or "")))
        if path == "/api/iprefresh":
            _mgr.refresh_exit_ip(_mgr.load_settings())
            return self._send_json({"ok": True})
        if path == "/api/quit":
            return self._send_json(action_quit())
        return self._send_json({"error": "not found"}, 404)


def serve(open_browser: bool = True):
    """Jalankan server; return tak kembali sampai /api/quit."""
    ports = DEFAULT_PORTS
    env_port = os.environ.get("NUSATUNNEL_PORT")
    if env_port:
        ports = (int(env_port),)
    httpd = None
    for port in ports:
        try:
            httpd = ThreadingHTTPServer(("127.0.0.1", port), Handler)
            break
        except OSError:
            continue
    if httpd is None:
        raise RuntimeError("Tidak ada port tersedia untuk WebUI "
                           f"({ports[0]}-{ports[-1]}).")
    url = f"http://127.0.0.1:{httpd.server_address[1]}/"
    httpd.timeout = 1.0            # agar loop bisa merespons /api/quit cepat
    print(f"{P.APP_NAME} {P.APP_VERSION} ({P.platform_label()})")
    print(f"UI  : {url}")
    print(f"Data: {core_mod.CONFIG_DIR}")
    if open_browser:
        threading.Timer(0.6, lambda: P.open_url(url)).start()

    t = threading.Thread(target=_quit_evt.wait, daemon=True)
    t.start()
    try:
        while not _quit_evt.is_set():
            httpd.handle_request()   # satu permintaan per iterasi (bisa diinterupsi)
    finally:
        try:
            action_disconnect()
        except Exception:
            pass
    print("NusaTunnel berhenti bersih.")
    return 0
