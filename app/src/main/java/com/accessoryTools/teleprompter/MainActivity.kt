package com.accessoryTools.teleprompter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.accessoryTools.teleprompter.data.LibrarySort
import com.accessoryTools.teleprompter.data.Script
import com.accessoryTools.teleprompter.data.ScriptImportException
import com.accessoryTools.teleprompter.data.ScriptImporter
import com.accessoryTools.teleprompter.data.ScriptSharing
import com.accessoryTools.teleprompter.overlay.PrompterOverlayService
import com.accessoryTools.teleprompter.platform.PrompterCommandReceiver
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.accessoryTools.teleprompter.ui.StageScreen
import com.accessoryTools.teleprompter.ui.TeleprompterTheme
import com.accessoryTools.teleprompter.ui.editor.EditorScreen
import com.accessoryTools.teleprompter.ui.library.LibraryScreen
import com.accessoryTools.teleprompter.ui.settings.SettingsScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pipModeState = mutableStateOf(false)
    private val stageActive = mutableStateOf(false)
    private val incomingIntent = mutableStateOf<Intent?>(null)
    private var pipAspectIndex = 0
    private lateinit var mediaSession: MediaSession

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
        incomingIntent.value = intent
        val launchOnPrompter = intent.getBooleanExtra(EXTRA_OPEN_PROMPTER, false)
        createMediaSession()
        setContent {
            TeleprompterTheme {
                TeleprompterRoot(
                    initialStage = launchOnPrompter,
                    incomingIntent = incomingIntent.value,
                    onIncomingIntentConsumed = { incomingIntent.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntent.value = intent
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
    private fun TeleprompterRoot(
        initialStage: Boolean,
        incomingIntent: Intent?,
        onIncomingIntentConsumed: () -> Unit,
    ) {
        val context = LocalContext.current
        val activity = context as MainActivity
        val app = context.applicationContext as TeleprompterApp
        val scope = rememberCoroutineScope()
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val onStage = currentRoute == Routes.STAGE
        val isInPip = activity.pipModeState.value
        val pipSupported =
            activity.packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE,
            )

        val scripts by app.scripts.scripts.collectAsState()
        val loading by app.scripts.loading.collectAsState()
        val storedSettings by app.settings.settings.collectAsState(initial = null)

        var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        var startOverlayRequested by remember { mutableStateOf(false) }
        var awaitingNotificationPermission by remember { mutableStateOf(false) }

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
                        adaptiveContrast = PrompterSession.adaptiveContrastEnabled,
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

        LaunchedEffect(initialStage) {
            if (initialStage) {
                navController.navigate(Routes.STAGE)
            }
        }

        DisposableEffect(onStage, isInPip, PrompterSession.keepAwakeEnabled) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (onStage && !isInPip) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            if (onStage && PrompterSession.keepAwakeEnabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        DisposableEffect(onStage) {
            activity.stageActive.value = onStage
            activity.mediaSession.isActive = onStage
            if (onStage) {
                activity.updateMediaSessionState()
            }
            onDispose {
                activity.stageActive.value = false
                activity.mediaSession.isActive = false
            }
        }

        LaunchedEffect(onStage, PrompterSession.isPlaying, PrompterSession.countdownRemaining) {
            if (onStage) {
                activity.updateMediaSessionState()
            }
        }

        DisposableEffect(onStage, activity) {
            if (!onStage) {
                return@DisposableEffect onDispose { }
            }
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE) {
                        val id = PrompterSession.scriptId ?: return@LifecycleEventObserver
                        val progress = PrompterSession.progress
                        val stage = PrompterSession.currentStageSettings()
                        scope.launch {
                            app.scripts.updateStageSettings(id, stage)
                            app.scripts.saveProgress(id, progress)
                        }
                    }
                }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }

        DisposableEffect(onStage) {
            if (!onStage) {
                return@DisposableEffect onDispose { }
            }
            var saveJob: Job? = null
            PrompterSession.onStageSettingsChanged = { settings ->
                val id = PrompterSession.scriptId
                if (id != null) {
                    saveJob?.cancel()
                    saveJob =
                        scope.launch {
                            delay(350)
                            app.scripts.updateStageSettings(id, settings)
                        }
                }
            }
            onDispose {
                saveJob?.cancel()
                PrompterSession.onStageSettingsChanged = null
            }
        }

        val saveStageProgress: suspend () -> Unit = {
            val id = PrompterSession.scriptId
            if (id != null) {
                app.scripts.updateStageSettings(id, PrompterSession.currentStageSettings())
                app.scripts.saveProgress(id, PrompterSession.progress)
            }
        }

        val exitStage: () -> Unit = {
            scope.launch {
                PrompterSession.setControlLocked(false)
                if (!PrompterSession.overlayRunning) {
                    PrompterSession.setPlayback(false)
                }
                saveStageProgress()
                navController.popBackStack()
            }
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

        val importUris: suspend (List<Uri>) -> Int = { uris ->
            var imported = 0
            var firstError: Throwable? = null
            uris.forEach { uri ->
                try {
                    val document = ScriptImporter.import(context, uri)
                    app.scripts.createScript(document.title, document.content)
                    imported += 1
                } catch (error: Throwable) {
                    if (firstError == null) {
                        firstError = error
                    }
                }
            }
            if (imported == 0) {
                throw firstError ?: ScriptImportException("No scripts were imported.")
            }
            imported
        }

        val importClipboard: suspend () -> Boolean = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clip = clipboard?.primaryClip
            val text =
                if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(context)?.toString()
                } else {
                    null
                }
            if (text.isNullOrBlank()) {
                false
            } else {
                val document = ScriptImporter.fromPlainText(text, "Pasted script")
                app.scripts.createScript(document.title, document.content)
                true
            }
        }

        LaunchedEffect(incomingIntent) {
            val intent = incomingIntent ?: return@LaunchedEffect
            val action = intent.action
            if (action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW) {
                onIncomingIntentConsumed()
                return@LaunchedEffect
            }
            runCatching {
                when {
                    action == Intent.ACTION_SEND && intent.sharedStream() != null -> {
                        val uri = intent.sharedStream()!!
                        val document = ScriptImporter.import(context, uri)
                        app.scripts.createScript(document.title, document.content)
                    }

                    action == Intent.ACTION_SEND -> {
                        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                        if (text.isNullOrBlank()) {
                            error("The shared content did not include text.")
                        }
                        val document = ScriptImporter.fromPlainText(text, "Shared script")
                        app.scripts.createScript(document.title, document.content)
                    }

                    else -> {
                        val uri = intent.data
                        if (uri == null) {
                            error("The opened item did not include a file.")
                        }
                        val document = ScriptImporter.import(context, uri)
                        app.scripts.createScript(document.title, document.content)
                    }
                }
            }.onSuccess { script ->
                navController.navigate(Routes.editor(script.id))
            }.onFailure { error ->
                Toast
                    .makeText(
                        context,
                        error.message ?: "Could not import the shared content.",
                        Toast.LENGTH_LONG,
                    ).show()
            }
            onIncomingIntentConsumed()
        }

        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    scripts = scripts,
                    loading = loading,
                    initialSort = storedSettings?.sortOrder ?: LibrarySort.UPDATED,
                    initialFavoritesOnly = storedSettings?.favoritesOnly ?: false,
                    onSortChanged = { sort ->
                        app.settings.saveLibraryPreferences(sort, storedSettings?.favoritesOnly ?: false)
                    },
                    onFavoritesChanged = { favoritesOnly ->
                        app.settings.saveLibraryPreferences(
                            storedSettings?.sortOrder ?: LibrarySort.UPDATED,
                            favoritesOnly,
                        )
                    },
                    onNewScript = {
                        scope.launch {
                            val script = app.scripts.createScript("Untitled script", "")
                            navController.navigate(Routes.editor(script.id))
                        }
                    },
                    onOpenScript = { id -> navController.navigate(Routes.editor(id)) },
                    onPresent = { script ->
                        scope.launch {
                            val fresh = app.scripts.markOpened(script.id) ?: script
                            PrompterSession.loadScript(
                                id = fresh.id,
                                title = fresh.title,
                                content = fresh.content,
                                startProgress = fresh.lastPosition,
                                stage = fresh.stage,
                            )
                            navController.navigate(Routes.STAGE)
                        }
                    },
                    onToggleFavorite = { script ->
                        scope.launch {
                            app.scripts.setFavorite(script.id, !script.favorite)
                        }
                    },
                    onDuplicate = { script ->
                        scope.launch { app.scripts.duplicateScript(script.id) }
                    },
                    onShare = { script -> ScriptSharing.shareText(context, script) },
                    onDelete = { script ->
                        scope.launch { app.scripts.deleteScript(script.id) }
                    },
                    onRestore = { id ->
                        scope.launch { app.scripts.restoreScript(id) }
                    },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onImportUris = importUris,
                    onImportClipboard = importClipboard,
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument(Routes.EDITOR_ARG) { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString(Routes.EDITOR_ARG).orEmpty()
                val script = scripts.firstOrNull { it.id == id }
                when {
                    script != null -> {
                        EditorScreen(
                            script = script,
                            onBack = { title, content ->
                                app.scripts.updateScript(id, title, content)
                                navController.popBackStack()
                            },
                            onAutosave = { title, content ->
                                app.scripts.updateScript(id, title, content)
                            },
                            onPresent = { title, content ->
                                val updated =
                                    app.scripts.updateScript(id, title, content)
                                        ?: Script(
                                            id = id,
                                            title = title,
                                            content = content,
                                            createdAt = script.createdAt,
                                            updatedAt = System.currentTimeMillis(),
                                        )
                                PrompterSession.loadScript(
                                    id = updated.id,
                                    title = updated.title,
                                    content = updated.content,
                                    startProgress = updated.lastPosition,
                                    stage = updated.stage,
                                )
                                navController.navigate(Routes.STAGE)
                            },
                            onShare = { title, content ->
                                ScriptSharing.shareText(
                                    context,
                                    script.copy(title = title, content = content),
                                )
                            },
                            onExport = { title, content, markdown, uri ->
                                ScriptSharing.export(
                                    context = context,
                                    uri = uri,
                                    title = title,
                                    content = content,
                                    markdown = markdown,
                                )
                            },
                        )
                    }

                    loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "This script is no longer available.",
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    overlayPermissionGranted = overlayAllowed,
                    overlayRunning = PrompterSession.overlayRunning,
                    projectionActive = PrompterSession.projectionActive,
                    onBack = { navController.popBackStack() },
                    onRequestOverlayPermission = requestOverlayPermission,
                    onStartOverlay = requestOverlayStart,
                    onStopOverlay = { PrompterOverlayService.stop(context) },
                    onBackup = { uri -> app.scripts.writeBackup(uri) },
                    onRestore = { uri ->
                        val restored = app.scripts.restoreBackup(uri)
                        app.scripts.refresh()
                        restored
                    },
                )
            }

            composable(Routes.STAGE) {
                BackHandler(onBack = exitStage)
                StageScreen(
                    isInPip = isInPip,
                    pipSupported = pipSupported,
                    overlayRunning = PrompterSession.overlayRunning,
                    onExit = exitStage,
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
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipResizeReceiver) }
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (stageActive.value && handleStageKey(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleStageKey(event: KeyEvent): Boolean {
        val onDown = event.action == KeyEvent.ACTION_DOWN
        val firstPress = onDown && event.repeatCount == 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> {
                if (firstPress) {
                    PrompterSession.togglePlay()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            -> {
                if (onDown) {
                    PrompterSession.seekBySeconds(-SEEK_STEP_SECONDS)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            -> {
                if (onDown) {
                    PrompterSession.seekBySeconds(SEEK_STEP_SECONDS)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_VOLUME_UP,
            -> {
                if (onDown) {
                    PrompterSession.nudgeSpeed(SPEED_STEP)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            -> {
                if (onDown) {
                    PrompterSession.nudgeSpeed(-SPEED_STEP)
                }
                return true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                if (onDown) {
                    PrompterSession.nudgeFontSize(2f)
                }
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (onDown) {
                    PrompterSession.nudgeFontSize(-2f)
                }
                return true
            }

            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_BUTTON_B,
            -> {
                if (firstPress) {
                    PrompterSession.restart()
                }
                return true
            }

            KeyEvent.KEYCODE_M -> {
                if (firstPress) {
                    PrompterSession.toggleMirrorHorizontal()
                }
                return true
            }

            KeyEvent.KEYCODE_L -> {
                if (firstPress) {
                    PrompterSession.toggleLock()
                }
                return true
            }

            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (onDown) {
                    PrompterSession.setPlayback(false)
                }
                return true
            }

            else -> {
                return false
            }
        }
    }

    private fun createMediaSession() {
        mediaSession = MediaSession(this, MEDIA_SESSION_TAG)
        mediaSession.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!PrompterSession.isPlaying && PrompterSession.countdownRemaining == 0) {
                        PrompterSession.togglePlay()
                    }
                    updateMediaSessionState()
                }

                override fun onPause() {
                    PrompterSession.setPlayback(false)
                    updateMediaSessionState()
                }

                override fun onStop() {
                    PrompterSession.setPlayback(false)
                    updateMediaSessionState()
                }

                override fun onSkipToNext() {
                    PrompterSession.seekBySeconds(SEEK_STEP_SECONDS)
                }

                override fun onSkipToPrevious() {
                    PrompterSession.seekBySeconds(-SEEK_STEP_SECONDS)
                }

                override fun onFastForward() {
                    PrompterSession.seekBySeconds(MEDIA_SEEK_SECONDS)
                }

                override fun onRewind() {
                    PrompterSession.seekBySeconds(-MEDIA_SEEK_SECONDS)
                }
            },
        )
        updateMediaSessionState()
    }

    fun updateMediaSessionState() {
        if (!::mediaSession.isInitialized) {
            return
        }
        val playing = PrompterSession.isPlaying
        val state =
            PlaybackState
                .Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_FAST_FORWARD or
                        PlaybackState.ACTION_REWIND,
                ).setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) 1f else 0f,
                ).build()
        mediaSession.setPlaybackState(state)
    }

    private fun openOverlayPermissionSettings() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        startActivity(intent)
    }

    private object Routes {
        const val LIBRARY = "library"
        const val SETTINGS = "settings"
        const val STAGE = "stage"
        const val EDITOR_ARG = "scriptId"
        const val EDITOR = "editor/{$EDITOR_ARG}"

        fun editor(id: String): String = "editor/$id"
    }

    companion object {
        const val EXTRA_OPEN_PROMPTER = "open_prompter"
        private const val TAG = "TeleprompterPip"
        private const val MEDIA_SESSION_TAG = "Teleprompter"
        private const val REQUEST_PIP_PLAY_PAUSE = 21
        private const val REQUEST_PIP_RESTART = 22
        private const val REQUEST_PIP_RESIZE = 23
        private const val SPEED_STEP = 5f
        private const val SEEK_STEP_SECONDS = 5f
        private const val MEDIA_SEEK_SECONDS = 15f

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

private fun Intent.sharedStream(): Uri? {
    val stream =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    return stream ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
}
