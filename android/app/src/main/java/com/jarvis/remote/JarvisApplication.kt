package com.jarvis.remote

import android.app.Application

class JarvisApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}