# 1.21.11 对上游 1.2.0-hotfix 的逐项对齐记录

2026-11-26。本线 `1.2.0+fabric.1.21.11.R1`（MC 1.21.11 / Java 21 / Loom remap）。

- **上游**：`Sh1roCu/TACZ-Refabricated` 分支 `1.21.1`，比较点为 `356bdf9d48f2225bac6ceb56adc5576a418517a8`。上一次对齐的基线是 `d2903554`。
- **交接线**：`origin/26.3` `c5d536b7b`。

用户要求：26.3 和上游都要完全对齐，边缘功能也算在内；只搬一部分是最危险的状态。下面每一条差异都标明处理方式。**本文不把「已登记」写成「已修复」**；运行验收以文末 CI 回执为准。

## 判定口径

| 标签 | 含义 |
|---|---|
| PORTED | 本轮按上游语义改了代码 |
| COVERED | 本线已有等价实现（常见情形：26.x 原生替代、不同文件或不同 API）；列出依据 |
| ADAPT | 上游内容引用了 Forge/NeoForge 命名空间、1.21.1 专属 API 或 1.21.1 版本名，本线按 1.21.11/Fabric 改写 |
| ABSENT_FEATURE | 上游功能依赖本线不存在的库或构件；配置与语言键不发布，不声称可用 |
| KEEP_LOCAL / KNOWN_TEXT_DEVIATION | 上游文本与实际行为不符（包括上游自身）；保留本线准确文本 |
| FALLBACK_TO_EN | 同一错误描述在其他语言里不发布，回落到准确的 en_us 文本 |

## 工具

所有脚本只读取上游的单个提交，不引入上游分支。

| 脚本 | 作用 |
|---|---|
| `scripts/align_three_way.py [--summary]` | 上游 / 26.3 / HEAD 三方表面差异：API、命令、事件、序列化字段、Lua、配置、语言、标签、payload、注册表 |
| `scripts/align_common_tokens.py --only upstream\|both\|delta` | 对共有文件做标识符和字符串指纹，找出上游有、本线缺的 token。它读取已提交的 HEAD |
| `scripts/align_config_text.py` | 逐键比较配置注释、默认值和存在性；有未登记差异时退出 1 |
| `scripts/port_upstream_lang.py [--check]` | 由上游重新生成全部 21 个 TACZ 语言文件；`--check` 只校验 |

CI `build.yml` 的步骤「Upstream 1.2.0 text alignment」会运行后两个脚本。镜像文件 `docs/ci/build.yml` 与之逐字节相同。

## 表面对齐（三方）

| 表面 | 上游 | 26.3 | HEAD | 结论 |
|---|---|---|---|---|
| api / commands / events | 27 / 7 / 14 | 同左 | 同左 | 一致 |
| serialized / lua | 373 / 91 | 433 / 111 | 433 / 111 | HEAD ⊇ 上游 |
| config | 92 | 146 | 120 | 上游独有 3 键：2 个 VBO + Sable，均为 ABSENT_FEATURE |
| payloads | 45 | 49 | 50 | 覆盖 |
| tags | 374 | 218 | 217 | 上游独有的是 340 个 `c:` 约定标签，由 Fabric API 1.21.11 自带（convention-tags-v2 共 531 个）。本线引用的 155 个 `c:` 标签全部存在 |
| registry | 76 | 98 | 97 | `ModRecipe:gun_pegboard_dye` → COVERED（见下）；`CommonRegistry:simulated` → ABSENT_FEATURE |

## 配置

`align_config_text.py` 的结果：上游 93 键、本线 120 键，**0 条未登记差异**，本线独有 30 键。

**PORTED**
- `AmmoConfig.init()` 按上游顺序重写。
- PreLoad / Other / Sync 注释，以及 14 条 Render/Sync 注释改为上游原文。
- 下列逐条对照调用点，确认描述属实：
  - `GunHurtBobMultiplier`：`|sin(hurtDir)|` 是前后受击权重，侧面保持倍率，与上游 `GameRendererMixin` 相同。
  - `DisableThirdPersonOffhandAndHotbarGunRender`：`HumanoidOffhandRender`。
  - `EnableOcularInflate` / `OcularInflate`：在烘焙期生效。
  - `RenderCreativePackSidebar`：关闭时恢复全部物品。
  - `HitParticleMaxDistance`：0 表示不限距离。
  - `EnableExperimentalScopeLens`、`EnableScopeThermal`：`ScopeLensFrame.needsHeat()` 门控热源采集。
  - `RenderMuzzleSmoke`。
  - `overwrite_on/off` 的「下次启动」：只有 `GunPackLoader` 的 `firstLoad && !override` 才导出。

**ADAPT**：默认破坏方块为 `#c:glass_blocks` / `#c:glass_panes`（上游为 `#forge:glass*`）。旧配置由 `AmmoBlockConfigRead` 迁移。

**ABSENT_FEATURE**
- `EnableSableCompat`：没有 1.21.11 的 Sable 构件。ryanhcode/sable 的 `main` 为 2.0.7，只支持 `[1.21.1]`，也没有 1.21.11 分支。
- `EnableExperimentalDisplayGunVbo`、`EnableStaticItemGunVbo`：依赖 simplebedrockmodel 的 VBO 渲染器（`DisplayGunMeshGroup`、`ExternalGunItemVboRenderer`）。本线没有该库的 1.21.11 构件（`build.gradle` 注释），改用本地兼容接口加 collector 渲染。

**KNOWN_TEXT_DEVIATION**：`HitParticleEnable`。两条线都在弹孔贴花判断之前生成碎屑（上游 `HitBlockEffectHandler`，本线 `BulletHoleParticle.Provider`）。上游注释「when bullets leave bullet holes」不属实，本线保留准确描述。

## 语言

21 个语言文件全部由 `port_upstream_lang.py` 生成：
- 已有键的位置保持不变；上游新键按上游顺序追加；本线独有键保留。
- 若某语言文件的 bundle 副本已等于目标结果，就不复制到 `src/main/resources`。
- 此前有 18 个语言只来自 26.1.2 bundle，每个缺 161 个上游键。

残余 **117 条**，全部已登记：

| 类别 | 条数 | 内容 |
|---|---|---|
| ABSENT_FEATURE | 84 | VBO ×4 与 `sable_compat` ×2 键，在各语言中不发布 |
| ADAPT | 13 | 11 个语言的 `pass_through_blocks.desc`（`#forge:glass` → `#c:glass_blocks`）；en/zh 的 `converter.universal.skipped`（1.21.1 → 1.21.11，本线转换器写入 `compat_1_21_11`） |
| KEEP_LOCAL | 2 | en/zh 的 `hit_particle_enable.desc` |
| FALLBACK_TO_EN | 18 | 其余 18 个语言不发布 `hit_particle_enable.desc`，显示准确的英文，而不是错误描述的译文。没有经过核实的译文可用 |

en/zh 的中英键集仍然一致（CI「Lang key parity」）。

## Java 行为差异（上游 `d2903554..356bdf9` 改动过的共有文件）

### PORTED（本轮）

| 项 | 上游 | 本线改动 |
|---|---|---|
| 枪口曳光起点 | `FirstPersonRenderMath#muzzleOffset`：保留视图深度，横纵分量乘 `tan(world/2)/tan(item/2)` | `GunItemRendererWrapper.cacheMuzzlePosition` 改用同一公式，作用在本线已有的视图空间换算（去掉手部基座）之后。旧公式缩放深度：屏幕位置相同，但近端深度错误 |
| GUI 中忽略 LOD | `ScreenItemVboContext`：屏幕绘制期间使用深度计数 | `RenderDistance.isGuiRender()` = 深度计数 > 0 或 `ScreenRenderTracker.isRenderingScreen()`。删除旧的 100 ms 时间窗。见下文「RenderDistance 范围」 |
| 工作台制造按钮 | 无选中配方时 `active=false`，标签变灰 | `TaczImageButton` 同样处理，文字为 `0xFF888888` |
| 工作台材料悬停提示 | `renderIngredientTooltip`，附带所属创造栏 | 同样的格子与每秒轮换；用 1.21.11 的 `SlotDisplayContext` 解析，经 `setTooltipForNextFrame` 提交 |
| 缺失枪械显示的告警 | 每次重载、每个 id 只告警一次，并给出路径 | `ClientIndexManager.WARNED_MISSING_GUN_DISPLAYS`，在 `clear()` 时清空 |
| 暂停时的第三人称姿态 | 删除「暂停即 return」 | 暂停时仍计算原版持枪姿态。只有装了 PAL 时跳过（本线 PAL 仍在模型阶段更新，旧注释提到的 StackOverflow 来源于此） |
| 改装键 | 充能中（`isCharging`）不打开改装界面 | `RefitKey.onRefitPress` 加同样的守卫 |
| 战利品注入 | 任意 `RuntimeException` 都跳过该文件 | `LootInjectionManager` 同样处理，日志文本与上游一致 |
| 挂板洗色 | 炼药锅洗掉挂板染色 | 两种挂板都接入原版炼药锅洗色。gametest `RackChecks.pegboardCauldronWash` |
| 配件标签合并 | `LinkedHashSet` 保序去重 | `AttachmentsTagManager` |
| 约束旋转 | `Math.abs(Math.toDegrees(...))` | `ConstraintRotateListener` |

### COVERED（已有等价实现）

| 上游项 | 本线依据 |
|---|---|
| `GunPegboardDyeRecipe`（`gun_pegboard_dye`） | 两种挂板都在 `#minecraft:dyeable`，由原版染色配方处理：一块挂板加任意染料，两块挂板被拒绝。新增 gametest `RackChecks.pegboardDyeCraft`，通过服务端 RecipeManager 查找真实配方 |
| `ArmorStandMixin`（`getMinecartType()==RIDEABLE`） | 1.21.11 的盔甲架谓词改为调用 `AbstractMinecart#isRideable()`，本线 `AbstractMinecartMixin` 直接覆写它 |
| `RecipeManagerMixin`（`type` → `fabric:type`） | `GunSmithTableIngredient.normalizeCustomIngredient` |
| `GunSmithTableResult`：nbt 与 components 不能同时出现 | `GunSmithTableResultSerializer` 抛出 `JsonParseException` |
| `GunPackOverlayResolver` / `GunPackLoader` 的版本与依赖校验 | 本线重写版有相同的拒绝条件（目录名、重复、链接逃逸、依赖不匹配），只是日志措辞不同 |
| 第三人称枪口火光（`MuzzleFlashRender.onEntityShoot`）与粒子（`ThirdPersonMuzzleParticleManager`） | `NativeMuzzleEffects`：按射手记录 `SHOTS`，`thirdFlash` 寿命 150 ms，其他玩家可见。另有 `AnimationParticleDispatcher` 与 Snowstorm 运行时（与 26.3 的 `client/particle` 文件集合完全相同） |
| 第一人称粒子窗口（`MuzzleParticleManager`、`FirstPersonParticleSystem`） | 同上，native emitter 与 `muzzleWorldPose` |
| 陈列预览半透明（`ItemPreviewRenderContext`） | `RackPreviewCollector` / `IndigoRackPreviewCollector` |
| 展示柜锁提示（InteractKey `showHintForTarget`） | `DisplayCaseLockHint.show` 只在柜子锁住时调用；`tick` 注册于 `TaCZFabricClient:150`，`render` 注册于 `ClientSetupEvent:98` |
| `ModernKineticGunScriptAPI` 脚本守卫 | `GunScriptGuard`、`scheduleCycle`、`applyRecoilPush`、`GunFirePostEvent`、`ServerMessageGunActionPost` |
| ADS 路径、瞄具变体安装偏移、idle 瞄具高度 | `FirstPersonRenderGunEvent` 已有 `AdsPath`、`getScopeMountOffset`、`idleScopeHeightAdjust` |
| ADS 预设缺失回落、`modelFov` | `GunDisplayInstance` 已有 |
| 声音资源缓存清理 | `ReloadResourceEvent` 调用 `SoundPlayManager.clearSoundResourceCache` |
| `TargetRenderer` 在 `oRot`→`rot` 间插值 | 已有 |

### ADAPT / 不适用（平台路径不同，不是缺功能）

- `ClientIndexManager` 在 `finally` 里递增资源修订号：本线的 `revision` 用于异步烘焙过期判断。若在 `warmUpInventoryModels` 之后递增，会让刚启动的烘焙失效，所以没有照搬。
- `RenderHelper.initializeItemEntityStencil` / OptiFine 深度模板、`IrisCompat` 的 `FullyBufferedMultiBufferSource` / 手部 buffer、`ScopeLensRenderer.registerShader`：这些属于上游 1.21.1 的 GL 即时路径。本线使用 1.21.11 的 submit/collector 管线、镜片合成和本线 Iris 钩子（26.3 同样替代）。
- `ImmediatelyFastCompat` 的 HUD 批处理：本线物品走 GUI 图集加 collector，没有可以结束或重启的即时批次，是空门面。
- 上游 `IFunctionalRenderer` 的 `MultiBufferSource` 重载：本线扩展节点走 `IFunctionalSubmitter`。只实现旧回调的第三方节点会被跳过，不能直接沿用。
- 上游 `GunItemTransforms` 只是重构，本线保留原位实现。

### ABSENT_FEATURE（不发布、不声称）

- **Sable / Simulated** 联动（`compat/sable`、`compat/simulated`，`CommonRegistry:simulated`）：没有 1.21.11 构件。
- **SBM VBO**：展示柜与静态物品的 VBO、`ExternalGunItemVboRenderer`，以及 `RenderClothConfig` 的两项开关。
- **SBM 外部枪模型**（`ExternalGunModel`、`client/renderer/external`、`ExternalShellRenderer`）及外部 LOD：依赖 simplebedrockmodel 2.6.3 的外部模型烘焙。
- **EMI** 独立入口：本线只验证了 JEI/REI。
- **Accelerated Rendering**：`shouldAccelerate()` 返回 false，是**禁用**，不是「冲突已修复」。IF/AR 兼容类同样是禁用桩。

## RenderDistance 范围（决策）

上游 `ScreenItemVboContext.isActive()` 只覆盖屏幕绘制期间。1.21.11 的 PIP（`GunPreviewRenderer.renderToTexture`）在 `Screen#render` 之后才绘制，所以本线在 `renderToTexture` 里用 try/finally 包住 `beginScreenItemDraw()` / `endScreenItemDraw()`。

- 屏幕打开时，世界中渲染的枪仍然使用 LOD。
- 物品栏、快捷栏图标由 GuiRenderer 在 `Screen#render` 之外绘制，所以仍不绕过 LOD。这与旧行为相同，不是回归，也没有宣称已改变。

## 验证

本地已通过（不能替代编译）：
- `python3 -m unittest discover -s tests`：24 项。
- `align_config_text.py`：0 差异。
- `port_upstream_lang.py --check`：无过期。
- en/zh 键集一致；21 个语言 JSON 均可解析。
- 两份 `build.yml` 逐字节相同。

本机没有 JDK，也没有 1.21.11 jar。编译、`verify_mixin_targets.py`、gametest（`pegboardCauldronWash`、`pegboardDyeCraft`）和成品双端只以 CI 回执为准，见 `REMAINING_PORT_PLAN_12111.md` 的对应小节。
