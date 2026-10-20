package com.mp3tag.mobile.data.tag

import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentFieldKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Pure Kotlin/Java reader and writer for **APE** (Monkey's Audio `.ape`) and
 * **WavPack** (`.wv`) files, using **APEv2** tags (`APETAGEX`).
 *
 * Both formats store metadata and embedded cover art (`Cover Art (front)` binary item:
 * null-terminated filename + raw JPEG/PNG bytes) in an APEv2 tag at the end of the file
 * (optionally followed by an legacy 128-byte ID3v1 trailer, which is preserved).
 */
internal object ApeTagHandler {

    private const val APE_PREAMBLE = "APETAGEX"
    private const val APE_VERSION_2000 = 2000
    private const val APE_HEADER_SIZE = 32

    private const val FLAG_CONTAINS_HEADER = 1 shl 31
    private const val FLAG_IS_HEADER = 1 shl 29
    private const val ITEM_TYPE_MASK = 0x06
    private const val ITEM_TYPE_UTF8 = 0x00
    private const val ITEM_TYPE_BINARY = 0x02

    private val KEY_TO_APE: Map<FieldKey, String> = mapOf(
        FieldKey.TITLE to "Title",
        FieldKey.ARTIST to "Artist",
        FieldKey.ALBUM_ARTIST to "Album Artist",
        FieldKey.ALBUM to "Album",
        FieldKey.YEAR to "Year",
        FieldKey.TRACK to "Track",
        FieldKey.DISC_NO to "Disc",
        FieldKey.GENRE to "Genre",
        FieldKey.COMPOSER to "Composer",
        FieldKey.COMMENT to "Comment",
        FieldKey.SUBTITLE to "Subtitle",
        FieldKey.DISC_SUBTITLE to "DiscSubtitle",
        FieldKey.BPM to "BPM",
        FieldKey.KEY to "Key",
        FieldKey.LANGUAGE to "Language",
        FieldKey.MOOD to "Mood",
        FieldKey.GROUPING to "Grouping",
        FieldKey.LYRICS to "Lyrics",
        FieldKey.LYRICIST to "Lyricist",
        FieldKey.TITLE_SORT to "TitleSort",
        FieldKey.ARTIST_SORT to "ArtistSort",
        FieldKey.ALBUM_SORT to "AlbumSort",
        FieldKey.ALBUM_ARTIST_SORT to "AlbumArtistSort",
        FieldKey.COMPOSER_SORT to "ComposerSort",
        FieldKey.CONDUCTOR to "Conductor",
        FieldKey.ARRANGER to "Arranger",
        FieldKey.ENGINEER to "Engineer",
        FieldKey.PRODUCER to "Producer",
        FieldKey.REMIXER to "MixArtist",
        FieldKey.DJMIXER to "DJMixer",
        FieldKey.MIXER to "Mixer",
        FieldKey.PERFORMER to "Performer",
        FieldKey.ORCHESTRA to "Orchestra",
        FieldKey.ENSEMBLE to "Ensemble",
        FieldKey.INSTRUMENT to "Instrument",
        FieldKey.ISRC to "ISRC",
        FieldKey.ISWC to "ISWC",
        FieldKey.BARCODE to "Barcode",
        FieldKey.CATALOG_NO to "CatalogNumber",
        FieldKey.RECORD_LABEL to "Label",
        FieldKey.COUNTRY to "Country",
        FieldKey.MEDIA to "Media",
        FieldKey.ENCODER to "EncodedBy",
        FieldKey.COPYRIGHT to "Copyright",
        FieldKey.ORIGINAL_ARTIST to "Original Artist",
        FieldKey.ORIGINAL_ALBUM to "Original Album",
        FieldKey.ORIGINAL_YEAR to "Original Year",
        FieldKey.MOVEMENT to "Movement",
        FieldKey.WORK to "Work",
        FieldKey.IS_COMPILATION to "Compilation",
        FieldKey.RATING to "Rating",
        FieldKey.MUSICBRAINZ_TRACK_ID to "MUSICBRAINZ_TRACKID",
        FieldKey.MUSICBRAINZ_RELEASEID to "MUSICBRAINZ_ALBUMID",
        FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID to "MUSICBRAINZ_RELEASEGROUPID",
        FieldKey.MUSICBRAINZ_ARTISTID to "MUSICBRAINZ_ARTISTID",
        FieldKey.MUSICBRAINZ_RELEASEARTISTID to "MUSICBRAINZ_ALBUMARTISTID",
        FieldKey.MUSICBRAINZ_WORK_ID to "MUSICBRAINZ_WORKID",
        FieldKey.ACOUSTID_ID to "ACOUSTID_ID",
        FieldKey.ACOUSTID_FINGERPRINT to "ACOUSTID_FINGERPRINT",
    )

    private val APE_TO_KEY: Map<String, FieldKey> = buildMap {
        KEY_TO_APE.forEach { (fk, apeName) ->
            put(apeName.uppercase(), fk)
        }
        put("ALBUMARTIST", FieldKey.ALBUM_ARTIST)
        put("DATE", FieldKey.YEAR)
        put("TRACKNUMBER", FieldKey.TRACK)
        put("TRACKTOTAL", FieldKey.TRACK_TOTAL)
        put("TOTALTRACKS", FieldKey.TRACK_TOTAL)
        put("DISCNUMBER", FieldKey.DISC_NO)
        put("DISCTOTAL", FieldKey.DISC_TOTAL)
        put("TOTALDISCS", FieldKey.DISC_TOTAL)
        put("ORGANIZATION", FieldKey.RECORD_LABEL)
        put("PUBLISHER", FieldKey.RECORD_LABEL)
        // Fallback mappings from standard Vorbis keys as well
        FieldKey.entries.forEach { fk ->
            runCatching {
                val vKey = VorbisCommentFieldKey.valueOf(fk.name)
                putIfAbsent(vKey.fieldName.uppercase(), fk)
            }
        }
    }

    private data class ApeItem(
        val key: String,
        val flags: Int,
        val valueBytes: ByteArray,
    ) {
        val isBinary: Boolean
            get() = (flags and ITEM_TYPE_MASK) == ITEM_TYPE_BINARY
    }

    private data class ParsedApeLocation(
        val tagStartOffset: Long,
        val tagEndOffset: Long,
        val id3v1Trailer: ByteArray?,
        val items: List<ApeItem>,
    )

    private data class AudioTechInfo(
        val durationSeconds: Int?,
        val sampleRateHz: Int?,
        val bitrateKbps: Int?,
    )

    fun read(file: File, format: AudioFormat): TagSnapshot {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            val tech = when (format) {
                AudioFormat.WAVPACK -> parseWavPackHeader(raf, fileLen)
                AudioFormat.APE -> parseMonkeysAudioHeader(raf, fileLen)
                else -> AudioTechInfo(null, null, null)
            }
            val parsed = locateAndParseApeTag(raf, fileLen)

            val rawFields = LinkedHashMap<FieldKey, String>()
            var cover: CoverImage? = null

            for (item in parsed?.items.orEmpty()) {
                val upperKey = item.key.uppercase()
                if (item.isBinary) {
                    if (upperKey.startsWith("COVER ART") && cover == null) {
                        cover = decodeApeCoverBinary(item.valueBytes)
                    }
                } else {
                    val text = item.valueBytes.toString(Charsets.UTF_8)
                        .replace('\u0000', ';')
                        .trim()
                    if (text.isNotEmpty()) {
                        val fk = APE_TO_KEY[upperKey]
                        if (fk != null && fk != FieldKey.COVER_ART) {
                            rawFields[fk] = text
                        }
                    }
                }
            }

            val fields = TagEngine.normalizeSplitFieldsInternal(rawFields)
            val tagDesc = if (parsed != null) "APEv2" else "${format.name} (untagged)"

            return TagSnapshot(
                path = file.absolutePath,
                format = format,
                durationSeconds = tech.durationSeconds,
                bitrateKbps = tech.bitrateKbps,
                sampleRateHz = tech.sampleRateHz,
                tagDescription = tagDesc,
                fields = fields,
                cover = cover,
                fileSizeBytes = fileLen,
            )
        }
    }

    fun write(file: File, request: WriteRequest) {
        RandomAccessFile(file, "rw").use { raf ->
            val fileLen = raf.length()
            val parsed = locateAndParseApeTag(raf, fileLen)

            // Index existing items by uppercase key so unknown/custom APE items survive untouched
            val itemMap = LinkedHashMap<String, ApeItem>()
            for (item in parsed?.items.orEmpty()) {
                itemMap[item.key.uppercase()] = item
            }

            // Merge Track + TrackTotal and Disc + DiscTotal the way MP3Tag writes APEv2 ("track/total")
            val requested = request.fieldValues.toMutableMap()

            fun combineSlashPair(primaryKey: FieldKey, totalKey: FieldKey, defaultApeName: String) {
                val primaryTouched = requested.containsKey(primaryKey)
                val totalTouched = requested.containsKey(totalKey)
                if (!primaryTouched && !totalTouched) return

                val existingRaw = itemMap[defaultApeName.uppercase()]?.takeIf { !it.isBinary }
                    ?.valueBytes?.toString(Charsets.UTF_8).orEmpty()
                val existingParts = existingRaw.split('/', limit = 2)
                val curPrimary = existingParts.getOrNull(0)?.trim().orEmpty()
                val curTotal = existingParts.getOrNull(1)?.trim().orEmpty()

                val newPrimary = if (primaryTouched) requested.remove(primaryKey)?.trim().orEmpty() else curPrimary
                val newTotal = if (totalTouched) requested.remove(totalKey)?.trim().orEmpty() else curTotal

                // Remove separate TrackTotal / DiscTotal keys if they existed
                if (primaryKey == FieldKey.TRACK) {
                    itemMap.remove("TRACKTOTAL")
                    itemMap.remove("TOTALTRACKS")
                    itemMap.remove("TRACKNUMBER")
                } else if (primaryKey == FieldKey.DISC_NO) {
                    itemMap.remove("DISCTOTAL")
                    itemMap.remove("TOTALDISCS")
                    itemMap.remove("DISCNUMBER")
                }

                val combined = when {
                    newPrimary.isNotEmpty() && newTotal.isNotEmpty() -> "$newPrimary/$newTotal"
                    newPrimary.isNotEmpty() -> newPrimary
                    newTotal.isNotEmpty() -> "/$newTotal"
                    else -> ""
                }
                if (combined.isEmpty()) {
                    itemMap.remove(defaultApeName.uppercase())
                } else {
                    itemMap[defaultApeName.uppercase()] = ApeItem(
                        key = defaultApeName,
                        flags = ITEM_TYPE_UTF8,
                        valueBytes = combined.toByteArray(Charsets.UTF_8),
                    )
                }
            }

            combineSlashPair(FieldKey.TRACK, FieldKey.TRACK_TOTAL, "Track")
            combineSlashPair(FieldKey.DISC_NO, FieldKey.DISC_TOTAL, "Disc")

            for ((fk, value) in requested) {
                val apeKey = KEY_TO_APE[fk]
                    ?: runCatching { VorbisCommentFieldKey.valueOf(fk.name).fieldName }.getOrNull()
                    ?: fk.name
                val upper = apeKey.uppercase()
                if (value.isNullOrBlank()) {
                    itemMap.remove(upper)
                } else {
                    val existingKeyName = itemMap[upper]?.key ?: apeKey
                    itemMap[upper] = ApeItem(
                        key = existingKeyName,
                        flags = ITEM_TYPE_UTF8,
                        valueBytes = value.trim().toByteArray(Charsets.UTF_8),
                    )
                }
            }

            when (request.coverAction) {
                CoverAction.NONE -> Unit
                CoverAction.REMOVE -> {
                    val coverKeys = itemMap.keys.filter { it.startsWith("COVER ART") }
                    coverKeys.forEach { itemMap.remove(it) }
                }
                CoverAction.REPLACE -> request.newCover?.let { cover ->
                    val coverKeys = itemMap.keys.filter { it.startsWith("COVER ART") }
                    coverKeys.forEach { itemMap.remove(it) }
                    val ext = when (cover.mimeType.lowercase()) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/gif" -> "gif"
                        else -> "jpg"
                    }
                    val filenameBytes = "Cover Art (front).$ext".toByteArray(Charsets.US_ASCII)
                    val payload = ByteArray(filenameBytes.size + 1 + cover.bytes.size)
                    System.arraycopy(filenameBytes, 0, payload, 0, filenameBytes.size)
                    payload[filenameBytes.size] = 0
                    System.arraycopy(cover.bytes, 0, payload, filenameBytes.size + 1, cover.bytes.size)

                    itemMap["COVER ART (FRONT)"] = ApeItem(
                        key = "Cover Art (front)",
                        flags = ITEM_TYPE_BINARY,
                        valueBytes = payload,
                    )
                }
            }

            val truncateOffset = parsed?.tagStartOffset ?: fileLen
            val serializedTag = serializeApeTag(itemMap.values.toList())

            raf.seek(truncateOffset)
            if (serializedTag.isNotEmpty()) {
                raf.write(serializedTag)
            }
            parsed?.id3v1Trailer?.let { raf.write(it) }
            raf.setLength(raf.filePointer)
            raf.fd.sync()
        }
    }

    // ------------------------------------------------------------------
    // APEv2 binary layout
    // ------------------------------------------------------------------

    private fun locateAndParseApeTag(raf: RandomAccessFile, fileLen: Long): ParsedApeLocation? {
        if (fileLen < APE_HEADER_SIZE) return null

        // Check for optional 128-byte ID3v1 tag at the very end of the file
        var id3v1Bytes: ByteArray? = null
        var searchEnd = fileLen
        if (fileLen >= 128L) {
            raf.seek(fileLen - 128L)
            val tagMagic = ByteArray(3)
            raf.readFully(tagMagic)
            if (tagMagic[0] == 'T'.code.toByte() && tagMagic[1] == 'A'.code.toByte() && tagMagic[2] == 'G'.code.toByte()) {
                val fullId3v1 = ByteArray(128)
                raf.seek(fileLen - 128L)
                raf.readFully(fullId3v1)
                id3v1Bytes = fullId3v1
                searchEnd = fileLen - 128L
            }
        }

        if (searchEnd < APE_HEADER_SIZE) return null
        raf.seek(searchEnd - APE_HEADER_SIZE)
        val footerBytes = ByteArray(APE_HEADER_SIZE)
        raf.readFully(footerBytes)
        val preamble = String(footerBytes, 0, 8, Charsets.US_ASCII)
        if (preamble != APE_PREAMBLE) {
            return if (id3v1Bytes != null) {
                ParsedApeLocation(
                    tagStartOffset = searchEnd,
                    tagEndOffset = searchEnd,
                    id3v1Trailer = id3v1Bytes,
                    items = emptyList(),
                )
            } else {
                null
            }
        }

        val footerBuf = ByteBuffer.wrap(footerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val tagSize = footerBuf.getInt(12).toLong() and 0xFFFFFFFFL // items + 32-byte footer (excludes 32-byte header)
        val itemCount = footerBuf.getInt(16)
        val flags = footerBuf.getInt(20)

        if (tagSize < APE_HEADER_SIZE || tagSize > searchEnd) return null
        val hasHeader = (flags and FLAG_CONTAINS_HEADER) != 0
        val itemsStart = searchEnd - tagSize
        val tagStart = if (hasHeader && itemsStart >= APE_HEADER_SIZE) {
            // Verify header preamble
            raf.seek(itemsStart - APE_HEADER_SIZE)
            val hdrMagic = ByteArray(8)
            raf.readFully(hdrMagic)
            if (String(hdrMagic, Charsets.US_ASCII) == APE_PREAMBLE) {
                itemsStart - APE_HEADER_SIZE
            } else {
                itemsStart
            }
        } else {
            itemsStart
        }

        val itemsBytesLen = (tagSize - APE_HEADER_SIZE).toInt()
        val items = ArrayList<ApeItem>(itemCount.coerceIn(0, 512))
        if (itemsBytesLen > 0 && itemsBytesLen <= 64 * 1024 * 1024) {
            val rawItems = ByteArray(itemsBytesLen)
            raf.seek(itemsStart)
            raf.readFully(rawItems)
            var pos = 0
            var parsedCount = 0
            while (pos + 8 <= rawItems.size && parsedCount < itemCount) {
                val valSize = ByteBuffer.wrap(rawItems, pos, 8).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
                val itemFlags = ByteBuffer.wrap(rawItems, pos, 8).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
                pos += 8
                val keyStart = pos
                while (pos < rawItems.size && rawItems[pos] != 0.toByte()) {
                    pos++
                }
                if (pos >= rawItems.size) break
                val key = String(rawItems, keyStart, pos - keyStart, Charsets.US_ASCII)
                pos += 1 // skip null terminator
                if (valSize < 0 || pos + valSize > rawItems.size) break
                val valueBytes = rawItems.copyOfRange(pos, pos + valSize)
                pos += valSize
                if (key.isNotBlank()) {
                    items.add(ApeItem(key, itemFlags, valueBytes))
                }
                parsedCount++
            }
        }

        return ParsedApeLocation(
            tagStartOffset = tagStart,
            tagEndOffset = searchEnd,
            id3v1Trailer = id3v1Bytes,
            items = items,
        )
    }

    private fun serializeApeTag(items: List<ApeItem>): ByteArray {
        if (items.isEmpty()) return ByteArray(0)
        val itemsOut = ByteArrayOutputStream()
        for (item in items) {
            val keyBytes = item.key.toByteArray(Charsets.US_ASCII)
            val hdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            hdr.putInt(item.valueBytes.size)
            hdr.putInt(item.flags)
            itemsOut.write(hdr.array())
            itemsOut.write(keyBytes)
            itemsOut.write(0)
            itemsOut.write(item.valueBytes)
        }
        val itemsData = itemsOut.toByteArray()
        val tagSizeWithFooter = itemsData.size + APE_HEADER_SIZE

        val header = buildApeHeaderOrFooter(
            tagSize = tagSizeWithFooter,
            itemCount = items.size,
            isHeader = true,
        )
        val footer = buildApeHeaderOrFooter(
            tagSize = tagSizeWithFooter,
            itemCount = items.size,
            isHeader = false,
        )

        val total = ByteArray(APE_HEADER_SIZE + itemsData.size + APE_HEADER_SIZE)
        System.arraycopy(header, 0, total, 0, APE_HEADER_SIZE)
        System.arraycopy(itemsData, 0, total, APE_HEADER_SIZE, itemsData.size)
        System.arraycopy(footer, 0, total, APE_HEADER_SIZE + itemsData.size, APE_HEADER_SIZE)
        return total
    }

    private fun buildApeHeaderOrFooter(tagSize: Int, itemCount: Int, isHeader: Boolean): ByteArray {
        val buf = ByteBuffer.allocate(APE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(APE_PREAMBLE.toByteArray(Charsets.US_ASCII))
        buf.putInt(APE_VERSION_2000)
        buf.putInt(tagSize)
        buf.putInt(itemCount)
        var flags = FLAG_CONTAINS_HEADER
        if (isHeader) flags = flags or FLAG_IS_HEADER
        buf.putInt(flags)
        buf.putLong(0L) // 8 reserved zero bytes
        return buf.array()
    }

    private fun decodeApeCoverBinary(bytes: ByteArray): CoverImage? {
        // APEv2 binary cover art begins with a null-terminated description/filename followed by image bytes
        var nullIdx = -1
        val maxScan = minOf(bytes.size, 256)
        for (i in 0 until maxScan) {
            if (bytes[i] == 0.toByte()) {
                nullIdx = i
                break
            }
        }
        val imgBytes = if (nullIdx in 0 until (bytes.size - 4)) {
            bytes.copyOfRange(nullIdx + 1, bytes.size)
        } else {
            bytes
        }
        if (imgBytes.size < 8) return null
        return CoverImage(imgBytes, sniffImageMime(imgBytes))
    }

    // ------------------------------------------------------------------
    // Technical Audio Header Parsers (WavPack & Monkey's Audio)
    // ------------------------------------------------------------------

    private val WAVPACK_SAMPLE_RATES = intArrayOf(
        6000, 8000, 9600, 11025, 12000, 16000, 22050, 24000,
        32000, 44100, 48000, 64000, 88200, 96000, 192000, 0
    )

    private fun parseWavPackHeader(raf: RandomAccessFile, fileLen: Long): AudioTechInfo = runCatching {
        if (fileLen < 32L) return AudioTechInfo(null, null, null)
        raf.seek(0L)
        val hdr = ByteArray(32)
        raf.readFully(hdr)
        if (String(hdr, 0, 4, Charsets.US_ASCII) != "wvpk") {
            return AudioTechInfo(null, null, null)
        }
        val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
        val totalSamples = buf.getInt(12).toLong() and 0xFFFFFFFFL
        val flags = buf.getInt(24)
        val srIdx = (flags ushr 23) and 0x0F
        val sampleRate = WAVPACK_SAMPLE_RATES[srIdx].takeIf { it > 0 }
        val duration = if (sampleRate != null && totalSamples > 0L && totalSamples != 0xFFFFFFFFL) {
            (totalSamples.toDouble() / sampleRate.toDouble()).roundToInt()
        } else {
            null
        }
        val bitrate = if (duration != null && duration > 0) {
            ((fileLen * 8L) / (duration.toLong() * 1000L)).toInt()
        } else {
            null
        }
        AudioTechInfo(durationSeconds = duration, sampleRateHz = sampleRate, bitrateKbps = bitrate)
    }.getOrDefault(AudioTechInfo(null, null, null))

    private fun parseMonkeysAudioHeader(raf: RandomAccessFile, fileLen: Long): AudioTechInfo = runCatching {
        if (fileLen < 76L) return AudioTechInfo(null, null, null)
        raf.seek(0L)
        val hdr = ByteArray(76)
        raf.readFully(hdr)
        if (String(hdr, 0, 4, Charsets.US_ASCII) != "MAC ") {
            return AudioTechInfo(null, null, null)
        }
        val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.getShort(4).toInt() and 0xFFFF
        val sampleRate: Int?
        val totalSamples: Long
        if (version >= 3980) {
            val descriptorBytes = buf.getInt(8).coerceIn(52, 256)
            if (fileLen < descriptorBytes + 24) return AudioTechInfo(null, null, null)
            raf.seek(descriptorBytes.toLong())
            val apeHdr = ByteArray(24)
            raf.readFully(apeHdr)
            val apeBuf = ByteBuffer.wrap(apeHdr).order(ByteOrder.LITTLE_ENDIAN)
            val blocksPerFrame = apeBuf.getInt(4).toLong() and 0xFFFFFFFFL
            val finalFrameBlocks = apeBuf.getInt(8).toLong() and 0xFFFFFFFFL
            val totalFrames = apeBuf.getInt(12).toLong() and 0xFFFFFFFFL
            sampleRate = apeBuf.getInt(20).takeIf { it > 0 }
            totalSamples = if (totalFrames > 0L) {
                (totalFrames - 1L) * blocksPerFrame + finalFrameBlocks
            } else {
                0L
            }
        } else {
            sampleRate = buf.getInt(24).takeIf { it > 0 }
            totalSamples = 0L
        }
        val duration = if (sampleRate != null && sampleRate > 0 && totalSamples > 0L) {
            (totalSamples.toDouble() / sampleRate.toDouble()).roundToInt()
        } else {
            null
        }
        val bitrate = if (duration != null && duration > 0) {
            ((fileLen * 8L) / (duration.toLong() * 1000L)).toInt()
        } else {
            null
        }
        AudioTechInfo(durationSeconds = duration, sampleRateHz = sampleRate, bitrateKbps = bitrate)
    }.getOrDefault(AudioTechInfo(null, null, null))
}
