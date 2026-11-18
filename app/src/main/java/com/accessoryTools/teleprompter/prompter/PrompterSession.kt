package com.accessoryTools.teleprompter.prompter

import android.animation.ValueAnimator
import android.os.CountDownTimer
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.accessoryTools.teleprompter.data.OverlayArea
import com.accessoryTools.teleprompter.data.StageAlignment
import com.accessoryTools.teleprompter.data.StageBackground
import com.accessoryTools.teleprompter.data.StageFont
import com.accessoryTools.teleprompter.data.StageSettings
import com.accessoryTools.teleprompter.data.StageTextColor
import com.accessoryTools.teleprompter.data.StageWeight
import com.accessoryTools.teleprompter.data.argb
import com.accessoryTools.teleprompter.data.wordCount
import kotlin.math.abs

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
    private const val FLING_DECELERATION = 5500f
    private const val FLING_MIN_START = 120f

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

    var focusFraction by mutableFloatStateOf(0.3f)
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

    var customTextArgb by mutableIntStateOf(0xFFF7F4EC.toInt())
        private set

    var customBackgroundArgb by mutableIntStateOf(0xFF000000.toInt())
        private set

    var panelTranslucent by mutableStateOf(false)
        private set

    var customFontFile by mutableStateOf<String?>(null)
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

    var overlayArea by mutableStateOf(OverlayArea.FULL)
        private set

    var overlayAreaAdjusting by mutableStateOf(false)
        private set

    var lastContrastLuminance by mutableFloatStateOf(-1f)
        private set

    var adaptiveTextColorArgb by mutableIntStateOf(WHITE)
        private set

    var adaptiveOutlineColorArgb by mutableIntStateOf(DARK_OUTLINE)
        private set

    private var defaultSpeedWpm = 140f
    private var defaultFontSizeSp = 46f
    private var flingVelocity = 0f
    private var flingActive = false
    private var seekAnimator: ValueAnimator? = null

    private val choreographer by lazy { Choreographer.getInstance() }
    private var frameScheduled = false
    private var lastFrameNanos = 0L
    private var lastLayoutContentHeight = 0f
    private var pendingStartProgress: Float? = null
    private var countdownTimer: CountDownTimer? = null

    var onPrompterSettingsChanged: ((speedWpm: Float, fontSizeSp: Float, adaptiveContrast: Boolean) -> Unit)? = null
    var onStageSettingsChanged: ((StageSettings) -> Unit)? = null
    var onOverlayAreaChanged: ((OverlayArea) -> Unit)? = null
    var onOverlayAreaPreviewChanged: ((OverlayArea) -> Unit)? = null

    private val frameCallback =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameScheduled = false
                if (!isPlaying && !flingActive) {
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
                if (flingActive) {
                    advanceFling(deltaSeconds)
                }
                advance(deltaSeconds)
                if (isPlaying || flingActive) {
                    scheduleFrame()
                }
            }
        }

    fun setPlayback(playing: Boolean) {
        cancelCountdown()
        if (playing && script.isBlank()) {
            // Nothing to scroll — treat Play as a no-op instead of idling forever.
            isPlaying = false
            return
        }
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
        pendingStartProgress = if (startProgress.isFinite()) startProgress.coerceIn(0f, 1f) else 0f
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
        defaultSpeedWpm = if (speedWpm.isFinite()) speedWpm.coerceIn(StageSettings.MIN_WPM, StageSettings.MAX_WPM) else 140f
        defaultFontSizeSp = if (fontSizeSp.isFinite()) fontSizeSp.coerceIn(StageSettings.MIN_FONT_SP, StageSettings.MAX_FONT_SP) else 46f
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
        customTextArgb = sanitized.customTextArgb
        customBackgroundArgb = sanitized.customBackgroundArgb
        panelTranslucent = sanitized.panelTranslucent
        customFontFile = sanitized.customFontFile
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
            panelTranslucent = panelTranslucent,
            customTextArgb = customTextArgb,
            customBackgroundArgb = customBackgroundArgb,
            customFontFile = customFontFile,
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
        if (!value.isFinite()) return
        speedWpm = value.coerceIn(StageSettings.MIN_WPM, StageSettings.MAX_WPM)
        persistPrompterSettings()
        notifyStageSettingsChanged()
    }

    fun nudgeSpeed(delta: Float) {
        updateSpeedWpm(speedWpm + delta)
    }

    fun updateFontSizeSp(value: Float) {
        if (!value.isFinite()) return
        fontSizeSp = value.coerceIn(StageSettings.MIN_FONT_SP, StageSettings.MAX_FONT_SP)
        persistPrompterSettings()
        notifyStageSettingsChanged()
    }

    fun nudgeFontSize(delta: Float) {
        updateFontSizeSp(fontSizeSp + delta)
    }

    fun updateFocusFraction(value: Float) {
        if (!value.isFinite()) return
        focusFraction = value.coerceIn(StageSettings.MIN_FOCUS, StageSettings.MAX_FOCUS)
        notifyStageSettingsChanged()
    }

    fun updateGradientEdgeAlpha(value: Float) {
        if (!value.isFinite()) return
        gradientEdgeAlpha = value.coerceIn(0f, 1f)
        notifyStageSettingsChanged()
    }

    fun updateMarginDp(value: Float) {
        if (!value.isFinite()) return
        marginDp = value.coerceIn(StageSettings.MIN_MARGIN_DP, StageSettings.MAX_MARGIN_DP)
        notifyStageSettingsChanged()
    }

    fun updateLineSpacing(value: Float) {
        if (!value.isFinite()) return
        lineSpacing = value.coerceIn(StageSettings.MIN_LINE_SPACING, StageSettings.MAX_LINE_SPACING)
        notifyStageSettingsChanged()
    }

    fun updateStageFont(value: StageFont) {
        stageFont = value
        notifyStageSettingsChanged()
    }

    fun updateStageCustomFont(fileName: String?) {
        customFontFile = fileName
        stageFont = if (fileName != null) StageFont.CUSTOM else StageFont.SANS
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

    fun updateCustomTextColor(value: Int) {
        customTextArgb = value
        textColorPreset = StageTextColor.CUSTOM
        notifyStageSettingsChanged()
    }

    fun updateCustomBackgroundColor(value: Int) {
        customBackgroundArgb = value
        backgroundPreset = StageBackground.CUSTOM
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
            overlayAreaAdjusting = false
        }
    }

    fun applyOverlayArea(area: OverlayArea) {
        overlayArea = area.sanitized()
    }

    fun updateOverlayArea(area: OverlayArea) {
        val sanitized = area.sanitized()
        if (sanitized == overlayArea) {
            return
        }
        overlayArea = sanitized
        onOverlayAreaPreviewChanged?.invoke(sanitized)
    }

    fun persistOverlayArea() {
        onOverlayAreaChanged?.invoke(overlayArea)
    }

    fun updateOverlayAreaAdjusting(adjusting: Boolean) {
        overlayAreaAdjusting = adjusting
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
        if (!progress.isFinite()) return
        cancelCountdown()
        stopFling()
        lastFrameNanos = 0L
        pendingStartProgress = null
        isAtEnd = false
        val target = PrompterMath.offsetForProgress(progress, contentHeightPx).coerceIn(0f, contentHeightPx.coerceAtLeast(0f))
        seekAnimator?.cancel()
        val start = offsetPx
        val distance = abs(target - start)
        if (distance < 1f) {
            offsetPx = target
            return
        }
        if (distance > contentHeightPx * 0.5f) {
            // Large jumps (restore) snap instead of flying across the screen.
            offsetPx = target
            return
        }
        // Chase the thumb with a short ease so dragging the scrubber feels smooth
        // instead of stepped; big velocity changes just re-target the same chase.
        seekAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 150L
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { offsetPx = start + (target - start) * animatedFraction }
                start()
            }
    }

    fun seekBySeconds(seconds: Float) {
        if (contentHeightPx <= 0f) {
            return
        }
        scrollByPixels(PrompterMath.seekDeltaPixels(contentHeightPx, wordCount, speedWpm, seconds))
    }

    fun scrollByPixels(deltaPx: Float) {
        stopFling()
        seekAnimator?.cancel()
        cancelCountdown()
        val maxOffset = contentHeightPx.coerceAtLeast(0f)
        offsetPx = (offsetPx + deltaPx).coerceIn(0f, maxOffset)
        isAtEnd = maxOffset > 0f && offsetPx >= maxOffset
        lastFrameNanos = 0L
        pendingStartProgress = null
    }

    /**
     * Starts an inertial fling from [flingVelocity] (px per second in screen
     * coordinates; positive moves the text down, matching the drag direction).
     * The fling decelerates with a constant deceleration and stops at the
     * content bounds — the same feel as flinging a list.
     */
    fun startFling(flingVelocity: Float) {
        if (contentHeightPx <= 0f || abs(flingVelocity) < FLING_MIN_START) {
            this.flingVelocity = 0f
            flingActive = false
            return
        }
        cancelCountdown()
        seekAnimator?.cancel()
        this.flingVelocity = flingVelocity
        flingActive = true
        lastFrameNanos = 0L
        scheduleFrame()
    }

    fun stopFling() {
        flingVelocity = 0f
        flingActive = false
    }

    private fun advanceFling(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) {
            return
        }
        val dir = if (flingVelocity >= 0f) 1f else -1f
        val decel = FLING_DECELERATION * deltaSeconds
        flingVelocity =
            if (abs(flingVelocity) <= decel) {
                0f
            } else {
                flingVelocity - dir * decel
            }
        if (flingVelocity == 0f) {
            flingActive = false
            return
        }
        val maxOffset = contentHeightPx.coerceAtLeast(0f)
        val next = offsetPx - flingVelocity * deltaSeconds
        offsetPx = next.coerceIn(0f, maxOffset)
        isAtEnd = maxOffset > 0f && offsetPx >= maxOffset
        if (offsetPx <= 0f || offsetPx >= maxOffset) {
            flingVelocity = 0f
            flingActive = false
        }
    }

    fun updateAdaptiveContrast(luminance: Float) {
        lastContrastLuminance = luminance
        when (PrompterMath.adaptiveTone(luminance)) {
            AdaptiveTone.LIGHT -> {
                adaptiveTextColorArgb = WHITE
                adaptiveOutlineColorArgb = DARK_OUTLINE
            }

            AdaptiveTone.DARK -> {
                adaptiveTextColorArgb = BLACK
                adaptiveOutlineColorArgb = LIGHT_OUTLINE
            }

            AdaptiveTone.UNCHANGED -> {
                // Keep the current colour inside the hysteresis band.
            }
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
                    StageTextColor.CUSTOM -> customTextArgb
                }
            }

    val effectiveOutlineColorArgb: Int
        get() =
            if (adaptiveContrastEnabled && projectionActive) {
                adaptiveOutlineColorArgb
            } else if (textColorPreset == StageTextColor.INK ||
                (textColorPreset == StageTextColor.CUSTOM && luminance(customTextArgb) < 0.5f)
            ) {
                LIGHT_OUTLINE
            } else {
                DARK_OUTLINE
            }

    /** Background the stage + overlay draw. Custom colour when the preset is CUSTOM. */
    val effectiveBackgroundArgb: Int
        get() =
            if (backgroundPreset == StageBackground.CUSTOM) customBackgroundArgb else backgroundPreset.argb

    fun luminance(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    val progress: Float
        get() = PrompterMath.progressFor(offsetPx, contentHeightPx)

    val totalSeconds: Int
        get() = PrompterMath.totalSeconds(wordCount, speedWpm)

    val remainingSeconds: Int
        get() = PrompterMath.remainingSeconds(totalSeconds, progress)

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
        if (contentHeightPx <= 0f) {
            return
        }
        val next =
            PrompterMath.advance(
                offsetPx = offsetPx,
                contentHeightPx = contentHeightPx,
                wordCount = wordCount,
                speedWpm = speedWpm,
                deltaSeconds = deltaSeconds,
                loopEnabled = loopEnabled,
            )
        offsetPx = next.offsetPx
        if (next.isAtEnd) {
            isPlaying = false
            isAtEnd = true
            lastFrameNanos = 0L
        } else if (next.looped) {
            isAtEnd = false
            lastFrameNanos = 0L
        }
    }

    private fun scheduleFrame() {
        if (!frameScheduled) {
            frameScheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }
}
