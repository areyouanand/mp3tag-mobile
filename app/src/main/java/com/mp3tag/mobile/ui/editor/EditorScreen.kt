package com.mp3tag.mobile.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.tag.CoverImage
import com.mp3tag.mobile.data.tag.OFFERABLE_FIELDS
import com.mp3tag.mobile.data.tag.TagSnapshot
import com.mp3tag.mobile.util.Format
import com.mp3tag.mobile.util.ImageDecode
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
private fun fieldLabel(key: FieldKey): String =
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
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    paths: List<String>,
    sessionKey: String,
    onDone: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: EditorViewModel = viewModel(
        key = sessionKey,
        factory = EditorViewModel.factory(paths, app),
    )
    val state by vm.state.collectAsState()

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::onCoverPicked)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit tags")
                        Text(
                            text = "${state.paths.size} file(s) • ${formatSummary(state.snapshots)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
        Box(Modifier.fillMaxSize().padding(padding)) {

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        CoverSection(state, vm) {
                            pickCover.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    }

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
                        Button(
                            onClick = vm::save,
                            enabled = state.hasEdits && !state.saving,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save ${state.paths.size} file(s)")
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
                    LoadingIndicator()
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
}

@Composable
private fun CoverSection(state: EditorState, vm: EditorViewModel, pick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverThumbnail(cover = state.pendingCover ?: state.cover, mixed = state.coverMixed)
            Spacer(Modifier.width(16.dp))
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
                Row {
                    FilledTonalButton(onClick = pick) { Text("Replace") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = vm::onCoverRemove) { Text("Remove") }
                    if (state.pendingCover != null || state.coverRemoveRequested) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = vm::onCoverUndo) { Text("Undo") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverThumbnail(cover: CoverImage?, mixed: Boolean) {
    val bitmap: Bitmap? = remember(cover) { cover?.let { ImageDecode.decodeScaled(it.bytes, 512) } }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Cover art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp),
            )
        } else {
            Card(Modifier.size(96.dp)) {
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
    var valueSheetOpen by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = field.text,
        onValueChange = { vm.onFieldChange(key, it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = {
            Text(
                when {
                    field.mixed && options.size >= 2 -> "${fieldLabel(key)} (mixed — ${options.size} values)"
                    field.mixed && options.isNotEmpty() -> "${fieldLabel(key)} (mixed — 1 value)"
                    field.mixed -> "${fieldLabel(key)} (mixed)"
                    else -> fieldLabel(key)
                }
            )
        },
        placeholder = {
            if (field.mixed) Text("leave empty to keep existing values")
        },
        supportingText = {
            if (field.edited && !field.mixed) {
                Text("was: ${field.original.ifEmpty { "(empty)" }}")
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
            Column(Modifier.padding(horizontal = 8.dp)) {
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
            }
        }
    }
}

private fun formatSummary(snapshots: List<TagSnapshot>): String =
    snapshots.map { it.format.name }.distinct().joinToString("/").ifEmpty { "?" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldSection(state: EditorState, vm: EditorViewModel) {
    var sheetOpen by remember { mutableStateOf(false) }
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
                Spacer(Modifier.height(12.dp))
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
