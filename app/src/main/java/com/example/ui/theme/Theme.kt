package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DesktopXPrimary,
    secondary = DesktopXSecondary,
    tertiary = DesktopXAccent,
    background = DesktopXBackground,
    surface = DesktopXSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = DesktopXTextPrimary,
    onSurface = DesktopXTextPrimary,
    surfaceVariant = DesktopXSurfaceBorder,
    onSurfaceVariant = DesktopXTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark theme for premium futuristic DesktopX experience
    dynamicColor: Boolean = false, // Disable dynamic colors by default to preserve the strict sci-fi cyber aesthetic
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        dynamicDarkColorScheme(context)
    } else {
        DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
