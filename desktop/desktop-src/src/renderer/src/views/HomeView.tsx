/* View Beranda — hero + tombol daya + kartu IP/TUN/proxy lokal. */
import { motion } from "framer-motion";
import { useApp, esc } from "../appState";
import { api } from "../api";
import { Switch } from "../components/Controls";
import { PowerButton } from "../components/PowerButton";

const reveal = {
  initial: { opacity: 0, y: 22 },
  animate: { opacity: 1, y: 0 },
  transition: { type: "spring" as const, stiffness: 260, damping: 26 },
};

export function HomeView() {
  const { state, pending, refresh, pushToast } = useApp();
  if (!state) return null;
  const busy = state.connecting || pending === "connecting";
  const conn = state.connected && !busy;

  const toggleTun = async (on: boolean) => {
    const r = await api.tun(on);
    if (r.error) pushToast(`<b>TUN gagal:</b> ${esc(r.error)}`, "err", 6500);
    else pushToast(on ? "Mode TUN <b>aktif</b> — seluruh trafik lewat tunnel"
      : "Mode TUN <b>dimatikan</b>", on ? "ok" : "info");
    void refresh();
  };

  const copyEndpoint = (kind: string, port: number) => {
    const val = `${kind === "SOCKS5" ? "socks5" : "http"}://127.0.0.1:${port}`;
    navigator.clipboard?.writeText(val).then(() => {
      pushToast(`Tersalin: <b>${esc(val)}</b>`, "ok", 2400);
    });
  };

  const ip = state.exit_ip;

  return (
    <div className="view-inner">
      <div className="hero">
        <div className="hero-left">
          <div className="hero-eyebrow">
            {busy ? "Menegosiasikan koneksi" : conn ? "Tunnel aktif" : "Siap menyambung"}
          </div>
          <h1 className="hero-title">
            Jelajahi internet<br /><span className="grad">tanpa batas.</span>
          </h1>
          <p className="hero-sub">
            Tekan tombol daya untuk menghubungkan tunnel.
            Semua koneksi Anda terenkripsi via Xray-core.
          </p>
          <div className="hero-meta">
            <span className={`chip${conn ? " on" : ""}`}>
              <i className="dot" />{state.server ? state.server.name : "Belum ada server"}
            </span>
            <span className="chip">
              Rute: {state.settings.routing_mode === "bypass-id" ? "Bypass Indonesia" : "Global"}
            </span>
          </div>
        </div>
        <div className="hero-right">
          <PowerButton />
        </div>
      </div>

      <div className="grid-3">
        <motion.div className="card glass" {...reveal} transition={{ ...reveal.transition, delay: 0.05 }}>
          <div className="card-head">
            <h3>IP Keluar</h3>
            <button className="icon-btn" title="Segarkan IP"
              onClick={async () => {
                await api.ipRefresh();
                pushToast("Memeriksa IP keluar...", "info", 2200);
                setTimeout(() => void refresh(), 1500);
              }}>
              <svg viewBox="0 0 24 24"><path d="M21 12a9 9 0 1 1-3-6.7L21 8" /><path d="M21 3v5h-5" /></svg>
            </button>
          </div>
          <div className={`ip-stat${conn ? "" : " dim"}`}>
            <div className="lbl">Alamat IP Tunnel</div>
            <div className={`ip-big mono${busy && !ip ? " loading" : ""}`}>{ip ? ip.ip : "-"}</div>
            <div className="ip-meta">
              {ip
                ? <><b>{ip.isp}</b><br />{ip.city ? `${ip.city}, ` : ""}{ip.country}</>
                : "Sambungkan tunnel untuk melihat IP keluar"}
            </div>
          </div>
        </motion.div>

        <motion.div className={`card glass${state.features.tun ? "" : " tun-off"}`}
          {...reveal} transition={{ ...reveal.transition, delay: 0.12 }}>
          <div className="card-head">
            <h3>Mode TUN</h3>
            <Switch on={state.tun.active} disabled={!state.features.tun}
              onChange={() => void toggleTun(!state.tun.active)} />
          </div>
          <p className="card-desc">
            {state.features.tun
              ? "VPN level sistem: seluruh trafik perangkat (termasuk speedtest & game) dipaksa lewat tunnel."
              : "Hanya tersedia di Linux."}
          </p>
          <div className={`tun-state${state.tun.active ? " on" : ""}`}>
            <span className="dot" />
            {state.tun.active ? "AKTIF — semua trafik lewat tunnel" : "Nonaktif"}
          </div>
        </motion.div>

        <motion.div className="card glass" {...reveal} transition={{ ...reveal.transition, delay: 0.19 }}>
          <div className="card-head"><h3>Proxy Lokal</h3></div>
          <button className="endpoint glass-chip" onClick={() => copyEndpoint("SOCKS5", state.settings.socks_port)}>
            <small>SOCKS5</small>
            <b className="mono">127.0.0.1:{state.settings.socks_port}</b>
            <svg className="cp" viewBox="0 0 24 24">
              <rect x="9" y="9" width="12" height="12" rx="2" /><path d="M5 15V5a2 2 0 0 1 2-2h10" />
            </svg>
          </button>
          <button className="endpoint glass-chip" onClick={() => copyEndpoint("HTTP", state.settings.http_port)}>
            <small>HTTP</small>
            <b className="mono">127.0.0.1:{state.settings.http_port}</b>
            <svg className="cp" viewBox="0 0 24 24">
              <rect x="9" y="9" width="12" height="12" rx="2" /><path d="M5 15V5a2 2 0 0 1 2-2h10" />
            </svg>
          </button>
          <p className="card-desc muted-small">Klik untuk menyalin. Aplikasi apa pun bisa memakai proxy ini secara manual.</p>
        </motion.div>
      </div>
    </div>
  );
}
