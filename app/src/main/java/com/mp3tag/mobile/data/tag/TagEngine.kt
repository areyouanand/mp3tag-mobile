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
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4FieldKey
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField
import org.jaudiotagger.tag.asf.AsfTagTextField
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
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
            // JAudioTagger rewrites files in place. Work on a temporary sibling so a
            // crash/power loss mid-save leaves the original byte-for-byte intact.
            else -> writeSafely(file) { temp -> writeWithJAudioTagger(temp, format, request, options) }
        }
    }

    /**
     * Every free-form/custom field in [file] (ID3 `TXXX`, VorbisComment keys, APEv2 items),
     * keyed by upper-case name and excluding binary artwork entries. The converter uses this
     * to copy metadata 1:1 instead of only the known [FieldKey]s.
     */
    fun readAllCustomFields(file: File): Map<String, String> = runCatching {
        when (formatFor(file)) {
            AudioFormat.DFF -> DffTagHandler.readAllCustomFields(file)
            AudioFormat.OPUS -> OpusTagHandler.readAllCustomFields(file)
            AudioFormat.APE, AudioFormat.WAVPACK -> ApeTagHandler.readAllCustomFields(file)
            AudioFormat.MP3, AudioFormat.WAV, AudioFormat.AIFF, AudioFormat.DSF -> {
                val af = readAudioFile(file)
                id3TagOf(af.tag)?.let { Id3CustomFields.readAll(it) } ?: emptyMap()
            }
            AudioFormat.FLAC, AudioFormat.OGG -> {
                vorbisTagOf(readAudioFile(file).tag)?.let { vorbisCustomFields(it) } ?: emptyMap()
            }
            AudioFormat.M4A -> readMp4CustomFields(readAudioFile(file).tag)
            AudioFormat.WMA -> readAsfCustomFields(readAudioFile(file).tag)
            else -> emptyMap()
        }
    }.getOrDefault(emptyMap())

    // ------------------------------------------------------------------
    // MP4 free-form ("----:com.apple.iTunes:KEY") and ASF attributes
    // ------------------------------------------------------------------

    /** Reverse-DNS descriptors that JAudioTagger already maps to a [FieldKey]. */
    private val KNOWN_MP4_REVERSE_DNS: Set<String> = runCatching {
        Mp4FieldKey.entries
            .filter { it.isReverseDnsType }
            .map { it.fieldName.substringAfterLast(':').uppercase() }
            .toSet()
    }.getOrDefault(emptySet())

    private val KNOWN_ASF_NAMES: Set<String> = runCatching {
        org.jaudiotagger.tag.asf.AsfFieldKey.entries.map { it.fieldName.uppercase() }.toSet()
    }.getOrDefault(emptySet())

    private fun readMp4CustomFields(tag: Tag?): Map<String, String> {
        if (tag !is Mp4Tag) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (field in tag.fields.asSequence()) {
            val reverse = field as? Mp4TagReverseDnsField ?: continue
            val key = runCatching { reverse.descriptor }.getOrNull()?.trim() ?: continue
            if (key.isEmpty()) continue
            val upper = key.uppercase()
            if (upper in KNOWN_MP4_REVERSE_DNS) continue
            val value = runCatching { reverse.content }.getOrNull()?.trim() ?: continue
            if (value.isNotEmpty()) out[upper] = value
        }
        return out
    }

    private fun readAsfCustomFields(tag: Tag?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val fields: List<org.jaudiotagger.tag.TagField> =
            tag?.getFields()?.asSequence()?.toList() ?: emptyList()
        for (field in fields) {
            if (field.isBinary) continue
            val key = runCatching { field.id }.getOrNull()?.trim() ?: continue
            if (key.isEmpty()) continue
            val upper = key.uppercase()
            if (upper in KNOWN_ASF_NAMES) continue
            // Internal encoder metadata (WM/EncodingSettings, WM/EncodingTime, …)
            // is not user tag data; ReplayGain/R128 attributes are kept.
            if (upper.startsWith("WM/") &&
                !upper.startsWith("WM/REPLAYGAIN") &&
                !upper.startsWith("WM/R128")
            ) {
                continue
            }
            val value = (field as? TagTextField)?.content?.trim() ?: continue
            if (value.isNotEmpty()) out[upper] = value
        }
        return out
    }

    private fun readAudioFile(file: File): AudioFile =
        if (file.extension.lowercase() == "asf") AudioFileIO.readAs(file, "wma")
        else AudioFileIO.read(file)

    private fun id3TagOf(tag: org.jaudiotagger.tag.Tag?): AbstractID3v2Tag? = when (tag) {
        is AbstractID3v2Tag -> tag
        is WavTag -> tag.iD3Tag
        is AiffTag -> tag.iD3Tag
        else -> null
    }

    /** FLAC wraps its Vorbis comments in a FlacTag; Ogg/Opus expose them directly. */
    private fun vorbisTagOf(
        tag: org.jaudiotagger.tag.Tag?,
    ): org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag? = when (tag) {
        is org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag -> tag
        is org.jaudiotagger.tag.flac.FlacTag -> tag.vorbisCommentTag
        else -> null
    }

    private fun vorbisCustomFields(
        tag: org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag,
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (field in tag.fields) {
            val key = runCatching { field.id }.getOrNull()?.trim() ?: continue
            if (key.isEmpty()) continue
            val upper = key.uppercase()
            if (upper == "METADATA_BLOCK_PICTURE" || upper.startsWith("COVERART")) continue
            // FieldKey-mapped comments are written by the normal field pass; copying them
            // again would let raw values (e.g. TRACKNUMBER "3/12") override split fields.
            if (upper in STANDARD_VORBIS_KEYS) continue
            val value = runCatching { tag.getFirst(key) }.getOrNull()?.trim() ?: continue
            if (value.isNotEmpty()) out[upper] = value
        }
        return out
    }

    private val STANDARD_VORBIS_KEYS = setOf(
        "VENDOR", "TITLE", "VERSION", "SUBTITLE", "ALBUM", "ALBUMARTIST", "ALBUM ARTIST",
        "ARTIST", "ARTISTS", "TRACKNUMBER", "TRACKTOTAL", "TOTALTRACKS", "DISCNUMBER",
        "DISCTOTAL", "TOTALDISCS", "COMPOSER", "DATE", "YEAR", "ORIGINALDATE", "GENRE",
        "COMMENT", "DESCRIPTION", "LYRICS", "LYRICIST", "CONDUCTOR", "REMIXER", "PRODUCER",
        "ENGINEER", "MIXER", "DJMIXER", "ARRANGER", "PERFORMER", "COPYRIGHT", "ENCODEDBY",
        "ENCODER", "ENCODING", "GROUPING", "LANGUAGE", "MOOD", "MEDIA", "LABEL",
        "ORGANIZATION", "CATALOGNUMBER", "BARCODE", "COMPILATION", "ISRC", "BPM", "KEY",
        "SCRIPT", "RELEASETYPE", "RELEASESTATUS", "RELEASECOUNTRY", "INVOLVEDPEOPLE",
        "ORCHESTRA", "CHOIR", "ENSEMBLE", "INSTRUMENT", "WORK", "MOVEMENT",
        "MOVEMENTNAME", "MOVEMENTNUMBER", "MOVEMENTTOTAL", "OCCASION", "QUALITY", "RATING",
    )

    /** Reads requested custom fields (e.g. `REPLAYGAIN_*`, `R128_*`), keyed by uppercase name. */
    fun readCustomFields(file: File, keys: Set<String>): Map<String, String> = runCatching {
        when (val format = formatFor(file)) {
            AudioFormat.DFF -> DffTagHandler.readCustomFields(file, keys)
            AudioFormat.OPUS -> OpusTagHandler.readCustomFields(file, keys)
            AudioFormat.APE, AudioFormat.WAVPACK -> ApeTagHandler.readCustomFields(file, keys)
            else -> {
                val af = if (file.extension.lowercase() == "asf") {
                    AudioFileIO.readAs(file, "wma")
                } else {
                    AudioFileIO.read(file)
                }
                val tag = af.tag ?: return@runCatching emptyMap()
                val id3Tag: AbstractID3v2Tag? = when (tag) {
                    is AbstractID3v2Tag -> tag
                    is WavTag -> tag.iD3Tag
                    is AiffTag -> tag.iD3Tag
                    else -> null
                }
                if (id3Tag != null) {
                    Id3CustomFields.read(id3Tag, keys)
                } else if (tag is org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag) {
                    val out = LinkedHashMap<String, String>()
                    for (key in keys) {
                        val v = runCatching { tag.getFirst(key) }.getOrNull()?.trim()
                            ?: runCatching { tag.getFirst(key.uppercase()) }.getOrNull()?.trim()
                            ?: runCatching { tag.getFirst(key.lowercase()) }.getOrNull()?.trim()
                        if (!v.isNullOrEmpty()) {
                            out[key.uppercase()] = v
                        }
                    }
                    out
                } else if (tag is Mp4Tag) {
                    val wanted = keys.map { it.uppercase() }.toSet()
                    readMp4CustomFields(tag).filterKeys { it in wanted }
                } else if (tag is AsfTag) {
                    val wanted = keys.map { it.uppercase() }.toSet()
                    readAsfCustomFields(tag).filterKeys { it in wanted }
                } else {
                    emptyMap()
                }
            }
        }
    }.getOrDefault(emptyMap())

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
        val multiFields = LinkedHashMap<FieldKey, List<String>>()
        val all: Map<FieldKey, String> = FieldKey.entries
            .filter { it != FieldKey.COVER_ART }
            .mapNotNull { key ->
                val values = runCatching { tag?.getAll(key) }.getOrNull()
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
                    .orEmpty()
                if (values.isEmpty()) return@mapNotNull null
                if (values.size > 1) multiFields[key] = values
                key to values.joinToString(MULTI_VALUE_SEPARATOR)
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
        }.getOrNull() ?: when (format) {
            AudioFormat.FLAC -> FlacPictureHandler.read(file)
            else -> vorbisTagOf(tag)?.let { FlacPictureHandler.readVorbisPicture(it) }
        }

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
            multiFields = multiFields,
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

        // Multi-value tags: delete the old frames/comments and add one per value so
        // two artists/genres survive instead of collapsing into "A; B".
        if (request.multiFields.isNotEmpty()) {
            val multiTarget: Tag = id3TagOf(tag) ?: vorbisTagOf(tag) ?: tag
            request.multiFields.forEach { (key, values) ->
                val clean = values.map { it.trim() }.filter { it.isNotEmpty() }
                runCatching { multiTarget.deleteField(key) }
                clean.forEach { value ->
                    runCatching { multiTarget.addField(key, value) }
                }
            }
        }

        // Free-form custom fields (TXXX for ID3, arbitrary keys for VorbisComment,
        // reverse-DNS atoms for MP4, named attributes for ASF).
        if (request.customFields.isNotEmpty()) {
            val id3Tag = id3TagOf(tag)
            val vorbisTag = vorbisTagOf(tag)
            request.customFields.forEach { (key, value) ->
                when {
                    id3Tag != null -> Id3CustomFields.write(id3Tag, key, value)
                    vorbisTag != null -> {
                        val attempt = if (value.isNullOrBlank()) {
                            runCatching { vorbisTag.deleteField(key) }
                        } else {
                            runCatching { vorbisTag.setField(key, value) }
                        }
                        if (attempt.isFailure) {
                            android.util.Log.w("TagEngine", "Vorbis custom field '$key' failed", attempt.exceptionOrNull())
                        }
                    }
                    tag is Mp4Tag -> writeMp4CustomField(tag, key, value)
                    tag is AsfTag -> writeAsfCustomField(tag, key, value)
                }
            }
        }

        when (request.coverAction) {
            CoverAction.NONE -> Unit
            CoverAction.REMOVE -> {
                runCatching { tag.deleteArtworkField() }
                vorbisTagOf(tag)?.let { runCatching { it.deleteField("METADATA_BLOCK_PICTURE") } }
            }
            CoverAction.REPLACE -> request.newCover?.let { cover ->
                val vorbis = vorbisTagOf(tag)
                if (vorbis != null) {
                    // Vorbis-family tags use a base64 PICTURE block; JAudioTagger's
                    // Artwork path throws on Android (AndroidArtwork).
                    runCatching { vorbis.deleteArtworkField() }
                    val encoded = android.util.Base64.encodeToString(
                        FlacPictureHandler.buildPictureBlock(cover),
                        android.util.Base64.NO_WRAP,
                    )
                    vorbis.setField("METADATA_BLOCK_PICTURE", encoded)
                } else {
                    runCatching { tag.deleteArtworkField() }
                    val artwork = ArtworkFactory.getNew()
                    artwork.binaryData = cover.bytes
                    artwork.mimeType = cover.mimeType
                    tag.setField(artwork)
                }
            }
        }

        af.commit()
    }

    private fun writeMp4CustomField(tag: Mp4Tag, key: String, value: String?) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return
        val id = "----:com.apple.iTunes:$cleanKey"
        runCatching { tag.deleteField(id) }
        if (value.isNullOrBlank()) return
        runCatching {
            tag.setField(Mp4TagReverseDnsField(id, "com.apple.iTunes", cleanKey, value))
        }.onFailure {
            android.util.Log.w("TagEngine", "MP4 free-form field '$cleanKey' failed", it)
        }
    }

    private fun writeAsfCustomField(tag: AsfTag, key: String, value: String?) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return
        runCatching { tag.deleteField(cleanKey) }
        if (value.isNullOrBlank()) return
        runCatching {
            tag.setField(AsfTagTextField(cleanKey, value))
        }.onFailure {
            android.util.Log.w("TagEngine", "ASF attribute '$cleanKey' failed", it)
        }
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
                    // MP3File.setID3v2Tag() only updates its id3v2tag field; AudioFile.tag
                    // would still point at the converted tag and swallow later edits.
                    af.setTag(newTag)
                }
            }

            // WAV/AIFF need an explicit ID3 sub-tag before TXXX custom fields (and
            // ID3-specific fields) can be written; otherwise the write is a silent no-op.
            format == AudioFormat.WAV -> {
                val wavTag = af.tag as? WavTag
                if (wavTag != null && wavTag.iD3Tag == null) {
                    wavTag.setID3Tag(if (want == ID3V2Version.ID3_V24) ID3v24Tag() else ID3v23Tag())
                }
            }

            format == AudioFormat.AIFF -> {
                val aiffTag = af.tag as? AiffTag
                if (aiffTag != null && aiffTag.iD3Tag == null) {
                    aiffTag.setID3Tag(if (want == ID3V2Version.ID3_V24) ID3v24Tag() else ID3v23Tag())
                }
            }

            format == AudioFormat.DSF -> {                val current = af.tag as? AbstractID3v2Tag
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
