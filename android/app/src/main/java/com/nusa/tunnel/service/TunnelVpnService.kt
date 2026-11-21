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
import com.nusa.tunnel.core.TunnelManager
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * VpnService NusaTunnel — SEMUA NYATA:
 *  1. Builder VPN (addr/route/dns/MTU) → establish() → fd TUN
 *  2. fd dilempar ke Xray-core (startLoop config, tunFd)
 *  3. stopLoop() + close() interface saat disconnect — benar-benar berhenti
 */
class TunnelVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.nusa.tunnel.START"
        const val ACTION_START_CORE = "com.nusa.tunnel.START_CORE"
        const val ACTION_STOP = "com.nusa.tunnel.STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_TUN_FD = "tun_fd"

        private const val TAG = "TunnelVpnService"
        private const val CHANNEL_ID = "nusatunnel_status"
        private const val NOTIF_ID = 1

        @Volatile var coreController: CoreController? = null
            private set
        private val starting = AtomicBoolean(false)
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    // ---------------------------------------------------------- lifecycle

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                if (starting.compareAndSet(false, true)) {
                    Thread { establishVpn() }.start()
                } else {
                    Log.w(TAG, "Start sudah berjalan, abaikan")
                }
            }
            ACTION_START_CORE -> {
                val config = intent.getStringExtra(EXTRA_CONFIG) ?: run {
                    TunnelManager.onCoreError("Config kosong")
                    stopSelfClean()
                    return START_NOT_STICKY
                }
                val tunFd = intent.getIntExtra(EXTRA_TUN_FD, 0)
                startCore(config, tunFd)
            }
            ACTION_STOP -> stopSelfClean()
            else -> startAsForeground()
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------- VPN builder

    private fun establishVpn() {
        try {
            closeInterface()
            val builder = Builder()
                .setSession("NusaTunnel")
                .setMtu(1500)
                .addAddress("10.10.14.1", 30)
                .addAddress("fc00::10:10:14:1", 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setBlocking(true)

            vpnInterface = builder.establish() ?: run {
                TunnelManager.onCoreError("Izin VPN ditolak / interface gagal dibuat")
                stopSelfClean()
                return
            }
            Log.i(TAG, "VPN interface aktif, fd=${vpnInterface!!.fd}")
            TunnelManager.onVpnEstablished(this, vpnInterface!!.fd)
        } catch (e: Exception) {
            Log.e(TAG, "establishVpn gagal", e)
            TunnelManager.onCoreError(e.message ?: "VPN gagal: ${e.javaClass.simpleName}")
            stopSelfClean()
        } finally {
            starting.set(false)
        }
    }

    // ---------------------------------------------------------- core Xray

    private fun startCore(config: String, tunFd: Int) {
        Thread {
            try {
                // hentikan instance lama bila ada
                runCatching { coreController?.stopLoop() }
                val controller = Libv2ray.newCoreController(TunnelManager.coreCallback)
                controller.startLoop(config, tunFd)
                coreController = controller
                if (controller.isRunning) {
                    TunnelManager.onCoreStarted()
                } else {
                    throw IllegalStateException("Core tidak berjalan setelah startLoop")
                }
            } catch (e: Exception) {
                Log.e(TAG, "startCore gagal", e)
                TunnelManager.onCoreError(e.message ?: e.javaClass.simpleName)
                stopSelfClean()
            }
        }.start()
    }

    // ---------------------------------------------------------- stop (NYATA)

    private fun stopSelfClean() {
        thread(name = "nusa-stop") {
            // 1. hentikan core Xray — stopLoop mematikan instance Go
            try {
                coreController?.let { c ->
                    Log.i(TAG, "stopLoop dipanggil…")
                    c.stopLoop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "stopLoop: ${e.message}")
            } finally {
                coreController = null
            }
            // 2. tutup fd TUN
            closeInterface()
            // 3. berhenti + notifikasi
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            TunnelManager.onServiceDestroyed()
        }
    }

    private fun closeInterface() {
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
