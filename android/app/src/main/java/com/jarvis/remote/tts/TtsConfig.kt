package com.jarvis.remote.tts

import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class TtsConfig(
    val language: String = Locale.getDefault().language,
    val pitch: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val voiceName: String? = null,
    val useElevenLabs: Boolean = false,
    val elevenLabsApiKey: String? = null,
) {
    fun toTtsParams(): Bundle = Bundle().apply {
        putFloat("pitch", pitch)
        putFloat("speechRate", speechRate)
        voiceName?.let { putString("voiceName", it) }
        putString("language", language)
    }
}