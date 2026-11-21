# NusaTunnel untuk Android

Aplikasi Android **NusaTunnel** — `com.nusa.tunnel`, developer **Xbibz Official**.

- **Kotlin 2.4.21** + **Jetpack Compose** (BOM 2026.11.01, Material 3)
- **Gradle 9.9.2** + **AGP 9.5.2**, Kotlin DSL + version catalog
- Engine tunnel nyata: **Xray-core (XTLS)** via AndroidLibXrayLite v26.11.9 (`app/libs/libxray.aar`,
  sudah termasuk `libgojni.so` 4 ABI + geoip/geosite)
- **VpnService** mode TUN seluruh sistem (fd TUN diteruskan langsung ke Xray-core)
- Import link `vmess://` `vless://` `trojan://` `ss://`, ping nyata via `measureOutboundDelay`,
  statistik trafik nyata dari counter Xray (bukan simulasi)
- UI "Midnight Aurora": liquid glass, tombol connect berdenyut, transisi animasi,
  font Montserrat offline, paginasi daftar server 15 + MUAT LAGI (+15)

## Build

```bash
# prasyarat: JDK 21, Android SDK (platform 37.1, build-tools 37)
cd android
../gradle-9.9.2/bin/gradle :app:assembleNusaRelease
# hasil: app/build/outputs/apk/nusaRelease/app-nusaRelease.apk
```

Keystore rilis dibuat via `./keystore-gen.sh` (keytool, self-signed Xbibz Official) —
**jangan** commit keystore ke repo.

## Struktur

```
app/src/main/java/com/nusa/tunnel/
  MainActivity.kt        UI Compose (dashboard/server/pengaturan/log)
  NusaApp.kt             init engine (Seq.setContext + initCoreEnv)
  core/                  parser link, builder config Xray, DataStore, TunnelManager
  service/TunnelVpnService.kt  VpnService + startLoop/stopLoop engine
  ui/                    tema, komponen glass, layar
```
