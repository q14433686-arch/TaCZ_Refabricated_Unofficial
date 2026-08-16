# 全量待办 / 技术债 / 兼容缺口盘点（2026-08-11 · 历史复核版）

> **已被 2026-08-12 的源码/上游差分审计取代。** 本文对 PAL、Shoulder Surfing、
> ImmediatelyFast、LRTactical 网络同步与枪械等级脚手的若干结论已经过时；当前结论见
> [`UPSTREAM_PARITY_AND_TODO_AUDIT_2026_08_12.md`](UPSTREAM_PARITY_AND_TODO_AUDIT_2026_08_12.md)。
> 保留本文只为追踪当时的判断，不再作为待办清单。
>
> 范围：`src/main/java` 全量 grep（TODO/FIXME/XXX/HACK + 中文「暂未/未实现/禁用/回退/待」语义）+
> `compat/` 全目录过目 + docs 已知事项对照。
>
> **⚠ 方法论声明（本版与初版的关键区别）**：初版存在「照抄代码注释当结论」的错误
> （用户指出：注释很多是落后/错的）。本版**每一条结论都以调用链/字节码核实为准，
> 注释仅作线索**；核实过程中发现的错误注释已顺手在源码里更正
> （TextShowRender、MuzzleFlashRender、BeamRenderer、IrisCompat、FeatureRenderCompat）。
> 案例①~⑦的诊疗史见 `COMPAT_AND_ROADMAP.md`，不重复收录。

## A. 玩家可见的功能缺口（按影响排序）

| # | 项 | 核实结论（证据） | 玩家影响 | 建议 |
|---|---|---|---|---|
| ~~A1~~ | ~~枪身/镜身文字显示未实现~~ | **❌ 初版误判，已推翻。** 真实状态：**已实现且在役**。证据链：`GunItemRendererWrapper:299/561 → BedrockGunModel.submit → BedrockModel.submit:372 → BedrockRenderSnapshot.capture`（capturePart 里对 `IFunctionalSubmitter` 走 `extract`）→ `TextShowRender.extract:52` 提交 `collector.submitText(...)` → `BedrockModel.submit:382 submitFunctionalTasks` 落 collector。`submitText(PoseStack,F,F,FormattedCharSequence,Z,Font$DisplayMode,I,I,I,I)V` 在 26.2 `OrderedSubmitNodeCollector` 上字节码存在且签名逐参一致。误判根因：`TextShowRender:77` 的旧 TODO 写在已死的 legacy `render()` 里（整条 `renderInto`/`delegateRenderers` 旧链 26.2 零调用方），注释已更正 | 无（默认枪包 ak47/minigun/rpk/两瞄具的 text_show 应该正常显示弹药计数） | **请用户实测一眼**：第一人称手持 AK-47 看枪身计数文字。确认即结案，不占用开发轮次 |
| A2 | **LRTactical 物品冷却无客户端同步** | ✅ 控制流核实：`onThrow` 两个调用点均服务端限定（`onUseTick` 有 `!isClientSide` 门禁、`releaseUsing` 客户端早退 true），客户端 `CLIENT_COOL_DOWNS` 表永远为空；`onCooldownStarted/Ended` 为空实现；`SCustomCoolDownMessage` 类不存在。服务端判定链完整 | 服务端判定正确；**客户端物品栏不显示冷却遮罩** | 补冷却同步包（起/止两条）+ 客户端收包写 `CLIENT_COOL_DOWNS`，工作量小 |
| A3 | **LRTactical 手雷弹跳/死亡音效** | ✅ 代码核实：弹跳**不是静默**——播的是被撞方块自身的 step 音效（`state.getSoundType().getStepSound()`）；缺的是上游 `GRENADE_BOUNCE` 专属音效。**注意：这是素材授权决定，不是单纯没写代码**——该 .ogg 属原作 ARR 素材，本移植不打包，`lrtactical/init` 下连音效注册类都不存在 | 弹跳音=方块脚步声（可接受）；无专属弹跳/死亡音 | 可做的合法改进：注册空 `SoundEvent` id（代码无版权问题），sounds.json/.ogg 留给内容包补齐。与 A2 同批做 |
| A3b | **LRTactical 爆炸震屏/破坏倍率不生效**（初版漏收） | ✅ 代码核实：`GrenadeEntity.screenShakeTime/screenShakeAmplitude` 字段保留但**无任何生效路径**（上游靠 `SShakeScreenMessage` 自定义包，网络层未移植）；`destroyMultiplier` 因走原版 `Level#explode`（无该参数）同样不生效 | 爆炸无屏幕震动；破坏力倍率配置无效 | 震屏可与 A2 共用一条网络包通道顺带实现；倍率需自定义爆炸逻辑，建议维持不做 |
| A4 | **光影下的镜内掩码** | **❌ 初版措辞过时。** 当前设计（代码核实 `IrisCompat.shouldDisableScopeMaskUnderShaderPack`）：Iris 下**不再全局回退**——走 `assignPipeline`→Iris hand program + `tacz.iris.mixins.json` 注入的 `tacz_ScopeMaskMode` 着色器分支（默认关，仅携带 ScopeMaskSampler 的 scope_* 管道启用）；仅 **sulkan Mod 存在**时才返回 true 整体回退。失败兜底：Iris API rev<3 时打 warn 日志并回退 | 开 Iris 光影时掩码正常即无感；assign 失败才可能镜内见镜筒内壁 | 维持现状；若用户声量起，再查 Iris assign 失败现场 |
| A5 | **多部件实体命中近似** | ✅ 代码核实：`EntityKineticBullet:386 MaybeMultipartEntity.of` 的 PartEntity 父级解析被注释（`hitPart` 原样作 `core`）；Fabric 无原生 PartEntity | 打末影龙等多部件实体时归属按部件自身记账；普通生物无感 | 低优先；上游决定映射方式前不动 |
| A6 | **镜内枪口烟雾不裁** | ✅ 定案维持（世界粒子 pass，裁剪需侵入全局粒子着色器，风险高） | 开镜开火镜片里仍见烟雾 | 维持不做 |

## B. Mod 兼容桩（等外部条件，非我方债务）

| # | Mod | 核实结论 | 解锁条件 |
|---|---|---|---|
| B1 | **Accelerated Rendering** | ✅ `ARCompat.init` 即使检测到 AR 也**强制** `LOADED=false`，`shouldAccelerate()` 硬返回 false；AR mixin json 以独立 `tacz.compat.acceleratedrendering.mixins.json` 隔离。激光/枪模走普通路径在役正常 | AR 发布 26.2 构建后恢复 implements 与开关 |
| B2 | **KubeJS** | 桩（`KubeJSGunEventPoster`） | KubeJS 支持 26.2 |
| B3 | **Controllable（手柄）** | 桩（`ControllableCompat`） | Controllable 支持 26.2 |
| B4 | **Sulkan（Vulkan 光影）** | **❌ 初版「检测未实现」有误。** 实测代码：`isUsingRenderPack` 与 `shouldDisableScopeMaskUnderShaderPack` 都有 `FabricLoader.isModLoaded("sulkan")` 存在级探测并在存在时保守回退。Sulkan 确为真实新 Mod（mravatin 的 Vulkan 光影引擎，面向 26.2，Sodium/独立后端），其有无公开查询 API **未核实源码**、按无处理 | Sulkan 提供公开 API；现状回退安全。另：IrisCompat 旧头注「Iris 已被 Sulkan 取代」系错误陈述，已更正——Iris 在 26.2 在役（用户实测环境 iris 1.11.2） |
| B5 | Optifine-detect / ShoulderSurfing / CarryOn / ImmediatelyFast / JEI / REI / Zoomify / PAL | 检测或浅兼容，在役（JEI/REI PR#22 已修；PAL 案例⑥已闭环） | — |

## C. 死代码 / 陈旧标记（无玩家影响，可择机清理）

| # | 位置 | 核实结论 |
|---|---|---|
| C1 | `MuzzleFlashRender` 旧链（`render` 复写 → `renderMuzzleFlash:56` → `delegateRender` → `doRender:68`） | ✅ 死链确认：`BedrockModel.renderInto` 全仓零调用、deprecated `render(...)` 已 no-op、`delegateRenderers` 循环随旧链一起死。在役路径是 `extract()`（含镜内掩码逻辑）。外部引用只碰 `isSelf`/`onShoot()` 静态量。删除零风险，但连带牵扯 `renderInto`/`delegateRender` API，建议整链一次删净 |
| C2 | `BeamRenderer:58-61` collector==null 分支 | ✅ 现存 2 个调用方都传 collector；**4 参便捷重载**（内部传 null）才无调用方（初版误写「5 参」）。分支语义=不画，不可达但有护肝价值，注释已更正 |
| C3 | `RenderHelper.enable/disableItemEntityStencilTest` | ✅ 无代码调用方（仅 `BedrockAttachmentModel:446` 一处注释提及），可整段删 |
| C4 | `cn.sh1rocu.tacz.mixin.client.SoundEngineMixin` + `ChannelAccessHandleMixin` | **❌ 初版「空档位/删注入点」措辞有误。** 实情：两个类**未注册进任何 mixins.json**（无从谈起删注入点），且内部仍残留 Yarn 名的 `@ModifyArg(method_19757)` 等——注册即炸，所以「保持不注册」是硬约束不是选择。另一事实是 `PlaySoundSourceEvent` 仓内零消费者，但它是 `cn.sh1rocu.tacz.api.event` 下的**公开 API 面**（外部 Mod 可订阅）。处置：源文件保留当文档即可，勿注册、勿删 API |
| C5 | `renderer/feature/` 包（`FeatureRenderCompat`/`GunModelSubmit`/`GunModelFeatureRenderer`） | **❌ 初版只写了「TranslucentSubmit 未实现」，实质上整包是死脚手**：`FeatureRenderCompat.submit` 零调用方（`BedrockModel.submit` 直用 `submitCustomGeometry`），`GunModelSubmit` 仅在该方法内被 new，`GunModelFeatureRenderer.TYPE` 注册了但永远收不到节点。「TranslucentSubmit 未实现」因此根本无关紧要。可整包删（记得摘掉 init 注册）或留作将来 Feature-Rendering 化的模板 |
| C6 | `ServerMessageLevelUp:56` 升级 Toast 封印块 | ✅ 上游功能半成品，跟上游对齐，不动 |

## D. 悬置的技术审视项（有意不做的决定，记录在案）

| # | 项 | 定案 |
|---|---|---|
| D1 | 案例③ 目镜八边形镜框内圈被凸包掩码啃边 | **用户挂起**；`ScopeMaskHullFill=false` 秒退 |
| D2 | 案例⑦ 炮弹/炮烟第一人称从眼位出发 | **已回退为上游原生行为**（用户定）；重启入口=射击当帧采视模世界矩阵，勿复用曳光锚定缓存向量 |
| D3 | PIP / 第二世界镜内渲染 | 暂停（上游本无 PIP，放大靠 FOV 变焦） |
| D4 | `CommonNetworkCacheEvent` 单人存档缓存豁免 | 「尚未修复也未验证」在案：多次基于推理的误改史，**改动前必须先加日志取证** |
| D5 | 案例⑤ 激光改色 | 结案：光影包限制（无彩色自发光），LaserDebug 探针保留备查 |

## E. 上游自带 TODO（继承债，不动）

`LootTableInjectorModifier`（第 39 轮已结案，残留 TODO 字样是历史文书）、
`LivingEntityShoot:270`、`EntityKineticBullet:439`（暴击 flag 透传）、
`ModernKineticGunScriptAPI:843`。这些等上游定，跟上游对齐即可。

## 建议行动顺序（复核后）

1. ~~A1 TextShow~~ → **改成零成本动作：用户实测确认枪身文字在显示**（代码侧已证在役）；
2. **A2+A3+A3b LRTactical 小批**：冷却同步包 + 空 SoundEvent 注册 + （可选）震屏包，同一条网络通道一次做完；
3. C 类清理顺手轮：C1 整链删除 / C3 整段删除 / C5 整包删除（各为独立小提交，均无风险）；
4. B 类全部等外部版本，A4/A5/A6 与 D 类维持现状。
