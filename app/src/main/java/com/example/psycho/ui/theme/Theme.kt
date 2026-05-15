package com.example.psycho.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = MutedTeal,
    onPrimary = SlateInk,
    secondary = WarmSand,
    onSecondary = SlateInk,
    tertiary = SoftClay,
    background = SlateInk,
    onBackground = InkOn,
    surface = SlateSurface,
    onSurface = InkOn,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = MutedOn,
)

private val LightColors = lightColorScheme(
    primary = MutedTealDark,
    onPrimary = SlateSurfaceLight,
    secondary = SoftClay,
    onSecondary = SlateSurfaceLight,
    tertiary = WarmSand,
    background = SlateMist,
    onBackground = InkOnLight,
    surface = SlateSurfaceLight,
    onSurface = InkOnLight,
    surfaceVariant = SlateMist,
    onSurfaceVariant = MutedOn,
)

@Composable
fun PsychoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
