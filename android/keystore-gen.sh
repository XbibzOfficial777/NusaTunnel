#!/bin/bash
# Buat keystore rilis NusaTunnel (self-signed, milik Xbibz Official)
set -e
cd "$(dirname "$0")"
if [ ! -f nusatunnel.keystore ]; then
  /usr/lib/jvm/java-21-openjdk-amd64/bin/keytool -genkeypair -v \
    -keystore nusatunnel.keystore -alias nusatunnel \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass nusatunnel2026 -keypass nusatunnel2026 \
    -dname "CN=NusaTunnel, OU=Xbibz Official, O=Xbibz Official, L=Jakarta, C=ID"
fi
