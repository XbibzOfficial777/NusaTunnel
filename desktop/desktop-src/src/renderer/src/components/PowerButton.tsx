/* Tombol daya besar — inti visual "liquid glass":
   kaca tebal berlapis, riak cahaya saat terhubung, rotor saat
   menghubungkan. Klik = hubungkan/putus server terakhir. */
import { motion } from "framer-motion";
import { useApp, esc } from "../appState";

export function PowerButton() {
  const { state, pending, connect, disconnect, pushToast, setView } = useApp();
  const busy = !!state?.connecting || pending === "connecting";
  const conn = !!state?.connected && !busy;
  const cutting = pending === "disconnecting";

  const onClick = async () => {
    if (!state) return;
    if (busy) { pushToast("Sedang menghubungkan, tunggu…", "info", 2000); return; }
    if (conn || cutting) { await disconnect(); return; }
    if (!state.servers.length) {
      pushToast("Belum ada server. Buka tab <b>Server</b> untuk impor.", "err");
      setView("servers");
      return;
    }
    const last = state.settings.last_server;
    const name = last && state.servers.some((s) => s.name === last)
      ? last : state.servers[0].name;
    await connect(name);
  };

  return (
    <div className="connect-wrap">
      <motion.button
        className={`connect-btn${conn ? " on" : ""}${busy ? " busy" : ""}${cutting ? " cutting" : ""}`}
        onClick={onClick}
        aria-label="Sambungkan"
        whileTap={{ scale: 0.94 }}
        whileHover={{ scale: 1.03 }}
        transition={{ type: "spring", stiffness: 400, damping: 22 }}
      >
        {conn && <span className="ring-pulse" />}
        {conn && <span className="ring-pulse r2" />}
        {(busy || cutting) && <span className="ring-rotor" />}
        <span className="btn-core">
          <svg viewBox="0 0 24 24">
            <path d="M12 2v9" />
            <path d="M18.4 6.6a9 9 0 1 1-12.8 0" />
          </svg>
        </span>
        <span className="spec-hi" />
      </motion.button>
      <div className={`connect-label${conn ? " on" : ""}${busy || cutting ? " busy" : ""}`}>
        {cutting ? "Memutuskan…" : busy ? "Menghubungkan…"
          : conn ? "Terhubung — tekan untuk memutus"
          : "Tekan untuk terhubung"}
      </div>
      {conn && state?.server && (
        <div className="connect-server">{esc(state.server.name)}</div>
      )}
    </div>
  );
}
