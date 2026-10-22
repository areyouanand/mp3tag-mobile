package com.mp3tag.mobile.ui.editor

import android.content.Context
import com.mp3tag.mobile.data.tag.CoverImage
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/** Persists an in-progress editor draft (per-file overrides + selection + extras + pending cover)
 *  so unsaved edits survive process death / app kill. Keyed by the selected file paths. */
class EditorDraftStore(context: Context) {
    private val dir = File(context.cacheDir, "editor_drafts").apply { mkdirs() }

    data class Draft(
        val perFileOverrides: Map<FieldKey, Map<String, String>>,
        val excludedPaths: Set<String>,
        val extraKeys: List<FieldKey>,
        val pendingCover: CoverImage?,
    )

    private fun keyFile(paths: List<String>): File {
        val digest = MessageDigest.getInstance("SHA-256")
        paths.sorted().forEach { digest.update(it.toByteArray(Charsets.UTF_8)); digest.update(0) }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return File(dir, "$hex.draft")
    }

    fun save(
        paths: List<String>,
        perFileOverrides: Map<FieldKey, Map<String, String>>,
        excludedPaths: Set<String>,
        extraKeys: List<FieldKey>,
        pendingCover: CoverImage?,
    ) {
        val hasContent = perFileOverrides.isNotEmpty() || excludedPaths.isNotEmpty() ||
            extraKeys.isNotEmpty() || pendingCover != null
        val f = keyFile(paths)
        if (!hasContent) {
            f.delete()
            return
        }
        val sb = StringBuilder()
        extraKeys.forEach { sb.append("EXTRA\t${it.name}\n") }
        excludedPaths.forEach { sb.append("EXCLUDED\t${b64(it)}\n") }
        perFileOverrides.forEach { (key, byPath) ->
            byPath.forEach { (path, value) ->
                sb.append("OVERRIDE\t${key.name}\t${b64(path)}\t${b64(value)}\n")
            }
        }
        pendingCover?.let { c ->
            sb.append("PENDINGCOVER\t${c.mimeType}\t${b64Bytes(c.bytes)}\n")
        }
        f.writeText(sb.toString())
    }

    fun load(paths: List<String>): Draft? {
        val f = keyFile(paths)
        if (!f.exists()) return null
        val overrides = linkedMapOf<FieldKey, MutableMap<String, String>>()
        val excluded = linkedSetOf<String>()
        val extras = mutableListOf<FieldKey>()
        var pending: CoverImage? = null
        f.readLines().forEach { line ->
            val parts = line.split('\t')
            when (parts.getOrNull(0)) {
                "EXTRA" -> parts.getOrNull(1)?.let { name ->
                    runCatching { FieldKey.valueOf(name) }.getOrNull()?.let { extras.add(it) }
                }
                "EXCLUDED" -> parts.getOrNull(1)?.let { b64 ->
                    runCatching { String(Base64.getDecoder().decode(b64), Charsets.UTF_8) }.getOrNull()?.let { excluded.add(it) }
                }
                "OVERRIDE" -> {
                    val keyName = parts.getOrNull(1)
                    val pathB64 = parts.getOrNull(2)
                    val valB64 = parts.getOrNull(3)
                    if (keyName != null && pathB64 != null && valB64 != null) {
                        val key = runCatching { FieldKey.valueOf(keyName) }.getOrNull()
                        val path = runCatching { String(Base64.getDecoder().decode(pathB64), Charsets.UTF_8) }.getOrNull()
                        val value = runCatching { String(Base64.getDecoder().decode(valB64), Charsets.UTF_8) }.getOrNull()
                        if (key != null && path != null && value != null) {
                            overrides.getOrPut(key) { linkedMapOf() }[path] = value
                        }
                    }
                }
                "PENDINGCOVER" -> {
                    val mime = parts.getOrNull(1)
                    val bytesB64 = parts.getOrNull(2)
                    if (mime != null && bytesB64 != null) {
                        val bytes = runCatching { Base64.getDecoder().decode(bytesB64) }.getOrNull()
                        if (bytes != null) pending = CoverImage(bytes, mime)
                    }
                }
            }
        }
        if (overrides.isEmpty() && excluded.isEmpty() && extras.isEmpty() && pending == null) return null
        return Draft(
            perFileOverrides = overrides.mapValues { it.value.toMap() },
            excludedPaths = excluded,
            extraKeys = extras.distinct(),
            pendingCover = pending,
        )
    }

    fun clear(paths: List<String>) {
        keyFile(paths).delete()
    }

    fun draftCount(): Int = dir.listFiles { f -> f.isFile && f.name.endsWith(".draft") }?.size ?: 0

    fun clearAll() {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".draft") }?.forEach { it.delete() }
    }

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun b64Bytes(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
}
