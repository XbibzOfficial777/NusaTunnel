#!/bin/bash
# =============================================================
# Bangun NusaTunnel-2.2.0-macOS-{AppleSilicon,Intel}.zip —
# APLIKASI DESKTOP ELECTRON (.app) + backend Python + xray.
# =============================================================
set -euo pipefail
ROOT=/home/z/my-project
CROSS=$ROOT/build/cross/app
DESK=$ROOT/build/cross/desktop
BINS=$ROOT/build/cross-bins
EDL=$ROOT/build/electron-dl

build_arch () {
  local ARCH=$1 XBIN=$2 TAG=$3          # ARCH=arm64|x64, TAG=AppleSilicon|Intel
  local WORK=$ROOT/build/macpkg22/$TAG
  local EDIST=$WORK/electron-mac
  rm -rf "$WORK"
  mkdir -p "$EDIST"

  # ---------- dist Electron darwin ----------
  unzip -q "$EDL/electron-v31.7.7-darwin-$ARCH.zip" -d "$EDIST"
  local APPDIR="$WORK/NusaTunnel.app"
  mv "$EDIST/Electron.app" "$APPDIR"   # nama .app penting utk Finder

  # ganti nama app + executable + Info.plist
  mv "$APPDIR/Contents/MacOS/Electron" "$APPDIR/Contents/MacOS/nusatunnel"
  chmod 755 "$APPDIR/Contents/MacOS/nusatunnel"
  python3 - "$APPDIR/Contents/Info.plist" <<'PYEOF'
import plistlib, sys
p = sys.argv[1]
with open(p, "rb") as f: d = plistlib.load(f)
d["CFBundleExecutable"] = "nusatunnel"
d["CFBundleName"] = "NusaTunnel"
d["CFBundleDisplayName"] = "NusaTunnel"
d["CFBundleIdentifier"] = "id.nusatunnel.app"
d["CFBundleShortVersionString"] = "2.2.0"
d["CFBundleVersion"] = "2.2.0"
with open(p, "wb") as f: plistlib.dump(d, f)
print("Info.plist OK")
PYEOF

  # ikon dock
  python3 "$ROOT/scripts/make_icns.py" \
    "$ROOT/build/nusatunnel/usr/lib/nusatunnel/assets/nusatunnel-256.png" \
    "$APPDIR/Contents/Resources/electron.icns"

  # app Electron (main/preload/package.json/icon)
  rm -rf "$APPDIR/Contents/Resources/default_app.asar"
  mkdir -p "$APPDIR/Contents/Resources/app"
  cp -r "$DESK/." "$APPDIR/Contents/Resources/app/"

  # backend
  mkdir -p "$APPDIR/Contents/Resources/backend/app/bin" \
           "$APPDIR/Contents/Resources/backend/app/assets"
  cp -r "$CROSS/lib" "$APPDIR/Contents/Resources/backend/app/lib"
  rm -f "$APPDIR/Contents/Resources/backend/app/lib/tunmgr.py" \
        "$APPDIR/Contents/Resources/backend/app/lib/sysproxy.py"
  cp "$CROSS/main.pyw" "$APPDIR/Contents/Resources/backend/app/main.pyw"
  cp "$BINS/$XBIN/xray" "$APPDIR/Contents/Resources/backend/app/bin/xray-macos-$ARCH"
  chmod 755 "$APPDIR/Contents/Resources/backend/app/bin/xray-macos-$ARCH"
  cp "$CROSS/assets/geoip.dat" "$CROSS/assets/geosite.dat" \
     "$APPDIR/Contents/Resources/backend/app/assets/"

  # BACA-SAYA di luar .app
  cat > "$WORK/BACA-SAYA.txt" <<EOF
============================================================
 NusaTunnel 2.2.0 untuk macOS $TAG (aplikasi desktop)
============================================================

CARA PAKAI
----------
1. Ekstrak isi ZIP ini (mis. ke ~/Applications).
2. Dua-klik  NusaTunnel.app  — jendela aplikasi desktop
   (liquid glass) terbuka, TANPA browser.
3. Tab "Server": tempel URL subscription atau link
   vmess:// vless:// trojan:// ss:// lalu "Hubungkan".

BARU DI 2.2
-----------
* UI ditulis ulang dengan React + TypeScript (arsitektur ala
  VSCode) + animasi framer-motion, tema liquid glass gelap.
* PERBAIKAN: tombol putus koneksi kini benar-benar memutus.

MUNCUL PERINGATAN GATEKEEPER / "TIDAK DAPAT DIVERIFIKASI"?
---------------------------------------------------------
Karena aplikasi belum ditandatangani Apple, macOS memblokirnya.
Buka Terminal di folder hasil ekstrak lalu jalankan sekali:
    xattr -dr com.apple.quarantine NusaTunnel.app
lalu dua-klik NusaTunnel.app lagi.

MAC MEMINTA INSTAL PYTHON?
--------------------------
Aplikasi butuh Python 3 bawaan sistem. Bila diminta, jalankan
sekali di Terminal:  xcode-select --install
lalu buka NusaTunnel lagi.

DAFTAR SERVER BANYAK?
---------------------
Daftar dibatasi 15 server per halaman; tombol MUAT LAGI
menambah 15 server berikutnya per klik.

DIAGNOSA
--------
Jalankan dari Terminal:
    open NusaTunnel.app --args --enable-logging
Data & log:  ~/Library/Application Support/NusaTunnel/

PROXY LOKAL UNTUK APLIKASI LAIN
-------------------------------
  SOCKS5 : 127.0.0.1:10808
  HTTP   : 127.0.0.1:10809

CATATAN
-------
* Mode TUN (VPN seluruh sistem) saat ini hanya di Linux.
* Binary Xray-core resmi (XTLS) untuk $ARCH sudah dibundel.
EOF

  # ---------- zip ----------
  mkdir -p "$ROOT/download"
  cd "$WORK"
  rm -f "$ROOT/download/NusaTunnel-2.2.0-macOS-$TAG.zip"
  zip -q -r -9 "$ROOT/download/NusaTunnel-2.2.0-macOS-$TAG.zip" \
      NusaTunnel.app BACA-SAYA.txt -x "*.DS_Store"
  ls -la "$ROOT/download/NusaTunnel-2.2.0-macOS-$TAG.zip"
}

# BINS/xa = xray darwin arm64, BINS/xm = xray darwin amd64
build_arch arm64 xa AppleSilicon
build_arch x64   xm Intel
