package com.flowforge.android.ui.theme

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

private val Violet = Color(0xFF6D3BF5)
private val VioletLight = Color(0xFFB9A2FF)
private val Teal = Color(0xFF00A870)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF23005C),
    primaryContainer = Color(0xFF4B21C7),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Teal,
    background = Color(0xFF121016),
    surface = Color(0xFF1A1720),
    surfaceVariant = Color(0xFF272231),
    onSurfaceVariant = Color(0xFFC9C2D6),
)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF23005C),
    secondary = Teal,
    background = Color(0xFFFBF8FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDE8F5),
    onSurfaceVariant = Color(0xFF4A4458),
)

@Composable
fun FlowForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
