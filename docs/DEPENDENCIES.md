# Third-Party Dependencies

## Pojlib

VoxyQuest uses Pojlib as an upstream Minecraft: Java Edition launcher core.

- Upstream: https://github.com/QuestCraftPlusPlus/Pojlib
- Upstream branch: `QuestCraft-6.0.0`
- Pinned commit: `c6566d540a3ef530017f99b59f24d38121e3a5df`
- License: GNU Lesser General Public License v3.0 (LGPL-3.0)

Pojlib is included as a Git submodule at `third_party/Pojlib` so upstream history and licensing remain separate and updates can be reviewed explicitly.

Pojlib's current `mods.json` runtime manifest already points at QuestCraft's OpenXR Vivecraft builds plus Fabric API and performance mods for supported Minecraft versions. Those jars should remain runtime-managed rather than copied into this repository.

## QCXR-XR-Wrapper

VoxyQuest tracks QuestCraft's active XR wrapper project as the initial reference implementation for getting the launcher running as a VR-native application.

- Upstream: https://github.com/QuestCraftPlusPlus/QCXR-XR-Wrapper
- Upstream branch: `master`
- Pinned commit: `5aeb178085f36e3f4db70a548330d05f34bdf897`
- Unity project version: `2022.3.62f3`
- OpenXR package: `com.unity.xr.openxr` `1.14.3`
- License file: GNU Lesser General Public License v3.0 (LGPL-3.0)

The upstream README currently displays a GPLv3 badge while the repository `LICENSE` file contains LGPL-3.0 text. Keep the upstream license files intact and verify licensing before redistribution.

QCXR-XR-Wrapper is included as a Git submodule at `third_party/QCXR-XR-Wrapper` instead of copying the Unity project into VoxyQuest.

## Clone / initialize

Clone with submodules:

```bash
git clone --recurse-submodules https://github.com/austinslair/Minecraft-flat-screen-vr-launcher.git
```

For an existing clone:

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

Do not copy private keys, signing credentials, Unity license data, Minecraft/Microsoft account tokens, or other secrets into this repository.
