import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "node:path";

/**
 * Renderer NusaTunnel — React + TypeScript.
 * Output build: <proyek>/../app/lib/webui  (disajikan oleh backend
 * Python webui.py di http://127.0.0.1:<port>/ — origin tunggal,
 * tanpa CORS, header CSRF X-NusaTunnel tetap berlaku).
 * base "./" agar aset ditemukan baik via Electron maupun browser.
 */
export default defineConfig({
  plugins: [react()],
  base: "./",
  root: "src/renderer",
  build: {
    outDir: resolve(import.meta.dirname, "../app/lib/webui"),
    emptyOutDir: true,
    target: "chrome120",
    assetsInlineLimit: 0,
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        entryFileNames: "assets/[name].[hash].js",
        chunkFileNames: "assets/[name].[hash].js",
        assetFileNames: "assets/[name].[hash][extname]",
      },
    },
  },
});
