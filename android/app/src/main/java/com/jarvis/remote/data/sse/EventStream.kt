package com.jarvis.remote.data.sse

import com.jarvis.remote.data.model.OpenCodeEvent
import com.jarvis.remote.data.repo.basicAuthHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.atomic.AtomicBoolean

class EventStream(
    private val url: String,
    private val username: String,
    private val password: String,
    private val okHttp: OkHttpClient,
    private val backoff: Backoff = Backoff,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _events = MutableSharedFlow<OpenCodeEvent>(replay = 16, extraBufferCapacity = 64)
    private val _connected = MutableStateFlow(false)
    private val closed = AtomicBoolean(false)

    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var reconnectAttempt = 0

    @Volatile
    private var eventSource: EventSource? = null

    private val factory: EventSource.Factory = EventSources.createFactory(
        okHttp.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", basicAuthHeader(username, password))
                        .build()
                )
            }
            .build()
    )

    private val listener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            reconnectAttempt = 0
            _connected.value = true
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            EventJsonParser.parseEvent(data)?.let { _events.tryEmit(it) }
        }

        override fun onClosed(eventSource: EventSource) {
            _connected.value = false
            scheduleReconnect()
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            _connected.value = false
            scheduleReconnect()
        }
    }

    init {
        connect()
    }

    fun events(): Flow<OpenCodeEvent> = _events

    fun close() {
        closed.set(true)
        _connected.value = false
        eventSource?.cancel()
        eventSource = null
    }

    private fun connect() {
        if (closed.get()) return
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()
        eventSource = factory.newEventSource(request, listener)
    }

    private fun scheduleReconnect() {
        if (closed.get()) return
        val waitMs = backoff.nextDelay(reconnectAttempt)
        reconnectAttempt++
        scope.launch {
            if (waitMs > 0) delay(waitMs)
            if (!closed.get()) connect()
        }
    }
}