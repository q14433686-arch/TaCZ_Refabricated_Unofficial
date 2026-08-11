# 26.2 → 26.1.2 同步移植清单（arena/019fea3a 分支全部修复）

> 写给负责 26.1.2 分支的移植 AI。
> 源分支：`arena/019fea3a-tacz-refabricated-unofficial`（基线 = `26.2(main)` 的 `5e8cba8`，
> 修复终止点 = `7389916`）。
> 目标分支：`26.1.2`（撰写时 tip = `5928558`）。
> 本文每一条平台结论都来自两边官方未混淆 jar 的字节码实读
> （26.2 jar 在 `26.2(main)` 的 `.gradle/loom-cache/...6f7fc6e6bc-26.2.jar`；
>  26.1.2 jar 在 `26.1.2` 分支的 `.gradle/loom-cache/...0d09a28b48-26.1.2.jar`），
> 不是凭记忆/文档。凡标注【已核实】的条目不必再查；标注【待核实】的请照文中给的方法自查。

---

## 0. 三个必须先建立的认知

### 0.1 两条分支的「瞄准镜透视」架构根本不同

- **26.2 线（本源分支）**：离屏掩码纹理方案。`ScopeMaskRenderer` 在
  `FeatureRenderDispatcher` 阶段边界把当帧目镜几何画进离屏 target
  （`ScopeMaskTarget` / `ScopeMaskTextureHandle`，动态纹理 `tacz:scope_mask`），
  镜身/枪身/火光等的专属 RenderType 在 `scope_body.fsh` 里按屏幕坐标采样该纹理做
  `discard`。关键文件：`ScopeMaskGeometry / ScopeMaskRenderer / ScopeMaskTarget /
  ScopeMaskTextureHandle / ScopeBodyRenderTypes / IrisScopeMaskState +
  mixins/client/FeatureRenderDispatcherMixin + GameRendererMixin 的手持 pass 标记 +
  shaders/core/scope_body.{fsh,vsh}`。
- **26.1.2 线（目标分支）**：深度孔径方案。`ScopeRenderTypes` + `ScopeDepthCopyState` +
  `GlCommandEncoderScopeDepthCopyMixin` + `IrisDepthRestoreShaderMixin` +
  `scope_depth_cleanup.fsh / scope_reticle_mask.fsh`：目镜先写真实深度（不写颜色），
  镜身在孔径内被深度测试杀死，事后把世界深度原样恢复。
  **26.1.2 分支根本没有 `ScopeMask*` 那一族文件。**

因此：凡依赖掩码架构的修复（本文单元 C / D）**不能直接打补丁**，要先在 26.1.2
实测症状是否存在，再决定移植深度（§4 C/D 给了决策树）。凡与渲染架构无关的纯
逻辑/数据/数学修复（单元 A / B / E / F），按指示直接搬。

### 0.2 「与基线逐字节相同」的判定是本次移植的基石

已对源分支改动涉及的 24 个文件逐一做了 blob 哈希三方比对
（`26.1.2` tip vs 源分支基线 `5e8cba8` vs 源分支 tip）：

| 26.1.2 与基线【逐字节相同】→ patch 可直贴 | 26.1.2 与两者都不同 → 需按语义适配 | 26.1.2 缺失 |
|---|---|---|
| IEntityAdditionalSpawnData.java | BedrockAttachmentModel.java | ScopeMaskRenderer.java |
| CameraSetupEvent.java | BedrockGunModel.java | ScopeBodyRenderTypes.java |
| FirstPersonRenderGunEvent.java | MuzzleFlashRender.java | IrisScopeMaskState.java |
| BedrockAnimatedModel.java | ShellRender.java | docs/*（文档，免移植） |
| BedrockCubeBox.java | EntityBulletRenderer.java | |
| BedrockCubePerFace.java | AnimateGeoItemRenderer.java | |
| AttachmentRender.java | GunItemRendererWrapper.java | |
| BeamRenderer.java | RenderConfig.java | |
| PalAnimationManager.java | | |

**注意**：直贴成立的充要条件是「26.1.2 当前文件 == 源基线文件」。动手前先再次确认
哈希（26.1.2 可能又有新提交）：

```bash
git fetch origin
# 例：核对 PalAnimationManager
git rev-parse origin/26.1.2:src/main/java/com/tacz/guns/compat/playeranimator/pal/PalAnimationManager.java
git rev-parse 5e8cba8ff8ae256e1ccef88384e295942700ef16:src/main/java/com/tacz/guns/compat/playeranimator/pal/PalAnimationManager.java
# 相等 → 可直接 git apply 源 patch；不等 → 按语义手贴
```

patch 的统一生成方式（在 26.1.2 工作树上，先 `git fetch origin
arena/019fea3a-tacz-refabricated-unofficial`）：

```bash
BASE=5e8cba8ff8ae256e1ccef88384e295942700ef16
TIP=$(git rev-parse FETCH_HEAD)   # 即 arena 分支最新 tip（≥7389916；附录 A 单元 H 于初稿后追加，取最新值才包含它）
git diff $BASE..$TIP -- <相对路径> > /tmp/x.patch
git apply --check /tmp/x.patch && git apply /tmp/x.patch
```

### 0.3 建议落地顺序

A（PAL 哑动画，一行级修复、收益最大）→ B1（实体生成包取整）→ B3（法线双变换）
→ F（调试开关，可选）→ E（激光探针）→ B2（枪口归一化 + 斜向后坐力，数学链）→
C/D（镜内裁切类，先评估必要性再动）。

---

## 1. 平台 API 对照表（26.2 ↔ 26.1.2，全部字节码核实）

| # | 概念 | 26.2 | 26.1.2 | 移植指引 |
|---|---|---|---|---|
| 1 | RenderSystem | `com.mojang.blaze3d.systems.RenderSystem` | **同左**（仍在 systems 包；早前"搬到 pipeline 包"的说法作废） | import 不用动 |
| 2 | 投影矩阵 CPU 获取 | 无 `getProjectionMatrix()`；`getProjectionMatrixBuffer()` → `GpuBufferSlice` → `slice.map(read,write)` → `GpuBufferSlice.MappedView`（有 `data()`） | 同样无 `getProjectionMatrix()`；`getProjectionMatrixBuffer()` 有；【但 `GpuBufferSlice` 没有 `map()`，也没有 `GpuBufferSlice$MappedView` 内部类】 | 26.1.2 改用：`RenderSystem.getDevice().createCommandEncoder().mapBuffer(slice, true, false)` → 返回 `GpuBuffer.MappedView`（**同样有 `data():ByteBuffer`、`close()`**，可 try-with-resources） |
| 3 | `RenderPipeline.builder(...)` | 只有 `builder(Snippet...)` | **同左**（可空调，等价空 snippet 起步；26.1.2 现成 clone 范本：`ScopeRenderTypes.clonePipeline`，用 `getColorTargetState()/getSamplers()/getUniforms()/getShaderDefines()` 等 getter 逐项拷贝） | 26.1.2 推荐「克隆 vanilla 管线再改」路线，整段复刻配方可以省掉 |
| 4 | BindGroupLayout | `com.mojang.blaze3d.pipeline.BindGroupLayout` + `net.minecraft.client.renderer.BindGroupLayouts`，builder 用 `withBindGroupLayout(...)` | **两个类都不存在**。26.1.2 的 Builder 用 `withSampler(String)` / `withUniform(String, UniformType[, TextureFormat])` 逐个声明 | 26.2 的 `withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)` → 26.1.2 的 `.withSampler("Sampler0").withSampler("Sampler2")`；`SAMPLER1`→`.withSampler("Sampler1")`；我们自定义的 `MASK_SAMPLER_LAYOUT("ScopeMaskSampler")` → `.withSampler("ScopeMaskSampler")`，并删掉 `MASK_SAMPLER_LAYOUT` 常量（`BindGroupLayout` 类不存在） |
| 5 | 顶点格式/拓扑 | `withVertexBinding(0, VertexFormat)` + `withPrimitiveTopology(PrimitiveTopology.QUADS)` | **没有这两个方法**；用老式合并调用 `withVertexFormat(VertexFormat, VertexFormat$Mode)` | 例：`.withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(QUADS)` → `.withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)` |
| 6 | ColorTargetState | 三参构造 `(Optional<BlendFunction>, GpuFormat, int writeMask)`；`GpuFormat.RGBA8_UNORM` | 只有 `(BlendFunction)` 与 `(Optional<BlendFunction>, int writeMask)` 两个构造；**没有 `GpuFormat` 类**，格式枚举在 `com.mojang.blaze3d.textures.TextureFormat`（有 `RGBA8`） | 掩码管线那行 → `new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL)`；`TRANSLUCENT/ADDITIVE` 等 `BlendFunction` 常量两边同名 |
| 7 | RenderPipelines 常量 | `ENTITY_SNIPPET / MATRICES_FOG_SNIPPET / MATRICES_FOG_LIGHT_DIR_SNIPPET / ENTITY_SNIPPET / ENTITY_TRANSLUCENT / ENERGY_SWIRL / ENTITY_TRANSLUCENT_EMISSIVE ...` | **字段名完全一致**（共 23 个 Snippet + 全套成品管线）；`register(pipeline)` 也有 | 直接按名引用 |
| 8 | RenderType(s) 包 | `net.minecraft.client.renderer.rendertype.RenderType / RenderTypes / RenderSetup / TextureTransform(...)` | **同左**（26.1.2 已叫 rendertype 子包）。工厂 `entityTranslucent(Identifier)`、`entityTranslucentEmissive(Identifier)`、`energySwirl(Identifier,float,float)` 都在 | import 不用动 |
| 9 | RenderSetupBuilder | `withTexture(String,Identifier) / useLightmap() / useOverlay() / sortOnUpload() / setTextureTransform(...) / createRenderSetup()` | **全套同名方法都有**；另注意 26.1.2 要求「管线声明的每个 sampler 都必须在 RenderSetup 里 withTexture 绑定，占位也要」（其 ScopeRenderTypes 注释即此戒律，与 26.2 的 r52 教训同一条） | 工厂代码可逐行搬 |
| 10 | SubmitNodeCollector | 接口自带 `submitCustomGeometry / submitModel / ...` | **该接口只有 `order(I)` 一个方法**；全部 submit 系列在父接口 `OrderedSubmitNodeCollector` 上 —— 但 **`SubmitNodeCollector extends OrderedSubmitNodeCollector`** | 以 `SubmitNodeCollector` 形参调用 submit 系列的代码**不用改，原样编译** |
| 11 | FeatureRenderDispatcher | `renderAllFeatures` 内部：`prepareFrame(storage)` → `f.executeSolid()` → `f.executeTranslucent()` → …（我们的 mixin 注在 `INVOKE PreparedFrame.executeSolid() BEFORE`） | 方法集为 `renderSolidFeatures / renderTranslucentFeatures / renderTranslucentParticles / clearSubmitNodes / renderAllFeatures / endFrame`，**没有 PreparedFrame.executeSolid**；手部 pass 的 `renderAllFeatures` 调用点结构见其审计文档 §2 | 若移植掩码架构：26.1.2 的注入点改为 `renderAllFeatures` 内 `INVOKE renderSolidFeatures BEFORE`（先反编 `renderAllFeatures` 方法体确认该调用真实存在且位于 prepare/upload 之后；不行就退 `HEAD`） |
| 12 | GameRenderer.renderItemInHand | `(CameraRenderState, float, Matrix4fc)`，开头 `mulPose(new Matrix4f(viewRotation).invert())`（基座 R(q)，view→world）+ `modelViewStack.pushMatrix().mul(viewRotation)` | **结构与基座语义完全一致**（已核实头部 400 字节：同一对 mulPose/pushMatrix.mul，随后 bobHurt/bobView，再走 `ItemInHandRenderer.renderHandsWithItems`） | 单元 B2 的空间契约在 26.1.2 原样成立 |
| 13 | 手部方法名 | `submitHandsWithItems / submitArmWithItem` | `renderHandsWithItems / renderArmWithItem`（26.2 改的名） | 26.1.2 侧 mixin 目标名**保持原名**，别跟着 26.2 patch 改 |
| 14 | Identifier | `net.minecraft.resources.Identifier` | **同左**（26.1.2 没有 `ResourceLocation` 类！） | import 不用动 |
| 15 | `RenderSystem.getModelViewMatrixCopy()` | 有（探针用） | 无；但有 `getModelViewMatrix():Matrix4f`（直接返回值，拷贝用 `new Matrix4f(RenderSystem.getModelViewMatrix())`）与 `getModelViewStack()` | 仅 RecoilDebug 探针用到，见单元 F |
| 16 | VertexConsumer.setNormal(Pose,FFF) | 内部会再乘一次 `pose.transformNormal`（双变换陷阱） | **同样调 `transformNormal`**（已核实调用链） | 单元 B3 的修法两边语义相同，直贴成立 |
| 17 | 其余类（`TextureTarget / GpuSampler / ByteBufferBuilder / DepthStencilState.DEFAULT / TextureTransform.OffsetTextureTransform / OutputTarget / CompareOp / UniformType / ShaderDefines(在 net.minecraft.client.renderer)`） | 有 | **全有**，包名相同 | — |

---

## 2. 单元总览

| 单元 | 案例/主题 | 源提交 | 移植方式 | 优先级 |
|---|---|---|---|---|
| A | ⑥PAL 切枪后第三人称动画整局哑掉 | `7389916` | **直贴** | ★★★（最高） |
| B1 | 实体生成包坐标被 BlockPos 取整（曳光弹道随朝向摆动残留） | `fa2297f` | **直贴**（一处字节码事实已在两边核实一致） | ★★★ |
| B2 | 枪口空间归一化 + ADS 开枪斜向固定侧偏 | `a2838e4` `d24e604` `c975748` | B2a 直贴 + B2b 需适配 | ★★ |
| B3 | 枪械法线双变换（阴影方向错/平视过暗） | `a2838e4`（其一部分） | **直贴** | ★★ |
| C | ②枪身/非瞄具配件镜内可见 → clipForViewmodel | `9738161` | **依赖掩码架构**，先评估 | ★（评估后定） |
| D | ④镜内枪口火光（大面片+辉光两层） | `9b56e8b` `8ff08fe` `571ac3c` `eb6f2f7` `1237ddb` | **依赖掩码架构/深度架构适配**，先实测 | ★（评估后定） |
| E | ⑤激光改色 NVIDIA+Iris —— LaserDebug 探针 | `9738161`（部分） | **直贴** | ★★ |
| F | RecoilDebug 探针 + 四个 FX 运行时开关 | `2de6cb0` `7e95af9` `e42be3b` `896b1d9` `92174cd` | 半直贴半适配，可选 | ★ |
| G | ⑤激光改色的治本候选（已结案：光影包限制，不实施） | — | 搁置 | — |
| H | ~~⑦炮弹/炮烟第一人称枪口锚定（第 31 轮）~~ **已回退，勿移植** | 见附录 A | — | ✕ |

---

## 3. 单元 A —— ⑥PAL 切枪整局哑掉【直贴，最高优先】

**症状**：装 Player Animation Library 1.2.5 后，初次持枪第三人称动画正常；只要切枪
（不必在第三人称），本次会话动画永不恢复；小退/大退「治好」。
**用户已在 26.2 实测确认根治。** 26.1.2 用的 PAL 同为 1.2.5（其 gradle.properties
`player_animation_lib=1.2.5`），同一缺陷必现。

**根因（PAL 源码级实锤，zigythebird/PlayerAnimationLibrary@main）**：
`AbstractFadeModifier#canRemove()` 只有 FADE_IN 完成（progress≥1）才返回 true，
**FADE_OUT 恒 false → fadeOut 播完也永远留在 modifier 链上**；
`AnimationController#tick()` 只按 canRemove 摘除；`get3DTransform` 链非空即交给链首，
完成态 fadeOut 的 alpha=0 把下游全部输出乘 0 → controller 永久哑掉，直到 avatar 重建。

**改法**：`PalAnimationManager.stop()` 里把
`AbstractFadeModifier.standardFadeOut(fadeTicks, EasingType.EASE_IN_OUT_SINE)` 换成
`standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE)`（FADE_IN-to-null：8 tick 滑入
identity=视觉淡出，完成后自动摘除）。

**落地**：`PalAnimationManager.java` 三方比对为「与基线相同」→ 直接
`git diff $BASE..$TIP -- src/main/java/com/tacz/guns/compat/playeranimator/pal/PalAnimationManager.java | git apply`。
（patch 只有注释块更新 + 一行 API 改名。）

**验收**：装 PAL；第三人称；持枪 → 切另一把枪 → 切空手 → 再持枪，循环数轮，
动画每轮都在；收枪淡出观感与之前一致。

---

## 4. 单元 B —— 弹道/后坐力数学链

### B1 实体生成包坐标取整【直贴】

**症状**：曳光弹远端弹道随朝向呈「北偏右、南偏左、东西回正」的规律性偏移
（实测 113 发 spawn−eye 恒为块内小数部分相反数）。
**根因**：`IEntityAdditionalSpawnData.getEntitySpawningPacket` 用
`new ClientboundAddEntityPacket(entity, 0, entity.blockPosition())`；而
**(Entity,int,BlockPos) 构造器在 26.2 和 26.1.2 里都已把 `BlockPos.getX/Y/Z`（int）
直接当 x/y/z 用**（两边字节码均已核实），客户端出生点被块对齐。
**改法**：改用公开全参构造器写入精确 double 坐标。该全参构造签名
`(I, Ljava/util/UUID; DDD, FF, LEntityType; I, LVec3; D)V` 在 **26.1.2 一字不差**
（已核实），patch 原样适用。

**落地**：`IEntityAdditionalSpawnData.java` 直贴。
`EntityBulletRenderer.java` 里 fa2297f 还有一个消费侧小 hunk（用精确出生数据初始化
曳光起点而不是实体插值位）；该文件整体需适配（见 B2e），把这个小 hunk 一并手贴：
语义 = 「曳光首帧定位使用实体当前精确坐标（服务器发包值），不要沿用旧的块对齐
缓存/插值」。读 `git show fa2297f -- src/main/java/com/tacz/guns/client/renderer/entity/EntityBulletRenderer.java`
照搬到 26.1.2 对应方法。

### B2 枪口空间归一化 + ADS 斜向固定侧偏【需适配为主】

**症状**（26.2 第 26–27 轮结案，用户实测确认）：
开第一人称开枪时，枪身/后坐反馈在斜向朝向上出现固定侧偏（正弦指纹 sin2θ）；
第一人称曳光起点不在枪口而随朝向漂移。

**根因与修法**（两份空间契约，26.1.2 已核实同样成立，见 §1 表 #12）：

1. 26.2/26.1.2 的手部 pass 入口基座变为「`mulPose(invert(viewRotationMatrix))`」
   （B ≈ R(q)，view→world），枪口矩阵里读到的平移是**世界轴**的 R(q)·v，
   不是 1.21.1 时代的纯视图空间 v。
2. `GunItemRendererWrapper.renderFirstPerson` 入口先把整条入口矩阵记入
   `handBasePose`（B），`cacheMuzzlePosition` 用
   `Bᵀ·(m − B.t)`（旋转部分转置 × 去平移）把采集位移还原成纯视图空间，
   免 Iris 特例、vanilla/Iris 手部 pass 同时正确。
3. `FirstPersonRenderGunEvent.applyAnimationConstraintTransform`：逐轴约束系数
   在 26.2/26.1.2 手部 pass 会被 R(q) 共轭污染 → 用入口基座旋转 B（3×3）按
   **Bᵀ·diag·Bᵀ**（注意方向！`c975748` 专门修正过：写反成 B·diag·Bᵀ 会得到
   R(2θ) 指纹）归一化恢复 1.21.1 语义。

**落地**：

- B2a【直贴】`FirstPersonRenderGunEvent.java` 基线相同 → 直接 apply。
  **但** patch 里调用了 `GunItemRendererWrapper.copyHandBaseRotation(Matrix3f)`，
  编译依赖 B2b 先在 wrapper 落地。
- B2b【需适配】`GunItemRendererWrapper.java` —— 在 26.1.2 同名方法
  `renderFirstPerson`（26.1.2 方法名与内部 API 可能有出入）里移植三个点：
  1. 字段 `private static final Matrix4f handBasePose = new Matrix4f();`
  2. 入口捕获（一切 mulPose 之前的**第一行**）：
     `handBasePose.set(poseStack.last().pose());`（配合注释说明其语义：入口基座 B）
  3. 公开方法 `public static void copyHandBaseRotation(Matrix3f dst){ dst.set(handBasePose); }`
  4. `cacheMuzzlePosition` 的解算改为 Bᵀ 还原：
     `dx=mx−B.m30; dy=my−B.m31; dz=mz−B.m32;`
     `viewX = B.m00·dx + B.m01·dy + B.m02·dz;`（m10/m11/m12 → viewY；m20/m21/m22 → viewZ，即乘 B 的转置）
  参考源文件 `7389916:…/GunItemRendererWrapper.java` 第 99/108/258/358-363 行。
- B2c【需适配】`EntityBulletRenderer.java` —— 曳光起点使用上式还原出的视图空间
  muzzleRenderOffset，再按相机转向转到世界轴（实体提交侧 pose 不含相机旋转，
  相机旋转由绘制链路统一施加 —— 26.2 字节码结论，26.1.2 请对其
  `LevelRenderer.render` 的实体段做一次同样的抽查确认：
  只看实体是否只做 `translate(entityPos − cameraPos)`）。
  参考 `git show a2838e4` 对该文件的完整改写（91 行）。
- B2d `BedrockAnimatedModel.java` 基线相同 → 若 diff 只含探针（见其 23 行净变化）
  可整文件直贴，也可跳过（它只有 RecoilDebug 探针）。

**验收**：第一人称站定朝东南西北 + 四个斜向分别开枪，枪身不再有任何固定方向
的侧偏；曳光弹起点始终贴枪口；ADS 开枪同上。

### B3 法线双变换【直贴】

**症状**：枪械阴影方向错误 / 视平线高度看枪过暗（动画骨骼旋转被二倍化后「上表面」
在光照里实际朝下）。**根因**：`BedrockCubeBox/PerFace` 先手动 `matrix3f` 变换法线，
再调 `setNormal(pose, nx,ny,nz)` —— 该重载**内部还会再乘一次** `pose.transformNormal`
（26.1.2 字节码同样如此，已核实）→ 双重变换。**改法**：写裸值
`setNormal(nx, ny, nz)`。**落地**：两个文件基线相同 → 直接 apply。
**验收**：白天平视/俯视/仰视观察枪身明暗与阴影方向，应与周围方块光照一致。

---

## 5. 单元 C —— ②镜内看见枪身/配件【依赖掩码架构；先评估】

26.2 修法语义：
`ScopeBodyRenderTypes.clipForViewmodel(original, texture, isFirstPersonViewmodel)`
在「掩码就绪」时把渲染类型换成 `scope_body` 裁剪版（镜内 discard、镜外原样）；
挂两个点：`AttachmentRender`（非瞄具配件），`BedrockGunModel.submit`（枪身，
**必须在瞄具提交之后判：**掩码清单在瞄具提交时才被登记）。
`BedrockAttachmentModel` 的净变化只是一段回退注释，**免移植**。

**26.1.2 先做这个实验再决定**：
开任何中高倍镜（含 AUG 自带），ADS 后看镜片内能不能看到护木/枪身/枪口配件碎片。

- **看不到**（预期如此：26.1.2 的深度孔径方案里，枪身几何在孔径深度平面之后，
  被深度测试天然杀死）→ **单元 C 整体跳过**，什么都不用做。
- **看得到** → 说明仍有从孔径平面之前穿过的几何（如导轨侧面的高挂配件、
  复合镜的侧翻红点臂），此时再做，两条路线：
  - 路线 α（推荐，贴合 26.1.2 现状）：诊断该几何为何未被孔径深度杀死
    （多半是它物理上位于目镜平面之前/侧方）。可考虑给这类配件的 RenderType
    克隆一份带 `scope_reticle_mask.fsh` 式屏幕采样的丢弃版（你们的
    `ScopeRenderTypes.clonePipeline` 现成范式）。
  - 路线 β：移植 26.2 整套掩码架构（见 §7），然后把
    `AttachmentRender`（基线相同 → 直贴）与 `BedrockGunModel.submit` 的
    `clipForViewmodel` 两个挂点照 §5 语义接上。工作量大，除非 α 无解否则不建议。

---

## 6. 单元 D —— ④镜内枪口火光【依赖环境；先实测】

26.2 结论链（全部用户实测闭环）：
火光 = 两个 submit：大面片（vanilla `entityTranslucent`）+ 辉光涡旋
（`energySwirl(tex,1,1)`），各 50ms 窗口。镜内残团经「LayerAssignment 裁决实验」
坐实**全部来自辉光层**。修法 = 两个裁剪版渲染类型
（`flashTranslucent(tex)` / `flashSwirlClipped(tex)`），由
`maskReadyForViewmodel(firstPerson)` 统一切门（`SCOPE_MASK_ENABLE &&
!irisUnsafe && !ScopeMaskGeometry.isEmpty() && ScopeMaskTextureHandle.syncToMaskTarget()`），
`MuzzleFlashRender.extract` 里按当帧结果二选一。管线配方 =
**逐状态复刻** vanilla ENTITY_TRANSLUCENT / ENERGY_SWIRL，只加
`SCOPE_MASK` define + `ScopeMaskSampler` 声明/绑定 + 换 `core/scope_body` 着色器；
Iris 侧把两条管线登记为 HAND（`IrisScopeMaskState` mode=1 映射）。

**26.1.2 先实测**：ADS 开火，镜内是否看到火光团/柔光团。

- **看不到** → 跳过。
- **看得到**（很可能：你们的 DEPTH_CLEANUP 在后续半透明 pass 前恢复了世界深度，
  火光在世界背景之前 → 深度测试放行）→ 在 26.1.2 上实现等价裁剪，**推荐做法**：
  1. 从 26.1.2 的 vanilla `core/entity.fsh` 起，加你们已有的 mask 采样范式
     （或直接移植 §8 的 SCOPE_MASK GLSL 段），产出 `core/scope_flash_clip.fsh`；
  2. 用 `ScopeRenderTypes.clonePipeline` 分别克隆
     `RenderPipelines.ENTITY_TRANSLUCENT` 与 `RenderPipelines.ENERGY_SWIRL`，
     `withFragmentShader(新fsh)` + `withSampler(<mask sampler名>)` ＋
     `IrisCompat.assignPipelineToIris(pipeline, "HAND_TRANSLUCENT"/"HAND", …)`；
  3. RenderType 工厂照 26.2 的 `flashTranslucent/flashSwirlClipped` 语义
     （swirl 必须 `setTextureTransform(new TextureTransform.OffsetTextureTransform(1,1))`
     + useLightmap/useOverlay/sortOnUpload，观感才与 vanilla 逐像素一致）；
  4. `MuzzleFlashRender.extract` 里做当帧切换（其 26.1.2 版本文件需适配，
     方法名叫什么照 26.1.2 现状）；
  5. **不要在 Iris 开着光影且你们判定不安全的路径强切**（26.2 有
     `shouldDisableScopeMaskUnderShaderPack()` 闸门，26.1.2 无此函数 ——
     若你们的深度方案本身在任何光影包下都安全，该闸门可为恒 false）。

**验收**：ADS 下连续开火，镜内无火团、无柔光；腰射时火光正常（不裁）。

---

## 7.【条件性】26.2 掩码架构全套移植清单（仅当 §5/§6 决策走路线 β 时）

需要整批新建的文件（从源分支原样拷，再按下表改 26.1.2 差异点）：

```
src/main/java/com/tacz/guns/client/render/scope/ScopeMaskGeometry.java
src/main/java/com/tacz/guns/client/render/scope/ScopeMaskTarget.java
src/main/java/com/tacz/guns/client/render/scope/ScopeMaskTextureHandle.java
src/main/java/com/tacz/guns/client/render/scope/ScopeMaskRenderer.java
src/main/java/com/tacz/guns/client/render/scope/ScopeBodyRenderTypes.java
src/main/java/com/tacz/guns/compat/iris/IrisScopeMaskState.java
src/main/java/com/tacz/guns/mixin/client/FeatureRenderDispatcherMixin.java
src/main/resources/assets/tacz/shaders/core/scope_body.fsh
src/main/resources/assets/tacz/shaders/core/scope_body.vsh
```

外加注入：`tacz.mixins.json` 注册 `FeatureRenderDispatcherMixin`；
`GameRendererMixin.renderItemInHand` HEAD/RETURN 各加一行
`ScopeMaskRenderer.setInHandPass(true/false)`（26.1.2 该方法名未变，其
GameRendererMixin 已有同方法注入点，直接加两行）。

逐文件的 26.1.2 适配点（缺一个都编译不过）：

| 文件 | 26.1.2 适配 |
|---|---|
| ScopeMaskRenderer | ①投影读回：`slice.map(true,false)` → `RenderSystem.getDevice().createCommandEncoder().mapBuffer(slice,true,false)`（返回 `GpuBuffer.MappedView`，同样有 `data()`）；②`MASK_PIPELINE`：`.withVertexBinding(0, POSITION).withPrimitiveTopology(QUADS)` → `.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)`；`new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, WRITE_ALL)` → `new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL)`；③`MATRICES_FOG_SNIPPET` 同名可用 |
| ScopeBodyRenderTypes | ①全部 `withBindGroupLayout(...)` → `withSampler(...)` 逐个声明（对照 §1 表 #4），删 `MASK_SAMPLER_LAYOUT`；②`.withVertexBinding(0, ENTITY).withPrimitiveTopology(QUADS)` → `.withVertexFormat(ENTITY, Mode.QUADS)`；③`RenderPipelines.register(...)` 你们习惯显式注册（其 ScopeRenderTypes 每个都 register —— 26.2 靠懒注册也活，26.1.2 请统一 register）；④Iris 登记改用你们现成的 `IrisCompat.assignPipelineToIris(pipeline, "HAND"/"HAND_TRANSLUCENT", debugName)`，`IrisScopeMaskState` 可不建 |
| scope_body.fsh/vsh | **不要直接拷**。以 26.1.2 vanilla `core/entity.fsh/vsh` 为底（从 26.1.2 jar 的 `assets/minecraft/shaders/core/` 提取），把 §8 的 SCOPE_MASK 分支段原样贴入 main() 开头；vsh 留意其 `APPLY_TEXTURE_MATRIX` 分支是否与 26.2 一致（swirl 靠它滚动 UV）——若 26.1.2 的 ENERGY_SWIRL 配方不同（有独立 swirl shader），辉光层就克隆其原配方而非 26.2 记录 |
| FeatureRenderDispatcherMixin | 注入目标改为 `renderAllFeatures` 内 `INVOKE renderSolidFeatures BEFORE`（§1 表 #11；先反编确认调用点） |
| GameRendererMixin | 仅加两行 setInHandPass + import |

完成后的首轮冒烟：开 `ScopeMaskDebug` 等价物（若一并移植了 RenderConfig 键）
看掩码预览；再按 §4（C/D）验收。

---

## 8. 附：SCOPE_MASK GLSL 片段（fsh，供 §6/§7 移植用）

26.2 的 `scope_body.fsh` 与 vanilla `core/entity.fsh` 的全部差异就是下面这段
（uniform 声明 + main() 开头分支；其余逐字节与 vanilla 相同）：

```glsl
// 声明区（放在 Sampler0 声明之后）：
#ifdef SCOPE_MASK
uniform sampler2D ScopeMaskSampler;   // 白=镜内（目镜投影覆盖），黑=镜外
#endif

// main() 开头：
#ifdef SCOPE_MASK
    vec2 maskUv = gl_FragCoord.xy / ScreenSize;      // 左下原点，无需翻 Y
    vec2 maskSample = texture(ScopeMaskSampler, maskUv).rg;
    bool insideOcular = maskSample.r > 0.5;
    if (insideOcular) {
        float progress = maskSample.g;               // g 通道=开镜进度（掩码 pass 写入）
        if (progress < 0.999) {
            // 开镜渐进：以掩码自身当距离场，progress<1 时把边缘带视为镜外
            const int RINGS = 3;  const int STEPS = 8;
            float inside = 0.0, total = 0.0, unit = 0.055;
            for (int r = 1; r <= RINGS; r++) {
                float radius = unit * float(r) / float(RINGS);
                for (int i = 0; i < STEPS; i++) {
                    float a = 6.2831853 * float(i) / float(STEPS);
                    vec2 off = vec2(cos(a), sin(a)) * radius;
                    off.x *= ScreenSize.y / max(ScreenSize.x, 1.0);
                    total += 1.0;
                    inside += texture(ScopeMaskSampler, maskUv + off).r > 0.5 ? 1.0 : 0.0;
                }
            }
            if (inside / total < 1.0 - progress) insideOcular = false;
        }
    }
  #ifdef SCOPE_MASK_INVERT
    if (!insideOcular) discard;   // 准星：只留镜内
  #else
    if (insideOcular) discard;    // 镜身/火光：只留镜外
  #endif
#endif
```

（行内注释即设计契约：渐进式开镜用「掩码距离场内缩」而不是「3D 几何缩放」，
后者在透视下会改变投影位置、观感是镜内区域从画面外飞入。）

---

## 9. 单元 E —— ⑤LaserDebug 探针【直贴】（案例⑤已结案：光影包限制，无需代码修复）

> 状态更新（2026-08-11）：案例⑤已由用户定性结案 —— 测试员的光影包**不支持
> 彩色自发光**所致，与 mod、显卡厂商均无关。下述探针属于「白送的诊断件」，
> 移植与否均可；「治本候选」两案**搁置，不要实施**。

**探针（直贴）**：`BeamRenderer.java` 基线相同 → 直接 apply（净变化 = 探针方法 +
节流字段）；`RenderConfig.java` 需适配 —— 在 26.1.2 的 `[render]` builder 区追加：

```java
LASER_DEBUG = builder
        .comment("[DEBUG] Log the laser beam vertex color at each submit (throttled 1s),",
                "to bisect the 'laser recolor has no effect on NVIDIA + shader pack' issue. Default off.")
        .define("LaserDebug", false);
```
（顶部加字段 `public static ForgeConfigSpec.BooleanValue LASER_DEBUG;`）

**审查结论（2026-08-11，26.2 侧完成，对 26.1.2 同样有效）**：

- 数据侧干净：`LaserColorUtil.getLaserColor` 每次从 NBT 现读（无缓存），
  改装界面写回路径若坏则**所有平台**都会坏，与「仅 N 卡+光影」不符。
- 颜色只走**一条通道**：`collector.submitCustomGeometry` +
  vanilla `RenderTypes.entityTranslucentEmissive(tex)` + 逐顶点 `setColor(r,g,b,a)`。
- 光影开启时 Iris 会把该 vanilla 管线整包替换成 shader pack 的
  hand/entities 程序，**顶点色是否参与乘算完全由 pack 决定**（vanilla 原生内容
  几乎不依赖 entityTranslucentEmissive 的顶点色，pack 忘了乘也不会有人发现）。
  「N 卡写法不对、A 卡兜底」这个方向**没有任何已知文献/Issue 支撑**；GLSL 顶点
  色语义与厂商无关，且我们顶点格式所有元素都写满（属 GL 规范定义内行为，
  两厂驱动不应分歧）。
- 实测上最便宜的判别：让 AMD 机主本人用**同一个光影包**开光影自测激光改色
  —— 若同样失效 → 纯 Iris/包侧问题，与厂商无关（概率大）。

**治本候选（择一，建议先等 LaserDebug 日志 + 上述自测结果）**：
1. 给激光做专属 RenderType（克隆 `ENTITY_TRANSLUCENT_EMISSIVE`），并
   `IrisCompat.assignPipelineToIris(pipeline, "HAND_TRANSLUCENT", "laser_beam")`
   （26.1.2 现成 API），迫使 Iris 走受控程序并对齐我们声明的顶点格式；
2. 若仍被 pack 吞色：把颜色**烤进按色缓存的动态纹理**（参照
   `ScopeMaskTextureHandle` 的动态纹理做法），渲染类型换成不依赖顶点色的
   emissive —— 代价是纹理随颜色种数增长（激光色离散，量可控）。

---

## 10. 单元 F —— 调试基础设施（可选，默认全关，建议有计划问题排查时移植）

| 件 | 文件 | 方式 |
|---|---|---|
| RecoilDebug 总键 + 相机逐帧探针 | `CameraSetupEvent.java` | 基线相同 → 直贴 |
| `RecoilDebug`/`DebugDisableMuzzleFlash/Shell/Tracer/CameraAnim` 键 | `RenderConfig.java` | 需适配 = 在 [render] 区追加（照 `git diff $BASE..$TIP -- …/RenderConfig.java` 抄块） |
| 枪口火光/抛壳/曳光的运行时关断 | `MuzzleFlashRender.java` `ShellRender.java` `EntityBulletRenderer.java` | 需适配：每处只是方法体开头 `if (RenderConfig.DEBUG_DISABLE_X.get()) return;` 三行 |
| 手部渲染链探针（wrap 清洁标记/枪口空间/sight 骨骼链） | `GunItemRendererWrapper.java` | 需适配且与 B2b 同文件，一起做；注意 26.1.2 无 `getModelViewMatrixCopy()`，用 `new Matrix4f(RenderSystem.getModelViewMatrix())` |
| AnimateGeoItemRenderer 探针 | `AnimateGeoItemRenderer.java` | 需适配，**建议跳过**（纯取证代码，已完成历史使命） |

---

## 11. 不作移植的内容

- `BedrockAttachmentModel.java`：源分支对其净改动只有一段回退说明注释，无行为差异。
- `BedrockAnimatedModel.java` / `AnimateGeoItemRenderer.java` 的探针：见上。
- `docs/COMPAT_AND_ROADMAP.md`、`docs/publish/*.md`：26.2 线的检修日志与发布文案，
  26.1.2 有自己的文档体系（如 `RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md`），不必合并。
- 案例③（目镜黑边被啃）：26.2 侧用户已决定挂起；且其成因与掩码几何方案强绑定
  （凸包掩码 > 真实孔径），26.1.2 的深度孔径方案无此掩码，**大概率不存在该症状**。
  若 26.1.2 用户报告类似黑边，先开 `ScopeMaskHullFill=false` 等价思路排查——不，
  26.1.2 没这个开关；直接在 26.1.2 开独立排查，不要移植 26.2 的凸包模式。

## 12. 版本号建议

本次落地完成后把 26.1.2 的 build metadata 升一档（如
`1.1.8+fabric.26.1.2.HOTFIX` → `...HOTFIX2`），保持「`+` 后段不参与枪包版本比较」
的既有约定不动（其 gradle.properties 注释已有完整论证，照抄语义即可）。

---

## 附录 A —— 单元 H（已回退，第 31–31.3 轮存档）：弹药模型/尾烟第一人称枪口锚定

> **2026-08-11 更新：本单元已在源分支整组回退（代码恢复至第 31 轮之前），
> 不要移植。** 三轮尝试（曳光锚定链照搬到弹药模型/尾烟路径 → 低速弹种
> 幽灵位移「钉在枪口上方」→ 收敛窗口缩 2.5 格 → 存量 muzzleRenderOffset
> 纵向分量疑似反号继续打补丁）后被判定该静态向量是在别的渲染语义下为
> 曳光目的采集的，借用到这条路径上符号/语义风险无法收束，观感仍怪，
> 用户决定止损回退，维持上游原生行为（炮弹/尾烟按实体位置渲染）。
> 完整教训链见 COMPAT_AND_ROADMAP 案例⑦。若未来重启，正确入口是从
> **射击当帧的第一人称视模世界矩阵**现场解算出膛口（事件时点采集），
> 而非复用曳光目的的存量缓存向量。

---

## 附录 B —— 案例⑧ 修复回流确认（2026-08-12）：你们的 Q/C 洞见已转写进 26.2（mode 3）

> 写给 26.1.2 移植 AI：**你们的解法我们收到了，谢谢**。转写已落在本仓
> `arena/019fea3a` 分支 `FirstPersonRenderGunEvent.applyAnimationConstraintTransform`
> 的 `ConstraintCompensateMode=3`（默认仍是 0，26.2 在体验证通过后才翻默认）。

**26.2 这边的案情背景（解释为什么你们的洞见是决定性的）**：经 §4-B2 把 8/10 的
Bᵀ 三明治带给你们后，我们这边也爆发同案（8/11 起）：ADS 换弹/后坐的修正向量
随朝向摆动 + 竖直跑后方 + 后坐过压。26.2 在体实测否决了三枚形态——
入口基座 Bᵀ 三明治（病灶本体）、锁 modelView（引指向性过强）、活姿态帧共轭
W·D·Wᵀ（消漏成功但「转」复现）。**唯独没有人共轭过「含 Q 的完整系数 C」**——
你们指出的「写回 (−x,−y,+z) 藏了 Q=diag(−1,−1,+1) 且 Q 与旋转不可交换」正是
三版全灭的原因：三条被拒形态共轭的全是 D，Q 每次都被留在写入帧。

**转写代数（供你们核对）**：保留下游写回旧约定（槽位 = Q·v），则
验证链 world = W·C·Wᵀ·v0 ⇔ 本链写入向量 v = Q·W·C·Wᵀ·v0 = Ŵ·D·Wᵀ·v0，
其中 Ŵ = Q·W·Q、W = mulDirection 当帧姿态 3x3（两侧都用这一张，不取后置帧）。
纯偏航/纯俯仰下 Ŵ = Wᵀ——即形态上是「c975748 三明治把入口基座快照换成
写入当帧活姿态」，26.2 从未测过的第四种排列。系数行 D 原样沿用。

**请求**：请把你们的最终实现（commit/diff）推到 `26.1.2` 分支远端——尤其想核对
两点：① 写回槽位的符号处理（你们是否把 m30−=/m31−=/m32+= 一并改掉了）；
② 你们公式里 B 的实际取值位点（写入当帧姿态栈 3x3，还是入口捕获的基座快照）。
若实现与本转写有出入，我这边按你们原样版改。

> **2026-08-12 同日更新：转写在 26.2 在体复现通过**（用户答卷
> 「不转 / 不漏 / 自然」三项全过），mode 3 已翻为 26.2 默认，案例⑧ 双线结案。
> 上述核对请求仍然有效——拿到原版实现后做逐位对齐，避免两仓公式日后各自漂移。
