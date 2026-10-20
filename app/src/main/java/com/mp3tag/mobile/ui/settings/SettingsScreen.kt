package com.mp3tag.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mp3tag.mobile.data.prefs.DesignLanguage
import com.mp3tag.mobile.data.prefs.Id3Version
import com.mp3tag.mobile.data.prefs.PerformanceMode
import com.mp3tag.mobile.data.prefs.ThemeMode
import com.mp3tag.mobile.ui.common.RadioRow
import com.mp3tag.mobile.ui.common.SectionHeader
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo
import com.mp3tag.mobile.util.DeviceProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    id3Version: Id3Version,
    onId3VersionChange: (Id3Version) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    designLanguage: DesignLanguage = DesignLanguage.MATERIAL_3_EXPRESSIVE,
    onDesignLanguageChange: (DesignLanguage) -> Unit = {},
    performanceMode: PerformanceMode = PerformanceMode.AUTO,
    onPerformanceModeChange: (PerformanceMode) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val windowInfo = rememberAdaptiveWindowInfo()
    val capabilities = remember(context, performanceMode) {
        DeviceProfile.get(context, performanceMode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (windowInfo.useTwoPane) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                ThemeSettingsBlock(
                                    themeMode = themeMode,
                                    onThemeChange = onThemeChange,
                                    designLanguage = designLanguage,
                                    onDesignLanguageChange = onDesignLanguageChange,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                TagSettingsBlock(
                                    id3Version = id3Version,
                                    onId3VersionChange = onId3VersionChange,
                                )
                                PerformanceSettingsBlock(
                                    performanceMode = performanceMode,
                                    onPerformanceModeChange = onPerformanceModeChange,
                                    capabilitiesText = "Detected: ${capabilities.detectedTier.label} • " +
                                        "${capabilities.cpuCores} cores, ~${capabilities.totalRamMb} MB RAM " +
                                        "(active: ${capabilities.ioParallelism} I/O threads, " +
                                        "${capabilities.thumbnailMaxPx}px thumbnails)",
                                )
                                AboutSettingsBlock()
                            }
                        }
                    }
                } else {
                    item {
                        ThemeSettingsBlock(
                            themeMode = themeMode,
                            onThemeChange = onThemeChange,
                            designLanguage = designLanguage,
                            onDesignLanguageChange = onDesignLanguageChange,
                        )
                    }
                    item {
                        TagSettingsBlock(
                            id3Version = id3Version,
                            onId3VersionChange = onId3VersionChange,
                        )
                    }
                    item {
                        PerformanceSettingsBlock(
                            performanceMode = performanceMode,
                            onPerformanceModeChange = onPerformanceModeChange,
                            capabilitiesText = "Detected: ${capabilities.detectedTier.label} • " +
                                "${capabilities.cpuCores} cores, ~${capabilities.totalRamMb} MB RAM " +
                                "(active: ${capabilities.ioParallelism} I/O threads, " +
                                "${capabilities.thumbnailMaxPx}px thumbnails)",
                        )
                    }
                    item {
                        AboutSettingsBlock()
                    }
                }
                item {
                    Spacer(Modifier.navigationBarsPadding().height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsBlock(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    designLanguage: DesignLanguage,
    onDesignLanguageChange: (DesignLanguage) -> Unit,
) {
    SectionHeader("Theme")
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Screen mode",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = mode.label,
                    selected = themeMode == mode,
                    onClick = { onThemeChange(mode) },
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Design language",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            DesignLanguage.entries.forEach { language ->
                RadioRow(
                    label = language.label,
                    subtitle = language.description,
                    selected = designLanguage == language,
                    onClick = { onDesignLanguageChange(language) },
                )
            }
        }
    }
}

@Composable
private fun TagSettingsBlock(
    id3Version: Id3Version,
    onId3VersionChange: (Id3Version) -> Unit,
) {
    SectionHeader("MP3 & DSD (DSF / DFF) tag version")
    Column {
        Id3Version.entries.forEach { version ->
            RadioRow(
                label = version.label,
                selected = id3Version == version,
                onClick = { onId3VersionChange(version) },
            )
        }
    }
    Text(
        text = "Applies when saving MP3, DSF, and DFF (DSDIFF) files.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )

    SectionHeader("WAV & AIFF tags")
    Text(
        text = "WAV files are written with both an ID3v2 chunk and a RIFF INFO chunk (like MP3Tag's default). " +
            "AIFF files are written with an embedded ID3v2 chunk.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun PerformanceSettingsBlock(
    performanceMode: PerformanceMode,
    onPerformanceModeChange: (PerformanceMode) -> Unit,
    capabilitiesText: String,
) {
    SectionHeader("Device & performance")
    Column {
        PerformanceMode.entries.forEach { mode ->
            RadioRow(
                label = mode.label,
                selected = performanceMode == mode,
                onClick = { onPerformanceModeChange(mode) },
            )
        }
    }
    Text(
        text = capabilitiesText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun AboutSettingsBlock() {
    SectionHeader("About")
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("MP3Tag Mobile 0.4.0", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Personal-use tag editor & file manager inspired by MP3Tag for Windows.\n\n" +
                    "Supported now: Home workspace grid, Tag Editor for MP3 (ID3v1/v2.3/v2.4), FLAC, " +
                    "M4A/MP4/M4B, Ogg Vorbis, Opus, WAV (RIFF INFO + ID3), AIFF/AIF/AIFC, WMA/ASF, " +
                    "APE (Monkey's Audio), WavPack (.wv), DSF & DFF (DSD / DSDIFF), tags from filename, " +
                    "rename from tags, batch regex & case transforms, full File Manager, " +
                    "multi-design-language Theme system (Material 3 Expressive, Material You, " +
                    "Liquid Glass, Halo), and adaptive phone/tablet layouts.\n\n" +
                    "Roadmap: Automate workflows, MusicBrainz & Discogs lookup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
