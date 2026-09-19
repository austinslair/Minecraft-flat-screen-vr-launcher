# VoxyQuest VR Stack

This document records the initial path for getting VoxyQuest running as a VR-native launcher on standalone Quest-class Android headsets.

## Current stack

1. **QCXR-XR-Wrapper** — VR shell/reference project. It is a Unity project using Unity OpenXR, XR Management, XR Core Utilities, and XR Interaction Toolkit.
2. **Pojlib** — Minecraft: Java Edition launcher/runtime core. It handles the Android-side Minecraft launch pipeline and supporting libraries.
3. **QuestCraft Vivecraft fork** — Minecraft VR mod. Pojlib's version manifest already downloads the appropriate OpenXR Vivecraft release for supported Minecraft versions.
4. **Fabric API and performance mods** — also runtime-managed by Pojlib's manifest rather than vendored here.

## Why the XR wrapper is separate

The VR shell owns headset tracking, controller/hand input, OpenXR session state, VR UI, and the launcher environment. Pojlib owns Minecraft setup and process/runtime concerns. Keeping these separate lets VoxyQuest eventually replace or heavily customize the launcher UI without rewriting the Minecraft runtime layer.

## First development milestone

The first milestone is intentionally smaller than "Minecraft VR works":

- build the XR wrapper for Android arm64
- launch it on a Quest headset
- enter an OpenXR session successfully
- show a simple world-space VoxyQuest panel
- confirm headset and controller tracking
- call into a launcher bridge without starting Minecraft yet

After that works reliably, wire Pojlib into the VR shell and test the Minecraft runtime separately.

## Flat-screen mode later

Flat-screen Minecraft inside the headset should be treated as a separate mode. It will need a surface/window presentation path plus mouse/keyboard/controller input translation. Do not mix that implementation into the first VR boot milestone.

## Engine note

The current QuestCraft reference wrapper is Unity-based. A Godot OpenXR shell is still possible later, but starting with both Unity and Godot would duplicate XR-session ownership and slow down the first bootable build. For now, VoxyQuest tracks the known QuestCraft Unity/OpenXR path while keeping Pojlib isolated so the shell can be replaced later if desired.
