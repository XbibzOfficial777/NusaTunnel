package com.nusa.tunnel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.nusa.tunnel.MainActivity
import com.nusa.tunnel.R
import com.nusa.tunnel.core.HevConfigBuilder
import com.nusa.tunnel.core.TunnelManager
import com.nusa.tunnel.core.TunnelSettings
import com.nusa.tunnel.core.UdpTunnelEngine
import com.nusa.tunnel.core.VpnAppRoutingPlanner
import com.nusa.tunnel.core.VpnApplicationCatalog
import hev.htproxy.TProxyService
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * VpnService NusaTunnel — SEMUA NYATA:
 *  1. Builder VPN (addr/route/dns/MTU) → establish() → fd TUN
 *  2. Hev (default) consumes fd TUN and forwards to Xray SOCKS; Xray-native remains selectable
 *  3. Both engines stop before the VPN interface is closed
 */
class TunnelVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.nusa.tunnel.START"
        const val ACTION_START_CORE = "com.nusa.tunnel.START_CORE"
        const val ACTION_STOP = "com.nusa.tunnel.STOP"
        const val ACTION_REFRESH_WAKE_LOCK = "com.nusa.tunnel.REFRESH_WAKE_LOCK"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_TUN_FD = "tun_fd"
        const val EXTRA_UDP_ENGINE = "udp_engine"
        const val EXTRA_HEV_CONFIG = "hev_config"

        private const val TAG = "TunnelVpnService"
        private const val CHANNEL_ID = "nusatunnel_status"
        private const val NOTIF_ID = 1

        @Volatile var coreController: CoreController? = null
            private set
        private val starting = AtomicBoolean(false)
    }

    @Volatile private var vpnInterface: ParcelFileDescriptor? = null
    private val engineLifecycleLock = Any()
    private val cleanupStarted = AtomicBoolean(false)
    private val hevMonitor = AtomicBoolean(false)
    @Volatile private var hevStarted = false
    private lateinit var cpuWakeLockController: VpnCpuWakeLockController

    // ---------------------------------------------------------- lifecycle

    override fun onCreate() {
        super.onCreate()
        cpuWakeLockController = VpnCpuWakeLockController(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (cleanupStarted.get()) {
                    TunnelManager.onCoreError("VPN service sedang berhenti; coba lagi sesaat")
                    return START_NOT_STICKY
                }
                startAsForeground()
                if (starting.compareAndSet(false, true)) {
                    Thread { establishVpn() }.start()
                } else {
                    Log.w(TAG, "Start sudah berjalan, abaikan")
                }
            }
            ACTION_START_CORE -> {
                if (cleanupStarted.get()) {
                    Log.i(TAG, "Abaikan startCore karena service sedang berhenti")
                    return START_NOT_STICKY
                }
                val config = intent.getStringExtra(EXTRA_CONFIG) ?: run {
                    TunnelManager.onCoreError("Config kosong")
                    stopSelfClean()
                    return START_NOT_STICKY
                }
                val tunFd = intent.getIntExtra(EXTRA_TUN_FD, 0)
                val engine = UdpTunnelEngine.fromPreference(intent.getStringExtra(EXTRA_UDP_ENGINE))
                val hevConfig = intent.getStringExtra(EXTRA_HEV_CONFIG).orEmpty()
                startCore(config, tunFd, engine, hevConfig)
            }
            ACTION_STOP -> stopSelfClean()
            ACTION_REFRESH_WAKE_LOCK -> {
                if (vpnInterface == null && !starting.get()) stopSelf()
                else applyCpuWakeLock()
            }
            else -> startAsForeground()
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------- VPN builder

    private fun establishVpn() {
        try {
            synchronized(engineLifecycleLock) {
                if (cleanupStarted.get()) return@synchronized
                closeInterface()
                val vpnBehavior = TunnelSettings.vpnBehavior(this)
                val ownerPackageName = this.packageName
                val appRouting = TunnelSettings.vpnAppRouting(this)
                val visiblePackages = VpnApplicationCatalog.launchableApps(this)
                    .mapTo(mutableSetOf()) { it.packageName }
                    .apply { add(ownerPackageName) }
                val appPlan = VpnAppRoutingPlanner.create(
                    settings = appRouting,
                    ownerPackageName = ownerPackageName,
                    installedPackages = visiblePackages,
                )
                TunnelManager.log(
                    "VPN app routing: ${appRouting.mode.label}, " +
                        "${appRouting.selectedPackages.size} saved selection(s)",
                )
                val builder = Builder()
                    .setSession("NusaTunnel")
                    .setMtu(vpnBehavior.mtu)
                    .addAddress("10.10.14.1", 30)
                    .addAddress("fc00::10:10:14:1", 126)
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0)
                    .addDnsServer(vpnBehavior.primaryDns)
                    .apply {
                        if (vpnBehavior.fallbackDns != vpnBehavior.primaryDns) {
                            addDnsServer(vpnBehavior.fallbackDns)
                        }
                    }
                    .apply {
                        if (appPlan.allowedPackages != null) {
                            appPlan.allowedPackages.sorted().forEach { addAllowedApplication(it) }
                        } else {
                            // AndroidLibXrayLite startLoop() does not expose VpnService.protect(fd).
                            // The owner app must never loop its own upstream sockets into the TUN.
                            appPlan.disallowedPackages.sorted().forEach { addDisallowedApplication(it) }
                        }
                    }
                    .setBlocking(true)

                val established = builder.establish()
                if (established == null) {
                    TunnelManager.onCoreError("Izin VPN ditolak / interface gagal dibuat")
                    stopSelfClean()
                    return@synchronized
                }
                vpnInterface = established
                applyCpuWakeLock()
                if (cleanupStarted.get()) {
                    closeInterface()
                    return@synchronized
                }
                Log.i(TAG, "VPN interface aktif, fd=${established.fd}")
                TunnelManager.onVpnEstablished(this, established.fd)
            }
        } catch (e: Exception) {
            Log.e(TAG, "establishVpn gagal", e)
            TunnelManager.onCoreError(e.message ?: "VPN gagal: ${e.javaClass.simpleName}")
            stopSelfClean()
        } finally {
            starting.set(false)
        }
    }

    // ---------------------------------------------------------- core Xray / Hev

    private fun startCore(
        config: String,
        tunFd: Int,
        engine: UdpTunnelEngine,
        hevConfig: String,
    ) {
        Thread {
            synchronized(engineLifecycleLock) {
                if (cleanupStarted.get()) return@synchronized
                var newController: CoreController? = null
                try {
                    // Stop tun2socks before its Xray SOCKS upstream if replacing a core.
                    TunnelManager.onServiceStopping()
                    if (hevStarted) stopHevTunnel()
                    coreController?.let { old ->
                        runCatching {
                            TunnelManager.stopCoreSafely(old) { coreController = null }
                        }.onFailure { Log.w(TAG, "stop core lama: ${it.message}") }
                    }

                    val controller = Libv2ray.newCoreController(TunnelManager.coreCallback)
                    newController = controller
                    // Hev owns the VPN fd; Xray only needs its local SOCKS inbound in this mode.
                    controller.startLoop(
                        config,
                        if (engine == UdpTunnelEngine.HevSocks5Tunnel) 0 else tunFd,
                    )
                    if (!controller.isRunning) {
                        throw IllegalStateException("Core tidak berjalan setelah startLoop")
                    }
                    coreController = controller

                    if (engine == UdpTunnelEngine.HevSocks5Tunnel) {
                        startHevTunnel(hevConfig.ifBlank {
                            HevConfigBuilder.build(
                                tunManagerPort(),
                                TunnelSettings.logLevel(this),
                                TunnelSettings.vpnBehavior(this).mtu,
                            )
                        }, tunFd)
                        startHevMonitor()
                    }
                    if (cleanupStarted.get()) return@synchronized
                    TunnelManager.onCoreStarted()
                } catch (e: Exception) {
                    newController?.takeIf { coreController !== it }?.let { controller ->
                        runCatching { controller.stopLoop() }
                            .onFailure { Log.w(TAG, "cleanup core parsial gagal: ${it.message}") }
                    }
                    Log.e(TAG, "startCore gagal", e)
                    TunnelManager.onCoreError(e.message ?: e.javaClass.simpleName)
                    stopSelfClean()
                }
            }
        }.start()
    }

    private fun tunManagerPort(): Int = TunnelManager.socksPort

    private fun startHevTunnel(config: String, tunFd: Int) {
        val configFile = java.io.File(filesDir, "hev-socks5-tunnel.yaml")
        configFile.writeText(config, Charsets.UTF_8)
        val started = runCatching {
            TProxyService.TProxyStartService(configFile.absolutePath, tunFd)
        }.getOrElse { throw IllegalStateException("Gagal memuat HevSocks5Tunnel: ${it.message}", it) }
        if (!started) throw IllegalStateException("HevSocks5Tunnel menolak start")
        hevStarted = true
        TunnelManager.log("HevSocks5Tunnel menerima TUN fd; SOCKS5 127.0.0.1:${TunnelManager.socksPort}")
        // The JNI API returns after creating its worker thread, not after parsing YAML.
        Thread.sleep(350)
        if (!runCatching { TProxyService.TProxyIsRunning() }.getOrDefault(false)) {
            throw IllegalStateException("HevSocks5Tunnel berhenti saat inisialisasi; periksa log native")
        }
    }

    private fun startHevMonitor() {
        if (!hevMonitor.compareAndSet(false, true)) return
        thread(name = "nusa-hev-health") {
            while (hevMonitor.get()) {
                try {
                    Thread.sleep(1_000)
                } catch (_: InterruptedException) {
                    break
                }
                if (!hevMonitor.get()) break
                val running = runCatching { TProxyService.TProxyIsRunning() }.getOrDefault(false)
                if (!running) {
                    hevMonitor.set(false)
                    TunnelManager.onCoreError("HevSocks5Tunnel berhenti tak terduga")
                    stopSelfClean()
                    break
                }
            }
        }
    }

    private fun stopHevTunnel() {
        hevMonitor.set(false)
        if (!hevStarted) return
        runCatching { TProxyService.TProxyStopService() }
            .onFailure { Log.w(TAG, "HevSocks5Tunnel stop: ${it.message}") }
        hevStarted = false
    }

    // ---------------------------------------------------------- stop (NYATA)

    private fun stopSelfClean() {
        if (!cleanupStarted.compareAndSet(false, true)) return
        releaseCpuWakeLock()
        hevMonitor.set(false)
        // Cancel stats before locking teardown; stopCoreSafely serializes native calls.
        TunnelManager.onServiceStopping()
        thread(name = "nusa-stop") {
            synchronized(engineLifecycleLock) {
                // Stop tun2socks before the Xray SOCKS listener it depends on.
                stopHevTunnel()
                try {
                    coreController?.let { c ->
                        Log.i(TAG, "stopLoop dipanggil…")
                        TunnelManager.stopCoreSafely(c) { coreController = null }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "stopLoop: ${e.message}")
                } finally {
                    coreController = null
                }
                closeInterface()
                stopForeground(STOP_FOREGROUND_REMOVE)
                TunnelManager.onServiceDestroyed()
                stopSelf()
            }
        }
    }

    /** Hold a partial wake lock only while an established VPN interface is active. */
    private fun applyCpuWakeLock() {
        cpuWakeLockController.update(
            preferenceEnabled = TunnelSettings.keepCpuAwake(this),
            vpnInterfaceActive = vpnInterface != null,
            stopping = cleanupStarted.get(),
        )
    }

    private fun releaseCpuWakeLock() {
        if (::cpuWakeLockController.isInitialized) cpuWakeLockController.release()
    }

    private fun closeInterface() {
        releaseCpuWakeLock()
        try {
            vpnInterface?.let {
                runCatching { it.close() }
                Log.i(TAG, "VPN interface ditutup")
            }
        } finally {
            vpnInterface = null
        }
    }

    override fun onRevoke() { // user cabut izin VPN dari pengaturan
        stopSelfClean()
    }

    override fun onDestroy() {
        releaseCpuWakeLock()
        if (coreController != null) stopSelfClean()
        else closeInterface()
        super.onDestroy()
    }

    // ---------------------------------------------------------- notifikasi

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
        )
    }

    private fun startAsForeground() {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification =
            androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(TunnelManager.currentProfile?.remarks ?: "Menghubungkan…")
                .setContentIntent(pi)
                .setOngoing(true)
                .setForegroundServiceBehavior(
                    androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                )
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, notif,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, notif)
    }
}
