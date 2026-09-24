package com.jarvis.remote.notify

import com.jarvis.remote.data.model.OpenCodeEvent

object EventNotifier {

    data class Alert(val channel: String, val title: String, val message: String)

    fun map(event: OpenCodeEvent): Alert? {
        return when (event) {
            is OpenCodeEvent.SessionIdle -> Alert(
                channel = NotificationHelper.CHANNEL_WORK,
                title = "Work finished",
                message = "A model finished working on ${event.sessionID.asSessionLabel()}"
            )

            is OpenCodeEvent.SessionError -> Alert(
                channel = NotificationHelper.CHANNEL_ERROR,
                title = "Session error",
                message = event.message ?: "A session error occurred"
            )

            is OpenCodeEvent.PermissionUpdated ->
                if (event.state == "allow" || event.state == "deny") {
                    null
                } else {
                    Alert(
                        channel = NotificationHelper.CHANNEL_PERMISSION,
                        title = "Permission needed",
                        message = event.permissionText ?: "opencode is asking for approval"
                    )
                }

            is OpenCodeEvent.ServerConnected -> null
            is OpenCodeEvent.Unknown -> null
            else -> null
        }
    }

    private fun String?.asSessionLabel(): String = this?.takeIf { it.isNotBlank() } ?: "session"
}