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
  Mojang、Gradle 均不可达），因此**无法本地编译、无法本地 `javap`**。
  已用 **GitHub Actions 作编译闭环**解决：workflow `compile-check` 在 CI 跑 `compileJava`
  并把日志写回 `build-reports/compile-java.log`（Contents API 可读）；同时在 build.gradle 尾部
  挂过临时 `dumpRenderApi` task（`finalizedBy compileJava`）用 `javap` 把 1.21.11 渲染 API
  真实签名打进编译日志，再读回沙箱。§3 的「待确认」由此全部落成事实表（见 §3.1）。

本文档给出：26.2 侧完整清单、1.21.11 侧逐条 API 断点（**已全部 javap 核实**）、
分阶段实施计划与当前进度。

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

### 3.1 javap 核实后的 1.21.11 事实表（2026-08-30 CI `dumpRenderApi` 实读）

> 全部来自 `minecraft-merged-1.21.11.jar`（loom compileClasspath）的 `javap -p / -c -p` 输出，
> 原始日志见 commit `38117c3` 的 `build-reports/compile-java.log`。**只列本日志实锤过的项**，
> 没 dump 到的一律不在此断言。

**`GameRenderer.renderLevel` 的 10 参调用（`javap -c`，字节码 342–502）**

- 投影上传（字节码 330–342）：字段 `levelProjectionMatrixBuffer: PerspectiveProjectionMatrixBuffer`
  的 `getBuffer(本地7)` → `RenderSystem.setProjectionMatrix(slice, ProjectionType.PERSPECTIVE)`。
- 投影矩阵（本地 7）先经 `rotate / scale(1/zoom,1,1) / rotate` 后处理（即 fov 变焦写死在 X 轴缩放），
  再上传（字节码 269–329）。
- 视图矩阵（本地 14，字节码 345–376）：`new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()))`。
- 雾（字节码 378–440）：`fogRenderer.setupFog(camera, getEffectiveRenderDistance(), deltaTracker,
  getDarkenWorldAmount(partialTick), level)` → 本地 15（Vector4f）；`fogRenderer.getBuffer(FogMode.WORLD)` → 本地 16。
- 天空开关（字节码 445–501）：`arg10 = !bossOverlay.shouldCreateWorldFog()`。
- 实参压栈（字节码 460–502）：`minecraft.levelRenderer`、`resourcePool`（`CrossFrameResourcePool`，
  实现 `GraphicsResourceAllocator`）、`deltaTracker`、`blockOutline`、`mainCamera`、视图（本地14）、
  投影（本地7）、`getProjectionMatrixForCulling(partialTick)`、雾缓冲（本地16）、雾颜色（本地15）、天空开关。

**`GameRenderer.getProjectionMatrix(float)`（`javap -c` 全文）**

`new Matrix4f().perspective(fov * 0.017453292f, window.getWidth()/window.getHeight()(f), 0.05f, getDepthFar())`。
→ 近平面是**字面量 0.05f**（非字段），`getDepthFar()` 是 public 方法；fov 参数单位是度（内部乘 DEG2RAD）。

**`PerspectiveProjectionMatrixBuffer`（`javap -c -p`）**

- 字段 `buffer: GpuBuffer`、`bufferSlice: GpuBufferSlice`；构造 `(String)`；`getBuffer(Matrix4f)`；`close()`。
- `getBuffer` 内部：`Std140Builder.putMat4f` 打包 → `CommandEncoder.writeToBuffer` 上传，返回 `bufferSlice`
  —— 即 **getBuffer 即上传**，随后 `setProjectionMatrix` 即可，无额外上传步骤。

**`CachedPerspectiveProjectionMatrixBuffer`（`javap -p`）**

构造 `(String, float, float)`、`getBuffer(int, int, float)`、私有 `createProjectionMatrix(int, int, float)`。
（缓存版，按 width/height/fov 缓存；本移植暂用非缓存版 `PerspectiveProjectionMatrixBuffer`。）

**`LevelRenderer`（`javap -p` 方法清单 + 局部 `-c`）**

- `public void renderLevel(GraphicsResourceAllocator, DeltaTracker, boolean, Camera, Matrix4f, Matrix4f, Matrix4f, GpuBufferSlice, Vector4f, boolean)`（10 参，已确认）。
- `private Frustum prepareCullFrustum(Matrix4f, Matrix4f, Vec3)` —— 接收**两个**矩阵；
  `private void addMainPass(FrameGraphBuilder, Frustum, Matrix4f, GpuBufferSlice, boolean, LevelRenderState, DeltaTracker, ProfilerFiller)` —— 只接收**一个** Matrix4f；
  `addSkyPass(FrameGraphBuilder, Camera, GpuBufferSlice)`、`addLateDebugPass(FrameGraphBuilder, CameraRenderState, GpuBufferSlice, Matrix4f)`、
  `addWeatherPass/addCloudsPass/addParticlesPass`、`method_62214(GpuBufferSlice, LevelRenderState, ProfilerFiller, Matrix4f, ResourceHandle, ResourceHandle, boolean, ResourceHandle, ResourceHandle)` 等。
- ⚠️ 三个 `Matrix4f`（view/projection/cull）在 renderLevel 内各自的消费点，**本次未逐条 dump**
  （只 dump 了方法清单 + 少量关键方法）；本移植不依赖该细节（B1 直接把宽/窄投影与宽视锥原样传入，
  交给 vanilla 自己分配）。

**override 字段唯一写入者（`javap -c -p LevelRenderer` 全文 grep `putstatic outputColor/DepthTextureOverride`）**

仅命中 `private void method_75413(GpuBufferSlice, ResourceHandle<RenderTarget>, CameraRenderState, Matrix4f)`：
`setShaderFog` → 用 param2 的 `getColorTextureView()/getDepthTextureView()` 写两个 override →
若有 gizmo 则清主目标深度并渲染 → 两 override 置 null。`renderLevel` 本体不写这两个字段
⇒ 不能靠 override 字段重定向世界渲染（B2 需改 `LevelTargetBundle`）。

**`RenderSystem` override 字段（`javap -p`）**

`public static com.mojang.blaze3d.textures.GpuTextureView outputColorTextureOverride;`、`outputDepthTextureOverride;`。

**`LevelTargetBundle`（`javap -c -p` 全文）**

- public 字段 7 个 `ResourceHandle<RenderTarget>`：`main`（构造=`ResourceHandle.invalid()`）、
  `translucent/itemEntity/particles/weather/clouds/entityOutline`（默认 null）。
- `replace(Identifier, ResourceHandle)` 按 7 个 id 逐一 putfield，未知 id 抛 `IllegalArgumentException`；
  `get(Identifier)` 反查（未命中返 null）；`clear()`：main=invalid、其余 null。
- static id 常量：`MAIN_TARGET_ID`(=PostChain.MAIN_TARGET_ID)、`TRANSLUCENT_TARGET_ID`("translucent")、
  `ITEM_ENTITY_TARGET_ID`("item_entity")、`PARTICLES_TARGET_ID`("particles")、`WEATHER_TARGET_ID`("weather")、
  `CLOUDS_TARGET_ID`("clouds")、`ENTITY_OUTLINE_TARGET_ID`("entity_outline")；
  `MAIN_TARGETS`={main}、`OUTLINE_TARGETS`={main,entity_outline}、
  `SORTING_TARGETS`={main,translucent,item_entity,particles,weather,clouds}。

**本分支可直接复用的既有 API**

- `MathUtil.magnificationToFov(mag, fov)` / `fovToMagnification`（`util/math/MathUtil.java`）。
- `Minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)`（`ScopePipRenderState` 已在用）。
- `Minecraft.getMainRenderTarget()`、`gameRenderer.getDepthFar()`、`levelRenderer`/`level`/`player` 公开字段。

---

## 4. 分阶段实施计划（每步一个编译检查点）

> 编译环境：本地无 JDK，用 GitHub Actions `compile-check` workflow 作编译闭环（见 §0）。
> 当前进度：**B0/B1/B2/B3/B4 已完成且 `compileJava` 绿**（已 commit+push）；
> 分辨率缩放（离屏重定向）与 Iris/其它 mod 兼容后置为 B5。

### B0 · 补签名 —— ✅ 完成
CI 挂 `dumpRenderApi`（`finalizedBy compileJava`）用 `javap` 把 §3 全部「待确认」落成 §3.1 事实表；
诊断 task 已从 `build.gradle` 移除。

### B1 · 离屏 target —— ✅ 完成（方案变更：拷主目标，不建离屏 FBO）
26.2 的 `ScopePipTarget`（`TextureTarget` 离屏 FBO）未移植。改为把窄 FOV 那遍画进**主目标**后
立刻 `captureSceneFromMain` 拷走，随后 vanilla 那遍重画覆盖 —— 与 26.2 光影路径同款思路，
避开 1.21.11 FrameGraph 输出重定向。代价：镜内那遍仍是主目标全分辨率，`resolutionScale` 暂不生效。

### B2 · 配置 + Cloth + 语言 —— ✅ 完成
`RenderConfig` 的 `SCOPE_PIP_RERENDER`（默认关）/`SCOPE_PIP_RESOLUTION_SCALE`（0.25~1.0，默认 0.75）
已接入；lang 4 键已补；与 B3/B4 同批合入。

### B3 · `renderScopeView` 核心 —— ✅ 完成（vanilla-only，编译绿，运行未验证）
- 注入点：`GameRendererMixin` 新增 `@Redirect`，目标 `LevelRenderer.renderLevel` 的 10 参描述符
  （§3.1 实锤），先 `ScopePipRerender.renderScopeView(...)` 再原样调用。
- 窄投影：从宽投影 `m11` 反解基准 FOV → `MathUtil.magnificationToFov` 求窄 FOV →
  `PerspectiveProjectionMatrixBuffer.getBuffer` + `RenderSystem.setProjectionMatrix(slice, PERSPECTIVE)`。
- 结束还原：手工存档/恢复投影（`getProjectionMatrixBuffer()/getProjectionType()`）；异常 → `failed=true` 回退。
- 只做 vanilla；Iris 下 `isUsingRenderPack()` 直接跳过（沿用现有屏幕空间路径）。

### B4 · 合成接线 —— ✅ 完成
`ScopePipRenderState.captureScene` 在 rerender 下早期跳过、公开 `captureSceneFromMain`；
`compositeZoom()`=1（镜内已是窄 FOV 真画）；`worldZoomTarget()` 在 rerender 下恒 1×；Iris 最终合成仍用 `lensZoom()`。

### B5 · 分辨率缩放（离屏重定向）+ Iris/Sodium/Voxy/PhysicsMod 兼容 —— ⏳ 后置
- `ScopePipResolutionScale` 真正降采样需把镜内那遍画进**低分辨率离屏 target**
  （`LevelTargetBundle.replace` + `ResourceHandle`，结构见 §3.1），属下一个编译检查点。
- Iris/其它 mod 兼容按 §2.3 逐项重新审计（26.2 审计结果不可直接套用）。

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

## 7. 验证矩阵

- [x] `compileJava` 通过（CI `compile-check`，`BUILD SUCCESSFUL`）。
- [ ] `./gradlew build`（完整构建/产物）—— 未执行。
- [ ] **运行期全部未执行**（本沙箱无法启动游戏客户端），下列条目待客户端环境逐项验证：
- [ ] 默认配置（rerender 关）：画面与现状逐帧一致，无新增日志。
- [ ] rerender 开 + vanilla：镜内原生分辨率、镜外 1×，6×/8× 镜下明显更清晰；
      进出开镜无 POV 跳变；准星/遮光罩仍在镜片上层。
- [ ] `ScopePipResolutionScale` 0.25/0.5/0.75/1.0 生效（镜内画质/帧率随档位变化）。
- [ ] 异常注入（如切视距时开镜）→ `failed=true` 回退整屏变焦，不崩。
- [ ] Iris 下：rerender 开关不生效、走现有屏幕空间路径、tooltip 与行为一致。
- [ ] Sodium / Voxy / PhysicsMod 在场：标注「未验证」，默认关。

---

## 8. 下一步

1. **B5 分辨率缩放真正生效**：把镜内那遍画进低分辨率离屏 target（`LevelTargetBundle.replace` +
   `ResourceHandle`），`ScopePipResolutionScale` 才真正降采样。需要新的 `javap` 探针
   （`LevelRenderer.renderLevel` 内部如何把 `targets.main` 交给 FrameGraph、`ResourceHandle` 的构造/写回 API），
   再开一个编译检查点。
2. **运行期验证**：需一个能启动游戏客户端的环境（当前 CI 只能编译）。重点验证：
   默认关=零回归；开启后镜内/镜外 FOV、POV 跳变、实体消失、进出开镜无闪烁；异常回退整屏变焦。
3. 语言文件修复（`assets/tacz/lang/{en_us,zh_cn}.json`）已在本任务之前完成，随分支推进，无需再动。
