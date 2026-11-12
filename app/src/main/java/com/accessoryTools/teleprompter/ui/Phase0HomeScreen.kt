package com.accessoryTools.teleprompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accessoryTools.teleprompter.prompter.PrompterSession
import kotlin.math.roundToInt

private val WideBreakpoint = 600.dp
private val WideContentMaxWidth = 1240.dp
private val CompactContentMaxWidth = 720.dp

@Composable
fun Phase0HomeScreen(
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    pipSupported: Boolean,
    onOpenPrompter: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideBreakpoint && maxHeight >= 700.dp
        if (wide) {
            WideHome(
                overlayPermissionGranted = overlayPermissionGranted,
                overlayRunning = overlayRunning,
                pipSupported = pipSupported,
                onOpenPrompter = onOpenPrompter,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onStartOverlay = onStartOverlay,
                onStopOverlay = onStopOverlay,
            )
        } else {
            CompactHome(
                overlayPermissionGranted = overlayPermissionGranted,
                overlayRunning = overlayRunning,
                pipSupported = pipSupported,
                onOpenPrompter = onOpenPrompter,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onStartOverlay = onStartOverlay,
                onStopOverlay = onStopOverlay,
            )
        }
    }
}

@Composable
private fun CompactHome(
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    pipSupported: Boolean,
    onOpenPrompter: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { CompactActionBar(onOpenPrompter = onOpenPrompter) },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HomeHeader(
                    compact = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = CompactContentMaxWidth),
                )
            }
            item {
                DemoScriptCard(
                    compact = true,
                    scriptMaxLines = 4,
                    pipSupported = pipSupported,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = CompactContentMaxWidth),
                )
            }
            item {
                ReadingSettingsCard(
                    compact = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = CompactContentMaxWidth),
                )
            }
            item {
                OverlayCard(
                    compact = true,
                    overlayPermissionGranted = overlayPermissionGranted,
                    overlayRunning = overlayRunning,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = CompactContentMaxWidth),
                )
            }
            item {
                VerificationCard(
                    compact = true,
                    onOpenPrompter = onOpenPrompter,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = CompactContentMaxWidth),
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CompactActionBar(onOpenPrompter: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Demo script",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = scriptSummary(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onOpenPrompter,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text("Open prompter", maxLines = 1)
            }
        }
    }
}

@Composable
private fun WideHome(
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    pipSupported: Boolean,
    onOpenPrompter: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WideHeader(
            onOpenPrompter = onOpenPrompter,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = WideContentMaxWidth),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = WideContentMaxWidth)
                    .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DemoScriptCard(
                    compact = false,
                    fillHeight = true,
                    scriptMaxLines = 40,
                    pipSupported = pipSupported,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
                VerificationCard(
                    compact = false,
                    onOpenPrompter = onOpenPrompter,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                WideControlDeck(
                    pipSupported = pipSupported,
                    overlayPermissionGranted = overlayPermissionGranted,
                    overlayRunning = overlayRunning,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhaseLabel()
        HomeTitle(compact = compact)
        HomeSubtitle()
    }
}

@Composable
private fun WideHeader(
    onOpenPrompter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PhaseLabel()
            HomeTitle(compact = false)
            HomeSubtitle()
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenPrompter,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text("Open prompter")
            }
            Text(
                text = scriptSummary(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun PhaseLabel() {
    Text(
        text = "PHASE 0  ·  TECHNICAL SPIKE",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HomeTitle(compact: Boolean) {
    Text(
        text = "Teleprompter",
        style =
            if (compact) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.displaySmall
            },
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HomeSubtitle() {
    Text(
        text =
            "Fullscreen reading, picture-in-picture, transparent overlay, " +
                "and adaptive contrast.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WideControlDeck(
    pipSupported: Boolean,
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
        ) {
            Text(
                text = "Reading settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LabeledSlider(
                    label = "Speed",
                    valueLabel = "${PrompterSession.speedWpm.roundToInt()} wpm",
                    value = PrompterSession.speedWpm,
                    valueRange = 60f..240f,
                    onValueChange = PrompterSession::updateSpeedWpm,
                )
                LabeledSlider(
                    label = "Text size",
                    valueLabel = "${PrompterSession.fontSizeSp.roundToInt()} sp",
                    value = PrompterSession.fontSizeSp,
                    valueRange = 24f..80f,
                    onValueChange = PrompterSession::updateFontSizeSp,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SessionPanel(
                pipSupported = pipSupported,
                overlayPermissionGranted = overlayPermissionGranted,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Transparent overlay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Adaptive contrast",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            "Samples the screen below and switches text between " +
                                "light and dark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = PrompterSession.adaptiveContrastEnabled,
                    onCheckedChange = PrompterSession::updateAdaptiveContrastEnabled,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text =
                    when {
                        overlayRunning -> "Overlay is running over other apps."
                        overlayPermissionGranted -> "Overlay permission granted."
                        else -> "Overlay permission is required for transparent mode."
                    },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (overlayRunning) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!overlayPermissionGranted) {
                    OutlinedButton(
                        onClick = onRequestOverlayPermission,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Grant overlay permission")
                    }
                }
                if (overlayRunning) {
                    Button(
                        onClick = onStopOverlay,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                    ) {
                        Text("Stop transparent overlay")
                    }
                } else {
                    Button(
                        onClick = onStartOverlay,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        enabled = overlayPermissionGranted,
                    ) {
                        Text("Start transparent overlay")
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPanel(
    pipSupported: Boolean,
    overlayPermissionGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxHeight >= 200.dp) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Session",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SessionStat(
                                label = "Words",
                                value = PrompterSession.wordCount.toString(),
                                modifier = Modifier.weight(1f),
                            )
                            SessionStat(
                                label = "Duration",
                                value =
                                    formatDuration(
                                        PrompterSession.wordCount,
                                        PrompterSession.speedWpm,
                                    ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SessionStat(
                                label = "Pace",
                                value = "${PrompterSession.speedWpm.roundToInt()} wpm",
                                modifier = Modifier.weight(1f),
                            )
                            SessionStat(
                                label = "Text",
                                value = "${PrompterSession.fontSizeSp.roundToInt()} sp",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReadinessRow(
                                label = "Picture-in-picture",
                                ready = pipSupported,
                                readyLabel = "Ready",
                                notReadyLabel = "Unavailable",
                            )
                            ReadinessRow(
                                label = "Transparent overlay",
                                ready = overlayPermissionGranted,
                                readyLabel = "Ready",
                                notReadyLabel = "Permission needed",
                            )
                            ReadinessRow(
                                label = "Adaptive contrast",
                                ready = PrompterSession.adaptiveContrastEnabled,
                                readyLabel = "On",
                                notReadyLabel = "Off",
                            )
                        }
                    }
                    Text(
                        text =
                            if (pipSupported && overlayPermissionGranted) {
                                "Ready to prompt"
                            } else {
                                "Finish setup above"
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (pipSupported && overlayPermissionGranted) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                }
            } else {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${PrompterSession.wordCount} words",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text =
                            formatDuration(
                                PrompterSession.wordCount,
                                PrompterSession.speedWpm,
                            ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "${PrompterSession.speedWpm.roundToInt()} wpm",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "${PrompterSession.fontSizeSp.roundToInt()} sp",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReadinessRow(
    label: String,
    ready: Boolean,
    readyLabel: String,
    notReadyLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            color =
                                if (ready) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            shape = CircleShape,
                        ),
            )
            Text(
                text = if (ready) readyLabel else notReadyLabel,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (ready) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun DemoScriptCard(
    compact: Boolean,
    fillHeight: Boolean = false,
    scriptMaxLines: Int,
    pipSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                if (fillHeight) {
                    Modifier
                        .fillMaxSize()
                        .padding(if (compact) 16.dp else 20.dp)
                } else {
                    Modifier.padding(if (compact) 16.dp else 20.dp)
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Demo script",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = PrompterSession.script,
                modifier =
                    if (fillHeight) {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    } else {
                        Modifier
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = scriptMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = scriptSummary(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text =
                    if (pipSupported) {
                        "Picture-in-picture is available on this device. Enter it from " +
                            "the prompter, then open any camera app."
                    } else {
                        "Picture-in-picture is unavailable on this device. " +
                            "The transparent overlay remains available."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadingSettingsCard(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Reading settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LabeledSlider(
                label = "Speed",
                valueLabel = "${PrompterSession.speedWpm.roundToInt()} wpm",
                value = PrompterSession.speedWpm,
                valueRange = 60f..240f,
                onValueChange = PrompterSession::updateSpeedWpm,
            )
            LabeledSlider(
                label = "Text size",
                valueLabel = "${PrompterSession.fontSizeSp.roundToInt()} sp",
                value = PrompterSession.fontSizeSp,
                valueRange = 24f..80f,
                onValueChange = PrompterSession::updateFontSizeSp,
            )
        }
    }
}

@Composable
private fun OverlayCard(
    compact: Boolean,
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Transparent overlay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Adaptive contrast",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            "Samples the screen below and switches text between " +
                                "light and dark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = PrompterSession.adaptiveContrastEnabled,
                    onCheckedChange = PrompterSession::updateAdaptiveContrastEnabled,
                )
            }
            Text(
                text =
                    when {
                        overlayRunning -> "Overlay is running over other apps."
                        overlayPermissionGranted -> "Overlay permission granted."
                        else -> "Overlay permission is required for transparent mode."
                    },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (overlayRunning) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (!overlayPermissionGranted) {
                OutlinedButton(
                    onClick = onRequestOverlayPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant overlay permission")
                }
            }
            if (overlayRunning) {
                Button(
                    onClick = onStopOverlay,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop transparent overlay")
                }
            } else {
                Button(
                    onClick = onStartOverlay,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = overlayPermissionGranted,
                ) {
                    Text("Start transparent overlay")
                }
            }
        }
    }
}

@Composable
private fun VerificationCard(
    compact: Boolean,
    onOpenPrompter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Phase 0 verification",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    "1. Open the prompter and test fullscreen scrolling.\n" +
                        "2. Press PiP, then open the camera app.\n" +
                        "3. Start the overlay, then open the camera app.\n" +
                        "4. Check that touches pass through the script and the control " +
                        "bar still works.\n" +
                        "5. Toggle adaptive contrast and point the camera at light and dark " +
                        "scenes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPrompter) {
                Text("Skip to prompter")
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
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
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

private fun scriptSummary(): String =
    "${PrompterSession.wordCount} words  ·  " +
        "${formatDuration(PrompterSession.wordCount, PrompterSession.speedWpm)} " +
        "at ${PrompterSession.speedWpm.roundToInt()} wpm"

private fun formatDuration(
    words: Int,
    wpm: Float,
): String {
    val seconds = ((words / wpm.coerceAtLeast(1f)) * 60f).roundToInt()
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${remainder}s"
    } else {
        "${remainder}s"
    }
}
