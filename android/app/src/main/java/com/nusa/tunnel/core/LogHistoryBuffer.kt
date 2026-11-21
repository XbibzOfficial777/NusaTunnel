package com.nusa.tunnel.core

import java.util.ArrayDeque

/** Bounded history measured in log entries, not rendered text lines. */
internal class LogHistoryBuffer(initialLimit: Int) {
    private val entries = ArrayDeque<String>()
    private var entryLimit = initialLimit

    init {
        require(initialLimit in TunnelSettings.MIN_LOG_ENTRY_LIMIT..TunnelSettings.MAX_LOG_ENTRY_LIMIT)
    }

    @Synchronized
    fun append(entry: String): List<String> {
        entries.addLast(entry)
        trimOldest()
        return entries.toList()
    }

    @Synchronized
    fun clear(): List<String> {
        entries.clear()
        return emptyList()
    }

    @Synchronized
    fun setLimit(limit: Int): List<String> {
        require(limit in TunnelSettings.MIN_LOG_ENTRY_LIMIT..TunnelSettings.MAX_LOG_ENTRY_LIMIT)
        entryLimit = limit
        trimOldest()
        return entries.toList()
    }

    private fun trimOldest() {
        while (entries.size > entryLimit) entries.removeFirst()
    }
}
