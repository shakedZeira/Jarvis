package com.jarvis.remote.data.model

sealed class OpenCodeEvent {
    data object ServerConnected : OpenCodeEvent()
    data class SessionCreated(val sessionID: String?) : OpenCodeEvent()
    data class SessionUpdated(val sessionID: String?) : OpenCodeEvent()
    data class SessionIdle(val sessionID: String?) : OpenCodeEvent()
    data class SessionError(val sessionID: String?, val message: String?) : OpenCodeEvent()
    data class SessionStatusChanged(val sessionID: String?, val status: String?) : OpenCodeEvent()
    data class MessageUpdated(val sessionID: String?, val messageID: String?) : OpenCodeEvent()
    data class MessagePartUpdated(val sessionID: String?, val messageID: String?) : OpenCodeEvent()
    data class MessagePartDelta(val sessionID: String?, val messageID: String?, val text: String?) : OpenCodeEvent()
    data class PermissionUpdated(
        val sessionID: String?,
        val permissionID: String?,
        val permissionText: String?,
        val state: String?
    ) : OpenCodeEvent()
    data class Unknown(val type: String) : OpenCodeEvent()
}

val OpenCodeEvent.displayType: String
    get() = when (this) {
        is OpenCodeEvent.ServerConnected -> "Connected"
        is OpenCodeEvent.SessionCreated -> "Session created"
        is OpenCodeEvent.SessionUpdated -> "Session updated"
        is OpenCodeEvent.SessionIdle -> "Session idle"
        is OpenCodeEvent.SessionError -> "Session error"
        is OpenCodeEvent.SessionStatusChanged -> "Session status"
        is OpenCodeEvent.MessageUpdated -> "Message updated"
        is OpenCodeEvent.MessagePartUpdated -> "Message part updated"
        is OpenCodeEvent.MessagePartDelta -> "Message streaming"
        is OpenCodeEvent.PermissionUpdated -> "Permission"
        is OpenCodeEvent.Unknown -> "Event: ${this.type}"
    }