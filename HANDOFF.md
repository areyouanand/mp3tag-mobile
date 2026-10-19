# HANDOFF — MP3Tag Mobile

**You are taking over an active, working project.** Read this first — it documents everything
that is NOT obvious from the code. The whole project lives in this directory (= git repo,
remote: `github.com/areyouanand/mp3tag-mobile`, private, push access via GitHub CLI device auth).

## What this is

A personal-use Android tag editor that mirrors the workflow of MP3Tag for Windows.
Single user, sideloaded, never going to the Play Store.

- Kotlin + Jetpack Compose, **Material 3 Expressive** UI (dynamic Material You color)
- minSdk 31 (Android 12), target/compile SDK 36, AGP 8.13.2, Kotlin 2.4.21, Gradle 8.14.4 (wrapper)
- Tag engine: **JAudioTagger 3.0.1** (pure Java): MP3 (ID3v1 preserved; v2.3/v2.4 selectable),
  FLAC, M4A/MP4, Ogg Vorbis, **WAV (ID3 + RIFF INFO chunks)**, **DSF (DSD, embedded ID3v2)**
- versionName 0.2.1 / versionCode 2 (two debug builds so far: v0.1.0 phase 1, v0.2.1 phase 2)

## Locked-in product decisions (do not silently change)

1. **All Files Access** (`MANAGE_EXTERNAL_STORAGE`) — browse/edit any folder like MP3Tag; sideload only.
2. **DSF works; DFF does not yet** — DSF (DSD Stream File) is handled by JAudioTagger.
   DFF (DSDIFF) is Phase 3, via TagLib 2.x through NDK/JNI.
3. **Local tagging only, for now** — MusicBrainz/Discogs lookup is Phase 3, not sooner (user chose this).
4. All pickers/menus are **ModalBottomSheets** — NOT DropdownMenu (broken on the device; see gotchas).
5. Editing follows MP3Tag semantics, implemented as a **per-file cached-edit model** (see below).

## Current state (v0.2.1 — everything below is verified working on the device)

**Browser**: start at last-visited folder (persists), storage-volume chips (Internal storage / SD
card) + "Choose storage…" sheet (USB-OTG included automatically), natural numeric sort, name
filter, recent-folder chips, overflow menu (select all / clear / refresh / settings).

**Multi-select + editor**: tap files to select, long-press one file to edit it directly.
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

- `.github/workflows/build.yml`: builds the APK on every push to `master` (+ manual dispatch),
  uploads a per-run artifact, and updates a rolling **`latest` prerelease** so there is a
  permanent phone-downloadable URL:
  `https://github.com/areyouanand/mp3tag-mobile/releases/latest/download/mp3tag-mobile-debug.apk`
- Pushing a `v*` tag creates a versioned release (`--latest` flagged).
- **`app/debug.keystore` is committed** — shared DEBUG signing config for local AND CI builds,
  so all APKs install over each other without uninstalling. Do not git-ignore or delete it;
  it's a debug key only, not a production secret.
- To re-verify phone updateability over USB (no uninstall needed anymore): `adb install -r <apk>`.

## Workflow conventions

- Update `HANDOFF.md` + `README.md` status tables at the end of any session; commit + push
  (message per logical change). Every push green-lights CI automatically.
- Feature work lives in `app/src/main/java/com/mp3tag/mobile/`:
  - `MainActivity.kt` — activity, AppViewModel (nav: Browser/Settings/Editor, permission, theme)
  - `data/fs/` folder scanner · `data/prefs/AppPrefs.kt` settings/recents/patterns
  - `data/tag/TagData.kt` + `TagEngine.kt` — models + JAudioTagger engine
  - `ui/browser/`, `ui/editor/` (EditorScreen, EditorViewModel, ToolsDialogs), `ui/settings/`,
    `ui/common/Common.kt`, `ui/theme/Theme.kt` (M3 Expressive), `util/` (Format, ImageDecode,
    PatternParser)
- No tests exist yet; verification = build green + install on device + uiautomator checks.

## Roadmap

- **Phase 2 remaining**: tag export/import (CSV) — export selected/folder's tags via SAF
  CreateDocument, import by filename match. Possibly free-form custom fields
  (TXXX / Vorbis freeform / MP4 `----:` atoms) beyond the standard catalog.
- **Phase 3**: DFF (DSDIFF) via TagLib 2.x JNI (TagLib 2.0+ supports DSDIFF; latest 2.3.2;
  "Fix building with Android NDK 29" in 2.2 → NDK-supported), Opus, WMA/ASF, APE, WavPack;
  MusicBrainz + Cover Art Archive + Discogs (user still wants local-first until they say otherwise).
- Larger libraries: WorkManager for background batch saves, paging for file list.
- Persist unsaved edit cache across process death (currently lost on app kill) — user was told;
  may come up.
- UX ideas from user: show which files keep which values after a batch save; per-file editing
  launched from the batch screen.
