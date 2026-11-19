# TaCZ Refabricated — Unofficial Fabric Port

Build a collection in survival: craft a gunsmithing table, fit attachments, and add community gun packs to your world. This is a community port of **Timeless & Classics Guns: Zero (TaCZ)**, not an official release or a project endorsed by the TACZ Dev Team.

### Pick a file before you install

In **Files**, match the **exact Minecraft version** and the **Fabric** loader. The four versions use separate jars. Install **Fabric API** and **Forge Config API Port** for the same game version. Do not install the Forge/NeoForge TaCZ jar alongside this one.

| Minecraft | Java | What to expect |
|---|---|---|
| 26.3 | 25 | The development version is `1.2.0+fabric.26.3.R1`, with selected upstream 1.2.0-hotfix features. Check the individual file's changelog; the older R1-hotfix3 release predates the features below. |
| 26.2, 26.1.2 | 25 | Separate builds with their own pack, renderer and optional-mod compatibility. |
| 1.21.11 | 21 | A separate build for this game version, not a 26.x jar. |

### What's different in this port?

The port adapts pack loading, networking and scopes to each game version. It includes **TacZ Mesh Loader** for supported high-poly packs and **part of LRTactical's data-driven framework**; it is not the full LRTactical mod. Experimental scope picture-in-picture (PIP) is optional and off by default.

**In the 26.3 development build**, you can hang items on pegboards or lock them in display cases, use tiered ammo storage, and browse compatible items in JEI or REI. Hold a gun and open **Esc → TaCZ: View settings** to set first-person position and a HUD style for your launcher account. A style for one gun overrides its category; neither changes the scope reticle or gameplay. The same build keeps ordinary crafting recipes in REI when gun-pack data arrives after login. Recipe-book unlocks also require updated server data. These changes are **not** promised by every 26.3 upload or by the three older lines. A pack requiring exactly `1.1.8` will not pass the new version check. Passing the check does not guarantee full support for every 1.2.0 pack.

### Packs and troubleshooting

- Put a pack folder or ZIP in `.minecraft/tacz/` with `gunpack.meta.json` at its root. `tacz_backup/` is for the legacy converter, not normal loading.
- Packs requiring TacZ:Arcana's encrypted assets are not supported. Missing textures alone do not identify an Arcana pack: check the pack's dependencies and paths first.
- Can't find a recipe? Try the ingredients in the vanilla 3×3 table, check the recipe book, then check your viewer. A server recipe, a book unlock and a viewer listing are different things. A blank item obtained via a viewer's cheat command may instead be missing its pack ID; compare with a stack from a TaCZ creative tab.
- Please report issues to **this port's issue tracker**, not the original authors. Include the exact mod file, game and loader versions, pack name and steps to reproduce. For a rendering problem, include your shader pack and whether it happens with shaders off.

### 中文说明

这是 TaCZ 的**非官方 Fabric 移植**。在「Files」按自己的 MC 版本选文件：26.3、26.2、26.1.2 使用 Java 25，1.21.11 使用 Java 21；都需要对应版本的 Fabric API 与 Forge Config API Port。普通枪包放在 `.minecraft/tacz/`，ZIP 不用解压，包根必须有 `gunpack.meta.json`。

**26.3 开发构建 `1.2.0+fabric.26.3.R1`** 中可以在挂板和展示柜陈列物品、使用分级弹药库，持枪按 **Esc → TaCZ 持枪与准星** 可按账号保存持枪位置，并为单枪或类别指定准星。普通工作台合成、配方书解锁和 JEI/REI 查询是三条不同路径。旧版发布文件不会因源码合并自动更新，另外三条线也不共用 26.3 的构建；安装前看该文件的更新说明。内置 TML 不代表所有挂板物品都有静态 VBO，也不保证任意第三方枪包、光影或完整 LRTactical 兼容。

### Credits and licenses / 署名与许可

Original mod: [TACZ Dev Team](https://github.com/MCModderAnchor/TACZ). Upstream Fabric port: [Sh1roCu](https://github.com/Sh1roCu/TACZ-Refabricated). Mesh loader: [VellEagle](https://github.com/VellEagle/TacZMeshLoader). LRTactical code: LesRaisins. TaCZ port code is GPL-3.0; the default pack's models, textures, animations and sounds are **CC BY-NC-ND 4.0**, not GPL. The bundled Mayday Animation Engine is MIT; the relocated SimpleBedrockModel particle/Molang code on the 26.3 development line is LGPL-3.0. See the license notices in the project source.
