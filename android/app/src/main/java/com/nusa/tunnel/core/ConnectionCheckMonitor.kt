package com.nusa.tunnel.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Schedules checks while connected; all probe outcomes come from the active SOCKS path. */
class ConnectionCheckMonitor(
    private val scope: CoroutineScope,
    private val probe: suspend (url: String, socksPort: Int, timeoutMillis: Int) -> ConnectionCheckResult,
    private val onAttempt: (attempt: Int, maxAttempts: Int, result: ConnectionCheckResult?, error: Throwable?) -> Unit,
    private val onStopAfterFailure: () -> Unit,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
) {
    @Volatile private var job: Job? = null

    @Synchronized
    fun start(
        settings: ConnectionCheckSettings,
        socksPort: Int,
        isConnected: () -> Boolean,
    ) {
        stop()
        if (!settings.enabled || !ConnectionCheckSettingsValidator.isValid(settings) ||
            socksPort !in 1..65_535
        ) return

        job = scope.launch {
            while (isActive && isConnected()) {
                delay(settings.intervalSeconds * 1_000L)
                if (!isActive || !isConnected()) break

                var succeeded = false
                for (attempt in 1..settings.maxAttempts) {
                    if (!isActive || !isConnected()) break
                    var result: ConnectionCheckResult? = null
                    var failure: Throwable? = null
                    try {
                        result = probe(
                            settings.url,
                            socksPort,
                            settings.timeoutSeconds * 1_000,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = error
                    }
                    if (!isActive || !isConnected()) break
                    onAttempt(attempt, settings.maxAttempts, result, failure)
                    if (result?.succeeded == true) {
                        succeeded = true
                        break
                    }
                    if (attempt < settings.maxAttempts) delay(retryDelayMillis)
                }

                if (!succeeded && isActive && isConnected() && settings.stopOnFailure) {
                    onStopAfterFailure()
                    break
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    @Synchronized
    fun isRunning(): Boolean = job?.isActive == true

    private companion object {
        const val DEFAULT_RETRY_DELAY_MILLIS = 1_000L
    }
}
