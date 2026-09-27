package com.jarvis.remote

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jarvis.remote.data.ConnectionProfile
import com.jarvis.remote.data.CredentialStore
import com.jarvis.remote.data.model.OpenCodeEvent
import com.jarvis.remote.data.repo.OpenCodeClient
import com.jarvis.remote.data.sse.EventStream
import com.jarvis.remote.notify.VoiceNotificationCoordinator
import com.jarvis.remote.tts.AndroidTtsEngine
import com.jarvis.remote.tts.TextToSpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.OkHttpClient

class AppContainer(private val application: Application) {

    val credentialStore: CredentialStore = CredentialStore.forContext(application)

    private val ttsJob = SupervisorJob()
    private val ttsScope = CoroutineScope(ttsJob + Dispatchers.IO)
    val ttsEngine: TextToSpeechEngine = AndroidTtsEngine(application)

    @Volatile
    private var _opencodeClient: OpenCodeClient? = null

    @Volatile
    private var _eventStream: EventStream? = null

    @Volatile
    private var _profile: ConnectionProfile? = null

    val profile: ConnectionProfile? get() = _profile

    val opencodeClient: OpenCodeClient
        get() = checkNotNull(_opencodeClient) { "No profile connected yet" }

    val eventStream: EventStream? get() = _eventStream

    val events: Flow<OpenCodeEvent>
        get() = _eventStream?.events() ?: emptyFlow()

    val voiceNotificationCoordinator: VoiceNotificationCoordinator by lazy {
        VoiceNotificationCoordinator(
            context = application,
            client = opencodeClient,
            ttsEngine = ttsEngine,
            scope = ttsScope
        )
    }

    fun connect(profile: ConnectionProfile) {
        _profile = profile
        credentialStore.save(profile)
        credentialStore.setDefault(profile.name)
        _eventStream?.close()
        _opencodeClient = OpenCodeClient.build(profile.baseUrl, profile.username, profile.password)
        _eventStream = EventStream(
            url = "${profile.baseUrl.trimEnd('/')}/event",
            username = profile.username,
            password = profile.password,
            okHttp = OkHttpClient.Builder().build()
        )
    }

    fun disconnect() {
        _eventStream?.close()
        _eventStream = null
        _opencodeClient = null
        _profile = null
    }

    fun shutdown() {
        ttsJob.cancel()
        ttsEngine.shutdown()
    }
}

inline fun <reified VM : ViewModel> jarvisViewModelFactory(
    crossinline create: () -> VM
): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }