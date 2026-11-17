package com.huimao.fairway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

/** Expressive 圆角（大圆角是 M3 Expressive 的视觉特征）。 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(48.dp),
)

/**
 * Material 3 Expressive 主题（果岭绿主色）。
 * 注意：BOM 2025.04.01 的 material3 1.3.1 里 MaterialExpressiveTheme 还是 internal，
 * 所以用 MaterialTheme + Expressive 圆角实现同样的视觉效果；以后升 BOM 到
 * 2026.09.00（material3 1.4.0）后可直接换回 MaterialExpressiveTheme。
 */
@Composable
fun FairwayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ExpressiveShapes,
        content = content,
    )
}
