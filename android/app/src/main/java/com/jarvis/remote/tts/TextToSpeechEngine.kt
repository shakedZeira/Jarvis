package com.jarvis.remote.tts

interface TextToSpeechEngine {
    suspend fun speak(text: String): kotlin.Result<Unit>
    fun stop()
    fun isSpeaking(): Boolean
    fun setConfig(config: TtsConfig)
    fun shutdown()
}