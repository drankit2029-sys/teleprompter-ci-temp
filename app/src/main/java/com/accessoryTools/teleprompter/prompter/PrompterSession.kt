package com.accessoryTools.teleprompter.prompter

import android.os.CountDownTimer
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.accessoryTools.teleprompter.data.StageAlignment
import com.accessoryTools.teleprompter.data.StageBackground
import com.accessoryTools.teleprompter.data.StageFont
import com.accessoryTools.teleprompter.data.StageSettings
import com.accessoryTools.teleprompter.data.StageTextColor
import com.accessoryTools.teleprompter.data.StageWeight
import com.accessoryTools.teleprompter.data.argb
import com.accessoryTools.teleprompter.data.wordCount
import kotlin.math.abs
import kotlin.math.roundToInt

object PrompterSession {
    private val WHITE = StageTextColor.WHITE.argb
    private val BLACK = 0xFF000000.toInt()
    private val WARM_WHITE = StageTextColor.WARM_WHITE.argb
    private val AMBER = StageTextColor.AMBER.argb
    private val MINT = StageTextColor.MINT.argb
    private val INK = StageTextColor.INK.argb
    private const val DARK_OUTLINE = 0xE6000000.toInt()
    private const val LIGHT_OUTLINE = 0xE6FFFFFF.toInt()
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

    var marginDp by mutableFloatStateOf(28f)
        private set

    var lineSpacing by mutableFloatStateOf(1.34f)
        private set

    var stageFont by mutableStateOf(StageFont.SANS)
        private set

    var stageWeight by mutableStateOf(StageWeight.MEDIUM)
        private set

    var stageAlignment by mutableStateOf(StageAlignment.CENTER)
        private set

    var textColorPreset by mutableStateOf(StageTextColor.WARM_WHITE)
        private set

    var backgroundPreset by mutableStateOf(StageBackground.BLACK)
        private set

    var mirrorHorizontal by mutableStateOf(false)
        private set

    var mirrorVertical by mutableStateOf(false)
        private set

    var countdownSeconds by mutableIntStateOf(3)
        private set

    var countdownRemaining by mutableIntStateOf(0)
        private set

    var loopEnabled by mutableStateOf(false)
        private set

    var keepAwakeEnabled by mutableStateOf(true)
        private set

    var locked by mutableStateOf(false)
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

    private var defaultSpeedWpm = 140f
    private var defaultFontSizeSp = 46f

    private val choreographer by lazy { Choreographer.getInstance() }
    private var frameScheduled = false
    private var lastFrameNanos = 0L
    private var lastLayoutContentHeight = 0f
    private var pendingStartProgress: Float? = null
    private var countdownTimer: CountDownTimer? = null

    var onPrompterSettingsChanged: ((speedWpm: Float, fontSizeSp: Float, adaptiveContrast: Boolean) -> Unit)? = null
    var onStageSettingsChanged: ((StageSettings) -> Unit)? = null

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
        cancelCountdown()
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
        when {
            countdownRemaining > 0 -> {
                cancelCountdown()
            }

            isPlaying -> {
                setPlayback(false)
            }

            else -> {
                val startingFromTop = offsetPx <= 1f || isAtEnd
                if (isAtEnd) {
                    offsetPx = 0f
                    isAtEnd = false
                }
                if (startingFromTop && countdownSeconds > 0) {
                    startCountdown()
                } else {
                    setPlayback(true)
                }
            }
        }
    }

    fun restart() {
        cancelCountdown()
        offsetPx = 0f
        isAtEnd = false
        lastFrameNanos = 0L
        pendingStartProgress = null
    }

    fun restartAndPlay() {
        restart()
        setPlayback(false)
        togglePlay()
    }

    fun loadScript(
        id: String,
        title: String,
        content: String,
        startProgress: Float,
        stage: StageSettings? = null,
    ) {
        cancelCountdown()
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
        locked = false
        lastFrameNanos = 0L
        applyStageSettings(
            stage ?: StageSettings(
                speedWpm = defaultSpeedWpm,
                fontSizeSp = defaultFontSizeSp,
            ),
        )
    }

    fun applyPersistedSettings(
        speedWpm: Float,
        fontSizeSp: Float,
        adaptiveContrast: Boolean,
    ) {
        defaultSpeedWpm = speedWpm.coerceIn(StageSettings.MIN_WPM, StageSettings.MAX_WPM)
        defaultFontSizeSp = fontSizeSp.coerceIn(StageSettings.MIN_FONT_SP, StageSettings.MAX_FONT_SP)
        this.speedWpm = defaultSpeedWpm
        this.fontSizeSp = defaultFontSizeSp
        adaptiveContrastEnabled = adaptiveContrast
    }

    private fun applyStageSettings(settings: StageSettings) {
        val sanitized = settings.sanitized()
        speedWpm = sanitized.speedWpm
        fontSizeSp = sanitized.fontSizeSp
        focusFraction = sanitized.focusFraction
        gradientEdgeAlpha = sanitized.edgeAlpha
        marginDp = sanitized.marginDp
        lineSpacing = sanitized.lineSpacing
        stageFont = sanitized.font
        stageWeight = sanitized.weight
        stageAlignment = sanitized.alignment
        textColorPreset = sanitized.textColor
        backgroundPreset = sanitized.background
        mirrorHorizontal = sanitized.mirrorHorizontal
        mirrorVertical = sanitized.mirrorVertical
        countdownSeconds = sanitized.countdownSeconds
        loopEnabled = sanitized.loop
        keepAwakeEnabled = sanitized.keepAwake
    }

    fun currentStageSettings(): StageSettings =
        StageSettings(
            speedWpm = speedWpm,
            fontSizeSp = fontSizeSp,
            focusFraction = focusFraction,
            edgeAlpha = gradientEdgeAlpha,
            marginDp = marginDp,
            lineSpacing = lineSpacing,
            font = stageFont,
            weight = stageWeight,
            alignment = stageAlignment,
            textColor = textColorPreset,
            background = backgroundPreset,
            mirrorHorizontal = mirrorHorizontal,
            mirrorVertical = mirrorVertical,
            countdownSeconds = countdownSeconds,
            loop = loopEnabled,
            keepAwake = keepAwakeEnabled,
        ).sanitized()

    private fun persistPrompterSettings() {
        defaultSpeedWpm = speedWpm
        defaultFontSizeSp = fontSizeSp
        onPrompterSettingsChanged?.invoke(speedWpm, fontSizeSp, adaptiveContrastEnabled)
    }

    private fun notifyStageSettingsChanged() {
        onStageSettingsChanged?.invoke(currentStageSettings())
    }

    fun updateSpeedWpm(value: Float) {
        speedWpm = value.coerceIn(StageSettings.MIN_WPM, StageSettings.MAX_WPM)
        persistPrompterSettings()
        notifyStageSettingsChanged()
    }

    fun nudgeSpeed(delta: Float) {
        updateSpeedWpm(speedWpm + delta)
    }

    fun updateFontSizeSp(value: Float) {
        fontSizeSp = value.coerceIn(StageSettings.MIN_FONT_SP, StageSettings.MAX_FONT_SP)
        persistPrompterSettings()
        notifyStageSettingsChanged()
    }

    fun nudgeFontSize(delta: Float) {
        updateFontSizeSp(fontSizeSp + delta)
    }

    fun updateFocusFraction(value: Float) {
        focusFraction = value.coerceIn(StageSettings.MIN_FOCUS, StageSettings.MAX_FOCUS)
        notifyStageSettingsChanged()
    }

    fun updateGradientEdgeAlpha(value: Float) {
        gradientEdgeAlpha = value.coerceIn(0f, 1f)
        notifyStageSettingsChanged()
    }

    fun updateMarginDp(value: Float) {
        marginDp = value.coerceIn(StageSettings.MIN_MARGIN_DP, StageSettings.MAX_MARGIN_DP)
        notifyStageSettingsChanged()
    }

    fun updateLineSpacing(value: Float) {
        lineSpacing = value.coerceIn(StageSettings.MIN_LINE_SPACING, StageSettings.MAX_LINE_SPACING)
        notifyStageSettingsChanged()
    }

    fun updateStageFont(value: StageFont) {
        stageFont = value
        notifyStageSettingsChanged()
    }

    fun updateStageWeight(value: StageWeight) {
        stageWeight = value
        notifyStageSettingsChanged()
    }

    fun updateStageAlignment(value: StageAlignment) {
        stageAlignment = value
        notifyStageSettingsChanged()
    }

    fun updateTextColorPreset(value: StageTextColor) {
        textColorPreset = value
        notifyStageSettingsChanged()
    }

    fun updateBackgroundPreset(value: StageBackground) {
        backgroundPreset = value
        notifyStageSettingsChanged()
    }

    fun toggleMirrorHorizontal() {
        mirrorHorizontal = !mirrorHorizontal
        notifyStageSettingsChanged()
    }

    fun toggleMirrorVertical() {
        mirrorVertical = !mirrorVertical
        notifyStageSettingsChanged()
    }

    fun updateCountdownSeconds(value: Int) {
        countdownSeconds = value.coerceIn(0, 5)
        notifyStageSettingsChanged()
    }

    fun updateLoopEnabled(enabled: Boolean) {
        loopEnabled = enabled
        notifyStageSettingsChanged()
    }

    fun updateKeepAwakeEnabled(enabled: Boolean) {
        keepAwakeEnabled = enabled
        notifyStageSettingsChanged()
    }

    fun toggleLock() {
        locked = !locked
    }

    fun setControlLocked(value: Boolean) {
        locked = value
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
        cancelCountdown()
        val clamped = progress.coerceIn(0f, 1f)
        offsetPx = clamped * contentHeightPx
        isAtEnd = false
        lastFrameNanos = 0L
        pendingStartProgress = null
    }

    fun seekBySeconds(seconds: Float) {
        if (contentHeightPx <= 0f) {
            return
        }
        val totalSeconds = (wordCount / speedWpm.coerceAtLeast(1f)).coerceAtLeast(1f) * 60f
        val pixelsPerSecond = contentHeightPx / totalSeconds
        scrollByPixels(pixelsPerSecond * seconds)
    }

    fun scrollByPixels(deltaPx: Float) {
        cancelCountdown()
        val maxOffset = contentHeightPx.coerceAtLeast(0f)
        offsetPx = (offsetPx + deltaPx).coerceIn(0f, maxOffset)
        isAtEnd = maxOffset > 0f && offsetPx >= maxOffset
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
                when (textColorPreset) {
                    StageTextColor.WARM_WHITE -> WARM_WHITE
                    StageTextColor.WHITE -> WHITE
                    StageTextColor.AMBER -> AMBER
                    StageTextColor.MINT -> MINT
                    StageTextColor.INK -> INK
                }
            }

    val effectiveOutlineColorArgb: Int
        get() =
            if (adaptiveContrastEnabled && projectionActive) {
                adaptiveOutlineColorArgb
            } else if (textColorPreset == StageTextColor.INK) {
                LIGHT_OUTLINE
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

    val totalSeconds: Int
        get() = ((wordCount / speedWpm.coerceAtLeast(1f)) * 60f).roundToInt().coerceAtLeast(1)

    val remainingSeconds: Int
        get() = (totalSeconds * (1f - progress).coerceIn(0f, 1f)).roundToInt().coerceAtLeast(0)

    private fun startCountdown() {
        cancelCountdown()
        val seconds = countdownSeconds.coerceAtLeast(1)
        countdownRemaining = seconds
        countdownTimer =
            object : CountDownTimer(seconds * 1_000L, 250L) {
                override fun onTick(millisUntilFinished: Long) {
                    countdownRemaining =
                        ((millisUntilFinished + 999L) / 1_000L)
                            .toInt()
                            .coerceIn(1, seconds)
                }

                override fun onFinish() {
                    countdownTimer = null
                    countdownRemaining = 0
                    setPlayback(true)
                }
            }.start()
    }

    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        countdownRemaining = 0
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
                ((wordCount / speedWpm.coerceAtLeast(1f)).coerceAtLeast(1f) * 60f)
        val next = offsetPx + pixelsPerSecond * deltaSeconds
        if (next >= maxOffset) {
            if (loopEnabled) {
                offsetPx = 0f
                isAtEnd = false
                lastFrameNanos = 0L
            } else {
                offsetPx = maxOffset
                isPlaying = false
                isAtEnd = true
                lastFrameNanos = 0L
            }
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
