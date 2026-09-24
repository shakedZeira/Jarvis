package com.jarvis.remote.notify

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

class ConnectivityMonitor(
    context: Context,
    private val onOnline: () -> Unit
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile
    private var wasOffline = false

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (wasOffline) {
                wasOffline = false
                onOnline()
            }
        }

        override fun onLost(network: Network) {
            wasOffline = true
        }
    }

    fun start() {
        if (registered) return
        registered = true
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}