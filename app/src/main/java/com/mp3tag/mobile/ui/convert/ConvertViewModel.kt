package com.mp3tag.mobile.ui.convert

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.mobile.data.convert.AudioConverter
import com.mp3tag.mobile.data.convert.ConversionOutcome
import com.mp3tag.mobile.data.convert.ConversionProgress
import com.mp3tag.mobile.data.convert.ConvertSettings
import com.mp3tag.mobile.data.convert.ConvertTarget
import com.mp3tag.mobile.data.convert.FfmpegBridge
import com.mp3tag.mobile.data.prefs.AppPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds conversion settings + the running batch. Scoped to the activity, so a
 * conversion keeps going while the user navigates away (progress is restored when
 * they come back) and survives rotation.
 */
class ConvertViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val settings: ConvertSettings = ConvertSettings(),
        val running: Boolean = false,
        val progress: ConversionProgress? = null,
        val outcome: ConversionOutcome? = null,
        val totalFiles: Int = 0,
        val cancelRequested: Boolean = false,
        val lastPaths: List<String> = emptyList(),
    )

    private val prefs = AppPrefs(application)

    private val _state = MutableStateFlow(UiState(settings = loadSettings()))
    val state = _state.asStateFlow()

    private var job: Job? = null

    private fun loadSettings(): ConvertSettings {
        val target = ConvertTarget.entries.firstOrNull { it.name == prefs.convertTarget }
            ?: ConvertTarget.MP3
        return ConvertSettings(
            target = target,
            bitrateKbps = prefs.convertBitrate.takeIf { it > 0 } ?: target.defaultBitrate,
            sampleRateHz = prefs.convertSampleRate,
            channels = prefs.convertChannels,
            bitDepth = prefs.convertBitDepth,
            keepTagsAndCover = prefs.convertKeepTags,
            deleteSourceAfterSuccess = prefs.convertDeleteSource,
            outputSubfolder = prefs.convertOutputSubfolder,
            namePattern = prefs.convertNamePattern,
            preserveTimestamps = prefs.convertPreserveTimestamps,
            compressionLevel = prefs.convertCompressionLevel,
        ).normalized()
    }

    fun updateSettings(settings: ConvertSettings) {
        val normalized = settings.normalized()
        _state.value = _state.value.copy(settings = normalized)
        prefs.convertTarget = normalized.target.name
        prefs.convertBitrate = normalized.bitrateKbps
        prefs.convertSampleRate = normalized.sampleRateHz
        prefs.convertChannels = normalized.channels
        prefs.convertBitDepth = normalized.bitDepth
        prefs.convertKeepTags = normalized.keepTagsAndCover
        prefs.convertDeleteSource = normalized.deleteSourceAfterSuccess
        prefs.convertOutputSubfolder = normalized.outputSubfolder
        prefs.convertNamePattern = normalized.namePattern
        prefs.convertPreserveTimestamps = normalized.preserveTimestamps
        prefs.convertCompressionLevel = normalized.compressionLevel
    }

    fun start(paths: List<String>) {
        if (paths.isEmpty() || _state.value.running) return
        val settings = _state.value.settings
        _state.value = _state.value.copy(
            running = true,
            progress = ConversionProgress(0, paths.size, "", 0),
            outcome = null,
            totalFiles = paths.size,
            cancelRequested = false,
            lastPaths = paths,
        )
        job = viewModelScope.launch {
            val outcome = AudioConverter.convert(
                paths = paths,
                settings = settings,
                id3Version = prefs.id3Version,
                onProgress = { progress ->
                    _state.value = _state.value.copy(progress = progress)
                },
                shouldCancel = { _state.value.cancelRequested },
            )
            _state.value = _state.value.copy(
                running = false,
                outcome = outcome,
                cancelRequested = false,
            )
        }
    }

    fun cancel() {
        _state.value = _state.value.copy(cancelRequested = true)
        FfmpegBridge.cancel()
    }

    fun clearOutcome() {
        _state.value = _state.value.copy(outcome = null, progress = null)
    }

    /** Keeps the conversion going if the screen is left; nothing to cancel here. */
    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
