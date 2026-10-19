# MP3Tag Mobile

A personal-use Android tag editor that mirrors the workflow of [MP3Tag](https://www.mp3tag.de) for
Windows, built with Kotlin, Jetpack Compose and **Material 3 Expressive**.

- Minimum Android version: **12 (API 31)**
- Uses the **All files access** permission (`MANAGE_EXTERNAL_STORAGE`) so it can browse any folder
  and rewrite files in place — sideload only, not for Play Store distribution.

## Current status — Phase 1

| Feature | State |
|---|---|
| Folder browser with filter, recent folders, natural (numeric-aware) sorting | ✅ |
| Multi-select files, batch tag editing with MP3Tag-style "mixed" field handling | ✅ |
| Single-file editing (long-press a file) | ✅ |
| Fields: title, artist, album artist, album, year, track/disc + totals, genre, composer, comment | ✅ |
| Cover art: view, embed/replace from photo picker, remove | ✅ |
| MP3: ID3v2.3 / ID3v2.4 writing (configurable), ID3v1 preserved | ✅ |
| WAV: RIFF INFO + ID3 chunks (configurable, MP3Tag-style default) | ✅ |
| DSD: DSF files tagged through their embedded ID3v2 tag | ✅ |
| FLAC, M4A/MP4, Ogg Vorbis | ✅ |
| Tags-from-filename, rename-from-tags, batch actions (regex/case), export/import | Phase 2 |
| DFF (DSDIFF) via TagLib/JNI, Opus/WMA/APE/WavPack | Phase 2/3 |
| MusicBrainz / Discogs metadata & cover lookup | Phase 2/3 |

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
