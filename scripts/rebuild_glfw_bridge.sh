#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
jar_file="$root/third_party/Pojlib/src/main/assets/lwjgl/lwjgl-glfw-classes.jar"
lib_dir="$root/third_party/Pojlib/jre_lwjgl3glfw/libs"
android_jar="${ANDROID_HOME:?ANDROID_HOME is required}/platforms/android-35/android.jar"
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT

# Rebuild the JVM GLFW class before the Android plugin packages its runtime assets.
javac --release 8 -cp "$jar_file:$lib_dir/*:$android_jar" -d "$output" \
    "$root/third_party/Pojlib/jre_lwjgl3glfw/src/main/java/org/lwjgl/glfw/GLFW.java"
(cd "$output" && jar uf "$jar_file" org/lwjgl/glfw/GLFW.class)
