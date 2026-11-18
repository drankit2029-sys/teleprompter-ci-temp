package com.accessoryTools.teleprompter.prompter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrompterMathTest {
    @Test
    fun totalSecondsRoundsAndClamps() {
        assertEquals(101, PrompterMath.totalSeconds(235, 140f))
        assertEquals(1, PrompterMath.totalSeconds(0, 140f))
        assertEquals(60, PrompterMath.totalSeconds(100, 100f))
    }

    @Test
    fun totalSecondsTreatsNonPositiveSpeedAsOneWpm() {
        assertEquals(600, PrompterMath.totalSeconds(10, 0f))
        assertEquals(600, PrompterMath.totalSeconds(10, -25f))
    }

    @Test
    fun remainingSecondsTracksProgress() {
        assertEquals(60, PrompterMath.remainingSeconds(60, 0f))
        assertEquals(30, PrompterMath.remainingSeconds(60, 0.5f))
        assertEquals(0, PrompterMath.remainingSeconds(60, 1f))
        assertEquals(0, PrompterMath.remainingSeconds(60, 2f))
        assertEquals(60, PrompterMath.remainingSeconds(60, -1f))
    }

    @Test
    fun pixelsPerSecondUsesWordCountAndHeight() {
        assertEquals(
            1000f / 60f,
            PrompterMath.pixelsPerSecond(
                contentHeightPx = 1000f,
                wordCount = 100,
                speedWpm = 100f,
            ),
            0.0001f,
        )
        assertEquals(0f, PrompterMath.pixelsPerSecond(0f, 100, 100f))
    }

    @Test
    fun pixelsPerSecondScalesForScriptsShorterThanAMinute() {
        // 26 words at 40 WPM is 39 seconds and at 260 WPM is 6 seconds. The
        // scroll rate must follow those durations instead of clamping to 60s.
        assertEquals(
            25f,
            PrompterMath.pixelsPerSecond(
                contentHeightPx = 975f,
                wordCount = 26,
                speedWpm = 40f,
            ),
            0.0001f,
        )
        assertEquals(
            162.5f,
            PrompterMath.pixelsPerSecond(
                contentHeightPx = 975f,
                wordCount = 26,
                speedWpm = 260f,
            ),
            0.0001f,
        )
    }

    @Test
    fun pixelsPerSecondCoversContentWithinTheReportedDuration() {
        val cases =
            listOf(
                26 to 40f,
                26 to 260f,
                100 to 100f,
                235 to 140f,
                235 to 260f,
            )
        for ((words, wpm) in cases) {
            val height = 1000f
            val seconds = PrompterMath.totalSeconds(words, wpm)
            val travelled = PrompterMath.pixelsPerSecond(height, words, wpm) * seconds
            assertEquals("words=$words wpm=$wpm", height, travelled, height * 0.02f)
        }
    }

    @Test
    fun advanceCompletesShortScriptAtReportedDuration() {
        val result =
            PrompterMath.advance(
                offsetPx = 0f,
                contentHeightPx = 975f,
                wordCount = 26,
                speedWpm = 260f,
                deltaSeconds = 6f,
                loopEnabled = false,
            )
        assertEquals(975f, result.offsetPx, 0.0001f)
        assertTrue(result.isAtEnd)
    }

    @Test
    fun advanceMovesProportionally() {
        val result =
            PrompterMath.advance(
                offsetPx = 100f,
                contentHeightPx = 1000f,
                wordCount = 100,
                speedWpm = 100f,
                deltaSeconds = 6f,
                loopEnabled = false,
            )
        assertEquals(200f, result.offsetPx, 0.0001f)
        assertFalse(result.isAtEnd)
        assertFalse(result.looped)
    }

    @Test
    fun advanceStopsAtEndWithoutLoop() {
        val result =
            PrompterMath.advance(
                offsetPx = 990f,
                contentHeightPx = 1000f,
                wordCount = 100,
                speedWpm = 6000f,
                deltaSeconds = 10f,
                loopEnabled = false,
            )
        assertEquals(1000f, result.offsetPx, 0.0001f)
        assertTrue(result.isAtEnd)
        assertFalse(result.looped)
    }

    @Test
    fun advanceWrapsToTopWithLoop() {
        val result =
            PrompterMath.advance(
                offsetPx = 990f,
                contentHeightPx = 1000f,
                wordCount = 100,
                speedWpm = 6000f,
                deltaSeconds = 10f,
                loopEnabled = true,
            )
        assertEquals(0f, result.offsetPx, 0.0001f)
        assertFalse(result.isAtEnd)
        assertTrue(result.looped)
    }

    @Test
    fun seekDeltaPixelsMatchesPlaybackRate() {
        assertEquals(
            500f / 6f,
            PrompterMath.seekDeltaPixels(
                contentHeightPx = 1000f,
                wordCount = 100,
                speedWpm = 100f,
                seconds = 5f,
            ),
            0.0001f,
        )
        assertEquals(0f, PrompterMath.seekDeltaPixels(0f, 100, 100f, 5f))
    }

    @Test
    fun progressAndOffsetRoundTripAndClamp() {
        assertEquals(250f, PrompterMath.offsetForProgress(0.25f, 1000f), 0.0001f)
        assertEquals(1000f, PrompterMath.offsetForProgress(2f, 1000f), 0.0001f)
        assertEquals(0f, PrompterMath.offsetForProgress(-1f, 1000f), 0.0001f)
        assertEquals(0.25f, PrompterMath.progressFor(250f, 1000f), 0.0001f)
        assertEquals(1f, PrompterMath.progressFor(1500f, 1000f), 0.0001f)
        assertEquals(0f, PrompterMath.progressFor(10f, 0f), 0.0001f)
    }

    @Test
    fun adaptiveToneUsesHysteresisBand() {
        assertEquals(AdaptiveTone.LIGHT, PrompterMath.adaptiveTone(0f))
        assertEquals(AdaptiveTone.LIGHT, PrompterMath.adaptiveTone(0.42f))
        assertEquals(AdaptiveTone.UNCHANGED, PrompterMath.adaptiveTone(0.43f))
        assertEquals(AdaptiveTone.UNCHANGED, PrompterMath.adaptiveTone(0.5f))
        assertEquals(AdaptiveTone.UNCHANGED, PrompterMath.adaptiveTone(0.57f))
        assertEquals(AdaptiveTone.DARK, PrompterMath.adaptiveTone(0.58f))
        assertEquals(AdaptiveTone.DARK, PrompterMath.adaptiveTone(1f))
    }

    @Test
    fun focusGradientStopsStayOrderedAndClamped() {
        val stops = PrompterMath.focusGradientStops(edgeAlpha = 0.25f, focusFraction = 0.5f)
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(0.25f, stops.first().second, 0.0001f)
        assertEquals(1f, stops.last().first, 0f)
        assertEquals(0.25f, stops.last().second, 0.0001f)
        assertTrue(stops.zipWithNext().all { (left, right) -> left.first <= right.first })
        assertTrue(stops.all { (_, alpha) -> alpha in 0f..1f })
        // The centre band is pinned at full clarity.
        assertTrue(stops.any { it.second == 1f })
        // The eased ramp keeps the middle of the fade below the arithmetic mean,
        // making the maximum fade visibly stronger.
        assertTrue(stops[1].second < 0.5f)
    }

    @Test
    fun focusGradientStopsClampExtremeInputs() {
        val top = PrompterMath.focusGradientStops(edgeAlpha = -1f, focusFraction = 0f)
        assertEquals(0f, top[0].second, 0f)
        assertEquals(0f, top[0].first, 0f)
        assertTrue(top.zipWithNext().all { (left, right) -> left.first <= right.first })
        assertTrue(top.all { (_, alpha) -> alpha in 0f..1f })
        val bottom = PrompterMath.focusGradientStops(edgeAlpha = 2f, focusFraction = 1f)
        assertEquals(1f, bottom.last().first, 0f)
        assertEquals(1f, bottom.last().second, 0f)
        assertTrue(bottom.zipWithNext().all { (left, right) -> left.first <= right.first })
        assertTrue(bottom.all { (_, alpha) -> alpha in 0f..1f })
    }
}
