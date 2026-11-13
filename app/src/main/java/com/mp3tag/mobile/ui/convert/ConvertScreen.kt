package com.mp3tag.mobile.ui.convert

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.convert.ConvertTarget
import com.mp3tag.mobile.data.convert.FfmpegBridge
import com.mp3tag.mobile.data.convert.SAMPLE_RATE_CHOICES
import com.mp3tag.mobile.data.convert.channelLabel
import com.mp3tag.mobile.data.convert.sampleRateLabel
import com.mp3tag.mobile.data.tag.TagEngine
import com.mp3tag.mobile.ui.browser.BrowserScreen
import com.mp3tag.mobile.ui.browser.BrowserViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: ConvertViewModel = viewModel(),
) {
    val browserVm: BrowserViewModel = viewModel(key = "convert-browser")
    val state by viewModel.state.collectAsState()
    var sessionPaths by rememberSaveable { mutableStateOf<List<String>?>(null) }

    val activePaths = sessionPaths
        ?: state.lastPaths.takeIf { state.running || state.outcome != null }

    if (activePaths == null) {
        BrowserScreen(
            onBackHome = onBack,
            onEditFiles = { paths -> sessionPaths = paths },
            onOpenSettings = onOpenSettings,
            viewModel = browserVm,
            actionLabel = "Convert",
            actionIcon = Icons.Filled.SwapHoriz,
            folderActionLabel = "Convert all audio in this folder",
            hintText = "Tap to select audio files to convert — long-press a file or folder to convert it",
        )
    } else {
        ConvertSetupView(
            paths = activePaths,
            onBack = onBack,
            onBackToSelection = {
                sessionPaths = null
                browserVm.clearSelection()
            },
            onOpenSettings = onOpenSettings,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConvertSetupView(
    paths: List<String>,
    onBack: () -> Unit,
    onBackToSelection: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    viewModel: ConvertViewModel,
) {
    val state by viewModel.state.collectAsState()
    val settings = state.settings
    val running = state.running

    var formatSheet by rememberSaveable { mutableStateOf(false) }
    var bitrateSheet by rememberSaveable { mutableStateOf(false) }
    var rateSheet by rememberSaveable { mutableStateOf(false) }
    var channelSheet by rememberSaveable { mutableStateOf(false) }
    var depthSheet by rememberSaveable { mutableStateOf(false) }
    var compressionSheet by rememberSaveable { mutableStateOf(false) }
    var detailsSheet by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val formats = remember(paths) {
        paths.map { TagEngine.formatFor(File(it)) }.distinct()
    }
    val totalBytes = remember(paths) { paths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) } }
    val ffmpegVersion = remember { FfmpegBridge.ffmpegVersion() }

    BackHandler {
        if (!running) onBackToSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Convert audio") },
                navigationIcon = {
                    IconButton(onClick = { if (!running) onBackToSelection() }, enabled = !running) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to selection")
                    }
                },
                actions = {
                    onOpenSettings?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Home, contentDescription = "Back to Home")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectionSummary(
                fileCount = paths.size,
                formatsLabel = formats.joinToString(" · ") { it.extension.uppercase(Locale.US).ifEmpty { "audio" } },
                totalBytes = totalBytes,
                enabled = !running,
                onChange = onBackToSelection,
            )

            SectionTitle("Output format")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    PickerRow(
                        title = settings.target.label,
                        subtitle = settings.target.description,
                        enabled = !running,
                        onClick = { formatSheet = true },
                    )
                    HorizontalDivider()
                    if (settings.target.lossy) {
                        PickerRow(
                            title = "${settings.bitrateKbps} kbps",
                            subtitle = "Bitrate",
                            enabled = !running,
                            onClick = { bitrateSheet = true },
                        )
                    } else {
                        PickerRow(
                            title = "Lossless",
                            subtitle = when (settings.target) {
                                ConvertTarget.FLAC -> "FLAC compression level ${settings.target.qualityFor(settings.compressionLevel)} (slower, smaller)"
                                ConvertTarget.WAVPACK -> "WavPack compression level ${settings.target.qualityFor(settings.compressionLevel)}"
                                else -> "No quality loss"
                            },
                            enabled = false,
                            onClick = {},
                        )
                    }
                    HorizontalDivider()
                    PickerRow(
                        title = if (settings.target == ConvertTarget.OPUS) "48 kHz (required by Opus)"
                        else sampleRateLabel(settings.sampleRateHz),
                        subtitle = "Sample rate",
                        enabled = !running && settings.target != ConvertTarget.OPUS,
                        onClick = { rateSheet = true },
                    )
                    HorizontalDivider()
                    PickerRow(
                        title = channelLabel(settings.channels),
                        subtitle = "Channels",
                        enabled = !running,
                        onClick = { channelSheet = true },
                    )
                    if (settings.target.supportsBitDepth) {
                        HorizontalDivider()
                        PickerRow(
                            title = "${settings.bitDepth}-bit PCM",
                            subtitle = "Bit depth",
                            enabled = !running,
                            onClick = { depthSheet = true },
                        )
                    }
                }
            }

            SectionTitle("Output location")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    OutlinedTextField(
                        value = settings.outputSubfolder,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(outputSubfolder = it))
                        },
                        label = { Text("Subfolder (optional)") },
                        placeholder = { Text("e.g. Converted") },
                        singleLine = true,
                        enabled = !running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    OutlinedTextField(
                        value = settings.namePattern,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(namePattern = it))
                        },
                        label = { Text("Filename pattern (optional)") },
                        placeholder = { Text("%artist% - %title%") },
                        supportingText = {
                            Text("Blank keeps the original name. Use %title%, %artist%, %album%, %track%… and / for subfolders.")
                        },
                        singleLine = true,
                        enabled = !running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = "Preserve modified date",
                        subtitle = "Copies the source file's timestamp to the output",
                        checked = settings.preserveTimestamps,
                        enabled = !running,
                        onCheckedChange = {
                            viewModel.updateSettings(settings.copy(preserveTimestamps = it))
                        },
                    )
                    if (settings.target == ConvertTarget.FLAC || settings.target == ConvertTarget.WAVPACK) {
                        HorizontalDivider()
                        PickerRow(
                            title = "Compression level ${settings.target.qualityFor(settings.compressionLevel)}",
                            subtitle = "Higher = smaller file, slower encode",
                            enabled = !running,
                            onClick = { compressionSheet = true },
                        )
                    }
                }
            }

            SectionTitle("Metadata")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchRow(
                        title = "Keep tags & cover art",
                        subtitle = "Copies every field present in the source file into the new file",
                        checked = settings.keepTagsAndCover,
                        enabled = !running,
                        onCheckedChange = {
                            viewModel.updateSettings(settings.copy(keepTagsAndCover = it))
                        },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = "Delete original after success",
                        subtitle = "Off by default — the source file is only removed once the new file is written",
                        checked = settings.deleteSourceAfterSuccess,
                        enabled = !running,
                        onCheckedChange = {
                            if (it) confirmDeleteDialog = true
                            else viewModel.updateSettings(settings.copy(deleteSourceAfterSuccess = false))
                        },
                    )
                }
            }

            Text(
                text = "New files are written next to the originals with the target extension. " +
                    "Existing files are never overwritten — a “(converted)” suffix is added when needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            if (running) {
                ProgressCard(
                    progressText = state.progress?.let { progress ->
                        val fileName = progress.fileName.ifBlank { "Preparing…" }
                        "${progress.index + 1} of ${progress.total} · $fileName"
                    } ?: "Preparing…",
                    percent = state.progress?.overallPercent ?: 0,
                    cancelRequested = state.cancelRequested,
                    onCancel = viewModel::cancel,
                )
            } else {
                Button(
                    onClick = { viewModel.start(paths) },
                    enabled = paths.isNotEmpty() && FfmpegBridge.isAvailable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Convert ${paths.size} file${if (paths.size == 1) "" else "s"}")
                }
                if (!FfmpegBridge.isAvailable) {
                    Text(
                        text = "FFmpeg is not available on this device: " +
                            FfmpegBridge.unavailableReason.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            state.outcome?.let { outcome ->
                OutcomeCard(
                    succeeded = outcome.succeeded.size,
                    failed = outcome.failed.size,
                    warnings = outcome.succeeded.count { it.warning != null },
                    cancelled = outcome.cancelled,
                    onShowDetails = { detailsSheet = true },
                )
            }

            Text(
                text = "Powered by FFmpeg $ffmpegVersion (LGPL build)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (formatSheet) {
        ChoiceSheet(
            title = "Output format",
            onDismiss = { formatSheet = false },
        ) {
            ConvertTarget.entries.forEach { target ->
                SheetRow(
                    title = target.label,
                    subtitle = buildString {
                        append(target.description)
                        if (!target.lossy) append(" · ${target.extension.uppercase(Locale.US)}")
                    },
                    selected = target == settings.target,
                    onClick = {
                        viewModel.updateSettings(
                            settings.copy(
                                target = target,
                                bitrateKbps = target.defaultBitrate,
                            ),
                        )
                        formatSheet = false
                    },
                )
            }
        }
    }

    if (bitrateSheet) {
        ChoiceSheet(title = "Bitrate", onDismiss = { bitrateSheet = false }) {
            settings.target.bitratesKbps.forEach { rate ->
                SheetRow(
                    title = "$rate kbps",
                    subtitle = bitrateHint(rate, settings.target),
                    selected = rate == settings.bitrateKbps,
                    onClick = {
                        viewModel.updateSettings(settings.copy(bitrateKbps = rate))
                        bitrateSheet = false
                    },
                )
            }
        }
    }

    if (compressionSheet) {
        ChoiceSheet(title = "Compression level", onDismiss = { compressionSheet = false }) {
            listOf(0, 2, 4, 6, 8, 10, 12).forEach { level ->
                SheetRow(
                    title = if (level == 0) "Encoder default" else "Level $level",
                    subtitle = when {
                        level <= 2 -> "Fastest encode, larger file"
                        level >= 10 -> "Smallest file, slowest encode"
                        else -> "Balanced"
                    },
                    selected = (if (settings.compressionLevel == 0) 0 else settings.compressionLevel) == level,
                    onClick = {
                        viewModel.updateSettings(settings.copy(compressionLevel = level))
                        compressionSheet = false
                    },
                )
            }
        }
    }

    if (rateSheet) {
        ChoiceSheet(title = "Sample rate", onDismiss = { rateSheet = false }) {
            SAMPLE_RATE_CHOICES.forEach { rate ->
                SheetRow(
                    title = sampleRateLabel(rate),
                    subtitle = if (rate == 0) {
                        "Use the source file's rate"
                    } else {
                        "Re-sample with libswresample"
                    },
                    selected = rate == settings.sampleRateHz,
                    onClick = {
                        viewModel.updateSettings(settings.copy(sampleRateHz = rate))
                        rateSheet = false
                    },
                )
            }
        }
    }

    if (channelSheet) {
        ChoiceSheet(title = "Channels", onDismiss = { channelSheet = false }) {
            listOf(0, 1, 2).forEach { value ->
                SheetRow(
                    title = channelLabel(value),
                    subtitle = if (value == 0) "Keep the source channel layout" else "Down/up-mix as needed",
                    selected = value == settings.channels,
                    onClick = {
                        viewModel.updateSettings(settings.copy(channels = value))
                        channelSheet = false
                    },
                )
            }
        }
    }

    if (depthSheet) {
        ChoiceSheet(title = "Bit depth", onDismiss = { depthSheet = false }) {
            listOf(16, 24).forEach { bits ->
                SheetRow(
                    title = "$bits-bit",
                    subtitle = if (bits == 16) "CD quality, smallest files" else "Studio quality, larger files",
                    selected = bits == settings.bitDepth,
                    onClick = {
                        viewModel.updateSettings(settings.copy(bitDepth = bits))
                        depthSheet = false
                    },
                )
            }
        }
    }

    if (detailsSheet) {
        val outcome = state.outcome
        ChoiceSheet(title = "Conversion results", onDismiss = { detailsSheet = false }) {
            if (outcome != null) {
                if (outcome.cancelled) {
                    Text(
                        "Conversion was cancelled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                outcome.results.forEach { result ->
                    val sourceName = File(result.sourcePath).name
                    val ok = result.outputPath != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = if (ok) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sourceName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            result.outputPath?.let { path ->
                                Text(
                                    text = "→ ${File(path).name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            result.warning?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            result.error?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Delete originals?") },
            text = {
                Text(
                    "Each source file will be deleted after its converted copy has been written " +
                        "successfully. Failed conversions never delete anything. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateSettings(settings.copy(deleteSourceAfterSuccess = true))
                    confirmDeleteDialog = false
                }) { Text("Delete originals") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SelectionSummary(
    fileCount: Int,
    formatsLabel: String,
    totalBytes: Long,
    enabled: Boolean,
    onChange: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "$fileCount file${if (fileCount == 1) "" else "s"} selected",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = listOfNotNull(
                        formatsLabel.takeIf { it.isNotBlank() },
                        formatBytes(totalBytes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onChange, enabled = enabled) { Text("Change") }
        }
    }
}

@Composable
private fun ProgressCard(
    progressText: String,
    percent: Int,
    cancelRequested: Boolean,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (cancelRequested) "Cancelling…" else "Converting",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text("$percent%", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = progressText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                enabled = !cancelRequested,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel conversion")
            }
        }
    }
}

@Composable
private fun OutcomeCard(
    succeeded: Int,
    failed: Int,
    warnings: Int,
    cancelled: Boolean,
    onShowDetails: () -> Unit,
) {
    val container = if (failed > 0) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = buildString {
                    if (cancelled) append("Conversion cancelled — ")
                    append("$succeeded converted")
                    if (failed > 0) append(" · $failed failed")
                    if (warnings > 0) append(" · $warnings with tag warnings")
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onShowDetails) { Text("Show details") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun SheetRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(background, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun bitrateHint(rate: Int, target: ConvertTarget): String = when {
    target == ConvertTarget.MP3 && rate >= 320 -> "Maximum quality, large files"
    target == ConvertTarget.MP3 && rate <= 128 -> "Small files, audible loss on good gear"
    target == ConvertTarget.OPUS -> "Opus is efficient — ${rate}k sounds like ~${rate * 2 / 3}k MP3"
    rate >= 256 -> "High quality"
    rate >= 192 -> "Good quality"
    else -> "Compact"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
