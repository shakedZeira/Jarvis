package com.jarvis.remote.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val slug: String? = null,
    val projectID: String? = null,
    val directory: String? = null,
    val path: String? = null,
    val title: String? = null,
    val agent: String? = null,
    val cost: Double? = null,
    val time: SessionTime? = null,
    val parentID: String? = null,
    val model: SessionModel? = null
)

@Serializable
data class SessionTime(
    val created: Long? = null,
    val updated: Long? = null
)

@Serializable
data class SessionModel(
    val id: String? = null,
    val providerID: String? = null
)