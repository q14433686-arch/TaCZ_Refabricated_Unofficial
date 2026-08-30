# 1.21.11 分支：PIP 二次渲染 + 分辨率缩放 移植方案（ScopePipRerender / ScopePipResolutionScale）

日期: 2026-08-30
分支: `arena/01a052b2-tacz-refabricated-unofficial`（1.21.11，depth-aperture 架构）
目标: 把 `26.2(main)` 的「二次渲染 + 分辨率缩放」移植到本分支，并接入局内配置（Cloth/ModMenu）。

---

## 0. 结论（TL;DR）

- 「PIP 分辨率配置」（`ScopePipResolutionScale`）在本分支上**不存在**，因为它只对 26.2 的
  `ScopePipRerender`（二次世界渲染）路径有意义；本分支的 PIP 是 Step-3「屏幕空间重投影」，
  镜内分辨率被数学锁死为「屏幕分辨率 ÷ 倍率」，没有二次渲染 pass 就没有可缩放的对象。
- 26.2 的 rerender 是一整套 **25+ 文件**的特性，且写死在 26.2 的
  **render-state API**（`GameRenderState` / `CameraRenderState` / 8 参 `LevelRenderer#render` /
  `GameRenderer#mainRenderTarget()`）与 **mask**（`ScopeMaskRenderer`）架构上。
- 本分支是更早的渲染 API（`renderItemInHand(float, boolean, Matrix4f)`，无 `GameRenderState`），
  且是 **depth-aperture** 架构 —— 无法直接复制，必须逐处适配。
- **本沙箱无 JDK、无通用外网**（只能访问 github.com/api.github.com，Debian 源、Maven、
  Mojang、Gradle 均不可达），因此**无法编译、无法 `javap` 1.21.11 的 jar** 来确认签名。
  按本仓库规则（编译通过 ≠ 运行期安全、不得声称未验证的实现），渲染核心不能「盲写」。

本文档给出：26.2 侧完整清单、1.21.11 侧逐条 API 断点、分阶段实施计划与 `javap` 检查清单。

---

## 1. 背景：本分支现状 vs 26.2

| | 本分支（1.21.11，Step-3） | 26.2(main) |
|---|---|---|
| PIP 实现 | 屏幕空间重投影（`ScopePipRenderState`） | 重投影（默认）+ **二次渲染**（`ScopePipRerender`） |
| 镜片判定 | 深度孔径 `ad < wd - eps` | 目镜掩码 `ScopeMaskRenderer` |
| 抓取点 | `renderItemInHand` HEAD / Iris final TAIL | `LevelRenderer#render` 之后 |
| 合成点 | `renderItemInHand` RETURN | 掩码画完、镜身画之前的阶段边界 |
| 镜内分辨率 | 屏幕 ÷ Z（不可配） | 重投影=屏幕÷Z；**rerender=原生，可配 `ScopePipResolutionScale`** |
| 已接入配置 | 8 项（enable/min_progress/min_mag/world_zoom_share/sharpness/allow_shader_packs/debug_no_composite/debug_paint_lens） | 上述 8 项 + rerender/resolution/shadow/isolate/debug_trace/release_idle/idle_delay/debug_gpu_mem |

结论：把 `ScopePipRerender` + `ScopePipResolutionScale` 移植进来后，`ScopePipWorldZoomShare`
的注释里「这是唯一能真正增加镜内分辨率的旋钮」这句话就不再成立 —— 那正是本任务要改变的点。

---

## 2. 26.2 rerender 完整文件清单（移植依赖）

### 2.1 核心（必须先移植）

| 文件（26.2 路径 `com/tacz/guns/client/render/scope/`） | 职责 | 移植难度 |
|---|---|---|
| `ScopePipRenderer.java`（1419 行） | rerender 主流程：窄 FOV 二次渲染 + 投影四路同步 + 合成 + 闸门 | **高**（核心，但依赖大量 26.2 API） |
| `ScopePipTarget.java`（121 行） | 离屏 color target（`new TextureTarget(...)`） | 中（`TextureTarget` 在 1.21.11 需换等价物） |
| `ScopePipResourceProbe.java`（218 行） | 资源泄漏诊断探针 | 低（纯诊断，可**后置**） |
| `ScopePipTrace.java`（200 行） | 渲染目标归属 trace 诊断 | 低（纯诊断，可**后置**） |

### 2.2 mixin（26.2 `mixin/client/`）

| 文件 | 职责 | 1.21.11 断点 |
|---|---|---|
| `GameRendererMixin.java` | `renderLevel` 注入（rerender 调用点）、`captureScene`、`mainRenderTarget()` 重定向、`compositeAfterLevelUnderShaders` | 注入目标/重定向方法都不同（见 §3） |
| `LevelRendererAccessor.java` | 暴露 `submitNodeStorage` | 字段名/类型待 javap |
| `FeatureRenderDispatcherMixin.java` | PreparedFrame 泄漏恢复 + 阶段边界合成钩子 | 1.21.11 的 `FeatureRenderDispatcher` 有无 `PreparedFrame` 待 javap |
| `PreparedFrameAccessor.java` | 同上 | 同上 |
| `SimpleFeatureRenderPhaseMixin.java` / `TranslucentFeatureRenderPhaseMixin.java` | 「本帧提交节点留给主画面」防实体消失 | 阶段类结构待 javap |
| `LevelExtractorScopePassMixin.java` | 镜内一遍的 level extract | 待 javap |
| `SkyRendererMixin.java` | 镜内一遍天空 | 待 javap |

### 2.3 Iris / Voxy / 其它 mod 兼容（26.2）

| 文件 | 职责 |
|---|---|
| `compat/iris/IrisScopePipelineCompat.java` + `IrisScopeMaskState.java` | 光影下镜内一遍的独立管线 |
| `mixin/client/iris/IrisScopeDimensionMixin.java` / `IrisShadowResolutionMixin.java` / `IrisExtendedShaderMixin.java` / `IrisGlCommandEncoderMixin.java` / `IrisShaderCreatorMixin.java` | 光影 rerender 的深度注入（针对 **Iris 1.10.7-for-26.2** 逐字节审计） |
| `compat/sodium/SodiumCompat.java` | 投影快照覆盖 + 区块 uniform 上传闸复位（针对 Sodium 0.9.x-for-26.2） |
| `compat/voxy/VoxyCompat.java` + `VoxyScopePipelineCompat.java` + `mixin/client/voxy/*` | Voxy LOD 视口分离 |
| `compat/physicsmod/PhysicsModCompat.java` | Physics Mod 投影同步 |

> 本分支（1.21.11）**没有** `SodiumCompat` / `VoxyCompat` / `PhysicsModCompat`（compat 目录无这三者），
> Iris 侧是另一套 mixin（`IrisFinalScopeOverlayMixin` / `IrisDepthRestoreShaderMixin` /
> `IrisHandRendererReticlePassMixin`）。整段 §2.3 应**整体后置**，见 §5 裁剪策略。

---

## 3. 1.21.11 API 断点清单（`javap` 确认后才能动笔）

> 「已确认」= 从本分支现有代码/docks 直接读出的证据；「待确认」= 必须对
> `minecraft-merged-1.21.11.jar`（或 loom 反编译产物）`javap` 才能定。

| # | 26.2 用法 | 1.21.11 已知事实 | 待 `javap` 确认 |
|---|---|---|---|
| 1 | `renderItemInHand(CameraRenderState, float, Matrix4fc)` | `renderItemInHand(float, boolean, Matrix4f)`（`GameRendererMixin` 头注释 + `PORT_1_21_11_PHASE2.md` L315） | —（已确认） |
| 2 | `renderScopeView(..., GameRenderState gameRenderState, DeltaTracker)`，读 `gameRenderState.levelRenderState.cameraRenderState` | 全仓库无 `GameRenderState`/`LevelRenderState` 引用；`renderItemInHand` 不带 state | `GameRenderState`/`LevelRenderState` 是否存在；若不存在，镜头信息从 live `Camera` + `renderItemInHand` 的 `Matrix4f projection` 反推 |
| 3 | `mc.levelRenderer.render(allocator, deltaTracker, false, camera, viewRotationMatrix, fogBuffer, fogColor, renderSky)`（8 参） | 无任何调用证据 | `LevelRenderer#render` 在 1.21.11 的真实签名与可见性；`GameRenderer#renderLevel` 的结构（哪几行设置投影、在哪调 `levelRenderer.render`） |
| 4 | 重定向 `GameRenderer#mainRenderTarget()`（mixin） | `Minecraft#getMainRenderTarget()` 存在且被本分支大量使用；另有 `RenderSystem.outputColorTextureOverride` / `outputDepthTextureOverride`（`ScopeFinalOverlayState` 用过） | 1.21.11 里主 target 由谁持有；重定向该改 `Minecraft#getMainRenderTarget` 还是走 `outputColorTextureOverride`；`LevelRenderer` 内部从哪读 target |
| 5 | `RenderPipeline.builder(...).withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, WRITE_COLOR))` | **1.21.11 没有 `ColorTargetState`/`DepthStencilState`**（`ScopeRenderTypes` L480 注释），用 `withColorWrite(bool)` 风格；格式类型是 `com.mojang.blaze3d.textures.TextureFormat` 而非 `GpuFormat` | 合成管线 `compositePipeline()` 必须按 1.21.11 builder 重写（本分支 `ScopePipRenderState.pipeline()` 已有等价写法可抄） |
| 6 | `new TextureTarget("tacz_scope_pip", w, h, needsDepth, format)` | 本分支离屏纹理是**手写 GL**（`SceneColorTarget`：`glGenTextures`+`glTexImage2D`），未见 `TextureTarget` | 1.21.11 有没有 `TextureTarget`/`RenderTarget` 子类可用于「可渲染（带深度附件）的离屏 target」；没有的话需照 `SceneColorTarget` 手写 FBO |
| 7 | `new Projection()` / `PROJECTION.setupPerspective(0.05f, depthFar, fov, w, h)` / `PROJECTION.getMatrix(NARROW_MATRIX)` | `RenderSystem.getProjectionMatrixBuffer()` / `getProjectionType()` / `setProjectionMatrix(GpuBufferSlice, ProjectionType)` 均存在（`ScopeFinalOverlayState` L134-142） | `com.mojang.blaze3d.Projection` 是否存在、构造窄投影的等价 API（`ProjectionMatrixBuffer` + `Projection` 的 setup 方法） |
| 8 | `CameraRenderState.projectionMatrix` / `.viewRotationMatrix` / `.fogData.color` / `.depthFar` / `.isPanoramicMode` / `.initialized` | 本分支用 live `Camera`（`event.getCamera()`）；`renderItemInHand` 直接给 `Matrix4f projection` | 1.21.11 里「本帧基准投影矩阵 / FOV / 近远平面」从哪取（`GameRenderer#getProjectionMatrix`? `renderItemInHand` 的投影参数能否复用于 rerender 的宽投影） |
| 9 | `CommandEncoder.copyTextureToTexture`（`GpuFormat` 两端一致） | 本分支 `SceneColorTarget.copyFrom` 已用同一 API（`TextureFormat`）✅ | 离屏 target 的纹理能否被 `copyTextureToTexture` 当 dst（usage 位） |
| 10 | Iris/Sodium/Voxy/PhysicsMod 各兼容层 | 本分支无对应类；Iris 是 1.21.11 构建（`iris_curse_file=7805348`），与 26.2 审计对象不同 | 整体**后置**，见 §5 |

---

## 4. 分阶段实施计划（每步一个编译检查点）

> 前提：拿到 JDK（≥21）+ 可跑 `./gradlew build` 的环境（含对 maven.fabricmc.net / services.gradle.org
> / modrinth / cursemaven 的网络），并 `javap` 出 §3 里「待确认」的签名。

### B0 · 补签名（半天）
`javap` `minecraft-merged-1.21.11.jar` 的 `net/minecraft/client/renderer/GameRenderer`、
`LevelRenderer`、`Camera`、`com/mojang/blaze3d/pipeline/*`、`com/mojang/blaze3d/Projection*`，
把 §3 的「待确认」填成事实表。

### B1 · `ScopePipTarget` 移植（安全，可独立编译）
把 26.2 `ScopePipTarget` 改成 1.21.11 的可渲染离屏 target（含深度附件），
`TextureFormat` 替代 `GpuFormat`；若 `TextureTarget` 不存在则照 `SceneColorTarget` 手写 FBO。
此步产物可独立被 `ScopePipRenderState` 引用，不改任何运行行为。

### B2 · 配置 + Cloth + 语言（安全）
`RenderConfig` 加 `SCOPE_PIP_RERENDER`（默认关）、`SCOPE_PIP_RESOLUTION_SCALE`（0.25~1.0，默认 0.75）；
`RenderClothConfig` 加两项；`assets/tacz/lang/{en_us,zh_cn}.json` 加 4 键。
**注意**：此步必须与 B3 同批合入 —— 单独合入会暴露「点了没反应」的死配置。

### B3 · `renderScopeView` 核心（高风险，本任务的关键）
- 注入点：按 §3#3 的 javap 结论，在 `GameRenderer#renderLevel` 里、vanilla `levelRenderer.render`
  之前调 `renderScopeView(...)`（本分支已有 `GameRendererMixin`，扩展它）。
- 窄投影：按 §3#7/#8 结论构造；把 `RenderSystem.setProjectionMatrix` 换成窄投影。
- 重定向：按 §3#4 结论把这一遍的世界画进离屏 target。
- 结束还原：投影、target 全部还原，异常 → `failed=true` 回退整屏变焦（沿用本分支「失败即退回」模式）。
- **只做 vanilla（无光影）路径**；Iris 下直接沿用现有屏幕空间路径（见 §5）。

### B4 · 合成接线
复用本分支 `ScopePipRenderState` 的**深度孔径合成**：rerender 开启时把采样源从
「主画面拷贝」换成「B3 的窄投影离屏 target」，且 `lensZoom=1`（离屏已是窄 FOV，不能再重投影），
`worldZoomTarget()` 恒返 1（镜外保持 1×）。等价于 26.2 `runComposite(rerenderMode() ? 1.0f : ...)`。

### B5 · Iris/Sodium/Voxy/PhysicsMod 兼容（后置，另开任务）
按 §2.3 逐项重新审计（26.2 的审计结果**不可直接套用**到 1.21.11 的对应 mod 构建）。

---

## 5. 裁剪 / 降级策略（诚实标注）

- **本轮目标**：vanilla（无光影）rerender + `ScopePipResolutionScale`，默认**关闭**，行为与
  现状完全一致（零回归）。
- **Iris 下**：rerender 开关暂不生效，保持现有「屏幕空间成品帧合成」路径（`ScopePipRenderState`
  的 `captureSceneAfterIrisFinal`），即「光影下分辨率缩放暂不可用」—— 需在配置 tooltip 里写明。
- **Sodium / Voxy / PhysicsMod / Iris 隔离**：整体后置；这些 mod 在场时 rerender 行为未验证
  （沿用 26.2 文档里「与 Sodium 二次渲染有实体消失史」的警示，默认关）。
- **不做**：`ScopePipShadowScale` / `ScopePipIsolatePipeline` / `ScopePipDebugTrace` /
  `ScopePipReleaseIdlePipeline` / `ScopePipIdleReleaseDelayFrames` / `ScopePipDebugGpuMem`
  —— 这些是 26.2 光影/性能调优的配套，等 B5 一起。

---

## 6. 配置 / Cloth / 语言接入清单

- `RenderConfig`：`SCOPE_PIP_RERENDER`（`define("ScopePipRerender", false)`）、
  `SCOPE_PIP_RESOLUTION_SCALE`（`defineInRange("ScopePipResolutionScale", 0.75, 0.25, 1.0)`）。
- `RenderClothConfig`：两项条目 + tooltip（沿用 26.2 的 `scope_pip_rerender` /
  `scope_pip_resolution_scale` 键，但 tooltip 文案改为本分支口径，注明「光影下暂不生效」）。
- lang 键：`config.tacz.client.render.scope_pip_rerender(.desc)`、
  `config.tacz.client.render.scope_pip_resolution_scale(.desc)`（en/zh 各 4 键）。

---

## 7. 验证矩阵（**均未执行** —— 本沙箱无编译/运行环境）

- [ ] `./gradlew build` 通过（B1~B4 每步一次）。
- [ ] 默认配置（rerender 关）：画面与现状逐帧一致，无新增日志。
- [ ] rerender 开 + vanilla：镜内原生分辨率、镜外 1×，6×/8× 镜下明显更清晰；
      进出开镜无 POV 跳变；准星/遮光罩仍在镜片上层。
- [ ] `ScopePipResolutionScale` 0.25/0.5/0.75/1.0 生效（镜内画质/帧率随档位变化）。
- [ ] 异常注入（如切视距时开镜）→ `failed=true` 回退整屏变焦，不崩。
- [ ] Iris 下：rerender 开关不生效、走现有屏幕空间路径、tooltip 与行为一致。
- [ ] Sodium / Voxy / PhysicsMod 在场：标注「未验证」，默认关。

---

## 8. 下一步需要什么

1. **一个能编译的环境**：JDK 21+，且能访问 fabric/maven/gradle 镜像 —— 当前沙箱两者皆无。
2. 或：把 `javap` 出的 §3 签名表贴回来，我据此把 B1~B4 写完整（渲染核心无法脱离签名盲写）。
3. 或：接受「盲写 + 明确标注未编译」的草稿（本仓库规则不推荐，会冒编译失败拖垮整个分支的风险）。

> 语言文件修复（`assets/tacz/lang/{en_us,zh_cn}.json` 316 键）已在上一轮完成，与本任务解耦，
> 可直接单独提交。
