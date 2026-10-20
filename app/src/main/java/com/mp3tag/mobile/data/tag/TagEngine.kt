package com.mp3tag.mobile.data.tag

import com.mp3tag.mobile.data.prefs.Id3Version
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.aiff.AiffTag
import org.jaudiotagger.tag.asf.AsfTag
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.ID3V2Version
import org.jaudiotagger.tag.wav.WavTag
import java.io.File

/**
 * Reads and writes audio tags across all supported formats:
 * - JAudioTagger: MP3, FLAC, M4A/MP4/M4B, Ogg Vorbis, WAV (RIFF INFO + ID3), AIFF/AIF/AIFC (ID3v2),
 *   WMA/ASF, DSF (DSD)
 * - Custom binary handlers:
 *   - DFF (DSDIFF) via [DffTagHandler] (embedded `"ID3 "` chunk inside `"FRM8"`)
 *   - Opus (`.opus`) via [OpusTagHandler] (RFC 7845 `OpusHead` + `OpusTags` VorbisComment)
 *   - Monkey's Audio (`.ape`) & WavPack (`.wv`) via [ApeTagHandler] (`APETAGEX` APEv2)
 */
object TagEngine {

    init {
        // Artwork handling without java.awt (required on Android).
        TagOptionSingleton.getInstance().isAndroid = true
    }

    fun formatFor(file: File): AudioFormat = when (file.extension.lowercase()) {
        "mp3" -> AudioFormat.MP3
        "flac" -> AudioFormat.FLAC
        "m4a", "mp4", "m4b" -> AudioFormat.M4A
        "ogg", "oga" -> AudioFormat.OGG
        "opus" -> AudioFormat.OPUS
        "wav" -> AudioFormat.WAV
        "aif", "aiff", "aifc" -> AudioFormat.AIFF
        "wma", "asf" -> AudioFormat.WMA
        "ape" -> AudioFormat.APE
        "wv" -> AudioFormat.WAVPACK
        "dsf" -> AudioFormat.DSF
        "dff" -> AudioFormat.DFF
        else -> AudioFormat.OTHER
    }

    /** Reads the tag of [file]; throws on unreadable/corrupt files. */
    fun read(file: File): TagSnapshot {
        return when (val format = formatFor(file)) {
            AudioFormat.DFF -> DffTagHandler.read(file)
            AudioFormat.OPUS -> OpusTagHandler.read(file)
            AudioFormat.APE, AudioFormat.WAVPACK -> ApeTagHandler.read(file, format)
            else -> readWithJAudioTagger(file, format)
        }
    }

    /** Applies [request] to [file] and commits to disk; throws on failure. */
    fun write(file: File, request: WriteRequest, options: WriteOptions) {
        when (val format = formatFor(file)) {
            AudioFormat.DFF -> DffTagHandler.write(file, request, options)
            AudioFormat.OPUS -> OpusTagHandler.write(file, request)
            AudioFormat.APE, AudioFormat.WAVPACK -> ApeTagHandler.write(file, request)
            else -> writeWithJAudioTagger(file, format, request, options)
        }
    }

    // ---------------------------------------------------------------------
    // JAudioTagger path (MP3, FLAC, M4A/MP4/M4B, Ogg, WAV, AIFF, WMA/ASF, DSF)
    // ---------------------------------------------------------------------

    private fun readWithJAudioTagger(file: File, format: AudioFormat): TagSnapshot {
        val af = if (file.extension.lowercase() == "asf") {
            AudioFileIO.readAs(file, "wma")
        } else {
            AudioFileIO.read(file)
        }
        val tag = af.tag
        val header = af.audioHeader

        // Scan every standard field key that has a value in this file; the editor
        // uses this to offer "fields present in the files" beyond the core set.
        val all: Map<FieldKey, String> = FieldKey.entries
            .filter { it != FieldKey.COVER_ART }
            .mapNotNull { key ->
                runCatching { tag?.getFirst(key) }.getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { key to it }
            }
            .toMap()
        val fields = normalizeSplitFieldsInternal(all)

        val cover = runCatching {
            tag?.firstArtwork?.let { art ->
                art.binaryData?.let { bytes ->
                    CoverImage(
                        bytes = bytes,
                        mimeType = art.mimeType?.takeUnless { it.isBlank() } ?: sniffImageMime(bytes),
                    )
                }
            }
        }.getOrNull()

        return TagSnapshot(
            path = file.absolutePath,
            format = format,
            durationSeconds = header?.trackLength,
            bitrateKbps = header?.bitRate?.filter { it.isDigit() }?.toIntOrNull(),
            sampleRateHz = header?.sampleRate?.filter { it.isDigit() }?.toIntOrNull(),
            tagDescription = describeTag(af, format),
            fields = fields,
            cover = cover,
            fileSizeBytes = file.length(),
        )
    }

    private fun writeWithJAudioTagger(
        file: File,
        format: AudioFormat,
        request: WriteRequest,
        options: WriteOptions,
    ) {
        val af = if (file.extension.lowercase() == "asf") {
            AudioFileIO.readAs(file, "wma")
        } else {
            AudioFileIO.read(file)
        }
        ensureTag(af, format, options)

        val tag = requireNotNull(af.tag) { "No tag could be created for this file" }

        request.fieldValues.forEach { (key, value) ->
            if (value.isNullOrBlank()) {
                runCatching { tag.deleteField(key) }
            } else {
                tag.setField(key, value)
            }
        }

        when (request.coverAction) {
            CoverAction.NONE -> Unit
            CoverAction.REMOVE -> runCatching { tag.deleteArtworkField() }
            CoverAction.REPLACE -> request.newCover?.let { cover ->
                runCatching { tag.deleteArtworkField() }
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = cover.bytes
                artwork.mimeType = cover.mimeType
                tag.setField(artwork)
            }
        }

        af.commit()
    }

    /**
     * Makes sure [af] has a writable tag. MP3 and DSF files additionally get their
     * ID3v2 tag created or converted to the configured version (v2.3/v2.4).
     */
    private fun ensureTag(af: AudioFile, format: AudioFormat, options: WriteOptions) {
        val want = if (options.id3Version == Id3Version.V2_4) {
            ID3V2Version.ID3_V24
        } else {
            ID3V2Version.ID3_V23
        }
        when {
            af is MP3File -> {
                val current: AbstractID3v2Tag? = af.getID3v2Tag()
                val alreadyRight = current != null && (current is ID3v24Tag) == (want == ID3V2Version.ID3_V24)
                if (!alreadyRight) {
                    val newTag: AbstractID3v2Tag = if (current == null) {
                        if (want == ID3V2Version.ID3_V24) ID3v24Tag() else ID3v23Tag()
                    } else {
                        af.convertID3Tag(current, want)
                    }
                    af.setID3v2Tag(newTag)
                }
            }

            format == AudioFormat.DSF -> {
                val current = af.tag as? AbstractID3v2Tag
                val wantV24 = options.id3Version == Id3Version.V2_4
                if (current == null) {
                    af.setTag(if (wantV24) ID3v24Tag() else ID3v23Tag())
                } else if (wantV24 && current !is ID3v24Tag) {
                    af.setTag(ID3v24Tag(current))
                } else if (!wantV24 && current !is ID3v23Tag) {
                    af.setTag(ID3v23Tag(current))
                }
            }

            af.tag == null -> af.setTag(af.createDefaultTag())
        }
    }

    /** ID3 and APE store track/disc as "n/total"; split that into the two editor fields. */
    internal fun normalizeSplitFieldsInternal(fields: Map<FieldKey, String>): Map<FieldKey, String> {
        val out = fields.toMutableMap()
        fun split(primary: FieldKey, total: FieldKey) {
            val value = out[primary] ?: return
            if (!value.contains('/')) return
            val parts = value.split('/', limit = 2)
            out[primary] = parts.getOrNull(0)?.trim().orEmpty()
            val totalPart = parts.getOrNull(1)?.trim().orEmpty()
            val existing = out[total]
            if ((existing == null || existing.isBlank()) && totalPart.isNotBlank()) out[total] = totalPart
        }
        split(FieldKey.TRACK, FieldKey.TRACK_TOTAL)
        split(FieldKey.DISC_NO, FieldKey.DISC_TOTAL)
        return out
    }

    private fun describeTag(af: AudioFile, format: AudioFormat): String = runCatching {
        when (af) {
            is MP3File -> when (af.getID3v2Tag()) {
                null -> "ID3v1"
                is ID3v24Tag -> "ID3v2.4"
                is ID3v23Tag -> "ID3v2.3"
                else -> "ID3v2"
            }
            else -> when (af.tag) {
                is WavTag -> "WAV (ID3/INFO)"
                is AiffTag -> "AIFF (ID3v2)"
                is AsfTag -> "WMA / ASF"
                else -> when (format) {
                    AudioFormat.FLAC, AudioFormat.OGG -> "Vorbis comments"
                    AudioFormat.M4A -> "MP4 atoms"
                    AudioFormat.DSF -> "ID3v2 (DSD)"
                    AudioFormat.AIFF -> "AIFF (ID3v2)"
                    AudioFormat.WMA -> "WMA / ASF"
                    else -> af.audioHeader?.format ?: "Unknown"
                }
            }
        }
    }.getOrDefault("Unknown")
}
