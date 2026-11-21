/* ============================================================
   NusaTunnel 2.2 — state aplikasi (context)
   Polling /api/state + aksi + toast + penghubung Electron.
   ============================================================ */
import {
  createContext, useCallback, useContext, useEffect, useRef, useState,
  type ReactNode,
} from "react";
import { api } from "./api";
import type { AppState, ToastItem, ToastKind, ViewId } from "./types";

const POLL_MS = 2200;

export type Pending = "none" | "connecting" | "disconnecting";

interface AppCtx {
  state: AppState | null;
  pending: Pending;                    // aksi optimistik (UX "memutus...")
  refresh: () => Promise<void>;
  view: ViewId;
  setView: (v: ViewId) => void;
  toasts: ToastItem[];
  pushToast: (html: string, kind?: ToastKind, ms?: number) => void;
  connect: (name: string) => Promise<void>;
  disconnect: () => Promise<void>;
  desktop: boolean;
}

const Ctx = createContext<AppCtx | null>(null);
export const useApp = (): AppCtx => {
  const v = useContext(Ctx);
  if (!v) throw new Error("useApp harus di dalam <AppProvider>");
  return v;
};

let toastSeq = 1;

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AppState | null>(null);
  const [pending, setPending] = useState<Pending>("none");
  const [view, setView] = useState<ViewId>("home");
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const lastErrRef = useRef("");
  const timerRef = useRef<number | null>(null);

  const pushToast = useCallback(
    (html: string, kind: ToastKind = "info", ms = 4200) => {
      const id = toastSeq++;
      setToasts((t) => [...t, { id, kind, html }]);
      window.setTimeout(() => {
        setToasts((t) => t.filter((x) => x.id !== id));
      }, ms);
    }, []);

  const refresh = useCallback(async () => {
    const s = await api.state();
    if (s && !s.error) {
      setState(s);
      // selesai menghubungkan/memutus -> bersihkan status optimistik
      setPending((p) => {
        if (p === "connecting" && (s.connected || s.connect_error)) return "none";
        if (p === "disconnecting" && !s.connected) return "none";
        return p;
      });
      // toast error koneksi (sekali per pesan)
      if (s.connect_error && s.connect_error !== lastErrRef.current) {
        lastErrRef.current = s.connect_error;
        pushToast(`<b>Gagal:</b> ${esc(s.connect_error)}`, "err", 7000);
      } else if (!s.connect_error) {
        lastErrRef.current = "";
      }
    }
  }, [pushToast]);

  /* polling */
  useEffect(() => {
    void refresh();
    timerRef.current = window.setInterval(() => void refresh(), POLL_MS);
    return () => {
      if (timerRef.current) window.clearInterval(timerRef.current);
    };
  }, [refresh]);

  const connect = useCallback(async (name: string) => {
    setPending("connecting");
    pushToast(`Menghubungkan ke <b>${esc(name)}</b>...`, "info", 3000);
    await api.connect(name);
    void refresh();
  }, [pushToast, refresh]);

  const disconnect = useCallback(async () => {
    // optimistik: UI langsung "Memutuskan..." — tidak menunggu backend
    setPending("disconnecting");
    pushToast("Memutuskan tunnel...", "info", 2500);
    await api.disconnect();
    void refresh();
    // watchdog: bila backend lambat, tetap paksa turun setelah 6 dtk
    window.setTimeout(() => setPending((p) =>
      p === "disconnecting" ? "none" : p), 6000);
  }, [pushToast, refresh]);

  return (
    <Ctx.Provider value={{
      state, pending, refresh, view, setView, toasts, pushToast,
      connect, disconnect,
      desktop: typeof window !== "undefined" && !!(window as WinAny).nusaDesktop,
    }}>
      {children}
    </Ctx.Provider>
  );
}

/* ---------- util bersama ---------- */
export function esc(t: unknown): string {
  return String(t ?? "").replace(/[&<>"']/g, (c) => (
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c] as string
  ));
}

export function fmtDur(sec: number): string {
  const s = Math.max(0, sec | 0);
  const h = String(Math.floor(s / 3600)).padStart(2, "0");
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, "0");
  const x = String(s % 60).padStart(2, "0");
  return `${h}:${m}:${x}`;
}

type WinAny = Window & { nusaDesktop?: unknown };
