/* ============================================================
   NusaTunnel 2.2 — proses utama Electron (TypeScript)
   Arsitektur ala VSCode: main (Node) + preload (jembatan kecil)
   + renderer (React, dimuat dari backend Python di 127.0.0.1).
   - Menjalankan backend Python (main.pyw --no-browser) sebagai child
   - Jendela frameless + titlebar kustom; vibrancy di macOS
   - Tray, single-instance, cleanup backend saat keluar
   - Mode E2E: NUSA_E2E=1 -> screenshot otomatis semua view
   ============================================================ */
import {
  app, BrowserWindow, Tray, Menu, ipcMain, nativeImage, shell,
} from "electron";
import { spawn, type ChildProcess } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import http from "node:http";
import path from "node:path";

const APP_ROOT = path.resolve(__dirname, "..");       // .../desktop
let win: BrowserWindow | null = null;
let tray: Tray | null = null;
let backend: ChildProcess | null = null;
let backendUrl = "";
let quitting = false;

/* ---------- lokasi backend per platform ---------- */
interface BackendInfo { base: string; script: string; py: string }

function findBackend(): BackendInfo | null {
  if (process.env.NUSATUNNEL_BACKEND) {
    const base = process.env.NUSATUNNEL_BACKEND;
    const py = process.env.NUSATUNNEL_PYTHON ||
      (process.platform === "win32" ? "python" : "/usr/bin/python3");
    for (const rel of ["main.pyw", "app/main.pyw"]) {
      const script = path.join(base, rel);
      if (existsSync(script)) return { base, script, py };
    }
  }
  const here = APP_ROOT;
  const candidates: Array<Partial<BackendInfo> & { base: string }> = [
    // Linux .deb: /usr/lib/nusatunnel/{main.pyw,lib,...} + desktop/
    { base: path.resolve(here, "..", ".."), script: "main.pyw", py: "/usr/bin/python3" },
    // Win/Mac portable: <root>/backend/ atau <Resources>/backend/
    { base: path.join(here, "backend"), winPy: "runtime/pythonw.exe", script: "app/main.pyw" },
    { base: path.join(here, "..", "backend"), winPy: "runtime/pythonw.exe", script: "app/main.pyw" },
  ];
  for (const c of candidates) {
    const script = c.script ? path.join(c.base, c.script) : null;
    if (!script || !existsSync(script)) continue;
    let py = c.py ?? null;
    if (!py && c.winPy) {
      const p = path.join(c.base, c.winPy);
      if (existsSync(p)) py = p;
    }
    if (py) return { base: c.base, script, py };
  }
  return null;
}

/* ---------- jalankan backend & tunggu URL UI ---------- */
function startBackend(): Promise<string> {
  return new Promise((resolve, reject) => {
    const info = findBackend();
    if (!info) return reject(new Error("Backend NusaTunnel tidak ditemukan."));
    let py = info.py || process.env.NUSATUNNEL_PYTHON ||
      (process.platform === "win32" ? "python"
        : process.platform === "darwin" ? "/usr/bin/python3" : "python3");
    if (process.platform === "win32" && py.includes("pythonw")) py = info.py;
    const env = Object.assign({}, process.env, { PYTHONUNBUFFERED: "1" });
    delete (env as Record<string, string | undefined>).PYTHONHOME;
    backend = spawn(py, [info.script, "--no-browser"], {
      cwd: info.base, env, windowsHide: true,
      stdio: ["ignore", "pipe", "pipe"],
    });
    let buf = "";
    const timer = setTimeout(() => {
      if (!backendUrl) reject(new Error("Backend tidak merespons (timeout 25 detik)."));
    }, 25000);
    const onData = (d: Buffer): void => {
      buf += d.toString();
      const m = buf.match(/http:\/\/127\.0\.0\.1:(\d+)/);
      if (m && !backendUrl) {
        backendUrl = `http://127.0.0.1:${m[1]}`;
        clearTimeout(timer);
        resolve(backendUrl);
      }
    };
    backend.stdout?.on("data", onData);
    backend.stderr?.on("data", onData);
    backend.on("error", (e: Error) => {
      if (!backendUrl && !quitting) {
        clearTimeout(timer);
        reject(new Error(`Gagal menjalankan backend (${e.message}).`));
      }
    });
    backend.on("exit", (code: number | null) => {
      if (!backendUrl && !quitting) {
        clearTimeout(timer);
        reject(new Error(`Backend berhenti (kode ${code}).`));
      }
      backend = null;
    });
  });
}

/* ---------- kirim POST ke API backend ---------- */
function apiPost(p: string): Promise<void> {
  return new Promise((resolve) => {
    if (!backendUrl) return resolve();
    const u = new URL(backendUrl + p);
    const req = http.request({
      hostname: "127.0.0.1", port: u.port, path: p, method: "POST",
      headers: { "X-NusaTunnel": "1", "Content-Type": "application/json" },
      timeout: 4000,
    }, (res) => { res.resume(); res.on("end", () => resolve()); });
    req.on("error", () => resolve());
    req.end("{}");
  });
}

/* ---------- jendela utama ---------- */
function iconPath(): string | null {
  for (const p of [path.join(__dirname, "icon.png"), path.join(APP_ROOT, "icon.png")])
    if (existsSync(p)) return p;
  return null;
}

function createWindow(): void {
  const opts: Electron.BrowserWindowConstructorOptions = {
    width: 1220, height: 800, minWidth: 960, minHeight: 640,
    show: false, frame: false, backgroundColor: "#06121a",
    title: "NusaTunnel",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true, nodeIntegration: false, sandbox: false,
    },
  };
  if (process.platform === "darwin") {
    opts.vibrancy = "under-window";
    opts.titleBarStyle = "hiddenInset";
  }
  if (process.platform === "linux") {
    const ic = iconPath();
    if (ic) opts.icon = nativeImage.createFromPath(ic);
  }
  win = new BrowserWindow(opts);
  void win.loadURL(backendUrl);
  win.once("ready-to-show", () => win?.show());
  win.on("maximize", () => win?.webContents.send("win-state", { maximized: true }));
  win.on("unmaximize", () => win?.webContents.send("win-state", { maximized: false }));
  win.on("closed", () => { win = null; });
  // link eksternal -> browser default, bukan jendela app
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith("http://127.0.0.1")) return { action: "allow" };
    void shell.openExternal(url);
    return { action: "deny" };
  });
}

/* ---------- tray ---------- */
function createTray(): void {
  try {
    const ic = iconPath();
    if (!ic) return;
    tray = new Tray(nativeImage.createFromPath(ic).resize({ width: 16, height: 16 }));
    tray.setToolTip("NusaTunnel");
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: "Tampilkan NusaTunnel", click: () => {
          if (!win) return;
          if (win.isMinimized()) win.restore(); else win.show();
          win.focus();
        } },
      { type: "separator" },
      { label: "Keluar", click: () => { quitting = true; app.quit(); } },
    ]));
    tray.on("click", () => {
      if (!win) return;
      if (win.isVisible()) win.hide(); else win.show();
    });
  } catch { /* tray opsional */ }
}

/* ---------- IPC window controls ---------- */
ipcMain.on("win-ctl", (_e, cmd: string) => {
  if (!win) return;
  if (cmd === "minimize") win.minimize();
  else if (cmd === "maximize") win.isMaximized() ? win.unmaximize() : win.maximize();
  else if (cmd === "close") { quitting = true; app.quit(); }
});

/* ---------- mode E2E: screenshot otomatis utk pengujian ---------- */
async function runE2E(): Promise<void> {
  if (!win) return;
  const outDir = process.env.NUSA_E2E_DIR || "/tmp/render";
  mkdirSync(outDir, { recursive: true });
  const errs: string[] = [];
  win.webContents.on("console-message", (_e, _lvl, msg) => {
    if (/error|uncaught/i.test(msg)) errs.push(msg);
  });
  const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
  /* renderer bisa tersendat (restart GPU-less) — coba ulang dengan aman */
  const evalRetry = async (js: string): Promise<unknown> => {
    for (let t = 0; t < 6; t++) {
      try { return await win!.webContents.executeJavaScript(js); }
      catch { await sleep(900); }
    }
    return null;
  };
  const snap = async (name: string, js?: string) => {
    if (js) await evalRetry(js).catch(() => {});
    await sleep(1600);
    let img = await win!.webContents.capturePage();
    for (let t = 0; t < 5 && (img.isEmpty() || img.getSize().width === 0); t++) {
      await sleep(900);
      img = await win!.webContents.capturePage();
    }
    writeFileSync(path.join(outDir, name), img.toPNG());
    console.log("E2E-SNAP", name);
  };
  await sleep(2500);
  // sambungkan tunnel sungguhan via API backend
  if (process.env.NUSA_E2E_CONNECT) {
    await win.webContents.executeJavaScript(
      `fetch('/api/connect', {method:'POST', headers:{'X-NusaTunnel':'1','Content-Type':'application/json'}, body: JSON.stringify({name: ${JSON.stringify(process.env.NUSA_E2E_CONNECT)}})}).then(r=>r.json())`,
    ).catch(() => {});
    await sleep(4000);
  }
  await snap("e2e-home-terhubung.png");
  await snap("e2e-server-hal1.png",
    `document.querySelectorAll('.nav-item')[1].click()`);
  // hitung dulu SELAGI masih di view server (AnimatePresence me-unmount view lain)
  const nSrv = await evalRetry(
    `document.querySelectorAll('.server-list .srv').length`);
  const counter = await evalRetry(
    `document.querySelector('.srv-counter')?.textContent || ''`);
  await snap("e2e-server-hal2.png",
    `document.querySelector('.srv-foot .btn')?.click()`);
  await snap("e2e-pengaturan.png",
    `document.querySelectorAll('.nav-item')[2].click()`);
  console.log("E2E-ROWS", nSrv, "|", counter);
  writeFileSync(path.join(outDir, "e2e-console-errors.txt"),
    errs.join("\n") || "(tidak ada error)");
  quitting = true;
  app.quit();
}

/* ---------- siklus hidup ---------- */
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (win) {
      if (win.isMinimized()) win.restore(); else win.show();
      win.focus();
    }
  });
  app.whenReady().then(async () => {
    try {
      await startBackend();
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      console.error("BACKEND-GAGAL:", msg);
      process.stderr.write(`BACKEND-GAGAL: ${msg}\n`);
      app.exit(1);
      return;
    }
    createWindow();
    createTray();
    if (process.env.NUSA_E2E === "1") void runE2E();
  });
  app.on("window-all-closed", () => { quitting = true; app.quit(); });
  app.on("before-quit", (e) => {
    if (!backend) return;
    e.preventDefault();
    void apiPost("/api/quit").then(() => {
      setTimeout(() => {
        try { backend?.kill("SIGTERM"); } catch { /* abaikan */ }
        setTimeout(() => app.exit(0), 300);
      }, 900);
      backend = null;
    });
  });
  process.on("exit", () => {
    try { backend?.kill("SIGKILL"); } catch { /* abaikan */ }
  });
}
