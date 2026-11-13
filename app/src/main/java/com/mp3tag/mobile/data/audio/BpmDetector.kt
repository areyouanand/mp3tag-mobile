package com.mp3tag.mobile.data.audio

import com.mp3tag.mobile.data.audio.FfmpegPcmReader
import java.io.File
import kotlin.math.sqrt

/**
 * Lightweight tempo estimator used by the editor's "Detect BPM" tool.
 *
 * Decodes a 90 s window, builds a 10 ms onset envelope, then picks the
 * autocorrelation peak in the 50–200 BPM range. This is not a substitute for a
 * dedicated beat tracker, but it is good enough to pre-fill the BPM tag on
 * electronic/pop material and is entirely on-device.
 */
object BpmDetector {

    private const val WINDOW_SECONDS = 90.0
    private const val ENVELOPE_HOP_SECONDS = 0.01

    fun detect(file: File): Int? {
        val reader = FfmpegPcmReader.open(file, WINDOW_SECONDS) ?: return null
        reader.use {
            val hop = (reader.sampleRate * ENVELOPE_HOP_SECONDS).toInt().coerceAtLeast(64)
            val energy = ArrayList<Double>()
            var sum = 0.0
            var count = 0
            while (true) {
                val chunk = reader.read(hop * 8) ?: break
                for (sample in chunk) {
                    val v = sample.toDouble()
                    sum += v * v
                    count++
                    if (count == hop) {
                        energy.add(sqrt(sum / hop))
                        sum = 0.0
                        count = 0
                    }
                }
            }
            if (energy.size < 64) return null

            // Onset strength: positive energy change only.
            val onset = DoubleArray(energy.size)
            for (i in 1 until energy.size) {
                onset[i] = (energy[i] - energy[i - 1]).coerceAtLeast(0.0)
            }
            val mean = onset.average()
            if (mean <= 1e-9) return null
            for (i in onset.indices) onset[i] = (onset[i] - mean).coerceAtLeast(0.0)

            val framesPerSecond = 1.0 / ENVELOPE_HOP_SECONDS
            val minLag = (framesPerSecond * 60.0 / 200.0).toInt().coerceAtLeast(1)
            val maxLag = (framesPerSecond * 60.0 / 50.0).toInt().coerceAtMost(onset.size - 1)
            if (maxLag <= minLag) return null

            var bestLag = 0
            var bestScore = 0.0
            for (lag in minLag..maxLag) {
                var score = 0.0
                var n = 0
                var i = lag
                while (i < onset.size) {
                    score += onset[i] * onset[i - lag]
                    n++
                    i++
                }
                if (n > 0) score /= n
                // Slight preference for 90-140 BPM to avoid half/double-time errors.
                val bpm = framesPerSecond * 60.0 / lag
                val weight = when {
                    bpm in 80.0..160.0 -> 1.15
                    bpm in 60.0..180.0 -> 1.05
                    else -> 1.0
                }
                score *= weight
                if (score > bestScore) {
                    bestScore = score
                    bestLag = lag
                }
            }
            if (bestLag == 0) return null
            val bpm = (framesPerSecond * 60.0 / bestLag).toInt()
            return bpm.takeIf { it in 40..220 }
        }
    }
}
