package com.jarvis.remote.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.remote.data.model.Message
import com.jarvis.remote.data.model.OpenCodeEvent
import com.jarvis.remote.data.repo.ApiException
import com.jarvis.remote.data.repo.OpenCodeClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val sessionID: String,
    val title: String? = null,
    val items: List<TranscriptItem> = emptyList(),
    val streamingText: String? = null,
    val running: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val sending: Boolean = false,
    val disconnected: Boolean = false,
)

class SessionViewModel(
    private val client: OpenCodeClient,
    private val events: Flow<OpenCodeEvent>,
    private val sessionID: String,
    app: Application,
) : AndroidViewModel(app) {

    companion object {
        private const val MESSAGE_LIMIT = 200
        private const val RELOAD_DEBOUNCE_MS = 800L
        private const val SEND_RELOAD_DELAY_MS = 1_200L
        private const val SEND_POLL_INTERVAL_MS = 1_500L
        private const val SEND_TIMEOUT_MS = 120_000L
        private const val STREAM_CAP = 50_000
    }

    private val _uiState = MutableStateFlow(SessionUiState(sessionID = sessionID))
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var eventsJob: Job? = null
    private var reloadJob: Job? = null
    private var streamingMessageId: String? = null
    private var optimisticCounter = 0
    private var sendGeneration = 0
    private var pendingUserText: String? = null

    @Volatile
    var isBackgrounded = false
        private set

    init {
        eventsJob = viewModelScope.launch {
            events
                .onEach { handleEvent(it) }
                .catch { _uiState.update { it.copy(disconnected = true) } }
                .collect()
        }
        load(initial = true)
    }

    fun load(initial: Boolean = true) {
        viewModelScope.launch {
            runCatching { client.messages(sessionID, limit = MESSAGE_LIMIT) }
                .onSuccess { applyMessages(messages = it, initial = initial) }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(
                            error = cause.message ?: "Couldn't load the session transcript",
                            disconnected = true,
                        )
                    }
                }
        }
    }

    private fun applyMessages(messages: List<Message>, initial: Boolean) {
        val serverItems = TranscriptMapper.toItems(messages)
        val firstAssistant = messages.firstOrNull { it.info.role == "assistant" }
        val lastAssistant = messages.lastOrNull { it.info.role == "assistant" }
        val tailText = lastAssistant
            ?.takeIf { it.info.finish == null }
            ?.let { TranscriptMapper.messageStreamingText(it.parts) }

        val pendingUser = pendingUserText
        val completed = pendingUser != null && hasCompletedAssistantAfter(messages, pendingUser)

        _uiState.update { state ->
            val items = if (initial || state.items.isEmpty()) {
                serverItems
            } else {
                mergeRefresh(previous = state.items, server = serverItems)
            }
            val streaming = tailText
                ?: if (state.sending && !completed && state.streamingText != null) state.streamingText else null
            state.copy(
                title = TranscriptMapper.heading(firstAssistant?.info).takeIf { it.isNotBlank() } ?: state.title,
                items = items,
                streamingText = streaming,
                running = TranscriptMapper.runningSignature(messages.lastOrNull()?.parts.orEmpty()),
                error = null,
                disconnected = false,
                sending = if (completed) false else state.sending,
                busy = if (completed) false else state.busy,
            ).also {
                if (completed) pendingUserText = null
            }
        }
        streamingMessageId = if (tailText != null) lastAssistant?.info?.id else null
    }

    private fun hasCompletedAssistantAfter(messages: List<Message>, userText: String): Boolean {
        val trimmed = userText.trim()
        var userIndex = -1
        messages.forEachIndexed { index, message ->
            if (message.info.role == "user" && message.userText().trim() == trimmed) userIndex = index
        }
        if (userIndex < 0) return false
        return messages.drop(userIndex + 1).any { it.info.role == "assistant" && it.info.finish != null }
    }

    private fun mergeRefresh(previous: List<TranscriptItem>, server: List<TranscriptItem>): List<TranscriptItem> {
        if (server.isEmpty()) return previous
        val serverUserTexts = server.filter { it.role == MessageRole.USER }.map { it.text }.toSet()
        val retained = previous.filter { it.key.startsWith("opt-") }
            .filter { it.role != MessageRole.USER || it.text !in serverUserTexts }
        return server + retained
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.sending) return

        val generation = ++sendGeneration
        val optimisticKey = "opt-user-${optimisticCounter++}"
        pendingUserText = trimmed
        _uiState.update {
            it.copy(
                sending = true,
                error = null,
                streamingText = "",
                items = it.items + TranscriptItem(
                    key = optimisticKey,
                    role = MessageRole.USER,
                    text = trimmed,
                    isStreaming = true,
                ),
            )
        }
        viewModelScope.launch {
            val sent = runCatching { client.sendPrompt(sessionID, trimmed) }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(
                            error = sendErrorText(cause),
                            sending = false,
                            busy = false,
                        )
                    }
                    pendingUserText = null
                }
                .isSuccess
            if (sent) {
                delay(SEND_RELOAD_DELAY_MS)
                load(initial = false)
                while (_uiState.value.sending) {
                    delay(SEND_POLL_INTERVAL_MS)
                    load(initial = false)
                }
            }
        }
        viewModelScope.launch {
            delay(SEND_TIMEOUT_MS)
            if (generation == sendGeneration && _uiState.value.sending) {
                _uiState.update { it.copy(sending = false) }
                pendingUserText = null
            }
        }
    }

    private fun sendErrorText(cause: Throwable): String {
        val api = cause as? ApiException
        if (api?.code == 400) {
            return "Request rejected by server: ${api.detail ?: api.message}"
        }
        return api?.detail ?: cause.message ?: "Couldn't send the prompt"
    }

    fun abort() {
        viewModelScope.launch {
            runCatching { client.abort(sessionID) }
                .onFailure { cause ->
                    _uiState.update { it.copy(error = cause.message ?: "Couldn't abort the session") }
                }
        }
    }

    fun refresh() = load(initial = false)

    fun setBackgrounded(value: Boolean) {
        isBackgrounded = value
    }

    fun dispose() {
        eventsJob?.cancel()
        eventsJob = null
    }

    override fun onCleared() {
        dispose()
        reloadJob?.cancel()
        super.onCleared()
    }

    private fun handleEvent(event: OpenCodeEvent) {
        when (event) {
            is OpenCodeEvent.MessagePartDelta ->
                if (event.sessionID == sessionID && !isBackgrounded) onDelta(event.messageID, event.text)

            is OpenCodeEvent.MessagePartUpdated ->
                if (event.sessionID == sessionID && !isBackgrounded) scheduleReload()

            is OpenCodeEvent.MessageUpdated ->
                if (event.sessionID == sessionID && !isBackgrounded) scheduleReload()

            is OpenCodeEvent.SessionIdle ->
                if (event.sessionID == sessionID && !isBackgrounded) {
                    _uiState.update { it.copy(busy = false, sending = false) }
                    scheduleReload()
                }

            is OpenCodeEvent.SessionStatusChanged ->
                if (event.sessionID == sessionID && !isBackgrounded) {
                    when (event.status) {
                        "busy", "retry", "working", "running" -> _uiState.update { it.copy(busy = true) }
                        "idle" -> {
                            _uiState.update { it.copy(busy = false, sending = false) }
                            scheduleReload()
                        }
                        else -> Unit
                    }
                }

            is OpenCodeEvent.SessionError ->
                if (event.sessionID == sessionID && !isBackgrounded) {
                    _uiState.update {
                        it.copy(
                            error = event.message?.takeIf { message -> message.isNotBlank() } ?: "Session error",
                            busy = false,
                            sending = false,
                        )
                    }
                }

            is OpenCodeEvent.PermissionUpdated ->
                if (event.sessionID == sessionID && !isBackgrounded) _uiState.update { it.copy(busy = true) }

            is OpenCodeEvent.ServerConnected -> _uiState.update { it.copy(disconnected = false) }

            is OpenCodeEvent.SessionCreated ->
                if (event.sessionID == sessionID && !isBackgrounded) scheduleReload()

            is OpenCodeEvent.SessionUpdated ->
                if (event.sessionID == sessionID && !isBackgrounded) scheduleReload()

            is OpenCodeEvent.Unknown -> Unit
        }
    }

    private fun onDelta(messageID: String?, text: String?) {
        val chunk = text.orEmpty()
        if (chunk.isEmpty() && streamingMessageId == messageID) return
        val reset = streamingMessageId != messageID
        streamingMessageId = messageID
        _uiState.update { state ->
            val base = if (reset) "" else state.streamingText ?: ""
            state.copy(
                streamingText = (base + chunk).take(STREAM_CAP),
                busy = true,
                error = null,
            )
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(RELOAD_DEBOUNCE_MS)
            load(initial = false)
        }
    }
}