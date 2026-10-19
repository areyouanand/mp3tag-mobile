package com.mp3tag.mobile.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.fs.FolderScanner
import com.mp3tag.mobile.ui.common.EmptyState
import com.mp3tag.mobile.util.Format
import java.io.File

/**
 * MP3Tag-style folder view: browse directories, tap files to (de)select,
 * long-press a file to open its tag editor directly.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    onEditFiles: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: BrowserViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var filter by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var storageDialogOpen by remember { mutableStateOf(false) }

    val root = state.directory
    val isAtStart = root.absolutePath == android.os.Environment.getExternalStorageDirectory().absolutePath

    val filteredDirs = remember(state.directories, filter) {
        if (filter.isBlank()) state.directories
        else state.directories.filter { it.name.contains(filter, ignoreCase = true) }
    }
    val filteredFiles = remember(state.audioFiles, filter) {
        if (filter.isBlank()) state.audioFiles
        else state.audioFiles.filter { it.name.contains(filter, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(root.name.ifEmpty { root.absolutePath }, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    if (!isAtStart) {
                        IconButton(onClick = viewModel::navigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Select all files") },
                            leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                            onClick = { menuOpen = false; viewModel.selectAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear selection") },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                            onClick = { menuOpen = false; viewModel.clearSelection() },
                        )
                        DropdownMenuItem(
                            text = { Text("Choose storage…") },
                            leadingIcon = { Icon(Icons.Filled.SdCard, contentDescription = null) },
                            onClick = { menuOpen = false; storageDialogOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenSettings() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = state.selectedPaths.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "${state.selectedPaths.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearSelection) { Text("Clear") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onEditFiles(viewModel.selectedFiles.map { it.absolutePath }) }) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text("Filter") },
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
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    items(state.volumes, key = { it.directory.absolutePath }) { volume ->
                        val isCurrentVolume = state.directory.absolutePath
                            .startsWith(volume.directory.absolutePath)
                        AssistChip(
                            onClick = { viewModel.openVolume(volume) },
                            label = { Text(volume.name) },
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
                    items(state.recents) { path ->
                        AssistChip(
                            onClick = { viewModel.open(File(path)) },
                            label = { Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }

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

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(filteredDirs, key = { "dir:" + it.absolutePath }) { dir ->
                        ListItem(
                            headlineContent = { Text(dir.name) },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            modifier = Modifier.combinedClickable(onClick = { viewModel.open(dir) }),
                        )
                    }
                    items(filteredFiles, key = { "file:" + it.absolutePath }) { file ->
                        val selected = file.absolutePath in state.selectedPaths
                        ListItem(
                            headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    "${Format.fileSize(file.length())} • ${file.extension.uppercase()}",
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
                                Checkbox(checked = selected, onCheckedChange = null)
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                            modifier = Modifier.combinedClickable(
                                onClick = { viewModel.toggleSelect(file) },
                                onLongClick = { onEditFiles(listOf(file.absolutePath)) },
                            ),
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    if (storageDialogOpen) {
        AlertDialog(
            onDismissRequest = { storageDialogOpen = false },
            title = { Text("Choose storage device") },
            text = {
                Column {
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
