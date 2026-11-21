package com.nusa.tunnel.core

import android.content.Context
import android.content.Intent
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
    private const val KEY_DEVICE = "xudp_device_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(TunnelStatus())
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    var currentProfile: ServerProfile? = null
        private set

    var socksPort: Int = 10808
        private set

    @Volatile private var statsJob: Job? = null
    @Volatile private var coreStarted = false

    fun log(line: String) {
        Log.d(TAG, line)
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        _logs.value = (_logs.value + "[$stamp] $line").takeLast(400)
    }

    fun clearLogs() { _logs.value = emptyList() }

    fun engineVersion(): String = runCatching { Libv2ray.checkVersionX() }
        .getOrDefault("Xray-core (libxray)")

    /** Harus dipanggil sekali dari Application.onCreate. */
    fun initEngine(context: Context) {
        runCatching {
            Seq.setContext(context.applicationContext)
            val files = context.filesDir.absolutePath
            copyGeoAssets(context, files)
            val deviceId = deviceId(context)
            Libv2ray.initCoreEnv(files, deviceId)
            _status.value = _status.value.copy(engineVersion = engineVersion())
            log("Engine siap: ${engineVersion()}")
        }.onFailure { log("Gagal init engine: ${it.message}") }
    }

    private fun deviceId(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getString(KEY_DEVICE, null) ?: java.util.UUID.randomUUID().toString().also {
            sp.edit().putString(KEY_DEVICE, it).apply()
        }
    }

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
        this.socksPort = socksPort
        currentProfile = profile
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
        _status.value = _status.value.copy(state = TunnelState.Disconnecting)
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
            val config = XrayConfigBuilder.build(profile, socksPort)
            log("Config Xray dibangun (${config.length} byte), memulai core (tunFd=$tunFd)…")
            _status.value = _status.value.copy(state = TunnelState.Connecting)
            context.startService(Intent(context, TunnelVpnService::class.java).apply {
                action = TunnelVpnService.ACTION_START_CORE
                putExtra(TunnelVpnService.EXTRA_CONFIG, config)
                putExtra(TunnelVpnService.EXTRA_TUN_FD, tunFd)
            })
        }
    }

    fun onCoreStarted() {
        coreStarted = true
        _status.value = _status.value.copy(state = TunnelState.Connected)
        log("Tunnel TERHUBUNG — ${currentProfile?.remarks}")
        startStatsPolling()
    }

    fun onCoreError(message: String) {
        coreStarted = false
        _status.value = _status.value.copy(state = TunnelState.Error, error = message)
        log("Error core: $message")
        stopStatsPolling()
    }

    fun onServiceDestroyed() {
        stopStatsPolling()
        coreStarted = false
        if (_status.value.state != TunnelState.Error) {
            _status.value = _status.value.copy(
                state = TunnelState.Idle,
                upBytes = 0, downBytes = 0,
            )
        }
        log("Tunnel terputus.")
    }

    // ---------------------------------------------------------- statistik nyata

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                val stats = readStatsOnce()
                _status.value = _status.value.copy(upBytes = stats.first, downBytes = stats.second)
                delay(1000)
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    /**
     * Baca counter trafik nyata Xray.
     * Format: "tag,direction,value;tag,direction,value;"
     */
    fun readStatsOnce(): Pair<Long, Long> = runCatching {
        val raw = (TunnelVpnService.coreController as? libv2ray.CoreController)
            ?.queryAllOutboundTrafficStats() ?: return 0L to 0L
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
    }.getOrDefault(0L to 0L)

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
