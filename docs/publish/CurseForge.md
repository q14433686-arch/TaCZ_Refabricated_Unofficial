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
| Bundled mesh renderer | **TacZ Mesh Loader [TML]** by **VellEagle** — ported into this build and extended here (GPL-3.0) |
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
- **High-poly `mesh` guns render out of the box.** The community *TacZ Mesh Loader*
  addon (by VellEagle, GPL-3.0) is built in, so packs using that format no longer
  need it installed separately. Vertex work is baked on the GPU, both in first
  person and for guns held by other players / dropped on the ground.
- **In-game config saves again.** Settings changed in the config screen are written
  back to `config/tacz-client.toml`; a Forge Config API Port 26.x regression had
  made saves in-memory only, so every setting reset on restart.

## Status: test build

**Playable, but under active testing. Expect bugs.** The exact build identity is
in the file name and version field of each upload — see the release notes there
for what that build contains.

## Configuring

Almost every option is available **in game**: open the mod list (Mod Menu), pick
**Timeless and Classics Guns**, then the gear icon, and pick the **Render**
category (the screen is one searchable list; the other categories are alongside). Saving there takes effect immediately and is written back to
`config/tacz-client.toml`, so editing that file by hand is normally unnecessary —
it is only needed for the handful of debug keys that have no in-game entry, and it
requires a game restart.

One notable option is **Scope Picture-in-Picture (PIP)**, an experimental in-lens
world renderer for high-magnification scopes. It is **off by default** and is
turned on in that same Render page (plus "Allow PIP with Shader Packs" if you use
Iris). Only `ScopePipShadowScale` needs a restart or a dimension change; everything
else applies live. Turning the option back off returns the game to the default
rendering path.

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

## FAQ: REI/JEI cheating on a dedicated server gives purple/black items with `item.tacz.*` names

**Symptom:** on a dedicated server, items cheated / picked up from REI or JEI
(and sometimes `/give tacz:modern_kinetic_gun` without components) show
**missing textures and raw keys** such as `item.tacz.modern_kinetic_gun` or
`item.tacz.attachment`, while the same items render correctly inside the
REI/JEI list, and single-player / LAN is fine.

**Cause:** TACZ items are not self-contained: gun, ammo, attachment and
workbench ids live in the `minecraft:custom_data` component (`GunId` /
`AmmoId` / `AttachmentId` / `BlockId`). When REI **is not installed on the
server**, its cheat-give falls back to a client-built `/give` command carrying
only the item's registry id and an **empty component tag**
(`ClientHelperImpl#tryCheatingEntry`, `tagMessage = /* TODO 24w09a ... */ ""`).
The server receives a **bare item**; TACZ then reads `tacz:empty` as the
content id and falls back to the vanilla `item.*` key with no model. The same
happens on the original mod — this is REI behaviour, not a port defect. (The
iron ammo box keeps a real name because its key ships in the mod jar; that is
the bare-item fallback itself.)

**Fix:**

- **Install the same REI version on the dedicated server** — cheating then uses
  REI's network packet, which serialises the full item components;
- or take items from **TACZ's own creative tabs** or the built-in **Ammo /
  Attachment / Gun Smith Table** recipe categories (entries are built with the
  required id components);
- for `/give`, always append components, e.g.
  `/give @p tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}]`.

JEI's client-side fallback uses the vanilla creative-slot packet, which does
carry components; if you see the same symptom with JEI alone, please report it
with the exact viewer and server versions.

## Licensing

The repository is not covered by a single license:

| Scope | License |
|---|---|
| **Code** (this port and the original mod's code) | **GPL-3.0** |
| **Ported third-party code** — TacZ Mesh Loader (VellEagle), LRTactical (LesRaisins) | **GPL-3.0**, each under its upstream project's license |
| **Bundled runtime library** — Mayday Animation Engine 1.1.1 | **MIT** |
| **Bundled default gun pack assets** | **CC BY-NC-ND 4.0** |

The code is copyleft — redistribute modifications under GPL-3.0. The bundled
default pack assets are **NonCommercial + NoDerivatives**: they may not be sold,
and may not be modified and redistributed. To build your own pack, create a
separate pack rather than editing the default one. Code licenses never cover art
assets; a full notice lives in `LICENSES.md` in the source repository.

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
| **Display Name** | `TACZ-Refabricated-26.2-1.1.8+fabric.26.2.R3` |
| **Release Type** | **Release** |
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

> **⚠️ 上面这块是已发布原文（留档勿动），但其中一条已经过时**：
> `PIP / second-world scope rendering is not enabled by default and remains paused.`
> —— PIP 早已实现，只是**默认关闭**、且现在是**游戏内配置界面里的开关**
> （Mod Menu → Timeless and Classics Guns → 齿轮 →「渲染」分类）。
> 下次发布**不要**把这句抄进新 changelog，用下面 ⑤-ter 的措辞。

---

## ⑤-bis 次回发布用 Changelog 草稿（案例⑧ 正式修复 · 2026-08-12）

> R1 发布时用本块替换线上 changelog；§⑤ 里的 Beta 3 Hotfix 块是已发布原文，留档勿动。

```markdown
R1 – first release build of this Fabric port.

### Fixes
- First-person ADS viewmodel: the diagonal-heading recoil fix shipped in
  Beta-3-Hotfix (2026-08-10) turned out to be defective. Its B^T*diag*B^T
  sandwich conjugated only the raw coefficient D = (ICAx-1, ICAy-1, 1-ICAz)
  and left the write-back sign flip Q = diag(-1,-1,+1) outside the frame
  transform; since Q does not commute with rotations, aiming down sights while
  reloading or firing made the whole arm+gun assembly rotate with the player's
  heading (north drifted left, west right, the gun ran backward when looking
  up/down, recoil over-pressed downward — everything normal only under Iris
  shader packs). The constraint translation now conjugates the true authored
  coefficient C = Q*D inside the live pose frame (v = (Q*W*Q)*D*Wᵀ*v0, W = the
  write-time pose rotation), matching 1.21.1 behavior in all eight headings:
  no heading-locked rotation, no diagonal-direction lateral leak, natural
  muzzle feel. Verified in-body on both 26.1.2 and 26.2. Instant fallback:
  ConstraintCompensateMode=0 restores the pre-fix plain form.
- See-through scopes: the physical ocular_ring (the black eyepiece rim present on all
  14 mid/high-magnification scopes of the default pack) is now drawn unclipped while
  aiming, mirroring upstream 1.21.1 stencil-ALWAYS handling — previously it rode the
  mask-clipped body batch, so the oculus mask nibbled its inner rim. Adapted for the
  26.2 mask architecture from the sibling 26.1.2 port's verified fix. Instant
  fallback: ScopeOcularRingFix=false.
- Low-power / red-dot sights (including the low-power side of combo scopes) no longer
  mask-clip the sight body, restoring upstream 1.21.1 renderSight semantics (the sight
  body is drawn unconditionally there). Our clip had been nibbling the sight's own
  inner frame since the scope-mask architecture landed. Instant fallback:
  ScopeSightClipFix=false.
- Player Animation Library, prone-dive exit: this build carries the sibling 26.1.2
  port's controller reset at the prone-to-standing edge (ported 1:1; it passed
  in-body verification on 26.1.2). On 26.2 the following remains a KNOWN ISSUE:
  in third person with PAL installed, after a prone cycle the arms can show a
  stale pose until the camera view is toggled; a prone cycle is required to
  trigger it, standing gun-switches alone do not. Cause not established —
  recorded as phenomenon only.
```

---

## ⑤-ter 次回发布用 Changelog 草稿（R3 · 2026-09-02）

> 依据 `docs/CHANGELOG_26_2_R2.md` 的 R3 段撰写，替换 ⑤-bis 的 R1 草稿。
> 标 *(Pending in-game verification)* 的两项**尚未实机验证**，发布前若已验证请
> 按 `AGENTS.md` §2 的措辞纪律改写或删掉；不要把它们写成已验证。

```markdown
R3 – test build of this Fabric port.

### Highlights
- **High-poly `mesh` guns now render in the world, not just in hand.** Guns from
  packs using the TacZ Mesh Loader format are baked on the GPU when held by other
  players, dropped, or displayed on item frames and pedestals, with a quantized
  lighting cache and a per-frame bake budget, so multiplayer scenes stop paying a
  per-vertex CPU transform cost. The baked detail gates are now scaled by the
  current aiming magnification, so a zoomed sight no longer drops a nearby mesh gun
  to a bare cube. *(Pending in-game verification; turning `MeshGpuWorld` off in the
  config screen returns to the previous behaviour.)*
- **In-game config saves again.** Settings changed in the config screen are written
  back to `config/tacz-client.toml`; a Forge Config API Port 26.x regression had
  left saves in memory only, so every setting reset on restart. Values already
  written into an older TOML file need to be changed and saved once to refresh.
- **Cross-pack crafting recipes load again.** Recipes converted to the newer
  `tacz:nbt` ingredient form (as produced by the community pack upgrader) were
  dropped outright, which showed up as "the addon pack's recipes neither appear nor
  craft". A dedicated ingredient type plus JSON normalisation fixes them, including
  the legacy `{item, nbt}` form whose NBT was silently discarded.
- **In-lens clipping now covers the arm, scope-mounted text and the crosshair**
  under shader packs, so mounted readouts (e.g. the MK5HD ammo counter) stay inside
  the ocular instead of drawing over the scope body.
- **Scope text content fixed:** packs that inline the display string into
  `text_key` (e.g. `%ammo_count%`) no longer show a `Format error:` prefix.
- **Inspect animations are interruptible again while aiming** (firing or reloading
  cancels them as expected).

### Notes
- Requires Java 25 and Forge Config API Port.
- Nearly every option lives in the in-game config screen (Mod Menu → Timeless and
  Classics Guns → gear icon). Only the debug keys and a few idle-release settings
  are TOML-only; `ScopePipShadowScale` additionally needs a restart or a dimension
  change, everything else applies on save.
- Gun packs requiring TacZ:Arcana (encrypted assets) will show missing textures.

### Known issues
- The optional **Scope PIP** in-lens renderer is **experimental and off by
  default**. It is switched on in the in-game config screen — not by editing files —
  and switching it off returns the game to the default rendering path.
- With `ScopePipRerender` on plus an active shader pack, expect roughly half the
  frame rate: the in-lens pass renders the world a second time.
- Mesh guns whose textures live in a `uv/` subfolder show a missing-texture sprite
  in the world (first person is unaffected). Known, not yet fixed; turning
  `MeshGpuWorld` off avoids it.
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
