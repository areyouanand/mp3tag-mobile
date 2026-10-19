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

data class EditorState(
    val paths: List<String> = emptyList(),
    val loading: Boolean = true,
    val snapshots: List<TagSnapshot> = emptyList(),
    val fields: Map<FieldKey, FieldUi> = emptyMap(),
    val extraKeys: List<FieldKey> = emptyList(),
    val availableKeys: List<FieldKey> = emptyList(),
    val valueOptions: Map<FieldKey, List<String>> = emptyMap(),
    val cover: CoverImage? = null,
    val coverMixed: Boolean = false,
    val pendingCover: CoverImage? = null,
    val coverRemoveRequested: Boolean = false,
    val saving: Boolean = false,
    val outcomes: List<SaveOutcome>? = null,
) {
    val displayedKeys: List<FieldKey>
        get() = EDITABLE_FIELDS + extraKeys.filter { it !in EDITABLE_FIELDS }

    val hasEdits: Boolean
        get() = fields.values.any { it.edited } || pendingCover != null || coverRemoveRequested
}

class EditorViewModel(
    private val paths: List<String>,
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
            current.buildFrom(current.snapshots, current.extraKeys + key, preserve = current.fields)
        }
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val snapshots = readAll(paths)
            _state.update { it.buildFrom(snapshots, it.extraKeys) }
        }
    }

    fun onFieldChange(key: FieldKey, value: String) = _state.update {
        val field = it.fields[key] ?: return@update it
        it.copy(fields = it.fields + (key to field.copy(text = value, edited = true, cleared = false)))
    }

    /** Explicit "clear this field in all selected files" action. */
    fun onFieldClear(key: FieldKey) = _state.update {
        val field = it.fields[key] ?: return@update it
        it.copy(fields = it.fields + (key to field.copy(text = "", edited = true, cleared = true)))
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

        val fieldValues = buildMap<FieldKey, String?> {
            current.fields.forEach { (key, field) ->
                when {
                    !field.edited -> Unit
                    field.cleared -> put(key, null)
                    field.mixed || field.text != field.original -> put(key, field.text.trim())
                }
            }
        }
        val coverAction = when {
            current.pendingCover != null -> CoverAction.REPLACE
            current.coverRemoveRequested -> CoverAction.REMOVE
            else -> CoverAction.NONE
        }
        val options = WriteOptions(id3Version = prefs.id3Version)

        viewModelScope.launch {
            _state.update { it.copy(saving = true, outcomes = null) }
            val outcomes = withContext(Dispatchers.IO) {
                current.snapshots.map { snapshot ->
                    runCatching {
                        TagEngine.write(
                            file = File(snapshot.path),
                            request = WriteRequest(
                                fieldValues = fieldValues,
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
            // Re-read from disk so the editor shows the persisted state.
            val snapshots = readAll(current.snapshots.map { it.path })
            _state.update { it.buildFrom(snapshots, it.extraKeys).copy(saving = false, outcomes = outcomes) }
        }
    }

    fun dismissOutcomes() = _state.update { it.copy(outcomes = null) }

    private suspend fun readAll(paths: List<String>): List<TagSnapshot> = withContext(Dispatchers.IO) {
        paths.mapNotNull { path ->
            runCatching { TagEngine.read(File(path)) }.getOrNull()
        }
    }

    /**
     * Builds editor state from fresh snapshots: fields whose values differ across
     * the selection become "mixed" (left untouched unless the user edits them),
     * matching MP3Tag's "<keep>" behaviour. [preserve] keeps in-progress edits
     * when new field rows are added.
     */
    private fun EditorState.buildFrom(
        snapshots: List<TagSnapshot>,
        extraKeys: List<FieldKey>,
        preserve: Map<FieldKey, FieldUi> = emptyMap(),
    ): EditorState {
        val displayed = EDITABLE_FIELDS + extraKeys.filter { it !in EDITABLE_FIELDS }
        val fields = displayed.associateWith { key ->
            preserve[key]?.takeIf { it.edited } ?: run {
                val distinct = snapshots.map { it.fields[key].orEmpty() }.distinct()
                if (distinct.size == 1) {
                    FieldUi(mixed = false, original = distinct[0], text = distinct[0], edited = false, cleared = false)
                } else {
                    FieldUi(mixed = true, original = "", text = "", edited = false, cleared = false)
                }
            }
        }
        // Fields present in the files but not currently displayed, MP3Tag-style.
        val available = snapshots.flatMap { it.fields.keys }
            .distinct()
            .filter { it !in displayed }
            .sortedBy { key ->
                OFFERABLE_FIELDS.indexOf(key).takeIf { index -> index >= 0 }
                    ?: (1_000 + key.ordinal)
            }
        val covers = snapshots.mapNotNull { it.cover }.distinct()
        return copy(
            loading = false,
            snapshots = snapshots,
            fields = fields,
            extraKeys = extraKeys.filter { it !in EDITABLE_FIELDS },
            availableKeys = available,
            valueOptions = displayed.associateWith { key ->
                snapshots.map { it.fields[key].orEmpty() }
                    .filter { it.isNotBlank() }
                    .distinct()
            },
            cover = covers.firstOrNull(),
            coverMixed = covers.size > 1,
            pendingCover = null,
            coverRemoveRequested = false,
            outcomes = null,
        )
    }

    companion object {
        fun factory(paths: List<String>, app: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { EditorViewModel(paths, app) }
            }
    }
}
