package com.example.healthpro.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SahayColorScheme = darkColorScheme(
    primary = TealAccent,
    secondary = BlueAccent,
    tertiary = PurpleAccent,
    background = DarkNavy,
    surface = DarkNavySurface,
    surfaceVariant = CardDark,
    onPrimary = DarkNavy,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextGray,
    error = SosRed,
    onError = Color.White
)

@Composable
fun HealthProTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkNavy.toArgb()
            window.navigationBarColor = BottomBarDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = SahayColorScheme,
        typography = SahayTypography,
        content = content
    )
}