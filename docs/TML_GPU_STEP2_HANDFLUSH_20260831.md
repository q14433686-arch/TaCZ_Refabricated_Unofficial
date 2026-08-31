# 第 2 步 v2 —— 把 mesh GPU pass 开进「手部 flush 之内」（2026-08-31）

> 接手说明：上一轮留下的待办原文是
> 「mixin 进 `FeatureRenderDispatcher.renderAllFeatures()`/`renderSolidFeatures()`，用 MixinExtras
> `@Local` 捕获那次 flush 的 `RenderPass pass`，在 vanilla 几何画完后把 `HAND_DRAWS` 画进同一
> pass（并把 LIT/EMISSIVE 归入 HAND）」（`TML_GPU_FEASIBILITY_1211_20260831.md` §6.2 末段）。
>
> 本轮把那条路线**做完审计后改道**了：`@Local` 方案的前提在 1.21.11 上不成立（§1），
> 而改道后的方案比它更简单、注入面更小。本文是实现依据，代码在
> `PolyMeshGpuRenderer` / `ItemInHandRendererMixin` / `IrisCompat`。
>
> 按 AGENTS.md §2：**本文没有任何「已实机修好」的表述**。运行期结论全部标注为待实机。

---

## 0. 结论（TL;DR）

1. **`@Local` 抓 flush pass 这条路在 1.21.11 是空的**：`FeatureRenderDispatcher#renderAllFeatures()`
   的字节码（CI `javap -c` 全文，103 行，见 §1.1）只有「取 `SubmitNodeCollection` → 逐个
   feature renderer 调用 → `submitNodeStorage.clear()`」，**没有任何 `RenderPass` 局部变量**，
   也没有 `renderSolidFeatures`/`renderTranslucentFeatures`（`NOT FOUND`）。`RenderPass` 是在更
   里面、每个批次各自的 `RenderType#draw(MeshData)` 里创建并关闭的（局部槽位 13）。
   抓不到 pass，也「没有同一 pass」可以塞进去。
2. **但根本不需要抓别人的 pass**：`RenderType#draw` 解析输出目标时先看
   `RenderSystem.outputColorTextureOverride` / `outputDepthTextureOverride`（§1.2），而 Iris
   在 1.21.11 不另开 RenderPass、只靠「当前绑定的 framebuffer + `MixinGlCommandEncoder` 拦掉
   vanilla 的 `glBindFramebuffer`」来接管输出。于是：**只要在世界渲染阶段内、按原版同款的
   目标解析规则自己开一个 pass，就自动落在 Iris 当刻绑定的 gbuffer 上。**
3. **注入点也换了**：1.21.11 的手部几何**不是**延迟到 `renderLevel` 末尾统一 flush，而是
   `ItemInHandRenderer#renderHandsWithItems` 自己以 `renderAllFeatures()` + `endBatch()` 收尾
   （Iris 正是 hook 这两个调用接管手部绘制，§1.3）。所以在**本方法自己的 RETURN** 画 GPU 骨骼，
   一次注入同时覆盖两条路：无光影 = 原版刚 flush 完；光影 = Iris 刚 `endRender()` flush 完、
   仍在 `HAND_SOLID` 阶段内。**不 mixin Iris 内部类，不 patch `RenderType#draw` 全局热点。**
   （该结构已由 CI 上 1.21.11 的真实字节码逐条核实：`renderHandsWithItems` 共 143 行、
   **只有 1 个 `return`**、尾部正是 `renderAllFeatures()` + `endBatch()` —— 见 §3 第 1 条。）
4. 光影下再配 `IrisApi.assignPipeline(pipeline, IrisProgram.HAND)`（scope reticle 已实机 PASS
   过的同一机制），让常驻 VBO 收 `gbuffers_hand` 照明。
5. 安全底线：`MeshGpuUnderShaders` **默认关**；三层门禁（配置 + Iris 1.10.x 版本审计 +
   钩子存活证明）任一不满足就回 collector，且**不可能出现**「collector 被跳过 + GPU 没画」
   的枪体消失（§2.4）。

---

## 1. 本轮新增的实读证据

### 1.1 `FeatureRenderDispatcher#renderAllFeatures()`（1.21.11，mojmap，`javap -c -p` 全文）

```
 0: getfield submitNodeStorage
 4: SubmitNodeStorage.getSubmitsPerOrder() -> Int2ObjectAVLTreeMap.values().iterator()
16: iterator 循环，每个 SubmitNodeCollection 依次：
     ShadowFeatureRenderer.render(coll, bufferSource)
     ModelFeatureRenderer.render(coll, bufferSource, outlineBufferSource, crumblingBufferSource)
     ModelPartFeatureRenderer.render(...) / Flame / NameTag / Text / Leash /
     ItemFeatureRenderer.render(...) / BlockFeatureRenderer.render(...)
     CustomFeatureRenderer.render(coll, bufferSource)      <-- TACZ mesh 的 collector 路径在这
     ParticleFeatureRenderer.render(coll)
202: submitNodeStorage.clear()
```

* 无 `RenderPass`、无 `MultiBufferSource#endBatch`、无 solid/translucent 拆分。
* `renderSolidFeatures` / `renderTranslucentFeatures`：`NOT FOUND`。
* `CustomFeatureRenderer.render`（同批 dump，45 行）也只是
  `bufferSource.getBuffer(renderType)` → `CustomGeometrySubmit.customGeometryRenderer().render(pose, consumer)`，
  即**只往 builder 里写顶点**，绘制在 `BufferSource#endBatch` 里。
* 结论：`@Local` 无对象可捕获，「画进同一 pass」在 1.21.11 没有落点。

### 1.2 `RenderType#draw(MeshData)`（同批 dump，227 行）—— pass 到底怎么选目标

```
184: getstatic RenderSystem.outputColorTextureOverride
196: RenderTarget.getColorTextureView()           // override 为 null 时才用
205: RenderTarget.useDepth                         // 只有 useDepth 才挂深度
211: getstatic RenderSystem.outputDepthTextureOverride
234/258: RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(label, colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())
274: pass.setPipeline(state.pipeline)
279: RenderSystem.getScissorStateForRenderTypeDraws() -> enabled 则 pass.enableScissor(...)
321: RenderSystem.bindDefaultUniforms(pass)
326: pass.setUniform("DynamicTransforms", slice)
341: pass.setVertexBuffer(0, vb)   434: setIndexBuffer  451: drawIndexed(0,0,indexCount,1)
463: pass.close()
```

`PolyMeshGpuRenderer.drawList` 现在**逐条复刻**这段：同款 override/useDepth 目标解析、
同款 scissor 处理、同款「pass 外 `writeTransform` → pass 内只 `setUniform`」的顺序
（1.21.11 不允许在开 pass 期间 map buffer，第 1 步已实测踩到）。

### 1.3 手部 flush 的真正位置（Iris 1.10.7 `1.21.11` 分支源码逐行）

`net.irisshaders.iris.mixin.MixinItemInHandRenderer`：

```java
@WrapWithCondition(method = "renderHandsWithItems",
    at = @At(value = "INVOKE", target = "...FeatureRenderDispatcher;renderAllFeatures()V"))
private boolean iris$wrapHand(FeatureRenderDispatcher instance) { return customRenderer == null; }

@WrapOperation(method = "renderHandsWithItems",
    at = @At(value = "INVOKE", target = "...MultiBufferSource$BufferSource;endBatch()V"))
private void iris$wrapHand2(...) { if (customRenderer == null) original.call(...); else customRenderer.endRender(); }
```

`HandRenderer`（同分支）：

```java
public void endRender() { featureRenderDispatcher.renderAllFeatures(); bufferSource.bufferSource().endBatch(); }

public void renderSolid(Matrix4fc modelMatrix, float tickDelta, Camera camera, GameRenderer gameRenderer, WorldRenderingPipeline pipeline) {
    if (!canRender(camera, gameRenderer) || !Iris.isPackInUseQuick()) { return; }
    RenderSystem.backupProjectionMatrix();
    ACTIVE = true;
    PoseStack poseStack = setupGlState(...);          // 投影 = 手部投影（Z 乘 DEPTH=0.125）
    pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);
    renderingSolid = true;
    RenderSystem.getModelViewStack().pushMatrix();
    RenderSystem.getModelViewStack().set(poseStack.last().pose());   // <-- ModelView 在手部阶段内不变
    gameRenderer.itemInHandRenderer.iris$renderHandsWithCustomRenderer(this, tickDelta, new PoseStack(),
            this.submitNodeCollector, player, packedLight);          // -> renderHandsWithItems -> endRender()
    ... 之后才 restoreProjectionMatrix() / popMatrix() / renderingSolid = false / setPhase(NONE) ...
}
```

`MixinLevelRenderer`：`renderSolid(...)` 由 `pipeline.beginHand()` 之后、`beginTranslucents()` 之前
调用（在世界主 pass 的 FramePass 里）；`MixinGameRenderer#iris$disableVanillaHandRendering`
在光影下把 `GameRenderer#renderItemInHand` 里的 `renderHandsWithItems` **掏空**。

**由此得出**：`ItemInHandRenderer#renderHandsWithItems` 的返回点，两种情形都恰好是
「刚 flush 完手部几何、手部 ModelView/Projection 还没被还原、（光影下）仍在 `HAND_SOLID`
阶段内」。一个注入点，两条路。

### 1.4 顶点格式：pass 消费的是 Iris 替换后的格式

`MixinRenderPipeline`（Iris）：

```java
@Inject(method = "getVertexFormat", at = @At("RETURN"), cancellable = true)
private void iris$change(CallbackInfoReturnable<VertexFormat> cir) {
    if (Iris.isPackInUseQuick() && ImmediateState.renderWithExtendedVertexFormat && ImmediateState.isRenderingLevel) {
        ... else if (vf == DefaultVertexFormat.NEW_ENTITY) cir.setReturnValue(IrisVertexFormats.ENTITY);
```

而 `MixinRenderPass#<init>` 又用 `pipeline.getVertexFormat()` 去设 `setVertexFormatFromProgramSet`。
即：**同一条管线的 `getVertexFormat()` 在有/无光影下是两个值**。所以烘焙端不能写死
`DefaultVertexFormat.NEW_ENTITY`，必须问 `LIT_PIPELINE.getVertexFormat()`（`bakeFormat()`），
并把格式记进 `BakedBone`；格式变了立即重烘（`ensureBaked` 比对 + 绘制端二次校验，
不一致就跳过当帧并 bump `bakeGeneration`，宁少一帧也不按错 stride 解读 buffer）。

`MixinGlCommandEncoder`（同分支，202 行）还解释了「为什么自己的 pass 能进 gbuffer」：

```java
@Redirect(method = "createRenderPass(...)", at = @At(value = "INVOKE",
          target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_glBindFramebuffer(II)V"))
private void changeFramebuffer(int i, int j) {
    if (ShadowRenderingState.areShadowsCurrentlyBeingRendered() || ImmediateState.safeToMultiply) {
        this.tempFBO = j;   return;           // 不让 vanilla 抢回主 framebuffer
    }
    GlStateManager._glBindFramebuffer(i, j);
}
@Inject(method = "trySetup", at = @At("HEAD"), cancellable = true)
private void iris$bypassSetup(GlRenderPass p, Collection<String> c, CallbackInfoReturnable<Boolean> cir) {
    DepthColorStorage.unlockDepthColor();
    if (ImmediateState.safeToMultiply && !(p.pipeline.program() instanceof ExtendedShader)) {
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, tempFBO);
    }
    ...
}
```

即：**在世界渲染阶段内新建的 pass，其 framebuffer 绑定由 Iris 代管**，这正是「常驻 VBO
进得了 `gbuffers_hand`」的机制。第 2 步 PoC 失败是因为绘制点开在 `renderItemInHand` RETURN
（世界帧图之外），与这套机制无关。

### 1.5 程序分配：`IrisApi.assignPipeline` 的真实语义

`IrisApiV0Impl`（`getMinorApiRevision() == 3`）→ `IrisPipelines.assignPipeline(pipeline,
ShaderKey.findBestMatch(pipeline, ProgramId.fromAPI(program)))` → `coreShaderMap.put(pipeline, p -> key)`
（**全局静态、重复登记抛 `IllegalStateException("Shader already assigned")`**，本仓
`IrisCompat.isAlreadyAssigned` 已吞这种情况）。`ShaderKey.HAND_CUTOUT` 的定义是
`(ProgramId.Hand, ONE_TENTH_ALPHA, IrisVertexFormats.ENTITY, PER_VERTEX, LIGHTMAP)`，
`findBestMatch` 的「perfect」分支要求管线带 `ALPHA_CUTOUT` 且
`pipeline.getVertexFormat() == key.vertexFormat` —— 我们的 `LIT_PIPELINE` 两条都满足
（§1.4 的替换恰好把它送到 `IrisVertexFormats.ENTITY`），即使只命中「decent」分支也是
`ProgramId.Hand` 的第一条键。

---

## 2. 实现（本轮改动）

| 位置 | 改动 |
|---|---|
| `PolyMeshGpuRenderer.renderAtHandFlush()` | 取代 `renderAfterSolid()`；由 `ItemInHandRendererMixin` 在 `renderHandsWithItems` 的每个 RETURN 调用；先记存活证明 `lastHandFlushFrame`，再按 `irisFlush != isUsingRenderPack()` 判定该不该画（两条路各只认自己那次 flush），末尾无条件清单 |
| `PolyMeshGpuRenderer.drawList()` | 目标解析与 `RenderType#draw` 同款（override 优先 + `useDepth`）；补 `getScissorStateForRenderTypeDraws()` → `enableScissor`；ModelView **取 flush 当刻现值**（不再用 submit 时刻偷拍的 `Bᵀ`，见 §2.3）；光影下 `IrisCompat.assignMeshPipelineToHand(pipeline)`；烘焙格式与 pass 格式二次校验 |
| `PolyMeshGpuRenderer.bakeFormat()` / `BakedBone.format` | 烘焙跟随 `LIT_PIPELINE.getVertexFormat()`（§1.4） |
| `shouldSubmitGpu()` | 无光影：`inHandPass`；光影：`IrisCompat.isRenderingSolidHandPass()`（= `isActive && isRenderingSolid`）；两者都额外要求 `handFlushAlive()`（§2.4）且光影下要 `MeshGpuUnderShaders` + `IrisCompat.supportsHandFlushHook()` |
| `GameRendererMixin` | **删掉** `renderItemInHand` RETURN 处的 `renderAfterSolid()` 调用（保留 `beginFrame()` 与 `setInHandPass()`） |
| `IrisCompat` | 新增 `supportsHandFlushHook()`（Iris 1.10.x 前缀审计门）与 `assignMeshPipelineToHand(RenderPipeline)`（复用既有 `assignPipelineToIris` 反射桥，不新增编译期依赖） |
| `MeshyConfig` / lang(en/zh) | `MeshGpuUnderShaders` 从「恒为 no-op 的诊断位」改成「实验性：光影下走常驻显存」，默认仍为 false |

### 2.1 为什么不在 `renderItemInHand` 的 RETURN 画

第 1 步（无光影）曾经画在那里，理由是「collector 延迟到 `renderLevel` 末尾 flush，所以此刻手部
立方体还没画」。**§1.3 证明这个前提是错的**：手部几何在 `renderHandsWithItems` 内部就 flush 了。
后果有两个，本轮一并解决：

* RETURN 时 ModelView 栈已被还原 → 必须在 submit 时刻偷拍 `Bᵀ` 带过去（第 1 步
  「相对人物世界位置恒定 / 朝向恒北」bug 的来源，偷拍只与 `ScopeFinalOverlayState.captureHandTransform`
  的时点约定一致，属于**巧合可用**而不是**由构造成立**）；
* 光影下那次 flush 发生在世界帧图里、RETURN 在帧图外 → 强开就是「枪画到主渲染目标、被
  composite 覆盖」= 第 2 步 PoC 的实测消失。

画在 flush 紧后，ModelView/Projection/目标覆写三样都是**刚被原版手部批次用过的那一份**，
两条路的正确性由构造保证。

### 2.2 与 26.2 的差异（不要照搬 26.2）

26.2 把 `renderAfterSolid()` 挂在 `renderAllFeatures` 的 `executeSolid` 之后，并靠
`RenderType.prepare()` + `PreparedRenderType.drawFromBuffer()` 把常驻 VBO 塞进当前 pass。
**1.21.11 两者皆无**（`TML_GPU_FEASIBILITY` §2.2 的 MISSING 表），因此本轮不追求「同一个 pass」，
而是**自己开一个 pass 但用同款目标解析**（§1.2/§1.4）。

### 2.3 collector 与 GPU 的代数等价（重述，别再改回去）

* collector：顶点 = `pose_submit · v`，flush 时 `ModelViewMat = getModelViewMatrix()`（记作 `M`）
  → `clip = P · M · pose · v`；
* GPU：顶点留骨骼本地，`mv = M · pose` → `clip = P · M · mv_local · v`，与上面同式。
  关键是 **`M` 必须是 flush 当刻那份**；`P` 同理（`bindDefaultUniforms` 现取）。

### 2.4 三层回退，杜绝「枪消失」

1. `MeshGpuUnderShaders` 默认 false；`MeshGpuBaking` 关掉则整体回 collector。
2. `IrisCompat.supportsHandFlushHook()`：非 1.10.x 直接不放行（只 `warn` 一次）。
3. **存活证明**：`lastHandFlushFrame` 必须是本帧或上一帧。mixin 因 `require=0` 静默失效、
   或该帧根本没走到那个 RETURN → 下一帧 `shouldSubmitGpu()` 立刻 false，回 collector。
   最坏代价是「一帧没吃到 GPU 加速」，不是丢几何。
4. 绘制抛 `Exception | LinkageError` → 本会话 `gpuDisabledThisSession = true` 并把
   `MeshGpuBaking` 写回 false（与第 1 步同语义）。

---

## 3. 静态审计结果（CI 已完成，2026-08-31）

沙箱无 JDK，所以 `build.gradle` 里的 TEMP task `dumpHandFlushApi` 把每一项都从 **CI 上真实的
1.21.11 + Iris 1.10.7 编译 classpath** 里 javap 出来，日志即 `build-reports/compile-java.log`。
两次 push（`ba3e7bd`、`0fdd481`）均 `BUILD SUCCESSFUL`，除本仓既有告警外**无 error、无新告警**。

- [x] **`ItemInHandRenderer#renderHandsWithItems` 的结构（本轮全部前提）**：
      `143 lines, 1 x 'return'`，尾部指令逐字为
      ```
      278: GameRenderer.getFeatureRenderDispatcher()
      281: FeatureRenderDispatcher.renderAllFeatures()V
      288: Minecraft.renderBuffers()
      291: RenderBuffers.bufferSource() -> MultiBufferSource$BufferSource
      294: MultiBufferSource$BufferSource.endBatch()V
      297: return
      ```
      ⇒ 那次 flush **就在方法末尾**，且**只有一个 return**。所以 `@At(value="RETURN")`
      恰好命中一次、且必在 flush 之后；不存在「提前 return 绕过钩子」的路径。
      （`@At("TAIL")` 在此等价，但 return 数一变就只覆盖最后一条，故选 RETURN。）
- [x] blaze3d 成员全部存在且签名与调用一致：
      `RenderPass`: `setPipeline(RenderPipeline)` / `bindTexture(String, GpuTextureView, GpuSampler)` /
      `setUniform(String, GpuBufferSlice)` / `enableScissor(IIII)` / `setVertexBuffer(int, GpuBuffer)` /
      `setIndexBuffer(GpuBuffer, VertexFormat$IndexType)` / `drawIndexed(IIII)` / `close()`；
      `ScissorState`: `enabled()` + `x()/y()/width()/height()`（record 风格访问器，无 getter 前缀）；
      `RenderTarget`: `public final boolean useDepth` + `getColorTextureView()/getDepthTextureView()`；
      `RenderSystem`: `outputColorTextureOverride` / `outputDepthTextureOverride`
      （均 `public static GpuTextureView`）、`getScissorStateForRenderTypeDraws()`、
      `bindDefaultUniforms(RenderPass)`、`getSequentialBuffer(Mode)`、`getModelViewMatrix()`、`getDevice()`。
- [x] 烘焙端：`BufferBuilder(ByteBufferBuilder, VertexFormat$Mode, VertexFormat)` 公开构造器存在，
      `addVertex(float,float,float)` 返回 `VertexConsumer` ⇒ `PolyMesh#writeRaw` 可以按任意格式写
      （第 1 步写死 `NEW_ENTITY`，本轮改成传入 `bakeFormat()` 的结果）。
- [x] **`RenderType` 的包名在 1.21.11 变了**：`net.minecraft.client.renderer.rendertype.RenderType`
      （第一版脚手架按老包名查，直接 `class not found`；已修）。`RenderType#draw(MeshData)`
      `227 lines, 1 x 'return'`，与本仓复刻的指令序一致。
- [x] Iris 侧：`pathways.HandRenderer` 有 `public void endRender()`、`public boolean isActive()`、
      `public boolean isRenderingSolid()`、`renderSolid(Matrix4fc, float, Camera, GameRenderer,
      WorldRenderingPipeline)`；`api.v0.IrisApi` 有 `assignPipeline(RenderPipeline, IrisProgram)` +
      `getMinorApiRevision()`；`IrisProgram.HAND` 存在（另有 `HAND_TRANSLUCENT`、`EMISSIVE_ENTITIES`，
      所以 `assignPipelineToIris(..., "HAND", ...)` 用的枚举名是对的）；
      `vertices.IrisVertexFormats.ENTITY`、`pipeline.programs.ShaderKey.HAND_CUTOUT{,_BRIGHT,_DIFFUSE}`、
      `pipeline.IrisPipelines.assignPipeline(RenderPipeline, ShaderKey)`、
      `vertices.ImmediateState.{isRenderingLevel, renderWithExtendedVertexFormat, safeToMultiply}` 全在。

- [ ] **仍未证实（只有实机能答）**：`VertexFormat` 里比 `NEW_ENTITY` 多的那些分量
      （`at_mid_block` / `at_tangent` 等）在 `BufferBuilder.addVertex` 写不到时是否按
      `VertexFormatElement` 的默认值补齐 —— javap 的 `-p` 过滤看不到填充逻辑，且这是**观感级**
      风险（最坏是切线/中点为 0，表现为法线贴图/光滑照明异常），不是崩溃级：
      格式与 stride 的一致性由 `BakedBone.format` 比对保证。

---

## 4. 明确不做

* 不 mixin Iris 内部类（`HandRenderer` / `GlCommandEncoder` / `RenderPass`）。
* 不 patch `RenderType#draw`（全局热点，且错误注入会拖垮所有光影用户）。
* 光影下的 translucent 骨骼仍走 collector（混合顺序交给 Iris）。
* 阴影 pass、弹匣补画、预算闸门、格式量化/焊接等第 0 步语义一律不动。
