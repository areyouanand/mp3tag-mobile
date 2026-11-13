package com.mp3tag.mobile.data.tag

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Reads and writes FLAC `PICTURE` metadata blocks directly.
 *
 * JAudioTagger cannot embed artwork on Android (`AndroidArtwork.setImageFromData()` throws
 * `UnsupportedOperationException`), so FLAC covers are handled here at the container level.
 * Writes go through [writeSafely], i.e. a temp copy replaces the original only on success.
 */
internal object FlacPictureHandler {

    private const val MAGIC = "fLaC"
    private const val TYPE_STREAMINFO = 0
    private const val TYPE_PICTURE = 6
    private const val PICTURE_TYPE_FRONT_COVER = 3
    private const val MAX_PICTURE_BYTES = 32 * 1024 * 1024

    private data class Block(val type: Int, val data: ByteArray)

    fun read(file: File): CoverImage? = runCatching {
        val (blocks, _) = readBlocks(file)
        val picture = blocks.firstOrNull { it.type == TYPE_PICTURE } ?: return null
        parsePicture(picture.data)
    }.getOrNull()

    fun write(file: File, cover: CoverImage) {
        val block = buildPicture(PICTURE_TYPE_FRONT_COVER, cover)
        rewrite(file) { blocks -> blocks.filter { it.type != TYPE_PICTURE } + Block(TYPE_PICTURE, block) }
    }

    /** FLAC PICTURE block payload, also used for Ogg/Opus `METADATA_BLOCK_PICTURE`. */
    fun buildPictureBlock(cover: CoverImage): ByteArray = buildPicture(PICTURE_TYPE_FRONT_COVER, cover)

    fun parsePictureBlock(data: ByteArray): CoverImage? = parsePicture(data)

    /** Cover stored as a base64 `METADATA_BLOCK_PICTURE` comment (Ogg Vorbis / Opus). */
    fun readVorbisPicture(
        tag: org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag,
    ): CoverImage? = runCatching {
        val encoded = runCatching { tag.getFirst("METADATA_BLOCK_PICTURE") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { tag.getFirst("COVERART") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
        parsePicture(bytes)
    }.getOrNull()

    fun remove(file: File) {
        rewrite(file) { blocks -> blocks.filter { it.type != TYPE_PICTURE } }
    }

    // ------------------------------------------------------------------
    // block level
    // ------------------------------------------------------------------

    private fun readBlocks(file: File): Pair<List<Block>, Long> {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(4)
            raf.readFully(header)
            require(String(header, Charsets.US_ASCII) == MAGIC) { "Not a FLAC file" }

            val blocks = ArrayList<Block>()
            var last = false
            while (!last) {
                val first = raf.readUnsignedByte()
                last = (first and 0x80) != 0
                val type = first and 0x7F
                val length = (raf.readUnsignedByte() shl 16) or
                    (raf.readUnsignedByte() shl 8) or
                    raf.readUnsignedByte()
                require(length in 0..MAX_PICTURE_BYTES) { "Invalid FLAC metadata block size" }
                if (type == TYPE_PICTURE && length > MAX_PICTURE_BYTES) {
                    error("FLAC picture block is too large")
                }
                val data = ByteArray(length)
                raf.readFully(data)
                blocks += Block(type, data)
            }
            require(blocks.firstOrNull()?.type == TYPE_STREAMINFO) { "FLAC STREAMINFO missing" }
            return blocks to raf.filePointer
        }
    }

    private fun rewrite(file: File, transform: (List<Block>) -> List<Block>) {
        val (blocks, audioOffset) = readBlocks(file)
        val updated = transform(blocks)
        require(updated.isNotEmpty() && updated.first().type == TYPE_STREAMINFO) {
            "FLAC STREAMINFO missing"
        }

        val headerBytes = ByteArrayOutputStream()
        headerBytes.write(MAGIC.toByteArray(Charsets.US_ASCII))
        updated.forEachIndexed { index, block ->
            val isLast = index == updated.lastIndex
            headerBytes.write((if (isLast) 0x80 else 0x00) or (block.type and 0x7F))
            headerBytes.write((block.data.size ushr 16) and 0xFF)
            headerBytes.write((block.data.size ushr 8) and 0xFF)
            headerBytes.write(block.data.size and 0xFF)
            headerBytes.write(block.data)
        }

        writeSafely(file) { target ->
            target.outputStream().use { output ->
                headerBytes.writeTo(output)
                RandomAccessFile(file, "r").use { source ->
                    source.seek(audioOffset)
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
                output.fd.sync()
            }
        }
    }

    // ------------------------------------------------------------------
    // PICTURE block
    // ------------------------------------------------------------------

    private fun buildPicture(type: Int, cover: CoverImage): ByteArray {
        val mime = cover.mimeType.ifBlank { sniffImageMime(cover.bytes) }.toByteArray(Charsets.US_ASCII)
        val dimensions = imageDimensions(cover.bytes)
        val out = ByteArrayOutputStream()
        fun be32(value: Int) {
            out.write((value ushr 24) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write(value and 0xFF)
        }
        be32(type)
        be32(mime.size)
        out.write(mime)
        be32(0) // empty description
        be32(dimensions.width)
        be32(dimensions.height)
        be32(dimensions.depth)
        be32(0) // indexed colours
        be32(cover.bytes.size)
        out.write(cover.bytes)
        return out.toByteArray()
    }

    private fun parsePicture(data: ByteArray): CoverImage? {
        if (data.size < 32) return null
        var pos = 0
        fun be32(): Int {
            val value = ((data[pos].toInt() and 0xFF) shl 24) or
                ((data[pos + 1].toInt() and 0xFF) shl 16) or
                ((data[pos + 2].toInt() and 0xFF) shl 8) or
                (data[pos + 3].toInt() and 0xFF)
            pos += 4
            return value
        }
        be32() // picture type
        val mimeLength = be32()
        if (mimeLength < 0 || pos + mimeLength > data.size) return null
        val mime = String(data, pos, mimeLength, Charsets.US_ASCII)
        pos += mimeLength
        val descriptionLength = be32()
        if (descriptionLength < 0 || pos + descriptionLength > data.size) return null
        pos += descriptionLength
        pos += 16 // width, height, depth, colours
        val dataLength = be32()
        if (dataLength <= 0 || pos + dataLength > data.size) return null
        val bytes = data.copyOfRange(pos, pos + dataLength)
        return CoverImage(bytes, mime.ifBlank { sniffImageMime(bytes) })
    }

    private data class Dimensions(val width: Int, val height: Int, val depth: Int)

    /** Minimal PNG/JPEG header parser; unknown formats fall back to zeroed fields. */
    private fun imageDimensions(bytes: ByteArray): Dimensions = runCatching {
        when {
            bytes.size > 25 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> {
                val width = beInt(bytes, 16)
                val height = beInt(bytes, 20)
                val bitDepth = bytes[24].toInt() and 0xFF
                val colorType = bytes[25].toInt() and 0xFF
                val channels = when (colorType) {
                    0 -> 1
                    2 -> 3
                    3 -> 1
                    4 -> 2
                    6 -> 4
                    else -> 3
                }
                Dimensions(width, height, bitDepth * channels)
            }
            bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> {
                var pos = 2
                while (pos + 9 < bytes.size) {
                    if (bytes[pos].toInt() and 0xFF != 0xFF) {
                        pos++
                        continue
                    }
                    val marker = bytes[pos + 1].toInt() and 0xFF
                    if (marker == 0xD8 || marker == 0xD9 ||
                        marker in 0xD0..0xD7 || marker == 0x01 || marker == 0xFF
                    ) {
                        pos += if (marker == 0xFF) 1 else 2
                        continue
                    }
                    val length = beShort(bytes, pos + 2)
                    val isSof = marker in 0xC0..0xC3 || marker in 0xC5..0xC7 ||
                        marker in 0xC9..0xCB || marker in 0xCD..0xCF
                    if (isSof) {
                        val precision = bytes[pos + 4].toInt() and 0xFF
                        val height = beShort(bytes, pos + 5)
                        val width = beShort(bytes, pos + 7)
                        val components = bytes[pos + 9].toInt() and 0xFF
                        return Dimensions(width, height, precision * components)
                    }
                    pos += 2 + length
                }
                Dimensions(0, 0, 0)
            }
            else -> Dimensions(0, 0, 0)
        }
    }.getOrDefault(Dimensions(0, 0, 0))

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun beShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}
