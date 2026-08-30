# TML 内置 GPU 路径 —— 1.21.11 可行性核实（2026-08-31）

> 问题：`SYNC_GUIDE_REFAB_1211_20260830.md` §1.2 判「GPU 烘焙层不可搬，硬依赖 26.2 的
> `RenderPipeline.builder` + `BindGroupLayouts`，1.21.11 需整体重设计」。本文用 CI `javap`
> 实读 1.21.11 的 API 面重新裁决：**该结论对 1.21.11 不成立 —— GPU 烘焙层是无光影路径上的
> 机械级移植，不是跨纪元重写。**
>
> 判据全部来自 `javap -p`（commit `6990a14` 的 `build-reports/compile-java.log`，诊断 task 已移除）。
> 按 AGENTS.md §2：本文只列已核实签名，未实机的运行期行为（尤其光影下）明确标注「未验证」。

---

## 0. 结论（TL;DR）

1. **无光影下的 GPU 烘焙不难。** 1.21.11 具备 26.2 `PolyMeshGpuRenderer` 所需的**全部**运行时能力，
   缺失的只是几个**命名/形态差异**（见 §3 映射表），照表改即可，不是重设计。
2. **上游 TML（VellEagle/TacZMeshLoader `1.21.1_fabric` v0.1.7）才是 1.21.11 的正主参考**——
   它本来就是给 `TaCZ: Refabricated` 的 1.21.1 Fabric 写的，架构（逐骨骼静态 VBO 烘焙 +
   光照分档缓存 + 半透明拆分 + Screen/Shader tracker）与 26.2 的 PoC **收敛到同一设计**，
   且顶点格式用 `DefaultVertexFormat.NEW_ENTITY`（正是 1.21.11 的名字，26.2 已改名 `ENTITY`）。
3. **真正难的部分只有一个：光影（Iris）下的 GPU 照明**。自定义 `RenderPass` 会绕开 Iris 的
   `gbuffers_hand`，枪身收不到光影光照（26.2 PoC 因此在光影下回退 collector）。这不是「能不能
   画」的问题，是「画出来对不对光」的问题，需要 `assignPipeline(HAND)` 路线 + 实机验证。
4. **内置的公平性**：上游 TML 就是 TACZ 生态里给 1.21.1 Fabric 用的公开第三方 mod（GPL-3.0，
   与本源兼容），内置它是「补齐枪包生态缺口」而非「夹带私货」。§5 给出建议的内置范围。

---

## 1. 两个参考实现

| | 上游 TML `1.21.1_fabric` | 26.2 `PolyMeshGpuRenderer`（`8191f6b`） |
|---|---|---|
| 宿主 | `TaCZ: Refabricated`（Fabric 1.21.1） | 26.2 分支 |
| GPU 机制 | 逐骨骼静态 `VertexBuffer` 烘焙，按光照等级缓存（≤8 档 LRU），`drawWithShader(modelView, projection, shader)` | 逐骨骼静态 `GpuBuffer` 烘焙，光照 4 级量化 + 1s 节流，`RenderPass.drawIndexed` |
| 顶点格式 | `DefaultVertexFormat.NEW_ENTITY` | `DefaultVertexFormat.ENTITY`（26.2 改名） |
| 半透明 | `translucent` 骨骼名 → `entityTranslucentCull` 单独 pass | 同思路（cutout/translucent 拆分） |
| 光影处理 | `ShaderStateTracker`：Iris 开关切换时 `invalidateVboCache()`；`ScreenRenderTracker`：区分 GUI 内嵌 3D | `shouldSubmitGpu()`：光影下回退 collector（无光照） |
| 可直接照搬进 1.21.11 | 架构 + 顶点格式名 + 光照缓存思路 + tracker（是） | draw 代码 + 管线配方（是，经 §3 改名） |

**两者互补**：上游给「1.21.x 该长什么样」的架构与命名，26.2 给「GpuBuffer/RenderPass 那套
draw 调用该怎么写」。1.21.11 恰好同时具备两者所需。

---

## 2. 1.21.11 实读的 API 面（`javap -p`）

### 2.1 存在且够用（无光影 GPU 的硬前提全部满足）

| 类 | 关键成员 | 备注 |
|---|---|---|
| `com.mojang.blaze3d.systems.RenderPass` | `setVertexBuffer(int, GpuBuffer)`、`setIndexBuffer(GpuBuffer, VertexFormat$IndexType)`、`drawIndexed(int,int,int,int)`、`draw(int,int)`、`setPipeline(RenderPipeline)`、`bindTexture(String, GpuTextureView, GpuSampler)`、`setUniform(String, GpuBuffer/GpuBufferSlice)` | 26.2 drawList 用到的每个调用都在 |
| `com.mojang.blaze3d.pipeline.RenderPipeline$Builder` | `withVertexShader`、`withFragmentShader`、`withShaderDefine`、`withSampler(String)`、`withUniform`、`withVertexFormat(VertexFormat, Mode)`、`withCull`、`withBlend`/`withoutBlend`、`withColorWrite`、`withDepthWrite`、`withDepthTestFunction(DepthTestFunction)`、`withPolygonMode`、`withDepthBias`、`buildSnippet`、`build` | 用 `withSampler`/`withVertexFormat`/`withColorWrite`/`withDepthWrite`/`withDepthTestFunction` 替代 26.2 的 `withBindGroupLayout`/`withVertexBinding`/`withColorTargetState`/`withDepthStencilState` |
| `com.mojang.blaze3d.systems.GpuDevice` | `createBuffer(Supplier<String>, int, ByteBuffer)`、`createBuffer(Supplier<String>, int, long)`、`createCommandEncoder` | 烘焙顶点缓冲的入口 |
| `com.mojang.blaze3d.buffers.GpuBuffer` | `USAGE_VERTEX`、`USAGE_INDEX`、`USAGE_UNIFORM`、`slice()`、`slice(long,long)` | |
| `com.mojang.blaze3d.vertex.MeshData` | `vertexBuffer()`、`indexBuffer()`、`drawState()`、`sortQuads` | 与 26.2 同名同形 |
| `com.mojang.blaze3d.vertex.Tesselator` | `begin(Mode, VertexFormat)`、`getInstance()`、`clear()` | 立即模式烘焙仍在 |
| `com.mojang.blaze3d.vertex.ByteBufferBuilder` | `(int)`、`reserve(int)`、`build()`、`close()` | |
| `com.mojang.blaze3d.vertex.DefaultVertexFormat` | `NEW_ENTITY`、`BLOCK`、`EMPTY`、…（**无 `ENTITY`**） | 用 `NEW_ENTITY`（与上游一致） |
| `net.minecraft.client.renderer.RenderPipelines` | `MATRICES_FOG_SNIPPET`、`ENTITY_SNIPPET`、`ENTITY_EMISSIVE_SNIPPET`、`ENTITY_CUTOUT_NO_CULL`、`ENTITY_TRANSLUCENT`、`register(...)` | 管线底子与注册入口 |
| `RenderSystem`（过滤） | `getDynamicUniforms()`、`bindDefaultUniforms(RenderPass)`、`getSequentialBuffer(Mode)`、`getDevice()`、`getModelViewMatrix()`、`getModelViewStack()`、`getProjectionMatrixBuffer()` | `getShader()`/`getProjectionMatrix()` 已不在（见 §2.2） |

### 2.2 确认不存在（= 26.2/上游各自的过时代码，不是缺口）

| 类/成员 | 谁在用 | 1.21.11 结论 |
|---|---|---|
| `com.mojang.blaze3d.vertex.VertexBuffer`（含 `drawWithShader`） | 上游 TML | **MISSING** —— 上游的直接照搬路径断掉，但其**架构**照搬无碍 |
| `net.minecraft.client.renderer.BindGroupLayouts` | 26.2 | **MISSING** —— 用 `RenderPipeline.Builder.withSampler` 替代 |
| `RenderPipeline.Builder.withVertexBinding` / `.withPrimitiveTopology` / `.withColorTargetState` / `.withDepthStencilState` | 26.2 | 无 —— 用 `withVertexFormat(fmt, mode)` / `withColorWrite` / `withDepthWrite` / `withDepthTestFunction` 替代 |
| `DefaultVertexFormat.ENTITY` | 26.2 | 无 —— 用 `NEW_ENTITY` |
| `RenderSystem.getShader()` / `RenderSystem.getProjectionMatrix()` | 上游 TML | 无 —— 投影改为 `GpuBufferSlice`，shader 改为 `RenderPipeline` |
| `RenderType.entityCutoutNoCull / entityTranslucentCull / setupRenderState / clearRenderState` | 上游 TML | `javap` 未命中 —— 由 `RenderPipelines.ENTITY_CUTOUT_NO_CULL / ENTITY_TRANSLUCENT` 管线取代（collector 路径接线时以 1.21.11 实际 RenderType 为准） |

---

## 3. 26.2 → 1.21.11 的机械改名映射（draw 代码可直接照此改）

| 26.2 `PolyMeshGpuRenderer` | 1.21.11 等价 |
|---|---|
| `RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)` | 同（`MATRICES_FOG_SNIPPET` 存在） |
| `.withBindGroupLayout(BindGroupLayouts.SAMPLER0)` / `SAMPLER2` | `.withSampler("Sampler0")` / `.withSampler("Sampler2")`（注意：`withSampler` 声明的是**管线采样点**，`bindTexture("Sampler0", …)` 按名绑定，与 26.2 的 `pass.bindTexture("Sampler0", …)` 一致） |
| `.withVertexBinding(0, DefaultVertexFormat.ENTITY)` | `.withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)` |
| `.withPrimitiveTopology(PrimitiveTopology.QUADS)` | 并入上面 `withVertexFormat` 的 `Mode.QUADS` |
| `.withDepthStencilState(DepthStencilState.DEFAULT)` | `.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(true)`（等价值） |
| `.withColorTargetState(ColorTargetState.DEFAULT)` | `.withColorWrite(true)` |
| `RenderSystem.getDevice().createBuffer(name, GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer())` | 同（`createBuffer(Supplier<String>, int, ByteBuffer)` 存在） |
| `pass.setVertexBuffer(0, bone.vertexBuffer.slice())` | `pass.setVertexBuffer(0, bone.vertexBuffer)` —— 1.21.11 的 `setVertexBuffer(int, GpuBuffer)` 收整块 `GpuBuffer`，**不要** `.slice()`（`.slice()` 返回 `GpuBufferSlice`，签名不符） |
| `RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)` → `pass.setIndexBuffer(...)` → `pass.drawIndexed(...)` | `getSequentialBuffer(VertexFormat.Mode.QUADS)` 存在；`PrimitiveTopology` 换 `VertexFormat.Mode.QUADS`。**`drawIndexed` 参数序不同（见下）** |
| `RenderSystem.bindDefaultUniforms(pass)` + `getDynamicUniforms().writeTransform(model, WHITE)` | `bindDefaultUniforms(pass)` 同；`writeTransform` 是**四参**：`writeTransform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMat)`（26.2 两参版不存在；第三参是 modelOffset 不是 lightDir，手部传 `(0,0,0)`，第四参传单位阵）。**必须在 open render pass 之前调用**：`writeTransform` 会 map DynamicTransforms UBO，1.21.11 的 `mapBuffer` 在 pass 打开时抛 `Close the existing render pass before performing additional commands`（26.2 允许 pass 内写、1.21.11 不允许）——同理顺序索引缓冲 `AutoStorageIndexBuffer.getBuffer(n)` 首次/扩容时也 map，须在 pass 外预热 |

> **`drawIndexed` 参数序（1.21.11 实核，与 26.2 不同）**：`drawIndexed(int baseVertex, int firstIndex, int count, int instanceCount)`
> —— 依据 yarn 1.21.11 `GlCommandEncoder#drawBoundObjectWithRenderPass(baseVertex, firstIndex, count, indexType, instanceCount)` 逐参对齐。
> 顺序索引缓冲 0..count-1 时单实例调用即 `drawIndexed(0, 0, indexCount, 1)`。26.2 的
> `drawIndexed(indexCount, 1, 0, 0, 0)`（五参）不存在，机械移植必须改写。
> `createRenderPass` 用五参重载：`createRenderPass(labelGetter, colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())`（不清色不清深度，深度附着主深度缓冲）。

---

## 4. 真正难的、以及为什么难

**难的不是 GPU 绘制，是「光影下 GPU 绘制还收得到光照」。** 分三层：

- **无光影（vanilla）**：`RenderPass` + `RenderPipelines.MATRICES_FOG_SNIPPET` + `ENTITY` 管线，
  `core/entity` 着色器 + `ALPHA_CUTOUT/NO_OVERLAY/NO_CARDINAL_LIGHTING`。这是 26.2 PoC 已经
  写好的配方，§3 改名后即可落地。**风险点只有「着色器/贴图/lightmap 在 1.21.11 的绑定细节」，
  属编译+实机可闭环的工程量，不是设计难度。**
- **光影（Iris）**：自建 `RenderPass` 不经过 Iris 的 `gbuffers_hand`，枪身没有光影照明
  （26.2 因此回退 collector，导致高模枪光影下每帧 O(36 万) CPU 变换）。出路是 TML_PERF 文档
  「方向 1」：`IrisCompat.assignPipeline(RenderPipeline, IrisProgram.HAND)` 把 mesh 管线登记进
  HAND program（本仓已有 `assignScopePipelineToHand` 的实机 PASS 先例）。**这是唯一需要
  重新设计 + 实机验证的部分，且依赖 Iris 版本逐字节审计。**
- **兜底**：collector 路径（`submitCustomGeometry`）永远保留作回退，光影路线失败不劣化。

---

## 5. 建议的内置范围（公平性 + 分阶段）

1. **第 0 步（已论证可做，安全）**：把 26.2 的「安全子集」（collector + 解析缓存 + 预算闸门 +
   弹匣补画，`8c6ad27` 的架构）按 1.21.11 的 `submitCustomGeometry` 落地的同时，**同步把上游
   `1.21.1_fabric` 的 `ScreenRenderTracker` / `ShaderStateTracker` / 半透明拆分带进来**——
   这些是纯 CPU/状态追踪，无 GPU 赌注，却是 GPU 路径的必备基建。
2. **第 1 步（机械级，可独立验证）**：无光影 GPU 烘焙（26.2 `PolyMeshGpuRenderer` §3 改名），
   验收 = 无光影下 36 万顶点枪 spark 热点消失、`GPU mesh pass drew N bones` 日志出现。
3. **第 2 步（真难点，单独立项）**：光影下 `assignPipeline(HAND)` PoC（一根骨、一个三角形、
   挂 HAND program 看是否被 gbuffers_hand 照明），过了再谈全量。
4. **不做的**：导入期焊接/量化/磁盘缓存（Tier 0 蒙皮那套）——等 1/2 步实机 PASS 再说。

内置的公平性主张：TML 是 TACZ 1.21.1 Fabric 生态**事实上的标准**（上游官方仓库、GPL-3.0、
`provides: ["taczmeshloader"]` 兼容声明），内置等价于「把枪包生态的既有缺口正式补齐」；
路由选择（无光影 GPU / 光影回退 / 预算闸门）与 26.2 及上游一致，无偏袒。

---

## 6. 验证状态（如实）

- [x] 1.21.11 API 面实读（本表 §2，`javap -p` + yarn 1.21.11 命名逐参对齐）。
- [x] 上游 TML `1.21.1_fabric` 源码全文核对（`PolyMesh`/`PolyMeshModel`/render 四件套）。
- [x] 26.2 `PolyMeshGpuRenderer` 源码全文核对。
- [x] **第 0 步落地**：collector 安全子集 + 预算闸门 + 弹匣补画 + Screen/ShaderStateTracker + 半透明拆分（`de9b285` → `bc047a7`，CI 绿）。
- [x] **第 1 步落地（编译绿，运行期待实机）**：无光影 GPU 静态烘焙（`PolyMeshGpuRenderer` + `TaczPolyMeshGunModel.ensureBaked` + `GameRendererMixin` 挂点 + 配置/语言键），提交 `1c0193b`（CI `33336848343` success）。
- [x] **实机首测定位并修复运行时崩溃（待复测）**：烘焙/提交正常，但 `drawList` 在 open render pass 内调 `writeTransform` 抛 `IllegalStateException: Close the existing render pass before performing additional commands`（`GpuBuffer.mapBuffer`）。修复 = 全部 `writeTransform` 提到开 pass 之前、顺序索引缓冲在 pass 外预热到本帧最大 indexCount。**修复只过了编译，尚未实机复测。**
- [x] **「相对人物世界位置恒定 / 朝向恒北」老 bug 修复（编译绿，待实机）**：矩阵语义修正见 §6.1。`mv` 改在 submit 当刻捕获的 `Bᵀ` 上乘 `pose_submit`（原代码在 RETURN 现取 `getModelViewMatrix()`，已非 Bᵀ）。提交 `ca08b1c`，CI 编译绿；运行期朝向/位置待实机确认。
- [x] **第 2 步 PoC 脚手架落地（编译绿，运行期待实机）**：`assignMeshPipelinesToHand()` 把 LIT/EMISSIVE 登记进 Iris HAND program；`shouldSubmitGpu()` 在光影下改认 `IrisCompat.isRenderingSolidHandPass()`；新增 `IrisMeshGpuPassMixin`（`HandRenderer#renderSolid` TAIL，require=0）补光影下的绘制点。全部由 `MeshGpuUnderShaders=true` 强开，默认光影下仍回退 collector。**核心假设（Iris 1.10.7 是否按 assignPipeline(HAND) 拦截自定义 RenderPass 并路由进 gbuffers_hand）尚未实机验证，见 §6.2。**
- [ ] 全部运行期行为 —— 未验证（本沙箱无客户端，实机清单见 MESH_LOADER.md §5）。

### 6.1 第 1 步的注入点（与 26.2 不同，已按 1.21.11 实况重定位）

26.2 把 `renderAfterSolid()` 挂在 `FeatureRenderDispatcher.renderAllFeatures` 的
`executeSolid` 之后（1.21.11 无 executeSolid 拆分）。1.21.11 改为：

- `PolyMeshGpuRenderer.beginFrame()` → `GameRenderer#render` HEAD（帧首清表 + 光影开关翻转 bump 世代号）。
- `setInHandPass(true/false)` → `GameRenderer#renderItemInHand` HEAD/RETURN（`shouldSubmitGpu` 只认这个门，不认 `transformType.firstPerson()` —— 后者对「第一人称上下文画 GUI」也返回 true，正是关 PR 世界 pass 泄漏的入口）。
- `renderAfterSolid()`（真正 drawList）→ `renderItemInHand` RETURN：此时手部投影已设、深度已清、手部立方体还没 flush（deferred collector），MV 栈已 pop 回 `V`（视矩阵）。GPU 骨骼在此画，opaque + 深度写，与稍后 flush 的手部立方体/translucent 骨骼由深度缓冲自洽排序。

矩阵语义（**实测修正**，本段最初写「RETURN 时 MV = 视矩阵 V」是错的，正是新 bug 的来源）：

- 1.21.11 手部 pass 的提交 pose（`entry.model()`）**预乘相机基座** `B = camera.rotation()`
  （view→world），即 `rawWorld = B · H · F · chain`（`H·F` = hurt/view bob 与骨骼链，见
  `FirstPersonRenderGunEvent.applyAnimationConstraintTransform` 的注释推导）。
- collector 路径把这份 pose 烘进顶点，flush 时 ModelViewMat = **手部 pass 当刻**的
  `getModelViewMatrix()` = `Bᵀ`（`screen = Bᵀ · world`），`Bᵀ·B = I` 相消。
- GPU 路径顶点留骨骼本地，须写 `mv = Bᵀ × pose_submit`；**Bᵀ 只能在 submit 当刻捕获**
  （`ScopeFinalOverlayState.captureHandTransform` 同一时点，reticle 路径已实测），
  `renderItemInHand` RETURN 时 ModelView 栈已被 vanilla 还原，现取是 I/V 之类的错矩阵。
  只写 `pose_submit` 或乘错矩阵，B 没被抵消 = 「相对人物世界位置恒定 / 朝向恒北」老 bug。

### 6.2 第 2 步 PoC：assignPipeline(HAND) 的机制、脚手架与待验证假设

**为什么 26.2 的现成答案搬不过来。** 26.2 光影路径（`drawListViaRenderType`）用
`RenderType.prepare()` + `PreparedRenderType.drawFromBuffer()`，把常驻 VBO 经 entityCutout
管线（已 `assignCommonEntityPipelinesToHandIfNeeded()` 归入 HAND）画出去，Iris 按管线拦截、
给枪体光影光照。**1.21.11 没有 `RenderType.prepare()` / `PreparedRenderType`**（§2.2），
所以 1.21.11 的光影路径没有现成的「用 HAND 管线画常驻 VBO」入口。

**本仓已证的先例。** scope reticle 是「自定义管线 → `IrisApi.assignPipeline(HAND)` → Iris
按管线路由进 HAND program」这条链路的**实机 PASS**：`ScopeRenderTypes` 里 `scope_viewmodel_cutout`
等管线都 `assignPipelineToIris(..., "HAND", ...)`，经 collector 提交后在光影下渲染正确。
也就是说「assignPipeline(HAND) 机制本身」在本仓已被 scope 证明；剩下唯一未验证的是
**「自定义 RenderPass（非 collector）画的 VBO 是否同样被 Iris 拦截」**。

**本次脚手架做的事（全部 `MeshGpuUnderShaders=true` 才生效，默认无变化）：**

1. `assignMeshPipelinesToHand()`：把 `LIT_PIPELINE`/`EMISSIVE_PIPELINE` 登记进 HAND（幂等、
   反射保护，无 Iris 时 no-op）。
2. `shouldSubmitGpu()`：光影下改认 `IrisCompat.isRenderingSolidHandPass()`（vanilla 的
   `renderItemInHand` 被 Iris 绕过、`inHandPass` 恒 false）。
3. `IrisMeshGpuPassMixin`：`HandRenderer#renderSolid` TAIL 调 `renderAfterSolid()`，补光影下
   的绘制点（soft require=0，未装 Iris 不加载）。

**实机验证清单（第 2 步验收 = 下面第 1 条成立）：**

1. 开 Iris + 任意光影包 + `MeshGpuUnderShaders=true`，第一人称 mesh 枪：枪体是否出现？
   是否有 gbuffers_hand 光影照明（对照 collector 回退时的效果）？日志是否有
   `GPU mesh pass drew N bones`？
2. 若枪体消失/无光照：先看日志是否 `GPU mesh pass failed; falling back to collector` ——
   若是，说明 TAIL 时已不在合法 render-pass 边界（`createRenderPass` 抛了），把注入点前移到
   `renderSolid` 内「vanilla 手部提交之后、Iris endBatch 之前」（按字节码定位，对齐 reticle
   mixin 在 `renderTranslucent` 的挂钩语义）。
3. 若枪体出现但无光照：说明 Iris 1.10.7 不拦截自定义 RenderPass，或拦截了但 TAIL 时 phase
   已离开 HAND_SOLID —— 此时结论就是「1.21.11 必须走 collector 提交（如 scope reticle），
   光影下零 CPU 的常驻 VBO 路线需要等 26.x 的 `drawFromBuffer` 等价物」，PoC 以失败收尾、
   光影下维持 collector 回退（不劣化）。
