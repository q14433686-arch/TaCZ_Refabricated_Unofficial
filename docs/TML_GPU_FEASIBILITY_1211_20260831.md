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
| `RenderSystem.bindDefaultUniforms(pass)` + `getDynamicUniforms().writeTransform(model, WHITE)` | `bindDefaultUniforms(pass)` 同；`writeTransform` 是**四参**：`writeTransform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMat)`（26.2 两参版不存在；第三参是 modelOffset 不是 lightDir，手部传 `(0,0,0)`，第四参传单位阵） |

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
- [ ] 光影下 `assignPipeline(HAND)` PoC —— 未开始（真难点，第 2 步）。
- [ ] 全部运行期行为 —— 未验证（本沙箱无客户端，实机清单见 MESH_LOADER.md §5）。

### 6.1 第 1 步的注入点（与 26.2 不同，已按 1.21.11 实况重定位）

26.2 把 `renderAfterSolid()` 挂在 `FeatureRenderDispatcher.renderAllFeatures` 的
`executeSolid` 之后（1.21.11 无 executeSolid 拆分）。1.21.11 改为：

- `PolyMeshGpuRenderer.beginFrame()` → `GameRenderer#render` HEAD（帧首清表 + 光影开关翻转 bump 世代号）。
- `setInHandPass(true/false)` → `GameRenderer#renderItemInHand` HEAD/RETURN（`shouldSubmitGpu` 只认这个门，不认 `transformType.firstPerson()` —— 后者对「第一人称上下文画 GUI」也返回 true，正是关 PR 世界 pass 泄漏的入口）。
- `renderAfterSolid()`（真正 drawList）→ `renderItemInHand` RETURN：此时手部投影已设、深度已清、手部立方体还没 flush（deferred collector），MV 栈已 pop 回 `V`（视矩阵）。GPU 骨骼在此画，opaque + 深度写，与稍后 flush 的手部立方体/translucent 骨骼由深度缓冲自洽排序。

`handModelView = getModelViewMatrix()`（RETURN 时 = 视矩阵 V），每骨骼 `mv = V × pose_bone`；
`pose_bone` 含 `invert(V)`，相乘消掉 V —— 与 collector「顶点烘 pose、MV 当刻」的等价性、
以及 26.2 「朝向恒北」修复的矩阵语义逐位一致。
