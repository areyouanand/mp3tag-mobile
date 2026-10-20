package com.mp3tag.mobile.ui.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSizeBucket {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Immutable
data class AdaptiveWindowInfo(
    val widthDp: Int,
    val heightDp: Int,
    val widthClass: WindowSizeBucket,
    val heightClass: WindowSizeBucket,
    val isLandscape: Boolean,
) {
    /** True on tablets or unfolded foldables (both dimensions at least medium). */
    val isTablet: Boolean
        get() = (widthDp >= 600 && heightDp >= 540) || widthDp >= 840

    /** True when horizontal space is wide enough for a 2-pane split layout (phone landscape & tablets). */
    val useTwoPane: Boolean
        get() = widthDp >= 600

    /** True on phone landscape where vertical height is tight (< 480dp). */
    val isCompactHeight: Boolean
        get() = heightClass == WindowSizeBucket.COMPACT

    /** Number of columns for tag fields in the editor pane. */
    val editorFieldColumns: Int
        get() = when {
            widthDp >= 1040 -> 2
            widthDp >= 600 -> 2
            else -> 1
        }

    /** Number of columns for file rows in the browser's main list pane. */
    val browserListColumns: Int
        get() = when {
            widthDp >= 960 -> 2
            else -> 1
        }

    /** Width of the left sidebar / overview pane when [useTwoPane] is active. */
    val sidePaneWidth: Dp
        get() = when {
            widthDp >= 960 -> 320.dp
            widthDp >= 720 -> 276.dp
            else -> 236.dp
        }
}

@Composable
fun rememberAdaptiveWindowInfo(): AdaptiveWindowInfo {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || widthDp > heightDp

    return remember(widthDp, heightDp, isLandscape) {
        val widthClass = when {
            widthDp < 600 -> WindowSizeBucket.COMPACT
            widthDp < 840 -> WindowSizeBucket.MEDIUM
            else -> WindowSizeBucket.EXPANDED
        }
        val heightClass = when {
            heightDp < 480 -> WindowSizeBucket.COMPACT
            heightDp < 900 -> WindowSizeBucket.MEDIUM
            else -> WindowSizeBucket.EXPANDED
        }
        AdaptiveWindowInfo(
            widthDp = widthDp,
            heightDp = heightDp,
            widthClass = widthClass,
            heightClass = heightClass,
            isLandscape = isLandscape,
        )
    }
}
