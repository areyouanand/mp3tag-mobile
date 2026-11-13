package com.mp3tag.mobile.data.convert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mp3tag.mobile.data.audio.TestAudioFactory
import com.mp3tag.mobile.data.prefs.Id3Version
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end checks for the bundled FFmpeg transcoder. Run with:
 *   ./gradlew connectedDebugAndroidTest
 *
 * Every test converts a generated source file and verifies the container magic
 * bytes, so a broken JNI build fails loudly instead of silently producing files.
 */
@RunWith(AndroidJUnit4::class)
class AudioConverterInstrumentedTest {

    private lateinit var dir: File

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "convert-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun wavToMp3() = convert("tone.wav", ConvertTarget.MP3, "ID3", 0)

    @Test
    fun wavToFlac() = convert("tone.wav", ConvertTarget.FLAC, "fLaC", 0)

    @Test
    fun wavToM4a() = convert("tone.wav", ConvertTarget.M4A, "ftyp", 4)

    @Test
    fun wavToOpus() = convert("tone.wav", ConvertTarget.OPUS, "OggS", 0)

    @Test
    fun wavToWav24Bit() = convert("tone.wav", ConvertTarget.WAV, "RIFF", 0, bitDepth = 24)

    @Test
    fun dffToMp3UsesCustomDsdiffReader() {
        val source = File(dir, "silence.dff")
        TestAudioFactory.writeDff(source)
        val result = runConversion(source, ConvertTarget.MP3)
        assertTrue("conversion failed: ${result.error}", result.outputPath != null)
        assertMagic(File(result.outputPath!!), "ID3", 0)
    }

    @Test
    fun tagsSurviveConversion() {
        val source = taggedWav("tagged.wav")

        val result = runConversion(source, ConvertTarget.FLAC)
        assertTrue("conversion failed: ${result.error}", result.outputPath != null)
        val snapshot = com.mp3tag.mobile.data.tag.TagEngine.read(File(result.outputPath!!))
        assertEquals("warning=${result.warning}", "Test Tone", snapshot.fields[org.jaudiotagger.tag.FieldKey.TITLE])
        assertEquals("warning=${result.warning}", "Unit Test", snapshot.fields[org.jaudiotagger.tag.FieldKey.ARTIST])
        assertEquals("warning=${result.warning}", "Conversion Suite", snapshot.fields[org.jaudiotagger.tag.FieldKey.ALBUM])
    }

    @Test
    fun coverSurvivesConversionToMp3() {
        val source = taggedWav("tagged-cover.wav")

        val result = runConversion(source, ConvertTarget.MP3)
        assertTrue("conversion failed: ${result.error}", result.outputPath != null)
        val outFile = File(result.outputPath!!)
        val snapshot = com.mp3tag.mobile.data.tag.TagEngine.read(outFile)
        assertEquals("warning=${result.warning}", "Test Tone", snapshot.fields[org.jaudiotagger.tag.FieldKey.TITLE])
        assertTrue("cover art was not copied (warning=${result.warning})", snapshot.cover != null)
    }

    private fun taggedWav(name: String): File {
        val source = File(dir, name)
        TestAudioFactory.writeWav(source)
        val cover = com.mp3tag.mobile.data.tag.CoverImage(onePixelPng(), "image/png")
        com.mp3tag.mobile.data.tag.TagEngine.write(
            source,
            com.mp3tag.mobile.data.tag.WriteRequest(
                fieldValues = mapOf(
                    org.jaudiotagger.tag.FieldKey.TITLE to "Test Tone",
                    org.jaudiotagger.tag.FieldKey.ARTIST to "Unit Test",
                    org.jaudiotagger.tag.FieldKey.ALBUM to "Conversion Suite",
                ),
                coverAction = com.mp3tag.mobile.data.tag.CoverAction.REPLACE,
                newCover = cover,
            ),
            com.mp3tag.mobile.data.tag.WriteOptions(Id3Version.V2_3),
        )
        val written = com.mp3tag.mobile.data.tag.TagEngine.read(source)
        assertEquals("source tags were not written", "Test Tone", written.fields[org.jaudiotagger.tag.FieldKey.TITLE])
        return source
    }

    private fun onePixelPng(): ByteArray = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        android.util.Base64.DEFAULT,
    )

    private fun convert(
        name: String,
        target: ConvertTarget,
        magic: String,
        offset: Int,
        bitDepth: Int = 16,
    ) {
        val source = File(dir, name)
        TestAudioFactory.writeWav(source)
        val result = runConversion(source, target, bitDepth)
        assertTrue("conversion failed: ${result.error}", result.outputPath != null)
        val out = File(result.outputPath!!)
        assertTrue("empty output", out.length() > 512)
        assertMagic(out, magic, offset)
    }

    private fun runConversion(
        source: File,
        target: ConvertTarget,
        bitDepth: Int = 16,
    ): ConversionFileResult {
        val outcome = runBlocking {
            AudioConverter.convert(
                paths = listOf(source.absolutePath),
                settings = ConvertSettings(target = target, bitDepth = bitDepth).normalized(),
                id3Version = Id3Version.V2_3,
                onProgress = {},
            )
        }
        assertEquals(1, outcome.results.size)
        return outcome.results.single()
    }

    private fun assertMagic(file: File, expected: String, offset: Int) {
        val bytes = file.inputStream().use { stream ->
            val buffer = ByteArray(offset + expected.length)
            stream.read(buffer)
            buffer
        }
        val actual = String(bytes, offset, expected.length, Charsets.US_ASCII)
        assertEquals("container magic of ${file.name}", expected, actual)
    }
}
