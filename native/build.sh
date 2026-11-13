#!/usr/bin/env bash
# Builds the JNI transcoder (app/src/main/jniLibs/<abi>/libmp3tagffmpeg.so)
# against a prebuilt FFmpeg + libmp3lame prefix.
#
#   ANDROID_NDK_HOME=/c/Android/Sdk/ndk/28.2.13676358 \
#   FFMPEG_PREFIX=/c/.../mp3tag-tooling/out \
#   native/build.sh arm64-v8a x86_64
#
# The FFmpeg prefix itself is produced by native/build-ffmpeg.sh.
set -euo pipefail

: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to the NDK root}"
: "${FFMPEG_PREFIX:?set FFMPEG_PREFIX to the dir holding ffmpeg/<abi> and lame/<abi>}"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST_TAG=windows-x86_64
if [ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64" ]; then
  HOST_TAG=linux-x86_64
elif [ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64" ]; then
  HOST_TAG=darwin-x86_64
fi
TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
API=31

for ABI in "$@"; do
  case "$ABI" in
    arm64-v8a) TRIPLE=aarch64-linux-android ;;
    x86_64)    TRIPLE=x86_64-linux-android ;;
    *) echo "unsupported abi: $ABI"; exit 1 ;;
  esac

  OUTDIR="$REPO_ROOT/app/src/main/jniLibs/$ABI"
  mkdir -p "$OUTDIR"
  echo "=== $ABI ==="
  "$TC/bin/${TRIPLE}${API}-clang" -O2 -fPIC -Wall -Wextra \
    -shared \
    -o "$OUTDIR/libmp3tagffmpeg.so" "$REPO_ROOT/native/ffmpeg_jni.c" \
    -I"$FFMPEG_PREFIX/ffmpeg/$ABI/include" \
    -Wl,--start-group \
    "$FFMPEG_PREFIX/ffmpeg/$ABI/lib/libavformat.a" \
    "$FFMPEG_PREFIX/ffmpeg/$ABI/lib/libavcodec.a" \
    "$FFMPEG_PREFIX/ffmpeg/$ABI/lib/libswresample.a" \
    "$FFMPEG_PREFIX/ffmpeg/$ABI/lib/libavutil.a" \
    "$FFMPEG_PREFIX/lame/$ABI/lib/libmp3lame.a" \
    "$FFMPEG_PREFIX/opus/$ABI/lib/libopus.a" \
    -Wl,--end-group -lm -llog -ldl -Wl,-z,max-page-size=16384
  "$TC/bin/llvm-strip" --strip-unneeded "$OUTDIR/libmp3tagffmpeg.so"
  ls -lh "$OUTDIR/libmp3tagffmpeg.so"
done
