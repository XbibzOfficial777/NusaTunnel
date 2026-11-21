# AGENTS.md — Panduan Agen untuk Kode Basis NusaTunnel

Dokumen ini wajib dibaca oleh siapa pun (manusia atau AI) yang akan mengembangkan,
memperbaiki, atau merilis NusaTunnel. Ia berisi arsitektur, aturan main, cara membangun,
cara menguji, dan proses rilis.

---

## 1. Tentang Proyek

**NusaTunnel** adalah klien tunneling VMess / VLESS (REALITY) / Trojan / Shadowsocks
**lintas platform** (Linux / Windows / macOS), dibangun di atas **binary Xray-core resmi
(XTLS)**, dengan **UI web modern beranimasi** yang terbuka otomatis di browser default
(backend murni Python stdlib — tanpa dependensi GUI apa pun).

- Versi saat ini: **2.0.0**
- Paket: `nusatunnel_2.0.0_amd64.deb` (Linux), `NusaTunnel-2.0.0-Windows-x64.zip`
  (portable, embedded Python), `NusaTunnel-2.0.0-macOS.zip` (amd64+arm64)
- Target: Linux Mint 20+, Windows 10 1803+, macOS (Intel & Apple Silicon)
- Mode TUN & proxy sistem otomatis & CLI: khusus Linux (fitur lain identik di 3 OS)

## 2. Aturan Main (TIDAK BOLEH DILANGGAR)

1. **100% NYATA, TANPA SIMULASI.** Setiap klaim fitur harus dibuktikan dengan proses
   sungguhan: server Xray lokal + client melalui jalur kode aplikasi + koneksi internet
   nyata. Mock hanya boleh untuk STUB ANTARMUKA EKSTERNAL (mis. gsettings stub saat uji),
   tidak untuk logika inti. Kebanggaan proyek ini: "curl via SOCKS sukses saat server
   hidup, GAGAL saat server dimatikan."
2. **Semua teks antarmuka (GUI, CLI, pesan error, log) dalam Bahasa Indonesia.**
3. **Internet user tidak boleh pernah dibiarkan rusak.** Setiap perubahan rute/proxy
   harus punya jalur pemulihan (lihat §5, "empat lapis pengaman").
4. **Jangan percaya PATH.** Selalu cari interpreter `/usr/bin/python3` dan binary sistem
   secara absolut (pelajaran v1.0.2: `python3` milik user bisa pyenv/conda).
5. **Setelah menulis gsettings/dsettings, selalu VERIFIKASI READBACK** sebelum melaporkan
   sukses (pelajaran v1.1.1: kegagalan senyap).
6. Placeholder/hardcode kredensial DILARANG. Konfigurasi user disimpan di
   `~/.config/nusatunnel/`.
7. **Aplikasi = DESKTOP ELEKTRON, bukan web.** UI dimuat dari backend lokal ke
   BrowserWindow Electron; mode browser hanya fallback darurat. Jangan pernah
   mengubah launcher agar membuka browser sebagai mode utama.
8. **Paginasi daftar server wajib dipertahankan**: 15 server per halaman, tombol
   MUAT LAGI menambah TEPAT SATU potong per klik — jangan pernah "tampilkan semua"
   setelah load more (permintaan eksplisit user v2.1.0).

## 3. Layout Kode Basis

```
build/cross/app/                    # SUMBER UTAMA backend (lintas platform)
├── main.pyw                        # titik masuk backend: sys.path lib/ → webui.serve()
├── bin/xray                        # binary Xray (Linux; win: xray.exe; mac: 2 binary)
├── assets/                         # geoip.dat (v2fly), geosite.dat
└── lib/
    ├── platform_util.py            # path per-OS, xray_bin, curl_bin, pid_alive, popen_kwargs
    ├── core.py                     # TunnelManager + stop() cepat (bunuh xray dulu,
    │                               #   teardown TUN tanpa lock, SIGKILL, cek /proc/comm)
    ├── webui.py                    # HTTP server stdlib + JSON API + keamanan lokal
    │                               #   + /assets (build Vite) + /fonts
    ├── webui/                      # HASIL BUILD VITE (React) — jangan edit manual!
    │   ├── index.html              #   edit di desktop-src lalu npm run build
    │   ├── assets/index.*.js|css   #   bundle React + CSS liquid glass
    │   └── fonts/                  #   Montserrat woff2 300/500/600/700 (dibundel!)
    ├── linkparser.py / xraygen.py / nettest.py / ipcheck.py / subscription.py
    └── tunmgr.py, sysproxy.py      # LINUX-ONLY (dikecualikan dari paket Win/Mac!)

build/cross/desktop-src/            # SUMBER APLIKASI DESKTOP (v2.2, React+TS ala VSCode)
├── package.json                   # react 19, framer-motion, electron 31.7.7 (types),
│                                  #   vite, esbuild, typescript — npm run build
├── tsconfig{,.app,.node}.json     # strict; app=renderer, node=main/preload
├── vite.config.ts                 # renderer → outDir ../app/lib/webui (base "./")
├── build-main.mjs                 # esbuild: main.ts + preload.ts → ../desktop/*.js
└── src/
    ├── main/main.ts               # proses utama Electron (spawn backend, frameless,
    │                              #   tray, IPC, E2E evalRetry)
    ├── preload/preload.ts         # contextBridge: window.nusaDesktop (win-ctl)
    └── renderer/
        ├── index.html             # CSP ketat; root React
        └── src/                   # types.ts (kontrak API), api.ts (get/post TERPISAH —
                                   #   aksi TANPA body pun wajib POST!), appState.tsx
                                   #   (polling+toast+aksi optimistik), components/,
                                   #   views/ (Home/Servers/Settings/Log), styles/global.css

build/cross/desktop/                # HASIL BUNDLE (dipakai paket; jangan edit manual)
├── main.js / preload.js           # dari build-main.mjs
├── package.json / icon.png

build/deb-src/                      # file khusus paket .deb
├── control / postinst / prerm / nusatunnel.desktop
├── nusatunnel                      # launcher Linux (desktop-first + fallback web)
├── nusatunnel-cli                  # CLI (list|start|stop|status|log|tun|doctor)
├── changelog.Debian / README

build/nusatunnel/                   # tree v1.2.0 (ARSIP: ikon, tun2socks, tunmgr-root.sh,
                                   #   polkit rules, docs — dipakai ulang oleh build 2.1)
build/cross-bins/                   # binary unduhan: xw (win), xm/xa (mac), pe (python embed)
build/electron-dl/                  # dist Electron v31.7.7 resmi (linux/win/darwin-x64/arm64)

scripts/                           # skrip uji & build (TIDAK ikut dalam paket)
├── build_deb_220.sh / build_windows_zip_22.sh / build_macos_zip_22.sh / make_icns.py
├── fetch_cross_bins.sh             # pengunduh binary resmi (Xray XTLS + python.org)
├── test1..test5                    # uji v1.x (masih relevan utk parser/proto)
├── test6_webui.py                  # uji e2e WebUI 2.0 (22 cek)
├── test7_packages.py               # uji paket 3 platform (43 cek)
├── test_visual_ui_21.sh            # screenshot UI + verifikasi visual
└── vlm_check22.js                  # verifikasi visual screenshot via VLM

download/                          # deliverable + md5 + PANDUAN
```

File .deb dipasang ke `/usr/lib/nusatunnel/` dengan struktur:
`main.pyw`, `lib/`, `bin/xray`, `assets/`, `tunmgr-root.sh`, `tun2socks`, dan
`desktop/` (dist Electron + `resources/app` dari build/cross/desktop).
Electron mencari backend otomatis: `desktop/resources/app/main.js` naik dua tingkat
(`../..`) → `/usr/lib/nusatunnel/main.pyw`. Paket Win: `NusaTunnel.exe` +
`resources/app` + `backend/{runtime,app}`. Paket mac: `NusaTunnel.app/Contents/`
`{MacOS/nusatunnel, Resources/app, Resources/backend}`.

## 4. Arsitektur & Alur Data

```
link vmess:// / vless:// / trojan:// / ss://  ── atau ──  subscription URL
        │                                              │
        ▼                                              ▼
  linkparser.py ◄─────────────────────────────── subscription.py
        │  dict server (address, port, id, security, transport, ...)
        ▼
  xraygen.py  → config Xray client (inbound SOCKS 10808 + HTTP 10809, routing geoip)
        │
        ▼
  core.py (TunnelManager) ── pre-check tcp_latency → xray run -test → spawn → tunggu siap
        │
        ├──► sysproxy.py  (opsional: proxy sistem GNOME/Cinnamon/MATE, backup+restore)
        └──► tunmgr.py ──pkexec──► tunmgr-root.sh ──► tun2socks + interface nusatun0
                                                    + pengalihan default route (v4+v6)
        │
        ▼
  nusatunnel.py (GUI) / nusatunnel-cli ── status, IP keluar (ipcheck.py via curl)
```

**Dua jalur "menyalurkan trafik", keduanya bisa aktif bersamaan:**

| Jalur | Cara kerja | Keterbatasan |
|---|---|---|
| Proxy sistem (sysproxy.py) | gsettings `system.proxy` → dihormati browser & app desktop | Diabaikan speedtest/game/CLI; hanya GNOME/Cinnamon/MATE |
| **Mode TUN (tunmgr\*.py/sh)** | Interface `nusatun0` + tun2socks + default route dialihkan | Butuh root sekali (pkexec); bekerja untuk SEMUA app & SEMUA DE |

Mode TUN adalah **solusi primer**; proxy sistem hanya kenyamanan tambahan.

## 5. Desain Mode TUN (WAJIB DIPAHAMI SEBELUM MENYENTUH)

- Interface `nusatun0`, alamat `198.18.0.1/15`, gateway `198.18.0.2`, MTU 9000
  (198.18.0.0/15 = range benchmark RFC, tidak dipakai jaringan riil — resep resmi tun2socks)
- Helper root `tunmgr-root.sh` punya 4 subcommand: `up <socks_port> <server_host>`,
  `down`, `status` (JSON satu baris), `watch` (watchdog internal)
- **Urutan `up`**: cek root → snapshot default route v4+v6 ke `/run/nusatunnel-tun/` →
  pasang host-route ke IP server VPN via gateway asli (anti loop Xray→tunnel→Xray) →
  nyalakan tun2socks (via `watch`) → alihkan default route ke TUN
- **Empat lapis pengaman** agar internet user tak pernah rusak:
  1. `down` eksplisit memulihkan rute dari snapshot, PERSIS seperti semula
  2. Watchdog `watch`: tun2socks mati/crash → rute otomatis dipulihkan
  3. GUI startup recovery: TUN tertinggal dari sesi sebelumnya dideteksi & ditawarkan pulih
  4. `prerm`: uninstall selalu mematikan TUN + reset proxy sistem
- Env override **khusus pengujian** (produksi memakai default): `NUSATUNNEL_RUNDIR`,
  `NUSATUNNEL_LOGDIR`, `NUSATUNNEL_SYSNET`, `NUSATUNNEL_PKEXEC`, `NUSATUNNEL_IP`,
  `NUSATUNNEL_TUN2SOCKS`, `NUSATUNNEL_ALLOW_NONROOT`
- Polkit `40-nusatunnel.rules`: sesi desktop lokal (grup sudo/admin/netdev) tak perlu
  password berulang saat pkexec helper

## 6. Konstanta & State Penting

| Item | Nilai |
|---|---|
| SOCKS lokal | `127.0.0.1:10808` |
| HTTP lokal | `127.0.0.1:10809` |
| Interface TUN | `nusatun0` |
| Routing mode | `global` (default) / `bypass-id` |
| Konfigurasi user | `~/.config/nusatunnel/` (settings.json, servers, launcher.log) |
| Backup proxy sistem | `~/.config/nusatunnel/sysproxy_backup.json` |
| State TUN (root) | `/run/nusatunnel-tun/` ; log TUN `/var/log/nusatunnel/` |
| Pidfile xray | `~/.config/nusatunnel/xray.pid` (dibersihkan prerm) |

## 7. Membangun & Merilis

```bash
# 1. Bangun APLIKASI DESKTOP dari sumber (wajib sebelum paket):
cd build/cross/desktop-src && npm run build
#   = tsc --noEmit (typecheck strict) + esbuild (main+preload → ../desktop)
#   + vite (renderer React → ../app/lib/webui: index.html + assets/ + fonts/)

# 2. Bangun SEMUA paket dari sumber yang sama (build/cross/app + build/cross/desktop):
bash scripts/build_deb_220.sh            # → download/nusatunnel_2.2.0_amd64.deb (Electron dibundel)
bash scripts/build_windows_zip_22.sh      # → download/NusaTunnel-2.2.0-Windows-x64.zip
bash scripts/build_macos_zip_22.sh        # → macOS-AppleSilicon.zip + macOS-Intel.zip (.app)

# dpkg-deb dipakai dengan --root-owner-group (tanpa fakeroot);
# zip mempertahankan permission unix (penting utk .app macOS).
# Binary resmi diunduh sekali: scripts/fetch_cross_bins.sh
# (Xray XTLS: windows-64, macos-64, macos-arm64; python.org: embed-amd64)
# dan dist Electron v31.7.7 resmi (4 platform) di build/electron-dl/.

# Uji E2E aplikasi desktop (nyata, via Xvfb — XVFB HARUS SEGAR, restart bila renderer mati):
Xvfb :99 -screen 0 1280x860x24 &
NUSA_E2E=1 NUSA_E2E_CONNECT="Server Uji Lokal" NUSATUNNEL_HOME=/tmp/nt5-home \
  NUSATUNNEL_PORT=17891 DISPLAY=:99 <dir>/desktop/nusatunnel --no-sandbox --disable-gpu
# output: /tmp/render/e2e-*.png + E2E-ROWS (harus "15 | Menampilkan 15 dari 37 server")
# verifikasi visual: node scripts/vlm_check22.js <png> "<prompt>"
```

**Checklist rilis versi baru:**
1. Naikkan `APP_VERSION` di `build/cross/app/lib/platform_util.py` DAN
   `Version:` di `build/deb-src/control` DAN nama file output di 3 skrip build
2. Tambah entri teratas `build/deb-src/changelog.Debian` (format Debian + tanggal RFC)
3. Jalankan SELURUH test suite (§8) — semua harus LULUS
4. Jalankan 3 skrip build; verifikasi md5
5. Perbarui `download/PANDUAN-NusaTunnel.txt` bila ada fitur/CLI baru
6. Perbarui worklog.md + agents.md

## 8. Menjalankan Uji

```bash
cd /home/z/my-project/scripts
python3 test1_parser.py      # parser 4 format link
python3 test2_e2e.py         # e2e v1.x: server Xray lokal NYATA + client + fetch internet
python3 test3_proto.py       # protokol
python3 test4_features.py    # fitur v1.x (sysproxy via stub gsettings, ipcheck, routing)
python3 test5_tun.py         # mode TUN (helper, routing, SOCKS UDP/DNS)
python3 test6_webui.py       # WebUI 2.0: API + connect nyata + keamanan lokal (22 cek)
python3 test7_packages.py    # paket 3 platform: struktur + sambung nyata (43 cek)
bash test_visual_ui.sh       # screenshot UI + verifikasi visual
bash test_gui.sh             # GUI GTK lama v1.x (arsip)
```

Aturan: **tidak ada rilis tanpa seluruh suite LULUS.** Jika menambah fitur, tulis
testN_<nama>.py baru dengan proses nyata, bukan memperluas mock. Untuk uji konektivitas
SOCKS pakai **curl** (urllib tidak mendukung socks5h tanpa PySocks).

## 9. Keunikan Implementasi (JANGAN REGRESI)

1. **geoip.dat full v2fly** — JANGAN ganti dengan geoip.dat rilis Xray (terbukti rusak
   untuk kode us/jp/id, error "EOF")
2. **Trojan**: default TLS HANYA bila field security kosong; hormati `security=none`
3. **Semua fetch HTTP (subscription, ipcheck) via curl** — bukan urllib; path curl
   diselesaikan `platform_util.curl_bin()` (Windows 10+ punya curl bawaan)
4. **pid_alive() Windows WAJIB pakai OpenProcess** — `os.kill(pid, 0)` di Windows
   justru MEMBUNUH proses (TerminateProcess)
5. **Popen Windows**: CREATE_NO_WINDOW (+ DETACHED bila detach); POSIX: start_new_session
6. **WebUI hanya 127.0.0.1 + POST wajib header X-NusaTunnel + validasi Host** —
   jangan pernah longgarkan tanpa pengganti keamanan setara
7. **Modul Linux-only (tunmgr.py, sysproxy.py) TIDAK boleh ikut ke paket Win/Mac**
   (build script sudah menghapusnya; uji B2/C4 menjaga)
8. **sysproxy**: `Gio.Settings.get_child()` utk child schema; verifikasi readback; backup
   dipulihkan saat disconnect/quit/crash
9. **Host-route ke server VPN sebelum mengalihkan default route** — tanpa ini koneksi
   Xray mengulang ke dalam tunnel (loop)
10. **Launcher Linux** tetap mencari `/usr/bin/python3` absolut (pelajaran pyenv/conda v1.0.2)
11. **Electron `main.js`**: backend di-spawn dengan `--no-browser`; URL UI diparsing dari
    stdout (port bisa 17500/17501/17510/17999); `before-quit` wajib POST /api/quit dulu
    agar tunnel diputus & sistem dipulihkan SEBELUM kill; tangani event `error` spawn
12. **capturePage() E2E intermiten kosong** — selalu retry saat `img.isEmpty()` (kode
    sudah ada di main.js); validasi PNG hasil dengan PIL sebelum dipakai
13. **Ikon .exe Windows masih ikon Electron bawaan** (ganti ikon exe perlu rcedit+wine,
    tidak tersedia); ikon jendela tetap benar via `BrowserWindow.icon`
14. **AKSI UI WAJIB POST** (pelajaran v2.1 "tidak mau putus"): endpoint POST tanpa body
    (`/api/disconnect`, `/api/latency`, `/api/iprefresh`, `/api/quit`) TIDAK BOLEH
    dikirim GET — `api.ts` memisahkan get()/post(); aksi tanpa body = post dengan `{}`
15. **`core.py stop()` urutan WAJIB**: bunuh xray DULU di dalam lock (SIGTERM→≤3dtk→
    SIGKILL) → baru teardown TUN/sysproxy TANPA menahan lock (timeout 20 dtk) —
    polling /api/state tidak boleh pernah diblokir pkexec
16. **Bunuh PID hanya setelah cek `/proc/<pid>/comm` = xray** (anti PID-reuse)
17. **Renderer React di-build Vite ke `app/lib/webui`** — hasil build (index.*.js/css
    ber-hash) JANGAN diedit manual; sumbernya `build/cross/desktop-src/src/renderer`;
    server statis webui.py hanya melayani ekstensi MIME yang dipetakan

## 10. Batasan Diketahui & Roadmap

| Status | Item |
|---|---|
| Batasan | Proxy sistem otomatis hanya GNOME/Cinnamon/MATE (Linux); XFCE/KDE belum |
| Batasan | Mode TUN hanya Linux (Win perlu wintun+route berbeda; mac perlu pf) - wintun.dll sudah disertakan di paket Windows sebagai bekal |
| Batasan | Paket Windows/macOS belum ditandatangani (SmartScreen/Gatekeeper minta konfirmasi) |
| Batasan | Paket Win/Mac diuji struktur + kode lintas-platform-nya, TIDAK bisa diuji runtime di sandbox Linux - laporkan bila ada galat lapangan |
| Roadmap | Mode TUN Windows (wintun + tun2socks.exe) & macOS |
| Roadmap | Dukungan sysproxy XFCE (xfconf) & KDE (kwriteconfig5) |
| Roadmap | Tray icon & autostart opsional; split routing per-applikasi; arm64 Linux |

## 11. Riwayat Singkat (detail lengkap: baca `worklog.md`)

| Versi | Tanggal | Inti perubahan |
|---|---|---|
| 1.0.0 | 20 Nov 2026 | Rilis awal: GUI+tray, 4 protokol, subscription, CLI |
| 1.0.1 | 21 Nov 2026 | Auto-install dependensi via pkexec + zenity |
| 1.0.2 | 21 Nov 2026 | Interpreter Python sistem absolut (perbaiki pyenv/conda) |
| 1.1.0 | 21 Nov 2026 | Proxy sistem otomatis + routing global default + label IP keluar |
| 1.1.1 | 21 Nov 2026 | Perbaikan kritis schema gsettings yang gagal senyap |
| 1.2.0 | 21 Nov 2026 | **MODE TUN** — VPN level sistem via tun2socks; tuntas "speedtest tidak berubah" |
| 2.0.0 | 21 Nov 2026 | **Lintas platform (Win/mac) + UI web modern beranimasi, tanpa dep GTK** |
| 2.1.0 | 21 Nov 2026 | **Aplikasi desktop Electron + liquid glass + paginasi 15/potong** |
| 2.2.0 | 21 Nov 2026 | **React + TypeScript strict (ala VSCode) + liquid glass gelap; perbaikan kritis "tidak mau putus" (aksi wajib POST + stop() tanpa lock saat pkexec)** |
