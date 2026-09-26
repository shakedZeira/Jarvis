package com.jarvis.remote.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val worktree: String? = null,
    val vcs: String? = null,
    val time: SessionTime? = null,
) {
    val timeUpdated: Long? get() = time?.updated
    val timeCreated: Long? get() = time?.created
}