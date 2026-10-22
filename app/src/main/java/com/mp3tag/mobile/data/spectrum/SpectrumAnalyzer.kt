package com.mp3tag.mobile.data.spectrum

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

enum class HintSeverity { INFO, WARNING }

data class SpectrumHint(
    val severity: HintSeverity,
    val title: String,
    val detail: String,
)

/**
 * Result of analysing one file.
 * [curveDb] is the averaged, smoothed level per FFT bin in dB relative to a full-scale sine
 * (bin k is at k * sampleRate / FFT_SIZE). [cutoffHz] is null when the spectrum reaches Nyquist.
 */
class SpectrumReport(
    val sampleRate: Int,
    val analyzedSeconds: Double,
    val codecLabel: String,
    val cutoffHz: Double?,
    val referenceDb: Double,
    val curveDb: FloatArray,
    val spectrogramWidth: Int,
    val spectrogramHeight: Int,
    /** ARGB pixels, row-major, top row = highest frequency. */
    val spectrogramPixels: IntArray,
    /** Null for lossy formats, where bit depth does not apply. */
    val bitDepth: BitDepthReport?,
    val hints: List<SpectrumHint>,
)

internal object SpectrumAnalyzer {

    const val FFT_SIZE = 4096
    private const val HOP = FFT_SIZE / 2
    private const val SMOOTH_HALF_WIDTH = 7
    private const val MAX_COLUMNS = 480
    private const val ROWS = 256
    private const val CUTOFF_DROP_DB = 60.0
    private const val QUIET_DB = -100.0
    private const val NORMALISE = (FFT_SIZE * 0.5) * (FFT_SIZE * 0.5)

    fun analyze(audio: DecodedAudio, lossless: Boolean, containerBits: Int? = null): SpectrumReport {
        val sr = audio.sampleRate
        val x = audio.mono
        require(x.size >= FFT_SIZE) { "The file is too short to analyse." }

        val fft = Fft(FFT_SIZE)
        val bins = FFT_SIZE / 2 + 1
        val window = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1)) }
        val frameCount = (x.size - FFT_SIZE) / HOP + 1
        val stride = max(1, frameCount / MAX_COLUMNS)
        val columns = (frameCount + stride - 1) / stride

        val average = DoubleArray(bins)
        val frame = DoubleArray(FFT_SIZE)
        val power = DoubleArray(bins)
        val spectroDb = FloatArray(columns * ROWS)
        var column = 0

        for (f in 0 until frameCount) {
            val start = f * HOP
            for (i in 0 until FFT_SIZE) frame[i] = x[start + i] * window[i]
            fft.power(frame, power)
            for (k in 0 until bins) average[k] += power[k]

            if (f % stride == 0 && column < columns) {
                for (row in 0 until ROWS) {
                    val kFrom = row * bins / ROWS
                    val kTo = max(kFrom + 1, (row + 1) * bins / ROWS)
                    var sum = 0.0
                    for (k in kFrom until kTo) sum += power[k]
                    spectroDb[column * ROWS + row] = toDb(sum / (kTo - kFrom)).toFloat()
                }
                column += 1
            }
        }

        val averageDb = DoubleArray(bins) { toDb(average[it] / frameCount) }
        val smooth = smooth(averageDb)

        val nyquist = sr / 2.0
        val referenceDb = median(smooth, binOf(1000.0, sr), binOf(min(8000.0, nyquist * 0.8), sr))
        val threshold = referenceDb - CUTOFF_DROP_DB
        val quiet = referenceDb < QUIET_DB

        val upperStart = binOf(nyquist * 0.9, sr)
        val upperLevel = median(smooth, upperStart, bins - 2)
        val fullBand = upperLevel > threshold

        val cutoffHz: Double? = if (quiet || fullBand) {
            null
        } else {
            var found: Int? = null
            for (k in upperStart downTo 1) {
                if (smooth[k] > threshold) {
                    found = k
                    break
                }
            }
            found?.let { it * sr.toDouble() / FFT_SIZE }
        }

        val bitDepth = if (lossless) BitDepth.evaluate(audio.bitStats, containerBits) else null

        val spectrumHints = buildHints(
            sr = sr,
            lossless = lossless,
            quiet = quiet,
            cutoffHz = cutoffHz,
            fullBand = fullBand,
            ultrasonicLevel = if (sr >= 88_200) median(smooth, binOf(22_050.0, sr), bins - 2) else null,
            threshold = threshold,
        )
        val hints = (spectrumHints + listOfNotNull(bitDepth?.hint)).ifEmpty {
            listOf(
                SpectrumHint(
                    HintSeverity.INFO,
                    "No problems found",
                    "The spectrum looks consistent with a file at ${format1(sr / 1000.0)} kHz.",
                ),
            )
        }

        val pixels = colourise(spectroDb, columns, referenceDb)
        return SpectrumReport(
            sampleRate = sr,
            analyzedSeconds = x.size.toDouble() / sr,
            codecLabel = audio.codecLabel,
            cutoffHz = cutoffHz,
            referenceDb = referenceDb,
            curveDb = FloatArray(bins) { smooth[it].toFloat() },
            spectrogramWidth = columns,
            spectrogramHeight = ROWS,
            spectrogramPixels = pixels,
            bitDepth = bitDepth,
            hints = hints,
        )
    }

    private fun buildHints(
        sr: Int,
        lossless: Boolean,
        quiet: Boolean,
        cutoffHz: Double?,
        fullBand: Boolean,
        ultrasonicLevel: Double?,
        threshold: Double,
    ): List<SpectrumHint> {
        if (quiet) {
            return listOf(
                SpectrumHint(
                    HintSeverity.WARNING,
                    "Very quiet recording",
                    "The average level is too low to judge the frequency content reliably.",
                ),
            )
        }
        val hints = mutableListOf<SpectrumHint>()
        val nyquistKhz = sr / 2000.0

        if (fullBand || cutoffHz == null) {
            if (sr >= 88_200 && ultrasonicLevel != null && ultrasonicLevel > threshold) {
                hints += SpectrumHint(
                    HintSeverity.INFO,
                    "Real content above 22 kHz",
                    "Energy continues past 22 kHz up to ${format1(nyquistKhz)} kHz. This looks like genuine high-resolution audio.",
                )
            } else {
                hints += SpectrumHint(
                    HintSeverity.INFO,
                    "No lowpass found",
                    "The spectrum reaches the Nyquist limit of ${format1(nyquistKhz)} kHz.",
                )
            }
        } else {
            val c = cutoffHz
            val upsampledFrom: String? = when {
                sr < 88_200 -> null
                c in 20_500.0..22_600.0 -> "44.1 kHz"
                c in 22_600.0..24_600.0 -> "48 kHz"
                c < 20_500.0 -> "a lossy or lower-rate source"
                else -> null
            }
            if (upsampledFrom != null) {
                hints += SpectrumHint(
                    HintSeverity.WARNING,
                    "Likely upsampled",
                    "The sound stops at ${format1(c / 1000)} kHz, which fits a $upsampledFrom original. " +
                        "The ${format1(sr / 1000.0)} kHz file has no real detail above that, so it adds size but not quality.",
                )
            }

            when {
                c in 15_000.0..17_500.0 -> hints += lossyHint(lossless, c, "about 16 kHz", "MP3 or AAC at about 128 kbps or lower")
                c in 17_500.0..19_500.0 -> hints += lossyHint(lossless, c, "about 19 kHz", "MP3 or AAC at about 192 kbps")
                c in 19_500.0..20_700.0 && lossless -> hints += SpectrumHint(
                    HintSeverity.INFO,
                    "Top cut at ${format1(c / 1000)} kHz",
                    "Common for CD-era masters and for lossy files at 256 to 320 kbps. " +
                        "If the file came from a lossy source, the lossless container adds no quality.",
                )
                c < 20_700.0 && !lossless -> hints += SpectrumHint(
                    HintSeverity.INFO,
                    "Lowpass at ${format1(c / 1000)} kHz",
                    "Normal for lossy encoding at this bitrate.",
                )
            }
        }

        return hints
    }

    private fun lossyHint(lossless: Boolean, cutoffHz: Double, where: String, typical: String): SpectrumHint =
        if (lossless) {
            SpectrumHint(
                HintSeverity.WARNING,
                "Lossless file, but lossy-looking",
                "The top is cut at $where, which is typical of $typical. " +
                    "This file was probably converted from a lossy source, so it cannot restore the lost detail.",
            )
        } else {
            SpectrumHint(
                HintSeverity.INFO,
                "Lowpass at ${format1(cutoffHz / 1000)} kHz",
                "Typical of $typical.",
            )
        }

    private fun format1(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    private fun binOf(hz: Double, sr: Int): Int =
        floor(hz * FFT_SIZE / sr).toInt().coerceIn(1, FFT_SIZE / 2)

    private fun toDb(power: Double): Double = 10.0 * log10(power / NORMALISE + 1e-20)

    private fun smooth(values: DoubleArray): DoubleArray {
        val out = DoubleArray(values.size)
        for (k in values.indices) {
            val from = max(0, k - SMOOTH_HALF_WIDTH)
            val to = min(values.size - 1, k + SMOOTH_HALF_WIDTH)
            var sum = 0.0
            for (i in from..to) sum += values[i]
            out[k] = sum / (to - from + 1)
        }
        return out
    }

    private fun median(values: DoubleArray, fromInclusive: Int, toInclusive: Int): Double {
        val slice = values.copyOfRange(fromInclusive, toInclusive + 1).sorted()
        if (slice.isEmpty()) return Double.NEGATIVE_INFINITY
        return slice[slice.size / 2]
    }

    private fun colourise(spectroDb: FloatArray, columns: Int, referenceDb: Double): IntArray {
        val floor = referenceDb - 90.0
        val top = referenceDb + 10.0
        val pixels = IntArray(columns * ROWS)
        for (col in 0 until columns) {
            for (row in 0 until ROWS) {
                val t = ((spectroDb[col * ROWS + row] - floor) / (top - floor)).coerceIn(0.0, 1.0)
                val y = ROWS - 1 - row
                pixels[y * columns + col] = heat(t)
            }
        }
        return pixels
    }

    /** black → blue → red → yellow → white */
    private fun heat(t: Double): Int {
        val s = (t * 4.0).coerceIn(0.0, 4.0)
        val r: Int
        val g: Int
        val b: Int
        when {
            s < 1.0 -> { r = 0; g = 0; b = (s * 255).toInt() }
            s < 2.0 -> { r = ((s - 1) * 255).toInt(); g = 0; b = ((2 - s) * 255).toInt() }
            s < 3.0 -> { r = 255; g = ((s - 2) * 255).toInt(); b = 0 }
            else -> { r = 255; g = 255; b = ((s - 3) * 255).toInt() }
        }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
