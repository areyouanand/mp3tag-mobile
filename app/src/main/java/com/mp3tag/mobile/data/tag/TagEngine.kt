package com.mp3tag.mobile.data.tag

import com.mp3tag.mobile.data.prefs.Id3Version
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.ID3V2Version
import org.jaudiotagger.tag.wav.WavTag
import java.io.File

/**
 * Reads and writes audio tags through JAudioTagger.
 *
 * Phase 1 formats: MP3, FLAC, M4A/MP4, Ogg, WAV (RIFF INFO + ID3), DSF (DSD).
 * DFF (DSDIFF) support is planned via TagLib/JNI in a later phase.
 */
object TagEngine {

    init {
        // Artwork handling without java.awt (required on Android).
        TagOptionSingleton.getInstance().isAndroid = true
    }

    fun formatFor(file: File): AudioFormat = when (file.extension.lowercase()) {
        "mp3" -> AudioFormat.MP3
        "flac" -> AudioFormat.FLAC
        "m4a", "mp4" -> AudioFormat.M4A
        "ogg", "oga" -> AudioFormat.OGG
        "wav" -> AudioFormat.WAV
        "dsf" -> AudioFormat.DSF
        else -> AudioFormat.OTHER
    }

    /** Reads the tag of [file]; throws on unreadable/corrupt files. */
    fun read(file: File): TagSnapshot {
        val af = AudioFileIO.read(file)
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
        val fields = normalizeSplitFields(all)

        val cover = runCatching {
            tag?.getFirstArtwork()?.let { art ->
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
            format = formatFor(file),
            durationSeconds = header?.trackLength,
            bitrateKbps = header?.bitRate?.toIntOrNull(),
            sampleRateHz = header?.sampleRate?.toIntOrNull(),
            tagDescription = describeTag(af, file),
            fields = fields,
            cover = cover,
            fileSizeBytes = file.length(),
        )
    }

    /** Applies [request] to [file] and commits to disk; throws on failure. */
    fun write(file: File, request: WriteRequest, options: WriteOptions) {
        val af = AudioFileIO.read(file)
        ensureTag(af, file, options)

        val tag = requireNotNull(af.tag) { "No tag could be created for this file" }

        request.fieldValues.forEach { (key, value) ->
            if (value.isNullOrBlank()) {
                tag.deleteField(key)
            } else {
                tag.setField(key, value)
            }
        }

        when (request.coverAction) {
            CoverAction.NONE -> Unit
            CoverAction.REMOVE -> tag.deleteArtworkField()
            CoverAction.REPLACE -> request.newCover?.let { cover ->
                tag.deleteArtworkField()
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = cover.bytes
                artwork.mimeType = cover.mimeType
                tag.setField(artwork)
            }
        }

        af.commit()
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * Makes sure [af] has a writable tag. MP3 files additionally get their
     * ID3v2 tag created or converted to the configured version (v2.3/v2.4).
     */
    private fun ensureTag(af: AudioFile, file: File, options: WriteOptions) {
        when {
            af is MP3File -> {
                val want = if (options.id3Version == Id3Version.V2_4) {
                    ID3V2Version.ID3_V24
                } else {
                    ID3V2Version.ID3_V23
                }
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

            af.tag == null -> af.setTag(af.createDefaultTag())
        }
    }

    /** ID3 stores track/disc as "n/total"; split that into the two editor fields. */
    private fun normalizeSplitFields(fields: Map<FieldKey, String>): Map<FieldKey, String> {
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

    private fun describeTag(af: AudioFile, file: File): String = runCatching {
        when (af) {
            is MP3File -> when (af.getID3v2Tag()) {
                null -> "ID3v1"
                is ID3v24Tag -> "ID3v2.4"
                is ID3v23Tag -> "ID3v2.3"
                else -> "ID3v2"
            }
            else -> when (af.tag) {
                is WavTag -> "WAV (ID3/INFO)"
                else -> when (formatFor(file)) {
                    AudioFormat.FLAC, AudioFormat.OGG -> "Vorbis comments"
                    AudioFormat.M4A -> "MP4 atoms"
                    AudioFormat.DSF -> "ID3v2 (DSD)"
                    else -> af.audioHeader?.format ?: "Unknown"
                }
            }
        }
    }.getOrDefault("Unknown")
}
