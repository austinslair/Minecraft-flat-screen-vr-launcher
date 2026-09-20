<div align="center">

<img src="assets/voxyquest-banner.svg" alt="VoxyQuest banner" width="100%">

# VoxyQuest

**A VR-native Minecraft launcher for standalone headsets, with VR and flat-screen play modes.**

### Download

[![Download VoxyQuest APK](https://img.shields.io/badge/Download-VoxyQuest%20APK-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/austinslair/Minecraft-flat-screen-vr-launcher/releases/download/v0.1.0-alpha.4/VoxyQuest-Quest.apk)

[View all releases](https://github.com/austinslair/Minecraft-flat-screen-vr-launcher/releases)

**Current test release:** `v0.1.0-alpha.4` · Meta Quest APK

</div>

VoxyQuest is being built as a headset-native launcher instead of a normal desktop-style launcher window. The launcher shell is now based on **Godot 4.7.2 + OpenXR**. Minecraft runtime work is kept behind an Android bridge so the UI can evolve without becoming coupled to launcher internals.

## Current architecture

```text
Godot / OpenXR launcher shell
        |
        v
VoxyQuest Android bridge
        |
        v
Pojlib host adapter
        |
        v
Minecraft Java runtime + Vivecraft / flat-screen mode
```

The Godot project lives in `launcher/`. The Android bridge source lives in `android/bridge/`. Pojlib remains pinned under `third_party/Pojlib`.

## Current status

The repository now contains a bootable Godot XR project skeleton, a Godot Android plugin bridge, an Android/OpenXR export preset, and CI that checks the bridge build and Godot project import.

Pojlib is **not directly engine-neutral yet**: its current upstream code still references `UnityPlayerActivity`. VoxyQuest therefore treats Pojlib as the runtime core behind a host adapter instead of letting the Godot UI call Pojlib directly. See `docs/GODOT_INTEGRATION.md`.

## Development targets

- VR-native launcher UI in Godot
- Meta Quest / Android arm64 OpenXR startup
- controller and headset tracking
- Minecraft VR launch through Vivecraft/OpenXR
- flat-screen Minecraft rendered as a VR panel later
- instance, mod-loader, and settings management

## Repository layout

```text
.
├── launcher/              # Godot XR launcher project
├── android/bridge/        # Godot Android plugin / runtime bridge
├── third_party/Pojlib/    # pinned Minecraft launcher core
├── docs/
├── assets/
└── .github/workflows/
```

Use Godot **4.7.2 stable** for the launcher project. The project uses the Mobile renderer and OpenXR.
