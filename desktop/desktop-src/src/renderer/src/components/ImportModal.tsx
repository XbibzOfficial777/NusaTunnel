/* Modal impor link — glass tebal + animasi spring. */
import { AnimatePresence, motion } from "framer-motion";
import { useState } from "react";
import { api } from "../api";
import { useApp } from "../appState";

export function ImportModal({
  open, onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { pushToast, refresh } = useApp();
  const [text, setText] = useState("");

  const doImport = async () => {
    if (!text.trim()) return;
    const r = await api.import(text);
    if (r.error) pushToast(r.error, "err");
    else {
      pushToast(
        `<b>${r.added ?? 0}</b> server diimpor` +
        (r.errors?.length ? `, ${r.errors.length} link tidak valid` : ""), "ok");
      setText("");
      onClose();
    }
    void refresh();
  };

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="modal-backdrop"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
        >
          <motion.div
            className="modal glass-heavy"
            initial={{ opacity: 0, y: 28, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 18, scale: 0.97 }}
            transition={{ type: "spring", stiffness: 380, damping: 30 }}
          >
            <div className="modal-head">
              <h3>Impor Link Server</h3>
              <button className="icon-btn" onClick={onClose} title="Tutup">
                <svg viewBox="0 0 24 24"><path d="M18 6 6 18M6 6l12 12" /></svg>
              </button>
            </div>
            <p className="card-desc">
              Tempel satu atau banyak link (vmess://, vless://, trojan://, ss://)
              — satu per baris.
            </p>
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              spellCheck={false}
              placeholder={"vmess://eyJ2IjoiMiIs…\ntrojan://password@server.com:443#NamaServer"}
            />
            <div className="modal-actions">
              <button className="btn ghost" onClick={onClose}>Batal</button>
              <button className="btn primary" onClick={doImport}>Impor Sekarang</button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
