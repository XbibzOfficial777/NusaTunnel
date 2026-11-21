package com.nusa.tunnel.core

/**
 * Accumulates the deltas returned by Xray's queryAllOutboundTrafficStats().
 * That API clears its counters after each read, while the dashboard displays
 * traffic totals for the current VPN session.
 */
internal class TrafficStatsAccumulator {
    private var uploaded = 0L
    private var downloaded = 0L

    @Synchronized
    fun reset() {
        uploaded = 0L
        downloaded = 0L
    }

    @Synchronized
    fun add(uploadDelta: Long, downloadDelta: Long): Pair<Long, Long> {
        uploaded = saturatedAdd(uploaded, uploadDelta.coerceAtLeast(0L))
        downloaded = saturatedAdd(downloaded, downloadDelta.coerceAtLeast(0L))
        return uploaded to downloaded
    }

    private fun saturatedAdd(total: Long, delta: Long): Long =
        if (delta > Long.MAX_VALUE - total) Long.MAX_VALUE else total + delta
}
