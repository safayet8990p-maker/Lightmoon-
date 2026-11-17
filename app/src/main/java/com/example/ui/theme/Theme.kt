package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkroomColorScheme = darkColorScheme(
    primary = OpticalSilver,
    onPrimary = StudioBackground,
    primaryContainer = StudioSurfaceHighest,
    onPrimaryContainer = OpticalSilver,
    secondary = StudioCyan,
    onSecondary = Color(0xFF041D28),
    secondaryContainer = StudioCyanContainer,
    onSecondaryContainer = Color(0xFFC9EEFF),
    tertiary = DarkroomAmber,
    onTertiary = Color(0xFF261800),
    tertiaryContainer = DarkroomAmberContainer,
    onTertiaryContainer = Color(0xFFFFDFA6),
    background = StudioBackground,
    onBackground = OpticalSilver,
    surface = StudioSurface,
    onSurface = OpticalSilver,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = OpticalSilverDim,
    surfaceContainerLowest = StudioBackground,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceElevated,
    surfaceContainerHighest = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutlineVariant,
    error = StudioDanger,
    onError = Color.White
)

private val DarkroomShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun LightmoonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkroomColorScheme,
        typography = LightmoonTypography,
        shapes = DarkroomShapes,
        content = content
    )
}
