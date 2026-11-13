package com.mp3tag.mobile.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mp3tag.mobile.data.audio.BpmDetector
import com.mp3tag.mobile.data.fs.FolderScanner
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.tag.CoverAction
import com.mp3tag.mobile.data.tag.CoverImage
import com.mp3tag.mobile.data.tag.EDITABLE_FIELDS
import com.mp3tag.mobile.data.tag.MULTI_VALUE_FIELDS
import com.mp3tag.mobile.data.tag.OFFERABLE_FIELDS
import com.mp3tag.mobile.data.tag.TagEngine
import com.mp3tag.mobile.data.tag.TagSnapshot
import com.mp3tag.mobile.data.tag.WriteOptions
import com.mp3tag.mobile.data.tag.WriteRequest
import com.mp3tag.mobile.data.tag.splitMultiValue
import com.mp3tag.mobile.data.tag.sniffImageMime
import com.mp3tag.mobile.util.DeviceProfile
import com.mp3tag.mobile.util.ImageDecode
import com.mp3tag.mobile.util.PatternParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Editor state of one field, mirroring MP3Tag's batch behaviour. */
data class FieldUi(
    val mixed: Boolean,
    val original: String,
    val text: String,
    val edited: Boolean,
    val cleared: Boolean,
)

data class SaveOutcome(val file: File, val success: Boolean, val message: String?)

/** Tags captured before the most recent save, for "Undo last save". */
data class SaveBackup(val snapshots: List<TagSnapshot>)

/** Result of one rename operation. */
data class RenameOutcome(val oldPath: String, val newPath: String?, val error: String?)

data class EditorState(
    val paths: List<String> = emptyList(),
    val loading: Boolean = true,
    val progressText: String? = null,
    val snapshots: List<TagSnapshot> = emptyList(),
    val fields: Map<FieldKey, FieldUi> = emptyMap(),
    val extraKeys: List<FieldKey> = emptyList(),
    val availableKeys: List<FieldKey> = emptyList(),
    val valueOptions: Map<FieldKey, List<String>> = emptyMap(),
    val perFileOverrides: Map<FieldKey, Map<String, String>> = emptyMap(),
    val excludedPaths: Set<String> = emptySet(),
    val undoAvailable: Boolean = false,
    val redoAvailable: Boolean = false,
    val keptPaths: Set<String> = emptySet(),
    val cover: CoverImage? = null,
    val coverMixed: Boolean = false,
    val pendingCover: CoverImage? = null,
    val coverRemoveRequested: Boolean = false,
    val saving: Boolean = false,
    val outcomes: List<SaveOutcome>? = null,
    val renameOutcomes: List<RenameOutcome>? = null,
    val clipboardCount: Int = 0,
    val lastSaveBackup: SaveBackup? = null,
    val statusMessage: String? = null,
) {
    val displayedKeys: List<FieldKey>
        get() = EDITABLE_FIELDS + extraKeys.filter { it !in EDITABLE_FIELDS }

    val overrideKeys: Set<FieldKey>
        get() = perFileOverrides.keys

    val includedSnapshots: List<TagSnapshot>
        get() = snapshots.filter { it.path !in excludedPaths }

    val hasEdits: Boolean
        get() = perFileOverrides.isNotEmpty() || pendingCover != null || coverRemoveRequested
}

class EditorViewModel(
    private val paths: List<String>,
    application: Application,
) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)
    private val draftStore = EditorDraftStore(application)

    private val _state = MutableStateFlow(EditorState(paths = paths))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // Undo / redo history (in-memory; not persisted across process death).
    private data class EditSnapshot(
        val perFileOverrides: Map<FieldKey, Map<String, String>>,
        val excludedPaths: Set<String>,
        val extraKeys: List<FieldKey>,
        val pendingCover: CoverImage?,
        val coverRemoveRequested: Boolean,
    )

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private var reloadJob: Job? = null

    private fun currentSnapshot(): EditSnapshot = EditSnapshot(
        perFileOverrides = _state.value.perFileOverrides,
        excludedPaths = _state.value.excludedPaths,
        extraKeys = _state.value.extraKeys,
        pendingCover = _state.value.pendingCover,
        coverRemoveRequested = _state.value.coverRemoveRequested,
    )

    private fun pushUndo() {
        undoStack.addLast(currentSnapshot())
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        syncAvailability()
    }

    private fun syncAvailability() {
        _state.update { it.copy(undoAvailable = undoStack.isNotEmpty(), redoAvailable = redoStack.isNotEmpty()) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(currentSnapshot())
        val snap = undoStack.removeLast()
        restoreSnapshot(snap)
        syncAvailability()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(currentSnapshot())
        val snap = redoStack.removeLast()
        restoreSnapshot(snap)
        syncAvailability()
    }

    private fun restoreSnapshot(snap: EditSnapshot) {
        _state.update { current ->
            current.buildFrom(current.snapshots, snap.extraKeys, snap.perFileOverrides, snap.excludedPaths)
                .copy(
                    pendingCover = snap.pendingCover,
                    coverRemoveRequested = snap.coverRemoveRequested,
                    keptPaths = emptySet(),
                )
        }
    }

    // ------------------------------------------------------------------
    // Saved rename/tag patterns (MP3Tag-style pattern library)
    // ------------------------------------------------------------------

    val savedPatterns: List<String> get() = prefs.savedPatterns

    /** Saves a used pattern for future tools menus; built-ins and duplicates are ignored. */
    fun savePattern(pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) return
        if (PatternParser.COMMON_PATTERNS.contains(trimmed)) return
        prefs.savedPatterns = (listOf(trimmed) + prefs.savedPatterns).distinct().take(12)
    }

    init {
        // Restore any persisted draft (survives process death / app kill).
        draftStore.load(paths)?.let { draft ->
            _state.update {
                it.copy(
                    perFileOverrides = draft.perFileOverrides,
                    excludedPaths = draft.excludedPaths,
                    extraKeys = draft.extraKeys,
                    pendingCover = draft.pendingCover,
                )
            }
        }
        reload()
        // Persist draft on every state change.
        viewModelScope.launch {
            state.collect { s ->
                if (!s.loading && !s.saving) {
                    draftStore.save(
                        paths = paths,
                        perFileOverrides = s.perFileOverrides,
                        excludedPaths = s.excludedPaths,
                        extraKeys = s.extraKeys,
                        pendingCover = s.pendingCover,
                    )
                }
            }
        }
    }

    /** Shows an additional field row (e.g. a tag found in the selected files). */
    fun addField(key: FieldKey) {
        pushUndo()
        _state.update { current ->
            if (key in current.displayedKeys) {
                current
            } else {
                current.buildFrom(current.snapshots, current.extraKeys + key)
            }
        }
    }

    fun reload(hardReset: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val total = _state.value.paths.size
            _state.update {
                it.copy(
                    loading = true,
                    progressText = if (total > 1) "Reading 0 / $total files…" else null,
                )
            }
            val snapshots = readAll(_state.value.paths) { done, count ->
                if (count > 1) {
                    _state.update { it.copy(progressText = "Reading $done / $count files…") }
                }
            }
            _state.update { current ->
                var updated = current.buildFrom(snapshots, current.extraKeys)
                    .copy(progressText = null)
                if (hardReset) {
                    updated = updated.copy(
                        perFileOverrides = emptyMap(),   // keyed by pre-rename paths — stale
                        pendingCover = null,
                        coverRemoveRequested = false,
                        outcomes = null,
                        excludedPaths = emptySet(),
                        keptPaths = emptySet(),
                    )
                    draftStore.clear(paths)
                }
                updated
            }
        }
    }

    fun cancelReload() {
        reloadJob?.cancel()
        _state.update { it.copy(loading = false, progressText = null) }
    }

    // ------------------------------------------------------------------
    // Phase 2: tags from filename, batch actions, rename from tags
    // ------------------------------------------------------------------

    /**
     * Applies tags parsed from file names. Each file gets its own values,
     * stored as per-file overrides that Save writes to disk.
     */
    fun applyParsedTags(entries: List<Pair<String, Map<FieldKey, String>>>) {
        pushUndo()
        _state.update { current ->
            var overrides = current.perFileOverrides
            for ((path, values) in entries) {
                values.forEach { (key, value) ->
                    overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (path to value)))
                }
            }
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
        }
    }

    /** Applies a batch transform (regex/case) result per file. */
    fun applyTransforms(transforms: List<Triple<String, FieldKey, String>>) {
        pushUndo()
        _state.update { current ->
            var overrides = current.perFileOverrides
            for ((path, key, value) in transforms) {
                overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (path to value)))
            }
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
        }
    }

    /** Renames the selected files according to a tag-based pattern. */
    fun applyRenames(pattern: String, onDone: () -> Unit) {
        val current = _state.value
        if (current.saving || current.loading) return
        val targets = current.includedSnapshots
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, progressText = "Renaming ${targets.size} file(s)…") }
            val outcomes = withContext(Dispatchers.IO) {
                performRenames(targets, pattern)
            }
            _state.update {
                val renamedOldPaths = outcomes.map { it.oldPath }.toSet()
                it.copy(
                    saving = false,
                    progressText = null,
                    renameOutcomes = outcomes,
                    paths = outcomes.map { o -> o.newPath ?: o.oldPath } +
                        current.snapshots.filter { s -> s.path !in renamedOldPaths }.map { s -> s.path },
                )
            }
            reload(hardReset = true)
            onDone()
        }
    }

    fun dismissRenameOutcomes() = _state.update { it.copy(renameOutcomes = null) }

    private fun performRenames(snapshots: List<TagSnapshot>, pattern: String): List<RenameOutcome> {
        val segments = PatternParser.compile(pattern)
            ?: return snapshots.map { RenameOutcome(it.path, null, "Invalid pattern") }
        return snapshots.map { snapshot ->
            try {
                val file = File(snapshot.path)
                val base = PatternParser.buildFileName(segments, snapshot.fields)
                    ?: return@map RenameOutcome(snapshot.path, null, "Missing tag values for pattern")
                val relative = PatternParser.sanitizeRelativePath(base)
                val ext = file.extension.ifBlank { "dat" }
                val parent = file.parentFile
                    ?: return@map RenameOutcome(snapshot.path, null, "No parent folder")
                val targetDir = File(parent, relative.substringBeforeLast('/', ""))
                if (relative.contains('/') && !targetDir.exists() && !targetDir.mkdirs()) {
                    return@map RenameOutcome(snapshot.path, null, "Could not create ${targetDir.name}")
                }
                val leaf = relative.substringAfterLast('/')
                var candidate = "$leaf.$ext"
                var n = 2
                while (targetDir.resolve(candidate).exists() &&
                    targetDir.resolve(candidate).absolutePath != file.absolutePath
                ) {
                    candidate = "$leaf ($n).$ext"
                    n += 1
                }
                val target = targetDir.resolve(candidate)
                if (file.renameTo(target)) {
                    RenameOutcome(snapshot.path, target.absolutePath, null)
                } else {
                    RenameOutcome(snapshot.path, null, "Rename failed")
                }
            } catch (e: Exception) {
                RenameOutcome(snapshot.path, null, e.message ?: "Rename failed")
            }
        }
    }

    fun onFieldChange(key: FieldKey, value: String) {
        pushUndo()
        _state.update { current ->
            val included = current.includedSnapshots
            if (included.isEmpty()) return@update current
            // Cache the edit per file: only the files included NOW receive this change,
            // and it stays cached until Save or Discard.
            var overrides = current.perFileOverrides
            included.forEach { snap ->
                overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (snap.path to value)))
            }
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
        }
    }

    /** Explicit "clear this field" for the currently included files. */
    fun onFieldClear(key: FieldKey) {
        pushUndo()
        _state.update { current ->
            val included = current.includedSnapshots
            if (included.isEmpty()) return@update current
            var overrides = current.perFileOverrides
            included.forEach { snap ->
                overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (snap.path to "")))
            }
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
        }
    }

    /** Removes the pending edit of one field, reverting it to the on-disk value. */
    fun revertField(key: FieldKey) {
        if (key !in _state.value.perFileOverrides) return
        pushUndo()
        _state.update { current ->
            val overrides = current.perFileOverrides - key
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
                .copy(statusMessage = "Reverted ${key.name.lowercase()} to the saved value")
        }
    }

    /** Throws away all cached (unsaved) changes. */
    fun discardChanges() {        pushUndo()
        _state.update { current ->
            current.buildFrom(current.snapshots, current.extraKeys, perFileOverrides = emptyMap())
                .copy(pendingCover = null, coverRemoveRequested = false, keptPaths = emptySet())
        }
        draftStore.clear(paths)
    }

    fun onCoverPicked(uri: Uri) {
        val context = getApplication<Application>()
        val profile = DeviceProfile.get(context, prefs.performanceMode)
        viewModelScope.launch {
            val cover = withContext(Dispatchers.IO) {
                runCatching {
                    val rawBytes = context.contentResolver.openInputStream(uri)
                        ?.use { stream -> stream.readBytes() }
                        ?: return@runCatching null
                    val rawMime = sniffImageMime(rawBytes)
                    val (normBytes, normMime) = ImageDecode.normalizeImportedCover(
                        bytes = rawBytes,
                        mimeType = rawMime,
                        maxDimensionPx = profile.maxImportedCoverPx,
                    )
                    CoverImage(normBytes, normMime)
                }.getOrNull()
            }
            if (cover != null) {
                pushUndo()
                _state.update { it.copy(pendingCover = cover, coverRemoveRequested = false) }
            }
        }
    }

    fun onCoverRemove() {
        pushUndo()
        _state.update { it.copy(coverRemoveRequested = true, pendingCover = null) }
    }

    fun onCoverUndo() {
        pushUndo()
        _state.update { it.copy(pendingCover = null, coverRemoveRequested = false) }
    }

    // ------------------------------------------------------------------
    // Tag clipboard, numbering, validation, covers, CSV, BPM
    // ------------------------------------------------------------------

    private var clipboard: Map<FieldKey, String> = emptyMap()

    /** Copies every non-empty effective field of the first included file. */
    fun copyTags() {
        val current = _state.value
        val first = current.includedSnapshots.firstOrNull() ?: return
        val values = first.fields.filterValues { it.isNotBlank() }
        clipboard = values
        setStatus("Copied ${values.size} field(s) from ${File(first.path).name}")
    }

    /** Applies the copied fields to every included file. */
    fun pasteTags() {
        if (clipboard.isEmpty()) return
        pushUndo()
        _state.update { current ->
            val included = current.includedSnapshots
            var overrides = current.perFileOverrides
            included.forEach { snap ->
                clipboard.forEach { (key, value) ->
                    overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (snap.path to value)))
                }
            }
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
                .copy(statusMessage = "Pasted ${clipboard.size} field(s) to ${included.size} file(s)")
        }
    }

    /** Numbers included files in path order; total/count is optional. */
    fun applyTrackNumbers(start: Int, step: Int, total: Int?) {
        pushUndo()
        _state.update { current ->
            val included = current.includedSnapshots.sortedWith { a, b ->
                FolderScanner.compareNatural(File(a.path).name, File(b.path).name)
            }
            var overrides = current.perFileOverrides
            included.forEachIndexed { index, snap ->
                val number = start + index * step
                overrides = overrides + (FieldKey.TRACK to ((overrides[FieldKey.TRACK] ?: emptyMap()) + (snap.path to number.toString())))
                if (total != null && total > 0) {
                    overrides = overrides + (FieldKey.TRACK_TOTAL to ((overrides[FieldKey.TRACK_TOTAL] ?: emptyMap()) + (snap.path to total.toString())))
                }
            }
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
                .copy(statusMessage = "Numbered ${included.size} file(s) starting at $start")
        }
    }

    fun applyDiscNumbers(start: Int, step: Int, total: Int?) {
        pushUndo()
        _state.update { current ->
            val included = current.includedSnapshots.sortedWith { a, b ->
                FolderScanner.compareNatural(File(a.path).name, File(b.path).name)
            }
            var overrides = current.perFileOverrides
            included.forEachIndexed { index, snap ->
                val number = start + index * step
                overrides = overrides + (FieldKey.DISC_NO to ((overrides[FieldKey.DISC_NO] ?: emptyMap()) + (snap.path to number.toString())))
                if (total != null && total > 0) {
                    overrides = overrides + (FieldKey.DISC_TOTAL to ((overrides[FieldKey.DISC_TOTAL] ?: emptyMap()) + (snap.path to total.toString())))
                }
            }
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
                .copy(statusMessage = "Disc numbers set on ${included.size} file(s)")
        }
    }

    /** Human-readable consistency checks for the current selection. */
    fun validationReport(): List<String> {
        val current = _state.value
        val included = current.includedSnapshots
        if (included.isEmpty()) return listOf("No files included in this edit.")

        fun value(snap: TagSnapshot, key: FieldKey): String =
            current.perFileOverrides[key]?.get(snap.path) ?: snap.fields[key].orEmpty()

        val issues = ArrayList<String>()
        val missingTitle = included.count { value(it, FieldKey.TITLE).isBlank() }
        if (missingTitle > 0) issues += "$missingTitle file(s) have no title."
        val missingAlbum = included.count { value(it, FieldKey.ALBUM).isBlank() }
        if (missingAlbum > 0) issues += "$missingAlbum file(s) have no album."

        val albumArtists = included.map { value(it, FieldKey.ALBUM_ARTIST).trim().lowercase() }
            .filter { it.isNotEmpty() }.distinct()
        if (albumArtists.size > 1) {
            issues += "Album artist differs across the selection (${albumArtists.size} values)."
        }

        included.groupBy { value(it, FieldKey.ALBUM).trim().lowercase() }.forEach { (album, group) ->
            val label = album.ifBlank { "(no album)" }
            val numbers = group.map { value(it, FieldKey.TRACK).trim() }
            val duplicates = numbers.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().filterValues { it > 1 }
            if (duplicates.isNotEmpty()) {
                issues += "$label: duplicate track number(s) ${duplicates.keys.joinToString(", ")}."
            }
            group.forEach { snap ->
                val track = value(snap, FieldKey.TRACK).toIntOrNull()
                val total = value(snap, FieldKey.TRACK_TOTAL).toIntOrNull()
                if (track != null && total != null && track > total) {
                    issues += "${File(snap.path).name}: track $track is greater than total $total."
                }
            }
        }
        if (issues.isEmpty()) issues += "No obvious inconsistencies found."
        return issues
    }

    /** Uses cover.jpg / folder.jpg / front.jpg from the first file's folder. */
    fun useFolderCover() {
        val first = _state.value.includedSnapshots.firstOrNull() ?: return
        viewModelScope.launch {
            val (cover, name) = withContext(Dispatchers.IO) {
                val dir = File(first.path).parentFile
                val candidates = listOf(
                    "cover.jpg", "cover.jpeg", "folder.jpg", "folder.jpeg",
                    "front.jpg", "front.jpeg", "albumart.jpg", "cover.png", "folder.png",
                )
                val file = candidates.asSequence()
                    .map { File(dir, it) }
                    .firstOrNull { it.isFile && it.length() in 1..(20L * 1024 * 1024) }
                    ?: return@withContext null to null
                val bytes = runCatching { file.readBytes() }.getOrNull()
                    ?: return@withContext null to null
                CoverImage(bytes, sniffImageMime(bytes)) to file.name
            }
            if (cover != null) {
                pushUndo()
                _state.update {
                    it.copy(
                        pendingCover = cover,
                        coverRemoveRequested = false,
                        statusMessage = "Cover loaded from $name",
                    )
                }
            } else {
                setStatus("No cover.jpg / folder.jpg found next to ${File(first.path).name}")
            }
        }
    }

    /** Bytes of the cover currently shown (pending edit first, then disk). */
    fun currentCoverBytes(): ByteArray? =
        _state.value.pendingCover?.bytes ?: _state.value.cover?.bytes

    fun currentCoverMime(): String =
        _state.value.pendingCover?.mimeType ?: _state.value.cover?.mimeType ?: "image/jpeg"

    fun detectBpm() {
        val current = _state.value
        if (current.saving || current.loading) return
        val targets = current.includedSnapshots
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, progressText = "Detecting BPM 0 / ${targets.size}…") }
            val results = withContext(Dispatchers.IO) {
                targets.mapIndexedNotNull { index, snap ->
                    val bpm = BpmDetector.detect(File(snap.path))
                    _state.update {
                        it.copy(progressText = "Detecting BPM ${index + 1} / ${targets.size}…")
                    }
                    bpm?.let { snap.path to it.toString() }
                }
            }
            _state.update { state ->
                var overrides = state.perFileOverrides
                results.forEach { (path, bpm) ->
                    overrides = overrides + (
                        FieldKey.BPM to ((overrides[FieldKey.BPM] ?: emptyMap()) + (path to bpm))
                        )
                }
                val extra = if (FieldKey.BPM in state.displayedKeys) {
                    state.extraKeys
                } else {
                    state.extraKeys + FieldKey.BPM
                }
                state.buildFrom(state.snapshots, extra, overrides).copy(
                    saving = false,
                    progressText = null,
                    statusMessage = "BPM detected for ${results.size} of ${targets.size} file(s)",
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // CSV export / import
    // ------------------------------------------------------------------

    fun exportCsv(output: java.io.OutputStream): Int {
        val current = _state.value
        val included = current.includedSnapshots
        val keys = current.displayedKeys
        fun value(snap: TagSnapshot, key: FieldKey): String =
            current.perFileOverrides[key]?.get(snap.path) ?: snap.fields[key].orEmpty()

        java.io.BufferedWriter(output.writer(Charsets.UTF_8)).use { writer ->
            writer.write((listOf("path") + keys.map { it.name }).joinToString(","))
            writer.newLine()
            included.forEach { snap ->
                val cells = listOf(snap.path) + keys.map { key -> value(snap, key) }
                writer.write(cells.joinToString(",") { csvEscape(it) })
                writer.newLine()
            }
        }
        return included.size
    }

    fun importCsv(input: java.io.InputStream): Int {
        val rows = java.io.BufferedReader(input.reader(Charsets.UTF_8)).readLines()
        if (rows.size < 2) return 0
        val header = parseCsvLine(rows.first())
        val pathIndex = header.indexOfFirst { it.equals("path", ignoreCase = true) || it.equals("filename", ignoreCase = true) }
        if (pathIndex < 0) return 0
        val columns = header.mapIndexedNotNull { index, name ->
            runCatching { FieldKey.valueOf(name.trim().uppercase()) }.getOrNull()?.let { index to it }
        }
        val byPath = _state.value.snapshots.associateBy { it.path }
        val byName = _state.value.snapshots.associateBy { File(it.path).name.lowercase() }
        var applied = 0
        pushUndo()
        _state.update { current ->
            var overrides = current.perFileOverrides
            for (row in rows.drop(1)) {
                val cells = parseCsvLine(row)
                if (cells.size <= pathIndex) continue
                val rawPath = cells[pathIndex].trim()
                val snap = byPath[rawPath] ?: byName[File(rawPath).name.lowercase()] ?: continue
                columns.forEach { (index, key) ->
                    val value = cells.getOrNull(index)?.trim() ?: return@forEach
                    overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (snap.path to value)))
                }
                applied++
            }
            current.buildFrom(current.snapshots, current.extraKeys, overrides)
                .copy(statusMessage = "Imported tags for $applied file(s)")
        }
        return applied
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    out.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        out.add(current.toString())
        return out
    }

    // ------------------------------------------------------------------
    // Undo the most recent save
    // ------------------------------------------------------------------

    fun restoreLastSave() {
        val backup = _state.value.lastSaveBackup ?: return
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, progressText = "Restoring previous tags…") }
            withContext(Dispatchers.IO) {
                backup.snapshots.forEach { snap ->
                    val file = File(snap.path)
                    if (!file.isFile) return@forEach
                    runCatching {
                        val cleared = FieldKey.entries
                            .filter { it != FieldKey.COVER_ART }
                            .associateWith { null as String? }
                        TagEngine.write(
                            file = file,
                            request = WriteRequest(
                                fieldValues = cleared + snap.fields,
                                multiFields = snap.multiFields,
                                coverAction = if (snap.cover != null) CoverAction.REPLACE else CoverAction.REMOVE,
                                newCover = snap.cover,
                            ),
                            options = WriteOptions(prefs.id3Version),
                        )
                    }
                }
            }
            _state.update {
                it.copy(
                    saving = false,
                    progressText = null,
                    lastSaveBackup = null,
                    statusMessage = "Restored tags from before the last save",
                )
            }
            reload(hardReset = true)
        }
    }

    fun setStatus(message: String?) = _state.update { it.copy(statusMessage = message) }

    fun save() {
        val current = _state.value
        if (current.saving || current.loading || !current.hasEdits) return
        if (current.includedSnapshots.isEmpty()) return

        val coverAction = when {
            current.pendingCover != null -> CoverAction.REPLACE
            current.coverRemoveRequested -> CoverAction.REMOVE
            else -> CoverAction.NONE
        }
        val options = WriteOptions(id3Version = prefs.id3Version)
        val profile = DeviceProfile.get(getApplication(), prefs.performanceMode)

        fun fieldValuesFor(path: String): Map<FieldKey, String?> = buildMap {
            current.perFileOverrides.forEach { (key, byPath) ->
                byPath[path]?.let { value ->
                    put(key, value.trim().takeIf { it.isNotEmpty() })
                }
            }
        }

        fun multiValuesFor(path: String): Map<FieldKey, List<String>> = buildMap {
            current.perFileOverrides.forEach { (key, byPath) ->
                if (key in MULTI_VALUE_FIELDS) {
                    byPath[path]?.let { raw ->
                        val values = splitMultiValue(raw)
                        if (values.size > 1) put(key, values)
                    }
                }
            }
        }

        viewModelScope.launch {
            val targets = current.snapshots.filter { it.path !in current.excludedPaths }
            val total = targets.size
            val backup = SaveBackup(
                targets.mapNotNull { target -> current.snapshots.firstOrNull { it.path == target.path } },
            )
            _state.update {
                it.copy(
                    saving = true,
                    outcomes = null,
                    progressText = if (total > 1) "Saving 0 / $total files…" else "Saving…",
                )
            }
            val outcomes = withContext(Dispatchers.IO) {
                val sem = Semaphore(profile.ioParallelism.coerceAtMost(4))
                val doneCount = AtomicInteger(0)
                coroutineScope {
                    targets.map { snapshot ->
                        async {
                            sem.withPermit {
                                val res = runCatching {
                                    TagEngine.write(
                                        file = File(snapshot.path),
                                        request = WriteRequest(
                                            fieldValues = fieldValuesFor(snapshot.path),
                                            multiFields = multiValuesFor(snapshot.path),
                                            coverAction = coverAction,
                                            newCover = current.pendingCover,
                                        ),
                                        options = options,
                                    )
                                }.fold(
                                    onSuccess = { SaveOutcome(File(snapshot.path), success = true, message = null) },
                                    onFailure = { SaveOutcome(File(snapshot.path), success = false, it.message) },
                                )
                                val done = doneCount.incrementAndGet()
                                if (total > 1) {
                                    _state.update { it.copy(progressText = "Saving $done / $total files…") }
                                }
                                res
                            }
                        }
                    }.awaitAll()
                }
            }
            // Written files are done; files not written (excluded) keep their cached edits.
            val writtenPaths = targets.map { it.path }.toSet()
            val keptOverrides = current.perFileOverrides
                .mapValues { (_, byPath) -> byPath.filterKeys { it !in writtenPaths } }
                .filterValues { it.isNotEmpty() }
            val keptPaths = keptOverrides.values.flatMap { it.keys }.toSet()
            // Re-read from disk so the editor shows the persisted state.
            val snapshots = readAll(current.snapshots.map { it.path })
            _state.update {
                it.buildFrom(snapshots, it.extraKeys, keptOverrides).copy(
                    saving = false,
                    progressText = null,
                    outcomes = outcomes,
                    pendingCover = null,
                    coverRemoveRequested = false,
                    keptPaths = keptPaths,
                    lastSaveBackup = if (outcomes.any { o -> o.success }) backup else it.lastSaveBackup,
                )
            }
            // Clear the draft if nothing pending remains.
            if (keptPaths.isEmpty()) draftStore.clear(paths)
        }
    }

    fun dismissOutcomes() = _state.update { it.copy(outcomes = null, keptPaths = emptySet()) }

    /**
     * Reads all [paths] on [Dispatchers.IO] with hardware-scaled bounded parallelism
     * and deduplicates identical embedded cover art across files so large album batches
     * do not multiply cover byte arrays in heap on low-RAM phones.
     */
    private suspend fun readAll(
        paths: List<String>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): List<TagSnapshot> = withContext(Dispatchers.IO) {
        val profile = DeviceProfile.get(getApplication(), prefs.performanceMode)
        val sem = Semaphore(profile.ioParallelism)
        val total = paths.size
        val doneCount = AtomicInteger(0)

        val uniqueCovers = HashMap<CoverImage, CoverImage>()
        var retainedCoverBytes = 0L
        val coverLock = Any()

        coroutineScope {
            paths.map { path ->
                async {
                    sem.withPermit {
                        val raw = runCatching { TagEngine.read(File(path)) }.getOrNull()
                        val deduped = if (raw?.cover != null) {
                            val c = raw.cover
                            val canonical = synchronized(coverLock) {
                                val existing = uniqueCovers[c]
                                if (existing != null) {
                                    existing
                                } else if (retainedCoverBytes + c.bytes.size <= profile.maxRetainedCoverBytes) {
                                    uniqueCovers[c] = c
                                    retainedCoverBytes += c.bytes.size
                                    c
                                } else {
                                    // Low-RAM safeguard when many distinct huge covers are loaded at once:
                                    // downscale the preview bytes so we never trigger OutOfMemoryError.
                                    val (smallBytes, smallMime) = ImageDecode.normalizeImportedCover(
                                        bytes = c.bytes,
                                        mimeType = c.mimeType,
                                        maxDimensionPx = profile.thumbnailMaxPx,
                                    )
                                    val compact = CoverImage(smallBytes, smallMime)
                                    uniqueCovers[c] = compact
                                    retainedCoverBytes += compact.bytes.size
                                    compact
                                }
                            }
                            raw.copy(cover = canonical)
                        } else {
                            raw
                        }
                        val done = doneCount.incrementAndGet()
                        onProgress?.invoke(done, total)
                        deduped
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Builds editor state from fresh snapshots. Display values are derived from
     * the per-file cached edits (overrides) layered over the on-disk tags, and
     * are computed over the INCLUDED files only. Pending picks and dialogs are
     * left untouched.
     */
    private fun EditorState.buildFrom(
        snapshots: List<TagSnapshot>,
        extraKeys: List<FieldKey>,
        perFileOverrides: Map<FieldKey, Map<String, String>> = this.perFileOverrides,
        excludedPaths: Set<String> = this.excludedPaths,
    ): EditorState {
        val cleanExtra = extraKeys.filter { it !in EDITABLE_FIELDS }
        val included = snapshots.filter { it.path !in excludedPaths }
        val displayed = EDITABLE_FIELDS + cleanExtra
        fun effectiveValue(snap: TagSnapshot, key: FieldKey): String =
            perFileOverrides[key]?.get(snap.path) ?: snap.fields[key].orEmpty()

        val fields = displayed.associateWith { key ->
            val effective = included.map { effectiveValue(it, key) }.distinct()
            val edited = included.any { perFileOverrides[key]?.containsKey(it.path) == true }
            val original = included.firstOrNull()?.fields?.get(key).orEmpty()
            FieldUi(
                mixed = effective.size > 1,
                original = original,
                text = if (effective.size == 1) effective[0] else "",
                edited = edited,
                cleared = edited && effective.size == 1 && effective[0].isEmpty(),
            )
        }

        // Fast path: when only perFileOverrides changed (e.g. typing in a text field),
        // availableKeys, valueOptions, cover, and coverMixed are invariant because they
        // depend solely on `included` and `displayed`.
        val canReuseSnapshotDerived =
            !this.loading &&
                this.snapshots === snapshots &&
                this.excludedPaths == excludedPaths &&
                this.extraKeys == cleanExtra &&
                this.fields.isNotEmpty()

        if (canReuseSnapshotDerived) {
            return copy(
                loading = false,
                fields = fields,
                perFileOverrides = perFileOverrides,
            )
        }

        // Fields present in the files but not currently displayed, MP3Tag-style.
        val available = included.flatMap { it.fields.keys }
            .distinct()
            .filter { it !in displayed }
            .sortedBy { key ->
                OFFERABLE_FIELDS.indexOf(key).takeIf { index -> index >= 0 }
                    ?: (1_000 + key.ordinal)
            }
        val covers = included.mapNotNull { it.cover }.distinct()
        return copy(
            loading = false,
            snapshots = snapshots,
            excludedPaths = excludedPaths,
            fields = fields,
            perFileOverrides = perFileOverrides,
            extraKeys = cleanExtra,
            availableKeys = available,
            valueOptions = displayed.associateWith { key ->
                included.map { it.fields[key].orEmpty() }
                    .filter { it.isNotBlank() }
                    .distinct()
            },
            cover = covers.firstOrNull(),
            coverMixed = covers.size > 1,
        )
    }

    /** Includes/excludes a file from the current edit session (MP3Tag-style selection). */
    fun toggleFileIncluded(path: String) {
        pushUndo()
        _state.update { current ->
            val excluded = current.excludedPaths.toMutableSet()
            if (!excluded.add(path)) excluded.remove(path)
            current.buildFrom(current.snapshots, current.extraKeys, excludedPaths = excluded)
        }
    }

    fun includeAllFiles() {
        pushUndo()
        _state.update { current ->
            current.buildFrom(current.snapshots, current.extraKeys, excludedPaths = emptySet())
        }
    }

    fun excludeAllFiles() {
        pushUndo()
        _state.update { current ->
            current.buildFrom(
                current.snapshots,
                current.extraKeys,
                excludedPaths = current.snapshots.map { it.path }.toSet(),
            )
        }
    }

    companion object {
        fun factory(paths: List<String>, app: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { EditorViewModel(paths, app) }
            }
    }
}
