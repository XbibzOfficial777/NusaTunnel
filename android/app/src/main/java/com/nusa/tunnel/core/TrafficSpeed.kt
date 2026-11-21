package com.nusa.tunnel.core

import java.util.Locale
import kotlin.math.roundToLong

/** Unit shown for the live upload/download rate (not the cumulative session byte counters). */
enum class SpeedUnit(val preferenceValue: String, val label: String) {
    BytesPerSecond("bytes_per_second", "Byte/s (B/s)"),
    BitsPerSecond("bits_per_second", "Bit/s (bps)");

    companion object {
        fun fromPreference(value: String?): SpeedUnit =
            entries.firstOrNull { it.preferenceValue == value } ?: BytesPerSecond
    }
}

/** Pure traffic-rate calculations so unit conversion and boundary behavior are JVM-testable. */
object TrafficSpeed {
    /** Convert a byte delta sampled over elapsed milliseconds to a non-negative byte/second rate. */
    fun bytesPerSecond(deltaBytes: Long, elapsedMillis: Long): Long {
        if (deltaBytes <= 0L || elapsedMillis <= 0L) return 0L
        val rate = deltaBytes.toDouble() * 1_000.0 / elapsedMillis.toDouble()
        if (!rate.isFinite() || rate >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return rate.roundToLong().coerceAtLeast(0L)
    }

    /** Human-readable live rate. Decimal prefixes are used consistently for B/s and bit/s. */
    fun format(bytesPerSecond: Long, unit: SpeedUnit): String {
        val bits = unit == SpeedUnit.BitsPerSecond
        var value = bytesPerSecond.coerceAtLeast(0L).toDouble() * if (bits) 8.0 else 1.0
        val symbols = if (bits) {
            arrayOf("bps", "kbps", "Mbps", "Gbps", "Tbps")
        } else {
            arrayOf("B/s", "KB/s", "MB/s", "GB/s", "TB/s")
        }
        var index = 0
        while (value >= 1_000.0 && index < symbols.lastIndex) {
            value /= 1_000.0
            index++
        }
        val formatted = if (index == 0 && value % 1.0 == 0.0) {
            String.format(Locale.ROOT, "%.0f", value)
        } else {
            String.format(Locale.ROOT, "%.1f", value)
        }
        return "$formatted ${symbols[index]}"
    }
}
