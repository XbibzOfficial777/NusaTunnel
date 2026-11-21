package com.nusa.tunnel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nusa.tunnel.core.ConnectionCheckSettings
import com.nusa.tunnel.core.SpeedUnit
import com.nusa.tunnel.core.TunnelSettings
import com.nusa.tunnel.core.VpnAppRoutingMode
import com.nusa.tunnel.core.VpnAppRoutingSettings
import com.nusa.tunnel.core.VpnApplicationCatalog
import com.nusa.tunnel.core.VpnBehaviorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun logEntryLimitEditorPersistsResetAndRejectsInvalidValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText("Atur").performClick()

        compose.onNodeWithTag("log_entry_limit_row").performScrollTo().performClick()
        compose.onNodeWithTag("log_entry_limit_input").performTextReplacement("250")
        compose.onNodeWithTag("save_log_entry_limit").performClick()
        assertEquals(250, TunnelSettings.logEntryLimit(context))

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("250 entries · older entries are trimmed immediately").assertIsDisplayed()

        compose.onNodeWithTag("log_entry_limit_row").performScrollTo().performClick()
        compose.onNodeWithTag("log_entry_limit_input").performTextReplacement("99")
        compose.onNodeWithTag("save_log_entry_limit").assertIsNotEnabled()
        compose.onNodeWithTag("cancel_log_entry_limit").performClick()
        assertEquals(250, TunnelSettings.logEntryLimit(context))

        compose.onNodeWithTag("log_entry_limit_row").performScrollTo().performClick()
        compose.onNodeWithTag("reset_log_entry_limit").performClick()
        compose.onNodeWithTag("save_log_entry_limit").performClick()
        assertEquals(TunnelSettings.DEFAULT_LOG_ENTRY_LIMIT, TunnelSettings.logEntryLimit(context))
    }

    @Test
    fun speedUnitChoicePersistsAndSurvivesActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = TunnelSettings.speedUnit(context)
        val selected = if (original == SpeedUnit.BytesPerSecond) {
            SpeedUnit.BitsPerSecond
        } else {
            SpeedUnit.BytesPerSecond
        }

        compose.onNodeWithText("Atur").performClick()
        compose.onNodeWithTag("speed_unit_choice").performScrollTo().performClick()
        compose.onNodeWithText(selected.label).performClick()
        assertEquals(selected, TunnelSettings.speedUnit(context))

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("speed_unit_choice").performScrollTo()
        compose.onNodeWithText(selected.label).assertIsDisplayed()

        TunnelSettings.setSpeedUnit(context, original)
    }

    @Test
    fun keepCpuAwakeSettingPersistsAcrossActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = TunnelSettings.keepCpuAwake(context)
        try {
            TunnelSettings.setKeepCpuAwake(context, false)

            compose.onNodeWithText("Atur").performClick()
            compose.onNodeWithTag("keep_cpu_awake_switch").performScrollTo().performClick()
            assertTrue(TunnelSettings.keepCpuAwake(context))

            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("keep_cpu_awake_switch").performScrollTo().assertIsOn()
        } finally {
            TunnelSettings.setKeepCpuAwake(context, original)
        }
    }

    @Test
    fun connectionCheckSettingsValidatePersistAndSurviveActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = TunnelSettings.connectionCheck(context)
        try {
            assertFalse(
                TunnelSettings.setConnectionCheck(
                    context,
                    original.copy(maxAttempts = ConnectionCheckSettings.MIN_MAX_ATTEMPTS - 1),
                ),
            )
            assertEquals(original, TunnelSettings.connectionCheck(context))

            compose.onNodeWithText("Atur").performClick()
            compose.onNodeWithTag("connection_check_row").performScrollTo().performClick()

            compose.onNodeWithTag("connection_check_url").performScrollTo()
                .performTextReplacement("ftp://check.example/status")
            compose.onNodeWithTag("save_connection_check").performScrollTo().assertIsNotEnabled()
            assertEquals(original, TunnelSettings.connectionCheck(context))

            compose.onNodeWithTag("connection_check_url").performTextReplacement("https://check.example/status")
            compose.onNodeWithTag("connection_check_interval").performScrollTo()
                .performTextReplacement("30")
            compose.onNodeWithTag("connection_check_timeout").performScrollTo()
                .performTextReplacement("5")
            compose.onNodeWithTag("connection_check_attempts").performScrollTo()
                .performTextReplacement("2")
            if (!original.enabled) {
                compose.onNodeWithTag("connection_check_enabled").performScrollTo().performClick()
            }
            if (!original.stopOnFailure) {
                compose.onNodeWithTag("connection_check_stop_on_failure").performScrollTo().performClick()
            }
            compose.onNodeWithTag("save_connection_check").performScrollTo().performClick()

            val expected = ConnectionCheckSettings(
                enabled = true,
                url = "https://check.example/status",
                intervalSeconds = 30,
                timeoutSeconds = 5,
                maxAttempts = 2,
                stopOnFailure = true,
            )
            assertEquals(expected, TunnelSettings.connectionCheck(context))

            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("connection_check_row").performScrollTo().performClick()
            compose.onNodeWithTag("connection_check_url").performScrollTo()
                .assertTextEquals("HTTP(S) URL", "https://check.example/status")
            compose.onNodeWithTag("connection_check_interval").performScrollTo()
                .assertTextEquals("Interval (5–3600s)", "30")
            compose.onNodeWithTag("connection_check_timeout").performScrollTo()
                .assertTextEquals("Timeout (1–60s)", "5")
            compose.onNodeWithTag("connection_check_attempts").performScrollTo()
                .assertTextEquals("Maximum attempts (1–10)", "2")
            compose.onNodeWithTag("connection_check_enabled").performScrollTo().assertIsOn()
            compose.onNodeWithTag("connection_check_stop_on_failure").performScrollTo().assertIsOn()
        } finally {
            TunnelSettings.setConnectionCheck(context, original)
        }
    }

    @Test
    fun perAppVpnAllowlistPersistsAndIsAppliedToTheEditorAfterRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = TunnelSettings.vpnAppRouting(context)
        val app = VpnApplicationCatalog.launchableApps(context).firstOrNull()
        assertNotNull("KVM emulator must expose at least one launchable app", app)
        val selectedApp = requireNotNull(app)
        TunnelSettings.setVpnAppRouting(context, VpnAppRoutingSettings())

        compose.onNodeWithText("Atur").performClick()
        compose.onNodeWithText("Per-app VPN").performScrollTo().performClick()
        compose.onNodeWithTag("vpn_app_mode_choice").performClick()
        compose.onNodeWithText(VpnAppRoutingMode.SelectedOnly.label).performClick()
        compose.onNodeWithTag("vpn_app_${selectedApp.packageName}")
            .performScrollTo().performClick()
        compose.onNodeWithTag("save_vpn_app_routing").performClick()

        val expected = VpnAppRoutingSettings(
            VpnAppRoutingMode.SelectedOnly,
            setOf(selectedApp.packageName),
        )
        assertEquals(expected, TunnelSettings.vpnAppRouting(context))

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Per-app VPN").performScrollTo().performClick()
        compose.onNodeWithTag("vpn_app_check_${selectedApp.packageName}")
            .performScrollTo().assertIsOn()

        assertTrue(TunnelSettings.setVpnAppRouting(context, original))
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
