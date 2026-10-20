# MP3Tag Mobile

> [!IMPORTANT]
> **Unofficial fan build.** MP3Tag Mobile is a free, fan-made Android app *inspired by* the
> workflow of [Mp3tag](https://www.mp3tag.de) for Windows. It is **not an official Mp3tag
> product** and is **not affiliated with, sponsored by, or endorsed by** Florian Heidenreich /
> [Mp3tag](https://www.mp3tag.de). No code or assets from the original Windows application are
> used — this is an independent, from-scratch Android implementation. The name "Mp3tag" is only
> used to describe the inspiration; all trademark rights belong to their respective owners.
> For the official Windows tool, visit **[mp3tag.de](https://www.mp3tag.de)**.

A personal-use Android audio tag editor and file manager that mirrors the workflow of [MP3Tag](https://www.mp3tag.de) for
Windows, built with Kotlin, Jetpack Compose, and **Material 3 Expressive**.

- Current release: **v0.4.1** (`versionCode = 9`)
- Minimum Android version: **12 (API 31)**
- Uses the **All files access** permission (`MANAGE_EXTERNAL_STORAGE`) so it can browse any folder
  and rewrite files in place — sideload only, not for Play Store distribution.

## Current status — v0.4.1

| Feature | State |
|---|---|
| **Extended audio formats (Phase 3): Opus (`.opus`), DFF / DSDIFF (`.dff`), Monkey's Audio (`.ape`), WavPack (`.wv`), WMA/ASF (`.wma`/`.asf`), AIFF (`.aif`/`.aiff`/`.aifc`), M4B (`.m4b`)** | ✅ v0.4.0 |
| **Theme section: Screen mode (`Auto`, `Dark`, `Light`) + Design language (`Material 3 Expressive`, `Material You`, `Liquid Glass`, `Halo`)** | ✅ v0.3.2 |
| **Home workspace in Grid style (`Tag editor`, `File manager`, `Automate`, `Settings`)** | ✅ v0.3.1 |
| **Full File Manager (copy, move/cut, paste, rename, delete, new folder, sort, hidden files, properties, open in Tag Editor)** | ✅ v0.3.0 |
| **Automate workspace section (placeholder ready for automation rules)** | ✅ v0.3.0 |
| **Select / edit entire folders in Tag Editor (folder checkbox, long-press folder, or menu action)** | ✅ v0.3.1 |
| Folder browser with filter, recent folders, storage-device picker, natural sorting | ✅ |
| Multi-select files & folders, batch tag editing with MP3Tag-style "mixed" field handling | ✅ |
| Editor file picker: include/exclude files from the edit (MP3Tag-style) | ✅ |
| Single-file editing (long-press a file) | ✅ |
| Fields: title, artist, album artist, album, year, track/disc + totals, genre, composer, comment | ✅ |
| Per-field value pickers (values found in the selected files) | ✅ |
| Add-field picker (tags present in files + ~75 standard fields) | ✅ |
| **Tools: tags from filename** (`%artist% - %title%` patterns with preview) | ✅ Phase 2 |
| **Tools: rename from tags** (preview, sanitized names, collision handling) | ✅ Phase 2 |
| **Tools: batch actions** (regex replace, case conversion) | ✅ Phase 2 |
| Cover art: view, embed/replace from photo picker, remove | ✅ |
| **Adaptive layouts: phone & tablet, portrait & landscape (2-pane split view, 2-column grid)** | ✅ v0.2.2 |
| **Hardware-tier scaling: high-end parallel I/O & low-spec memory/RGB_565 saver** | ✅ v0.2.2 |
| **Versioned APK builds (`mp3tag-mobile-v<versionName>-debug.apk`) & matching GitHub Releases** | ✅ v0.3.1 |
| MP3: ID3v2.3 / ID3v2.4 writing (configurable), ID3v1 preserved | ✅ |
| WAV: RIFF INFO + ID3 chunks (MP3Tag-style both) | ✅ |
| DSD: DSF & DFF (DSDIFF) files tagged through their embedded ID3v2 tag | ✅ v0.4.0 |
| FLAC, M4A/MP4/M4B, Ogg Vorbis, Opus, AIFF, WMA/ASF, APE (Monkey's Audio), WavPack (`.wv`) | ✅ v0.4.0 |
| Audio format conversion (transcoding while preserving tags & cover art) | Roadmap |
| MusicBrainz / Discogs metadata & cover lookup | Phase 3 remaining |

## Building

Toolchain installed locally (set up for CLI builds):
- JDK 21: `%LOCALAPPDATA%\mp3tag-tooling\jdk-21.0.12.1+1`
- Android SDK (API 36): `%LOCALAPPDATA%\mp3tag-tooling\sdk` (see `local.properties`)
- Gradle 8.14.4 via the wrapper

Build from this folder:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\mp3tag-tooling\jdk-21.0.12.1+1"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\mp3tag-tooling\sdk"
.\gradlew.bat assembleDebug
```

The debug APK lands at `app\build\outputs\apk\debug\mp3tag-mobile-v<versionName>-debug.apk` (for example,
`mp3tag-mobile-v0.3.1-debug.apk`) — sideload it onto the device or install via `adb install -r`.
Opening the project root in Android Studio works too; it will reuse the SDK via `local.properties`.

### CI / Releases & Installing on Your Device

Every logical change bumps `versionName` and `versionCode` in `app/build.gradle.kts` (and `SettingsScreen.kt`).
Pushing to `master` runs a GitHub Action that builds the versioned APK (`mp3tag-mobile-v<versionName>-debug.apk`)
and publishes/updates the corresponding versioned GitHub Release (`v<versionName>`, marked as **Latest**):

- Latest release page: `https://github.com/areyouanand/mp3tag-mobile/releases/latest`
- Current release (`v0.4.1`): `https://github.com/areyouanand/mp3tag-mobile/releases/tag/v0.4.1`

All builds — local and CI — sign with the committed shared debug keystore, so CI-built APKs install
over previously installed versions without uninstalling.

## Using the App

1. **Grant All-files access**: On first launch, grant **All files access** in the system screen opened by the app.
2. **Home Grid**: Choose from the four grid cards:
   - **Tag editor**: Browse folders across Internal storage, SD card, and USB OTG. Tap audio files or folder checkboxes to multi-select (and tap **Edit**), or long-press any file or folder to edit its tags immediately.
   - **File manager**: Full storage file manager with breadcrumb path bar, search, Copy, Move (Cut), Paste, Rename, Delete, New Folder, Sort (Name, Date, Size, Type; Ascending/Descending), Show/Hide `.dot` hidden files, Properties/Details, and one-tap **Edit tags** when audio files are selected.
   - **Automate**: Workspace section reserved for upcoming automated tagging and file workflows.
   - **Settings**: Configure Theme (**Screen mode**: `Auto`, `Dark`, `Light`; **Design language**: `Material 3 Expressive`, `Material You`, `Liquid Glass`, `Halo`), MP3/DSF ID3v2 version (`v2.3` vs `v2.4`), and Device & performance mode (`Auto`, `High performance`, `Low-spec / memory saver`).

## Project layout

```
app/src/main/java/com/mp3tag/mobile/
├── MainActivity.kt                  # app shell, workspace navigation, all-files permission gate
├── data/
│   ├── fs/FolderScanner.kt          # directory listing, recursive folder audio scan, natural sort
│   ├── prefs/AppPrefs.kt            # ID3 version, theme, performance mode, recents, saved patterns
│   └── tag/
│       ├── TagData.kt               # models, O(1) hashed CoverImage, editable field catalog
│       ├── TagEngine.kt             # unified read/write dispatcher across all audio formats
│       ├── DffTagHandler.kt         # DSDIFF (.dff) FRM8/PROP parser & ID3v2 chunk reader/writer
│       ├── OpusTagHandler.kt        # Ogg Opus (.opus) OpusHead/OpusTags VorbisComment + page CRC writer
│       └── ApeTagHandler.kt         # APEv2 (APETAGEX) reader/writer for Monkey's Audio (.ape) & WavPack (.wv)
├── ui/
│   ├── home/HomeScreen.kt           # Grid Home dashboard (Tag editor, File manager, Automate, Settings)
│   ├── browser/                     # Tag editor folder & file browser, file/folder multi-select
│   ├── editor/                      # Tag editor (single & batch, 1-pane & 2-pane, ToolsDialogs)
│   ├── filemanager/                 # Full File Manager (copy, move, paste, rename, delete, sort, info)
│   ├── automate/AutomateScreen.kt   # Automate workspace placeholder
│   ├── settings/SettingsScreen.kt   # ID3, theme, and hardware performance settings
│   ├── common/
│   │   ├── AdaptiveLayout.kt        # Window size classes & phone/tablet portrait/landscape layout rules
│   │   └── Common.kt                # Shared UI components
│   └── theme/Theme.kt               # Material 3 Expressive theme
└── util/
    ├── DeviceProfile.kt             # Hardware tier detection (LOW_SPEC / STANDARD / HIGH_END)
    ├── Format.kt                    # Size/duration formatting + LruCache & OOM-safe ImageDecode
    └── PatternParser.kt             # %field% pattern parser and filename builder
```

Tag engine: [JAudioTagger 3.0.1](https://www.jthink.net/jaudiotagger/) + custom binary handlers for
DSDIFF (`.dff`), Ogg Opus (`.opus`), Monkey's Audio (`.ape`), and WavPack (`.wv`).
