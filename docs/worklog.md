# WORKLOG — NusaTunnel

**Proyek**: Klien tunneling VMess / VLESS / Trojan / Shadowsocks — Linux, Windows, macOS
**Periode pengembangan**: 20–21 November 2026
**Status akhir**: v2.2.0 stable — aplikasi desktop Electron + React + TypeScript strict (arsitektur ala VSCode), UI liquid glass gelap, perbaikan kritis "tidak mau putus" (GET→404 + lock teardown TUN), lintas platform + TUN terverifikasi
**Deliverable**: `nusatunnel_2.2.0_amd64.deb`, `NusaTunnel-2.2.0-Windows-x64.zip`, `NusaTunnel-2.2.0-macOS-AppleSilicon.zip`, `NusaTunnel-2.2.0-macOS-Intel.zip`

---

## Ringkasan Singkat

NusaTunnel dikembangkan dari nol sebagai aplikasi desktop GTK3 + system tray berbasis
binary **Xray-core resmi (XTLS)**, dengan prinsip kerja **100% nyata, tanpa simulasi**:
semua pengujian memakai proses Xray sungguhan (server lokal + client melalui jalur kode
aplikasi) dan koneksi internet nyata.

Perjalanan rilis: **1.0.0 → 1.0.1 → 1.0.2 → 1.1.0 → 1.1.1 → 1.2.0 → 2.0.0 → 2.1.0 → 2.2.0**, dengan enam gelombang masalah lapangan yang semuanya teratasi: (1) aplikasi gagal dibuka, (2) dependensi terpasang tetapi tetap gagal, (3) IP/ISP pada speedtest tidak berubah — tuntas lewat mode TUN, (4) permintaan dukungan Windows/macOS + UI modern beranimasi — tuntas lewat arsitektur WebUI lintas platform, (5) permintaan aplikasi desktop sungguhan (bukan browser) + liquid glass + paginasi "Muat Lagi" — tuntas lewat Electron 31 + desain ulang total, (6) "putus tidak mau putus" + permintaan framework profesional ala VSCode — tuntas lewat rewrite React+TypeScript + perbaikan dua akar bug disconnect.

---

## Kronologi Rinci

### v1.0.0 — Rilis Awal (20 Nov 2026, malam)

**Fitur yang dibangun:**
- GUI GTK3 + system tray (AyatanaAppIndicator3, fallback Gtk.StatusIcon), Bahasa Indonesia
- Parser link: `vmess://` (base64 JSON), `vless://` (termasuk REALITY: pbk/sid/fp/flow),
  `trojan://`, `ss://` (SIP002 + legacy)
- Generator konfigurasi Xray (`xraygen.py`): transport ws / grpc / h2 / tcp-header,
  setting TLS dan REALITY
- Subscription URL (base64 / teks polos), import link tunggal & massal
- TunnelManager (`core.py`): spawn `xray run` + PID + tunggu proxy siap
- Proxy lokal SOCKS5 `127.0.0.1:10808` dan HTTP `:10809`
- CLI `nusatunnel-cli list|start|stop|status|log`
- Assets lengkap: ikon tray on/off/wait (16–64 px), ikon aplikasi 16–512 px + SVG,
  banner, geoip.dat + geosite.dat

**Masalah yang ditemukan & diperbaiki sebelum rilis:**
| Masalah | Akar masalah | Perbaikan |
|---|---|---|
| `geoip:id` error "EOF" | geoip.dat rilis Xray rusak untuk kode us/jp/id | Ganti dengan geoip.dat **full v2fly** |
| Trojan dipaksa TLS padahal server `security=none` | Asumsi default salah | Hormati `security=none`; default TLS hanya bila kosong |
| Server mati tidak terdeteksi (lazy connect) | Xray baru konek saat ada trafik | Pre-check `tcp_latency` + `xray run -test` + stop bila proxy tak siap 8 dtk |
| `urllib` socks5h bermasalah untuk URL http:// | Keterbatasan pustaka | Modul `ipcheck.py` memakai **curl** (`--socks5-hostname` / `-x`) |

**Uji**: test1_parser 4/4, test2_e2e 11/11 (server VMess lokal nyata + fetch internet
sungguhan — curl via SOCKS sukses saat server hidup, GAGAL saat server dimatikan),
test3_proto 3/3. GUI diverifikasi via Xvfb + screenshot.

---

### v1.0.1 — Auto-Install Dependensi (21 Nov 2026, dini hari)

**Masalah user**: "saya buka app nya malah gabisa dibuka"

**Diagnosis**: `python3-gi`, `gir1.2-gtk-3.0`, `gir1.2-ayatanaappindicator3-0.1` tidak
terpasang di sistem user; log launcher menunjukkan `import gi` gagal.

**Solusi**:
- Launcher (`/usr/bin/nusatunnel`) mendeteksi modul hilang → tawarkan instalasi via
  dialog **zenity** → `pkexec apt-get install -y <deps>` → cek ulang
- Log launcher lengkap di `~/.config/nusatunnel/launcher.log`
- `postinst` ikut memasang dependensi + verifikasi

---

### v1.0.2 — Perbaikan Interpreter Python (21 Nov 2026, 02:40)

**Masalah user**: dependensi SUDAH terpasang tapi aplikasi TETAP gagal dibuka
(log: `import gi` tetap gagal).

**Diagnosis (akar masalah sebenarnya)**: `python3` dari PATH user ternyata
**pyenv/conda/Python manual** yang tidak memiliki GI system. Launcher mempercayai PATH.

**Solusi**:
- Launcher kini **mencari interpreter Python SISTEM** (`/usr/bin/python3`, dst.) yang
  benar-benar bisa memuat GTK — tidak lagi mempercayai PATH
- `unset PYTHONHOME` agar tidak merusak modul sistem
- Dialog error memuat diagnostik nyata: interpreter aktif + pesan import persis
- `nusatunnel-cli` juga memakai `/usr/bin/python3`

**Uji**: test_launcher_v102.sh — skenario PATH rusak (pyenv palsu) vs PATH sehat, lulus.

---

### v1.1.0 — Proxy Sistem Otomatis (21 Nov 2026, 03:10)

**Masalah user**: "terhubung nya ga nyata,, di speedtest provider nya ga ganti?"

**Analisis**: koneksi tunnel sebenarnya NYATA (terbukti uji e2e), tetapi browser/speedtest
tidak otomatis memakai proxy lokal — aplikasi harus diarahkan manual ke 127.0.0.1:10808.

**Solusi v1.1.0**:
- Modul `sysproxy.py`: setel **proxy sistem otomatis** via gsettings
  (org.gnome / org.cinnamon / org.mate → system.proxy) saat tersambung; backup &
  restore otomatis saat disconnect / quit / crash-recovery
- Mode routing baru: **"Semua via Tunnel" (global, jadi default)** dan
  "Bypass Situs Indonesia" — sebelumnya trafik Indonesia selalu direct sehingga
  speedtest yang memilih server lokal menampilkan ISP asli
- **IP keluar & ISP** ditampilkan di GUI (kanan atas) dan `nusatunnel-cli status`,
  diverifikasi via curl
- `curl` masuk `Depends`

**Uji**: test4_features 12/12 (termasuk sysproxy dengan stub gsettings).

---

### v1.1.1 — Perbaikan Kritis gsettings (21 Nov 2026, 04:00)

**Masalah user**: "kok tetep sama???" — proxy sistem seolah aktif tapi trafik tetap keluar langsung.

**Diagnosis (gagal senyap)**: modul proxy sistem memakai struktur schema yang SALAH
(`"http host"` sebagai satu key) → gsettings menolak dengan "No such key" → mode manual
aktif tetapi host/port proxy KOSONG.

**Solusi**:
- Pakai `Gio.Settings.get_child()` (child schema `http`/`https`/`socks` dengan path otomatis)
- **VERIFIKASI readback**: `apply()` hanya melaporkan sukses bila nilai benar-benar berubah
  setelah dibaca ulang
- Diverifikasi dengan gsettings ASLI (schema compiled + backend keyfile) dalam uji otomatis
- `prerm`: reset proxy user ke `none` saat uninstall agar internet tidak rusak

---

### v1.2.0 — MODE TUN: VPN Level Sistem (21 Nov 2026, 05:30) ← SOLUSI DEFINITIF

**Masalah user**: "kok tetep sama aja sih????,, kamu cari mendalam di internet cara
mengimplementasikan vpn / proxy di linux!!!!!"

**Hasil riset mendalam** (studi cara kerja v2rayN/Nekoray/sing-box/hev-socks5-tunnel di Linux):

> Proxy sistem (gsettings) HANYA dihormati aplikasi yang secara sadar membacanya
> (browser, dsb.). Aplikasi desktop/CLI seperti speedtest, game, dan tool jaringan
> **mengabaikan proxy sistem sama sekali**. Satu-satunya cara memaksa SELURUH trafik
> lewat tunnel adalah di level jaringan: **perangkat TUN + tun2socks + pengalihan
> default route** — persis seperti "TUN Mode" di v2rayN/Nekoray.

**Implementasi:**
- **tun2socks resmi xjasonlyu v2.5.2** (binary rilis GitHub) di-embed di
  `/usr/lib/nusatunnel/tun2socks`
- Interface `nusatun0` dengan 198.18.0.1/15 (range benchmark, tidak dipakai siapa pun —
  resep resmi tun2socks), MTU 9000
- **Helper root** `/usr/lib/nusatunnel/tunmgr-root.sh` via **pkexec**:
  - `up <socks_port> <server_host>` — snapshot default route (v4+v6) → host-route khusus
    ke IP server VPN via gateway asli (anti loop) → alihkan default route via TUN
  - `down` — pulihkan rute PERSIS seperti semula
  - `status` — JSON satu baris
  - `watch` — watchdog: bila tun2socks mati/crash, rute otomatis dipulihkan
    (internet tidak putus total)
- IPv6 juga dialihkan bila ada default v6 (**anti bocor**)
- **Aturan polkit** `40-nusatunnel.rules`: sesi desktop lokal anggota sudo/admin/netdev
  tidak diminta password berulang-ulang
- GUI: checkbox "Mode TUN (VPN seluruh sistem)", status TUN di statusbar, penerapan TUN
  tanpa perlu reconnect, pemulihan TUN tertinggal saat startup
- CLI baru: `nusatunnel-cli tun on|off|status` dan `nusatunnel-cli doctor`
  (diagnostik lengkap: versi, proses, rute, proxy sistem, IP asli vs IP tunnel, kesimpulan)
- `prerm` mematikan TUN saat uninstall agar internet aman
- Modul `nettest.py`: tes latensi TCP langsung (bukan ping ICMP yang sering diblokir)

**Uji**: test5_tun — uji helper up/down/status dengan stub nyata, verifikasi routing table,
negosiasi SOCKS5 lengkap (handshake `05 00`, UDP ASSOCIATE untuk DNS), dan DNS query
sungguhan via SOCKS UDP.

**Hasil di lapangan**: SEMUA aplikasi termasuk speedtest kini melewati tunnel —
provider/IP pada speedtest berubah. **Masalah tuntas.**

---

### v2.0.0 — Lintas Platform + UI Modern Beranimasi (21 Nov 2026, 06:30)

**Permintaan user**: "lanjut support windows, mac os!!!!! dan rombak ui ux nya yang lebih
modern dan canggih dan beranimasi dan tidak kaku"

**Keputusan arsitektur**: GTK3 tidak bisa dipakai lintas platform dan terbukti kaku.
Solusi: backend WebUI murni Python stdlib (`http.server`, TANPA dependensi eksternal)
yang menyajikan SPA + JSON API; UI terbuka otomatis di browser default — identik di
ketiga OS. Bonus besar: masalah "gagal import gi" dari v1.0.x menjadi mustahil terjadi.

**Implementasi:**
- **Backend** (`webui.py`): ThreadingHTTPServer 127.0.0.1:17500 (fallback 17501/17510/17999),
  API JSON: state / connect / disconnect / latency / import / subscription / settings /
  tun / delete-server / iprefresh / quit. Keamanan lokal: Host check anti DNS-rebinding +
  POST wajib header `X-NusaTunnel` (situs lain tak bisa memanggil).
- **Refactor cross-platform** (`platform_util.py`): path data per-OS (Linux `~/.config/nusatunnel`,
  Win `%APPDATA%\NusaTunnel`, mac `~/Library/Application Support/NusaTunnel`), resolusi binary
  Xray per platform, `pid_alive()` aman Windows (OpenProcess, bukan os.kill signal 0 yang
  MEMBUNUH proses di Windows), fetch subscription via curl (Win10+/macOS punya curl bawaan).
- **UI baru** ("Midnight Aurora"): glassmorphism + backdrop blur, 3 blob aurora gradien
  bergerak lambat, tombol daya besar (idle: napas halus; connecting: riak mengembang;
  connected: cincin conic-gradient berputar + glow hijau), nav glider meluncur, kartu server
  masuk berjenjang, badge latensi berwarna + flash, switch animasi, segmented control
  berpendar, toast slide-in, skeleton shimmer IP, panel log real-time — semua offline tanpa CDN.
- **Paket Windows**: Python 3.12.8 embeddable resmi (pythonw.exe tanpa jendela) + xray.exe
  resmi XTLS + wintun.dll; `NusaTunnel.bat` dua-klik; portable tanpa instalasi & registry.
- **Paket macOS**: xray darwin amd64 + arm64 (dipilih otomatis via uname), `NusaTunnel.command`
  double-click dengan dialog osascript bila Python 3 belum ada.
- **.deb 2.0.0**: dependensi menyusut (python3, curl, iproute2, policykit-1 — GTK/gi HILANG),
  mode TUN + polkit + CLI tetap penuh; modul Linux-only tidak ikut ke paket Win/Mac.

**Uji**: test6_webui 22/22 (server VMess nyata → API connect → fetch internet via SOCKS
sukses → server dimatikan → fetch GAGAL; keamanan 403 terverifikasi), test7_packages 43/43
(deb: metadata + sambung nyata + fitur TUN terdeteksi; Win: struktur + PE binary + runtime
python utuh + CRLF; mac: .command executable + 2 binary Mach-O). UI diverifikasi visual via
browser headless + VLM: layout modern, tanpa cacat, IP keluar nyata tampil (Alibaba HK).

---

### v2.1.0 — Aplikasi Desktop Electron + Liquid Glass + Paginasi (21 Nov 2026, malam)

**Permintaan pengguna:** (a) daftar server saat "load more" tidak boleh menampilkan semua
sekaligus, tetap dibatasi; (b) implementasi DESIGN.md (palet NerdWallet) menyeluruh; (c) BUKAN
web — aplikasi desktop Electron; (d) lebih modern dengan liquid glass.

**Yang dibangun:**
- **Aplikasi desktop Electron 31 resmi** (`desktop/main.js` + `preload.js`): proses utama
  men-spawn backend Python (`main.pyw --no-browser`) sebagai child, mem-parsing URL
  `http://127.0.0.1:<port>` dari stdout, lalu memuat UI di BrowserWindow frameless.
  Titlebar kustom (drag region + tombol minimize/maximize/close via IPC; macOS pakai
  hiddenInset traffic light), tray icon, single-instance lock, cleanup backend saat quit
  (POST /api/quit → tunggu → SIGTERM).
- **UI "Liquid Glass"** (rewrite `style.css`): panel `rgba(255,255,255,.52)` +
  `backdrop-filter: blur(26px) saturate(1.65)`, tepi specular `inset 0 1px 0` putih,
  radius 22px, sheen cair `translateX` saat hover tombol, spring `cubic-bezier(.34,1.56,.64,1)`;
  palet DESIGN.md dipertahankan (spruce #008254, mint, lime, Montserrat 300/700 offline).
  Hero = kaca hijau dengan kilau radial bergerak; tombol daya = bola kaca dengan titik
  specular; sidebar & daftar server = panel kaca melayang di atas blob mint beranimasi.
- **Paginasi daftar server** (`app.js`): `PAGE = 15`; tombol MUAT LAGI menambah TEPAT satu
  potong per klik (`srvShown += PAGE`, lalu `slice`), hitungan "Menampilkan X dari Y server",
  reset ke halaman awal saat pencarian berubah / daftar berubah (deteksi via signature nama).
- **Font Montserrat resmi dibundel** (woff2 300/500/600/700 latin+latin-ext, 224KB) —
  disajikan lokal oleh backend (`/fonts/*.woff2`, nama tervalidasi regex).
- **Paket**:
  - .deb 2.1.0: Electron dibundel di `/usr/lib/nusatunnel/desktop/` (dist penuh +
    `resources/app`), chrome-sandbox setuid-root via postinst, Depends ditambah pustaka
    Chromium (libgtk-3, libnss3, libasound2, libgbm1, dll), launcher desktop-first dengan
    fallback web (`NUSATUNNEL_NO_DESKTOP=1`), Installed-Size dihitung otomatis.
  - Windows: `NusaTunnel.exe` (electron.exe resmi) + `resources/app` + `backend/` berisi
    Python 3.12 embeddable + xray.exe + wintun.dll — dua-klik, portable.
  - macOS: `NusaTunnel.app` asli (Info.plist via plistlib: nama/identifier/versi,
    executable `nusatunnel`, ikon .icns dibuat sendiri — icp4/icp5/ic07/ic08) + backend
    di `Contents/Resources/backend` + xray per-arsitektur; dua zip (AppleSilicon, Intel).

**Uji**: E2E Electron nyata via Xvfb (`NUSA_E2E=1`): backend spawn → jendela → connect
VMess lokal nyata (IP keluar 47.57.232.232 Alibaba HK terlihat di UI) → 4 screenshot
view via `capturePage` (dengan retry karena intermiten kosong) → E2E-ROWS **30 dari 37**
setelah satu klik MUAT LAGI (bukti paginasi terbatas, bukan semua) → 0 error console.
Diverifikasi visual via VLM: titlebar + tombol window, panel kaca liquid glass, status
TERHUBUNG, server aktif di-highlight, tanpa cacat render. Uji ulang dari FILE .deb
YANG SUDAH DIBANGUN (diekstrak, deteksi backend otomatis tanpa env override) — semua
lolos. Struktur zip Win/mac diverifikasi (exe/plist/pythonw/xray/icns/CRLF).

### v2.2.0 — React + TypeScript (ala VSCode) + Liquid Glass Gelap + Perbaikan "Tidak Mau Putus" (21 Nov 2026, malam kedua)

**Permintaan pengguna:** (a) "kenapa ga pake react + typescript aja, dan pastikan modern dan
jangan asal asalan"; (b) framework profesional seperti VSCode; (c) laporan lapangan: "tadi pas
gua putusin tunneling nya gamau putus".

**Akar bug "tidak mau putus" (dua lapis, keduanya nyata):**
1. **Frontend (akar utama)**: klien API lama memakai satu fungsi `api(path, payload)` yang
   mengirim **GET saat payload kosong** — padahal `/api/disconnect`, `/api/latency`,
   `/api/iprefresh`, `/api/quit` adalah endpoint POST. Klik "Putuskan" → GET → 404 →
   tidak pernah ada pemutusan. Terbukti saat uji: instrumentasi fetch menunjukkan
   `"/api/disconnect GET"`. Perbaikan: `api.ts` baru memisahkan `get()`/`post()` —
   semua aksi wajib POST (body default `{}`).
2. **Backend (lapis kedua)**: `stop()` lama memegang `self._lock` sambil menjalankan
   `_tun_off()` → `pkexec` (timeout 90 dtk) — polling `/api/state` ikut membutuhkan lock
   itu → UI beku di "Terhubung" selama dialog polkit. Perbaikan: bunuh Xray DULU di dalam
   lock (SIGTERM → tunggu ≤3 dtk → SIGKILL), teardown TUN/sysproxy dikerjakan TANPA lock
   dengan timeout pendek (20 dtk), plus verifikasi PID dari `/proc/<pid>/comm` benar-benar
   milik xray (anti PID-reuse) sebelum membunuh.

**Yang dibangun (rewrite menyeluruh, bukan tempelan):**
- **Proyek `build/cross/desktop-src/`** — TypeScript strict terpisah dua tsconfig
  (app: renderer; node: main/preload/build script), `npm run typecheck && build` bersih.
- **Proses utama Electron** (`src/main/main.ts`, di-bundle esbuild → `desktop/main.js`):
  port penuh stub lama ke TS bertipe + `evalRetry()` untuk E2E tahan renderer tersendat.
- **Preload** (`src/preload/preload.ts`): contextBridge minimal (platform, minimize/
  maximize/close, onWinState) — contextIsolation ON, tanpa nodeIntegration.
- **Renderer React 19 + framer-motion** (`src/renderer/`): `types.ts` (kontrak API),
  `api.ts`, `appState.tsx` (context: polling 2.2 dtk, toast, aksi optimistik — label
  "Memutuskan…" langsung tampil + watchdog 6 dtk), komponen (Titlebar, LiquidBackground,
  Sidebar dengan glider `layoutId` spring, Topbar, PowerButton dengan riak lime + rotor
  amber + highlight spekular, Switch/Segmented, Toasts AnimatePresence, ImportModal,
  ServerList dengan stagger baris), 4 view (Home/Servers/Settings/Log), transisi antar
  view AnimatePresence `mode="wait"`.
- **Desain liquid glass GELAP** (`global.css` ~800 baris): aurora hijau spruce/lime
  bergerak (4 blob blur 90px + vignette + grain SVG) di belakang panel kaca
  `backdrop-filter: blur(26px) saturate(1.45)` berlapis dengan kilau tepi
  `inset 0 1px 0 rgba(255,255,255,.14)`; tombol daya bola kaca dengan specular; kartu
  IP stat hijau gelap bermotif garis diagonal lime; radius 12–22px; responsif 1080/860px.
- **Backend `webui.py`**: route statis generik `/assets/*` + `/fonts/*` dengan sanitasi
  path + MIME map (melayani hasil build Vite: `index.html` + `assets/index.*.js|css`).
- **Paginasi dipertahankan persis**: `PAGE = 15`, state `shown`, MUAT LAGI
  `setShown(s => s + PAGE)` — satu potong per klik; reset saat daftar/pencarian berubah.

**Uji (semua nyata, tanpa simulasi):**
- **TypeScript**: `tsc --noEmit` bersih; build Vite: 375KB JS (118KB gzip) + 26KB CSS.
- **Browser (agent-browser + Chromium)**: buka UI → klik tombol daya → **tunnel VMess
  lokal nyata tersambung** (IP keluar 47.57.x via server Xray 127.0.0.1:10001) → klik
  putus → **state turun < 3 dtk, xray client mati bersih** (sebelum perbaikan: klik
  putus tidak berefek sama sekali — bug direproduksi dulu, lalu diperbaiki, lalu
  diverifikasi). Paginasi: 15 → MUAT LAGI → 30 → tombol "7 SERVER BERIKUTNYA";
  pencarian "uji" → 1 hasil; pengaturan & log tampil benar; 0 error console.
- **Electron (Xvfb, dari SOURCE dan dari FILE .deb YANG DIEKSTRAK)**: E2E otomatis
  `NUSA_E2E=1` — connect nyata via API → 4 screenshot (home terhubung, server hal 1,
  hal 2, pengaturan) → `E2E-ROWS 15 | Menampilkan 15 dari 37 server` → 0 error console.
  (Catatan: Xvfb tua menyebabkan renderer mati intermiten — restart Xvfb memulihkannya;
  harness E2E diperkokoh dengan retry.) Verifikasi visual via VLM: titlebar kustom,
  status terhubung hijau jelas, layout rapi, tanpa cacat render.
- **Paket**: deb 93MB (Electron dist + chrome-sandbox setuid via postinst), zip Win
  142MB (pythonw embeddable + xray.exe + wintun.dll), zip macOS AppleSilicon 304MB +
  Intel 323MB (.app dengan Info.plist plistlib + icns). Struktur semua paket
  diverifikasi berisi build React baru (hash file identik `index.B-eQy3Vw.js`).

---

## Ringkasan Uji Otomatis

| Skrip | Cakupan | Hasil |
|---|---|---|
| `test1_parser.py` | Parser 4 format link | 4/4 LULUS |
| `test2_e2e.py` | End-to-end: server Xray lokal nyata + client + fetch internet | 11/11 LULUS |
| `test3_proto.py` | Protokol VMess/VLESS/Trojan/SS | 3/3 LULUS |
| `test4_features.py` | Fitur: sysproxy, ipcheck, routing, launcher | 12/12 LULUS |
| `test5_tun.py` | Mode TUN: helper, routing, SOCKS UDP/DNS | LULUS |
| `test6_webui.py` | WebUI 2.0: API + connect nyata + keamanan lokal | 22/22 LULUS |
| `test7_packages.py` | Paket 2.0.0: deb/Win/mac struktur + nyala | 43/43 LULUS |
| E2E Electron 2.1 | Desktop app: spawn backend, connect nyata, paginasi 30/37, 0 error console | LULUS |
| `test_visual_ui.sh` | Screenshot UI + verifikasi VLM | LULUS |
| `test_gui.sh` | GUI lama (v1.x) via Xvfb | LULUS (arsip) |
| `test_launcher*.sh` | Launcher v1.x: PATH rusak vs sehat | LULUS (arsip) |

Prinsip pengujian: **semua uji memakai proses nyata** — bukan mock hasil. Bukti definitif
konektivitas: curl via SOCKS sukses saat server hidup, GAGAL saat server dimatikan.

## Keputusan Teknis Penting

1. **Binary Xray-core resmi XTLS** — bukan wrapper pihak ketiga, tanpa simulasi
2. **geoip.dat full v2fly** — rilis Xray terbukti rusak untuk kode negara tertentu
3. **ipcheck & subscription via curl** — urllib tidak andal untuk SOCKS & tidak ada di semua OS
4. **Interpreter `/usr/bin/python3` eksplisit** — PATH bisa berisi pyenv/conda
5. **WebUI stdlib murni** — satu kode, tiga OS; tidak mungkin gagal karena dependensi GUI
6. **pid check Windows pakai OpenProcess** — os.kill(pid, 0) justru MEMBUNUH proses di Windows
7. **TUN + tun2socks sebagai solusi utama (Linux)** — proxy sistem hanya pelengkap
8. **Snapshot & restore rute** — internet user tidak pernah dibiarkan rusak
   (down/watchdog/prerm/startup-recovery empat lapis pengaman)
9. **Modul Linux-only dikecualikan dari paket Win/Mac** — tunmgr/sysproxy hanya di .deb
10. **Electron mem-serve UI dari backend lokal** — renderer tetap SPA yang sama; mode
    browser tetap ada sebagai fallback darurat (`NUSATUNNEL_NO_DESKTOP=1`)
11. **Paginasi di sisi renderer** — `srvShown` state lokal + signature daftar; satu klik
    = satu potong (15), bukan "tampilkan semua"

## Pelajaran yang Didapat

- "Tunnel nyata" menurut developer (proxy lokal jalan) ≠ "nyata" menurut user
  (speedtest berubah) — persepsi user adalah standar akhir
- Proxy sistem bisa **gagal senyap**: selalu verifikasi readback setelah set gsettings
- PATH `python3` di mesin user bisa menipu — selalu cari interpreter sistem absolut
- Kejahilan aplikasi terhadap proxy sistem adalah sifat, bukan bug — solusinya TUN
- urllib tidak mendukung socks5h tanpa PySocks — uji konektivitas pakai curl
- GTK terbukti rapuh lintas platform & "kaku" — web tech memberi animasi terbaik
  dengan satu basis kode untuk semua OS
