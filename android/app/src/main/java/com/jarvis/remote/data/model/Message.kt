package com.jarvis.remote.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Message(
    val info: MessageInfo,
    val parts: List<Part> = emptyList()
) {
    fun plainText(): String =
        parts.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")

    fun userText(): String = if (info.role == "user") plainText() else ""
}

@Serializable
data class MessageInfo(
    val id: String? = null,
    val role: String? = null,
    val sessionID: String? = null,
    val agent: String? = null,
    val modelID: String? = null,
    val providerID: String? = null,
    val finish: String? = null,
    val time: MessageTime? = null
)

@Serializable
data class MessageTime(
    val created: Long? = null,
    val completed: Long? = null
)

@Serializable
data class Part(
    val type: String,
    val id: String? = null,
    val text: String? = null,
    val tool: String? = null,
    val callID: String? = null,
    val title: String? = null,
    val state: JsonObject? = null,
    val reason: String? = null
) {
    fun isTool(): Boolean = type == "tool"

    fun toolSummary(): String {
        val toolName = tool
        val detail = title ?: inputCommand()
        if (toolName == null) return detail ?: "tool"
        return if (detail != null) "$toolName: $detail" else toolName
    }

    fun inputCommand(): String? = (state?.get("input") as? JsonObject)?.let { input ->
        when (val command = input["command"]) {
            is JsonNull -> null
            is JsonPrimitive -> command.content
            else -> null
        }
    }
}