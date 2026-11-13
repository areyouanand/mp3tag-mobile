package com.mp3tag.mobile.ui.browser

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.mobile.data.fs.FolderScanner
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.tag.TagEngine
import com.mp3tag.mobile.util.DeviceProfile
import org.jaudiotagger.tag.FieldKey
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
import java.io.File

/** Tags read lazily for the current folder, used for search/sort/duplicates. */
data class BrowserTagInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val track: Int = Int.MAX_VALUE,
    val disc: Int = Int.MAX_VALUE,
    val durationSeconds: Int = 0,
    val year: String = "",
) {
    val searchText: String
        get() = listOf(title, artist, album, albumArtist, year).filter { it.isNotBlank() }.joinToString(" ")
}

enum class BrowserSort { NAME, TITLE, ARTIST, ALBUM, TRACK, DURATION, SIZE, DATE }

/** One group of files that appear to be duplicates. */
data class DuplicateGroup(val label: String, val paths: List<String>)

/** One mounted storage volume (internal storage, SD card, USB OTG...). */
data class StorageVolumeInfo(
    val name: String,
    val directory: File,
    val isRemovable: Boolean,
)

data class BrowserState(
    val directory: File = Environment.getExternalStorageDirectory(),
    val directories: List<File> = emptyList(),
    val audioFiles: List<File> = emptyList(),
    val fileSubtitles: Map<String, String> = emptyMap(),
    val selectedPaths: Set<String> = emptySet(),
    val selectedFolderPaths: Set<String> = emptySet(),
    val loading: Boolean = false,
    val resolvingSelection: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
    val recents: List<String> = emptyList(),
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val tagInfo: Map<String, BrowserTagInfo> = emptyMap(),
    val sortMode: BrowserSort = BrowserSort.NAME,
    val sortAscending: Boolean = true,
    val tagScanProgress: String? = null,
    val duplicateGroups: List<DuplicateGroup>? = null,
) {
    val totalSelectedCount: Int
        get() = selectedPaths.size + selectedFolderPaths.size

    /** Files in the chosen sort order (tag-aware). */
    val displayedAudioFiles: List<File>
        get() {
            val comparator = when (sortMode) {
                BrowserSort.NAME -> compareBy<File> { it.name.lowercase() }
                BrowserSort.TITLE -> compareBy { tagInfo[it.absolutePath]?.title?.lowercase().orEmpty() }
                BrowserSort.ARTIST -> compareBy { tagInfo[it.absolutePath]?.artist?.lowercase().orEmpty() }
                BrowserSort.ALBUM -> compareBy { tagInfo[it.absolutePath]?.album?.lowercase().orEmpty() }
                BrowserSort.TRACK -> compareBy<File> {
                    val tag = tagInfo[it.absolutePath]
                    (tag?.disc ?: Int.MAX_VALUE) * 100_000L + (tag?.track ?: Int.MAX_VALUE)
                }.thenBy { tagInfo[it.absolutePath]?.title?.lowercase().orEmpty() }
                BrowserSort.DURATION -> compareBy { tagInfo[it.absolutePath]?.durationSeconds ?: 0 }
                BrowserSort.SIZE -> compareBy { it.length() }
                BrowserSort.DATE -> compareBy { it.lastModified() }
            }
            // Natural ordering is still nicer for plain names and equal tag values.
            val nameComparator = Comparator<File> { a, b ->
                FolderScanner.compareNatural(a.name, b.name)
            }
            val sorted = audioFiles.sortedWith(comparator.then(nameComparator))
            return if (sortAscending) sorted else sorted.reversed()
        }
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)
    private var loadJob: Job? = null
    private var tagJob: Job? = null
    private var duplicateJob: Job? = null

    private val _state = MutableStateFlow(
        BrowserState(
            recents = prefs.recentFolders,
            sortMode = runCatching { BrowserSort.valueOf(prefs.browserSortMode) }
                .getOrDefault(BrowserSort.NAME),
            sortAscending = prefs.browserSortAscending,
        ),
    )
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    val selectedFiles: List<File>
        get() = _state.value.selectedPaths.map(::File)

    init {
        loadVolumes()
        val last = prefs.lastFolder?.let(::File)?.takeIf { it.exists() && it.isDirectory }
        open(last ?: Environment.getExternalStorageDirectory(), remember = false)
    }

    fun open(directory: File, remember: Boolean = true) {
        loadJob?.cancel()
        tagJob?.cancel()
        duplicateJob?.cancel()
        _state.update {
            it.copy(
                loading = true,
                error = null,
                statusMessage = null,
                directory = directory,
                selectedPaths = emptySet(),
                selectedFolderPaths = emptySet(),
            )
        }
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { FolderScanner.list(directory) }
            }
            _state.update { current ->
                if (current.directory != directory) {
                    current // a newer navigation superseded this load
                } else if (result.isFailure) {
                    current.copy(
                        loading = false,
                        error = result.exceptionOrNull()?.message ?: "Could not read folder",
                    )
                } else {
                    val (dirs, files, subtitles) = result.getOrThrow()
                    if (remember) rememberRecent(directory)
                    prefs.lastFolder = directory.absolutePath
                    current.copy(
                        loading = false,
                        error = null,
                        directories = dirs,
                        audioFiles = files,
                        fileSubtitles = subtitles,
                        tagInfo = emptyMap(),
                        duplicateGroups = null,
                        tagScanProgress = null,
                    )
                }
            }
            if (result.isSuccess && _state.value.directory == directory) {
                loadTagInfo(result.getOrThrow().audioFiles)
            }
        }
    }

    fun canNavigateUp(): Boolean {
        val dir = _state.value.directory
        val volumes = _state.value.volumes
        if (volumes.any { it.directory.absolutePath == dir.absolutePath }) return false
        return dir.parentFile != null && dir.parentFile?.canRead() == true
    }

    fun navigateUp(): Boolean {
        if (!canNavigateUp()) return false
        val parent = _state.value.directory.parentFile ?: return false
        open(parent)
        return true
    }

    fun openVolume(volume: StorageVolumeInfo) = open(volume.directory)

    fun refresh() = open(_state.value.directory, remember = false)

    fun reloadPrefs() {
        _state.update { it.copy(recents = prefs.recentFolders) }
    }

    // ------------------------------------------------------------------
    // Tag-aware search, sort and duplicate detection
    // ------------------------------------------------------------------

    /** Reads tags for the current folder in the background (bounded parallelism). */
    private fun loadTagInfo(files: List<File>) {
        tagJob?.cancel()
        if (files.isEmpty()) return
        tagJob = viewModelScope.launch {
            _state.update { it.copy(tagScanProgress = "Reading tags 0 / ${files.size}…") }
            val info = HashMap<String, BrowserTagInfo>()
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            coroutineScope {
                val sem = Semaphore(4)
                files.take(1000).map { file ->
                    async {
                        sem.withPermit {
                            val snapshot = runCatching { TagEngine.read(file) }.getOrNull()
                            if (snapshot != null) {
                                val tags = BrowserTagInfo(
                                    title = snapshot.fields[FieldKey.TITLE].orEmpty(),
                                    artist = snapshot.fields[FieldKey.ARTIST].orEmpty(),
                                    album = snapshot.fields[FieldKey.ALBUM].orEmpty(),
                                    albumArtist = snapshot.fields[FieldKey.ALBUM_ARTIST].orEmpty(),
                                    track = snapshot.fields[FieldKey.TRACK]?.toIntOrNull() ?: Int.MAX_VALUE,
                                    disc = snapshot.fields[FieldKey.DISC_NO]?.toIntOrNull() ?: Int.MAX_VALUE,
                                    durationSeconds = snapshot.durationSeconds ?: 0,
                                    year = snapshot.fields[FieldKey.YEAR].orEmpty(),
                                )
                                synchronized(info) { info[file.absolutePath] = tags }
                            }
                            val count = done.incrementAndGet()
                            if (count % 8 == 0 || count == files.size) {
                                _state.update { it.copy(tagScanProgress = "Reading tags $count / ${files.size}…") }
                            }
                        }
                    }
                }.awaitAll()
            }
            _state.update {
                it.copy(
                    tagInfo = it.tagInfo + info,
                    tagScanProgress = null,
                )
            }
        }
    }

    fun setSort(mode: BrowserSort) {
        val current = _state.value
        val ascending = if (current.sortMode == mode) !current.sortAscending else true
        prefs.browserSortMode = mode.name
        prefs.browserSortAscending = ascending
        _state.update { it.copy(sortMode = mode, sortAscending = ascending) }
    }

    /** Groups files in the current folder by ARTIST + TITLE (case-insensitive). */
    fun findDuplicates() {
        val current = _state.value
        if (current.audioFiles.isEmpty()) {
            _state.update { it.copy(statusMessage = "This folder has no audio files") }
            return
        }
        duplicateJob?.cancel()
        duplicateJob = viewModelScope.launch {
            _state.update { it.copy(statusMessage = "Looking for duplicates…") }
            val info = current.tagInfo.ifEmpty {
                val map = HashMap<String, BrowserTagInfo>()
                withContext(Dispatchers.IO) {
                    current.audioFiles.forEach { file ->
                        runCatching { TagEngine.read(file) }.getOrNull()?.let { snapshot ->
                            map[file.absolutePath] = BrowserTagInfo(
                                title = snapshot.fields[FieldKey.TITLE].orEmpty(),
                                artist = snapshot.fields[FieldKey.ARTIST].orEmpty(),
                                album = snapshot.fields[FieldKey.ALBUM].orEmpty(),
                                albumArtist = snapshot.fields[FieldKey.ALBUM_ARTIST].orEmpty(),
                                track = snapshot.fields[FieldKey.TRACK]?.toIntOrNull() ?: Int.MAX_VALUE,
                                disc = snapshot.fields[FieldKey.DISC_NO]?.toIntOrNull() ?: Int.MAX_VALUE,
                                durationSeconds = snapshot.durationSeconds ?: 0,
                                year = snapshot.fields[FieldKey.YEAR].orEmpty(),
                            )
                        }
                    }
                }
                map
            }
            val groups = current.audioFiles
                .mapNotNull { file ->
                    val tag = info[file.absolutePath] ?: return@mapNotNull null
                    if (tag.artist.isBlank() || tag.title.isBlank()) return@mapNotNull null
                    "${tag.artist.trim().lowercase()}\u0001${tag.title.trim().lowercase()}" to file
                }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }
                .map { (key, files) ->
                    val parts = key.split('\u0001')
                    DuplicateGroup(
                        label = "${parts.getOrNull(0).orEmpty()} — ${parts.getOrNull(1).orEmpty()}",
                        paths = files.map { it.absolutePath },
                    )
                }
                .sortedBy { it.label }
            _state.update {
                it.copy(
                    duplicateGroups = groups,
                    statusMessage = if (groups.isEmpty()) {
                        "No duplicates found in this folder"
                    } else {
                        "Found ${groups.size} duplicate group(s)"
                    },
                )
            }
        }
    }

    fun selectDuplicateExtras() {
        val groups = _state.value.duplicateGroups.orEmpty()
        val extras = groups.flatMap { it.paths.drop(1) }.toSet()
        _state.update { it.copy(selectedPaths = extras, statusMessage = "${extras.size} duplicate file(s) selected") }
    }

    fun dismissDuplicates() = _state.update { it.copy(duplicateGroups = null) }

    fun toggleSelect(file: File) = _state.update {
        val selection = it.selectedPaths.toMutableSet()
        if (!selection.add(file.absolutePath)) selection.remove(file.absolutePath)
        it.copy(selectedPaths = selection, statusMessage = null)
    }

    fun toggleSelectFolder(dir: File) = _state.update {
        val selection = it.selectedFolderPaths.toMutableSet()
        if (!selection.add(dir.absolutePath)) selection.remove(dir.absolutePath)
        it.copy(selectedFolderPaths = selection, statusMessage = null)
    }

    fun selectAll() = _state.update {
        it.copy(
            selectedPaths = it.audioFiles.map { f -> f.absolutePath }.toSet(),
            selectedFolderPaths = it.directories.map { d -> d.absolutePath }.toSet(),
            statusMessage = null,
        )
    }

    fun clearSelection() = _state.update {
        it.copy(selectedPaths = emptySet(), selectedFolderPaths = emptySet(), statusMessage = null)
    }

    fun dismissStatus() = _state.update { it.copy(statusMessage = null) }

    /**
     * Resolves all selected folders (recursively) + selected individual audio files
     * into a deduplicated list of audio file paths and passes them to [onReady].
     */
    fun resolveSelectionForEdit(onReady: (List<String>) -> Unit) {
        val current = _state.value
        if (current.resolvingSelection) return
        if (current.selectedFolderPaths.isEmpty()) {
            val direct = current.selectedPaths.toList()
            if (direct.isNotEmpty()) onReady(direct)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(resolvingSelection = true, statusMessage = null) }
            val resolved = withContext(Dispatchers.IO) {
                val combined = LinkedHashSet<String>()
                for (folderPath in current.selectedFolderPaths) {
                    val folderFiles = FolderScanner.collectAudioFilesRecursively(File(folderPath))
                    for (f in folderFiles) {
                        combined.add(f.absolutePath)
                    }
                }
                for (filePath in current.selectedPaths) {
                    combined.add(filePath)
                }
                combined.toList()
            }
            _state.update { it.copy(resolvingSelection = false) }
            if (resolved.isEmpty()) {
                _state.update {
                    it.copy(statusMessage = "No supported audio files found inside the selected folder(s)")
                }
            } else {
                onReady(resolved)
            }
        }
    }

    /**
     * Collects all supported audio files inside [dir] (recursively) and opens the editor.
     */
    fun editEntireFolder(dir: File, onReady: (List<String>) -> Unit) {
        if (_state.value.resolvingSelection) return
        viewModelScope.launch {
            _state.update { it.copy(resolvingSelection = true, statusMessage = null) }
            val resolved = withContext(Dispatchers.IO) {
                FolderScanner.collectAudioFilesRecursively(dir).map { it.absolutePath }
            }
            _state.update { it.copy(resolvingSelection = false) }
            if (resolved.isEmpty()) {
                _state.update {
                    it.copy(statusMessage = "No supported audio files found in '${dir.name}'")
                }
            } else {
                onReady(resolved)
            }
        }
    }

    private fun rememberRecent(directory: File) {
        val recents = (listOf(directory.absolutePath) + prefs.recentFolders).distinct().take(8)
        prefs.recentFolders = recents
        _state.update { it.copy(recents = recents) }
    }

    /** Enumerates all mounted storage volumes (internal + removable). */
    private fun loadVolumes() {
        val context = getApplication<Application>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val mounted = storageManager.storageVolumes
            .filter { it.state == Environment.MEDIA_MOUNTED }
            .filter { it.directory != null }

        val removableCount = mounted.count { it.isRemovable }
        var removableIndex = 0
        val volumes = mounted.map { volume ->
            val dir = volume.directory ?: File("/")
            val name = if (!volume.isRemovable) {
                "Internal storage"
            } else {
                removableIndex += 1
                if (removableCount > 1) {
                    val label = volume.uuid?.take(4)
                    if (label.isNullOrBlank()) "SD card $removableIndex" else "SD card ($label)"
                } else {
                    "SD card"
                }
            }
            StorageVolumeInfo(name = name, directory = dir, isRemovable = volume.isRemovable)
        }
        _state.update { it.copy(volumes = volumes) }
    }
}
