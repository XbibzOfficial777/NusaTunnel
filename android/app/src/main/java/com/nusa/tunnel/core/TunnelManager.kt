package com.nusa.tunnel.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.nusa.tunnel.service.TunnelVpnService
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray

/** Status tunnel yang dibagikan ke UI. */
enum class TunnelState { Idle, Connecting, Connected, Disconnecting, Error }

data class TunnelStatus(
    val state: TunnelState = TunnelState.Idle,
    val serverName: String = "",
    val error: String = "",
    val upBytes: Long = 0L,
    val downBytes: Long = 0L,
    val uploadBytesPerSecond: Long = 0L,
    val downloadBytesPerSecond: Long = 0L,
    val engineVersion: String = "",
)

/**
 * Pengelola inti — SATU sumber kebenaran untuk engine Xray.
 * Semua angka trafik & latency diambil NYATA dari engine, bukan simulasi:
 *  - `queryAllOutboundTrafficStats()`  : counter Xray stats manager
 *  - `measureOutboundDelay()`          : HTTP nyata lewat instance Xray
 */
object TunnelManager {

    private const val TAG = "NusaTunnel"
    private const val PREFS = "nusatunnel_prefs"
    private const val KEY_DEVICE = "xudp_basekey"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var appContext: Context? = null

    private val _status = MutableStateFlow(TunnelStatus())
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private val logStateLock = Any()
    private val logHistory = LogHistoryBuffer(TunnelSettings.DEFAULT_LOG_ENTRY_LIMIT)

    @Volatile var currentProfile: ServerProfile? = null
        private set

    @Volatile var socksPort: Int = TunnelSettings.DEFAULT_SOCKS_PORT
        private set

    @Volatile private var statsJob: Job? = null
    @Volatile private var coreStarted = false

    // queryAllOutboundTrafficStats() returns (and resets) bytes accumulated since
    // the previous query, so keep a separate per-connection total for the UI.
    private val trafficTotals = TrafficStatsAccumulator()

    // Serialize stats queries against stopLoop(); the native stats manager can be
    // torn down while the VPN service is shutting down.
    private val coreApiLock = Any()

    private val connectionCheckMonitor = ConnectionCheckMonitor(
        scope = scope,
        probe = { url, port, timeoutMillis ->
            withContext(Dispatchers.IO) { Socks5HttpProbe.check(url, port, timeoutMillis) }
        },
        onAttempt = { attempt, maxAttempts, result, error ->
            when {
                result?.succeeded == true -> log(
                    "Connection check OK: HTTP ${result.statusCode}, ${result.elapsedMillis} ms",
                )
                result != null -> log(
                    "Connection check gagal: HTTP ${result.statusCode}, attempt $attempt/$maxAttempts",
                )
                else -> log(
                    "Connection check error, attempt $attempt/$maxAttempts: ${error?.message ?: "unknown"}",
                )
            }
        },
        onStopAfterFailure = {
            appContext?.let { context -> disconnect(context) }
                ?: log("Connection check exhausted attempts, but app context is unavailable")
        },
    )

    fun log(line: String) {
        Log.d(TAG, line)
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        synchronized(logStateLock) {
            _logs.value = logHistory.append("[$stamp] $line")
        }
    }

    fun clearLogs() {
        synchronized(logStateLock) {
            _logs.value = logHistory.clear()
        }
    }

    /** Applies a bounded in-memory history limit and trims older entries immediately. */
    fun setLogEntryLimit(limit: Int) {
        synchronized(logStateLock) {
            _logs.value = logHistory.setLimit(limit)
        }
    }

    fun engineVersion(): String = runCatching { Libv2ray.checkVersionX() }
        .getOrDefault("Xray-core (libxray)")

    /** Harus dipanggil sekali dari Application.onCreate. */
    fun initEngine(context: Context) {
        appContext = context.applicationContext
        setLogEntryLimit(TunnelSettings.logEntryLimit(context))
        runCatching {
            Seq.setContext(context.applicationContext)
            val files = context.filesDir.absolutePath
            copyGeoAssets(context, files)
            // xray.xudp.basekey WAJIB base64 URL-safe (tanpa padding) dari
            // tepat 32 byte acak — UUID/string lain ditolak Xray-core:
            // "invalid value (BaseKey must be 32 bytes)" → core init failed.
            Libv2ray.initCoreEnv(files, xudpBaseKey(context))
            _status.value = _status.value.copy(engineVersion = engineVersion())
            log("Engine siap: ${engineVersion()}")
        }.onFailure { log("Gagal init engine: ${it.message}") }
    }

    /** Kunci XUDP persisten: 32 byte acak, base64 URL-safe tanpa padding. */
    private fun xudpBaseKey(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = sp.getString(KEY_DEVICE, null)
        if (existing != null && isValidBaseKey(existing)) return existing
        // regenerasi: key lama (UUID era v2.2.1) tidak valid
        val key = java.security.SecureRandom().let { r ->
            ByteArray(32).also { r.nextBytes(it) }
        }
        val encoded = android.util.Base64.encodeToString(
            key, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        sp.edit().putString(KEY_DEVICE, encoded).apply()
        return encoded
    }

    /** Validasi format persis seperti Xray-core common/xudp: RawURLEncoding, 32 byte. */
    private fun isValidBaseKey(s: String): Boolean = runCatching {
        val bytes = android.util.Base64.decode(
            s, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        bytes.size == 32
    }.getOrDefault(false)

    /** geoip.dat / geosite.dat dari AAR assets → filesDir (harus file nyata utk Xray). */
    private fun copyGeoAssets(context: Context, dir: String) {
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            val out = java.io.File(dir, name)
            if (!out.exists()) {
                runCatching {
                    context.assets.open(name).use { input ->
                        java.io.FileOutputStream(out).use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- start / stop

    fun connect(context: Context, profile: ServerProfile, socksPort: Int) {
        if (_status.value.state == TunnelState.Connecting ||
            _status.value.state == TunnelState.Connected
        ) return
        this.socksPort = socksPort.takeIf { it in 1..65_535 } ?: TunnelSettings.DEFAULT_SOCKS_PORT
        if (this.socksPort != socksPort) {
            log("Port SOCKS tidak valid; memakai port default ${TunnelSettings.DEFAULT_SOCKS_PORT}")
        }
        currentProfile = profile
        trafficTotals.reset()
        _status.value = TunnelStatus(
            state = TunnelState.Connecting,
            serverName = profile.remarks,
            engineVersion = engineVersion(),
        )
        log("Menghubungkan ke ${profile.remarks} (${profile.host}:${profile.port}, ${profile.proto})…")
        val intent = Intent(context, TunnelVpnService::class.java)
        intent.action = TunnelVpnService.ACTION_START
        context.startForegroundService(intent)
    }

    fun disconnect(context: Context) {
        if (_status.value.state == TunnelState.Idle) return
        _status.value = _status.value.copy(
            state = TunnelState.Disconnecting,
            uploadBytesPerSecond = 0L,
            downloadBytesPerSecond = 0L,
        )
        stopStatsPolling()
        connectionCheckMonitor.stop()
        log("Memutuskan tunnel…")
        context.startService(Intent(context, TunnelVpnService::class.java).apply {
            action = TunnelVpnService.ACTION_STOP
        })
    }

    /** Dipanggil TunnelVpnService setelah VPN interface aktif. */
    fun onVpnEstablished(context: Context, tunFd: Int) {
        val profile = currentProfile ?: run {
            _status.value = _status.value.copy(state = TunnelState.Error, error = "Tidak ada server dipilih")
            return
        }
        scope.launch {
            val requestedEngine = TunnelSettings.udpTunnelEngine(context)
            val engine = if (requestedEngine == UdpTunnelEngine.HevSocks5Tunnel && Build.VERSION.SDK_INT < 29) {
                log("HevSocks5Tunnel perlu Android 10+; memakai Xray native TUN pada perangkat ini")
                UdpTunnelEngine.XrayNative
            } else requestedEngine
            val logLevel = TunnelSettings.logLevel(context)
            val vpnBehavior = TunnelSettings.vpnBehavior(context)
            val config = XrayConfigBuilder.build(
                p = profile,
                socksPort = socksPort,
                snifferEnabled = TunnelSettings.snifferEnabled(context),
                includeTunInbound = engine == UdpTunnelEngine.XrayNative,
                logLevel = logLevel.xrayValue,
                mtu = vpnBehavior.mtu,
                dnsServers = vpnBehavior.dnsServers,
            )
            val hevConfig = HevConfigBuilder.build(socksPort, logLevel, vpnBehavior.mtu)
            log("Engine UDP: ${engine.label}; log: ${logLevel.label}; MTU: ${vpnBehavior.mtu}; DNS: ${vpnBehavior.dnsServers.joinToString()}")
            log("Config Xray dibangun (${config.length} byte), memulai core (tunFd=$tunFd)…")
            _status.value = _status.value.copy(state = TunnelState.Connecting)
            context.startService(Intent(context, TunnelVpnService::class.java).apply {
                action = TunnelVpnService.ACTION_START_CORE
                putExtra(TunnelVpnService.EXTRA_CONFIG, config)
                putExtra(TunnelVpnService.EXTRA_TUN_FD, tunFd)
                putExtra(TunnelVpnService.EXTRA_UDP_ENGINE, engine.preferenceValue)
                putExtra(TunnelVpnService.EXTRA_HEV_CONFIG, hevConfig)
            })
        }
    }

    fun onCoreStarted(context: Context) {
        appContext = context.applicationContext
        coreStarted = true
        _status.value = _status.value.copy(state = TunnelState.Connected)
        log("Tunnel TERHUBUNG — ${currentProfile?.remarks}")
        startStatsPolling()
        startConnectionCheckMonitoring(context.applicationContext)
    }

    fun onConnectionCheckSettingsChanged(context: Context, settings: ConnectionCheckSettings) {
        appContext = context.applicationContext
        if (_status.value.state != TunnelState.Connected) {
            connectionCheckMonitor.stop()
            return
        }
        log(
            if (settings.enabled) {
                "Connection check enabled, every ${settings.intervalSeconds}s"
            } else {
                "Connection check disabled"
            },
        )
        connectionCheckMonitor.start(settings, socksPort) {
            _status.value.state == TunnelState.Connected
        }
    }

    private fun startConnectionCheckMonitoring(context: Context) {
        val settings = TunnelSettings.connectionCheck(context)
        if (!settings.enabled) {
            connectionCheckMonitor.stop()
            return
        }
        log("Connection check armed, every ${settings.intervalSeconds}s")
        connectionCheckMonitor.start(settings, socksPort) {
            _status.value.state == TunnelState.Connected
        }
    }

    fun onCoreError(message: String) {
        coreStarted = false
        _status.value = _status.value.copy(
            state = TunnelState.Error,
            error = message,
            uploadBytesPerSecond = 0L,
            downloadBytesPerSecond = 0L,
        )
        log("Error core: $message")
        stopStatsPolling()
        connectionCheckMonitor.stop()
    }

    /** Hentikan polling sebelum VpnService mulai mematikan native core. */
    fun onServiceStopping() {
        stopStatsPolling()
        connectionCheckMonitor.stop()
    }

    fun onServiceDestroyed() {
        stopStatsPolling()
        connectionCheckMonitor.stop()
        coreStarted = false
        trafficTotals.reset()
        if (_status.value.state != TunnelState.Error) {
            _status.value = _status.value.copy(
                state = TunnelState.Idle,
                upBytes = 0L,
                downBytes = 0L,
                uploadBytesPerSecond = 0L,
                downloadBytesPerSecond = 0L,
            )
        } else {
            _status.value = _status.value.copy(
                uploadBytesPerSecond = 0L,
                downloadBytesPerSecond = 0L,
            )
        }
        log("Tunnel terputus.")
    }

    // ---------------------------------------------------------- statistik nyata

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var previousSampleAt = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1_000)
                if (!isActive) break
                val sampleAt = SystemClock.elapsedRealtime()
                val elapsedMillis = (sampleAt - previousSampleAt).coerceAtLeast(1L)
                previousSampleAt = sampleAt
                val delta = readStatsOnce()
                if (!isActive) break
                val total = trafficTotals.add(delta.first, delta.second)
                _status.value = _status.value.copy(
                    upBytes = total.first,
                    downBytes = total.second,
                    uploadBytesPerSecond = TrafficSpeed.bytesPerSecond(delta.first, elapsedMillis),
                    downloadBytesPerSecond = TrafficSpeed.bytesPerSecond(delta.second, elapsedMillis),
                )
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    /**
     * Ambil delta counter trafik Xray sejak polling sebelumnya.
     * queryAllOutboundTrafficStats() me-reset counter setelah dibaca.
     * Format: "tag,direction,value;tag,direction,value;"
     */
    fun readStatsOnce(): Pair<Long, Long> = runCatching {
        synchronized(coreApiLock) {
            val raw = (TunnelVpnService.coreController as? libv2ray.CoreController)
                ?.queryAllOutboundTrafficStats().orEmpty()
            if (raw.isBlank()) {
                0L to 0L
            } else {
                var up = 0L; var down = 0L
                raw.split(";").filter { it.isNotBlank() }.forEach { entry ->
                    val parts = entry.split(",")
                    if (parts.size == 3 && parts[0] == "proxy") {
                        val v = parts[2].toLongOrNull() ?: 0L
                        when (parts[1]) {
                            "uplink" -> up += v
                            "downlink" -> down += v
                        }
                    }
                }
                up to down
            }
        }
    }.getOrDefault(0L to 0L)

    /** Dipakai service agar query stats dan stopLoop tidak berjalan bersamaan. */
    fun stopCoreSafely(controller: libv2ray.CoreController?, clearController: () -> Unit) {
        synchronized(coreApiLock) {
            try {
                controller?.stopLoop()
            } finally {
                // Clear sebelum melepas lock agar polling yang sudah dibatalkan
                // tidak sempat query controller yang baru saja dihentikan.
                clearController()
            }
        }
    }

    /**
     * Ping nyata: bikin config proxy-only lalu HTTP request lewat instance Xray sementara.
     * @return latency ms, atau -1 kalau gagal.
     */
    suspend fun measureLatency(profile: ServerProfile, url: String = "https://www.gstatic.com/generate_204"): Int =
        runCatching {
            val config = XrayConfigBuilder.buildProxyOnly(profile)
            val r = Libv2ray.measureOutboundDelay(config, url)
            r.coerceAtLeast(-1L).toInt()
        }.getOrDefault(-1)

    // ---------------------------------------------------------- callback engine

    val coreCallback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            log("Core: $p1")
            return 0
        }
    }

    /** Format bytes 1,2 GB dst. */
    fun fmtBytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.2f GB".format(b.toDouble() / (1L shl 30))
        b >= 1L shl 20 -> "%.1f MB".format(b.toDouble() / (1L shl 20))
        b >= 1L shl 10 -> "%.1f KB".format(b.toDouble() / (1L shl 10))
        else -> "$b B"
    }
}
