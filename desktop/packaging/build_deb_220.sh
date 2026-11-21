#!/bin/bash
# =============================================================
# Bangun nusatunnel_2.2.0_amd64.deb
# APLIKASI DESKTOP: Electron 31 (dist resmi linux-x64) + renderer
# React+TypeScript (sudah di-build ke app/lib/webui) + backend
# Python stdlib + Xray-core resmi + TUN helper.
# =============================================================
set -euo pipefail
ROOT=/home/z/my-project
CROSS=$ROOT/build/cross/app
DESK=$ROOT/build/cross/desktop
EDL=$ROOT/build/electron-dl
SRC=$ROOT/build/deb-src
OLD=$ROOT/build/nusatunnel           # tree v1.2.0 (aset/tun2socks/polkit)
STAGE=$ROOT/build/deb220/nusatunnel
TMP=$ROOT/build/deb220/edist

rm -rf "$ROOT/build/deb220"
mkdir -p "$STAGE" "$TMP"

# ---------- 1. struktur aplikasi (backend) ----------
mkdir -p "$STAGE/usr/lib/nusatunnel/bin" "$STAGE/usr/lib/nusatunnel/assets"
cp -r "$CROSS/lib"                     "$STAGE/usr/lib/nusatunnel/lib"
cp "$CROSS/main.pyw"                   "$STAGE/usr/lib/nusatunnel/main.pyw"
cp "$CROSS/bin/xray"                   "$STAGE/usr/lib/nusatunnel/bin/xray"
cp "$CROSS/assets/geoip.dat" "$CROSS/assets/geosite.dat" \
                                        "$STAGE/usr/lib/nusatunnel/assets/"
cp "$OLD/usr/lib/nusatunnel/assets"/*.png \
                                        "$STAGE/usr/lib/nusatunnel/assets/" 2>/dev/null || true

# Linux-only: TUN helper + tun2socks
cp "$OLD/usr/lib/nusatunnel/tunmgr-root.sh" "$STAGE/usr/lib/nusatunnel/"
cp "$OLD/usr/lib/nusatunnel/tun2socks"      "$STAGE/usr/lib/nusatunnel/"

# ---------- 2. shell desktop Electron ----------
unzip -q "$EDL/electron-v31.7.7-linux-x64.zip" -d "$TMP"
rm -rf "$TMP/resources/default_app.asar"
mv "$TMP/electron" "$STAGE/usr/lib/nusatunnel/desktop/nusatunnel" 2>/dev/null || {
    mkdir -p "$STAGE/usr/lib/nusatunnel/desktop"
    mv "$TMP/electron" "$STAGE/usr/lib/nusatunnel/desktop/nusatunnel"
}
# pindahkan SELURUH dist (libEGL, chrome-sandbox, locales, dll)
mv "$TMP"/* "$STAGE/usr/lib/nusatunnel/desktop/" 2>/dev/null || true
mkdir -p "$STAGE/usr/lib/nusatunnel/desktop/resources/app"
cp "$DESK/main.js" "$DESK/preload.js" "$DESK/package.json" "$DESK/icon.png" \
    "$STAGE/usr/lib/nusatunnel/desktop/resources/app/"

# ---------- 3. DEBIAN/ ----------
mkdir -p "$STAGE/DEBIAN"
cp "$SRC/control"  "$STAGE/DEBIAN/control"
cp "$SRC/postinst" "$STAGE/DEBIAN/postinst"
cp "$SRC/prerm"    "$STAGE/DEBIAN/prerm"
chmod 755 "$STAGE/DEBIAN/postinst" "$STAGE/DEBIAN/prerm"

# polkit
mkdir -p "$STAGE/etc/polkit-1/rules.d"
cp "$OLD/etc/polkit-1/rules.d/40-nusatunnel.rules" \
    "$STAGE/etc/polkit-1/rules.d/40-nusatunnel.rules"

# ---------- 4. bin/ + menu + ikon ----------
mkdir -p "$STAGE/usr/bin"
cp "$SRC/nusatunnel"     "$STAGE/usr/bin/nusatunnel"
cp "$SRC/nusatunnel-cli" "$STAGE/usr/bin/nusatunnel-cli"
chmod 755 "$STAGE/usr/bin/nusatunnel" "$STAGE/usr/bin/nusatunnel-cli"

mkdir -p "$STAGE/usr/share/applications"
cp "$SRC/nusatunnel.desktop" "$STAGE/usr/share/applications/"
mkdir -p "$STAGE/usr/share/icons"
cp -r "$OLD/usr/share/icons/hicolor" "$STAGE/usr/share/icons/hicolor"

# ---------- 5. docs ----------
DOCD=$STAGE/usr/share/doc/nusatunnel
mkdir -p "$DOCD"
cp "$SRC/README" "$DOCD/README"
cp "$OLD/usr/share/doc/nusatunnel/copyright"        "$DOCD/copyright"
cp "$OLD/usr/share/doc/nusatunnel/LICENSE-xray-core" "$DOCD/LICENSE-xray-core"
cp "$OLD/usr/share/doc/nusatunnel/README-xray-core.md" "$DOCD/README-xray-core.md" 2>/dev/null || true
gzip -9 -n -c "$SRC/changelog.Debian" > "$DOCD/changelog.Debian.gz"

# ---------- 6. izin eksekusi ----------
chmod 755 "$STAGE/usr/lib/nusatunnel/bin/xray" \
          "$STAGE/usr/lib/nusatunnel/tun2socks" \
          "$STAGE/usr/lib/nusatunnel/tunmgr-root.sh" \
          "$STAGE/usr/lib/nusatunnel/desktop/nusatunnel"

# ---------- 7. bangun paket ----------
cd "$ROOT/build/deb220"
dpkg-deb --build --root-owner-group -Zxz nusatunnel
mkdir -p "$ROOT/download"
mv nusatunnel.deb "$ROOT/download/nusatunnel_2.2.0_amd64.deb"
cd "$ROOT/download" && md5sum nusatunnel_2.2.0_amd64.deb > nusatunnel_2.2.0_amd64.deb.md5
ls -la "$ROOT/download/nusatunnel_2.2.0_amd64.deb"
echo "DEB-220-OK"
