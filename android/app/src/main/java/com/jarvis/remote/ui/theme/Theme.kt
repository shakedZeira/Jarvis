package com.jarvis.remote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisOrange,
    onPrimary = Color.Black,
    secondary = JarvisBlue,
    onSecondary = Color.Black,
    tertiary = JarvisOffWhite,
    onTertiary = JarvisDark,
    background = JarvisDark,
    onBackground = JarvisOffWhite,
    surface = JarvisSurface,
    onSurface = JarvisOffWhite,
    surfaceVariant = JarvisSurfaceRaised,
    onSurfaceVariant = Color(0xFF9AA7B8),
    error = JarvisDanger,
    onError = Color.White
)

private val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        shapes = JarvisShapes,
        typography = JarvisTypography,
        content = content
    )
}