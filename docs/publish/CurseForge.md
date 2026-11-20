# CurseForge 发布文案

> 更新项目页时只复制 §② Summary 和 §③ 链接的双语正文；Screenshots 按图题另传。§⑤ 将当前测试包说明与历史文件 changelog 分开。
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
Craft guns at specialized benches, fit attachments and load community gun packs on Fabric. Choose the file for your exact Minecraft version.
```

## ③ Description（项目页正文）

打开 [CurseForge-Description.md](CurseForge-Description.md)，复制**全文**到项目的 Description（英文在前、中文在后，无占位符）。Summary 单独填上一栏。不要把仓库指南、历史 changelog 或 Actions 下载链接贴进去。此操作只更新项目介绍，不上传模组文件。正文已经嵌入原图，图址固定到公开提交；若编辑器不保留 Markdown 表格或外链图片，用[截图上传单](README.md#截图上传)的同一批 PNG 和开／关图注上传至站内 Images，不拿测试用青色方片作封面。

## ④ 文件上传（仅在另行发版时）

| 字段 | 填什么 |
|---|---|
| **Display Name / Game Version** | 按实际 JAR 名称与目标 MC 填写，不给单一 JAR 勾选四个游戏版本 |
| **Release Type** | 按该文件的实际发布状态，不把 CI 测试包冒充正式版 |
| **Modloader / Java Version** | Fabric；26.x 为 Java 25，1.21.11 为 Java 21 |

### Relations（依赖）—— 漏填会导致用户装不上

| 项目 | Relation Type |
|---|---|
| **Fabric API** | `Required Dependency` |
| **Forge Config API Port** | `Required Dependency` |

---

## ⑤ Changelog

**26.3 当前测试文件（不是线上 Release）**：[可复用英文说明](26.3-1.2.0-R1-notes.md) · [中文完整增量](../CHANGELOG_26_3_R1.md#120-r1)。今后确实上传文件时，先核对所发提交，不把 #124 内容贴到更早的同号 JAR。下方 §⑤ 各旧块仅为 26.2 历史。

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

## ⑤-bis 26.2 历史文件草稿（案例⑧ 正式修复 · 2026-08-12）

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

## ⑤-ter 26.2 历史文件草稿（R3 · 2026-09-02）

> 依据 `docs/archive/CHANGELOG_26_2_R2.md` 的 R3 段撰写，替换 ⑤-bis 的 R1 草稿。
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
- **Links**：[Source](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial) / [Issues](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)。不要在介绍中放外部下载链接。
- **Avatar**：**[[ 需自制 400×400 图，不可纯色，不可直接用 TACZ 官方图 ]]**
- **Rewards Program**：**必须关闭**
