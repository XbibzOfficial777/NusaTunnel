# NusaTunnel untuk Android

Aplikasi Android **NusaTunnel** — `com.nusa.tunnel`, developer **Xbibz Official**.

- **Kotlin 2.4.21** + **Jetpack Compose** (BOM 2026.11.01, Material 3)
- **Gradle 9.9.2** + **AGP 9.5.2**, Kotlin DSL + version catalog
- Engine inti **Xray-core (XTLS)** via AndroidLibXrayLite v26.11.9 (`app/libs/libxray.aar`,
  termasuk `libgojni.so` 4 ABI + geoip/geosite)
- **HevSocks5Tunnel 2.18.0** adalah default packet engine TUN-to-SOCKS5 pada Android 10+;
  Xray native TUN tetap bisa dipilih manual dan menjadi fallback pada Android 8–9
- Parser/build config untuk VMess, VLESS, Trojan, Shadowsocks; VLESS/Trojan REALITY, XHTTP,
  dan HTTPUpgrade. Detail dukungan: `../docs/android-support-matrix.md`
- Ping nyata via `measureOutboundDelay`, statistik trafik dari counter Xray, opsi log-level,
  serta auto-ping lima-menit yang opt-in
- UI "Midnight Aurora": liquid glass, tombol connect berdenyut, transisi animasi,
  font Montserrat offline, paginasi daftar server 15 + MUAT LAGI (+15)

## Build

Build lokal memerlukan JDK 21, Gradle 9.9.2, Android SDK platform 37.1/build-tools 37, serta kedua
AAR di `app/libs/`. Workflow CI mengunduh versi yang dipin dan memeriksa SHA-256; AAR tidak
disimpan di Git. Untuk build di lingkungan pengembang yang sudah disiapkan:

```bash
cd android
gradle :app:assembleNusaRelease
```

Keystore rilis dibuat via `./keystore-gen.sh` (keytool, self-signed Xbibz Official) —
**jangan** commit keystore ke repo.

## Struktur

```
app/src/main/java/com/nusa/tunnel/
  MainActivity.kt        UI Compose (dashboard/server/pengaturan/log)
  NusaApp.kt             init engine (Seq.setContext + initCoreEnv)
  core/                  parser link, builder config Xray, DataStore, TunnelManager
  service/TunnelVpnService.kt  VpnService + Xray/Hev engine lifecycle
  ui/                    tema, komponen glass, layar
```
