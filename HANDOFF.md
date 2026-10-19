# HANDOFF — MP3Tag Mobile

Read this first if you're picking the project up in a new session. Everything important that is
NOT obvious from the code is documented here.

## What this is

A personal-use Android tag editor that mirrors the workflow of MP3Tag for Windows.
Single-user app, sideloaded, not for Play Store. Working directory = project root.

- Language/UI: Kotlin + Jetpack Compose, Material 3 Expressive
- minSdk 31 (Android 12), target/compile 36, JDK 17 target
- Tag engine: JAudioTagger 3.0.1 (pure Java) — MP3, FLAC, M4A/MP4, Ogg, WAV, DSF (DSD)

## Locked-in product decisions (do not silently change)

1. **All Files Access** (`MANAGE_EXTERNAL_STORAGE`) — browse/edit anywhere like MP3Tag. Sideload only.
2. **DSF first, DFF later** — DSF (DSD Stream File, ID3v2 inside) works via JAudioTagger.
   DFF (DSDIFF) is NOT supported yet; plan: TagLib 2.x via NDK/JNI (Phase 3).
3. **Local tagging first** — MusicBrainz/Discogs lookup deferred to Phase 2/3.
4. Batch editing follows MP3Tag semantics: fields differing across the selection show "(mixed)",
   stay untouched unless edited; picking a value from a field's picker FILLS the field only —
   the user commits with Save (applies to all selected files).

## Current state (v0.2.0, verified)

Built and running on the target device. Phase 1 complete: folder browser (natural sort, filter,
recents, storage-volume chips + "Choose storage…" sheet), multi-select batch editor, per-field
value pickers (▼ → bottom sheet of values found in files; any mixed field with ≥1 value gets a
picker), "Add field" sheet (fields found in files + ~75 standard fields), cover art
replace/remove, ID3v2.3/v2.4 setting, save with per-file results.

Phase 2 (in): **Tools menu** in the editor (wrench icon → bottom sheet) with
- **Tags from filename** (`%artist% - %title%` patterns, live preview, per-file apply)
- **Rename from tags** (immediate rename with preview, sanitization, collision suffixes "(2)")
- **Batch actions** (regex replace with `$1` groups, case conversion: UPPER/lower/Title)
All three write **per-file overrides** layered onto the batch model (`perFileOverrides` in
EditorState; shown as "per-file changes pending — Save to apply"; Save writes each file its own
values). Also: app remembers last-visited folder on start. CSV export/import not done yet.

**Editor file picker** (MP3Tag-style): the editor header ("Edit tags ▼") opens a sheet listing
all files in the session with checkboxes — include/exclude files from the current edit, plus
Select all / Deselect all. Implemented via `excludedPaths` in EditorState.

**Editor edit model** (v0.2.1): every field edit is cached **per file at edit time** — only the
files included when you make the change receive it (`perFileOverrides`), and they stay cached
until **Save** or **Discard**. Re-including a file later does NOT inherit edits made while it was
excluded; mixed/value displays recompute over included files with caches layered over disk
values. Unsaved edits trigger an **exit confirmation** ("Discard & quit / Keep editing") when
leaving the editor (back arrow or system back). Files excluded before Save keep their cached
edits after the save.

**Device UI-testing note**: bottom-sheet rows near the very bottom edge (~y>1950 on 1080x2160)
sit over the scrim — taps there dismiss the sheet instead of clicking the row. Tap higher rows
when scripting UI tests.

## Critical gotchas discovered (do not regress)

- **`DropdownMenu` popups DO NOT RENDER on the iBasso DX270's ROM.** Taps land, no popup window
  is ever created (verified via `dumpsys window`). Rule: use `ModalBottomSheet` for any
  value/menu picker on this device. AlertDialog works fine.
- **material3 1.4.0 stable has the Expressive APIs (MaterialExpressiveTheme, MotionScheme,
  LoadingIndicator) marked `internal`** — unusable. That's why the project uses
  **material3 1.5.0-alpha01**. Do not bump to 1.5.0-beta01 blindly: it pulls Compose UI 1.12
  which requires AGP 9.1+ and compileSdk 37 (project is on AGP 8.13.2 / compileSdk 36).
- **JAudioTagger 3.0.1 API specifics**: use explicit `getID3v2Tag()` / `setID3v2Tag()` calls
  (property syntax fails due to overloads); ID3 version conversion via
  `AudioFile.convertID3Tag(tag, ID3V2Version.ID3_V23 / ID3_V24)`; artwork via
  `ArtworkFactory.getNew()` + `TagOptionSingleton.getInstance().isAndroid = true` (no java.awt);
  WAV has no per-chunk write flags in 3.0.1 — it always writes both ID3 and INFO chunks
  (matches MP3Tag default; the old WavTagMode setting was removed for this reason).
- `TagSnapshot.fields` contains EVERY standard FieldKey found with a value (not just the core
  display set) — the editor's "found in these files" logic depends on it.

## Toolchain on this machine

- JDK 21: `%LOCALAPPDATA%\mp3tag-tooling\jdk-21.0.12.1+1`
- Android SDK (API 36 + build-tools + platform-tools): `%LOCALAPPDATA%\mp3tag-tooling\sdk`
- `adb`: `%LOCALAPPDATA%\mp3tag-tooling\sdk\platform-tools\adb.exe`
- `local.properties` points at the SDK (machine-specific, git-ignored — recreate if missing)
- Build: `JAVA_HOME=<jdk above>; .\gradlew.bat assembleDebug` from project root
- APK: `app\build\outputs\apk\debug\app-debug.apk`
- Install on device: `adb install -r <apk>` then `adb shell am start -n com.mp3tag.mobile/.MainActivity`

## Target device

- **iBasso DX270** Android DAP, Android 12, screen 1080x2160, USB debugging on.
- Internal storage `/storage/emulated/0`; SD card mounted at `/storage/6539-3462`
  (user's music library lives on the SD card). All-files access already granted.
- The user tests live on the touchscreen while the agent works — beware of interfering taps.

## UI verification trick (no screenshots needed)

The image reader can't display device screenshots, but this works great:

```
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml
```

Then parse the XML for `text=`/`content-desc=` + `bounds="..."` nodes; tap things with
`adb shell input tap <x> <y>` (center of bounds). Also useful: `adb shell "dumpsys window windows | grep 'Window #'"`.
logcat is flaky to clear on this device (`logcat -c` fails) — use `logcat -d -t 300`.

## Roadmap

- **Phase 2** (next): tags-from-filename patterns (`%artist% - %title%`), rename-from-tags,
  batch actions (regex replace, case conversion), tag export/import (CSV), maybe custom
  free-form fields (TXXX / Vorbis freeform / MP4 ---- atoms).
- **Phase 3**: DFF (DSDIFF) via TagLib 2.x JNI (supports DSDIFF since TagLib 2.0, latest 2.3.2),
  Opus/WMA/APE/WavPack, MusicBrainz + Cover Art Archive + Discogs lookup.
- UX ideas discussed: per-file editing from the batch screen; show which files keep which values
  after a batch save; remember last-visited volume on start.
