package com.jarvis.remote.data

import com.jarvis.remote.data.model.OpenCodeEvent
import com.jarvis.remote.data.sse.EventJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventJsonParserTest {

    @Test
    fun `parses server connected sample`() {
        val event = EventJsonParser.parseEvent("""{"id":"evt_x","type":"server.connected","properties":{}}""")
        assertEquals(OpenCodeEvent.ServerConnected, event)
    }

    @Test
    fun `parses session created`() {
        val event = EventJsonParser.parseEvent("""{"type":"session.created","properties":{"sessionID":"ses_x"}}""")
        assertEquals(OpenCodeEvent.SessionCreated("ses_x"), event)
    }

    @Test
    fun `parses session updated`() {
        val event = EventJsonParser.parseEvent("""{"type":"session.updated","properties":{"sessionID":"ses_x"}}""")
        assertEquals(OpenCodeEvent.SessionUpdated("ses_x"), event)
    }

    @Test
    fun `parses session idle`() {
        val event = EventJsonParser.parseEvent("""{"type":"session.idle","properties":{"sessionID":"ses_x"}}""")
        assertEquals(OpenCodeEvent.SessionIdle("ses_x"), event)
    }

    @Test
    fun `parses session error with message`() {
        val event = EventJsonParser.parseEvent(
            """{"type":"session.error","properties":{"sessionID":"ses_x","error":"boom"}}"""
        )
        assertEquals(OpenCodeEvent.SessionError("ses_x", "boom"), event)
    }

    @Test
    fun `parses session status`() {
        val event = EventJsonParser.parseEvent(
            """{"type":"session.status","properties":{"sessionID":"ses_x","status":"idle"}}"""
        )
        assertEquals(OpenCodeEvent.SessionStatusChanged("ses_x", "idle"), event)
    }

    @Test
    fun `parses message updated`() {
        val event = EventJsonParser.parseEvent(
            """{"type":"message.updated","properties":{"sessionID":"ses_x","messageID":"msg_1"}}"""
        )
        assertEquals(OpenCodeEvent.MessageUpdated("ses_x", "msg_1"), event)
    }

    @Test
    fun `parses message part updated`() {
        val event = EventJsonParser.parseEvent(
            """{"type":"message.part.updated","properties":{"sessionID":"ses_x","messageID":"msg_1"}}"""
        )
        assertEquals(OpenCodeEvent.MessagePartUpdated("ses_x", "msg_1"), event)
    }

    @Test
    fun `parses message part delta with text`() {
        val event = EventJsonParser.parseEvent(
            """{"type":"message.part.delta","properties":{"sessionID":"ses_x","messageID":"msg_1","text":"hel"}}"""
        )
        assertEquals(OpenCodeEvent.MessagePartDelta("ses_x", "msg_1", "hel"), event)
    }

    @Test
    fun `parses permission updated from permission object`() {
        val event = EventJsonParser.parseEvent(
            """
            {"type":"permission.updated","properties":{
                "sessionID":"ses_x","permissionID":"perm_1",
                "permission":{"text":"Allow bash?","state":"pending"}
            }}
            """.trimIndent()
        )
        assertEquals(OpenCodeEvent.PermissionUpdated("ses_x", "perm_1", "Allow bash?", "pending"), event)
    }

    @Test
    fun `parses permission updated from flat props`() {
        val event = EventJsonParser.parseEvent(
            """{"type":"permission.updated","properties":{"sessionID":"ses_x","permissionID":"perm_1","state":"denied"}}"""
        )
        assertEquals(OpenCodeEvent.PermissionUpdated("ses_x", "perm_1", null, "denied"), event)
    }

    @Test
    fun `parses unknown type as Unknown`() {
        val event = EventJsonParser.parseEvent("""{"type":"omega.event","properties":{"sessionID":"ses_x"}}""")
        assertEquals(OpenCodeEvent.Unknown("omega.event"), event)
    }

    @Test
    fun `tolerates unknown property keys`() {
        val event = EventJsonParser.parseEvent(
            """{"type":"message.updated","properties":{"sessionID":"ses_x","messageID":"msg_1","futureKey":{"a":1}}}"""
        )
        assertEquals(OpenCodeEvent.MessageUpdated("ses_x", "msg_1"), event)
    }

    @Test
    fun `returns null for malformed input`() {
        assertNull(EventJsonParser.parseEvent("not json"))
        assertNull(EventJsonParser.parseEvent(""))
        assertNull(EventJsonParser.parseEvent("{}"))
        assertNull(EventJsonParser.parseEvent("""{"type":123}"""))
    }

    @Test
    fun `parses part via PartJsonParser`() {
        val part = com.jarvis.remote.data.sse.PartJsonParser.parsePart(
            """{"type":"tool","tool":"bash","title":"git status","state":{"input":{"command":"git status"}}}"""
        )
        assertEquals("tool", part?.type)
        assertEquals("bash: git status", part?.toolSummary())
    }
}