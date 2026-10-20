package com.mp3tag.mobile.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.fs.FolderScanner
import com.mp3tag.mobile.ui.common.EmptyState
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo
import java.io.File

private fun formatSelectionSummary(state: BrowserState): String {
    val files = state.selectedPaths.size
    val folders = state.selectedFolderPaths.size
    return when {
        folders > 0 && files > 0 -> "$folders folder(s), $files file(s) selected"
        folders > 0 -> "$folders folder(s) selected"
        else -> "$files selected"
    }
}

/**
 * MP3Tag-style folder view: browse directories, select files or entire folders,
 * or long-press a file/folder to open its tag editor directly.
 *
 * Adapts across form factors:
 * - Compact portrait phones: single-column with chip strips and bottom selection bar
 * - Phone landscape & tablets: two-pane split layout (left sidebar for filter, storage
 *   volumes, recents, and selection actions; right pane for full-height file list or
 *   2-column tablet grid).
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun BrowserScreen(
    onBackHome: () -> Unit,
    onEditFiles: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: BrowserViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val windowInfo = rememberAdaptiveWindowInfo()

    var filter by rememberSaveable { mutableStateOf("") }
    var menuSheetOpen by rememberSaveable { mutableStateOf(false) }
    var storageDialogOpen by rememberSaveable { mutableStateOf(false) }

    val root = state.directory
    val isAtStart = !viewModel.canNavigateUp()
    val hasSelection = state.totalSelectedCount > 0

    BackHandler {
        when {
            hasSelection -> viewModel.clearSelection()
            viewModel.canNavigateUp() -> viewModel.navigateUp()
            else -> onBackHome()
        }
    }

    val filteredDirs = remember(state.directories, filter) {
        if (filter.isBlank()) state.directories
        else state.directories.filter { it.name.contains(filter, ignoreCase = true) }
    }
    val filteredFiles = remember(state.audioFiles, filter) {
        if (filter.isBlank()) state.audioFiles
        else state.audioFiles.filter { it.name.contains(filter, ignoreCase = true) }
    }

    val triggerEditSelected = {
        viewModel.resolveSelectionForEdit(onEditFiles)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            root.name.ifEmpty { root.absolutePath },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = root.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!viewModel.navigateUp()) {
                                onBackHome()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isAtStart) {
                        IconButton(onClick = onBackHome) {
                            Icon(Icons.Filled.Home, contentDescription = "Home")
                        }
                    }
                    if (windowInfo.useTwoPane) {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        if (hasSelection) {
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { menuSheetOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                },
            )
        },
        bottomBar = {
            // In compact 1-pane mode, show the selection bar at the bottom.
            // In 2-pane mode (phone landscape / tablet), selection actions live in the left pane
            // so the file list gets 100% of the vertical height.
            AnimatedVisibility(visible = !windowInfo.useTwoPane && hasSelection) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = formatSelectionSummary(state),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = viewModel::clearSelection) { Text("Clear") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = triggerEditSelected,
                            enabled = !state.resolvingSelection,
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        ) {
            if (windowInfo.useTwoPane) {
                Row(modifier = Modifier.fillMaxSize()) {
                    BrowserSidePane(
                        state = state,
                        filter = filter,
                        onFilterChange = { filter = it },
                        onOpenVolume = viewModel::openVolume,
                        onOpenDir = { viewModel.open(it) },
                        onClearSelection = viewModel::clearSelection,
                        onEditSelected = triggerEditSelected,
                        modifier = Modifier
                            .width(windowInfo.sidePaneWidth)
                            .fillMaxHeight(),
                    )
                    VerticalDivider()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        BrowserStatusBanner(
                            message = state.statusMessage,
                            onDismiss = viewModel::dismissStatus,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            BrowserListContent(
                                state = state,
                                filteredDirs = filteredDirs,
                                filteredFiles = filteredFiles,
                                columns = windowInfo.browserListColumns,
                                onOpenDir = viewModel::open,
                                onToggleSelectDir = viewModel::toggleSelectFolder,
                                onEditDir = { dir -> viewModel.editEntireFolder(dir, onEditFiles) },
                                onToggleSelect = viewModel::toggleSelect,
                                onEditSingle = { file -> onEditFiles(listOf(file.absolutePath)) },
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    BrowserStatusBanner(
                        message = state.statusMessage,
                        onDismiss = viewModel::dismissStatus,
                    )

                    FilterSearchField(
                        filter = filter,
                        onFilterChange = { filter = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    if (state.volumes.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            items(state.volumes, key = { it.directory.absolutePath }) { volume ->
                                VolumeChip(
                                    volume = volume,
                                    isCurrentVolume = state.directory.absolutePath
                                        .startsWith(volume.directory.absolutePath),
                                    onClick = { viewModel.openVolume(volume) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }

                    if (state.recents.isNotEmpty() && isAtStart) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            items(state.recents, key = { it }) { path ->
                                AssistChip(
                                    onClick = { viewModel.open(File(path)) },
                                    label = { Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        BrowserListContent(
                            state = state,
                            filteredDirs = filteredDirs,
                            filteredFiles = filteredFiles,
                            columns = 1,
                            onOpenDir = viewModel::open,
                            onToggleSelectDir = viewModel::toggleSelectFolder,
                            onEditDir = { dir -> viewModel.editEntireFolder(dir, onEditFiles) },
                            onToggleSelect = viewModel::toggleSelect,
                            onEditSingle = { file -> onEditFiles(listOf(file.absolutePath)) },
                        )
                    }
                }
            }

            if (state.resolvingSelection) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        ) {
                            LoadingIndicator()
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Scanning folder audio files…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    if (menuSheetOpen) {
        ModalBottomSheet(onDismissRequest = { menuSheetOpen = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Browser actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                ListItem(
                    headlineContent = { Text("Edit all audio in this folder") },
                    leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        viewModel.editEntireFolder(state.directory, onEditFiles)
                    },
                )
                ListItem(
                    headlineContent = { Text("Select all") },
                    leadingContent = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        viewModel.selectAll()
                    },
                )
                ListItem(
                    headlineContent = { Text("Clear selection") },
                    leadingContent = { Icon(Icons.Filled.Close, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        viewModel.clearSelection()
                    },
                )
                ListItem(
                    headlineContent = { Text("Choose storage…") },
                    leadingContent = { Icon(Icons.Filled.SdCard, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        storageDialogOpen = true
                    },
                )
                ListItem(
                    headlineContent = { Text("Settings") },
                    leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        onOpenSettings()
                    },
                )
                ListItem(
                    headlineContent = { Text("Back to Home") },
                    leadingContent = { Icon(Icons.Filled.Home, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        onBackHome()
                    },
                )
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }

    if (storageDialogOpen) {
        AlertDialog(
            onDismissRequest = { storageDialogOpen = false },
            title = { Text("Choose storage device") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.volumes.forEach { volume ->
                        ListItem(
                            headlineContent = { Text(volume.name) },
                            supportingContent = {
                                Text(
                                    volume.directory.absolutePath,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    when {
                                        !volume.isRemovable -> Icons.Filled.Smartphone
                                        volume.name.startsWith("USB") -> Icons.Filled.Usb
                                        else -> Icons.Filled.SdCard
                                    },
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                storageDialogOpen = false
                                viewModel.openVolume(volume)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { storageDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BrowserStatusBanner(
    message: String?,
    onDismiss: () -> Unit,
) {
    if (message == null) return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowserSidePane(
    state: BrowserState,
    filter: String,
    onFilterChange: (String) -> Unit,
    onOpenVolume: (StorageVolumeInfo) -> Unit,
    onOpenDir: (File) -> Unit,
    onClearSelection: () -> Unit,
    onEditSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterSearchField(
            filter = filter,
            onFilterChange = onFilterChange,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.totalSelectedCount > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = formatSelectionSummary(state),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onEditSelected,
                            enabled = !state.resolvingSelection,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Edit")
                        }
                        TextButton(onClick = onClearSelection) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        if (state.volumes.isNotEmpty()) {
            Text(
                text = "Storage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.volumes.forEach { volume ->
                    VolumeChip(
                        volume = volume,
                        isCurrentVolume = state.directory.absolutePath
                            .startsWith(volume.directory.absolutePath),
                        onClick = { onOpenVolume(volume) },
                    )
                }
            }
        }

        if (state.recents.isNotEmpty()) {
            Text(
                text = "Recent folders",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.recents.forEach { path ->
                    AssistChip(
                        onClick = { onOpenDir(File(path)) },
                        leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = {
                            Text(
                                File(path).name.ifEmpty { path },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun FilterSearchField(
    filter: String,
    onFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = filter,
        onValueChange = onFilterChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text("Filter") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (filter.isNotEmpty()) {
                IconButton(onClick = { onFilterChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear filter")
                }
            }
        },
    )
}

@Composable
private fun VolumeChip(
    volume: StorageVolumeInfo,
    isCurrentVolume: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(volume.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                when {
                    !volume.isRemovable -> Icons.Filled.Smartphone
                    volume.name.startsWith("USB") -> Icons.Filled.Usb
                    else -> Icons.Filled.SdCard
                },
                contentDescription = null,
            )
        },
        colors = if (isCurrentVolume) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BrowserListContent(
    state: BrowserState,
    filteredDirs: List<File>,
    filteredFiles: List<File>,
    columns: Int,
    onOpenDir: (File) -> Unit,
    onToggleSelectDir: (File) -> Unit,
    onEditDir: (File) -> Unit,
    onToggleSelect: (File) -> Unit,
    onEditSingle: (File) -> Unit,
) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }

        state.error != null -> EmptyState(
            icon = Icons.Filled.ErrorOutline,
            title = "Could not read this folder",
            subtitle = state.error,
        )

        filteredDirs.isEmpty() && filteredFiles.isEmpty() -> EmptyState(
            icon = Icons.Filled.Folder,
            title = "Nothing here",
            subtitle = "No folders or supported audio files match. Supported: " +
                FolderScanner.AUDIO_EXTENSIONS.joinToString(", "),
        )

        columns <= 1 -> LazyColumn(Modifier.fillMaxSize()) {
            items(
                items = filteredDirs,
                key = { "dir:" + it.absolutePath },
                contentType = { "dir" },
            ) { dir ->
                val path = dir.absolutePath
                DirectoryRow(
                    dir = dir,
                    selected = path in state.selectedFolderPaths,
                    onOpen = { onOpenDir(dir) },
                    onToggleSelect = { onToggleSelectDir(dir) },
                    onEditFolder = { onEditDir(dir) },
                )
            }
            items(
                items = filteredFiles,
                key = { "file:" + it.absolutePath },
                contentType = { "file" },
            ) { file ->
                val path = file.absolutePath
                AudioFileRow(
                    file = file,
                    subtitle = state.fileSubtitles[path] ?: file.extension.uppercase(),
                    selected = path in state.selectedPaths,
                    onToggleSelect = { onToggleSelect(file) },
                    onEditSingle = { onEditSingle(file) },
                )
            }
            item { Spacer(Modifier.navigationBarsPadding().height(12.dp)) }
        }

        else -> {
            val dirRows = remember(filteredDirs, columns) { filteredDirs.chunked(columns) }
            val fileRows = remember(filteredFiles, columns) { filteredFiles.chunked(columns) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(
                    items = dirRows,
                    key = { "dirRow:" + it.first().absolutePath },
                    contentType = { "dirRow" },
                ) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { dir ->
                            val path = dir.absolutePath
                            Box(Modifier.weight(1f)) {
                                DirectoryRow(
                                    dir = dir,
                                    selected = path in state.selectedFolderPaths,
                                    onOpen = { onOpenDir(dir) },
                                    onToggleSelect = { onToggleSelectDir(dir) },
                                    onEditFolder = { onEditDir(dir) },
                                )
                            }
                        }
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                items(
                    items = fileRows,
                    key = { "fileRow:" + it.first().absolutePath },
                    contentType = { "fileRow" },
                ) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { file ->
                            val path = file.absolutePath
                            Box(Modifier.weight(1f)) {
                                AudioFileRow(
                                    file = file,
                                    subtitle = state.fileSubtitles[path] ?: file.extension.uppercase(),
                                    selected = path in state.selectedPaths,
                                    onToggleSelect = { onToggleSelect(file) },
                                    onEditSingle = { onEditSingle(file) },
                                )
                            }
                        }
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(12.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DirectoryRow(
    dir: File,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEditFolder: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = onEditFolder,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioFileRow(
    file: File,
    subtitle: String,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEditSingle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Icon(
                if (selected) Icons.Filled.MusicNote else Icons.Filled.Audiotrack,
                contentDescription = null,
            )
        },
        trailingContent = {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier.combinedClickable(
            onClick = onToggleSelect,
            onLongClick = onEditSingle,
        ),
    )
}
