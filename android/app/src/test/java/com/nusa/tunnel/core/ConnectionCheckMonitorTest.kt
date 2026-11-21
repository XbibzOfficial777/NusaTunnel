package com.nusa.tunnel.core

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.backgroundScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCheckMonitorTest {
    @Test
    fun retriesThroughTheConfiguredMaximumThenStopsOnlyWhenRequested() = runTest {
        var attempts = 0
        var stops = 0
        val monitor = ConnectionCheckMonitor(
            scope = backgroundScope,
            probe = { _, _, _ ->
                attempts++
                throw IOException("controlled timeout")
            },
            onAttempt = { _, _, _, _ -> },
            onStopAfterFailure = { stops++ },
            retryDelayMillis = 10,
        )
        val settings = ConnectionCheckSettings(
            enabled = true,
            intervalSeconds = ConnectionCheckSettings.MIN_INTERVAL_SECONDS,
            timeoutSeconds = 1,
            maxAttempts = 3,
            stopOnFailure = true,
        )

        monitor.start(settings, socksPort = 10808) { true }
        advanceTimeBy(settings.intervalSeconds * 1_000L)
        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(10)
        runCurrent()
        assertEquals(2, attempts)
        advanceTimeBy(10)
        runCurrent()

        assertEquals(3, attempts)
        assertEquals(1, stops)
        assertFalse(monitor.isRunning())
        monitor.stop()
    }

    @Test
    fun doesNotStopTunnelWhenStopOnFailureIsDisabled() = runTest {
        var attempts = 0
        var stops = 0
        val monitor = ConnectionCheckMonitor(
            scope = backgroundScope,
            probe = { _, _, _ ->
                attempts++
                throw IOException("controlled timeout")
            },
            onAttempt = { _, _, _, _ -> },
            onStopAfterFailure = { stops++ },
            retryDelayMillis = 10,
        )
        val settings = ConnectionCheckSettings(
            enabled = true,
            intervalSeconds = ConnectionCheckSettings.MIN_INTERVAL_SECONDS,
            timeoutSeconds = 1,
            maxAttempts = 2,
            stopOnFailure = false,
        )

        monitor.start(settings, socksPort = 10808) { true }
        advanceTimeBy(settings.intervalSeconds * 1_000L)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(0, stops)
        assertTrue(monitor.isRunning())
        monitor.stop()
        assertFalse(monitor.isRunning())
    }

    @Test
    fun stopCancelsAnInFlightCheckAndPreventsFutureAttempts() = runTest {
        var attempts = 0
        val started = CompletableDeferred<Unit>()
        val pendingProbe = CompletableDeferred<ConnectionCheckResult>()
        val monitor = ConnectionCheckMonitor(
            scope = backgroundScope,
            probe = { _, _, _ ->
                attempts++
                started.complete(Unit)
                pendingProbe.await()
            },
            onAttempt = { _, _, _, _ -> },
            onStopAfterFailure = { throw AssertionError("no failure threshold expected") },
            retryDelayMillis = 10,
        )

        monitor.start(
            ConnectionCheckSettings(
                enabled = true,
                intervalSeconds = ConnectionCheckSettings.MIN_INTERVAL_SECONDS,
                timeoutSeconds = 1,
                maxAttempts = 1,
            ),
            socksPort = 10808,
        ) { true }
        advanceTimeBy(ConnectionCheckSettings.MIN_INTERVAL_SECONDS * 1_000L)
        runCurrent()
        assertTrue(started.isCompleted)

        monitor.stop()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, attempts)
        assertFalse(monitor.isRunning())
    }

    @Test
    fun successAfterFailureStopsTheCurrentRetryCycleWithoutDisconnecting() = runTest {
        var attempts = 0
        var stops = 0
        val monitor = ConnectionCheckMonitor(
            scope = backgroundScope,
            probe = { _, _, _ ->
                attempts++
                if (attempts == 1) throw IOException("controlled first-attempt failure")
                ConnectionCheckResult(statusCode = 204, elapsedMillis = 4)
            },
            onAttempt = { _, _, _, _ -> },
            onStopAfterFailure = { stops++ },
            retryDelayMillis = 10,
        )

        monitor.start(
            ConnectionCheckSettings(
                enabled = true,
                intervalSeconds = ConnectionCheckSettings.MIN_INTERVAL_SECONDS,
                timeoutSeconds = 1,
                maxAttempts = 2,
                stopOnFailure = true,
            ),
            socksPort = 10808,
        ) { true }
        advanceTimeBy(ConnectionCheckSettings.MIN_INTERVAL_SECONDS * 1_000L)
        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(10)
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(0, stops)
        monitor.stop()
    }

    @Test
    fun disconnectDuringRetryDelayPreventsFurtherAttemptsAndStopAction() = runTest {
        var attempts = 0
        var stops = 0
        var connected = true
        val monitor = ConnectionCheckMonitor(
            scope = backgroundScope,
            probe = { _, _, _ ->
                attempts++
                throw IOException("controlled timeout")
            },
            onAttempt = { _, _, _, _ -> },
            onStopAfterFailure = { stops++ },
            retryDelayMillis = 10,
        )
        val settings = ConnectionCheckSettings(
            enabled = true,
            intervalSeconds = ConnectionCheckSettings.MIN_INTERVAL_SECONDS,
            timeoutSeconds = 1,
            maxAttempts = 3,
            stopOnFailure = true,
        )

        monitor.start(settings, socksPort = 10808) { connected }
        advanceTimeBy(settings.intervalSeconds * 1_000L)
        runCurrent()
        assertEquals(1, attempts)

        connected = false
        advanceTimeBy(10)
        runCurrent()

        assertEquals(1, attempts)
        assertEquals(0, stops)
        assertFalse(monitor.isRunning())
        monitor.stop()
    }
}
