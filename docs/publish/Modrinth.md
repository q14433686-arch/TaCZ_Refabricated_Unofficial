# Modrinth 发布文案

> 复制粘贴用。`[[ ]]` 里的内容**需你自己替换**，发布前搜 `[[` 全部处理掉。
> 所有规则依据均为 Modrinth 官方 [Content Rules](https://modrinth.com/legal/rules) 原文。

---

## ⚠️ 会导致驳回的硬性规则（务必先读）

| 规则原文 | 对我们的影响 |
|---|---|
| §5.2 **"Project titles are only the name of the project, without any other unnecessary filler data."** | **标题只能是项目名**，不能塞 `26.1.2`、`(Unofficial Port)` 这类附加信息 |
| §5.3 **"Project summaries contain a small summary... without any formatting and without repeating the project title."** | Summary **不能带 Markdown 格式**，**不能重复标题里的词** |
| §1.9 不得 **"Give the impression that they emanate from or are endorsed by... any other person or entity, if this is not the case."** | 必须明确「非官方、未获 TACZ 团队背书」，否则可能被判**冒充/误导** |
| §4 **"...it is a license-abiding 'fork'... We define 'forks' as modified copies of a project which have diverged substantially"** | 我们靠 **GPL-3.0 合规 fork** 这一条立足，描述里必须写明许可与来源 |
| §2.1 描述须回答：做什么 / 为何下载 / 下载前必须知道的关键信息 | 三点都要覆盖（下面文案已覆盖） |
| §5.6 **"All dependencies must be specified in the Dependencies section"** | 依赖必须填在 Dependencies 字段，**不能只写在描述里** |
| §5.1 metadata 须与别处**一致** | License / 环境 / 标签要和 GitHub、CurseForge 对得上 |

> **另注**：默认枪包资源是 `CC BY-NC-ND 4.0`（NC = 非商业），
> **[[ 必须到 Settings → Monetization 关闭货币化 ]]**。

---

## ① Project Title

```
[UNOFFICIAL]TaCZ Refabricated
```

> ✅ **无版本号** —— 这是 §5.2 真正要规避的 filler data。
>
> ✅ **`[UNOFFICIAL]` 前缀是有意保留的**，理由有三：
> 1. §5 前言原文写明这些条目 *"will not necessarily always be enforced"*
>    —— §5.2 属**软规则**（影响审核速度，不构成驳回理由）；
> 2. 它正面服务于 §1.9 这条**硬规则** —— 不得
>    *"give the impression that they... are endorsed by any other person or
>    entity, if this is not the case"*。标注 UNOFFICIAL 恰恰是在履行该条；
> 3. **实证先例**：Modrinth 线上项目
>    `[UNOFFICIAL] TaCZ 1.21.1 NeoForge Port`（49 万次下载）连版本号都带着，
>    至今正常在线。
>
> ❌ 不要加 `26.1.2` 或 `(Unofficial Port)` 这类后缀 —— 前者是版本号，
> 后者与前缀语义重复，都属真正意义上的 filler data。

---

## ② Summary（≤256 字符，纯文本无格式）

```
A community Fabric port with customizable firearms, a gun-smithing workbench, built-in tactical-equipment support, and compatibility with most standard ZIP knife packs.
```

> 168 字符。无 Markdown、不重复标题里的 "TaCZ Refabricated" —— 符合 §5.3。

---

## ③ Description

```markdown
# Unofficial Fabric port

> **This is an unofficial community port. It is not an official TACZ release and
> is not affiliated with, reviewed by, or endorsed by the TACZ Dev Team.**

## What this project does

**Timeless and Classics Guns: Zero (TaCZ)** is a modern firearms mod: deeply
customizable guns with attachments, optics and ammo types, plus a gun-smithing
workbench for building and modifying weapons. It also supports third-party
"gun packs" that add entirely new weapon sets.

This project **ports that mod to Minecraft 26.1.2 on Fabric**. It also includes a
code-only compatibility port of **LesRaisins Tactical Equipements (LRTactical/LR)**,
so no separate LR mod jar is required. The aim is to keep the existing TaCZ and
LRTactical content-pack ecosystems usable without bundling third-party weapon art.

## Why you might want it

If you want TaCZ's gun system on Fabric and on a game version the original does
not target, this port provides it, including compatibility fixes for older gun
packs. The built-in LR layer also loads **most standard, unencrypted ZIP knife
packs** directly from `.minecraft/tacz/`.

## Credits and licensing

| | |
|---|---|
| Original mod | **Timeless and Classics Guns: Zero**, by the **TACZ Dev Team** |
| Upstream Fabric port | [`Sh1roCu/TACZ-Refabricated`](https://github.com/Sh1roCu/TACZ-Refabricated) |
| Baseline | upstream **1.1.8-hotfix** |
| Built-in LR code layer | [`LesRaisins-Studios/LesRaisins-Tactical-Equipements`](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements), code only |
| Source of this port | https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial |

The original mod is licensed **GPL-3.0**, which permits this port; this project is
released under GPL-3.0 as well.

**Note:** the bundled default gun pack's *assets* are licensed
**CC BY-NC-ND 4.0** (NonCommercial, NoDerivatives) — separate from the code
license. Don't sell them, and don't modify-then-redistribute them. To make your
own pack, create a separate pack instead of editing the default.

## What was changed from the original

The target game version removed or reworked a number of APIs, so this is more than
a recompile:

- **Scope rendering adapted to 26.1.2's delayed rendering pipeline**, including
  depth-aperture cleanup and Iris HAND-pass compatibility.
- **Networking** migrated to the current packet/codec system.
- **Resource and data-pack loading** updated for the new formats.
- **Gun pack compatibility fixes** so older third-party packs keep loading.
- **LRTactical built in** as a code-only compatibility layer for melee, throwable,
  consumable and detonator data. Most ordinary unencrypted ZIP knife packs work
  without a separate LR installation.

## Before you download — important

**R1 is the first public release-labelled 26.1.2 build. It is playable, but
third-party pack and mod combinations can still expose compatibility issues.**

Requirements: **Fabric**, **Fabric API**, **Forge Config API Port**, and
**Java 25+**. Exact game and dependency versions are listed per file.
There is no Forge or NeoForge build.

## Installing gun packs

Gun packs go in **`.minecraft/tacz/`** — *not* `tacz_backup`.

```
.minecraft/
├── tacz/                  ← put gun packs HERE (created on first launch)
│   ├── some_pack.zip      ← .zip works directly, no need to extract
│   └── another_pack/      ← extracted folders also work
└── tacz_backup/           ← input directory for the legacy pack converter only
```

A folder or zip is only recognised if **`gunpack.meta.json` exists at its root**:

```json
{ "namespace": "your_pack_namespace" }
```

Without it the pack is **silently skipped**.

**If a pack doesn't load, check in order:**

1. Is it in `tacz/` rather than `tacz_backup/`?
2. Does `gunpack.meta.json` exist?
3. Is the zip nesting right? That file must be at the zip **root**, not inside
   `zip/packname/`.
4. Check the log for `Mod version mismatch`.

**On the reported version:** this port reports the upstream baseline (`1.1.8`)
followed by build metadata after a `+`. SemVer ignores build metadata when
comparing versions, so packs requiring e.g. `"tacz": ">=1.1.8"` load normally.

## Built-in LRTactical / ZIP knife packs

The R1 jar already provides the `lrtactical` compatibility ID and runtime code.
**Do not install a separate LRTactical jar.** Most ordinary LR knife packs work as
ZIP files when they:

- are unencrypted and use the standard LR data/display layout;
- contain `gunpack.meta.json` at the ZIP root; and
- are placed directly in `.minecraft/tacz/`.

Common melee indexes, left/right attacks, cooldowns, delays, hitboxes, attributes,
models, textures, animations, scripts and workbench recipes are supported. This is
not a complete copy of the original add-on: `flash_shield`, some advanced systems,
and TacZ:Arcana-encrypted packs are not supported. The original LRTactical art and
sounds are not redistributed.

## Known limitation: packs requiring TacZ:Arcana

Some gun packs **cannot load on any TACZ build without TacZ:Arcana**. Symptom:
**purple/black missing textures and no models**, while gun entries, names and
recipes appear correctly.

To identify one, extract it and look for:

- `recursion/taczpack.dat` (often tens of MB — most of the pack), or
- `data/<namespace>/expansions/taczexpands.data`, and
- **no `.png` / `.ogg` / `geo_models/` / `textures/` anywhere**

Such packs ship models, textures, animations and sounds **encrypted** inside that
`.dat`, leaving only JSON indexes. Decryption is handled by the separate
Forge-only, closed-source mod **TacZ:Arcana**.

No TACZ build includes that decryption — not the original Forge mod, not the
upstream Fabric port, and not this one. **This is not a defect in this port and
cannot be fixed here.**

## Reporting issues

Report issues to this project's issue tracker, not to the original authors — they
have no obligation to support this port.

## Disclaimer

Provided **"as is", without warranty of any kind**. You assume all risk, including
world corruption, crashes, data loss and mod conflicts. Non-commercial project.
```

---

## ④ 版本上传（Create version）

| 字段 | 填什么 |
|---|---|
| **Version name** | `1.1.8+fabric.26.1.2.R1` |
| **Version number** | `1.1.8+fabric.26.1.2.R1` |
| **Release channel** | **Release** |
| **Loaders** | `Fabric` |
| **Game versions** | `26.1.2` |

### Dependencies（§5.6 强制要求填在此处）

| 项目 | 类型 |
|---|---|
| **Fabric API** | `Required` |
| **Forge Config API Port** | `Required` |

---

## ⑤ Changelog

```markdown
R1 public release for Minecraft 26.1.2 Fabric.

Based on TACZ `1.1.8-hotfix` and the upstream Fabric port
`Sh1roCu/TACZ-Refabricated`.

### Highlights
- Built-in LRTactical/LR code compatibility: no separate LR jar is required, and
  most standard unencrypted ZIP knife packs can be loaded directly from `tacz/`.
- Fixed PAL third-person animations becoming permanently muted after switching
  weapons, plus prone-to-standing pose contamination and one-shot replay issues.
- Restored Controllable bindings/rumble, Shoulder Surfing behavior, and Carry On
  exclusions with currently available 26.1.2 integrations.
- Improved scope depth cleanup, physical ocular-ring rendering, Iris compatibility,
  illuminated reticles, and clipping of gun/fire geometry inside the optic.
- Fixed precise projectile spawn-coordinate transport, orientation-dependent ADS
  constraint displacement, and duplicate normal transformation.
- Fixed cross-dimension/rejoin gun state, left-handed third-person rendering,
  gun-pack filtering, heat HUD, interaction hints, and workbench preview controls.
- Removed the misleading unfinished weapon-level `0 (MAX)` placeholder.

### Notes
- Requires Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API
  0.155.2+26.1.2, Forge Config API Port 26.1.5+, and Java 25+.
- PAL, Controllable, Shoulder Surfing, Carry On, Iris and Sodium remain optional.
- The `+fabric.26.1.2.R1` suffix is build metadata; pack comparison still sees the
  compatible TACZ baseline as `1.1.8`.

### Known limitations
- LRTactical is a code-only partial port. `flash_shield`, the custom cooldown
  inventory overlay, effect-cloud-specific tooltips and some advanced systems are
  not complete; original restricted art/audio is not bundled.
- TacZ:Arcana-encrypted packs cannot be decrypted on this Fabric build.
- The rejected first-person projectile world-offset experiment remains reverted;
  no unverified muzzle-coordinate transform is claimed in R1.
```

---

## ⑥ 其他设置

- **Categories**：`Adventure`、`Equipment`
- **Environments**：`Client: Required`、`Server: Required`
- **License**：`GPL-3.0-only`
- **Links**：Source 填 `https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial`，Issues 填 `https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues`（§5.4 要求链接指向公开且相关的资源）
- **Gallery**：§5.5 要求每张图**都要有标题**
- **Monetization**：**必须关闭**
