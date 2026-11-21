package com.nusa.tunnel.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficStatsAccumulatorTest {
    @Test
    fun `accumulates polling deltas into session totals`() {
        val stats = TrafficStatsAccumulator()

        assertEquals(120L to 30L, stats.add(120L, 30L))
        assertEquals(180L to 75L, stats.add(60L, 45L))
    }

    @Test
    fun `reset starts a fresh session`() {
        val stats = TrafficStatsAccumulator()
        stats.add(120L, 30L)

        stats.reset()

        assertEquals(5L to 9L, stats.add(5L, 9L))
    }

    @Test
    fun `ignores negative deltas and saturates instead of overflowing`() {
        val stats = TrafficStatsAccumulator()

        assertEquals(0L to 10L, stats.add(-1L, 10L))
        assertEquals(Long.MAX_VALUE to 10L, stats.add(Long.MAX_VALUE, 0L))
        assertEquals(Long.MAX_VALUE to 10L, stats.add(1L, 0L))
    }
}
