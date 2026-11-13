package com.mp3tag.mobile.data.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import com.mp3tag.mobile.data.convert.AudioConverter
import com.mp3tag.mobile.data.convert.ConvertSettings
import com.mp3tag.mobile.data.convert.ConvertTarget
import com.mp3tag.mobile.data.prefs.Id3Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Verifies the FFmpeg streaming decoder used by Spectrum analysis and ReplayGain. */
@RunWith(AndroidJUnit4::class)
class FfmpegPcmReaderInstrumentedTest {

    private lateinit var dir: File

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "decode-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun readsWavAtNativeRate() {
        val wav = File(dir, "tone.wav")
        TestAudioFactory.writeWav(wav)

        val reader = FfmpegPcmReader.open(wav)
        assertNotNull("FFmpeg could not open the WAV", reader)
        reader!!.use {
            assertEquals(44100, it.sampleRate)
            assertEquals(2, it.channels)
            val samples = readAll(it)
            assertEquals(44100, samples.size)
            assertTrue("audio is silent", samples.any { s -> abs(s) > 0.1f })
        }
    }

    @Test
    fun readsDsdiffAt352k() {
        val dff = File(dir, "silence.dff")
        TestAudioFactory.writeDff(dff)

        val reader = FfmpegPcmReader.open(dff)
        assertNotNull("FFmpeg could not open the DFF", reader)
        reader!!.use {
            // DSD64 is 2.8224 MHz; the decoder outputs one PCM sample per DSD byte.
            assertEquals(352800, it.sampleRate)
            assertEquals(2, it.channels)
            val samples = readAll(it)
            assertTrue("no DSD samples decoded", samples.size > 10_000)
        }
    }

    @Test
    fun readsConvertedMp3() {
        val wav = File(dir, "tone.wav")
        TestAudioFactory.writeWav(wav)
        val outcome = runBlocking {
            AudioConverter.convert(
                paths = listOf(wav.absolutePath),
                settings = ConvertSettings(target = ConvertTarget.MP3),
                id3Version = Id3Version.V2_3,
                onProgress = {},
            )
        }
        val mp3 = File(outcome.results.single().outputPath!!)

        val reader = FfmpegPcmReader.open(mp3)
        assertNotNull("FFmpeg could not open the converted MP3", reader)
        reader!!.use {
            assertEquals(44100, it.sampleRate)
            val samples = readAll(it)
            // LAME delay/padding means the decoded length is close to, not exactly, 1 s.
            assertTrue("unexpected decoded length ${samples.size}", samples.size > 30_000)
            assertTrue("decoded MP3 is silent", samples.any { s -> abs(s) > 0.1f })
        }
    }

    @Test
    fun middleWindowSeeksIntoLongFiles() {
        val wav = File(dir, "tone.wav")
        TestAudioFactory.writeWav(wav)
        val reader = FfmpegPcmReader.open(wav, windowSeconds = 0.25)
        assertNotNull(reader)
        reader!!.use {
            val samples = readAll(it, maxSamples = 20_000)
            // The window is 0.25 s at 44.1 kHz; reading must stop before the file end.
            assertTrue(samples.size > 8_000)
            assertTrue(samples.size <= 20_000)
        }
    }

    @Test
    fun spectrumAnalyzerHandlesDsdiff() {
        val dff = File(dir, "silence.dff")
        TestAudioFactory.writeDff(dff)

        val audio = com.mp3tag.mobile.data.spectrum.PcmDecoder.decode(dff, 60.0)
        assertEquals(352800, audio.sampleRate)
        assertTrue(audio.mono.isNotEmpty())

        val report = com.mp3tag.mobile.data.spectrum.SpectrumAnalyzer.analyze(
            audio,
            lossless = false,
            containerBits = audio.sourceBits,
        )
        assertEquals(352800, report.sampleRate)
    }

    @Test
    fun replayGainHandlesDsdiff() {
        val dff = File(dir, "silence.dff")
        TestAudioFactory.writeDff(dff)

        val loudness = com.mp3tag.mobile.data.replaygain.LoudnessAnalyzer.analyzeFile(context, dff)
        assertNotNull("ReplayGain did not analyse the DSDIFF file", loudness)
        assertTrue(loudness!!.lufs.isFinite())
        assertTrue(loudness.gainDb.isFinite())
    }

    private fun readAll(reader: FfmpegPcmReader, maxSamples: Int = Int.MAX_VALUE): FloatArray {
        val chunks = ArrayList<FloatArray>()
        var total = 0
        while (total < maxSamples) {
            val chunk = reader.read(minOf(1 shl 16, maxSamples - total)) ?: break
            chunks.add(chunk)
            total += chunk.size
        }
        val out = FloatArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        return out
    }
}
