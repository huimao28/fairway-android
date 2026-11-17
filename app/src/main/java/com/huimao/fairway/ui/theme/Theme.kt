package com.huimao.fairway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6B34),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE9C5),
    onPrimaryContainer = Color(0xFF0B3D1C),
    secondary = Color(0xFF4C6B4F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E8D2),
    onSecondaryContainer = Color(0xFF1E3A22),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF3A2700),
    surface = Color(0xFFF7FBF3),
    onSurface = Color(0xFF1A211B),
    surfaceContainerLow = Color(0xFFEAF3E8),
    surfaceContainer = Color(0xFFDDE8DA),
    surfaceContainerHigh = Color(0xFFD0DDCF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BC98A),
    onPrimary = Color(0xFF0B3D1C),
    primaryContainer = Color(0xFF1E5B2E),
    onPrimaryContainer = Color(0xFFBFE9C5),
    secondary = Color(0xFFA9C7A8),
    onSecondary = Color(0xFF1E3A22),
    secondaryContainer = Color(0xFF334D36),
    onSecondaryContainer = Color(0xFFD4E8D2),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF3A2700),
    tertiaryContainer = Color(0xFF6B4A00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    surface = Color(0xFF111712),
    onSurface = Color(0xFFE4EDE3),
    surfaceContainerLow = Color(0xFF18211A),
    surfaceContainer = Color(0xFF1F2A21),
    surfaceContainerHigh = Color(0xFF2A352B),
    error = Color(0xFFFFB4AB),
)

/** Material 3 Expressive 主题（果岭绿主色）。 */
@Composable
fun FairwayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
