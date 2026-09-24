package com.jarvis.remote.data.repo

import java.util.Base64

fun basicAuthHeader(username: String, password: String): String {
    val raw = "$username:$password".toByteArray(Charsets.UTF_8)
    return "Basic " + Base64.getEncoder().encodeToString(raw)
}