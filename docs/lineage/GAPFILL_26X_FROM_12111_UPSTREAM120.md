# 26.X 线遗漏补缺清单（来源：1.21.11 对上游 1.2.0-hotfix 的全量对齐）

2026-11-26。本文是**给 26.X 各线的施工单**，不是完成报告。下面每一条都是用 `git show origin/<分支>:<路径>` 逐一核对后确认的缺口；核对时的分支头：

| 分支 | 头 | `mod_version` |
|---|---|---|
| `26.1.2` | `d12a3f229` | `1.2.0+fabric.26.1.2.R1` |
| `26.2(main)` | `307f063a8` | `1.2.0+fabric.26.2.R1` |
| `26.3` | `c5d536b7b` | `1.2.0+fabric.26.3.R1` |
| `26.4` | `b5c9fe5cd` | `1.1.8+fabric.26.3.R1-hotfix`（见 §0） |

- **上游基准**：`Sh1roCu/TACZ-Refabricated` 的 `1.21.1` 分支，比较点 `356bdf9d48f2225bac6ceb56adc5576a418517a8`（1.2.0-hotfix）。
- **1.21.11 的实现与回执**：`f3894a03`（实现）、`593bb9cd`（探针），记录在 [UPSTREAM_120_ALIGNMENT_12111.md](UPSTREAM_120_ALIGNMENT_12111.md)。

## 施工规则（来自 AGENTS.md 与本轮教训）

1. **不得直接复制 1.21.11 的文件**。1.21.11 是 Java 21 加 Loom remap，26.X 是各自的原生环境，只能照语义改写。注释里的版本名、分支名要逐句改成目标线。
2. 每一项都要走完「代码 → 目标线编译与单测 → 专项成品运行 → 回执」，才能标 DONE。不能用「同一提交另一个作业通过了」代替。
3. 改 `gradle.properties` 版本或依赖时，必须在同一个变更集里同步 README 的六处，并运行 `bash scripts/check_release_consistency.sh --strict`。本清单的项都不需要动版本号。
4. 「禁用」不等于「修复」；没有构件的功能不能写成已支持（见 §5）。

## 0. 26.4：先补 1.2 基线，本清单暂不适用

`origin/26.4` 停在 2026-10-12（`b5c9fe5cd`）：
- `mod_version` 仍是 1.1.8，并且 `gradle.properties` 里写着 `minecraft_version=26.3`。
- 1.2 的标志性文件 `GunPackOverlayResolver`、`AmmoStorageBlock`、`NativeMuzzleEffects`、`DisplayCaseLockHint` **一个都没有**。

26.4 的缺口是整个 1.2 回移，而不是下面这些边缘项。建议以 `26.3`（最接近的 1.2 线）为语义来源，先完成 1.2 回移和版本号/README 同步，再回到本清单。在此之前，不要把下面的条目零散打进 26.4。

## 1. 代码缺口（26.1.2 / 26.2(main) / 26.3 三线相同）

核对方式：在三线的同一路径查找上游语义的标志符，三线结果完全一致。下表的路径均省略前缀 `src/main/java/com/tacz/guns/`。

| # | 项 | 上游 `356bdf9` 语义 | 26.X 现状 | 1.21.11 参考 | 改写要点 / 测试影响 |
|---|---|---|---|---|---|
| C1 | 枪口曳光起点的 FOV 补偿 | `FirstPersonRenderMath#muzzleOffset`：保留视图深度，x/y 乘 `tan(world/2)/tan(item/2)` | `client/renderer/item/GunItemRendererWrapper.java` 的 `cacheMuzzlePosition` 仍是 `viewZ * fovScale`（缩放深度） | `f3894a03`：`muzzleRenderOffset.set(viewX*lateral, viewY*lateral, viewZ)` | 在已有的「去掉手部基座、转到视图空间」之后替换最后一步；`itemTan ≤ 1e-6` 时比例取 1。**26.2(main) 与 26.3 必须同时改 `src/gametest/java/com/tacz/guns/test/ViewTuningSmoke.java:138-140`**（26.1.2 没有这个测试；那里要新增等价的手部枪口断言，否则 C1 没有回执）：它按旧语义断言 `delta.x≈.237, delta.y≈-.113, delta.z<-.08`。1.21.11 实测 delta = (0.2874, −0.1370, −0.330)，横纵 ×1.2127，深度不变，旧断言因此失败。改成从 `CameraSetupEvent.ITEM_MODEL_FOV_DYNAMICS / WORLD_FOV_DYNAMICS` 算出比例 k，断言横纵 = XYZ×k、深度 = XYZ.z（参考 1.21.11 `PresentationProbe.verifyHandPreview`）。`TracerFrameSmoke`、`WorldTracerSmoke` 只是读写这个缓存值，要重跑确认 |
| C2 | GUI 中忽略 LOD 的范围 | `ScreenItemVboContext`：只在屏幕绘制期间生效（深度计数） | `util/RenderDistance.java` 仍用 `GUI_RENDER_TIMESTAMP` 的 100 ms 时间窗；`GunSmithTableScreen` 调用 `markGuiRenderTimestamp()` | `f3894a03`：`beginScreenItemDraw()/endScreenItemDraw()` 深度计数；`isGuiRender()` = 计数 > 0 或 `ScreenRenderTracker.isRenderingScreen()`；`GunPreviewRenderer.renderToTexture` 用 try/finally 包住 | 三线都已有 `GunPreviewRenderer`（PIP）和 `ScreenRenderTracker`，可以按同样方式改。PIP 在 `Screen#render` 之后才画，所以必须在 `renderToTexture` 里开启作用域。删掉时间窗后，界面打开期间世界里的枪会恢复使用 LOD。物品栏图标仍不绕过 LOD，与旧行为相同 |
| C3 | 工作台制造按钮 | 无选中配方时 `active=false`，悬停偏移为 0，文字变灰 `0x888888` | `addCraftButton` 无此逻辑 | `f3894a03`（`TaczImageButton`，ARGB 写作 `0xFF888888`） | 26.X 的颜色参数需要带 alpha |
| C4 | 工作台材料悬停提示 | `renderIngredientTooltip` / `getIngredientTooltip`，把所属创造栏插在第 2 行 | 没有 | `f3894a03`：与 `renderIngredient` 同一个格子和每秒轮换，用 `SlotDisplayContext.fromLevel` 解析，经 `setTooltipForNextFrame` 提交 | 只在 `selectedRecipeList` 非空时调用；材料为 null 时跳过 |
| C5 | 缺失枪械显示的告警 | 每次重载、每个 id 只告警一次，并给出路径 `assets/<ns>/display/guns/<path>.json` | `client/resource/ClientIndexManager.java` 每次查询都打印 `"{} display instance is missing from cache"` | `f3894a03`：`WARNED_MISSING_GUN_DISPLAYS`（并发 Set），在 `clear()` 时清空 | **不要**照搬上游在 `reload()` 的 finally 里递增修订号：本线的 `revision` 用于异步烘焙过期判断，在预热之后递增会让刚启动的烘焙全部作废 |
| C6 | 暂停时的第三人称姿态 | 删除 `isPaused()` 时的直接 return | `client/animation/third/InnerThirdPersonManager.java` 仍是暂停即 return | `f3894a03`：暂停时只在 `PlayerAnimatorCompat.isInstalled()` 时 return，否则照常计算原版持枪姿态 | 上游把 PAL 播放移出了模型阶段，26.X 的 PAL 仍在这里 `updatePlayerLayers`，所以暂停时要保留 PAL 那一支的跳过（旧注释提到的 StackOverflow 来源于此）。改完要在装/不装 PAL 两种情况下各暂停一次验证 |
| C7 | 改装键与充能 | 充能中不打开改装界面 | `client/input/RefitKey.java` 无 `isCharging` 判断 | `f3894a03`：`IClientPlayerGunOperator.fromLocalPlayer(player).isCharging()` 时 return | 关闭界面的分支不变 |
| C8 | 战利品注入的容错 | 任意 `RuntimeException` 都跳过该文件，日志为 `Failed to load loot injection {}; skipping` | `resource/manager/LootInjectionManager.java` 只捕获 `JsonParseException` | `f3894a03` | 删掉无用的 import |
| C9 | 配件标签合并 | `LinkedHashSet` 保序去重 | 已有 | — | 不是缺口（三线都已有 `LinkedHashSet`） |
| C10 | 约束旋转 | `Math.abs(Math.toDegrees(...))` | 已有 | — | 不是缺口 |

C9、C10 是在核对中排除的，保留在表里，以免后人重复排查。真正需要施工的是 **C1–C8 共 8 项**。

## 2. 配置注释（三线相同：24 条 COMMENT 差异，没有默认值或缺键差异）

把 1.21.11 的 `scripts/align_config_text.py` 放到三线的工作树里运行，结果都是「上游 93 键，24 条差异」（26.1.2 为本地 120 键，另外两线为 146 键）。除了已登记的 Sable/VBO 和 `HitParticleEnable` 外，差异如下：

- **ammo（7 条）**：`BulletDamageIgnoreList`、`DestroyBlockHardnessThreshold`、`DestroyBlocks`、`DestroyGlass`、`EnableBulletDamageIgnoreList`、`IgnoreTeamFriendlyFire`、`PassThroughBlocks`
- **gun_pegboard（2 条）**：`GunPegboardBlacklistItems`、`GunPegboardWhitelistItems`
- **pre/other（3 条）**：`gunpack.DefaultPackDebug`、`other.DefaultPackDebug`、`other.HideGunPackCompatibilityWarning`
- **misc（1 条）**：`BulletHitBlockEffectSendDistance`
- **render（11 条）**：`CrosshairType`、`DisableThirdPersonOffhandAndHotbarGunRender`、`EnableExperimentalScopeLens`、`EnableOcularInflate`、`EnableScopeThermal`、`GunHurtBobMultiplier`、`HitExtraParticleEnable`、`HitParticleMaxDistance`、`OcularInflate`、`RenderCreativePackSidebar`、`RenderMuzzleSmoke`

**要逐条核对行为，不能整批替换。** 26.X 的部分措辞带有上游没有的事实，例如 `BulletHitBlockEffectSendDistance` 的 “shooter is always included”。先在目标线代码里确认这句话成立，再决定：
- 采用上游原文；
- 或者保留本线措辞，并把它登记到脚本的 `KNOWN_TEXT_DEVIATION`。

1.21.11 逐条核对时用过的调用点可以参考：
- `GunHurtBobMultiplier`：`|sin(hurtDir)|` 是前后受击权重。
- `EnableScopeThermal`：由 `ScopeLensFrame.needsHeat()` 门控。

`AmmoConfig.init()` 的定义顺序（会决定生成的 TOML 里的顺序）在 1.21.11 已经按上游重排，属于可选项。

## 3. 语言文件

| 项 | 26.1.2 / 26.2(main) | 26.3 |
|---|---|---|
| 上游键缺失（不计 VBO/Sable 键） | 0 | 0 |
| en_us 与上游值不同 | 12 条：`commands.tacz.reload.{success,backup,overwrite_on,overwrite_off,overwrite_command.on,overwrite_command.off}`、`destroy_glass.desc`、`pass_through_blocks.desc`、`other.default_pack_debug(.desc)`、`jei…recipe_unavailable`、`converter.universal.skipped` | 4 条，均为有意差异：`cloth_config_warning.tips`、`crosshair_type.desc`（指向 26.X 的「Esc → TaCZ: View settings」），`recipe_unavailable` 的措辞，`converter.universal.skipped`（写 26.3，ADAPT） |
| 其他语言与上游值不同 | es 24、id 36、th 15、vi 16、ko 7，pl/pt_br/ru/tr/uk/zh_tw 各 10 | 只有 zh_tw 2 条（同上，有意差异） |

**L1（26.1.2 / 26.2(main)）**：把 en_us 的 12 条和各语言对应的条目改为上游值，规则如下。
- `pass_through_blocks.desc` 里的 `#forge:glass` 是 Forge 命名空间，需要先确认目标线的默认值用的是哪个标签，再写成对应的 `#c:` 标签（ADAPT）。
- `converter.universal.skipped` 里的版本号写成目标线（ADAPT）。
- **取舍要先定下来**：上游自己的部分语言（1.21.11 实测 id/pl/pt_br/th/tr/uk/vi）在这 9 个键上是**英文**，因为上游改了含义（overwrite → 自动更新），还没有翻译。26.1.2 现有的是**旧含义的译文**。
  - 1.21.11 选择与上游逐字节一致，共 63 处显示英文，以免显示与行为不符的旧说明。
  - 如果某条线想保留译文，就必须确认译文描述的是新行为；否则一样是错误信息。

**L2（三线都有）**：`config.tacz.client.render.hit_particle_enable.desc` 现在是上游原文 “…when bullets leave bullet holes”，但三线的 `BulletHoleParticle.Provider` 都是**先调用** `HitSurfaceParticleSpawner.spawn`，再判断贴花（26.3 第 274 行，26.1.2 第 273 行），所以这句话不属实（上游自身也不属实）。处理方式：
- en/zh 改写成准确描述。1.21.11 的写法是「子弹命中时生成基础方块碎屑，独立于枪械/弹药是否允许弹孔贴花」。
- 其他语言删除这个键，让它回落到准确的 en_us，而不是保留错误说明的译文。

`tr-TR.json` 不需要处理：上游与各线都带着这个文件，但 Minecraft 只加载小写的语言代码（`tr_tr`），它在所有线上都不会生效。

## 4. 工具与 CI（三线都没有）

可以照搬思路，但要逐线适配：

| 工具 | 适配要点 |
|---|---|
| `scripts/align_config_text.py` | 配置类名和路径与 1.21.11 相同，可以直接运行（本次核对就是这样跑出 24 条的）。需要把 `KNOWN_ABSENT`、`KNOWN_TEXT_DEVIATION` 换成各线自己的登记 |
| `scripts/port_upstream_lang.py` | `effective()` 默认读取 `resources/tacz-26.1.2-source-and-resource-bundle.jar`，**26.2(main) 和 26.3 没有这个 jar**，必须改成只读源码语言文件。`ADAPT_KEYS` 里的版本号要换成目标线。按 §3 的结论配置 `KEEP_LOCAL` 和 `FALLBACK_TO_EN` |
| `scripts/align_three_way.py`、`align_common_tokens.py` | 只读诊断工具。common-tokens 的结果大量来自 API 名称漂移，需要人工判读 |
| CI 步骤 | 1.21.11 在 `build.yml` 加了「Upstream 1.2.0 text alignment」：用 `git fetch --depth=1` 按 SHA 抓取上游单个提交，然后运行前两个脚本。有 `docs/ci/build.yml` 镜像的线要保持两份逐字节相同 |

## 5. 核对后确认「不是缺口」的项（避免重复劳动）

三线都已具备：
- 工作台请求选中配方（`applyRequestedRecipeSelection`）和「按手持筛选为空」提示；
- `DisplayCaseLockHint` 的 tick/HUD 注册；
- `GunScriptGuard` / `scheduleCycle`；
- `AdsPath`；
- ADS 预设缺失时的回落；
- 配方结果的 nbt 与 components 互斥校验；
- `NativeMuzzleEffects` 的第三人称火光；
- 远端动画粒子网络注册；
- 挂板染色（三线是数据驱动的 `data/tacz/recipe/gun_pegboard_dye.json`；1.21.11 是 `#minecraft:dyeable` 加原版染色）与炼药锅洗色（`cauldron_can_remove_dye` 标签）；
- C9、C10。

和 1.21.11 一样**没有构件、不能声称支持**的：
- Sable / Simulated：Sable 只有 `[1.21.1]`。
- simplebedrockmodel 的 VBO 与外部枪模型。
- EMI 独立入口。
- AR、IF 兼容是**禁用桩**。

## 6. 建议顺序与验收

1. **26.3**：离上游最近，语言只差 L2。按 C1–C8 → §2 → L2 → §4 的顺序推进。C1 必须连同 `ViewTuningSmoke` 的断言修改一起提交；C2 要跑 PIP 工作台预览和世界 LOD 的回归；C6 要做 PAL 装/不装两组验证。
2. **26.2(main)**：同上，再加 L1。这条线带有 `check_release_consistency.sh`，合并前要跑 `--strict`。
3. **26.1.2**：同 26.2(main)，但 C1 需要新增枪口断言（这条线没有 `ViewTuningSmoke`）。另外它有 26.1.2 bundle，语言脚本可以保留 bundle 读取逻辑。
4. **26.4**：先完成 §0。

每一项完成后，在该线的执行账本里记下：提交 SHA、编译/单测/成品作业编号，以及没有覆盖到的部分。间歇性的 `PORT_WORLD_STALL` 类失败要单独登记，不能用「重跑通过」把它记成已修复。
