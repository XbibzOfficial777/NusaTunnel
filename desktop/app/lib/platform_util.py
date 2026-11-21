#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NusaTunnel - platform_util.py
Abstraksi perbedaan Linux / Windows / macOS: path config, binary Xray,
binary curl, pengecekan PID, spawn proses.
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

APP_NAME = "NusaTunnel"
APP_VERSION = "2.2.0"

APP_DIR = Path(__file__).resolve().parent.parent      # .../app
BIN_DIR = APP_DIR / "bin"
ASSET_DIR = APP_DIR / "assets"


def os_name() -> str:
    """'windows' | 'macos' | 'linux'"""
    if sys.platform.startswith("win"):
        return "windows"
    if sys.platform == "darwin":
        return "macos"
    return "linux"


def platform_label() -> str:
    n = os_name()
    if n == "windows":
        return "Windows"
    if n == "macos":
        return "macOS"
    return "Linux"


def config_dir() -> Path:
    """Direktori data user sesuai konvensi tiap OS."""
    env = os.environ.get("NUSATUNNEL_HOME")
    if env:
        return Path(env)
    n = os_name()
    if n == "windows":
        base = os.environ.get("APPDATA") or str(Path.home() / "AppData" / "Roaming")
        return Path(base) / "NusaTunnel"
    if n == "macos":
        return Path.home() / "Library" / "Application Support" / "NusaTunnel"
    return Path.home() / ".config" / "nusatunnel"


def xray_bin() -> Path:
    """Binary Xray sesuai platform (bisa dioverride utk pengujian)."""
    override = os.environ.get("NUSATUNNEL_XRAY")
    if override:
        return Path(override)
    n = os_name()
    if n == "windows":
        return BIN_DIR / "xray.exe"
    if n == "macos":
        if subprocess.run(["uname", "-m"], capture_output=True,
                          text=True).stdout.strip() == "arm64":
            cand = BIN_DIR / "xray-macos-arm64"
        else:
            cand = BIN_DIR / "xray-macos-amd64"
        if cand.exists():
            return cand
        return BIN_DIR / "xray"
    return BIN_DIR / "xray"


def curl_bin() -> str:
    """Cari curl: Windows 10+ punya curl.exe bawaan; fallback curl di app dir."""
    override = os.environ.get("NUSATUNNEL_CURL")
    if override:
        return override
    w = shutil.which("curl")
    if w:
        return w
    if os_name() == "windows":
        bundled = APP_DIR / "curl.exe"
        if bundled.exists():
            return str(bundled)
    return ""


def curl_available() -> bool:
    return bool(curl_bin())


def pid_alive(pid: int) -> bool:
    """Cek proses hidup, lintas platform (aman: tidak membunuh apa pun)."""
    if not pid or pid <= 0:
        return False
    if os_name() == "windows":
        try:
            import ctypes
            k32 = ctypes.windll.kernel32
            PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
            h = k32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, int(pid))
            if not h:
                return False
            k32.CloseHandle(h)
            return True
        except Exception:
            return False
    try:
        os.kill(int(pid), 0)   # POSIX: signal 0 = cek keberadaan saja
        return True
    except PermissionError:
        return True
    except OSError:
        return False


def popen_kwargs(detach: bool) -> dict:
    """Kwarg tambahan Popen per platform (detach = lepaskan dari parent)."""
    if os_name() == "windows":
        kw = {"creationflags": 0x08000000}      # CREATE_NO_WINDOW
        if detach:
            kw["creationflags"] |= 0x00000008   # DETACHED_PROCESS
        return kw
    kw = {}
    if detach:
        kw["start_new_session"] = True
    return kw


def open_url(url: str) -> bool:
    """Buka URL di browser default, lintas platform."""
    try:
        import webbrowser
        return bool(webbrowser.open(url))
    except Exception:
        return False
