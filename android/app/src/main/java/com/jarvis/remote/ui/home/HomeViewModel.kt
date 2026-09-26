package com.jarvis.remote.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.remote.data.model.OpenCodeEvent
import com.jarvis.remote.data.model.Project
import com.jarvis.remote.data.model.Session
import com.jarvis.remote.data.model.SessionStatus
import com.jarvis.remote.data.repo.OpenCodeClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val sessions: List<SessionRow> = emptyList(),
    val connected: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val projects: List<Project> = emptyList(),
    val selectedProject: String? = null,
    val showActiveOnly: Boolean = false,
    val activeCount: Int = 0,
)

data class SessionRow(
    val id: String,
    val title: String,
    val directory: String?,
    val agent: String?,
    val status: RowStatus,
    val busy: Boolean,
    val lastActivityMillis: Long?,
)

enum class RowStatus { RUNNING, IDLE, ERROR, UNKNOWN }

fun Project.displayName(): String {
    val path = worktree?.trim()?.trimEnd('/', '\\')
    if (!path.isNullOrEmpty()) {
        val last = path.substringAfterLast('/').substringAfterLast('\\')
        if (last.isNotBlank()) return last
        return path
    }
    return id
}

object HomeRowMapper {

    const val IDLE_TIMEOUT_MILLIS: Long = 5 * 60 * 1000L
    const val ACTIVE_WINDOW_MILLIS: Long = 30 * 60 * 1000L

    fun activeRows(rows: List<SessionRow>, now: Long): List<SessionRow> =
        rows.filter { row ->
            row.busy ||
                row.status == RowStatus.RUNNING ||
                (row.lastActivityMillis != null && now - row.lastActivityMillis <= ACTIVE_WINDOW_MILLIS)
        }

    fun toRows(
        sessions: List<Session>,
        statuses: Map<String, SessionStatus>,
        busyIds: Set<String>,
        now: Long,
        errors: Map<String, String> = emptyMap(),
    ): List<SessionRow> =
        sessions.map { session ->
            toRow(session, statuses, busyIds, now, errors[session.id])
        }.sortedByDescending { it.lastActivityMillis ?: 0L }

    fun toRow(
        session: Session,
        statuses: Map<String, SessionStatus>,
        busyIds: Set<String>,
        now: Long,
        error: String? = null,
    ): SessionRow {
        val serverBusy = statuses.containsKey(session.id)
        val busy = busyIds.contains(session.id) || serverBusy
        val activity = activityMillis(session)
        val decayedToIdle = serverBusy && !busyIds.contains(session.id) &&
            session.time?.updated == null && activity != null &&
            now - activity > IDLE_TIMEOUT_MILLIS
        val status = when {
            error != null -> RowStatus.ERROR
            busy -> if (decayedToIdle) RowStatus.IDLE else RowStatus.RUNNING
            else -> RowStatus.IDLE
        }
        return SessionRow(
            id = session.id,
            title = session.title?.takeIf { it.isNotBlank() } ?: session.id,
            directory = session.directory,
            agent = session.agent,
            status = status,
            busy = busy && !decayedToIdle,
            lastActivityMillis = activity,
        )
    }

    fun activityMillis(session: Session): Long? {
        val time = session.time ?: return null
        return time.updated ?: time.created
    }

    fun formatRelative(ms: Long?, now: Long): String {
        if (ms == null) return "—"
        val diff = now - ms
        return when {
            diff < 60_000L -> "just now"
            diff < 60 * 60_000L -> "${diff / 60_000L}m ago"
            diff < 24 * 60 * 60_000L -> "${diff / 3_600_000L}h ago"
            else -> "${diff / 86_400_000L}d ago"
        }
    }
}

class HomeViewModel(
    private val client: OpenCodeClient,
    private val events: Flow<OpenCodeEvent>,
    private val statusFlow: Flow<Map<String, SessionStatus>>,
    app: Application,
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val busyIds = mutableSetOf<String>()
    private val errors = mutableMapOf<String, String>()
    private val refetchRequests = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)

    private val projects = mutableListOf<Project>()

    @Volatile
    private var latestSessions: List<Session> = emptyList()

    @Volatile
    private var latestStatuses: Map<String, SessionStatus> = emptyMap()

    @Volatile
    private var selectedProjectDir: String? = null

    @Volatile
    private var activeOnly: Boolean = false

    init {
        observeStatus()
        observeEvents()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            loadProjects()
            runCatching { fetchAllSessions() to client.sessionStatus() }
                .onSuccess { (sessions, statuses) ->
                    latestSessions = sessions
                    renderRows(statuses)
                    _uiState.update { it.copy(loading = false, error = null) }
                }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(loading = false, error = cause.message ?: cause.javaClass.simpleName)
                    }
                }
        }
    }

    fun retry() = refresh()

    fun selectProject(directory: String?) {
        selectedProjectDir = directory
        _uiState.update { it.copy(selectedProject = directory) }
        if (latestSessions.isEmpty()) {
            refresh()
        } else {
            renderRows(latestStatuses)
        }
    }

    fun selectMode(activeOnly: Boolean) {
        this.activeOnly = activeOnly
        _uiState.update { it.copy(showActiveOnly = activeOnly) }
        if (latestSessions.isEmpty()) {
            refresh()
        } else {
            renderRows(latestStatuses)
        }
    }

    private suspend fun loadProjects() {
        runCatching { client.projects() }
            .onSuccess { fetched ->
                projects.clear()
                projects.addAll(fetched)
                _uiState.update {
                    it.copy(projects = fetched.filter { project -> !project.worktree.isNullOrBlank() })
                }
            }
    }

    private suspend fun fetchAllSessions(): List<Session> {
        val directories = projects
            .asSequence()
            .mapNotNull { it.worktree }
            .map { it.trim().trimEnd('/', '\\') }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (directories.isEmpty()) return client.sessions()
        return coroutineScope {
            val results = directories.map { directory ->
                async { runCatching { client.sessions(directory = directory, limit = 500) } }
            }.awaitAll()
            val successes = results.mapNotNull { it.getOrNull() }
            val failures = results.mapNotNull { it.exceptionOrNull() }
            if (successes.isEmpty() && failures.isNotEmpty()) throw failures.first()
            if (successes.isEmpty()) return@coroutineScope emptyList()
            successes.flatMap { it }.distinctBy { it.id }
        }
    }

    private fun observeStatus() {
        viewModelScope.launch {
            statusFlow
                .onEach { statuses ->
                    latestStatuses = statuses
                    val rendered = renderedRows(statuses, System.currentTimeMillis())
                    _uiState.update { state ->
                        state.copy(
                            connected = true,
                            sessions = if (latestSessions.isEmpty()) state.sessions else rendered.visible,
                            activeCount = rendered.activeCount,
                        )
                    }
                }
                .catch { _uiState.update { it.copy(connected = false) } }
                .onCompletion { _uiState.update { it.copy(connected = false) } }
                .collect()
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            events
                .onEach { event ->
                    when (event) {
                        is OpenCodeEvent.ServerConnected -> _uiState.update { it.copy(connected = true) }
                        is OpenCodeEvent.SessionUpdated -> onActivity(event.sessionID)
                        is OpenCodeEvent.SessionCreated -> onActivity(event.sessionID)
                        is OpenCodeEvent.SessionStatusChanged -> onStatusChanged(event.sessionID, event.status)
                        is OpenCodeEvent.SessionIdle -> onIdle(event.sessionID)
                        is OpenCodeEvent.SessionError -> onError(event.sessionID, event.message)
                        is OpenCodeEvent.MessageUpdated -> onActivity(event.sessionID)
                        is OpenCodeEvent.MessagePartUpdated -> onActivity(event.sessionID)
                        is OpenCodeEvent.MessagePartDelta -> onActivity(event.sessionID)
                        is OpenCodeEvent.PermissionUpdated -> onActivity(event.sessionID)
                        is OpenCodeEvent.Unknown -> Unit
                    }
                }
                .catch { _uiState.update { it.copy(connected = false) } }
                .onCompletion { _uiState.update { it.copy(connected = false) } }
                .collect()
        }
        viewModelScope.launch {
            refetchRequests
                .distinctUntilChanged()
                .debounce(500)
                .collect { refreshRows() }
        }
    }

    private fun onActivity(sessionID: String?) {
        if (sessionID == null) return
        busyIds.add(sessionID)
        errors.remove(sessionID)
        _uiState.update { state ->
            state.copy(
                connected = true,
                sessions = state.sessions.map {
                    if (it.id == sessionID) it.copy(status = RowStatus.RUNNING, busy = true) else it
                },
            )
        }
        refetchRequests.tryEmit(sessionID)
    }

    private fun onStatusChanged(sessionID: String?, status: String?) {
        if (sessionID == null) return
        when (status) {
            "busy", "retry" -> onActivity(sessionID)
            "idle" -> onIdle(sessionID)
            else -> Unit
        }
    }

    private fun onIdle(sessionID: String?) {
        if (sessionID == null) return
        busyIds.remove(sessionID)
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions.map {
                    if (it.id == sessionID) it.copy(status = RowStatus.IDLE, busy = false) else it
                },
            )
        }
    }

    private fun onError(sessionID: String?, message: String?) {
        if (sessionID == null) return
        busyIds.remove(sessionID)
        if (message.isNullOrBlank()) {
            errors.remove(sessionID)
        } else {
            errors[sessionID] = message
        }
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions.map {
                    if (it.id == sessionID) it.copy(status = RowStatus.ERROR, busy = false) else it
                },
            )
        }
    }

    private suspend fun refreshRows() {
        runCatching {
            val sessions = fetchAllSessions()
            latestSessions = sessions
            val statuses = client.sessionStatus()
            renderRows(statuses)
        }.getOrNull() ?: return
        _uiState.update { it.copy(connected = true) }
    }

    private fun renderRows(statuses: Map<String, SessionStatus>) {
        latestStatuses = statuses
        val rendered = renderedRows(statuses, System.currentTimeMillis())
        _uiState.update { state ->
            state.copy(
                connected = true,
                sessions = rendered.visible,
                activeCount = rendered.activeCount,
            )
        }
    }

    private data class RenderedRows(val visible: List<SessionRow>, val activeCount: Int)

    private fun renderedRows(statuses: Map<String, SessionStatus>, now: Long): RenderedRows {
        val allRows = HomeRowMapper.toRows(
            sessions = latestSessions,
            statuses = statuses,
            busyIds = busyIds.toSet(),
            now = now,
            errors = errors.toMap(),
        )
        val active = HomeRowMapper.activeRows(allRows, now)
        val visible = if (activeOnly) active else filterRowsByProject(allRows)
        return RenderedRows(visible, active.size)
    }

    private fun filterRowsByProject(rows: List<SessionRow>): List<SessionRow> {
        val target = selectedProjectDir?.normalizedPath() ?: return rows
        return rows.filter { it.directory.normalizedPath() == target }
    }

    private fun String?.normalizedPath(): String? {
        if (this == null) return null
        return trim().replace('\\', '/').trimEnd('/')
    }
}