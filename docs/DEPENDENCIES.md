# Third-Party Dependencies

## Pojlib

VoxyQuest uses Pojlib as an upstream Minecraft: Java Edition launcher core.

- Upstream: https://github.com/QuestCraftPlusPlus/Pojlib
- Upstream branch: `QuestCraft-6.0.0`
- Pinned commit: `c6566d540a3ef530017f99b59f24d38121e3a5df`
- License: GNU Lesser General Public License v3.0 (LGPL-3.0)

Pojlib is included as a Git submodule at `third_party/Pojlib` so upstream history and licensing remain separate and updates can be reviewed explicitly.

Clone with submodules:

```bash
git clone --recurse-submodules https://github.com/austinslair/Minecraft-flat-screen-vr-launcher.git
```

For an existing clone:

```bash
git submodule update --init --recursive
```

Do not copy private keys, signing credentials, account tokens, or other secrets into this repository.
