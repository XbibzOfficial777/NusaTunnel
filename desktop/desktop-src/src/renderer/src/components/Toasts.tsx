/* Toast — slide masuk dari kanan bawah dengan AnimatePresence. */
import { AnimatePresence, motion } from "framer-motion";
import { useApp } from "../appState";

export function Toasts() {
  const { toasts } = useApp();
  return (
    <div className="toasts" aria-live="polite">
      <AnimatePresence>
        {toasts.map((t) => (
          <motion.div
            key={t.id}
            className={`toast ${t.kind}`}
            initial={{ opacity: 0, x: 60, scale: 0.95 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: 40, scale: 0.95 }}
            transition={{ type: "spring", stiffness: 480, damping: 34 }}
          >
            <span className="t-bar" />
            <div dangerouslySetInnerHTML={{ __html: t.html }} />
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
