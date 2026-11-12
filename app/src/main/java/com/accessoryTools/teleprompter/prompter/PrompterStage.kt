package com.accessoryTools.teleprompter.prompter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun PrompterStage(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    textColor: Color = Color(0xFFF7F4EC),
    outlineColor: Color = Color(0xCC000000),
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
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
        val sidePadding = if (compact) 10.dp else 28.dp
        val maxTextWidth = if (compact) 2_000.dp else 1_000.dp
        val edgeAlpha = PrompterSession.gradientEdgeAlpha
        val focus = PrompterSession.focusFraction
        val maskBrush =
            remember(edgeAlpha, focus) {
                val band = 0.09f
                Brush.verticalGradient(
                    colorStops =
                        arrayOf(
                            0f to Color.Black.copy(alpha = edgeAlpha),
                            (focus - band).coerceIn(0f, 1f) to Color.Black,
                            (focus + band).coerceIn(0f, 1f) to Color.Black,
                            1f to Color.Black.copy(alpha = edgeAlpha),
                        ),
                )
            }
        val textStyle =
            remember(fontSizeSp, outlineColor) {
                TextStyle(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.34f).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
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
        val offsetYPx = (focusYPx - PrompterSession.offsetPx).roundToInt()
        with(density) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
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
                            .offset { IntOffset(0, offsetYPx) }
                            .onGloballyPositioned { coordinates ->
                                PrompterSession.updateLayout(
                                    contentHeight = coordinates.size.height.toFloat(),
                                    viewportHeight = viewportHeightPx,
                                )
                            }.padding(horizontal = sidePadding),
                ) {
                    Text(
                        text = PrompterSession.script,
                        color = textColor,
                        style = textStyle,
                    )
                }
            }
        }
    }
}
