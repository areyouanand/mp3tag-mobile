package com.mp3tag.mobile.data.fs

import java.io.File

/** Folder listing filtered to what a tag editor cares about. */
object FolderScanner {

    val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "ogg", "oga", "wav", "dsf")

    fun isAudioFile(file: File): Boolean =
        file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS

    data class Listing(val directories: List<File>, val audioFiles: List<File>)

    fun list(dir: File): Listing {
        val entries = dir.listFiles()
            ?: return Listing(emptyList(), emptyList())

        val directories = entries
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedWith(compareBy { it.name.lowercase() })

        val audioFiles = entries
            .filter { isAudioFile(it) }
            .sortedWith(compareBy(naturalComparator()) { it.name })

        return Listing(directories, audioFiles)
    }

    /** Numeric-aware ("natural") file-name ordering, like MP3Tag's file list. */
    fun naturalComparator(): Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

    fun compareNatural(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        while (ia < a.length && ib < b.length) {
            val ca = a[ia]
            val cb = b[ib]
            if (ca.isDigit() && cb.isDigit()) {
                var ja = ia
                while (ja < a.length && a[ja].isDigit()) ja++
                var jb = ib
                while (jb < b.length && b[jb].isDigit()) jb++
                val na = a.substring(ia, ja).trimStart('0')
                val nb = b.substring(ib, jb).trimStart('0')
                val cmp = if (na.length != nb.length) {
                    na.length.compareTo(nb.length)
                } else {
                    na.compareTo(nb)
                }
                if (cmp != 0) return cmp
                ia = ja
                ib = jb
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                ia++
                ib++
            }
        }
        return (a.length - ia).compareTo(b.length - ib)
    }
}
