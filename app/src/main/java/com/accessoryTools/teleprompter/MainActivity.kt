package com.accessoryTools.teleprompter

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.accessoryTools.teleprompter.overlay.PrompterOverlayService
import com.accessoryTools.teleprompter.platform.PrompterCommandReceiver
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.accessoryTools.teleprompter.ui.Phase0HomeScreen
import com.accessoryTools.teleprompter.ui.Phase0StageScreen
import com.accessoryTools.teleprompter.ui.TeleprompterTheme

class MainActivity : ComponentActivity() {
    private val pipModeState = mutableStateOf(false)
    private val startOnPrompter = false
    private var pipAspectIndex = 0

    private val pipResizeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == PrompterCommandReceiver.ACTION_APPLY_PIP_RESIZE) {
                    applyNextPipSize()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            pipResizeReceiver,
            IntentFilter(PrompterCommandReceiver.ACTION_APPLY_PIP_RESIZE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val launchOnPrompter = intent.getBooleanExtra(EXTRA_OPEN_PROMPTER, startOnPrompter)
        setContent {
            TeleprompterTheme {
                TeleprompterRoot(initialStage = launchOnPrompter)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipModeState.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            runCatching { setPictureInPictureParams(buildPipParams()) }
        }
    }

    fun enterPrompterPip(): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return false
        }
        return runCatching {
            enterPictureInPictureMode(buildPipParams())
        }.getOrDefault(false)
    }

    fun refreshPipParams() {
        if (pipModeState.value) {
            runCatching { setPictureInPictureParams(buildPipParams()) }
        }
    }

    private fun applyNextPipSize() {
        if (!isInPictureInPictureMode) {
            Log.d(TAG, "Ignoring PiP resize outside PiP")
            return
        }
        pipAspectIndex = (pipAspectIndex + 1) % PIP_ASPECTS.size
        val aspect = PIP_ASPECTS[pipAspectIndex]
        Log.d(TAG, "Applying PiP aspect $aspect (index $pipAspectIndex)")
        runCatching { setPictureInPictureParams(buildPipParams()) }
            .onFailure { error -> Log.w(TAG, "Could not resize PiP window", error) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val playPauseAction =
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (PrompterSession.isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    },
                ),
                if (PrompterSession.isPlaying) "Pause" else "Play",
                "Play or pause the teleprompter",
                commandPendingIntent(
                    PrompterCommandReceiver.ACTION_TOGGLE_PLAY,
                    REQUEST_PIP_PLAY_PAUSE,
                ),
            )
        val restartAction =
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_rew),
                "Restart",
                "Restart from the beginning",
                commandPendingIntent(PrompterCommandReceiver.ACTION_RESTART, REQUEST_PIP_RESTART),
            )
        val resizeAction =
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_menu_crop),
                "Resize",
                "Cycle picture-in-picture size and shape",
                commandPendingIntent(
                    PrompterCommandReceiver.ACTION_CYCLE_PIP_SIZE,
                    REQUEST_PIP_RESIZE,
                ),
            )
        val builder =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(PIP_ASPECTS[pipAspectIndex])
                .setActions(listOf(playPauseAction, restartAction, resizeAction))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    private fun commandPendingIntent(
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(this, PrompterCommandReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @Composable
    private fun TeleprompterRoot(initialStage: Boolean) {
        val context = LocalContext.current
        val activity = context as MainActivity
        var onStage by rememberSaveable { mutableStateOf(initialStage) }
        var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        var startOverlayRequested by remember { mutableStateOf(false) }
        var awaitingNotificationPermission by remember { mutableStateOf(false) }
        val isInPip = activity.pipModeState.value
        val pipSupported =
            activity.packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE,
            )

        val overlayPermissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                overlayAllowed = Settings.canDrawOverlays(context)
            }
        val notificationPermissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                awaitingNotificationPermission = false
            }
        val projectionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    PrompterOverlayService.start(
                        context = context,
                        adaptiveContrast = true,
                        resultCode = result.resultCode,
                        resultData = result.data,
                    )
                } else {
                    Toast
                        .makeText(
                            context,
                            "Screen capture denied; using manual high-contrast colors",
                            Toast.LENGTH_LONG,
                        ).show()
                    PrompterOverlayService.start(context, adaptiveContrast = false)
                }
            }

        DisposableEffect(activity) {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        overlayAllowed = Settings.canDrawOverlays(context)
                    }
                }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(
            startOverlayRequested,
            overlayAllowed,
            awaitingNotificationPermission,
        ) {
            if (startOverlayRequested && overlayAllowed && !awaitingNotificationPermission) {
                startOverlayRequested = false
                if (PrompterSession.adaptiveContrastEnabled) {
                    val manager = context.getSystemService(MediaProjectionManager::class.java)
                    projectionLauncher.launch(manager.createScreenCaptureIntent())
                } else {
                    PrompterOverlayService.start(context, adaptiveContrast = false)
                }
            }
        }

        LaunchedEffect(isInPip, PrompterSession.isPlaying) {
            if (isInPip) {
                activity.refreshPipParams()
            }
        }

        DisposableEffect(onStage, isInPip) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (onStage && !isInPip) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        BackHandler(enabled = onStage && !isInPip) {
            onStage = false
        }

        val requestOverlayStart: () -> Unit = {
            if (!Settings.canDrawOverlays(context)) {
                startOverlayRequested = true
                activity.openOverlayPermissionSettings()
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                startOverlayRequested = true
                awaitingNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startOverlayRequested = true
            }
        }
        val requestOverlayPermission: () -> Unit = {
            activity.openOverlayPermissionSettings()
        }

        if (onStage) {
            Phase0StageScreen(
                isInPip = isInPip,
                pipSupported = pipSupported,
                overlayRunning = PrompterSession.overlayRunning,
                onExit = { onStage = false },
                onEnterPip = {
                    if (!activity.enterPrompterPip()) {
                        Toast
                            .makeText(
                                context,
                                "Picture-in-picture could not be started",
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                },
                onStartOverlay = requestOverlayStart,
                onStopOverlay = { PrompterOverlayService.stop(context) },
            )
        } else {
            Phase0HomeScreen(
                overlayPermissionGranted = overlayAllowed,
                overlayRunning = PrompterSession.overlayRunning,
                pipSupported = pipSupported,
                onOpenPrompter = {
                    PrompterSession.restart()
                    onStage = true
                },
                onRequestOverlayPermission = requestOverlayPermission,
                onStartOverlay = requestOverlayStart,
                onStopOverlay = { PrompterOverlayService.stop(context) },
            )
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipResizeReceiver) }
        super.onDestroy()
    }

    private fun openOverlayPermissionSettings() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        startActivity(intent)
    }

    companion object {
        const val EXTRA_OPEN_PROMPTER = "open_prompter"
        private const val TAG = "TeleprompterPip"
        private const val REQUEST_PIP_PLAY_PAUSE = 21
        private const val REQUEST_PIP_RESTART = 22
        private const val REQUEST_PIP_RESIZE = 23

        private val PIP_ASPECTS =
            listOf(
                Rational(9, 16),
                Rational(3, 4),
                Rational(1, 1),
                Rational(4, 3),
                Rational(16, 9),
            )
    }
}
