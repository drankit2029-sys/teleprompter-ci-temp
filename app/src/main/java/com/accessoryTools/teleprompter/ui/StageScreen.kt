package com.accessoryTools.teleprompter.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.accessoryTools.teleprompter.data.StageAlignment
import com.accessoryTools.teleprompter.data.StageBackground
import com.accessoryTools.teleprompter.data.StageFont
import com.accessoryTools.teleprompter.data.StageSettings
import com.accessoryTools.teleprompter.data.StageTextColor
import com.accessoryTools.teleprompter.data.StageWeight
import com.accessoryTools.teleprompter.data.argb
import com.accessoryTools.teleprompter.data.isReadableTextColorOn
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.accessoryTools.teleprompter.prompter.PrompterStage
import com.accessoryTools.teleprompter.prompter.StageTypefaces
import com.accessoryTools.teleprompter.ui.Neo
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val WideChromeBreakpoint = 600.dp
private val WideChromeMaxWidth = 1_100.dp


private fun panelColor(): Color = Neo.Paper

private enum class StageTab(
    val label: String,
) {
    PLAYBACK("Playback"),
    TEXT("Text"),
    LOOK("Look"),
    WINDOW("Window"),
}

@Composable
fun StageScreen(
    isInPip: Boolean,
    pipSupported: Boolean,
    overlayRunning: Boolean,
    onExit: () -> Unit,
    onEnterPip: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    var showChrome by rememberSaveable { mutableStateOf(!isInPip) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    val locked = PrompterSession.locked
    // Locking must not strand a half-finished scrub (chrome disappears while the
    // slider is capturing the gesture). Pausing only when a scrub was in flight.
    LaunchedEffect(locked) {
        if (locked && scrubbing) {
            scrubbing = false
            wasPlayingBeforeScrub = false
            PrompterSession.setPlayback(false)
        }
    }

    LaunchedEffect(locked) {
        if (!isInPip) {
            showChrome = !locked
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(PrompterSession.effectiveBackgroundArgb)),
    ) {
        val wide = maxWidth >= WideChromeBreakpoint
        val isLandscape = maxWidth > maxHeight
        val maxWidthDp = maxWidth
        val maxHeightDp = maxHeight
        // The side panel is a landscape layout; portrait (including Expanded-width
        // tablets like the 10") always uses the bottom control panel.
        val useSidePanel = isLandscape
        val sidePanelDefault =
            when {
                maxWidthDp >= 840.dp -> 380f
                maxWidthDp >= 640.dp -> 320f
                else -> 260f
            }
        val sidePanelWidthDp =
            rememberSaveable(useSidePanel) {
                mutableFloatStateOf(sidePanelDefault)
            }
        val sidePanelWidth = sidePanelWidthDp.floatValue.dp
        val panelFraction =
            rememberSaveable(isLandscape) {
                mutableFloatStateOf(if (isLandscape) 0.66f else 0.46f)
            }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val panelMaxHeight = maxHeight * panelFraction.floatValue
        // In side-panel mode the stage must render only the script column —
        // anything else renders centered under the panel and gets clipped.
        val stageModifier =
            if (useSidePanel) {
                Modifier.fillMaxHeight().width(maxWidth - sidePanelWidthDp.floatValue.dp - 22.dp)
            } else {
                Modifier.fillMaxSize()
            }
        PrompterStage(
            modifier = stageModifier.safeDrawingPadding(),
            compact = isInPip,
        )
        if (!isInPip) {
            Box(
                modifier =
                    stageModifier
                        .pointerInput(PrompterSession.isPlaying, locked) {
                            if (!locked && !PrompterSession.isPlaying) {
                                // Drag-to-scrub with an inertial fling on release,
                                // like a scrollable list.
                                val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        PrompterSession.scrollByPixels(-dragAmount)
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        PrompterSession.startFling(velocityTracker.calculateVelocity().y)
                                    },
                                    onDragCancel = {
                                        velocityTracker.resetTracking()
                                    },
                                )
                            }
                        }.pointerInput(locked) {
                            detectTapGestures(
                                onTap = {
                                    if (!PrompterSession.locked) {
                                        showChrome = !showChrome
                                    }
                                },
                                onLongPress = {
                                    if (PrompterSession.locked) {
                                        PrompterSession.setControlLocked(false)
                                    }
                                },
                            )
                        },
            )
            if (showChrome && !locked) {
                if (useSidePanel) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .padding(
                                    horizontal = if (wide) 24.dp else 8.dp,
                                    vertical = 8.dp,
                                ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Transparent column: chrome only, taps fall through to the stage below.
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            TopChrome(
                                wide = wide,
                                onExit = onExit,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = WideChromeMaxWidth),
                            )
                        }
                        VerticalPanelResizeHandle(
                            widthDp = sidePanelWidthDp,
                            modifier = Modifier.fillMaxHeight(),
                        )
                        // Side panel is a narrow single column: never the two-column layout.
                        StagePanel(
                            wide = false,
                            sidePanel = true,
                            selectedTabIndex = selectedTabIndex,
                            onSelectTab = { selectedTabIndex = it },
                            panelMaxHeight = maxHeightDp,
                            pipSupported = pipSupported,
                            overlayRunning = overlayRunning,
                            scrubbing = scrubbing,
                            onScrubStart = {
                                if (!scrubbing) {
                                    scrubbing = true
                                    wasPlayingBeforeScrub = PrompterSession.isPlaying
                                    if (wasPlayingBeforeScrub) {
                                        PrompterSession.setPlayback(false)
                                    }
                                }
                            },
                            onScrubEnd = {
                                if (scrubbing) {
                                    if (wasPlayingBeforeScrub) {
                                        PrompterSession.setPlayback(true)
                                    }
                                    scrubbing = false
                                    wasPlayingBeforeScrub = false
                                }
                            },
                            onExit = onExit,
                            onEnterPip = onEnterPip,
                            onStartOverlay = onStartOverlay,
                            onStopOverlay = onStopOverlay,
                            modifier =
                                Modifier
                                    .width(sidePanelWidth)
                                    .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .padding(
                                    horizontal = if (wide) 24.dp else 8.dp,
                                    vertical = 10.dp,
                                ),
                    ) {
                        TopChrome(
                            wide = wide,
                            onExit = onExit,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .widthIn(max = WideChromeMaxWidth)
                                    .fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        PanelResizeHandle(
                            fraction = panelFraction,
                            maxHeightPx = maxHeightPx,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        StagePanel(
                        wide = wide,
                        selectedTabIndex = selectedTabIndex,
                        onSelectTab = { selectedTabIndex = it },
                        panelMaxHeight = panelMaxHeight,
                        pipSupported = pipSupported,
                        overlayRunning = overlayRunning,
                        scrubbing = scrubbing,
                        onScrubStart = {
                            if (!scrubbing) {
                                scrubbing = true
                                wasPlayingBeforeScrub = PrompterSession.isPlaying
                                if (wasPlayingBeforeScrub) {
                                    PrompterSession.setPlayback(false)
                                }
                            }
                        },
                        onScrubEnd = {
                            if (scrubbing) {
                                if (wasPlayingBeforeScrub) {
                                    PrompterSession.setPlayback(true)
                                }
                                scrubbing = false
                                wasPlayingBeforeScrub = false
                            }
                        },
                        onExit = onExit,
                        onEnterPip = onEnterPip,
                        onStartOverlay = onStartOverlay,
                                onStopOverlay = onStopOverlay,
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .widthIn(max = WideChromeMaxWidth)
                                        .fillMaxWidth(),
                        )
                    }
                }
            }
            if (locked) {
                LockedBadge(
                    onUnlock = { PrompterSession.setControlLocked(false) },
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .safeDrawingPadding()
                            .padding(top = 14.dp),
                )
            }
            if (PrompterSession.countdownRemaining > 0) {
                CountdownOverlay(
                    remaining = PrompterSession.countdownRemaining,
                    onCancel = PrompterSession::togglePlay,
                )
            }
            if (PrompterSession.isAtEnd && !PrompterSession.loopEnabled && !locked) {
                EndOverlay(
                    onReplay = PrompterSession::togglePlay,
                    onExit = onExit,
                )
            }
        }
    }
}

@Composable
private fun TopChrome(
    wide: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.border(3.dp, Neo.Ink),
        color = Neo.Yellow,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (wide) 16.dp else 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PrompterSession.scriptTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        "${PrompterSession.speedWpm.roundToInt()} WPM  ·  " +
                            "${(PrompterSession.progress * 100).roundToInt()}%  ·  " +
                            "${formatClock(PrompterSession.remainingSeconds)} left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = PrompterSession::toggleLock) {
                Text("Lock")
            }
            TextButton(onClick = onExit) {
                Text("Exit")
            }
        }
    }
}

@Composable
private fun VerticalPanelResizeHandle(
    widthDp: androidx.compose.runtime.MutableFloatState,
    modifier: Modifier = Modifier,
) {
    val minWidth = 240f
    val maxWidth = 560f
    val density = LocalDensity.current
    Row(
        modifier =
            modifier
                .width(22.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        // Dragging left widens the panel, dragging right narrows it.
                        widthDp.floatValue =
                            (widthDp.floatValue - dragAmount / density.density)
                                .coerceIn(minWidth, maxWidth)
                    }
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(14.dp)
                    .height(56.dp)
                    .background(Neo.Paper)
                    .border(2.dp, Neo.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Box(modifier = Modifier.size(7.dp, 3.dp).background(Neo.Ink))
                }
            }
        }
    }
}

@Composable
private fun PanelResizeHandle(
    fraction: androidx.compose.runtime.MutableFloatState,
    maxHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        fraction.floatValue =
                            (fraction.floatValue + (-dragAmount / maxHeightPx.coerceAtLeast(1f)))
                                .coerceIn(0.35f, 0.92f)
                    }
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(56.dp)
                    .height(14.dp)
                    .background(Neo.Paper)
                    .border(2.dp, Neo.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) {
                    Box(modifier = Modifier.size(3.dp, 7.dp).background(Neo.Ink))
                }
            }
        }
    }
}

@Composable
private fun StagePanel(
    wide: Boolean,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    panelMaxHeight: Dp,
    sidePanel: Boolean = false,
    pipSupported: Boolean,
    overlayRunning: Boolean,
    scrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onExit: () -> Unit,
    onEnterPip: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = StageTab.entries
    val selectedTab = tabs[selectedTabIndex.coerceIn(0, tabs.lastIndex)]
    val contentScroll = rememberScrollState()
    LaunchedEffect(selectedTab) {
        contentScroll.scrollTo(0)
    }
    Surface(
        modifier =
            if (sidePanel) {
                modifier.fillMaxHeight().border(3.dp, Neo.Ink)
            } else {
                // Default hugs the controls; the grip above can grow the cap up to
                // panelMaxHeight, and the content scrolls past the cap.
                modifier.heightIn(max = panelMaxHeight).border(3.dp, Neo.Ink)
            },
        color = panelColor(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (wide) 12.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 6.dp),
        ) {
            ProgressScrubber(
                scrubbing = scrubbing,
                onScrubStart = onScrubStart,
                onScrubEnd = onScrubEnd,
            )
            TransportRow()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = tab == selectedTab
                    if (selected) {
                        Button(
                            onClick = { onSelectTab(index) },
                            modifier = Modifier.weight(1f),
                            contentPadding = tightPadding(),
                        ) {
                            Text(
                                tab.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectTab(index) },
                            modifier = Modifier.weight(1f),
                            contentPadding = tightPadding(),
                        ) {
                            Text(
                                tab.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(contentScroll),
                verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 8.dp),
            ) {
                when (selectedTab) {
                    StageTab.PLAYBACK -> {
                        PlaybackSettings(wide = wide)
                    }

                    StageTab.TEXT -> {
                        TextSettings(wide = wide)
                    }

                    StageTab.LOOK -> {
                        LookSettings(wide = wide)
                    }

                    StageTab.WINDOW -> {
                        WindowSettings(
                            wide = wide,
                            pipSupported = pipSupported,
                            overlayRunning = overlayRunning,
                            onExit = onExit,
                            onEnterPip = onEnterPip,
                            onStartOverlay = onStartOverlay,
                            onStopOverlay = onStopOverlay,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressScrubber(
    scrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Position",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    "${formatClock((PrompterSession.totalSeconds * PrompterSession.progress).roundToInt())} / " +
                        formatClock(PrompterSession.totalSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = PrompterSession.progress,
            onValueChange = {
                if (!scrubbing) {
                    onScrubStart()
                }
                PrompterSession.seekToProgress(it)
            },
            onValueChangeFinished = onScrubEnd,
        )
    }
}

@Composable
private fun TransportRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = PrompterSession::togglePlay,
            modifier = Modifier.weight(1.25f),
            contentPadding = tightPadding(),
        ) {
            Text(
                text =
                    when {
                        PrompterSession.countdownRemaining > 0 -> "Cancel"
                        PrompterSession.isAtEnd -> "Replay"
                        PrompterSession.isPlaying -> "Pause"
                        else -> "Play"
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        OutlinedButton(
            onClick = { PrompterSession.seekBySeconds(-SEEK_SECONDS) },
            modifier = Modifier.weight(1f),
            contentPadding = tightPadding(),
        ) {
            Text("−5s", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = { PrompterSession.seekBySeconds(SEEK_SECONDS) },
            modifier = Modifier.weight(1f),
            contentPadding = tightPadding(),
        ) {
            Text("+5s", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = PrompterSession::restart,
            modifier = Modifier.weight(1f),
            contentPadding = tightPadding(),
        ) {
            Text("Restart", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PlaybackSettings(wide: Boolean) {
    val left: @Composable () -> Unit = {
        ScrubControl(
            label = "Speed",
            valueText = "${PrompterSession.speedWpm.roundToInt()} WPM",
            detail = speedDetail(),
            value = PrompterSession.speedWpm,
            valueRange = StageSettings.MIN_WPM..StageSettings.MAX_WPM,
            step = 5f,
            defaultValue = 140f,
            onValueChange = PrompterSession::updateSpeedWpm,
        )
        SpeedPresets()
    }
    val right: @Composable () -> Unit = {
        ChoiceRow(
            label = "Countdown",
            options = listOf("Off", "3s", "5s"),
            selectedIndex =
                when (PrompterSession.countdownSeconds) {
                    0 -> 0
                    3 -> 1
                    else -> 2
                },
            onSelect = { index ->
                PrompterSession.updateCountdownSeconds(
                    when (index) {
                        0 -> 0
                        1 -> 3
                        else -> 5
                    },
                )
            },
        )
        ToggleRow(
            label = "Loop at end",
            checked = PrompterSession.loopEnabled,
            onCheckedChange = PrompterSession::updateLoopEnabled,
        )
        ToggleRow(
            label = "Keep screen awake",
            checked = PrompterSession.keepAwakeEnabled,
            onCheckedChange = PrompterSession::updateKeepAwakeEnabled,
        )
    }
    TwoColumn(wide = wide, left = left, right = right)
}

@Composable
private fun SpeedPresets() {
    val presets = listOf(100f, 120f, 140f, 160f, 180f, 220f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEach { preset ->
                val selected = (PrompterSession.speedWpm - preset).absoluteValue < 1f
                val chipPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                val chipText =
                    @Composable {
                        Text(
                            preset.roundToInt().toString(),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                if (selected) {
                    Button(
                        onClick = { PrompterSession.updateSpeedWpm(preset) },
                        modifier = Modifier.weight(1f),
                        contentPadding = chipPadding,
                    ) {
                        chipText()
                    }
                } else {
                    OutlinedButton(
                        onClick = { PrompterSession.updateSpeedWpm(preset) },
                        modifier = Modifier.weight(1f),
                        contentPadding = chipPadding,
                    ) {
                        chipText()
                    }
                }
            }
        }
    }
}

private fun fontsDirFiles(context: android.content.Context): List<java.io.File> {
    val dir = java.io.File(context.filesDir, "fonts")
    val all = dir.listFiles() ?: return emptyList()
    return all
        .filter { it.extension.equals("ttf", true) || it.extension.equals("otf", true) }
        .sortedBy { it.name.lowercase() }
}

@Composable
private fun FontPickerRow() {
    val context = LocalContext.current
    var installedFonts by remember { mutableStateOf(fontsDirFiles(context)) }
    val chosen = PrompterSession.stageFont
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            runCatching {
                val displayName =
                    runCatching {
                        context.contentResolver
                            .query(
                                uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME),
                                null,
                                null,
                                null,
                            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    }.getOrNull() ?: uri.lastPathSegment ?: "custom-font"
                val stream = context.contentResolver.openInputStream(uri)
                val stored =
                    stream?.use { input -> StageTypefaces.importFont(input, displayName) }
                if (stored != null) {
                    PrompterSession.updateStageCustomFont(stored)
                }
            }
            installedFonts = fontsDirFiles(context)
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Font",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val builtIns =
                listOf(
                    StageFont.SANS to "Sans",
                    StageFont.SERIF to "Serif",
                    StageFont.MONO to "Mono",
                    StageFont.SANS_LIGHT to "Light",
                    StageFont.SANS_MEDIUM to "Medium",
                    StageFont.SANS_CONDENSED to "Condensed",
                    StageFont.SANS_SMALLCAPS to "Smallcaps",
                    StageFont.SERIF_MEDIUM to "Serif Med",
                    StageFont.CURSIVE to "Cursive",
                    StageFont.CASUAL to "Casual",
                )
            builtIns.forEach { (font, label) ->
                val selected = chosen == font
                FontChoiceButton(label, selected) { PrompterSession.updateStageFont(font) }
            }
            installedFonts.forEach { file ->
                val fileName = file.name
                val name = StageTypefaces.displayName(fileName)
                val selected = chosen == StageFont.CUSTOM && PrompterSession.customFontFile == fileName
                Row(
                    modifier = Modifier.border(if (selected) 2.dp else 1.dp, Neo.Ink),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FontChoiceButton(name, selected) {
                        PrompterSession.updateStageCustomFont(fileName)
                    }
                    IconButton(
                        onClick = {
                            if (StageTypefaces.deleteFont(fileName) &&
                                PrompterSession.customFontFile == fileName
                            ) {
                                PrompterSession.updateStageCustomFont(null)
                            }
                            installedFonts = fontsDirFiles(context)
                        },
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove font $name",
                            tint = Neo.Ink,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "font/ttf",
                            "application/x-font-ttf",
                            "font/otf",
                            "application/vnd.ms-opentype",
                            "application/x-font",
                        ),
                    )
                },
                contentPadding = compactPadding(),
            ) {
                Text("+ Add font", maxLines = 1)
            }
        }
    }
}

@Composable
private fun FontChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            contentPadding = compactPadding(),
        ) {
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = compactPadding(),
        ) {
            Text(label, maxLines = 1)
        }
    }
}

@Composable
private fun TextSettings(wide: Boolean) {
    val left: @Composable () -> Unit = {
        ScrubControl(
            label = "Text size",
            valueText = "${PrompterSession.fontSizeSp.roundToInt()} sp",
            value = PrompterSession.fontSizeSp,
            valueRange = StageSettings.MIN_FONT_SP..StageSettings.MAX_FONT_SP,
            step = 2f,
            defaultValue = 46f,
            onValueChange = PrompterSession::updateFontSizeSp,
        )
        ScrubControl(
            label = "Line spacing",
            valueText = String.format(Locale.US, "%.2f×", PrompterSession.lineSpacing),
            value = PrompterSession.lineSpacing,
            valueRange = StageSettings.MIN_LINE_SPACING..StageSettings.MAX_LINE_SPACING,
            step = 0.05f,
            defaultValue = 1.34f,
            onValueChange = PrompterSession::updateLineSpacing,
            decimals = 2,
        )
        ScrubControl(
            label = "Side margins",
            valueText = "${PrompterSession.marginDp.roundToInt()} dp",
            value = PrompterSession.marginDp,
            valueRange = StageSettings.MIN_MARGIN_DP..StageSettings.MAX_MARGIN_DP,
            step = 2f,
            defaultValue = 28f,
            onValueChange = PrompterSession::updateMarginDp,
        )
    }
    val right: @Composable () -> Unit = {
        FontPickerRow()
        ChoiceRow(
            label = "Weight",
            options = listOf("Regular", "Medium", "Bold"),
            selectedIndex =
                when (PrompterSession.stageWeight) {
                    StageWeight.REGULAR -> 0
                    StageWeight.MEDIUM -> 1
                    StageWeight.BOLD -> 2
                },
            onSelect = { index ->
                PrompterSession.updateStageWeight(
                    when (index) {
                        0 -> StageWeight.REGULAR
                        1 -> StageWeight.MEDIUM
                        else -> StageWeight.BOLD
                    },
                )
            },
        )
        ChoiceRow(
            label = "Alignment",
            options = listOf("Center", "Left", "Right"),
            selectedIndex =
                when (PrompterSession.stageAlignment) {
                    StageAlignment.CENTER -> 0
                    StageAlignment.LEFT -> 1
                    StageAlignment.RIGHT -> 2
                },
            onSelect = { index ->
                PrompterSession.updateStageAlignment(
                    when (index) {
                        0 -> StageAlignment.CENTER
                        1 -> StageAlignment.LEFT
                        else -> StageAlignment.RIGHT
                    },
                )
            },
        )
    }
    TwoColumn(wide = wide, left = left, right = right)
}

@Composable
private fun LookSettings(wide: Boolean) {
    var showTextPicker by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    val left: @Composable () -> Unit = {
        ColorPicker(
            label = "Text color",
            options =
                StageTextColor.entries.filter {
                    it != StageTextColor.CUSTOM &&
                        isReadableTextColorOn(it, PrompterSession.backgroundPreset)
                },
            selected = PrompterSession.textColorPreset,
            labelFor = { it.displayName() },
            colorFor = { Color(it.argb) },
            onSelect = PrompterSession::updateTextColorPreset,
            customSelected = PrompterSession.textColorPreset == StageTextColor.CUSTOM,
            customColor = Color(PrompterSession.customTextArgb),
            onCustom = { showTextPicker = true },
        )
        ColorPicker(
            label = "Background",
            options =
                StageBackground.entries.filter {
                    it != StageBackground.CUSTOM &&
                        isReadableTextColorOn(PrompterSession.textColorPreset, it)
                },
            selected = PrompterSession.backgroundPreset,
            labelFor = { it.displayName() },
            colorFor = { Color(it.argb) },
            onSelect = PrompterSession::updateBackgroundPreset,
            customSelected = PrompterSession.backgroundPreset == StageBackground.CUSTOM,
            customColor = Color(PrompterSession.customBackgroundArgb),
            onCustom = { showBackgroundPicker = true },
        )
    }
    val right: @Composable () -> Unit = {
        ScrubControl(
            label = "Focus line",
            valueText = "${(PrompterSession.focusFraction * 100f).roundToInt()}%",
            value = PrompterSession.focusFraction,
            valueRange = StageSettings.MIN_FOCUS..StageSettings.MAX_FOCUS,
            step = 0.01f,
            defaultValue = 0.3f,
            onValueChange = PrompterSession::updateFocusFraction,
            percentValue = true,
        )
        ScrubControl(
            label = "Edge fade",
            valueText = "${((1f - PrompterSession.gradientEdgeAlpha) * 100f).roundToInt()}%",
            value = 1f - PrompterSession.gradientEdgeAlpha,
            valueRange = 0f..1f,
            step = 0.05f,
            defaultValue = 0.72f,
            onValueChange = { PrompterSession.updateGradientEdgeAlpha(1f - it) },
            percentValue = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToggleButton(
                label = "Mirror ↔",
                selected = PrompterSession.mirrorHorizontal,
                onClick = PrompterSession::toggleMirrorHorizontal,
                modifier = Modifier.weight(1f),
            )
            ToggleButton(
                label = "Mirror ↕",
                selected = PrompterSession.mirrorVertical,
                onClick = PrompterSession::toggleMirrorVertical,
                modifier = Modifier.weight(1f),
            )
        }
        ToggleRow(
            label = "Adaptive contrast",
            checked = PrompterSession.adaptiveContrastEnabled,
            onCheckedChange = PrompterSession::updateAdaptiveContrastEnabled,
        )
    }
    TwoColumn(wide = wide, left = left, right = right)
    if (showTextPicker) {
        CustomColourDialog(
            title = "Text colour",
            initialColor = PrompterSession.customTextArgb,
            onColorChanged = PrompterSession::updateCustomTextColor,
            onDismissRequest = { showTextPicker = false },
        )
    }
    if (showBackgroundPicker) {
        CustomColourDialog(
            title = "Background colour",
            initialColor = PrompterSession.customBackgroundArgb,
            onColorChanged = PrompterSession::updateCustomBackgroundColor,
            onDismissRequest = { showBackgroundPicker = false },
        )
    }
}

@Composable
private fun WindowSettings(
    wide: Boolean,
    pipSupported: Boolean,
    overlayRunning: Boolean,
    onExit: () -> Unit,
    onEnterPip: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    val left: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onEnterPip,
                modifier = Modifier.weight(1f),
                enabled = pipSupported,
                contentPadding = compactPadding(),
            ) {
                Text("Picture-in-picture", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = if (overlayRunning) onStopOverlay else onStartOverlay,
                modifier = Modifier.weight(1f),
                contentPadding = compactPadding(),
            ) {
                Text(
                    text = if (overlayRunning) "Stop overlay" else "Transparent overlay",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ToggleRow(
            label = "Keep screen awake",
            checked = PrompterSession.keepAwakeEnabled,
            onCheckedChange = PrompterSession::updateKeepAwakeEnabled,
        )
    }
    val right: @Composable () -> Unit = {
        Text(
            text =
                if (overlayRunning) {
                    "Overlay is live. Open your camera app; touches pass through the script."
                } else {
                    "Overlay keeps the script above another app. Picture-in-picture is the " +
                        "fallback when overlays are blocked."
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    PrompterSession.restart()
                    PrompterSession.setControlLocked(false)
                },
                modifier = Modifier.weight(1f),
                contentPadding = compactPadding(),
            ) {
                Text("Reset position", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.weight(1f),
                contentPadding = compactPadding(),
            ) {
                Text("Exit stage", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TwoColumn(wide = wide, left = left, right = right)
        OverlayAreaEditor(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TwoColumn(
    wide: Boolean,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                left()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                right()
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            left()
            right()
        }
    }
}

@Composable
private fun ScrubControl(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    detail: String? = null,
    decimals: Int = 0,
    percentValue: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    var showInput by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val change: (Float) -> Unit = { raw ->
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        val quantized =
            ((clamped / step).roundToInt() * step).coerceIn(
                valueRange.start,
                valueRange.endInclusive,
            )
        onValueChange(quantized)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            inputText =
                                if (percentValue) {
                                    (value * 100f).roundToInt().toString()
                                } else {
                                    String.format(Locale.US, "%.${decimals}f", value)
                                }
                            showInput = true
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onValueChange(defaultValue)
                        },
                    ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    change(value - step)
                },
                modifier = Modifier.size(width = 48.dp, height = 42.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = value,
                onValueChange = change,
                valueRange = valueRange,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    change(value + step)
                },
                modifier = Modifier.size(width = 48.dp, height = 42.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showInput) {
        AlertDialog(
            onDismissRequest = { showInput = false },
            title = { Text("Set $label") },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                    suffix = { Text(if (percentValue) "%" else "") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        inputText.toFloatOrNull()?.let { parsed ->
                            change(if (percentValue) parsed / 100f else parsed)
                        }
                        showInput = false
                    },
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInput = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    label = {
                        Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ToggleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = compactPadding()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = compactPadding()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun <T> ColorPicker(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    colorFor: (T) -> Color,
    onSelect: (T) -> Unit,
    customSelected: Boolean = false,
    customColor: Color? = null,
    onCustom: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val optionLabel = labelFor(option)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colorFor(option))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    shape = CircleShape,
                                ).semantics {
                                    contentDescription = optionLabel
                                    this.selected = isSelected
                                }.clickable { onSelect(option) },
                    )
                    Text(
                        text = optionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (customColor != null && onCustom != null) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(customColor)
                                .border(
                                    width = if (customSelected) 3.dp else 1.dp,
                                    color =
                                        if (customSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    shape = CircleShape,
                                ).semantics {
                                    contentDescription = "Custom colour"
                                    this.selected = customSelected
                                }.clickable { onCustom() },
                    )
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (customSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedBadge(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .border(3.dp, Neo.Ink)
                .semantics {
                    contentDescription = "Locked. Long press anywhere to unlock."
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Unlock") {
                                onUnlock()
                                true
                            },
                        )
                }.combinedClickable(
                    onClick = { },
                    onLongClick = onUnlock,
                ),
        color = Neo.Yellow,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "Locked · hold anywhere to unlock",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Neo.Ink,
        )
    }
}

@Composable
private fun CountdownOverlay(
    remaining: Int,
    onCancel: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remaining.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = Neo.Yellow,
            )
            Text(
                text = "Tap to cancel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EndOverlay(
    onReplay: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Neo.White,
            shape = MaterialTheme.shapes.large,
            modifier =
                Modifier
                    .widthIn(max = 360.dp)
                    .padding(24.dp)
                    .border(3.dp, Neo.Ink),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "End of script",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Finished in ${formatClock(PrompterSession.totalSeconds)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onReplay,
                        modifier = Modifier.weight(1f),
                        contentPadding = compactPadding(),
                    ) {
                        Text("Replay")
                    }
                    OutlinedButton(
                        onClick = onExit,
                        modifier = Modifier.weight(1f),
                        contentPadding = compactPadding(),
                    ) {
                        Text("Exit")
                    }
                }
            }
        }
    }
}

private fun StageTextColor.displayName(): String =
    when (this) {
        StageTextColor.WARM_WHITE -> "Warm"
        StageTextColor.WHITE -> "White"
        StageTextColor.AMBER -> "Amber"
        StageTextColor.MINT -> "Mint"
        StageTextColor.INK -> "Ink"
        StageTextColor.CUSTOM -> "Custom"
    }

private fun StageBackground.displayName(): String =
    when (this) {
        StageBackground.BLACK -> "Black"
        StageBackground.CHARCOAL -> "Charcoal"
        StageBackground.NAVY -> "Navy"
        StageBackground.FOREST -> "Forest"
        StageBackground.PAPER -> "Paper"
        StageBackground.CUSTOM -> "Custom"
    }

private fun speedDetail(): String {
    val total = PrompterSession.totalSeconds.coerceAtLeast(1)
    val pxPerSecond =
        if (PrompterSession.contentHeightPx > 0f) {
            PrompterSession.contentHeightPx / total
        } else {
            0f
        }
    return "${pxPerSecond.roundToInt()} px/s · ${formatClock(total)} total"
}

private fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3_600
    val minutes = (safe % 3_600) / 60
    val remainder = safe % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, remainder)
    }
}

private fun compactPadding(): PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

private fun tightPadding(): PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

private const val SEEK_SECONDS = 5f

@Composable
private fun CustomColourDialog(
    title: String,
    initialColor: Int,
    onColorChanged: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val initialHsv =
        remember(initialColor) {
            FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
        }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val argb =
        remember(hue, saturation, value) {
            android.graphics.Color.HSVToColor(255, floatArrayOf(hue, saturation, value))
        }
    val presets =
        listOf(
            0xFFFF0000.toInt(),
            0xFFFF7F00.toInt(),
            0xFFFFD700.toInt(),
            0xFF7FFF00.toInt(),
            0xFF00C2C8.toInt(),
            0xFF0000FF.toInt(),
            0xFF7F00FF.toInt(),
            0xFFFF00FF.toInt(),
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFF5F5DC.toInt(),
            0xFF101216.toInt(),
        )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Neo.White,
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Done") }
        },
        title = { Text(text = title.uppercase(), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(Color(argb))
                            .border(3.dp, Neo.Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = String.format("#%06X", 0xFFFFFF and argb),
                        fontWeight = FontWeight.Black,
                        color =
                            if ((
                                    (argb shr 16 and 0xFF) * 0.2126f +
                                        (argb shr 8 and 0xFF) * 0.7152f +
                                        (argb and 0xFF) * 0.0722f
                                ) > 140f
                            ) {
                                Color.Black
                            } else {
                                Color.White
                            },
                    )
                }
                Text(
                    text = "HUE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = hue,
                    onValueChange = { newHue ->
                        hue = newHue
                        onColorChanged(android.graphics.Color.HSVToColor(255, floatArrayOf(newHue, saturation, value)))
                    },
                    valueRange = 0f..360f,
                )
                Text(
                    text = "SATURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = saturation,
                    onValueChange = { newSat ->
                        saturation = newSat
                        onColorChanged(android.graphics.Color.HSVToColor(255, floatArrayOf(hue, newSat, value)))
                    },
                    valueRange = 0f..1f,
                )
                Text(
                    text = "VALUE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = value,
                    onValueChange = { newValue ->
                        value = newValue
                        onColorChanged(android.graphics.Color.HSVToColor(255, floatArrayOf(hue, saturation, newValue)))
                    },
                    valueRange = 0f..1f,
                )
                Text(
                    text = "PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.take(6).forEach { preset ->
                        Box(
                            modifier =
                                Modifier
                                    .size(26.dp)
                                    .background(Color(preset))
                                    .border(2.dp, Neo.Ink)
                                    .clickable {
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(preset, hsv)
                                        hue = hsv[0]
                                        saturation = hsv[1]
                                        value = hsv[2]
                                        onColorChanged(preset)
                                    },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.drop(6).forEach { preset ->
                        Box(
                            modifier =
                                Modifier
                                    .size(26.dp)
                                    .background(Color(preset))
                                    .border(2.dp, Neo.Ink)
                                    .clickable {
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(preset, hsv)
                                        hue = hsv[0]
                                        saturation = hsv[1]
                                        value = hsv[2]
                                        onColorChanged(preset)
                                    },
                        )
                    }
                }
            }
        },
    )
}
