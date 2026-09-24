package com.jarvis.remote.ui.session

import com.jarvis.remote.data.model.Message
import com.jarvis.remote.data.model.MessageInfo
import com.jarvis.remote.data.model.Part
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMapperTest {

    private fun textPart(text: String) = Part(type = "text", text = text)

    private fun reasoningPart(text: String? = null) = Part(type = "reasoning", text = text)

    private fun stepPart(type: String) = Part(type = type)

    private fun toolPart(
        tool: String = "bash",
        title: String? = null,
        command: String? = null,
        status: String? = null,
        output: String? = null,
    ) = Part(
        type = "tool",
        tool = tool,
        title = title,
        state = buildJsonObject {
            if (status != null) put("status", status)
            if (command != null) put("input", buildJsonObject { put("command", command) })
            if (output != null) put("output", buildJsonObject { put("kind", "text"); put("result", output) })
        },
    )

    private fun msg(info: MessageInfo, vararg parts: Part) = Message(info, parts.toList())

    private fun assistant(id: String? = null, vararg parts: Part) =
        msg(MessageInfo(id = id, role = "assistant"), *parts)

    private fun user(id: String? = null, text: String) =
        Message(MessageInfo(id = id, role = "user"), listOf(textPart(text)))

    @Test
    fun `user message maps to a single user item`() {
        val items = TranscriptMapper.toItems(listOf(user(id = "u1", text = "hey there")))

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(MessageRole.USER, item.role)
        assertEquals("hey there", item.text)
        assertEquals("u1-user", item.key)
        assertEquals("u1", item.baseMessageId)
        assertFalse(item.collapsed)
    }

    @Test
    fun `consecutive text parts coalesce into one assistant item`() {
        val items = TranscriptMapper.toItems(
            listOf(assistant(id = "a1", textPart("Hello"), textPart(" there")))
        )

        assertEquals(1, items.size)
        assertEquals(MessageRole.ASSISTANT, items[0].role)
        assertEquals("Hello there", items[0].text)
        assertEquals("a1-t0", items[0].key)
    }

    @Test
    fun `text before and after a tool stay separate items`() {
        val items = TranscriptMapper.toItems(
            listOf(assistant(id = "a1", textPart("first"), toolPart(title = "git status"), textPart("done")))
        )

        assertEquals(3, items.size)
        assertEquals(listOf("first", "done"), items.filter { it.toolName == null }.map { it.text })
        assertEquals("bash", items[1].toolName)
    }

    @Test
    fun `tool part yields collapsible tool item with name title and output`() {
        val part = toolPart(tool = "bash", title = "git status", output = "✓ 3 files changed")
        val item = TranscriptMapper.toItems(listOf(assistant(id = "a1", part))).single()

        assertEquals(MessageRole.ASSISTANT, item.role)
        assertEquals("bash", item.toolName)
        assertEquals("git status", item.toolTitle)
        assertTrue(item.collapsed)
        assertEquals("✓ 3 files changed", item.text)
    }

    @Test
    fun `tool title falls back to the input command`() {
        val part = toolPart(tool = "bash", command = "ls -la")
        val item = TranscriptMapper.toItems(listOf(assistant(id = "a1", part))).single()

        assertEquals("ls -la", item.toolTitle)
    }

    @Test
    fun `tool state without output falls back to raw state json`() {
        val part = toolPart(tool = "bash", command = "ls")
        val item = TranscriptMapper.toItems(listOf(assistant(id = "a1", part))).single()

        assertEquals(part.state.toString(), item.text)
    }

    @Test
    fun `failed tool part is flagged as error`() {
        val part = toolPart(tool = "bash", title = "build", status = "error")
        val item = TranscriptMapper.toItems(listOf(assistant(id = "a1", part))).single()

        assertTrue(item.isError)
    }

    @Test
    fun `reasoning parts coalesce into a system item with truncated content`() {
        val long = "x".repeat(600)
        val parts = listOf(reasoningPart("thinking about "), reasoningPart(long))
        val item = TranscriptMapper.toItems(listOf(assistant(id = "a1", *parts.toTypedArray()))).single()

        assertEquals(MessageRole.SYSTEM, item.role)
        assertEquals("⏳ reasoning…\n" + ("thinking about " + long).take(500), item.text)
    }

    @Test
    fun `empty reasoning coalesces to the reasoning prefix only`() {
        val item = TranscriptMapper.toItems(listOf(assistant(id = "a1", reasoningPart("  ")))).single()

        assertEquals("⏳ reasoning…", item.text)
        assertEquals("a1-reason", item.key)
    }

    @Test
    fun `step start and finish parts are dropped`() {
        val items = TranscriptMapper.toItems(
            listOf(
                assistant(
                    id = "a1",
                    textPart("alpha"),
                    stepPart("step-start"),
                    textPart("beta"),
                    stepPart("step-finish"),
                )
            )
        )

        assertEquals(1, items.size)
        assertEquals("alphabeta", items[0].text)
    }

    @Test
    fun `blank user text is skipped`() {
        assertTrue(TranscriptMapper.toItems(listOf(user(text = "   "))).isEmpty())
    }

    @Test
    fun `running signature describes the latest tool part`() {
        val parts = listOf(
            toolPart(tool = "bash", command = "git status"),
            textPart("ok"),
            toolPart(tool = "bash", title = "npm test"),
        )

        assertEquals("▶ Running: bash — npm test", TranscriptMapper.runningSignature(parts))
    }

    @Test
    fun `running signature is null with no tool parts`() {
        assertNull(TranscriptMapper.runningSignature(listOf(textPart("hi"), reasoningPart("x"))))
    }

    @Test
    fun `running signature falls back to tool name or title`() {
        assertEquals(
            "▶ Running: bash — ls",
            TranscriptMapper.runningSignature(listOf(toolPart(tool = "bash", command = "ls")))
        )
        assertEquals(
            "▶ Running: build",
            TranscriptMapper.runningSignature(listOf(Part(type = "tool", title = "build")))
        )
    }

    @Test
    fun `message streaming text joins only text parts`() {
        val parts = listOf(textPart("Hel"), toolPart(), textPart("lo"))

        assertEquals("Hello", TranscriptMapper.messageStreamingText(parts))
    }

    @Test
    fun `heading formats model and agent`() {
        assertEquals(
            "big-pickle · build",
            TranscriptMapper.heading(MessageInfo(modelID = "big-pickle", agent = "build"))
        )
        assertEquals("build", TranscriptMapper.heading(MessageInfo(agent = "build")))
        assertEquals("big-pickle", TranscriptMapper.heading(MessageInfo(modelID = "big-pickle")))
        assertEquals("", TranscriptMapper.heading(MessageInfo()))
        assertEquals("", TranscriptMapper.heading(null))
    }
}