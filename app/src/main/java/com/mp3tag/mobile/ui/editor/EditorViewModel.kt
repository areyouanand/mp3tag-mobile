package com.mp3tag.mobile.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
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
import com.mp3tag.mobile.util.PatternParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.FieldKey
import java.io.File

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
            _state.update { it.copy(loading = true) }
            val snapshots = readAll(_state.value.paths)
            _state.update { current ->
                var updated = current.buildFrom(snapshots, current.extraKeys)
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
            _state.update { it.copy(saving = true) }
            val outcomes = withContext(Dispatchers.IO) {
                performRenames(targets, pattern)
            }
            _state.update {
                val renamedOldPaths = outcomes.map { it.oldPath }.toSet()
                it.copy(
                    saving = false,
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
        viewModelScope.launch {
            val cover = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)
                        ?.use { stream -> stream.readBytes() }
                        ?: return@runCatching null
                    CoverImage(bytes, sniffImageMime(bytes))
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

        fun fieldValuesFor(path: String): Map<FieldKey, String?> = buildMap {
            current.perFileOverrides.forEach { (key, byPath) ->
                byPath[path]?.let { value ->
                    put(key, value.trim().takeIf { it.isNotEmpty() })
                }
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, outcomes = null) }
            val targets = current.snapshots.filter { it.path !in current.excludedPaths }
            val outcomes = withContext(Dispatchers.IO) {
                targets.map { snapshot ->
                    runCatching {
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
                    outcomes = outcomes,
                    pendingCover = null,
                    coverRemoveRequested = false,
                )
            }
        }
    }

    fun dismissOutcomes() = _state.update { it.copy(outcomes = null) }

    private suspend fun readAll(paths: List<String>): List<TagSnapshot> = withContext(Dispatchers.IO) {
        paths.mapNotNull { path ->
            runCatching { TagEngine.read(File(path)) }.getOrNull()
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
        val included = snapshots.filter { it.path !in excludedPaths }
        val displayed = EDITABLE_FIELDS + extraKeys.filter { it !in EDITABLE_FIELDS }
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
            extraKeys = extraKeys.filter { it !in EDITABLE_FIELDS },
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
