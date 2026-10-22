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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterAlt
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
import androidx.compose.material3.OutlinedButton
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
import com.mp3tag.mobile.data.tag.AudioFormat
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
import kotlin.math.roundToInt

private val REPLAYGAIN_KEYS = setOf(
    "REPLAYGAIN_TRACK_GAIN",
    "REPLAYGAIN_TRACK_PEAK",
    "REPLAYGAIN_ALBUM_GAIN",
    "REPLAYGAIN_ALBUM_PEAK",
    "R128_TRACK_GAIN",
    "R128_ALBUM_GAIN",
)

private data class ExistingRgTags(
    val trackGain: String? = null,
    val trackPeak: String? = null,
    val albumGain: String? = null,
    val albumPeak: String? = null,
    val r128TrackGain: String? = null,
    val r128AlbumGain: String? = null,
) {
    val hasAny: Boolean
        get() = !trackGain.isNullOrBlank() ||
            !albumGain.isNullOrBlank() ||
            !r128TrackGain.isNullOrBlank() ||
            !r128AlbumGain.isNullOrBlank()

    fun summaryLabel(): String? {
        if (!hasAny) return null
        return buildList {
            trackGain?.let { add("Track $it") }
            albumGain?.let { add("Album $it") }
            r128TrackGain?.let { add("R128 track $it (${formatQ78Db(it)})") }
            r128AlbumGain?.let { add("R128 album $it (${formatQ78Db(it)})") }
        }.joinToString(" • ")
    }
}

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

    var existingByPath by remember(paths) { mutableStateOf<Map<String, ExistingRgTags>>(emptyMap()) }
    var tracks by remember(paths) { mutableStateOf<List<TrackLoudness>>(emptyList()) }
    var skippedPaths by remember(paths) { mutableStateOf<List<String>>(emptyList()) }
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

    val hasOpusFiles = remember(paths, excludedPaths) {
        paths.any { it !in excludedPaths && TagEngine.formatFor(File(it)) == AudioFormat.OPUS }
    }

    var writeTrackGain by rememberSaveable { mutableStateOf(true) }
    var writeAlbumGain by rememberSaveable { mutableStateOf(true) }
    var writeOpusR128Gain by rememberSaveable { mutableStateOf(hasOpusFiles) }
    var skipExistingRg by rememberSaveable { mutableStateOf(false) }

    // Opus R128 gain is only meaningful for .opus files. Force it off when no
    // .opus files are in the active selection, but remember the user's choice
    // when .opus files are present (across recompositions and process death).
    LaunchedEffect(hasOpusFiles) {
        if (!hasOpusFiles) writeOpusR128Gain = false
    }

    var saving by remember { mutableStateOf(false) }
    var saveProgressText by remember { mutableStateOf<String?>(null) }
    var outcomes by remember { mutableStateOf<List<SaveOutcome>?>(null) }

    var filesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var gainSheetOpen by rememberSaveable { mutableStateOf(false) }
    var toolsSheetOpen by rememberSaveable { mutableStateOf(false) }

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

    suspend fun loadExistingTagsMap(): Map<String, ExistingRgTags> = withContext(Dispatchers.IO) {
        paths.associateWith { path ->
            val raw = TagEngine.readCustomFields(File(path), REPLAYGAIN_KEYS)
            ExistingRgTags(
                trackGain = raw["REPLAYGAIN_TRACK_GAIN"],
                trackPeak = raw["REPLAYGAIN_TRACK_PEAK"],
                albumGain = raw["REPLAYGAIN_ALBUM_GAIN"],
                albumPeak = raw["REPLAYGAIN_ALBUM_PEAK"],
                r128TrackGain = raw["R128_TRACK_GAIN"],
                r128AlbumGain = raw["R128_ALBUM_GAIN"],
            )
        }
    }

    fun startScan(skipAlreadyTagged: Boolean = skipExistingRg) {
        scanJob?.cancel()
        analyzing = true
        tracks = emptyList()
        skippedPaths = emptyList()
        failedPaths = emptyList()
        progressCurrent = 0
        progressTotal = paths.size
        currentFileName = paths.firstOrNull()?.let { File(it).name }
        albumGain = null
        albumLufs = null
        albumPeakLinear = null

        scanJob = scope.launch {
            val existingMap = loadExistingTagsMap()
            existingByPath = existingMap

            val analyzedList = mutableListOf<TrackLoudness>()
            val skippedList = mutableListOf<String>()
            val failedList = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                for ((idx, path) in paths.withIndex()) {
                    if (!isActive) break
                    val file = File(path)
                    withContext(Dispatchers.Main) {
                        currentFileName = file.name
                    }
                    val existing = existingMap[path]
                    if (skipAlreadyTagged && existing?.hasAny == true) {
                        skippedList.add(path)
                    } else {
                        val result = LoudnessAnalyzer.analyzeFile(context, file)
                        if (result != null) {
                            analyzedList.add(result)
                        } else {
                            failedList.add(path)
                        }
                    }
                    val snapshotTracks = analyzedList.toList()
                    val snapshotSkipped = skippedList.toList()
                    val snapshotFailed = failedList.toList()
                    withContext(Dispatchers.Main) {
                        progressCurrent = idx + 1
                        tracks = snapshotTracks
                        skippedPaths = snapshotSkipped
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
        startScan(skipExistingRg)
    }

    val requestExit: () -> Unit = {
        scanJob?.cancel()
        onBackToBrowser()
    }
    BackHandler { requestExit() }

    val includedTracks = remember(tracks, excludedPaths) {
        tracks.filter { it.path !in excludedPaths }
    }
    val activePathsForRemove = remember(paths, excludedPaths) {
        paths.filter { it !in excludedPaths }
    }
    val existingTaggedCount = remember(existingByPath) {
        existingByPath.values.count { it.hasAny }
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
                        val isOpus = TagEngine.formatFor(file) == AudioFormat.OPUS
                        val customFields = LinkedHashMap<String, String>()
                        if (writeTrackGain) {
                            customFields["REPLAYGAIN_TRACK_GAIN"] = formatSignedDb(t.gainDb)
                            customFields["REPLAYGAIN_TRACK_PEAK"] = formatLinearPeak(10.0.pow(t.peakDb / 20.0))
                            if (isOpus && writeOpusR128Gain) {
                                customFields["R128_TRACK_GAIN"] = toOpusR128Q78(t.gainDb)
                            }
                        }
                        if (writeAlbumGain && targetAlbumGain != null) {
                            customFields["REPLAYGAIN_ALBUM_GAIN"] = formatSignedDb(targetAlbumGain)
                            customFields["REPLAYGAIN_ALBUM_PEAK"] = formatLinearPeak(targetAlbumPeak)
                            if (isOpus && writeOpusR128Gain) {
                                customFields["R128_ALBUM_GAIN"] = toOpusR128Q78(targetAlbumGain)
                            }
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
                existingByPath = loadExistingTagsMap()
                saving = false
                saveProgressText = null
                outcomes = results
            }
        }
    }

    val triggerRemoveReplayGainTags: () -> Unit = {
        if (!analyzing && !saving && activePathsForRemove.isNotEmpty()) {
            saving = true
            saveProgressText = "Removing ReplayGain tags…"
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    val options = WriteOptions(id3Version = prefs.id3Version)
                    val clearMap = REPLAYGAIN_KEYS.associateWith { "" }
                    activePathsForRemove.mapIndexed { idx, path ->
                        val file = File(path)
                        withContext(Dispatchers.Main) {
                            saveProgressText = "Removing ${idx + 1} / ${activePathsForRemove.size}: ${file.name}"
                        }
                        runCatching {
                            TagEngine.write(
                                file = file,
                                request = WriteRequest(customFields = clearMap),
                                options = options,
                            )
                        }.fold(
                            onSuccess = { SaveOutcome(file, success = true, message = null) },
                            onFailure = { SaveOutcome(file, success = false, message = it.message) },
                        )
                    }
                }
                existingByPath = loadExistingTagsMap()
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
        val next = paths.toSet()
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
                        onClick = { startScan(skipExistingRg) },
                        enabled = !analyzing && !saving,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Re-scan")
                    }
                    IconButton(
                        onClick = { toolsSheetOpen = true },
                        enabled = !saving,
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = "ReplayGain tools")
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
                                skippedCount = skippedPaths.size,
                                existingTaggedCount = existingTaggedCount,
                                includedCount = includedTracks.size,
                                albumLufs = albumLufs,
                                albumGain = albumGain,
                                albumPeakLinear = albumPeakLinear,
                                writeTrackGain = writeTrackGain,
                                writeAlbumGain = writeAlbumGain,
                                writeOpusR128Gain = writeOpusR128Gain,
                                hasOpusFiles = hasOpusFiles,
                                skipExistingRg = skipExistingRg,
                                onOpenGainSheet = { gainSheetOpen = true },
                                onOpenFilesSheet = { filesSheetOpen = true },
                                canSave = canSave,
                                onSave = triggerSave,
                                canRemove = !analyzing && !saving && existingTaggedCount > 0,
                                onRemoveTags = triggerRemoveReplayGainTags,
                                modifier = Modifier
                                    .width(windowInfo.sidePaneWidth)
                                    .fillMaxHeight(),
                            )
                            VerticalDivider()
                            ReplayGainTracksPane(
                                tracks = tracks,
                                skippedPaths = skippedPaths,
                                failedPaths = failedPaths,
                                existingByPath = existingByPath,
                                excludedPaths = excludedPaths,
                                writeOpusR128Gain = writeOpusR128Gain,
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
                                    skippedCount = skippedPaths.size,
                                    existingTaggedCount = existingTaggedCount,
                                    totalFiles = paths.size,
                                    albumLufs = albumLufs,
                                    albumGain = albumGain,
                                    albumPeakLinear = albumPeakLinear,
                                    hasOpusFiles = hasOpusFiles,
                                    writeOpusR128Gain = writeOpusR128Gain,
                                )
                            }

                            item {
                                GainModePickerField(
                                    writeTrackGain = writeTrackGain,
                                    writeAlbumGain = writeAlbumGain,
                                    writeOpusR128Gain = writeOpusR128Gain,
                                    hasOpusFiles = hasOpusFiles,
                                    skipExistingRg = skipExistingRg,
                                    onOpenSheet = { gainSheetOpen = true },
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
                                        existing = existingByPath[track.path],
                                        selected = selected,
                                        showOpusR128 = writeOpusR128Gain && TagEngine.formatFor(File(track.path)) == AudioFormat.OPUS,
                                        onToggle = { toggleTrackIncluded(track.path) },
                                    )
                                }
                            }

                            if (skippedPaths.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Skipped — already have ReplayGain (${skippedPaths.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                items(
                                    items = skippedPaths,
                                    key = { "skipped:$it" },
                                ) { path ->
                                    val existing = existingByPath[path]
                                    ListItem(
                                        headlineContent = {
                                            Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        supportingContent = {
                                            Text(
                                                existing?.summaryLabel() ?: "Existing ReplayGain kept",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        },
                                        leadingContent = {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        },
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
                                if (existingTaggedCount > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = triggerRemoveReplayGainTags,
                                        enabled = !analyzing && !saving && activePathsForRemove.isNotEmpty(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                    ) {
                                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Remove ReplayGain tags")
                                    }
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

    // ModalBottomSheet for selecting Gain tags to write & scan options (matching EditorScreen's value picker sheet)
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
                        writeOpusR128Gain = hasOpusFiles
                    }) { Text("Select all") }
                    TextButton(onClick = {
                        writeTrackGain = false
                        writeAlbumGain = false
                        writeOpusR128Gain = false
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

                val opusChecked = hasOpusFiles && writeOpusR128Gain
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Opus R128 gain (.opus files)",
                            color = if (hasOpusFiles) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (hasOpusFiles) {
                                "Writes RFC 7845 R128_TRACK_GAIN / R128_ALBUM_GAIN (Q7.8 at -23 LUFS reference)"
                            } else {
                                "Disabled — only available when .opus files are selected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasOpusFiles) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = if (hasOpusFiles) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                        )
                    },
                    trailingContent = {
                        Checkbox(
                            checked = opusChecked,
                            onCheckedChange = null,
                            enabled = hasOpusFiles,
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (opusChecked) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    modifier = if (hasOpusFiles) {
                        Modifier.clickable { writeOpusR128Gain = !writeOpusR128Gain }
                    } else {
                        Modifier
                    },
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Scan behavior",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                ListItem(
                    headlineContent = { Text("Skip files that already have ReplayGain") },
                    supportingContent = {
                        Text(
                            "Files with existing REPLAYGAIN_* or R128_* tags are skipped when scanning ($existingTaggedCount found)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Filled.FilterAlt, contentDescription = null)
                    },
                    trailingContent = {
                        Checkbox(checked = skipExistingRg, onCheckedChange = null)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (skipExistingRg) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    modifier = Modifier.clickable {
                        val next = !skipExistingRg
                        skipExistingRg = next
                        if (!analyzing) {
                            startScan(next)
                        }
                    },
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

    // Tools sheet (matching EditorScreen's Tools sheet)
    if (toolsSheetOpen) {
        ModalBottomSheet(onDismissRequest = { toolsSheetOpen = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "ReplayGain tools",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                ListItem(
                    headlineContent = { Text("Re-scan all files (include already tagged)") },
                    leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    modifier = Modifier.clickable {
                        toolsSheetOpen = false
                        skipExistingRg = false
                        startScan(skipAlreadyTagged = false)
                    },
                )
                ListItem(
                    headlineContent = { Text("Scan only files missing ReplayGain") },
                    leadingContent = { Icon(Icons.Filled.FilterAlt, contentDescription = null) },
                    modifier = Modifier.clickable {
                        toolsSheetOpen = false
                        skipExistingRg = true
                        startScan(skipAlreadyTagged = true)
                    },
                )
                ListItem(
                    headlineContent = { Text("Remove ReplayGain & R128 tags from active files") },
                    supportingContent = {
                        Text(
                            "Clears REPLAYGAIN_TRACK_*, REPLAYGAIN_ALBUM_*, and R128_*_GAIN tags",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    modifier = Modifier.clickable {
                        toolsSheetOpen = false
                        triggerRemoveReplayGainTags()
                    },
                )
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
                    if (existingTaggedCount > 0) {
                        TextButton(onClick = {
                            val tagged = existingByPath.filterValues { it.hasAny }.keys
                            excludedPaths = tagged
                            recomputeAlbumMetrics(tracks, tagged)
                        }) { Text("Deselect already tagged") }
                    }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = (windowInfo.heightDp * 0.55f).dp.coerceIn(180.dp, 460.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    val trackByPath = remember(tracks) { tracks.associateBy { it.path } }
                    paths.forEach { path ->
                        val included = path !in excludedPaths
                        val track = trackByPath[path]
                        val existing = existingByPath[path]
                        val subtitle = buildList {
                            track?.let {
                                add("New: ${formatSignedDb(it.gainDb)} (${String.format(Locale.US, "%.1f LUFS", it.lufs)})")
                            }
                            existing?.summaryLabel()?.let {
                                add("Existing: $it")
                            }
                        }.joinToString(" • ").ifEmpty { "Pending scan" }

                        ListItem(
                            headlineContent = {
                                Text(File(path).name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                            },
                            leadingContent = {
                                Icon(Icons.Filled.MusicNote, contentDescription = null)
                            },
                            trailingContent = {
                                Checkbox(checked = included, onCheckedChange = null)
                            },
                            modifier = Modifier.clickable { toggleTrackIncluded(path) },
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
                    if (ok == resultList.size) "Updated $ok file(s)"
                    else "Updated $ok of ${resultList.size} files",
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
    skippedCount: Int,
    existingTaggedCount: Int,
    totalFiles: Int,
    albumLufs: Double?,
    albumGain: Double?,
    albumPeakLinear: Double?,
    hasOpusFiles: Boolean,
    writeOpusR128Gain: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val topLine = buildList {
                add("$analyzedCount of $totalFiles analyzed")
                if (skippedCount > 0) add("$skippedCount skipped")
                if (existingTaggedCount > 0) add("$existingTaggedCount already tagged")
                add("Target ${String.format(Locale.US, "%.1f LUFS", LoudnessAnalyzer.TARGET_LUFS)}")
                albumLufs?.let { add("Album ${String.format(Locale.US, "%.1f LUFS", it)}") }
            }.joinToString(" • ")
            Text(topLine, style = MaterialTheme.typography.bodySmall)
            if (albumGain != null) {
                Spacer(Modifier.height(4.dp))
                val r128Suffix = if (hasOpusFiles && writeOpusR128Gain) {
                    " • Opus R128: ${toOpusR128Q78(albumGain)} (${formatSignedDb(albumGain - 5.0)})"
                } else {
                    ""
                }
                Text(
                    text = "Album gain: ${formatSignedDb(albumGain)} • Album peak: ${formatLinearPeak(albumPeakLinear ?: 1.0)}$r128Suffix",
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
    writeOpusR128Gain: Boolean,
    hasOpusFiles: Boolean,
    skipExistingRg: Boolean,
    onOpenSheet: () -> Unit,
) {
    val baseText = when {
        writeTrackGain && writeAlbumGain -> "Track gain & Album gain"
        writeTrackGain -> "Track gain only"
        writeAlbumGain -> "Album gain only"
        else -> "None selected"
    }
    val extras = buildList {
        if (hasOpusFiles && writeOpusR128Gain) add("+ Opus R128")
        if (skipExistingRg) add("Skip existing")
    }
    val summaryText = if (extras.isEmpty()) baseText else "$baseText (${extras.joinToString(", ")})"

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = summaryText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Gain tags to write") },
            supportingText = {
                Text("Tap to select Track gain, Album gain, Opus R128, or skip existing")
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
private fun ReplayGainSidePane(
    totalFiles: Int,
    analyzedCount: Int,
    skippedCount: Int,
    existingTaggedCount: Int,
    includedCount: Int,
    albumLufs: Double?,
    albumGain: Double?,
    albumPeakLinear: Double?,
    writeTrackGain: Boolean,
    writeAlbumGain: Boolean,
    writeOpusR128Gain: Boolean,
    hasOpusFiles: Boolean,
    skipExistingRg: Boolean,
    onOpenGainSheet: () -> Unit,
    onOpenFilesSheet: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    canRemove: Boolean,
    onRemoveTags: () -> Unit,
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
            skippedCount = skippedCount,
            existingTaggedCount = existingTaggedCount,
            totalFiles = totalFiles,
            albumLufs = albumLufs,
            albumGain = albumGain,
            albumPeakLinear = albumPeakLinear,
            hasOpusFiles = hasOpusFiles,
            writeOpusR128Gain = writeOpusR128Gain,
        )

        GainModePickerField(
            writeTrackGain = writeTrackGain,
            writeAlbumGain = writeAlbumGain,
            writeOpusR128Gain = writeOpusR128Gain,
            hasOpusFiles = hasOpusFiles,
            skipExistingRg = skipExistingRg,
            onOpenSheet = onOpenGainSheet,
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

        if (existingTaggedCount > 0) {
            OutlinedButton(
                onClick = onRemoveTags,
                enabled = canRemove,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Remove RG tags")
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ReplayGainTracksPane(
    tracks: List<TrackLoudness>,
    skippedPaths: List<String>,
    failedPaths: List<String>,
    existingByPath: Map<String, ExistingRgTags>,
    excludedPaths: Set<String>,
    writeOpusR128Gain: Boolean,
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
                    existing = existingByPath[track.path],
                    selected = selected,
                    showOpusR128 = writeOpusR128Gain && TagEngine.formatFor(File(track.path)) == AudioFormat.OPUS,
                    onToggle = { onToggleTrack(track.path) },
                )
            }
        }
        if (skippedPaths.isNotEmpty()) {
            item {
                Text(
                    text = "Skipped — already have ReplayGain (${skippedPaths.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(
                items = skippedPaths,
                key = { "skipped:$it" },
            ) { path ->
                val existing = existingByPath[path]
                ListItem(
                    headlineContent = {
                        Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            existing?.summaryLabel() ?: "Existing ReplayGain kept",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
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
    existing: ExistingRgTags?,
    selected: Boolean,
    showOpusR128: Boolean,
    onToggle: () -> Unit,
) {
    val file = File(track.path)
    val linearPeak = 10.0.pow(track.peakDb / 20.0)
    val r128Part = if (showOpusR128) {
        " • R128 ${toOpusR128Q78(track.gainDb)} (${formatSignedDb(track.gainDb - 5.0)})"
    } else {
        ""
    }
    val analyzedLine = String.format(
        Locale.US,
        "Track %s • %.1f LUFS • Peak %s (%.1f dBFS)%s",
        formatSignedDb(track.gainDb),
        track.lufs,
        formatLinearPeak(linearPeak),
        track.peakDb,
        r128Part,
    )
    val existingLine = existing?.summaryLabel()

    ListItem(
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    text = analyzedLine,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (existingLine != null) {
                    Text(
                        text = "Existing: $existingLine",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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

/**
 * Converts a ReplayGain 2.0 gain (referenced to -18.0 LUFS) to an RFC 7845 Opus
 * `R128_*_GAIN` Q7.8 fixed-point integer string (referenced to -23.0 LUFS).
 */
private fun toOpusR128Q78(replayGainDb: Double): String {
    val r128GainDb = replayGainDb - 5.0
    val q78 = (r128GainDb * 256.0).roundToInt().coerceIn(-32768, 32767)
    return q78.toString()
}

private fun formatQ78Db(q78Str: String): String {
    val q = q78Str.trim().toIntOrNull() ?: return q78Str
    return formatSignedDb(q / 256.0)
}

private fun formatSignedDb(gainDb: Double): String =
    String.format(Locale.US, "%+.2f dB", gainDb)

private fun formatLinearPeak(peakLinear: Double): String =
    String.format(Locale.US, "%.6f", peakLinear)
