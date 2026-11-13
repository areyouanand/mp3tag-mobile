package com.mp3tag.mobile.data.spectrum

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.mp3tag.mobile.data.audio.FfmpegPcmReader
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mono PCM from one window of a file, in the range -1..1. */
internal class DecodedAudio(
    val sampleRate: Int,
    val mono: FloatArray,
    val codecLabel: String,
    val fileDurationSeconds: Double,
    val bitStats: BitStats = BitStats(),
    /** Bit depth declared by the codec/container, when known (e.g. 24-bit APE). */
    val sourceBits: Int? = null,
)

/**
 * Decodes a window of an audio file to mono float PCM.
 *
 * The bundled FFmpeg decoder is used first: it handles every format the app
 * supports (DSD/DSF/DFF, APE, WavPack, AIFF, WMA, …) at the file's native sample
 * rate. MediaCodec remains as a fallback for hardware-specific formats (and for
 * `content://` URIs) when FFmpeg is unavailable or cannot open the file.
 */
internal object PcmDecoder {

    /** Hard cap on decoded samples (~32 MB of float PCM) for very high sample rates. */
    private const val MAX_SAMPLES = 8_000_000

    fun decode(file: File, windowSeconds: Double): DecodedAudio {
        decodeWithFfmpeg(file, windowSeconds)?.let { return it }
        val extractor = MediaExtractor()
        try {
            FileInputStream(file).use { fis ->
                extractor.setDataSource(fis.fd)
            }
            return decodeExtractor(extractor, windowSeconds)
        } finally {
            extractor.release()
        }
    }

    private fun decodeWithFfmpeg(file: File, windowSeconds: Double): DecodedAudio? {
        val reader = FfmpegPcmReader.open(file, windowSeconds) ?: return null
        return reader.use { open ->
            val rate = open.sampleRate
            val wanted = if (windowSeconds > 0) {
                minOf((windowSeconds * rate).toLong(), MAX_SAMPLES.toLong()).toInt()
            } else {
                MAX_SAMPLES
            }
            val buffer = FloatArray(wanted)
            var filled = 0
            while (filled < wanted) {
                val chunk = open.read(minOf(1 shl 18, wanted - filled)) ?: break
                chunk.copyInto(buffer, filled)
                filled += chunk.size
            }
            if (filled == 0) return@use null

            val stats = BitStats()
            stats.add(open.sampleOrBits.toInt(), open.integerWidth)
            DecodedAudio(
                sampleRate = rate,
                mono = if (filled == buffer.size) buffer else buffer.copyOf(filled),
                codecLabel = prettyCodec(open.codecName),
                fileDurationSeconds = open.durationSeconds,
                bitStats = stats,
                sourceBits = open.sourceBits,
            )
        }
    }

    private fun prettyCodec(name: String): String = when {
        name.startsWith("dsd") -> "DSD"
        name == "ape" -> "MONKEY'S AUDIO"
        name == "wavpack" -> "WAVPACK"
        name == "wmav1" || name == "wmav2" -> "WMA"
        name == "alac" -> "ALAC"
        name == "libopus" || name == "opus" -> "OPUS"
        name.isBlank() -> "AUDIO"
        else -> name.uppercase()
    }

    fun decode(context: Context, uri: Uri, windowSeconds: Double): DecodedAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            return decodeExtractor(extractor, windowSeconds)
        } finally {
            extractor.release()
        }
    }

    private fun decodeExtractor(extractor: MediaExtractor, windowSeconds: Double): DecodedAudio {
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No audio track found in this file.")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio format.")
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
        val fileSeconds = durationUs / 1_000_000.0

        if (fileSeconds > windowSeconds) {
            val startUs = ((fileSeconds - windowSeconds) / 2.0 * 1_000_000.0).toLong()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            error("This device has no decoder for $mime.")
        }
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            return decodeLoop(extractor, codec, format, mime, windowSeconds, fileSeconds)
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
        mime: String,
        windowSeconds: Double,
        fileSeconds: Double,
    ): DecodedAudio {
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding = AudioFormat.ENCODING_PCM_16BIT

        val chunks = mutableListOf<FloatArray>()
        val stats = BitStats()
        var collected = 0L
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val deadline = System.nanoTime() + 60_000_000_000L

        while (!outputDone) {
            check(System.nanoTime() < deadline) { "Decoding took too long." }

            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex) ?: error("Decoder input unavailable.")
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val out = codec.outputFormat
                    sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                }
                MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val mono = toMono(buffer.slice().order(ByteOrder.LITTLE_ENDIAN), channels, encoding, stats)
                        chunks += mono
                        collected += mono.size
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 ||
                        collected >= windowSeconds * sampleRate
                    ) {
                        outputDone = true
                    }
                }
            }
        }

        val samples = FloatArray(collected.toInt())
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(samples, offset)
            offset += chunk.size
        }
        check(samples.isNotEmpty()) { "No audio could be decoded from this file." }
        return DecodedAudio(
            sampleRate = sampleRate,
            mono = samples,
            codecLabel = mime.removePrefix("audio/").uppercase(),
            fileDurationSeconds = fileSeconds,
            bitStats = stats,
        )
    }

    private fun toMono(buffer: ByteBuffer, channels: Int, encoding: Int, stats: BitStats): FloatArray {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val channelCount = channels.coerceAtLeast(1)
        val frames = buffer.remaining() / (bytesPerSample * channelCount)
        val out = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            repeat(channelCount) {
                sum += readSample(buffer, encoding, stats)
            }
            out[frame] = sum / channelCount
        }
        return out
    }

    private fun readSample(buffer: ByteBuffer, encoding: Int, stats: BitStats): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> {
            val value = buffer.float
            val scaled = value.toDouble() * 8_388_608.0
            val whole = Math.rint(scaled)
            if (scaled != whole) stats.nonIntegral = true
            stats.add(whole.toInt(), 24)
            value
        }
        AudioFormat.ENCODING_PCM_32BIT -> {
            val raw = buffer.int
            stats.add(raw, 32)
            raw / 2_147_483_648f
        }
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
            val b0 = buffer.get().toInt() and 0xFF
            val b1 = buffer.get().toInt() and 0xFF
            val b2 = buffer.get().toInt()
            val raw = (b2 shl 16) or (b1 shl 8) or b0
            stats.add(raw, 24)
            raw / 8_388_608f
        }
        AudioFormat.ENCODING_PCM_8BIT -> {
            val raw = (buffer.get().toInt() and 0xFF) - 128
            stats.add(raw, 8)
            raw / 128f
        }
        else -> {
            val raw = buffer.short.toInt()
            stats.add(raw, 16)
            raw / 32_768f
        }
    }
}
