/* View Server — toolbar, subscription bar, daftar + paginasi, modal impor. */
import { useState } from "react";
import { ServerList } from "../components/ServerList";
import { ImportModal } from "../components/ImportModal";
import { useApp } from "../appState";
import { api } from "../api";

export function ServersView() {
  const { state, pushToast, refresh } = useApp();
  const [modal, setModal] = useState(false);
  const [subUrl, setSubUrl] = useState<string | null>(null);   // null = sinkron dgn server

  const url = subUrl ?? state?.settings.subscription_url ?? "";
  const subStatus = state?.ui.sub_last_result ?? "";

  const updateSub = async () => {
    if (!/^https?:\/\//.test(url.trim())) {
      pushToast("URL harus diawali http:// atau https://", "err");
      return;
    }
    const r = await api.subscription(url.trim());
    if (r.error) pushToast(`<b>Gagal:</b> ${r.error}`, "err", 6000);
    else pushToast("Subscription diperbarui di latar belakang...", "info");
    setTimeout(() => void refresh(), 1200);
    setTimeout(() => void refresh(), 3500);
  };

  return (
    <div className="view-inner">
      <div className="subbar glass reveal">
        <svg className="sub-ico" viewBox="0 0 24 24">
          <path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7" />
          <path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7" />
        </svg>
        <input
          type="url"
          value={url}
          onChange={(e) => setSubUrl(e.target.value)}
          placeholder="https://contoh.com/subscription (base64 atau teks)"
        />
        <button className="btn accent" onClick={updateSub} disabled={state?.ui.sub_running}>
          {state?.ui.sub_running ? "Memperbarui…" : "Perbarui"}
        </button>
        <span className={`sub-status${subStatus.startsWith("Gagal") ? " err" : ""}`}>
          {subStatus}
        </span>
      </div>

      <div className="toolbar-row reveal">
        <button className="btn primary" onClick={() => setModal(true)}>
          <svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" /></svg>
          Impor Link
        </button>
      </div>

      <ServerList />

      <ImportModal open={modal} onClose={() => setModal(false)} />
    </div>
  );
}
