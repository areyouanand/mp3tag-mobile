package com.mp3tag.mobile.ui.browser

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.mobile.data.fs.FolderScanner
import com.mp3tag.mobile.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val selectedPaths: Set<String> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val recents: List<String> = emptyList(),
    val volumes: List<StorageVolumeInfo> = emptyList(),
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(BrowserState(recents = prefs.recentFolders))
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
        _state.update {
            it.copy(
                loading = true,
                error = null,
                directory = directory,
                selectedPaths = emptySet(),
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
                    val (dirs, files) = result.getOrThrow()
                    if (remember) rememberRecent(directory)
                    prefs.lastFolder = directory.absolutePath
                    current.copy(loading = false, error = null, directories = dirs, audioFiles = files)
                }
            }
        }
    }

    fun navigateUp() {
        _state.value.directory.parentFile?.let { open(it) }
    }

    fun openVolume(volume: StorageVolumeInfo) = open(volume.directory)

    fun refresh() = open(_state.value.directory, remember = false)

    fun toggleSelect(file: File) = _state.update {
        val selection = it.selectedPaths.toMutableSet()
        if (!selection.add(file.absolutePath)) selection.remove(file.absolutePath)
        it.copy(selectedPaths = selection)
    }

    fun selectAll() = _state.update {
        it.copy(selectedPaths = it.audioFiles.map { f -> f.absolutePath }.toSet())
    }

    fun clearSelection() = _state.update { it.copy(selectedPaths = emptySet()) }

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
