package com.nusa.tunnel.core

/** Pure guard for the service's Android PARTIAL_WAKE_LOCK lifecycle. */
object VpnWakeLockPolicy {
    fun shouldHold(preferenceEnabled: Boolean, vpnInterfaceActive: Boolean, stopping: Boolean): Boolean =
        preferenceEnabled && vpnInterfaceActive && !stopping
}
