#!/usr/bin/env bash
# Builds the Quest OpenXR Vivecraft jars for NeoForge and extracts the processed NeoForge
# clients bundled in the APK. CI caches the output; the cache key hashes this script and the
# pinned Vivecraft revisions, so edit this file (not the workflow) when the inputs change.
# Expects the three Vivecraft checkouts in the working directory, as godot.yml provides.
set -eu

cd quest-vivecraft-openxr
sed -i 's/^enabled_platforms=.*/enabled_platforms=neoforge/' gradle.properties
bash ./gradlew :neoforge:remapJar --no-daemon
cd ..
ASSETS=third_party/Pojlib/src/main/assets/voxyquest/neoforge
mkdir -p "$ASSETS" "$RUNNER_TEMP/neoforge-client"
cp quest-vivecraft-openxr/neoforge/build/libs/vivecraft-1.21.5-1.3.4-neoforge.jar "$ASSETS/vivecraft.jar"
for MC_VERSION in 1.21.4 1.21.1; do
  cd "quest-vivecraft-openxr-$MC_VERSION"
  sed -i 's/^enabled_platforms=.*/enabled_platforms=neoforge/' gradle.properties
  bash ./gradlew :neoforge:remapJar --no-daemon
  cd ..
  mkdir -p "$ASSETS/$MC_VERSION"
  cp "quest-vivecraft-openxr-$MC_VERSION/neoforge/build/libs/vivecraft-$MC_VERSION-1.2.5-neoforge.jar" "$ASSETS/$MC_VERSION/vivecraft.jar"
done
curl -fsSL --retry 3 https://maven.neoforged.net/releases/net/neoforged/neoforge/21.5.2-beta/neoforge-21.5.2-beta-installer.jar -o "$RUNNER_TEMP/neoforge-installer.jar"
printf '{"profiles":{}}' > "$RUNNER_TEMP/neoforge-client/launcher_profiles.json"
java -jar "$RUNNER_TEMP/neoforge-installer.jar" --installClient "$RUNNER_TEMP/neoforge-client"
cp "$RUNNER_TEMP/neoforge-client/versions/neoforge-21.5.2-beta/neoforge-21.5.2-beta.json" "$ASSETS/version.json"
cp "$RUNNER_TEMP/neoforge-client/libraries/net/neoforged/neoforge/21.5.2-beta/neoforge-21.5.2-beta-client.jar" "$ASSETS/client.jar"
cp "$RUNNER_TEMP/neoforge-client/libraries/net/neoforged/neoforge/21.5.2-beta/neoforge-21.5.2-beta-universal.jar" "$ASSETS/universal.jar"
MC_LIB="$RUNNER_TEMP/neoforge-client/libraries/net/minecraft/client/1.21.5-20250325.162830"
cp "$MC_LIB/client-1.21.5-20250325.162830-srg.jar" "$ASSETS/minecraft-srg.jar"
cp "$MC_LIB/client-1.21.5-20250325.162830-extra.jar" "$ASSETS/minecraft-extra.jar"
unzip -Z1 "$ASSETS/minecraft-srg.jar" | grep -Fx 'net/minecraft/client/Minecraft.class'
unzip -Z1 "$ASSETS/minecraft-extra.jar" | grep -Fx 'assets/.mcassetsroot'
unzip -p "$ASSETS/vivecraft.jar" META-INF/neoforge.mods.toml | grep -q 'modId = "vivecraft"'

# Each additional Minecraft version needs its own processed client and
# profile. Keep them isolated so the NeoForge locator cannot load the
# wrong game classes when instances switch versions.
for spec in "1.21.4:21.4.150" "1.21.1:21.1.220"; do
  MC_VERSION="${spec%%:*}"
  NEO_VERSION="${spec#*:}"
  VERSION_ASSETS="$ASSETS/$MC_VERSION"
  CLIENT_DIR="$RUNNER_TEMP/neoforge-client-$MC_VERSION"
  mkdir -p "$VERSION_ASSETS" "$CLIENT_DIR"
  printf '{"profiles":{}}' > "$CLIENT_DIR/launcher_profiles.json"
  curl -fsSL --retry 3 "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO_VERSION/neoforge-$NEO_VERSION-installer.jar" -o "$RUNNER_TEMP/neoforge-installer-$MC_VERSION.jar"
  java -jar "$RUNNER_TEMP/neoforge-installer-$MC_VERSION.jar" --installClient "$CLIENT_DIR"
  cp "$CLIENT_DIR/versions/neoforge-$NEO_VERSION/neoforge-$NEO_VERSION.json" "$VERSION_ASSETS/version.json"
  cp "$CLIENT_DIR/libraries/net/neoforged/neoforge/$NEO_VERSION/neoforge-$NEO_VERSION-client.jar" "$VERSION_ASSETS/client.jar"
  cp "$CLIENT_DIR/libraries/net/neoforged/neoforge/$NEO_VERSION/neoforge-$NEO_VERSION-universal.jar" "$VERSION_ASSETS/universal.jar"
  MC_LIB=$(find "$CLIENT_DIR/libraries/net/minecraft/client" -mindepth 1 -maxdepth 1 -type d -name "$MC_VERSION-*" -print -quit)
  test -n "$MC_LIB"
  cp "$MC_LIB"/client-*-srg.jar "$VERSION_ASSETS/minecraft-srg.jar"
  cp "$MC_LIB"/client-*-extra.jar "$VERSION_ASSETS/minecraft-extra.jar"
  unzip -Z1 "$VERSION_ASSETS/minecraft-srg.jar" | grep -Fx 'net/minecraft/client/Minecraft.class'
  unzip -Z1 "$VERSION_ASSETS/minecraft-extra.jar" | grep -Fx 'assets/.mcassetsroot'
  unzip -Z1 "$VERSION_ASSETS/universal.jar" | grep -Fx 'META-INF/neoforge.mods.toml'
done
python3 - <<'PY'
import json
from pathlib import Path
root = Path("third_party/Pojlib/src/main/assets/voxyquest/neoforge")
for version, loader in (("1.21.5", "21.5.2-beta"), ("1.21.4", "21.4.150"), ("1.21.1", "21.1.220")):
    profile = root / ("" if version == "1.21.5" else version) / "version.json"
    data = json.loads(profile.read_text())
    assert data["id"] == f"neoforge-{loader}", profile
    game = data["arguments"]["game"]
    assert game[game.index("--fml.mcVersion") + 1] == version, profile
    assert isinstance(game[game.index("--fml.neoFormVersion") + 1], str), profile
    assert all(isinstance(arg, str) for key in ("jvm", "game") for arg in data["arguments"][key]), profile
PY
