package com.mp3tag.mobile.ui.spectrum

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.spectrum.HintSeverity
import com.mp3tag.mobile.data.spectrum.SpectrumAnalyzer
import com.mp3tag.mobile.data.spectrum.SpectrumHint
import com.mp3tag.mobile.data.spectrum.SpectrumReport
import com.mp3tag.mobile.ui.common.EmptyState
import com.mp3tag.mobile.ui.filemanager.FileManagerScreen
import com.mp3tag.mobile.ui.filemanager.FileManagerViewModel
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectrumScreen(
    onBack: () -> Unit,
    onEditAudioTags: (List<String>) -> Unit = {},
    initialFilePath: String? = null,
    fileManagerViewModel: FileManagerViewModel = viewModel(),
    viewModel: SpectrumViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var browsingFiles by rememberSaveable { mutableStateOf(initialFilePath == null) }

    LaunchedEffect(initialFilePath) {
        if (initialFilePath != null && state.filePath != initialFilePath) {
            browsingFiles = false
            viewModel.analyze(initialFilePath)
        }
    }

    if (browsingFiles) {
        FileManagerScreen(
            onBackHome = onBack,
            onEditAudioTags = onEditAudioTags,
            onAnalyzeSpectrum = { path ->
                browsingFiles = false
                viewModel.analyze(path)
            },
            pickerModeForSpectrum = true,
            viewModel = fileManagerViewModel,
        )
        return
    }

    val launchPicker = { browsingFiles = true }
    val handleBackFromReport = {
        if (initialFilePath != null) {
            onBack()
        } else {
            browsingFiles = true
        }
    }

    BackHandler { handleBackFromReport() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spectrum analysis") },
                navigationIcon = {
                    IconButton(onClick = handleBackFromReport) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = launchPicker, enabled = !state.loading) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Choose audio file")
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Home, contentDescription = "Back to Home")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.fileName == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                ) {
                    EmptyState(
                        icon = Icons.Filled.GraphicEq,
                        title = "Check a file's real frequency content",
                        subtitle = "Shows a spectrogram and the top of the sound, and warns if the file looks " +
                            "upsampled or transcoded from a lossy source.",
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = launchPicker) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose audio file")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(
                            text = state.fileName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                        )
                        state.filePath?.let { path ->
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    when {
                        state.loading -> Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Decoding and analysing…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        state.error != null -> Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = state.error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }

                        state.report != null && state.spectrogram != null -> ReportContent(
                            report = state.report!!,
                            spectrogram = state.spectrogram!!,
                        )
                    }

                    OutlinedButton(
                        onClick = launchPicker,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Analyse another file")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportContent(
    report: SpectrumReport,
    spectrogram: androidx.compose.ui.graphics.ImageBitmap,
) {
    SummaryCard(report)

    SectionTitle("Spectrogram")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Image(
                bitmap = spectrogram,
                contentDescription = "Spectrogram of the analysed section",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("0 Hz", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(
                    "${formatKhz(report.sampleRate / 2.0)} (Nyquist)",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = "Time runs left to right across the analysed section. Brighter means louder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    SectionTitle("Average spectrum")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            SpectrumCurve(report)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("20 Hz", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("1 kHz", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(
                    formatKhz(report.sampleRate / 2.0),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = "Log frequency scale. The dashed line marks the detected top of the sound.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    SectionTitle("What this suggests")
    report.hints.forEach { hint -> HintCard(hint) }

    Text(
        text = "Based on ${formatSeconds(report.analyzedSeconds)} from the middle of the file. " +
            "Hints are estimates: check suspicious results against the original source.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SummaryCard(report: SpectrumReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = report.cutoffHz?.let { "Top of sound: ${formatKhz(it)}" } ?: "Full bandwidth",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${report.codecLabel} · ${formatRate(report.sampleRate)} · " +
                    "Nyquist ${formatKhz(report.sampleRate / 2.0)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.bitDepth?.let { bits ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Bit depth: " + when {
                        bits.effectiveBits != null && bits.containerBits != null ->
                            "${bits.effectiveBits}-bit effective in a ${bits.containerBits}-bit file"
                        bits.effectiveBits != null -> "${bits.effectiveBits}-bit effective"
                        bits.containerBits != null -> "${bits.containerBits}-bit file"
                        else -> "unknown"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun HintCard(hint: SpectrumHint) {
    val warning = hint.severity == HintSeverity.WARNING
    val container = if (warning) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (warning) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(
                imageVector = if (warning) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = if (warning) "Warning" else "Info",
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hint.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(hint.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SpectrumCurve(report: SpectrumReport) {
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nyquist = report.sampleRate / 2.0
    val top = report.referenceDb + 10.0
    val bottom = report.referenceDb - 90.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val w = size.width
        val h = size.height
        fun xFor(hz: Double): Float = (w * (ln(hz / 20.0) / ln(nyquist / 20.0))).toFloat()
        fun yFor(db: Double): Float = (h * (1.0 - (db - bottom) / (top - bottom))).toFloat().coerceIn(0f, h)

        listOf(1_000.0, 10_000.0).forEach { hz ->
            val x = xFor(hz)
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }

        val path = Path()
        val points = 400
        val bins = report.curveDb.size
        for (i in 0..points) {
            val hz = 20.0 * (nyquist / 20.0).pow(i.toDouble() / points)
            val bin = (hz * SpectrumAnalyzer.FFT_SIZE / report.sampleRate).toInt().coerceIn(1, bins - 1)
            val x = xFor(hz)
            val y = yFor(report.curveDb[bin].toDouble())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        report.cutoffHz?.let { cutoff ->
            val x = xFor(cutoff)
            drawLine(
                color = markerColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }
    }
}

private fun formatKhz(hz: Double): String =
    String.format(java.util.Locale.US, "%.1f kHz", hz / 1000.0)

private fun formatRate(hz: Int): String =
    String.format(java.util.Locale.US, "%.1f kHz", hz / 1000.0)

private fun formatSeconds(seconds: Double): String =
    if (seconds >= 60) String.format(java.util.Locale.US, "%.1f min", seconds / 60) else "${seconds.toInt()} s"
