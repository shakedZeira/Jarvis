package com.jarvis.remote

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jarvis.remote.data.ConnectionProfile
import com.jarvis.remote.data.CredentialStore
import com.jarvis.remote.data.model.OpenCodeEvent
import com.jarvis.remote.data.repo.OpenCodeClient
import com.jarvis.remote.data.sse.EventStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.OkHttpClient

class AppContainer(private val application: Application) {

    val credentialStore: CredentialStore = CredentialStore.forContext(application)

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
}

inline fun <reified VM : ViewModel> jarvisViewModelFactory(
    crossinline create: () -> VM
): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }