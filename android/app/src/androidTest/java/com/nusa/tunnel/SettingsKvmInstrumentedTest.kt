package com.nusa.tunnel

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nusa.tunnel.core.TunnelSettings
import com.nusa.tunnel.core.VpnBehaviorSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a real x86_64 Android virtual device in GitHub Actions with KVM acceleration.
 * This verifies the settings UI -> persisted preference path, not just Compose rendering.
 */
@RunWith(AndroidJUnit4::class)
class SettingsKvmInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun vpnDnsAndMtuUiPersistValidValues() {
        compose.onNodeWithText("Atur").performClick()
        compose.onNodeWithText("VPN behavior").performScrollTo().performClick()

        compose.onNodeWithTag("vpn_primary_dns").performScrollTo()
            .performTextReplacement("9.9.9.9")
        compose.onNodeWithTag("vpn_fallback_dns").performScrollTo()
            .performTextReplacement("1.0.0.1")
        compose.onNodeWithTag("vpn_mtu").performScrollTo()
            .performTextReplacement("1420")
        compose.onNodeWithTag("save_vpn_behavior").performScrollTo().performClick()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            VpnBehaviorSettings("9.9.9.9", "1.0.0.1", 1420),
            TunnelSettings.vpnBehavior(context),
        )
    }

    @Test
    fun invalidDnsIsRejectedWithoutChangingTheSavedValue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = TunnelSettings.vpnBehavior(context)

        compose.onNodeWithText("Atur").performClick()
        compose.onNodeWithText("VPN behavior").performScrollTo().performClick()
        compose.onNodeWithTag("vpn_primary_dns").performScrollTo()
            .performTextReplacement("999.9.9.9")
        compose.onNodeWithTag("save_vpn_behavior").performScrollTo().assertIsNotEnabled()

        assertEquals(before, TunnelSettings.vpnBehavior(context))
    }

    @Test
    fun invalidMtuIsRejectedWithoutChangingTheSavedValue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = TunnelSettings.vpnBehavior(context)

        compose.onNodeWithText("Atur").performClick()
        compose.onNodeWithText("VPN behavior").performScrollTo().performClick()
        compose.onNodeWithTag("vpn_mtu").performScrollTo()
            .performTextReplacement("1279")
        compose.onNodeWithTag("save_vpn_behavior").performScrollTo().assertIsNotEnabled()

        assertEquals(before, TunnelSettings.vpnBehavior(context))
    }
}
