package com.jarvis.remote.data.sse

import com.jarvis.remote.data.model.JarvisJson
import com.jarvis.remote.data.model.OpenCodeEvent
import com.jarvis.remote.data.model.Part
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object EventJsonParser {

    fun parseEvent(json: String): OpenCodeEvent? {
        val root = try {
            JarvisJson.parseToJsonElement(json) as? JsonObject
        } catch (e: Exception) {
            return null
        } ?: return null

        val type = root["type"]?.let { value ->
            when (val p = value) {
                is JsonPrimitive -> p.takeIf { it.isString }?.content
                else -> null
            }
        } ?: return null
        val props = root["properties"] as? JsonObject
        val sessionID = props.optString("sessionID")
        val messageID = props.optString("messageID")

        return when (type) {
            "server.connected" -> OpenCodeEvent.ServerConnected
            "session.created" -> OpenCodeEvent.SessionCreated(sessionID)
            "session.updated" -> OpenCodeEvent.SessionUpdated(sessionID)
            "session.idle" -> OpenCodeEvent.SessionIdle(sessionID)
            "session.error" -> OpenCodeEvent.SessionError(sessionID, props.optString("error"))
            "session.status" -> OpenCodeEvent.SessionStatusChanged(sessionID, props.optString("status"))
            "message.updated" -> OpenCodeEvent.MessageUpdated(sessionID, messageID)
            "message.part.updated" -> OpenCodeEvent.MessagePartUpdated(sessionID, messageID)
            "message.part.delta" -> OpenCodeEvent.MessagePartDelta(sessionID, messageID, props.optString("text"))
            "permission.updated" -> {
                val (pid, text, state) = permissionFields(props)
                OpenCodeEvent.PermissionUpdated(sessionID, pid, text, state)
            }
            else -> OpenCodeEvent.Unknown(type)
        }
    }

    private fun permissionFields(props: JsonObject?): Triple<String?, String?, String?> {
        if (props == null) return Triple(null, null, null)
        val permissionID = props.optString("permissionID")
        val state = props.optString("state")
        val permission = props["permission"]
        return when (val p = permission) {
            is JsonNull -> Triple(permissionID, null, state)
            is JsonPrimitive -> Triple(permissionID, p.content, state)
            is JsonObject -> Triple(
                permissionID,
                p.optString("text") ?: p.optString("message"),
                p.optString("state") ?: p.optString("status") ?: state
            )
            else -> Triple(permissionID, props.optString("permissionText"), state)
        }
    }

    private fun JsonObject?.optString(key: String): String? =
        when (val el = this?.get(key)) {
            is JsonNull -> null
            is JsonPrimitive -> el.content
            else -> null
        }
}

object PartJsonParser {
    fun parsePart(json: String): Part? = try {
        JarvisJson.decodeFromString<Part>(json)
    } catch (e: Exception) {
        null
    }
}