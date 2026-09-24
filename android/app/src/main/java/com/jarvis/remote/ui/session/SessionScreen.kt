package com.jarvis.remote.ui.session

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.remote.JarvisApplication
import com.jarvis.remote.jarvisViewModelFactory

const val SESSION_ROUTE = "session"
private const val STREAMING_LIVE_KEY = "streaming-live"

@Composable
fun SessionScreen(sessionID: String, onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: SessionViewModel = viewModel(
        factory = jarvisViewModelFactory { provideSessionViewModel(application, sessionID) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var draft by rememberSaveable { mutableStateOf("") }

    DisposableEffect(sessionID) {
        onDispose { viewModel.dispose() }
    }

    LaunchedEffect(state.items.size, state.streamingText?.length) {
        val last = listState.layoutInfo.totalItemsCount
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Scaffold(
        topBar = {
            SessionTopBar(
                title = state.title?.takeIf { it.isNotBlank() } ?: shortSessionId(sessionID),
                running = if (state.busy) state.running else null,
                busy = state.busy,
                error = state.error,
                onBack = onBack,
                onAbort = viewModel::abort,
            )
        },
        bottomBar = {
            ChatComposer(
                value = draft,
                onValueChange = { draft = it },
                sending = state.sending,
                onSend = viewModel::send,
                onSendConsumed = { draft = "" },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.error?.let { ErrorBanner(it) }
            if (state.disconnected) {
                Text(
                    text = "Disconnected — retrying…",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.key }) { item ->
                    MessageView(item)
                }
                val streamingText = state.streamingText
                if (streamingText != null) {
                    item(key = STREAMING_LIVE_KEY) {
                        AssistantBubble(text = streamingText, streaming = true)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopBar(
    title: String,
    running: String?,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onAbort: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (running != null) {
                    Text(
                        text = running,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            StatusChip(busy = busy, error = error)
            if (busy) {
                IconButton(onClick = onAbort) {
                    Icon(Icons.Filled.Stop, contentDescription = "Abort")
                }
            }
        },
    )
}

@Composable
private fun StatusChip(busy: Boolean, error: String?) {
    val label: String
    val color: Color
    when {
        error != null -> {
            label = "● Error"
            color = MaterialTheme.colorScheme.error
        }
        busy -> {
            label = "● Working"
            color = MaterialTheme.colorScheme.primary
        }
        else -> {
            label = "● Idle"
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun provideSessionViewModel(app: Application, sessionID: String): SessionViewModel {
    val container = (app as JarvisApplication).container
    return SessionViewModel(
        client = container.opencodeClient,
        events = container.events,
        sessionID = sessionID,
        app = app,
    )
}

private fun shortSessionId(sessionID: String): String =
    if (sessionID.length > 14) "…${sessionID.takeLast(8)}" else sessionID