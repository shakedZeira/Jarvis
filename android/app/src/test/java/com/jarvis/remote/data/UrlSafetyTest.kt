package com.jarvis.remote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyTest {

    @Test
    fun lanHost_privateRanges() {
        assertTrue(UrlSafety.isLanHost("192.168.1.5"))
        assertTrue(UrlSafety.isLanHost("192.168.0.1"))
        assertTrue(UrlSafety.isLanHost("10.0.0.5"))
        assertTrue(UrlSafety.isLanHost("172.16.0.1"))
        assertTrue(UrlSafety.isLanHost("172.31.255.1"))
        assertTrue(UrlSafety.isLanHost("169.254.1.1"))
    }

    @Test
    fun lanHost_nonPrivateIpv4() {
        assertFalse(UrlSafety.isLanHost("8.8.8.8"))
        assertFalse(UrlSafety.isLanHost("1.1.1.1"))
        assertFalse(UrlSafety.isLanHost("172.15.1.1"))
        assertFalse(UrlSafety.isLanHost("172.32.0.1"))
        assertFalse(UrlSafety.isLanHost("192.169.1.1"))
        assertFalse(UrlSafety.isLanHost("11.0.0.1"))
        assertFalse(UrlSafety.isLanHost("169.253.1.1"))
    }

    @Test
    fun lanHost_names() {
        assertTrue(UrlSafety.isLanHost("localhost"))
        assertTrue(UrlSafety.isLanHost("opencode.local"))
        assertTrue(UrlSafety.isLanHost("some-box.local"))
        assertTrue(UrlSafety.isLanHost("my-pc"))
        assertTrue(UrlSafety.isLanHost("127.0.0.1"))
        assertTrue(UrlSafety.isLanHost("127.8.9.10"))
        assertFalse(UrlSafety.isLanHost("opencode.example.com"))
    }

    @Test
    fun lanHost_ipv6() {
        assertTrue(UrlSafety.isLanHost("fe80::1"))
        assertTrue(UrlSafety.isLanHost("::1"))
        assertFalse(UrlSafety.isLanHost("2001:db8::1"))
    }

    @Test
    fun describe_httpsIsSafeInternet() {
        assertEquals(SafetyLevel.INTERNET_SAFE, UrlSafety.describe("https://opencode.example.com:4443"))
        assertEquals(SafetyLevel.INTERNET_SAFE, UrlSafety.describe("https://192.168.1.5:443"))
    }

    @Test
    fun describe_localhost() {
        assertEquals(SafetyLevel.LOCALHOST, UrlSafety.describe("http://localhost:4096"))
        assertEquals(SafetyLevel.LOCALHOST, UrlSafety.describe("http://127.0.0.1:4096"))
    }

    @Test
    fun describe_lanHosts() {
        assertEquals(SafetyLevel.LAN, UrlSafety.describe("http://192.168.1.5:4096"))
        assertEquals(SafetyLevel.LAN, UrlSafety.describe("http://10.0.0.8:4096"))
        assertEquals(SafetyLevel.LAN, UrlSafety.describe("http://172.20.0.2:4096"))
        assertEquals(SafetyLevel.LAN, UrlSafety.describe("http://opencode.local:4096"))
        assertEquals(SafetyLevel.LAN, UrlSafety.describe("http://home-server:8080"))
    }

    @Test
    fun describe_cleartextInternetWarns() {
        assertEquals(SafetyLevel.INTERNET, UrlSafety.describe("http://opencode.example.com:4096"))
        assertEquals(SafetyLevel.INTERNET, UrlSafety.describe("http://8.8.8.8:4096"))
    }

    @Test
    fun describe_emptyOrBare() {
        assertEquals(SafetyLevel.INTERNET, UrlSafety.describe(""))
        assertEquals(SafetyLevel.LAN, UrlSafety.describe("192.168.1.5"))
    }
}