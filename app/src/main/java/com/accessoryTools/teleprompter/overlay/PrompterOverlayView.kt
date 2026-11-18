package com.accessoryTools.teleprompter.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.accessoryTools.teleprompter.data.OverlayArea
import com.accessoryTools.teleprompter.data.StageAlignment
import com.accessoryTools.teleprompter.data.StageFont
import com.accessoryTools.teleprompter.data.StageWeight
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.accessoryTools.teleprompter.prompter.StageTypefaces
import com.accessoryTools.teleprompter.prompter.WordWrap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PrompterOverlayView(
    context: Context,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fontScale = resources.configuration.fontScale
    private val scaledDensity = density * fontScale
    private val fillPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TypefaceHolder.medium
            color = PrompterSession.effectiveTextColorArgb
        }
    private val outlinePaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fillPaint.typeface
            style = Paint.Style.STROKE
            strokeWidth = 3.2f * density
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = PrompterSession.effectiveOutlineColorArgb
        }
    private val maskPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    private val adjustDimPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000
        }
    private val adjustBorderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT_TEAL
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
    private val adjustOuterPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 6f * density
        }
    private val handleFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F5DC.toInt()
        }
    private val handleStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
    private val hintPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * scaledDensity
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
    private val hintBackgroundPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
        }
    private val statusTextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f * density
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
    private val statusBackgroundPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6000000.toInt()
        }
    private var statusText: String? = null
    private val statusClearRunnable =
        Runnable {
            statusText = null
            invalidate()
        }
    private val maxContentWidth = 1_000f * density

    private var fillLayout: StaticLayout? = null
    private var outlineLayout: StaticLayout? = null
    private var layoutLeft = 0f
    private var layoutWidth = 0
    private var layoutFontSizeSp = -1f
    private var layoutLineSpacing = -1f
    private var layoutMarginDp = -1f
    private var layoutFont = StageFont.SANS
    private var layoutWeight = StageWeight.MEDIUM
    private var layoutAlignment = StageAlignment.CENTER
    private var layoutCustomFontFile: String? = null
    private var layoutScript = ""
    private var layoutArea: RectF? = null
    private var maskShader: LinearGradient? = null
    private var maskShaderEdge = -1f
    private var maskShaderFocus = -1f
    private var maskShaderTop = Float.NaN
    private var maskShaderBottom = Float.NaN
    private var attached = false
    private var looping = false
    private var adjusting = false

    private var touchMode = TouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val frameRunnable =
        object : Runnable {
            override fun run() {
                if (!attached || !PrompterSession.isPlaying) {
                    looping = false
                    invalidate()
                    return
                }
                invalidate()
                postOnAnimation(this)
            }
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun kick() {
        if (!looping && attached) {
            looping = true
            postOnAnimation(frameRunnable)
        }
    }

    fun setAdjusting(adjusting: Boolean) {
        if (this.adjusting == adjusting) {
            return
        }
        this.adjusting = adjusting
        touchMode = TouchMode.NONE
        isClickable = adjusting
        invalidate()
    }

    fun refreshOverlayArea() {
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        kick()
    }

    override fun onDetachedFromWindow() {
        attached = false
        removeCallbacks(frameRunnable)
        removeCallbacks(statusClearRunnable)
        looping = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildLayout(width)
        val area = currentAreaRect()
        PrompterSession.updateLayout(
            contentHeight = fillLayout?.height?.toFloat() ?: 0f,
            viewportHeight = area.height(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width
        val height = height
        if (width <= 0 || height <= 0) {
            return
        }
        val area = currentAreaRect()
        val currentFontSize = PrompterSession.fontSizeSp
        if (layoutWidth != width ||
            layoutArea != area ||
            layoutFontSizeSp != currentFontSize ||
            layoutLineSpacing != PrompterSession.lineSpacing ||
            layoutMarginDp != PrompterSession.marginDp ||
            layoutFont != PrompterSession.stageFont ||
            layoutWeight != PrompterSession.stageWeight ||
            layoutAlignment != PrompterSession.stageAlignment ||
            layoutCustomFontFile != PrompterSession.customFontFile ||
            layoutScript != PrompterSession.script
        ) {
            rebuildLayout(width)
        }
        val layout = fillLayout ?: return
        val outline = outlineLayout ?: return
        val contentHeight = layout.height.toFloat()
        PrompterSession.updateLayout(contentHeight, area.height())

        fillPaint.color = PrompterSession.effectiveTextColorArgb
        outlinePaint.color = PrompterSession.effectiveOutlineColorArgb
        val translateY = area.top + area.height() * PrompterSession.focusFraction - PrompterSession.offsetPx
        val mirrorHorizontal = PrompterSession.mirrorHorizontal
        val mirrorVertical = PrompterSession.mirrorVertical

        canvas.save()
        if (mirrorHorizontal || mirrorVertical) {
            canvas.translate(
                if (mirrorHorizontal) area.left + area.right else 0f,
                if (mirrorVertical) area.top + area.bottom else 0f,
            )
            canvas.scale(
                if (mirrorHorizontal) -1f else 1f,
                if (mirrorVertical) -1f else 1f,
            )
        }
        canvas.saveLayer(area.left, area.top, area.right, area.bottom, null)
        canvas.clipRect(area)
        canvas.save()
        canvas.translate(layoutLeft, translateY)
        outline.draw(canvas)
        layout.draw(canvas)
        canvas.restore()
        canvas.drawRect(area, maskPaintFor(area))
        canvas.restore()
        canvas.restore()

        if (adjusting) {
            drawAdjustmentOverlay(canvas, area)
        }
        if (!adjusting) {
            drawStatusChip(canvas, area)
        }
    }

    /**
     * Briefly shows a small readout (for example the current speed) in the
     * top corner of the overlay region. Unlike a Toast this can be shown on
     * every button tap without being rate-limited by the system.
     */
    fun showStatus(text: String) {
        statusText = text
        removeCallbacks(statusClearRunnable)
        postDelayed(statusClearRunnable, 1500L)
        invalidate()
    }

    private fun drawStatusChip(
        canvas: Canvas,
        area: RectF,
    ) {
        val text = statusText ?: return
        val textWidth = statusTextPaint.measureText(text)
        val padH = 14f * density
        val chipHeight = statusTextPaint.textSize + 14f * density
        val chipWidth = textWidth + padH * 2f
        val left = area.left + 10f * density
        val top = area.top + 10f * density
        val rect = RectF(left, top, left + chipWidth, top + chipHeight)
        canvas.drawRoundRect(rect, 9f * density, 9f * density, statusBackgroundPaint)
        val baseline = top + chipHeight / 2f - (statusTextPaint.descent() + statusTextPaint.ascent()) / 2f
        canvas.drawText(text, left + padH, baseline, statusTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!adjusting) {
            return false
        }
        val area = currentAreaRect()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val corner = hitCorner(event.x, event.y, area)
                touchMode =
                    when {
                        corner != null -> corner
                        area.contains(event.x, event.y) -> TouchMode.MOVE
                        else -> TouchMode.NONE
                    }
                if (touchMode == TouchMode.NONE) {
                    return false
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchMode == TouchMode.NONE) {
                    return false
                }
                val dx = (event.x - lastTouchX) / width.coerceAtLeast(1)
                val dy = (event.y - lastTouchY) / height.coerceAtLeast(1)
                val updated = applyDrag(PrompterSession.overlayArea, dx, dy)
                PrompterSession.updateOverlayArea(updated)
                lastTouchX = event.x
                lastTouchY = event.y
                layoutWidth = 0
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = touchMode != TouchMode.NONE
                touchMode = TouchMode.NONE
                if (handled) {
                    PrompterSession.persistOverlayArea()
                }
                return handled
            }

            else -> {
                return touchMode != TouchMode.NONE
            }
        }
    }

    private fun applyDrag(
        area: OverlayArea,
        dx: Float,
        dy: Float,
    ): OverlayArea {
        val minSize = OverlayArea.MIN_SIZE
        return when (touchMode) {
            TouchMode.MOVE -> {
                val widthFraction = area.right - area.left
                val heightFraction = area.bottom - area.top
                val left = (area.left + dx).coerceIn(0f, 1f - widthFraction)
                val top = (area.top + dy).coerceIn(0f, 1f - heightFraction)
                OverlayArea(left, top, left + widthFraction, top + heightFraction)
            }

            TouchMode.TOP_LEFT -> {
                OverlayArea(
                    left = (area.left + dx).coerceIn(0f, area.right - minSize),
                    top = (area.top + dy).coerceIn(0f, area.bottom - minSize),
                    right = area.right,
                    bottom = area.bottom,
                )
            }

            TouchMode.TOP_RIGHT -> {
                OverlayArea(
                    left = area.left,
                    top = (area.top + dy).coerceIn(0f, area.bottom - minSize),
                    right = (area.right + dx).coerceIn(area.left + minSize, 1f),
                    bottom = area.bottom,
                )
            }

            TouchMode.BOTTOM_LEFT -> {
                OverlayArea(
                    left = (area.left + dx).coerceIn(0f, area.right - minSize),
                    top = area.top,
                    right = area.right,
                    bottom = (area.bottom + dy).coerceIn(area.top + minSize, 1f),
                )
            }

            TouchMode.BOTTOM_RIGHT -> {
                OverlayArea(
                    left = area.left,
                    top = area.top,
                    right = (area.right + dx).coerceIn(area.left + minSize, 1f),
                    bottom = (area.bottom + dy).coerceIn(area.top + minSize, 1f),
                )
            }

            TouchMode.NONE -> {
                area
            }
        }
    }

    private fun hitCorner(
        x: Float,
        y: Float,
        area: RectF,
    ): TouchMode? {
        val radius = 24f * density
        val corners =
            listOf(
                TouchMode.TOP_LEFT to (area.left to area.top),
                TouchMode.TOP_RIGHT to (area.right to area.top),
                TouchMode.BOTTOM_LEFT to (area.left to area.bottom),
                TouchMode.BOTTOM_RIGHT to (area.right to area.bottom),
            )
        return corners
            .map { (mode, point) -> mode to abs(x - point.first) + abs(y - point.second) }
            .filter { (_, distance) -> distance <= radius * 2f }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    private fun rebuildLayout(viewWidth: Int) {
        if (viewWidth <= 0) {
            return
        }
        val area = currentAreaRect()
        val margin = PrompterSession.marginDp * density
        val availableWidth =
            max(
                1,
                min(area.width() - margin * 2f, maxContentWidth).toInt(),
            )
        layoutLeft =
            when (PrompterSession.stageAlignment) {
                StageAlignment.LEFT -> area.left + margin
                StageAlignment.RIGHT -> area.right - margin - availableWidth
                StageAlignment.CENTER -> area.left + (area.width() - availableWidth) / 2f
            }
        fillPaint.typeface =
            StageTypefaces.typefaceFor(
                PrompterSession.stageFont,
                PrompterSession.stageWeight,
                PrompterSession.customFontFile,
            )
        outlinePaint.typeface = fillPaint.typeface
        val fontSizePx = PrompterSession.fontSizeSp * scaledDensity
        fillPaint.textSize = fontSizePx
        outlinePaint.textSize = fontSizePx
        fillPaint.isFakeBoldText = PrompterSession.stageWeight == StageWeight.BOLD
        outlinePaint.isFakeBoldText = PrompterSession.stageWeight == StageWeight.BOLD
        val text = PrompterSession.script
        val wrappedText =
            WordWrap.wrap(
                text = text,
                maxWidth = (availableWidth - 1f).coerceAtLeast(1f),
                measure = { value -> fillPaint.measureText(value, 0, value.length) },
            )
        fillLayout = buildLayout(wrappedText, availableWidth, fillPaint)
        outlineLayout = buildLayout(wrappedText, availableWidth, outlinePaint)
        layoutWidth = viewWidth
        layoutFontSizeSp = PrompterSession.fontSizeSp
        layoutLineSpacing = PrompterSession.lineSpacing
        layoutMarginDp = PrompterSession.marginDp
        layoutFont = PrompterSession.stageFont
        layoutWeight = PrompterSession.stageWeight
        layoutAlignment = PrompterSession.stageAlignment
        layoutCustomFontFile = PrompterSession.customFontFile
        layoutScript = text
        layoutArea = area
        maskShader = null
        PrompterSession.updateLayout(
            contentHeight = fillLayout?.height?.toFloat() ?: 0f,
            viewportHeight = area.height(),
        )
        invalidate()
    }

    private fun buildLayout(
        text: String,
        availableWidth: Int,
        paint: TextPaint,
    ): StaticLayout {
        val alignment =
            when (PrompterSession.stageAlignment) {
                StageAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
                StageAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                StageAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            }
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, availableWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, PrompterSession.lineSpacing)
            .setIncludePad(false)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    private fun currentAreaRect(): RectF =
        PrompterSession.overlayArea.toRect(width.coerceAtLeast(1), height.coerceAtLeast(1))

    private fun maskPaintFor(area: RectF): Paint {
        val edge = PrompterSession.gradientEdgeAlpha
        val focus = PrompterSession.focusFraction
        if (maskShader == null ||
            maskShaderEdge != edge ||
            maskShaderFocus != focus ||
            maskShaderTop != area.top ||
            maskShaderBottom != area.bottom
        ) {
            val band = 0.09f
            val colors =
                intArrayOf(
                    alphaColor(edge),
                    alphaColor(1f),
                    alphaColor(1f),
                    alphaColor(edge),
                )
            val positions =
                floatArrayOf(
                    0f,
                    (focus - band).coerceIn(0f, 1f),
                    (focus + band).coerceIn(0f, 1f),
                    1f,
                )
            maskShader =
                LinearGradient(
                    0f,
                    area.top,
                    0f,
                    area.bottom,
                    colors,
                    positions,
                    Shader.TileMode.CLAMP,
                )
            maskPaint.shader = maskShader
            maskShaderEdge = edge
            maskShaderFocus = focus
            maskShaderTop = area.top
            maskShaderBottom = area.bottom
        }
        return maskPaint
    }

    private fun drawAdjustmentOverlay(
        canvas: Canvas,
        area: RectF,
    ) {
        val width = width.toFloat()
        val height = height.toFloat()
        canvas.drawRect(0f, 0f, width, area.top, adjustDimPaint)
        canvas.drawRect(0f, area.bottom, width, height, adjustDimPaint)
        canvas.drawRect(0f, area.top, area.left, area.bottom, adjustDimPaint)
        canvas.drawRect(area.right, area.top, width, area.bottom, adjustDimPaint)

        canvas.drawRect(area, adjustOuterPaint)
        canvas.drawRect(area, adjustBorderPaint)

        val handle = 22f * density
        val half = handle / 2f
        listOf(
            area.left to area.top,
            area.right to area.top,
            area.left to area.bottom,
            area.right to area.bottom,
        ).forEach { (x, y) ->
            canvas.drawRect(x - half, y - half, x + half, y + half, handleFillPaint)
            canvas.drawRect(x - half, y - half, x + half, y + half, handleStrokePaint)
        }

        val hint = "Drag inside to move · corners to resize"
        val textWidth = hintPaint.measureText(hint)
        val padding = 10f * density
        val boxHeight = 30f * density
        val boxTop =
            if (area.top > boxHeight + 16f * density) {
                area.top - boxHeight - 10f * density
            } else {
                area.top + 10f * density
            }
        val boxLeft = (area.left + 8f * density).coerceAtMost((width - textWidth - padding * 2f).coerceAtLeast(0f))
        canvas.drawRect(
            boxLeft,
            boxTop,
            boxLeft + textWidth + padding * 2f,
            boxTop + boxHeight,
            hintBackgroundPaint,
        )
        canvas.drawText(hint, boxLeft + padding, boxTop + boxHeight * 0.68f, hintPaint)
    }

    private fun alphaColor(alpha: Float): Int = Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)

    private enum class TouchMode {
        NONE,
        MOVE,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    private object TypefaceHolder {
        val medium: android.graphics.Typeface = StageTypefaces.typefaceFor(StageFont.SANS, StageWeight.MEDIUM, null)
    }

    companion object {
        private const val ACCENT_TEAL = 0xFF00C2C8.toInt()
    }
}
