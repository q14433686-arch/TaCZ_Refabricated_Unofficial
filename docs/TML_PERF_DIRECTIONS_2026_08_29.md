# 内置 TML 高模性能问题 —— 方向盘点（2026-08-29）

> 分支：`arena/01a04e96-tacz-refabricated-unofficial`
> 性质：**方向讨论稿，不含任何实现。** 所有「已核实」条目均给出出处；
> 未验证的推断明确标注。按 AGENTS.md §2：本文没有一句「已修复」。
>
> 背景：光影下 PIP 的帧率衰减泄漏已由 PR #82 修复合入（`SubmitNodeStorage`
> 清空被误拦截 → 精确限定为只保留主 storage）。剩下的大头是
> **TacZ Mesh Loader [TML] 内置后的高模渲染成本**（36 万顶点级 poly_mesh 枪，
> 典型样本 duyupack ak_enact，365,848 顶点，见 PR #70 正文的用户 log 实证）。

---

## 0. 现状盘点（全部为已核实事实）

### 0.1 主分支没有 TML 代码

`26.2(main)` 当前（`fcaa2b8`）**不含**任何 meshloader 源码
（`find src -path "*meshloader*"` 为空）。存在的只有上游 TACZ-Refabricated
自带的扩展点 `GunModelTypeManager` + `GunDisplay.modelType`，
外部 TML jar（Fabric 1.21.1）在 26.2 上不可用 —— 所以「内置」是唯一路线。

### 0.2 四次内置的结局与遗产

| PR | 结局 | 核心教训 |
|---|---|---|
| #33 | 关 | 画在世界 pass、乘不可信 `getModelViewMatrixCopy()`、pipeline 未声明深度状态被静默丢弃 |
| #69 | 关 | 光影一开整条回退 CPU（= 要解决的卡顿本体原样保留）；声称做了的烟雾光照代码没动（AGENTS.md §2 的反面教材） |
| #70 | 关 | 全局 `WORLD_DRAWS` 表把 GUI/掉落物/第三人称登记进世界 pass → 投影/贴图错乱；弹匣未接 `IMirrorGeometry` |
| #71/#72 | 关 | v4 架构上收敛（GPU 表只收第一人称手部 pass），但维护者要求**从干净基线重做**，不得复用关闭分支 |

v4（`pr/72` 分支，头提交 `7be8882`）已经写好的、经过前三轮教训修正的部分：

- GPU 静态烘焙**严格限于第一人称手部 pass**（判定用 `ScopeMaskRenderer.isInHandPass()`）；
- geo JSON 解析缓存（修复用户 log 实证的「每枪解析两遍」）；
- `MeshGuiMaxVertices` / `MeshWorldMaxVertices` 预算闸门；
- `additional_magazine` 走 `captureSubtree` 补画，弹匣不丢；
- collector 兜底路径的热循环去分配（矩阵系数提局部变量、零临时对象，见
  `PolyMesh#compile`）。

**v4 从未实机验证过**（#71 只反馈到「编译通过」），且按关闭意见不能整支复用。

### 0.3 没解决的问题到底是哪几块

按场景拆开（成本估算引自 RFC 稿 `optimize-high-poly-vertex-transformation.zip`
的 `src/data/content.ts`，为推算值非实测）：

| 场景 | 路径 | 每帧成本 | v4 是否已有方案 |
|---|---|---|---|
| 无光影 · 第一人称 | GPU 烘焙（Tier 1 逐骨骼 VBO） | O(骨骼) ≈ 微秒级 | ✅ 有（未验证） |
| **光影 · 第一人称** | **collector 回退 = O(36 万) CPU 变换 + 巨态虚调用** | **≈ 11 + 4 ms** | ❌ **没有 —— 这是核心缺口** |
| 光影 · 第一人称 · 开镜（PIP 二次渲染） | 上一行 ×2（见 §2.3 交互分析） | ≈ 30 ms | ❌ 没有 |
| Iris 双遍手部 pass（实心+半透明） | 每帧提交两遍 | ×2 | ✅ 主分支已修（`IrisHandPhaseSplitFix`，PR 前序工作，用户 A/B PASS）|
| GUI/JEI 图标、掉落物、展示框 | 预算闸门：超限只画立方体 | 一刀切 | ⚠️ 有，但体验粗糙（poly 直接消失）|
| 第三人称远处玩家 | display JSON 自带 `gunLod` 通道，本来就走低模 | 低 | ✅ 架构已有 |
| 加载期 | 解析缓存 | 一次性 | ✅ 有 |

结论：**「TML 内置的性能问题」收敛为一个主要缺口（光影下第一人称仍是每帧
O(顶点) CPU 变换）+ 一个交互放大器（PIP 开镜把它翻倍）+ 一组体验粗糙的兜底。**

---

## 1. 方向清单（按建议优先级）

### 方向 1【主攻建议】光影下的 Tier 1：自定义 pipeline 走 Iris `assignPipeline(HAND)`

v4 在光影下回退 collector 的理由是「自建 RenderPass 绕过光影管线，枪身收不到
光影照明」。但本仓**已经有一条被实机验证过的反例路径**：

- `IrisCompat#assignScopePipelineToHand` 用 Iris API v0 (revision ≥ 3) 的
  `assignPipeline(RenderPipeline, IrisProgram.HAND)` 把自建的
  `scope_body_clipped` / `scope_reticle_clipped` 等 6 条 pipeline 归入 HAND program；
- 用户 latest.log 实证 Iris 侧完成匹配：
  `Found perfect program match for tacz:pipeline/scope_body_clipped: HAND_CUTOUT`；
- 这条路径在 Complementary 系光影下已 PASS（瞄具镜身在光影下正常照明）。

**推论（未验证）**：给 mesh 枪身做一条
`tacz:pipeline/mesh_entity_hand`（`DefaultVertexFormat.ENTITY`，基于
`entity.vsh`），注册进 HAND program，然后：

- 顶点仍常驻 VBO、骨骼本地空间（v4 的烘焙层可整体复用思路）；
- 每骨骼一次 draw，`ModelViewMat = 手部 pose × 骨骼累计 pose`（标准 uniform，
  Iris patch 后的 gbuffers_hand 同样消费它）；
- CPU 成本 O(骨骼数)，**光影照明由 Iris 的 hand program 提供**，
  不再是 v4 实验开关那种「枪身收不到光影照明」的残次品。

风险与验证点（按顺序裁决）：

1. **绘制时机**：不能再开自己的 `RenderPass`（那就又绕开 Iris 了），必须让
   draw 发生在 Iris hand pass 的批次内。两条候选：
   a) 走 `SubmitNodeCollector.submitCustomGeometry` + 自定义 `RenderType`
      包该 pipeline —— 顶点还是每帧经 consumer 写（白干）；
   b) 学 vanilla 的 `CustomFeatureRenderer` 语义，在 feature 执行阶段
      `setVertexBuffer` 直接引用常驻 VBO —— 需要核对 26.2
      `SubmitNodeCollector`/`FeatureRenderDispatcher` 是否有能携带
      预建 `GpuBuffer` 的提交形态（**待字节码核对**，这是本方向的第一个
      必须回答的问题）。
2. **Iris 顶点属性**：Iris patch 的 entity 顶点着色器可能引用
   `at_tangent`/`mc_Entity` 等扩展属性；ENTITY 格式没有它们时 Iris 通常给
   默认值（瞄具管线就是这么活下来的），但 labPBR 法线贴图效果可能不完整 ——
   属于「视觉降级」而非「不可用」，需实机确认程度。
3. **回退语义**：assignPipeline 失败（旧 Iris、严格包）→ 退 collector，
   即现状，不劣化。

**先做一个最小 PoC 再定路线**：一根骨骼、一个三角形、挂 HAND program，
光影下看它是否被 gbuffers_hand 照明。PoC 通过才值得把 v4 的烘焙层搬过来。

### 方向 2 collector 兜底路径的常数因子（收益有限，明确上限）

光影路线失败/未覆盖的场景总要有兜底，值得做但**别指望它解决问题**：

- **姿态缓存**：骨骼矩阵 + light/overlay 的哈希不变（idle 时命中率高）→
  复用上一帧变换结果的 SoA 数组，省掉 RC-01 的每帧矩阵乘。
  已核对 26.2 `VertexConsumer` class：**没有 bulk 写入 API**（只有逐顶点
  `addVertex` 链），所以字节块级 memcpy 做不到，RC-02 的 ~4ms 虚调用
  省不掉。**收益上限 ≈ 2-3×，不是量级改善。**
- **三角形配对**：TML 把三角形展开成第 4 顶点重复的退化 quad
  （`PolyMesh` 构造器里 `poly.length == 3 → drawCount 4`），纯三角网格
  白付 33% 顶点。导入期把共面相邻三角形配对成真 quad 可拿回这部分。
- 顶点焊接/索引化对 consumer 路径无意义（非索引提交），不要在这条路上做。

### 方向 3 削放大倍数（低风险，与 1/2 正交，可先行）

1. **PIP × mesh 的交互放大（新问题，PR #82 之后才成立）**：
   - `ScopePipRenderer.shouldPreserveSubmits()` 在镜内二次渲染期间保留主
     storage 的提交节点 → 镜内那遍会**重放** `submitCustomGeometry` 的
     延迟回调，对 poly 枪 = 再跑一遍 O(36 万) 的 `PolyMeshSnapshot#write`；
   - 且 Iris 的 HandRenderer 是在 `LevelRenderer.render` 内部驱动的
     （latest.log 调用栈实证：`HandRenderer.renderSolid ← beginTranslucents`），
     隔离管线二次渲染时手部 pass 大概率再跑一遍。
   - **方向**：镜内那遍对 poly 层降级 —— 开镜时第一人称枪身几乎被镜筒/掩码
     遮挡，镜内世界渲染里的手部 poly 可以直接跳过或只画立方体。
     需要先实机核对「镜内那遍到底画不画手」。
2. **阴影 pass 跳过手部 poly**（v4 已有 `MeshPolyInShadow=false` 默认值，保留）。
3. **GUI/掉落物的体验升级**：现在超预算是「poly 整块消失」；
   如果枪包提供了 `gunLod`，GUI/掉落物可以先退 LOD 模型再退纯立方体，
   消失感会小很多——这条只用现有资源通道，不需要新格式。

### 方向 4 导入期烘焙（只为 GPU 路径服务，跟着方向 1 走）

RFC 稿 P1 阶段的内容：焊接+索引化（唯一顶点 36 万 → ~14 万）、
量化 36B→16B、`.tmesh` 内容哈希磁盘缓存、QEM 自动 LOD。
**前提是方向 1 的 GPU 路径成立**——这些优化全部作用于常驻 VBO 的构建，
对 collector 路径没有意义。不要倒序做。

### 方向 5【远期】Tier 0 GPU 蒙皮（骨骼调色板 + 自定义蒙皮着色器）

每实例每帧 3KB 骨骼矩阵 UBO，顶点着色器做刚体蒙皮，draw call 1-2 次。
是终局形态，但依赖：方向 1 的 Iris 接线结论 + 26.2 `RenderPass`/UBO 切片
的工程量 + Bedrock pivot/旋转序的逐顶点一致性测试。风险最高，放最后。

---

## 2. 建议的落地顺序（尊重 #72 的关闭意见）

1. **第 0 步：干净基线重新内置「安全子集」** —— 只带 collector 路径 +
   解析缓存 + 预算闸门 + 弹匣补画（v4 里已被三轮教训打磨过、不含 GPU
   赌注的部分，按关闭意见重新审计后以新代码落地）。这一步的验收是
   「mesh 枪包在 26.2 上**能用**且不劣化无 mesh 场景」，不承诺性能。
2. **第 1 步：无光影 GPU 路径**（v4 架构，重审后实现）——先把无光影场景的
   O(顶点) 归零并实机验证（`GPU mesh pass drew N bones` + spark 热点消失）。
3. **第 2 步：方向 1 的 PoC** → 依结果二选一：
   - PoC 通过 → 光影下同样走逐骨骼 VBO，核心缺口关闭；
   - PoC 失败 → 退方向 2 的姿态缓存 + 方向 3 的放大削减，并在 README
     如实写明「光影下高模枪包有固有帧率成本」。
4. **贯穿全程的度量**（RFC 稿 Verify 节）：
   `/spark profiler --thread "Render thread"` 固定场景采样；
   固定种子/坐标/视角的 600 帧 p50/p95 对比；每步一个可回滚开关。

## 3. 环境限制备忘

- 本沙箱无 JDK（`java: command not found`），任何实现都无法在沙箱内
  `./gradlew build`——历史上 #70/#71 均因此把编译错误漏到维护者本地。
  下次实现前值得先解决沙箱 JDK（或至少用 `.gradle/loom-cache` 里的
  `minecraft-merged-26.2.jar` 做字节码级 API 核对，v3 之后已在这么做）。
- 方向 1 的第一个硬问题（26.2 提交层能否携带预建 GpuBuffer）可以在写代码前
  用上述 jar 反编译核对 `SubmitNodeCollector` / `CustomFeatureRenderer` /
  `OrderedRenderCommandQueue` 的全部提交形态，直接裁决 a/b 两条候选。
