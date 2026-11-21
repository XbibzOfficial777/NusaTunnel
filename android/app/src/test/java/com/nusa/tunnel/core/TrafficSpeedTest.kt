package com.nusa.tunnel.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficSpeedTest {
    @Test
    fun `speed unit preference parsing defaults safely and rejects unknown values`() {
        assertEquals(SpeedUnit.BytesPerSecond, SpeedUnit.fromPreference(null))
        assertEquals(SpeedUnit.BytesPerSecond, SpeedUnit.fromPreference("unknown"))
        assertEquals(SpeedUnit.BitsPerSecond, SpeedUnit.fromPreference("bits_per_second"))
    }

    @Test
    fun `byte deltas are converted using the measured sampling interval`() {
        assertEquals(1_000L, TrafficSpeed.bytesPerSecond(1_000L, 1_000L))
        assertEquals(500L, TrafficSpeed.bytesPerSecond(1_000L, 2_000L))
        assertEquals(0L, TrafficSpeed.bytesPerSecond(0L, 1_000L))
        assertEquals(0L, TrafficSpeed.bytesPerSecond(-1L, 1_000L))
        assertEquals(0L, TrafficSpeed.bytesPerSecond(1_000L, 0L))
    }

    @Test
    fun `byte and bit modes format the same measured traffic in their selected units`() {
        assertEquals("1.0 KB/s", TrafficSpeed.format(1_000L, SpeedUnit.BytesPerSecond))
        assertEquals("8.0 kbps", TrafficSpeed.format(1_000L, SpeedUnit.BitsPerSecond))
        assertEquals("0 B/s", TrafficSpeed.format(0L, SpeedUnit.BytesPerSecond))
        assertEquals("0 bps", TrafficSpeed.format(0L, SpeedUnit.BitsPerSecond))
        assertEquals("80.0 Mbps", TrafficSpeed.format(10_000_000L, SpeedUnit.BitsPerSecond))
    }
}
