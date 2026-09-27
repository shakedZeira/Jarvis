package com.jarvis.remote.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jarvis.remote.R
import com.jarvis.remote.tts.TextToSpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceNotificationPlayer(
    private val context: Context,
    private val ttsEngine: TextToSpeechEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    companion object {
        private const val CHANNEL_TTS = "jarvis_tts"
        private const val NOTIFICATION_ID_TTS = 2001
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_TTS,
            "Voice Notifications",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for TTS playback"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    suspend fun speakSummary(sessionId: String, summary: String) {
        val fullText = "$summary. What would you like me to do next?"

        showSpeakingNotification(sessionId)

        try {
            ttsEngine.speak(fullText).getOrThrow()
        } catch (e: Exception) {
            // TTS failed, clear notification
            clearNotification()
            throw e
        }

        // Wait for TTS to finish speaking
        while (ttsEngine.isSpeaking()) {
            kotlinx.coroutines.delay(100)
        }

        clearNotification()
    }

    fun stop() {
        ttsEngine.stop()
        clearNotification()
    }

    private fun showSpeakingNotification(sessionId: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_TTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Jarvis")
            .setContentText("Speaking summary…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Speaking summary…"))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TTS, notification)
    }

    private fun clearNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_TTS)
    }
}