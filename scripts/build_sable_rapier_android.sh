#!/usr/bin/env bash
# Builds Sable's Rapier physics natives for Android arm64.
#
# Sable (required by Create 6) ships its Rust physics library only for desktop
# platforms, and unpacks it into the game directory on shared storage, where
# Android refuses to load native code. SableNativeFix makes Sable load this build
# from the APK's native library directory instead. The JNI interface changes
# between Sable releases, so the checkout must match SableNativeFix.SUPPORTED_VERSION.
#
# Usage: build_sable_rapier_android.sh <sable checkout> <output .so>
set -eu

SRC=$(cd "$1" && pwd)
OUT=$2
NDK=${ANDROID_NDK_HOME:-${ANDROID_HOME:?set ANDROID_NDK_HOME or ANDROID_HOME}/ndk/28.1.13356709}
BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
RUST=$SRC/sable_rapier/src/main/rust
TOOLCHAIN=$(sed -n 's/^channel = "\(.*\)"/\1/p' "$RUST/rust-toolchain.toml")

rustup toolchain install "$TOOLCHAIN" --profile minimal --target aarch64-linux-android
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$BIN/aarch64-linux-android29-clang
export CC_aarch64_linux_android=$BIN/aarch64-linux-android29-clang
export AR_aarch64_linux_android=$BIN/llvm-ar
# Quest firmware expects native libraries aligned to 16 KiB pages.
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"
(cd "$RUST/rapier" && cargo +"$TOOLCHAIN" build --release --locked --target aarch64-linux-android)

mkdir -p "$(dirname "$OUT")"
cp "$RUST/target/aarch64-linux-android/release/libsable_rapier.so" "$OUT"
"$BIN/llvm-nm" -D --defined-only "$OUT" | grep -q ' Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_initialize$' || {
  echo "$OUT lacks Sable's JNI entry points" >&2
  exit 1
}
