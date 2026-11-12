package com.accessoryTools.teleprompter.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
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
    private val onStop: () -> Unit,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
) : LinearLayout(context) {
    private val playPauseButton: TextView
    private val adaptiveLabel: TextView

    private var lastRawX = 0f
    private var lastRawY = 0f

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(6), dp(8), dp(6))
        background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(0xF21A1C22.toInt())
                setStroke(dp(1), 0x66FFFFFF)
            }
        elevation = dp(10).toFloat()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        addView(dragHandle())
        playPauseButton =
            actionButton("Play", "Play or pause") {
                onPlayPause()
                refresh()
            }
        addView(playPauseButton)
        addView(
            actionButton("Restart", "Restart from beginning") {
                onRestart()
                refresh()
            },
        )
        addView(
            actionButton("−", "Decrease speed") {
                onSlower()
                refresh()
            },
        )
        addView(
            actionButton("+", "Increase speed") {
                onFaster()
                refresh()
            },
        )
        adaptiveLabel =
            TextView(context).apply {
                text = "Auto"
                textSize = 10f
                setTextColor(0xFF8BD3C7.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(4), 0)
                visibility = if (PrompterSession.projectionActive) VISIBLE else GONE
            }
        addView(adaptiveLabel)
        addView(actionButton("Stop", "Stop teleprompter") { onStop() })
        refresh()
    }

    fun refresh() {
        playPauseButton.text = if (PrompterSession.isPlaying) "Pause" else "Play"
        adaptiveLabel.visibility = if (PrompterSession.projectionActive) VISIBLE else GONE
    }

    private fun dragHandle(): TextView =
        TextView(context).apply {
            text = "⋮⋮"
            textSize = 16f
            setTextColor(0xFFB8B5AD.toInt())
            gravity = Gravity.CENTER
            minWidth = dp(36)
            minHeight = dp(44)
            contentDescription = "Drag teleprompter controls"
            setPadding(dp(4), 0, dp(6), 0)
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
        label: String,
        description: String,
        onClick: () -> Unit,
    ): TextView =
        TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minHeight = dp(44)
            minWidth = if (label.length <= 1) dp(40) else dp(58)
            setPadding(dp(8), 0, dp(8), 0)
            contentDescription = description
            isClickable = true
            isFocusable = true
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(22).toFloat()
                    setColor(0x26FFFFFF)
                }
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
