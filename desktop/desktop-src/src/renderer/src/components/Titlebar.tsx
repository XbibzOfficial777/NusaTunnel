/* Titlebar kustom — hanya tampil di jendela Electron (frameless).
   Area drag -webkit-app-region + tombol kontrol jendela. */
import type { MouseEvent } from "react";

interface DesktopApi {
  minimize(): void;
  maximize(): void;
  close(): void;
  platform: string;
}
declare global {
  interface Window { nusaDesktop?: DesktopApi }
}

export function Titlebar({ title }: { title: string }) {
  if (!window.nusaDesktop) return null;
  const mac = window.nusaDesktop.platform === "darwin";
  const ctl = (fn: () => void) => (e: MouseEvent) => {
    e.stopPropagation();
    fn();
  };
  return (
    <header className="titlebar" data-mac={mac || undefined}>
      {!mac && (
        <div className="t-left">
          <span className="t-logo" />
          <b>Nusa<span>Tunnel</span></b>
          <span className="t-title">{title}</span>
        </div>
      )}
      <div className="t-spacer" />
      {!mac && (
        <div className="t-controls">
          <button className="wc" title="Minimalkan" onClick={ctl(() => window.nusaDesktop?.minimize())}>
            <svg viewBox="0 0 12 12"><path d="M1 6h10" /></svg>
          </button>
          <button className="wc" title="Perbesar" onClick={ctl(() => window.nusaDesktop?.maximize())}>
            <svg viewBox="0 0 12 12"><rect x="1.5" y="1.5" width="9" height="9" rx="2" /></svg>
          </button>
          <button className="wc close" title="Keluar" onClick={ctl(() => window.nusaDesktop?.close())}>
            <svg viewBox="0 0 12 12"><path d="M2 2l8 8M10 2l-8 8" /></svg>
          </button>
        </div>
      )}
    </header>
  );
}
