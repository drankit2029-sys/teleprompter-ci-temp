package com.accessoryTools.teleprompter.data

import android.graphics.RectF

/**
 * The fraction of the screen used by the transparent overlay. Coordinates are
 * normalized so the selection survives rotations and different display sizes.
 */
data class OverlayArea(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float
        get() = (right - left).coerceAtLeast(MIN_SIZE)

    val height: Float
        get() = (bottom - top).coerceAtLeast(MIN_SIZE)

    fun sanitized(): OverlayArea {
        // NaN is transparent to coerceIn, so guard explicitly first.
        val safeLeft = left.finiteOr(0f, 0f, 1f - MIN_SIZE)
        val safeTop = top.finiteOr(0f, 0f, 1f - MIN_SIZE)
        val safeRight = right.finiteOr(1f, safeLeft + MIN_SIZE, 1f)
        val safeBottom = bottom.finiteOr(1f, safeTop + MIN_SIZE, 1f)
        return OverlayArea(safeLeft, safeTop, safeRight, safeBottom)
    }

    fun toRect(
        viewWidth: Int,
        viewHeight: Int,
    ): RectF {
        val safe = sanitized()
        return RectF(
            safe.left * viewWidth,
            safe.top * viewHeight,
            safe.right * viewWidth,
            safe.bottom * viewHeight,
        )
    }

    private fun Float.finiteOr(
        default: Float,
        min: Float,
        max: Float,
    ): Float = if (isFinite()) coerceIn(min, max) else default

    companion object {
        const val MIN_SIZE = 0.2f

        val FULL = OverlayArea()
        val TOP_HALF = OverlayArea(top = 0f, bottom = 0.5f)
        val BOTTOM_HALF = OverlayArea(top = 0.5f, bottom = 1f)
        val CENTER = OverlayArea(left = 0.08f, top = 0.2f, right = 0.92f, bottom = 0.8f)
    }
}
