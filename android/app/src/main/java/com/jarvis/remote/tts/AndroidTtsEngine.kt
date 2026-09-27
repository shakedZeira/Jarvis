package com.jarvis.remote.tts

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException

class AndroidTtsEngine(
    private val context: Context,
) : TextToSpeechEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var config = TtsConfig()
    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var initDeferred: CompletableDeferred<Unit>? = null
    private var isInitialized = false

    init {
        initDeferred = CompletableDeferred()
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        isInitialized = status == TextToSpeech.SUCCESS
        if (isInitialized) {
            applyConfig()
            initDeferred?.complete(Unit)
        } else {
            val error = "TTS initialization failed with status: $status"
            Log.e("AndroidTtsEngine", error)
            initDeferred?.completeExceptionally(RuntimeException(error))
        }
    }

    private fun applyConfig() {
        tts?.let { t ->
            val locale = Locale.forLanguageTag(config.language)
            val result = t.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("AndroidTtsEngine", "Language not supported: ${config.language}, falling back to default")
                t.setLanguage(Locale.getDefault())
            }
            config.voiceName?.let { voiceName ->
                val availableVoices = t.voices
                val voice = availableVoices.firstOrNull { it.name == voiceName }
                if (voice != null) {
                    t.setVoice(voice)
                    Log.d("AndroidTtsEngine", "Selected voice: $voiceName")
                } else {
                    Log.w("AndroidTtsEngine", "Voice not found: $voiceName. Available: ${availableVoices.map { it.name }}")
                }
            }
        }
    }

    override suspend fun speak(text: String): kotlin.Result<Unit> {
        return try {
            val deferred = ensureInitialized()
            deferred.await()
            speakInternal(text).await()
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    private suspend fun ensureInitialized(): CompletableDeferred<Unit> {
        if (isInitialized) return CompletableDeferred<Unit>().also { it.complete(Unit) }
        return initDeferred ?: CompletableDeferred<Unit>().also { it.completeExceptionally(IllegalStateException("TTS not initialized")) }
    }

    private suspend fun speakInternal(text: String): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        val utteranceId = "utterance_${System.currentTimeMillis()}"

        pendingUtterances[utteranceId] = deferred

        tts?.let { t ->
            val params = config.toTtsParams().apply {
                putString("utteranceId", utteranceId)
            }
            val result = t.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Log.e("AndroidTtsEngine", "TTS speak failed for: $text")
                pendingUtterances.remove(utteranceId)
                deferred.completeExceptionally(RuntimeException("TTS speak failed"))
            }
        } ?: run {
            deferred.completeExceptionally(IllegalStateException("TTS not initialized"))
        }

        return deferred
    }

    override fun stop() {
        tts?.stop()
        pendingUtterances.values.forEach { it.completeExceptionally(CancellationException("TTS stopped")) }
        pendingUtterances.clear()
    }

    override fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    override fun setConfig(newConfig: TtsConfig) {
        config = newConfig
        if (isInitialized) {
            applyConfig()
        }
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private inner class TtsUtteranceListener : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            Log.d("AndroidTtsEngine", "Speaking started: $utteranceId")
        }

        override fun onDone(utteranceId: String) {
            pendingUtterances.remove(utteranceId)?.complete(Unit)
            Log.d("AndroidTtsEngine", "Speaking done: $utteranceId")
        }

        override fun onError(utteranceId: String) {
            pendingUtterances.remove(utteranceId)?.completeExceptionally(RuntimeException("TTS error for $utteranceId"))
            Log.e("AndroidTtsEngine", "TTS error: $utteranceId")
        }
    }
}