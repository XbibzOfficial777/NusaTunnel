/* ============================================================
   NusaTunnel 2.2 — kontrak tipe (renderer)
   Cermin persis bentuk JSON /api/state dari backend Python.
   ============================================================ */

export interface ServerEntry {
  name: string;
  proto: string;          // vmess | vless | trojan | ss
  address: string;
  port: number;
  latency: number | null;
}

export interface Settings {
  subscription_url: string;
  socks_port: number;
  http_port: number;
  last_server: string;
  routing_mode: "global" | "bypass-id";
  auto_system_proxy: boolean;
  tun_mode: boolean;
}

export interface ExitIp {
  ip: string;
  isp: string;
  country: string;
  city: string;
}

export interface AppInfo {
  name: string;
  version: string;
  platform: string;
  platform_label: string;
  config_dir: string;
  xray_bin: string;
  curl_ok: boolean;
}

export interface Features {
  tun: boolean;
  sysproxy: boolean;
}

export interface TunInfo {
  supported?: boolean;
  active: boolean;
  device: string;
  pid: string;
}

export interface UiInfo {
  latency_running: boolean;
  sub_running: boolean;
  sub_last_result: string;
  uptime: number;
}

export interface AppState {
  app: AppInfo;
  connected: boolean;
  connecting: boolean;
  connect_error: string;
  server: ServerEntry | null;
  connected_since: number;
  settings: Settings;
  servers: ServerEntry[];
  features: Features;
  tun: TunInfo;
  exit_ip: ExitIp | null;
  ui: UiInfo;
}

export interface ImportResult {
  added?: number;
  errors?: string[];
  error?: string;
}

export type ViewId = "home" | "servers" | "settings" | "log";

export type ToastKind = "info" | "ok" | "err";

export interface ToastItem {
  id: number;
  kind: ToastKind;
  html: string;
}
