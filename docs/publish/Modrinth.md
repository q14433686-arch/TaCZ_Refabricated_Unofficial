# Modrinth 发布文案

> 复制粘贴用。`[[ ]]` 里的内容**需你自己替换**，发布前搜 `[[` 全部处理掉。
> 所有规则依据均为 Modrinth 官方 [Content Rules](https://modrinth.com/legal/rules) 原文。

---

## ⚠️ 会导致驳回的硬性规则（务必先读）

| 规则原文 | 对我们的影响 |
|---|---|
| §5.2 **"Project titles are only the name of the project, without any other unnecessary filler data."** | **标题只能是项目名**，不能塞 `26.2`、`(Unofficial Port)` 这类附加信息 |
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
> ❌ 不要加 `26.2` 或 `(Unofficial Port)` 这类后缀 —— 前者是版本号，
> 后者与前缀语义重复，都属真正意义上的 filler data。

---

## ② Summary（≤256 字符，纯文本无格式）

```
A community port of the Timeless and Classics Guns mod to the Fabric loader, with customizable firearms, attachments, and a gun-smithing workbench.
```

> 148 字符。无 Markdown、不重复标题里的 "TaCZ Refabricated" —— 符合 §5.3。

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

This project **ports that mod to the Fabric loader on a newer Minecraft version**.
It deliberately adds no new guns or gameplay — the aim is to let the existing mod,
and the existing ecosystem of gun packs, keep working.

## Why you might want it

If you want TaCZ's gun system on Fabric and on a game version the original does
not target, this port provides it, including compatibility fixes so that
third-party gun packs — including older ones — still load.

## Credits and licensing

| | |
|---|---|
| Original mod | **Timeless and Classics Guns: Zero**, by the **TACZ Dev Team** |
| Upstream Fabric port | [`Sh1roCu/TACZ-Refabricated`](https://github.com/Sh1roCu/TACZ-Refabricated) |
| Baseline | upstream **1.1.8-hotfix** |
| Source of this port | [[ 填你的 GitHub 仓库地址 ]] |

The original mod is licensed **GPL-3.0**, which permits this port; this project is
released under GPL-3.0 as well.

**Note:** the bundled default gun pack's *assets* are licensed
**CC BY-NC-ND 4.0** (NonCommercial, NoDerivatives) — separate from the code
license. Don't sell them, and don't modify-then-redistribute them. To make your
own pack, create a separate pack instead of editing the default.

## What was changed from the original

The target game version removed or reworked a number of APIs, so this is more than
a recompile:

- **Scope rendering rewritten** — the stencil buffer that upstream used for
  through-the-optic rendering no longer exists. **Some visual effects therefore
  differ from the original.**
- **Networking** migrated to the current packet/codec system.
- **Resource and data-pack loading** updated for the new formats.
- **Gun pack compatibility fixes** so older third-party packs keep loading.

## Before you download — important

**This is an Alpha 2 test build. It is playable, but expect bugs.**

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
| **Version name** | `1.1.8+fabric.26.2.Beta-2` |
| **Version number** | `1.1.8+fabric.26.2.Beta-2` |
| **Release channel** | **Alpha** |
| **Loaders** | `Fabric` |
| **Game versions** | `26.2` |

### Dependencies（§5.6 强制要求填在此处）

| 项目 | 类型 |
|---|---|
| **Fabric API** | `Required` |
| **Forge Config API Port** | `Required` |

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
  both muzzle-flash layers (the main quad and the additive glow swirl) are now
  discarded inside the sight picture, so the world shows through the scope lens
  correctly (vanilla renderer). The glow layer replicates vanilla's 26.2
  energy_swirl setup (shared entity shader + APPLY_TEXTURE_MATRIX + additive
  blending) with the mask discard added on top, so its look is unchanged.
- Third-person gun animation: fixed a long-standing failure where switching guns
  once with Player Animation Library installed permanently killed all third-person
  gun animations for the session (a full world reload was required). Root cause is in
  PAL itself: its FADE_OUT modifier is never removed from the controller chain and
  zeroes out everything below it once finished; our stop path now uses a
  FADE_IN-to-null fade, which PAL removes correctly after completion. Gun switching,
  holstering and re-equipping now re-play animations as expected.
- Scope mask shape upgraded with an opt-out: the ocular mask is now the filled
  convex hull of the ocular projection (`ScopeMaskHullFill=true`), fixing sparse
  sliver-glass oculars (AUG built-in sight, Elcan slats) leaving scope-body
  fragments inside the lens. Set to false to instantly fall back to the legacy
  geometric projection.
### Notes
- Requires Java 25 and Forge Config API Port.
- Gun packs requiring TacZ:Arcana (encrypted assets) will show missing textures.

### Known issues
- Laser-sight recoloring works via vertex color on a vanilla emissive render type.
  Under an active Iris shader pack the pack decides whether that color is applied:
  minimal packs without colored-emissive support will keep the laser at its default
  color. This is a shader-pack limitation, not a GPU or driver issue.
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

## ⑥ 次回发布用 Changelog 草稿（案例⑧ 收口 · 2026-08-12）

> 下一版（Beta 3 Hotfix-2 / Beta 4）发布时替换 §⑤ 的 `` ```markdown `` 块；下方的
> 旧块是 Beta 3 Hotfix 已发布原文，留档勿动。

```markdown
Beta 3 Hotfix-2 – public test build of this Fabric port.

### Fixes
- First-person ADS viewmodel: reverted the 2026-08-10 diagonal-heading recoil fix
  (the B^T*diag*B^T constraint sandwich). In-body A/B testing proved that fix was
  the injector of the ADS regression reported on 2026-08-11 — the whole arm+gun
  assembly offset rotating with player heading, "running backward" shift when
  looking up/down, and over-pressed recoil. The constraint translation now writes
  the verified-clean plain form (config `ConstraintCompensateMode=0`, the new
  default), which in-body testing confirms is free of all of the above symptoms
  in every heading. Hip-fire and Iris shader-pack rendering are unchanged.

### Known issues
- The pre-existing diagonal-heading ADS lateral drift (SE/NW slightly left, NE/SW
  slightly right; vanilla renderer only) returns with the revert. Three candidate
  reference frames (hand-pass entry base, RenderSystem modelView, live pose frame)
  were each tested in-body and falsified — every one re-injected heading-locked
  rotation and/or crosshair-locked rigidity — so the drift is parked as a known
  port-era issue until trustworthy 26.2 hand-pass frame data is available.
```

---

## ⑥ 其他设置

- **Categories**：`Adventure`、`Equipment`
- **Environments**：`Client: Required`、`Server: Required`
- **License**：`GPL-3.0-only`
- **Links**：Source / Issues 填 `[[ 你的 GitHub 仓库 ]]`（§5.4 要求链接指向公开且相关的资源）
- **Gallery**：§5.5 要求每张图**都要有标题**
- **Monetization**：**必须关闭**
