package com.jarvis.remote.ui.session

import com.jarvis.remote.data.model.Message
import com.jarvis.remote.data.model.MessageInfo
import com.jarvis.remote.data.model.Part
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class TranscriptItem(
    val key: String,
    val role: MessageRole,
    val text: String,
    val toolName: String? = null,
    val toolTitle: String? = null,
    val collapsed: Boolean = false,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val baseMessageId: String? = null,
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

object TranscriptMapper {

    private const val MAX_REASONING_CHARS = 500
    private val STEP_TYPES = setOf("step-start", "step-finish")

    fun toItems(messages: List<Message>): List<TranscriptItem> {
        val out = mutableListOf<TranscriptItem>()
        messages.forEachIndexed { messageIndex, message ->
            val info = message.info
            val base = info.id ?: "m$messageIndex"

            if (info.role == "user") {
                val text = message.userText()
                if (text.isNotBlank()) {
                    out += TranscriptItem(
                        key = "$base-user",
                        role = MessageRole.USER,
                        text = text,
                        baseMessageId = info.id,
                    )
                }
                return@forEachIndexed
            }

            val finishedWithError = info.finish == "error"
            val reasoning = mutableListOf<String>()
            var hasReasoning = false
            var textBuffer = StringBuilder()
            var textStartIndex = 0
            var hasText = false

            fun flushReasoning() {
                if (!hasReasoning) return
                val content = reasoning.joinToString("").trim()
                val text = if (content.isEmpty()) {
                    "⏳ reasoning…"
                } else {
                    "⏳ reasoning…\n${content.take(MAX_REASONING_CHARS)}"
                }
                out += TranscriptItem(
                    key = "$base-reason",
                    role = MessageRole.SYSTEM,
                    text = text,
                    baseMessageId = info.id,
                )
                reasoning.clear()
                hasReasoning = false
            }

            fun flushText() {
                if (!hasText) return
                val text = textBuffer.toString().trim()
                if (text.isNotEmpty()) {
                    out += TranscriptItem(
                        key = "$base-t$textStartIndex",
                        role = MessageRole.ASSISTANT,
                        text = text,
                        isError = finishedWithError,
                        baseMessageId = info.id,
                    )
                }
                textBuffer = StringBuilder()
                hasText = false
            }

            message.parts.forEachIndexed { partIndex, part ->
                when {
                    part.isTool() -> {
                        flushReasoning()
                        flushText()
                        out += TranscriptItem(
                            key = "$base-tool$partIndex",
                            role = MessageRole.ASSISTANT,
                            text = toolStateText(part),
                            toolName = part.tool?.takeIf { it.isNotBlank() },
                            toolTitle = part.title?.takeIf { it.isNotBlank() } ?: part.inputCommand(),
                            collapsed = true,
                            isError = isErrorPart(part),
                            baseMessageId = info.id,
                        )
                    }

                    part.type == "reasoning" -> {
                        flushText()
                        hasReasoning = true
                        part.text?.takeIf { it.isNotBlank() }?.let { reasoning += it }
                    }

                    part.type in STEP_TYPES -> Unit

                    else -> {
                        val text = part.text.orEmpty()
                        if (text.isNotEmpty()) {
                            if (!hasText) textStartIndex = partIndex
                            textBuffer.append(text)
                            hasText = true
                        }
                    }
                }
            }
            flushReasoning()
            flushText()
        }
        return out
    }

    fun messageStreamingText(parts: List<Part>): String =
        parts.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")

    fun heading(messageInfo: MessageInfo?): String {
        if (messageInfo == null) return ""
        return listOfNotNull(messageInfo.modelID, messageInfo.agent)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
    }

    fun runningSignature(parts: List<Part>): String? {
        val latest = parts.lastOrNull { it.isTool() } ?: return null
        val name = latest.tool?.takeIf { it.isNotBlank() }
        val title = latest.title?.takeIf { it.isNotBlank() } ?: latest.inputCommand()
        val detail = when {
            name != null && title != null -> "$name — $title"
            title != null -> title
            name != null -> name
            else -> return null
        }
        return "▶ Running: $detail"
    }

    fun toolStateText(part: Part): String {
        val state = part.state ?: return ""
        val output = state["output"]
        val rendered = when (output) {
            is JsonPrimitive -> output.content
            is JsonObject -> when (val result = output["result"]) {
                is JsonPrimitive -> result.content
                is JsonObject -> result.toString()
                else -> output.toString()
            }
            else -> null
        }
        return rendered?.takeIf { it.isNotBlank() } ?: state.toString()
    }

    private fun isErrorPart(part: Part): Boolean =
        (part.state?.get("status") as? JsonPrimitive)?.content?.contains("error", ignoreCase = true) == true
}