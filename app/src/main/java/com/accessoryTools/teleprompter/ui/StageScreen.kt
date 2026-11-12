package com.accessoryTools.teleprompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
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
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.accessoryTools.teleprompter.prompter.PrompterStage
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val WideChromeBreakpoint = 600.dp
private val WideChromeMaxWidth = 1_100.dp
private val PanelChrome = Color(0xF2121419)

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

    LaunchedEffect(locked) {
        if (locked) {
            showChrome = false
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(PrompterSession.backgroundPreset.argb)),
    ) {
        val wide = maxWidth >= WideChromeBreakpoint
        val panelMaxHeight =
            if (maxWidth > maxHeight) {
                maxHeight * 0.66f
            } else {
                maxHeight * 0.46f
            }
        PrompterStage(
            modifier = Modifier.fillMaxSize(),
            compact = isInPip,
        )
        if (!isInPip) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(PrompterSession.isPlaying, locked) {
                            if (!PrompterSession.isPlaying && !locked) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    PrompterSession.scrollByPixels(-dragAmount)
                                }
                            }
                        }.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!locked) {
                                showChrome = !showChrome
                            }
                        },
            )
            if (showChrome && !locked) {
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
        modifier = modifier,
        color = PanelChrome,
        shape = RoundedCornerShape(18.dp),
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
private fun StagePanel(
    wide: Boolean,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    panelMaxHeight: Dp,
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
        modifier = modifier.heightIn(max = panelMaxHeight),
        color = PanelChrome,
        shape = RoundedCornerShape(if (wide) 26.dp else 22.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (wide) 18.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 8.dp),
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
                            contentPadding = compactPadding(),
                        ) {
                            Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectTab(index) },
                            modifier = Modifier.weight(1f),
                            contentPadding = compactPadding(),
                        ) {
                            Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            contentPadding = compactPadding(),
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
            )
        }
        OutlinedButton(
            onClick = { PrompterSession.seekBySeconds(-SEEK_SECONDS) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("−5s", maxLines = 1)
        }
        OutlinedButton(
            onClick = { PrompterSession.seekBySeconds(SEEK_SECONDS) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("+5s", maxLines = 1)
        }
        OutlinedButton(
            onClick = PrompterSession::restart,
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("Restart", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                if (selected) {
                    Button(
                        onClick = { PrompterSession.updateSpeedWpm(preset) },
                        modifier = Modifier.weight(1f),
                        contentPadding = compactPadding(),
                    ) {
                        Text(preset.roundToInt().toString(), maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { PrompterSession.updateSpeedWpm(preset) },
                        modifier = Modifier.weight(1f),
                        contentPadding = compactPadding(),
                    ) {
                        Text(preset.roundToInt().toString(), maxLines = 1)
                    }
                }
            }
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
        ChoiceRow(
            label = "Font",
            options = listOf("Sans", "Serif", "Mono"),
            selectedIndex =
                when (PrompterSession.stageFont) {
                    StageFont.SANS -> 0
                    StageFont.SERIF -> 1
                    StageFont.MONO -> 2
                },
            onSelect = { index ->
                PrompterSession.updateStageFont(
                    when (index) {
                        0 -> StageFont.SANS
                        1 -> StageFont.SERIF
                        else -> StageFont.MONO
                    },
                )
            },
        )
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
            options = listOf("Center", "Left"),
            selectedIndex =
                when (PrompterSession.stageAlignment) {
                    StageAlignment.CENTER -> 0
                    StageAlignment.LEFT -> 1
                },
            onSelect = { index ->
                PrompterSession.updateStageAlignment(
                    if (index == 0) StageAlignment.CENTER else StageAlignment.LEFT,
                )
            },
        )
    }
    TwoColumn(wide = wide, left = left, right = right)
}

@Composable
private fun LookSettings(wide: Boolean) {
    val left: @Composable () -> Unit = {
        ColorPicker(
            label = "Text color",
            options = StageTextColor.entries.toList(),
            selected = PrompterSession.textColorPreset,
            labelFor = { it.displayName() },
            colorFor = { Color(it.argb) },
            onSelect = PrompterSession::updateTextColorPreset,
        )
        ColorPicker(
            label = "Background",
            options = StageBackground.entries.toList(),
            selected = PrompterSession.backgroundPreset,
            labelFor = { it.displayName() },
            colorFor = { Color(it.argb) },
            onSelect = PrompterSession::updateBackgroundPreset,
        )
    }
    val right: @Composable () -> Unit = {
        ScrubControl(
            label = "Focus line",
            valueText = "${(PrompterSession.focusFraction * 100f).roundToInt()}%",
            value = PrompterSession.focusFraction,
            valueRange = StageSettings.MIN_FOCUS..StageSettings.MAX_FOCUS,
            step = 0.01f,
            defaultValue = 0.5f,
            onValueChange = PrompterSession::updateFocusFraction,
            percentValue = true,
        )
        ScrubControl(
            label = "Edge fade",
            valueText = "${(PrompterSession.gradientEdgeAlpha * 100f).roundToInt()}%",
            value = PrompterSession.gradientEdgeAlpha,
            valueRange = 0f..1f,
            step = 0.05f,
            defaultValue = 0.28f,
            onValueChange = PrompterSession::updateGradientEdgeAlpha,
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
    TwoColumn(wide = wide, left = left, right = right)
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
            modifier.combinedClickable(
                onClick = { },
                onLongClick = onUnlock,
            ),
        color = Color(0xCC121419),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = "Locked · hold to unlock",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
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
            color = PanelChrome,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
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
    }

private fun StageBackground.displayName(): String =
    when (this) {
        StageBackground.BLACK -> "Black"
        StageBackground.CHARCOAL -> "Charcoal"
        StageBackground.NAVY -> "Navy"
        StageBackground.FOREST -> "Forest"
        StageBackground.PAPER -> "Paper"
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

private const val SEEK_SECONDS = 5f
