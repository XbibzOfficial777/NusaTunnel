/* View Log — panel log backend dengan auto-scroll. */
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api";

export function LogView() {
  const [log, setLog] = useState("memuat log…");
  const preRef = useRef<HTMLPreElement>(null);

  const load = useCallback(async () => {
    const r = await api.log();
    setLog(r.log || "(kosong)");
  }, []);

  useEffect(() => {
    void load();
    const t = window.setInterval(() => void load(), 4000);
    return () => window.clearInterval(t);
  }, [load]);

  useEffect(() => {
    const el = preRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [log]);

  return (
    <div className="view-inner">
      <div className="log-panel glass reveal">
        <div className="log-head">
          <b>Log NusaTunnel</b>
          <button className="icon-btn" title="Segarkan" onClick={() => void load()}>
            <svg viewBox="0 0 24 24"><path d="M21 12a9 9 0 1 1-3-6.7L21 8" /><path d="M21 3v5h-5" /></svg>
          </button>
        </div>
        <pre ref={preRef} className="mono">{log}</pre>
      </div>
    </div>
  );
}
