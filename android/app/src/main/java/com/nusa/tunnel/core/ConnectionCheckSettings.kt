package com.nusa.tunnel.core

import java.net.URI

/** Persisted schedule and policy for real HTTP(S) checks through the active local Xray SOCKS proxy. */
data class ConnectionCheckSettings(
    val enabled: Boolean = false,
    val url: String = DEFAULT_URL,
    val intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val stopOnFailure: Boolean = false,
) {
    companion object {
        const val DEFAULT_URL = "https://www.gstatic.com/generate_204"
        const val DEFAULT_INTERVAL_SECONDS = 60
        const val MIN_INTERVAL_SECONDS = 5
        const val MAX_INTERVAL_SECONDS = 3_600
        const val DEFAULT_TIMEOUT_SECONDS = 10
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 60
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val MIN_MAX_ATTEMPTS = 1
        const val MAX_MAX_ATTEMPTS = 10
    }
}

object ConnectionCheckSettingsValidator {
    fun isValidUrl(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.length > 2_048) return false
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme !in setOf("http", "https")) return false
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null) return false
        return uri.port == -1 || uri.port in 1..65_535
    }

    fun isValid(settings: ConnectionCheckSettings): Boolean =
        isValidUrl(settings.url) &&
            settings.intervalSeconds in
                ConnectionCheckSettings.MIN_INTERVAL_SECONDS..ConnectionCheckSettings.MAX_INTERVAL_SECONDS &&
            settings.timeoutSeconds in
                ConnectionCheckSettings.MIN_TIMEOUT_SECONDS..ConnectionCheckSettings.MAX_TIMEOUT_SECONDS &&
            settings.maxAttempts in
                ConnectionCheckSettings.MIN_MAX_ATTEMPTS..ConnectionCheckSettings.MAX_MAX_ATTEMPTS
}
