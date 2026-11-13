# native/ — bundled FFmpeg transcoder

MP3Tag Mobile converts audio through a **statically linked LGPL FFmpeg build**
wrapped by a tiny JNI layer (`ffmpeg_jni.c`). The final `.so` is committed to
`app/src/main/jniLibs/{arm64-v8a,x86_64}/libmp3tagffmpeg.so`, so normal Gradle/CI
builds need no NDK work at all.

## Why a custom build

- `ffmpeg-kit` was removed from Maven Central and GitHub (only source remains), so
  there is no maintained prebuilt artifact to depend on.
- FFmpeg 8.0 **no longer ships a DSDIFF (`.dff`) demuxer** — `ffmpeg_jni.c` contains
  a minimal one that finds the `"DSD "` chunk inside `FRM8` and feeds the raw
  MSB-first DSD stream to the `dsd_msbf` decoder.
- Build is **LGPL only** (`--disable-gpl --disable-nonfree --disable-version3`).
  MP3 encoding comes from LAME (LGPL 2.1), Opus from libopus (BSD). No libfdk_aac,
  no `--enable-gpl` components.

| Component | Version | License |
|-----------|---------|---------|
| FFmpeg (libavformat/libavcodec/libswresample/libavutil) | 8.0.3 | LGPL 2.1+ |
| libmp3lame | 3.100 | LGPL 2.1 |
| libopus | 1.5.2 | BSD 3-clause |

Build flags of note:

```
--target-os=android --arch=aarch64|x86_64 --enable-cross-compile
--disable-avdevice --disable-avfilter --disable-swscale
--disable-network --disable-autodetect
--extra-cflags="-O3 -fPIC -fvisibility=hidden ..."
```

`-fvisibility=hidden` is required on arm64: without it `libavutil/aarch64/tx_float_neon.S`
produces non-PIC relocations against `ff_tx_tab_*` and the final link fails with
`R_AARCH64_ADR_PREL_PG_HI21 ... recompile with -fPIC`.

## Rebuilding

Prerequisites: Android NDK r28 (28.2.13676358), a POSIX shell with `make`, `nasm`,
and a host C compiler (MSYS2 on Windows: `pacman -S make diffutils nasm pcconf gcc`).

```bash
# 1. Download + extract the sources into SRC_DIR
#    ffmpeg-8.0.3.tar.xz, lame-3.100.tar.gz, opus-1.5.2.tar.gz

# 2. Build the static libraries (once per NDK/FFmpeg upgrade)
ANDROID_NDK_HOME=/c/Android/Sdk/ndk/28.2.13676358 \
SRC_DIR=/c/ffmpeg-src OUT_DIR=/c/ffmpeg-out \
bash native/build-ffmpeg.sh arm64-v8a x86_64

# 3. Build the JNI wrapper into the app
ANDROID_NDK_HOME=/c/Android/Sdk/ndk/28.2.13676358 \
FFMPEG_PREFIX=/c/ffmpeg-out \
bash native/build.sh arm64-v8a x86_64
```

`native/build.sh` strips the result and links with
`-Wl,-z,max-page-size=16384` (16 KB page-size devices). Re-run it after any change
to `ffmpeg_jni.c` and commit the refreshed `.so` files.

## JNI surface

`com.mp3tag.mobile.data.convert.FfmpegBridge`:

```
nativeTranscode(in, out, muxer, encoder, bitrateKbps, sampleRateHz, channels, quality, callback) -> Int
nativeCancel()
nativeLastError() -> String
nativeVersion() -> String
```

Return codes: `0` ok, `-1` error (message via `nativeLastError()`), `-2` cancelled.
Progress is pushed through `ProgressCallback.onProgress(0..100)` at most every 200 ms.

Supported output formats/encoders are listed in
`data/convert/ConversionOptions.kt`; inputs are whatever FFmpeg can demux, plus
`.dff` through the in-house DSDIFF reader (DST-compressed files are rejected).
