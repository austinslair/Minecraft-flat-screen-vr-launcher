# Godot Integration

## Engine baseline

VoxyQuest targets Godot `4.7.2-stable` with the Mobile renderer and built-in OpenXR support. The launcher project is under `launcher/`.

The first XR milestone is intentionally small: enter an OpenXR session, render a world-space status panel, and track the headset/controllers. Minecraft launching comes after that shell is stable.

## Runtime boundary

The launcher should only talk to the Android runtime through the `VoxyQuestBridge` Godot singleton.

```text
GDScript UI -> VoxyQuestBridge -> Android/Kotlin host -> Pojlib -> Minecraft
```

This keeps Android process/runtime details out of scenes and UI scripts.

## Why Pojlib cannot be linked directly yet

The pinned upstream Pojlib currently imports Unity player classes in `UnityPlayerActivity`. Other launcher code and one native JNI bridge refer back to that class for host services such as LWJGL asset installation and clipboard access.

The long-term fix is a small engine-neutral host abstraction: Android activity/context, display metrics, clipboard, input hooks, and runtime restart behavior belong in the VoxyQuest host layer. The rest of Pojlib can stay focused on Minecraft installation and launch logic.

Until that adapter is implemented, the Godot project and Android bridge can be developed and tested independently without carrying Unity into the launcher.

## Android plugin

`android/bridge/` is a Godot Android plugin v2 project. It currently exposes bridge/version/compatibility information to GDScript. Later Pojlib calls should be added there, not directly in scene scripts.

Build it with Gradle task:

```bash
gradle -p android/bridge :plugin:syncToGodot
```

That copies the generated AARs into `launcher/addons/VoxyQuestBridge/bin/`.

## Quest export

`launcher/export_presets.cfg` is configured for Android arm64 and OpenXR. Vendor-specific Meta features can be added later when a feature actually needs them; core headset/controller tracking should stay on standard OpenXR first.
