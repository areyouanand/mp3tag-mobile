package com.mp3tag.mobile.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.tag.CoverImage
import com.mp3tag.mobile.data.tag.OFFERABLE_FIELDS
import com.mp3tag.mobile.data.tag.TagSnapshot
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo
import com.mp3tag.mobile.util.DeviceProfile
import com.mp3tag.mobile.util.Format
import com.mp3tag.mobile.util.ImageDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.FieldKey
import java.io.File

private val FIELD_LABELS: Map<FieldKey, String> = mapOf(
    FieldKey.TITLE to "Title",
    FieldKey.ARTIST to "Artist",
    FieldKey.ALBUM_ARTIST to "Album artist",
    FieldKey.ALBUM to "Album",
    FieldKey.YEAR to "Year",
    FieldKey.TRACK to "Track #",
    FieldKey.TRACK_TOTAL to "Track total",
    FieldKey.DISC_NO to "Disc #",
    FieldKey.DISC_TOTAL to "Disc total",
    FieldKey.GENRE to "Genre",
    FieldKey.COMPOSER to "Composer",
    FieldKey.COMMENT to "Comment",
    FieldKey.SUBTITLE to "Subtitle",
    FieldKey.VERSION to "Version",
    FieldKey.DISC_SUBTITLE to "Disc subtitle",
    FieldKey.BPM to "BPM",
    FieldKey.KEY to "Key",
    FieldKey.LANGUAGE to "Language",
    FieldKey.MOOD to "Mood",
    FieldKey.TEMPO to "Tempo",
    FieldKey.GROUPING to "Grouping",
    FieldKey.LYRICS to "Lyrics",
    FieldKey.LYRICIST to "Lyricist",
    FieldKey.TITLE_SORT to "Title sort",
    FieldKey.ARTIST_SORT to "Artist sort",
    FieldKey.ALBUM_SORT to "Album sort",
    FieldKey.ALBUM_ARTIST_SORT to "Album artist sort",
    FieldKey.COMPOSER_SORT to "Composer sort",
    FieldKey.CONDUCTOR to "Conductor",
    FieldKey.ARRANGER to "Arranger",
    FieldKey.ENGINEER to "Engineer",
    FieldKey.PRODUCER to "Producer",
    FieldKey.REMIXER to "Remixer",
    FieldKey.DJMIXER to "DJ mixer",
    FieldKey.MIXER to "Mixer",
    FieldKey.PERFORMER to "Performer",
    FieldKey.ORCHESTRA to "Orchestra",
    FieldKey.CHOIR to "Choir",
    FieldKey.ENSEMBLE to "Ensemble",
    FieldKey.INSTRUMENT to "Instrument",
    FieldKey.INVOLVEDPEOPLE to "Involved people",
    FieldKey.ISRC to "ISRC",
    FieldKey.ISWC to "ISWC",
    FieldKey.BARCODE to "Barcode",
    FieldKey.CATALOG_NO to "Catalog number",
    FieldKey.RECORD_LABEL to "Record label",
    FieldKey.COUNTRY to "Country",
    FieldKey.MEDIA to "Media",
    FieldKey.ENCODER to "Encoder",
    FieldKey.COPYRIGHT to "Copyright",
    FieldKey.ORIGINAL_ARTIST to "Original artist",
    FieldKey.ORIGINAL_ALBUM to "Original album",
    FieldKey.ORIGINAL_LYRICIST to "Original lyricist",
    FieldKey.ORIGINAL_YEAR to "Original year",
    FieldKey.ORIGINALRELEASEDATE to "Original release date",
    FieldKey.MOVEMENT to "Movement",
    FieldKey.MOVEMENT_NO to "Movement no.",
    FieldKey.MOVEMENT_TOTAL to "Movement total",
    FieldKey.OPUS to "Opus",
    FieldKey.WORK to "Work",
    FieldKey.IS_COMPILATION to "Compilation",
    FieldKey.RATING to "Rating",
    FieldKey.QUALITY to "Quality",
    FieldKey.OCCASION to "Occasion",
    FieldKey.SCRIPT to "Script",
    FieldKey.URL_OFFICIAL_RELEASE_SITE to "URL: official release site",
    FieldKey.URL_OFFICIAL_ARTIST_SITE to "URL: official artist site",
    FieldKey.URL_WIKIPEDIA_RELEASE_SITE to "URL: Wikipedia release",
    FieldKey.URL_WIKIPEDIA_ARTIST_SITE to "URL: Wikipedia artist",
    FieldKey.URL_DISCOGS_RELEASE_SITE to "URL: Discogs release",
    FieldKey.URL_DISCOGS_ARTIST_SITE to "URL: Discogs artist",
    FieldKey.URL_LYRICS_SITE to "URL: lyrics site",
    FieldKey.MUSICBRAINZ_TRACK_ID to "MusicBrainz track ID",
    FieldKey.MUSICBRAINZ_RELEASEID to "MusicBrainz release ID",
    FieldKey.MUSICBRAINZ_RELEASE_TRACK_ID to "MusicBrainz release track ID",
    FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID to "MusicBrainz release group ID",
    FieldKey.MUSICBRAINZ_RELEASEARTISTID to "MusicBrainz release artist ID",
    FieldKey.MUSICBRAINZ_ARTISTID to "MusicBrainz artist ID",
    FieldKey.MUSICBRAINZ_WORK_ID to "MusicBrainz work ID",
    FieldKey.MUSICBRAINZ_DISC_ID to "MusicBrainz disc ID",
    FieldKey.MUSICBRAINZ_RELEASE_STATUS to "MusicBrainz release status",
    FieldKey.MUSICBRAINZ_RELEASE_TYPE to "MusicBrainz release type",
    FieldKey.MUSICBRAINZ_RELEASE_COUNTRY to "MusicBrainz release country",
    FieldKey.ACOUSTID_ID to "AcoustID",
    FieldKey.ACOUSTID_FINGERPRINT to "AcoustID fingerprint",
    FieldKey.MUSICIP_ID to "MusicIP ID",
    FieldKey.AMAZON_ID to "Amazon ID",
)

/** Human label for any FieldKey, with a readable fallback for exotic ones. */
internal fun fieldLabel(key: FieldKey): String =
    FIELD_LABELS[key]
        ?: key.name.lowercase()
            .split('_')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

private val NUMERIC_FIELDS = setOf(
    FieldKey.YEAR, FieldKey.TRACK, FieldKey.TRACK_TOTAL,
    FieldKey.DISC_NO, FieldKey.DISC_TOTAL,
)

/**
 * Tag editor for one or more selected files. Batch semantics follow MP3Tag:
 * fields that differ across the selection show as "mixed" and are only
 * written when the user edits them.
 *
 * Adapts across form factors and orientations:
 * - Phone portrait: single-column scrollable list
 * - Phone landscape & tablets: 2-pane split layout (left pane: file summary, cover art,
 *   file-inclusion card, and Save button; right pane: 2-column tag field grid).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    paths: List<String>,
    sessionKey: String,
    onDone: () -> Unit,
    onFilesRenamed: () -> Unit = {},
    onOpenSingleFile: (String) -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: EditorViewModel = viewModel(
        key = sessionKey,
        factory = EditorViewModel.factory(paths, app),
    )
    val state by vm.state.collectAsState()
    val windowInfo = rememberAdaptiveWindowInfo()

    var toolsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var showFilenameTags by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showBatchActions by rememberSaveable { mutableStateOf(false) }
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    var filesSheetOpen by rememberSaveable { mutableStateOf(false) }

    val requestExit: () -> Unit = {
        if (state.hasEdits) showExitConfirm = true else onDone()
    }
    BackHandler { requestExit() }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::onCoverPicked)
    }

    val includedSnapshots = state.snapshots.filter { it.path !in state.excludedPaths }
    val fileSummary = when {
        state.excludedPaths.isEmpty() -> "${state.paths.size} file(s) • ${formatSummary(includedSnapshots)}"
        else -> "${includedSnapshots.size} of ${state.paths.size} files • ${formatSummary(includedSnapshots)}"
    }

    val launchCoverPicker = {
        pickCover.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { filesSheetOpen = true },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Edit tags")
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Choose files to edit",
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        Text(
                            text = fileSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = vm::undo,
                        enabled = state.undoAvailable && !state.saving,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = vm::redo,
                        enabled = state.redoAvailable && !state.saving,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(
                        onClick = { toolsSheetOpen = true },
                        enabled = !state.saving,
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = "Tools")
                    }
                    IconButton(
                        onClick = vm::save,
                        enabled = state.hasEdits && !state.saving,
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
                        state.progressText?.let { progress ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = progress,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (windowInfo.useTwoPane) {
                Row(Modifier.fillMaxSize()) {
                    EditorSidePane(
                        state = state,
                        includedCount = includedSnapshots.size,
                        compactHeight = windowInfo.isCompactHeight,
                        isTablet = windowInfo.isTablet,
                        onOpenFilesSheet = { filesSheetOpen = true },
                        onPickCover = launchCoverPicker,
                        onRemoveCover = vm::onCoverRemove,
                        onUndoCover = vm::onCoverUndo,
                        onSave = vm::save,
                        modifier = Modifier
                            .width(windowInfo.sidePaneWidth)
                            .fillMaxHeight(),
                    )
                    VerticalDivider()
                    EditorFieldsTwoColumnPane(
                        state = state,
                        vm = vm,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .imePadding(),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { FileInfoCard(state.snapshots) }

                    item { LabeledField(FieldKey.TITLE, state, vm) }
                    item { LabeledField(FieldKey.ARTIST, state, vm) }
                    item { LabeledField(FieldKey.ALBUM_ARTIST, state, vm) }
                    item { LabeledField(FieldKey.ALBUM, state, vm) }
                    item { LabeledField(FieldKey.YEAR, state, vm) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) { LabeledField(FieldKey.TRACK, state, vm) }
                            Box(Modifier.weight(1f)) { LabeledField(FieldKey.TRACK_TOTAL, state, vm) }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) { LabeledField(FieldKey.DISC_NO, state, vm) }
                            Box(Modifier.weight(1f)) { LabeledField(FieldKey.DISC_TOTAL, state, vm) }
                        }
                    }
                    item { LabeledField(FieldKey.GENRE, state, vm) }
                    item { LabeledField(FieldKey.COMPOSER, state, vm) }
                    item { LabeledField(FieldKey.COMMENT, state, vm) }

                    // Additional fields (from the files or standard), MP3Tag-style.
                    items(state.extraKeys, key = { it.name }) { key ->
                        LabeledField(key, state, vm)
                    }
                    item { AddFieldSection(state, vm) }

                    item {
                        CoverSection(
                            state = state,
                            onPick = launchCoverPicker,
                            onRemove = vm::onCoverRemove,
                            onUndo = vm::onCoverUndo,
                            thumbnailSize = 96.dp,
                        )
                    }

                    item {
                        Button(
                            onClick = vm::save,
                            enabled = state.hasEdits && !state.saving,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save ${includedSnapshots.size} file(s)")
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            if (state.saving) {
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
                            state.progressText?.let { progress ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = progress,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.outcomes?.let { outcomes ->
        AlertDialog(
            onDismissRequest = vm::dismissOutcomes,
            title = {
                val ok = outcomes.count { it.success }
                Text(
                    if (ok == outcomes.size) "Saved $ok file(s)"
                    else "Saved $ok of ${outcomes.size} files",
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (state.keptPaths.isNotEmpty()) {
                        Text(
                            text = "Pending edits kept for ${state.keptPaths.size} file(s) not saved:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        state.keptPaths.take(8).forEach { p ->
                            Text(
                                text = "• ${File(p).name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (state.keptPaths.size > 8) {
                            Text(
                                text = "…and ${state.keptPaths.size - 8} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    outcomes.forEach { outcome ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (outcome.success) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (outcome.success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    outcome.file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                outcome.message?.let { message ->
                                    Text(
                                        message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::dismissOutcomes) { Text("OK") }
            },
        )
    }

    state.renameOutcomes?.let { outcomes ->
        val ok = outcomes.count { it.error == null }
        AlertDialog(
            onDismissRequest = vm::dismissRenameOutcomes,
            title = {
                Text(
                    if (ok == outcomes.size) "Renamed $ok file(s)"
                    else "Renamed $ok of ${outcomes.size} files",
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    outcomes.forEach { outcome ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (outcome.error == null) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (outcome.error == null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    File(outcome.oldPath).name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (outcome.error != null) {
                                    Text(
                                        outcome.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                } else if (outcome.newPath != null) {
                                    Text(
                                        "→ ${File(outcome.newPath).name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::dismissRenameOutcomes) { Text("OK") }
            },
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Unsaved changes") },
            text = {
                Text("You have edits that are not saved yet. Quit without saving?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    vm.discardChanges()
                    onDone()
                }) { Text("Discard & quit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Keep editing") }
            },
        )
    }

    if (toolsSheetOpen) {
        ModalBottomSheet(onDismissRequest = { toolsSheetOpen = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                listOf(
                    Triple(Icons.Filled.TextFields, "Tags from filename…", 0),
                    Triple(Icons.Filled.DriveFileRenameOutline, "Rename from tags…", 1),
                    Triple(Icons.Filled.AutoFixHigh, "Batch actions…", 2),
                ).forEach { (icon, label, id) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(icon, contentDescription = null) },
                        modifier = Modifier.clickable {
                            toolsSheetOpen = false
                            when (id) {
                                0 -> showFilenameTags = true
                                1 -> showRename = true
                                2 -> showBatchActions = true
                            }
                        },
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }

    if (showFilenameTags) {
        TagsFromFilenameDialog(
            snapshots = includedSnapshots,
            savedPatterns = vm.savedPatterns,
            rememberPattern = vm::savePattern,
            onApply = vm::applyParsedTags,
            onDismiss = { showFilenameTags = false },
        )
    }

    if (showRename) {
        RenameFromTagsDialog(
            snapshots = includedSnapshots,
            savedPatterns = vm.savedPatterns,
            rememberPattern = vm::savePattern,
            onApply = { pattern, done ->
                vm.applyRenames(pattern) {
                    onFilesRenamed()
                    done()
                }
            },
            onDismiss = { showRename = false },
        )
    }

    if (showBatchActions) {
        BatchActionsDialog(
            snapshots = includedSnapshots,
            displayedKeys = state.displayedKeys,
            onApply = vm::applyTransforms,
            onDismiss = { showBatchActions = false },
        )
    }

    if (filesSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filesSheetOpen = false }) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = "Files in this edit",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = vm::includeAllFiles) { Text("Select all") }
                    TextButton(onClick = vm::excludeAllFiles) { Text("Deselect all") }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = (windowInfo.heightDp * 0.55f).dp.coerceIn(180.dp, 460.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.snapshots.forEach { snapshot ->
                        val included = snapshot.path !in state.excludedPaths
                        ListItem(
                            headlineContent = {
                                Text(File(snapshot.path).name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = {
                                Icon(Icons.Filled.MusicNote, contentDescription = null)
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        filesSheetOpen = false
                                        onOpenSingleFile(snapshot.path)
                                    }) {
                                        Text("Edit")
                                    }
                                    Checkbox(checked = included, onCheckedChange = null)
                                }
                            },
                            modifier = Modifier.clickable { vm.toggleFileIncluded(snapshot.path) },
                        )
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun EditorSidePane(
    state: EditorState,
    includedCount: Int,
    compactHeight: Boolean,
    isTablet: Boolean,
    onOpenFilesSheet: () -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onUndoCover: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FileInfoCard(state.snapshots)

        CoverSection(
            state = state,
            onPick = onPickCover,
            onRemove = onRemoveCover,
            onUndo = onUndoCover,
            thumbnailSize = when {
                compactHeight -> 68.dp
                isTablet -> 112.dp
                else -> 88.dp
            },
        )

        if (state.paths.size > 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFilesSheet),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "$includedCount of ${state.paths.size} files active",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Tap to include/exclude files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            enabled = state.hasEdits && !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save $includedCount file(s)")
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun EditorFieldsTwoColumnPane(
    state: EditorState,
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val extraPairs = remember(state.extraKeys) { state.extraKeys.chunked(2) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FieldPairRow(FieldKey.TITLE, FieldKey.ARTIST, state, vm)
        }
        item {
            FieldPairRow(FieldKey.ALBUM, FieldKey.ALBUM_ARTIST, state, vm)
        }
        item {
            FieldPairRow(FieldKey.YEAR, FieldKey.GENRE, state, vm)
        }
        item {
            FieldPairRow(FieldKey.TRACK, FieldKey.TRACK_TOTAL, state, vm)
        }
        item {
            FieldPairRow(FieldKey.DISC_NO, FieldKey.DISC_TOTAL, state, vm)
        }
        item {
            FieldPairRow(FieldKey.COMPOSER, FieldKey.COMMENT, state, vm)
        }

        items(
            items = extraPairs,
            key = { pair -> "extraPair:" + pair.joinToString("_") { it.name } },
        ) { pair ->
            if (pair.size == 2) {
                FieldPairRow(pair[0], pair[1], state, vm)
            } else {
                LabeledField(pair[0], state, vm)
            }
        }

        item {
            AddFieldSection(state, vm)
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun FieldPairRow(
    leftKey: FieldKey,
    rightKey: FieldKey,
    state: EditorState,
    vm: EditorViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { LabeledField(leftKey, state, vm) }
        Box(Modifier.weight(1f)) { LabeledField(rightKey, state, vm) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverSection(
    state: EditorState,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onUndo: () -> Unit,
    thumbnailSize: Dp = 96.dp,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverThumbnail(
                cover = state.pendingCover ?: state.cover,
                mixed = state.coverMixed,
                sizeDp = thumbnailSize,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Cover art", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        state.pendingCover != null -> "New image ready to embed"
                        state.coverRemoveRequested -> "Will be removed on save"
                        state.coverMixed -> "Different covers in selection"
                        state.cover != null -> "Embedded cover found"
                        else -> "No cover art"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalButton(onClick = onPick) { Text("Replace") }
                    FilledTonalButton(onClick = onRemove) { Text("Remove") }
                    if (state.pendingCover != null || state.coverRemoveRequested) {
                        TextButton(onClick = onUndo) { Text("Undo") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverThumbnail(
    cover: CoverImage?,
    mixed: Boolean,
    sizeDp: Dp = 96.dp,
) {
    val context = LocalContext.current
    val profile = remember(context) {
        val prefs = AppPrefs(context)
        DeviceProfile.get(context, prefs.performanceMode)
    }

    val initialCached = remember(cover, profile) {
        cover?.let {
            ImageDecode.getCached(
                contentHash = it.hashCode(),
                maxSize = profile.thumbnailMaxPx,
                preferRgb565 = profile.preferRgb565,
            )
        }
    }
    var bitmap by remember(cover, profile) { mutableStateOf<Bitmap?>(initialCached) }

    LaunchedEffect(cover, profile) {
        if (cover == null) {
            bitmap = null
        } else if (bitmap == null) {
            bitmap = withContext(Dispatchers.Default) {
                ImageDecode.decodeScaled(
                    bytes = cover.bytes,
                    maxSize = profile.thumbnailMaxPx,
                    preferRgb565 = profile.preferRgb565,
                    contentHash = cover.hashCode(),
                )
            }
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(sizeDp)) {
        val currentBitmap = bitmap
        if (currentBitmap != null && !currentBitmap.isRecycled) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Cover art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp),
            )
        } else {
            Card(Modifier.size(sizeDp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        if (mixed) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Text(
                    text = "mixed",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun FileInfoCard(snapshots: List<TagSnapshot>) {
    val first = snapshots.firstOrNull() ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val tech = listOfNotNull(
                first.format.name,
                first.tagDescription,
                Format.duration(first.durationSeconds)?.let { "$it min" },
                first.bitrateKbps?.let { "$it kbps" },
                first.sampleRateHz?.let { "$it Hz" },
            ).joinToString(" • ")
            Text(tech, style = MaterialTheme.typography.bodySmall)
            if (snapshots.size > 1) {
                val same = snapshots.all { it.durationSeconds == first.durationSeconds }
                if (!same) {
                    Text(
                        "…per-file technical values differ within the selection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledField(
    key: FieldKey,
    state: EditorState,
    vm: EditorViewModel,
) {
    val field = state.fields[key] ?: return
    val options = state.valueOptions[key].orEmpty()
    // Mixed = files disagree (different values OR some files empty). Any mixed field
    // with at least one usable value gets the picker; fully-agreeing fields don't.
    val showValuePicker = options.size >= 2 || (field.mixed && options.isNotEmpty())
    var valueSheetOpen by rememberSaveable(key.name) { mutableStateOf(false) }

    OutlinedTextField(
        value = field.text,
        onValueChange = { vm.onFieldChange(key, it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = {
            Text(
                text = when {
                    field.mixed && options.size >= 2 -> "${fieldLabel(key)} (mixed — ${options.size} values)"
                    field.mixed && options.isNotEmpty() -> "${fieldLabel(key)} (mixed — 1 value)"
                    field.mixed -> "${fieldLabel(key)} (mixed)"
                    else -> fieldLabel(key)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        placeholder = {
            if (field.mixed) Text("leave empty to keep existing values", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingText = {
            Column {
                if (field.edited && !field.mixed) {
                    Text("was: ${field.original.ifEmpty { "(empty)" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (key in state.overrideKeys) {
                    Text("per-file changes pending — Save to apply", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showValuePicker) {
                    IconButton(onClick = { valueSheetOpen = true }) {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "Show values in files",
                        )
                    }
                }
                if (field.edited || field.text.isNotEmpty()) {
                    IconButton(onClick = { vm.onFieldClear(key) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear ${fieldLabel(key)}")
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (key in NUMERIC_FIELDS) KeyboardType.Number else KeyboardType.Text,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = if (field.edited) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            } else {
                Color.Transparent
            },
        ),
    )

    if (valueSheetOpen && showValuePicker) {
        ModalBottomSheet(onDismissRequest = { valueSheetOpen = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "${fieldLabel(key)} — values found in the selected files",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                options.forEach { value ->
                    ListItem(
                        headlineContent = {
                            Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            Icon(Icons.Filled.MusicNote, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            vm.onFieldChange(key, value)
                            valueSheetOpen = false
                        },
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

private fun formatSummary(snapshots: List<TagSnapshot>): String =
    snapshots.map { it.format.name }.distinct().joinToString("/").ifEmpty { "?" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldSection(state: EditorState, vm: EditorViewModel) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { sheetOpen = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add field")
        }
    }

    if (sheetOpen) {
        val inFiles = state.availableKeys
        val others = OFFERABLE_FIELDS.filter { it !in state.displayedKeys && it !in inFiles }
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Add a tag field to the editor",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                if (inFiles.isNotEmpty()) {
                    MenuHeader("Found in these files")
                    inFiles.forEach { key ->
                        ListItem(
                            headlineContent = { Text(fieldLabel(key)) },
                            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                            modifier = Modifier.clickable {
                                vm.addField(key)
                                sheetOpen = false
                            },
                        )
                    }
                    HorizontalDivider()
                }
                MenuHeader("All standard fields")
                others.forEach { key ->
                    ListItem(
                        headlineContent = { Text(fieldLabel(key)) },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.clickable {
                            vm.addField(key)
                            sheetOpen = false
                        },
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
