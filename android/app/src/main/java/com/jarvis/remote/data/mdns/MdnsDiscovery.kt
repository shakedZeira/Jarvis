package com.jarvis.remote.data.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

data class MdnsService(
    val host: String,
    val port: Int,
    val serviceName: String
)

class MdnsDiscovery(context: Context) {

    companion object {
        private const val TAG = "JarvisMdns"
        private const val SERVICE_PRIMARY = "_opencode._tcp"
        private const val SERVICE_FALLBACK = "_http._tcp"
        private const val SCAN_TIMEOUT_MS = 6_000L
    }

    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val discovered = LinkedHashMap<String, MdnsService>()
    private var currentListener: ((List<MdnsService>) -> Unit)? = null
    private var running = false
    private var stopRequested = false
    private var triedFallback = false

    private val timeoutRunnable = Runnable { onScanWindowElapsed() }

    fun start(listener: (List<MdnsService>) -> Unit) {
        stopInternal()
        running = true
        stopRequested = false
        triedFallback = false
        discovered.clear()
        currentListener = listener
        discover(SERVICE_PRIMARY)
        mainHandler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS)
    }

    fun stop() {
        if (!running) return
        stopRequested = true
        finish()
    }

    private fun discover(type: String) {
        try {
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (t: Throwable) {
            Log.i(TAG, "discoverServices($type) failed: $t")
            mainHandler.post {
                if (type != SERVICE_FALLBACK && discovered.isEmpty() && !triedFallback) {
                    tryFallback()
                } else {
                    finish()
                }
            }
        }
    }

    private fun onScanWindowElapsed() {
        if (stopRequested || !running) {
            finish()
            return
        }
        if (discovered.isEmpty() && !triedFallback) {
            tryFallback()
        } else {
            finish()
        }
    }

    private fun tryFallback() {
        if (stopRequested || !running) {
            finish()
            return
        }
        triedFallback = true
        discover(SERVICE_FALLBACK)
        mainHandler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS)
    }

    private fun finish() {
        if (!running) return
        running = false
        stopInternal()
        val snapshot = discovered.values.toList()
        currentListener?.invoke(snapshot)
        currentListener = null
    }

    private fun stopInternal() {
        mainHandler.removeCallbacks(timeoutRunnable)
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (t: Throwable) {
            Log.i(TAG, "stopServiceDiscovery failed: $t")
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.i(TAG, "Discovery started: $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            val type = serviceInfo.serviceType
            if (type != "${SERVICE_PRIMARY}.local." && type != "${SERVICE_FALLBACK}.local.") return
            try {
                nsdManager.resolveService(serviceInfo, resolveListener)
            } catch (t: Throwable) {
                Log.i(TAG, "resolveService failed: $t")
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.i(TAG, "Service lost: ${serviceInfo.serviceName}")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.i(TAG, "Start discovery failed ($errorCode): $serviceType")
            if (serviceType == SERVICE_PRIMARY && discovered.isEmpty() && !triedFallback && running && !stopRequested) {
                tryFallback()
            } else if (running) {
                finish()
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.i(TAG, "Stop discovery failed ($errorCode): $serviceType")
            finish()
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.i(TAG, "Resolve failed ($errorCode): ${serviceInfo.serviceName}")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host?.hostAddress
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: serviceInfo.serviceName.substringBefore('.').takeIf { it.isNotEmpty() }
            if (host != null || serviceInfo.port > 0) {
                val service = MdnsService(
                    host = host.orEmpty(),
                    port = serviceInfo.port,
                    serviceName = serviceInfo.serviceName
                )
                discovered["${service.host}:${service.port}"] = service
            }
        }
    }
}