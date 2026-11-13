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

- Latest stable release: **v0.8.1** (`versionCode = 28`)
- All releases and downloads: **https://github.com/areyouanand/mp3tag-mobile/releases**
- Minimum Android version: **12 (API 31)**
- Uses the **All files access** permission (`MANAGE_EXTERNAL_STORAGE`) so it can browse any folder
  and rewrite files in place — sideload only, not for Play Store distribution.

## Releases

| Channel | Version | Link |
|---|---|---|
| Stable (Latest) | v0.8.1 | [Latest release](https://github.com/areyouanand/mp3tag-mobile/releases/latest) |
| Beta (prerelease) | v0.8.1-beta | [v0.8.1-beta](https://github.com/areyouanand/mp3tag-mobile/releases/tag/v0.8.1-beta) |
| All releases | | [releases](https://github.com/areyouanand/mp3tag-mobile/releases) |

Install the APK that matches your device from a release's **Assets** — `arm64-v8a` for phones and DAPs (almost always this one) and `x86_64` only for Android emulators. Beta builds may still have bugs.

## Current status — v0.8.1

| Feature | State |
|---|---|
| **Explicit state machines**: navigation back stack, editor load/save/cover phases, conversion phase, file-manager operation state, ReplayGain scan cache + cancellable save | ✅ v0.8.1 |
| **Multi-value tags** (separate ARTIST/ALBUM_ARTIST/GENRE/COMPOSER/PERFORMER values) for MP3/FLAC/Ogg/Opus/DSD; APEv2 targets warn because the format is single-valued | ✅ v0.8.0 |
| **MP4 free-form atoms + ASF attributes** survive conversion, so custom/ReplayGain fields work on `.m4a` and `.wma` too | ✅ v0.8.0 |
| **WavPack APEv2 cover art** now reads back correctly — all 10 conversion targets keep cover art | ✅ v0.8.0 |
| **Crash-safe tag writes** for every format (temp copy + atomic replace) and "undo last save" in the editor | ✅ v0.8.0 |
| **Editor power tools**: tag clipboard, auto-numbering, consistency check, per-field revert, cover from folder/extract, BPM detection, CSV import/export, subfolder rename patterns | ✅ v0.8.0 |
| **Tag-aware browser**: search by title/artist/album, persisted tag sorting, duplicate finder | ✅ v0.8.0 |
| **File Manager**: paste conflict policy, cancellable copy/move with progress, free-space check, move-to-trash, SHA-256 properties | ✅ v0.8.0 |
| **ReplayGain**: ITU-R BS.1770-4 K-weighting, gated blocks, 4× true peak, clip-safe gain, gated album gain | ✅ v0.8.0 |
| **Conversion output control**: subfolder + filename pattern (`%artist%/%album%/%title%`), timestamps, compression levels, post-write verification | ✅ v0.8.0 |
| **Spectrum**: 15/30/60 s window, signal-health diagnostics, result cache, PNG export | ✅ v0.8.0 |
| **Settings backup/restore (JSON), release notes, in-app open-source licenses** | ✅ v0.8.0 |
| **Per-ABI APKs (`arm64-v8a` for devices, `x86_64` for emulators) — smaller downloads** | ✅ v0.7.1 |
| **1:1 metadata on conversion** (custom fields and cover art) | ✅ v0.7.1 |
| **Audio format conversion (Home → Convert): MP3 (LAME), FLAC, M4A/AAC, M4A/ALAC, Opus, Ogg Vorbis, WAV, AIFF, WMA, WavPack — per-file progress, cancel, optional delete-original** | ✅ v0.7.0 |
| **Settings: in-app GitHub update checker (`Release` vs `Beta` channel), ReplayGain defaults, File Manager/cache controls, Developer (`@areyouanand` Vision) & GitHub links** | ✅ v0.6.3 |
| **Spectrum analysis (Home → Spectrum analysis): in-app File Manager picker, spectrogram, top-of-sound cutoff, upsampling & lossy-origin hints, bit-depth padding check — now decodes DSD (DSF/DFF), APE and WavPack through the bundled FFmpeg** | ✅ v0.7.0 |
| **Folder summary and tap/long-press hint in the browser** | ✅ v0.4.2 |
| **Safer DFF/APE tag writes (temp copy, replaced only on success)** | ✅ v0.4.2 |
| **Extended audio formats (Phase 3): Opus (`.opus`), DFF / DSDIFF (`.dff`), Monkey's Audio (`.ape`), WavPack (`.wv`), WMA/ASF (`.wma`/`.asf`), AIFF (`.aif`/`.aiff`/`.aifc`), M4B (`.m4b`)** | ✅ v0.4.0 |
| **Theme section: Screen mode (`Auto`, `Dark`, `Light`) + Design language (`Material 3 Expressive`, `Material You`, `Liquid Glass`, `Halo`)** | ✅ v0.3.2 |
| **Home iPod / iTunes Cover Flow (`Tag editor`, `File manager`, `Convert`, `Spectrum analysis`, `ReplayGain`, `Automate`, `Settings`)** | ✅ v0.7.0 |
| **ReplayGain loudness tagging**: browser selection, scan progress banner, Track gain / Album gain / Opus R128 dropdown, existing RG detection, skip existing, remove RG tags — now analyses DSD/APE/WavPack too | ✅ v0.7.0 |
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
| Audio format conversion (transcoding while preserving tags & cover art) | ✅ v0.7.0 · 1:1 metadata v0.7.1 |
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
- Beta builds: pushes to the `beta` branch publish a prerelease `v<versionName>-beta` that is never marked Latest, for example [v0.6.3-beta](https://github.com/areyouanand/mp3tag-mobile/releases/tag/v0.6.3-beta).
- Current release (`v0.4.1`): `https://github.com/areyouanand/mp3tag-mobile/releases/tag/v0.4.1`

All builds — local and CI — sign with the committed shared debug keystore, so CI-built APKs install
over previously installed versions without uninstalling.

## Using the App

1. **Grant All-files access**: On first launch, grant **All files access** in the system screen opened by the app.
2. **Home Cover Flow**: Choose from the six Cover Flow tiles (tap a side cover to glide it to center; tap the centered cover to open it):
   - **Tag editor**: Browse folders across Internal storage, SD card, and USB OTG. Tap audio files or folder checkboxes to multi-select (and tap **Edit**), or long-press any file or folder to edit its tags immediately.
   - **File manager**: Full storage file manager with breadcrumb path bar, search, Copy, Move (Cut), Paste, Rename, Delete, New Folder, Sort (Name, Date, Size, Type; Ascending/Descending), Show/Hide `.dot` hidden files, Properties/Details, and one-tap **Edit tags** or **Analyze in Spectrum analysis** when audio files are selected.
   - **Spectrum analysis**: Uses the in-app File Manager to pick any audio file and inspect its spectrogram, top-of-sound frequency cutoff, upsampling/lossy-origin hints, and effective bit depth.
   - **ReplayGain**: Analyze track & album loudness and write ReplayGain / Opus R128 tags.
   - **Automate**: Workspace section reserved for upcoming automated tagging and file workflows.
   - **Settings**: Configure Theme (**Screen mode**: `Auto`, `Dark`, `Light`; **Design language**: `Material 3 Expressive`, `Material You`, `Liquid Glass`, `Halo`), MP3/DSD ID3v2 version (`v2.3` vs `v2.4`), ReplayGain defaults, File Manager `.dot` hidden files & history/cache clearing, Device & performance mode (`Auto`, `High performance`, `Low-spec / memory saver`), **Updates** (`Release (Stable)` vs `Beta (Prerelease)` channel selector + live GitHub update checker), and **Developer / About** (`@areyouanand` Vision credit & GitHub links).

## Project layout

```
app/src/main/java/com/mp3tag/mobile/
├── MainActivity.kt                  # app shell, workspace navigation, all-files permission gate
├── data/
│   ├── fs/FolderScanner.kt          # directory listing, recursive folder audio scan, natural sort
│   ├── prefs/AppPrefs.kt            # ID3 version, theme, update channel, RG defaults, recents, patterns
│   ├── update/UpdateChecker.kt      # GitHub Releases API update checker (Release vs Beta channel)
│   ├── replaygain/LoudnessAnalyzer.kt # MediaCodec loudness & peak analyzer (-18 LUFS target)
│   ├── spectrum/                    # FFT, MediaCodec PCM decoder, analysis rules, bit-depth check
│   └── tag/
│       ├── TagData.kt               # models, O(1) hashed CoverImage, editable field catalog
│       ├── TagEngine.kt             # unified read/write dispatcher across all audio formats
│       ├── Id3CustomFields.kt       # ID3v2 TXXX custom frame reader/writer
│       ├── DffTagHandler.kt         # DSDIFF (.dff) FRM8/PROP parser & ID3v2 chunk reader/writer
│       ├── OpusTagHandler.kt        # Ogg Opus (.opus) OpusHead/OpusTags VorbisComment + page CRC writer
│       └── ApeTagHandler.kt         # APEv2 (APETAGEX) reader/writer for Monkey's Audio (.ape) & WavPack (.wv)
├── ui/
│   ├── home/HomeScreen.kt           # Cover Flow Home dashboard (Tag editor, File manager, Spectrum, ReplayGain, Automate, Settings)
│   ├── spectrum/SpectrumScreen.kt   # Spectrum analysis: in-app File Manager picker, spectrogram, curve, hints
│   ├── replaygain/ReplayGainScreen.kt # ReplayGain & Opus R128 batch scanner & writer
│   ├── browser/                     # Tag editor folder & file browser, file/folder multi-select
│   ├── editor/                      # Tag editor (single & batch, 1-pane & 2-pane, undo/redo, draft cache, ToolsDialogs)
│   ├── filemanager/                 # Full File Manager (copy, move, paste, rename, delete, sort, info)
│   ├── automate/AutomateScreen.kt   # Automate workspace placeholder
│   ├── settings/SettingsScreen.kt   # Theme, tagging/RG, cache, performance, update checker, Developer & About
│   ├── common/
│   │   ├── AdaptiveLayout.kt        # Window size classes & phone/tablet portrait/landscape layout rules
│   │   └── Common.kt                # Shared UI components
│   └── theme/Theme.kt               # Material 3 Expressive, Material You, Liquid Glass & Halo themes
└── util/
    ├── DeviceProfile.kt             # Hardware tier detection (LOW_SPEC / STANDARD / HIGH_END)
    ├── Format.kt                    # Size/duration formatting + LruCache & OOM-safe ImageDecode
    └── PatternParser.kt             # %field% pattern parser and filename builder
```

Tag engine: [JAudioTagger 3.0.1](https://www.jthink.net/jaudiotagger/) + custom binary handlers for
DSDIFF (`.dff`), Ogg Opus (`.opus`), Monkey's Audio (`.ape`), and WavPack (`.wv`).

## Developer

- **Vision**: [@areyouanand](https://github.com/areyouanand)

## License

This project is **open source** under the [MIT License](LICENSE) — **free to use, modify, and fork**,
including for your own builds.

The "Mp3tag" name refers to [Mp3tag for Windows](https://www.mp3tag.de) by Florian Heidenreich and is
used only to describe this project's inspiration. MP3Tag Mobile is an **unofficial fan build** — not
affiliated with, sponsored by, or endorsed by the original Mp3tag, and containing no code or assets
from it. Forks are welcome under MIT, but should not imply endorsement by the original Mp3tag or its
author.
