package com.jarvis.remote.ui.connect

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.remote.JarvisApplication
import com.jarvis.remote.data.CredentialStore
import com.jarvis.remote.data.QrDecoder
import com.jarvis.remote.data.SafetyLevel
import com.jarvis.remote.data.UrlSafety
import com.jarvis.remote.data.repo.OpenCodeClient
import com.jarvis.remote.jarvisViewModelFactory
import com.jarvis.remote.ui.theme.JarvisBlue
import com.jarvis.remote.ui.theme.JarvisDanger
import com.jarvis.remote.ui.theme.JarvisOrange
import com.jarvis.remote.ui.theme.JarvisSurfaceRaised

@Composable
fun ConnectScreen(onConnected: (ConnectResult.Success) -> Unit) {
    val app = LocalContext.current.applicationContext as JarvisApplication
    val vm: ConnectViewModel = viewModel(
        factory = jarvisViewModelFactory {
            ConnectViewModel(
                store = CredentialStore.forContext(app),
                clientBuilder = { profile ->
                    OpenCodeClient.build(
                        baseUrl = profile.baseUrl,
                        username = profile.username,
                        password = profile.password
                    )
                },
                application = app
            )
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()

    var showPassword by rememberSaveable { mutableStateOf(false) }
    var qrError by remember { mutableStateOf<String?>(null) }

    val qrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            qrError = null
            @Suppress("DEPRECATION")
            val bitmap: Bitmap? = result.data?.getParcelableExtra("data")
            val text = bitmap?.let { QrDecoder.decode(it) }
            if (text.isNullOrBlank()) {
                qrError = "No QR code found in that photo. Try a closer, sharper shot."
            } else {
                vm.connectFromQr(text)
            }
        }
    }

    LaunchedEffect(state.connectedProfile) {
        state.connectedProfile?.let { onConnected(ConnectResult.Success(it)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CONNECT TO JARVIS",
            style = MaterialTheme.typography.headlineMedium,
            color = JarvisOrange,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Point this phone at the PC running opencode serve.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = {
                if (state.scanningMdns) vm.stopMdnsScan() else vm.startMdnsScan()
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.scanningMdns) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("SCAN FOR PC ON THIS NETWORK", fontWeight = FontWeight.Bold, color = JarvisBlue)
            }
        }

        if (state.mdnsServices.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.mdnsServices.forEach { service ->
                    Surface(
                        onClick = { vm.connectFromMdns(service) },
                        shape = RoundedCornerShape(12.dp),
                        color = JarvisSurfaceRaised,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = service.serviceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${service.host}:${service.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "CONNECT",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.host,
            onValueChange = vm::onHostChange,
            label = { Text("Host or IP") },
            placeholder = { Text("192.168.1.5") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.port,
                onValueChange = vm::onPortChange,
                label = { Text("Port") },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = vm::onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.weight(2f),
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = vm::validateAndConfirm,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisOrange)
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("CONNECTING…", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            } else {
                Text("CONNECT", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "or",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(10.dp))
            IconButton(
                onClick = {
                    qrError = null
                    try {
                        qrLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                    } catch (e: Exception) {
                        qrError = "No camera available on this device."
                    }
                },
                enabled = !state.busy
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan QR code",
                    tint = JarvisBlue
                )
            }
            Text(
                text = "Scan a pair QR code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!state.connected) {
            (state.message ?: qrError)?.let { errorText ->
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisDanger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }

    if (state.selectedSafety == SafetyLevel.INTERNET) {
        AlertDialog(
            onDismissRequest = vm::backFromWarning,
            title = { Text("Connect over the internet?") },
            text = {
                Text(
                    text = "${state.host} doesn't look like a trusted LAN address, and this " +
                        "connection is plain HTTP. Your opencode password would be sent " +
                        "unencrypted over the open internet.\n\n" +
                        "Use a VPN (Tailscale/WireGuard) or an HTTPS reverse proxy instead, " +
                        "e.g. https://your-server:port.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmConnection) {
                    Text("Connect anyway", color = JarvisDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::backFromWarning) {
                    Text("Back")
                }
            }
        )
    }
}

const val CONNECT_ROUTE = "connect"