package com.mp3tag.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mp3tag.mobile.data.prefs.DesignLanguage

val LocalDesignLanguage = staticCompositionLocalOf { DesignLanguage.MATERIAL_3_EXPRESSIVE }

// ---------------------------------------------------------------------------
// 1. Material 3 Expressive / Material You Fallback Palettes
// ---------------------------------------------------------------------------

private val ExpressiveLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
)

private val ExpressiveDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF44263D),
    tertiaryContainer = Color(0xFF5D3C55),
    onTertiaryContainer = Color(0xFFFFD7F1),
)

// ---------------------------------------------------------------------------
// 2. Liquid Glass Palettes (Translucent frosted-glass azure & cyan aesthetics)
// ---------------------------------------------------------------------------

private val LiquidGlassLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006884),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0F0FF),
    onPrimaryContainer = Color(0xFF00202B),
    secondary = Color(0xFF2B6378),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE9F6),
    onSecondaryContainer = Color(0xFF051F29),
    tertiary = Color(0xFF4D57A9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E0FF),
    onTertiaryContainer = Color(0xFF030865),
    background = Color(0xFFF3FAFF),
    onBackground = Color(0xFF0C1D24),
    surface = Color(0xFFF6FBFF),
    onSurface = Color(0xFF0C1D24),
    surfaceVariant = Color(0xFFDCEAF2),
    onSurfaceVariant = Color(0xFF3C4D56),
    surfaceContainerLow = Color(0xFFEEF7FC),
    surfaceContainer = Color(0xFFE6F2F9),
    surfaceContainerHigh = Color(0xFFDCEBF4),
    surfaceContainerHighest = Color(0xFFD2E5F0),
    outline = Color(0xFF6B818C),
    outlineVariant = Color(0xFFB5CBD6),
)

private val LiquidGlassDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF6CD2FF),
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF004D63),
    onPrimaryContainer = Color(0xFFBFE9FF),
    secondary = Color(0xFF96CDF8),
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF124B6B),
    onSecondaryContainer = Color(0xFFCBE6FF),
    tertiary = Color(0xFFBEC2FF),
    onTertiary = Color(0xFF1E2578),
    tertiaryContainer = Color(0xFF353E90),
    onTertiaryContainer = Color(0xFFE0E0FF),
    background = Color(0xFF07131A),
    onBackground = Color(0xFFDCEAF2),
    surface = Color(0xFF0A1821),
    onSurface = Color(0xFFDCEAF2),
    surfaceVariant = Color(0xFF1A2E3B),
    onSurfaceVariant = Color(0xFFB4C9D6),
    surfaceContainerLow = Color(0xFF0F202B),
    surfaceContainer = Color(0xFF142836),
    surfaceContainerHigh = Color(0xFF1B3344),
    surfaceContainerHighest = Color(0xFF233F52),
    outline = Color(0xFF618296),
    outlineVariant = Color(0xFF2A4556),
)

// ---------------------------------------------------------------------------
// 3. Halo Palettes (Futuristic OLED black/slate with glowing neon cyan & violet aura)
// ---------------------------------------------------------------------------

private val HaloLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF4E4),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF5D3FD3),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF1B0063),
    tertiary = Color(0xFF984061),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3E001D),
    background = Color(0xFFF6F9FC),
    onBackground = Color(0xFF0F171E),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F171E),
    surfaceVariant = Color(0xFFDCE4EC),
    onSurfaceVariant = Color(0xFF3E4852),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFE8EEF4),
    surfaceContainerHigh = Color(0xFFDFE7EF),
    surfaceContainerHighest = Color(0xFFD5DFE9),
    outline = Color(0xFF5C6B77),
    outlineVariant = Color(0xFFBFCAD4),
)

private val HaloDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFF9CF8FF),
    secondary = Color(0xFFC8B3FF),
    onSecondary = Color(0xFF301070),
    secondaryContainer = Color(0xFF472B88),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = Color(0xFFFF85B3),
    onTertiary = Color(0xFF5E0F33),
    tertiaryContainer = Color(0xFF7B284A),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF04070B),
    onBackground = Color(0xFFE5F1F8),
    surface = Color(0xFF070B12),
    onSurface = Color(0xFFE5F1F8),
    surfaceVariant = Color(0xFF151F2C),
    onSurfaceVariant = Color(0xFFB2C3D4),
    surfaceContainerLow = Color(0xFF0B111B),
    surfaceContainer = Color(0xFF101824),
    surfaceContainerHigh = Color(0xFF162030),
    surfaceContainerHighest = Color(0xFF1E2B3E),
    outline = Color(0xFF3B6978),
    outlineVariant = Color(0xFF213345),
)

// ---------------------------------------------------------------------------
// Shape & Typography Systems per Design Language
// ---------------------------------------------------------------------------

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val MaterialYouShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val LiquidGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

private val HaloShapes = Shapes(
    extraSmall = CutCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

private val BaseTypography = Typography()

private val ExpressiveTypography = BaseTypography.copy(
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    labelLarge = BaseTypography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
    ),
)

private val LiquidGlassTypography = BaseTypography.copy(
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.15.sp,
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
)

private val HaloTypography = BaseTypography.copy(
    titleLarge = BaseTypography.titleLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    ),
    labelLarge = BaseTypography.labelLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = BaseTypography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.6.sp,
    ),
)

/**
 * Multi-design-language theme supporting:
 * - Material 3 Expressive (Google's latest Android 16 expressive design language)
 * - Material You (Google's dynamic wallpaper palette + classic M3 geometry)
 * - Liquid Glass (frosted translucent cyan/azure palette & fluid capsule geometry)
 * - Halo (high-contrast OLED neon aura palette & crisp technical geometry)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Mp3tagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    designLanguage: DesignLanguage = DesignLanguage.MATERIAL_3_EXPRESSIVE,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme: ColorScheme = when (designLanguage) {
        DesignLanguage.MATERIAL_3_EXPRESSIVE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                // Boost expressive vibrancy on top of the dynamic palette
                if (darkTheme) {
                    dyn.copy(
                        surfaceContainerHigh = dyn.secondaryContainer.copy(alpha = 0.42f),
                    )
                } else {
                    dyn
                }
            } else {
                if (darkTheme) ExpressiveDarkColors else ExpressiveLightColors
            }
        }

        DesignLanguage.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) ExpressiveDarkColors else ExpressiveLightColors
            }
        }

        DesignLanguage.LIQUID_GLASS -> {
            if (darkTheme) LiquidGlassDarkColors else LiquidGlassLightColors
        }

        DesignLanguage.HALO -> {
            if (darkTheme) HaloDarkColors else HaloLightColors
        }
    }

    val shapes: Shapes = when (designLanguage) {
        DesignLanguage.MATERIAL_3_EXPRESSIVE -> ExpressiveShapes
        DesignLanguage.MATERIAL_YOU -> MaterialYouShapes
        DesignLanguage.LIQUID_GLASS -> LiquidGlassShapes
        DesignLanguage.HALO -> HaloShapes
    }

    val typography: Typography = when (designLanguage) {
        DesignLanguage.MATERIAL_3_EXPRESSIVE -> ExpressiveTypography
        DesignLanguage.MATERIAL_YOU -> BaseTypography
        DesignLanguage.LIQUID_GLASS -> LiquidGlassTypography
        DesignLanguage.HALO -> HaloTypography
    }

    val motionScheme: MotionScheme = when (designLanguage) {
        DesignLanguage.MATERIAL_3_EXPRESSIVE,
        DesignLanguage.LIQUID_GLASS -> MotionScheme.expressive()
        DesignLanguage.MATERIAL_YOU,
        DesignLanguage.HALO -> MotionScheme.standard()
    }

    CompositionLocalProvider(LocalDesignLanguage provides designLanguage) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = typography,
            motionScheme = motionScheme,
            content = content,
        )
    }
}
