package com.mp3tag.mobile.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.prefs.DesignLanguage
import com.mp3tag.mobile.data.prefs.Id3Version
import com.mp3tag.mobile.data.prefs.PerformanceMode
import com.mp3tag.mobile.data.prefs.ThemeMode
import com.mp3tag.mobile.data.prefs.UpdateChannel
import com.mp3tag.mobile.data.update.UpdateCheckResult
import com.mp3tag.mobile.data.update.UpdateChecker
import com.mp3tag.mobile.ui.common.RadioRow
import com.mp3tag.mobile.ui.common.SectionHeader
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo
import com.mp3tag.mobile.ui.editor.EditorDraftStore
import com.mp3tag.mobile.util.DeviceProfile
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val draftStore = remember { EditorDraftStore(context) }
    val windowInfo = rememberAdaptiveWindowInfo()
    val capabilities = remember(context, performanceMode) {
        DeviceProfile.get(context, performanceMode)
    }

    var showHiddenFiles by rememberSaveable { mutableStateOf(prefs.showHiddenFiles) }
    var writeOpusR128ByDefault by rememberSaveable { mutableStateOf(prefs.writeOpusR128ByDefault) }
    var skipExistingRgByDefault by rememberSaveable { mutableStateOf(prefs.skipExistingReplayGain) }
    var updateChannel by rememberSaveable { mutableStateOf(prefs.updateChannel) }

    var recentCount by remember { mutableIntStateOf(prefs.recentFolders.size) }
    var savedPatternCount by remember { mutableIntStateOf(prefs.savedPatterns.size) }
    var draftCount by remember { mutableIntStateOf(draftStore.draftCount()) }
    var dataStatusMessage by remember { mutableStateOf<String?>(null) }

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    val triggerUpdateCheck: (UpdateChannel) -> Unit = { channel ->
        if (!checkingUpdate) {
            checkingUpdate = true
            updateError = null
            scope.launch {
                val res = UpdateChecker.checkForUpdates(channel)
                checkingUpdate = false
                res.fold(
                    onSuccess = { result ->
                        updateResult = result
                        updateError = null
                    },
                    onFailure = { err ->
                        updateError = err.message ?: "Could not check for updates."
                    },
                )
            }
        }
    }

    val capabilitiesText = "Detected: ${capabilities.detectedTier.label} • " +
        "${capabilities.cpuCores} cores, ~${capabilities.totalRamMb} MB RAM " +
        "(active: ${capabilities.ioParallelism} I/O threads, " +
        "${capabilities.thumbnailMaxPx}px thumbnails)"

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
                                TagAndAudioSettingsBlock(
                                    id3Version = id3Version,
                                    onId3VersionChange = onId3VersionChange,
                                    writeOpusR128ByDefault = writeOpusR128ByDefault,
                                    onWriteOpusR128Change = { enabled ->
                                        writeOpusR128ByDefault = enabled
                                        prefs.writeOpusR128ByDefault = enabled
                                    },
                                    skipExistingRgByDefault = skipExistingRgByDefault,
                                    onSkipExistingRgChange = { enabled ->
                                        skipExistingRgByDefault = enabled
                                        prefs.skipExistingReplayGain = enabled
                                    },
                                )
                                FileAndCacheSettingsBlock(
                                    showHiddenFiles = showHiddenFiles,
                                    onShowHiddenChange = { enabled ->
                                        showHiddenFiles = enabled
                                        prefs.showHiddenFiles = enabled
                                    },
                                    recentCount = recentCount,
                                    onClearRecents = {
                                        prefs.clearRecentFolders()
                                        recentCount = 0
                                        dataStatusMessage = "Cleared recent folders"
                                    },
                                    savedPatternCount = savedPatternCount,
                                    onClearPatterns = {
                                        prefs.clearSavedPatterns()
                                        savedPatternCount = 0
                                        dataStatusMessage = "Cleared saved custom patterns"
                                    },
                                    draftCount = draftCount,
                                    onClearDrafts = {
                                        draftStore.clearAll()
                                        draftCount = 0
                                        dataStatusMessage = "Cleared unsaved editor drafts"
                                    },
                                    statusMessage = dataStatusMessage,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                PerformanceSettingsBlock(
                                    performanceMode = performanceMode,
                                    onPerformanceModeChange = onPerformanceModeChange,
                                    capabilitiesText = capabilitiesText,
                                )
                                UpdatesSettingsBlock(
                                    updateChannel = updateChannel,
                                    onUpdateChannelChange = { channel ->
                                        updateChannel = channel
                                        prefs.updateChannel = channel
                                        updateResult = null
                                        updateError = null
                                    },
                                    checking = checkingUpdate,
                                    result = updateResult,
                                    error = updateError,
                                    onCheckUpdates = { triggerUpdateCheck(updateChannel) },
                                    onOpenUrl = { url -> openExternalUrl(context, url) },
                                )
                                DeveloperAndAboutBlock(
                                    onOpenUrl = { url -> openExternalUrl(context, url) },
                                )
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
                        TagAndAudioSettingsBlock(
                            id3Version = id3Version,
                            onId3VersionChange = onId3VersionChange,
                            writeOpusR128ByDefault = writeOpusR128ByDefault,
                            onWriteOpusR128Change = { enabled ->
                                writeOpusR128ByDefault = enabled
                                prefs.writeOpusR128ByDefault = enabled
                            },
                            skipExistingRgByDefault = skipExistingRgByDefault,
                            onSkipExistingRgChange = { enabled ->
                                skipExistingRgByDefault = enabled
                                prefs.skipExistingReplayGain = enabled
                            },
                        )
                    }
                    item {
                        FileAndCacheSettingsBlock(
                            showHiddenFiles = showHiddenFiles,
                            onShowHiddenChange = { enabled ->
                                showHiddenFiles = enabled
                                prefs.showHiddenFiles = enabled
                            },
                            recentCount = recentCount,
                            onClearRecents = {
                                prefs.clearRecentFolders()
                                recentCount = 0
                                dataStatusMessage = "Cleared recent folders"
                            },
                            savedPatternCount = savedPatternCount,
                            onClearPatterns = {
                                prefs.clearSavedPatterns()
                                savedPatternCount = 0
                                dataStatusMessage = "Cleared saved custom patterns"
                            },
                            draftCount = draftCount,
                            onClearDrafts = {
                                draftStore.clearAll()
                                draftCount = 0
                                dataStatusMessage = "Cleared unsaved editor drafts"
                            },
                            statusMessage = dataStatusMessage,
                        )
                    }
                    item {
                        PerformanceSettingsBlock(
                            performanceMode = performanceMode,
                            onPerformanceModeChange = onPerformanceModeChange,
                            capabilitiesText = capabilitiesText,
                        )
                    }
                    item {
                        UpdatesSettingsBlock(
                            updateChannel = updateChannel,
                            onUpdateChannelChange = { channel ->
                                updateChannel = channel
                                prefs.updateChannel = channel
                                updateResult = null
                                updateError = null
                            },
                            checking = checkingUpdate,
                            result = updateResult,
                            error = updateError,
                            onCheckUpdates = { triggerUpdateCheck(updateChannel) },
                            onOpenUrl = { url -> openExternalUrl(context, url) },
                        )
                    }
                    item {
                        DeveloperAndAboutBlock(
                            onOpenUrl = { url -> openExternalUrl(context, url) },
                        )
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
private fun TagAndAudioSettingsBlock(
    id3Version: Id3Version,
    onId3VersionChange: (Id3Version) -> Unit,
    writeOpusR128ByDefault: Boolean,
    onWriteOpusR128Change: (Boolean) -> Unit,
    skipExistingRgByDefault: Boolean,
    onSkipExistingRgChange: (Boolean) -> Unit,
) {
    SectionHeader("Tagging & audio analysis")
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "MP3 & DSD (DSF / DFF) ID3v2 version",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Id3Version.entries.forEach { version ->
                RadioRow(
                    label = version.label,
                    selected = id3Version == version,
                    onClick = { onId3VersionChange(version) },
                )
            }
            Text(
                text = "Applies when saving MP3, DSF, and DFF (DSDIFF) files. WAV files are written with both ID3v2 and RIFF INFO chunks; AIFF files use embedded ID3v2.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text(
                text = "ReplayGain defaults",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text("Write Opus R128 gain by default") },
                supportingContent = {
                    Text(
                        "Automatically enable R128_TRACK_GAIN / R128_ALBUM_GAIN (Q7.8 at -23 LUFS) when .opus files are selected",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Checkbox(checked = writeOpusR128ByDefault, onCheckedChange = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onWriteOpusR128Change(!writeOpusR128ByDefault) },
            )
            ListItem(
                headlineContent = { Text("Skip already-tagged files by default") },
                supportingContent = {
                    Text(
                        "Skip tracks that already have REPLAYGAIN_* or R128_* tags when starting a ReplayGain scan",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Checkbox(checked = skipExistingRgByDefault, onCheckedChange = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onSkipExistingRgChange(!skipExistingRgByDefault) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileAndCacheSettingsBlock(
    showHiddenFiles: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    recentCount: Int,
    onClearRecents: () -> Unit,
    savedPatternCount: Int,
    onClearPatterns: () -> Unit,
    draftCount: Int,
    onClearDrafts: () -> Unit,
    statusMessage: String?,
) {
    SectionHeader("File manager, browser & cache")
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            ListItem(
                headlineContent = { Text("Show hidden files (.dot)") },
                supportingContent = {
                    Text(
                        "Display hidden files and folders starting with a dot in File Manager",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Icon(Icons.Filled.Visibility, contentDescription = null)
                },
                trailingContent = {
                    Checkbox(checked = showHiddenFiles, onCheckedChange = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onShowHiddenChange(!showHiddenFiles) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                text = "History & cached data",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onClearRecents,
                    enabled = recentCount > 0,
                ) {
                    Icon(Icons.Filled.History, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear recent folders ($recentCount)")
                }
                OutlinedButton(
                    onClick = onClearPatterns,
                    enabled = savedPatternCount > 0,
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear saved patterns ($savedPatternCount)")
                }
                OutlinedButton(
                    onClick = onClearDrafts,
                    enabled = draftCount > 0,
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear unsaved drafts ($draftCount)")
                }
            }

            if (statusMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PerformanceSettingsBlock(
    performanceMode: PerformanceMode,
    onPerformanceModeChange: (PerformanceMode) -> Unit,
    capabilitiesText: String,
) {
    SectionHeader("Device & performance")
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            PerformanceMode.entries.forEach { mode ->
                RadioRow(
                    label = mode.label,
                    selected = performanceMode == mode,
                    onClick = { onPerformanceModeChange(mode) },
                )
            }
            Text(
                text = capabilitiesText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdatesSettingsBlock(
    updateChannel: UpdateChannel,
    onUpdateChannelChange: (UpdateChannel) -> Unit,
    checking: Boolean,
    result: UpdateCheckResult?,
    error: String?,
    onCheckUpdates: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    SectionHeader("Updates")
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Update channel",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            UpdateChannel.entries.forEach { channel ->
                RadioRow(
                    label = channel.label,
                    subtitle = channel.description,
                    selected = updateChannel == channel,
                    onClick = { onUpdateChannelChange(channel) },
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onCheckUpdates,
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Checking GitHub…")
                    } else {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Check for updates")
                    }
                }
            }

            Text(
                text = "Installed: v${UpdateChecker.CURRENT_VERSION_NAME} (build ${UpdateChecker.CURRENT_VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Update check failed: $error",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { onOpenUrl(UpdateChecker.GITHUB_RELEASES_URL) },
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open GitHub Releases")
                            }
                        }
                    }
                }
            }

            if (result != null) {
                Spacer(Modifier.height(10.dp))
                val containerColor = if (result.isUpdateAvailable) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val contentColor = if (result.isUpdateAvailable) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (result.isUpdateAvailable) {
                                    Icons.Filled.SystemUpdate
                                } else {
                                    Icons.Filled.CheckCircle
                                },
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        result.latestRelease?.let { release ->
                            Spacer(Modifier.height(4.dp))
                            val datePart = if (release.publishedDate.isNotEmpty()) " • ${release.publishedDate}" else ""
                            Text(
                                text = "Latest ${result.channel.label}: ${release.title}$datePart",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val downloadTarget = release.apkDownloadUrl ?: release.releasePageUrl
                                if (result.isUpdateAvailable) {
                                    Button(onClick = { onOpenUrl(downloadTarget) }) {
                                        Icon(Icons.Filled.Download, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Download APK (${release.tagName})")
                                    }
                                } else {
                                    FilledTonalButton(onClick = { onOpenUrl(downloadTarget) }) {
                                        Icon(Icons.Filled.Download, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Get APK (${release.tagName})")
                                    }
                                }
                                OutlinedButton(onClick = { onOpenUrl(release.releasePageUrl) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Release notes")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperAndAboutBlock(
    onOpenUrl: (String) -> Unit,
) {
    SectionHeader("Developer")
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            ListItem(
                headlineContent = {
                    Text(
                        text = "@areyouanand",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                supportingContent = {
                    Text(
                        text = "Vision • github.com/areyouanand",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open developer GitHub profile",
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onOpenUrl(UpdateChecker.GITHUB_DEVELOPER_URL)
                },
            )
        }
    }

    SectionHeader("About")
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "MP3Tag Mobile ${UpdateChecker.CURRENT_VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Build ${UpdateChecker.CURRENT_VERSION_CODE} • Open source (MIT License) • Unofficial fan build",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            ListItem(
                headlineContent = { Text("GitHub Repository") },
                supportingContent = {
                    Text(
                        "github.com/areyouanand/mp3tag-mobile",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open GitHub repository",
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.clickable {
                    onOpenUrl(UpdateChecker.GITHUB_REPO_URL)
                },
            )

            Spacer(Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Releases & Downloads") },
                supportingContent = {
                    Text(
                        "github.com/areyouanand/mp3tag-mobile/releases",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open GitHub releases",
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.clickable {
                    onOpenUrl(UpdateChecker.GITHUB_RELEASES_URL)
                },
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Unofficial, fan-made Android audio tag editor & file manager inspired by Mp3tag for Windows " +
                    "(not affiliated with or endorsed by Florian Heidenreich / mp3tag.de).\n\n" +
                    "Included features: 3D Cover Flow Home, Tag Editor for MP3 (ID3v1/v2.3/v2.4), FLAC, " +
                    "M4A/MP4/M4B, Ogg Vorbis, Opus, WAV (RIFF INFO + ID3), AIFF/AIF/AIFC, WMA/ASF, " +
                    "APE (Monkey's Audio), WavPack (.wv), DSF & DFF (DSD / DSDIFF), custom tag fields, " +
                    "undo/redo & crash-safe draft cache, ReplayGain & Opus R128 loudness scanner, " +
                    "Spectrum & bit-depth analyzer with in-app File Manager, full File Manager, " +
                    "and 4 design languages (Material 3 Expressive, Material You, Liquid Glass, Halo).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
