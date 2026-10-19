package com.mp3tag.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val Indigo = Color(0xFF4355B9)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF4757A8),
    secondary = Color(0xFF5B5D72),
    tertiary = Color(0xFF77724B),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    secondary = Color(0xFFC5C3DD),
    tertiary = Color(0xFFE6C36C),
)

/**
 * Material 3 Expressive theme. Android 12 is the minimum target, so the
 * dynamic (Material You) color scheme is always available and used by default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Mp3tagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Fallback reference so [Indigo] survives lint when unused in dynamic color mode. */
private val Unused = Indigo
