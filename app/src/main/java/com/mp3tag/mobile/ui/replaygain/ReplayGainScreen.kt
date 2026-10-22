package com.mp3tag.mobile.ui.replaygain

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mp3tag.mobile.data.fs.FolderScanner
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.replaygain.LoudnessAnalyzer
import com.mp3tag.mobile.data.replaygain.TrackLoudness
import com.mp3tag.mobile.data.tag.TagEngine
import com.mp3tag.mobile.data.tag.WriteOptions
import com.mp3tag.mobile.data.tag.WriteRequest
import com.mp3tag.mobile.util.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayGainScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()

    var folderPath by remember { mutableStateOf(prefs.lastFolder ?: "") }
    var tracks by remember { mutableStateOf<List<TrackLoudness>>(emptyList()) }
    var albumGain by remember { mutableStateOf<Double?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ReplayGain") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = folderPath,
                onValueChange = { folderPath = it },
                label = { Text("Folder path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        analyzing = true
                        status = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val dir = File(folderPath)
                                val files = FolderScanner.collectAudioFilesRecursively(dir)
                                val list = files.mapNotNull { LoudnessAnalyzer.analyzeFile(context, it) }
                                val album = if (list.isNotEmpty()) {
                                    // Approximate album gain from average of track gains.
                                    list.map { it.gainDb }.average()
                                } else null
                                Pair(list, album)
                            }
                            tracks = result.first
                            albumGain = result.second
                            analyzing = false
                            status = "Analyzed ${tracks.size} file(s)"
                        }
                    },
                    enabled = !analyzing && !applying && folderPath.isNotBlank(),
                ) { Text("Analyze") }
                Button(
                    onClick = {
                        applying = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val options = WriteOptions(id3Version = prefs.id3Version)
                                tracks.forEach { t ->
                                    val file = File(t.path)
                                    val customFields = mutableMapOf<String, String>()
                                    customFields["REPLAYGAIN_TRACK_GAIN"] = "%.2f dB".format(t.gainDb)
                                    customFields["REPLAYGAIN_TRACK_PEAK"] = "%.6f".format(Math.pow(10.0, t.peakDb / 20.0))
                                    albumGain?.let {
                                        customFields["REPLAYGAIN_ALBUM_GAIN"] = "%.2f dB".format(it)
                                        customFields["REPLAYGAIN_ALBUM_PEAK"] = "%.6f".format(tracks.maxOfOrNull { tr -> Math.pow(10.0, tr.peakDb / 20.0) } ?: 1.0)
                                    }
                                    TagEngine.write(
                                        file = file,
                                        request = com.mp3tag.mobile.data.tag.WriteRequest(customFields = customFields),
                                        options = options,
                                    )
                                }
                            }
                            applying = false
                            status = "Applied ReplayGain tags to ${tracks.size} file(s)"
                        }
                    },
                    enabled = !analyzing && !applying && tracks.isNotEmpty(),
                ) { Text("Apply tags") }
            }
            if (analyzing || applying) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it)
            }
            Spacer(Modifier.height(8.dp))
            albumGain?.let { Text("Album gain: %.2f dB".format(it)) }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(tracks) { t ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(File(t.path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("LUFS %.1f  gain %.2f dB  peak %.1f dBFS".format(t.lufs, t.gainDb, t.peakDb), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
