package com.mp3tag.mobile.ui.replaygain

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.replaygain.LoudnessAnalyzer
import com.mp3tag.mobile.data.replaygain.TrackLoudness
import com.mp3tag.mobile.data.tag.TagEngine
import com.mp3tag.mobile.data.tag.WriteOptions
import com.mp3tag.mobile.data.tag.WriteRequest
import com.mp3tag.mobile.ui.browser.BrowserScreen
import com.mp3tag.mobile.ui.browser.BrowserViewModel
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo
import com.mp3tag.mobile.ui.editor.SaveOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReplayGainScreen(
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val browserVm: BrowserViewModel = viewModel(key = "replaygain-browser")
    var sessionPaths by rememberSaveable { mutableStateOf<List<String>?>(null) }

    val activePaths = sessionPaths
    if (activePaths == null) {
        BrowserScreen(
            onBackHome = onBack,
            onEditFiles = { paths -> sessionPaths = paths },
            onOpenSettings = onOpenSettings,
            viewModel = browserVm,
            actionLabel = "Analyze",
            actionIcon = Icons.Filled.GraphicEq,
            folderActionLabel = "Analyze all audio in this folder",
            hintText = "Tap to select · long-press a file or folder to analyze it",
        )
    } else {
        ReplayGainEditorView(
            paths = activePaths,
            onBackToBrowser = {
                sessionPaths = null
                browserVm.clearSelection()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReplayGainEditorView(
    paths: List<String>,
    onBackToBrowser: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val windowInfo = rememberAdaptiveWindowInfo()

    var tracks by remember(paths) { mutableStateOf<List<TrackLoudness>>(emptyList()) }
    var failedPaths by remember(paths) { mutableStateOf<List<String>>(emptyList()) }
    var excludedPaths by remember(paths) { mutableStateOf<Set<String>>(emptySet()) }
    var albumGain by remember(paths) { mutableStateOf<Double?>(null) }
    var albumLufs by remember(paths) { mutableStateOf<Double?>(null) }
    var albumPeakLinear by remember(paths) { mutableStateOf<Double?>(null) }

    var analyzing by remember(paths) { mutableStateOf(false) }
    var progressCurrent by remember(paths) { mutableIntStateOf(0) }
    var progressTotal by remember(paths) { mutableIntStateOf(paths.size) }
    var currentFileName by remember(paths) { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    var writeTrackGain by rememberSaveable { mutableStateOf(true) }
    var writeAlbumGain by rememberSaveable { mutableStateOf(true) }

    var saving by remember { mutableStateOf(false) }
    var saveProgressText by remember { mutableStateOf<String?>(null) }
    var outcomes by remember { mutableStateOf<List<SaveOutcome>?>(null) }

    var filesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var gainSheetOpen by rememberSaveable { mutableStateOf(false) }

    fun recomputeAlbumMetrics(currentTracks: List<TrackLoudness>, currentExcluded: Set<String>) {
        val included = currentTracks.filter { it.path !in currentExcluded }
        if (included.isEmpty()) {
            albumGain = null
            albumLufs = null
            albumPeakLinear = null
        } else {
            albumGain = included.map { it.gainDb }.average()
            albumLufs = included.map { it.lufs }.average()
            albumPeakLinear = included.maxOfOrNull { 10.0.pow(it.peakDb / 20.0) }
        }
    }

    fun startScan() {
        scanJob?.cancel()
        analyzing = true
        tracks = emptyList()
        failedPaths = emptyList()
        progressCurrent = 0
        progressTotal = paths.size
        currentFileName = paths.firstOrNull()?.let { File(it).name }
        albumGain = null
        albumLufs = null
        albumPeakLinear = null

        scanJob = scope.launch {
            val analyzedList = mutableListOf<TrackLoudness>()
            val failedList = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for ((idx, path) in paths.withIndex()) {
                    if (!isActive) break
                    val file = File(path)
                    withContext(Dispatchers.Main) {
                        currentFileName = file.name
                    }
                    val result = LoudnessAnalyzer.analyzeFile(context, file)
                    if (result != null) {
                        analyzedList.add(result)
                    } else {
                        failedList.add(path)
                    }
                    val snapshotTracks = analyzedList.toList()
                    val snapshotFailed = failedList.toList()
                    withContext(Dispatchers.Main) {
                        progressCurrent = idx + 1
                        tracks = snapshotTracks
                        failedPaths = snapshotFailed
                        recomputeAlbumMetrics(snapshotTracks, excludedPaths)
                    }
                }
            }
            analyzing = false
            currentFileName = null
        }
    }

    LaunchedEffect(paths) {
        startScan()
    }

    val requestExit: () -> Unit = {
        scanJob?.cancel()
        onBackToBrowser()
    }
    BackHandler { requestExit() }

    val includedTracks = remember(tracks, excludedPaths) {
        tracks.filter { it.path !in excludedPaths }
    }
    val canSave = !analyzing && !saving && includedTracks.isNotEmpty() && (writeTrackGain || writeAlbumGain)

    val triggerSave: () -> Unit = {
        if (canSave) {
            saving = true
            saveProgressText = "Saving ReplayGain tags…"
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    val options = WriteOptions(id3Version = prefs.id3Version)
                    val targetAlbumGain = albumGain
                    val targetAlbumPeak = albumPeakLinear ?: 1.0
                    includedTracks.mapIndexed { idx, t ->
                        withContext(Dispatchers.Main) {
                            saveProgressText = "Writing ${idx + 1} / ${includedTracks.size}: ${File(t.path).name}"
                        }
                        val file = File(t.path)
                        val customFields = LinkedHashMap<String, String>()
                        if (writeTrackGain) {
                            customFields["REPLAYGAIN_TRACK_GAIN"] = formatSignedDb(t.gainDb)
                            customFields["REPLAYGAIN_TRACK_PEAK"] = formatLinearPeak(10.0.pow(t.peakDb / 20.0))
                        }
                        if (writeAlbumGain && targetAlbumGain != null) {
                            customFields["REPLAYGAIN_ALBUM_GAIN"] = formatSignedDb(targetAlbumGain)
                            customFields["REPLAYGAIN_ALBUM_PEAK"] = formatLinearPeak(targetAlbumPeak)
                        }
                        runCatching {
                            TagEngine.write(
                                file = file,
                                request = WriteRequest(customFields = customFields),
                                options = options,
                            )
                        }.fold(
                            onSuccess = { SaveOutcome(file, success = true, message = null) },
                            onFailure = { SaveOutcome(file, success = false, message = it.message) },
                        )
                    }
                }
                saving = false
                saveProgressText = null
                outcomes = results
            }
        }
    }

    val headerSubtitle = when {
        analyzing -> "Analyzing $progressCurrent of $progressTotal file(s)…"
        excludedPaths.isEmpty() -> "${tracks.size} file(s) • ${albumGain?.let { "Album ${formatSignedDb(it)}" } ?: "No results"}"
        else -> "${includedTracks.size} of ${tracks.size} files • ${albumGain?.let { "Album ${formatSignedDb(it)}" } ?: "No results"}"
    }

    val toggleTrackIncluded: (String) -> Unit = { path ->
        val next = if (path in excludedPaths) excludedPaths - path else excludedPaths + path
        excludedPaths = next
        recomputeAlbumMetrics(tracks, next)
    }

    val includeAllFiles: () -> Unit = {
        excludedPaths = emptySet()
        recomputeAlbumMetrics(tracks, emptySet())
    }

    val excludeAllFiles: () -> Unit = {
        val next = tracks.map { it.path }.toSet()
        excludedPaths = next
        recomputeAlbumMetrics(tracks, next)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { filesSheetOpen = true },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ReplayGain")
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Choose files in scan",
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        Text(
                            text = headerSubtitle,
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
                        onClick = { startScan() },
                        enabled = !analyzing && !saving,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Re-scan")
                    }
                    IconButton(
                        onClick = triggerSave,
                        enabled = canSave,
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save ReplayGain tags")
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
            Column(Modifier.fillMaxSize()) {
                if (analyzing) {
                    ScanProgressBanner(
                        current = progressCurrent,
                        total = progressTotal,
                        currentFileName = currentFileName,
                        onCancel = {
                            scanJob?.cancel()
                            analyzing = false
                        },
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (windowInfo.useTwoPane) {
                        Row(Modifier.fillMaxSize()) {
                            ReplayGainSidePane(
                                totalFiles = paths.size,
                                analyzedCount = tracks.size,
                                includedCount = includedTracks.size,
                                albumLufs = albumLufs,
                                albumGain = albumGain,
                                albumPeakLinear = albumPeakLinear,
                                writeTrackGain = writeTrackGain,
                                writeAlbumGain = writeAlbumGain,
                                onToggleTrackGain = { writeTrackGain = !writeTrackGain },
                                onToggleAlbumGain = { writeAlbumGain = !writeAlbumGain },
                                onOpenGainSheet = { gainSheetOpen = true },
                                onOpenFilesSheet = { filesSheetOpen = true },
                                canSave = canSave,
                                onSave = triggerSave,
                                modifier = Modifier
                                    .width(windowInfo.sidePaneWidth)
                                    .fillMaxHeight(),
                            )
                            VerticalDivider()
                            ReplayGainTracksPane(
                                tracks = tracks,
                                failedPaths = failedPaths,
                                excludedPaths = excludedPaths,
                                onToggleTrack = toggleTrackIncluded,
                                onIncludeAll = includeAllFiles,
                                onExcludeAll = excludeAllFiles,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item {
                                ReplayGainSummaryCard(
                                    analyzedCount = tracks.size,
                                    totalFiles = paths.size,
                                    albumLufs = albumLufs,
                                    albumGain = albumGain,
                                    albumPeakLinear = albumPeakLinear,
                                )
                            }

                            item {
                                GainModePickerField(
                                    writeTrackGain = writeTrackGain,
                                    writeAlbumGain = writeAlbumGain,
                                    onOpenSheet = { gainSheetOpen = true },
                                )
                            }

                            item {
                                GainOptionsCard(
                                    writeTrackGain = writeTrackGain,
                                    writeAlbumGain = writeAlbumGain,
                                    albumGain = albumGain,
                                    albumPeakLinear = albumPeakLinear,
                                    sampleTrack = includedTracks.firstOrNull() ?: tracks.firstOrNull(),
                                    onToggleTrackGain = { writeTrackGain = !writeTrackGain },
                                    onToggleAlbumGain = { writeAlbumGain = !writeAlbumGain },
                                )
                            }

                            if (tracks.isNotEmpty()) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    ) {
                                        Text(
                                            text = "Analyzed files (${includedTracks.size} of ${tracks.size} selected)",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = includeAllFiles) { Text("Select all") }
                                        TextButton(onClick = excludeAllFiles) { Text("Clear") }
                                    }
                                }

                                items(
                                    items = tracks,
                                    key = { "track:" + it.path },
                                ) { track ->
                                    val selected = track.path !in excludedPaths
                                    AnalyzedTrackRow(
                                        track = track,
                                        selected = selected,
                                        onToggle = { toggleTrackIncluded(track.path) },
                                    )
                                }
                            }

                            if (failedPaths.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Could not analyze (${failedPaths.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                items(
                                    items = failedPaths,
                                    key = { "failed:$it" },
                                ) { path ->
                                    ListItem(
                                        headlineContent = {
                                            Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        supportingContent = {
                                            Text(
                                                "Unsupported or unreadable audio stream",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                    )
                                }
                            }

                            item {
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = triggerSave,
                                    enabled = canSave,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save ReplayGain to ${includedTracks.size} file(s)")
                                }
                                Spacer(Modifier.navigationBarsPadding().height(16.dp))
                            }
                        }
                    }
                }
            }

            if (saving) {
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
                            saveProgressText?.let { progress ->
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

    // ModalBottomSheet for selecting Gain mode (matching LabeledField's value picker in EditorScreen)
    if (gainSheetOpen) {
        ModalBottomSheet(onDismissRequest = { gainSheetOpen = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Gain tags to write — select options",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = {
                        writeTrackGain = true
                        writeAlbumGain = true
                    }) { Text("Select all") }
                    TextButton(onClick = {
                        writeTrackGain = false
                        writeAlbumGain = false
                    }) { Text("Deselect all") }
                }

                ListItem(
                    headlineContent = { Text("Track gain") },
                    supportingContent = {
                        Text(
                            "Writes REPLAYGAIN_TRACK_GAIN & REPLAYGAIN_TRACK_PEAK per file",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                    },
                    trailingContent = {
                        Checkbox(checked = writeTrackGain, onCheckedChange = null)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (writeTrackGain) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    modifier = Modifier.clickable { writeTrackGain = !writeTrackGain },
                )

                ListItem(
                    headlineContent = { Text("Album gain") },
                    supportingContent = {
                        Text(
                            albumGain?.let {
                                "Writes REPLAYGAIN_ALBUM_GAIN (${formatSignedDb(it)}) & REPLAYGAIN_ALBUM_PEAK"
                            } ?: "Writes REPLAYGAIN_ALBUM_GAIN & REPLAYGAIN_ALBUM_PEAK across selection",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Filled.Album, contentDescription = null)
                    },
                    trailingContent = {
                        Checkbox(checked = writeAlbumGain, onCheckedChange = null)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (writeAlbumGain) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    modifier = Modifier.clickable { writeAlbumGain = !writeAlbumGain },
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Quick presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                listOf(
                    Triple("Track & Album gain (Recommended)", true, true),
                    Triple("Track gain only", true, false),
                    Triple("Album gain only", false, true),
                ).forEach { (label, trackOn, albumOn) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                        modifier = Modifier.clickable {
                            writeTrackGain = trackOn
                            writeAlbumGain = albumOn
                            gainSheetOpen = false
                        },
                    )
                }

                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }

    // ModalBottomSheet for selecting active files (matching EditorScreen's "Files in this edit" sheet)
    if (filesSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filesSheetOpen = false }) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = "Files in this scan",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = includeAllFiles) { Text("Select all") }
                    TextButton(onClick = excludeAllFiles) { Text("Deselect all") }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = (windowInfo.heightDp * 0.55f).dp.coerceIn(180.dp, 460.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    tracks.forEach { track ->
                        val included = track.path !in excludedPaths
                        ListItem(
                            headlineContent = {
                                Text(File(track.path).name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    "Gain ${formatSignedDb(track.gainDb)} • ${String.format(Locale.US, "%.1f LUFS", track.lufs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Filled.MusicNote, contentDescription = null)
                            },
                            trailingContent = {
                                Checkbox(checked = included, onCheckedChange = null)
                            },
                            modifier = Modifier.clickable { toggleTrackIncluded(track.path) },
                        )
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(36.dp))
            }
        }
    }

    // Save outcomes dialog (matching EditorScreen)
    outcomes?.let { resultList ->
        val ok = resultList.count { it.success }
        AlertDialog(
            onDismissRequest = { outcomes = null },
            title = {
                Text(
                    if (ok == resultList.size) "Saved $ok file(s)"
                    else "Saved $ok of ${resultList.size} files",
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    resultList.forEach { outcome ->
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
                TextButton(onClick = { outcomes = null }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ScanProgressBanner(
    current: Int,
    total: Int,
    currentFileName: String?,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Analyzing $current of $total",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    currentFileName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = onCancel) {
                    Text("Stop")
                }
            }
            Spacer(Modifier.height(6.dp))
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ReplayGainSummaryCard(
    analyzedCount: Int,
    totalFiles: Int,
    albumLufs: Double?,
    albumGain: Double?,
    albumPeakLinear: Double?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val topLine = buildList {
                add("$analyzedCount of $totalFiles analyzed")
                add("Target ${String.format(Locale.US, "%.1f LUFS", LoudnessAnalyzer.TARGET_LUFS)}")
                albumLufs?.let { add("Album ${String.format(Locale.US, "%.1f LUFS", it)}") }
            }.joinToString(" • ")
            Text(topLine, style = MaterialTheme.typography.bodySmall)
            if (albumGain != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Album gain: ${formatSignedDb(albumGain)} • Album peak: ${formatLinearPeak(albumPeakLinear ?: 1.0)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun GainModePickerField(
    writeTrackGain: Boolean,
    writeAlbumGain: Boolean,
    onOpenSheet: () -> Unit,
) {
    val summaryText = when {
        writeTrackGain && writeAlbumGain -> "Track gain & Album gain"
        writeTrackGain -> "Track gain only"
        writeAlbumGain -> "Album gain only"
        else -> "None selected"
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = summaryText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Gain tags to write (2 options)") },
            supportingText = {
                Text("Tap to choose Track gain, Album gain, or both")
            },
            trailingIcon = {
                IconButton(onClick = onOpenSheet) {
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "Choose gain tags to write",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onOpenSheet),
        )
    }
}

@Composable
private fun GainOptionsCard(
    writeTrackGain: Boolean,
    writeAlbumGain: Boolean,
    albumGain: Double?,
    albumPeakLinear: Double?,
    sampleTrack: TrackLoudness?,
    onToggleTrackGain: () -> Unit,
    onToggleAlbumGain: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Tag fields to write",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            ListItem(
                headlineContent = { Text("Track gain") },
                supportingContent = {
                    val preview = sampleTrack?.let {
                        "REPLAYGAIN_TRACK_GAIN (${formatSignedDb(it.gainDb)}) • REPLAYGAIN_TRACK_PEAK (${formatLinearPeak(10.0.pow(it.peakDb / 20.0))})"
                    } ?: "REPLAYGAIN_TRACK_GAIN & REPLAYGAIN_TRACK_PEAK (per-file loudness)"
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Icon(
                        if (writeTrackGain) Icons.Filled.MusicNote else Icons.Filled.Audiotrack,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Checkbox(checked = writeTrackGain, onCheckedChange = { onToggleTrackGain() })
                },
                colors = ListItemDefaults.colors(
                    containerColor = if (writeTrackGain) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                ),
                modifier = Modifier.clickable(onClick = onToggleTrackGain),
            )
            ListItem(
                headlineContent = { Text("Album gain") },
                supportingContent = {
                    val preview = albumGain?.let {
                        "REPLAYGAIN_ALBUM_GAIN (${formatSignedDb(it)}) • REPLAYGAIN_ALBUM_PEAK (${formatLinearPeak(albumPeakLinear ?: 1.0)})"
                    } ?: "REPLAYGAIN_ALBUM_GAIN & REPLAYGAIN_ALBUM_PEAK (shared album loudness)"
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Album,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Checkbox(checked = writeAlbumGain, onCheckedChange = { onToggleAlbumGain() })
                },
                colors = ListItemDefaults.colors(
                    containerColor = if (writeAlbumGain) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                ),
                modifier = Modifier.clickable(onClick = onToggleAlbumGain),
            )
        }
    }
}

@Composable
private fun ReplayGainSidePane(
    totalFiles: Int,
    analyzedCount: Int,
    includedCount: Int,
    albumLufs: Double?,
    albumGain: Double?,
    albumPeakLinear: Double?,
    writeTrackGain: Boolean,
    writeAlbumGain: Boolean,
    onToggleTrackGain: () -> Unit,
    onToggleAlbumGain: () -> Unit,
    onOpenGainSheet: () -> Unit,
    onOpenFilesSheet: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReplayGainSummaryCard(
            analyzedCount = analyzedCount,
            totalFiles = totalFiles,
            albumLufs = albumLufs,
            albumGain = albumGain,
            albumPeakLinear = albumPeakLinear,
        )

        GainModePickerField(
            writeTrackGain = writeTrackGain,
            writeAlbumGain = writeAlbumGain,
            onOpenSheet = onOpenGainSheet,
        )

        GainOptionsCard(
            writeTrackGain = writeTrackGain,
            writeAlbumGain = writeAlbumGain,
            albumGain = albumGain,
            albumPeakLinear = albumPeakLinear,
            sampleTrack = null,
            onToggleTrackGain = onToggleTrackGain,
            onToggleAlbumGain = onToggleAlbumGain,
        )

        if (totalFiles > 1) {
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
                            text = "$includedCount of $analyzedCount files active",
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
            enabled = canSave,
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
private fun ReplayGainTracksPane(
    tracks: List<TrackLoudness>,
    failedPaths: List<String>,
    excludedPaths: Set<String>,
    onToggleTrack: (String) -> Unit,
    onIncludeAll: () -> Unit,
    onExcludeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val includedCount = tracks.count { it.path !in excludedPaths }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (tracks.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Analyzed files ($includedCount of ${tracks.size} selected)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onIncludeAll) { Text("Select all") }
                    TextButton(onClick = onExcludeAll) { Text("Clear") }
                }
            }
            items(
                items = tracks,
                key = { "track:" + it.path },
            ) { track ->
                val selected = track.path !in excludedPaths
                AnalyzedTrackRow(
                    track = track,
                    selected = selected,
                    onToggle = { onToggleTrack(track.path) },
                )
            }
        }
        if (failedPaths.isNotEmpty()) {
            item {
                Text(
                    text = "Could not analyze (${failedPaths.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(
                items = failedPaths,
                key = { "failed:$it" },
            ) { path ->
                ListItem(
                    headlineContent = {
                        Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            "Unsupported or unreadable audio stream",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
        item {
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun AnalyzedTrackRow(
    track: TrackLoudness,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val file = File(track.path)
    val linearPeak = 10.0.pow(track.peakDb / 20.0)
    ListItem(
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = String.format(
                    Locale.US,
                    "Track %s • %.1f LUFS • Peak %s (%.1f dBFS)",
                    formatSignedDb(track.gainDb),
                    track.lufs,
                    formatLinearPeak(linearPeak),
                    track.peakDb,
                ),
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
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier.clickable(onClick = onToggle),
    )
}

private fun formatSignedDb(gainDb: Double): String =
    String.format(Locale.US, "%+.2f dB", gainDb)

private fun formatLinearPeak(peakLinear: Double): String =
    String.format(Locale.US, "%.6f", peakLinear)
