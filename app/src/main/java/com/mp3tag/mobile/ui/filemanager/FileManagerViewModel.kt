package com.mp3tag.mobile.ui.filemanager

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.mobile.data.fs.FolderScanner
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.ui.browser.StorageVolumeInfo
import com.mp3tag.mobile.util.Format
import com.mp3tag.mobile.util.PatternParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileSortField(val label: String) {
    NAME("Name"),
    DATE("Date modified"),
    SIZE("Size"),
    TYPE("Type / extension"),
}

enum class ClipboardMode(val label: String) {
    COPY("Copy"),
    MOVE("Move"),
}

data class FileClipboard(
    val sourcePaths: List<String>,
    val mode: ClipboardMode,
)

data class FileEntry(
    val file: File,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val isAudio: Boolean,
    val subtitle: String,
)

data class FileDetailsInfo(
    val itemCount: Int,
    val folderCount: Int,
    val fileCount: Int,
    val totalSizeBytes: Long,
    val path: String,
    val name: String,
    val lastModifiedText: String,
    val permissionsText: String,
)

data class FileManagerState(
    val directory: File = Environment.getExternalStorageDirectory(),
    val entries: List<FileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val sortField: FileSortField = FileSortField.NAME,
    val sortAscending: Boolean = true,
    val showHidden: Boolean = false,
    val clipboard: FileClipboard? = null,
    val loading: Boolean = false,
    val busyMessage: String? = null,
    val statusMessage: String? = null,
    val error: String? = null,
    val recents: List<String> = emptyList(),
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val detailsInfo: FileDetailsInfo? = null,
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(
        FileManagerState(
            showHidden = prefs.showHiddenFiles,
            recents = prefs.recentFolders,
        )
    )
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    init {
        loadVolumes()
        val startDir = (prefs.lastFileManagerFolder ?: prefs.lastFolder)
            ?.let(::File)
            ?.takeIf { it.exists() && it.isDirectory }
            ?: Environment.getExternalStorageDirectory()
        open(startDir, remember = false)
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
            val showHidden = _state.value.showHidden
            val sortField = _state.value.sortField
            val sortAsc = _state.value.sortAscending
            val result = withContext(Dispatchers.IO) {
                runCatching { scanDirectory(directory, showHidden, sortField, sortAsc) }
            }
            _state.update { current ->
                if (current.directory != directory) {
                    current
                } else if (result.isFailure) {
                    current.copy(
                        loading = false,
                        error = result.exceptionOrNull()?.message ?: "Could not read folder",
                    )
                } else {
                    val entries = result.getOrThrow()
                    if (remember) rememberRecent(directory)
                    prefs.lastFileManagerFolder = directory.absolutePath
                    current.copy(
                        loading = false,
                        error = null,
                        entries = entries,
                    )
                }
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
        val nextHidden = prefs.showHiddenFiles
        val hiddenChanged = _state.value.showHidden != nextHidden
        _state.update {
            it.copy(
                showHidden = nextHidden,
                recents = prefs.recentFolders,
            )
        }
        if (hiddenChanged) refresh()
    }

    fun toggleSelect(entry: FileEntry) = _state.update {
        val next = it.selectedPaths.toMutableSet()
        if (!next.add(entry.path)) next.remove(entry.path)
        it.copy(selectedPaths = next)
    }

    fun selectSingle(entry: FileEntry) = _state.update {
        it.copy(selectedPaths = setOf(entry.path))
    }

    fun selectAll(visibleEntries: List<FileEntry> = _state.value.entries) = _state.update {
        it.copy(selectedPaths = visibleEntries.map { e -> e.path }.toSet())
    }

    fun invertSelection(visibleEntries: List<FileEntry> = _state.value.entries) = _state.update { current ->
        val inverted = visibleEntries
            .map { it.path }
            .filter { it !in current.selectedPaths }
            .toSet()
        current.copy(selectedPaths = inverted)
    }

    fun clearSelection() = _state.update { it.copy(selectedPaths = emptySet()) }

    fun setSorting(field: FileSortField, ascending: Boolean) {
        _state.update { it.copy(sortField = field, sortAscending = ascending) }
        resortCurrentEntries()
    }

    fun toggleShowHidden() {
        val next = !_state.value.showHidden
        prefs.showHiddenFiles = next
        _state.update { it.copy(showHidden = next) }
        refresh()
    }

    private fun resortCurrentEntries() {
        val current = _state.value
        val sorted = sortEntries(current.entries, current.sortField, current.sortAscending)
        _state.update { it.copy(entries = sorted) }
    }

    // ------------------------------------------------------------------
    // File Manager Operations: Copy, Move, Paste, Rename, New Folder, Delete, Details
    // ------------------------------------------------------------------

    fun copySelectionToClipboard(paths: Collection<String> = _state.value.selectedPaths) {
        if (paths.isEmpty()) return
        _state.update {
            it.copy(
                clipboard = FileClipboard(paths.toList(), ClipboardMode.COPY),
                selectedPaths = emptySet(),
                statusMessage = "${paths.size} item(s) ready to copy — navigate to destination and tap Paste",
            )
        }
    }

    fun moveSelectionToClipboard(paths: Collection<String> = _state.value.selectedPaths) {
        if (paths.isEmpty()) return
        _state.update {
            it.copy(
                clipboard = FileClipboard(paths.toList(), ClipboardMode.MOVE),
                selectedPaths = emptySet(),
                statusMessage = "${paths.size} item(s) ready to move — navigate to destination and tap Paste",
            )
        }
    }

    fun clearClipboard() = _state.update { it.copy(clipboard = null) }

    fun dismissStatus() = _state.update { it.copy(statusMessage = null) }

    fun dismissDetails() = _state.update { it.copy(detailsInfo = null) }

    fun pasteHere() {
        val clip = _state.value.clipboard ?: return
        val destDir = _state.value.directory
        if (_state.value.busyMessage != null) return

        viewModelScope.launch {
            val total = clip.sourcePaths.size
            val verb = if (clip.mode == ClipboardMode.COPY) "Copying" else "Moving"
            _state.update { it.copy(busyMessage = "$verb 0 / $total item(s)…") }

            var okCount = 0
            var failCount = 0
            var lastError: String? = null

            withContext(Dispatchers.IO) {
                clip.sourcePaths.forEachIndexed { index, srcPath ->
                    _state.update { it.copy(busyMessage = "$verb ${index + 1} / $total: ${File(srcPath).name}") }
                    val src = File(srcPath)
                    val res = runCatching {
                        if (!src.exists()) error("Source no longer exists: ${src.name}")
                        if (src.isDirectory) {
                            val srcCanonical = src.canonicalPath
                            val destCanonical = destDir.canonicalPath
                            if (destCanonical == srcCanonical || destCanonical.startsWith(srcCanonical + File.separator)) {
                                error("Cannot ${clip.mode.label.lowercase()} a folder into itself")
                            }
                        }
                        if (clip.mode == ClipboardMode.MOVE && src.parentFile?.canonicalPath == destDir.canonicalPath) {
                            // Moving into the exact same folder is a no-op
                            return@runCatching
                        }
                        val target = resolveNonCollidingTarget(destDir, src.name, src.isDirectory)
                        when (clip.mode) {
                            ClipboardMode.COPY -> copyFileOrDir(src, target)
                            ClipboardMode.MOVE -> moveFileOrDir(src, target)
                        }
                    }
                    if (res.isSuccess) {
                        okCount += 1
                    } else {
                        failCount += 1
                        lastError = res.exceptionOrNull()?.message
                    }
                }
            }

            val doneVerb = if (clip.mode == ClipboardMode.COPY) "Copied" else "Moved"
            val summary = if (failCount == 0) {
                "$doneVerb $okCount item(s)"
            } else {
                "$doneVerb $okCount of $total item(s) ($failCount failed${lastError?.let { ": $it" }.orEmpty()})"
            }

            _state.update {
                it.copy(
                    busyMessage = null,
                    clipboard = if (clip.mode == ClipboardMode.MOVE && failCount == 0) null else it.clipboard,
                    statusMessage = summary,
                )
            }
            refresh()
        }
    }

    fun createFolder(rawName: String) {
        val sanitized = PatternParser.sanitizeFileName(rawName.trim())
        if (sanitized.isBlank()) return
        val parent = _state.value.directory
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val target = parent.resolve(sanitized)
                    if (target.exists()) error("'$sanitized' already exists")
                    if (!target.mkdirs()) error("Could not create folder '$sanitized'")
                    target
                }
            }
            _state.update {
                it.copy(
                    statusMessage = res.fold(
                        onSuccess = { f -> "Created folder '${f.name}'" },
                        onFailure = { e -> e.message ?: "Failed to create folder" },
                    ),
                )
            }
            refresh()
        }
    }

    fun renameItem(targetPath: String, newRawName: String) {
        val trimmed = newRawName.trim()
        if (trimmed.isBlank()) return
        val file = File(targetPath)
        val sanitized = PatternParser.sanitizeFileName(trimmed)
        if (sanitized.isBlank() || sanitized == file.name) return

        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val parent = file.parentFile ?: error("No parent folder")
                    val dest = parent.resolve(sanitized)
                    if (dest.exists() && !dest.absolutePath.equals(file.absolutePath, ignoreCase = false)) {
                        error("An item named '$sanitized' already exists")
                    }
                    if (!file.renameTo(dest)) error("Rename failed")
                    dest
                }
            }
            _state.update {
                it.copy(
                    selectedPaths = emptySet(),
                    statusMessage = res.fold(
                        onSuccess = { dest -> "Renamed to '${dest.name}'" },
                        onFailure = { e -> e.message ?: "Rename failed" },
                    ),
                )
            }
            refresh()
        }
    }

    fun deletePaths(paths: Collection<String>) {
        if (paths.isEmpty() || _state.value.busyMessage != null) return
        val targets = paths.toList()
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Deleting ${targets.size} item(s)…") }
            var deleted = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                targets.forEachIndexed { idx, path ->
                    _state.update { it.copy(busyMessage = "Deleting ${idx + 1} / ${targets.size}…") }
                    val file = File(path)
                    val ok = runCatching { file.deleteRecursively() }.getOrDefault(false)
                    if (ok || !file.exists()) deleted += 1 else failed += 1
                }
            }
            _state.update {
                it.copy(
                    busyMessage = null,
                    selectedPaths = emptySet(),
                    statusMessage = if (failed == 0) {
                        "Deleted $deleted item(s)"
                    } else {
                        "Deleted $deleted of ${targets.size} item(s) ($failed failed)"
                    },
                )
            }
            refresh()
        }
    }

    fun inspectDetails(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val list = paths.toList()
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                var folders = 0
                var files = 0
                var totalBytes = 0L

                fun walk(f: File) {
                    if (f.isDirectory) {
                        folders += 1
                        f.listFiles()?.forEach(::walk)
                    } else if (f.isFile) {
                        files += 1
                        totalBytes += f.length()
                    }
                }

                list.forEach { walk(File(it)) }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                if (list.size == 1) {
                    val single = File(list.first())
                    val perms = buildString {
                        append(if (single.canRead()) "Readable" else "No read")
                        append(" • ")
                        append(if (single.canWrite()) "Writable" else "Read-only")
                        if (single.isHidden || single.name.startsWith(".")) append(" • Hidden")
                    }
                    FileDetailsInfo(
                        itemCount = 1,
                        folderCount = folders,
                        fileCount = files,
                        totalSizeBytes = totalBytes,
                        path = single.absolutePath,
                        name = single.name,
                        lastModifiedText = if (single.lastModified() > 0) {
                            dateFormat.format(Date(single.lastModified()))
                        } else {
                            "Unknown"
                        },
                        permissionsText = perms,
                    )
                } else {
                    val parentPath = File(list.first()).parent ?: ""
                    FileDetailsInfo(
                        itemCount = list.size,
                        folderCount = folders,
                        fileCount = files,
                        totalSizeBytes = totalBytes,
                        path = parentPath,
                        name = "${list.size} items selected",
                        lastModifiedText = "Multiple items",
                        permissionsText = "$folders folder(s), $files file(s)",
                    )
                }
            }
            _state.update { it.copy(detailsInfo = info) }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun scanDirectory(
        dir: File,
        showHidden: Boolean,
        sortField: FileSortField,
        sortAscending: Boolean,
    ): List<FileEntry> {
        val children = dir.listFiles() ?: return emptyList()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val raw = ArrayList<FileEntry>(children.size)

        for (f in children) {
            val name = f.name
            if (!showHidden && name.startsWith(".")) continue
            val isDir = f.isDirectory
            val size = if (isDir) 0L else f.length()
            val mod = f.lastModified()
            val ext = if (isDir) "" else f.extension.lowercase()
            val isAudio = !isDir && ext in FolderScanner.AUDIO_EXTENSIONS
            val dateStr = if (mod > 0L) dateFmt.format(Date(mod)) else ""

            val subtitle = if (isDir) {
                val count = f.list()?.let { arr ->
                    if (showHidden) arr.size else arr.count { !it.startsWith(".") }
                }
                val countStr = when (count) {
                    null -> "Folder"
                    1 -> "1 item"
                    else -> "$count items"
                }
                if (dateStr.isNotEmpty()) "$countStr • $dateStr" else countStr
            } else {
                val extLabel = ext.uppercase().ifEmpty { "FILE" }
                val base = "${Format.fileSize(size)} • $extLabel"
                if (dateStr.isNotEmpty()) "$base • $dateStr" else base
            }

            raw.add(
                FileEntry(
                    file = f,
                    path = f.absolutePath,
                    name = name,
                    isDirectory = isDir,
                    sizeBytes = size,
                    lastModified = mod,
                    extension = ext,
                    isAudio = isAudio,
                    subtitle = subtitle,
                )
            )
        }

        return sortEntries(raw, sortField, sortAscending)
    }

    private fun sortEntries(
        entries: List<FileEntry>,
        sortField: FileSortField,
        ascending: Boolean,
    ): List<FileEntry> {
        val nat = FolderScanner.naturalComparator()
        val dirMultiplier = if (ascending) 1 else -1
        return entries.sortedWith { a, b ->
            // Always group directories above files
            if (a.isDirectory != b.isDirectory) {
                if (a.isDirectory) -1 else 1
            } else {
                val rawCmp = when (sortField) {
                    FileSortField.NAME -> nat.compare(a.name, b.name)
                    FileSortField.DATE -> a.lastModified.compareTo(b.lastModified)
                        .takeIf { it != 0 } ?: nat.compare(a.name, b.name)
                    FileSortField.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                        .takeIf { it != 0 } ?: nat.compare(a.name, b.name)
                    FileSortField.TYPE -> a.extension.compareTo(b.extension)
                        .takeIf { it != 0 } ?: nat.compare(a.name, b.name)
                }
                rawCmp * dirMultiplier
            }
        }
    }

    private fun resolveNonCollidingTarget(destDir: File, originalName: String, isDir: Boolean): File {
        var candidate = destDir.resolve(originalName)
        if (!candidate.exists()) return candidate

        val dot = originalName.lastIndexOf('.')
        val base = if (!isDir && dot > 0) originalName.substring(0, dot) else originalName
        val ext = if (!isDir && dot > 0) originalName.substring(dot) else ""
        var index = 2
        while (candidate.exists()) {
            candidate = destDir.resolve("$base ($index)$ext")
            index += 1
        }
        return candidate
    }

    private fun copyFileOrDir(src: File, target: File) {
        if (src.isDirectory) {
            if (!target.exists() && !target.mkdirs()) {
                error("Could not create directory ${target.name}")
            }
            val children = src.listFiles() ?: emptyArray()
            for (child in children) {
                copyFileOrDir(child, target.resolve(child.name))
            }
        } else {
            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileInputStream(src).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            target.setLastModified(src.lastModified())
        }
    }

    private fun moveFileOrDir(src: File, target: File) {
        // Fast rename when on the same mount / filesystem
        if (src.renameTo(target)) return
        // Cross-volume fallback (e.g. Internal storage <-> SD card)
        copyFileOrDir(src, target)
        if (!src.deleteRecursively()) {
            error("Copied to destination, but could not remove original ${src.name}")
        }
    }

    private fun rememberRecent(directory: File) {
        val recents = (listOf(directory.absolutePath) + prefs.recentFolders).distinct().take(8)
        prefs.recentFolders = recents
        _state.update { it.copy(recents = recents) }
    }

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
