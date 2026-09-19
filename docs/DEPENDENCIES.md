# Third-Party Dependencies

## Godot

VoxyQuest uses Godot `4.7.2-stable` as the launcher/XR shell. The Android bridge compiles against `org.godotengine:godot:4.7.2.stable`.

## Pojlib

- Upstream: https://github.com/QuestCraftPlusPlus/Pojlib
- Branch: `QuestCraft-6.0.0`
- Pinned commit: `c6566d540a3ef530017f99b59f24d38121e3a5df`
- License: LGPL-3.0

Pojlib is kept as a Git submodule at `third_party/Pojlib`.

Important: upstream Pojlib currently has a direct Unity host dependency concentrated in `pojlib.UnityPlayerActivity`, and its Gradle build references Unity classes. VoxyQuest does not expose those Unity details to the Godot UI. The Android bridge is the stable boundary where a Godot-compatible Pojlib host adapter will live.

Pojlib's runtime manifests already describe QuestCraft Vivecraft/Fabric combinations for supported Minecraft versions, so those mod jars should remain runtime-managed instead of being copied into this repository.

Clone dependencies with:

```bash
git submodule update --init --recursive
```

Do not commit signing keys, account tokens, Unity/Godot credentials, or other secrets.
