package com.nusa.tunnel.core

import android.content.Context

/** TUN packet processor. Hev is the requested default; Xray remains a selectable fallback. */
enum class UdpTunnelEngine(val preferenceValue: String, val label: String) {
    HevSocks5Tunnel("hev_socks5", "HevSocks5Tunnel (default)"),
    XrayNative("xray_native", "Xray native TUN (custom UDP)");

    companion object {
        fun fromPreference(value: String?): UdpTunnelEngine =
            entries.firstOrNull { it.preferenceValue == value } ?: HevSocks5Tunnel
    }
}

/** User-facing verbosity choices mapped to the exact log-level vocabulary of each engine. */
enum class EngineLogLevel(
    val preferenceValue: String,
    val label: String,
    val xrayValue: String,
    val hevValue: String,
) {
    Warning("warning", "Warning", "warning", "warn"),
    Verbose("verbose", "Verbose", "debug", "debug"),
    Error("error", "Error", "error", "error");

    companion object {
        fun fromPreference(value: String?): EngineLogLevel =
            entries.firstOrNull { it.preferenceValue == value } ?: Warning
    }
}

/** Number of in-memory log entries retained by the app. */
enum class LogRetention(val preferenceValue: Int, val label: String) {
    Compact(100, "100 baris"),
    Standard(400, "400 baris"),
    Extended(1_000, "1.000 baris");

    companion object {
        fun fromPreference(value: Int): LogRetention =
            entries.firstOrNull { it.preferenceValue == value } ?: Standard
    }
}

/** Persisted app settings shared by the Compose UI and the VPN service. */
object TunnelSettings {
    const val DEFAULT_SOCKS_PORT = 10808

    private const val PREFS = "nusatunnel_prefs"
    private const val KEY_UDP_ENGINE = "udp_tunnel_engine"
    private const val KEY_LOG_LEVEL = "engine_log_level"
    private const val KEY_LOG_RETENTION = "log_retention"
    private const val KEY_AUTO_PING = "auto_ping_selected_server"
    private const val KEY_SOCKS_PORT = "socks_port"
    private const val KEY_SNIFFER_ENABLED = "sniffer_enabled"

    fun parseSocksPort(value: String): Int? =
        value.trim().toIntOrNull()?.takeIf { it in 1..65_535 }

    fun socksPort(context: Context): Int =
        prefs(context).getInt(KEY_SOCKS_PORT, DEFAULT_SOCKS_PORT)
            .takeIf { it in 1..65_535 } ?: DEFAULT_SOCKS_PORT

    /** Returns false and leaves the previous value untouched when the port is invalid. */
    fun setSocksPort(context: Context, port: Int): Boolean {
        if (port !in 1..65_535) return false
        prefs(context).edit().putInt(KEY_SOCKS_PORT, port).apply()
        return true
    }

    fun snifferEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SNIFFER_ENABLED, true)

    fun setSnifferEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SNIFFER_ENABLED, enabled).apply()
    }

    fun udpTunnelEngine(context: Context): UdpTunnelEngine =
        UdpTunnelEngine.fromPreference(prefs(context).getString(KEY_UDP_ENGINE, null))

    fun setUdpTunnelEngine(context: Context, engine: UdpTunnelEngine) {
        prefs(context).edit().putString(KEY_UDP_ENGINE, engine.preferenceValue).apply()
    }

    fun logLevel(context: Context): EngineLogLevel =
        EngineLogLevel.fromPreference(prefs(context).getString(KEY_LOG_LEVEL, null))

    fun setLogLevel(context: Context, level: EngineLogLevel) {
        prefs(context).edit().putString(KEY_LOG_LEVEL, level.preferenceValue).apply()
    }

    fun logRetention(context: Context): LogRetention =
        LogRetention.fromPreference(
            prefs(context).getInt(KEY_LOG_RETENTION, LogRetention.Standard.preferenceValue),
        )

    fun setLogRetention(context: Context, retention: LogRetention) {
        prefs(context).edit().putInt(KEY_LOG_RETENTION, retention.preferenceValue).apply()
    }

    fun autoPingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_PING, false)

    fun setAutoPingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_PING, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
