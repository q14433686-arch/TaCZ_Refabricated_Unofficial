# LR File 8652673（官方 1.20.1-0.4.3）评估 —— v2 修订版

> 对照源：`LesRaisins-Studios/LesRaisins-Tactical-Equipements` tag `0.4.3`
>（ CurseForge File ID **8652673**，2026-08-15，`lrtactical-1.20.1-0.4.3.jar`；
> 对应 commit `de97def`）。
>
> **2026-08-25 复核**：直接 `git clone` 官方仓库核对 —— `de97def`（0.4.3）
> 仍是主干最新 commit，其后无新提交；即 8652673 之后官方没有更新可跟。
> 本文同时修正 v1（PR #69 里的那版）的一处不实声明，见下表第 5 行。

## changelog 对照（六条，逐条核对官方 commit）

| # | 官方说明 | 对应 commit | 难度 | 本仓现状 | 本轮 |
|---|---|---|---|---|---|
| 1 | Added a batch of new consumables | `2100fff`「神秘ai2」等 | **高** | 消耗品数据/物品/tooltip 框架已在；官方新增消耗品带 **ARR 模型、贴图、动画、音效** | **不做资源打包**。框架已能加载第三方内容包自己的 consumable index/display。把官方美术打进本仓不合法。 |
| 2 | Fixed muzzle direction locked in TACZ third-person holding animation | （官方修在其 player_animator 兼容层） | **高** | 本仓第一版就整体跳过了 `compat/player_animator/` | **不做**。要跟就要单独开 PAL/player_animator 议题，不能把 1.20.1 Forge mixin 直接贴到 26.2。 |
| 3 | Improved tooltips: display some data + configurable custom descriptions | `aff16ba` / `3f54ec5` | **低** | 26.2 extracted-GUI 的 expandable tooltip 已在（描述折叠、数值行、Shift 展开） | **已有，不重复做**。 |
| 4 | Additional transforms for grenade entities + fix wrong orientations in some addons | `aef1194`「投掷物实体可设置额外偏移」 | **中** | 本仓渲染器原写死两段 translate，无 `entity_transform` | **已做**（v1 落地，v2 复核通过，见下）。 |
| 5 | Smoke particles now respond to ambient lighting | `f5430a6`「烟雾粒子亮度与环境匹配」 | **低** | —— | **已做**。⚠️ v1（PR #69）正文声称「已做：改为采环境光，下限钳 2，邻格兜底」，**代码实际未改**（`getLightCoords` 仍返回满亮 15728880，只加了 import 与注释）。本轮真正实现：`BlockPos.containing` 处块光/天光采样 + `LightCoordsUtil.pack`，语义与官方逐行对齐（官方无钳制、无邻格兜底，本版也不加）。 |
| 6 | Cookable throwables explode after fuse expires in hand | （更早已有） | **低** | 本仓 `ThrowableItem#onUseTick` 已实现，且比官方谨慎（先 `stopUsingItem` 再投，避免 `releaseUsingItem` 递归） | **已有，保持本仓写法**。 |

## 本轮落地的 0.4.3 行为（与官方 commit 逐行核对）

1. **`entity_transform`**（官方 `aef1194`）
   - display JSON 新字段 `entity_transform`（rotation/translation/scale，
     translation ×1/16 与官方 ItemTransform 反序列化一致）；
   - 缺省 = 官方 `DEFAULT_ENTITY_TRANSFORM`：绕 Z 90° + 平移 `(-0.3, 0.15, 0)`；
   - 不用 26.2 `ItemTransform#apply`（它自带 `-0.5` 物品槽回中，飞行实体不需要）
     —— 自建 `EntityExtraTransform`，语义照官方 `apply(false, poseStack)`；
   - `ThrowableEntityRenderer` 删掉写死的 `translate(0, 0.15, 0)` /
     `translate(0, 0.35, -0.15)`，改为「粘附面偏移 → 飞行朝向 → entity_transform」。
2. **粘性雷贴面方向**（官方 `aef1194` 的 StickyGrenadeEntity 部分）
   - 同步数据 `STUCK_FACE`（byte，`Direction#get3DDataValue` 编码，-1 = 未贴方块）；
   - `stickToBlock` 写入方向、贴实体写 -1、`detach` 清 -1，与官方一致；
   - 渲染按 `(-stepX·0.15, (1-stepY)·0.15, -stepZ·0.15)` 平移，与官方一致。
   - 26.2 适配：本仓实体渲染是 extract/submit 两段式，`stuckFace` 经 render
     state 传递（官方 1.20.1 是即时 render，结构不同、数值相同）。
3. **烟雾环境光**（官方 `f5430a6`）—— 本轮真正实现，见上表第 5 行。
   - 26.2 适配：`getLightColor(float)` 在 26.2 粒子基类上是
     `getLightCoords(float)`（本仓字节码核对过）；`LightTexture.pack` →
     `LightCoordsUtil.pack`。注意 26.2 的 pack 是 `block<<4 | sky<<20`。
   - 官方同时把渲染类型改成 PARTICLE_SHEET_TRANSLUCENT；26.2 粒子系统重组后
     本仓对应物 `Layer.TRANSLUCENT` 本来就在，无需再改。

## 明确不做、以及为什么

- **官方内置消耗品资源**：ARR。本仓定位是「代码框架 + 内容包自带资源」。
- **flash_shield**：盾牌/禁用/网络/渲染整条链是独立大项，不塞进这次。
- **第三人称枪口锁**：属于 TACZ × player_animator，26.2 PAL 路径与 1.20.1 不同。
- **官方 `cms uv 修正`（`5e746cb`）、配方平衡（`10455fc`）**：纯内容/资源侧，
  对本仓代码框架无对应物。
- **player_animator / JEI / Cloth 整包**：第一版就跳过，0.4.3 没改变这个判断。

## 难度总评

「把 8652673 整包搬到 26.2」不现实：资源 ARR、盾牌未移植、第三人称动画在
另一套 API 上。跟进行为层（手雷朝向、贴面方向、烟雾光照、预燃在手上炸）
是中低难度，本轮能做的已全部做完；其中烟雾光照是补 v1 的欠账。
消耗品「一批新物品」对玩家可见的部分几乎全是美术，代码框架早就接得上
内容包。官方在 0.4.3 之后（截至 2026-08-25）没有新提交，短期没有下一波
可跟。
