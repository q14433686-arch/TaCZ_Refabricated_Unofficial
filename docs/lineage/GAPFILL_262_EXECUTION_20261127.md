# 26.2(main) 应用《26.X 线遗漏补缺清单》执行记录（2026-11-27）

来源施工单：[`GAPFILL_26X_FROM_12111_UPSTREAM120.md`](GAPFILL_26X_FROM_12111_UPSTREAM120.md)
（1.21.11 线 `6262446e`，对上游 `356bdf9d` 1.2.0-hotfix 的全量对齐后开出的施工单）。
本文是 26.2(main) 线对该清单的**执行回执**；按施工单规则 2，每一项的
「编译/单测/成品回执」以本分支 CI（`build.yml`，push 到 `arena/**` 自动触发）为准，
沙箱无 JDK 不能编译，静态核对已完成的项目在下文逐条标注「实机/CI 待跑」。

- 工作分支：`arena/485f3381-tacz-refabricated-unofficial`（基于 `26.2(main)@307f063a8`）。
- 上游基准：`Sh1roCu/TACZ-Refabricated` `1.21.1` @ `356bdf9d48f2225bac6ceb56adc5576a418517a8`（`git fetch --depth=1` 按 SHA 抓取，未引入上游分支）。
- 1.21.11 参考实现：`f3894a03`（实现）、`593bb9cd`（探针语义改版）、`6262446e`（施工单）。

## C1–C8（全部落码；注释里的版本/分支名已改写为本线）

| # | 项 | 26.2 落点 | 语义核对（本线代码逐条验证过的调用点） |
|---|---|---|---|
| C1 | 枪口曳光起点 FOV 补偿 | `GunItemRendererWrapper.cacheMuzzlePosition`：视图空间换算（Bᵀ 去手部基座，第 26 轮机制）之后，最后一步从「深度 ×tan(item/2)/tan(level/2)」换成「横纵 ×tan(world/2)/tan(item/2)、深度保留」；`itemTan ≤ 1e-6` 时比例取 1。调试行 `[TACZ MuzzleSpace]` 的 `fovScale=` 键同步改名 `lateral=` | 与 1.21.11 `f3894a03` 同一公式（上游 `FirstPersonRenderMath#muzzleOffset`）。**探针同步改**：`ViewTuningSmoke` 断言从硬编码 `(0.237, −0.113, z<−0.08)` 改为按 `CameraSetupEvent.ITEM_MODEL_FOV_DYNAMICS / WORLD_FOV_DYNAMICS` 现算 k、断言 `(x·k, y·k, z)`（1.21.11 `PresentationProbe.verifyHandPreview` 同型）。该探针在 CI 客户端 smoke（`PackClientSmoke` 主流程）里真实执行 ⇒ CI 绿 = C1 回执 |
| C2 | GUI 中忽略 LOD 的范围 | `RenderDistance`：删除 100 ms 时间窗，改「深度计数 `screenItemDrawDepth` + `ScreenRenderTracker.isExtractingScreen()`」；新增 `beginScreenItemDraw()/endScreenItemDraw()`；`GunPreviewRenderer.renderToTexture` 用 try/finally 包住 submit（26.2 的预览 submit 发生在 extract 之后的 PIP 绘制里，所以作用域开在那里）；`GunSmithTableScreen.renderLeftModel` 的 `markGuiRenderTimestamp()` 删除。`TaczPolyMeshGunModel` / `PolyMeshGpuRenderer` 相关注释同步（闸门逻辑本身不动） | 26.2 差异说明：本线 `ScreenRenderTracker` 的事件是 extract 窗口（`beforeExtract/afterExtract`），方法名 `isExtractingScreen()`（1.21.11 为 `isRenderingScreen()`）。世界提取与 Screen extract 分时，故菜单开着时世界枪恢复 LOD；预览 PIP 在 extract 窗口外，由深度计数覆盖 |
| C3 | 工作台制造按钮 | `GunSmithTableScreen.addCraftButton`：无选中配方时 `active=false`、`yDiffTex` 取 0（悬停/禁用都落在普通贴图上）、`drawModCenteredString` 文字 `0xFF888888`（本线 ARGB 带 alpha） | 上游 1.2.0 / 1.21.11 `f3894a03` 同语义 |
| C4 | 工作台材料悬停提示 | `renderIngredientTooltip`（与 `renderIngredient` 同格子 `leftPos+254+45j / topPos+62+17i`、每秒轮换、`SlotDisplayContext.fromLevel` 解析、null 材料跳过）+ `getIngredientTooltip`（创造栏名插第 2 行，蓝色）；挂到 `extractRenderState` 的 ResultButton 提示之后，仅 `selectedRecipeList` 非空时调用；经 `setTooltipForNextFrame(font, list, Optional.empty(), x, y)` 提交（26.2 同名 API，FlatColorButton 同款用法） | 上游用 `renderComponentTooltip`（1.21.1），1.21.11 用 `setTooltipForNextFrame`；26.2 是 extract 两段式，`setTooltipForNextFrame` 正确 |
| C5 | 缺失枪械显示告警 | `ClientIndexManager`：`WARNED_MISSING_GUN_DISPLAYS`（并发 Set），`clear()` 清空；日志 `Gun display {} is unavailable (assets/{}/display/guns/{}.json);` 与上游逐字一致 | **未照搬**上游 `reload()` finally 递增修订号——本线 `revision` 是异步烘焙过期判断（施工单 C5 明确禁止）；1.21.11 同样未搬 |
| C6 | 暂停时的第三人称姿态 | `InnerThirdPersonManager`：暂停时仅 `PlayerAnimatorCompat.isInstalled()` 才 return；未暂停分支不变（PAL 在模型阶段 `updatePlayerLayers` 的跳过保留） | 旧注释 StackOverflow 来源 = PAL 在模型阶段播放；本线 PAL 仍在此处，故保留 PAL 支路跳过（1.21.11 同型）。**实机待跑**：装/不装 PAL 各暂停一次（清单 §6） |
| C7 | 改装键与充能 | `RefitKey.onRefitPress`：`IClientPlayerGunOperator.fromLocalPlayer(player).isCharging()` 时 return（关闭界面的分支不动） | 上游 1.2.0 / 1.21.11 同语义 |
| C8 | 战利品注入容错 | `LootInjectionManager`：`catch (RuntimeException e)`，日志 `Failed to load loot injection {}; skipping`；删除不再使用的 `JsonParseException` import | 上游 1.2.0 / 1.21.11 同语义 |
| C9/C10 | 配件标签合并 / 约束旋转 | 无改动（核对确认本线已有：`AttachmentsTagManager` `LinkedHashSet` 保序去重、`ConstraintRotateListener` `Math.abs(Math.toDegrees(...))`） | 施工单 §1 已标注「不是缺口」，此处复核留档 |

## §2 配置注释（24 条 → 上游原文；每条先在本线代码核对行为）

`scripts/align_config_text.py`（从 1.21.11 移植，`KNOWN_ABSENT`/`KNOWN_TEXT_DEVIATION` 沿用）
结果：**上游 93 键、本地 146 键、差异 0**（3 条 ABSENT-RECORDED：Sable/VBO 无构件；
1 条 DEVIATION-RECORDED：`HitParticleEnable`，见 §3 L2；56 条 LOCAL-ONLY 为 26.2 原生键，不计数）。

逐条核对的调用点（全部在本线代码内验证）：

- **ammo（7）**：`DestroyGlass`/`DestroyBlocks`/`DestroyBlockHardnessThreshold` = `DestroyGlassBlock`（无掉落销毁、hardness<0 或超阈值拒毁）；`PassThroughBlocks`/`DestroyBlocks` 的 `#` 标签 = `AmmoBlockConfigRead`（默认 `#c:glass_blocks/#c:glass_panes`，旧默认 `#forge:glass*` 迁移）；`IgnoreTeamFriendlyFire` = `EntityUtil.getHitResult`（队友不算命中、不耗穿透）；`EnableBulletDamageIgnoreList`/`BulletDamageIgnoreList` = `EntityUtil`/`EntityKineticBullet`（409/441/759）/`ProjectileExplosion`（命中、伤害、爆炸全生效）
- **gun_pegboard（2）**：`GunPegboardItemConfigRead`——内建允许集 = 枪/配件/弹药/弹药盒（`IGun||IAttachment||IAmmo||IAmmoBox`），黑名单先于白名单与内建集判定
- **pre/other（3）**：`gunpack.DefaultPackDebug` = `GunPackLoader.prepareDirectory`（`firstLoad && !override` 才导出）+ `GetJarResources.exportFolderIfChanged`（mod 版本+内置资源指纹，路径 `<game dir>/tacz/tacz_default_gun`）；`other.DefaultPackDebug` = `ReloadCommand` 唯一消费点已注释 ⇒ 「Deprecated and ignored」属实；`HideGunPackCompatibilityWarning` = `PackCompatibilityNotice`（拦截「可能需要转换的枪包」聊天提示）
- **misc（1）**：`BulletHitBlockEffectSendDistance` = `ServerMessageBulletHitBlock.shouldSend`（`range<=0` 即同 level 不限；"shooter is always included" 属实但上游文本不写，1.21.11 先例采纳上游原文，留档于此）
- **render（11）**：`DisableThirdPersonOffhandAndHotbarGunRender` = `HumanoidOffhandRender.renderGun`（主手路径不经此）；`EnableOcularInflate`/`OcularInflate` = `BedrockAttachmentModel.getExtraCubeInflate`（基类恒 0，仅配件目镜盒、烘焙期生效、改后需重载资源）；`EnableExperimentalScopeLens`/`EnableScopeThermal` = `ScopeLensFrame.beginFrame/needsHeat`（热源采集由 needsHeat 门控，禁用时回落普通光学镜片）；`GunHurtBobMultiplier` = `GunHurtBobTweak.heldGunTilt`（`1−0.5·|sin(hurtDir)|` 即前后减半、斜向平滑；枪伤专用晃动走 `onHurtBobTweak` 另一条路径）；`HitExtraParticleEnable` = `HitSurfaceParticleSpawner.spawn`（先碎屑后 display 粒子，禁用保留基础碎屑）；`HitParticleMaxDistance` = 同文件 `isCloseEnough`（≤0 不限；上游原文本对「extra」的措辞与其自身实现相同，1.21.11 已核对并采纳）；`RenderCreativePackSidebar` = `CreativePackFilters.updateTab`（关闭时 `showItems(tab.getDisplayItems())`）；`RenderMuzzleSmoke` = `NativeMuzzleEffects`（Snowstorm/renew 烟）；`CrosshairType` = `GunCrosshair.image`（经 `TuningValues.visible()` 读；本线与 1.21.11 同为账号 profile 架构，1.21.11 采纳上游原文，本线随行）
- **AmmoConfig.init() 定义顺序**（施工单标注「可选」）已按上游重排（GlobalBulletSpeedModifier 移末、Destroy 组随 DestroyGlass、忽略名单组随 IgniteEntity）；Sable 键不发布（无构件）。26.2 特有键不受影响

## §3 语言文件（L1 + L2，21 个语言全部由 `scripts/port_upstream_lang.py` 重建）

`scripts/port_upstream_lang.py`（从 1.21.11 移植；**本线无 bundle jar，`effective()` 只读源码语言文件**）。

- **L1**：en_us 12 条 → 上游值（ADAPT：`pass_through_blocks.desc` `#forge:glass`→`#c:glass_blocks`，与本线默认 `#c:glass_*` 一致；`converter.universal.skipped` 1.21.1→**26.2**，本线 `UniversalPackConverter.OVERLAY=compat_26_2`，改写结果与本地既有值相同）。其余语言取上游值：1.21.11 定下的「与上游逐字节一致」取舍随行（上游 id/pl/pt_br/th/tr/uk/vi 在 9 个键上是英文 ⇒ 显示英文，共 63 处；ko 等上游自带的旧译文也逐字节随上游——1.21.11 同状态，见下）
- **L2**：`config.tacz.client.render.hit_particle_enable.desc`——en/zh 改写为准确描述（1.21.11 已验证的文本：「Spawn basic block fragments on bullet impact, independently of whether the gun/ammo allows bullet-hole decals.」/「子弹命中时生成基础方块碎屑，独立于枪械/弹药是否允许弹孔贴花。」）；其余 18 语言删除该键回落 en_us（FALLBACK_TO_EN）。本线 `BulletHoleParticle.Provider` 同样先 `HitSurfaceParticleSpawner.spawn` 再判贴花（273-276 行），上游原文不属实，登记为 config 侧 KNOWN_TEXT_DEVIATION
- **ABSENT_FEATURE**：VBO ×4 + Sable ×2 语言键不发布（本线无对应构件与配置键；与 1.21.11 一致）
- `tr-TR.json`（大写文件名）不生效、随通用管线处理，无专门动作（1.21.11 已删除该文件，本线按「不需要处理」保留）
- 与 1.21.11 成品逐语言交叉比对：**共享键 0 值差异**（en_us/zh_cn 的差异全部是本线特有键：scope PIP/mask、meshloader、view tuning、pack compatibility 提示；`zh_tw` 的 89 个本线键为 26.2 自有翻译）；en/zh 键集 parity 校验通过（CI「Lang key parity」同款脚本本地已跑）

## §4 工具与 CI

| 项 | 状态 |
|---|---|
| `scripts/align_config_text.py` | 已移植（KNOWN_ABSENT/KNOWN_TEXT_DEVIATION 直接沿用 1.21.11 登记即可，差异 0） |
| `scripts/port_upstream_lang.py` | 已移植（去掉 bundle 读取；ADAPT_KEYS 版本号 → 26.2；ACCURATE_TEXT 代替 KEEP_LOCAL——本线本地 hit_particle 原文就是错误上游文本，不能保留，必须改写） |
| `align_three_way.py` / `align_common_tokens.py` | 未移植（只读诊断工具，与 API 名称漂移强耦合，需要时再按 26.2 表面适配） |
| CI「Upstream 1.2.0 text alignment」步骤 | **未装**：`.github/workflows/` 需维护者网页端上线（Agent 无 `workflows` 权限，AGENTS.md §1）；且 `docs/ci/build.yml` 镜像必须与正式件逐字节同步。两个脚本已可独立运行，建议与下次 workflow 改动一并装上（先改 `docs/ci/build.yml`，再由维护者同步到 `.github/workflows/build.yml`） |

## 验收状态

| 层 | 状态 |
|---|---|
| 静态（本沙箱，已跑） | `align_config_text.py` 差异 0；`port_upstream_lang.py --check` clean；en/zh 键 parity OK；`check_release_consistency.sh --strict` 6 通过 0 失败；mixin 配置完整性 OK；方块掉落接线 OK；方块实体持久化 OK |
| 编译 / 单测 / 成品 | **CI 全绿 @ `c0e0f026`**：`compile-check` [45942123094](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45942123094)（compileJava + gametest 源集编译）；`build` [45942123111](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45942123111)（全量构建、JUnit、专服 GameTest——202 产物结算回执、成品 JAR 审计、真实 Mesa 客户端 smoke）。首轮 `343dd5e2` 的 compile-check 抓出本执行中唯一的编码笔误（`AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER` 误写 `BooleanValue`），`c0e0f026` 修正后全绿 |
| C1 探针 | `ViewTuningSmoke`（含改版后的 k 比例断言）在 `build` 45942123111 的「Real integrated client」步真实执行并通过（该 smoke 主流程含 `ViewTuningSmoke.run`，任何断言失败都会使整个 run 变红）；`26.2 gameplay and renderer` [45942123139](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45942123139) 与 `Iris thermal regression` [45942123104](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45942123104)（3 个光影作业）亦全绿 |
| 实机专项 | **待维护者**：C2 的工作台预览 + 世界 LOD 回归、C6 装/不装 PAL 各暂停一次、C1 曳光近端深度目视（`TRACER_DEBUG` 日志键已改名 `lateral=`） |

## 未覆盖 / 与清单的偏差（逐条申报）

1. §4 两个诊断脚本与 CI 步骤未装（原因见上表）——清单要求「有镜像的线两份逐字节相同」，单方面改 `docs/ci/build.yml` 反而制造不一致。
2. L1 中 ko_kr 等上游自带旧译文（如 `commands.tacz.reload.backup` 仍写旧路径）按 1.21.11 先例逐字节随上游；如需更正须 1.21.11 先行，否则两线再次分叉。
3. 其余无偏差：C1–C8、§2、L1、L2、AmmoConfig 顺序全部落地。
