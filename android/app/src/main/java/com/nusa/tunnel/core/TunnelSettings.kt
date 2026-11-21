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
    Error("error", "Essential", "error", "error"),
    Warning("warning", "Normal", "warning", "warn"),
    Verbose("verbose", "Detailed", "debug", "debug");

    companion object {
        fun fromPreference(value: String?): EngineLogLevel =
            entries.firstOrNull { it.preferenceValue == value } ?: Warning
    }
}

/** Settings consumed by Android VpnService and the active Xray/Hev packet path. */
data class VpnBehaviorSettings(
    val primaryDns: String = DEFAULT_PRIMARY_DNS,
    val fallbackDns: String = DEFAULT_FALLBACK_DNS,
    val mtu: Int = DEFAULT_MTU,
) {
    val dnsServers: List<String> get() = listOf(primaryDns, fallbackDns).distinct()

    companion object {
        const val DEFAULT_PRIMARY_DNS = "8.8.8.8"
        const val DEFAULT_FALLBACK_DNS = "1.1.1.1"
        const val DEFAULT_MTU = 1500
        const val MIN_MTU = 1280
        const val MAX_MTU = 9000
    }
}

/** Persisted app settings shared by the Compose UI and the VPN service. */
object TunnelSettings {
    const val DEFAULT_SOCKS_PORT = 10808
    const val MIN_LOG_ENTRY_LIMIT = 100
    const val DEFAULT_LOG_ENTRY_LIMIT = 1_000
    const val MAX_LOG_ENTRY_LIMIT = 10_000

    private const val PREFS = "nusatunnel_prefs"
    private const val KEY_UDP_ENGINE = "udp_tunnel_engine"
    private const val KEY_LOG_LEVEL = "engine_log_level"
    private const val KEY_LOG_RETENTION = "log_retention"
    private const val KEY_AUTO_PING = "auto_ping_selected_server"
    private const val KEY_SOCKS_PORT = "socks_port"
    private const val KEY_SNIFFER_ENABLED = "sniffer_enabled"
    private const val KEY_VPN_PRIMARY_DNS = "vpn_primary_dns"
    private const val KEY_VPN_FALLBACK_DNS = "vpn_fallback_dns"
    private const val KEY_VPN_MTU = "vpn_mtu"
    private const val KEY_SPEED_UNIT = "traffic_speed_unit"
    private const val KEY_VPN_APP_MODE = "vpn_app_routing_mode"
    private const val KEY_VPN_APP_PACKAGES = "vpn_app_selected_packages"
    const val MAX_SELECTED_VPN_APPS = 500

    fun parseSocksPort(value: String): Int? =
        value.trim().toIntOrNull()?.takeIf { it in 1..65_535 }

    fun isValidIpv4Address(value: String): Boolean {
        val parts = value.trim().split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && !(part.length > 1 && part.startsWith('0')) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    fun parseVpnMtu(value: String): Int? =
        value.trim().toIntOrNull()?.takeIf {
            it in VpnBehaviorSettings.MIN_MTU..VpnBehaviorSettings.MAX_MTU
        }

    fun vpnBehavior(context: Context): VpnBehaviorSettings {
        val preferences = prefs(context)
        val primary = preferences.getString(
            KEY_VPN_PRIMARY_DNS,
            VpnBehaviorSettings.DEFAULT_PRIMARY_DNS,
        ).orEmpty().takeIf(::isValidIpv4Address)
            ?: VpnBehaviorSettings.DEFAULT_PRIMARY_DNS
        val fallback = preferences.getString(
            KEY_VPN_FALLBACK_DNS,
            VpnBehaviorSettings.DEFAULT_FALLBACK_DNS,
        ).orEmpty().takeIf(::isValidIpv4Address)
            ?: VpnBehaviorSettings.DEFAULT_FALLBACK_DNS
        val mtu = preferences.getInt(KEY_VPN_MTU, VpnBehaviorSettings.DEFAULT_MTU)
            .takeIf { it in VpnBehaviorSettings.MIN_MTU..VpnBehaviorSettings.MAX_MTU }
            ?: VpnBehaviorSettings.DEFAULT_MTU
        return VpnBehaviorSettings(primary, fallback, mtu)
    }

    /** Rejects invalid DNS/MTU input without changing the previously saved VPN behavior. */
    fun setVpnBehavior(context: Context, settings: VpnBehaviorSettings): Boolean {
        if (!isValidIpv4Address(settings.primaryDns) ||
            !isValidIpv4Address(settings.fallbackDns) ||
            settings.mtu !in VpnBehaviorSettings.MIN_MTU..VpnBehaviorSettings.MAX_MTU
        ) return false
        prefs(context).edit()
            .putString(KEY_VPN_PRIMARY_DNS, settings.primaryDns.trim())
            .putString(KEY_VPN_FALLBACK_DNS, settings.fallbackDns.trim())
            .putInt(KEY_VPN_MTU, settings.mtu)
            .apply()
        return true
    }

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

    fun logEntryLimit(context: Context): Int =
        normalizeLogEntryLimit(
            prefs(context).getInt(KEY_LOG_RETENTION, DEFAULT_LOG_ENTRY_LIMIT),
        )

    fun setLogEntryLimit(context: Context, limit: Int): Boolean {
        val normalized = normalizeLogEntryLimit(limit)
        if (normalized != limit) return false
        prefs(context).edit().putInt(KEY_LOG_RETENTION, normalized).apply()
        return true
    }

    fun parseLogEntryLimit(raw: String): Int? =
        raw.trim().toIntOrNull()?.takeIf { it in MIN_LOG_ENTRY_LIMIT..MAX_LOG_ENTRY_LIMIT }

    private fun normalizeLogEntryLimit(limit: Int): Int =
        limit.coerceIn(MIN_LOG_ENTRY_LIMIT, MAX_LOG_ENTRY_LIMIT)

    fun vpnAppRouting(context: Context): VpnAppRoutingSettings {
        val preferences = prefs(context)
        val mode = VpnAppRoutingMode.fromPreference(
            preferences.getString(KEY_VPN_APP_MODE, null),
        )
        val packages = preferences.getStringSet(KEY_VPN_APP_PACKAGES, emptySet())
            ?.toSet()
            .orEmpty()
        return VpnAppRoutingSettings(mode, packages)
    }

    /** Persists only a bounded, syntactically valid package selection. */
    fun setVpnAppRouting(context: Context, settings: VpnAppRoutingSettings): Boolean {
        if (settings.selectedPackages.size > MAX_SELECTED_VPN_APPS ||
            settings.selectedPackages.any { !VpnAppRoutingPlanner.isValidPackageName(it) } ||
            context.applicationContext.packageName in settings.selectedPackages ||
            (settings.mode == VpnAppRoutingMode.SelectedOnly && settings.selectedPackages.isEmpty())
        ) return false
        prefs(context).edit()
            .putString(KEY_VPN_APP_MODE, settings.mode.preferenceValue)
            .putStringSet(KEY_VPN_APP_PACKAGES, settings.selectedPackages.toSet())
            .apply()
        return true
    }

    fun speedUnit(context: Context): SpeedUnit =
        SpeedUnit.fromPreference(prefs(context).getString(KEY_SPEED_UNIT, null))

    fun setSpeedUnit(context: Context, unit: SpeedUnit) {
        prefs(context).edit().putString(KEY_SPEED_UNIT, unit.preferenceValue).apply()
    }

    fun autoPingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_PING, false)

    fun setAutoPingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_PING, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
