package com.nusa.tunnel

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nusa.tunnel.core.ConnectionCheckSettings
import com.nusa.tunnel.core.EngineLogLevel
import com.nusa.tunnel.core.NusaTunnelBackup
import com.nusa.tunnel.core.Proto
import com.nusa.tunnel.core.ServerProfile
import com.nusa.tunnel.core.ServerStore
import com.nusa.tunnel.core.ServerStoreSnapshot
import com.nusa.tunnel.core.SpeedUnit
import com.nusa.tunnel.core.TunnelSettings
import com.nusa.tunnel.core.TunnelSettingsBackup
import com.nusa.tunnel.core.UdpTunnelEngine
import com.nusa.tunnel.core.VpnAppRoutingMode
import com.nusa.tunnel.core.VpnBehaviorSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end persistence check for a v2.2.6 encrypted backup across an install-style reset. */
@RunWith(AndroidJUnit4::class)
class EncryptedBackupMigrationKvmTest {
    @Test
    fun encryptedBackupRestoresProfilesSubscriptionAndAllAndroidSettings() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val store = ServerStore(context)
        val oldServerData = store.backupSnapshot()
        val oldSettings = TunnelSettings.backupSnapshot(context)
        val preferences = context.getSharedPreferences("nusatunnel_prefs", Context.MODE_PRIVATE)
        val unrelatedPreferenceKey = "backup_test_unrelated_preference"
        val oldUnrelatedPreference = preferences.getString(unrelatedPreferenceKey, null)
        val passphrase = "migrate-this-private-config-2026".toCharArray()
        val secret = "backup-test-server-credential"
        val subscription = "https://subscription.example.invalid/private-token"

        val expectedServer = ServerProfile(
            id = "migration-test-profile",
            proto = Proto.VLESS,
            remarks = "Migration test server",
            host = "203.0.113.17",
            port = 443,
            secret = secret,
            encryption = "none",
            network = "ws",
            tls = true,
            sni = "example.invalid",
            wsPath = "/migration",
            subTag = subscription,
        )
        val expectedStore = ServerStoreSnapshot(
            servers = listOf(expectedServer),
            selectedId = expectedServer.id,
            subscriptionUrl = subscription,
            subscriptionAuto = true,
        )
        val expectedSettings = TunnelSettingsBackup(
            socksPort = 12345,
            snifferEnabled = false,
            udpEngine = UdpTunnelEngine.XrayNative.preferenceValue,
            logLevel = EngineLogLevel.Verbose.preferenceValue,
            logEntryLimit = 250,
            speedUnit = SpeedUnit.BitsPerSecond.preferenceValue,
            autoPing = true,
            keepCpuAwake = true,
            vpnBehavior = VpnBehaviorSettings("9.9.9.9", "1.0.0.1", 1420),
            vpnAppMode = VpnAppRoutingMode.ExcludeSelected.preferenceValue,
            vpnAppPackages = listOf("com.example.browser"),
            connectionCheck = ConnectionCheckSettings(
                enabled = true,
                url = "https://health.example.invalid/check",
                intervalSeconds = 35,
                timeoutSeconds = 4,
                maxAttempts = 2,
                stopOnFailure = true,
            ),
        )
        val defaultSettings = TunnelSettingsBackup(
            socksPort = TunnelSettings.DEFAULT_SOCKS_PORT,
            snifferEnabled = true,
            udpEngine = UdpTunnelEngine.HevSocks5Tunnel.preferenceValue,
            logLevel = EngineLogLevel.Warning.preferenceValue,
            logEntryLimit = TunnelSettings.DEFAULT_LOG_ENTRY_LIMIT,
            speedUnit = SpeedUnit.BytesPerSecond.preferenceValue,
            autoPing = false,
            keepCpuAwake = false,
            vpnBehavior = VpnBehaviorSettings(),
            vpnAppMode = VpnAppRoutingMode.AllApps.preferenceValue,
            vpnAppPackages = emptyList(),
            connectionCheck = ConnectionCheckSettings(),
        )
        val emptyServerStore = ServerStoreSnapshot(emptyList(), null, null, false)

        try {
            assertTrue(preferences.edit().putString(unrelatedPreferenceKey, "keep-me").commit())
            assertTrue(TunnelSettings.restoreBackupSnapshot(context, expectedSettings))
            store.restoreSnapshot(expectedStore)

            val encrypted = ByteArrayOutputStream()
            NusaTunnelBackup.export(context, store, encrypted, passphrase)
            val fileBytes = encrypted.toByteArray()
            val fileText = fileBytes.toString(Charsets.UTF_8)
            assertFalse(fileText.contains(secret))
            assertFalse(fileText.contains("private-token"))

            // Model the uninstall/install boundary: replace the app's local stores with empty defaults.
            store.restoreSnapshot(emptyServerStore)
            assertTrue(TunnelSettings.restoreBackupSnapshot(context, defaultSettings))

            val wrongPassphrase = "wrong-passphrase-for-test-2026".toCharArray()
            try {
                NusaTunnelBackup.restore(
                    context,
                    store,
                    ByteArrayInputStream(fileBytes),
                    wrongPassphrase,
                )
                throw AssertionError("Restore unexpectedly accepted a wrong passphrase")
            } catch (_: com.nusa.tunnel.core.NusaTunnelBackupException) {
                // Authentication must fail before either persisted store is changed.
            } finally {
                wrongPassphrase.fill('\u0000')
            }
            assertEquals(emptyServerStore, store.backupSnapshot())
            assertEquals(defaultSettings, TunnelSettings.backupSnapshot(context))

            NusaTunnelBackup.restore(
                context,
                store,
                ByteArrayInputStream(fileBytes),
                passphrase,
            )

            assertEquals(expectedStore, store.backupSnapshot())
            assertEquals(expectedSettings, TunnelSettings.backupSnapshot(context))
            assertEquals("keep-me", preferences.getString(unrelatedPreferenceKey, null))
        } finally {
            passphrase.fill('\u0000')
            store.restoreSnapshot(oldServerData)
            assertTrue(TunnelSettings.restoreBackupSnapshot(context, oldSettings))
            val cleanup = preferences.edit()
            if (oldUnrelatedPreference == null) cleanup.remove(unrelatedPreferenceKey)
            else cleanup.putString(unrelatedPreferenceKey, oldUnrelatedPreference)
            assertTrue(cleanup.commit())
        }
    }
}
