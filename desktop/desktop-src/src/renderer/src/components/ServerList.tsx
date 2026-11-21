/* Daftar server + paginasi terbatas.
   Aturan (dipertahankan dari 2.1): tampilkan 15 server per potong,
   tombol MUAT LAGI menambah SATU potong per klik — bukan semuanya. */
import { AnimatePresence, motion } from "framer-motion";
import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "../api";
import { useApp, esc } from "../appState";
import type { ServerEntry } from "../types";

export const PAGE = 15;

function LatBadge({ ms }: { ms: number | null }) {
  if (ms === null || ms === undefined) return <span className="lat-badge lat-none">-</span>;
  const cls = ms < 150 ? "lat-ok" : ms < 450 ? "lat-mid" : "lat-bad";
  return <span className={`lat-badge ${cls}`}>{ms} ms</span>;
}

function ServerRow({
  srv, active, index, onConnect, onDelete,
}: {
  srv: ServerEntry;
  active: boolean;
  index: number;
  onConnect: (s: ServerEntry) => void;
  onDelete: (s: ServerEntry) => void;
}) {
  return (
    <motion.div
      className={`srv${active ? " active connected" : ""}`}
      custom={Math.min(index, 14)}
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.98 }}
      transition={{ delay: Math.min(index, 14) * 0.026, type: "spring", stiffness: 380, damping: 30 }}
    >
      <span className={`srv-proto proto-${srv.proto || "vmess"}`}>
        {(srv.proto || "?").toUpperCase()}
      </span>
      <div className="srv-main">
        <div className="srv-name">{srv.name}</div>
        <div className="srv-addr mono">{srv.address}:{srv.port}</div>
      </div>
      <div className="srv-lat">
        <LatBadge ms={srv.latency} />
        {active && <small className="srv-aktif">aktif</small>}
      </div>
      <div className="srv-actions">
        <button className="icon-btn srv-del" title="Hapus server"
          onClick={(e) => { e.stopPropagation(); onDelete(srv); }}>
          <svg viewBox="0 0 24 24">
            <path d="M3 6h18" />
            <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
          </svg>
        </button>
        <button
          className={`btn ${active ? "" : "primary"} srv-conn-btn`}
          onClick={(e) => { e.stopPropagation(); onConnect(srv); }}
        >
          {active ? "Putuskan" : "Hubungkan"}
        </button>
      </div>
    </motion.div>
  );
}

export function ServerList() {
  const { state, connect, disconnect, pushToast, refresh, pending } = useApp();
  const [query, setQuery] = useState("");
  const [shown, setShown] = useState(PAGE);
  const prevIds = useRef("");
  const q = query.toLowerCase();

  const list = useMemo(() => (state?.servers ?? []).filter((x) =>
    !q || (x.name || "").toLowerCase().includes(q) ||
    (x.address || "").toLowerCase().includes(q)), [state?.servers, q]);

  /* daftar berubah (impor/subscription/hapus) -> kembali ke potong awal */
  const ids = (state?.servers ?? []).map((x) => x.name).join("|");
  if (ids !== prevIds.current) { prevIds.current = ids; setShown(PAGE); }
  useEffect(() => { setShown(PAGE); }, [q]);   // pencarian baru -> awal

  if (!state) return <div className="server-list" />;

  const onConnect = (srv: ServerEntry) => {
    const active = state.connected && srv.name === state.settings.last_server;
    if (active || (pending === "disconnecting" && srv.name === state.settings.last_server)) {
      void disconnect();
    } else {
      void connect(srv.name);
    }
  };

  const onDelete = async (srv: ServerEntry) => {
    if (!window.confirm(`Hapus server "${srv.name}"?`)) return;
    await api.deleteServer(srv.name);
    void refresh();
  };

  const total = list.length;
  const visible = list.slice(0, shown);
  const rest = Math.min(PAGE, total - shown);

  return (
    <>
      <div className="toolbar reveal">
        <div className="search glass-chip">
          <svg viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="7" /><path d="m21 21-4-4" />
          </svg>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            type="text" placeholder="Cari server…" autoComplete="off"
          />
        </div>
        <button
          className="btn ghost"
          onClick={async () => {
            await api.latency();
            pushToast("Menguji latensi semua server…", "info", 2500);
            void refresh();
          }}
        >
          <svg viewBox="0 0 24 24"><path d="M13 2 3 14h7l-1 8 10-12h-7l1-8z" /></svg>
          {state.ui.latency_running ? "Menguji…" : "Tes Latensi"}
        </button>
        <span className="toolbar-spacer" />
        <span className="sub-status">{state.ui.sub_last_result}</span>
      </div>

      <div className="server-list">
        {!state.servers.length ? (
          <div className="empty">
            <div className="empty-orb">
              <svg viewBox="0 0 24 24"><path d="M12 3v6m0 0a6 6 0 1 0 0 12 6 6 0 0 0 0-12z" /></svg>
            </div>
            <h3>Belum ada server</h3>
            <p>Impor link vmess:// / vless:// / trojan:// / ss://<br />
              atau tempel URL subscription Anda di bawah.</p>
          </div>
        ) : !total ? (
          <div className="empty">
            <h3>Tidak ada hasil</h3>
            <p>Tidak ada server yang cocok dengan pencarian.</p>
          </div>
        ) : (
          <AnimatePresence mode="popLayout">
            {visible.map((srv, i) => (
              <ServerRow
                key={srv.name}
                srv={srv}
                index={i}
                active={state.connected && srv.name === state.settings.last_server}
                onConnect={onConnect}
                onDelete={onDelete}
              />
            ))}
          </AnimatePresence>
        )}
      </div>

      {total > 0 && (
        <div className="srv-foot">
          <span className="srv-counter">
            Menampilkan <b>{Math.min(shown, total)}</b> dari <b>{total}</b> server
          </span>
          {total > shown && (
            <button className="btn" onClick={() => setShown((s) => s + PAGE)}>
              MUAT LAGI {rest} SERVER BERIKUTNYA
            </button>
          )}
        </div>
      )}
    </>
  );
}

export { esc };
