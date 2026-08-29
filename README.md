StorageDrawers
==============

A mod adding compartmental storage for Minecraft Fabric/NeoForge (unofficial port)

Versions
========

| Minecraft | Branch | Loaders | Latest release |
|---|---|---|---|
| 26.2 | [port/26.2](https://github.com/chaevsfe/StorageDrawers/tree/port/26.2) | Fabric, NeoForge | [v19.1.8](https://github.com/chaevsfe/StorageDrawers/releases/tag/v19.1.8) |
| 26.1.x | [port/26.1](https://github.com/chaevsfe/StorageDrawers/tree/port/26.1) | Fabric, NeoForge | [v19.1.8](https://github.com/chaevsfe/StorageDrawers/releases/tag/v19.1.8) |

Also on [Modrinth](https://modrinth.com/mod/storagedrawers-unofficial-fabric-port) and [CurseForge](https://www.curseforge.com/minecraft/mc-mods/storagedrawers-unofficial-port)

Before updating
===============

Enchanted and NBT-bearing may be deleted or reverted. Create a backup, if any problems: Empty modded containers of items into vanilla storage or your inventory before updating and report any [issues](https://github.com/chaevsfe/StorageDrawers/issues).

Verified from **1.18.2**: ﻿Enchantments, ﻿﻿custom names, attribute modifiers, model data, consumable properties, locks, jukebox data, tooltip visibility flags, old NBT-format items﻿. 

Modded items passed through unchanged: Most mods migrate their own data fine, but if a mod changes it's data format between versions without a migration items may be lost.

Worlds only move forward: a world opened in 26.2 may not go back to 26.1

For Players
-----------

All credit goes to the original author:
- [Minecraft Forums](http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/2198533-storage-drawers-v1-10-7-v3-5-0-v4-0-0-updated-nov)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/storage-drawers)
- [Github Releases](https://github.com/jaquadro/StorageDrawers/releases)

There's also a discord community for Texel's mods: https://discord.gg/8WtpQfy

For Developers
--------------

#### Building

StorageDrawers is built using `gradle`. These commands should be enough to get you started:

```
git clone https://github.com/chaevsfe/StorageDrawers
cd StorageDrawers
git checkout port/26.1   # or port/26.2 -- pick the branch for your Minecraft version
./gradlew :fabric:build     # or :neoforge:build
```

Reporting Bugs
--------------

When reporting bugs, always include the version number of the mod.  If you're reporting a crash, include your client or server log depending on where the crash ocurred.
