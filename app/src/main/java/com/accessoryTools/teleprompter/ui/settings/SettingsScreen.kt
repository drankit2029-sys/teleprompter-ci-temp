package com.accessoryTools.teleprompter.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accessoryTools.teleprompter.prompter.PrompterSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val SettingsMaxWidth = 900.dp

@Composable
fun SettingsScreen(
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    projectionActive: Boolean,
    onBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onBackup: suspend (Uri) -> Int,
    onRestore: suspend (Uri) -> Int,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                scope.launch {
                    backingUp = true
                    runCatching { onBackup(uri) }
                        .onSuccess { count ->
                            snackbarHostState.showSnackbar(
                                if (count == 1) "Backed up 1 script" else "Backed up $count scripts",
                            )
                        }.onFailure {
                            snackbarHostState.showSnackbar("Backup failed: ${it.message ?: "unknown error"}")
                        }
                    backingUp = false
                }
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    restoring = true
                    runCatching { onRestore(uri) }
                        .onSuccess { count ->
                            snackbarHostState.showSnackbar(
                                if (count == 1) "Restored 1 script" else "Restored $count scripts",
                            )
                        }.onFailure {
                            snackbarHostState.showSnackbar("Restore failed: ${it.message ?: "unknown error"}")
                        }
                    restoring = false
                }
            }
        }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Reading defaults, overlay, and local backup",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = SettingsMaxWidth)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ReadingDefaultsCard()
                OverlayCard(
                    overlayPermissionGranted = overlayPermissionGranted,
                    overlayRunning = overlayRunning,
                    projectionActive = projectionActive,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                )
                BackupCard(
                    backingUp = backingUp,
                    restoring = restoring,
                    onBackup = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        backupLauncher.launch("teleprompter-backup-$stamp.zip")
                    },
                    onRestore = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                )
                Text(
                    text =
                        "Teleprompter keeps scripts in app-private storage on this device. " +
                            "Nothing is uploaded automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReadingDefaultsCard() {
    SettingsCard(title = "Reading defaults") {
        Text(
            text = "These values apply when you open a script. You can still adjust them on the stage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SliderRow(
            label = "Speed",
            valueLabel = "${PrompterSession.speedWpm.roundToInt()} wpm",
            value = PrompterSession.speedWpm,
            valueRange = 40f..260f,
            steps = 21,
            onValueChange = PrompterSession::updateSpeedWpm,
        )
        SliderRow(
            label = "Text size",
            valueLabel = "${PrompterSession.fontSizeSp.roundToInt()} sp",
            value = PrompterSession.fontSizeSp,
            valueRange = 18f..100f,
            steps = 40,
            onValueChange = PrompterSession::updateFontSizeSp,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Adaptive contrast", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Sample the screen below and pick light or dark text automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = PrompterSession.adaptiveContrastEnabled,
                onCheckedChange = PrompterSession::updateAdaptiveContrastEnabled,
            )
        }
    }
}

@Composable
private fun OverlayCard(
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    projectionActive: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    SettingsCard(title = "Transparent overlay") {
        Text(
            text =
                when {
                    overlayRunning && projectionActive -> "Overlay is live with adaptive contrast."
                    overlayRunning -> "Overlay is live with manual high-contrast colors."
                    overlayPermissionGranted -> "Display-over-other-apps permission is granted."
                    else -> "Display-over-other-apps permission is required."
                },
            style = MaterialTheme.typography.bodySmall,
            color =
                if (overlayRunning) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (overlayRunning) {
                Button(onClick = onStopOverlay) { Text("Stop overlay") }
            } else {
                Button(onClick = onStartOverlay, enabled = overlayPermissionGranted) {
                    Text("Start overlay")
                }
            }
            if (!overlayPermissionGranted) {
                OutlinedButton(onClick = onRequestOverlayPermission) {
                    Text("Grant permission")
                }
            }
        }
        Text(
            text =
                "While the overlay is running, open any camera or third-party app. " +
                    "The script stays above it, touches pass through the text, and the " +
                    "floating bar can be dragged out of the way.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackupCard(
    backingUp: Boolean,
    restoring: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    SettingsCard(title = "Backup and restore") {
        Text(
            text =
                "Back up every script to a ZIP file you choose, or restore a previous " +
                    "backup. Restoring merges scripts and skips exact copies.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBackup, enabled = !backingUp && !restoring) {
                if (backingUp) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Back up")
                }
            }
            OutlinedButton(onClick = onRestore, enabled = !backingUp && !restoring) {
                if (restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Restore")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
