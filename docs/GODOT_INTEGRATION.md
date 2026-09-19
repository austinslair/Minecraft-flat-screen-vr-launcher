# Godot Integration

## Engine baseline

VoxyQuest targets Godot `4.7.2-stable` with the Mobile renderer. The launcher project is under `launcher/`.

Godot is the launcher engine, but the launcher itself is intentionally **not an OpenXR application**. On Quest it should appear as a normal flat Android application panel. When the user later chooses Play VR, the Minecraft/Vivecraft runtime owns the immersive OpenXR session.

## Runtime boundary

The launcher talks to Android/Minecraft services only through the `VoxyQuestBridge` Godot singleton:

```text
Godot 2D UI -> VoxyQuestBridge -> Android/Kotlin host -> Pojlib -> Minecraft
```

This keeps Android process, account, JVM, and Minecraft details out of scenes and UI scripts.

## Pojlib host

The vendored Pojlib source has been adapted away from Unity. `PojlibRuntime` provides engine-neutral Android host services and `VoxyQuestBridge` initializes it from Godot.

Pojlib remains editable source under `third_party/Pojlib`; there is no Unity player activity or Unity compile stub in the active runtime.

## Microsoft account flow

Microsoft authentication is exposed through `VoxyQuestBridge`. The Godot UI sees only login state, device code, verification URL, Minecraft username/UUID, and demo/ownership state. Access and refresh tokens remain in the Android/Pojlib layer.

See `docs/MICROSOFT_LOGIN.md` for registration and build configuration.

## Android plugin

`android/bridge/` is a Godot Android plugin v2 project. Build it with:

```bash
gradle -p android/bridge :plugin:syncToGodot
```

That builds both the bridge and Pojlib AARs and copies them into `launcher/addons/VoxyQuestBridge/bin/` for Android export.

## Quest export

`launcher/export_presets.cfg` targets Android arm64 as a normal non-XR Quest application. Godot must not request an OpenXR session at launcher startup.

The future Play VR path should hand off to the Minecraft/Vivecraft runtime, which then starts the game-specific OpenXR session. Flat-screen Minecraft should use its non-VR rendering path.

## Launcher home and installed profiles

The scenic home screen uses real account and instance state. News starts empty, no avatar is invented, and no version is selected until the user chooses a saved instance. Home has no green selection fill. The existing device-code page is embedded in the account window, preserving code copying, browser opening, cancellation and session restoration.

The Pojlib bridge exposes a read-only instance snapshot plus install, progress and VR launch operations. Installation runs on one worker, using the bundled `voxyquest/runtime_mods.json` catalog (copied from this repository's vendored Pojlib catalog). It installs Minecraft, Fabric, Vivecraft/runtime mods, assets and the Android Java runtime. An instance is appended to the registry only after those stages succeed. The registry is replaced atomically where supported. Failed downloads never publish a new installed profile; retries can reuse verified files.

Select an installed profile and sign in to enable Play. A dedicated `MinecraftGameActivity` hands its Android context to the existing Vivecraft/Pojlib JNI bridge and launches the JVM off the UI thread. The Godot launcher remains non-XR. Exiting the game restarts the process because a JVM cannot be safely launched twice in the same process. Flat-screen game rendering is not implemented by this change.

The catalog and native libraries are inherited from Pojlib; VoxyQuest uses its own UI, app identity and Microsoft registration. Upstream catalog URLs and runtime compatibility still need version-by-version headset validation.

## Verification

After importing the Godot project:

```bash
godot --headless --path launcher --script res://tests/home_smoke.gd
godot --headless --path launcher --script res://tests/auth_flow.gd
gradle -p android/bridge :plugin:syncToGodot :pojlib:testDebugUnitTest
```

Godot tests cover empty states, account changes, instance selection, Play gating and browser-once behavior using mocked Android responses. Download tests cover interrupted transfer retries, HTTP failure limits and preservation of existing files. A real Quest test remains necessary for browser focus/clipboard behavior, Microsoft/Xbox/Minecraft service authorization, large installs and the JNI/OpenXR game handoff. These tests do not certify that Minecraft has run successfully on a headset.
