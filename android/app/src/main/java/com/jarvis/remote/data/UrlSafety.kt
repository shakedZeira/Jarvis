package com.jarvis.remote.data

enum class SafetyLevel {
    LOCALHOST,
    LAN,
    INTERNET_SAFE,
    INTERNET
}

object UrlSafety {

    fun isLanHost(host: String): Boolean {
        val h = host.trim().lowercase().trimEnd('.')
        if (h.isEmpty()) return false
        if (h == "localhost") return true
        if (h == "opencode.local") return true
        if (h.endsWith(".local")) return true
        if (h.contains(':')) {
            return h == "::1" || h.startsWith("fe80:") || h.startsWith("fe80::")
        }
        if (!h.contains('.')) return true
        val parts = h.split('.').map { it.toIntOrNull() }
        if (parts.size == 4 && parts.all { it != null }) {
            val a = parts[0]!!
            val b = parts[1]!!
            return a == 10 ||
                (a == 192 && b == 168) ||
                (a == 172 && b in 16..31) ||
                (a == 169 && b == 254) ||
                a == 127
        }
        return false
    }

    fun describe(baseUrl: String): SafetyLevel {
        val raw = baseUrl.trim()
        val schemeEnd = raw.indexOf("://")
        val scheme = if (schemeEnd >= 0) raw.substring(0, schemeEnd).lowercase() else "http"
        if (scheme == "https") return SafetyLevel.INTERNET_SAFE

        val authority = raw.substring(if (schemeEnd >= 0) schemeEnd + 3 else 0).substringBefore('/')
        val host = hostOf(authority)
        return when {
            host.isEmpty() -> SafetyLevel.INTERNET
            isLoopback(host) -> SafetyLevel.LOCALHOST
            isLanHost(host) -> SafetyLevel.LAN
            else -> SafetyLevel.INTERNET
        }
    }

    private fun isLoopback(host: String): Boolean {
        val h = host.trim().lowercase()
        return h == "localhost" || h == "::1" || h == "127.0.0.1" || h.startsWith("127.")
    }

    private fun hostOf(authority: String): String {
        val a = authority.substringBefore('/')
        if (a.startsWith("[")) {
            val close = a.indexOf(']')
            return if (close >= 0) a.substring(1, close) else a
        }
        val colon = a.indexOf(':')
        return if (colon >= 0) a.substring(0, colon) else a
    }
}