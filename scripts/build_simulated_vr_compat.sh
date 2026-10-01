#!/usr/bin/env bash
# Compiles VoxyQuestVrHold, the class SimulatedVrFix adds to Create Aeronautics' Simulated
# library, into the APK assets. The launcher reads the class bytes from there at launch.
#
# Usage: build_simulated_vr_compat.sh [fakes output dir]
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
COMPAT=$ROOT/third_party/Pojlib/compat/simulated
FAKES=${1:-$(mktemp -d)}
HELPER=$(mktemp -d)
trap 'rm -rf "$HELPER"' EXIT

mapfile -d '' SOURCES < <(find "$COMPAT/fakes" -name '*.java' -print0)
# Java 17 class files: older than Minecraft 1.21's Java 21, so every supported runtime loads them.
javac --release 17 -d "$FAKES" "${SOURCES[@]}"
javac --release 17 -cp "$FAKES" -d "$HELPER" \
  "$COMPAT/src/dev/simulated_team/simulated/util/hold_interaction/VoxyQuestVrHold.java"
CLASSES=$(find "$HELPER" -name '*.class' | wc -l)
test "$CLASSES" -eq 1 || { echo "VoxyQuestVrHold must compile to a single class" >&2; exit 1; }
mkdir -p "$ROOT/third_party/Pojlib/src/main/assets/voxyquest/compat"
cp "$HELPER/dev/simulated_team/simulated/util/hold_interaction/VoxyQuestVrHold.class" \
  "$ROOT/third_party/Pojlib/src/main/assets/voxyquest/compat/VoxyQuestVrHold.bin"
