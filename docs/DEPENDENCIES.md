# Third-Party Dependencies

## Godot

VoxyQuest uses Godot `4.7.2-stable` for the flat launcher UI. The launcher itself is a normal non-XR Android/Quest application. The Android bridge compiles against `org.godotengine:godot:4.7.2.stable`.

## Pojlib

- Upstream: https://github.com/QuestCraftPlusPlus/Pojlib
- Branch: `QuestCraft-6.0.0`
- Imported commit: `c6566d540a3ef530017f99b59f24d38121e3a5df`
- License: LGPL-3.0
- Local source: `third_party/Pojlib`

Pojlib is vendored directly into this repository as normal Git files. It is not a Git submodule. The source under `third_party/Pojlib` can be edited, committed, reviewed, and built together with VoxyQuest.

VoxyQuest's copy has been adapted away from Unity. `PojlibRuntime` supplies engine-neutral Android host services and `VoxyQuestBridge` exposes launcher/runtime operations to Godot.

Pojlib includes the Microsoft/Xbox/Minecraft account chain used by VoxyQuest. VoxyQuest supplies its own Microsoft public-client application ID at build time; QuestCraft's application registration is not reused. MSAL4J is currently `com.microsoft.azure:msal4j:1.17.2`.

Pojlib's runtime manifests describe supported Vivecraft/Fabric combinations. Those game-side components remain runtime-managed rather than being copied into the Godot launcher UI.

No submodule initialization is required when cloning VoxyQuest.

Do not commit signing keys, account tokens, Microsoft client secrets, token caches, or other credentials.
