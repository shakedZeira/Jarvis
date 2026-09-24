package com.jarvis.remote.data

import com.jarvis.remote.data.model.Message
import com.jarvis.remote.data.model.MessageInfo
import com.jarvis.remote.data.model.Part
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageModelsTest {

    private fun textPart(text: String) = Part(type = "text", text = text)

    private fun toolPart(title: String? = null, command: String? = null) = Part(
        type = "tool",
        tool = "bash",
        title = title,
        state = if (command != null) {
            buildJsonObject { put("input", buildJsonObject { put("command", command) }) }
        } else null
    )

    @Test
    fun `plainText concatenates only text parts`() {
        val msg = Message(
            info = MessageInfo(role = "assistant"),
            parts = listOf(textPart("Hello"), textPart(" world"), toolPart(title = "git status", command = "git status"))
        )
        assertEquals("Hello world", msg.plainText())
    }

    @Test
    fun `userText only returns text for user role`() {
        val user = Message(MessageInfo(role = "user"), listOf(textPart("hi"), textPart(" there")))
        assertEquals("hi there", user.userText())

        val assistant = Message(MessageInfo(role = "assistant"), listOf(textPart("hello")))
        assertEquals("", assistant.userText())
    }

    @Test
    fun `toolSummary uses title`() {
        val part = toolPart(title = "git status", command = "git status")
        assertTrue(part.isTool())
        assertEquals("bash: git status", part.toolSummary())
    }

    @Test
    fun `toolSummary falls back to input command`() {
        val part = toolPart(command = "ls -la")
        assertEquals("bash: ls -la", part.toolSummary())
    }

    @Test
    fun `toolSummary without detail returns tool name`() {
        val part = Part(type = "tool", tool = "bash")
        assertEquals("bash", part.toolSummary())
    }

    @Test
    fun `toolSummary without tool falls back to title`() {
        val part = Part(type = "tool", title = "git status")
        assertEquals("git status", part.toolSummary())
    }

    @Test
    fun `text part is not a tool`() {
        assertFalse(textPart("hi").isTool())
    }
}