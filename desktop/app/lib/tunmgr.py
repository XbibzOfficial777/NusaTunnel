#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NusaTunnel - tunmgr.py
Kendali Mode TUN dari sisi user: memanggil helper root
(/usr/lib/nusatunnel/tunmgr-root.sh) via pkexec.

Mode TUN = "VPN sungguhan" di level jaringan: membuat interface
nusatun0 dan mengalihkan rute default, sehingga SEMUA aplikasi
(browser, speedtest, CLI, game, update sistem) otomatis lewat
tunnel tanpa perlu menghormati proxy sistem.
"""
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

LIB_DIR = Path(__file__).resolve().parent
# helper bisa berada di lib/ (paket portable) atau satu tingkat di atasnya
# (paket .deb: /usr/lib/nusatunnel/tunmgr-root.sh — dipertahankan agar
# aturan polkit & prerm lama tetap berlaku)
_CANDIDATES = [LIB_DIR / "tunmgr-root.sh", LIB_DIR.parent / "tunmgr-root.sh"]
HELPER = next((p for p in _CANDIDATES if p.exists()), _CANDIDATES[0])
TUN_DEV = "nusatun0"
# env override hanya untuk pengujian (produksi memakai default sistem)
SYSNET = os.environ.get("NUSATUNNEL_SYSNET", "/sys/class/net")
STATE_DIR = Path(os.environ.get("NUSATUNNEL_RUNDIR", "/run/nusatunnel-tun"))


def _pkexec_bin() -> str:
    return os.environ.get("NUSATUNNEL_PKEXEC", "pkexec")


def _run_root(args, timeout=90):
    """Jalankan helper sebagai root via pkexec. Return (output, error)."""
    pk = _pkexec_bin()
    if pk == "pkexec" and shutil.which("pkexec") is None:
        return None, ("pkexec tidak ditemukan. Pasang dulu: "
                      "sudo apt install policykit-1")
    try:
        r = subprocess.run([pk, str(HELPER)] + args,
                           capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return None, "Proses TUN timeout (90 dtk) — mungkin dialog password belum dijawab."
    except FileNotFoundError:
        return None, f"'{pk}' tidak dapat dieksekusi."
    if r.returncode == 0:
        return (r.stdout or "").strip(), None
    return None, ((r.stderr or "").strip() or (r.stdout or "").strip()
                  or f"pkexec gagal (rc={r.returncode})")


def enable(socks_port: int, server_host: str = ""):
    """Aktifkan TUN. Return (output, error)."""
    host = (server_host or "").strip()
    if not host or not re.match(r"^[A-Za-z0-9._:-]+$", host):
        host = "-"
    return _run_root(["up", str(int(socks_port)), host])


def disable(timeout: int = 90):
    """Matikan TUN + pulihkan rute. Return (output, error).

    timeout bisa dipendekkan pemanggil (mis. 20 dtk saat disconnect
    otomatis) agar UI tidak menunggu pkexec terlalu lama.
    """
    return _run_root(["down"], timeout=timeout)


def is_active() -> bool:
    """True bila interface TUN hidup (tanpa perlu root)."""
    return Path(f"{SYSNET}/{TUN_DEV}").exists()


def info() -> dict:
    """Info status TUN ringan (tanpa root)."""
    pid = ""
    try:
        pid = (STATE_DIR / "tun2socks.pid").read_text().strip()
    except Exception:
        pass
    return {"active": is_active(), "device": TUN_DEV if is_active() else "",
            "pid": pid}
