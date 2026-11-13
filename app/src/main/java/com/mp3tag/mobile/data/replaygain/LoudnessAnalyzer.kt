package com.mp3tag.mobile.data.replaygain

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.mp3tag.mobile.data.audio.FfmpegPcmReader
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.tan

data class TrackLoudness(
    val path: String,
    val lufs: Double,        // integrated loudness (BS.1770-4, gated)
    val peakDb: Double,      // true peak in dBFS (4x oversampled)
    val gainDb: Double,      // clip-safe track gain to reach the target
    /** True when the ideal gain had to be reduced to avoid clipping. */
    val clipLimited: Boolean = false,
    /** 400 ms gated block powers, used to compute a proper album gain. */
    val blockPowers: DoubleArray = DoubleArray(0),
)

data class AlbumLoudness(
    val lufs: Double,
    val peakDb: Double,
    val gainDb: Double,
    val clipLimited: Boolean = false,
)

object LoudnessAnalyzer {
    /** Target integrated loudness in LUFS. ReplayGain classic ~89 dB ≈ -18 LUFS. */
    const val TARGET_LUFS = -18.0

    /** Peak headroom kept when capping positive gain. */
    private const val PEAK_CEILING_DB = -0.1

    private const val READ_CHUNK = 1 shl 16

    /**
     * Analyses a whole file. The bundled FFmpeg decoder is preferred because it
     * handles DSD (DSF/DFF), APE and WavPack — formats MediaCodec cannot decode on
     * most devices. MediaCodec stays as a fallback (also used for `content://` URIs).
     */
    fun analyzeFile(context: Context, file: File): TrackLoudness? =
        analyzeWithFfmpeg(file) ?: analyzeWithMediaCodec(context, file)

    fun analyzeUri(context: Context, uri: Uri, path: String): TrackLoudness? =
        analyzeWithMediaCodec(context, File(path), uri)

    // ------------------------------------------------------------------
    // FFmpeg path (all supported formats)
    // ------------------------------------------------------------------

    private fun analyzeWithFfmpeg(file: File): TrackLoudness? {
        val reader = FfmpegPcmReader.open(file, 0.0) ?: return null
        return reader.use {
            val meter = Meter(reader.sampleRate)
            while (true) {
                val chunk = reader.read(READ_CHUNK) ?: break
                for (sample in chunk) meter.add(sample.toDouble())
            }
            meter.finish(file)
        }
    }

    // ------------------------------------------------------------------
    // MediaCodec fallback (URIs and unusual platform-only formats)
    // ------------------------------------------------------------------

    private fun analyzeWithMediaCodec(context: Context, file: File, uri: Uri? = null): TrackLoudness? {
        return try {
            val extractor = MediaExtractor()
            if (uri != null) {
                extractor.setDataSource(context, uri, null)
            } else {
                extractor.setDataSource(file.absolutePath)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var meter = Meter(sampleRate)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = System.nanoTime() + 120_000_000_000L

            while (!outputDone) {
                if (System.nanoTime() > deadline) break
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: break
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
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                        val newRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (newRate != sampleRate) {
                            sampleRate = newRate
                            meter = Meter(sampleRate)
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bb = buffer.slice().order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            val bytesPerSample = when (encoding) {
                                AudioFormat.ENCODING_PCM_8BIT -> 1
                                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                                AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
                                else -> 2
                            }
                            val frameCount = bb.remaining() / (bytesPerSample * channels.coerceAtLeast(1))
                            for (f in 0 until frameCount) {
                                var sum = 0.0
                                for (ch in 0 until channels.coerceAtLeast(1)) {
                                    sum += readSample(bb, encoding)
                                }
                                meter.add(sum / channels.coerceAtLeast(1))
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            runCatching { codec.stop() }
            codec.release()
            extractor.release()

            meter.finish(file)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // BS.1770-4 loudness meter
    // ------------------------------------------------------------------

    /**
     * ITU-R BS.1770-4: K-weighting (high shelf + RLB high-pass), 400 ms blocks with
     * 75 % overlap, -70 LUFS absolute gate and a -10 LU relative gate. True peak is
     * estimated with 4x linear oversampling (mono downmix).
     */
    private class Meter(private val sampleRate: Int) {
        private val hop = (sampleRate / 10).coerceAtLeast(1)
        private val blockLen = hop * 4
        private val pre = Biquad.highShelf(sampleRate)
        private val rlb = Biquad.highPass(sampleRate)

        private val blocks = ArrayList<Double>()
        private val hopCums = ArrayDeque<Double>()
        private var cumulative = 0.0
        private var processed = 0

        private var peak = 0.0
        private var prev = 0.0
        private var hasPrev = false

        fun add(sample: Double) {
            val weighted = rlb.process(pre.process(sample))
            cumulative += weighted * weighted
            processed++

            // True-peak approximation: 4 points per input interval.
            if (hasPrev) {
                val delta = sample - prev
                for (k in 1..3) {
                    val interpolated = abs(prev + delta * (k / 4.0))
                    if (interpolated > peak) peak = interpolated
                }
            }
            val amplitude = abs(sample)
            if (amplitude > peak) peak = amplitude
            prev = sample
            hasPrev = true

            if (processed % hop == 0) {
                hopCums.addLast(cumulative)
                while (hopCums.size > 4) hopCums.removeFirst()
                if (hopCums.size == 4) {
                    val before = hopCums.first()
                    blocks.add((cumulative - before) / blockLen)
                }
            }
        }

        fun finish(file: File): TrackLoudness? {
            if (blocks.isEmpty() && processed > 0) {
                // Very short files (<400 ms) never complete a gating block.
                blocks.add(cumulative / processed)
            }
            if (blocks.isEmpty()) return null
            val powers = blocks.toDoubleArray()
            val lufs = integratedLufs(powers)
            if (!lufs.isFinite()) return null
            val peakDb = 20.0 * log10(peak.coerceAtLeast(1e-12))
            val ideal = TARGET_LUFS - lufs
            val clipSafe = PEAK_CEILING_DB - peakDb
            val gain = min(ideal, clipSafe)
            return TrackLoudness(
                path = file.absolutePath,
                lufs = lufs,
                peakDb = peakDb,
                gainDb = gain,
                clipLimited = ideal > clipSafe + 0.01,
                blockPowers = powers,
            )
        }
    }

    /** Proper album loudness: gated across all tracks, peak-safe gain. */
    fun albumMetrics(tracks: List<TrackLoudness>): AlbumLoudness? {
        if (tracks.isEmpty()) return null
        val powers = tracks.flatMap { it.blockPowers.toList() }.toDoubleArray()
        val lufs = if (powers.isNotEmpty()) {
            integratedLufs(powers)
        } else {
            tracks.map { it.lufs }.average()
        }
        if (!lufs.isFinite()) return null
        val peakDb = tracks.maxOf { it.peakDb }
        val ideal = TARGET_LUFS - lufs
        val clipSafe = PEAK_CEILING_DB - peakDb
        return AlbumLoudness(
            lufs = lufs,
            peakDb = peakDb,
            gainDb = min(ideal, clipSafe),
            clipLimited = ideal > clipSafe + 0.01,
        )
    }

    private fun integratedLufs(blockPowers: DoubleArray): Double {
        if (blockPowers.isEmpty()) return Double.NEGATIVE_INFINITY
        val absolute = blockPowers.filter { loudnessOf(it) > -70.0 }
        // Fully silent material: report the gate floor instead of -Infinity so the
        // file still gets a (clip-limited) gain and the UI can show a result.
        if (absolute.isEmpty()) return -70.0
        val absoluteMean = absolute.average()
        val relativeGate = loudnessOf(absoluteMean) - 10.0
        val gated = absolute.filter { loudnessOf(it) > relativeGate }
        val finalBlocks = if (gated.isEmpty()) absolute else gated
        return loudnessOf(finalBlocks.average())
    }

    private fun loudnessOf(meanSquare: Double): Double =
        -0.691 + 10.0 * log10(meanSquare.coerceAtLeast(1e-12))

    /**
     * Direct-form biquad. Coefficients follow the reference BS.1770 implementation
     * (libebur128/pyloudnorm) and are recomputed for the file's sample rate.
     */
    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        companion object {
            private const val PI = Math.PI

            /** K-weighting stage 1: high shelf (+4 dB above ~1.7 kHz). */
            fun highShelf(fs: Int): Biquad {
                val f0 = 1681.9744509555319
                val gainDb = 3.99984385397
                val q = 0.7071752369554193
                val k = tan(PI * f0 / fs)
                val vh = Math.pow(10.0, gainDb / 20.0)
                val vb = Math.pow(vh, 0.4996667741545416)
                val a0 = 1.0 + k / q + k * k
                return Biquad(
                    b0 = (vh + vb * k / q + k * k) / a0,
                    b1 = 2.0 * (k * k - vh) / a0,
                    b2 = (vh - vb * k / q + k * k) / a0,
                    a1 = 2.0 * (k * k - 1.0) / a0,
                    a2 = (1.0 - k / q + k * k) / a0,
                )
            }

            /** K-weighting stage 2: RLB high-pass (~38 Hz). */
            fun highPass(fs: Int): Biquad {
                val f0 = 38.13547087602444
                val q = 0.5003270373238773
                val k = tan(PI * f0 / fs)
                val a0 = 1.0 + k / q + k * k
                return Biquad(
                    b0 = 1.0 / a0,
                    b1 = -2.0 / a0,
                    b2 = 1.0 / a0,
                    a1 = 2.0 * (k * k - 1.0) / a0,
                    a2 = (1.0 - k / q + k * k) / a0,
                )
            }
        }
    }

    private fun readSample(bb: java.nio.ByteBuffer, encoding: Int): Double = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> bb.float.toDouble()
        AudioFormat.ENCODING_PCM_32BIT -> bb.int / 2_147_483_648.0
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
            val b0 = bb.get().toInt() and 0xFF
            val b1 = bb.get().toInt() and 0xFF
            val b2 = bb.get().toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8_388_608.0
        }
        AudioFormat.ENCODING_PCM_8BIT -> ((bb.get().toInt() and 0xFF) - 128) / 128.0
        else -> bb.short.toInt() / 32_768.0
    }
}
