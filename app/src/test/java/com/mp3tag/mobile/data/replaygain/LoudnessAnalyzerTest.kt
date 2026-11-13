package com.mp3tag.mobile.data.replaygain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LoudnessAnalyzerTest {

    private fun powerFor(lufs: Double): Double = 10.0.pow((lufs + 0.691) / 10.0)

    private fun track(lufs: Double, peakDb: Double) = TrackLoudness(
        path = "test.flac",
        lufs = lufs,
        peakDb = peakDb,
        gainDb = LoudnessAnalyzer.TARGET_LUFS - lufs,
        blockPowers = doubleArrayOf(powerFor(lufs)),
    )

    @Test
    fun albumLoudnessIsGatedAcrossTracks() {
        val album = LoudnessAnalyzer.albumMetrics(listOf(track(-20.0, -3.0), track(-20.0, -6.0)))
        requireNotNull(album)
        assertEquals(-20.0, album.lufs, 0.2)
        assertEquals(-3.0, album.peakDb, 0.001)
        assertEquals(2.0, album.gainDb, 0.05)
    }

    @Test
    fun gainIsCappedToAvoidClipping() {
        val album = LoudnessAnalyzer.albumMetrics(listOf(track(-24.0, -0.5)))
        requireNotNull(album)
        assertTrue("gain should be capped below the peak ceiling", album.gainDb <= 0.4)
        assertTrue(album.clipLimited)
    }
}
