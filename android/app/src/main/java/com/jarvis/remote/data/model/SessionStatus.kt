package com.jarvis.remote.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SessionStatus(val raw: JsonObject)