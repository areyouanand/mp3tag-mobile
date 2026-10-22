package com.mp3tag.mobile.data.spectrum

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitDepthTest {

    @Test
    fun sixteenBitContentPaddedIntoTwentyFourBitIsFlagged() {
        val random = Random(1)
        val stats = BitStats()
        repeat(10_000) { stats.add(random.nextInt(-32_768, 32_768) shl 8, 24) }
        val report = BitDepth.evaluate(stats, containerBits = 24)
        assertEquals(16, report.effectiveBits)
        assertEquals("16-bit audio in a 24-bit file", report.hint?.title)
        assertEquals(HintSeverity.WARNING, report.hint?.severity)
    }

    @Test
    fun fullTwentyFourBitContentIsReportedAsFullDepth() {
        val random = Random(2)
        val stats = BitStats()
        repeat(10_000) { stats.add(random.nextInt(-8_388_608, 8_388_608), 24) }
        val report = BitDepth.evaluate(stats, containerBits = 24)
        assertEquals(24, report.effectiveBits)
        assertEquals("Full bit depth", report.hint?.title)
    }

    @Test
    fun sixteenBitDecodeOfTwentyFourBitFileCannotBeVerified() {
        val random = Random(3)
        val stats = BitStats()
        repeat(10_000) { stats.add(random.nextInt(-32_768, 32_768), 16) }
        val report = BitDepth.evaluate(stats, containerBits = 24)
        assertEquals("Bit depth not verifiable", report.hint?.title)
        assertEquals(HintSeverity.INFO, report.hint?.severity)
    }

    @Test
    fun fractionalFloatSamplesProduceNoClaim() {
        val stats = BitStats()
        stats.nonIntegral = true
        stats.add(12_345, 24)
        val report = BitDepth.evaluate(stats, containerBits = 24)
        assertNull(report.effectiveBits)
        assertNull(report.hint)
    }

    @Test
    fun silenceProducesNoClaim() {
        val stats = BitStats()
        stats.add(0, 24)
        stats.add(0, 24)
        val report = BitDepth.evaluate(stats, containerBits = 24)
        assertNull(report.effectiveBits)
        assertNull(report.hint)
    }
}
