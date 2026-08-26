# Official 0.4.3 follow-up sync — 26.2 Fabric (2026-08-26)

Source of truth: sister repo `q14433686-arch/TaCZ_Renovated`
branch `arena/01a03b03-tacz-renovated`（NeoForge 26.2 重制线）。
本仓库只取游戏语义，按本支（Fabric 26.2）的加载器环境落地，不抄 NeoForge API 表面。

## 已落地内容（本分支）

| 姐妹仓提交 | 语义 | 本仓库落地 |
| --- | --- | --- |
| `8431e68` Punchy 让出 | 持 TACZ/LR viewmodel 时走 Punchy blacklist 让出 | **早已存在**：`cn.sh1rocu.tacz.mixin.compat.punchy.*` 四个 mixin 已注册进 `tacz.fabric.mixins.json`，由 `MixinPlugin` 按 mod id 门控。本次仅补 `FirstPersonAnimationCompat` 的 Punchy 检测日志与类注释 |
| `ce0d245` 投掷物 idle 分流 | 站立时不再每 tick 给投掷物发 `INPUT_IDLE`（官方脚本用字面量 `idle` 取消拔销） | `LrTickAnimationEvent.tickAnimation(Minecraft)` 改为只驱动 `MeleeItemRenderer` |
| `c3acff7` 0.4.3 | 烟雾采环境光、cook=`prepare+lifeTime`、`display_offset`、`entity_transform`、消耗品 Bedrock/Lua 渲染 | 见下表逐项 |
| `305bed1` | `MeleeDisplay` record 补 `displayOffset` 组件 | 已落地 |
| `5f6b9e7`（保留部分） | 温雷满进度 `life` 被夹到 0 后永不爆 | `ThrowableItemEntity.tick` 改 `life >= 0 && tickCount >= life`；`0` 当帧炸，C4 `-1` 仍不超时 |

## 0.4.3 逐项对照

| Item | 官方 0.4.3 | 本移植（Fabric 26.2） |
| --- | --- | --- |
| Smoke light | Ambient + 6-neighbor fallback, floor 2 | 同款打包，用 `Level#getBrightness`（26.2 `LevelRenderer.getLightColor` 已迁走）；不再全亮 `0xF000F0` |
| Cook explode | `prepareTime + lifeTime` 在手上炸 | 同阈值；26.2 仍必须先 `stopUsingItem` 再 `onThrow`（见 `ThrowableItem.onUseTick` 注释） |
| `display_offset` | melee / throwable / consumable 的 `Vector3f` | 三类 display 均解析，transforms 之后、模型原点之前 `DisplayTransform.applyOffset` |
| `entity_transform` | `ItemTransform`，默认 Z90 + `(-0.3, 0.15, 0)` | `DisplayTransform.parseEntityTransform` 按 1.20.1 语义手写（26.2 `ItemTransform#apply` 会 recenter -0.5，不能用于飞行实体）；`ThrowableEntityRenderer` 有 display 时走该姿态，无内容包回退占位姿态 |
| Tooltip 自定义描述 | index `tooltip` 键，灰、宽 300、>3 行折叠 | `AbstractClientItemTooltip` 已按该契约实现，本轮未改 |
| Consumable 1P | `ConsumableItemRenderer` + display 通道 | 已补：`ConsumableDisplayInstance` / `ConsumableDisplayManager` / `ConsumableItemRenderer` / `ConsumableAnimationStateContext` + `items/consumable.json` 的 `has_custom_display` 分流 |

## 与本支环境的适配（相对姐妹仓的差异）

- **加载器桥**：消耗品 `getCustomRenderer` 走本仓库 Fabric 扩展接口
  `cn.sh1rocu.tacz.api.extension.IItem`（NeoForge 侧对应 `IClientItemExtensions#getCustomRenderer`），
  由 `TaCZFabricClient` 登记进 `BuiltinItemRendererRegistry`。
- **资源重载**：`ConsumableDisplayManager` 沿用本仓库 `JsonDataManager` 的 Fabric
  `IdentifiableResourceReloadListener`，覆写 `getFabricDependencies()` 复用
  `LrClientAssetsManager.taczAssetDependencies()`（保证在 TACZ 模型/动画/脚本之后加载）。
- **动画上下文**：`ConsumableAnimationStateContext extends BaseAnimationStateContext`，
  与 `ThrowableAnimationStateContext` 同一套重构（姐妹仓 0.4.3 写的是直接继承
  `ItemAnimationStateContext` 并重复定义 `currentItem`，本仓库已在投掷物上纠正过该写法）。
  Lua 侧可见方法名与官方 0.4.3 一致（`getCurrentItem/getStackCount/isUsing/getUsingTick`）。
- **mixin 注册**：Punchy mixin 本仓库不另建 `tacz.punchy.mixins.json`，仍归
  `tacz.fabric.mixins.json`（`required: true` + `MixinPlugin` 按 package 段门控）。
- **消耗品 JSON**：`items/consumable.json` 改用 `minecraft:condition` +
  `lrtactical:has_custom_display` 分流，`models/item/consumable_dynamic.json` 与
  `melee_dynamic.json` 同构（particle 用 `minecraft:item/potion`）。

## 调查但不接入（与姐妹仓一致）

- TACZ 第三人称枪口锁定：官方病根是 LR `AdjustmentYRotModifier`；本仓库 LR 没有该旋转层，
  PAL 已对 `is3rdFixedHand` 跳过手臂。**NO-GO**。
- 近战第三人称 player_animator：体积大、要内容包动画、可能与 TACZ PAL 抢层。**下一轮**。

## 验证状态

**未实机，不得标 PASS。** 本执行环境无 `java`/`JAVA_HOME`，未跑 `./gradlew build`，也未进行
单机/专服/内容包实测。改动均为源码级落地 + 26.2 命名/字节码静态核对。

完成标准前仍缺：
- JDK 25 环境下 `./gradlew compileJava` 与 `build` 通过；
- 有消耗品内容包时第一人称 Bedrock/Lua 渲染生效、`display_offset`/`entity_transform` 姿态正确；
- 无内容包时消耗品仍回退原版占位模型（`has_custom_display` 分流）；
- 温雷满进度 `0` 当帧炸、C4 `-1` 仍不超时；
- 投掷物静止不再反复拉栓抖动。

## 战略遗弃（与姐妹仓一致）

flash_shield、ARR art/models/sounds、standalone lrtactical mod。
