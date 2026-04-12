package com.capstone.storyvenue.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object StoryVenueColors {
    val Primary      = Color(0xFF5F7A6A)
    val PrimaryDark  = Color(0xFF3E5345)
    val PrimaryMid   = Color(0xFF8FA99A)
    val PrimaryLight = Color(0xFFC9D8B6)
    val Accent       = Color(0xFFD4A853)
    val Error        = Color(0xFFC0392B)
    val Background   = Color(0xFFFAFAF8)
    val Surface      = Color(0xFFF0EDE6)
    val OnSurface    = Color(0xFF1A1A1A)
    val SubText      = Color(0xFF6B6B6B)
    val Divider      = Color(0xFFE2DED6)
    val BubbleMine   = Color(0xFF5F7A6A)
    val BubbleOther  = Color(0xFFF0EDE6)
    val White        = Color(0xFFFFFFFF)
    val LikeRed      = Color(0xFFC0392B)
}

private val StoryVenueColorScheme = lightColorScheme(
    primary          = StoryVenueColors.Primary,
    onPrimary        = StoryVenueColors.White,
    primaryContainer = StoryVenueColors.PrimaryLight,
    secondary        = StoryVenueColors.Accent,
    onSecondary      = StoryVenueColors.White,
    error            = StoryVenueColors.Error,
    background       = StoryVenueColors.Background,
    surface          = StoryVenueColors.Surface,
    onSurface        = StoryVenueColors.OnSurface,
    outline          = StoryVenueColors.Divider,
)

@Composable
fun StoryVenueAppTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = StoryVenueColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(
        colorScheme = StoryVenueColorScheme,
        typography  = StoryVenueTypography,
        content     = content,
    )
}
