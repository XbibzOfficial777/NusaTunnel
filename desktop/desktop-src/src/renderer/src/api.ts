/* ============================================================
   NusaTunnel 2.2 — klien API lokal
   Semua POST membawa header X-NusaTunnel: 1 (anti CSRF origin silang).
   PERBAIKAN BUG 2.1 "gamau putus": aksi TANPA body pun wajib POST —
   dulu terkirim sebagai GET dan ditolak 404 oleh backend.
   ============================================================ */
import type { AppState, ImportResult, Settings } from "./types";

const CSRF = { "X-NusaTunnel": "1" } as const;

async function get<T>(path: string): Promise<T> {
  try {
    const r = await fetch(path, { headers: CSRF });
    return (await r.json()) as T;
  } catch (e) {
    return { error: String(e) } as T;
  }
}

async function post<T>(path: string, payload: unknown = {}): Promise<T> {
  try {
    const r = await fetch(path, {
      method: "POST",
      headers: { ...CSRF, "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return (await r.json()) as T;
  } catch (e) {
    return { error: String(e) } as T;
  }
}

export const api = {
  state: () => get<AppState & { error?: string }>("/api/state"),
  log: () => get<{ log: string }>("/api/log"),
  connect: (name: string) => post<{ ok: boolean }>("/api/connect", { name }),
  disconnect: () => post<{ ok: boolean }>("/api/disconnect"),
  latency: () => post<{ ok: boolean }>("/api/latency"),
  import: (text: string) => post<ImportResult>("/api/import", { text }),
  subscription: (url: string) =>
    post<{ ok?: boolean; error?: string }>("/api/subscription", { url }),
  settings: (patch: Partial<Settings>) =>
    post<{ ok?: boolean; warning?: string; error?: string }>("/api/settings", patch),
  tun: (on: boolean) => post<{ ok?: boolean; error?: string }>("/api/tun", { on }),
  deleteServer: (name: string) =>
    post<{ ok?: boolean }>("/api/delete-server", { name }),
  ipRefresh: () => post<{ ok: boolean }>("/api/iprefresh"),
  quit: () => post<{ ok: boolean }>("/api/quit"),
};

export type Api = typeof api;
