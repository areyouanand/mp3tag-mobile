package com.mp3tag.mobile.data.convert

import com.mp3tag.mobile.data.prefs.Id3Version
import com.mp3tag.mobile.data.tag.AudioFormat
import com.mp3tag.mobile.data.tag.CoverAction
import com.mp3tag.mobile.data.tag.EDITABLE_FIELDS
import com.mp3tag.mobile.data.tag.FlacPictureHandler
import com.mp3tag.mobile.data.tag.TagEngine
import com.mp3tag.mobile.data.tag.TagSnapshot
import com.mp3tag.mobile.data.tag.WriteOptions
import com.mp3tag.mobile.data.tag.WriteRequest
import com.mp3tag.mobile.util.PatternParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.FieldKey
import java.io.File

/** Per-file progress while converting a batch. */
data class ConversionProgress(
    val index: Int,
    val total: Int,
    val fileName: String,
    val filePercent: Int,
) {
    /** 0..100 across the whole batch. */
    val overallPercent: Int
        get() = if (total <= 0) 0 else ((index * 100 + filePercent.coerceIn(0, 100)) / total)
}

data class ConversionFileResult(
    val sourcePath: String,
    /** Final output file, when the audio conversion itself succeeded. */
    val outputPath: String? = null,
    /** Non-fatal problem (tags/cover could not be copied). */
    val warning: String? = null,
    /** Fatal problem; no usable output was produced. */
    val error: String? = null,
)

data class ConversionOutcome(
    val results: List<ConversionFileResult>,
    val cancelled: Boolean,
) {
    val succeeded: List<ConversionFileResult> get() = results.filter { it.outputPath != null }
    val failed: List<ConversionFileResult> get() = results.filter { it.error != null }
}

/**
 * Converts audio files through the bundled FFmpeg build and re-applies the source
 * tags + embedded cover art to the new file.
 *
 * Files are processed sequentially (decode + encode is CPU bound); progress and
 * cancellation surface through [FfmpegBridge].
 */
object AudioConverter {

    /** Serializes callers: the native FFmpeg layer is process-global and single-run. */
    private val convertMutex = Mutex()

    suspend fun convert(
        paths: List<String>,
        settings: ConvertSettings,
        id3Version: Id3Version,
        onProgress: (ConversionProgress) -> Unit,
        shouldCancel: () -> Boolean = { false },
    ): ConversionOutcome = convertMutex.withLock {
        withContext(Dispatchers.IO) {
        val options = settings.normalized()
        val results = ArrayList<ConversionFileResult>(paths.size)
        var cancelled = false

        if (!FfmpegBridge.isAvailable) {
            return@withContext ConversionOutcome(
                paths.map {
                    ConversionFileResult(
                        sourcePath = it,
                        error = "FFmpeg is unavailable: ${FfmpegBridge.unavailableReason}",
                    )
                },
                cancelled = false,
            )
        }

        for ((index, path) in paths.withIndex()) {
            if (cancelled || shouldCancel()) {
                cancelled = true
                break
            }
            ensureActive()

            val source = File(path)
            val name = source.name
            onProgress(ConversionProgress(index, paths.size, name, 0))

            if (!source.isFile) {
                results += ConversionFileResult(path, error = "File not found")
                continue
            }

            val output = outputFileFor(source, options)
            val encoder = options.target.encoderFor(options.bitDepth)
            val result = FfmpegBridge.transcode(
                inputPath = source.absolutePath,
                outputPath = output.absolutePath,
                muxer = options.target.muxer,
                encoder = encoder,
                bitrateKbps = options.bitrateKbps,
                sampleRateHz = if (options.target == ConvertTarget.OPUS) 48000 else options.sampleRateHz,
                channels = options.channels,
                quality = options.target.qualityFor(options.compressionLevel),
                callback = { percent ->
                    if (!cancelled) {
                        onProgress(ConversionProgress(index, paths.size, name, percent))
                    }
                },
            )

            if (result == FfmpegBridge.RESULT_CANCELLED) {
                output.delete()
                cancelled = true
                break
            }
            if (result != FfmpegBridge.RESULT_OK || !output.isFile || output.length() == 0L) {
                val message = FfmpegBridge.lastError()
                output.delete()
                results += ConversionFileResult(path, error = message)
                continue
            }

            var warning: String? = null
            if (options.keepTagsAndCover) {
                warning = copyTags(source, output, id3Version)
            }
            if (options.preserveTimestamps) {
                val modified = source.lastModified()
                if (modified > 0) output.setLastModified(modified)
            }

            if (options.deleteSourceAfterSuccess) {
                val deleted = runCatching { source.delete() }.getOrDefault(false)
                if (!deleted) {
                    warning = listOfNotNull(warning, "Converted, but the source file could not be deleted")
                        .joinToString(" · ")
                }
            }

            results += ConversionFileResult(
                sourcePath = path,
                outputPath = output.absolutePath,
                warning = warning,
            )
            onProgress(ConversionProgress(index, paths.size, name, 100))
        }

        ConversionOutcome(results, cancelled)
        }
    }

    /** Picks a name that never collides with an existing file or the source itself. */
    fun uniqueOutputFile(source: File, target: ConvertTarget): File =
        uniqueIn(source.parentFile ?: File("."), source.nameWithoutExtension, source, target.extension)

    /**
     * Resolves the output path from the optional subfolder + filename pattern.
     * Pattern segments may contain "/" to build a folder tree, e.g.
     * `%albumartist%/%album%/%track% %title%`.
     */
    fun outputFileFor(source: File, settings: ConvertSettings): File {
        val options = settings.normalized()
        val root = if (options.outputSubfolder.isBlank()) {
            source.parentFile ?: File(".")
        } else {
            File(source.parentFile ?: File("."), options.outputSubfolder).also { it.mkdirs() }
        }

        val base = if (options.namePattern.isBlank()) {
            source.nameWithoutExtension
        } else {
            val snapshot = runCatching { TagEngine.read(source) }.getOrNull()
            val segments = PatternParser.compile(options.namePattern)
            if (snapshot != null && segments != null) {
                PatternParser.buildFileName(segments, snapshot.fields) ?: source.nameWithoutExtension
            } else {
                source.nameWithoutExtension
            }
        }

        val relative = base.split('/', '\\')
            .map { PatternParser.sanitizeFileName(it.trim()) }
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString(File.separator)
            .ifBlank { source.nameWithoutExtension }

        val targetDir = File(root, relative.substringBeforeLast(File.separatorChar, ""))
            .takeIf { relative.contains(File.separatorChar) }
            ?.also { it.mkdirs() }
            ?: root
        val leaf = relative.substringAfterLast(File.separatorChar)
        return uniqueIn(targetDir, leaf, source, options.target.extension)
    }

    private fun uniqueIn(dir: File, rawBase: String, source: File, extension: String): File {
        val base = PatternParser.sanitizeFileName(rawBase).ifBlank { "converted" }
        val sourcePath = source.absolutePath

        fun candidate(suffix: String): File = File(dir, "$base$suffix.$extension")

        val preferred = candidate("")
        if (!preferred.exists() && preferred.absolutePath != sourcePath) return preferred

        var attempt = candidate(" (converted)")
        var n = 2
        while (attempt.exists() || attempt.absolutePath == sourcePath) {
            attempt = candidate(" (converted $n)")
            n++
        }
        return attempt
    }

    /**
     * Copies the source tag set + cover into [destination]. Formats disagree on
     * which fields they support, so field copying degrades gracefully: all fields
     * first, then the editor's core fields, then just title/artist/album. Artwork is
     * written in a second pass because some formats cannot embed it on Android
     * (JAudioTagger's `AndroidArtwork.setImageFromData()` throws for FLAC).
     */
    private fun copyTags(source: File, destination: File, id3Version: Id3Version): String? {
        val snapshot = runCatching { TagEngine.read(source) }.getOrElse { error ->
            return "Tags not copied: ${error.message ?: "unreadable source tag"}"
        }
        val format = TagEngine.formatFor(destination)
        var warning: String? = null

        val customFields = runCatching { TagEngine.readAllCustomFields(source) }.getOrDefault(emptyMap())
        val multiForTarget = if (format == AudioFormat.APE || format == AudioFormat.WAVPACK) {
            if (snapshot.multiFields.isNotEmpty()) {
                warning = append(warning, "Multi-value tags not copied: APEv2 stores one value per tag")
            }
            emptyMap()
        } else {
            snapshot.multiFields
        }

        // Fast path: one atomic write for fields + multi-values + custom fields (+ cover
        // for every format except FLAC, whose PICTURE block is written separately).
        // Falls back to the per-part passes below when a container rejects a field.
        val coverInRequest = when {
            format == AudioFormat.FLAC || snapshot.cover == null -> CoverAction.NONE
            else -> CoverAction.REPLACE
        }
        val combined = runCatching {
            TagEngine.write(
                destination,
                WriteRequest(
                    fieldValues = snapshot.fields,
                    multiFields = multiForTarget,
                    customFields = customFields,
                    coverAction = coverInRequest,
                    newCover = snapshot.cover,
                ),
                WriteOptions(id3Version),
            )
        }
        if (combined.isSuccess) {
            if (format == AudioFormat.FLAC) {
                snapshot.cover?.let { cover ->
                    val attempt = runCatching { FlacPictureHandler.write(destination, cover) }
                    if (attempt.isFailure) {
                        val failure = attempt.exceptionOrNull()
                        val why = failure?.message ?: failure?.javaClass?.simpleName ?: "unsupported"
                        warning = append(warning, "Cover art not copied: $why")
                    }
                }
            }
            return verifyCopy(destination, snapshot, warning)
        }
        android.util.Log.w(
            "AudioConverter",
            "Combined tag copy failed for ${destination.name}; using per-part fallback",
            combined.exceptionOrNull(),
        )

        if (snapshot.fields.isNotEmpty()) {
            val attempts = listOf(
                snapshot.fields,
                snapshot.fields.filterKeys { it in EDITABLE_FIELDS },
                snapshot.fields.filterKeys { it in setOf(FieldKey.TITLE, FieldKey.ARTIST, FieldKey.ALBUM) },
            ).distinct()

            var lastError: String? = null
            var copied = false
            for (fields in attempts) {
                val attempt = runCatching {
                    TagEngine.write(
                        destination,
                        WriteRequest(fieldValues = fields),
                        WriteOptions(id3Version),
                    )
                }
                if (attempt.isSuccess) {
                    copied = true
                    break
                }
                val failure = attempt.exceptionOrNull()
                lastError = failure?.message ?: failure?.javaClass?.simpleName ?: "unknown error"
                android.util.Log.w(
                    "AudioConverter",
                    "Tag copy failed for ${destination.name} (${fields.size} fields)",
                    failure,
                )
            }
            if (!copied) warning = append(warning, "Tags not copied: ${lastError ?: "unknown error"}")
        }

        // Preserve separate values for multi-value tags (two artists, several genres).
        if (multiForTarget.isNotEmpty()) {
            val attempt = runCatching {
                TagEngine.write(
                    destination,
                    WriteRequest(multiFields = multiForTarget),
                    WriteOptions(id3Version),
                )
            }
            if (attempt.isFailure) {
                val failure = attempt.exceptionOrNull()
                android.util.Log.w("AudioConverter", "Multi-value copy failed for ${destination.name}", failure)
                val why = failure?.message ?: failure?.javaClass?.simpleName ?: "unknown error"
                warning = append(warning, "Multi-value tags not copied: $why")
            }
        }

        // Free-form fields (TXXX / VorbisComment / APEv2 / MP4 reverse-DNS / ASF):
        // ReplayGain, MusicBrainz extras, custom user fields. Kept separate from the
        // FieldKey pass so a failure is isolated.
        val custom = customFields
        if (custom.isNotEmpty()) {
            val attempt = runCatching {
                TagEngine.write(destination, WriteRequest(customFields = custom), WriteOptions(id3Version))
            }
            if (attempt.isFailure) {
                val failure = attempt.exceptionOrNull()
                android.util.Log.w("AudioConverter", "Custom field copy failed for ${destination.name}", failure)
                val why = failure?.message ?: failure?.javaClass?.simpleName ?: "unknown error"
                warning = append(warning, "Custom fields not copied: $why")
            }
        }

        snapshot.cover?.let { cover ->
            val attempt = runCatching {
                when (format) {
                    // JAudioTagger's AndroidArtwork throws on write; FLAC PICTURE blocks
                    // are written directly at the container level instead.
                    AudioFormat.FLAC -> FlacPictureHandler.write(destination, cover)
                    else -> TagEngine.write(
                        destination,
                        WriteRequest(coverAction = CoverAction.REPLACE, newCover = cover),
                        WriteOptions(id3Version),
                    )
                }
            }
            if (attempt.isFailure) {
                val failure = attempt.exceptionOrNull()
                val why = when (failure) {
                    is UnsupportedOperationException -> "this format can't embed cover art on Android"
                    else -> failure?.message ?: failure?.javaClass?.simpleName ?: "unsupported"
                }
                android.util.Log.w("AudioConverter", "Cover copy failed for ${destination.name}", failure)
                warning = append(warning, "Cover art not copied: $why")
            } else if (runCatching { TagEngine.read(destination).cover }.getOrNull() == null) {
                // Write reported success but the format did not store it (WavPack today).
                warning = append(warning, "Cover art not copied: ${format.name} did not store it")
            }
        }

        // Cheap end-to-end check: the core identity fields should read back. Some
        // containers normalise values, so only report fields that disappeared.
        return verifyCopy(destination, snapshot, warning)
    }

    private fun verifyCopy(destination: File, snapshot: TagSnapshot, warning: String?): String? {
        var result = warning
        val actual = runCatching { TagEngine.read(destination) }.getOrNull()
        if (actual != null) {
            val missing = listOf(FieldKey.TITLE, FieldKey.ARTIST, FieldKey.ALBUM)
                .filter { key ->
                    val expected = snapshot.fields[key]
                    !expected.isNullOrBlank() && actual.fields[key].isNullOrBlank()
                }
            if (missing.isNotEmpty()) {
                result = append(
                    result,
                    "Tags may be incomplete: ${missing.joinToString(", ") { it.name.lowercase() }} not readable after write",
                )
            }
            if (snapshot.cover != null && actual.cover == null) {
                result = append(result, "Cover art not copied: ${TagEngine.formatFor(destination).name} did not store it")
            }
        }
        return result
    }

    private fun append(existing: String?, message: String): String =
        if (existing.isNullOrBlank()) message else "$existing · $message"
}
