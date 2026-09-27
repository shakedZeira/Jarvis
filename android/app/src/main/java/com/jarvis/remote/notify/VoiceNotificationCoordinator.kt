package com.jarvis.remote.notify

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.app.Notification
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jarvis.remote.MainActivity
import com.jarvis.remote.R
import com.jarvis.remote.data.model.Message
import com.jarvis.remote.data.repo.OpenCodeClient
import com.jarvis.remote.tts.TextToSpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.runCatching

class VoiceNotificationCoordinator(
    private val context: Context,
    private val client: OpenCodeClient,
    val ttsEngine: TextToSpeechEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    companion object {
        private const val SUMMARY_PROMPT = "Summarize what was just completed in one conversational sentence for a voice notification."
        private const val SUMMARY_POLL_INTERVAL_MS = 1500L
        private const val SUMMARY_TIMEOUT_MS = 60000L
        const val CHANNEL_VOICE_REPLY = "jarvis_voice_reply"
        const val NOTIFICATION_ID_VOICE_REPLY = 3001
        const val ACTION_REPLY_VOICE = "com.jarvis.remote.ACTION_REPLY_VOICE"
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = android.app.NotificationChannel(
            CHANNEL_VOICE_REPLY,
            "Voice Reply",
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications with voice reply action"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun onSessionIdle(sessionId: String) {
        scope.launch {
            runCatching {
                val summary = generateSummary(sessionId)
                speakAndNotify(sessionId, summary)
            }.onFailure { e ->
                android.util.Log.e("VoiceNotificationCoordinator", "Failed to handle session idle", e)
            }
        }
    }

    private suspend fun generateSummary(sessionId: String): String {
        // Send the summary prompt
        client.sendPrompt(sessionId, SUMMARY_PROMPT)

        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < SUMMARY_TIMEOUT_MS) {
            delay(SUMMARY_POLL_INTERVAL_MS)

            val messages = client.messages(sessionId, limit = 50)
            val userPromptIndex = messages.indexOfFirst {
                it.info.role == "user" && it.userText().trim() == SUMMARY_PROMPT.trim()
            }

            if (userPromptIndex >= 0) {
                // Look for assistant response after the user prompt
                val assistantResponse = messages.drop(userPromptIndex + 1)
                    .firstOrNull { it.info.role == "assistant" && it.info.finish != null }

                if (assistantResponse != null) {
                    val summaryText = assistantResponse.assistantText().trim()
                    if (summaryText.isNotBlank()) {
                        return summaryText
                    }
                }
            }
        }

        // Fallback: return a generic message
        return "Work completed on session ${sessionId.take(8)}"
    }

    private suspend fun speakAndNotify(sessionId: String, summary: String) {
        val player = VoiceNotificationPlayer(context, ttsEngine, scope)

        // Speak the summary
        player.speakSummary(sessionId, summary)

        // After TTS finishes, show the notification with "Reply by Voice" action
        showVoiceReplyNotification(sessionId, summary)
    }

    private fun showVoiceReplyNotification(sessionId: String, summary: String) {
        val replyIntent = Intent(context, VoiceInteractionActivity::class.java).apply {
            putExtra(VoiceInteractionActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(VoiceInteractionActivity.EXTRA_INITIAL_PROMPT, "What would you like me to do next?")
            action = ACTION_REPLY_VOICE
        }

        val replyPendingIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            replyIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_VOICE_REPLY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Work completed")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_mic_placeholder,
                    "Reply by Voice",
                    replyPendingIntent
                ).build()
            )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_VOICE_REPLY, notification)
    }
}

// Extension function for Message
fun Message.assistantText(): String =
    if (info.role == "assistant") plainText() else ""

// Placeholder activity for voice interaction (to be implemented by Sub-Agent 3)
class VoiceInteractionActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_INITIAL_PROMPT = "initial_prompt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Implement voice interaction UI
        finish()
    }
}