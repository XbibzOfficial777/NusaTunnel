/* Preload — satu-satunya jembatan renderer <-> Electron.
   contextIsolation aktif; tidak ada modul Node yang bocor ke halaman. */
import { contextBridge, ipcRenderer } from "electron";

const api = {
  platform: process.platform,
  minimize: () => ipcRenderer.send("win-ctl", "minimize"),
  maximize: () => ipcRenderer.send("win-ctl", "maximize"),
  close: () => ipcRenderer.send("win-ctl", "close"),
  /** notifikasi perubahan state jendela (mis. maximize) */
  onWinState: (cb: (s: { maximized: boolean }) => void) => {
    ipcRenderer.on("win-state", (_e, s) => cb(s));
  },
};

contextBridge.exposeInMainWorld("nusaDesktop", api);
export type NusaDesktopApi = typeof api;
