package com.mp3tag.mobile.data.audio

import com.mp3tag.mobile.data.convert.FfmpegBridge
import java.io.Closeable
import java.io.File

/**
 * Streaming mono PCM reader on top of the bundled FFmpeg build.
 *
 * Unlike `MediaCodec`, this decodes **every** format the app supports — including
 * DSD (DSF/DFF), Monkey's Audio (APE) and WavPack (.wv) — at the file's native
 * sample rate, so the spectrum and ReplayGain analyzers no longer depend on the
 * device's platform decoders.
 *
 * Samples are averaged to mono; [sampleRate] is the decoder's output rate.
 * `integerWidth`/`sampleOrBits` describe the OR of every integer sample decoded so
 * far (for the bit-depth padding check). They are only meaningful after reading.
 */
class FfmpegPcmReader private constructor(
    private val handle: Long,
    val sampleRate: Int,
    val channels: Int,
    val durationSeconds: Double,
    /** Bit depth declared by the codec/container (e.g. 24 for a 24-bit APE), or null. */
    val sourceBits: Int?,
    val codecName: String,
) : Closeable {

    private var closed = false

    /** 16/32/64 when the decoder delivers integer PCM, 0 for float/DSD. */
    val integerWidth: Int get() = info()[4].toInt()

    /** Bitwise OR of all decoded integer samples. */
    val sampleOrBits: Long get() = info()[3]

    private fun info(): LongArray = FfmpegBridge.decodeInfo(handle)

    /** Up to [maxSamples] mono samples, or null at the end of the stream. */
    fun read(maxSamples: Int): FloatArray? {
        if (closed || maxSamples <= 0) return null
        val chunk = FfmpegBridge.decodeRead(handle, maxSamples) ?: return null
        return if (chunk.isEmpty()) null else chunk
    }

    override fun close() {
        if (!closed) {
            closed = true
            FfmpegBridge.decodeClose(handle)
        }
    }

    companion object {
        /**
         * Opens [file]; when [windowSeconds] is positive and the file is longer, the
         * decoder is positioned at the middle window (matching the old analyzer
         * behaviour). Returns null when FFmpeg cannot decode the file.
         */
        fun open(file: File, windowSeconds: Double = 0.0): FfmpegPcmReader? {
            if (!FfmpegBridge.isAvailable) return null
            val handle = FfmpegBridge.decodeOpen(file.absolutePath, windowSeconds)
            if (handle == 0L) return null
            val info = FfmpegBridge.decodeInfo(handle)
            val rate = info[0].toInt()
            if (rate <= 0) {
                FfmpegBridge.decodeClose(handle)
                return null
            }
            return FfmpegPcmReader(
                handle = handle,
                sampleRate = rate,
                channels = info[1].toInt().coerceAtLeast(1),
                durationSeconds = info[2] / 1_000_000.0,
                sourceBits = info[5].toInt().takeIf { it > 0 },
                codecName = FfmpegBridge.decodeCodec(handle),
            )
        }
    }
}
