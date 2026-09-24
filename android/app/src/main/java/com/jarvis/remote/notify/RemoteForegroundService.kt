package com.jarvis.remote.notify

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jarvis.remote.data.ConnectionProfile
import com.jarvis.remote.data.CredentialStore
import com.jarvis.remote.data.sse.EventStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class RemoteForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var eventStream: EventStream? = null
    private var streamJob: Job? = null
    private var connectedJob: Job? = null
    private var connectivityMonitor: ConnectivityMonitor? = null
    private var profile: ConnectionProfile? = null
    @Volatile private var connected = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)

        val loadedProfile = CredentialStore(this).loadDefault()
        if (loadedProfile == null) {
            stopSelf()
            return
        }
        profile = loadedProfile
        startStream(loadedProfile)

        connectivityMonitor = ConnectivityMonitor(this) { reconnect() }.also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            foregroundNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Leave the service running; START_STICKY will re-deliver the command.
    }

    override fun onDestroy() {
        connectivityMonitor?.stop()
        connectivityMonitor = null
        streamJob?.cancel()
        connectedJob?.cancel()
        streamJob = null
        connectedJob = null
        scope.cancel()
        eventStream = null
        super.onDestroy()
    }

    private fun reconnect() {
        val current = profile ?: return
        startStream(current)
    }

    private fun startStream(target: ConnectionProfile) {
        streamJob?.cancel()
        connectedJob?.cancel()

        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = Base64.encodeToString(
                    "${target.username}:${target.password}".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                val request = chain.request().newBuilder()
                    .header("Authorization", "Basic $token")
                    .build()
                chain.proceed(request)
            }
            .build()

        val stream = EventStream(
            url = target.baseUrl.trimEnd('/') + "/event",
            username = target.username,
            password = target.password,
            okHttp = okHttp
        )
        eventStream = stream

        streamJob = scope.launch {
            stream.events().collect { event ->
                val alert = EventNotifier.map(event) ?: return@collect
                NotificationHelper.notify(
                    this@RemoteForegroundService,
                    alert.channel,
                    channelId(alert.channel),
                    alert.title,
                    alert.message
                )
            }
        }

        connectedJob = scope.launch {
            stream.connected.collect { isConnected ->
                connected = isConnected
                NotificationManagerCompat.from(this@RemoteForegroundService)
                    .notify(FOREGROUND_ID, foregroundNotification())
            }
        }
    }

    private fun foregroundNotification(): Notification =
        NotificationHelper.foregroundNotification(this, connectionStatus())

    private fun connectionStatus(): String =
        if (connected) "Connected" else "Reconnecting…"

    private fun channelId(channel: String): Int = when (channel) {
        NotificationHelper.CHANNEL_WORK -> ID_WORK
        NotificationHelper.CHANNEL_ERROR -> ID_ERROR
        NotificationHelper.CHANNEL_PERMISSION -> ID_PERMISSION
        else -> FOREGROUND_ID
    }

    companion object {
        private const val ID_WORK = 1001
        private const val ID_ERROR = 1002
        private const val ID_PERMISSION = 1003
        private const val FOREGROUND_ID = 1004
        private const val EXTRA_PROFILE = "com.jarvis.remote.notify.EXTRA_PROFILE"

        fun start(context: Context, profile: ConnectionProfile) {
            val intent = Intent(context, RemoteForegroundService::class.java).apply {
                putExtra(EXTRA_PROFILE, Json.encodeToString(ConnectionProfile.serializer(), profile))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteForegroundService::class.java))
        }
    }
}