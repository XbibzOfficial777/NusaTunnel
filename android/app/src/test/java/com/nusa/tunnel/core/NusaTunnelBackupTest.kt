package com.nusa.tunnel.core

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NusaTunnelBackupTest {
    private val passphrase = "correct horse battery staple".toCharArray()

    @Test
    fun encryptedBackupRoundTripsCredentialsAndSettingsWithoutPlaintextLeak() {
        val payload = samplePayload()
        val encrypted = NusaTunnelBackup.encryptPayload(payload, passphrase.copyOf())
        val envelope = encrypted.toString(Charsets.UTF_8)

        assertFalse(envelope.contains("server-secret-value"))
        assertFalse(envelope.contains("subscription-token"))
        assertEquals(payload, NusaTunnelBackup.decryptPayload(encrypted, passphrase.copyOf()))
    }

    @Test
    fun wrongPassphraseAndTamperingAreRejectedBeforeRestore() {
        val encrypted = NusaTunnelBackup.encryptPayload(samplePayload(), passphrase.copyOf())

        assertThrows(NusaTunnelBackupException::class.java) {
            NusaTunnelBackup.decryptPayload(encrypted, "a different long passphrase".toCharArray())
        }

        val envelope = encrypted.toString(Charsets.UTF_8)
        val ciphertext = envelope.substringAfter("\"ciphertext\":\"").substringBefore('"')
        val decoded = Base64.getDecoder().decode(ciphertext)
        decoded[decoded.lastIndex] = (decoded.last().toInt() xor 1).toByte()
        val tamperedEnvelope = envelope.replace(
            ciphertext,
            Base64.getEncoder().encodeToString(decoded),
        ).toByteArray(Charsets.UTF_8)
        assertThrows(NusaTunnelBackupException::class.java) {
            NusaTunnelBackup.decryptPayload(tamperedEnvelope, passphrase.copyOf())
        }
    }

    @Test
    fun rejectsWeakPassphrasesAndInvalidSettings() {
        assertThrows(NusaTunnelBackupException::class.java) {
            NusaTunnelBackup.encryptPayload(samplePayload(), "too-short".toCharArray())
        }

        val invalid = samplePayload().copy(
            settings = samplePayload().settings.copy(socksPort = 0),
        )
        assertThrows(NusaTunnelBackupException::class.java) {
            NusaTunnelBackup.encryptPayload(invalid, passphrase.copyOf())
        }
    }

    private fun samplePayload() = NusaTunnelBackupPayload(
        formatVersion = NusaTunnelBackup.FORMAT_VERSION,
        exportedAtEpochMillis = 1_800_000_000_000,
        serverStore = ServerStoreSnapshot(
            servers = listOf(
                ServerProfile(
                    id = "profile-1",
                    proto = Proto.VLESS,
                    remarks = "Private profile",
                    host = "203.0.113.12",
                    port = 443,
                    secret = "server-secret-value",
                    network = "ws",
                    tls = true,
                    sni = "example.invalid",
                    wsPath = "/tunnel",
                    subTag = "https://subscription.example.invalid/subscription-token",
                ),
            ),
            selectedId = "profile-1",
            subscriptionUrl = "https://subscription.example.invalid/subscription-token",
            subscriptionAuto = true,
        ),
        settings = TunnelSettingsBackup(
            socksPort = 10808,
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
        ),
    )
}
