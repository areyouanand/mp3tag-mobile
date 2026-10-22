package com.mp3tag.mobile.data.tag

import org.jaudiotagger.audio.ogg.util.OggCRCFactory
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentCreator
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentReader
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Pure Kotlin/Java reader and writer for **Ogg Opus** (`.opus`, RFC 7845).
 *
 * Opus streams live inside an Ogg bitstream (`OggS` pages):
 * - Packet 0 (Page 0, BOS): `"OpusHead"` (19+ bytes: version, channels, pre-skip, input sample rate, gain, mapping)
 * - Packet 1 (Page 1..N): `"OpusTags"` (`8` byte magic + standard VorbisComment block without a framing bit)
 * - Subsequent pages: compressed Opus audio packets (always decoded at 48,000 Hz granule rate).
 *
 * Reuses JAudioTagger's [VorbisCommentReader], [VorbisCommentTag], [VorbisCommentCreator], and
 * [OggCRCFactory] for full field + `METADATA_BLOCK_PICTURE` cover art support.
 */
internal object OpusTagHandler {

    private val OGG_MAGIC = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
    private val OPUS_HEAD_MAGIC = "OpusHead".toByteArray(Charsets.US_ASCII)
    private val OPUS_TAGS_MAGIC = "OpusTags".toByteArray(Charsets.US_ASCII)

    private data class RawOggPage(
        val fileOffset: Long,
        val headerType: Int,
        val granulePosition: Long,
        val serialNumber: Int,
        val pageSequence: Int,
        val segmentTable: ByteArray,
        val body: ByteArray,
    ) {
        val totalByteLength: Int
            get() = 27 + segmentTable.size + body.size
    }

    fun readCustomFields(file: File, keys: Set<String>): Map<String, String> = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            if (fileLen < 28L) return emptyMap()
            val firstPage = readNextPage(raf) ?: return emptyMap()
            if (firstPage.body.size < 19 || !firstPage.body.copyOfRange(0, 8).contentEquals(OPUS_HEAD_MAGIC)) {
                return emptyMap()
            }
            val tagsPacket = ByteArrayOutputStream()
            var tagsComplete = false
            while (!tagsComplete && raf.filePointer < fileLen) {
                val page = readNextPage(raf) ?: break
                var bodyPos = 0
                for (seg in page.segmentTable) {
                    val segLen = seg.toInt() and 0xFF
                    if (bodyPos + segLen <= page.body.size) {
                        tagsPacket.write(page.body, bodyPos, segLen)
                    }
                    bodyPos += segLen
                    if (segLen < 255) {
                        tagsComplete = true
                        break
                    }
                }
            }
            val tagsBytes = tagsPacket.toByteArray()
            val vorbisTag: VorbisCommentTag = if (
                tagsBytes.size >= 16 &&
                tagsBytes.copyOfRange(0, 8).contentEquals(OPUS_TAGS_MAGIC)
            ) {
                val commentBytes = tagsBytes.copyOfRange(8, tagsBytes.size)
                runCatching { VorbisCommentReader().read(commentBytes, false, null) }.getOrNull() ?: return emptyMap()
            } else {
                return emptyMap()
            }
            val out = LinkedHashMap<String, String>()
            for (key in keys) {
                val v = runCatching { vorbisTag.getFirst(key) }.getOrNull()?.trim()
                    ?: runCatching { vorbisTag.getFirst(key.uppercase()) }.getOrNull()?.trim()
                    ?: runCatching { vorbisTag.getFirst(key.lowercase()) }.getOrNull()?.trim()
                if (!v.isNullOrEmpty()) {
                    out[key.uppercase()] = v
                }
            }
            out
        }
    }.getOrDefault(emptyMap())

    fun read(file: File): TagSnapshot {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            require(fileLen >= 28L) { "File too small to be a valid Opus file" }

            val firstPage = readNextPage(raf)
                ?: error("Not a valid Ogg Opus file (missing first OggS page)")
            require(
                firstPage.body.size >= 19 &&
                    firstPage.body.copyOfRange(0, 8).contentEquals(OPUS_HEAD_MAGIC)
            ) { "Not a valid Opus file (missing OpusHead header)" }

            val headBuf = ByteBuffer.wrap(firstPage.body).order(ByteOrder.LITTLE_ENDIAN)
            val preSkip = headBuf.getShort(10).toInt() and 0xFFFF
            val inputSampleRate = headBuf.getInt(12)
            val sampleRateHz = if (inputSampleRate in 8000..192000) inputSampleRate else 48000

            // Assemble the second packet ("OpusTags"), which starts on page 1 and may span multiple pages
            val tagsPacket = ByteArrayOutputStream()
            var tagsComplete = false
            while (!tagsComplete && raf.filePointer < fileLen) {
                val page = readNextPage(raf) ?: break
                var bodyPos = 0
                for (seg in page.segmentTable) {
                    val segLen = seg.toInt() and 0xFF
                    if (bodyPos + segLen <= page.body.size) {
                        tagsPacket.write(page.body, bodyPos, segLen)
                    }
                    bodyPos += segLen
                    if (segLen < 255) {
                        tagsComplete = true
                        break
                    }
                }
            }

            val tagsBytes = tagsPacket.toByteArray()
            val vorbisTag: VorbisCommentTag? = if (
                tagsBytes.size >= 16 &&
                tagsBytes.copyOfRange(0, 8).contentEquals(OPUS_TAGS_MAGIC)
            ) {
                val commentBytes = tagsBytes.copyOfRange(8, tagsBytes.size)
                runCatching { VorbisCommentReader().read(commentBytes, false, null) }.getOrNull()
            } else {
                null
            }

            val durationSeconds = readDurationSeconds(raf, fileLen, preSkip)
            val bitrateKbps = if (durationSeconds != null && durationSeconds > 0) {
                ((fileLen * 8L) / (durationSeconds.toLong() * 1000L)).toInt()
            } else {
                null
            }

            val allFields: Map<FieldKey, String> = if (vorbisTag != null) {
                FieldKey.entries
                    .filter { it != FieldKey.COVER_ART }
                    .mapNotNull { key ->
                        runCatching { vorbisTag.getFirst(key) }.getOrNull()
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { key to it }
                    }
                    .toMap()
            } else {
                emptyMap()
            }

            val cover: CoverImage? = runCatching {
                vorbisTag?.firstArtwork?.let { art ->
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
                format = AudioFormat.OPUS,
                durationSeconds = durationSeconds,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                tagDescription = "OpusTags (Vorbis)",
                fields = TagEngine.normalizeSplitFieldsInternal(allFields),
                cover = cover,
                fileSizeBytes = fileLen,
            )
        }
    }

    fun write(file: File, request: WriteRequest) {
        val firstPage: RawOggPage
        val audioDataStartOffset: Long
        val existingVorbisTag: VorbisCommentTag

        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            firstPage = readNextPage(raf)
                ?: error("Not a valid Ogg Opus file")
            require(
                firstPage.body.size >= 19 &&
                    firstPage.body.copyOfRange(0, 8).contentEquals(OPUS_HEAD_MAGIC)
            ) { "Not a valid Opus file (missing OpusHead)" }

            val tagsPacket = ByteArrayOutputStream()
            var tagsComplete = false
            var trailingAfterTags = ByteArray(0)

            while (!tagsComplete && raf.filePointer < fileLen) {
                val page = readNextPage(raf)
                    ?: error("Unexpected EOF while reading OpusTags pages")
                var bodyPos = 0
                for (i in page.segmentTable.indices) {
                    val segLen = page.segmentTable[i].toInt() and 0xFF
                    if (bodyPos + segLen <= page.body.size) {
                        tagsPacket.write(page.body, bodyPos, segLen)
                    }
                    bodyPos += segLen
                    if (segLen < 255) {
                        tagsComplete = true
                        // Any remaining segments in this page belong to subsequent packets
                        if (bodyPos < page.body.size) {
                            trailingAfterTags = page.body.copyOfRange(bodyPos, page.body.size)
                        }
                        break
                    }
                }
            }
            require(tagsComplete) { "Incomplete OpusTags packet in file" }
            audioDataStartOffset = raf.filePointer

            val tagsBytes = tagsPacket.toByteArray()
            existingVorbisTag = if (
                tagsBytes.size >= 16 &&
                tagsBytes.copyOfRange(0, 8).contentEquals(OPUS_TAGS_MAGIC)
            ) {
                val commentBytes = tagsBytes.copyOfRange(8, tagsBytes.size)
                runCatching { VorbisCommentReader().read(commentBytes, false, null) }.getOrNull()
                    ?: VorbisCommentTag.createNewTag()
            } else {
                VorbisCommentTag.createNewTag()
            }
            if (trailingAfterTags.isNotEmpty()) {
                error("Opus file shares an Ogg page between OpusTags and audio packets; cannot safely rewrite")
            }
        }

        // Apply edits to VorbisCommentTag
        request.fieldValues.forEach { (key, value) ->
            if (value.isNullOrBlank()) {
                runCatching { existingVorbisTag.deleteField(key) }
            } else {
                existingVorbisTag.setField(key, value)
            }
        }
        request.customFields.forEach { (key, value) ->
            if (value.isNullOrBlank()) runCatching { existingVorbisTag.deleteField(key) }
            else runCatching { existingVorbisTag.setField(key, value) }
        }

        when (request.coverAction) {
            CoverAction.NONE -> Unit
            CoverAction.REMOVE -> runCatching { existingVorbisTag.deleteArtworkField() }
            CoverAction.REPLACE -> request.newCover?.let { cover ->
                runCatching { existingVorbisTag.deleteArtworkField() }
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = cover.bytes
                artwork.mimeType = cover.mimeType
                existingVorbisTag.setField(artwork)
            }
        }

        // Serialize "OpusTags" + VorbisComment bytes (no framing bit in Opus!)
        val vorbisBuffer = VorbisCommentCreator().convertMetadata(existingVorbisTag, false)
        val vorbisBytes = ByteArray(vorbisBuffer.remaining())
        vorbisBuffer.get(vorbisBytes)

        val opusTagsPacket = ByteArray(OPUS_TAGS_MAGIC.size + vorbisBytes.size)
        System.arraycopy(OPUS_TAGS_MAGIC, 0, opusTagsPacket, 0, OPUS_TAGS_MAGIC.size)
        System.arraycopy(vorbisBytes, 0, opusTagsPacket, OPUS_TAGS_MAGIC.size, vorbisBytes.size)

        val newTagPages = packetizeOpusTags(
            packet = opusTagsPacket,
            serialNumber = firstPage.serialNumber,
            startingSequence = firstPage.pageSequence + 1,
        )

        val tempFile = File(file.parentFile, ".${file.name}.mp3tag_tmp")
        try {
            FileOutputStream(tempFile).use { out ->
                // 1. Write Page 0 (OpusHead)
                out.write(serializeOggPage(firstPage))

                // 2. Write Page 1..K (OpusTags)
                var nextSeq = firstPage.pageSequence + 1
                for (tagPage in newTagPages) {
                    out.write(serializeOggPage(tagPage))
                    nextSeq = tagPage.pageSequence + 1
                }

                // 3. Copy remaining audio pages, renumbering pageSequenceNumber and recomputing CRC if needed
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(audioDataStartOffset)
                    // Peek at the first audio page to check if sequence numbers shifted
                    val firstAudioPagePos = raf.filePointer
                    val firstAudioPage = readNextPage(raf)
                    if (firstAudioPage != null) {
                        val seqDelta = nextSeq - firstAudioPage.pageSequence
                        if (seqDelta == 0) {
                            // Fast path: page count for OpusTags did not change! Stream-copy raw audio bytes directly.
                            FileInputStream(file).use { fis ->
                                fis.channel.position(firstAudioPagePos)
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = fis.read(buf)
                                    if (r <= 0) break
                                    out.write(buf, 0, r)
                                }
                            }
                        } else {
                            // Page count changed (e.g. cover art added/removed); update page sequence + CRC on audio pages
                            out.write(serializeOggPage(firstAudioPage.copy(pageSequence = firstAudioPage.pageSequence + seqDelta)))
                            while (raf.filePointer < raf.length()) {
                                val page = readNextPage(raf) ?: break
                                out.write(serializeOggPage(page.copy(pageSequence = page.pageSequence + seqDelta)))
                            }
                        }
                    }
                }
                out.fd.sync()
            }

            if (!tempFile.renameTo(file)) {
                // Fallback copy if renameTo fails on filesystem
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output, 64 * 1024)
                        output.fd.sync()
                    }
                }
                tempFile.delete()
            }
        } finally {
            if (tempFile.exists()) {
                runCatching { tempFile.delete() }
            }
        }
    }

    private fun packetizeOpusTags(
        packet: ByteArray,
        serialNumber: Int,
        startingSequence: Int,
    ): List<RawOggPage> {
        // Build Ogg lacing values (255, 255, ..., remainder < 255)
        val segments = ArrayList<Int>(packet.size / 255 + 2)
        var remaining = packet.size
        while (remaining >= 255) {
            segments.add(255)
            remaining -= 255
        }
        segments.add(remaining) // final lacing value (< 255, including 0 if exact multiple of 255)

        val pages = ArrayList<RawOggPage>()
        var segIdx = 0
        var dataOffset = 0
        var seq = startingSequence

        while (segIdx < segments.size) {
            val segCount = minOf(255, segments.size - segIdx)
            val segTable = ByteArray(segCount)
            var pageBodyLen = 0
            for (i in 0 until segCount) {
                val len = segments[segIdx + i]
                segTable[i] = len.toByte()
                pageBodyLen += len
            }
            val body = packet.copyOfRange(dataOffset, dataOffset + pageBodyLen)
            val isContinuation = segIdx > 0
            val isLastPageOfPacket = (segIdx + segCount) == segments.size

            val headerType = if (isContinuation) 0x01 else 0x00
            val granulePos = if (isLastPageOfPacket) 0L else -1L

            pages.add(
                RawOggPage(
                    fileOffset = 0L,
                    headerType = headerType,
                    granulePosition = granulePos,
                    serialNumber = serialNumber,
                    pageSequence = seq,
                    segmentTable = segTable,
                    body = body,
                )
            )
            segIdx += segCount
            dataOffset += pageBodyLen
            seq += 1
        }
        return pages
    }

    private fun serializeOggPage(page: RawOggPage): ByteArray {
        val total = ByteArray(27 + page.segmentTable.size + page.body.size)
        val hdr = ByteBuffer.wrap(total, 0, 27).order(ByteOrder.LITTLE_ENDIAN)
        hdr.put(OGG_MAGIC)
        hdr.put(0.toByte()) // version = 0
        hdr.put(page.headerType.toByte())
        hdr.putLong(page.granulePosition)
        hdr.putInt(page.serialNumber)
        hdr.putInt(page.pageSequence)
        hdr.putInt(0) // checksum placeholder at offset 22..25
        hdr.put(page.segmentTable.size.toByte())

        System.arraycopy(page.segmentTable, 0, total, 27, page.segmentTable.size)
        System.arraycopy(page.body, 0, total, 27 + page.segmentTable.size, page.body.size)

        val crc = OggCRCFactory.computeCRC(total)
        System.arraycopy(crc, 0, total, 22, 4)
        return total
    }

    private fun readNextPage(raf: RandomAccessFile): RawOggPage? {
        val start = raf.filePointer
        if (start + 27L > raf.length()) return null
        val fixed = ByteArray(27)
        raf.readFully(fixed)
        if (!fixed.copyOfRange(0, 4).contentEquals(OGG_MAGIC)) return null

        val buf = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN)
        val headerType = fixed[5].toInt() and 0xFF
        val granule = buf.getLong(6)
        val serial = buf.getInt(14)
        val seq = buf.getInt(18)
        val pageSegments = fixed[26].toInt() and 0xFF

        if (raf.filePointer + pageSegments > raf.length()) return null
        val segTable = ByteArray(pageSegments)
        raf.readFully(segTable)

        var bodyLen = 0
        for (b in segTable) {
            bodyLen += (b.toInt() and 0xFF)
        }
        if (raf.filePointer + bodyLen > raf.length()) return null
        val body = ByteArray(bodyLen)
        raf.readFully(body)

        return RawOggPage(
            fileOffset = start,
            headerType = headerType,
            granulePosition = granule,
            serialNumber = serial,
            pageSequence = seq,
            segmentTable = segTable,
            body = body,
        )
    }

    private fun readDurationSeconds(raf: RandomAccessFile, fileLen: Long, preSkip: Int): Int? = runCatching {
        val tailSize = minOf(fileLen, 65536L).toInt()
        val tail = ByteArray(tailSize)
        raf.seek(fileLen - tailSize)
        raf.readFully(tail)

        for (i in (tail.size - 27) downTo 0) {
            if (tail[i] == 'O'.code.toByte() &&
                tail[i + 1] == 'g'.code.toByte() &&
                tail[i + 2] == 'g'.code.toByte() &&
                tail[i + 3] == 'S'.code.toByte()
            ) {
                val granule = ByteBuffer.wrap(tail, i + 6, 8).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
                if (granule > preSkip) {
                    return ((granule - preSkip).toDouble() / 48000.0).roundToInt()
                }
            }
        }
        null
    }.getOrNull()
}
