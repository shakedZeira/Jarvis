package com.jarvis.remote.ui.connect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.remote.data.ConnectionProfile
import com.jarvis.remote.data.CredentialStore
import com.jarvis.remote.data.SafetyLevel
import com.jarvis.remote.data.UrlSafety
import com.jarvis.remote.data.parseQr
import com.jarvis.remote.data.mdns.MdnsDiscovery
import com.jarvis.remote.data.mdns.MdnsService
import com.jarvis.remote.data.model.Health
import com.jarvis.remote.data.repo.ApiException
import com.jarvis.remote.data.repo.OpenCodeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val host: String = "",
    val port: String = "4096",
    val username: String = "opencode",
    val password: String = "",
    val qrText: String? = null,
    val mdnsServices: List<MdnsService> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val selectedSafety: SafetyLevel? = null,
    val connected: Boolean = false,
    val connectedProfile: ConnectionProfile? = null,
    val scanningMdns: Boolean = false
)

sealed interface ConnectResult {
    data class Success(val profile: ConnectionProfile) : ConnectResult
    data class AuthError(val message: String) : ConnectResult
    data class NetworkError(val message: String) : ConnectResult
}

class ConnectViewModel(
    private val store: CredentialStore,
    private val clientBuilder: (ConnectionProfile) -> OpenCodeClient,
    application: Application
) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(ConnectUiState())
    val ui = _ui.asStateFlow()

    private val mdns: MdnsDiscovery by lazy { MdnsDiscovery(application) }

    override fun onCleared() {
        mdns.stop()
        super.onCleared()
    }

    fun onHostChange(value: String) = _ui.update { it.copy(host = value, message = null) }
    fun onPortChange(value: String) = _ui.update {
        it.copy(port = value.filter(Char::isDigit).take(5), message = null)
    }
    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value, message = null) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, message = null) }
    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun startMdnsScan() {
        if (_ui.value.scanningMdns) return
        _ui.update { it.copy(scanningMdns = true, message = null) }
        mdns.start { services ->
            _ui.update { it.copy(mdnsServices = services, scanningMdns = false) }
        }
    }

    fun stopMdnsScan() {
        mdns.stop()
        _ui.update { it.copy(scanningMdns = false) }
    }

    fun validateAndConfirm() {
        val state = _ui.value
        if (state.busy) return
        val profile = buildProfile() ?: return
        if (UrlSafety.describe(profile.baseUrl) == SafetyLevel.INTERNET) {
            _ui.update { it.copy(selectedSafety = SafetyLevel.INTERNET) }
        } else {
            confirmConnection()
        }
    }

    fun confirmConnection() {
        val state = _ui.value
        if (state.busy) return
        val profile = buildProfile() ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null, selectedSafety = null) }
            when (val result = probe(profile)) {
                is ConnectResult.Success -> onConnected(profile)
                is ConnectResult.AuthError -> {
                    _ui.update { it.copy(busy = false, message = result.message) }
                }
                is ConnectResult.NetworkError -> {
                    _ui.update { it.copy(busy = false, message = result.message) }
                }
            }
        }
    }

    fun connectFromMdns(service: MdnsService) {
        _ui.update {
            it.copy(host = service.host, port = service.port.toString(), message = null)
        }
        confirmConnection()
    }

    fun connectFromQr(text: String) {
        val profile = parseQr(text)
        if (profile == null) {
            _ui.update {
                it.copy(
                    message = "That QR code doesn't look like a Jarvis connection link.",
                    qrText = text
                )
            }
            return
        }
        _ui.update {
            it.copy(
                host = profile.displayHost,
                port = (profile.port ?: 4096).toString(),
                username = profile.username,
                password = profile.password,
                qrText = text,
                message = null
            )
        }
        confirmConnection()
    }

    fun backFromWarning() = _ui.update { it.copy(selectedSafety = null) }

    private fun onConnected(profile: ConnectionProfile) {
        store.save(profile)
        store.setDefault(profile.name)
        _ui.update {
            it.copy(
                busy = false,
                connected = true,
                connectedProfile = profile,
                message = "Connected to ${profile.displayHost}"
            )
        }
    }

    private fun buildProfile(): ConnectionProfile? {
        val s = _ui.value
        var host = s.host.trim().trimEnd('/')
        if (host.isEmpty()) {
            _ui.update { it.copy(message = "Enter the PC's IP or hostname.") }
            return null
        }
        if (host.contains("://")) host = host.substringAfter("://")
        host = host.substringBefore('/').trimEnd('/')

        var portText = s.port.trim()
        if (!host.startsWith("[") && host.count { it == ':' } == 1) {
            val inline = host.substringAfter(':')
            if (inline.isNotEmpty() && inline.all(Char::isDigit) && (portText.isEmpty() || portText == "4096")) {
                portText = inline
                host = host.substringBefore(':')
            }
        }

        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            _ui.update { it.copy(message = "Port must be a number between 1 and 65535.") }
            return null
        }
        val username = s.username.trim().ifEmpty { "opencode" }
        return ConnectionProfile(
            name = "$host:$port",
            baseUrl = "http://$host:$port",
            username = username,
            password = s.password
        )
    }

    private suspend fun probe(profile: ConnectionProfile): ConnectResult =
        runCatching { clientBuilder(profile).health() }
            .fold(
                onSuccess = { health: Health ->
                    if (health.healthy) {
                        ConnectResult.Success(profile)
                    } else {
                        ConnectResult.NetworkError("Server at ${profile.displayHost} didn't report healthy.")
                    }
                },
                onFailure = { error ->
                    if (error.isAuthFailure()) {
                        ConnectResult.AuthError("Wrong username or password (HTTP 401).")
                    } else {
                        ConnectResult.NetworkError(
                            "Couldn't reach ${profile.displayHost}. ${error.rootMessage()}"
                        )
                    }
                }
            )

    private fun Throwable.isAuthFailure(): Boolean {
        if (this is ApiException && code == 401) return true
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty()
            if ("401" in message ||
                message.contains("unauthorized", ignoreCase = true) ||
                message.contains("authentication failed", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun Throwable.rootMessage(): String {
        var current: Throwable? = this
        var message = ""
        while (current != null && message.isEmpty()) {
            message = current.message.orEmpty()
            current = current.cause
        }
        return message
    }
}