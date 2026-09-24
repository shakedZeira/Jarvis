package com.jarvis.remote.data

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionProfile(
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String
) {

    val displayHost: String
        get() = splitAuthority(baseUrl)?.host ?: baseUrl

    val port: Int?
        get() = splitAuthority(baseUrl)?.port

    fun toQrString(): String = buildString {
        append("opencode://").append(username).append(':').append(password).append('@')
        val parsed = splitAuthority(baseUrl)
        if (parsed != null) {
            append(parsed.host)
            parsed.port?.let { append(':').append(it) }
        } else {
            append(baseUrl.substringAfter("://", baseUrl).substringBefore('/').trimEnd('/'))
        }
    }
}

private data class Authority(val host: String, val port: Int?)

fun parseQr(content: String): ConnectionProfile? {
    val raw = content.trim()
    if (!raw.startsWith("opencode://", ignoreCase = true)) return null
    val authority = raw.substringAfter("://").substringBefore('#').trimEnd('/')
    val at = authority.lastIndexOf('@')
    if (at <= 0) return null
    val creds = authority.substring(0, at)
    val hostPort = authority.substring(at + 1)
    val colon = creds.indexOf(':')
    if (colon <= 0 || colon >= creds.length - 1) return null
    val username = creds.substring(0, colon)
    val password = creds.substring(colon + 1)
    val split = splitAuthority(hostPort) ?: return null
    if (split.host.isEmpty()) return null
    val port = split.port ?: 4096
    return ConnectionProfile(
        name = if (split.port != null) "${split.host}:${split.port}" else split.host,
        baseUrl = "http://${split.host}:$port",
        username = username,
        password = password
    )
}

private fun splitAuthority(authority: String): Authority? {
    var rest = authority.trim().trimEnd('/')
    if (rest.isEmpty()) return null
    val schemeEnd = rest.indexOf("://")
    if (schemeEnd >= 0) rest = rest.substring(schemeEnd + 3)

    if (rest.startsWith("[")) {
        val close = rest.indexOf(']')
        if (close < 0) return null
        val host = rest.substring(1, close)
        val after = rest.substring(close + 1)
        val port = if (after.startsWith(":")) after.substring(1).toIntOrNull() else null
        if (after.isNotEmpty() && !after.startsWith(":")) return null
        if (port != null && port !in 1..65535) return null
        return Authority(host, port)
    }

    val firstColon = rest.indexOf(':')
    val lastColon = rest.lastIndexOf(':')
    return when {
        firstColon == -1 -> Authority(rest, null)
        firstColon != lastColon -> Authority(rest, null)
        else -> {
            val port = rest.substring(lastColon + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            Authority(rest.substring(0, lastColon), port)
        }
    }
}