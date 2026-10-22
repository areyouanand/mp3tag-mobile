package com.mp3tag.mobile.data.tag

import com.mp3tag.mobile.data.prefs.Id3Version
import org.jaudiotagger.tag.FieldKey

/** Supported audio formats across Phase 1–3. */
enum class AudioFormat(val extension: String) {
    MP3("mp3"),
    FLAC("flac"),
    M4A("m4a"),
    OGG("ogg"),
    OPUS("opus"),
    WAV("wav"),
    AIFF("aiff"),
    WMA("wma"),
    APE("ape"),
    WAVPACK("wv"),
    DSF("dsf"),
    DFF("dff"),
    OTHER(""),
}

/** Embedded artwork with O(1) cached content hash for fast batch deduplication. */
data class CoverImage(val bytes: ByteArray, val mimeType: String) {
    private val cachedHash: Int = bytes.contentHashCode() * 31 + mimeType.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoverImage) return false
        if (cachedHash != other.cachedHash || bytes.size != other.bytes.size || mimeType != other.mimeType) {
            return false
        }
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = cachedHash
}

/** Everything read from one file. [fields] contains every tag field present with a value. */
data class TagSnapshot(
    val path: String,
    val format: AudioFormat,
    val durationSeconds: Int?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val tagDescription: String,
    val fields: Map<FieldKey, String>,
    val cover: CoverImage?,
    val fileSizeBytes: Long,
)

data class WriteOptions(
    val id3Version: Id3Version = Id3Version.V2_3,
)

enum class CoverAction { NONE, REPLACE, REMOVE }

/**
 * A single write request for one file.
 * [fieldValues]: value to set; `null` or empty means "remove this field".
 */
data class WriteRequest(
    val fieldValues: Map<FieldKey, String?> = emptyMap(),
    val customFields: Map<String, String> = emptyMap(),
    val coverAction: CoverAction = CoverAction.NONE,
    val newCover: CoverImage? = null,
)

/** The fields always shown in the editor, in display order. */
val EDITABLE_FIELDS: List<FieldKey> = listOf(
    FieldKey.TITLE,
    FieldKey.ARTIST,
    FieldKey.ALBUM_ARTIST,
    FieldKey.ALBUM,
    FieldKey.YEAR,
    FieldKey.TRACK,
    FieldKey.TRACK_TOTAL,
    FieldKey.DISC_NO,
    FieldKey.DISC_TOTAL,
    FieldKey.GENRE,
    FieldKey.COMPOSER,
    FieldKey.COMMENT,
)

/** Additional standard fields offered in the "Add field" dropdown, in display order. */
val OFFERABLE_FIELDS: List<FieldKey> = listOf(
    FieldKey.SUBTITLE,
    FieldKey.VERSION,
    FieldKey.DISC_SUBTITLE,
    FieldKey.BPM,
    FieldKey.KEY,
    FieldKey.LANGUAGE,
    FieldKey.MOOD,
    FieldKey.TEMPO,
    FieldKey.GROUPING,
    FieldKey.LYRICS,
    FieldKey.LYRICIST,
    FieldKey.TITLE_SORT,
    FieldKey.ARTIST_SORT,
    FieldKey.ALBUM_SORT,
    FieldKey.ALBUM_ARTIST_SORT,
    FieldKey.COMPOSER_SORT,
    FieldKey.CONDUCTOR,
    FieldKey.ARRANGER,
    FieldKey.ENGINEER,
    FieldKey.PRODUCER,
    FieldKey.REMIXER,
    FieldKey.DJMIXER,
    FieldKey.MIXER,
    FieldKey.PERFORMER,
    FieldKey.ORCHESTRA,
    FieldKey.CHOIR,
    FieldKey.ENSEMBLE,
    FieldKey.INSTRUMENT,
    FieldKey.INVOLVEDPEOPLE,
    FieldKey.ISRC,
    FieldKey.ISWC,
    FieldKey.BARCODE,
    FieldKey.CATALOG_NO,
    FieldKey.RECORD_LABEL,
    FieldKey.COUNTRY,
    FieldKey.MEDIA,
    FieldKey.ENCODER,
    FieldKey.COPYRIGHT,
    FieldKey.ORIGINAL_ARTIST,
    FieldKey.ORIGINAL_ALBUM,
    FieldKey.ORIGINAL_LYRICIST,
    FieldKey.ORIGINAL_YEAR,
    FieldKey.ORIGINALRELEASEDATE,
    FieldKey.MOVEMENT,
    FieldKey.MOVEMENT_NO,
    FieldKey.MOVEMENT_TOTAL,
    FieldKey.OPUS,
    FieldKey.WORK,
    FieldKey.IS_COMPILATION,
    FieldKey.RATING,
    FieldKey.QUALITY,
    FieldKey.OCCASION,
    FieldKey.SCRIPT,
    FieldKey.URL_OFFICIAL_RELEASE_SITE,
    FieldKey.URL_OFFICIAL_ARTIST_SITE,
    FieldKey.URL_WIKIPEDIA_RELEASE_SITE,
    FieldKey.URL_WIKIPEDIA_ARTIST_SITE,
    FieldKey.URL_DISCOGS_RELEASE_SITE,
    FieldKey.URL_DISCOGS_ARTIST_SITE,
    FieldKey.URL_LYRICS_SITE,
    FieldKey.MUSICBRAINZ_TRACK_ID,
    FieldKey.MUSICBRAINZ_RELEASEID,
    FieldKey.MUSICBRAINZ_RELEASE_TRACK_ID,
    FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID,
    FieldKey.MUSICBRAINZ_RELEASEARTISTID,
    FieldKey.MUSICBRAINZ_ARTISTID,
    FieldKey.MUSICBRAINZ_WORK_ID,
    FieldKey.MUSICBRAINZ_DISC_ID,
    FieldKey.MUSICBRAINZ_RELEASE_STATUS,
    FieldKey.MUSICBRAINZ_RELEASE_TYPE,
    FieldKey.MUSICBRAINZ_RELEASE_COUNTRY,
    FieldKey.ACOUSTID_ID,
    FieldKey.ACOUSTID_FINGERPRINT,
    FieldKey.MUSICIP_ID,
    FieldKey.AMAZON_ID,
)

fun sniffImageMime(bytes: ByteArray): String = when {
    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
    bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
    bytes.size >= 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "image/webp"
    else -> "image/jpeg"
}
