# TaCZ Refabricated 26.3 R1 release notes（草稿，尚未发布）

**构建元数据：`1.1.8+fabric.26.3.R1`**

**基线：`origin/26.2(main)` R3-hotfix2（`c7c3c55` 之前的 26.2 源码）→ 本线 `26.3`**

**环境：Minecraft 26.3 · Fabric Loader 0.19.5+ · Java 25+ · Fabric API 0.160.7+26.3 · Forge Config API Port 26.3.0+**

> 状态（2026-09-21）：编译/CI 全绿；下列标 ✅ 的条目已由维护者在 26.3 实机验证（2026-09-18 ~ 09-21，
> 无光影 + Iris 光影 + 专服三种环境）；标 🔧 的仅编译通过。**尚无可下载构建。**
> 姊妹项目（NeoForge）移植指南：[`lineage/PORT_GUIDE_26_3_FOR_RENOVATED_NEOFORGE_20260921.md`](lineage/PORT_GUIDE_26_3_FOR_RENOVATED_NEOFORGE_20260921.md)。

---

## 1. 引擎适配（Mojang 26.3）

- ✅ 渲染底层 `com.mojang.blaze3d.*` → `com.mojang.renderpearl.*` 包迁移；`PoseStack#mulPose → rotate`、
  `BindGroupLayout#withSampler → withUniform(COMBINED_IMAGE_SAMPLER)`、`TextureTarget` 新构造、
  `RenderPipeline` 显式 `ColorTargetState` 等 API 改名（明细见移植指南 §2.2）。
- ✅ 第一人称渲染随 vanilla 拆分：`ItemInHandRendererMixin` → `FirstPersonHandsAndItemsMixin`（收枪保持）
  + `FirstPersonHandsAndItemsRendererMixin`（viewmodel 接管）。
- ✅ Render pass 归属倒置：`FeatureRenderDispatcher.prepareFrame` + 静态 `renderAllFeatures(RenderPass, PreparedFrame)`；
  `RenderSystem.output*TextureOverride` 删除后 `ScopeFinalOverlayState` 自建 pass；世界高模改挂
  `LevelRenderer#executeSolid` RETURN 复用传入的 pass。
- ✅ 着色器改 shaderc/SPIR-V 方言：`#moj_import → #include`、显式 `layout(location)`、`OIT_ALPHA_ONLY` 条件同步。
- ✅ 自定义管线预热（`ScopePipelinePrewarm`）：26.3 管线按需异步编译，未预热时 Iris 首次开镜 NPE。
- ✅ `swing(hand, SwingAnimation, boolean)`、`invulnerableTime` 私有化（新 `DamageCooldownUtil` 同时清 `lastHurt`）、
  `InputConstants` 键位常量、`Blaze3D.openUri(URI)`、`AbstractPackMetadataResources`、`Pack.ResourcesSupplier` 新签名、
  `FriendlyByteBuf#readMap/writeMap` 移除（`BufMapCodec`）、方块 `codec()` 移除、`PushReaction.POPPED`。
- ✅ 方块战利品表迁移到 26.3 schema（`condition/function → type`、`match_block`）；第三方枪包注入 loot 运行期迁移。
  顺带移除 `AbstractGunSmithTableBlock` 的手工半边掉落（26.2 起就会双掉落）。

## 2. Iris 26.3 适配

- ✅ 光影下 `tacz_ScopeMaskMode` 判定重做：`GlRenderPipeline#info()` 被删后不再按管线身份反查，改为
  `FrontendRenderPass → boundPipeline → uniforms()` 声明表按名判定（新 `IrisFrontendRenderPassMixin`），
  mode 2 由标记采样器 `ScopeMaskMode2Sampler` 区分。
- ✅ `GlCommandEncoder#trySetup → setupDraw`、`ExtendedShader#iris$setupState` 新签名两处 hook 随改名跟进
  （旧名 `require=0` 会静默不装）。
- ✅ 掩码 pass 绑空雾 UBO：修水下/失明开镜不裁剪（vanilla 亦受影响）。
- ✅ 高模 GPU 路径每骨骼强制 program rebind，修光影下法线只在开枪瞬间正确。

## 3. 行为修复（26.3 实机发现）

- ✅ JEI 刷新改用 `Internal.restartJei()`，不再丢服务端同步的 vanilla 型配方。
- ✅ 枪匠台配方材料改惰性解析：旧语法第三方枪包不再导致 `RECIPE` 注册表加载失败、进不去存档。
- ✅ 高模第一人称：消费点回到 `renderAllFeatures` 内 executeSolid 之后（修朝向漂移 + 光影下拉伸成片）；
  提交时预加载贴图（修首帧回退 collector）；collector 回退路径也套镜内裁剪。
- ✅ 专服：把 `minecraft:*` 与 `tacz:*` 配方序列化器登记进 Fabric 配方同步（`RecipeSynchronization`），
  服务端不装 JEI 时客户端 JEI 也能看到 mod 的 crafting 配方、枪匠台材料不再是空槽位。**服务端也需更新到本 build。**

## 4. 兼容层变化

- 🔧 **禁用**（非修复，上游无 26.3 构件，2026-09-21 复查仍无）：REI（+Architectury）、Zoomify、Shoulder Surfing Reloaded。
  门面保留、IMPL 排除，上游发布后回补。
- 🔧 JEI 31.0.0.5、ModMenu 21.0.0-beta.1、PAL 1.2.7+26.3 均为 **beta** 通道，正式版发布后需重新钉版本。
- Voxy / Carry On 无 26.3 构件但 mixin 走 `@Pseudo` 字符串目标，保留未验证。

## 5. 已知未验证 / 未做

- Sodium 0.9.2+mc26.3 下的高模与 PIP 未专项复测（Iris 测试均带 Sodium，无独立矩阵）。
- 世界语境 GPU 高模烘焙（`MeshGpuWorld`）继承 26.2 状态，26.3 未单独跑 `MESH_LOADER.md` §5.2-bis 矩阵。
- 26.3 OIT 对半透明附件/枪口火光的影响未系统评估（实机未见异常，但没有专门看）。
