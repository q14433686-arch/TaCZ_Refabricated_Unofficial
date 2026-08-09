# TacZMeshLoader（1.21.1_fabric）整合进 26.2 移植版：可行性评估与实施方案

> 结论先行：**可行，推荐做，但不是「复制粘贴」级移植。**
> 上游 2,600 行代码里，约 60% 可以原样/机械替换搬过来，约 40%（渲染路径）
> 必须按 26.2 的 `SubmitNodeCollector` 延迟渲染管线重写。
> 好消息是：本移植版保留了上游几乎全部的类名与方法签名（19/19 命中），
> 而且 26.2 的新管线反而让上游大量「打补丁式」代码（VBO 缓存、GUI 检测、
> 帧末 flush、ShaderStateTracker）整体消失，**移植后的代码比上游更简单**。
>
> 本文所有 API 结论均逐一核对过本仓库源码（2026-08-09），不是推测。

---

## 1. 上游项目是什么，依赖哪些 TacZ 内部 API

`VellEagle/TacZMeshLoader`（GPL-3.0-only，纯客户端，v0.1.7）做的事：

1. 读取 Blockbench **Meshy 插件**导出的基岩版 `geo.json` 里的 `poly_mesh` 段
   （`positions` / `normals` / `uvs` / `polys`，不再是立方体），
   在 TacZ 原有立方体模型之上**叠加绘制**网格几何；
2. 通过 `GunModelTypeManager.registerModelType("mesh", ...)` 注册新的枪械
   model type（枪包 display 里写 `"model_type": "mesh"` 即启用）；
3. 用 4 个 mixin 把弹药 / 配件 / 方块 / 枪的模型实例在加载时换成 poly 子类
   （模型旁存在 `geo_models/<模型名>.json` 时才替换，纯立方体枪包零影响）；
4. 配套渲染基建：按光照等级缓存的 VBO、Iris 切光影时失效 VBO 缓存、
   GUI 打开时禁用 VBO、半透明纹理帧末统一 flush、自定义 RenderType。

它依赖的 TacZ 内部类全部在本移植版的 `com.tacz.guns.*` 下同名存在，
这是整个移植能成立的根基（详见第 2 节）。

### 上游分支状况（重要）

- `1.21.1_fabric` 分支**只有一个提交（0.1.7，2026-08-07）**，且是从
  `1.20.1` 分支裁剪而来：`1.20.1` 有 13 个额外提交，包含
  **LRTactical 支持**（`LrPolyMeshModel` / `LrDisplayInstanceMixin`，
  在本分支被删除）和 **multi-material textures**。
- 仓库 0 star / 0 fork，无 release，无持续维护迹象。
- 结论：移植时以 `1.21.1_fabric` 为基线，但 **1.20.1 分支的 LRTactical
  注入器值得一并阅读**——我们 fork 恰好内置了 `lrtactical` 兼容层，
  未来给 LR 装备做 mesh 支持可以直接参考那份被删掉的代码。

---

## 2. 兼容性盘点

### 2.1 上游用到的 TacZ API —— 在本移植版中的存在性（全部核对过）

| 上游 API | 26.2 移植版状态 |
|---|---|
| `com.tacz.guns.api.client.other.GunModelTypeManager.registerModelType(String, BiFunction<BedrockModelPOJO, BedrockVersion, ? extends BedrockGunModel>)` | ✅ 原样存在，签名一致 |
| `com.tacz.guns.client.model.BedrockGunModel`（`modelMap`/`scopePosPath`/`setFunctionalRenderer`/`supplyListeners`/`cleanAnimationTransform`/`getCurrentAttachmentItem`/`getCurrentGunItem`/`getShouldRender`） | ✅ 全部存在（`scopePosPath` 为 protected） |
| `com.tacz.guns.client.model.BedrockAttachmentModel`（`isScope`/`setIsScope`/`setIsSight`） | ✅ 存在，但旧字段 `divisionNodePaths`/`scopeBodyPath`/`ocularRingPath`/`ocularNodePaths` **已被 26.2 重构移除**（见 2.3） |
| `com.tacz.guns.client.model.BedrockAmmoModel` / `bedrock.BedrockModel` | ✅ 存在 |
| `bedrock.BedrockPart`（`name/x/y/z/xRot/yRot/zRot/xScale/yScale/zScale/visible/illuminated/children` + `translateAndRotateAndScale`） | ✅ 字段与方法全部原样 |
| `com.tacz.guns.client.model.FunctionalBedrockPart`（public 字段 `functionalRenderer`） | ✅ 原样 |
| `bedrock.ModelRendererWrapper.getModelRenderer()` | ✅ 原样 |
| `com.tacz.guns.client.model.listener.model.ModelAdditionalMagazineListener` | ✅ 原样（`update` 里 `visible = true`） |
| `GunModelConstant.MAG_NORMAL_NODE` / `MAG_ADDITIONAL_NODE` | ✅ 原样（`"magazine"` / `"additional_magazine"`） |
| `IFunctionalRenderer`（`render(PoseStack, VertexConsumer, ItemDisplayContext, int, int)`） | ✅ 原样 —— **additional_magazine 挂钩模式可直接沿用** |
| `AnimationListener` / `ObjectAnimationChannel.ChannelType` | ✅ 原样 |
| `client.resource.GunDisplayInstance`（字段 `gunModel`/`lodModel`，方法 `checkTextureAndModel(GunDisplay)`/`checkLod(GunDisplay)`） | ✅ 方法签名与字段名原样（`lodModel` 的 Pair 泛型从 `ResourceLocation` 变 `Identifier`） |
| `client.resource.index.ClientAmmoIndex`（`checkTextureAndModel`/`checkAmmoEntity`/`checkShell`，字段 `ammoModel`/`ammoEntityModel`/`shellModel`） | ✅ 全部原样（均为 static 方法，与 mixin 目标匹配） |
| `client.resource.index.ClientAttachmentIndex`（`checkTextureAndModel`/`checkLod`，字段 `attachmentModel`/`lodModel`） | ✅ 原样 |
| `client.resource.index.ClientBlockIndex`（`checkModel`，字段 `model`） | ✅ 原样 |
| `ClientAssetsManager.INSTANCE.getBedrockModelPOJO(...)` / `BedrockVersion.isLegacyVersion(...)` | ✅ 原样（参数类型换 `Identifier`） |
| display POJO 的 getter（`getModelLocation`/`getModelTexture`/`getAmmoEntity`/`getShellDisplay`/`getAttachmentLod`/`getGunLod`/`isScope`/`isSight`…） | ✅ 全部存在 |
| `TimelessAPI.getGunDisplay/getClientAttachmentIndex/getAllClientAttachmentIndex/getIAttachmentOrNull` | ✅ 全部存在 |
| `com.tacz.guns.compat.iris.IrisCompat`（`endBatch`/`isRenderShadow`/`isUsingRenderPack`） | ✅ 存在（`endBatch` 多了 `SubmitNodeCollector` 重载；`isRenderShadow` 语义保留） |
| `com.tacz.guns.util.RenderHelper.enableItemEntityStencilTest/disableItemEntityStencilTest` | ⚠️ 存在但**已是 no-op**（26.2 Vulkan 兼容，见 2.3） |
| `cn.sh1rocu.tacz.api.event.RenderTickEvent` | ❌ 包名变了：26.2 在 `cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent`（同构：`Phase.START/END` + `CALLBACK`，`TaCZFabricClient` 已在用） |
| `com.tacz.guns.compat.ar.ARCompat.disableAcceleration()/resetAcceleration()` | ❌ 26.2 没有这两个方法（见 2.3） |
| `geo_models/` 资源目录约定 | ✅ 原样（`ClientAssetsManager` 仍按 `geo_models` 加载） |

### 2.2 机械替换清单（改名/换包，无逻辑变化）

| 上游写法 | 26.2 写法 |
|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier`（`fromNamespaceAndPath` 同名） |
| `net.minecraft.client.renderer.RenderType` | `net.minecraft.client.renderer.rendertype.RenderType`（工厂类 `...rendertype.RenderTypes`） |
| `RenderType.entityCutoutNoCull(tex)` | 26.2 无此方法 → `RenderTypes.entityCutout(tex)` |
| `RenderType.entityTranslucentCull(tex)` | 26.2 无 → `RenderTypes.entityTranslucent(tex)`（本移植版 EntityBulletRenderer 已有同款替代注释） |
| `RenderSystem.getModelViewMatrix()` | `RenderSystem.getModelViewMatrixCopy()`（移植版 BedrockAttachmentModel:896 在用） |
| mixin json 里的 `"refmap": "..."` | 删除（26.1+ 无 mappings，mixin AP 已禁用；本移植版 mixin json 均无 refmap） |
| `Util.memoize(...)` | 26.2 未确认 → 直接用 Guava `Suppliers.memoize`（移植版已大量使用）或自建 Map 缓存 |
| 上游包名 `com.example.taczmeshloader.*` | 建议并入 `cn.sh1rocu.tacz.compat.meshloader.*`（见第 4 节） |

### 2.3 需要语义重写的部分（26.2 渲染管线差异）

这是移植的真正工作量所在，共 4 处：

1. **旧 `render(...)` + `MultiBufferSource` 体系已死。**
   26.2 的 `BedrockModel.render(...)` 已被标注 `@Deprecated` 且是 no-op
   （注释原文：*"26.2: Minecraft.renderBuffers() removed. Use submit() or
   renderInto() with an explicit VertexConsumer instead."*）。
   所有模型子类必须改为重写 **`submit(...)`** 系列：
   - 枪：`BedrockGunModel.submit(PoseStack, ItemStack, ItemDisplayContext, SubmitNodeCollector, RenderType, int light, int overlay)`
   - 配件：`BedrockAttachmentModel.submit(ItemStack attachment, ItemStack gun, PoseStack, ItemDisplayContext, SubmitNodeCollector, RenderType, Identifier texture, int, int)`
   - 弹药/方块：`BedrockModel.submit(PoseStack, ItemDisplayContext, SubmitNodeCollector, RenderType, int, int[, r,g,b,a])`
   自定义几何统一走
   `collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> ...)`。

2. **stencil 全部失效，瞄具裁剪走「离屏掩码 + shader」路线。**
   上游 mesh loader 的 `renderPolyMeshWithStencil` /
   `renderPolyMeshThroughStencilHole`（`stencilFunc`/`GL_STENCIL_BUFFER_BIT`
   清理等）在 26.2 直接不成立。本移植版的做法：
   - 普通描镜不再有任何模板操作，几何原样提交（`BedrockAttachmentModel.submit`
     第 45 轮重构后的行为）；
   - 第一人称镜内裁剪 = `ScopeMaskRenderer`（自建 `RenderPipeline` +
     离屏 `TextureTarget` 的掩码 pass）+ `ScopeBodyRenderTypes`（镜身
     RenderType 按掩码 discard）+ `IReticleRenderer`（准星反向裁剪）；
   - Iris 光影下自动回退（`IrisCompat.shouldDisableScopeMaskUnderShaderPack()`）。
   移植的 mesh 配件必须**接入这套机制**而不是复刻 stencil。

3. **配件模型内部结构重构，旧字段没了。**
   上游 `TaczPolyMeshAttachmentModel` 依赖的
   `divisionNodePaths`/`scopeBodyPath`/`ocularRingPath`/`ocularNodePaths`
   在 26.2 已换成 `scopeViewPaths`/`reticleNodes`/`ocularByIndex`/
   `divisionByIndex`/`ocularParts`。上游的
   `restorePartVisibilityForPolyMesh()` 补丁式逻辑整体不需要了
   （26.2 的 submit 已经自带 `finally` 还原 visible）。
   另外 26.2 的 `registerOcularMaskGeometry`/`resolveBodyRenderType`/
   `resolveReticleRenderType`/`shouldDrawOcularBlackout` 等 helper 目前是
   **private**——要么改成 protected（我们自己的 fork，一行改动），
   要么在子类里复刻逻辑。**推荐前者。**

4. **AR（Accelerated Rendering）联动接口没了。**
   上游靠 `ARCompat.disableAcceleration()/resetAcceleration()` 把 mesh 枪
   整体移出 AR 加速。26.2 的 `ARCompat` 只有
   `init/shouldAccelerate/isAccelerated(VertexConsumer)/setRenderLayer/...`，
   而且 AR 的挂载点是 `BedrockPartMixin`（立方体级）。移植策略改为：
   **不调用 AR 任何东西**——立方体部分照常走 `super.submit`（是否被 AR
   加速由 AR 自己决定），poly mesh 部分走 `submitCustomGeometry`（天然不加速，
   也不会被 AR 破坏）。是否需要像上游那样整体排除，留到实机测试再定。

### 2.4 移植后会整体消失的上游代码（好事）

| 上游文件/机制 | 为什么可以删 |
|---|---|
| `PolyMesh` 的 VBO 缓存（`vboCache`/`ensureUploaded`/`drawVBO`/`invalidateVboCache`） | 26.2 无即时模式 GL 绘制，`Tesselator`/`BufferUploader`/`RenderSystem.setShader` 已移除；顶点统一走 collector 的 consumer，每帧写入由引擎自己批量上传 |
| `render/ShaderStateTracker` | 它的存在理由是「Iris 切光影时失效 VBO 缓存」；VBO 没了，它也没了 |
| `render/ScreenRenderTracker` | 它的存在理由是「GUI 打开时禁用 VBO」；没有 VBO 路径，consumer 路径在 GUI 里天然正确 |
| `render/MeshyBatchFlushHandler` | 它的存在理由是「半透明纹理要帧末统一 flush 才能正确排序」；collector 按 RenderType 分批，`entityTranslucent` 由引擎自己排序 |
| `render/MeshyRenderTypes` | 26.2 有现成的 `RenderTypes.entityTranslucent`/`entityTranslucentEmissive` |
| 枪模型里 `mc2.gameRenderer.lightTexture().turnOnLightLayer()` 的开关 | 26.2 管线没有这个操作（本移植版全仓库已无此调用） |

也就是说：**上游 6 个「补丁型」渲染基建文件，在 26.2 里一个都不用搬。**
实际需要移植的只有：`IPolyMeshBone` + `PolyMesh`（解析部分）+
`PolyMeshModel`（树逻辑 + consumer 渲染）+ 4 个模型子类 + 4 个 mixin，
以及一个把几何送进 `submitCustomGeometry` 的适配层。

---

## 3. 26.2 渲染管线的三个根本差异（决定移植形态）

理解这三点，移植方案就清楚了：

1. **提交即快照。** `BedrockRenderSnapshot.capture(...)` 在 `submit()` 时把
   每个骨头的矩阵/可见性冻成不可变结构，真正的顶点写入发生在稍后的
   延迟回调里（`collector.submitCustomGeometry(identity, type, (pose, consumer) -> snapshot.write(consumer))`）。
   上游 mesh loader 在回调里现读 `BedrockPart` 变换的做法在 26.2 是**竞态**——
   `cleanAnimationTransform()`/其他实体提交会改写共享 `BedrockPart`。
   所以 mesh 移植必须照抄这个模式：**submit 时遍历骨头树、冻结每根骨头的
   矩阵+可见性，回调里只写顶点**（下文 P1 的 `PolyMeshSnapshot`）。

2. **没有 per-light VBO 缓存。** 光照等级直接 `setLight(packedLight)` 写进
   顶点；引擎按 RenderType 分批、自行管理 GPU 缓冲。代价是每帧 CPU 侧
   重新生成顶点（上游 consumer 路径本来也是这么干的），高面数模型
   （Meshy 高模动辄几万面）需要实测帧率——这是移植后唯一需要担心的性能点。

3. **遮罩/裁剪是离屏 pass，不是状态机。** `ScopeMaskGeometry` 收集
   「目镜的模型矩阵 + 立方体」→ 阶段边界 `ScopeMaskRenderer` 用
   `position` 格式的管线画进离屏 target → 镜身/准星 RenderType 采样掩码
   discard。mesh 目镜要参与裁剪，需要给 `ScopeMaskGeometry` 加一个
   「mesh 几何」变体（存烘焙后的顶点位置数组，绘制时走同一个
   `drawIndexed` 路径）——工作量集中在 P3。

---

## 4. 推荐架构与代码落位

**方案：内置整合（本仓库内实现），不做独立 mod。**

理由：
- 我们 fork 完整保留了 `com.tacz.guns.*` 内部类名，上游 mod 面向的
  「外部 mixin + 反射」手段（反射读 `scopeViewRadiusModifier` 之类）
  在我们内部完全可以改为直接访问/改 protected，代码更干净；
- 单 jar 分发，无版本漂移问题；与 LRTactical 内置同模式，README 已有先例；
- 若要按上游方式做「外部附属 mod」，26.2 下同样要做本文全部渲染重写，
  还多一层 mixin 稳定性/版本锁定的负担，没有收益。

落位建议（与现有代码风格一致）：

```
src/main/java/cn/sh1rocu/tacz/compat/meshloader/
├── api/IPolyMeshBone.java               ← 上游原样（包名换掉）
├── core/PolyMesh.java                   ← 只留解析 + compileConsumer
├── core/PolyMeshModel.java              ← 树逻辑 + 冻结快照 + consumer 渲染
├── core/PolyMeshSnapshot.java           ← 新增：submit 时冻结矩阵/可见性
├── model/TaczPolyMeshGunModel.java      ← 继承 BedrockGunModel，重写 submit
├── model/TaczPolyMeshAmmoModel.java     ← 继承 BedrockAmmoModel
├── model/TaczPolyMeshBlockModel.java    ← 继承 BedrockModel
├── model/TaczPolyMeshAttachmentModel.java ← 继承 BedrockAttachmentModel
├── mixin/ClientAmmoIndexMixin.java      ← 上游 4 个 mixin 改名换包
├── mixin/ClientAttachmentIndexMixin.java
├── mixin/ClientBlockIndexMixin.java
├── mixin/GunDisplayInstanceMixin.java
└── TaczMeshyIntegration.java            ← register() 入口
```

挂载点：
1. `TaCZFabricClient.onInitializeClient()` 末尾调用
   `TaczMeshyIntegration.onClientSetup()`（注册 `"mesh"` model type）；
2. 4 个 mixin 加入 `src/main/resources/tacz.mixins.json` 的 `client` 列表
   （或新建 `tacz.mesh.mixins.json` 并在 fabric.mod.json 追加——推荐后者，
   便于日后整块摘除）；
3. `fabric.mod.json` 的 `provides` 可选追加 `"taczmeshloader"`，
   让依赖该 mod id 的内容包能过依赖检查（与 `lrtactical` 同款做法）；
4. 若 P3 需要 `BedrockAttachmentModel` 的 private helper，把
   `registerOcularMaskGeometry`/`resolveBodyRenderType`/
   `resolveReticleRenderType`/`resolveIlluminatedReticleRenderType`/
   `shouldDrawOcularBlackout`/`currentAimingProgress` 的可见性改 protected。

---

## 5. 分阶段实施计划与代码骨架

### P0 —— 核心解析层（纯逻辑，无渲染，约 0.5 天）

`IPolyMeshBone` + `PolyMesh` 的解析部分（`parse2DArray`/`parse3DArray`/
坐标变换/flat normal/UV）原样搬入；删除 VBO 相关字段与方法，保留：

```java
// PolyMesh —— 26.2 版只剩解析 + 写顶点
public void compile(PoseStack.Pose pose, VertexConsumer consumer,
                    int light, int overlay, float r, float g, float b, float a) {
    if (vertexCount == 0) return;
    for (int i = 0; i < vertexCount; i++) {
        consumer.addVertex(pose, bakedX[i], bakedY[i], bakedZ[i])
                .setColor(r, g, b, a)
                .setUv(bakedU[i], bakedV[i])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, -bakedNX[i], -bakedNY[i], -bakedNZ[i]);
    }
}
```

> 注意上游 `compileConsumer` 里法线取了负号（`-bakedNX`），这是它在
> 1.21.1 调通的结果，先原样保留，实机若出现光照反向再调。

### P1 —— 枪械模型 + submit 接入（最小可用闭环，2~3 天）

核心新增：`PolyMeshSnapshot`（照抄 `BedrockRenderSnapshot` 的模式）：

```java
// 在 submit() 时冻结，回调里只读不碰 BedrockPart
public final class PolyMeshSnapshot {
    private record Command(Matrix4f matrix, Matrix3f normal,
                           boolean visible, boolean illuminated,
                           List<PolyMesh> meshes) {}
    private final List<Command> cutout;
    private final List<Command> translucent;

    public static PolyMeshSnapshot capture(PolyMeshModel model, PoseStack root,
                                           ItemDisplayContext ctx, int light, int overlay) {
        // 遍历 IPolyMeshBone 树：
        //   * 用 bone.applyTransform(ps) 前先 ps.last().pose()/normal() copy 一份
        //   * visible=false / 被 exclude / 不在 meshAncestorBones 的剪枝
        //   * illuminated（含祖先传播）→ light=15728880
        //   * 按骨头名是否含 "translucent" 分进 cutout/translucent 两张表
    }

    public void write(VertexConsumer consumer, int light, int overlay) {
        // 每根骨头：consumer.addVertex(command.matrix(), ...)（Pose 由矩阵构造）
    }
}
```

`TaczPolyMeshGunModel` 重写：

```java
@Override
public void submit(PoseStack poseStack, ItemStack gunItem, ItemDisplayContext transformType,
                   SubmitNodeCollector collector, RenderType renderType, int light, int overlay) {
    if (!hasPolyMesh()) { super.submit(poseStack, gunItem, transformType, collector, renderType, light, overlay); return; }

    // 1. 与上游一致：渲染前设置 additional_magazine 的 exclude 状态
    if (cachedHasAdditionalMagMesh) polyMeshModel.setExcludeSubtree(GunModelConstant.MAG_ADDITIONAL_NODE);
    else polyMeshModel.clearExcludeSubtree();

    // 2. 立方体部分原样走 super（含 scope、laser、AR 等一切现有行为）
    super.submit(poseStack, gunItem, transformType, collector, renderType, light, overlay);

    // 3. poly mesh 部分：submit 时冻结快照，注册自定义几何
    Identifier tex = resolveTexture(gunItem);            // 沿用上游 cachedTexture 逻辑
    if (tex == null) return;
    PolyMeshSnapshot snap = PolyMeshSnapshot.capture(polyMeshModel, poseStack,
            transformType, light, overlay);
    if (!snap.isEmpty()) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(tex),
                (pose, consumer) -> snap.writeCutout(consumer, light, overlay));
        if (snap.hasTranslucent()) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(tex),
                    (pose, consumer) -> snap.writeTranslucent(consumer, light, overlay));
        }
    }
}
```

> 注意 1：`super.submit` 内部把 `poseStack` 冻结成快照后从 identity 提交，
> 我们这里的 `submitCustomGeometry(poseStack, ...)` 传的是**同一个**
> `poseStack`，引擎会自行处理 entry pose，不会双重变换（BeamRenderer
> 就是这么用的）。回调里的 `pose` 参数不要再用，直接用冻结的矩阵。
>
> 注意 2：`additional_magazine` 的 FunctionalRenderer 挂钩（上游
> `applyAdditionalMagazineMeshHook`，往 `functionalPart.functionalRenderer`
> 里包一层、调 `polyMeshModel.renderSubtreeDirect`）**原样可用**——
> `IFunctionalRenderer` 的签名没变，`FunctionalBedrockPart.functionalRenderer`
> 还是 public 字段。但 26.2 的 functional renderer 是在快照回调里执行的，
> 所以 `renderSubtreeDirect` 里也要改为「先冻结子树矩阵、回调写顶点」。
>
> 注意 3：`setFunctionalRenderer`/`supplyListeners`（`MeshAdditionalMagazineListener`）
> 的覆写逻辑不变，仅 import 与包名变化。

验证清单（此时应全部通过）：
- 枪包 display 写 `"model_type": "mesh"` + 同目录 `geo_models/*.json`；
- 第一人称/第三人称/物品栏图标/掉落物/展示框/工作台预览都显示 mesh；
- 换弹时 `additional_magazine` 动画正常（排除/恢复逻辑生效）；
- 无 geo.json 的普通枪包完全不受影响。

### P2 —— 弹药 / 方块 / 配件子类 + 4 个 mixin + LOD（1~2 天）

- `TaczPolyMeshAmmoModel` / `TaczPolyMeshBlockModel`：重写
  `submit(PoseStack, ItemDisplayContext, SubmitNodeCollector, RenderType, int, int)`
  与带 `r,g,b,a` 的 10 参重载（曳光弹走的是 10 参版），
  `super.submit(...)` 后再提交 poly 快照，逻辑与 P1 相同；
- 4 个 mixin：**注入点签名全部匹配，几乎照抄**，改动仅为
  - `ResourceLocation` → `Identifier`（含 `toGeoPath`）；
  - `Pair<BedrockGunModel, ResourceLocation>` → `Pair<BedrockGunModel, Identifier>`；
  - 反射写字段的代码可以保留（字段名都没变），也可以改成
    accessWidener 或包内直接访问（我们是同一 mod，推荐直接访问/加 setter）；
  - mixin json 去掉 `refmap` 字段；
- `GunDisplayInstanceMixin` 的 LOD 分支：26.2 的 `checkLod` 已有
  「高模缺失回退 LOD」逻辑，注入点仍是 `@At("TAIL")`，行为不变；
- `ClientAttachmentIndexMixin` 的 LOD 分支同理（`lodModel` 的 Pair 类型换
  `Identifier`）。

### P3 —— 瞄具掩码 / 半透明 / 性能 / AR（3~5 天，风险最高）

- **mesh 目镜接入镜内裁剪**：`ScopeMaskGeometry` 增加 mesh 变体
  （`addMesh(Matrix4f pose, List<PolyMesh> meshes)`，顶点烘焙成 position-only
  数组；`ScopeMaskRenderer` 的掩码 pass 照现有 `drawIndexed` 路径画），
  `TaczPolyMeshAttachmentModel.submit` 里在 `super.submit` 之前登记目镜
  poly 几何、之后用 `resolveBodyRenderType`/`resolveReticleRenderType`
  提交 mesh 镜身/准星（先要把这些 helper 改 protected）；
- 或者**保守方案**：P3 第一版不做 mesh 目镜裁剪，mesh 瞄具按普通几何
  提交（镜内见镜筒内壁，与本移植版当前 cube 瞄具的非光影回退行为一致）。
  先保证不崩、不黑屏，裁剪作为后续增强；
- 半透明：`translucent` 后缀骨头走 `RenderTypes.entityTranslucent`，
  排序交给引擎；若实测发现穿插，再考虑拆分 RenderType（如
  `entityTranslucentEmissive`）或恢复帧末 flush 思路（但 26.2 没有
  `BufferSource.endBatch`，届时需要找 collector 的等价物）；
- 性能：对比 consumer 路径在 1 万/5 万/10 万面模型下的帧率；
  必要时把「静态部分」烘焙成 `StagedVertexBuffer`（移植版
  `FeatureRenderDispatcherMixin` 已展示 26.2 的用法），但这是最后手段；
- AR：实机装 AR 验证 mesh 枪不闪不黑；若 AR 与 mesh 冲突，再给
  `ARCompat` 加一个 26.2 版的 `disableAcceleration`（在 `BedrockPartMixin`
  判定处加旁路开关）。

---

## 6. 风险清单与「不建议整合」的情形

### 风险（按严重度排序）

| 风险 | 说明 | 缓解 |
|---|---|---|
| 延迟回调竞态 | 回调里读 `BedrockPart` 会被 `cleanAnimationTransform` 污染 → 模型抽搐/错位 | P1 的 `PolyMeshSnapshot` 冻结矩阵，架构上根除 |
| mesh 瞄具裁剪 | 上游 stencil 语义在 26.2 无对应物，mesh 目镜要进 `ScopeMaskGeometry` | P3 保守版先不裁剪；裁剪版在无光影环境验证后再开 |
| 高面数性能 | 每帧 CPU 生成顶点 | 实测；必要时静态 VBO（`StagedVertexBuffer`） |
| AR 联动 | 26.2 `ARCompat` 无 disableAcceleration，mesh 部分天然不加速 | 先不干预，实机验证 |
| Iris | 掩码 pass 与光影冲突（移植版已有 `shouldDisableScopeMaskUnderShaderPack` 回退） | 跟随现有回退逻辑 |
| 上游质量 | 0.1.7 是单提交分支，注释大量日文、有反射 hack、存在未用字段（`isStandalone` 等） | 移植时顺手清理；不承诺与上游同步 |

### 什么情况下**不建议**整合

1. **你手上没有用 Meshy 插件建模的枪包**——这是纯增量功能，对纯立方体
   枪包零收益。先确认目标：是为了某个具体枪包，还是「先备着」？
   若只是备着，P0+P1 做个可关闭的实验开关即可，不必全量落地。
2. **P1 闭环后如果发现高面数帧率不可接受**（consumer 路径每帧重建顶点），
   且静态 VBO 方案在 26.2 下成本过高，则应暂停 P2/P3，只保留 gun 支持。
3. 若后续 26.2 官方/上游出现原生 poly mesh 支持（目前没有任何迹象），
   本方案应整体让位。

---

## 7. 许可与署名

- TacZMeshLoader 是 **GPL-3.0-only**；本仓库代码许可为 **GPL3**——
  **可以内置**，但必须保留原作者署名（VellEagle）与 GPL-3.0 许可声明，
  不得改许可。建议按 LRTactical 的既有模式处理：
  - 移植文件头保留上游版权行 + 注明「自 VellEagle/TacZMeshLoader
    (1.21.1_fabric, v0.1.7) 移植」；
  - README 增加「内置附属：TacZ Mesh Loader（GPL-3.0 代码移植）」一节，
    与现有 LRTactical 说明并列；
  - `fabric.mod.json` 的 `provides` 增加 `"taczmeshloader"`（可选）。
- 不要搬运上游仓库里的任何二进制/资源（`icon.png` 等），代码即可。

---

## 8. 工作量估算

| 阶段 | 内容 | 估算 |
|---|---|---|
| P0 | 解析层 + 包结构 | 0.5 天 |
| P1 | gun + snapshot + submit 闭环 | 2~3 天 |
| P2 | ammo/block/attachment + mixin + LOD | 1~2 天 |
| P3 | 瞄具掩码 + 半透明 + 性能 + AR + Iris | 3~5 天 |
| 合计 | 含实机回归（默认枪包 + 2~3 个 mesh 枪包） | 约 1.5~2 周（非全职） |

---

## 9. 最终建议

**推荐「内置整合、分阶段落地」，先做 P0+P1。** 依据：

1. 兼容面比预想的好得多——19 个依赖类全部原样存在，mixin 注入点签名
   全部匹配，`geo_models` 约定未变，`IFunctionalRenderer`/
   `FunctionalBedrockPart`/`ModelAdditionalMagazineListener` 原样可用；
2. 26.2 新管线让上游 6 个补丁型基建文件自然消亡，净移植量约 1,500 行，
   且架构上更干净（快照冻结模式与本移植版 `BedrockRenderSnapshot` 完全同构，
   是「顺着架构走」而不是「逆着架构硬塞」）；
3. 真正的硬骨头只有两块：延迟回调竞态（P1 快照解决）和 mesh 瞄具裁剪
   （P3，可先保守降级），都在可控范围内；
4. GPL-3.0 许可兼容，按既有 LRTactical 模式署名即可。

不建议的只有一件事：**不要试图把上游代码原样拷进来编译**——那会
撞上已删除的 `renderBuffers`/stencil/VBO/`ARCompat.disableAcceleration`
等一堆 26.2 不存在的 API，产生「看起来能跑、一开镜就黑」的假移植。
按本文 P0→P3 的路径走，每一步都有可验证的里程碑。

---

## 10. 实施进度（2026-08-09 更新）

### ✅ P0+P1 已落地（本仓库工作分支）

已提交到工作分支的代码（全部为新建/小改动，可整体摘除）：

```
src/main/java/cn/sh1rocu/tacz/compat/meshloader/
├── api/IPolyMeshBone.java                     ← 上游原样（换包名）
├── core/PolyMesh.java                         ← 解析 + 顶点写入（删 VBO）
├── core/PolyMeshModel.java                    ← 树逻辑 + 快照采集 + 排除控制
├── core/PolyMeshSnapshot.java                 ← 新增：冻结矩阵快照（record）
├── model/TaczPolyMeshGunModel.java            ← 继承 BedrockGunModel，重写 submit
├── mixin/GunDisplayInstanceMixin.java         ← checkTextureAndModel/checkLod TAIL
└── TaczMeshyIntegration.java                  ← 注册 "mesh" model type
```

接线（3 处）：
- `TaCZFabricClient.onInitializeClient()` 调用 `TaczMeshyIntegration.onClientSetup()`
- 新 mixin 配置 `src/main/resources/tacz.mesh.mixins.json`（独立文件，便于整块摘除）
- `fabric.mod.json` mixins 数组追加该配置

实现要点（相对本文 §5 的骨架）：
- 顶点写入完全照抄移植版 `BedrockCubeBox` 的已验证模式（位置/法线手动乘
  矩阵后以原始坐标写入），不依赖 26.2 `VertexConsumer` 各重载的变换语义；
- additional_magazine 镜像副本 pass 与移植版 `IMirrorGeometry` 的
  `captureGeometry` 逐条对齐：把 additional_magazine 完整变换压入 pose 后，
  以「不套用根变换但根网格照画」的镜像模式采集 magazine / additional_magazine
  子树（magazine 副本不会跟着换弹动画跑）；
- 贴图解析带 LOD 固定（`setOverrideTexture`，LOD mixin 注入时调用）；
- 不再需要上游的 VBO / ShaderStateTracker / ScreenRenderTracker /
  MeshyBatchFlushHandler / MeshyRenderTypes / ARCompat 联动。

已做的验证（沙箱网络受限，无法跑 Gradle，见下）：
- 全部 8 个 Java 文件通过 java-parser 语法解析；
- 新代码引用的每个外部符号（类/方法/字段）均逐一与移植版源码 grep 核对；
- 两个 JSON 通过格式校验。

**⚠️ 尚未验证（必须在你本地做）：**
1. `./gradlew build` 编译通过（沙箱无法下载 Gradle/Loom/MC 26.2 依赖）；
2. 实机加载一个 `model_type: "mesh"` 的枪包，检查第一/第三人称、物品栏、
   工作台预览、换弹动画（magazine 镜像）是否正常；
3. 若出现光照反向/模型翻转/UV 错位，优先调 `PolyMesh` 顶部的
   `FLIP_MODEL_X/Y`、`FLIP_UV_V`、`INVERT_FLAT_NORMAL` 开关；
4. 高面数模型的帧率（consumer 路径每帧重建顶点，必要时再上
   `StagedVertexBuffer` 静态烘焙）。

### ⏳ 未实施（后续阶段）

- P2：ammo / block / attachment 子类 + 对应 3 个 mixin（上游
  `TaczPolyMeshAmmoModel` / `TaczPolyMeshBlockModel` /
  `TaczPolyMeshAttachmentModel`，同样改成 submit 路径）；
- P3：mesh 瞄具接入离屏掩码裁剪（`ScopeMaskGeometry` 加 mesh 变体）、
  半透明排序实测、AR/Iris 联动实测。

---

### 附：本仓库核对过的关键文件（2026-08-09）

- `com/tacz/guns/api/client/other/GunModelTypeManager.java`（注册签名一致）
- `com/tacz/guns/client/model/BedrockGunModel.java`（`submit` 7 参重载，第 293~318 行）
- `com/tacz/guns/client/model/BedrockAttachmentModel.java`（9 参 `submit` + 掩码流程，第 484~630 行）
- `com/tacz/guns/client/model/bedrock/BedrockModel.java`（`render` 已废弃，`submit`/`renderInto` 现行，第 352~382 行）
- `com/tacz/guns/client/model/FunctionalBedrockPart.java`（`functionalRenderer` public 字段）
- `com/tacz/guns/client/model/bedrock/BedrockPart.java`（字段全部原样）
- `com/tacz/guns/client/render/scope/ScopeMaskGeometry.java` / `ScopeMaskRenderer.java`
- `com/tacz/guns/util/RenderHelper.java`（stencil no-op 注释）
- `cn/sh1rocu/simplebedrockmodel/api/event/RenderTickEvent.java`
- `com/tacz/guns/compat/ar/ARCompat.java`（无 disableAcceleration）
- `src/main/resources/tacz.mixins.json`（无 refmap 的 mixin 配置范式）
- 上游 `TacZMeshLoader` @ `1.21.1_fabric`（v0.1.7，全量源码已核对）
