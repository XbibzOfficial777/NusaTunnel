#!/bin/bash
# =============================================================
# Bangun NusaTunnel-2.2.0-Windows-x64.zip — APLIKASI DESKTOP
# ELECTRON + React + TypeScript + Python 3.12 embeddable +
# xray.exe resmi XTLS. Dua-klik NusaTunnel.exe -> jendela
# desktop liquid glass.
# =============================================================
set -euo pipefail
ROOT=/home/z/my-project
CROSS=$ROOT/build/cross/app
DESK=$ROOT/build/cross/desktop
BINS=$ROOT/build/cross-bins
EDL=$ROOT/build/electron-dl
STAGE=$ROOT/build/winpkg22/NusaTunnel
EDIST=$ROOT/build/winpkg22/electron-win

rm -rf "$ROOT/build/winpkg22"
mkdir -p "$STAGE" "$EDIST"

# ---------- 1. dist Electron win32-x64 ----------
unzip -q "$EDL/electron-v31.7.7-win32-x64.zip" -d "$EDIST"
rm -rf "$EDIST/resources/default_app.asar"
mv "$EDIST/electron.exe" "$EDIST/NusaTunnel.exe"
mkdir -p "$EDIST/resources/app"
cp "$DESK/main.js" "$DESK/preload.js" "$DESK/package.json" "$DESK/icon.png" \
    "$EDIST/resources/app/"
mv "$EDIST"/* "$STAGE/"

# ---------- 2. backend (python embeddable + aplikasi) ----------
mkdir -p "$STAGE/backend/runtime" "$STAGE/backend/app/bin" "$STAGE/backend/app/assets"
cp -r "$BINS/pe/." "$STAGE/backend/runtime/"
cp -r "$CROSS/lib" "$STAGE/backend/app/lib"
rm -f "$STAGE/backend/app/lib/tunmgr.py" "$STAGE/backend/app/lib/sysproxy.py"
rm -rf "$STAGE/backend/app/lib/__pycache__"
cp "$CROSS/main.pyw"        "$STAGE/backend/app/main.pyw"
cp "$BINS/xw/xray.exe"      "$STAGE/backend/app/bin/xray.exe"
cp "$BINS/xw/wintun.dll"    "$STAGE/backend/app/bin/wintun.dll"
cp "$CROSS/assets/geoip.dat" "$CROSS/assets/geosite.dat" "$STAGE/backend/app/assets/"
cp "$ROOT/build/nusatunnel/usr/lib/nusatunnel/assets/nusatunnel-256.png" "$STAGE/"

# ---------- 3. BACA-SAYI ----------
cat > "$STAGE/BACA-SAYI.txt" <<'EOF'
============================================================
 NusaTunnel 2.2.0 untuk Windows (desktop, 64-bit)
============================================================

CARA PAKAI
----------
1. Ekstrak seluruh isi ZIP ini ke satu folder (mis. C:\NusaTunnel).
2. Dua-klik  NusaTunnel.exe  — jendela aplikasi desktop
   (liquid glass) terbuka, TANPA browser.
3. Tab "Server": tempel URL subscription atau link
   vmess:// vless:// trojan:// ss:// lalu "Hubungkan".

BARU DI 2.2
-----------
* UI ditulis ulang dengan React + TypeScript (arsitektur ala
  VSCode) + animasi framer-motion, tema liquid glass gelap.
* PERBAIKAN: tombol putus koneksi kini benar-benar memutus
  (sebelumnya permintaan pemutusan gagal terkirim).
* Daftar tetap terpaginasi 15 server; MUAT LAGI menambah
  15 berikutnya per klik.

TANPA INSTALASI
---------------
Sudah berisi Electron, Python 3.12 (embeddable resmi
python.org) dan Xray-core resmi (XTLS) — tidak perlu
menginstal apa pun, tidak menyentuh registry Windows.

MUNCUL PERINGATAN SMARTSCREEN?
------------------------------
File dari internet yang belum dikenal ditahan SmartScreen.
Klik "More info" lalu "Run anyway".
Atau: klik-kanan NusaTunnel.exe > Properties > centang "Unblock".

DIAGNOSA
--------
Data & log tersimpan di:  %APPDATA%\NusaTunnel\
Jalankan dari cmd untuk melihat log backend:
   NusaTunnel.exe --enable-logging

PROXY LOKAL UNTUK APLIKASI LAIN
-------------------------------
  SOCKS5 : 127.0.0.1:10808
  HTTP   : 127.0.0.1:10809

CATATAN
-------
* Mode TUN (VPN seluruh sistem) saat ini hanya di Linux;
  di Windows gunakan proxy lokal / proxy sistem browser.
* Butuh Windows 10 (1803+) atau lebih baru.
EOF
unix2dos "$STAGE/BACA-SAYI.txt" 2>/dev/null || sed -i 's/$/\r/' "$STAGE/BACA-SAYI.txt"

# ---------- 4. zip ----------
mkdir -p "$ROOT/download"
cd "$ROOT/build/winpkg22"
rm -f "$ROOT/download/NusaTunnel-2.2.0-Windows-x64.zip"
zip -q -r -9 "$ROOT/download/NusaTunnel-2.2.0-Windows-x64.zip" NusaTunnel
cd "$ROOT/download" && md5sum NusaTunnel-2.2.0-Windows-x64.zip > NusaTunnel-2.2.0-Windows-x64.zip.md5
ls -la NusaTunnel-2.2.0-Windows-x64.zip
echo "WIN-PKG-22-OK"
