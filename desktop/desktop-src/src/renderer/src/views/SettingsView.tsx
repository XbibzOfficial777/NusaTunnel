/* View Pengaturan — routing, integrasi sistem, port, tentang, keluar. */
import { useEffect, useState } from "react";
import { Segmented, Switch } from "../components/Controls";
import { useApp, esc, fmtDur } from "../appState";
import { api } from "../api";

export function SettingsView() {
  const { state, refresh, pushToast, desktop } = useApp();
  const [socks, setSocks] = useState("");
  const [http, setHttp] = useState("");

  useEffect(() => {
    if (state) { setSocks(String(state.settings.socks_port)); setHttp(String(state.settings.http_port)); }
  }, [state?.settings.socks_port, state?.settings.http_port]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!state) return null;
  const s = state.settings;

  const patch = async (p: Parameters<typeof api.settings>[0], msg: string) => {
    const r = await api.settings(p);
    if (r.error) pushToast(r.error, "err");
    else if (r.warning) pushToast(`<b>Perhatian:</b> ${esc(r.warning)}`, "err");
    else if (msg) pushToast(msg, "ok");
    void refresh();
  };

  const quit = async () => {
    if (!window.confirm("Matikan NusaTunnel? Tunnel akan diputus dan pengaturan sistem dipulihkan.")) return;
    if (desktop) { window.nusaDesktop?.close(); return; }
    await api.quit();
    pushToast("NusaTunnel berhenti. Tab ini bisa ditutup.", "ok", 8000);
  };

  return (
    <div className="view-inner narrow">
      <div className="card glass reveal">
        <div className="card-head"><h3>Routing</h3></div>
        <p className="card-desc">Menentukan lalu lintas mana yang lewat tunnel. Berlaku pada sambungan berikutnya.</p>
        <Segmented
          value={s.routing_mode}
          options={[
            { val: "global" as const, label: "Semua via Tunnel" },
            { val: "bypass-id" as const, label: "Bypass Indonesia" },
          ]}
          onChange={(v) => void patch(
            { routing_mode: v },
            `Routing: <b>${v === "bypass-id" ? "Bypass Indonesia" : "Global"}</b>`)}

        />
      </div>

      <div className="card glass reveal d1">
        <div className="card-head"><h3>Integrasi Sistem</h3></div>
        <div className="row-set">
          <div className="row">
            <div>
              <b>Proxy Sistem Otomatis</b>
              <small>Browser &amp; aplikasi desktop otomatis lewat tunnel (Linux)</small>
            </div>
            <Switch on={s.auto_system_proxy} disabled={!state.features.sysproxy}
              onChange={() => void patch({ auto_system_proxy: !s.auto_system_proxy },
                `Proxy sistem otomatis: <b>${s.auto_system_proxy ? "nonaktif" : "aktif"}</b>`)} />
          </div>
          <div className="row">
            <div>
              <b>Mode TUN (VPN Seluruh Sistem)</b>
              <small>Semua aplikasi dipaksa lewat tunnel — butuh otentikasi admin</small>
            </div>
            <Switch on={s.tun_mode} disabled={!state.features.tun}
              onChange={async () => {
                const r = await api.tun(!s.tun_mode);
                if (r.error) pushToast(`<b>TUN gagal:</b> ${esc(r.error)}`, "err", 6500);
                else pushToast(`Mode TUN <b>${!s.tun_mode ? "aktif" : "dimatikan"}</b>`, "ok");
                void refresh();
              }} />
          </div>
        </div>
      </div>

      <div className="card glass reveal d2">
        <div className="card-head"><h3>Port Proxy Lokal</h3></div>
        <div className="ports">
          <label>SOCKS5
            <input type="number" min={1024} max={65535} value={socks}
              onChange={(e) => setSocks(e.target.value)} />
          </label>
          <label>HTTP
            <input type="number" min={1024} max={65535} value={http}
              onChange={(e) => setHttp(e.target.value)} />
          </label>
          <button className="btn accent"
            onClick={() => void patch(
              { socks_port: parseInt(socks, 10), http_port: parseInt(http, 10) },
              "Port disimpan. Berlaku pada sambungan berikutnya.")}>
            Simpan Port
          </button>
        </div>
        <p className="hint">Perubahan port berlaku saat sambungan berikutnya dibuka.</p>
      </div>

      <div className="card glass reveal d3">
        <div className="card-head"><h3>Tentang</h3></div>
        <div className="about mono">
          Versi&nbsp;&nbsp;&nbsp;&nbsp;: NusaTunnel {state.app.version}<br />
          Platform : {state.app.platform_label}<br />
          Mesin&nbsp;&nbsp;&nbsp;&nbsp;: Xray-core ({state.app.xray_bin})<br />
          UI&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: Electron + React + TypeScript<br />
          Data&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: {state.app.config_dir}<br />
          Uptime&nbsp;&nbsp;: {fmtDur(state.ui.uptime)}
        </div>
        <div className="danger-zone">
          <button className="btn danger" onClick={quit}>Matikan NusaTunnel</button>
        </div>
      </div>
    </div>
  );
}
