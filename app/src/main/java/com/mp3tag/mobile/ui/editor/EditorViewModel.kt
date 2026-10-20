package com.mp3tag.mobile.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.tag.CoverAction
import com.mp3tag.mobile.data.tag.CoverImage
import com.mp3tag.mobile.data.tag.EDITABLE_FIELDS
import com.mp3tag.mobile.data.tag.OFFERABLE_FIELDS
import com.mp3tag.mobile.data.tag.TagEngine
import com.mp3tag.mobile.data.tag.TagSnapshot
import com.mp3tag.mobile.data.tag.WriteOptions
import com.mp3tag.mobile.data.tag.WriteRequest
import com.mp3tag.mobile.data.tag.sniffImageMime
import com.mp3tag.mobile.util.DeviceProfile
import com.mp3tag.mobile.util.ImageDecode
import com.mp3tag.mobile.util.PatternParser
import kotlinx.coroutines.Dispatchers
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
    val cover: CoverImage? = null,
    val coverMixed: Boolean = false,
    val pendingCover: CoverImage? = null,
    val coverRemoveRequested: Boolean = false,
    val saving: Boolean = false,
    val outcomes: List<SaveOutcome>? = null,
    val renameOutcomes: List<RenameOutcome>? = null,
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
    paths: List<String>,
    application: Application,
) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)

    private val _state = MutableStateFlow(EditorState(paths = paths))
    val state: StateFlow<EditorState> = _state.asStateFlow()

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
        reload()
    }

    /** Shows an additional field row (e.g. a tag found in the selected files). */
    fun addField(key: FieldKey) = _state.update { current ->
        if (key in current.displayedKeys) {
            current
        } else {
            current.buildFrom(current.snapshots, current.extraKeys + key)
        }
    }

    fun reload(hardReset: Boolean = false) {
        viewModelScope.launch {
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
                    )
                }
                updated
            }
        }
    }

    // ------------------------------------------------------------------
    // Phase 2: tags from filename, batch actions, rename from tags
    // ------------------------------------------------------------------

    /**
     * Applies tags parsed from file names. Each file gets its own values,
     * stored as per-file overrides that Save writes to disk.
     */
    fun applyParsedTags(entries: List<Pair<String, Map<FieldKey, String>>>) = _state.update { current ->
        var overrides = current.perFileOverrides
        for ((path, values) in entries) {
            values.forEach { (key, value) ->
                overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (path to value)))
            }
        }
        current.buildFrom(current.snapshots, current.extraKeys, overrides)
    }

    /** Applies a batch transform (regex/case) result per file. */
    fun applyTransforms(transforms: List<Triple<String, FieldKey, String>>) = _state.update { current ->
        var overrides = current.perFileOverrides
        for ((path, key, value) in transforms) {
            overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (path to value)))
        }
        current.buildFrom(current.snapshots, current.extraKeys, overrides)
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
                val sanitized = PatternParser.sanitizeFileName(base)
                val ext = file.extension.ifBlank { "dat" }
                val parent = file.parentFile
                    ?: return@map RenameOutcome(snapshot.path, null, "No parent folder")
                var candidate = "$sanitized.$ext"
                var n = 2
                while (parent.resolve(candidate).exists() &&
                    parent.resolve(candidate).absolutePath != file.absolutePath
                ) {
                    candidate = "$sanitized ($n).$ext"
                    n += 1
                }
                val target = parent.resolve(candidate)
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

    fun onFieldChange(key: FieldKey, value: String) = _state.update { current ->
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

    /** Explicit "clear this field" for the currently included files. */
    fun onFieldClear(key: FieldKey) = _state.update { current ->
        val included = current.includedSnapshots
        if (included.isEmpty()) return@update current
        var overrides = current.perFileOverrides
        included.forEach { snap ->
            overrides = overrides + (key to ((overrides[key] ?: emptyMap()) + (snap.path to "")))
        }
        current.buildFrom(current.snapshots, current.extraKeys, overrides)
    }

    /** Throws away all cached (unsaved) changes. */
    fun discardChanges() = _state.update { current ->
        current.buildFrom(current.snapshots, current.extraKeys, perFileOverrides = emptyMap())
            .copy(pendingCover = null, coverRemoveRequested = false)
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
                _state.update { it.copy(pendingCover = cover, coverRemoveRequested = false) }
            }
        }
    }

    fun onCoverRemove() = _state.update { it.copy(coverRemoveRequested = true, pendingCover = null) }

    fun onCoverUndo() = _state.update { it.copy(pendingCover = null, coverRemoveRequested = false) }

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

        viewModelScope.launch {
            val targets = current.snapshots.filter { it.path !in current.excludedPaths }
            val total = targets.size
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
            // Re-read from disk so the editor shows the persisted state.
            val snapshots = readAll(current.snapshots.map { it.path })
            _state.update {
                it.buildFrom(snapshots, it.extraKeys, keptOverrides).copy(
                    saving = false,
                    progressText = null,
                    outcomes = outcomes,
                    pendingCover = null,
                    coverRemoveRequested = false,
                )
            }
        }
    }

    fun dismissOutcomes() = _state.update { it.copy(outcomes = null) }

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
    fun toggleFileIncluded(path: String) = _state.update { current ->
        val excluded = current.excludedPaths.toMutableSet()
        if (!excluded.add(path)) excluded.remove(path)
        current.buildFrom(current.snapshots, current.extraKeys, excludedPaths = excluded)
    }

    fun includeAllFiles() = _state.update { current ->
        current.buildFrom(current.snapshots, current.extraKeys, excludedPaths = emptySet())
    }

    fun excludeAllFiles() = _state.update { current ->
        current.buildFrom(
            current.snapshots,
            current.extraKeys,
            excludedPaths = current.snapshots.map { it.path }.toSet(),
        )
    }

    companion object {
        fun factory(paths: List<String>, app: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { EditorViewModel(paths, app) }
            }
    }
}
