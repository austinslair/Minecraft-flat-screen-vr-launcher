#!/usr/bin/env bash
# Builds the Quest OpenXR Vivecraft jars for NeoForge and extracts the processed NeoForge
# clients bundled in the APK. CI caches the output; the cache key hashes this script and the
# pinned Vivecraft revisions, so edit this file (not the workflow) when the inputs change.
# Expects the three Vivecraft checkouts in the working directory, as godot.yml provides.
set -eu

# maven.neoforged.net and the Mojang/Forge mirrors occasionally answer 502/503. Retry the
# network-heavy commands with a growing pause instead of failing the whole build.
retry() {
  attempt=1
  until "$@"; do
    if [ "$attempt" -ge 4 ]; then
      echo "Failed after $attempt attempts: $*" >&2
      return 1
    fi
    echo "Attempt $attempt failed, retrying in $((attempt * 20))s: $*" >&2
    sleep $((attempt * 20))
    attempt=$((attempt + 1))
  done
}

# Only the NeoForge jar is needed. Vivecraft's settings.gradle includes every platform
# regardless of enabled_platforms, and configuring :forge alone runs Forge's full MCP
# pipeline (minutes per checkout), so drop the unused projects before building.
neoforge_only() {
  sed -i 's/^enabled_platforms=.*/enabled_platforms=neoforge/' gradle.properties
  sed -i '/^include("fabric")$/d; /^include("forge")$/d' settings.gradle
  # Newer Quest branches keep the NeoForge sources but leave them out of the build.
  grep -qx 'include("neoforge")' settings.gradle || echo 'include("neoforge")' >> settings.gradle
}

# Gradle's own retry gives up after a few short attempts; allow more, with longer backoff.
GRADLE_RETRY_ARGS="-Dorg.gradle.internal.repository.max.retries=6 -Dorg.gradle.internal.repository.initial.backoff=2000"

cd quest-vivecraft-openxr
neoforge_only
retry bash ./gradlew :neoforge:remapJar --no-daemon $GRADLE_RETRY_ARGS
cd ..
ASSETS=third_party/Pojlib/src/main/assets/voxyquest/neoforge
mkdir -p "$ASSETS" "$RUNNER_TEMP/neoforge-client"
cp quest-vivecraft-openxr/neoforge/build/libs/vivecraft-1.21.5-1.3.4-neoforge.jar "$ASSETS/vivecraft.jar"
for MC_VERSION in 1.21.8 1.21.4 1.21.1; do
  cd "quest-vivecraft-openxr-$MC_VERSION"
  neoforge_only
  VIVECRAFT_VERSION=$(sed -n 's/^mod_version=//p' gradle.properties)
  retry bash ./gradlew :neoforge:remapJar --no-daemon $GRADLE_RETRY_ARGS
  cd ..
  mkdir -p "$ASSETS/$MC_VERSION"
  cp "quest-vivecraft-openxr-$MC_VERSION/neoforge/build/libs/vivecraft-$MC_VERSION-$VIVECRAFT_VERSION-neoforge.jar" "$ASSETS/$MC_VERSION/vivecraft.jar"
done
curl -fsSL --retry 5 --retry-delay 10 https://maven.neoforged.net/releases/net/neoforged/neoforge/21.5.2-beta/neoforge-21.5.2-beta-installer.jar -o "$RUNNER_TEMP/neoforge-installer.jar"
printf '{"profiles":{}}' > "$RUNNER_TEMP/neoforge-client/launcher_profiles.json"
retry java -jar "$RUNNER_TEMP/neoforge-installer.jar" --installClient "$RUNNER_TEMP/neoforge-client"
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
# A version ending in ".*" installs the newest stable release of that NeoForge line.
latest_neoforge() {
  prefix="${1%\*}"
  retry curl -fsSL --retry 5 --retry-delay 10 https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml \
    -o "$RUNNER_TEMP/neoforge-metadata.xml"
  sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$RUNNER_TEMP/neoforge-metadata.xml" |
    grep -E "^${prefix//./\\.}[0-9]+$" | sort -V | tail -n 1
}

for spec in "1.21.8:21.8.*" "1.21.4:21.4.150" "1.21.1:21.1.228"; do
  MC_VERSION="${spec%%:*}"
  NEO_VERSION="${spec#*:}"
  case "$NEO_VERSION" in
    *\*) NEO_VERSION=$(latest_neoforge "$NEO_VERSION"); test -n "$NEO_VERSION" ;;
  esac
  echo "NeoForge for $MC_VERSION: $NEO_VERSION"
  VERSION_ASSETS="$ASSETS/$MC_VERSION"
  CLIENT_DIR="$RUNNER_TEMP/neoforge-client-$MC_VERSION"
  mkdir -p "$VERSION_ASSETS" "$CLIENT_DIR"
  printf '{"profiles":{}}' > "$CLIENT_DIR/launcher_profiles.json"
  curl -fsSL --retry 5 --retry-delay 10 "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO_VERSION/neoforge-$NEO_VERSION-installer.jar" -o "$RUNNER_TEMP/neoforge-installer-$MC_VERSION.jar"
  retry java -jar "$RUNNER_TEMP/neoforge-installer-$MC_VERSION.jar" --installClient "$CLIENT_DIR"
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
for version, loader in (("1.21.8", "21.8.*"), ("1.21.5", "21.5.2-beta"), ("1.21.4", "21.4.150"),
                        ("1.21.1", "21.1.228")):
    profile = root / ("" if version == "1.21.5" else version) / "version.json"
    data = json.loads(profile.read_text())
    if loader.endswith("*"):
        assert data["id"].startswith("neoforge-" + loader[:-1]), profile
    else:
        assert data["id"] == f"neoforge-{loader}", profile
    # The launcher starts NeoForge through ModLauncher, which NeoForge 21.9 removed.
    assert data["mainClass"] == "cpw.mods.bootstraplauncher.BootstrapLauncher", profile
    game = data["arguments"]["game"]
    assert game[game.index("--fml.mcVersion") + 1] == version, profile
    assert isinstance(game[game.index("--fml.neoFormVersion") + 1], str), profile
    assert all(isinstance(arg, str) for key in ("jvm", "game") for arg in data["arguments"][key]), profile
PY
