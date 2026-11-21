/* App — layout shell + perpindahan view beranimasi (AnimatePresence). */
import { AnimatePresence, motion } from "framer-motion";
import { AppProvider, useApp } from "./appState";
import { Titlebar } from "./components/Titlebar";
import { LiquidBackground } from "./components/LiquidBackground";
import { Sidebar } from "./components/Sidebar";
import { Topbar } from "./components/Topbar";
import { Toasts } from "./components/Toasts";
import { HomeView } from "./views/HomeView";
import { ServersView } from "./views/ServersView";
import { SettingsView } from "./views/SettingsView";
import { LogView } from "./views/LogView";

function Shell() {
  const { view, desktop } = useApp();
  return (
    <div className={`app${desktop ? " desktop" : ""}`}>
      <Titlebar title="Tunnel VPN Modern" />
      <div className="app-body">
        <Sidebar />
        <main className="content">
          <Topbar />
          <AnimatePresence mode="wait">
            <motion.div
              key={view}
              className="view"
              initial={{ opacity: 0, y: 16, scale: 0.995 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -10, scale: 0.997 }}
              transition={{ duration: 0.22, ease: [0.32, 0.72, 0, 1] }}
            >
              {view === "home" && <HomeView />}
              {view === "servers" && <ServersView />}
              {view === "settings" && <SettingsView />}
              {view === "log" && <LogView />}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
      <Toasts />
    </div>
  );
}

export default function App() {
  return (
    <AppProvider>
      <LiquidBackground />
      <Shell />
    </AppProvider>
  );
}
