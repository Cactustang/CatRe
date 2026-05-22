package com.catre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFF47C6B),
    secondary = Color(0xFF58BFA3),
    tertiary = Color(0xFFF5B84B),
    background = Color(0xFFFFF8F2),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A8),
    secondary = Color(0xFF8FE0CA),
    tertiary = Color(0xFFFFD27A),
    background = Color(0xFF211A18),
    surface = Color(0xFF2A211F),
    onPrimary = Color(0xFF5A150C),
    onSecondary = Color(0xFF00382D),
    onTertiary = Color(0xFF442B00),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5)
)

@Composable
fun CatReTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
