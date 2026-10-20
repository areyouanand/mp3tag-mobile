# MP3Tag Mobile

A personal-use Android tag editor that mirrors the workflow of [MP3Tag](https://www.mp3tag.de) for
Windows, built with Kotlin, Jetpack Compose and **Material 3 Expressive**.

- Minimum Android version: **12 (API 31)**
- Uses the **All files access** permission (`MANAGE_EXTERNAL_STORAGE`) so it can browse any folder
  and rewrite files in place — sideload only, not for Play Store distribution.

## Current status — Phase 1 + 2

| Feature | State |
|---|---|
| **Home page in Grid style (`Tag editor`, `File manager`, `Automate`, `Settings`)** | ✅ v0.3.0 |
| **Full File Manager (copy, move/cut, paste, rename, delete, new folder, sort, hidden files, properties, open in Tag Editor)** | ✅ v0.3.0 |
| **Automate workspace section (placeholder ready for automation rules)** | ✅ v0.3.0 |
| Folder browser with filter, recent folders, storage-device picker, natural sorting | ✅ |
| Multi-select files & entire folders (checkbox or long-press folder), batch tag editing with MP3Tag-style "mixed" field handling | ✅ |
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
| MP3: ID3v2.3 / ID3v2.4 writing (configurable), ID3v1 preserved | ✅ |
| WAV: RIFF INFO + ID3 chunks (MP3Tag-style both) | ✅ |
| DSD: DSF files tagged through their embedded ID3v2 tag | ✅ |
| FLAC, M4A/MP4, Ogg Vorbis | ✅ |
| Tag export/import (CSV) | Phase 2 remaining |
| DFF (DSDIFF) via TagLib/JNI, Opus/WMA/APE/WavPack | Phase 3 |
| MusicBrainz / Discogs metadata & cover lookup | Phase 3 |

## Building

Toolchain installed locally (set up for CLI builds):
- JDK 21: `%LOCALAPPDATA%\mp3tag-tooling\jdk-21.0.12.1+1`
- Android SDK (API 36): `%LOCALAPPDATA%\mp3tag-tooling\sdk` (see `local.properties`)
- Gradle 8.14.4 via the wrapper

Build from this folder:

```
$env:JAVA_HOME = "$env:LOCALAPPDATA\mp3tag-tooling\jdk-21.0.12.1+1"
.\gradlew.bat assembleDebug
```

The debug APK lands at `app\build\outputs\apk\debug\app-debug.apk` — sideload it onto the phone
(open it on the device, or `adb install`). Opening the project root in Android Studio works too;
it will reuse the SDK via `local.properties`.

### CI / install from your phone

Every push to `master` runs a GitHub Action that builds the APK and attaches it to a rolling
prerelease. On the phone, open:

```
https://github.com/areyouanand/mp3tag-mobile/releases/latest/download/mp3tag-mobile-debug.apk
```

(sign in to GitHub, download, then open the APK). All builds — local and CI — sign with the
committed shared debug keystore, so CI-built APKs install over previously installed versions
without uninstalling. Versioned releases are created when a `v*` tag is pushed.

## First launch

1. Grant **All files access** in the system screen the app opens for you.
2. You start at `/storage/emulated/0` — navigate to your music folder.
3. Tap files to select them (like checkbox selection in MP3Tag), then tap **Edit**.
4. Long-press a single file to edit just that one.
5. On save, MP3 (and DSF) files are written with the ID3v2 version configured in Settings.

## Project layout

```
app/src/main/java/com/mp3tag/mobile/
├── MainActivity.kt            # app shell, navigation, all-files permission gate
├── data/
│   ├── fs/FolderScanner.kt    # directory listing + natural sort
│   ├── prefs/AppPrefs.kt      # ID3 version, WAV mode, theme, recents
│   └── tag/
│       ├── TagData.kt         # models + editable field list
│       └── TagEngine.kt       # JAudioTagger read/write engine
├── ui/
│   ├── browser/               # folder + file list, selection
│   ├── editor/                # tag editor (single & batch)
│   ├── settings/              # tag format options, theme
│   ├── common/Common.kt
│   └── theme/Theme.kt         # Material 3 Expressive theme
└── util/Format.kt             # size/duration/cover decoding helpers
```

Tag engine: [JAudioTagger 3.0.1](https://www.jthink.net/jaudiotagger/) (pure Java). DFF support will
use [TagLib 2.x](https://taglib.org/) through JNI.
