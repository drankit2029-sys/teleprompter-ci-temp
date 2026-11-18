package com.accessoryTools.teleprompter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accessoryTools.teleprompter.data.OverlayArea
import com.accessoryTools.teleprompter.prompter.PrompterSession
import kotlin.math.abs

private enum class AreaDragMode {
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

private val AreaInk = Color(0xFF000000)
private val AreaPaper = Color(0xFFF5F5DC)
private val AreaTeal = Color(0xFF00C2C8)
private val AreaMagenta = Color(0xFFFF00FF)

@Composable
fun OverlayAreaEditor(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "OVERLAY AREA",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text =
                "Choose where the script floats. Drag the box to move it; drag a corner to resize. " +
                    "Changes apply live to a running overlay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(AreaInk)
                    .border(3.dp, AreaInk, RectangleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val bounds = Size(size.width.toFloat(), size.height.toFloat())
                            val rect = PrompterSession.overlayArea.toPreviewRect(bounds)
                            val threshold = 24.dp.toPx()
                            val mode = hitMode(down.position, rect, threshold)
                            if (mode == null) {
                                // Outside the interactive area: let the panel
                                // scroll, as if this control were not here.
                                return@awaitEachGesture
                            }
                            if (mode == AreaDragMode.MOVE &&
                                rect.width >= bounds.width * 0.85f &&
                                rect.height >= bounds.height * 0.85f
                            ) {
                                // Near-full overlay area: dragging it barely moves anything, and
                                // swallowing every touch here would make the whole panel
                                // unscrollable on compact screens. Let taps/drags scroll instead.
                                return@awaitEachGesture
                            }
                            var dragMode = mode
                            var previousPosition = down.position
                            down.consume()
                            drag(down.id) { change ->
                                val delta = change.position - previousPosition
                                previousPosition = change.position
                                if (abs(delta.x) > 0.5f || abs(delta.y) > 0.5f) {
                                    val dx = delta.x / bounds.width.coerceAtLeast(1f)
                                    val dy = delta.y / bounds.height.coerceAtLeast(1f)
                                    PrompterSession.updateOverlayArea(
                                        PrompterSession.overlayArea.applyDrag(dragMode, dx, dy),
                                    )
                                }
                                change.consume()
                            }
                            PrompterSession.persistOverlayArea()
                        }
                    },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawAreaPreview(
                    area = PrompterSession.overlayArea,
                    size = size,
                    handleRadius = 9.dp.toPx(),
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AreaPresetChip("Full", OverlayArea.FULL)
            AreaPresetChip("Top", OverlayArea.TOP_HALF)
            AreaPresetChip("Bottom", OverlayArea.BOTTOM_HALF)
            AreaPresetChip("Center", OverlayArea.CENTER)
            TextButton(
                onClick = {
                    PrompterSession.updateOverlayArea(OverlayArea.FULL)
                    PrompterSession.persistOverlayArea()
                },
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun AreaPresetChip(
    label: String,
    preset: OverlayArea,
) {
    FilterChip(
        selected = PrompterSession.overlayArea == preset,
        onClick = {
            PrompterSession.updateOverlayArea(preset)
            PrompterSession.persistOverlayArea()
        },
        label = { Text(label, maxLines = 1) },
    )
}

private fun OverlayArea.toPreviewRect(bounds: Size) =
    androidx.compose.ui.geometry.Rect(
        left = left * bounds.width,
        top = top * bounds.height,
        right = right * bounds.width,
        bottom = bottom * bounds.height,
    )

private fun hitMode(
    position: Offset,
    rect: androidx.compose.ui.geometry.Rect,
    threshold: Float,
): AreaDragMode? {
    val corners =
        listOf(
            AreaDragMode.TOP_LEFT to Offset(rect.left, rect.top),
            AreaDragMode.TOP_RIGHT to Offset(rect.right, rect.top),
            AreaDragMode.BOTTOM_LEFT to Offset(rect.left, rect.bottom),
            AreaDragMode.BOTTOM_RIGHT to Offset(rect.right, rect.bottom),
        )
    return corners
        .map { (mode, point) -> mode to abs(position.x - point.x) + abs(position.y - point.y) }
        .filter { (_, distance) -> distance <= threshold * 2f }
        .minByOrNull { (_, distance) -> distance }
        ?.first
        ?: if (rect.contains(position)) AreaDragMode.MOVE else null
}

private fun OverlayArea.applyDrag(
    mode: AreaDragMode,
    dx: Float,
    dy: Float,
): OverlayArea {
    val minSize = OverlayArea.MIN_SIZE
    return when (mode) {
        AreaDragMode.MOVE -> {
            val width = right - left
            val height = bottom - top
            val newLeft = (left + dx).coerceIn(0f, 1f - width)
            val newTop = (top + dy).coerceIn(0f, 1f - height)
            OverlayArea(newLeft, newTop, newLeft + width, newTop + height)
        }

        AreaDragMode.TOP_LEFT -> {
            OverlayArea(
                left = (left + dx).coerceIn(0f, right - minSize),
                top = (top + dy).coerceIn(0f, bottom - minSize),
                right = right,
                bottom = bottom,
            )
        }

        AreaDragMode.TOP_RIGHT -> {
            OverlayArea(
                left = left,
                top = (top + dy).coerceIn(0f, bottom - minSize),
                right = (right + dx).coerceIn(left + minSize, 1f),
                bottom = bottom,
            )
        }

        AreaDragMode.BOTTOM_LEFT -> {
            OverlayArea(
                left = (left + dx).coerceIn(0f, right - minSize),
                top = top,
                right = right,
                bottom = (bottom + dy).coerceIn(top + minSize, 1f),
            )
        }

        AreaDragMode.BOTTOM_RIGHT -> {
            OverlayArea(
                left = left,
                top = top,
                right = (right + dx).coerceIn(left + minSize, 1f),
                bottom = (bottom + dy).coerceIn(top + minSize, 1f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAreaPreview(
    area: OverlayArea,
    size: Size,
    handleRadius: Float,
) {
    // A simplified camera view behind the script region.
    drawRect(color = Color(0xFF27292E))
    val grid = Color(0xFF3A3D44)
    val step = size.width / 7f
    var x = step
    while (x < size.width) {
        drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }

    val rect = area.toPreviewRect(size)
    drawRect(
        color = AreaTeal.copy(alpha = 0.22f),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
    )
    drawRect(
        color = AreaPaper,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        style =
            androidx.compose.ui.graphics.drawscope
                .Stroke(width = 7f),
    )
    drawRect(
        color = AreaTeal,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        style =
            androidx.compose.ui.graphics.drawscope
                .Stroke(width = 4f),
    )

    // Hint lines inside the script region.
    val lineLeft = rect.left + rect.width * 0.1f
    val lineRight = rect.right - rect.width * 0.1f
    val lineCount = 5
    repeat(lineCount) { index ->
        val lineY = rect.top + rect.height * (0.22f + index * 0.14f)
        if (lineY < rect.bottom - 8f) {
            drawLine(
                color = AreaInk.copy(alpha = 0.55f),
                start = Offset(lineLeft, lineY),
                end = Offset(lineRight - rect.width * (index % 2) * 0.16f, lineY),
                strokeWidth = 4f,
            )
        }
    }

    listOf(
        Offset(rect.left, rect.top),
        Offset(rect.right, rect.top),
        Offset(rect.left, rect.bottom),
        Offset(rect.right, rect.bottom),
    ).forEach { corner ->
        drawRect(
            color = AreaMagenta,
            topLeft = Offset(corner.x - handleRadius, corner.y - handleRadius),
            size = Size(handleRadius * 2f, handleRadius * 2f),
        )
        drawRect(
            color = AreaInk,
            topLeft = Offset(corner.x - handleRadius, corner.y - handleRadius),
            size = Size(handleRadius * 2f, handleRadius * 2f),
            style =
                androidx.compose.ui.graphics.drawscope
                    .Stroke(width = 3f),
        )
    }
}
