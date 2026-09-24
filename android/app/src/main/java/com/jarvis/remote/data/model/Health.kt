package com.jarvis.remote.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val healthy: Boolean = false,
    val version: String? = null
)