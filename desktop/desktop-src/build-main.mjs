// Bundle proses utama + preload Electron (TypeScript -> CommonJS).
// Hasil di-build ke ../desktop/ agar bisa dikemas oleh skrip packaging.
import { build } from "esbuild";

const common = {
  bundle: true,
  platform: "node",
  format: "cjs",
  target: "node20",
  external: ["electron"],
  sourcemap: false,
  legalComments: "none",
  minify: false, // tetap terbaca untuk audit
};

await build({
  ...common,
  entryPoints: ["src/main/main.ts"],
  outfile: "../desktop/main.js",
});

await build({
  ...common,
  entryPoints: ["src/preload/preload.ts"],
  outfile: "../desktop/preload.js",
});

console.log("OK: desktop/main.js + desktop/preload.js");
