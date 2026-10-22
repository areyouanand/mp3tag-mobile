package com.mp3tag.mobile.data.spectrum

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Collects the bits set in the decoded integer samples. Zero low bits across the whole
 * section mean the audio was padded from a shorter word length.
 */
internal class BitStats {
    var mask: Int = 0
        private set
    var precisionBits: Int = 0
        private set

    /** True when float samples were not whole multiples of 2^-23, so integer checks are invalid. */
    var nonIntegral: Boolean = false

    fun add(raw: Int, bits: Int) {
        mask = mask or raw
        precisionBits = bits
    }
}

data class BitDepthReport(
    /** Bit depth declared by the container header (FLAC/WAV), if it could be read. */
    val containerBits: Int?,
    /** Precision the decoder delivered. */
    val decodedBits: Int,
    /** Bits that actually carry signal, or null when it can't be determined. */
    val effectiveBits: Int?,
    val hint: SpectrumHint?,
)

internal object BitDepth {

    fun evaluate(stats: BitStats, containerBits: Int?): BitDepthReport {
        val decoded = stats.precisionBits
        val effective = if (stats.mask != 0 && !stats.nonIntegral && decoded > 0) {
            decoded - Integer.numberOfTrailingZeros(stats.mask)
        } else {
            null
        }
        return BitDepthReport(
            containerBits = containerBits,
            decodedBits = decoded,
            effectiveBits = effective,
            hint = hintFor(effective, decoded, containerBits),
        )
    }

    private fun hintFor(effective: Int?, decoded: Int, containerBits: Int?): SpectrumHint? {
        if (effective == null) return null
        if (decoded < 24) {
            return if (containerBits != null && containerBits > decoded) {
                SpectrumHint(
                    HintSeverity.INFO,
                    "Bit depth not verifiable",
                    "This is a ${containerBits}-bit file, but this device decodes it to $decoded-bit, " +
                        "so padding inside the file can't be checked.",
                )
            } else {
                null
            }
        }
        return when {
            effective <= 16 -> SpectrumHint(
                HintSeverity.WARNING,
                "16-bit audio in a ${containerBits ?: decoded}-bit file",
                "Only $effective bits carry signal; the rest are zeros. Upscaling to a higher bit depth adds no detail.",
            )
            effective < 24 -> SpectrumHint(
                HintSeverity.INFO,
                "Effective ${effective}-bit",
                "The lowest ${decoded - effective} bits are always zero, so this is ${effective}-bit audio with padding.",
            )
            else -> SpectrumHint(
                HintSeverity.INFO,
                "Full bit depth",
                "The samples use all $decoded bits, so the file has the full bit-depth resolution.",
            )
        }
    }
}

/** Reads the bit depth from the header of FLAC and WAV files. */
internal object ContainerProbe {

    fun bitsPerSample(file: File, extension: String): Int? = runCatching {
        FileInputStream(file).use { stream ->
            when (extension) {
                "flac" -> flacBits(stream)
                "wav", "wave" -> wavBits(stream)
                else -> null
            }
        }
    }.getOrNull()

    fun bitsPerSample(context: Context, uri: Uri, extension: String): Int? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (extension) {
                "flac" -> flacBits(stream)
                "wav", "wave" -> wavBits(stream)
                else -> null
            }
        }
    }.getOrNull()

    private fun flacBits(stream: InputStream): Int? {
        val head = ByteArray(42)
        if (!readFully(stream, head)) return null
        if (String(head, 0, 4, StandardCharsets.US_ASCII) != "fLaC") return null
        // STREAMINFO starts at byte 8; sample rate (20 bits), channels (3) and bits-per-sample minus one (5)
        // follow at its byte 10.
        val b12 = head[20].toInt() and 0xFF
        val b13 = head[21].toInt() and 0xFF
        return ((b12 and 1) shl 4 or (b13 shr 4)) + 1
    }

    private fun wavBits(stream: InputStream): Int? {
        val header = ByteArray(12)
        if (!readFully(stream, header)) return null
        if (String(header, 0, 4, StandardCharsets.US_ASCII) != "RIFF" ||
            String(header, 8, 4, StandardCharsets.US_ASCII) != "WAVE"
        ) {
            return null
        }
        repeat(64) {
            val chunk = ByteArray(8)
            if (!readFully(stream, chunk)) return null
            val id = String(chunk, 0, 4, StandardCharsets.US_ASCII)
            val size = (chunk[4].toInt() and 0xFF) or
                ((chunk[5].toInt() and 0xFF) shl 8) or
                ((chunk[6].toInt() and 0xFF) shl 16) or
                ((chunk[7].toInt() and 0xFF) shl 24)
            if (id == "fmt ") {
                val fmt = ByteArray(size.coerceAtLeast(16))
                if (!readFully(stream, fmt)) return null
                return (fmt[14].toInt() and 0xFF) or ((fmt[15].toInt() and 0xFF) shl 8)
            }
            var remaining = size.toLong() + (size and 1)
            while (remaining > 0) {
                val skipped = stream.skip(remaining)
                if (skipped <= 0) return null
                remaining -= skipped
            }
        }
        return null
    }

    private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val n = stream.read(buffer, offset, buffer.size - offset)
            if (n < 0) return false
            offset += n
        }
        return true
    }
}
