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

## Build and signing

Build/test Android untuk pekerjaan parity dan release dilakukan melalui GitHub Actions; jangan
menjalankan Gradle lokal untuk slice ini. Workflow CI mengunduh versi AAR yang dipin dan
memeriksa SHA-256; AAR tidak disimpan di Git.

Keystore lama pernah berada dalam riwayat repositori publik dan harus dianggap bocor. Jangan
menggunakan atau membuat ulang keystore itu. Release baru memerlukan PKCS#12 baru yang dibuat
di luar repositori, disimpan sebagai GitHub Actions secrets, dan SHA-256 sertifikatnya dicatat
sebagai repository variable. Build CI biasa boleh memakai debug signing hanya untuk memeriksa
assembly; artefak tersebut tidak boleh dipublikasikan. Lihat
[`../docs/android-release-readiness.md`](../docs/android-release-readiness.md) untuk rotasi,
secret names, migrasi pengguna, dan seluruh release gates.

## Struktur

```
app/src/main/java/com/nusa/tunnel/
  MainActivity.kt        UI Compose (dashboard/server/pengaturan/log)
  NusaApp.kt             init engine (Seq.setContext + initCoreEnv)
  core/                  parser link, builder config Xray, DataStore, TunnelManager
  service/TunnelVpnService.kt  VpnService + Xray/Hev engine lifecycle
  ui/                    tema, komponen glass, layar
```
