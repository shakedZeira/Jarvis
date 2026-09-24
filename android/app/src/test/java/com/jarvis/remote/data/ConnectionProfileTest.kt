package com.jarvis.remote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionProfileTest {

    @Test
    fun displayHost_ipv4() {
        val profile = ConnectionProfile(
            name = "192.168.1.5:4096",
            baseUrl = "http://192.168.1.5:4096",
            username = "opencode",
            password = "secret"
        )
        assertEquals("192.168.1.5", profile.displayHost)
        assertEquals(4096, profile.port)
    }

    @Test
    fun displayHost_hostname() {
        val profile = ConnectionProfile(
            name = "opencode.local:4096",
            baseUrl = "http://opencode.local:4096",
            username = "opencode",
            password = "secret"
        )
        assertEquals("opencode.local", profile.displayHost)
    }

    @Test
    fun toQrString_roundTrips() {
        val profile = ConnectionProfile(
            name = "192.168.1.5:4096",
            baseUrl = "http://192.168.1.5:4096",
            username = "opencode",
            password = "s3cret:+x"
        )
        val qr = profile.toQrString()
        assertEquals("opencode://opencode:s3cret:+x@192.168.1.5:4096", qr)
        assertEquals(profile, parseQr(qr))
    }

    @Test
    fun parseQr_hostnameWithoutPort() {
        val parsed = parseQr("opencode://opencode:pass@opencode.local")
        assertEquals("http://opencode.local:4096", parsed?.baseUrl)
        assertEquals("opencode.local", parsed?.displayHost)
        assertEquals("opencode", parsed?.username)
        assertEquals("pass", parsed?.password)
    }

    @Test
    fun parseQr_withPathOrHashIsTolerant() {
        val parsed = parseQr("opencode://user:hunter2@10.0.0.4:4443/")
        assertEquals("http://10.0.0.4:4443", parsed?.baseUrl)
    }

    @Test
    fun parseQr_passwordContainingAt() {
        val parsed = parseQr("opencode://user:p@ss@172.16.0.9:4096")
        assertEquals("p@ss", parsed?.password)
        assertEquals("172.16.0.9", parsed?.displayHost)
    }

    @Test
    fun parseQr_rejectsGarbage() {
        assertNull(parseQr(""))
        assertNull(parseQr("https://example.com/"))
        assertNull(parseQr("opencode://onlyuser@host"))
        assertNull(parseQr("opencode://user:@host:4096"))
        assertNull(parseQr("opencode://user:passnohost"))
        assertNull(parseQr("opencode://user:pass@"))
    }

    @Test
    fun parseQr_rejectsBadPort() {
        assertNull(parseQr("opencode://user:pass@host:99999"))
        assertNull(parseQr("opencode://user:pass@host:notaport"))
    }

    @Test
    fun toQrString_withoutPort() {
        val profile = ConnectionProfile(
            name = "box.local",
            baseUrl = "http://box.local",
            username = "opencode",
            password = "pw"
        )
        assertEquals("opencode://opencode:pw@box.local", profile.toQrString())
    }
}