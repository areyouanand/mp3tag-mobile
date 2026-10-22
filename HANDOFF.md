# HANDOFF — MP3Tag Mobile

**You are taking over an active, working project.** Read this first — it documents everything
that is NOT obvious from the code. The whole project lives in this directory (= git repo,
remote: `github.com/areyouanand/mp3tag-mobile`, **public** since Oct 2026, push access via GitHub CLI device auth).

## What this is

A personal-use Android tag editor that mirrors the workflow of MP3Tag for Windows.
Single user, sideloaded, never going to the Play Store.

- Kotlin + Jetpack Compose, **Material 3 Expressive** UI (dynamic Material You color)
- minSdk 31 (Android 12), target/compile SDK 36, AGP 8.13.2, Kotlin 2.4.21, Gradle 8.14.4 (wrapper)
- Tag engine: **JAudioTagger 3.0.1** + custom binary format handlers (`DffTagHandler`, `OpusTagHandler`, `ApeTagHandler`):
  - **MP3** (ID3v1 preserved; ID3v2.3 / ID3v2.4 selectable)
  - **FLAC**, **Ogg Vorbis** (`.ogg`/`.oga`), **Opus** (`.opus`, RFC 7845 `OpusHead` + `OpusTags`)
  - **M4A / MP4 / M4B** (MP4 atoms)
  - **WAV** (ID3v2 + RIFF INFO chunks), **AIFF / AIF / AIFC** (embedded ID3v2)
  - **WMA / ASF** (`.wma`/`.asf`)
  - **APE** (Monkey's Audio `.ape`) & **WavPack** (`.wv`) via APEv2 (`APETAGEX`)
  - **DSF** (DSD) & **DFF** (DSDIFF `.dff`, embedded `"ID3 "` chunk in `"FRM8"`, ID3v2.3 / ID3v2.4 selectable)
- versionName 0.5.1 / versionCode 12 (v0.1.0 phase 1, v0.2.1 phase 2, v0.2.2 adaptive UI + hardware scaling, v0.3.0 Home grid + File Manager + Automate, v0.3.1 folder selection + versioned APK/releases, v0.3.2 Theme Screen mode & Design language selector, v0.3.3 roadmap cleanup, v0.4.0 Phase 3 extended audio formats, v0.4.1 audio format conversion added to roadmap, v0.4.2 browser folder summary + in-place tag write fix for DFF/APE, v0.5.0 spectrum analysis, v0.5.1 bit-depth check)

## Locked-in product decisions (do not silently change)

1. **All Files Access** (`MANAGE_EXTERNAL_STORAGE`) — browse/edit any folder like MP3Tag; sideload only.
2. **Both DSF and DFF (DSDIFF) are supported** — DSF via JAudioTagger and DFF via `DffTagHandler` (`"ID3 "` chunk inside `"FRM8"`), both respecting the ID3v2.3 / ID3v2.4 setting.
3. **Local tagging only, for now** — MusicBrainz/Discogs lookup is the next Phase 3 step when the user asks for it.
4. All pickers/menus are **ModalBottomSheets** — NOT DropdownMenu (broken on the device; see gotchas).
5. Editing follows MP3Tag semantics, implemented as a **per-file cached-edit model** (see below).
6. **Home Workspace Grid is the app entry point** — launches into 5 grid tiles (`Tag editor`, `File manager`, `Spectrum analysis`, `Automate`, `Settings`). Only the grid tile for `Settings` is shown on the Home page (no duplicate top-bar gear icon).
7. **Every change goes under a new release number (`versionName` / `versionCode`)** — bump `versionName` and `versionCode` in `app/build.gradle.kts` (and the version string in `SettingsScreen.kt`), tag `v<versionName>`, and let the GitHub Release + APK filename (`mp3tag-mobile-v<versionName>-debug.apk`) reflect that version.
8. **The repo is PUBLIC and must stay clearly marked as an unofficial fan build** — the
   GitHub description, the topics (incl. `unofficial`), and the `[!IMPORTANT]` disclaimer block
   at the top of `README.md` state that this is a fan-made, independent Android implementation
   inspired by Mp3tag for Windows, NOT affiliated with / endorsed by Florian Heidenreich
   (mp3tag.de), containing no original Mp3tag code. Keep that disclaimer intact in any README
   rework; reflect the same "unofficial fan build" wording anywhere the app presents itself
   publicly (e.g. if an About screen is ever added).
9. **Open source under MIT (`LICENSE`)** — code is free to use/modify/fork, including commercial
   use. `LICENSE` is kept as **pure, unmodified MIT text** so GitHub detects and labels it MIT
   (any extra text in the file downgrades detection to "Other"). The trademark & non-affiliation
   disclaimer lives in `README.md` instead (the `[!IMPORTANT]` block and the License section):
   forks must not imply endorsement by, or affiliation with, the original Mp3tag (mp3tag.de)
   or its author.

## Current state (v0.5.1 beta — v0.4.1 and older verified on the device; v0.4.2 and v0.5.x NOT yet device-tested)

**Spectrum analysis** (v0.5.0, bit-depth check v0.5.1; `data/spectrum/`, `ui/spectrum/`, Home tile):
- `PcmDecoder`: decodes a 60 s window from the middle of a file to mono float with MediaExtractor +
  MediaCodec, picked through the system file picker (SAF). Works only for formats Android can decode
  (MP3, AAC, FLAC, Opus, Vorbis, and PCM WAV if the device has a decoder). DSD, APE and WavPack are
  refused with a message.
- `Fft` (radix-2, Hann window, 4096 points) → averaged spectrum, smoothed ±7 bins, and a 480×256 spectrogram.
- `SpectrumAnalyzer`: reference level = median of 1–8 kHz; "top of sound" (cutoff) = highest bin above
  reference − 60 dB, unless the 0.9·Nyquist–Nyquist region is also above that (then full band).
  Hints: likely upsampled (≥88.2 kHz file, content stops at 20.5–22.6 kHz → 44.1 origin, 22.6–24.6 kHz → 48 kHz origin),
  lossy-looking lowpass (16 kHz / 19 kHz / 20 kHz bands; lossless container → WARNING), real content above 22 kHz,
  no lowpass, too quiet.
- `BitDepth`: OR of all decoded integer samples; trailing zero bits = padding. Only claims padding when the
  decoder delivers ≥24-bit (`BitStats`). If the device decodes to 16-bit, the hint is "Bit depth not verifiable".
  `ContainerProbe` reads bit depth from the FLAC STREAMINFO and WAV fmt headers.
- Thresholds were checked only with synthetic signals (`app/src/test/.../spectrum/`, 10 JVM tests). They still
  need calibration on real CD, hi-res, and MP3 files, and on the DX270's MediaCodec output.



**Phase 3 Extended Audio Formats** (`data/tag/TagEngine.kt`, `DffTagHandler.kt`, `OpusTagHandler.kt`, `ApeTagHandler.kt`):
- **DFF / DSDIFF (`.dff`)**: `DffTagHandler` parses the big-endian `FRM8` / `DSD ` / `PROP` (`FS  `, `CHNL`) / `DSD ` audio chunks for sample rate, channels, duration, and bitrate, and reads/writes an embedded `"ID3 "` chunk (respecting the user's ID3v2.3 / ID3v2.4 setting) including cover art.
- **Ogg Opus (`.opus`)**: `OpusTagHandler` parses `OpusHead` (input sample rate, pre-skip) + final Ogg page granule position (`48,000 Hz` granule rate) for exact duration, and reads/writes `OpusTags` (VorbisComment + `METADATA_BLOCK_PICTURE` cover art), re-lacing Ogg pages and recomputing page sequence numbers + `OggCRCFactory` checksums when tag size changes.
- **Monkey's Audio (`.ape`) & WavPack (`.wv`)**: `ApeTagHandler` parses `MAC ` and `wvpk` headers for sample rate/duration/bitrate and reads/writes **APEv2 (`APETAGEX`)** tags at the end of the file (preserving any trailing 128-byte ID3v1 tag), including `Cover Art (front)` binary artwork and `Track`/`Disc` `n/total` splitting.
- **WMA / ASF (`.wma`, `.asf`)**, **AIFF (`.aif`, `.aiff`, `.aifc`)**, and **M4B (`.m4b`)**: wired through JAudioTagger (`AsfTag`, `AiffTag`, `Mp4Tag`) with ID3v2 version conversion fixed for both MP3 and DSF.

**Theme: Screen Mode & Design Language** (`ui/theme/Theme.kt`, `ui/settings/SettingsScreen.kt`, `data/prefs/AppPrefs.kt`):
- Settings → **Theme** section includes two subsections:
  1. **Screen mode**: `Auto (follow system)`, `Dark`, and `Light`.
  2. **Design language**:
     - **Material 3 Expressive** (Google's latest Android 16 expressive design language: spring motion, bold pill geometry, dynamic tonal surfaces)
     - **Material You (Google Dynamic)** (Google's wallpaper-driven dynamic palette with classic M3 geometry)
     - **Liquid Glass** (translucent frosted-glass azure/cyan palette, luminous borders, fluid 38dp capsule shapes)
     - **Halo** (deep OLED-friendly contrast, glowing neon cyan/violet aura accents, crisp cut/angular geometry & monospace labels)

**Home Workspace Grid** (`ui/home/HomeScreen.kt`):
- Launches into a Material 3 Expressive grid dashboard (`2` columns on phones, `3` columns on wide tablets/landscape `>= 900dp`) with cards for:
  1. **Tag editor**: opens the existing MP3Tag folder browser and batch/single tag editor workflow.
  2. **File manager**: opens the full-storage file manager (`ui/filemanager/`).
  3. **Spectrum analysis**: opens `SpectrumScreen` (`ui/spectrum/`) to pick a file and view its spectrogram, cutoff, and hints.
  4. **Automate**: opens the `AutomateScreen` (`ui/automate/`) placeholder for upcoming automated workflows.
  5. **Settings**: opens app settings and returns to whichever workspace launched it (the duplicate top-bar settings icon on the Home screen was removed per user request).

**File Manager** (`ui/filemanager/FileManagerScreen.kt`, `FileManagerViewModel.kt`):
- Full storage file & folder browser across Internal storage, SD card, and USB OTG with interactive breadcrumb path bar, search filter, and adaptive 1-pane / 2-pane layouts.
- Operations: **Copy**, **Move (Cut)** (same-volume fast `renameTo` + cross-volume stream fallback), **Paste** with collision suffixing (`name (2).ext`) and self-subfolder protection, **Rename**, **Delete** (recursive with confirmation), **Create new folder**, **Properties / Details** (recursive folder/file count, byte size, modified timestamp, access flags), **Sort by** (Name, Date, Size, Type; Ascending/Descending), **Show/Hide `.dot` hidden files**, **Invert selection**, and direct **"Edit tags"** jump into `EditorScreen` when audio files are selected.

**Adaptive Form Factors & Orientations** (`ui/common/AdaptiveLayout.kt`):
- **Phone Portrait (`< 600dp` width)**: single-column layouts with `ModalBottomSheet` menus/pickers and bottom selection bar.
- **Phone Landscape & Tablets (`>= 600dp` width)**: 2-pane split layouts across Browser, Editor, Settings, and Tools dialogs:
  - **Browser**: left sidebar holds filter, active selection card (`Edit`/`Clear`), storage volumes, and recent folders; right pane gets 100% height for the file list (and a 2-column grid on large tablets `>= 960dp`).
  - **Editor**: left pane holds file info, cover art, file-inclusion card, and Save button; right pane arranges tag fields in a 2-column grid with `imePadding()`.
  - **Tools dialogs**: side-by-side controls + live preview in landscape/tablet; `rememberSaveable` across all screens/dialogs so rotation never loses typed filters, patterns, or open sheets.

**High-End vs. Low-Spec Hardware Scaling** (`util/DeviceProfile.kt` + Settings override):
- Auto-detects `LOW_SPEC`, `STANDARD`, or `HIGH_END` from RAM, heap class, `isLowRamDevice`, and CPU cores (overridable in Settings → Device & performance).
- **High-end**: bounded parallel tag reading (`4..8` coroutines), `768px` `ARGB_8888` cover thumbnails, larger bitmap `LruCache`.
- **Low-spec**: conservative I/O concurrency (`2`), `256px` `RGB_565` thumbnails (4× less bitmap RAM), OOM-safe fallback decoding, automatic downscaling of oversized imported photos, and `onTrimMemory` cache eviction.
- **All tiers**: O(1) cached hash + batch `CoverImage` deduplication across tracks in a folder (e.g. 30 tracks sharing a 5 MB album cover use 5 MB heap instead of 150 MB), off-main-thread thumbnail decoding, precomputed file sizes in `FolderScanner` (zero `stat()` syscalls during scroll), and fast-path `buildFrom()` on keystrokes.

**Browser**: start at last-visited folder (persists), storage-volume chips (Internal storage / SD
card) + "Choose storage…" sheet (USB-OTG included automatically), natural numeric sort, name
filter, recent-folder chips, overflow menu (select all / clear / refresh / settings).

**Multi-select + editor**: tap files to select, long-press one file to edit it directly.
Folders in Tag Editor also have a **trailing checkbox** (tap the folder row to open it, tap its
checkbox to include all audio files inside that folder recursively in the selection, or
**long-press a folder** to immediately open all its audio files in the tag editor; also available
via "Edit all audio in this folder" in the Browser actions menu).
Editor header shows "N of M files • formats" and opens a **file picker sheet** with checkboxes
(include/exclude files from the current edit; Select all / Deselect all) — MP3Tag's selection
checkboxes. Leaving the editor with pending edits pops an **exit confirmation**
(Discard & quit / Keep editing).

**Tag fields**: core fields always shown (title, artist, album artist, album, year,
track #/total, disc #/total, genre, composer, comment); extra fields via the **"Add field"**
button (fields actually present in the selected files + a curated ~75-field catalog).
Fields with disagreements across the selection show "(mixed — n values)"; every field with
usable values has a **▼ picker sheet** listing the values found in the files — picking one
fills the box (user's explicit choice: fill only, commit happens on Save).

**The edit model (important for any change you make)**: ALL field edits/transforms are cached
**per file, at edit time** (`EditorState.perFileOverrides: Map<FieldKey, Map<path, value>>`,
"" = clear the field on save). Only the files included when the edit is made receive it.
Save writes each included file its own values, clears their caches, but **keeps caches of
excluded files** for later. UI highlights: "was: …" supporting text, tinted container,
"per-file changes pending — Save to apply" indicator. `buildFrom()` in EditorViewModel
derives ALL display state (mixed/options/available/covers) from caches layered over disk
values, scoped to included files — keep that invariant if you touch it.

**Tools** (wrench icon in editor top bar → sheet):
- **Tags from filename**: `%field%` patterns parsed from file names, live per-file preview,
  apply → per-file caches.
- **Rename from tags**: builds new names from current disk tags, preview "old → new",
  Windows-illegal chars sanitized, collision suffix "(2)", immediate rename + browser refresh.
- **Batch actions**: regex replace (with `$1` groups, syntax-checked) or case conversion
  (UPPER/lower/Title) on a chosen field, preview before apply.
Custom patterns used in the first two are auto-saved into the chip strip
(`AppPrefs.savedPatterns`, max 12; built-ins and duplicates never re-saved).

**Cover art**: thumbnail + Replace (system photo picker) / Remove / Undo, embedded on Save;
mixed-cover detection. Section sits at the bottom of the editor (user requested).

## Critical gotchas (do not regress)

- **Spectrum decoding uses MediaCodec only**: DSD, APE and WavPack are refused with a message. The
  bit-depth check only works when the device delivers ≥24-bit PCM; otherwise it reports "not verifiable".
- **DFF and APE tag writes go through `writeSafely()`** (`data/tag/SafeWrite.kt`): they edit a temp copy and
  replace the original only on success. Keep that for any new in-place writer.
- **`DropdownMenu`/popups DO NOT RENDER on the iBasso DX270's ROM** — taps land but no popup
  window is ever created (verified via `dumpsys window`). Always use `ModalBottomSheet`;
  AlertDialog works fine too. This bit us twice.
- **material3 1.4.0 stable marks all Expressive APIs (MaterialExpressiveTheme, MotionScheme,
  LoadingIndicator) `internal`**. The project therefore pins **material3 1.5.0-alpha01**.
  Do NOT bump to 1.5.0-beta01 blindly: it pulls Compose UI 1.12 → requires AGP 9.1+ and
  compileSdk 37; the current toolchain is AGP 8.13.2 / compileSdk 36.
- **JAudioTagger 3.0.1 API quirks**: call `getID3v2Tag()`/`setID3v2Tag()` explicitly (Kotlin
  property syntax fails — two setter overloads); convert versions via
  `AudioFile.convertID3Tag(tag, ID3V2Version.ID3_V23 / ID3_V24)`; artwork must use
  `ArtworkFactory.getNew()` with `TagOptionSingleton.getInstance().isAndroid = true` set once
  (no java.awt on Android). WAV has no per-chunk write flags in 3.0.1 — it always writes both
  ID3 and INFO chunks (matches MP3Tag default; the old WavTagMode setting was removed because of this).
- **`AudioFileIO.read` on a file whose format's reader can throw KeyNotFoundException for
  unsupported FieldKeys** — every `getFirst()` must be wrapped in runCatching. Unsupported keys
  set in `setField` fail per-file and surface in the save-results dialog (acceptable).
- **Bottom-sheet rows near the very bottom edge of the screen (~y > 1950 on 1080x2160) sit over
  the scrim** — scripted taps there dismiss the sheet instead of clicking the row.
- **LazyColumn inside a ModalBottomSheet misbehaved on the device** (row taps dismissed the
  sheet); sheets use plain `Column` + `verticalScroll` everywhere — keep it that way.

## Toolchain on this machine (nothing global is installed — JDK/SDK/git/gh are all portable)

- JDK 21: `%LOCALAPPDATA%\mp3tag-tooling\jdk-21.0.12.1+1`
- Android SDK (platform 36, build-tools, platform-tools): `%LOCALAPPDATA%\mp3tag-tooling\sdk`
- Portable git: `%LOCALAPPDATA%\mp3tag-tooling\cmd\git.exe` (add its dir to `$env:PATH` for any
  command that shells out to git)
- GitHub CLI: `%LOCALAPPDATA%\mp3tag-tooling\bin\gh.exe` — needs git in PATH too; logged in as
  `areyouanand` (token in Windows keyring, scopes incl. repo+workflow; re-auth via
  `gh auth login`/`gh auth refresh -s workflow` device flow if it ever lapses)
- `local.properties` holds `sdk.dir` (git-ignored, machine-specific)
- Build: `$env:JAVA_HOME = "<JDK above>"; $env:ANDROID_HOME = "<SDK above>"; .\gradlew.bat assembleDebug`
- Install/launch on device: `adb install -r <apk>` → `adb shell am start -n com.mp3tag.mobile/.MainActivity`

## Target device

- **iBasso DX270** Android DAP, Android 12 (12.1), 1080x2160, USB debugging on.
- Internal storage `/storage/emulated/0`; SD card at `/storage/6539-3462` — the user's music
  library (FLAC albums, DSD folder) lives on the SD card.
- All-files access was re-granted after the CI keystore swap; if the gate screen shows up,
  have the user grant it once more.
- The user often tests on the touchscreen WHILE the agent works — scripted input and user
  input will collide; keep automated UI runs short and re-dump before each tap.

## CI / installs from anywhere

- **Versioning rule**: every logical change must bump `versionName` and `versionCode` in `app/build.gradle.kts` (and `SettingsScreen.kt`).
- `base.archivesName.set("mp3tag-mobile-v$versionName")` in `app/build.gradle.kts` names the built APK **`mp3tag-mobile-v<versionName>-debug.apk`** (e.g. `app/build/outputs/apk/debug/mp3tag-mobile-v0.3.1-debug.apk`).
- `.github/workflows/build.yml`: on every push to `master` (or `v*` tag / manual dispatch), extracts `versionName` & `versionCode` from `app/build.gradle.kts`, builds `mp3tag-mobile-v<versionName>-debug.apk`, uploads it as a workflow artifact, and creates/updates the versioned GitHub Release **`v<versionName>`** (`--latest`) with `mp3tag-mobile-v<versionName>-debug.apk` attached:
  `https://github.com/areyouanand/mp3tag-mobile/releases/latest`
- **`app/debug.keystore` is committed** — shared DEBUG signing config for local AND CI builds,
  so all APKs install over each other without uninstalling. Do not git-ignore or delete it;
  it's a debug key only, not a production secret.
- To re-verify phone updateability over USB (no uninstall needed anymore): `adb install -r app\build\outputs\apk\debug\mp3tag-mobile-v<versionName>-debug.apk`.

- **Beta channel**: the `beta` branch is also built by `.github/workflows/build.yml`. Those builds are tagged
  `v<versionName>-beta`, published as **prereleases**, and never marked Latest. `master` stays the stable channel.
  Current beta: `v0.5.1-beta`.

## Workflow conventions

- Update `HANDOFF.md` + `README.md` status tables at the end of any session; commit + push
- Branches: day-to-day work and beta releases go on `beta`; `master` gets merged from `beta` once a build is verified on the device
  (message per logical change). Every push green-lights CI automatically.
- Feature work lives in `app/src/main/java/com/mp3tag/mobile/`:
  - `MainActivity.kt` — activity, AppViewModel (nav: `Home` / `Browser` (Tag editor) / `FileManager` / `Automate` / `Settings(returnTo)` / `Editor(paths, session, returnTo)`, permission, theme, performance mode)
  - `data/fs/FolderScanner.kt` — natural sorting, directory listing, recursive audio file collection (`collectAudioFilesRecursively`)
  - `data/prefs/AppPrefs.kt` — settings, recent folders, last folders (`lastFolder` + `lastFileManagerFolder`), `showHiddenFiles`, saved patterns
  - `data/tag/TagData.kt`, `TagEngine.kt`, `DffTagHandler.kt`, `OpusTagHandler.kt`, `ApeTagHandler.kt` — models, JAudioTagger engine, and custom DFF/Opus/APEv2 binary handlers
  - `ui/home/` (`HomeScreen`), `ui/filemanager/` (`FileManagerScreen`, `FileManagerViewModel`),
    `ui/automate/` (`AutomateScreen`), `ui/browser/`, `ui/editor/` (`EditorScreen`, `EditorViewModel`,
    `ToolsDialogs`), `ui/settings/`, `ui/common/` (`Common.kt`, `AdaptiveLayout.kt`),
    `ui/theme/Theme.kt` (M3 Expressive), `util/` (`DeviceProfile.kt`, `Format.kt` + `ImageDecode`,
    `PatternParser.kt`)
- No tests exist yet; verification = build green + install on device + uiautomator checks.

## Roadmap

- **Audio format conversion** (blocked, not started in the repo): the prebuilt FFmpegKit binaries were removed from Maven Central and GitHub (only source remains). Plan: build LGPL FFmpeg from the archived ffmpeg-kit source with the NDK, which needs WSL Ubuntu or MSYS2 with make/nasm/pkg-config. This machine has neither yet. A MediaCodec-only fallback was also discussed. It can't output MP3 or decode DSD/APE/WavPack. Goal: convert/transcode audio files between formats (e.g. FLAC/WAV/DSD/APE/WV ↔ MP3/Opus/M4A/FLAC/WAV) while preserving metadata tags and embedded cover art.
- **Phase 3 remaining**: MusicBrainz + Cover Art Archive + Discogs online metadata and cover lookup.
- **Automate workspace**: user requested the `Automate` section on the Home grid as a placeholder to be populated with automated tagging/file workflows in a future session.
- **Free-form custom fields**: possibly support custom tag keys (`TXXX` / Vorbis freeform / MP4 `----:` atoms / APEv2 custom items) beyond the standard ~75-field catalog.
- Larger libraries: WorkManager for background batch saves, paging for file list.
- Persist unsaved edit cache across process death (currently lost on app kill) — user was told;
  may come up.
- UX ideas from user: show which files keep which values after a batch save; per-file editing
  launched from the batch screen.
