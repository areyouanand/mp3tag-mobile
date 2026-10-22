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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpectrumUiState(
    val fileName: String? = null,
    val loading: Boolean = false,
    val report: SpectrumReport? = null,
    val spectrogram: ImageBitmap? = null,
    val error: String? = null,
)

class SpectrumViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SpectrumUiState())
    val state: StateFlow<SpectrumUiState> = _state.asStateFlow()

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
        val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "wave", "aif", "aiff", "aifc")
        val UNSUPPORTED_EXTENSIONS = setOf("dsf", "dff", "ape", "wv")
    }
}
