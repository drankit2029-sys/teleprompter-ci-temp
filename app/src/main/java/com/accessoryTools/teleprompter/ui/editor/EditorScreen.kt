package com.accessoryTools.teleprompter.ui.editor

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accessoryTools.teleprompter.data.Script
import com.accessoryTools.teleprompter.data.formatDuration
import com.accessoryTools.teleprompter.data.readingSeconds
import com.accessoryTools.teleprompter.data.wordCount
import com.accessoryTools.teleprompter.prompter.PrompterSession
import com.accessoryTools.teleprompter.ui.Neo
import com.accessoryTools.teleprompter.ui.NeoPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EditorWideBreakpoint = 900.dp
private val EditorMaxWidth = 1320.dp
private const val MAX_HISTORY = 100
private const val MAX_HISTORY_BYTES = 2_000_000
private const val MAX_MATCH_COUNT = 999

private fun trimmedHistory(list: List<String>): List<String> {
    var bytes = list.sumOf { it.length }
    var trimmed = list
    while (trimmed.size > MAX_HISTORY || bytes > MAX_HISTORY_BYTES) {
        bytes -= trimmed.first().length
        trimmed = trimmed.drop(1)
    }
    return trimmed
}

private enum class SaveStatus(
    val label: String,
) {
    SAVED("Saved"),
    SAVING("Saving…"),
    ERROR("Save failed"),
}

@Composable
fun EditorScreen(
    script: Script,
    onBack: suspend (title: String, content: String) -> Unit,
    onAutosave: suspend (title: String, content: String) -> Unit,
    onPresent: suspend (title: String, content: String) -> Unit,
    onShare: (title: String, content: String) -> Unit,
    onExport: suspend (title: String, content: String, markdown: Boolean, uri: Uri) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var title by remember(script.id) { mutableStateOf(script.title) }
    var content by remember(script.id) { mutableStateOf(script.content) }
    var undoStack by remember(script.id) { mutableStateOf<List<String>>(emptyList()) }
    var redoStack by remember(script.id) { mutableStateOf<List<String>>(emptyList()) }
    var saveStatus by remember(script.id) { mutableStateOf(SaveStatus.SAVED) }
    var initialized by remember(script.id) { mutableStateOf(false) }
    var leaving by remember(script.id) { mutableStateOf(false) }
    var showFind by rememberSaveable(script.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun export(
        markdown: Boolean,
        uri: Uri,
    ) {
        if (uri == Uri.EMPTY) {
            return
        }
        scope.launch {
            runCatching { onExport(title, content, markdown, uri) }
                .onSuccess { snackbarHostState.showSnackbar("Script exported") }
                .onFailure { snackbarHostState.showSnackbar("Export failed: ${it.message ?: "unknown error"}") }
        }
    }

    val exportMarkdownLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
            uri?.let { export(markdown = true, it) }
        }
    val exportTextLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { export(markdown = false, it) }
        }

    var lastUndoPushMillis by remember { mutableStateOf(0L) }

    fun applyEdit(newContent: String) {
        if (newContent == content) {
            return
        }
        val now = SystemClock.uptimeMillis()
        val endsWord =
            newContent.isNotEmpty() &&
                newContent.last().isWhitespace() &&
                (content.isEmpty() || !content.last().isWhitespace())
        if (endsWord || now - lastUndoPushMillis >= 350L) {
            undoStack = trimmedHistory(undoStack + content)
            lastUndoPushMillis = now
        }
        redoStack = emptyList()
        content = newContent
    }

    fun undo() {
        val previous = undoStack.lastOrNull() ?: return
        redoStack = trimmedHistory(redoStack + content)
        undoStack = undoStack.dropLast(1)
        content = previous
    }

    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = trimmedHistory(undoStack + content)
        redoStack = redoStack.dropLast(1)
        content = next
    }

    fun saveAndBack() {
        if (leaving) {
            return
        }
        scope.launch {
            leaving = true
            runCatching { onBack(title, content) }
                .onFailure {
                    leaving = false
                    saveStatus = SaveStatus.ERROR
                    snackbarHostState.showSnackbar("Could not save the script.")
                }
        }
    }

    fun saveAndPresent() {
        if (leaving) {
            return
        }
        scope.launch {
            leaving = true
            runCatching { onPresent(title, content) }
                .onFailure {
                    leaving = false
                    saveStatus = SaveStatus.ERROR
                    snackbarHostState.showSnackbar("Could not prepare the prompter.")
                }
        }
    }

    BackHandler(onBack = ::saveAndBack)

    LaunchedEffect(title, content) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        saveStatus = SaveStatus.SAVING
        delay(600)
        runCatching { onAutosave(title, content) }
            .onSuccess { saveStatus = SaveStatus.SAVED }
            .onFailure { saveStatus = SaveStatus.ERROR }
    }

    val words = remember(content) { wordCount(content) }
    val characters = content.length
    val duration = formatDuration(readingSeconds(words, PrompterSession.speedWpm))

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EditorTopBar(
                saveStatus = saveStatus,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onBack = ::saveAndBack,
                onUndo = ::undo,
                onRedo = ::redo,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onFind = {
                    menuOpen = false
                    showFind = !showFind
                },
                onShare = {
                    menuOpen = false
                    onShare(title, content)
                },
                onExportMarkdown = {
                    menuOpen = false
                    exportMarkdownLauncher.launch("${safeFileName(title)}.md")
                },
                onExportText = {
                    menuOpen = false
                    exportTextLauncher.launch("${safeFileName(title)}.txt")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        bottomBar = {
            EditorBottomBar(
                saveStatus = saveStatus,
                words = words,
                duration = duration,
                leaving = leaving,
                onPresent = ::saveAndPresent,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = EditorMaxWidth),
            ) {
                val wide = maxWidth >= EditorWideBreakpoint
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        EditorColumn(
                            title = title,
                            onTitleChange = { title = it },
                            content = content,
                            onContentChange = ::applyEdit,
                            modifier = Modifier.weight(1.7f).fillMaxSize(),
                        )
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            StatsCard(words = words, characters = characters, duration = duration)
                            FindReplaceCard(
                                findText = rememberSaveable(script.id) { mutableStateOf("") },
                                replaceText = rememberSaveable(script.id) { mutableStateOf("") },
                                caseSensitive = rememberSaveable(script.id) { mutableStateOf(false) },
                                content = content,
                                onContentChange = ::applyEdit,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EditorColumn(
                            title = title,
                            onTitleChange = { title = it },
                            content = content,
                            onContentChange = ::applyEdit,
                            modifier = Modifier.weight(1f),
                        )
                        if (showFind) {
                            FindReplaceCard(
                                findText = rememberSaveable(script.id) { mutableStateOf("") },
                                replaceText = rememberSaveable(script.id) { mutableStateOf("") },
                                caseSensitive = rememberSaveable(script.id) { mutableStateOf(false) },
                                content = content,
                                onContentChange = ::applyEdit,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    saveStatus: SaveStatus,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.border(3.dp, Neo.Ink),
        color = Neo.Yellow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Neo.Paper).border(3.dp, Neo.Ink),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Neo.Ink)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = "EDIT SCRIPT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Neo.Ink,
                )
                Text(
                    text = saveStatus.label,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (saveStatus == SaveStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Neo.Ink.copy(alpha = 0.72f)
                        },
                )
            }
            TextButton(onClick = onUndo, enabled = canUndo) { Text("Undo") }
            TextButton(onClick = onRedo, enabled = canRedo) { Text("Redo") }
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(if (menuOpen) "Find and replace" else "Find and replace") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        onClick = onFind,
                    )
                    DropdownMenuItem(
                        text = { Text("Share text") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = onShare,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    DropdownMenuItem(
                        text = { Text("Export Markdown") },
                        onClick = onExportMarkdown,
                    )
                    DropdownMenuItem(
                        text = { Text("Export plain text") },
                        onClick = onExportText,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorBottomBar(
    saveStatus: SaveStatus,
    words: Int,
    duration: String,
    leaving: Boolean,
    onPresent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.border(3.dp, Neo.Ink),
        color = Neo.White,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = saveStatus.label,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (saveStatus == SaveStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Text(
                    text = "$words words · $duration at ${PrompterSession.speedWpm.toInt()} wpm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onPresent, enabled = !leaving) {
                if (leaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Present")
                }
            }
        }
    }
}

@Composable
private fun EditorColumn(
    title: String,
    onTitleChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Title") },
            textStyle = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("Script") },
            placeholder = { Text("Write or paste your script here…") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

@Composable
private fun StatsCard(
    words: Int,
    characters: Int,
    duration: String,
) {
    NeoPanel(
        modifier = Modifier.fillMaxWidth(),
        color = Neo.White,
        contentPadding = 18.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SCRIPT STATISTICS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
            StatRow("Words", words.toString())
            StatRow("Characters", characters.toString())
            StatRow("Read time", duration)
            StatRow("Pace", "${PrompterSession.speedWpm.toInt()} wpm")
            HorizontalDivider(color = Neo.Ink)
            Text(
                text = "Autosave keeps changes on this device. Present saves before opening the prompter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun safeFileName(title: String): String =
    title
        .trim()
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .replace(Regex("\\s+"), " ")
        .take(60)
        .ifBlank { "script" }

@Composable
private fun FindReplaceCard(
    findText: androidx.compose.runtime.MutableState<String>,
    replaceText: androidx.compose.runtime.MutableState<String>,
    caseSensitive: androidx.compose.runtime.MutableState<Boolean>,
    content: String,
    onContentChange: (String) -> Unit,
) {
    val find = findText.value
    val replacement = replaceText.value
    val options =
        if (caseSensitive.value) {
            emptySet()
        } else {
            setOf(RegexOption.IGNORE_CASE)
        }
    val regex =
        remember(find, caseSensitive.value) {
            if (find.isEmpty()) null else Regex(Regex.escape(find), options)
        }
    // Cap counting work so a short pattern on a huge document can't block the UI per keystroke.
    val matchCount = remember(regex, content) { regex?.findAll(content)?.take(MAX_MATCH_COUNT + 1)?.count() ?: 0 }
    val matchLabel = if (matchCount > MAX_MATCH_COUNT) "$MAX_MATCH_COUNT+" else matchCount.toString()

    fun replaceFirst() {
        val match = regex?.find(content) ?: return
        onContentChange(content.replaceRange(match.range, replacement))
    }

    fun replaceAll() {
        val current = regex ?: return
        onContentChange(current.replace(content) { replacement })
    }

    NeoPanel(
        modifier = Modifier.fillMaxWidth(),
        color = Neo.White,
        contentPadding = 18.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FIND AND REPLACE",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "$matchLabel matches",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = find,
                onValueChange = { findText.value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Find") },
            )
            OutlinedTextField(
                value = replacement,
                onValueChange = { replaceText.value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Replace with") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = caseSensitive.value,
                    onCheckedChange = { caseSensitive.value = it },
                )
                Text("Match case", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ::replaceFirst, enabled = matchCount > 0) {
                    Text("Replace")
                }
                OutlinedButton(onClick = ::replaceAll, enabled = matchCount > 0) {
                    Text("Replace all")
                }
            }
        }
    }
}
