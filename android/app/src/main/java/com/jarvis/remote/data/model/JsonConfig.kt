package com.jarvis.remote.data.model

import kotlinx.serialization.json.Json

val JarvisJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}