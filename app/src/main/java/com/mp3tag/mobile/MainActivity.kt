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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp3tag.mobile.data.prefs.AppPrefs
import com.mp3tag.mobile.data.prefs.DesignLanguage
import com.mp3tag.mobile.data.prefs.Id3Version
import com.mp3tag.mobile.data.prefs.PerformanceMode
import com.mp3tag.mobile.data.prefs.ThemeMode
import com.mp3tag.mobile.ui.automate.AutomateScreen
import com.mp3tag.mobile.ui.spectrum.SpectrumScreen
import com.mp3tag.mobile.ui.browser.BrowserScreen
import com.mp3tag.mobile.ui.browser.BrowserViewModel
import com.mp3tag.mobile.ui.common.EmptyState
import com.mp3tag.mobile.ui.editor.EditorScreen
import com.mp3tag.mobile.ui.filemanager.FileManagerScreen
import com.mp3tag.mobile.ui.filemanager.FileManagerViewModel
import com.mp3tag.mobile.ui.home.HomeScreen
import com.mp3tag.mobile.ui.replaygain.ReplayGainScreen
import com.mp3tag.mobile.ui.settings.SettingsScreen
import com.mp3tag.mobile.ui.theme.Mp3tagTheme
import com.mp3tag.mobile.util.DeviceProfile
import com.mp3tag.mobile.util.ImageDecode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Mp3tagApp() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ImageDecode.trimCache(level)
    }
}

sealed interface Screen {
    data object Home : Screen
    data object Browser : Screen
    data object FileManager : Screen
    data object Automate : Screen
    data class Spectrum(
        val initialPath: String? = null,
        val returnTo: Screen = Home,
    ) : Screen
    data object ReplayGain : Screen
    data class Settings(val returnTo: Screen = Home) : Screen
    data class Editor(
        val paths: List<String>,
        val session: Int,
        val returnTo: Screen = Browser,
    ) : Screen
}

class AppViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission = _hasPermission.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen = _screen.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode = _themeMode.asStateFlow()

    private val _designLanguage = MutableStateFlow(prefs.designLanguage)
    val designLanguage = _designLanguage.asStateFlow()

    private val _id3Version = MutableStateFlow(prefs.id3Version)
    val id3Version = _id3Version.asStateFlow()

    private val _performanceMode = MutableStateFlow(prefs.performanceMode)
    val performanceMode = _performanceMode.asStateFlow()

    private var sessionCounter = 0

    fun recheckPermission() {
        _hasPermission.value = checkPermission()
    }

    private fun checkPermission(): Boolean =
        android.os.Environment.isExternalStorageManager()

    fun openHome() {
        _screen.value = Screen.Home
    }

    fun openTagEditor() {
        _screen.value = Screen.Browser
    }

    fun openFileManager() {
        _screen.value = Screen.FileManager
    }

    fun openAutomate() {
        _screen.value = Screen.Automate
    }

    fun openSpectrum(initialPath: String? = null, returnTo: Screen = Screen.Home) {
        _screen.value = Screen.Spectrum(initialPath = initialPath, returnTo = returnTo)
    }

    fun openReplayGain() {
        _screen.value = Screen.ReplayGain
    }

    fun openEditor(paths: List<String>, returnTo: Screen = Screen.Browser) {
        sessionCounter += 1
        _screen.value = Screen.Editor(paths, sessionCounter, returnTo)
    }

    fun openSettings(returnTo: Screen = _screen.value) {
        _screen.value = Screen.Settings(returnTo)
    }

    fun back() {
        _screen.value = when (val current = _screen.value) {
            is Screen.Editor -> current.returnTo
            is Screen.Settings -> current.returnTo
            is Screen.Spectrum -> current.returnTo
            else -> Screen.Home
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun setDesignLanguage(language: DesignLanguage) {
        prefs.designLanguage = language
        _designLanguage.value = language
    }

    fun setId3Version(version: Id3Version) {
        prefs.id3Version = version
        _id3Version.value = version
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        prefs.performanceMode = mode
        _performanceMode.value = mode
        DeviceProfile.get(getApplication(), mode)
    }
}

@Composable
fun Mp3tagApp(appViewModel: AppViewModel = viewModel()) {
    val themeMode by appViewModel.themeMode.collectAsState()
    val designLanguage by appViewModel.designLanguage.collectAsState()
    val id3Version by appViewModel.id3Version.collectAsState()
    val performanceMode by appViewModel.performanceMode.collectAsState()
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
        designLanguage = designLanguage,
    ) {
        if (!hasPermission) {
            PermissionGate(onRequest = { requestAllFilesAccess(context) })
        } else {
            val browserVm: BrowserViewModel = viewModel()
            val fileManagerVm: FileManagerViewModel = viewModel()

            when (val current = screen) {
                Screen.Home -> HomeScreen(
                    onOpenTagEditor = appViewModel::openTagEditor,
                    onOpenFileManager = appViewModel::openFileManager,
                    onOpenSpectrum = { appViewModel.openSpectrum() },
                    onOpenReplayGain = appViewModel::openReplayGain,
                    onOpenAutomate = appViewModel::openAutomate,
                    onOpenSettings = { appViewModel.openSettings(Screen.Home) },
                )

                is Screen.Spectrum -> SpectrumScreen(
                    onBack = appViewModel::back,
                    onEditAudioTags = { paths -> appViewModel.openEditor(paths, current) },
                    initialFilePath = current.initialPath,
                    fileManagerViewModel = fileManagerVm,
                )

                Screen.ReplayGain -> ReplayGainScreen(
                    onBack = appViewModel::openHome,
                    onOpenSettings = { appViewModel.openSettings(Screen.ReplayGain) },
                )

                Screen.Browser -> BrowserScreen(
                    onBackHome = appViewModel::openHome,
                    onEditFiles = { paths -> appViewModel.openEditor(paths, Screen.Browser) },
                    onOpenSettings = { appViewModel.openSettings(Screen.Browser) },
                    viewModel = browserVm,
                )

                Screen.FileManager -> FileManagerScreen(
                    onBackHome = appViewModel::openHome,
                    onEditAudioTags = { paths -> appViewModel.openEditor(paths, Screen.FileManager) },
                    onAnalyzeSpectrum = { path -> appViewModel.openSpectrum(path, Screen.FileManager) },
                    viewModel = fileManagerVm,
                )

                Screen.Automate -> AutomateScreen(
                    onBack = appViewModel::openHome,
                )

                is Screen.Editor -> EditorScreen(
                    paths = current.paths,
                    sessionKey = "editor-${current.session}",
                    onDone = {
                        appViewModel.back()
                        browserVm.refresh()
                        fileManagerVm.refresh()
                    },
                    onFilesRenamed = {
                        browserVm.refresh()
                        fileManagerVm.refresh()
                    },
                    onOpenSingleFile = { path ->
                        appViewModel.openEditor(listOf(path), current.returnTo)
                    },
                )

                is Screen.Settings -> SettingsScreen(
                    id3Version = id3Version,
                    onId3VersionChange = appViewModel::setId3Version,
                    themeMode = themeMode,
                    onThemeChange = appViewModel::setThemeMode,
                    designLanguage = designLanguage,
                    onDesignLanguageChange = appViewModel::setDesignLanguage,
                    performanceMode = performanceMode,
                    onPerformanceModeChange = appViewModel::setPerformanceMode,
                    onBack = {
                        browserVm.reloadPrefs()
                        fileManagerVm.reloadPrefs()
                        appViewModel.back()
                    },
                )
            }

            BackHandler(
                enabled = screen is Screen.Settings || screen is Screen.Automate || screen is Screen.Spectrum
            ) {
                if (screen is Screen.Settings) {
                    browserVm.reloadPrefs()
                    fileManagerVm.reloadPrefs()
                }
                appViewModel.back()
            }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            EmptyState(
                icon = Icons.Filled.Folder,
                title = "All-files access needed",
                subtitle = "MP3Tag Mobile edits audio tags in place, like MP3Tag on Windows. " +
                    "Grant \"All files access\" so it can browse folders and rewrite your music files.\n\n" +
                    "This app is for personal use and is not distributed through the Play Store.",
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest, modifier = Modifier.height(48.dp)) {
                Text("Grant all-files access")
            }
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
