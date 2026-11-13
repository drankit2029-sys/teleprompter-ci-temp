package com.accessoryTools.teleprompter.overlay

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
    private var controlBar: OverlayControlBar? = null
    private var controlParams: WindowManager.LayoutParams? = null

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
                controlBar?.refresh()
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
        val display = getSystemService(DisplayManager::class.java).getDisplay(requestedDisplayId)
        val displayContext =
            if (display != null && requestedDisplayId != Display.DEFAULT_DISPLAY) {
                runCatching { createDisplayContext(display) }.getOrNull()
            } else {
                null
            }
        overlayContext = displayContext ?: this
        targetDisplayId =
            if (displayContext != null) requestedDisplayId else Display.DEFAULT_DISPLAY
        windowManager = overlayContext.getSystemService(WindowManager::class.java)
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
        startAsForeground(hasProjection = projectionAvailable)
        showOverlay()
        if (adaptive && resultData != null && targetDisplayId == Display.DEFAULT_DISPLAY) {
            startProjection(resultCode, resultData)
        } else {
            PrompterSession.updateProjectionActive(false)
        }
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    private fun startAsForeground(hasProjection: Boolean) {
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
    }

    private fun showOverlay() {
        PrompterSession.updateOverlayRunning(true)
        if (scriptView == null) {
            val view = PrompterOverlayView(overlayContext)
            windowManager.addView(view, scriptLayoutParams())
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
                    onSlower = { PrompterSession.nudgeSpeed(-SPEED_STEP) },
                    onFaster = { PrompterSession.nudgeSpeed(SPEED_STEP) },
                    onStop = { stopSelf() },
                    onDrag = ::dragControls,
                )
            val params = controlLayoutParams()
            controlParams = params
            windowManager.addView(bar, params)
            controlBar = bar
        }
        controlBar?.refresh()
    }

    private fun scriptLayoutParams(): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

    private fun controlLayoutParams(): WindowManager.LayoutParams =
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
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(64)
            }

    private fun dragControls(
        dx: Float,
        dy: Float,
    ) {
        val bar = controlBar ?: return
        val params = controlParams ?: return
        params.x += dx.roundToInt()
        params.y -= dy.roundToInt()
        val barWidth = bar.width.coerceAtLeast(1)
        val barHeight = bar.height.coerceAtLeast(1)
        val bounds = currentScreenBounds()
        val maxX = ((bounds.first - barWidth) / 2f).roundToInt().coerceAtLeast(0)
        val maxY = (bounds.second - barHeight - dp(8)).coerceAtLeast(0)
        params.x = params.x.coerceIn(-maxX, maxX)
        params.y = params.y.coerceIn(0, maxY)
        windowManager.updateViewLayout(bar, params)
    }

    private fun currentScreenBounds(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = overlayContext.resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
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
            ImageReader.newInstance(
                sampleWidth,
                sampleHeight,
                PixelFormat.RGBA_8888,
                2,
            )
        reader.setOnImageAvailableListener({ availableReader ->
            sampleAvailableImage(availableReader)
        }, handler)
        imageReader = reader
        virtualDisplay =
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
        PrompterSession.updateProjectionActive(true)
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

    private fun fallbackToManualContrast() {
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
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        sampleThread?.quitSafely()
        sampleThread = null
        sampleHandler = null
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
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_teleprompter)
            .setContentTitle("Teleprompter overlay active")
            .setContentText("Transparent script is floating over other apps")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_play, "Play/pause", togglePending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        releaseProjection()
        controlBar?.let { bar ->
            runCatching { windowManager.removeView(bar) }
        }
        scriptView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        controlBar = null
        scriptView = null
        PrompterSession.setPlayback(false)
        PrompterSession.updateOverlayRunning(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * overlayContext.resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_START = "com.accessoryTools.teleprompter.action.START_OVERLAY"
        const val ACTION_STOP = "com.accessoryTools.teleprompter.action.STOP_OVERLAY"
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
        private const val SPEED_STEP = 10f
        private const val SAMPLE_LONG_EDGE = 256
        private const val SAMPLE_PIXEL_STEP = 4
        private const val SAMPLE_INTERVAL_MS = 700L
        private const val LUMINANCE_BINS = 32
        private const val CONTROL_REFRESH_MS = 1_000L

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
