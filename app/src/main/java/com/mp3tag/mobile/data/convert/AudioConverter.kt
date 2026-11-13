package com.mp3tag.mobile.data.convert

import com.mp3tag.mobile.data.prefs.Id3Version
import com.mp3tag.mobile.data.tag.CoverAction
import com.mp3tag.mobile.data.tag.EDITABLE_FIELDS
import com.mp3tag.mobile.data.tag.TagEngine
import com.mp3tag.mobile.data.tag.WriteOptions
import com.mp3tag.mobile.data.tag.WriteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    suspend fun convert(
        paths: List<String>,
        settings: ConvertSettings,
        id3Version: Id3Version,
        onProgress: (ConversionProgress) -> Unit,
        shouldCancel: () -> Boolean = { false },
    ): ConversionOutcome = withContext(Dispatchers.IO) {
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

            val output = uniqueOutputFile(source, options.target)
            val encoder = options.target.encoderFor(options.bitDepth)
            val result = FfmpegBridge.transcode(
                inputPath = source.absolutePath,
                outputPath = output.absolutePath,
                muxer = options.target.muxer,
                encoder = encoder,
                bitrateKbps = options.bitrateKbps,
                sampleRateHz = if (options.target == ConvertTarget.OPUS) 48000 else options.sampleRateHz,
                channels = options.channels,
                quality = options.target.qualityFor(),
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

    /** Picks a name that never collides with an existing file or the source itself. */
    fun uniqueOutputFile(source: File, target: ConvertTarget): File {
        val dir = source.parentFile ?: File(".")
        val base = source.nameWithoutExtension
        val sourcePath = source.absolutePath

        fun candidate(suffix: String): File =
            File(dir, "$base$suffix.${target.extension}")

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
        var warning: String? = null

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
            if (!copied) warning = "Tags not copied: ${lastError ?: "unknown error"}"
        }

        snapshot.cover?.let { cover ->
            val attempt = runCatching {
                TagEngine.write(
                    destination,
                    WriteRequest(coverAction = CoverAction.REPLACE, newCover = cover),
                    WriteOptions(id3Version),
                )
            }
            if (attempt.isFailure) {
                val failure = attempt.exceptionOrNull()
                val why = when (failure) {
                    is UnsupportedOperationException -> "this format can't embed cover art on Android"
                    else -> failure?.message ?: failure?.javaClass?.simpleName ?: "unsupported"
                }
                android.util.Log.w("AudioConverter", "Cover copy failed for ${destination.name}", failure)
                warning = listOfNotNull(warning, "Cover art not copied: $why").joinToString(" · ")
            }
        }
        return warning
    }
}
