#!/usr/bin/env bash
# Builds the static FFmpeg + libmp3lame + libopus libraries that
# app/src/main/jniLibs/<abi>/libmp3tagffmpeg.so links against.
#
# Usage (MSYS2 on Windows, or any Linux/macOS with a POSIX shell + make + nasm):
#
#   ANDROID_NDK_HOME=/c/Android/Sdk/ndk/28.2.13676358 \
#   SRC_DIR=/c/ffmpeg-src OUT_DIR=/c/ffmpeg-out \
#   native/build-ffmpeg.sh arm64-v8a x86_64
#
# SRC_DIR must contain the extracted sources:
#   ffmpeg-8.0.3/  (https://ffmpeg.org/releases/ffmpeg-8.0.3.tar.xz)
#   lame-3.100/    (https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz)
#   opus-1.5.2/    (https://downloads.xiph.org/releases/opus/opus-1.5.2.tar.gz)
#
# The result lands in OUT_DIR/{ffmpeg,lame,opus}/<abi>/ and is consumed by
# native/build.sh. Only LGPL-compatible components are enabled: no GPL, no
# non-free, MP3 encoding through LAME (LGPL), Opus through libopus (BSD).
set -euo pipefail

: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to the NDK root}"
: "${SRC_DIR:?set SRC_DIR to the dir holding ffmpeg-8.0.3, lame-3.100 and opus-1.5.2}"
OUT_DIR=${OUT_DIR:-$PWD/ffmpeg-out}
API=${API:-31}
JOBS=${JOBS:-$(nproc 2>/dev/null || echo 8)}

HOST_TAG=linux-x86_64
case "$(uname -s)" in
  *NT*) HOST_TAG=windows-x86_64 ;;
  Darwin*) HOST_TAG=darwin-x86_64 ;;
esac
TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
FFMPEG_VER=8.0.3

abictx() {
  case "$1" in
    arm64-v8a) ARCH=aarch64; TRIPLE=aarch64-linux-android; CPU=armv8-a ;;
    x86_64)    ARCH=x86_64;  TRIPLE=x86_64-linux-android;  CPU=x86-64  ;;
    *) echo "unsupported abi: $1"; exit 1 ;;
  esac
  CC="$TC/bin/${TRIPLE}${API}-clang"
  CXX="$TC/bin/${TRIPLE}${API}-clang++"
}

build_lame() {
  local abi=$1 dir="$OUT_DIR/build/lame-$abi"
  echo "=== lame $abi ==="
  rm -rf "$dir"; mkdir -p "$dir"; cd "$dir"
  "$SRC_DIR/lame-3.100/configure" \
    --prefix="$OUT_DIR/lame/$abi" --host="$TRIPLE" \
    --enable-static --disable-shared --disable-frontend --disable-decoder \
    CC="$CC" AR="$TC/bin/llvm-ar" RANLIB="$TC/bin/llvm-ranlib" \
    CFLAGS="-O2 -fPIC -DNDEBUG" > configure.log 2>&1
  make -j"$JOBS" > make.log 2>&1
  make install > install.log 2>&1
  ls "$OUT_DIR/lame/$abi/lib/libmp3lame.a"
}

build_opus() {
  local abi=$1 dir="$OUT_DIR/build/opus-$abi"
  echo "=== opus $abi ==="
  rm -rf "$dir"; mkdir -p "$dir"; cd "$dir"
  "$SRC_DIR/opus-1.5.2/configure" \
    --prefix="$OUT_DIR/opus/$abi" --host="$TRIPLE" \
    --enable-static --disable-shared --disable-doc --disable-extra-programs \
    CC="$CC" AR="$TC/bin/llvm-ar" RANLIB="$TC/bin/llvm-ranlib" \
    CFLAGS="-O2 -fPIC -DNDEBUG" > configure.log 2>&1
  make -j"$JOBS" > make.log 2>&1
  make install > install.log 2>&1
  ls "$OUT_DIR/opus/$abi/lib/libopus.a"
}

build_ffmpeg() {
  local abi=$1 dir="$OUT_DIR/build/ffmpeg-$abi"
  echo "=== ffmpeg $abi ==="
  rm -rf "$dir"; mkdir -p "$dir"; cd "$dir"
  export PKG_CONFIG_PATH="$OUT_DIR/opus/$abi/lib/pkgconfig:$OUT_DIR/lame/$abi/lib/pkgconfig"
  local extra=()
  if [ "$abi" = "x86_64" ]; then extra+=(--enable-x86asm); fi

  "$SRC_DIR/ffmpeg-$FFMPEG_VER/configure" \
    --pkg-config="pkg-config --static" \
    --prefix="$OUT_DIR/ffmpeg/$abi" \
    --target-os=android --arch="$ARCH" --cpu="$CPU" \
    --enable-cross-compile \
    --cc="$CC" --cxx="$CXX" \
    --ar="$TC/bin/llvm-ar" --nm="$TC/bin/llvm-nm" --ranlib="$TC/bin/llvm-ranlib" \
    --strip="$TC/bin/llvm-strip" --sysroot="$TC/sysroot" \
    ${extra[@]+"${extra[@]}"} \
    --enable-static --disable-shared --enable-pic \
    --disable-programs --disable-doc --disable-debug \
    --disable-avdevice --disable-avfilter --disable-swscale \
    --disable-network --disable-autodetect \
    --disable-gpl --disable-nonfree --disable-version3 \
    --enable-libmp3lame --enable-libopus \
    --extra-cflags="-O3 -fPIC -fvisibility=hidden -I$OUT_DIR/lame/$abi/include -I$OUT_DIR/opus/$abi/include -D__ANDROID_API__=$API" \
    --extra-ldflags="-L$OUT_DIR/lame/$abi/lib -L$OUT_DIR/opus/$abi/lib -lm" \
    > configure.log 2>&1
  make -j"$JOBS" > make.log 2>&1
  make install > install.log 2>&1
  ls "$OUT_DIR/ffmpeg/$abi/lib/libavcodec.a"
}

for abi in "$@"; do
  abictx "$abi"
  build_lame "$abi"
  build_opus "$abi"
  build_ffmpeg "$abi"
done
echo "DONE -> $OUT_DIR"
