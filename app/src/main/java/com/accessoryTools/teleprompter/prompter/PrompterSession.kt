package com.accessoryTools.teleprompter.prompter

import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.accessoryTools.teleprompter.data.wordCount
import kotlin.math.abs
import kotlin.math.roundToInt

object PrompterSession {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val DARK_OUTLINE = 0xE6000000.toInt()
    private const val LIGHT_OUTLINE = 0xE6FFFFFF.toInt()
    private const val MIN_WPM = 40f
    private const val MAX_WPM = 260f
    private const val MIN_FONT_SP = 18f
    private const val MAX_FONT_SP = 100f
    private const val FRAME_SECONDS_LIMIT = 0.1f

    val demoScript =
        listOf(
            "Welcome to the Teleprompter technical spike. This script is intentionally " +
                "long enough to test smooth scrolling, focus fading, transparent overlays, " +
                "picture-in-picture, and adaptive text contrast.",
            "Read at a comfortable pace. The highlighted band near the vertical center " +
                "stays fully opaque while the lines above and below fade gradually. This " +
                "keeps your eyes anchored to the line you are reading and still gives you " +
                "enough look-ahead to stay natural.",
            "The transparent mode draws only this text over the camera application. " +
                "The script window does not accept touches, so the camera remains fully " +
                "usable. A small control bar floats separately and can be dragged out of " +
                "the way whenever it is not needed.",
            "Picture-in-picture is the dependable fallback. It works without special " +
                "permissions and keeps the script above the camera while recording. The " +
                "window can be moved by the system. Tap it to reveal playback actions.",
            "Adaptive contrast samples the screen at a low rate. When the content " +
                "below is dark, the script turns light. When the content below is bright, " +
                "the script turns dark. An outline protects readability when the scene is " +
                "complex. If screen capture is unavailable, the manual high-contrast " +
                "colors remain in place.",
            "This is the point where the demonstration script ends. Stop scrolling, " +
                "change speed, restart, or leave the prompter using the controls. In the " +
                "next phases these controls become scrubbable values and the script will " +
                "be stored in a real local library.",
        ).joinToString("\n\n")

    var script by mutableStateOf(demoScript)
        private set

    var scriptTitle by mutableStateOf("Demo script")
        private set

    var scriptId by mutableStateOf<String?>(null)
        private set

    var wordCount by mutableIntStateOf(wordCount(demoScript).coerceAtLeast(1))
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isAtEnd by mutableStateOf(false)
        private set

    var offsetPx by mutableFloatStateOf(0f)
        private set

    var contentHeightPx by mutableFloatStateOf(0f)
        private set

    var viewportHeightPx by mutableFloatStateOf(0f)
        private set

    var speedWpm by mutableFloatStateOf(140f)
        private set

    var fontSizeSp by mutableFloatStateOf(46f)
        private set

    var focusFraction by mutableFloatStateOf(0.5f)
        private set

    var gradientEdgeAlpha by mutableFloatStateOf(0.28f)
        private set

    var adaptiveContrastEnabled by mutableStateOf(true)
        private set

    var projectionActive by mutableStateOf(false)
        private set

    var overlayRunning by mutableStateOf(false)
        private set

    var lastContrastLuminance by mutableFloatStateOf(-1f)
        private set

    var adaptiveTextColorArgb by mutableIntStateOf(WHITE)
        private set

    var adaptiveOutlineColorArgb by mutableIntStateOf(DARK_OUTLINE)
        private set

    private val choreographer by lazy { Choreographer.getInstance() }
    private var frameScheduled = false
    private var lastFrameNanos = 0L
    private var lastLayoutContentHeight = 0f
    private var pendingStartProgress: Float? = null

    var onPrompterSettingsChanged: ((speedWpm: Float, fontSizeSp: Float, adaptiveContrast: Boolean) -> Unit)? = null

    private val frameCallback =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameScheduled = false
                if (!isPlaying) {
                    return
                }
                val deltaSeconds =
                    if (lastFrameNanos == 0L) {
                        0f
                    } else {
                        ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f)
                            .coerceIn(0f, FRAME_SECONDS_LIMIT)
                    }
                lastFrameNanos = frameTimeNanos
                advance(deltaSeconds)
                if (isPlaying) {
                    scheduleFrame()
                }
            }
        }

    fun setPlayback(playing: Boolean) {
        if (playing && isAtEnd) {
            offsetPx = 0f
            isAtEnd = false
        }
        isPlaying = playing
        lastFrameNanos = 0L
        if (playing) {
            scheduleFrame()
        }
    }

    fun togglePlay() {
        setPlayback(!isPlaying)
    }

    fun restart() {
        offsetPx = 0f
        isAtEnd = false
        lastFrameNanos = 0L
        pendingStartProgress = null
    }

    fun restartAndPlay() {
        restart()
        setPlayback(true)
    }

    fun loadScript(
        id: String,
        title: String,
        content: String,
        startProgress: Float,
    ) {
        scriptId = id
        scriptTitle = title.ifBlank { "Untitled script" }
        script = content
        wordCount = wordCount(content).coerceAtLeast(1)
        offsetPx = 0f
        contentHeightPx = 0f
        lastLayoutContentHeight = 0f
        pendingStartProgress = startProgress.coerceIn(0f, 1f)
        isPlaying = false
        isAtEnd = false
        lastFrameNanos = 0L
    }

    fun applyPersistedSettings(
        speedWpm: Float,
        fontSizeSp: Float,
        adaptiveContrast: Boolean,
    ) {
        this.speedWpm = speedWpm.coerceIn(MIN_WPM, MAX_WPM)
        this.fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        adaptiveContrastEnabled = adaptiveContrast
    }

    private fun persistPrompterSettings() {
        onPrompterSettingsChanged?.invoke(speedWpm, fontSizeSp, adaptiveContrastEnabled)
    }

    fun updateSpeedWpm(value: Float) {
        speedWpm = value.coerceIn(MIN_WPM, MAX_WPM)
        persistPrompterSettings()
    }

    fun nudgeSpeed(delta: Float) {
        updateSpeedWpm(speedWpm + delta)
    }

    fun updateFontSizeSp(value: Float) {
        fontSizeSp = value.coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        persistPrompterSettings()
    }

    fun nudgeFontSize(delta: Float) {
        updateFontSizeSp(fontSizeSp + delta)
    }

    fun updateFocusFraction(value: Float) {
        focusFraction = value.coerceIn(0.2f, 0.8f)
    }

    fun updateGradientEdgeAlpha(value: Float) {
        gradientEdgeAlpha = value.coerceIn(0f, 1f)
    }

    fun updateAdaptiveContrastEnabled(enabled: Boolean) {
        adaptiveContrastEnabled = enabled
        persistPrompterSettings()
    }

    fun updateProjectionActive(active: Boolean) {
        projectionActive = active
        if (!active) {
            adaptiveTextColorArgb = WHITE
            adaptiveOutlineColorArgb = DARK_OUTLINE
        }
    }

    fun updateOverlayRunning(running: Boolean) {
        overlayRunning = running
        if (!running) {
            projectionActive = false
        }
    }

    fun updateLayout(
        contentHeight: Float,
        viewportHeight: Float,
    ) {
        val previousContent = lastLayoutContentHeight
        val previousProgress = if (previousContent > 0f) offsetPx / previousContent else 0f
        viewportHeightPx = viewportHeight
        contentHeightPx = contentHeight
        val pending = pendingStartProgress
        if (pending != null && contentHeight > 0f) {
            offsetPx = (pending * contentHeight).coerceIn(0f, contentHeight)
            pendingStartProgress = null
        } else if (previousContent > 0f && abs(previousContent - contentHeight) > 1f) {
            offsetPx = (previousProgress * contentHeight).coerceIn(0f, contentHeight)
        }
        lastLayoutContentHeight = contentHeight
        offsetPx = offsetPx.coerceIn(0f, contentHeight.coerceAtLeast(0f))
    }

    fun seekToProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        offsetPx = clamped * contentHeightPx
        isAtEnd = false
        lastFrameNanos = 0L
        pendingStartProgress = null
    }

    fun updateAdaptiveContrast(luminance: Float) {
        lastContrastLuminance = luminance
        val chooseLight =
            when {
                luminance < 0.43f -> true
                luminance > 0.57f -> false
                else -> return
            }
        if (chooseLight) {
            adaptiveTextColorArgb = WHITE
            adaptiveOutlineColorArgb = DARK_OUTLINE
        } else {
            adaptiveTextColorArgb = BLACK
            adaptiveOutlineColorArgb = LIGHT_OUTLINE
        }
    }

    val effectiveTextColorArgb: Int
        get() =
            if (adaptiveContrastEnabled && projectionActive) {
                adaptiveTextColorArgb
            } else {
                WHITE
            }

    val effectiveOutlineColorArgb: Int
        get() =
            if (adaptiveContrastEnabled && projectionActive) {
                adaptiveOutlineColorArgb
            } else {
                DARK_OUTLINE
            }

    val progress: Float
        get() =
            if (contentHeightPx > 0f) {
                (offsetPx / contentHeightPx).coerceIn(0f, 1f)
            } else {
                0f
            }

    val remainingMinutes: Int
        get() {
            val remainingFraction = (1f - progress).coerceIn(0f, 1f)
            val totalMinutes = wordCount / speedWpm
            return (totalMinutes * remainingFraction).roundToInt()
        }

    private fun advance(deltaSeconds: Float) {
        if (!isPlaying || deltaSeconds <= 0f) {
            return
        }
        val maxOffset = contentHeightPx.coerceAtLeast(0f)
        if (maxOffset <= 0f) {
            return
        }
        val pixelsPerSecond =
            maxOffset /
                ((wordCount / speedWpm).coerceAtLeast(1f) * 60f)
        val next = offsetPx + pixelsPerSecond * deltaSeconds
        if (next >= maxOffset) {
            offsetPx = maxOffset
            isPlaying = false
            isAtEnd = true
            lastFrameNanos = 0L
        } else {
            offsetPx = next
        }
    }

    private fun scheduleFrame() {
        if (!frameScheduled) {
            frameScheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }
}
