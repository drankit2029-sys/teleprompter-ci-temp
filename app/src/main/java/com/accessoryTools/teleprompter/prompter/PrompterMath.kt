package com.accessoryTools.teleprompter.prompter

import kotlin.math.roundToInt

data class AdvancedOffset(
    val offsetPx: Float,
    val isAtEnd: Boolean,
    val looped: Boolean = false,
)

enum class AdaptiveTone {
    LIGHT,
    DARK,
    UNCHANGED,
}

/**
 * Pure playback and contrast math, kept free of Android types so it can be
 * exercised by JVM unit tests.
 */
object PrompterMath {
    const val FOCUS_BAND_FRACTION = 0.09f
    const val FOCUS_FADE_EXPONENT = 1.7f
    const val LIGHT_TEXT_THRESHOLD = 0.43f
    const val DARK_TEXT_THRESHOLD = 0.57f

    fun totalSeconds(
        wordCount: Int,
        speedWpm: Float,
    ): Int = ((wordCount / speedWpm.coerceAtLeast(1f)) * 60f).roundToInt().coerceAtLeast(1)

    fun remainingSeconds(
        totalSeconds: Int,
        progress: Float,
    ): Int = (totalSeconds * (1f - progress.coerceIn(0f, 1f))).roundToInt().coerceAtLeast(0)

    fun pixelsPerSecond(
        contentHeightPx: Float,
        wordCount: Int,
        speedWpm: Float,
    ): Float {
        if (contentHeightPx <= 0f) {
            return 0f
        }
        val seconds = (wordCount / speedWpm.coerceAtLeast(1f)) * 60f
        return contentHeightPx / seconds.coerceAtLeast(1f)
    }

    fun advance(
        offsetPx: Float,
        contentHeightPx: Float,
        wordCount: Int,
        speedWpm: Float,
        deltaSeconds: Float,
        loopEnabled: Boolean,
    ): AdvancedOffset {
        if (deltaSeconds <= 0f || contentHeightPx <= 0f) {
            return AdvancedOffset(offsetPx, false)
        }
        val pixelsPerSecond = pixelsPerSecond(contentHeightPx, wordCount, speedWpm)
        val next = offsetPx + pixelsPerSecond * deltaSeconds
        return if (next >= contentHeightPx) {
            if (loopEnabled) {
                AdvancedOffset(0f, false, looped = true)
            } else {
                AdvancedOffset(contentHeightPx, true)
            }
        } else {
            AdvancedOffset(next, false)
        }
    }

    fun seekDeltaPixels(
        contentHeightPx: Float,
        wordCount: Int,
        speedWpm: Float,
        seconds: Float,
    ): Float {
        if (contentHeightPx <= 0f) {
            return 0f
        }
        return pixelsPerSecond(contentHeightPx, wordCount, speedWpm) * seconds
    }

    fun offsetForProgress(
        progress: Float,
        contentHeightPx: Float,
    ): Float = progress.coerceIn(0f, 1f) * contentHeightPx.coerceAtLeast(0f)

    fun progressFor(
        offsetPx: Float,
        contentHeightPx: Float,
    ): Float =
        if (contentHeightPx > 0f) {
            (offsetPx / contentHeightPx).coerceIn(0f, 1f)
        } else {
            0f
        }

    fun adaptiveTone(luminance: Float): AdaptiveTone =
        when {
            luminance < LIGHT_TEXT_THRESHOLD -> AdaptiveTone.LIGHT
            luminance > DARK_TEXT_THRESHOLD -> AdaptiveTone.DARK
            else -> AdaptiveTone.UNCHANGED
        }

    /**
     * Position/alpha pairs for the focus mask, from the top edge to the
     * bottom edge. The centre band stays fully opaque.
     */
    fun focusGradientStops(
        edgeAlpha: Float,
        focusFraction: Float,
    ): List<Pair<Float, Float>> {
        val focus = focusFraction.coerceIn(0f, 1f)
        val edge = edgeAlpha.coerceIn(0f, 1f)
        val topBand = (focus - FOCUS_BAND_FRACTION).coerceIn(0f, 1f)
        val bottomBand = (focus + FOCUS_BAND_FRACTION).coerceIn(0f, 1f)
        // Intermediate stops with an eased curve so the maximum fade is
        // stronger than a linear ramp: mid-plateau content is dimmed well
        // below the linear midpoint.
        val stops = mutableListOf(0f to edge)
        for (index in 1 until 4) {
            val position = topBand * index / 4f
            val normalized = (index / 4f).coerceIn(0f, 1f)
            val alpha = edge + (1f - edge) * Math.pow(normalized.toDouble(), FOCUS_FADE_EXPONENT.toDouble()).toFloat()
            stops += position to alpha
        }
        stops += topBand to 1f
        stops += bottomBand to 1f
        for (index in 3 downTo 1) {
            val normalized = (index / 4f).coerceIn(0f, 1f)
            val alpha = edge + (1f - edge) * Math.pow(normalized.toDouble(), FOCUS_FADE_EXPONENT.toDouble()).toFloat()
            val position = bottomBand + (1f - bottomBand) * (4f - index) / 4f
            stops += position to alpha
        }
        stops += 1f to edge
        return stops
    }
}
