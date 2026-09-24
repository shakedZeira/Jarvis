package com.jarvis.remote.data.sse

object Backoff {
    fun nextDelay(attempt: Int, baseMs: Long = 2000, maxMs: Long = 30000): Long {
        if (attempt <= 0) return 0L
        val base = baseMs.coerceAtLeast(0L)
        val max = maxMs.coerceAtLeast(0L)
        val exponent = attempt - 1
        if (exponent >= 62) return max
        val factor = 1L shl exponent
        val scaled = if (base == 0L || factor > Long.MAX_VALUE / base) Long.MAX_VALUE else base * factor
        return if (scaled < max) scaled else max
    }
}