package com.mp3tag.mobile.ui.spectrum

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.mobile.data.spectrum.ContainerProbe
import com.mp3tag.mobile.data.spectrum.PcmDecoder
import com.mp3tag.mobile.data.spectrum.SpectrumAnalyzer
import com.mp3tag.mobile.data.spectrum.SpectrumReport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpectrumUiState(
    val filePath: String? = null,
    val fileName: String? = null,
    val loading: Boolean = false,
    val report: SpectrumReport? = null,
    val spectrogram: ImageBitmap? = null,
    val error: String? = null,
    val windowSeconds: Double = 60.0,
    val diagnostics: List<String> = emptyList(),
)

class SpectrumViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SpectrumUiState())
    val state: StateFlow<SpectrumUiState> = _state.asStateFlow()

    /** Small in-memory cache keyed by path + mtime + window. */
    private data class Cached(
        val modified: Long,
        val size: Long,
        val windowSeconds: Double,
        val report: SpectrumReport,
        val bitmap: ImageBitmap,
        val diagnostics: List<String>,
    )

    private val cache = HashMap<String, Cached>()

    fun analyze(path: String, windowSeconds: Double = _state.value.windowSeconds) {
        analyze(File(path), windowSeconds)
    }

    fun analyze(file: File, windowSeconds: Double = _state.value.windowSeconds) {
        val name = file.name.ifBlank { "audio file" }
        val path = file.absolutePath
        val cached = cache[path]
        if (cached != null && cached.modified == file.lastModified() &&
            cached.size == file.length() && cached.windowSeconds == windowSeconds
        ) {
            _state.value = SpectrumUiState(
                filePath = path,
                fileName = name,
                report = cached.report,
                spectrogram = cached.bitmap,
                windowSeconds = windowSeconds,
                diagnostics = cached.diagnostics,
            )
            return
        }
        _state.value = SpectrumUiState(filePath = path, fileName = name, loading = true, windowSeconds = windowSeconds)
        val extension = file.extension.lowercase()
        val lossless = extension in LOSSLESS_EXTENSIONS

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    if (!file.exists() || !file.canRead()) {
                        error("This file could not be read: $name")
                    }
                    val audio = PcmDecoder.decode(file, windowSeconds)
                    val containerBits = (if (lossless) {
                        ContainerProbe.bitsPerSample(file, extension)
                    } else {
                        null
                    }) ?: audio.sourceBits
                    val report = SpectrumAnalyzer.analyze(audio, lossless, containerBits)
                    val diagnostics = diagnostics(audio)
                    val bitmap = Bitmap.createBitmap(
                        report.spectrogramPixels,
                        report.spectrogramWidth,
                        report.spectrogramHeight,
                        Bitmap.Config.ARGB_8888,
                    )
                    val image = bitmap.asImageBitmap()
                    cache[path] = Cached(file.lastModified(), file.length(), windowSeconds, report, image, diagnostics)
                    Triple(report, image, diagnostics)
                }
            }
            _state.value = result.fold(
                onSuccess = { (report, image, diagnostics) ->
                    SpectrumUiState(
                        filePath = path,
                        fileName = name,
                        report = report,
                        spectrogram = image,
                        windowSeconds = windowSeconds,
                        diagnostics = diagnostics,
                    )
                },
                onFailure = { error ->
                    SpectrumUiState(
                        filePath = path,
                        fileName = name,
                        windowSeconds = windowSeconds,
                        error = error.message ?: "This file could not be analysed.",
                    )
                },
            )
        }
    }

    /** Clipping / DC offset / crest factor, computed on the analysed window. */
    private fun diagnostics(audio: com.mp3tag.mobile.data.spectrum.DecodedAudio): List<String> {
        if (audio.mono.isEmpty()) return emptyList()
        var clipped = 0
        var peak = 0.0
        var sum = 0.0
        var sumSquares = 0.0
        for (sample in audio.mono) {
            val value = sample.toDouble()
            if (kotlin.math.abs(value) >= 0.9999) clipped++
            if (kotlin.math.abs(value) > peak) peak = kotlin.math.abs(value)
            sum += value
            sumSquares += value * value
        }
        val count = audio.mono.size
        val dc = sum / count
        val rms = kotlin.math.sqrt(sumSquares / count)
        val peakDb = 20.0 * kotlin.math.log10(peak.coerceAtLeast(1e-9))
        val crestDb = if (rms > 1e-9) 20.0 * kotlin.math.log10(peak / rms) else 0.0
        return buildList {
            add("Peak ${"%.1f".format(peakDb)} dBFS · crest ${"%.1f".format(crestDb)} dB")
            add("DC offset ${"%.4f".format(dc)} · ${if (kotlin.math.abs(dc) > 0.002) "⚠ noticeable" else "ok"}")
            add(if (clipped > 0) "⚠ $clipped clipped sample(s) in the analysed window" else "No clipping detected")
        }
    }

    fun analyze(uri: Uri) {
        val name = displayName(uri)
        _state.value = SpectrumUiState(fileName = name, loading = true)
        val extension = name.substringAfterLast('.', "").lowercase()
        val lossless = extension in LOSSLESS_EXTENSIONS
        val unsupported = extension in UNSUPPORTED_EXTENSIONS

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    if (unsupported) {
                        error("DSD, Monkey's Audio and WavPack files can't be analysed on this device yet.")
                    }
                    val audio = PcmDecoder.decode(getApplication(), uri, WINDOW_SECONDS)
                    val containerBits = if (lossless) {
                        ContainerProbe.bitsPerSample(getApplication(), uri, extension)
                    } else {
                        null
                    }
                    val report = SpectrumAnalyzer.analyze(audio, lossless, containerBits)
                    val bitmap = Bitmap.createBitmap(
                        report.spectrogramPixels,
                        report.spectrogramWidth,
                        report.spectrogramHeight,
                        Bitmap.Config.ARGB_8888,
                    )
                    report to bitmap.asImageBitmap()
                }
            }
            _state.value = result.fold(
                onSuccess = { (report, image) ->
                    SpectrumUiState(fileName = name, report = report, spectrogram = image)
                },
                onFailure = { error ->
                    SpectrumUiState(
                        fileName = name,
                        error = error.message ?: "This file could not be analysed.",
                    )
                },
            )
        }
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "audio file"
    }

    private companion object {
        const val WINDOW_SECONDS = 60.0
        val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "wave", "aif", "aiff", "aifc", "ape", "wv", "dsf", "dff")
        val UNSUPPORTED_EXTENSIONS = setOf("dsf", "dff", "ape", "wv")
    }
}
