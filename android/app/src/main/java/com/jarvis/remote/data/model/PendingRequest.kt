package com.jarvis.remote.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PermissionRequest(
    val id: String,
    val sessionID: String,
    val permission: String,
    val patterns: List<String> = emptyList(),
    val always: List<String> = emptyList(),
)

@Serializable
data class QuestionRequest(
    val id: String,
    val sessionID: String,
    val questions: List<QuestionInfo> = emptyList(),
)

@Serializable
data class QuestionInfo(
    val question: String,
    val header: String,
    val options: List<QuestionOption> = emptyList(),
    val multiple: Boolean = false,
    val custom: Boolean = false,
)

@Serializable
data class QuestionOption(
    val label: String,
    val description: String = "",
)
