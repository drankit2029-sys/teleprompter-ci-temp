package com.accessoryTools.teleprompter.prompter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accessoryTools.teleprompter.data.StageAlignment
import com.accessoryTools.teleprompter.data.StageFont
import com.accessoryTools.teleprompter.data.StageWeight
import com.accessoryTools.teleprompter.data.argb
import kotlin.math.min

@Composable
fun PrompterStage(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(PrompterSession.effectiveBackgroundArgb))
                .clipToBounds(),
    ) {
        val density = LocalDensity.current
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val focusYPx = viewportHeightPx * PrompterSession.focusFraction
        val rawFontSizeSp = PrompterSession.fontSizeSp
        val fontSizeSp =
            if (compact) {
                (rawFontSizeSp * 0.34f).coerceIn(12f, 24f)
            } else {
                rawFontSizeSp
            }
        val sidePadding = if (compact) 10.dp else PrompterSession.marginDp.dp
        val maxTextWidth = if (compact) 2_000.dp else 1_000.dp
        val edgeAlpha = PrompterSession.gradientEdgeAlpha
        val focus = PrompterSession.focusFraction
        val textColor = Color(PrompterSession.effectiveTextColorArgb)
        val outlineColor = Color(PrompterSession.effectiveOutlineColorArgb)
        val fontFamily =
            remember(PrompterSession.stageFont, PrompterSession.stageWeight, PrompterSession.customFontFile) {
                val weight =
                    when (PrompterSession.stageWeight) {
                        StageWeight.REGULAR -> FontWeight.Normal
                        StageWeight.MEDIUM -> FontWeight.Medium
                        StageWeight.BOLD -> FontWeight.Bold
                    }
                when (PrompterSession.stageFont) {
                    StageFont.SANS -> {
                        FontFamily.SansSerif
                    }

                    StageFont.SERIF -> {
                        FontFamily.Serif
                    }

                    StageFont.MONO -> {
                        FontFamily.Monospace
                    }

                    StageFont.SANS_LIGHT -> {
                        FontFamily(Font(DeviceFontFamilyName("sans-serif-light"), weight))
                    }

                    StageFont.SANS_MEDIUM -> {
                        FontFamily(Font(DeviceFontFamilyName("sans-serif-medium"), weight))
                    }

                    StageFont.SANS_CONDENSED -> {
                        FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), weight))
                    }

                    StageFont.SANS_SMALLCAPS -> {
                        FontFamily(Font(DeviceFontFamilyName("sans-serif-smallcaps"), weight))
                    }

                    StageFont.SERIF_MEDIUM -> {
                        FontFamily(Font(DeviceFontFamilyName("serif-medium"), weight))
                    }

                    StageFont.CURSIVE -> {
                        FontFamily(Font(DeviceFontFamilyName("cursive"), weight))
                    }

                    StageFont.CASUAL -> {
                        FontFamily(Font(DeviceFontFamilyName("casual"), weight))
                    }

                    StageFont.CUSTOM -> {
                        val name = PrompterSession.customFontFile
                        val file = if (name != null) StageTypefaces.fontFile(name) else null
                        if (file != null && file.isFile) {
                            FontFamily(Font(file, weight))
                        } else {
                            FontFamily.SansSerif
                        }
                    }
                }
            }
        val fontWeight =
            when (PrompterSession.stageWeight) {
                StageWeight.REGULAR -> FontWeight.Normal
                StageWeight.MEDIUM -> FontWeight.Medium
                StageWeight.BOLD -> FontWeight.Bold
            }
        val textAlign =
            when (PrompterSession.stageAlignment) {
                StageAlignment.CENTER -> TextAlign.Center
                StageAlignment.LEFT -> TextAlign.Left
                StageAlignment.RIGHT -> TextAlign.Right
            }
        val lineSpacing = PrompterSession.lineSpacing
        val mirrorHorizontal = PrompterSession.mirrorHorizontal
        val mirrorVertical = PrompterSession.mirrorVertical
        val maskBrush =
            remember(edgeAlpha, focus, mirrorHorizontal, mirrorVertical) {
                val stops =
                    PrompterMath
                        .focusGradientStops(edgeAlpha, focus)
                        .map { (position, alpha) ->
                            position to Color.Black.copy(alpha = alpha)
                        }.toTypedArray()
                Brush.verticalGradient(colorStops = stops)
            }
        val textStyle =
            remember(fontSizeSp, lineSpacing, outlineColor, fontFamily, fontWeight, textAlign) {
                TextStyle(
                    fontSize = fontSizeSp.sp,
                    // Platform default, matching the StaticLayout multiplier in the overlay.
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    textAlign = textAlign,
                    lineBreak = LineBreak.Simple,
                    hyphens = Hyphens.None,
                    shadow =
                        Shadow(
                            color = outlineColor,
                            offset = Offset.Zero,
                            blurRadius = if (compact) 6f else 10f,
                        ),
                )
            }
        val availableTextWidthPx =
            (
                min(constraints.maxWidth.toFloat(), with(density) { maxTextWidth.toPx() }) -
                    with(density) { sidePadding.toPx() } * 2f
            ).coerceAtLeast(1f)
        val wrappedScript =
            remember(
                PrompterSession.script,
                fontSizeSp,
                PrompterSession.stageFont,
                PrompterSession.stageWeight,
                PrompterSession.customFontFile,
                availableTextWidthPx,
                density.density,
                density.fontScale,
            ) {
                val paint =
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface =
                            StageTypefaces.typefaceFor(
                                PrompterSession.stageFont,
                                PrompterSession.stageWeight,
                                PrompterSession.customFontFile,
                            )
                        textSize = fontSizeSp * density.density * density.fontScale
                        isFakeBoldText =
                            PrompterSession.stageWeight == com.accessoryTools.teleprompter.data.StageWeight.BOLD
                    }
                // Leave a small safety margin so Compose cannot decide to
                // re-wrap a line that the explicit measurement considered a
                // perfect fit.
                WordWrap.wrap(
                    text = PrompterSession.script,
                    maxWidth = (availableTextWidthPx - 1f).coerceAtLeast(1f),
                    measure = { value -> paint.measureText(value, 0, value.length) },
                )
            }
        with(density) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (mirrorHorizontal) -1f else 1f
                            scaleY = if (mirrorVertical) -1f else 1f
                            compositingStrategy = CompositingStrategy.Offscreen
                        }.drawWithContent {
                            drawContent()
                            drawRect(
                                brush = maskBrush,
                                blendMode = BlendMode.DstIn,
                            )
                        },
            ) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = maxTextWidth)
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true, align = Alignment.Top)
                            // Translate the (static) text layer instead of
                            // relaying out the huge Text every frame; the
                            // layer records once and merely shifts.
                            .graphicsLayer {
                                translationY = focusYPx - PrompterSession.offsetPx
                            }
                            .onGloballyPositioned { coordinates ->
                                PrompterSession.updateLayout(
                                    contentHeight = coordinates.size.height.toFloat(),
                                    viewportHeight = viewportHeightPx,
                                )
                            }.padding(horizontal = sidePadding),
                ) {
                    Text(
                        text = wrappedScript,
                        color = textColor,
                        style = textStyle,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
