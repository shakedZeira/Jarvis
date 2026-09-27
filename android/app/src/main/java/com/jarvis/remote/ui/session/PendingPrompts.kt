package com.jarvis.remote.ui.session

import com.jarvis.remote.data.model.PermissionRequest
import com.jarvis.remote.data.model.QuestionInfo
import com.jarvis.remote.data.model.QuestionRequest

data class QuestionDraft(
    val selected: List<String> = emptyList(),
    val custom: String = "",
)

object PendingPrompts {

    fun permissions(requests: List<PermissionRequest>, sessionID: String): List<PermissionRequest> =
        requests.filter { it.sessionID == sessionID }

    fun questions(requests: List<QuestionRequest>, sessionID: String): List<QuestionRequest> =
        requests.filter { it.sessionID == sessionID }

    fun heading(info: QuestionInfo): String = info.header.ifBlank { info.question }

    fun draftFor(request: QuestionRequest): List<QuestionDraft> =
        List(request.questions.size) { QuestionDraft() }

    fun toggle(
        request: QuestionRequest,
        draft: List<QuestionDraft>,
        index: Int,
        label: String,
    ): List<QuestionDraft> {
        val info = request.questions.getOrNull(index) ?: return draft
        val entry = draft.getOrNull(index) ?: QuestionDraft()
        val selected = if (!info.multiple) {
            listOf(label)
        } else {
            val order = info.options.map { it.label }
            val merged = if (label in entry.selected) entry.selected - label else entry.selected + label
            merged.sortedBy { labelOrLast(it, order) }
        }
        return replace(draft, index, entry.copy(selected = selected))
    }

    fun withCustom(draft: List<QuestionDraft>, index: Int, text: String): List<QuestionDraft> {
        val entry = draft.getOrNull(index) ?: return draft
        return replace(draft, index, entry.copy(custom = text))
    }

    fun answers(request: QuestionRequest, draft: List<QuestionDraft>): List<List<String>> =
        request.questions.indices.map { index -> answer(draft.getOrNull(index)) }

    fun canSubmit(request: QuestionRequest, draft: List<QuestionDraft>): Boolean =
        answers(request, draft).any { it.isNotEmpty() } ||
            request.questions.any { it.options.isEmpty() && !it.custom }

    private fun answer(draft: QuestionDraft?): List<String> {
        val entry = draft ?: return emptyList()
        val selected = entry.selected.filter { it.isNotBlank() }
        val custom = entry.custom.trim()
        return if (custom.isEmpty()) selected else selected + custom
    }

    private fun labelOrLast(label: String, order: List<String>): Int {
        val index = order.indexOf(label)
        return if (index < 0) Int.MAX_VALUE else index
    }

    private fun replace(draft: List<QuestionDraft>, index: Int, entry: QuestionDraft): List<QuestionDraft> {
        if (index !in draft.indices) return draft
        val copy = draft.toMutableList()
        copy[index] = entry
        return copy
    }
}
