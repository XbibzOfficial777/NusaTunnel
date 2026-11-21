package com.nusa.tunnel

import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nusa.tunnel.service.TunnelVpnService
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsOsNavigationKvmTest {
    @Test
    fun alwaysOnVpnActionResolvesToSystemVpnSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolved = context.packageManager.resolveActivity(Intent(Settings.ACTION_VPN_SETTINGS), 0)
        assertNotNull("Android must provide its VPN settings screen", resolved)
    }

    @Test
    fun batteryActionResolvesToAppDetailsSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertNotNull("Android must provide app details settings", resolved)
    }

    @Test
    fun vpnServiceRefreshActionResolvesToTheDeclaredService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serviceIntent = Intent(context, TunnelVpnService::class.java).apply {
            action = TunnelVpnService.ACTION_REFRESH_WAKE_LOCK
        }
        assertNotNull(context.packageManager.resolveService(serviceIntent, 0))
    }
}
