package com.mp3tag.mobile.data.tag

import com.mp3tag.mobile.data.prefs.Id3Version
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Pure Kotlin/Java DSDIFF (.dff) reader and writer.
 *
 * DSDIFF structure (big-endian 8-byte chunk sizes):
 *   "FRM8" (4B) + formSize (8B BE) + "DSD " (4B) + [child chunks...]
 *
 * Industry-standard DFF tagging (supported by MP3Tag, foobar2000, JRiver, Symfonium, and
 * iBasso/FiiO DAPs) embeds an `"ID3 "` chunk (4B ID + 8B BE size + ID3v2 payload + optional
 * 1-byte pad if size is odd) inside the top-level `"FRM8"` container (or inside `"PROP"`).
 */
internal object DffTagHandler {

    private data class DffChunkInfo(
        val id: String,
        val headerPos: Long,
        val dataPos: Long,
        val dataSize: Long,
    ) {
        val paddedDataSize: Long
            get() = if (dataSize % 2L != 0L) dataSize + 1L else dataSize
        val nextChunkPos: Long
            get() = dataPos + paddedDataSize
    }

    fun read(file: File): TagSnapshot {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            require(fileLen >= 16L) { "File too small to be a valid DFF file" }

            val frm8 = readChunkId(raf)
            require(frm8 == "FRM8") { "Not a valid DFF file (missing FRM8 header)" }
            val frm8DataSize = raf.readLong() // Big-endian 64-bit
            val formType = readChunkId(raf)
            require(formType == "DSD ") { "Not a valid DFF file (missing 'DSD ' form type)" }

            var sampleRateHz: Int? = null
            var channels = 2
            var soundDataBytes = 0L
            var id3Tag: AbstractID3v2Tag? = null

            val endOfFrm8 = (12L + frm8DataSize).coerceAtMost(fileLen)
            while (raf.filePointer + 12L <= endOfFrm8) {
                val chunk = readChunkHeader(raf) ?: break
                if (chunk.dataPos + chunk.dataSize > fileLen) break

                when (chunk.id) {
                    "PROP" -> {
                        if (chunk.dataSize >= 4L) {
                            val propType = readChunkId(raf)
                            if (propType == "SND ") {
                                val propEnd = chunk.dataPos + chunk.dataSize
                                while (raf.filePointer + 12L <= propEnd) {
                                    val sub = readChunkHeader(raf) ?: break
                                    if (sub.dataPos + sub.dataSize > propEnd) break
                                    when (sub.id) {
                                        "FS  " -> if (sub.dataSize >= 4L) {
                                            sampleRateHz = raf.readInt()
                                        }
                                        "CHNL" -> if (sub.dataSize >= 2L) {
                                            channels = raf.readUnsignedShort()
                                        }
                                        "ID3 ", "id3 " -> if (id3Tag == null && sub.dataSize > 10L && sub.dataSize <= 64 * 1024 * 1024) {
                                            id3Tag = parseId3Chunk(raf, sub.dataSize.toInt(), file.name)
                                        }
                                    }
                                    raf.seek(sub.nextChunkPos.coerceAtMost(propEnd))
                                }
                            }
                        }
                    }

                    "DSD " -> {
                        soundDataBytes = chunk.dataSize
                    }

                    "ID3 ", "id3 " -> {
                        if (chunk.dataSize in 10L..(64L * 1024L * 1024L)) {
                            id3Tag = parseId3Chunk(raf, chunk.dataSize.toInt(), file.name)
                        }
                    }
                }

                raf.seek(chunk.nextChunkPos.coerceAtMost(fileLen))
            }

            // Fallback: check if an ID3v2 tag was appended after FRM8
            if (id3Tag == null && endOfFrm8 + 10L <= fileLen) {
                raf.seek(endOfFrm8)
                val tailMagic = ByteArray(3)
                raf.readFully(tailMagic)
                if (String(tailMagic, Charsets.US_ASCII) == "ID3") {
                    val remaining = (fileLen - endOfFrm8).toInt()
                    if (remaining in 10..(64 * 1024 * 1024)) {
                        raf.seek(endOfFrm8)
                        id3Tag = parseId3Chunk(raf, remaining, file.name)
                    }
                }
            }

            val sr = sampleRateHz
            val ch = channels.coerceAtLeast(1)
            val durationSeconds: Int? = if (sr != null && sr > 0 && soundDataBytes > 0L) {
                val totalSamplesPerChannel = (soundDataBytes * 8L) / ch.toLong()
                (totalSamplesPerChannel.toDouble() / sr.toDouble()).roundToInt()
            } else {
                null
            }
            val bitrateKbps: Int? = if (sr != null && sr > 0) {
                (sr * ch) / 1000
            } else {
                null
            }

            val allFields: Map<FieldKey, String> = if (id3Tag != null) {
                FieldKey.entries
                    .filter { it != FieldKey.COVER_ART }
                    .mapNotNull { key ->
                        runCatching { id3Tag.getFirst(key) }.getOrNull()
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { key to it }
                    }
                    .toMap()
            } else {
                emptyMap()
            }

            val cover: CoverImage? = runCatching {
                id3Tag?.firstArtwork?.let { art ->
                    art.binaryData?.let { bytes ->
                        CoverImage(
                            bytes = bytes,
                            mimeType = art.mimeType?.takeUnless { it.isBlank() } ?: sniffImageMime(bytes),
                        )
                    }
                }
            }.getOrNull()

            val tagDesc = when (id3Tag) {
                is ID3v24Tag -> "DFF (ID3v2.4)"
                is ID3v23Tag -> "DFF (ID3v2.3)"
                is ID3v22Tag -> "DFF (ID3v2.2)"
                null -> "DFF (untagged)"
                else -> "DFF (ID3v2)"
            }

            return TagSnapshot(
                path = file.absolutePath,
                format = AudioFormat.DFF,
                durationSeconds = durationSeconds,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                tagDescription = tagDesc,
                fields = TagEngine.normalizeSplitFieldsInternal(allFields),
                cover = cover,
                fileSizeBytes = fileLen,
            )
        }
    }

    fun write(file: File, request: WriteRequest, options: WriteOptions) {
        writeSafely(file) { temp -> writeInPlace(temp, request, options) }
    }

    private fun writeInPlace(file: File, request: WriteRequest, options: WriteOptions) {
        RandomAccessFile(file, "rw").use { raf ->
            val fileLen = raf.length()
            require(fileLen >= 16L) { "Not a valid DFF file" }

            val frm8 = readChunkId(raf)
            require(frm8 == "FRM8") { "Not a valid DFF file (missing FRM8 header)" }
            val frm8DataSize = raf.readLong()
            val formType = readChunkId(raf)
            require(formType == "DSD ") { "Not a valid DFF file (missing 'DSD ' form type)" }

            val endOfFrm8 = (12L + frm8DataSize).coerceAtMost(fileLen)
            var topLevelId3Chunk: DffChunkInfo? = null
            var existingTag: AbstractID3v2Tag? = null

            while (raf.filePointer + 12L <= endOfFrm8) {
                val chunk = readChunkHeader(raf) ?: break
                if (chunk.dataPos + chunk.dataSize > fileLen) break
                if (chunk.id == "ID3 " || chunk.id == "id3 ") {
                    topLevelId3Chunk = chunk
                    if (chunk.dataSize in 10L..(64L * 1024L * 1024L)) {
                        existingTag = parseId3Chunk(raf, chunk.dataSize.toInt(), file.name)
                    }
                } else if (chunk.id == "PROP" && existingTag == null && chunk.dataSize >= 4L) {
                    val propType = readChunkId(raf)
                    if (propType == "SND ") {
                        val propEnd = chunk.dataPos + chunk.dataSize
                        while (raf.filePointer + 12L <= propEnd) {
                            val sub = readChunkHeader(raf) ?: break
                            if (sub.dataPos + sub.dataSize > propEnd) break
                            if ((sub.id == "ID3 " || sub.id == "id3 ") && sub.dataSize in 10L..(64L * 1024L * 1024L)) {
                                existingTag = parseId3Chunk(raf, sub.dataSize.toInt(), file.name)
                            }
                            raf.seek(sub.nextChunkPos.coerceAtMost(propEnd))
                        }
                    }
                }
                raf.seek(chunk.nextChunkPos.coerceAtMost(fileLen))
            }

            val targetTag: AbstractID3v2Tag = buildUpdatedId3Tag(existingTag, request, options)
            val id3Bytes = serializeId3Tag(targetTag)

            // Determine where to write the "ID3 " chunk:
            // If a top-level "ID3 " chunk was the last chunk in FRM8, replace it in place by truncating there.
            // If it was in the middle of FRM8, shift trailing chunks up and append at the end of FRM8.
            val truncatePos: Long = if (topLevelId3Chunk != null) {
                if (topLevelId3Chunk.nextChunkPos >= endOfFrm8) {
                    topLevelId3Chunk.headerPos
                } else {
                    shiftBytesUp(
                        raf = raf,
                        fromPos = topLevelId3Chunk.nextChunkPos,
                        toPos = topLevelId3Chunk.headerPos,
                        byteCount = endOfFrm8 - topLevelId3Chunk.nextChunkPos,
                    )
                    endOfFrm8 - (topLevelId3Chunk.nextChunkPos - topLevelId3Chunk.headerPos)
                }
            } else {
                endOfFrm8
            }

            raf.seek(truncatePos)
            raf.write("ID3 ".toByteArray(Charsets.US_ASCII))
            raf.writeLong(id3Bytes.size.toLong())
            raf.write(id3Bytes)
            if (id3Bytes.size % 2 != 0) {
                raf.writeByte(0)
            }

            val newEndOfFrm8 = raf.filePointer
            raf.setLength(newEndOfFrm8)

            // Update FRM8 64-bit size at offset 4
            val newFrm8DataSize = newEndOfFrm8 - 12L
            raf.seek(4L)
            raf.writeLong(newFrm8DataSize)
            raf.fd.sync()
        }
    }

    private fun buildUpdatedId3Tag(
        existing: AbstractID3v2Tag?,
        request: WriteRequest,
        options: WriteOptions,
    ): AbstractID3v2Tag {
        val wantV24 = options.id3Version == Id3Version.V2_4
        val tag: AbstractID3v2Tag = when {
            existing == null -> if (wantV24) ID3v24Tag() else ID3v23Tag()
            wantV24 && existing !is ID3v24Tag -> ID3v24Tag(existing)
            !wantV24 && existing !is ID3v23Tag -> ID3v23Tag(existing)
            else -> existing
        }

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
                val artwork = org.jaudiotagger.tag.images.ArtworkFactory.getNew()
                artwork.binaryData = cover.bytes
                artwork.mimeType = cover.mimeType
                tag.setField(artwork)
            }
        }

        return tag
    }

    private fun serializeId3Tag(tag: AbstractID3v2Tag): ByteArray {
        val baos = ByteArrayOutputStream()
        tag.write(baos, 0)
        return baos.toByteArray()
    }

    private fun parseId3Chunk(raf: RandomAccessFile, length: Int, fileName: String): AbstractID3v2Tag? = runCatching {
        val bytes = ByteArray(length)
        raf.readFully(bytes)
        if (bytes.size < 10 || bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return null
        }
        val version = bytes[3].toInt()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        when (version) {
            2 -> ID3v22Tag(buffer, fileName)
            3 -> ID3v23Tag(buffer, fileName)
            4 -> ID3v24Tag(buffer, fileName)
            else -> ID3v23Tag(buffer, fileName)
        }
    }.getOrNull()

    private fun readChunkHeader(raf: RandomAccessFile): DffChunkInfo? {
        if (raf.filePointer + 12L > raf.length()) return null
        val headerPos = raf.filePointer
        val id = readChunkId(raf)
        val size = raf.readLong()
        if (size < 0L) return null
        return DffChunkInfo(
            id = id,
            headerPos = headerPos,
            dataPos = raf.filePointer,
            dataSize = size,
        )
    }

    private fun readChunkId(raf: RandomAccessFile): String {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun shiftBytesUp(raf: RandomAccessFile, fromPos: Long, toPos: Long, byteCount: Long) {
        if (byteCount <= 0L || fromPos == toPos) return
        val buf = ByteArray(64 * 1024)
        var remaining = byteCount
        var readPos = fromPos
        var writePos = toPos
        while (remaining > 0L) {
            val step = minOf(buf.size.toLong(), remaining).toInt()
            raf.seek(readPos)
            raf.readFully(buf, 0, step)
            raf.seek(writePos)
            raf.write(buf, 0, step)
            readPos += step
            writePos += step
            remaining -= step
        }
    }
}
