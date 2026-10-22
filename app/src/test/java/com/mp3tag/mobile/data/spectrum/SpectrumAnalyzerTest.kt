package com.mp3tag.mobile.data.spectrum

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumAnalyzerTest {

    /** Sum of random-frequency tones between 100 Hz and [topHz]: a band-limited test signal. */
    private fun bandLimited(sampleRate: Int, seconds: Double, topHz: Double): DecodedAudio {
        val random = Random(7)
        val n = (sampleRate * seconds).toInt()
        val out = DoubleArray(n)
        repeat(150) {
            val freq = 100.0 + random.nextDouble() * (topHz - 100.0)
            val phase = random.nextDouble() * 2 * PI
            for (i in 0 until n) {
                out[i] += 0.03 * sin(2 * PI * freq * i / sampleRate + phase)
            }
        }
        return DecodedAudio(
            sampleRate = sampleRate,
            mono = FloatArray(n) { out[it].toFloat() },
            codecLabel = "TEST",
            fileDurationSeconds = seconds,
        )
    }

    @Test
    fun fftFindsTonePeak() {
        val size = 1024
        val input = DoubleArray(size) { sin(2 * PI * 50 * it / size) }
        val power = DoubleArray(size / 2 + 1)
        Fft(size).power(input, power)
        val peak = (1 until power.size).maxByOrNull { power[it] }
        assertEquals(50, peak)
    }

    @Test
    fun fullBandSignalHasNoCutoff() {
        val report = SpectrumAnalyzer.analyze(bandLimited(44_100, 3.0, 21_950.0), lossless = false)
        assertNull(report.cutoffHz)
        assertTrue(report.hints.any { it.title == "No lowpass found" })
    }

    @Test
    fun cdSourceUpsampledTo96kIsFlagged() {
        val report = SpectrumAnalyzer.analyze(bandLimited(96_000, 3.0, 21_500.0), lossless = true)
        val cutoff = requireNotNull(report.cutoffHz)
        assertTrue("cutoff was $cutoff", abs(cutoff - 21_500.0) < 600.0)
        val hint = report.hints.single { it.title == "Likely upsampled" }
        assertEquals(HintSeverity.WARNING, hint.severity)
        assertTrue(hint.detail.contains("44.1 kHz"))
    }

    @Test
    fun lossyLowpassInLosslessContainerIsFlagged() {
        val report = SpectrumAnalyzer.analyze(bandLimited(44_100, 3.0, 16_000.0), lossless = true)
        val cutoff = requireNotNull(report.cutoffHz)
        assertTrue("cutoff was $cutoff", abs(cutoff - 16_000.0) < 400.0)
        val hint = report.hints.single { it.title == "Lossless file, but lossy-looking" }
        assertEquals(HintSeverity.WARNING, hint.severity)
    }

    @Test
    fun silenceIsReportedAsTooQuiet() {
        val silent = DecodedAudio(44_100, FloatArray(44_100 * 2), "TEST", 2.0)
        val report = SpectrumAnalyzer.analyze(silent, lossless = true)
        assertNull(report.cutoffHz)
        assertTrue(report.hints.any { it.title == "Very quiet recording" })
    }
}
