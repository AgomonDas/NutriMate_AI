package com.example.nutrimate.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MintAccent,
    onPrimary = MintAccentDark,
    primaryContainer = MintAccentDark,
    onPrimaryContainer = MintAccentGlow,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    tertiary = OrangeAccent,
    onTertiary = Color.Black,
    background = BgDark,
    onBackground = Color.White,
    surface = Color(0xFF131A32),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E284A),
    onSurfaceVariant = Color(0xFFD0D7E5)
)

@Composable
fun NutriMateTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
