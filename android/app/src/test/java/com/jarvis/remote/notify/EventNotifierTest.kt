package com.jarvis.remote.notify

import com.jarvis.remote.data.model.OpenCodeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventNotifierTest {

    @Test
    fun `SessionIdle maps to work channel`() {
        val alert = EventNotifier.map(OpenCodeEvent.SessionIdle(sessionID = "ses_abc123"))

        assertEquals(NotificationHelper.CHANNEL_WORK, alert!!.channel)
        assertEquals("Work finished", alert.title)
        assertEquals("A model finished working on ses_abc123", alert.message)
    }

    @Test
    fun `SessionIdle with null session uses fallback label`() {
        val alert = EventNotifier.map(OpenCodeEvent.SessionIdle(sessionID = null))

        assertEquals("A model finished working on session", alert!!.message)
    }

    @Test
    fun `SessionError maps to error channel`() {
        val alert = EventNotifier.map(
            OpenCodeEvent.SessionError(sessionID = "ses_abc123", message = "timeout reading stream")
        )

        assertEquals(NotificationHelper.CHANNEL_ERROR, alert!!.channel)
        assertEquals("Session error", alert.title)
        assertEquals("timeout reading stream", alert.message)
    }

    @Test
    fun `SessionError with null message falls back`() {
        val alert = EventNotifier.map(OpenCodeEvent.SessionError(sessionID = null, message = null))

        assertEquals("A session error occurred", alert!!.message)
    }

    @Test
    fun `PermissionUpdated requested maps to permission channel`() {
        val alert = EventNotifier.map(
            OpenCodeEvent.PermissionUpdated(
                sessionID = "ses_abc123",
                permissionID = "perm_bash",
                permissionText = "Run: rm -rf /usr/local",
                state = "requested"
            )
        )

        assertEquals(NotificationHelper.CHANNEL_PERMISSION, alert!!.channel)
        assertEquals("Permission needed", alert.title)
        assertEquals("Run: rm -rf /usr/local", alert.message)
    }

    @Test
    fun `PermissionUpdated with null text falls back`() {
        val alert = EventNotifier.map(
            OpenCodeEvent.PermissionUpdated(
                sessionID = "ses_abc123",
                permissionID = "perm_bash",
                permissionText = null,
                state = "requested"
            )
        )

        assertEquals("opencode is asking for approval", alert!!.message)
    }

    @Test
    fun `PermissionUpdated already allowed is not alert-worthy`() {
        val alert = EventNotifier.map(
            OpenCodeEvent.PermissionUpdated(
                sessionID = "ses_abc123",
                permissionID = "perm_bash",
                permissionText = "Run: rm -rf /usr/local",
                state = "allow"
            )
        )

        assertNull(alert)
    }

    @Test
    fun `PermissionUpdated already denied is not alert-worthy`() {
        val alert = EventNotifier.map(
            OpenCodeEvent.PermissionUpdated(
                sessionID = "ses_abc123",
                permissionID = "perm_bash",
                permissionText = "Run: rm -rf /usr/local",
                state = "deny"
            )
        )

        assertNull(alert)
    }

    @Test
    fun `ServerConnected is not alert-worthy`() {
        assertNull(EventNotifier.map(OpenCodeEvent.ServerConnected))
    }

    @Test
    fun `Unknown events are not alert-worthy`() {
        assertNull(EventNotifier.map(OpenCodeEvent.Unknown("delta")))
    }
}