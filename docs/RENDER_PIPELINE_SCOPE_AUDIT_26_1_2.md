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
5. 不能替换或破坏主画面 / Iris 的深度附件；
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

## 6. 当前修复架构

### 6.1 三个独立批次

`BedrockAttachmentModel` 提交：

- `WRITE_MASK`：活动 ocular 快照；
- `DRAW_OUTSIDE`：移除了活动 ocular 的 body 快照；
- `DRAW_INSIDE`：etched / illuminated reticle。

`ScopeStencilRenderType` 包装原 RenderType，以不同对象身份阻止批次合并；三阶段分别提交到
order `-2 / -1 / 1`，利用 `SubmitNodeStorage` 的有序键保证先写 mask、再画 body、最后画 reticle，
不依赖 `HashMap` 的偶然迭代顺序。

### 6.2 mask pipeline 不写颜色和深度

mask 使用从 vanilla `RenderPipelines.ENTITY_CUTOUT` 克隆的 pipeline：

- shader、defines、sampler、uniform、顶点格式、cull 与原 pipeline 一致；
- `ColorTargetState.WRITE_NONE`；
- 保留 depth test，但 `writeDepth=false`；
- Iris 存在时经公开 API 指派到 `IrisProgram.HAND`。

这比在 lambda 中调用 `glColorMask(false...)` 可靠，因为 vanilla 和 Iris 都会在实际 draw 前重放
pipeline color/depth state。`TaCZFabricClient#onInitializeClient` 会提前强制注册该 pipeline，保证
首次 `ShaderManager` 资源重载已经编译它；不能等到玩家第一次开镜才惰性注册。

### 6.3 在最终 FBO 上挂独立 stencil

`GlCommandEncoderScopeStencilMixin` 注入 `drawFromBuffers` HEAD。此时：

- `createRenderPass` 已选定 vanilla 目标；
- Iris `trySetup` 已绑定其实际 shader FBO；
- 下一步就是 `glDraw*`。

`ScopeStencilState` 对当前 FBO：

- 若已有 stencil，直接复用；
- 否则把一个共享的 `GL_STENCIL_INDEX8` 挂到 `GL_STENCIL_ATTACHMENT`；
- 不修改已有 depth attachment；
- 共享 renderbuffer 按 viewport 变化 resize，并可同时挂到 Iris 的 mask/body FBO；
- 检查 framebuffer completeness；失败时取消 mask writer 与 inside-only reticle draw，只让已移除
  活动 ocular 的 body 正常绘制，形成不会黑屏的透明目镜降级。

## 7. 依赖版本审计（2026-07-30）

| 依赖 | 本项目 | 审计结论 |
|---|---:|---|
| Minecraft | 26.1.2 | 目标版本，正确 |
| Java | 25 | 26.x 构建目标，正确 |
| Fabric Loader | 0.19.3 | 当前版本，正确 |
| Fabric API | 0.155.2+26.1.2 | 已从 0.151.0 更新；当前 26.1.2 发布线 |
| Fabric Loom | 1.17-SNAPSHOT | 本地既有构建解析为 1.17.17；适配未混淆 26.1.x |
| Iris | 1.11.2 / 26.1.2 | 当前兼容目标；仅可选运行测试，不打进产物 |
| Sodium | 0.9.1+mc26.1.2 | Iris 1.11.2 对照版本 |
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
