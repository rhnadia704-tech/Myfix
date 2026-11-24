package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MYFixDarkColorScheme = darkColorScheme(
    primary = CyanEmerald,
    onPrimary = ObsidianBg,
    primaryContainer = CyanEmeraldDark,
    onPrimaryContainer = CyanEmerald,
    secondary = SkyAccent,
    onSecondary = ObsidianBg,
    secondaryContainer = SkyAccentDark,
    onSecondaryContainer = SkyAccent,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateOutline,
    error = StatusError,
    onError = ObsidianBg,
)

private val MYFixLightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = LightSurface,
    secondary = SkySecondaryLight,
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = StatusError,
    onError = LightSurface,
)

@Composable
fun MYFixTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) MYFixDarkColorScheme else MYFixLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MYFixTypography,
        content = content,
    )
}
