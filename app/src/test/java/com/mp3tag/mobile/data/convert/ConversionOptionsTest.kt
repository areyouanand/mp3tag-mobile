package com.mp3tag.mobile.data.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ConversionOptionsTest {

    @Test
    fun `lossless targets ignore the bitrate`() {
        val settings = ConvertSettings(
            target = ConvertTarget.FLAC,
            bitrateKbps = 320,
        ).normalized()
        assertEquals(0, settings.bitrateKbps)
    }

    @Test
    fun `unknown bitrate falls back to the target default`() {
        val settings = ConvertSettings(
            target = ConvertTarget.OPUS,
            bitrateKbps = 999,
        ).normalized()
        assertEquals(ConvertTarget.OPUS.defaultBitrate, settings.bitrateKbps)
    }

    @Test
    fun `known bitrate is kept`() {
        val settings = ConvertSettings(
            target = ConvertTarget.MP3,
            bitrateKbps = 192,
        ).normalized()
        assertEquals(192, settings.bitrateKbps)
    }

    @Test
    fun `unsupported sample rates reset to keep-source`() {
        val settings = ConvertSettings(target = ConvertTarget.MP3, sampleRateHz = 12345)
        assertEquals(0, settings.normalized().sampleRateHz)
    }

    @Test
    fun `channels are clamped to mono or stereo`() {
        assertEquals(2, ConvertSettings(channels = 6).normalized().channels)
        assertEquals(0, ConvertSettings(channels = -1).normalized().channels)
    }

    @Test
    fun `pcm encoders follow the requested bit depth`() {
        assertEquals("pcm_s16le", ConvertTarget.WAV.encoderFor(16))
        assertEquals("pcm_s24le", ConvertTarget.WAV.encoderFor(24))
        assertEquals("pcm_s16be", ConvertTarget.AIFF.encoderFor(16))
        assertEquals("pcm_s24be", ConvertTarget.AIFF.encoderFor(24))
    }

    @Test
    fun `lossy encoders are mapped by name`() {
        assertEquals("libmp3lame", ConvertTarget.MP3.encoderFor(16))
        assertEquals("aac", ConvertTarget.M4A.encoderFor(16))
        assertEquals("libopus", ConvertTarget.OPUS.encoderFor(16))
    }

    @Test
    fun `bit depth only applies to wav and aiff`() {
        assertTrue(ConvertTarget.WAV.supportsBitDepth)
        assertTrue(ConvertTarget.AIFF.supportsBitDepth)
        assertFalse(ConvertTarget.MP3.supportsBitDepth)
        assertFalse(ConvertTarget.FLAC.supportsBitDepth)
    }

    @Test
    fun `output file keeps the source name when there is no collision`() {
        val dir = Files.createTempDirectory("convert-test").toFile()
        try {
            val source = File(dir, "track.flac").apply { writeText("x") }
            val out = AudioConverter.uniqueOutputFile(source, ConvertTarget.MP3)
            assertEquals("track.mp3", out.name)
            assertEquals(dir.absolutePath, out.parentFile!!.absolutePath)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `existing output gets a converted suffix and never overwrites`() {
        val dir = Files.createTempDirectory("convert-test").toFile()
        try {
            val source = File(dir, "track.wav").apply { writeText("x") }
            File(dir, "track.mp3").writeText("old")
            val first = AudioConverter.uniqueOutputFile(source, ConvertTarget.MP3)
            assertEquals("track (converted).mp3", first.name)

            first.writeText("new")
            val second = AudioConverter.uniqueOutputFile(source, ConvertTarget.MP3)
            assertEquals("track (converted 2).mp3", second.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `same-format conversion never writes over the source`() {
        val dir = Files.createTempDirectory("convert-test").toFile()
        try {
            val source = File(dir, "track.mp3").apply { writeText("x") }
            val out = AudioConverter.uniqueOutputFile(source, ConvertTarget.MP3)
            assertEquals("track (converted).mp3", out.name)
            assertTrue(source.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `output subfolder is created and used`() {
        val dir = Files.createTempDirectory("convert-test").toFile()
        try {
            val source = File(dir, "track.wav").apply { writeText("x") }
            val out = AudioConverter.outputFileFor(
                source,
                ConvertSettings(target = ConvertTarget.FLAC, outputSubfolder = "Converted"),
            )
            assertEquals("Converted", out.parentFile!!.name)
            assertEquals("track.flac", out.name)
            assertTrue(out.parentFile!!.isDirectory)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `path traversal in the subfolder is removed`() {
        val normalized = ConvertSettings(outputSubfolder = "../../etc").normalized()
        assertEquals("etc", normalized.outputSubfolder)
    }

    @Test
    fun `compression override wins over the target default`() {
        assertEquals(8, ConvertTarget.FLAC.qualityFor())
        assertEquals(3, ConvertTarget.FLAC.qualityFor(3))
    }

    @Test
    fun `overall progress covers the whole batch`() {
        assertEquals(0, ConversionProgress(0, 4, "a", 0).overallPercent)
        assertEquals(50, ConversionProgress(2, 4, "c", 0).overallPercent)
        assertEquals(62, ConversionProgress(2, 4, "c", 50).overallPercent)
        assertEquals(100, ConversionProgress(3, 4, "d", 100).overallPercent)
    }
}
