package com.jarvis.remote.ui.home

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.remote.JarvisApplication
import com.jarvis.remote.data.model.SessionStatus
import com.jarvis.remote.data.repo.OpenCodeClient
import com.jarvis.remote.jarvisViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Composable
fun HomeRoute(onOpenSession: (String) -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: HomeViewModel = viewModel(
        factory = jarvisViewModelFactory { provideHomeViewModel(application) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(viewModel = viewModel, state = state, onOpenSession = onOpenSession)
}

private fun provideHomeViewModel(app: Application): HomeViewModel {
    val container = (app as JarvisApplication).container
    val client = container.opencodeClient
    return HomeViewModel(
        client = client,
        events = container.events,
        statusFlow = statusPoll(client),
        app = app,
    )
}

private fun statusPoll(client: OpenCodeClient): Flow<Map<String, SessionStatus>> = flow {
    while (true) {
        runCatching { client.sessionStatus() }
            .onSuccess { emit(it) }
            .onFailure { throw it }
        delay(2_000L)
    }
}