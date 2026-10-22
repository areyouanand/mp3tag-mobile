package com.mp3tag.mobile.ui.filemanager

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
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.ui.browser.StorageVolumeInfo
import com.mp3tag.mobile.ui.common.EmptyState
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo
import com.mp3tag.mobile.util.Format
import java.io.File

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov")
private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2")

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun FileManagerScreen(
    onBackHome: () -> Unit,
    onEditAudioTags: (List<String>) -> Unit,
    onAnalyzeSpectrum: ((String) -> Unit)? = null,
    pickerModeForSpectrum: Boolean = false,
    viewModel: FileManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val windowInfo = rememberAdaptiveWindowInfo()

    var filter by rememberSaveable { mutableStateOf("") }
    var menuSheetOpen by rememberSaveable { mutableStateOf(false) }
    var sortSheetOpen by rememberSaveable { mutableStateOf(false) }
    var storageDialogOpen by rememberSaveable { mutableStateOf(false) }
    var newFolderDialogOpen by rememberSaveable { mutableStateOf(false) }

    // Item-level or batch action states
    var contextEntryPath by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTargetPath by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteConfirmPaths by rememberSaveable { mutableStateOf<List<String>?>(null) }

    val filteredEntries = remember(state.entries, filter) {
        if (filter.isBlank()) state.entries
        else state.entries.filter { it.name.contains(filter, ignoreCase = true) }
    }

    val selectedEntries = remember(state.entries, state.selectedPaths) {
        state.entries.filter { it.path in state.selectedPaths }
    }
    val selectedAudioPaths = remember(selectedEntries) {
        selectedEntries.filter { it.isAudio }.map { it.path }
    }

    // Back handler: clear selection -> navigate up folder -> return to Home
    BackHandler {
        when {
            state.selectedPaths.isNotEmpty() -> viewModel.clearSelection()
            viewModel.canNavigateUp() -> viewModel.navigateUp()
            else -> onBackHome()
        }
    }

    val root = state.directory

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = root.name.ifEmpty { root.absolutePath },
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
                    if (viewModel.canNavigateUp()) {
                        IconButton(onClick = onBackHome) {
                            Icon(Icons.Filled.Home, contentDescription = "Home")
                        }
                    }
                    IconButton(onClick = { newFolderDialogOpen = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = { sortSheetOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                    if (windowInfo.useTwoPane) {
                        IconButton(onClick = { viewModel.selectAll(filteredEntries) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = { menuSheetOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                },
            )
        },
        bottomBar = {
            if (!windowInfo.useTwoPane) {
                Column {
                    // Clipboard Paste Banner
                    val clip = state.clipboard
                    if (clip != null) {
                        ClipboardBanner(
                            clipboard = clip,
                            onPaste = viewModel::pasteHere,
                            onCancel = viewModel::clearClipboard,
                        )
                    }

                    // Selection Action Bar
                    AnimatedVisibility(visible = state.selectedPaths.isNotEmpty()) {
                        SelectionBottomBar(
                            selectedCount = state.selectedPaths.size,
                            singleSelectedPath = state.selectedPaths.singleOrNull(),
                            selectedAudioPaths = selectedAudioPaths,
                            pickerModeForSpectrum = pickerModeForSpectrum,
                            onCopy = { viewModel.copySelectionToClipboard() },
                            onMove = { viewModel.moveSelectionToClipboard() },
                            onRename = { path -> renameTargetPath = path },
                            onDelete = { deleteConfirmPaths = state.selectedPaths.toList() },
                            onInfo = { viewModel.inspectDetails(state.selectedPaths) },
                            onEditTags = { onEditAudioTags(selectedAudioPaths) },
                            onAnalyzeSpectrum = if (onAnalyzeSpectrum != null && selectedAudioPaths.isNotEmpty()) {
                                {
                                    val target = selectedAudioPaths.first()
                                    viewModel.clearSelection()
                                    onAnalyzeSpectrum(target)
                                }
                            } else {
                                null
                            },
                            onClear = viewModel::clearSelection,
                        )
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
                Row(Modifier.fillMaxSize()) {
                    FileManagerSidePane(
                        state = state,
                        filter = filter,
                        onFilterChange = { filter = it },
                        selectedAudioPaths = selectedAudioPaths,
                        pickerModeForSpectrum = pickerModeForSpectrum,
                        onOpenVolume = viewModel::openVolume,
                        onOpenDir = { viewModel.open(it) },
                        onPaste = viewModel::pasteHere,
                        onCancelClipboard = viewModel::clearClipboard,
                        onCopy = { viewModel.copySelectionToClipboard() },
                        onMove = { viewModel.moveSelectionToClipboard() },
                        onRename = { path -> renameTargetPath = path },
                        onDelete = { deleteConfirmPaths = state.selectedPaths.toList() },
                        onInfo = { viewModel.inspectDetails(state.selectedPaths) },
                        onEditTags = { onEditAudioTags(selectedAudioPaths) },
                        onAnalyzeSpectrum = if (onAnalyzeSpectrum != null && selectedAudioPaths.isNotEmpty()) {
                            {
                                val target = selectedAudioPaths.first()
                                viewModel.clearSelection()
                                onAnalyzeSpectrum(target)
                            }
                        } else {
                            null
                        },
                        onClearSelection = viewModel::clearSelection,
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
                        StatusBanner(
                            message = state.statusMessage,
                            onDismiss = viewModel::dismissStatus,
                        )
                        if (pickerModeForSpectrum) {
                            SpectrumPickerHintBanner()
                        }
                        BreadcrumbStrip(
                            directory = state.directory,
                            volumes = state.volumes,
                            onOpenDir = { viewModel.open(it) },
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            FileManagerListContent(
                                state = state,
                                entries = filteredEntries,
                                columns = windowInfo.browserListColumns,
                                onEntryClick = { entry ->
                                    if (state.selectedPaths.isNotEmpty()) {
                                        viewModel.toggleSelect(entry)
                                    } else if (entry.isDirectory) {
                                        viewModel.open(entry.file)
                                    } else if (pickerModeForSpectrum && entry.isAudio && onAnalyzeSpectrum != null) {
                                        onAnalyzeSpectrum(entry.path)
                                    } else {
                                        contextEntryPath = entry.path
                                    }
                                },
                                onEntryLongClick = { entry ->
                                    contextEntryPath = entry.path
                                },
                                onToggleSelect = viewModel::toggleSelect,
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    StatusBanner(
                        message = state.statusMessage,
                        onDismiss = viewModel::dismissStatus,
                    )
                    if (pickerModeForSpectrum) {
                        SpectrumPickerHintBanner()
                    }

                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                        placeholder = { Text("Search files & folders") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filter.isNotEmpty()) {
                                IconButton(onClick = { filter = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear filter")
                                }
                            }
                        },
                    )

                    if (state.volumes.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            items(state.volumes, key = { it.directory.absolutePath }) { volume ->
                                val isCurrent = state.directory.absolutePath.startsWith(volume.directory.absolutePath)
                                AssistChip(
                                    onClick = { viewModel.openVolume(volume) },
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
                                    colors = if (isCurrent) {
                                        AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    } else {
                                        AssistChipDefaults.assistChipColors()
                                    },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }

                    BreadcrumbStrip(
                        directory = state.directory,
                        volumes = state.volumes,
                        onOpenDir = { viewModel.open(it) },
                    )

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        FileManagerListContent(
                            state = state,
                            entries = filteredEntries,
                            columns = 1,
                            onEntryClick = { entry ->
                                if (state.selectedPaths.isNotEmpty()) {
                                    viewModel.toggleSelect(entry)
                                } else if (entry.isDirectory) {
                                    viewModel.open(entry.file)
                                } else if (pickerModeForSpectrum && entry.isAudio && onAnalyzeSpectrum != null) {
                                    onAnalyzeSpectrum(entry.path)
                                } else {
                                    contextEntryPath = entry.path
                                }
                            },
                            onEntryLongClick = { entry ->
                                contextEntryPath = entry.path
                            },
                            onToggleSelect = viewModel::toggleSelect,
                        )
                    }
                }
            }

            if (state.busyMessage != null) {
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
                                text = state.busyMessage ?: "Working…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Sheets & Dialogs (all ModalBottomSheets / AlertDialogs — DX270 safe)
    // ------------------------------------------------------------------

    if (menuSheetOpen) {
        ModalBottomSheet(onDismissRequest = { menuSheetOpen = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "File manager options",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                ListItem(
                    headlineContent = { Text("New folder…") },
                    leadingContent = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        newFolderDialogOpen = true
                    },
                )
                if (state.clipboard != null) {
                    ListItem(
                        headlineContent = {
                            Text("Paste here (${state.clipboard?.sourcePaths?.size ?: 0} item(s))")
                        },
                        leadingContent = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                        modifier = Modifier.clickable {
                            menuSheetOpen = false
                            viewModel.pasteHere()
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text("Select all") },
                    leadingContent = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        viewModel.selectAll(filteredEntries)
                    },
                )
                ListItem(
                    headlineContent = { Text("Invert selection") },
                    leadingContent = { Icon(Icons.Filled.FlipToBack, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        viewModel.invertSelection(filteredEntries)
                    },
                )
                if (state.selectedPaths.isNotEmpty()) {
                    ListItem(
                        headlineContent = { Text("Clear selection") },
                        leadingContent = { Icon(Icons.Filled.Close, contentDescription = null) },
                        modifier = Modifier.clickable {
                            menuSheetOpen = false
                            viewModel.clearSelection()
                        },
                    )
                }
                ListItem(
                    headlineContent = { Text("Sort by…") },
                    supportingContent = {
                        Text("${state.sortField.label} (${if (state.sortAscending) "Ascending" else "Descending"})")
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        sortSheetOpen = true
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(if (state.showHidden) "Hide hidden files (.dot)" else "Show hidden files (.dot)")
                    },
                    leadingContent = {
                        Icon(
                            if (state.showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        viewModel.toggleShowHidden()
                    },
                )
                ListItem(
                    headlineContent = { Text("Choose storage device…") },
                    leadingContent = { Icon(Icons.Filled.SdCard, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        storageDialogOpen = true
                    },
                )
                ListItem(
                    headlineContent = { Text("Current folder properties") },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        viewModel.inspectDetails(listOf(state.directory.absolutePath))
                    },
                )
                ListItem(
                    headlineContent = { Text("Refresh") },
                    leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuSheetOpen = false
                        viewModel.refresh()
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

    if (sortSheetOpen) {
        ModalBottomSheet(onDismissRequest = { sortSheetOpen = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Sort files & folders",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                FileSortField.entries.forEach { field ->
                    ListItem(
                        headlineContent = { Text(field.label) },
                        leadingContent = {
                            RadioButton(
                                selected = state.sortField == field,
                                onClick = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            viewModel.setSorting(field, state.sortAscending)
                            sortSheetOpen = false
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(
                    text = "Order",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                ListItem(
                    headlineContent = { Text("Ascending (A → Z, Small → Large, Old → New)") },
                    leadingContent = {
                        RadioButton(selected = state.sortAscending, onClick = null)
                    },
                    modifier = Modifier.clickable {
                        viewModel.setSorting(state.sortField, true)
                        sortSheetOpen = false
                    },
                )
                ListItem(
                    headlineContent = { Text("Descending (Z → A, Large → Small, New → Old)") },
                    leadingContent = {
                        RadioButton(selected = !state.sortAscending, onClick = null)
                    },
                    modifier = Modifier.clickable {
                        viewModel.setSorting(state.sortField, false)
                        sortSheetOpen = false
                    },
                )
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }

    // Single-item action sheet (on long-press or file tap)
    val activeContextEntry = remember(contextEntryPath, state.entries) {
        contextEntryPath?.let { path -> state.entries.firstOrNull { it.path == path } }
    }
    if (activeContextEntry != null) {
        ModalBottomSheet(onDismissRequest = { contextEntryPath = null }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = activeContextEntry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Text(
                    text = activeContextEntry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                if (activeContextEntry.isDirectory) {
                    ListItem(
                        headlineContent = { Text("Open folder") },
                        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val dir = activeContextEntry.file
                            contextEntryPath = null
                            viewModel.open(dir)
                        },
                    )
                }
                if (activeContextEntry.isAudio) {
                    if (onAnalyzeSpectrum != null) {
                        ListItem(
                            headlineContent = { Text("Analyze in Spectrum analysis") },
                            leadingContent = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                            modifier = Modifier.clickable {
                                val path = activeContextEntry.path
                                contextEntryPath = null
                                onAnalyzeSpectrum(path)
                            },
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Edit audio tags in Tag Editor") },
                        leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val path = activeContextEntry.path
                            contextEntryPath = null
                            onEditAudioTags(listOf(path))
                        },
                    )
                }
                ListItem(
                    headlineContent = {
                        Text(if (activeContextEntry.path in state.selectedPaths) "Deselect" else "Select")
                    },
                    leadingContent = { Icon(Icons.Filled.Check, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val entry = activeContextEntry
                        contextEntryPath = null
                        viewModel.toggleSelect(entry)
                    },
                )
                ListItem(
                    headlineContent = { Text("Copy") },
                    leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val path = activeContextEntry.path
                        contextEntryPath = null
                        viewModel.copySelectionToClipboard(listOf(path))
                    },
                )
                ListItem(
                    headlineContent = { Text("Move (Cut)") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val path = activeContextEntry.path
                        contextEntryPath = null
                        viewModel.moveSelectionToClipboard(listOf(path))
                    },
                )
                ListItem(
                    headlineContent = { Text("Rename…") },
                    leadingContent = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val path = activeContextEntry.path
                        contextEntryPath = null
                        renameTargetPath = path
                    },
                )
                ListItem(
                    headlineContent = { Text("Properties / Details") },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val path = activeContextEntry.path
                        contextEntryPath = null
                        viewModel.inspectDetails(listOf(path))
                    },
                )
                ListItem(
                    headlineContent = {
                        Text("Delete…", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable {
                        val path = activeContextEntry.path
                        contextEntryPath = null
                        deleteConfirmPaths = listOf(path)
                    },
                )
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }

    if (newFolderDialogOpen) {
        var folderName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialogOpen = false },
            title = { Text("Create new folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = folderName
                        newFolderDialogOpen = false
                        viewModel.createFolder(name)
                    },
                    enabled = folderName.trim().isNotEmpty(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { newFolderDialogOpen = false }) { Text("Cancel") }
            },
        )
    }

    renameTargetPath?.let { targetPath ->
        val originalName = remember(targetPath) { File(targetPath).name }
        var newName by rememberSaveable(targetPath) { mutableStateOf(originalName) }
        AlertDialog(
            onDismissRequest = { renameTargetPath = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = newName
                        renameTargetPath = null
                        viewModel.renameItem(targetPath, updated)
                    },
                    enabled = newName.trim().isNotEmpty() && newName.trim() != originalName,
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetPath = null }) { Text("Cancel") }
            },
        )
    }

    deleteConfirmPaths?.let { pathsToDelete ->
        val count = pathsToDelete.size
        val previewNames = remember(pathsToDelete) {
            pathsToDelete.take(6).map { File(it).name }
        }
        AlertDialog(
            onDismissRequest = { deleteConfirmPaths = null },
            title = {
                Text(
                    if (count == 1) "Delete '${File(pathsToDelete.first()).name}'?"
                    else "Delete $count items?",
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "This will permanently delete the selected file(s) or folder(s) from storage.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    previewNames.forEach { name ->
                        Text(
                            text = "• $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (count > previewNames.size) {
                        Text(
                            text = "…and ${count - previewNames.size} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets = pathsToDelete
                        deleteConfirmPaths = null
                        viewModel.deletePaths(targets)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmPaths = null }) { Text("Cancel") }
            },
        )
    }

    state.detailsInfo?.let { info ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDetails,
            title = {
                Text(
                    text = info.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    DetailLine("Location", info.path)
                    DetailLine("Total size", "${Format.fileSize(info.totalSizeBytes)} (${info.totalSizeBytes} bytes)")
                    if (info.folderCount > 0 || info.itemCount > 1) {
                        DetailLine("Contents", "${info.folderCount} folder(s), ${info.fileCount} file(s)")
                    }
                    DetailLine("Modified", info.lastModifiedText)
                    DetailLine("Access", info.permissionsText)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDetails) { Text("Close") }
            },
        )
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
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SpectrumPickerHintBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Tap an audio file to analyze its spectrum • long-press for file manager actions",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatusBanner(
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

@Composable
private fun ClipboardBanner(
    clipboard: FileClipboard,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${clipboard.mode.label}: ${clipboard.sourcePaths.size} item(s)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Open destination folder and tap Paste",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(6.dp))
            Button(onClick = onPaste) {
                Text("Paste here")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    singleSelectedPath: String?,
    selectedAudioPaths: List<String>,
    pickerModeForSpectrum: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onEditTags: () -> Unit,
    onAnalyzeSpectrum: (() -> Unit)?,
    onClear: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                if (onAnalyzeSpectrum != null && (pickerModeForSpectrum || selectedAudioPaths.size == 1)) {
                    Button(
                        onClick = onAnalyzeSpectrum,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Analyze")
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (selectedAudioPaths.isNotEmpty() && !pickerModeForSpectrum) {
                    FilledTonalButton(
                        onClick = onEditTags,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Edit tags (${selectedAudioPaths.size})")
                    }
                    Spacer(Modifier.width(6.dp))
                }
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                }
                IconButton(onClick = onMove) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move")
                }
                if (singleSelectedPath != null) {
                    IconButton(onClick = { onRename(singleSelectedPath) }) {
                        Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                    }
                }
                IconButton(onClick = onInfo) {
                    Icon(Icons.Filled.Info, contentDescription = "Properties")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbStrip(
    directory: File,
    volumes: List<StorageVolumeInfo>,
    onOpenDir: (File) -> Unit,
) {
    val segments = remember(directory, volumes) {
        val matchingVolume = volumes
            .filter { directory.absolutePath.startsWith(it.directory.absolutePath) }
            .maxByOrNull { it.directory.absolutePath.length }

        if (matchingVolume != null) {
            val base = matchingVolume.directory
            val rel = directory.absolutePath.removePrefix(base.absolutePath).trimStart(File.separatorChar)
            val list = mutableListOf(matchingVolume.name to base)
            if (rel.isNotEmpty()) {
                var current = base
                for (part in rel.split(File.separatorChar)) {
                    current = current.resolve(part)
                    list.add(part to current)
                }
            }
            list
        } else {
            val list = mutableListOf<Pair<String, File>>()
            var cur: File? = directory
            while (cur != null) {
                list.add(0, (cur.name.ifEmpty { "/" }) to cur)
                cur = cur.parentFile
            }
            list
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(segments.size, key = { idx -> segments[idx].second.absolutePath }) { idx ->
            val (label, target) = segments[idx]
            val isLast = idx == segments.lastIndex
            AssistChip(
                onClick = { if (!isLast) onOpenDir(target) },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = if (isLast) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileManagerSidePane(
    state: FileManagerState,
    filter: String,
    onFilterChange: (String) -> Unit,
    selectedAudioPaths: List<String>,
    pickerModeForSpectrum: Boolean,
    onOpenVolume: (StorageVolumeInfo) -> Unit,
    onOpenDir: (File) -> Unit,
    onPaste: () -> Unit,
    onCancelClipboard: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onEditTags: () -> Unit,
    onAnalyzeSpectrum: (() -> Unit)?,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = filter,
            onValueChange = onFilterChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.isNotEmpty()) {
                    IconButton(onClick = { onFilterChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear filter")
                    }
                }
            },
        )

        state.clipboard?.let { clip ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "${clip.mode.label}: ${clip.sourcePaths.size} item(s)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPaste,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Paste")
                        }
                        TextButton(onClick = onCancelClipboard) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        if (state.selectedPaths.isNotEmpty()) {
            val single = state.selectedPaths.singleOrNull()
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${state.selectedPaths.size} selected",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearSelection) { Text("Clear") }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilledTonalButton(onClick = onCopy) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                        FilledTonalButton(onClick = onMove) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Move")
                        }
                        if (single != null) {
                            FilledTonalButton(onClick = { onRename(single) }) {
                                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Rename")
                            }
                        }
                        FilledTonalButton(onClick = onInfo) {
                            Icon(Icons.Filled.Info, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Details")
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                        if (onAnalyzeSpectrum != null && (pickerModeForSpectrum || selectedAudioPaths.size == 1)) {
                            Button(onClick = onAnalyzeSpectrum) {
                                Icon(Icons.Filled.GraphicEq, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Analyze spectrum")
                            }
                        }
                        if (selectedAudioPaths.isNotEmpty()) {
                            Button(onClick = onEditTags) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Edit tags (${selectedAudioPaths.size})")
                            }
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
                    val isCurrent = state.directory.absolutePath.startsWith(volume.directory.absolutePath)
                    AssistChip(
                        onClick = { onOpenVolume(volume) },
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
                        colors = if (isCurrent) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileManagerListContent(
    state: FileManagerState,
    entries: List<FileEntry>,
    columns: Int,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onToggleSelect: (FileEntry) -> Unit,
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

        entries.isEmpty() -> EmptyState(
            icon = Icons.Filled.Folder,
            title = "Empty folder",
            subtitle = "No files or folders match.",
        )

        columns <= 1 -> LazyColumn(Modifier.fillMaxSize()) {
            items(
                items = entries,
                key = { it.path },
                contentType = { if (it.isDirectory) "dir" else "file" },
            ) { entry ->
                FileEntryRow(
                    entry = entry,
                    selected = entry.path in state.selectedPaths,
                    onClick = { onEntryClick(entry) },
                    onLongClick = { onEntryLongClick(entry) },
                    onToggleSelect = { onToggleSelect(entry) },
                )
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }

        else -> {
            val rows = remember(entries, columns) { entries.chunked(columns) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(
                    items = rows,
                    key = { "row:" + it.first().path },
                ) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { entry ->
                            Box(Modifier.weight(1f)) {
                                FileEntryRow(
                                    entry = entry,
                                    selected = entry.path in state.selectedPaths,
                                    onClick = { onEntryClick(entry) },
                                    onLongClick = { onEntryLongClick(entry) },
                                    onToggleSelect = { onToggleSelect(entry) },
                                )
                            }
                        }
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = entry.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(
                imageVector = iconForEntry(entry, selected),
                contentDescription = null,
                tint = if (entry.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    )
}

private fun iconForEntry(entry: FileEntry, selected: Boolean): ImageVector = when {
    entry.isDirectory -> Icons.Filled.Folder
    entry.isAudio -> if (selected) Icons.Filled.MusicNote else Icons.Filled.Audiotrack
    entry.extension in IMAGE_EXTENSIONS -> Icons.Filled.Image
    entry.extension in VIDEO_EXTENSIONS -> Icons.Filled.Movie
    entry.extension in ARCHIVE_EXTENSIONS -> Icons.Filled.Archive
    else -> Icons.Filled.Description
}
