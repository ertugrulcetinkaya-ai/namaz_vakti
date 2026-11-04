package com.example.namazvakti.ui.main

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandGreen = Color(0xFF12372A)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E9DE),
    onPrimaryContainer = Color(0xFF10291E),
    secondary = Color(0xFF52665A),
    background = Color(0xFFF7F8F4),
    surface = Color(0xFFF7F8F4),
    surfaceVariant = Color(0xFFE5E9E3),
    onSurface = Color(0xFF191D19),
    onSurfaceVariant = Color(0xFF424A43)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3D3BF),
    onPrimary = Color(0xFF1C382A),
    primaryContainer = BrandGreen,
    onPrimaryContainer = Color(0xFFD5F1DE),
    secondary = Color(0xFFB8CCBD),
    background = Color(0xFF101511),
    surface = Color(0xFF101511),
    surfaceVariant = Color(0xFF404940),
    onSurface = Color(0xFFE1E4DE),
    onSurfaceVariant = Color(0xFFC1C9C0)
)

@Composable
fun NamazVaktiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colors, content = content)
}
