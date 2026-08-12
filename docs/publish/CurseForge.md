# CurseForge 发布文案

> 复制粘贴用。`[[ ]]` 里的内容**需你自己替换**，发布前搜 `[[` 全部处理掉。
> 所有规则依据均为 CurseForge 官方
> [Moderation Policies](https://support.curseforge.com/support/solutions/articles/9000197279-moderation-policies)（2025-12-31 版）原文。

---

## ⚠️ 会导致驳回的硬性规则（务必先读）

| 规则原文 | 对我们的影响 |
|---|---|
| **"Names should not contain game name, versions, file versions ect...."** | **项目名不能带 `26.1.2`、`1.1.8`、`Minecraft` 等任何版本/游戏名**。技术信息只能放描述和文件标签里 |
| **"Names must be in English."** | 项目名必须英文。描述可含中文，但**英文必须在中文之前** |
| **"If it is a fork, you must describe what has changed from the original and you cannot copy the description of the original project."** | 必须写清**改了什么**，且**不能照抄**上游描述 |
| **"...credit and link the original creator and make sure forking is allowed"** | 必须署名 + 链接原作者 |
| **"External download links for files are not allowed."** | 描述里**不能放外部下载链接**（GitHub Releases 也算）。源码/Issue 链接放 Links 字段 |
| 捐赠/个人网站/跨站链接 **"must appear at the bottom of the page"** | 这类内容只能放**页面最底部** |
| **Avatar 必须 400×400**，不可纯色、不可用受版权图像 | 别直接拿 TACZ 官方图当头像（那是他人版权素材） |

> **另注**：默认枪包资源是 `CC BY-NC-ND 4.0`（NC = 非商业），
> **[[ 必须到项目设置里关闭 Rewards Program（收益分成） ]]**，否则违反 NC 条款。

---

## ① Project Name

```
[UNOFFICIAL]TaCZ Refabricated
```

> ✅ **无版本号、无游戏名、纯英文** —— 符合 "Name - functional Information" 规则
> （该条禁止的是 *game name, versions, file versions*，`[UNOFFICIAL]` 属状态标注，不在其列）。
>
> ✅ **有实证先例**：CurseForge 线上项目 `[UNOFFICIAL] TaCZ NeoForge Port`
> （48 万次下载）用的正是同一形式，已通过审核。
>
> ✅ **降低风险**：明确标注非官方，可避免被误认为官方发布 ——
> 这与规则中「必须署名原作者、不得冒充」的要求方向一致。
>
> ❌ 不要用 `TaCZ Fabric 26.1.2 Port`（含版本号 + 游戏名，会被驳回）。
> 换 MC 版本时项目名无需变动，版本信息由文件名承载。

---

## ② Summary（一句话）

```
An unofficial Fabric port of TaCZ with customizable firearms, built-in tactical-equipment support, and compatibility with most standard ZIP knife packs.
```

> CurseForge 要求 Summary 是描述的 tldr、**尽量别与描述重复**，故此处不复述版本号。

---

## ③ Description

> ⚠️ 下面这段**不是**上游描述的复制品 —— 按规则必须自己写、且要说明「改了什么」。

```markdown
# TaCZ Refabricated — Unofficial Fabric Port

> **Unofficial community port. Not an official TACZ release, and not
> affiliated with, reviewed by, or endorsed by the TACZ Dev Team.**

## What this project is

**Timeless and Classics Guns: Zero (TaCZ)** is a modern firearms mod featuring
highly customizable guns — attachments, optics, ammo types, and a gun-smithing
workbench for crafting and modifying weapons.

This project is a **community port of that mod to Minecraft 26.1.2 on Fabric**.
It also includes a code-only compatibility port of **LesRaisins Tactical
Equipements (LRTactical/LR)**. No separate LR jar is required, and the project
does not bundle third-party weapon art or paid content.

## Credits — original work

| | |
|---|---|
| Original mod | **Timeless and Classics Guns: Zero**, by the **TACZ Dev Team** |
| Upstream Fabric port | [`Sh1roCu/TACZ-Refabricated`](https://github.com/Sh1roCu/TACZ-Refabricated) |
| Baseline version | upstream **1.1.8-hotfix** |
| Built-in LR code layer | [`LesRaisins-Studios/LesRaisins-Tactical-Equipements`](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements), code only |
| Source of this port | https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial |

The original mod is licensed under **GPL-3.0**, which permits this port. This
project is likewise released under GPL-3.0.

## What changed from the original

This is a port, not a content fork — but the target game version removed or
reworked a number of APIs, so substantial internal rework was required:

- **Scope rendering adapted to 26.1.2's delayed rendering pipeline**, including
  depth-aperture cleanup and Iris HAND-pass compatibility.
- **Networking updated** to the current packet/codec system.
- **Data-pack and model loading updated** for the new resource format.
- **Gun pack loading fixes** so that existing third-party gun packs — including
  ones written for older versions — continue to work.
- **LRTactical built in** as a code-only compatibility layer. Melee, throwable,
  consumable and detonator data are supported, and most ordinary unencrypted ZIP
  knife packs work without installing a separate LR mod.

## Status: R1 release build

**R1 is the first public release-labelled 26.1.2 build. It is playable, but
third-party pack and mod combinations can still expose compatibility issues.**

## Requirements

| | |
|---|---|
| Loader | **Fabric** |
| Fabric API | Required |
| Forge Config API Port | **Required** |
| Java | **25+** |

Exact Minecraft and dependency versions are listed on each file — check the file
you are downloading.

> **Fabric only.** There is no Forge or NeoForge build of this port.

## Installing gun packs

Gun packs go in **`.minecraft/tacz/`** — *not* `tacz_backup`.

```
.minecraft/
├── tacz/                  ← put gun packs HERE (created on first launch)
│   ├── some_pack.zip      ← .zip works directly, no need to extract
│   └── another_pack/      ← extracted folders also work
└── tacz_backup/           ← input directory for the legacy pack converter only
```

A folder or zip is recognised as a gun pack only if **`gunpack.meta.json` exists
at its root**:

```json
{ "namespace": "your_pack_namespace" }
```

Without that file the pack is **silently skipped**.

### If a pack doesn't load, check in this order

1. Is it in `tacz/` rather than `tacz_backup/`?
2. Does `gunpack.meta.json` exist?
3. Is the zip nesting correct? That file must sit at the zip **root**, not inside
   `zip/packname/`.
4. Check the log for `Mod version mismatch`.

### Note on the reported mod version

This port reports its version with the upstream baseline first
(`1.1.8`), followed by build metadata after a `+`. Under the SemVer
specification build metadata is **ignored when comparing versions**, so gun packs
that declare a requirement such as `"tacz": ">=1.1.8"` load normally.

## Built-in LRTactical / ZIP knife packs

The R1 jar already provides the `lrtactical` compatibility ID and runtime code.
**Do not install a separate LRTactical jar.** Most ordinary LR knife packs work as
ZIP files when they are unencrypted, use the standard LR data/display layout,
contain `gunpack.meta.json` at the ZIP root, and are placed in `.minecraft/tacz/`.

Common melee indexes, left/right attacks, cooldowns, delays, hitboxes, attributes,
models, textures, animations, scripts and workbench recipes are supported. This is
not a complete copy of the original add-on: `flash_shield`, some advanced systems,
and TacZ:Arcana-encrypted packs are not supported. The original LRTactical art and
sounds are not redistributed.

## Known limitation: packs requiring **TacZ:Arcana**

Some gun packs **cannot be loaded by any TACZ build that lacks TacZ:Arcana**.
Symptom: **missing textures (purple/black) and models not rendering**, while gun
entries, names and recipes all appear correctly.

To identify such a pack, extract it and look for:

- `recursion/taczpack.dat` (often tens of MB — most of the pack's size), or
- `data/<namespace>/expansions/taczexpands.data`, and
- **no `.png` / `.ogg` / `geo_models/` / `textures/` anywhere in the pack**

Those packs ship their models, textures, animations and sounds **encrypted**
inside that `.dat`, leaving only JSON indexes on disk. Decryption is performed by
the separate mod **TacZ:Arcana**, which is Forge-only and closed-source.

No TACZ build contains that decryption logic — not the original Forge mod, not the
upstream Fabric port, and not this one. **This is therefore not a defect in this
port and cannot be resolved here.**

## Licensing

Two independent licenses apply:

| Scope | License |
|---|---|
| **Code** | **GPL-3.0** (inherited from the original mod) |
| **Bundled default gun pack assets** | **CC BY-NC-ND 4.0** |

The code is copyleft — redistribute modifications under GPL-3.0. The bundled
default pack assets are **NonCommercial + NoDerivatives**: they may not be sold,
and may not be modified and redistributed. To build your own pack, create a
separate pack rather than editing the default one.

## Reporting issues

Please report issues to this project, not to the original authors — they have no
obligation to support this port. If an issue is confirmed to originate upstream,
it can be raised there afterwards.

## Disclaimer

Provided **"as is", without warranty of any kind**. You assume all risk, including
world corruption, crashes, data loss, and mod conflicts. Non-commercial project.
```

---

## ④ 文件上传（Upload File）

| 字段 | 填什么 |
|---|---|
| **Display Name** | `TACZ-Refabricated-26.1.2-1.1.8+fabric.26.1.2.R1` |
| **Release Type** | **Release** |
| **Game Version** | `26.1.2` |
| **Modloader** | `Fabric` |
| **Java Version** | `Java 25` |

> 版本号信息放在**文件名**里 —— 这正是 CurseForge 规则要求的
> "Any technical information belongs in the description or **relevant file tagging**"。

### Relations（依赖）—— 漏填会导致用户装不上

| 项目 | Relation Type |
|---|---|
| **Fabric API** | `Required Dependency` |
| **Forge Config API Port** | `Required Dependency` |

---

## ⑤ Changelog

```markdown
R1 public release for Minecraft 26.1.2 Fabric.

Based on TACZ `1.1.8-hotfix` and the upstream Fabric port
`Sh1roCu/TACZ-Refabricated`.

### Highlights
- Built-in LRTactical/LR code compatibility: no separate LR jar is required, and
  most standard unencrypted ZIP knife packs can be loaded directly from `tacz/`.
- Fixed PAL third-person animation loss after weapon switching and prone-to-standing
  pose contamination while preserving PAL's permanent rotation adjustment.
- Restored Controllable, Shoulder Surfing and Carry On integrations available on
  Minecraft 26.1.2.
- Improved scope depth cleanup, physical ocular-ring rendering, Iris compatibility,
  illuminated reticles and optic clipping for gun/fire geometry.
- Fixed precise projectile spawn-coordinate transport, orientation-dependent ADS
  constraint displacement and duplicate normal transformation.
- Fixed cross-dimension/rejoin gun state, left-handed third-person rendering,
  gun-pack filtering, heat HUD, interaction hints and workbench preview controls.
- Removed the misleading unfinished weapon-level `0 (MAX)` placeholder.

### Requirements
- Minecraft 26.1.2
- Fabric Loader 0.19.3+
- Fabric API 0.155.2+26.1.2
- Forge Config API Port 26.1.5+
- Java 25+

### Known limitations
- LRTactical is a code-only partial port. `flash_shield`, the custom cooldown
  inventory overlay, effect-cloud-specific tooltips and some advanced systems are
  not complete; original restricted art/audio is not bundled.
- TacZ:Arcana-encrypted packs cannot be decrypted on this Fabric build.
- The rejected first-person projectile world-offset experiment remains reverted;
  R1 does not claim an unverified muzzle-coordinate transform.
```

---

## ⑥ 其他设置

- **Categories**：`Armor, Tools, and Weapons` + `Adventure and RPG`
- **License**：`GNU General Public License version 3 (GPLv3)`
  （枪包资源的 CC 许可在描述里已说明）
- **Links**：Source 填 `https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial`，Issues 填 `https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues`
  —— 注意**不要在描述正文里放下载链接**（违反 Third Party Downloads 规则）
- **Avatar**：**[[ 需自制 400×400 图，不可纯色，不可直接用 TACZ 官方图 ]]**
- **Rewards Program**：**必须关闭**
