package com.nusa.tunnel.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionCheckSettingsTest {
    @Test
    fun defaultSettingsAreValidAndDisabled() {
        val settings = ConnectionCheckSettings()
        assertFalse(settings.enabled)
        assertTrue(ConnectionCheckSettingsValidator.isValid(settings))
    }

    @Test
    fun acceptsAbsoluteHttpAndHttpsUrlsWithoutEmbeddedCredentials() {
        assertTrue(ConnectionCheckSettingsValidator.isValidUrl("http://check.example/path?q=1"))
        assertTrue(ConnectionCheckSettingsValidator.isValidUrl("https://check.example:8443/health"))
        assertTrue(ConnectionCheckSettingsValidator.isValidUrl("http://127.0.0.1:8080/"))
    }

    @Test
    fun rejectsUnsupportedOrAmbiguousUrls() {
        listOf(
            "",
            "/relative/path",
            "ftp://check.example/file",
            "https://user:password@check.example/",
            "https://check.example/path#fragment",
            "http://check.example:70000/",
            "https://check.example:0/",
        ).forEach { assertFalse("must reject $it", ConnectionCheckSettingsValidator.isValidUrl(it)) }
    }

    @Test
    fun scheduleValuesEnforceDocumentedBounds() {
        assertTrue(
            ConnectionCheckSettingsValidator.isValid(
                ConnectionCheckSettings(
                    enabled = true,
                    intervalSeconds = ConnectionCheckSettings.MIN_INTERVAL_SECONDS,
                    timeoutSeconds = ConnectionCheckSettings.MIN_TIMEOUT_SECONDS,
                    maxAttempts = ConnectionCheckSettings.MIN_MAX_ATTEMPTS,
                ),
            ),
        )
        assertTrue(
            ConnectionCheckSettingsValidator.isValid(
                ConnectionCheckSettings(
                    intervalSeconds = ConnectionCheckSettings.MAX_INTERVAL_SECONDS,
                    timeoutSeconds = ConnectionCheckSettings.MAX_TIMEOUT_SECONDS,
                    maxAttempts = ConnectionCheckSettings.MAX_MAX_ATTEMPTS,
                ),
            ),
        )
        assertFalse(ConnectionCheckSettingsValidator.isValid(ConnectionCheckSettings(intervalSeconds = 4)))
        assertFalse(ConnectionCheckSettingsValidator.isValid(ConnectionCheckSettings(intervalSeconds = 3_601)))
        assertFalse(ConnectionCheckSettingsValidator.isValid(ConnectionCheckSettings(timeoutSeconds = 0)))
        assertFalse(ConnectionCheckSettingsValidator.isValid(ConnectionCheckSettings(timeoutSeconds = 61)))
        assertFalse(ConnectionCheckSettingsValidator.isValid(ConnectionCheckSettings(maxAttempts = 0)))
        assertFalse(ConnectionCheckSettingsValidator.isValid(ConnectionCheckSettings(maxAttempts = 11)))
    }
}
