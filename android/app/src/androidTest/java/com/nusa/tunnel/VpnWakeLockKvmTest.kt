package com.nusa.tunnel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nusa.tunnel.service.VpnCpuWakeLockController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android PowerManager WakeLock API; always releases it in the test. */
@RunWith(AndroidJUnit4::class)
class VpnWakeLockKvmTest {
    @Test
    fun partialWakeLockTracksVpnAndPreferenceLifecycle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val controller = VpnCpuWakeLockController(context)
        try {
            assertFalse(controller.isHeld())

            controller.update(preferenceEnabled = true, vpnInterfaceActive = true, stopping = false)
            assertTrue("opt-in with an active interface acquires PARTIAL_WAKE_LOCK", controller.isHeld())

            controller.update(preferenceEnabled = false, vpnInterfaceActive = true, stopping = false)
            assertFalse("turning the preference off releases the lock", controller.isHeld())

            controller.update(preferenceEnabled = true, vpnInterfaceActive = false, stopping = false)
            assertFalse("an absent VPN interface never holds the lock", controller.isHeld())

            controller.update(preferenceEnabled = true, vpnInterfaceActive = true, stopping = true)
            assertFalse("service teardown releases the lock", controller.isHeld())

            controller.update(preferenceEnabled = true, vpnInterfaceActive = true, stopping = false)
            assertTrue(controller.isHeld())
            controller.release()
            assertFalse("explicit release clears the OS wake lock", controller.isHeld())
        } finally {
            controller.release()
        }
    }
}
