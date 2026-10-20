package com.mp3tag.mobile.data.fs

import com.mp3tag.mobile.util.Format
import java.io.File

/** Folder listing filtered to what a tag editor cares about. */
object FolderScanner {

    val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "ogg", "oga", "wav", "dsf")

    fun isAudioFile(file: File): Boolean =
        file.extension.lowercase() in AUDIO_EXTENSIONS && file.isFile

    data class Listing(
        val directories: List<File>,
        val audioFiles: List<File>,
        /** Precomputed "size • EXT" per file path so UI scrolling never performs stat() syscalls. */
        val fileSubtitles: Map<String, String> = emptyMap(),
    )

    fun list(dir: File): Listing {
        val entries = dir.listFiles()
            ?: return Listing(emptyList(), emptyList(), emptyMap())

        val dirs = ArrayList<File>()
        val files = ArrayList<File>()

        for (entry in entries) {
            val name = entry.name
            if (name.startsWith(".")) continue
            val dot = name.lastIndexOf('.')
            val extLower = if (dot > 0 && dot < name.length - 1) {
                name.substring(dot + 1).lowercase()
            } else {
                ""
            }
            if (extLower in AUDIO_EXTENSIONS && entry.isFile) {
                files.add(entry)
            } else if (entry.isDirectory) {
                dirs.add(entry)
            }
        }

        val natCmp = naturalComparator()
        dirs.sortWith { a, b -> natCmp.compare(a.name, b.name) }
        files.sortWith { a, b -> natCmp.compare(a.name, b.name) }

        val subtitles = HashMap<String, String>(files.size * 2)
        for (file in files) {
            val ext = file.extension.uppercase()
            subtitles[file.absolutePath] = "${Format.fileSize(file.length())} • $ext"
        }

        return Listing(dirs, files, subtitles)
    }

    /**
     * Recursively collects all supported audio files inside [dir] (and its subfolders),
     * sorted in natural order per directory level.
     */
    fun collectAudioFilesRecursively(dir: File): List<File> {
        val result = ArrayList<File>()
        val natCmp = naturalComparator()

        fun walk(current: File) {
            val entries = current.listFiles() ?: return
            val subDirs = ArrayList<File>()
            val files = ArrayList<File>()
            for (entry in entries) {
                val name = entry.name
                if (name.startsWith(".")) continue
                val dot = name.lastIndexOf('.')
                val extLower = if (dot > 0 && dot < name.length - 1) {
                    name.substring(dot + 1).lowercase()
                } else {
                    ""
                }
                if (extLower in AUDIO_EXTENSIONS && entry.isFile) {
                    files.add(entry)
                } else if (entry.isDirectory) {
                    subDirs.add(entry)
                }
            }
            files.sortWith { a, b -> natCmp.compare(a.name, b.name) }
            subDirs.sortWith { a, b -> natCmp.compare(a.name, b.name) }
            result.addAll(files)
            for (sub in subDirs) {
                walk(sub)
            }
        }

        walk(dir)
        return result
    }

    /** Numeric-aware ("natural") file-name ordering, like MP3Tag's file list. */
    fun naturalComparator(): Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

    /**
     * Allocation-free natural string comparator (safe for sorting thousands of files
     * on low-spec devices without GC pressure).
     */
    fun compareNatural(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        val lenA = a.length
        val lenB = b.length
        while (ia < lenA && ib < lenB) {
            val ca = a[ia]
            val cb = b[ib]
            if (ca.isDigit() && cb.isDigit()) {
                var ja = ia
                while (ja < lenA && a[ja].isDigit()) ja++
                var jb = ib
                while (jb < lenB && b[jb].isDigit()) jb++

                var za = ia
                while (za < ja && a[za] == '0') za++
                var zb = ib
                while (zb < jb && b[zb] == '0') zb++

                val sigLenA = ja - za
                val sigLenB = jb - zb
                if (sigLenA != sigLenB) {
                    return sigLenA.compareTo(sigLenB)
                }
                for (k in 0 until sigLenA) {
                    val da = a[za + k]
                    val db = b[zb + k]
                    if (da != db) return da.compareTo(db)
                }
                // Tie-breaker on leading zeros if numeric values are equal (e.g. "01" vs "1")
                val runDiff = (ja - ia).compareTo(jb - ib)
                if (runDiff != 0) return runDiff

                ia = ja
                ib = jb
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                ia++
                ib++
            }
        }
        return (lenA - ia).compareTo(lenB - ib)
    }
}
