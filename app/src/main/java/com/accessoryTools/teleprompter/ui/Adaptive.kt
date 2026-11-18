package com.accessoryTools.teleprompter.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Coarse width classes (dp), mirroring the Material 3 guidance. */
enum class AdaptiveWidth {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Composable
fun rememberAdaptiveWidth(): AdaptiveWidth {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> AdaptiveWidth.COMPACT
        widthDp < 840 -> AdaptiveWidth.MEDIUM
        else -> AdaptiveWidth.EXPANDED
    }
}

@Composable
fun isLandscapeScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

/** Handset landscape: the height budget is very tight (typically under 480 dp). */
@Composable
fun isShortLandscape(): Boolean =
    isLandscapeScreen() && LocalConfiguration.current.screenHeightDp < 480

/** Phones in portrait: below the 600 dp compact width threshold. */
@Composable
fun isCompactWidthScreen(): Boolean = LocalConfiguration.current.screenWidthDp < 600

/** Short height screens (small phones / handset landscape): tighten vertical chrome. */
@Composable
fun isCompactHeightScreen(): Boolean = LocalConfiguration.current.screenHeightDp < 600
