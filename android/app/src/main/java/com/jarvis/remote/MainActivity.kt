package com.jarvis.remote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jarvis.remote.notify.RemoteForegroundService
import com.jarvis.remote.ui.connect.CONNECT_ROUTE
import com.jarvis.remote.ui.connect.ConnectScreen
import com.jarvis.remote.ui.home.HOME_ROUTE
import com.jarvis.remote.ui.home.HomeScreen
import com.jarvis.remote.ui.session.SESSION_ROUTE
import com.jarvis.remote.ui.session.SessionScreen
import com.jarvis.remote.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                AppNav()
            }
        }
    }
}

@Composable
private fun AppNav(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext as JarvisApplication
    val container = context.container

    val savedProfile = remember {
        container.credentialStore.loadDefault()?.takeIf { it.baseUrl.isNotBlank() }
    }

    LaunchedEffect(savedProfile) {
        if (savedProfile != null) {
            container.connect(savedProfile)
            RemoteForegroundService.start(context, savedProfile)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result observed by the notification channels */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (savedProfile != null) HOME_ROUTE else CONNECT_ROUTE,
        modifier = modifier
    ) {
        composable(CONNECT_ROUTE) {
            ConnectScreen(
                onConnected = { success ->
                    container.connect(success.profile)
                    RemoteForegroundService.start(context, success.profile)
                    navController.navigate(HOME_ROUTE) {
                        popUpTo(CONNECT_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        composable(HOME_ROUTE) {
            HomeScreen(
                onOpenSession = { sessionID ->
                    navController.navigate("$SESSION_ROUTE/$sessionID")
                }
            )
        }

        composable(
            route = "$SESSION_ROUTE/{sessionID}",
            arguments = listOf(
                navArgument("sessionID") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionID = backStackEntry.arguments?.getString("sessionID").orEmpty()
            SessionScreen(
                sessionID = sessionID,
                onBack = { navController.popBackStack() }
            )
        }
    }
}