# Minecraft 26.1.2 / Fabric / Iris 瞄准镜渲染审计

> 审计日期：2026-07-30（Asia/Shanghai）<br>
> 目标版本：Minecraft 26.1.2、Java 25、Fabric Loader 0.19.3<br>
> 直接上游：`Sh1roCu/TACZ-Refabricated` 的 `1.21.1` 分支<br>
> Iris 对照：`IrisShaders/Iris` 的 `26.1` 分支（1.11.2、Minecraft 26.1.2）

## 1. 项目目标和问题定义

本仓库不是内容扩展，而是把 TACZ 的 Fabric 分支迁移到 Minecraft 26.1.2。当前渲染目标是：

1. 中高倍镜与组合镜开镜后，目镜投影区域成为透明镜内；
2. 镜身只画在目镜区域之外；
3. 分划板只画在目镜区域之内；
4. vanilla 和 Iris shader pack 两条调度路径行为一致；
5. 不能永久替换或破坏主画面 / Iris 的深度附件；兼容路径必须在每次 draw 后原样恢复；
6. 目标 FBO 不支持 stencil 时必须安全降级，不能留下整块黑色遮罩。

原始仓库状态还存在一个工程问题：`src/main/java` 和大部分运行资源没有入库，只有
`build/classes`、运行 JAR 和 sources JAR。增量构建会误用旧 class，看似成功；`clean build`
则无法得到可运行模组。本轮已从仓库内已有的 sources JAR 恢复正式 `src/main/java`；完整默认
枪包资源以不改内容的压缩 bundle 移到 `resources/`，由 `processResources` 展开，避免在 Git 中
重复存一份约 80 MiB 的资源树。后续验证必须以 `clean build` 为准，不能以旧
`build/classes` 为准。

## 2. 26.1.2 vanilla Feature Rendering 调度

### 2.1 提取阶段不是绘制阶段

第一人称主链路为：

```text
GameRenderer.renderItemInHand
  -> ItemInHandRenderer.renderHandsWithItems
     -> 枪械/配件模型向 SubmitNodeCollector 提交节点
     -> FeatureRenderDispatcher.renderAllFeatures
        -> CustomFeatureRenderer.renderSolid / renderTranslucent
           -> CustomGeometryRenderer.render(pose, VertexConsumer)
              （这里只向 BufferBuilder 写 CPU 顶点）
     -> MultiBufferSource.BufferSource.endBatch
        -> RenderType.draw(MeshData)
           -> CommandEncoder.createRenderPass
           -> GlCommandEncoder.trySetup
           -> GlCommandEncoder.drawFromBuffers
              （这里才调用 glDraw*）
```

26.1.2 官方未混淆 JAR 的字节码可直接确认：

- `CustomFeatureRenderer` 先按 `RenderType` 分组，从 `BufferSource#getBuffer(type)` 取得
  `VertexConsumer`，再执行所有 `CustomGeometryRenderer`；
- `ItemInHandRenderer#renderHandsWithItems` 在 `renderAllFeatures()` 返回后才调用
  `bufferSource().endBatch()`；
- `RenderType#draw` 为每个批次创建 `RenderPass`；
- `GlCommandEncoder#executeDraw` 完成 `trySetup` 后才进入 `drawFromBuffers`。

因此，在 `CustomGeometryRenderer` lambda 里执行下列代码没有裁剪效果：

```java
glEnable(GL_STENCIL_TEST);
writeVertices(consumer);
glDisable(GL_STENCIL_TEST);
```

GL 状态在真正 draw 发生前已经恢复。此前“已启用 stencil 但遮罩仍在”的核心原因不是
`glEnable` 失效，而是调用发生在错误的调度阶段。

### 2.2 RenderType 分组带来的第二个限制

同一个 `RenderType` 的 mask、body、reticle 会合并到一个批次。即使能在回调里保留状态，也没有
mask draw 与 body draw 之间的 GPU 边界。三种几何必须使用身份不同的 `RenderType`，确保
`endBatch()` 产生三个真实 draw。

此外，`CustomFeatureRenderer.Storage` 内部是 `HashMap<RenderType, List<...>>`，不同 RenderType
之间的遍历顺序不等于提交顺序。必须同时使用 `SubmitNodeCollector.order(int)`：26.1.2 的
`SubmitNodeStorage` 以 `Int2ObjectAVLTreeMap` 保存 order，渲染时按整数升序处理。

## 3. Iris 1.11.2 的手部调度

Iris 开启 shader pack 后会重定向 vanilla 手部渲染：

1. `MixinGameRenderer` 阻止 vanilla `GameRenderer#renderItemInHand` 中的实际手部提交；
2. `MixinLevelRenderer` 在主世界 pass 的半透明边界前调用
   `HandRenderer.INSTANCE.renderSolid(...)`；
3. 半透明手部由 `HandRenderer.renderTranslucent(...)` 调度；
4. `HandRenderer` 拥有独立的 `SubmitNodeStorage`、`FeatureRenderDispatcher` 和
   `RenderBuffers`；
5. `HandRenderer.endRender()` 调用 `featureRenderDispatcher.renderAllFeatures()`，随后
   `bufferSource.endBatch()`；
6. Iris 在 `GlCommandEncoder#trySetup` 中把 vanilla/custom `RenderPipeline` 替换为
   `ExtendedShader`，设置 uniform/sampler，并绑定 shader pack 的 before/after-translucent FBO。

结论：Iris 改变了“由谁、在世界帧的哪个阶段绘制手部”，但仍保留
“先收集顶点、后 endBatch draw”的两阶段模型。修复不能只在 vanilla
`GameRenderer` 的 HEAD/RETURN 之间开 stencil。

Iris 1.11.2 提供公开 API：

```java
IrisApi.getInstance().assignPipeline(customPipeline, IrisProgram.HAND);
```

TACZ 的自定义 mask pipeline 必须归类为 `HAND`，否则 Iris 可能给它选择 fallback/错误 program，
或让 mask 与 body 落到不同 FBO。除此之外不需要修改 Iris 内部 shader 源码。旧的
`ShaderCreator` / `ExtendedShader` 内部 mixin 已移除，以降低对 Iris 私有实现和参数索引的耦合。

## 4. 上游 TACZ 的 stencil 语义

官方 `MCModderAnchor/TACZ` 1.20.1（1.1.8-hotfix）与
`Sh1roCu/TACZ-Refabricated` 1.21.1 的 `BedrockAttachmentModel` 在这段逻辑上等价，仍是即时渲染，
顺序为：

1. 清空 stencil；
2. 关闭 color/depth write，绘制 `ocular*`，把目镜投影写成编号；
3. 对中高倍镜，镜身用 `stencil == 0`，即只保留镜外；
4. 以目镜中心和开镜进度绘制一个二维圆并反转 stencil；
5. 黑色 ocular 遮光层只保留在圆外；
6. `division*` 只保留在圆内；
7. 关闭 stencil，再绘制不参与裁剪的普通部件。

26.1.2 不能原样复制这段代码，因为上游每次 `renderTempPart` 都立即 `endBatch(renderType)`；
当前 Feature Rendering 的模型快照只会排队。需要保留“mask -> outside -> inside”的语义，
但把状态移动到真实 draw 边界。

## 5. 此前实现的具体缺陷

### 5.1 GL 状态包围了顶点写入，而非 draw

旧代码在三个 `submitCustomGeometry` 回调中调用 `glStencilFunc` / `glColorMask`，回调返回时恢复。
见第 2 节，这些状态不会覆盖 `RenderType.draw`。

### 5.2 目镜又被写进 bodySnapshot

`ocular_scope*` 既作为 stencil writer 快照，又被保留在 `bodySnapshot`。即使 stencil 已正确写入，
body draw 仍会再画一遍不透明黑色目镜，从而完全覆盖镜内。这是中高倍镜仍有遮罩的直接原因之一。

当前实现把活动目镜从 body 快照移除。若 stencil 附件创建失败，这一规则同时成为透明目镜降级路径。

### 5.3 旧 FBO 修补会替换深度并持续泄漏

旧 `RenderHelper.enableItemEntityStencilTest()` 在每次调用时：

- 新建 `GL_DEPTH24_STENCIL8` renderbuffer；
- 挂到 `GL_DEPTH_STENCIL_ATTACHMENT`；
- 没有缓存和删除；
- 直接替换 vanilla/Iris 已绑定的 depth texture。

后果包括 renderbuffer 每帧泄漏、深度内容丢失、Iris 深度采样与后处理失配。它不能作为兼容方案。

### 5.4 26.2 离屏掩码残留是死链路

源码还保留 `scope_body.fsh`、`IrisScopeMaskState` 等 26.2 方案注释，但当前没有
`ScopeMaskRenderer`、没有绑定 `ScopeMaskSampler` 的 RenderSetup，也没有实际使用
`scope_*_clipped` pipeline。Iris mixin 注入的 mode 恒为 0。这条链路不能证明当前裁剪可用。

### 5.5 ocular 快照必须包含根节点自身变换

`BedrockRenderSnapshot.captureSubtree(root, rootPose, ...)` 的契约是：`rootPose` 已包含父级链和
`root` 自身的变换。首版 draw-time stencil 只应用了 ocular 的父级，导致 mask 写在父节点原点；
活动 ocular 虽从 body 中移除，镜身却没有在真实目镜位置被裁掉，正确位置的准星也无法通过
`stencil == 1`。实机表现正是“红点镜片透明但没有点，中高倍镜仍是整块黑色”。当前捕获路径会在
父级链之后显式执行 `ocular.translateAndRotateAndScale(ocularPose)`。

### 5.6 远端实机日志推翻了“独立 stencil 一定可挂”的假设

远端 `latest.log` 明确记录：

```text
[TACZ Scope] Stencil clipping unavailable: framebuffer 4 is incomplete after adding stencil
(status=0x8cdd)
```

`0x8CDD` 是 `GL_FRAMEBUFFER_UNSUPPORTED`。这发生在 Iris 已加载但 shader pack 尚未启用时，
因此不是某个光影包单独造成；环境为 AMD OpenGL、Iris 1.10.9、Sodium 0.8.12。首版 fallback
主动隐藏所有活动 ocular 并取消 inside reticle，才会表现出“所有目镜都消失、不同模型结果却不同”。
修复方向不能继续依赖删除节点，而必须恢复上游的真实 stencil 语义，并为拒绝独立 stencil 的驱动
提供临时 packed depth-stencil 路径。

### 5.7 永久提升 depth texture 会污染 Iris 生命周期

第二轮实机确认 packed texture 路径能正确裁切，但在 Iris shader pipeline 加载/重载后，屏幕会残留
白色的上一帧枪械/瞄具虚影；切换窗口分辨率后又回到未裁切状态。原因有两层：

1. 原地 `glTexImage2D` 改写了 Iris/vanilla 长期持有的 depth texture，pipeline 销毁/重建时资源元数据
   仍声明 DEPTH32，而 OpenGL storage 已变成 DEPTH24_STENCIL8；
2. resize 可能重建 storage 或复用 FBO ID，旧 stencil attachment 的 `objectType != NONE` 并不代表
   framebuffer 仍 complete。

因此第二次尝试不再永久修改 texture，而把 packed renderbuffer 延长到完整第一人称 hand batch，
并计划在 `renderHandsWithItems` RETURN 统一恢复。下一节的实机结果继续否定了该方向。

### 5.8 每个 scope draw 后立即恢复 depth 会拆开枪体与镜体

第三轮实机中，永久 texture 污染消失，但镜体/准星随观察距离呈半透明变化，且枪体会截断镜体。
原因是 scope body 在临时 packed depth 上绘制后立即恢复，而主枪体随后在原 depth 上绘制：两者颜色
进入同一 hand target，却不共享深度历史，Iris 合成与后续枪体 draw 会把它们当成不同层。

随后尝试把 packed session 延长到完整 `renderHandsWithItems`，结果同类半透明/远近变化扩散到
整只手臂、枪体、镜体和准星。这反而确认：只要替换 Iris hand FBO 的 depth attachment，无论范围
是单个 scope draw 还是整个 hand batch，都会破坏 Iris 对该 pass 的深度与合成假设。

### 5.9 最终方向：不再修改 FBO，以 ocular 深度制造孔

既然目标 FBO 不能追加独立 stencil，而替换 depth 又必然干扰 Iris，当前实现彻底移除
`GlCommandEncoder` raw-GL attachment 修改。活动 ocular 用只写深度、不写颜色的 pipeline 先绘制：
它不会盖住世界颜色，却会让后方 scope-body fragment 无法通过普通 depth test。枪体随后仍在同一
Iris/vanilla depth attachment 中绘制，因此不存在跨层截断或基于场景距离的透明合成差异。

镜身完成后必须恢复原始世界 depth；否则 Iris 在 solid-hand 之后绘制的水面、水体后处理、雾、
粒子和体积云都会被近处 ocular depth 拒绝。

发光点/十字通常是小型独立节点，改用 `depthTest=ALWAYS, writeDepth=false` 的
`HAND_TRANSLUCENT` pipeline。纯蚀刻 division 后续通过 CPU 尺寸过滤恢复，避免重新提交大面积
blackout panel；整个方案不碰目标 FBO 的 attachment 生命周期。

### 5.10 far depth 不是原始世界 depth

第四轮先把孔区写到 far plane，水和云虽然恢复，却会无视前方实体/地形叠加在所有物体上。far 只
表示“没有遮挡”，无法表达备份前该像素的真实 terrain/entity depth。

Iris 已在 `beginHand()` 把准确的 pre-hand 世界深度复制到 `depthtex2`，因此 Iris cleanup 直接采样
该官方来源；vanilla 才在 aperture writer 前把当前 depth blit 到同格式 backup texture。body 完成后
只重绘 ocular cleanup 几何，fragment 按 `gl_FragCoord` 采样并写 `gl_FragDepth`。因此只恢复镜孔
像素，不会抹掉已绘制镜框的深度。

## 6. 当前修复架构

### 6.1 有序批次

`BedrockAttachmentModel` 提交：

- order `-3`：活动 ocular 的 invisible depth aperture（draw 前先 BACKUP 世界深度）；
- order `-2`：aperture-copy 包装后的 attachment body（draw 边界先复制 ocular aperture
  depth，再正常绘制移除了活动 ocular 的镜身）；
- order `-1`：同一 ocular 的 exact world-depth cleanup；
- order `1`：illuminated reticle 与 CPU 过滤后的纯 etched reticle（两者都经
  ocular 屏幕空间 mask 裁剪）。

使用 `SubmitNodeCollector.order(int)` 是必要的，因为 custom geometry 在单个 order 内按
`HashMap<RenderType, ...>` 分组，不能依赖其偶然迭代顺序。

### 6.2 depth-aperture pipeline

`ScopeRenderTypes` 从 vanilla `ENTITY_CUTOUT` 克隆 pipeline，并只修改：

- `ColorTargetState.WRITE_NONE`：ocular 不写任何颜色；
- `writeDepth=true`：保留实际目镜投影深度；
- 轻微负 depth bias：避免与 scope body 共面时漏出黑片；
- Iris 通过公开 API 归类到 `IrisProgram.HAND`。

body 使用原始 RenderType 与原始 FBO/depth attachment。被 invisible ocular 覆盖的后方像素自然
失败，其他镜框/枪体仍按正常深度关系绘制。

### 6.3 精确 depth backup / restore

aperture RenderType 的同步 draw 由 `ScopeDepthCopyState.Operation.BACKUP` 标记。Iris shader 若暴露
`tacz_DepthRestoreMode + depthtex2`，直接采用 Iris 在 `beginHand()` 生成的 pre-hand depth；否则
（vanilla）读取当前 attachment 的 internal format/尺寸，创建同格式 sampleable depth texture 并 blit。

body 后的 cleanup geometry 由 `Operation.RESTORE` 标记。vanilla cleanup fragment 写本地备份；Iris
`ShaderCreator` 注入默认关闭的 `depthtex2` 分支，仅在 cleanup draw 打开 mode uniform。没有
attachment 替换、texture 重定义、整张 depth 覆盖或近似 far depth。

### 6.4 illuminated / filtered-etched reticle pipeline

发光准星从 `ENTITY_TRANSLUCENT_EMISSIVE` 克隆，并设置：

- `depthTest=ALWAYS_PASS`；
- `writeDepth=true`，保护准星像素不被后续水/雾/粒子覆盖；
- Iris 归类到 `HAND_TRANSLUCENT`。

这让小型发光节点不被 ocular depth writer 挡住，并写入近处 hand depth，避免随后透明世界效果
覆盖。纯 etched division 先按 cube 尺寸过滤：32×32/96×34 等面板被丢弃，细线与刻度保留；其
pipeline 同样使用 `ALWAYS_PASS, writeDepth=true`。

### 6.5 真正的 ocular 屏幕空间 mask（reticle 逐像素裁剪）

此前 reticle 只靠 `ALWAYS_PASS` 直接叠画，镜孔之外的溢出像素没有任何裁剪依据。本轮把
8 步规格落地为真实 mask，不需要 stencil，也不替换任何 FBO attachment：

```text
1. BACKUP：画 ocular 之前备份原始世界深度（Iris 直接用 depthtex2，vanilla blit 到自建
   depth texture）；
2. ocular 以 WRITE_NONE 写入不可见近深度（轻微 polygon offset）；
3. APERTURE_COPY：在 body draw 的边界再复制一份深度 —— 它与第 1 步的世界备份只在
   ocular 光栅化过的像素上有差异，即“只包含 ocular 差异”的 aperture depth；
4. scope body 正常绘制（孔后像素照旧深度测试失败，镜内透明）；
5. RESTORE：cleanup 几何把原始世界深度写回 ocular 足迹；
6. MASK：reticle draw 同时绑定两份深度 —— vanilla 走自建 world backup + aperture copy，
   Iris 走 depthtex2 + aperture copy；
7. fragment 里只有 `ocularDepth < worldDepth - epsilon(1e-6)` 的像素保留；
8. 其余像素 `discard`。
```

实现要点：

- body 的 `RenderType` 被 `ScopeRenderTypes.apertureCopy(base)` 包装成第二个
  `DepthCopyRenderType`（`Operation.APERTURE_COPY`），复制动作挂在 `GlCommandEncoder`
  draw 边界 —— 与 BACKUP/RESTORE 同一个注入点，不新增 mixin；
- 两个 reticle pipeline 改用 `core/scope_reticle_mask.fsh` —— 逐字克隆 26.1.2
  `entity.fsh` 后在 `main()` 顶部插入 mask 分支，define 驱动（EMISSIVE/ALPHA_CUTOUT 等）
  与原 pipeline 完全一致，非 mask 行为零差异；
- vanilla 下 `tacz_WorldDepthSampler`/`tacz_ApertureDepthSampler` 由
  `ScopeDepthCopyState` 绑到最高两个空闲 texture unit 并在 draw 后原样恢复；
- Iris 下 `IrisDepthRestoreShaderMixin` 注入第二个 dormant 分支
  （`tacz_ScopeMaskMode`），世界深度改用 Iris 官方 `depthtex2`；RESTORE 与 MASK
  两个 mode 在各自 draw 前互相显式清零，杜绝共享 HAND shader 上的 uniform 泄漏；
- 任一环节失败（FBO 不完整、blit 报错、目标尺寸/格式漂移）都退化为 mode=0 的原样
  绘制，绝不使用过期深度纹理；
- CPU 尺寸过滤 etched 面板的规则保留 —— 遮光板反正会被 mask 整块 discard，
  提前剔除省掉顶点写入与光栅化。

### 6.6 实机回归修复（开启光影不框定 + 春田横线缺失）

远端日志实锤了 mask 链路的两处缺陷，均已修复：

1. **Iris 路径下 `backupSourceFbo` 从未赋值，导致 aperture copy 全部被拒**。
   `BACKUP` 走 Iris 分支时不执行 blit，`backupSourceFbo` 残留为 0 或上一个
   vanilla 阶段的旧值；而 `copyApertureDepth()` 恰恰要求 body draw 的 FBO
   与它相等。日志里的 `fbo 94 does not match ... 0` 与 `fbo 96 ... 4`
   （切换光影包前后）都是这个错。结果是 `maskValid=false`，**Iris 下所有**
   **reticle 退回无裁剪** —— ACOG 之类只是因为准星小、溢出不可见才“看起来
   正常”。修复：`BACKUP` 时无条件记录 `ocularSourceFbo`（ocular 即将写入的
   目标，Iris/vanilla 同一点），aperture copy 只与它比对。

2. **蚀刻过滤被 `EMPTY_VERTEX` 占位面污染，细线被骨骼 pivot 位置误杀**。
   基岩模型里只声明部分面 UV 的 cube（默认枪包大量只有 `south` 面）给其余面
   生成全零顶点的退化 polygon。`isSafeEtchedCube` 的旧实现把它们计入 AABB：
   未旋转 cube 的顶点是骨骼局部坐标，(0,0,0) 落在<b>骨骼 pivot</b> 上 ——
   pivot 若离 cube 很远（1873 的 division pivot 在模型原点，十字线在 z=-111），
   bbox 就被拉成 32×10.97×111 而误判为遮光板。1873 的旋转竖线（cube 自带
   pivot 恰在自身范围内）存活、未旋转横线被杀，正是实机「竖线在、横线没」。
   修复：测量时跳过全零顶点的退化面。离线模拟确认修复后全部细线/刻度/弧段
   KEEP、全部遮光板 REJECT（1873/AUG/98k/QKM 逐项验证）。

巫毒（scope_vudu）随（1）一并恢复 —— 它的高倍组准星走 `HAND_TRANSLUCENT`，
与其他镜共用同一条 mask 链路，失效根因相同。

三条 custom pipeline 都在 `TaCZFabricClient#onInitializeClient` 提前注册。最小
`GlCommandEncoder` mixin 负责 backup/aperture-copy/cleanup/mask 的 sampler 绑定；可选 Iris mixin
补两条 dormant fragment branch。RenderType 构造器通过 Access Widener 开放，用于同步标记
BACKUP/APERTURE_COPY/RESTORE/MASK 操作。

## 7. 依赖版本审计（2026-07-30）

| 依赖 | 本项目 | 审计结论 |
|---|---:|---|
| Minecraft | 26.1.2 | 目标版本，正确 |
| Java | 25 | 26.x 构建目标，正确 |
| Fabric Loader | 0.19.3 | 当前版本，正确 |
| Fabric API | 0.155.2+26.1.2 | 已从 0.151.0 更新；当前 26.1.2 发布线 |
| Fabric Loom | 1.17-SNAPSHOT | 本地既有构建解析为 1.17.17；适配未混淆 26.1.x |
| Iris | 1.11.2 / 26.1.2 | 推荐验证目标；远端 1.10.9 也确认公开 `assignPipeline` 可用 |
| Sodium | 0.9.1+mc26.1.2 | 推荐对照版本；远端日志使用 0.8.12 |
| Mod Menu | 18.0.0-alpha.8 | 26.1–26.1.2 发布版本；原 `18.0.0` 坐标不准确 |
| Cloth Config | 26.1.154 | 支持 26.1–26.1.2，正确 |
| JEI | 29.5.0.26 | 明确支持 26.1–26.1.2，且是本移植已编译过的 API 线；不盲目追更 |
| REI | 26.1.819 | 26.1.2 当前发布，正确 |
| Architectury | 20.0.12 | 已从错误的 1.21.x `13.0.11` 更新 |
| Zoomify | 2.16.0+26.1 | 支持 26.1–26.1.2，正确 |
| Player Animation Library | 1.2.5 | 26.1.2 发布存在；但它不是 KosmX API 的直接替代，相关 compat 仍排除 |
| Forge Config API Port | 26.1.5 | 26.1.x maintained 发布，正确 |

Iris/Sodium 验证配置：

```bash
./gradlew runClient -PwithIris
```

正常构建不引入 Iris/Sodium：

```bash
./gradlew clean build
```

## 8. 实机验证矩阵

至少验证以下组合，不能只验证“客户端能启动”：

| 后端 | shader pack | 瞄具 |
|---|---|---|
| vanilla OpenGL | 关闭 | ACOG、LPVO、8x、98k 纯蚀刻 |
| Iris 1.11.2 + Sodium 0.9.1 | 关闭 shader pack | 同上 |
| Iris 1.11.2 + Sodium 0.9.1 | 任一常用 pack | 同上 |
| 两条路径 | 任意 | HAMR、Vudu、standard_8x 组合镜两种 view |

每项检查：

1. ADS 前镜身/非活动目镜不消失；
2. ADS 后活动目镜不出现整块黑面；
3. 镜身中心被扣除；
4. illuminated 与 etched 分划不溢出镜外；
5. 切换组合镜 view 后使用正确 ocular/division 编号；
6. 世界深度、手部深度、雾和 shader pack 后处理无异常；
7. 连续 resize / 全屏切换不产生 FBO incomplete 或显存持续增长。

## 9. 参考来源

- Fabric 26.1 porting：<https://docs.fabricmc.net/develop/porting/fabric-api>
- Fabric rendering：<https://docs.fabricmc.net/develop/rendering/world>
- Iris 26.1 分支：<https://github.com/IrisShaders/Iris/tree/26.1>
- TACZ 官方 1.20.1：<https://github.com/MCModderAnchor/TACZ/tree/1.20.1>
- TACZ Fabric 上游：<https://github.com/Sh1roCu/TACZ-Refabricated/tree/1.21.1>
- Iris 1.11.2 Fabric 26.1.2：<https://www.curseforge.com/minecraft/mc-mods/irisshaders/files/8402621>
- Sodium 0.9.1 Fabric 26.1.2：<https://modrinth.com/mod/sodium/version/mc26.1.2-0.9.1-fabric>
