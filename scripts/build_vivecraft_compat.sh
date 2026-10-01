#!/usr/bin/env bash
# Compiles VoxyQuestXr, the class VivecraftRefreshRateFix adds to Vivecraft's OpenXR provider
# to request performance levels, into the APK assets. It compiles against the LWJGL OpenXR
# bindings nested in a Quest Vivecraft JAR and the launcher's LWJGL core classes.
#
# Usage: build_vivecraft_compat.sh <Quest Vivecraft jar>
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
OPENXR=$(unzip -Z1 "$1" | grep -E '^META-INF/jars/.*lwjgl-openxr-[0-9.]+\.jar$' | head -n 1)
test -n "$OPENXR" || { echo "$1 has no LWJGL OpenXR bindings" >&2; exit 1; }
unzip -q -o "$1" "$OPENXR" -d "$WORK"
# Java 17 class files load on every runtime the supported Minecraft versions use.
javac --release 17 -d "$WORK/classes" \
  -cp "$ROOT/third_party/Pojlib/src/main/assets/lwjgl/lwjgl-glfw-classes.jar:$WORK/$OPENXR" \
  "$ROOT/third_party/Pojlib/compat/vivecraft/src/org/vivecraft/client_vr/provider/openxr/VoxyQuestXr.java"
CLASSES=$(find "$WORK/classes" -name '*.class' | wc -l)
test "$CLASSES" -eq 1 || { echo "VoxyQuestXr must compile to a single class" >&2; exit 1; }
mkdir -p "$ROOT/third_party/Pojlib/src/main/assets/voxyquest/compat"
cp "$WORK/classes/org/vivecraft/client_vr/provider/openxr/VoxyQuestXr.class" \
  "$ROOT/third_party/Pojlib/src/main/assets/voxyquest/compat/VoxyQuestXr.bin"
