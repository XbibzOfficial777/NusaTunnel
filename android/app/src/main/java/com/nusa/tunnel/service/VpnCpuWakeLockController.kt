package com.nusa.tunnel.service

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.nusa.tunnel.core.VpnWakeLockPolicy

/** Owns the opt-in partial wake lock for an established VPN interface. */
class VpnCpuWakeLockController(context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun update(preferenceEnabled: Boolean, vpnInterfaceActive: Boolean, stopping: Boolean) {
        if (!VpnWakeLockPolicy.shouldHold(preferenceEnabled, vpnInterfaceActive, stopping)) {
            releaseLocked()
            return
        }
        if (wakeLock?.isHeld == true) return

        wakeLock = runCatching {
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "${appContext.packageName}:VpnActive",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.w(TAG, "WakeLock acquire: ${it.message}") }
            .getOrNull()
    }

    @Synchronized
    fun release() {
        releaseLocked()
    }

    @Synchronized
    fun isHeld(): Boolean = wakeLock?.isHeld == true

    private fun releaseLocked() {
        val lock = wakeLock ?: return
        runCatching {
            if (lock.isHeld) lock.release()
        }.onFailure { Log.w(TAG, "WakeLock release: ${it.message}") }
        wakeLock = null
    }

    private companion object {
        const val TAG = "VpnCpuWakeLock"
    }
}
