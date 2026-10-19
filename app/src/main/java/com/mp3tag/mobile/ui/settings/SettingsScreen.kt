package com.mp3tag.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mp3tag.mobile.data.prefs.Id3Version
import com.mp3tag.mobile.data.prefs.ThemeMode
import com.mp3tag.mobile.ui.common.RadioRow
import com.mp3tag.mobile.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    id3Version: Id3Version,
    onId3VersionChange: (Id3Version) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(padding.calculateTopPadding()))
            }

            item {
                SectionHeader("MP3 & DSD (DSF) tag version")
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
                    text = "Applies when saving MP3 and DSF (DSD) files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            item {
                SectionHeader("WAV tags")
                Text(
                    text = "WAV files are written with both an ID3v2 chunk and a RIFF INFO chunk, " +
                        "like MP3Tag's default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            item {
                SectionHeader("Theme")
                Column {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(
                            label = mode.label,
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) },
                        )
                    }
                }
            }

            item {
                SectionHeader("About")
                Card(Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("MP3Tag Mobile 0.1.0", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Personal-use tag editor inspired by MP3Tag for Windows.\n\n" +
                                "Supported now: MP3 (ID3v1/v2.3/v2.4), FLAC, M4A/MP4, Ogg, " +
                                "WAV (RIFF INFO + ID3), DSF (DSD).\n\n" +
                                "Roadmap: tags from filename, rename from tags, batch actions " +
                                "(regex, case), DFF (DSDIFF) via TagLib, Opus/WMA/APE, " +
                                "MusicBrainz & Discogs lookup, export/import.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
