package com.mp3tag.mobile.data.replaygain

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

data class TrackLoudness(
    val path: String,
    val lufs: Double,        // integrated loudness (approx)
    val peakDb: Double,     // true peak in dBFS
    val gainDb: Double,     // track gain to reach target
)

object LoudnessAnalyzer {
    /** Target integrated loudness in LUFS. ReplayGain classic ~89 dB ≈ -18 LUFS. */
    const val TARGET_LUFS = -18.0

    fun analyzeFile(context: Context, file: File): TrackLoudness? {
        return try {
            val uri = Uri.fromFile(file)
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT

            // K-weighting biquad state (per channel collapsed to mono).
            var z1 = 0.0; var z2 = 0.0
            // coefficients approximating K-weighting at 48k; we keep simple high-pass + shelf.
            // Use a simple first-order high-pass to remove DC and a gain.
            var hpPrev = 0.0
            var hpOut = 0.0

            var sumSq = 0.0
            var count = 0L
            var peak = 0.0

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
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
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
                            val frames = bb.remaining() / (bytesPerSample * channels.coerceAtLeast(1))
                            for (f in 0 until frames) {
                                var sum = 0.0
                                for (ch in 0 until channels.coerceAtLeast(1)) {
                                    sum += readSample(bb, encoding)
                                }
                                val mono = sum / channels.coerceAtLeast(1)
                                // simple high-pass (remove DC) + gain
                                hpOut = mono - hpPrev + 0.995 * hpOut
                                hpPrev = mono
                                val k = hpOut * 1.5 // crude K-weighting gain
                                sumSq += k * k
                                count++
                                val a = kotlin.math.abs(mono)
                                if (a > peak) peak = a
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

            if (count == 0L) return null
            val meanSq = sumSq / count
            val lufs = -0.691 + 10.0 * log10(meanSq.coerceAtLeast(1e-12))
            val peakDb = 20.0 * log10(peak.coerceAtLeast(1e-12))
            val gain = TARGET_LUFS - lufs
            TrackLoudness(file.absolutePath, lufs, peakDb, gain)
        } catch (e: Exception) {
            null
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