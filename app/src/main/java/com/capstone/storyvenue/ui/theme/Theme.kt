package com.capstone.storyvenue.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val StoryVenueLightColors = lightColorScheme(
    primary          = AccentGreen,
    onPrimary        = BgPrimary,
    secondary        = AccentBrown,
    onSecondary      = BgPrimary,
    error            = AccentRed,
    background       = BgPrimary,
    onBackground     = TextPrimary,
    surface          = BgSecondary,
    onSurface        = TextPrimary,
    surfaceVariant   = BgTertiary,
    onSurfaceVariant = TextSecondary,
)

private val StoryVenueDarkColors = darkColorScheme(
    primary          = AccentGreen,
    onPrimary        = BgPrimary,
    secondary        = AccentBrown,
    onSecondary      = BgPrimary,
    error            = AccentRed,
    background       = TextPrimary,
    onBackground     = BgPrimary,
    surface          = TextSecondary,
    onSurface        = BgPrimary,
)

@Composable
fun Capstone_storyvenue_appTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) StoryVenueDarkColors else StoryVenueLightColors,
        typography  = Typography,
        content     = content
    )
}
