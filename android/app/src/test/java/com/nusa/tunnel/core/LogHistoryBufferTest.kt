package com.nusa.tunnel.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LogHistoryBufferTest {
    @Test
    fun `limit counts entries even when an entry spans multiple display lines`() {
        val buffer = LogHistoryBuffer(initialLimit = 200)
        repeat(150) { index ->
            buffer.append("entry-${index + 1}\ncontinuation line")
        }

        val trimmed = buffer.setLimit(TunnelSettings.MIN_LOG_ENTRY_LIMIT)
        assertEquals(100, trimmed.size)
        assertEquals("entry-51\ncontinuation line", trimmed.first())
        assertEquals("entry-150\ncontinuation line", trimmed.last())
    }

    @Test
    fun `appending beyond the configured limit discards oldest entries`() {
        val buffer = LogHistoryBuffer(initialLimit = TunnelSettings.MIN_LOG_ENTRY_LIMIT)
        var retained: List<String> = emptyList()
        repeat(101) { index -> retained = buffer.append("entry-${index + 1}") }

        assertEquals(100, retained.size)
        assertEquals("entry-2", retained.first())
        assertEquals("entry-101", retained.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buffer rejects a limit outside the supported range`() {
        LogHistoryBuffer(initialLimit = 99)
    }
}
