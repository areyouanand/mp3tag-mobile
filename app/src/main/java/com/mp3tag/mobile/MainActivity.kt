package com.mp3tag.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.prefs.Id3Version
import com.mp3tag.mobile.data.prefs.ThemeMode
import com.mp3tag.mobile.ui.browser.BrowserScreen
import com.mp3tag.mobile.ui.browser.BrowserViewModel
import com.mp3tag.mobile.ui.common.EmptyState
import com.mp3tag.mobile.ui.editor.EditorScreen
import com.mp3tag.mobile.ui.settings.SettingsScreen
import com.mp3tag.mobile.ui.theme.Mp3tagTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Mp3tagApp() }
    }
}

sealed interface Screen {
    data object Browser : Screen
    data object Settings : Screen
    data class Editor(val paths: List<String>, val session: Int) : Screen
}

class AppViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission = _hasPermission.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Browser)
    val screen = _screen.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode = _themeMode.asStateFlow()

    private var sessionCounter = 0

    fun recheckPermission() {
        _hasPermission.value = checkPermission()
    }

    private fun checkPermission(): Boolean =
        android.os.Environment.isExternalStorageManager()

    fun openEditor(paths: List<String>) {
        sessionCounter += 1
        _screen.value = Screen.Editor(paths, sessionCounter)
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun back() {
        _screen.value = Screen.Browser
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun setId3Version(version: Id3Version) {
        prefs.id3Version = version
    }

    val id3Version: Id3Version get() = prefs.id3Version
}

@Composable
fun Mp3tagApp(appViewModel: AppViewModel = viewModel()) {
    val themeMode by appViewModel.themeMode.collectAsState()
    val hasPermission by appViewModel.hasPermission.collectAsState()
    val screen by appViewModel.screen.collectAsState()
    val context = LocalContext.current

    // Re-check all-files access whenever the activity resumes (e.g. returning
    // from the system settings screen after granting it).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) appViewModel.recheckPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Mp3tagTheme(
        darkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        },
    ) {
        if (!hasPermission) {
            PermissionGate(onRequest = { requestAllFilesAccess(context) })
        } else {
            val browserVm: BrowserViewModel = viewModel()
            when (val current = screen) {
                Screen.Browser -> BrowserScreen(
                    onEditFiles = appViewModel::openEditor,
                    onOpenSettings = appViewModel::openSettings,
                    viewModel = browserVm,
                )

                is Screen.Editor -> EditorScreen(
                    paths = current.paths,
                    sessionKey = "editor-${current.session}",
                    onDone = {
                        appViewModel.back()
                        browserVm.refresh()
                    },
                )

                Screen.Settings -> SettingsScreen(
                    id3Version = appViewModel.id3Version,
                    onId3VersionChange = appViewModel::setId3Version,
                    themeMode = themeMode,
                    onThemeChange = appViewModel::setThemeMode,
                    onBack = appViewModel::back,
                )
            }
            BackHandler(enabled = screen != Screen.Browser) { appViewModel.back() }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        EmptyState(
            icon = Icons.Filled.Folder,
            title = "All-files access needed",
            subtitle = "MP3Tag Mobile edits audio tags in place, like MP3Tag on Windows. " +
                "Grant \"All files access\" so it can browse folders and rewrite your music files.\n\n" +
                "This app is for personal use and is not distributed through the Play Store.",
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, modifier = Modifier.height(48.dp)) {
            Text("Grant all-files access")
        }
    }
}

private fun requestAllFilesAccess(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        } catch (_: Exception) {
            // Nothing else we can do; the system has no settings screen for this.
        }
    }
}
