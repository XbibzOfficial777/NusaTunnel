package com.nusa.tunnel.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnWakeLockPolicyTest {
    @Test
    fun `partial wake lock is held only while preference and VPN interface are active`() {
        assertTrue(VpnWakeLockPolicy.shouldHold(true, vpnInterfaceActive = true, stopping = false))
        assertFalse(VpnWakeLockPolicy.shouldHold(false, vpnInterfaceActive = true, stopping = false))
        assertFalse(VpnWakeLockPolicy.shouldHold(true, vpnInterfaceActive = false, stopping = false))
        assertFalse(VpnWakeLockPolicy.shouldHold(true, vpnInterfaceActive = true, stopping = true))
    }
}
