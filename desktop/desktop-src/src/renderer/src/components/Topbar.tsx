/* Topbar — pill status koneksi + pill IP keluar. */
import { useApp } from "../appState";

export function Topbar() {
  const { state, pending } = useApp();
  const conn = !!state?.connected && !state.connecting;
  const busy = !!state?.connecting || pending === "connecting";
  const label = pending === "disconnecting" ? "Memutuskan…"
    : busy ? "Menghubungkan…"
    : conn ? "Terhubung" : "Terputus";

  return (
    <header className="topbar">
      <div className={`status-pill${conn && !busy ? " on" : ""}${busy ? " busy" : ""}`}>
        <span className="dot" />{label}
      </div>
      <div className="ip-pill glass-chip" title="IP keluar saat ini">
        <svg viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="10" />
          <path d="M2 12h20M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20z" />
        </svg>
        <span className="mono">{state?.exit_ip ? state.exit_ip.ip : "IP: -"}</span>
      </div>
    </header>
  );
}
