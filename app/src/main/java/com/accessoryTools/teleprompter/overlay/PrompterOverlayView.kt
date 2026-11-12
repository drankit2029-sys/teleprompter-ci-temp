package com.accessoryTools.teleprompter.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.accessoryTools.teleprompter.data.StageAlignment
import com.accessoryTools.teleprompter.data.StageFont
import com.accessoryTools.teleprompter.data.StageWeight
import com.accessoryTools.teleprompter.prompter.PrompterSession
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
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
    private var layoutScript = ""
    private var maskShader: LinearGradient? = null
    private var maskShaderEdge = -1f
    private var maskShaderFocus = -1f
    private var maskShaderHeight = -1
    private var attached = false
    private var looping = false

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        kick()
    }

    override fun onDetachedFromWindow() {
        attached = false
        removeCallbacks(frameRunnable)
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
        PrompterSession.updateLayout(
            contentHeight = fillLayout?.height?.toFloat() ?: 0f,
            viewportHeight = height.toFloat(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width
        val height = height
        if (width <= 0 || height <= 0) {
            return
        }
        val currentFontSize = PrompterSession.fontSizeSp
        if (layoutWidth != width ||
            layoutFontSizeSp != currentFontSize ||
            layoutLineSpacing != PrompterSession.lineSpacing ||
            layoutMarginDp != PrompterSession.marginDp ||
            layoutFont != PrompterSession.stageFont ||
            layoutWeight != PrompterSession.stageWeight ||
            layoutAlignment != PrompterSession.stageAlignment ||
            layoutScript != PrompterSession.script
        ) {
            rebuildLayout(width)
        }
        val layout = fillLayout ?: return
        val outline = outlineLayout ?: return
        val contentHeight = layout.height.toFloat()
        PrompterSession.updateLayout(contentHeight, height.toFloat())

        fillPaint.color = PrompterSession.effectiveTextColorArgb
        outlinePaint.color = PrompterSession.effectiveOutlineColorArgb
        val focusY = height * PrompterSession.focusFraction
        val translateY = focusY - PrompterSession.offsetPx
        val mirrorHorizontal = PrompterSession.mirrorHorizontal
        val mirrorVertical = PrompterSession.mirrorVertical

        canvas.save()
        if (mirrorHorizontal || mirrorVertical) {
            canvas.translate(
                if (mirrorHorizontal) width.toFloat() else 0f,
                if (mirrorVertical) height.toFloat() else 0f,
            )
            canvas.scale(
                if (mirrorHorizontal) -1f else 1f,
                if (mirrorVertical) -1f else 1f,
            )
        }
        canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.save()
        canvas.translate(layoutLeft, translateY)
        outline.draw(canvas)
        layout.draw(canvas)
        canvas.restore()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaintFor())
        canvas.restore()
        canvas.restore()
    }

    private fun rebuildLayout(viewWidth: Int) {
        if (viewWidth <= 0) {
            return
        }
        val margin = PrompterSession.marginDp * density
        val availableWidth =
            max(
                1,
                min(viewWidth - margin * 2f, maxContentWidth).toInt(),
            )
        layoutLeft = (viewWidth - availableWidth) / 2f
        fillPaint.typeface = typefaceFor(PrompterSession.stageFont, PrompterSession.stageWeight)
        outlinePaint.typeface = fillPaint.typeface
        val fontSizePx = PrompterSession.fontSizeSp * scaledDensity
        fillPaint.textSize = fontSizePx
        outlinePaint.textSize = fontSizePx
        val text = PrompterSession.script
        fillLayout = buildLayout(text, availableWidth, fillPaint)
        outlineLayout = buildLayout(text, availableWidth, outlinePaint)
        layoutWidth = viewWidth
        layoutFontSizeSp = PrompterSession.fontSizeSp
        layoutLineSpacing = PrompterSession.lineSpacing
        layoutMarginDp = PrompterSession.marginDp
        layoutFont = PrompterSession.stageFont
        layoutWeight = PrompterSession.stageWeight
        layoutAlignment = PrompterSession.stageAlignment
        layoutScript = text
        maskShader = null
        PrompterSession.updateLayout(
            contentHeight = fillLayout?.height?.toFloat() ?: 0f,
            viewportHeight = height.toFloat(),
        )
        invalidate()
    }

    private fun typefaceFor(
        font: StageFont,
        weight: StageWeight,
    ): Typeface {
        val family =
            when (font) {
                StageFont.SANS -> if (weight == StageWeight.MEDIUM) "sans-serif-medium" else "sans-serif"
                StageFont.SERIF -> "serif"
                StageFont.MONO -> "monospace"
            }
        val style = if (weight == StageWeight.BOLD) Typeface.BOLD else Typeface.NORMAL
        return Typeface.create(family, style)
    }

    private fun buildLayout(
        text: String,
        availableWidth: Int,
        paint: TextPaint,
    ): StaticLayout {
        val alignment =
            if (PrompterSession.stageAlignment == StageAlignment.LEFT) {
                Layout.Alignment.ALIGN_NORMAL
            } else {
                Layout.Alignment.ALIGN_CENTER
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

    private fun maskPaintFor(): Paint {
        val edge = PrompterSession.gradientEdgeAlpha
        val focus = PrompterSession.focusFraction
        val currentHeight = height
        if (maskShader == null ||
            maskShaderEdge != edge ||
            maskShaderFocus != focus ||
            maskShaderHeight != currentHeight
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
                    0f,
                    0f,
                    currentHeight.toFloat(),
                    colors,
                    positions,
                    Shader.TileMode.CLAMP,
                )
            maskPaint.shader = maskShader
            maskShaderEdge = edge
            maskShaderFocus = focus
            maskShaderHeight = currentHeight
        }
        return maskPaint
    }

    private fun alphaColor(alpha: Float): Int = Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)
}
