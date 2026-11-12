package com.accessoryTools.teleprompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.accessoryTools.teleprompter.prompter.PrompterStage
import java.util.Locale
import kotlin.math.roundToInt

private val WideChromeBreakpoint = 600.dp
private val WideChromeMaxWidth = 1100.dp

@Composable
fun Phase0StageScreen(
    isInPip: Boolean,
    pipSupported: Boolean,
    overlayRunning: Boolean,
    onExit: () -> Unit,
    onEnterPip: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    var showChrome by remember { mutableStateOf(true) }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        val wide = maxWidth >= WideChromeBreakpoint
        PrompterStage(
            modifier = Modifier.fillMaxSize(),
            compact = isInPip,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (!isInPip) {
                            showChrome = !showChrome
                        }
                    },
        )
        if (showChrome && !isInPip) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(
                            horizontal = if (wide) 24.dp else 12.dp,
                            vertical = 12.dp,
                        ),
            ) {
                TopChrome(
                    onExit = onExit,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = WideChromeMaxWidth)
                            .fillMaxWidth(),
                )
                Spacer(modifier = Modifier.weight(1f))
                BottomChrome(
                    wide = wide,
                    pipSupported = pipSupported,
                    overlayRunning = overlayRunning,
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
}

@Composable
private fun TopChrome(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF121419),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Teleprompter",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "${PrompterSession.speedWpm.roundToInt()} wpm  ·  " +
                            "${(PrompterSession.progress * 100).roundToInt()}%  ·  " +
                            "${PrompterSession.remainingMinutes} min left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onExit) {
                Text("Exit")
            }
        }
    }
}

@Composable
private fun BottomChrome(
    wide: Boolean,
    pipSupported: Boolean,
    overlayRunning: Boolean,
    onEnterPip: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF121419),
        shape = RoundedCornerShape(if (wide) 26.dp else 22.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (wide) 18.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 8.dp),
        ) {
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1.3f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProgressBlock()
                        PlaybackRow(
                            pipSupported = pipSupported,
                            overlayRunning = overlayRunning,
                            onEnterPip = onEnterPip,
                            onStartOverlay = onStartOverlay,
                            onStopOverlay = onStopOverlay,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SpeedRow()
                        TextSizeRow()
                        StatusRow()
                    }
                }
            } else {
                ProgressBlock()
                PlaybackRow(
                    pipSupported = pipSupported,
                    overlayRunning = overlayRunning,
                    onEnterPip = onEnterPip,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                )
                CompactAdjustRow()
                StatusRow()
            }
            if (overlayRunning) {
                Text(
                    text =
                        "Overlay is live. Open your camera app; touches pass through " +
                            "the script and the floating bar remains available.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun ProgressBlock() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Position",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${PrompterSession.offsetPx.roundToInt()} px",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = PrompterSession.progress,
            onValueChange = PrompterSession::seekToProgress,
        )
    }
}

@Composable
private fun PlaybackRow(
    pipSupported: Boolean,
    overlayRunning: Boolean,
    onEnterPip: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = PrompterSession::togglePlay,
            modifier = Modifier.weight(1.15f),
            contentPadding = compactPadding(),
        ) {
            Text(
                text =
                    when {
                        PrompterSession.isAtEnd -> "Replay"
                        PrompterSession.isPlaying -> "Pause"
                        else -> "Play"
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = PrompterSession::restart,
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("Restart", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = onEnterPip,
            modifier = Modifier.weight(0.85f),
            enabled = pipSupported,
            contentPadding = compactPadding(),
        ) {
            Text("PiP", maxLines = 1)
        }
        OutlinedButton(
            onClick = if (overlayRunning) onStopOverlay else onStartOverlay,
            modifier = Modifier.weight(1.25f),
            contentPadding = compactPadding(),
        ) {
            Text(
                text = if (overlayRunning) "Stop" else "Overlay",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SpeedRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { PrompterSession.nudgeSpeed(-10f) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("−10")
        }
        OutlinedButton(
            onClick = { PrompterSession.nudgeSpeed(10f) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("+10")
        }
        Text(
            text = "${PrompterSession.speedWpm.roundToInt()} wpm",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun TextSizeRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Text",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { PrompterSession.nudgeFontSize(-2f) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("A−")
        }
        OutlinedButton(
            onClick = { PrompterSession.nudgeFontSize(2f) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("A+")
        }
        Text(
            text = "${PrompterSession.fontSizeSp.roundToInt()} sp",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun CompactAdjustRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { PrompterSession.nudgeSpeed(-10f) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("−10")
        }
        OutlinedButton(
            onClick = { PrompterSession.nudgeSpeed(10f) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("+10")
        }
        Text(
            text = "Text",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { PrompterSession.nudgeFontSize(-2f) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("A−")
        }
        OutlinedButton(
            onClick = { PrompterSession.nudgeFontSize(2f) },
            modifier = Modifier.weight(1f),
            contentPadding = compactPadding(),
        ) {
            Text("A+")
        }
    }
}

@Composable
private fun StatusRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                String.format(
                    Locale.US,
                    "%.0f%% edge fade",
                    PrompterSession.gradientEdgeAlpha * 100f,
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Tap script to hide controls",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun compactPadding(): PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
