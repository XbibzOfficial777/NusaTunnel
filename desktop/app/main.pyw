#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NusaTunnel 2.2 — titik masuk (Linux / Windows / macOS)

Menjalankan backend WebUI (stdlib murni, tanpa dependensi eksternal)
dan membuka UI modern di browser default.

Opsi baris perintah:
  --no-browser   jangan buka browser otomatis
  --port N       paksa port WebUI
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))


def main() -> int:
    open_browser = "--no-browser" not in sys.argv
    import webui
    return webui.serve(open_browser=open_browser)


if __name__ == "__main__":
    sys.exit(main())
