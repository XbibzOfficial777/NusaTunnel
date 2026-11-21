# NusaTunnel

Klien tunneling **VMess / VLESS / Trojan / Shadowsocks** lintas platform oleh **Xbibz Official**.

Backend inti memakai **Xray-core resmi (XTLS)** — bukan implementasi tiruan. Desktop berjalan di
shell **Electron 31** dengan renderer **React 19 + TypeScript** (workbench ala VSCode, liquid glass),
backend logika **Python stdlib murni**. Versi Android (Kotlin + Jetpack Compose) sedang dikembangkan
di folder `android/`.

## Fitur

- Protokol nyata via Xray-core: VMess, VLESS, Trojan, Shadowsocks
- **Mode TUN** (VPN level sistem) di Linux — interface `nusatun0` via tun2socks
- Proxy lokal untuk aplikasi lain: SOCKS5 `127.0.0.1:10808`, HTTP `127.0.0.1:10809`
- UI desktop modern: activity bar + sidebar + status bar ala VSCode, efek liquid glass, font Montserrat offline
- Daftar server dengan paginasi (15 server per halaman, tombol MUAT LAGI +15)
- Cek latency real-time per server, statistik trafik, log berjalan
- Import server dari link `vmess://`, `vless://`, `trojan://`, `ss://`

## Unduhan

Semua installer ada di tab **Releases**:

| Platform | File |
| --- | --- |
| Linux Mint / Ubuntu / Debian (amd64) | `nusatunnel_<versi>_amd64.deb` |
| Windows 10/11 (x64) | `NusaTunnel-<versi>-Windows-x64.zip` |
| macOS Apple Silicon | `NusaTunnel-<versi>-macOS-AppleSilicon.zip` |
| macOS Intel | `NusaTunnel-<versi>-macOS-Intel.zip` |
| Android (universal, 4 ABI) | `NusaTunnel-2.2.0-android-universal.apk` |

## Struktur repo

```
desktop/
  app/          backend Python (main.pyw + lib/) — logika, API lokal, webui hasil build
  desktop-src/  sumber Electron main/preload + React 19 + TypeScript (Vite)
  packaging/    script build paket .deb / Windows zip / macOS zip + deb-src
android/        sumber aplikasi Android (Kotlin DSL + Jetpack Compose)
docs/           worklog, panduan, catatan agen
dev/            skrip pengujian otomatis
DESIGN.md       bahasa desain UI (basis NerdWallet: spruce/mint/lime, Montserrat)
```

## Build dari sumber (desktop)

Prasyarat: Node.js 18+, Python 3.9+, Linux/macOS/Windows dengan `zip`.

```bash
# 1. Renderer React + main/preload Electron
cd desktop/desktop-src
npm install
npm run build          # renderer -> app/lib/webui, main/preload -> desktop/

# 2. Paket installer (butuh dist Electron resmi + binary xray di build/)
#    lihat detail path di dalam tiap script
cd ../packaging
./build_deb_220.sh        # Linux .deb
./build_windows_zip_22.sh # Windows .zip
./build_macos_zip_22.sh   # macOS .zip (AppleSilicon + Intel)
```

## Kontak

Developer: **Xbibz Official** — xbibzke2@gmail.com
