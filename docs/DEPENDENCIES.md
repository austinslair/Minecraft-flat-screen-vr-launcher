# Third-Party Dependencies

## Godot

VoxyQuest uses Godot `4.7.2-stable` as the launcher/XR shell. The Android bridge compiles against `org.godotengine:godot:4.7.2.stable`.

## Pojlib

- Upstream: https://github.com/QuestCraftPlusPlus/Pojlib
- Branch: `QuestCraft-6.0.0`
- Imported commit: `c6566d540a3ef530017f99b59f24d38121e3a5df`
- License: LGPL-3.0
- Local source: `third_party/Pojlib`

Pojlib is vendored directly into this repository as normal Git files. It is not a Git submodule. The source under `third_party/Pojlib` can be edited, committed, reviewed, and built together with VoxyQuest.

The imported snapshot keeps Pojlib's original `LICENSE`, Gradle files, Java/native sources, local libraries, runtime manifests, and wrapper scripts. `third_party/Pojlib/UPSTREAM.md` records the exact upstream revision used for the import.

VoxyQuest's vendored Pojlib has been adapted to use the Godot Android host. The Unity player activity and Unity compile stubs are removed; `PojlibRuntime` provides engine-neutral Android services and `VoxyQuestBridge` exposes them to Godot.

Pojlib's runtime manifests already describe QuestCraft Vivecraft/Fabric combinations for supported Minecraft versions, so those mod jars should remain runtime-managed instead of being copied into the launcher source tree.

No submodule initialization is required when cloning VoxyQuest.

Do not commit signing keys, account tokens, Unity/Godot credentials, or other secrets.
