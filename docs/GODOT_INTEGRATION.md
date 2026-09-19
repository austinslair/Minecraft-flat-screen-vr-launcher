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
