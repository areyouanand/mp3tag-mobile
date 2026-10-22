package com.mp3tag.mobile.data.tag

import java.io.File

/**
 * Runs [edit] on a temporary copy of [target] in the same folder, then replaces [target] with it.
 * If [edit] throws, [target] is left untouched and the temporary copy is deleted.
 */
internal fun writeSafely(target: File, edit: (File) -> Unit) {
    val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
    try {
        target.copyTo(temp, overwrite = true)
        edit(temp)
        check(temp.isFile && temp.length() > 0L) { "Tag write produced an empty file" }
        if (!temp.renameTo(target)) {
            // Some storage (SD cards, USB) cannot rename over an existing file; copy over instead.
            temp.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.fd.sync()
                }
            }
        }
    } finally {
        if (temp.exists()) temp.delete()
    }
}
