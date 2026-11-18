package com.accessoryTools.teleprompter.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.accessoryTools.teleprompter.MainActivity
import com.accessoryTools.teleprompter.R
import com.accessoryTools.teleprompter.prompter.PrompterSession
import kotlin.math.max
import kotlin.math.roundToInt

class PrompterOverlayService : Service() {
    private lateinit var overlayContext: Context
    private lateinit var windowManager: WindowManager
    private lateinit var mainHandler: Handler
    private var targetDisplayId = Display.DEFAULT_DISPLAY
    private var scriptView: PrompterOverlayView? = null
    private var scriptParams: WindowManager.LayoutParams? = null
    private var controlBar: OverlayControlBar? = null
    private var controlHost: android.widget.FrameLayout? = null
    private var barX = 0f
    private var barY = 0f
    private var controlParams: WindowManager.LayoutParams? = null
    private var areaAdjusting = false
    private var wasPlayingBeforeAdjust = false
    private var controlsCollapsed = false
    private var preCollapseX = 0f
    private var collapseEdgeRight = true
    private var collapseAnimator: ValueAnimator? = null
    private var snapAnimator: ValueAnimator? = null
    private var lastControlInteractionAt = 0L

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var sampleThread: HandlerThread? = null
    private var sampleHandler: Handler? = null
    private var lastSampleAt = 0L
    private var latestSampleHandler: Handler? = null

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                releaseProjection()
                PrompterSession.updateProjectionActive(false)
                mainHandler.post {
                    scriptView?.invalidate()
                    controlBar?.refresh()
                }
            }
        }

    private val refreshRunnable =
        object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                if (!controlsCollapsed && !areaAdjusting &&
                    now - lastControlInteractionAt >= CONTROL_COLLAPSE_DELAY_MS
                ) {
                    collapseControls()
                }
                if (targetDisplayId != Display.DEFAULT_DISPLAY &&
                    getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId) == null
                ) {
                    // The display this overlay was attached to went away (e.g. scrcpy
                    // virtual display closed); shut down instead of idling forever.
                    stopSelf()
                    return
                }
                clampControlBarToScreen()
                controlBar?.refresh()
                scriptView?.invalidate()
                mainHandler.postDelayed(this, CONTROL_REFRESH_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        overlayContext = this
        windowManager = overlayContext.getSystemService(WindowManager::class.java)
        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP_CAPTURE -> {
                releaseProjection()
                demoteForegroundWithoutProjection()
                scriptView?.invalidate()
                controlBar?.refresh()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_PLAY -> {
                PrompterSession.togglePlay()
                scriptView?.kick()
                controlBar?.refresh()
                return START_NOT_STICKY
            }

            else -> {
                startOverlay(intent)
            }
        }
        return START_NOT_STICKY
    }

    /** True when an overlay window pair (script + control bar) exists. */
    private fun hasOverlayViews(): Boolean = scriptView != null || controlHost != null

    /** Removes the overlay windows and resets in-memory view state (used when rebuilding on another display). */
    private fun teardownOverlayViews() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.removeCallbacks(squareSnapRunnable)
        collapseAnimator?.cancel()
        snapAnimator?.cancel()
        collapseAnimator = null
        snapAnimator = null
        controlsCollapsed = false
        if (areaAdjusting) {
            PrompterSession.persistOverlayArea()
            PrompterSession.updateOverlayAreaAdjusting(false)
            areaAdjusting = false
        }
        wasPlayingBeforeAdjust = false
        controlHost?.let { runCatching { windowManager.removeView(it) } }
        scriptView?.let { runCatching { windowManager.removeView(it) } }
        controlBar = null
        controlHost = null
        scriptView = null
        controlParams = null
        PrompterSession.onOverlayAreaPreviewChanged = null
    }

    private fun startOverlay(intent: Intent?) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }
        val adaptive = intent?.getBooleanExtra(EXTRA_ADAPTIVE_CONTRAST, false) == true
        val resultData = intent?.let { projectionResultData(it) }
        val requestedDisplayId =
            intent?.getIntExtra(EXTRA_DISPLAY_ID, Display.DEFAULT_DISPLAY)
                ?: Display.DEFAULT_DISPLAY
        val previousDisplayId = targetDisplayId
        val viewsExist = hasOverlayViews()
        val display = getSystemService(DisplayManager::class.java).getDisplay(requestedDisplayId)
        val displayContext =
            if (display != null && requestedDisplayId != Display.DEFAULT_DISPLAY) {
                runCatching { createDisplayContext(display) }.getOrNull()
            } else {
                null
            }
        if (requestedDisplayId != Display.DEFAULT_DISPLAY && displayContext == null) {
            // Asked for an overlay on a specific display that's gone/unusable — fail loud.
            Toast
                .makeText(
                    this,
                    "The requested display is unavailable",
                    Toast.LENGTH_LONG,
                ).show()
            stopSelf()
            return
        }
        overlayContext = displayContext ?: this
        targetDisplayId =
            if (displayContext != null) requestedDisplayId else Display.DEFAULT_DISPLAY
        windowManager = overlayContext.getSystemService(WindowManager::class.java)
        if (viewsExist && previousDisplayId != targetDisplayId) {
            // Rebuild the windows on the requested display instead of leaking the old ones.
            teardownOverlayViews()
        }
        val projectionAvailable =
            resultData != null && targetDisplayId == Display.DEFAULT_DISPLAY
        if (adaptive && !projectionAvailable) {
            Toast
                .makeText(
                    overlayContext,
                    "Adaptive contrast uses the main display; using manual colors",
                    Toast.LENGTH_LONG,
                ).show()
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        if (!startAsForeground(hasProjection = projectionAvailable)) {
            return
        }
        if (!showOverlay()) {
            return
        }
        if (adaptive && resultData != null && targetDisplayId == Display.DEFAULT_DISPLAY) {
            startProjection(resultCode, resultData)
        } else {
            // A manual or other-display start must not leave a stale projection sampling.
            releaseProjection()
            PrompterSession.updateProjectionActive(false)
        }
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    private fun startAsForeground(hasProjection: Boolean): Boolean {
        return runCatching {
            ensureNotificationChannel()
            val type =
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasProjection -> {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    }

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    }

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasProjection -> {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    }

                    else -> {
                        0
                    }
                }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                type,
            )
            true
        }.getOrElse {
            Toast.makeText(this, "Could not start the overlay", Toast.LENGTH_LONG).show()
            stopSelf()
            false
        }
    }

    private fun showOverlay(): Boolean {
        PrompterSession.onOverlayAreaPreviewChanged = { area ->
            if (area == PrompterSession.overlayArea) {
                mainHandler.post { scriptView?.refreshOverlayArea() }
            }
        }
        try {
            if (scriptView == null) {
                val view = PrompterOverlayView(overlayContext)
                val params = scriptLayoutParams()
                scriptParams = params
                windowManager.addView(view, params)
                scriptView = view
            }
            if (controlBar == null) {
            val bar =
                OverlayControlBar(
                    context = overlayContext,
                    onPlayPause = {
                        PrompterSession.togglePlay()
                        scriptView?.kick()
                    },
                    onRestart = {
                        PrompterSession.restart()
                        scriptView?.kick()
                    },
                    onSlower = { changeOverlaySpeed(-SPEED_STEP) },
                    onFaster = { changeOverlaySpeed(SPEED_STEP) },
                    onToggleArea = ::toggleAreaAdjusting,
                    onOpenStudio = ::openTeleprompterFromOverlay,
                    onStop = { stopSelf() },
                    onDrag = ::dragControls,
                ).apply {
                    onInteraction = { noteControlInteraction() }
                    onExpandRequested = { expandControlsIfCollapsed() }
                    onSquareDrag = { dx, dy -> dragCollapsedSquare(dx, dy) }
                    onSquareTouchEnd = { wasDrag -> onSquareTouchEnd(wasDrag) }
                }
            // The control window wraps ONLY the bar itself. A full-screen canvas
            // window is supposed to pass unconsumed touches through, but on some
            // devices/ROMs (seen on Samsung One UI) it swallows every tap below it.
            // Sizing the window to the bar makes pass-through structural.
            val host =
                android.widget.FrameLayout(overlayContext).apply {
                    clipChildren = false
                }
            host.addView(
                bar,
                android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP or android.view.Gravity.START,
                ),
            )
            barX = 0f
            barY = -dp(80).toFloat()
            val params = controlWindowParams(0, -dp(80))
            lastControlInteractionAt = SystemClock.elapsedRealtime()
            controlsCollapsed = false
            preCollapseX = 0f
            controlHost = host
            controlParams = params
            windowManager.addView(host, params)
            controlBar = bar
            bar.post {
                val bounds = currentScreenBounds()
                barX = ((bounds.first - bar.width) / 2).coerceAtLeast(0).toFloat()
                barY = (bounds.second - bar.height - dp(64) - navigationBarHeightPx()).coerceAtLeast(0).toFloat()
                applyBarTranslation()
            }
            }
        } catch (error: RuntimeException) {
            // Roll back whatever this start added so the process doesn't die or
            // leave invisible overlay windows attached.
            teardownOverlayViews()
            PrompterSession.updateOverlayRunning(false)
            Toast.makeText(overlayContext, "Could not start the overlay", Toast.LENGTH_LONG).show()
            stopSelf()
            return false
        }
        PrompterSession.updateOverlayRunning(true)
        controlBar?.refresh()
        return true
    }

    private fun noteControlInteraction() {
        lastControlInteractionAt = SystemClock.elapsedRealtime()
    }

    private fun applyBarTranslation() {
        val host = controlHost ?: return
        val params = controlParams ?: return
        val nextX = barX.toInt()
        val nextY = barY.toInt()
        if (params.x == nextX && params.y == nextY) {
            return
        }
        params.x = nextX
        params.y = nextY
        runCatching { windowManager.updateViewLayout(host, params) }
    }

    /** Brings the Teleprompter activity back to the front, on the overlay's own display. */
    private fun openTeleprompterFromOverlay() {
        val intent =
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && targetDisplayId != Display.DEFAULT_DISPLAY) {
                val options =
                    ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId).toBundle()
                startActivity(intent, options)
            } else {
                startActivity(intent)
            }
        }.getOrElse {
            // The requested display may have gone away; open on the primary display instead.
            runCatching { startActivity(intent) }
        }
    }

    /** Height of the system navigation bar, so the bar never rests beneath it. */
    private fun navigationBarHeightPx(): Int =
        try {
            val res = overlayContext.resources
            val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id != 0) res.getDimensionPixelSize(id) else 0
        } catch (_: Exception) {
            0
        }

    fun clampControlBarToScreen() {
        val bar = controlBar ?: return
        if (bar.width <= 0 || bar.height <= 0) {
            return
        }
        val bounds = currentScreenBounds()
        val newX = barX.coerceIn(0f, (bounds.first - bar.width).coerceAtLeast(0).toFloat())
        val newY = barY.coerceIn(0f, (bounds.second - bar.height - navigationBarHeightPx()).coerceAtLeast(0).toFloat())
        if (newX != barX || newY != barY) {
            barX = newX
            barY = newY
            applyBarTranslation()
        }
    }

    private fun collapseControls() {
        val bar = controlBar ?: return
        val bounds = currentScreenBounds()
        if (controlsCollapsed) {
            return
        }
        if (bar.width <= 0 || bar.height <= 0) {
            bar.post { collapseControls() }
            return
        }
        val startW = bar.width
        val startX = barX
        val startY = barY
        val square = dp(60)
        collapseEdgeRight = startX + startW / 2 >= bounds.first / 2
        val targetX =
            if (collapseEdgeRight) {
                (bounds.first - square - dp(8)).coerceAtLeast(0).toFloat()
            } else {
                dp(8).toFloat()
            }
        preCollapseX = startX
        controlsCollapsed = true
        collapseAnimator?.cancel()
        bar.prepareCollapseVisuals()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        collapseAnimator = animator
        animator.duration = 220L
        animator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        bar.setAnimating(true)
        animator.addUpdateListener { frame ->
            val progress = frame.animatedFraction
            barX = lerpF(startX, targetX, progress)
            barY = startY.coerceIn(0f, (bounds.second - square - navigationBarHeightPx()).coerceAtLeast(0).toFloat())
            applyBarTranslation()
            bar.setCollapseProgress(progress)
        }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    collapseAnimator = null
                    bar.setAnimating(false)
                    bar.setCollapsed(true)
                    barX = targetX
                    barY = startY.coerceIn(0f, (bounds.second - square - navigationBarHeightPx()).coerceAtLeast(0).toFloat())
                    applyBarTranslation()
                    // Width shrank to the square — give the window the new size.
                    relayoutControlWindow()
                }

                override fun onAnimationCancel(animation: Animator) {
                    collapseAnimator = null
                    bar.setAnimating(false)
                    bar.setCollapsed(true)
                    barX = targetX
                    applyBarTranslation()
                    relayoutControlWindow()
                }
            },
        )
        animator.start()
    }

    private fun expandControlsIfCollapsed() {
        val bar = controlBar ?: return
        val bounds = currentScreenBounds()
        if (!controlsCollapsed) {
            return
        }
        if (bar.width <= 0 || bar.height <= 0) {
            bar.post { expandControlsIfCollapsed() }
            return
        }
        val square = dp(60)
        val startX = barX
        val startY = barY
        controlsCollapsed = false
        collapseAnimator?.cancel()
        mainHandler.removeCallbacks(squareSnapRunnable)
        snapAnimator?.cancel()
        bar.prepareExpandVisuals()
        // Re-measure now (row visible again) so the window grows with the bar.
        relayoutControlWindow()
        // Measure the full row so the x target stays on-screen; the bar keeps
        // its own natural size — the window no longer resizes per frame.
        val naturalWidth =
            run {
                val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                bar.measure(widthSpec, heightSpec)
                bar.measuredWidth.coerceAtLeast(square)
            }
        val targetX = preCollapseX.coerceIn(0f, (bounds.first - naturalWidth).coerceAtLeast(0).toFloat())
        val animator = ValueAnimator.ofFloat(0f, 1f)
        collapseAnimator = animator
        animator.duration = 220L
        animator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        bar.setAnimating(true)
        animator.addUpdateListener { frame ->
            val progress = frame.animatedFraction
            barX = lerpF(startX, targetX, progress)
            applyBarTranslation()
            bar.setCollapseProgress(1f - progress)
        }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    collapseAnimator = null
                    bar.setAnimating(false)
                    bar.setCollapsed(false)
                    barX = targetX
                    barY = startY.coerceIn(0f, (bounds.second - dp(64) - navigationBarHeightPx()).coerceAtLeast(0).toFloat())
                    applyBarTranslation()
                    relayoutControlWindow()
                    controlBar?.refresh()
                }

                override fun onAnimationCancel(animation: Animator) {
                    collapseAnimator = null
                    bar.setAnimating(false)
                    bar.setCollapsed(false)
                    barX = preCollapseX.coerceIn(0f, (bounds.first - naturalWidth).coerceAtLeast(0).toFloat())
                    barY = startY.coerceIn(0f, (bounds.second - dp(64) - navigationBarHeightPx()).coerceAtLeast(0).toFloat())
                    applyBarTranslation()
                    relayoutControlWindow()
                    controlBar?.refresh()
                }
            },
        )
        animator.start()
        lastControlInteractionAt = SystemClock.elapsedRealtime()
    }

    private fun lerpF(
        from: Float,
        to: Float,
        fraction: Float,
    ): Float = from + (to - from) * fraction

    private fun dragCollapsedSquare(
        dx: Float,
        dy: Float,
    ) {
        val bar = controlBar ?: return
        val bounds = currentScreenBounds()
        val square = bar.width.coerceAtLeast(dp(60))
        mainHandler.removeCallbacks(squareSnapRunnable)
        snapAnimator?.cancel()
        barX = (barX + dx).coerceIn(0f, (bounds.first - square).coerceAtLeast(0).toFloat())
        barY = (barY + dy).coerceIn(0f, (bounds.second - square - navigationBarHeightPx()).coerceAtLeast(0).toFloat())
        collapseEdgeRight = barX + square / 2 >= bounds.first / 2
        noteControlInteraction()
        applyBarTranslation()
    }

    /** Called when a touch gesture on the collapsed square ends. */
    private fun onSquareTouchEnd(wasDrag: Boolean) {
        mainHandler.removeCallbacks(squareSnapRunnable)
        if (!controlsCollapsed) {
            return
        }
        if (wasDrag) {
            mainHandler.postDelayed(squareSnapRunnable, SQUARE_SNAP_DELAY_MS)
        }
    }

    private val squareSnapRunnable =
        Runnable { snapSquareToEdge() }

    /** Snaps the collapsed square to the nearest vertical edge with a gentle animation. */
    private fun snapSquareToEdge() {
        val bar = controlBar ?: return
        val bounds = currentScreenBounds()
        val square = bar.width.coerceAtLeast(dp(60))
        if (!controlsCollapsed) {
            return
        }
        val targetX =
            if (barX + square / 2 >= bounds.first / 2) {
                (bounds.first - square - dp(8)).coerceAtLeast(0).toFloat()
            } else {
                dp(8).toFloat()
            }
        val startX = barX
        collapseEdgeRight = targetX + square / 2 >= bounds.first / 2
        snapAnimator?.cancel()
        if (startX == targetX) {
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f)
        snapAnimator = animator
        animator.duration = 180L
        animator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        animator.addUpdateListener { frame ->
            barX = lerpF(startX, targetX, frame.animatedFraction)
            applyBarTranslation()
        }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    barX = targetX
                    snapAnimator = null
                    applyBarTranslation()
                }

                override fun onAnimationCancel(animation: Animator) {
                    snapAnimator = null
                }
            },
        )
        animator.start()
    }

    private fun toggleAreaAdjusting() {
        if (scriptView == null || scriptParams == null) {
            return
        }
        areaAdjusting = !areaAdjusting
        PrompterSession.updateOverlayAreaAdjusting(areaAdjusting)
        if (areaAdjusting) {
            wasPlayingBeforeAdjust = PrompterSession.isPlaying
            if (wasPlayingBeforeAdjust) {
                PrompterSession.setPlayback(false)
            }
        } else {
            PrompterSession.persistOverlayArea()
            if (wasPlayingBeforeAdjust) {
                PrompterSession.setPlayback(true)
                scriptView?.kick()
            }
            wasPlayingBeforeAdjust = false
        }
        scriptView?.setAdjusting(areaAdjusting)
        applyScriptTouchMode()
        controlBar?.setAreaAdjusting(areaAdjusting)
        Toast
            .makeText(
                overlayContext,
                if (areaAdjusting) {
                    "Drag the area to move it; drag a corner to resize"
                } else {
                    "Overlay area saved"
                },
                Toast.LENGTH_SHORT,
            ).show()
    }

    private fun applyScriptTouchMode() {
        val view = scriptView ?: return
        val params = scriptParams ?: return
        params.flags =
            if (areaAdjusting) {
                SCRIPT_FLAGS
            } else {
                SCRIPT_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun changeOverlaySpeed(delta: Float) {
        PrompterSession.nudgeSpeed(delta)
        scriptView?.kick()
        scriptView?.showStatus("${PrompterSession.speedWpm.roundToInt()} WPM")
    }

    private fun scriptLayoutParams(): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                SCRIPT_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

    /** Window sized to the bar only — every tap outside it reaches the app below. */
    private fun controlWindowParams(
        x: Int,
        y: Int,
    ): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

    /** Forces a re-measure after the bar's measured size changed (collapse/expand/fades). */
    private fun relayoutControlWindow() {
        val host = controlHost ?: return
        val params = controlParams ?: return
        runCatching { windowManager.updateViewLayout(host, params) }
    }

    private fun dragControls(
        dx: Float,
        dy: Float,
    ) {
        val bar = controlBar ?: return
        val bounds = currentScreenBounds()
        barX = (barX + dx).coerceIn(0f, (bounds.first - bar.width).coerceAtLeast(0).toFloat())
        barY = (barY + dy).coerceIn(0f, (bounds.second - bar.height - navigationBarHeightPx()).coerceAtLeast(0).toFloat())
        collapseEdgeRight = barX + bar.width / 2 >= bounds.first / 2
        applyBarTranslation()
        noteControlInteraction()
    }

    private fun currentScreenBounds(): Pair<Int, Int> {
        // Service contexts can report window-sized metrics on some API 30
        // devices. The display's real metrics are authoritative for both the
        // main display and display contexts created for secondary displays.
        val display = getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId)
        if (display != null) {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                return metrics.widthPixels to metrics.heightPixels
            }
        }
        val metrics = overlayContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun startProjection(
        resultCode: Int,
        resultData: Intent,
    ) {
        releaseProjection()
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection =
            try {
                manager.getMediaProjection(resultCode, resultData)
            } catch (error: RuntimeException) {
                fallbackToManualContrast()
                return
            }
        if (projection == null) {
            fallbackToManualContrast()
            return
        }
        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)
        val (screenWidth, screenHeight) = currentScreenBounds()
        val scale = SAMPLE_LONG_EDGE.toFloat() / max(screenWidth, screenHeight).coerceAtLeast(1)
        val sampleWidth = (screenWidth * scale).roundToInt().coerceAtLeast(32)
        val sampleHeight = (screenHeight * scale).roundToInt().coerceAtLeast(32)
        val thread = HandlerThread("teleprompter-contrast").also { it.start() }
        val handler = Handler(thread.looper)
        sampleThread = thread
        sampleHandler = handler
        latestSampleHandler = mainHandler
        val reader =
            try {
                ImageReader.newInstance(
                    sampleWidth,
                    sampleHeight,
                    PixelFormat.RGBA_8888,
                    2,
                )
            } catch (error: RuntimeException) {
                fallbackToManualContrast()
                return
            }
        reader.setOnImageAvailableListener({ availableReader ->
            runCatching { sampleAvailableImage(availableReader) }
        }, handler)
        imageReader = reader
        virtualDisplay =
            try {
                projection.createVirtualDisplay(
                    "teleprompter-contrast",
                    sampleWidth,
                    sampleHeight,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    handler,
                )
            } catch (error: RuntimeException) {
                fallbackToManualContrast()
                return
            }
        if (virtualDisplay == null) {
            fallbackToManualContrast()
            return
        }
        PrompterSession.updateProjectionActive(true)
        // Refresh the notification so it shows the capture-specific title and
        // the "Stop capture" action while projection is live.
        startAsForeground(hasProjection = true)
        controlBar?.refresh()
    }

    private fun sampleAvailableImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastSampleAt < SAMPLE_INTERVAL_MS) {
                return
            }
            lastSampleAt = now
            val luminance = medianLuminance(image)
            latestSampleHandler?.post {
                PrompterSession.updateAdaptiveContrast(luminance)
                scriptView?.invalidate()
            }
        } finally {
            image.close()
        }
    }

    private fun medianLuminance(image: Image): Float {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val histogram = IntArray(LUMINANCE_BINS)
        var count = 0
        var row = 0
        while (row < image.height) {
            val rowOffset = row * rowStride
            var column = 0
            while (column < image.width) {
                val index = rowOffset + column * pixelStride
                if (index + 2 < buffer.limit()) {
                    val red = buffer.get(index).toInt() and 0xFF
                    val green = buffer.get(index + 1).toInt() and 0xFF
                    val blue = buffer.get(index + 2).toInt() and 0xFF
                    val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
                    val bin =
                        (luminance * LUMINANCE_BINS)
                            .toInt()
                            .coerceIn(0, LUMINANCE_BINS - 1)
                    histogram[bin]++
                    count++
                }
                column += SAMPLE_PIXEL_STEP
            }
            row += SAMPLE_PIXEL_STEP
        }
        if (count == 0) {
            return 0.5f
        }
        val midpoint = count / 2
        var running = 0
        for (bin in histogram.indices) {
            running += histogram[bin]
            if (running >= midpoint) {
                return (bin + 0.5f) / LUMINANCE_BINS
            }
        }
        return 0.5f
    }

    /** Re-registers the foreground service WITHOUT the mediaProjection type after capture fails. */
    private fun demoteForegroundWithoutProjection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            ensureNotificationChannel()
            val type =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        }
    }

    private fun fallbackToManualContrast() {
        releaseProjection()
        demoteForegroundWithoutProjection()
        PrompterSession.updateProjectionActive(false)
        scriptView?.invalidate()
        controlBar?.refresh()
        mainHandler.post {
            Toast
                .makeText(
                    this,
                    "Adaptive contrast unavailable; using manual colors",
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    private fun releaseProjection() {
        // 1. Quiesce the sampler thread first so no frame callback races the close.
        sampleThread?.quitSafely()
        sampleThread?.join(100)
        sampleThread = null
        sampleHandler = null
        latestSampleHandler = null

        // 2. Stop the virtual display, then drop the listener/reader.
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        mediaProjection?.let { projection ->
            projection.unregisterCallback(projectionCallback)
            projection.stop()
        }
        mediaProjection = null
        PrompterSession.updateProjectionActive(false)
    }

    private fun projectionResultData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Floating teleprompter",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Controls for the transparent teleprompter overlay"
                    setShowBadge(false)
                }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending =
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val toggleIntent =
            Intent(this, PrompterOverlayService::class.java)
                .setAction(ACTION_TOGGLE_PLAY)
        val togglePending =
            PendingIntent.getService(
                this,
                REQUEST_TOGGLE,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            Intent(this, PrompterOverlayService::class.java)
                .setAction(ACTION_STOP)
        val stopPending =
            PendingIntent.getService(
                this,
                REQUEST_STOP,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val captureActive = PrompterSession.projectionActive
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_teleprompter)
                .setContentTitle(
                    if (captureActive) {
                        "Screen capture active — adaptive contrast"
                    } else {
                        "Teleprompter overlay active"
                    },
                )
                .setContentText(
                    if (captureActive) {
                        "Reading screen brightness on-device to keep the script readable"
                    } else {
                        "Transparent script is floating over other apps"
                    },
                )
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(android.R.drawable.ic_media_play, "Play/pause", togglePending)
        if (captureActive) {
            val stopCaptureIntent =
                Intent(this, PrompterOverlayService::class.java)
                    .setAction(ACTION_STOP_CAPTURE)
            val stopCapturePending =
                PendingIntent.getService(
                    this,
                    REQUEST_STOP_CAPTURE,
                    stopCaptureIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop capture",
                stopCapturePending,
            )
        }
        return builder
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.removeCallbacks(squareSnapRunnable)
        collapseAnimator?.cancel()
        snapAnimator?.cancel()
        collapseAnimator = null
        snapAnimator = null
        if (areaAdjusting) {
            PrompterSession.persistOverlayArea()
            PrompterSession.updateOverlayAreaAdjusting(false)
            areaAdjusting = false
        }
        releaseProjection()
        controlHost?.let { host ->
            runCatching { windowManager.removeView(host) }
        }
        scriptView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        controlBar = null
        controlHost = null
        scriptView = null
        controlParams = null
        PrompterSession.onOverlayAreaPreviewChanged = null
        PrompterSession.setPlayback(false)
        PrompterSession.updateOverlayRunning(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * overlayContext.resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_START = "com.accessoryTools.teleprompter.action.START_OVERLAY"
        const val ACTION_STOP = "com.accessoryTools.teleprompter.action.STOP_OVERLAY"
        const val ACTION_STOP_CAPTURE = "com.accessoryTools.teleprompter.action.STOP_CAPTURE"
        const val ACTION_TOGGLE_PLAY = "com.accessoryTools.teleprompter.action.OVERLAY_TOGGLE_PLAY"
        const val EXTRA_ADAPTIVE_CONTRAST = "adaptive_contrast"
        const val EXTRA_DISPLAY_ID = "overlay_display_id"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"

        private const val CHANNEL_ID = "teleprompter_overlay"
        private const val NOTIFICATION_ID = 421
        private const val REQUEST_OPEN = 10
        private const val REQUEST_TOGGLE = 11
        private const val REQUEST_STOP = 12
        private const val REQUEST_STOP_CAPTURE = 13
        private const val SPEED_STEP = 10f
        private const val CONTROL_COLLAPSE_DELAY_MS = 5_000L
        private const val SQUARE_SNAP_DELAY_MS = 1_600L
        private const val SAMPLE_LONG_EDGE = 256
        private const val SAMPLE_PIXEL_STEP = 4
        private const val SAMPLE_INTERVAL_MS = 700L
        private const val LUMINANCE_BINS = 32
        private const val CONTROL_REFRESH_MS = 1_000L
        private const val SCRIPT_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        fun start(
            context: Context,
            adaptiveContrast: Boolean,
            resultCode: Int? = null,
            resultData: Intent? = null,
            displayId: Int = Display.DEFAULT_DISPLAY,
        ) {
            val intent =
                Intent(context, PrompterOverlayService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_ADAPTIVE_CONTRAST, adaptiveContrast)
                    .putExtra(EXTRA_DISPLAY_ID, displayId)
            if (resultCode != null) {
                intent.putExtra(EXTRA_RESULT_CODE, resultCode)
            }
            if (resultData != null) {
                intent.putExtra(EXTRA_RESULT_DATA, resultData)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrompterOverlayService::class.java))
        }
    }
}
