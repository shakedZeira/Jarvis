package com.jarvis.remote.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.remote.data.model.Project
import com.jarvis.remote.ui.theme.JarvisBlue
import com.jarvis.remote.ui.theme.JarvisDanger
import com.jarvis.remote.ui.theme.JarvisOrange
import com.jarvis.remote.ui.theme.JarvisSuccess
import com.jarvis.remote.ui.theme.JarvisSurfaceRaised
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(onOpenSession: (String) -> Unit) {
    HomeRoute(onOpenSession = onOpenSession)
}

const val HOME_ROUTE = "home"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    viewModel: HomeViewModel,
    state: HomeUiState,
    onOpenSession: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jarvis — Sessions") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (!state.connected) {
                ConnectivityPill()
            }
            ModeFilterRow(
                activeOnly = state.showActiveOnly,
                activeCount = state.activeCount,
                onModeChange = viewModel::selectMode,
            )
            if (state.projects.isNotEmpty()) {
                ProjectFilterRow(
                    projects = state.projects,
                    selectedProject = state.selectedProject,
                    onSelect = viewModel::selectProject,
                )
            }
            val pullState = rememberPullToRefreshState()
            LaunchedEffect(state.loading) {
                if (state.loading) pullState.startRefresh() else pullState.endRefresh()
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    state.sessions.isEmpty() && state.loading -> LoadingState(Modifier.fillMaxSize())
                    state.sessions.isEmpty() && state.error != null ->
                        ErrorState(state.error, viewModel::retry, Modifier.fillMaxSize())
                    state.sessions.isEmpty() -> EmptyState(Modifier.fillMaxSize())
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.sessions, key = { it.id }) { row ->
                            SessionCard(row = row, onOpenSession = onOpenSession)
                        }
                    }
                }
                PullToRefreshContainer(
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun ModeFilterRow(
    activeOnly: Boolean,
    activeCount: Int,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !activeOnly,
            onClick = { onModeChange(false) },
            label = { Text("All", style = MaterialTheme.typography.labelMedium) },
        )
        FilterChip(
            selected = activeOnly,
            onClick = { onModeChange(true) },
            label = {
                Text("Active ($activeCount)", style = MaterialTheme.typography.labelMedium)
            },
        )
    }
}

@Composable
private fun ProjectFilterRow(
    projects: List<Project>,
    selectedProject: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedProject == null,
            onClick = { onSelect(null) },
            label = { Text("All", style = MaterialTheme.typography.labelMedium) },
        )
        projects.forEach { project ->
            FilterChip(
                selected = selectedProject == project.worktree,
                onClick = { onSelect(project.worktree) },
                label = {
                    Text(
                        project.displayName(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

@Composable
private fun SessionCard(
    row: SessionRow,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onOpenSession(row.id) },
        shape = MaterialTheme.shapes.medium,
        color = if (row.busy) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIndicator(row.status)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                row.directory?.takeIf { it.isNotBlank() }?.let { directory ->
                    Text(
                        text = directory,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    row.agent?.takeIf { it.isNotBlank() }?.let { agent ->
                        Text(
                            text = agent,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisBlue,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    RelativeTime(row.lastActivityMillis)
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: RowStatus, modifier: Modifier = Modifier) {
    when (status) {
        RowStatus.RUNNING -> CircularProgressIndicator(
            modifier = modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = JarvisSuccess,
        )
        RowStatus.IDLE -> Box(
            modifier.size(10.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
        )
        RowStatus.ERROR -> Box(
            modifier.size(10.dp).background(JarvisDanger, CircleShape)
        )
        RowStatus.UNKNOWN -> Box(
            modifier.size(10.dp).background(MaterialTheme.colorScheme.outline, CircleShape)
        )
    }
}

@Composable
private fun RelativeTime(ms: Long?, modifier: Modifier = Modifier) {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L)
        }
    }
    Text(
        text = HomeRowMapper.formatRelative(ms, now),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ConnectivityPill(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = JarvisSurfaceRaised,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = JarvisOrange,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Disconnected — reconnecting…",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisOrange,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No sessions from this server yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "An opencode server only reports sessions from the project folder it was started in. " +
                    "Start one there with `opencode attach`, or connect to a different project's server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = JarvisDanger,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Couldn't reach the opencode server.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisOrange),
        ) {
            Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}