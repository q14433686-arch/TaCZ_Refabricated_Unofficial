# CurseForge 发布文案

> 复制粘贴用。`[[ ]]` 里的内容**需你自己替换**，发布前搜 `[[` 全部处理掉。
> 所有规则依据均为 CurseForge 官方
> [Moderation Policies](https://support.curseforge.com/support/solutions/articles/9000197279-moderation-policies)（2025-12-31 版）原文。

---

## ⚠️ 会导致驳回的硬性规则（务必先读）

| 规则原文 | 对我们的影响 |
|---|---|
| **"Names should not contain game name, versions, file versions ect...."** | **项目名不能带 `26.2`、`1.1.8`、`Minecraft` 等任何版本/游戏名**。技术信息只能放描述和文件标签里 |
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
> ❌ 不要用 `TaCZ Fabric 26.2 Port`（含版本号 + 游戏名，会被驳回）。
> 换 MC 版本时项目名无需变动，版本信息由文件名承载。

---

## ② Summary（一句话）

```
An unofficial community port of the TaCZ gun mod, bringing its customizable firearms to newer Minecraft versions on Fabric.
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

This project is a **community port of that mod to the Fabric loader on newer
Minecraft versions**. It adds no new guns or gameplay of its own; the goal is to
make the existing mod run on a platform and game version it did not previously
support.

## Credits — original work

| | |
|---|---|
| Original mod | **Timeless and Classics Guns: Zero**, by the **TACZ Dev Team** |
| Upstream Fabric port | [`Sh1roCu/TACZ-Refabricated`](https://github.com/Sh1roCu/TACZ-Refabricated) |
| Baseline version | upstream **1.1.8-hotfix** |
| Source of this port | [[ 填你的 GitHub 仓库地址 ]] |

The original mod is licensed under **GPL-3.0**, which permits this port. This
project is likewise released under GPL-3.0.

## What changed from the original

This is a port, not a content fork — but the target game version removed or
reworked a number of APIs, so substantial internal rework was required:

- **Rendering layer partially rewritten.** The stencil buffer, which upstream
  relied on for through-the-optic (in-scope) rendering, no longer exists on the
  target version. The scope rendering path was reimplemented. **Some visual
  effects therefore differ from the original**, which is a known trade-off.
- **Networking updated** to the current packet/codec system.
- **Data-pack and model loading updated** for the new resource format
  (directory naming, item model definitions, component-based item data).
- **Gun pack loading fixes** so that existing third-party gun packs — including
  ones written for older versions — continue to work.

## Status: Alpha 2 test build

**Playable, but under active testing. Expect bugs.**

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
| **Display Name** | `TACZ-Refabricated-26.2-1.1.8+fabric.26.2.Beta-2` |
| **Release Type** | **Alpha** |
| **Game Version** | `26.2` |
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
Beta 3 Hotfix – public test build of this Fabric port.

Ported from the upstream Fabric project (`Sh1roCu/TACZ-Refabricated`),
based on TACZ `1.1.8-hotfix`.

### Highlights
- Fixed a long-standing viewmodel bug: while aiming-down-sights, firing or reloading
  made the whole gun body slide sideways by several degrees when facing the four
  diagonal headings (SE/NW drifted left, NE/SW right; vanilla renderer only — Iris
  shader packs were not affected). Root cause: the 26.2 vanilla hand pass premultiplies
  a per-facing camera base rotation that the ported ADS-constraint math did not
  neutralize; the anisotropic constraint coefficients were therefore rotated by that
  facing (R·diag·R leakage, ~sin(2·yaw)). The fix conjugates against the inverse base
  (Bᵀ·diag·Bᵀ), restoring exact 1.21.1 behavior in all 8 headings. Hip-fire and
  shader-pack rendering are unchanged.
- See-through scopes (offscreen-mask path): the gun body, non-scope attachments and
  the main muzzle-flash quad are now discarded inside the sight picture, so the
  world shows through the scope lens correctly (vanilla renderer).
- Scope mask shape upgraded with an opt-out: the ocular mask is now the filled
  convex hull of the ocular projection (`ScopeMaskHullFill=true`), fixing sparse
  sliver-glass oculars (AUG built-in sight, Elcan slats) leaving scope-body
  fragments inside the lens. Set to false to instantly fall back to the legacy
  geometric projection.

### Notes
- Requires Java 25 and Forge Config API Port.
- Gun packs requiring TacZ:Arcana (encrypted assets) will show missing textures.

### Known issues
- Under an active Iris shader pack, recoloring the laser sight may have no visible
  effect on some NVIDIA drivers (AMD, and NVIDIA without shaders, are unaffected);
  root cause under investigation.
- The ocular mask is now the filled convex hull of the ocular projection
  (ScopeMaskHullFill=true). On some scopes the hull can be slightly larger than
  the true aperture, nibbling the scope body's inner rim; if you see that, set
  ScopeMaskHullFill=false to fall back instantly and send us a screenshot with
  ScopeMaskDebug=true.
- PIP / second-world scope rendering is not enabled by default and remains paused.
- Under active Iris shader packs the in-lens masking stays in its safe fallback
  (scope tube interior visible inside the ocular).
- LRTactical is partially integrated; flash shield and some add-on systems are
  incomplete.

```

---

## ⑥ 其他设置

- **Categories**：`Armor, Tools, and Weapons` + `Adventure and RPG`
- **License**：`GNU General Public License version 3 (GPLv3)`
  （枪包资源的 CC 许可在描述里已说明）
- **Links**：Source / Issues 填 `[[ 你的 GitHub 仓库 ]]`
  —— 注意**不要在描述正文里放下载链接**（违反 Third Party Downloads 规则）
- **Avatar**：**[[ 需自制 400×400 图，不可纯色，不可直接用 TACZ 官方图 ]]**
- **Rewards Program**：**必须关闭**
