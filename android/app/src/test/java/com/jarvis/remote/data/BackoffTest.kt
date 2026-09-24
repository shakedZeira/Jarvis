package com.jarvis.remote.data

import com.jarvis.remote.data.sse.Backoff
import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {

    @Test
    fun `first attempt reconnects immediately`() {
        assertEquals(0L, Backoff.nextDelay(0))
    }

    @Test
    fun `delay grows exponentially from base`() {
        assertEquals(2000L, Backoff.nextDelay(1))
        assertEquals(4000L, Backoff.nextDelay(2))
        assertEquals(8000L, Backoff.nextDelay(3))
        assertEquals(16000L, Backoff.nextDelay(4))
    }

    @Test
    fun `delay is capped at max`() {
        assertEquals(30000L, Backoff.nextDelay(5))
        assertEquals(30000L, Backoff.nextDelay(10))
        assertEquals(30000L, Backoff.nextDelay(60))
    }

    @Test
    fun `custom base scales the growth`() {
        assertEquals(500L, Backoff.nextDelay(1, baseMs = 500))
        assertEquals(1000L, Backoff.nextDelay(2, baseMs = 500))
        assertEquals(2000L, Backoff.nextDelay(3, baseMs = 500))
    }

    @Test
    fun `custom max caps the delay`() {
        assertEquals(500L, Backoff.nextDelay(1, baseMs = 500, maxMs = 800))
        assertEquals(800L, Backoff.nextDelay(2, baseMs = 500, maxMs = 800))
        assertEquals(800L, Backoff.nextDelay(50, baseMs = 500, maxMs = 800))
    }
}