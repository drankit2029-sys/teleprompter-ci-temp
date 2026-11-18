package com.accessoryTools.teleprompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Neo-brutalist palette taken from the project style guide. */
object Neo {
    val Ink = Color(0xFF000000)
    val Paper = Color(0xFFF5F5DC)
    val White = Color(0xFFFFFFFF)
    val Teal = Color(0xFF00C2C8)
    val Magenta = Color(0xFFFF00FF)
    val Yellow = Color(0xFFFFD700)
    val MutedInk = Color(0xFF3A3A32)
}

private val TeleprompterColors =
    lightColorScheme(
        primary = Neo.Teal,
        onPrimary = Neo.Ink,
        primaryContainer = Neo.Teal,
        onPrimaryContainer = Neo.Ink,
        secondary = Neo.Magenta,
        onSecondary = Neo.Ink,
        secondaryContainer = Neo.Teal,
        onSecondaryContainer = Neo.Ink,
        tertiary = Neo.Yellow,
        onTertiary = Neo.Ink,
        tertiaryContainer = Neo.Yellow,
        onTertiaryContainer = Neo.Ink,
        background = Neo.Paper,
        onBackground = Neo.Ink,
        surface = Neo.White,
        onSurface = Neo.Ink,
        surfaceVariant = Neo.Paper,
        onSurfaceVariant = Neo.MutedInk,
        surfaceContainer = Neo.White,
        surfaceContainerHigh = Neo.Paper,
        surfaceContainerHighest = Neo.Paper,
        outline = Neo.Ink,
        outlineVariant = Neo.Ink,
        error = Color(0xFFD00000),
        onError = Neo.White,
        errorContainer = Neo.Magenta,
        onErrorContainer = Neo.Ink,
        scrim = Neo.Ink,
        inverseSurface = Neo.Ink,
        inverseOnSurface = Neo.White,
        inversePrimary = Neo.Teal,
    )

private val NeoShapes =
    Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
    )

private val BaseTypography = Typography()

private val NeoTypography =
    Typography(
        displayLarge = BaseTypography.displayLarge.neo(56.sp, FontWeight.Black, 60.sp),
        displayMedium = BaseTypography.displayMedium.neo(46.sp, FontWeight.Black, 50.sp),
        displaySmall = BaseTypography.displaySmall.neo(38.sp, FontWeight.Black, 42.sp),
        headlineLarge = BaseTypography.headlineLarge.neo(34.sp, FontWeight.Black, 38.sp),
        headlineMedium = BaseTypography.headlineMedium.neo(28.sp, FontWeight.Black, 32.sp),
        headlineSmall = BaseTypography.headlineSmall.neo(24.sp, FontWeight.Black, 28.sp),
        titleLarge = BaseTypography.titleLarge.neo(22.sp, FontWeight.Black, 26.sp),
        titleMedium = BaseTypography.titleMedium.neo(17.sp, FontWeight.Bold, 21.sp),
        titleSmall = BaseTypography.titleSmall.neo(15.sp, FontWeight.Bold, 19.sp),
        bodyLarge = BaseTypography.bodyLarge.neo(16.sp, FontWeight.Normal, 22.sp),
        bodyMedium = BaseTypography.bodyMedium.neo(14.sp, FontWeight.Normal, 20.sp),
        bodySmall = BaseTypography.bodySmall.neo(12.sp, FontWeight.Normal, 17.sp),
        labelLarge = BaseTypography.labelLarge.neo(13.sp, FontWeight.Bold, 17.sp, 0.4.sp),
        labelMedium = BaseTypography.labelMedium.neo(11.sp, FontWeight.Bold, 15.sp, 0.5.sp),
        labelSmall = BaseTypography.labelSmall.neo(10.sp, FontWeight.Bold, 14.sp, 0.6.sp),
    )

private fun TextStyle.neo(
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    line: androidx.compose.ui.unit.TextUnit,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
): TextStyle =
    copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = size,
        fontWeight = weight,
        lineHeight = line,
        letterSpacing = letterSpacing,
    )

@Composable
fun TeleprompterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TeleprompterColors,
        typography = NeoTypography,
        shapes = NeoShapes,
        content = content,
    )
}

/**
 * Paper panel with a thick black stroke and a hard offset shadow. The shadow
 * is drawn behind the panel and may extend a few dp beyond its layout bounds,
 * which is the intended print-like effect.
 */
@Composable
fun NeoPanel(
    modifier: Modifier = Modifier,
    color: Color = Neo.Paper,
    borderWidth: Dp = 3.dp,
    shadowOffset: Dp = 4.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Neo.Ink),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(end = shadowOffset, bottom = shadowOffset),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(color)
                        .border(borderWidth, Neo.Ink)
                        .padding(contentPadding),
                content = content,
            )
        }
    }
}
