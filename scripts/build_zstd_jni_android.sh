#!/usr/bin/env bash
# Builds zstd-jni's natives for Android arm64.
#
# Distant Horizons compresses its LOD database with zstd-jni, and the only arm64
# Linux library that jar carries links against glibc. Android's bionic cannot load
# it, so DH stops Minecraft during startup. The launcher passes this build to the
# game through zstd-jni's ZstdNativePath property instead.
#
# DH relocates zstd-jni into the `dhcomgithubluben` package, so the JNI entry points
# are compiled twice: once under DH's package and once under zstd-jni's own, for
# mods that bundle it without relocating.
#
# Usage: build_zstd_jni_android.sh <zstd-jni checkout> <output .so>
set -eu

SRC=$1
OUT=$2
NDK=${ANDROID_NDK_HOME:-${ANDROID_HOME:?set ANDROID_NDK_HOME or ANDROID_HOME}/ndk/28.1.13356709}
BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
CC=${ZSTD_JNI_CC:-$BIN/aarch64-linux-android29-clang}
NM=${ZSTD_JNI_NM:-$BIN/llvm-nm}

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp -r "$SRC/src/main/native" "$WORK/native"
mkdir "$WORK/dh"
for file in "$WORK"/native/jni_*.c; do
  # The sequence producer callbacks are the only non-static functions besides JNI entry points.
  sed -e 's/com_github_luben/dhcomgithubluben/g' -e 's#com/github/luben#dhcomgithubluben#g' \
    -e 's/\b\(stub\|builtin\)SequenceProducer\b/dh_\1SequenceProducer/g' \
    "$file" > "$WORK/dh/$(basename "$file")"
done
cat > "$WORK/exports.map" <<'MAP'
{
  global: Java_com_github_luben_zstd*; Java_dhcomgithubluben_zstd*;
  local: *;
};
MAP

mkdir -p "$(dirname "$OUT")"
# zstd's only assembly is an x86-64 Huffman decoder; ZSTD_DISABLE_ASM keeps the C path.
mapfile -d '' SOURCES < <(find "$WORK/native" "$WORK/dh" -name '*.c' -print0)
"$CC" -shared -fPIC -O2 -std=c99 -DZSTD_LEGACY_SUPPORT=4 -DZSTD_MULTITHREAD=1 -DZSTD_DISABLE_ASM \
  -I"$WORK/native" -Wl,--version-script="$WORK/exports.map" -Wl,-Bsymbolic \
  -Wl,-z,relro,-z,now -Wl,-z,max-page-size=16384 -s -o "$OUT" "${SOURCES[@]}"

for prefix in Java_com_github_luben_zstd_Zstd_compress Java_dhcomgithubluben_zstd_Zstd_compress; do
  "$NM" -D --defined-only "$OUT" | grep -q " $prefix" || { echo "$OUT lacks $prefix" >&2; exit 1; }
done
