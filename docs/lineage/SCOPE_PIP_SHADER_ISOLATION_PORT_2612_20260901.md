# 光影下二次渲染的时域隔离：本线怎么落地（26.1.2 G5 大件移植方案 + 首批已落地）

2026-09-01。维护者裁定：「**光影下二次渲染隔离大件我认为可以有**」⇒ 推翻上一批"整组不移植"的结论，
本文件写**具体做法**：哪些直接搬、哪些在我方必须换做法、哪些仍不搬及其前置条件。
上一批的逐 commit 甄别见 `docs/lineage/SYNC_REVIEW_2612_RENDER_LINE_20260901.md`（G5 组）。

## 0. 一句话结论

镜内那一遍**借 Iris 自己的按维度缓存管线**拿到一套独立管线（独立 colortex/程序/整族 previous uniform），
从而把"每份上一帧状态被推进两次"这个病根切断；阴影贴图再给它单独配小份。**首批已落地并过编译门**，
Sodium 通道**必须一起搬**（Fabric 上 Iris 硬依赖 Sodium，没它就没光影）；只有 Voxy 那条留到下一批（§3）。

## 1. 他们的形状（读补丁所得，非读文档所得）

| 件 | 他们的位置 | 作用 |
|---|---|---|
| 反射核心 | `compat/iris/IrisScopePipelineCompat.java`（407 行，**纯反射**摸 `Iris.getPipelineManager`/`PipelineManager.getPipelineNullable`/`preparePipeline`/`pipelinesPerDimension` map/`NamespacedId`/`IrisRenderingPipeline.destroy`） | 造 `tacz:scope_pip` 维度 id、预热建管线、热重建、空闲释放 |
| 维度替换 | `mixin/client/iris/IrisScopeDimensionMixin`：`@Inject` `Iris#getCurrentDimension` **HEAD + cancellable**，镜内那遍期间改答专用 id | 隔离的唯一开关点；生死周期仍归 Iris（`destroyPipeline` 遍历 map 一并回收，不自己 new） |
| 阴影降采样 | `mixin/client/iris/IrisShadowResolutionMixin`：`PackShadowDirectives#getResolution` **RETURN**，仅在 `buildingScopePipeline` 窗口内改返回值，并回执 `noteShadowResolutionIntercepted()` | 一帧两遍 ⇒ 阴影也两遍；减半 = 镜内那遍阴影只花 1/4，主画面不受影响 |
| 配置 | `ScopePipIsolatePipeline`(true) / `ScopePipShadowScale`(0.5) / `ScopePipReleaseIdlePipeline` / `ScopePipIdleReleaseDelayFrames`(120) | 隔离总闸、阴影旋钮、开镜帧率衰减的处置杠杆 |
| 接线 | `ScopePipRerender#prewarmShaderPipelineIfNeeded()`（由 `GameRendererMixin` render HEAD 调）+ 窄遍前置 `scopePassIsolated` + 出窗清零；`LevelRendererAllChangedScopePassMixin` 打破稳态快速路径 | 首镜不卡编译；`allChanged` 后重建 |
| Sodium 通道 | `SodiumCompat.overrideProjection(NARROW_MATRIX)` + `mixin/sodium/SodiumProjectionSnapshotSyncMixin`（同步私有投影快照）+ 第二渲染栈 `secondaryRenderers()` | 他们**必须**做：Sodium 地形不读 `RenderSystem` 投影槽，只认 `ProjectionMatrixBuffer#getBuffer` 抓走的私有快照 |
| Voxy 通道 | `compat/voxy/VoxyScopePipelineCompat`（第二套栈、`swapIn`、`isMainStackBoundTo` 熔断）+ `IrisVoxyScopeCompat` + 预热窗口内建栈 | 他们**必须**做：Voxy 每遍只消费一次"当前管线"，切不过去就得让镜内那遍坐过；建栈时机错 = 整局崩（`Pipeline data already bound` 被 Voxy 捕获后顺手 `disableIrisShaders()` → 主画面 NPE） |

## 2. 我方已落地（本文件写完时已在分支上，CI `success`）

| 件 | 我方落点 | 与他们的差异 |
|---|---|---|
| 反射核心 | 新建 `com/tacz/guns/compat/iris/IrisScopePipelineCompat.java` | **剥掉全部 Voxy 分支**（本线无 `compat/voxy`）；`releaseScopePipelineIfPresent()` 的兜底闸从"isMainStackBoundTo"换成**我方自己的** `ScopePipRerender.isInsideScopeLevelRender()`（没第二栈可绑，但要防的就是同一件事：销毁正在被用的管线）；新增 `handlesAvailable()`/`isScopePipelineBuilt()`/`isShadowHookAlive()` 三条诊断口 |
| 维度替换 | 新建 `mixin/client/iris/IrisScopeDimensionMixin.java`，注册进 `tacz.iris.mixins.json`（该 config 由 `IrisCompatMixinPlugin` 按 `isModLoaded("iris")` 整包把关、`required=false`） | 逐字照搬（含 `remap=false`、`require=0`）——我方 Iris 是 `modCompileOnly`，但我们刻意**不 import** Iris 类，好让结构变化退化成"安静放弃" |
| 阴影降采样 | 新建 `mixin/client/iris/IrisShadowResolutionMixin.java`，同上注册 | 逐字照搬；`2 的幂对齐 + 至少 256` 的规则保留 |
| 配置 | `config/client/RenderConfig.java` 四条：`ScopePipIsolatePipeline`(默认 true)、`ScopePipShadowScale`(0.5，0.25–1.0)、`ScopePipReleaseIdlePipeline`(**默认 false**)、`ScopePipIdleReleaseDelayFrames`(120，30–1200) | 空闲释放我方**默认关**：它是"确实看到衰减才用"的杠杆，默认开会让首镜多一次编译卡顿（待实机定默认值） |
| 接线 | `ScopePipRerender#prewarmShaderPipelineIfNeeded()`（含空闲释放计数），由 `GameRendererMixin#tacz$renderTickStart` 在 `ScopeDepthCopyState.onClientFrameStart()` 之后调；窄遍内 `scopePassIsolated` 置位 / `finally` 清零 | 预热问 Iris 走 **20 帧节流缓存** `shaderPackActiveCached()`——render HEAD 逐帧跑，不能每帧 `Class.forName`（他们那侧 `IrisCompat` 有缓存，我方没有） |
| 硬拒改造 | `ScopePipRerender#renderScopeView`：旧 `if (IrisCompat.isUsingRenderPack()) return false;` 换成 `if (irisPass && !shaderIsolateSafe()) return false;` | 放行条件 = 隔离开关开 **且** `ScopePipAllowShaderPacks` 显式开 **且** 反射句柄可用 **且** 未装 Voxy（`voxelism`） |
| Sodium 通道 | 新建 `compat/sodium/SodiumCompat.java`（**纯反射、零版本 API**，照搬 26.1.2 的形状）：窄遍期间就地改写 Sodium 的地形投影私有快照、出窗还原；再 `prepareFrame()` 重开它「每帧只上传一次区块 uniform」的闸 | 已落地。**这两条不是优化而是前提**：不改写快照 ⇒ 镜内地形宽 FOV、实体窄 FOV，两套比例糊在一起；不重开门 ⇒ 主画面沿用镜内那遍上传的 uniform（即「镜内画面溢出到全屏」的真正病因） |

**没有**照搬 `LevelRendererAllChangedScopePassMixin`：我方没有 indigo/`LevelExtractor` 相关的第二栈状态机，
`prewarmIfNeeded()` 的稳态快速路径盯的就是"Iris 主管线换人没有"（`prewarmedAgainst`），重载光影包必然换人 ⇒
自动重建。那条 mixin 是为 Voxy 的 `voxyStackSettled` 才存在的，缺它反而多一个注入点风险。

## 3. 仍不搬的，与解锁条件

| 件 | 为何本线不搬 | 解锁条件（谁要做就按这个顺序做） |
|---|---|---|
| ~~Sodium 私有投影快照同步~~ | **已随批搬进**（纯反射，既不必把 Sodium 提进编译期，也不需要新增 mixin 包） | — |
> **本文件前一版在这里犯过一个必须留下来的错**：它把「装了 Sodium 就硬拒」写成"保守替代"。Fabric 上
> **Iris 硬依赖 Sodium** ⇒ 该条件恒真，等于「有光影就不放行」，整条隔离永远不会进；当时给的实机清单还写着
> 「装 Iris、不装 Sodium」——做不到的操作。教训：**拿"可选 mod"当安全闸之前，先确认它在目标加载器上
> 究竟可选不可选**（Voxy 是真·可选，Sodium 不是）。
| Voxy 第二渲染栈 | 本线无 `compat/voxy`，且他们自己的注释就写着"缺它时 Voxy 的镜内行为未定义"；建栈时机错误会**整局崩** | 先补 `compat/voxy` 反射层（`renderSystem()`、`isAvailable()`、`ensureBuilt()` 必须落在预热的 `buildingScopePipeline` 窗口内），再补 `swapIn` 与三道 ESC 闸，最后才放开 Voxy 侧拒绝 |
| `ScopePipRerenderInterval`（N 帧复用） | 我方无此键；纯性能杠杆，与隔离无关 | 独立小批，别混进隔离 |

## 3b. 与自家 08-30 方案的顺序冲突（写在这里，免得下一个搬运者以为漏了）

`docs/SCOPE_PIP_RERENDER_1211_PORT_PLAN_20260830.md` 原本把本文件落地的这几条键整组列为「不做，等 B5 一起」，
并且写明前置条件是 **B5 离屏重定向**（窄 FOV 那遍画进**独立 target**，而不是画进主目标再拷）。
本批按维护者裁定提前接线，**B5 仍没做** ⇒ §4-2 那条"主目标里有窄遍成品"的前提才是本批真正的薄弱点。
判序钉死：实机若见"镜内是上一帧主画面/黑屏"，**先补 B5**，不要往隔离层继续叠补丁
（隔离解决的是"两遍互相污染时域状态"，B5 解决的是"这一遍的成品在哪儿"）。

## 4. 落地后必须知道的三处"不可验证性"（按 AGENTS §2 记账）

1. **两个 mixin 是否命中**：`require=0` 软注入 + `targets=` 字符串，CI 与本地都验不了（沙箱无 Iris jar）。
   已把它变成**可自检**：`resolveHandles()` 成功/失败各打一行 INFO/WARN；阴影钩子用回执核验，
   "真构建过却一次都没拦到 `getResolution()`"会打明确 WARN。⇒ **实机跑一次，把 `[TACZ Scope]` 行贴出来即可判定**。
2. **镜内画面在光影下取自哪里**：我方 B1 是"窄遍结束后立刻从主目标拷"。这套在光影下成立的前提是
   **Iris 的最终 blit 发生在 `LevelRenderer#renderLevel` 之内**（Iris 的 `MixinLevelRenderer` 包住整次调用）。
   这是源码级推断、**未实测**。若实机表现为"镜内是上一帧主画面/黑屏"，就是前提不成立 ⇒ 走 Stage 2：
   镜内那遍改由 `GlCommandEncoderScopeDepthCopyMixin` 那条 GL 级通道取色（本线已有该 mixin，现用于深度拷贝）。
3. **首次开镜的编译卡顿**：预热只在 `ScopePipRerender` 开着时生效；若玩家先开镜再改配置，仍会卡一次。属已知取舍。

## 5. 建议的实机判别清单（改一项测一项，别一次全开）

TOML：`ScopePipEnable=true`、`ScopePipRerender=true`、`ScopePipAllowShaderPacks=true`、`ScopePipIsolatePipeline=true`。
**Sodium 必须在场**（Iris 的前置，不是可选项）；**Voxy 必须不在场**（本线还没搬它的第二渲染栈，装了会被 `shaderIsolateSafe()` 挡掉）。

1. **只看日志**（Iris + Sodium 在、Voxy 不在，任意含 TAA/体积云的包）：应依次看到 `Iris pipeline-manager handles resolved`、`Scope pass is using its own Iris pipeline (tacz:scope_pip)`、`Pre-built the scope pass' Iris pipeline now`、`Sodium terrain projection is being synced for the scope pass`、`Sodium chunk uniforms will be re-uploaded for the main pass …`；阴影生效还要有 `Scope pass gets a NNNNxNNNN shadow map instead of …`。
   resolved`、`Scope pass is using its own Iris pipeline (tacz:scope_pip)`、`Pre-built the scope pass' Iris
   pipeline now`；阴影想生效还要有 `Scope pass gets a NNNNxNNNN shadow map instead of …`。
   缺哪条就说明对应件没命中，按 §4-1 处理。
2. **主画面回归**：开镜期间镜外**不应**出现拖影/云噪点/发糙（这是本次的全部意义）；对照 `ScopePipIsolatePipeline=false`
   应能稳定重现那三种伪影 ⇒ 重现与消除都成立才算隔离生效，别只看"没崩"。
3. **显存**：`/debug` 或驱动侧看开镜前后增量；高分屏 + 4096² 阴影的包可能到几百 MB（配置注释已写）。
4. **切维度/重载包**：下界镜内可能吃到带 `*` 的 fallback 着色器（已知取舍）；F3+T 后应能自动重建（看第二条 INFO 再来一次）。
5. `ScopePipShadowScale` 从 1.0 → 0.5 不重启：应看到 `ScopePipShadowScale changed … rebuilding` + 新的 shadow map 行。

## 6. 下一步（按优先级）

①等 §5 的 1/2 两条实机结论回来（决定 §4-2 那条取色前提要不要改）→ ②`ScopePipReleaseIdlePipeline` 的默认值裁定
（要不要用"开镜帧率衰减"换首镜卡顿）→ ③Cloth 配置面板条目 + lang 键（现在这四条只认 TOML）→
④Stage 2（**只剩 Voxy** 第二渲染栈 + 三道 ESC 闸，见 §3）→ ⑤`ScopePipRerenderInterval`。
