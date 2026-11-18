package com.accessoryTools.teleprompter.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import com.accessoryTools.teleprompter.prompter.PrompterSession
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class OverlayControlBar(
    context: Context,
    private val onPlayPause: () -> Unit,
    private val onRestart: () -> Unit,
    private val onSlower: () -> Unit,
    private val onFaster: () -> Unit,
    private val onToggleArea: () -> Unit,
    private val onOpenStudio: () -> Unit,
    private val onStop: () -> Unit,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
) : LinearLayout(context) {
    private val compact = resources.displayMetrics.widthPixels / resources.displayMetrics.density < 430f
    private val controlBackground = NeoControlBackground()
    private val playPauseButton: TextView
    private val areaButton: TextView
    private val studioButton: TextView
    private val adaptiveLabel: TextView
    private val contentLayout = LinearLayout(context)
    private val collapsedButton: TextView
    private var collapsed = false
    private var areaAdjusting = false

    /** Called on any touch, including on the collapsed button. */
    var onInteraction: (() -> Unit)? = null

    /** Set while a collapse/expand animator is running; refresh() healing defers to it. */
    fun setAnimating(value: Boolean) {
        animating = value
    }

    /** Called when the user taps the collapsed square. */
    var onExpandRequested: (() -> Unit)? = null

    private var animating = false
    private var lastRawX = 0f
    private var lastRawY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var squareDownX = 0f
    private var squareDownY = 0f
    private var squareLastX = 0f
    private var squareLastY = 0f
    private var squareDragging = false

    /** Called with the fractional delta when the user drags the collapsed square. */
    var onSquareDrag: ((dx: Float, dy: Float) -> Unit)? = null

    /** Called when a touch gesture on the collapsed square ends. */
    var onSquareTouchEnd: ((wasDrag: Boolean) -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            onInteraction?.invoke()
        }
        return super.dispatchTouchEvent(event)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(3), dp(3), dp(3), dp(3))
        background = controlBackground
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        contentLayout.orientation = HORIZONTAL
        contentLayout.gravity = Gravity.CENTER_VERTICAL
        addView(contentLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        contentLayout.addView(dragHandle())
        playPauseButton =
            actionButton(
                expandedLabel = "Play",
                compactLabel = "▶",
                description = "Play or pause",
                accent = ACCENT_TEAL,
            ) {
                onPlayPause()
                refresh()
            }
        contentLayout.addView(playPauseButton)
        contentLayout.addView(
            actionButton(
                expandedLabel = "Restart",
                compactLabel = "↺",
                description = "Restart from beginning",
            ) {
                onRestart()
                refresh()
            },
        )
        contentLayout.addView(
            actionButton(
                expandedLabel = "−",
                compactLabel = "−",
                description = "Decrease speed",
            ) { onSlower() },
        )
        contentLayout.addView(
            actionButton(
                expandedLabel = "+",
                compactLabel = "+",
                description = "Increase speed",
            ) { onFaster() },
        )
        areaButton =
            actionButton(
                expandedLabel = "Area",
                compactLabel = "Area",
                description = "Adjust the overlay area",
                accent = ACCENT_YELLOW,
                compactTextSize = 10f,
            ) { onToggleArea() }
        contentLayout.addView(areaButton)
        studioButton =
            actionButton(
                expandedLabel = "Studio",
                compactLabel = "S",
                description = "Return to Teleprompter",
                compactTextSize = 14f,
            ) { onOpenStudio() }
        contentLayout.addView(studioButton)
        adaptiveLabel =
            TextView(context).apply {
                text = "AUTO"
                textSize = 9f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                setPadding(dp(3), 0, dp(3), 0)
                contentDescription = "Adaptive contrast active"
                visibility = if (PrompterSession.projectionActive && !compact) VISIBLE else GONE
            }
        contentLayout.addView(adaptiveLabel)
        contentLayout.addView(
            actionButton(
                expandedLabel = "Stop",
                compactLabel = "■",
                description = "Stop teleprompter",
                accent = ACCENT_MAGENTA,
            ) { onStop() },
        )
        collapsedButton =
            TextView(context).apply {
                text = "TP"
                typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                textSize = 17f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                includeFontPadding = false
                minWidth = dp(54)
                minHeight = dp(54)
                contentDescription = "Expand teleprompter controls"
                setPadding(dp(2), 0, dp(2), 0)
                background = NeoButtonBackground(ACCENT_YELLOW)
                setOnClickListener { onExpandRequested?.invoke() }
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            squareDownX = event.rawX
                            squareDownY = event.rawY
                            squareLastX = event.rawX
                            squareLastY = event.rawY
                            squareDragging = false
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val totalX = event.rawX - squareDownX
                            val totalY = event.rawY - squareDownY
                            if (!squareDragging && (abs(totalX) > touchSlop || abs(totalY) > touchSlop)) {
                                squareDragging = true
                            }
                            if (squareDragging) {
                                onSquareDrag?.invoke(event.rawX - squareLastX, event.rawY - squareLastY)
                            }
                            squareLastX = event.rawX
                            squareLastY = event.rawY
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            val wasDrag = squareDragging
                            squareDragging = false
                            onSquareTouchEnd?.invoke(wasDrag)
                            if (!wasDrag) {
                                performClick()
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            squareDragging = false
                            onSquareTouchEnd?.invoke(true)
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }
            }
        addView(collapsedButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        collapsedButton.visibility = GONE
        refresh()
    }

    fun refresh() {
        // Self-heal visuals after any interrupted collapse/expand: restore the
        // alpha/scale of the side that is currently committed.
        if (!animating) {
        if (collapsed) {
            collapsedButton.alpha = 1f
            collapsedButton.scaleX = 1f
            collapsedButton.scaleY = 1f
            controlBackground.setAlphaFraction(0f)
        } else {
            contentLayout.alpha = 1f
            contentLayout.scaleX = 1f
            contentLayout.scaleY = 1f
            controlBackground.setAlphaFraction(1f)
        }
        }
        playPauseButton.text =
            if (compact) {
                if (PrompterSession.isPlaying) "⏸" else "▶"
            } else {
                if (PrompterSession.isPlaying) "Pause" else "Play"
            }
        adaptiveLabel.visibility =
            if (PrompterSession.projectionActive && !compact) VISIBLE else GONE
        areaButton.text = if (areaAdjusting) "Done" else "Area"
        areaButton.contentDescription =
            if (areaAdjusting) "Finish adjusting the overlay area" else "Adjust the overlay area"
        areaButton.background = buttonBackground(if (areaAdjusting) ACCENT_TEAL else ACCENT_YELLOW)
    }

    fun setAreaAdjusting(adjusting: Boolean) {
        areaAdjusting = adjusting
        refresh()
    }

    /**
     * Swaps the full control row for the small "TP" square and back. Callers
     * are responsible for re-requesting the window layout afterwards so the
     * overlay window resizes to the new content.
     */
    fun setCollapsed(value: Boolean) {
        if (collapsed == value) {
            setCollapseProgress(if (value) 1f else 0f)
            return
        }
        collapsed = value
        contentLayout.visibility = if (value) GONE else VISIBLE
        collapsedButton.visibility = if (value) VISIBLE else GONE
        setCollapseProgress(if (value) 1f else 0f)
    }

    fun isCollapsed(): Boolean = collapsed

    /** Shows the collapsed square (invisible) so it can fade in during collapse. */
    fun prepareCollapseVisuals() {
        contentLayout.visibility = VISIBLE
        collapsedButton.visibility = VISIBLE
        collapsedButton.alpha = 0f
        collapsedButton.scaleX = 0.6f
        collapsedButton.scaleY = 0.6f
        setCollapseProgress(0f)
    }

    /** Shows the full row (invisible) so it can fade in during expand. */
    fun prepareExpandVisuals() {
        contentLayout.visibility = VISIBLE
        collapsedButton.visibility = VISIBLE
        contentLayout.alpha = 0f
        setCollapseProgress(1f)
    }

    /**
     * Drives the crossfade/slide between the full control row and the small
     * "TP" square. [fraction] = 0 means fully expanded, 1 means fully collapsed.
     */
    fun setCollapseProgress(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        contentLayout.alpha = 1f - clamped
        contentLayout.scaleX = 1f - 0.35f * clamped
        contentLayout.scaleY = 1f - 0.35f * clamped
        collapsedButton.alpha = clamped
        collapsedButton.scaleX = 0.6f + 0.4f * clamped
        collapsedButton.scaleY = 0.6f + 0.4f * clamped
        controlBackground.setAlphaFraction(1f - clamped)
    }

    private fun dragHandle(): TextView =
        TextView(context).apply {
            text = "⋮⋮"
            textSize = if (compact) 14f else 15f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            minWidth = dp(if (compact) 40 else 44)
            minHeight = dp(44)
            contentDescription = "Drag teleprompter controls"
            setPadding(dp(2), 0, dp(2), 0)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastRawX
                        val dy = event.rawY - lastRawY
                        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                            onDrag(dx, dy)
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }

    private fun actionButton(
        expandedLabel: String,
        compactLabel: String,
        description: String,
        accent: Int = Color.WHITE,
        compactTextSize: Float = 17f,
        onClick: () -> Unit,
    ): TextView =
        TextView(context).apply {
            text = if (compact) compactLabel else expandedLabel
            textSize = if (compact) compactTextSize else 13f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isSingleLine = true
            minHeight = dp(44)
            minWidth = dp(if (compact) 40 else 58)
            setPadding(dp(if (compact) 3 else 10), 0, dp(if (compact) 3 else 10), 0)
            contentDescription = description
            isClickable = true
            isFocusable = true
            background = buttonBackground(accent)
            setOnClickListener { onClick() }
        }

    private fun buttonBackground(accent: Int): Drawable = NeoButtonBackground(accent)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class NeoControlBackground : Drawable() {
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
        private val stroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat()
            }
        private val inset = dp(4).toFloat()

        override fun draw(canvas: Canvas) {
            val shadowOffset = dp(5).toFloat()
            canvas.drawRect(
                bounds.left + shadowOffset,
                bounds.top + shadowOffset,
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                shadow,
            )
            val right = bounds.right - inset
            val bottom = bounds.bottom - inset
            canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), right, bottom, fill)
            canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), right, bottom, stroke)
        }

        override fun setAlpha(alpha: Int) {
            setAlphaFraction(alpha / 255f)
        }

        /** Fades the panel (fill, stroke and offset shadow) together with the controls. */
        fun setAlphaFraction(alpha: Float) {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
            fill.alpha = a
            stroke.alpha = a
            shadow.alpha = a
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            fill.colorFilter = colorFilter
            stroke.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
    }

    private inner class NeoButtonBackground(
        private val fillColor: Int,
    ) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
        private val stroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
            }

        override fun draw(canvas: Canvas) {
            val inset = stroke.strokeWidth / 2f
            canvas.drawRect(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset,
                fill,
            )
            canvas.drawRect(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset,
                stroke,
            )
        }

        override fun setAlpha(alpha: Int) {
            fill.alpha = alpha
            stroke.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            fill.colorFilter = colorFilter
            stroke.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
    }

    companion object {
        private const val PAPER = 0xFFF5F5DC.toInt()
        private const val ACCENT_TEAL = 0xFF00C2C8.toInt()
        private const val ACCENT_YELLOW = 0xFFFFD700.toInt()
        private const val ACCENT_MAGENTA = 0xFFFF00FF.toInt()
    }
}
