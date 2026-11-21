/* Sidebar — rail navigasi glass dengan glider framer-motion (layoutId). */
import { motion } from "framer-motion";
import { useApp, fmtDur } from "../appState";
import type { ViewId } from "../types";

const NAV: Array<{ id: ViewId; label: string; icon: JSX.Element }> = [
  {
    id: "home", label: "Beranda",
    icon: <svg viewBox="0 0 24 24"><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /></svg>,
  },
  {
    id: "servers", label: "Server",
    icon: <svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="7" rx="2" /><rect x="3" y="13" width="18" height="7" rx="2" /><path d="M7 7.5h.01M7 16.5h.01" /></svg>,
  },
  {
    id: "settings", label: "Pengaturan",
    icon: <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1 1.55V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1-1.55 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.7 1.7 0 0 0 .34-1.87 1.7 1.7 0 0 0-1.55-1H3a2 2 0 1 1 0-4h.09a1.7 1.7 0 0 0 1.55-1 1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.87.34h0a1.7 1.7 0 0 0 1-1.55V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1 1.55h0a1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.87v0a1.7 1.7 0 0 0 1.55 1H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.55 1z" /></svg>,
  },
  {
    id: "log", label: "Log",
    icon: <svg viewBox="0 0 24 24"><path d="m4 17 6-5-6-5" /><path d="M12 19h8" /></svg>,
  },
];

export function Sidebar() {
  const { state, view, setView } = useApp();
  const conn = !!state?.connected && !state.connecting;
  const busy = !!state?.connecting;

  return (
    <aside className="sidebar glass">
      <div className="brand">
        <div className="brand-logo">
          <svg viewBox="0 0 24 24">
            <path d="M12 3v6m0 0a6 6 0 1 0 0 12 6 6 0 0 0 0-12z" />
          </svg>
        </div>
        <div className="brand-text">
          <div className="brand-name">Nusa<b>Tunnel</b></div>
          <div className="brand-ver">
            {state ? `v${state.app.version} · ${state.app.platform_label}` : "…"}
          </div>
        </div>
      </div>

      <nav className="nav">
        {NAV.map((n) => {
          const active = view === n.id;
          return (
            <button
              key={n.id}
              className={`nav-item${active ? " active" : ""}`}
              onClick={() => setView(n.id)}
            >
              {active && (
                <motion.span
                  layoutId="nav-glider"
                  className="nav-glider"
                  transition={{ type: "spring", stiffness: 520, damping: 38 }}
                />
              )}
              <span className="nav-ico">{n.icon}</span>
              <span className="nav-label">{n.label}</span>
              {n.id === "servers" && state && state.servers.length > 0 && (
                <em className="nav-badge">{state.servers.length}</em>
              )}
            </button>
          );
        })}
      </nav>

      <div className="sidebar-foot">
        <div className={`conn-mini${conn ? " on" : ""}${busy ? " busy" : ""}`}>
          <span className="dot" />
          <div className="conn-mini-text">
            <b>{busy ? "Menghubungkan" : conn ? "Terhubung" : "Terputus"}</b>
            <small>
              {busy ? "Menyiapkan tunnel…"
                : conn ? (state?.server?.name ?? "Tunnel aktif")
                : "Tunnel tidak aktif"}
            </small>
            {conn && state?.connected_since ? (
              <small className="conn-dur mono">
                {fmtDur(Date.now() / 1000 - state.connected_since)}
              </small>
            ) : null}
          </div>
        </div>
      </div>
    </aside>
  );
}
