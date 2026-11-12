package com.accessoryTools.teleprompter.ui.library

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accessoryTools.teleprompter.data.LibrarySort
import com.accessoryTools.teleprompter.data.Script
import com.accessoryTools.teleprompter.data.ScriptImportException
import com.accessoryTools.teleprompter.data.formatDuration
import com.accessoryTools.teleprompter.data.readingSeconds
import com.accessoryTools.teleprompter.prompter.PrompterSession
import kotlinx.coroutines.launch

private val LibraryWideBreakpoint = 760.dp
private val LibraryMaxWidth = 1240.dp
private val CardMinWidth = 320.dp

@Composable
fun LibraryScreen(
    scripts: List<Script>,
    loading: Boolean,
    initialSort: LibrarySort,
    initialFavoritesOnly: Boolean,
    onSortChanged: (LibrarySort) -> Unit,
    onFavoritesChanged: (Boolean) -> Unit,
    onNewScript: () -> Unit,
    onOpenScript: (String) -> Unit,
    onPresent: (Script) -> Unit,
    onToggleFavorite: (Script) -> Unit,
    onDuplicate: (Script) -> Unit,
    onShare: (Script) -> Unit,
    onDelete: (Script) -> Unit,
    onRestore: (String) -> Unit,
    onSettings: () -> Unit,
    onImportUris: suspend (List<Uri>) -> Int,
    onImportClipboard: suspend () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(initialSort) }
    var favoritesOnly by rememberSaveable { mutableStateOf(initialFavoritesOnly) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }

    LaunchedEffect(initialSort, initialFavoritesOnly) {
        sortOrder = initialSort
        favoritesOnly = initialFavoritesOnly
    }

    val filtered =
        remember(scripts, query, sortOrder, favoritesOnly) {
            val normalizedQuery = query.trim()
            scripts
                .asSequence()
                .filter { script ->
                    !favoritesOnly || script.favorite
                }.filter { script ->
                    normalizedQuery.isEmpty() ||
                        script.title.contains(normalizedQuery, ignoreCase = true) ||
                        script.content.contains(normalizedQuery, ignoreCase = true)
                }.sortedWith(
                    when (sortOrder) {
                        LibrarySort.UPDATED -> compareByDescending { it.updatedAt }
                        LibrarySort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                        LibrarySort.CREATED -> compareByDescending { it.createdAt }
                    },
                ).toList()
        }

    fun showError(error: Throwable) {
        val message = (error as? ScriptImportException)?.message ?: "Could not import the selected file."
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    importing = true
                    runCatching { onImportUris(uris) }
                        .onSuccess { count ->
                            snackbarHostState.showSnackbar(
                                if (count == 1) "Imported 1 script" else "Imported $count scripts",
                            )
                        }.onFailure(::showError)
                    importing = false
                }
            }
        }

    fun startImport() {
        importLauncher.launch(
            arrayOf(
                "text/*",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.oasis.opendocument.text",
                "application/rtf",
                "application/pdf",
                "text/html",
                "text/markdown",
                "application/octet-stream",
            ),
        )
    }

    fun startPaste() {
        scope.launch {
            pasting = true
            runCatching { onImportClipboard() }
                .onSuccess { imported ->
                    snackbarHostState.showSnackbar(
                        if (imported) "Pasted script from clipboard" else "Clipboard is empty",
                    )
                }.onFailure(::showError)
            pasting = false
        }
    }

    fun deleteWithUndo(script: Script) {
        onDelete(script)
        scope.launch {
            val result =
                snackbarHostState.showSnackbar(
                    message = "Deleted ${script.title}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) {
                onRestore(script.id)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewScript,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New script") },
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
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = LibraryMaxWidth),
            ) {
                val wide = maxWidth >= LibraryWideBreakpoint
                Column(modifier = Modifier.fillMaxSize()) {
                    LibraryHeader(
                        scriptCount = scripts.size,
                        onSettings = onSettings,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (wide) 28.dp else 16.dp)
                                .padding(top = 20.dp),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    LibraryToolbar(
                        query = query,
                        onQueryChange = { query = it },
                        favoritesOnly = favoritesOnly,
                        onFavoritesChange = {
                            favoritesOnly = it
                            onFavoritesChanged(it)
                        },
                        sortOrder = sortOrder,
                        sortMenuOpen = sortMenuOpen,
                        onSortMenuOpenChange = { sortMenuOpen = it },
                        onSortSelected = {
                            sortOrder = it
                            onSortChanged(it)
                            sortMenuOpen = false
                        },
                        importing = importing,
                        pasting = pasting,
                        onImport = ::startImport,
                        onPaste = ::startPaste,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (wide) 28.dp else 16.dp),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    when {
                        loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        filtered.isEmpty() -> {
                            EmptyLibrary(
                                query = query,
                                favoritesOnly = favoritesOnly,
                                hasScripts = scripts.isNotEmpty(),
                                onCreate = onNewScript,
                                onImport = ::startImport,
                                onPaste = ::startPaste,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = if (wide) 28.dp else 16.dp),
                            )
                        }

                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = CardMinWidth),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding =
                                    PaddingValues(
                                        start = if (wide) 28.dp else 16.dp,
                                        end = if (wide) 28.dp else 16.dp,
                                        top = 4.dp,
                                        bottom = 112.dp,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(filtered, key = { script -> script.id }) { script ->
                                    ScriptCard(
                                        script = script,
                                        onOpen = { onOpenScript(script.id) },
                                        onPresent = { onPresent(script) },
                                        onToggleFavorite = { onToggleFavorite(script) },
                                        onDuplicate = { onDuplicate(script) },
                                        onShare = { onShare(script) },
                                        onDelete = { deleteWithUndo(script) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    scriptCount: Int,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Teleprompter",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text =
                    when (scriptCount) {
                        0 -> "Your local script library"
                        1 -> "1 script stored on this device"
                        else -> "$scriptCount scripts stored on this device"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun LibraryToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesChange: (Boolean) -> Unit,
    sortOrder: LibrarySort,
    sortMenuOpen: Boolean,
    onSortMenuOpenChange: (Boolean) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    importing: Boolean,
    pasting: Boolean,
    onImport: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search titles and scripts") },
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { onFavoritesChange(!favoritesOnly) },
                label = { Text("Favorites") },
                leadingIcon =
                    if (favoritesOnly) {
                        {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
            )
            Box {
                TextButton(onClick = { onSortMenuOpenChange(true) }) {
                    Text(sortOrder.label)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { onSortMenuOpenChange(false) },
                ) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { onSortSelected(option) },
                        )
                    }
                }
            }
            VerticalDivider(
                modifier = Modifier.height(24.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            OutlinedButton(onClick = onImport, enabled = !importing) {
                if (importing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Import")
                }
            }
            OutlinedButton(onClick = onPaste, enabled = !pasting) {
                if (pasting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Paste")
                }
            }
        }
    }
}

@Composable
private fun ScriptCard(
    script: Script,
    onOpen: () -> Unit,
    onPresent: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val preview =
        remember(script.content) {
            script.content
                .replace('\n', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        onClick = onOpen,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = script.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata(script),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector =
                            if (script.favorite) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                        contentDescription = if (script.favorite) "Remove favorite" else "Add favorite",
                        tint =
                            if (script.favorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
            Text(
                text = preview.ifBlank { "Empty script" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPresent,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Present")
                }
                TextButton(onClick = onOpen) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(5.dp))
                    Text("Edit")
                }
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(
    query: String,
    favoritesOnly: Boolean,
    hasScripts: Boolean,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searching = query.isNotBlank() || favoritesOnly
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text =
                    when {
                        searching && hasScripts -> "No matching scripts"
                        favoritesOnly -> "No favorites yet"
                        else -> "No scripts yet"
                    },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    when {
                        searching && hasScripts -> "Try a different search or clear the filters."
                        favoritesOnly -> "Mark a script with the heart to keep it here."
                        else -> "Create a script, import a file, or paste from the clipboard."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!searching) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCreate) { Text("New script") }
                    OutlinedButton(onClick = onImport) { Text("Import") }
                    OutlinedButton(onClick = onPaste) { Text("Paste") }
                }
            }
        }
    }
}

private fun metadata(script: Script): String {
    val duration = formatDuration(readingSeconds(script.words, PrompterSession.speedWpm))
    val relative =
        DateUtils.getRelativeTimeSpanString(
            script.updatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
    return "${script.words} words · $duration · $relative"
}
