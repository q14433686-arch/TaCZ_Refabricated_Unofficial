# 26.3 移植指南 —— 给姊妹项目 TaCZ_Renovated（NeoForge）

> 日期：2026-09-21。作者侧：refab `26.3` 线（`1.1.8+fabric.26.3.R1`，实机验证已过一轮）。
> 受众：TaCZ_Renovated 的 `26.3` 分支（目前仍是 26.2 R3-hotfix2 内容，`b2d8ed3`，2026-09-13）。
>
> **覆盖范围**：refab `26.2(main)` → `26.3` 的**全部**改动（130 文件，+2940/−1033），
> 不只是最后几轮。每条都标了「谁改的」（Mojang / Iris / 我们自己发现的行为 bug）
> 与「NeoForge 侧怎么办」。Mojang 与 Iris 侧的差异与加载器无关，是本文的主体；
> Fabric 特有的实现在 §6 单列，不要照抄。
>
> **证据级别**：标 ✅ 的项在 refab 26.3 实机验证通过（2026-09-18 ~ 09-21，多轮 latest.log）；
> 标 🔧 的项仅 CI 编译通过；标 ❓ 的是我们没踩到、但你们可能会踩的。
> 全部 NeoForge 侧建议都是**未在 NeoForge 上执行过的**，请按 PORTING_NOTES §9 的纪律逐个核对签名。

---

## 0. 先读这个：三条真正会让你返工的教训

1. **26.3 的「编译通过」比 26.2 更不可信。** 这一轮我们 17 个实质提交里有 9 个是「CI 绿、
   进游戏就错」：`renderItemInHand` RETURN 处 MV 栈已 pop（枪固定在视角空间，只有朝北正常）、
   `require=0` 的 mixin 因目标改名**静默不装**（裁剪无声失效）、Iris 拿到 null 管线 NPE、
   `#moj_import` 被 shaderc 当未知指令**吞掉**（雾函数找不到）……全都是编译期零报错。
   **每一段渲染改动都要进游戏，且要分「无光影 / Iris」两遍。**
2. **26.3 服务端不再向客户端发全量配方。** 这条影响 JEI、影响你们的枪匠台 JEI 类别、影响专服。
   NeoForge 有自己的 opt-in 通道（§4.9），**和 Fabric 完全不同**，是本文唯一「机制不同、
   症状相同」的大项。
3. **Iris 26.3 把「后端管线不再持有前端引用」做死了**（`GlRenderPipeline#info()` 删除）。
   26.2 线那套按管线对象身份查 `tacz_ScopeMaskMode` 的做法在 26.3 上**不可能**工作，
   我们试过三种反射容错全是死路（§3.2）。直接按 §3.3 的「声明式采样器判 mode」重做，不要试图修补旧路。

---

## 1. 工具链与依赖（NeoForge 侧需自查）

| 项 | refab 26.3 实值 | NeoForge 侧 |
|---|---|---|
| Minecraft | `26.3`（stable，2026-09） | 同 |
| Java | 25（不变） | 同 |
| 加载器 | Fabric Loader 0.19.5 / Fabric API 0.160.7+26.3 | NeoForge `26.3.x` 分支活跃（2026-09-19 仍在合 PR），**发布版本号请自查 maven**；本文写作时未核实是否已有 stable |
| Loom / NeoGradle | Loom 1.17-SNAPSHOT（未动） | 你们的 MDG/NG 版本按 NeoForge 26.3 要求 |
| JEI | 31.0.0.5（**beta**） | JEI 26.3 分支存在（`mezz/JustEnoughItems` `26.3`，含 NeoForge 子项目）；NeoForge 构件版本自查 |
| Cloth Config | 26.3.158 | 自查 |
| Forge Config API Port | 26.3.0（**版本写法从 `26.2.1` 变 `26.3.0`**） | 你们用原生 NeoForge config，无此项 |
| Iris | 1.11.6+26.3 | Iris 有 NeoForge 26.3 构件（同一代码库），§3 全部适用 |
| PAL | 1.2.7+26.3（beta） | 自查 |
| **摘除**（无 26.3 构件） | REI(+Architectury)、Zoomify、Shoulder Surfing Reloaded | 2026-09-21 复查仍无 26.3。**保留门面、只排除 IMPL**，README 措辞必须写「禁用」不是「修复」 |
| 保留（无构件但 mixin 走 `targets=` 字符串 + `@Pseudo`） | Voxy、Carry On | 同理可保留 |

`fabric.mod.json` 侧：`"minecraft": "26.3"`、`fabricloader >=0.19.5`、`forgeconfigapiport >=26.3.0`，
摘掉 `rei_client` / `rei_common` 入口。→ 对应你们 `neoforge.mods.toml` 的 `[[dependencies]]` 版本区间。

---

## 2. Mojang 侧改动（与加载器无关 —— 全部要做）

### 2.1 `com.mojang.blaze3d.*` → `com.mojang.renderpearl.*` 包迁移 ✅

机械替换，但**不是全部**都搬了，`PoseStack`、`Blaze3D`、`InputConstants`、`GraphicsResourceAllocator`
仍在 `blaze3d`。实测映射（本仓命中数见 `PORT_26_3_PLAN_2026_09_17.md` §2.1）：

| 26.2 | 26.3 |
|---|---|
| `blaze3d.pipeline.{RenderPipeline,ColorTargetState,BindGroupLayout,DepthStencilState,BlendFunction}` | `renderpearl.api.pipeline.*` |
| `blaze3d.PrimitiveTopology` | `renderpearl.api.pipeline.PrimitiveTopology` |
| `blaze3d.platform.CompareOp` | `renderpearl.api.pipeline.CompareOp` |
| `blaze3d.GpuFormat` | `renderpearl.api.GpuFormat`（**api 根，不在子包**） |
| `blaze3d.buffers.{GpuBuffer,GpuBufferSlice}` | `renderpearl.api.buffers.*` |
| `blaze3d.textures.{GpuTexture,GpuTextureView,GpuSampler,FilterMode}` | `renderpearl.api.textures.*` |
| `blaze3d.systems.{RenderPass,CommandEncoder}` | `renderpearl.api.commands.*` |
| `blaze3d.opengl.GlCommandEncoder`（mixin `targets=` 字符串） | `renderpearl.backend.opengl.GlCommandEncoder` |

涉及文件：`Scope*` 全家（Body/Text RenderTypes、Mask/Pip Renderer/Target、FinalOverlayState）、
`IrisCompat`、`PolyMeshGpuRenderer`、`IrisGlCommandEncoderMixin`。

### 2.2 API 小改名（每一个都能编译失败，好发现）✅

| 旧 | 新 | 备注 |
|---|---|---|
| `PoseStack#mulPose(Quaternionf)` | `PoseStack#rotate(Quaternionf)` | 21 处：BedrockPart、ShellRender、GunItemRendererWrapper、AnimateGeoItemRenderer、AmmoItemRenderer、Statue/Target/GunSmithTable/Minecart renderer、GunHurtBobTweak、EntityBulletRenderer、LRT 三个 renderer |
| `BindGroupLayout.builder().withSampler(name)` | `.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER)` | ScopeBody/Text RenderTypes、ScopePipRenderer |
| `new TextureTarget(name, w, h, boolean useDepth, GpuFormat color)` | `new TextureTarget(name, w, h, GpuFormat color, @Nullable GpuFormat depth)` | 深度格式用 `GpuFormat.D32_FLOAT`，不要深度传 `null` |
| `RenderPipeline.builder()...` 缺省 color target | 必须显式 `.withColorTargetState(ColorTargetState.DEFAULT)` | 否则 `FrontendRenderPass#setPipeline` 附件数校验炸（V-r3） |
| `EntityRenderDispatcher#getPlayerRenderer(player)` | 删除 → `(AvatarRenderer<?>) getRenderer(player)` | RenderHelper |
| `EntityRenderer#shouldRender(T, Frustum, x, y, z)` | 末尾多一个 `float partialTicks` | EntityBulletRenderer 覆写签名 |
| `AbstractSoundInstance#resolve(SoundManager)` | `getOrResolve(SoundManager)` | GunSoundInstance 覆写 |
| `Util.getPlatform().openUri(String)` | `Blaze3D.openUri(URI)`，URL 先 `Util.parseAndValidateUntrustedUri` | GunSmithTableScreen、ClothConfigScreen、OpenGunPackDirEntry |
| `InputConstants.Type.KEYSYM` / `GLFW.GLFW_KEY_*` / `GLFW_PRESS` | `InputConstants.Type.KEYBOARD` / `InputConstants.KEY_*` / `InputConstants.PRESS`、`MOUSE_BUTTON_LEFT/RIGHT` | 所有 `*Key.java`、MeleeAttackKeys、ForgeSlider（`event.isLeft()/isRight()`）、ClientAttachmentItemTooltip（`isKeyDown(int)` 不再要 Window） |
| `KeyEvent#scancode()` | `keycode()` | KeyboardHandler 事件；我们的 `InputEvent.Key` 字段跟着改名（旧 getter 保留 `@Deprecated`） |
| `Player#swing(InteractionHand)` | `swing(hand, SwingAnimation, boolean sendToSwingingEntity)`，**返回 boolean** | 动画取 `stack.getAttackAnimation()` / `getInteractAnimation()`。`LivingEntityMixin` 的取消注入从 `ci.cancel()` 改 `cir.setReturnValue(false)`；MinecraftMixin 的 `@WrapWithCondition` target 描述符跟着变 |
| `Entity.invulnerableTime = 0` | 字段私有化 → `setInvulnerableTime(0)`；**受伤冷却拆成** `LivingEntity.damageCooldownTime`（public）+ `lastHurt`（private，需 Accessor） | 新 `DamageCooldownUtil.clear(entity)` 一处收口；调用点 EntityKineticBullet×3、ExplodeUtil×2、IMeleeWeapon。**只清 invulnerableTime 不清 lastHurt 的话，同 tick 二次伤害仍被「只算增量」吞掉** |
| `Block#codec()` 抽象方法 | **BaseEntityBlock/Block 不再要求 codec()** | 五个方块类的 `CODEC` + 覆写整段删除（留着会「覆写不存在的方法」编译失败） |
| `PushReaction.DESTROY` | 语义变化 → 用 `PushReaction.POPPED` | 方块被推时按战利品表掉落而非直接消失 |
| `FriendlyByteBuf#readMap/writeMap` | 已不可用 | ClientMessageLaserColor、ServerMessageSyncGunPack、ServerMessageSyncLrPack 改手写 varint 循环（新 `BufMapCodec` 工具）。NeoForge 若用 `StreamCodec` 则不受影响 |
| `AbstractPackResources` | `AbstractPackMetadataResources` + 显式 `implements PackResources`；`ResourceOutput` 改成 `PackResources.ResourceOutput` 嵌套类型 | PathPackResources、DelegatingPackResources |
| `Pack.ResourcesSupplier#openPrimary/openFull` | `openMetadata(PackLocationInfo)` : `PackMetadataResources` + `openResources(PackLocationInfo, Pack.Metadata)` : `Stream<PackResources>` | GunPackLoader 的 zip 枪包打开；`Pack.Metadata` 构造 `(Component, PackCompatibility, FeatureFlagSet, List)` |
| `GameRenderer#renderLevel(DeltaTracker)` / `LevelRenderer#render(...)` | 形参重排（去掉 `DeltaTracker`，多 `CameraRenderState` + renderpearl 类型） | GameRendererMixin 的 `@Inject` 全部改成只收 `CallbackInfo`，需要 partialTick 时用 `this.minecraft.getDeltaTracker()`；`@At(INVOKE target=...)` 描述符重写 |
| `GameRenderer.hudProjection` / `fogRenderer` | 需要读取 → 新 `GameRendererProjectionAccessor`（@Accessor） | 投影 UBO 在 26.3 **不可读回**（§2.5），改为自算 |

### 2.3 第一人称渲染一分为三 ✅（`ItemInHandRenderer` 没了）

| 26.3 类 | 职责 | 我们的 mixin |
|---|---|---|
| `net.minecraft.client.player.FirstPersonHandsAndItems` | 状态/tick：`mainHandItem`、两手高度，`tick(LocalPlayer)` | **新** `FirstPersonHandsAndItemsMixin`：承载 `KeepingItemRenderer`（收枪保持）；`tick` HEAD 的注入**刻意留空**（上游同款，见文件 javadoc —— 别顺手实现它，会打断切枪动画） |
| `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer` | 渲染：`submitHandsWithItems(float, PoseStack, SubmitNodeCollector, PlayerRenderState, FirstPersonHandsAndItemsRenderState)`、`submitArmWithItem(PlayerRenderState, FirstPersonHandsAndItemsRenderState, float, float, InteractionHand, float, ItemStack, float, PoseStack, SubmitNodeCollector, int)` | `ItemInHandRendererMixin` 重命名为 `FirstPersonHandsAndItemsRendererMixin`：`BeforeRenderHandEvent` + TACZ viewmodel 接管点。**玩家不再是形参**，从 `Minecraft.getInstance().player` 取（第一人称只可能是本地玩家） |
| `...state.level.FirstPersonHandsAndItemsRenderState` | 新增渲染状态对象 | 只读 |

`KeepingItemRenderer.getRenderer()` 的取法从 `entityRenderDispatcher.getItemInHandRenderer()` 变为
`player.firstPersonHandsAndItems()`，**可能为 null**（player 未就绪）→ 新增静态 `getCurrentRenderItem()`
兜底返回 `ItemStack.EMPTY`，调用点 CameraSetupEvent×4、ScopePipRenderer、LocalPlayerDraw 全改。

**NeoForge 侧**：你们如果用 `RenderHandEvent` 之类的事件而不是 mixin，先确认 26.3 NeoForge 上该事件
是否还在、挂在拆分后的哪个类上（26.3.x 的 `patches/net/minecraft/client/renderer/` 下看有没有
`FirstPersonHandsAndItemsRenderer.java.patch`）。收枪保持这种要改 `mainHandItem` 字段的，NeoForge
也只能 mixin/AT 到 `FirstPersonHandsAndItems`。

### 2.4 Render pass 归属倒置（V-r3）✅

- `FeatureRenderDispatcher#renderAllFeatures(SubmitNodeStorage)` 拆成
  `PreparedFrame prepareFrame(storage)` + **静态** `renderAllFeatures(RenderPass, PreparedFrame)`；
  `PreparedFrame#executeSolid()` 变成 `executeSolid(RenderPass)`。**pass 内不能再开 pass**。
- `RenderSystem.outputColorTextureOverride / outputDepthTextureOverride` **删除**。
  `ScopeFinalOverlayState` 改为自己 `createRenderPass(name, colorView, Optional.empty(), depthView, OptionalDouble.empty())`
  再调静态 `renderAllFeatures(pass, frame)`，`frame.close()` 在 finally。
- 我们的 `FeatureRenderDispatcherMixin`：掩码阶段边界注入从「executeSolid 之前」改到 `prepareFrame` RETURN；
  高模 GPU 消费点见 §4.6（这一处踩了两次坑）。
- 旧 `PreparedFrameSolidMixin` 整个删除；世界高模改挂 `LevelRenderer#executeSolid(ChunkSectionsToRender, PreparedFrame, RenderPass)` RETURN（`LevelRendererWorldPassMixin`），**把形参 `renderPass` 传进去复用**。

### 2.5 投影矩阵 UBO 不再可读回 ✅

26.3 的 `RenderSystem.getProjectionMatrixBuffer()` 一族拿到的是 GPU slice，不能回读。
PIP / 掩码 / 裁剪需要的 hand 投影改为自算（`GameRendererProjectionAccessor#tacz$getHudProjection` 拿 `Projection`
对象 + 相机参数），见 `ScopePipRenderer` / `ScopeMaskRenderer` 的 diff。

### 2.6 着色器：shaderc + SPIR-V 方言 ✅

我们随包的 6 个 GLSL（`scope_body.{vsh,fsh}`、`scope_text.{vsh,fsh}`、`scope_pip.fsh`、`scope_ring_final.fsh`）全部要改：

1. `#moj_import <minecraft:x.glsl>` → `#include <minecraft:x.glsl>`。**旧写法不报错**：shaderc 把它当未知预处理指令整行丢弃，
   症状是 `'fog_cylindrical_distance': no matching overloaded function found`（2026-09-18 实机日志第 1 行）。
2. 顶点属性与 varying **必须**显式 `layout(location = N)`，vsh out 与 fsh in 的 location 要配对。
3. 顶部 `#extension GL_ARB_separate_shader_objects : require`。
4. 26.3 vanilla `entity.vsh/fsh` 多了 `OIT_ALPHA_ONLY` 分支，我们的 `scope_body` 是 vanilla entity 的逐字节拷贝 + 注入，
   `#ifndef NO_OVERLAY` 等条件要同步改成 `#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)`。
5. **Iris 注入的 HAND 着色器不吃我们的 GLSL 文件**，注入片段走 `IrisScopeMaskState` 的字符串拼接，那边的 `ScreenSize` 依赖已换成 `textureSize(tacz_ScopeMaskSampler, 0)`。

做法建议：直接 diff 26.3 vanilla `assets/minecraft/shaders/core/entity.vsh` 与你们的拷贝，只保留注入行。

### 2.7 管线编译按需 + 异步 ✅ —— 必须预热

26.3 `RenderSystem.getCompiledPipelineNullable(pipeline)` 在缓存未命中/异步未完成时返回 **null**；
vanilla 资源重载的预编译波覆盖不到 mod 静态构建的自定义管线。无光影下 vanilla 自己会等；
**开 Iris 时，Iris 挂在该方法 RETURN 的 `redirectIrisProgram` 不查 null → 第一次开镜 NPE 崩溃**（§3.5）。

修复（`ScopePipelinePrewarm`，客户端 tick 末尾）：对全部自定义管线（ScopeBody 7 条、ScopeText、Mask、Pip、PolyMesh 3 条）
逐条 `RenderSystem.getCompiledPipeline`，用 Iris 的 `ImmediateState.bypass = true` 包住（否则预热调用本身触发同款 NPE；
反射拿不到该字段就裸调）。失败管线 200 tick 退避，状态变化才打日志。`ScopeMaskRenderer.drawMask` 的 `setPipeline`
也改成传 `getCompiledPipeline(MASK_PIPELINE)`。NeoForge：挂 `ClientTickEvent.Post`。

### 2.8 数据包 schema：战利品表 ✅（这条在 26.2 上就是错的，26.3 才暴露）

26.3 战利品表 JSON：`"condition": "x"` / `"function": "x"` 键改为 `"type"`；`conditions: [..]` 数组改单个 `condition`
（多个用 `minecraft:all_of` + `terms`）；`functions` 同理；`block_state_property{block, properties}` →
`match_block{blocks, state}`；建议加 `random_sequence`。旧格式**静默不加载** → 方块掉落变紫黑无名物品。

- `data/tacz/loot_table/blocks/{gun_smith_table,statue,target,workbench_a,b,c}.json` 六份重写。
- 枪包注入的 loot（`LootTableInjection`）新增 `LegacyLootCompat.migrateSchema` 运行期迁移，让第三方旧包不炸。
- 附带修了一个**与版本无关的老 bug**：`AbstractGunSmithTableBlock#playerWillDestroy` 里对「挖非根半边」额外 `popResource`
  一个克隆物品，与根半边的战利品表叠加 → 挖上半/头部爆出两个工作台。已删除，掉落全部交给战利品表（`match_block` 只让 lower/foot 掉，
  并从根方块实体拷 `BlockId`）。**你们 26.2 线大概率也有这个双掉落。**

### 2.9 服务端不再全量同步配方 ✅ —— 见 §4.9（NeoForge 机制不同）

---

## 3. Iris 26.3 侧（NeoForge 版 Iris 同一代码库，全部适用）

### 3.1 断点总表

| # | 差异 | 后果 | 处置 |
|---|---|---|---|
| I-r1/V-r1 | `GlRenderPipeline#info()` 删除；Iris 重定向从 `GlDevice#getOrCompilePipeline` 搬到 `RenderSystem#getCompiledPipelineNullable`，新建的 `GlRenderPipeline` 是后端专用构造（无前端引用） | 26.2 按管线对象反查 location 定 `tacz_ScopeMaskMode` 的路**彻底死**，mode 恒 0 → 光影下不裁 | §3.3 重做 |
| I-r2 | `GlCommandEncoder#trySetup(GlRenderPass, Collection):boolean` → `setupDraw(GlRenderPass):void`，类搬到 `renderpearl.backend.opengl` | 我们两个 `require=0` hook **静默不装** | 改名 + 改包（`IrisGlCommandEncoderMixin`） |
| I-r3 | `ExtendedShader#iris$setupState(HashMap, GpuTextureView)` → `iris$setupState(List<BindGroupLayout.UniformDescription>)`，调用点挪到 `MixinGlRenderPipeline#iris$bind`（`pipeline.bind()` RETURN） | 旧签名处理器在 APPLY 阶段抛 `InvalidInjectionException`，整个 hook 丢弃 | `IrisExtendedShaderMixin` 改空形参 `(CallbackInfo)` |
| — | `MixinGameRenderer` 把 vanilla `submitHandsWithItems` Redirect 成 no-op；手部由 `HandRenderer#renderSolid/renderTranslucent` 在 `LevelRenderer.render` **内部**自调 `renderAllFeatures` | 任何「在 vanilla 手部 pass 之后」的消费点在 Iris 下都跑在 render 括号外，顶点格式/MV 都不对 | §4.6 |
| — | `MixinGlProgram.iris$samplerBinding` 只认 `Sampler0/1/2/CloudFaces`（+Sodium 名） | 我们自定义采样器 binding = -1，**光影下 `setUniform` 绑 mask 纹理永远无效** | mask 纹理必须继续走 GL 直绑（`resolveMaskTextureId` → `ScopeMaskTarget.current()`），勿再试 setUniform 路 |
| — | `redirectIrisProgram` 不查 null | 首次开镜 NPE | §2.7 预热 |
| — | `ShadowRenderer:603` 也调 `renderAllFeatures` | 高模 GPU 消费点会在阴影 pass 再跑一遍 | `IrisCompat.isRenderShadow()` 早退 |
| — | `iris_NormalMat` 只在 `pipeline.bind()` 时按当刻 MV 计算；`setupDraw` 只在 `lastPipeline != pipeline` 时 bind | 多骨骼同管线连续 draw 只有第一根拿到正确法线（§4.7） | 强制 rebind |

### 3.2 已证伪、不要再试的路（省你两天）

- 反射 `GlRenderPass.samplers`：26.3 **没有这个字段**（`NoSuchFieldException: samplers`，实机日志）。
- 「名字列表 + 扫第一个 Map 字段 + dump 全部字段」三级容错：全部落空——26.3 后端 pass 上根本没有按名字的采样器表，
  名字信息在**前端** `FrontendRenderPass#boundPipeline → FrontendRenderPipeline#uniforms()`。
- `scopeUv` varying 替代 `gl_FragCoord/ScreenSize`：实机截图证明 vanilla 路径本来就健康，永久否决。
- 26.2 的 `info()` 老路作为 26.3 兜底：留着无害（我们留了），但它在 26.3 永远不会命中。

### 3.3 光影下 mode 判定的 26.3 做法 ✅

1. **新 mixin** `IrisFrontendRenderPassMixin`（`@Pseudo` + `require=0`）：`FrontendRenderPass` 构造 RETURN 处调
   `IrisScopeMaskState.noteFrontendPass(this)`，建立 后端 `GlRenderPass` → 前端 `FrontendRenderPass` 的弱键弱值表
   （构造器首参即 backend，一对一同生同灭）。
2. `resolveMode` 在 `setupDraw(GlRenderPass)` hook 里由后端找回前端 → `boundPipeline` → `uniforms()` 声明表按名判：
   含 `ScopeMaskMode2Sampler` → 2；含 `ScopeMaskSampler` → 1；否则 0。结果按 `FrontendRenderPipeline` 实例缓存。
3. mode 2 的区分手段 = **标记采样器**：准星（reticle / reticle_emissive）与裁字（scope_text_clipped）三类 RenderType 的
   bind group 多声明一个 `ScopeMaskMode2Sampler`（COMBINED_IMAGE_SAMPLER），绑同一张掩码纹理，GLSL 声明但从不采样。
4. `resolveMaskTextureId` 同路拿 `TextureViewAndSampler`（`getGlTextureId` 要 `view()` 解包），拿不到回退 `ScopeMaskTarget.current()`。

### 3.4 掩码 pass 的雾污染（vanilla 也受影响）✅

`MASK_PIPELINE` 用 `core/position.fsh` → 输出 `apply_fog(...)`；`bindDefaultUniforms` 绑的是 `RenderSystem.getShaderFog()`，
手部 pass 期间仍是 `FogMode.WORLD`。水下/失明时雾终点只有几格 → 掩码 R 通道被雾混到 <0.5 → **不裁剪**。
修：`drawMask` 在 `bindDefaultUniforms` 后 `pass.setUniform("Fog", fogRenderer.getBuffer(FogMode.NONE))`。
症状是「水下/失明开镜不裁」，晴天完全看不出来。

### 3.5 Iris 静态复核通过、零改动的钩子（供你们对表）

`ShaderCreator.link` 七参签名与 4 处 `createShader(name, ShaderType, source)` 调用点 → `@ModifyArgs` 仍命中；
`ShaderKey.HAND_*` 全在；`IrisApi#assignPipeline(RenderPipeline, IrisProgram)` + `IrisProgram.HAND` 原样；
`MixinGlRenderPipeline.java:98` 是每次 bind 的 `iris$setupState(createInfo.uniforms())` 调用点。

---

## 4. 我们在 26.3 实机上发现的行为 bug（多数与加载器无关，按发现顺序）

### 4.1 JEI 重启方式 ✅（`7b736a5`）
枪包同步后刷新 JEI 不能再用 `AFTER_RECIPES_UPDATED` 事件——JEI 26.3 在这条路上会丢掉服务端同步来的 vanilla 型配方。
改为反射调 `mezz.jei.common.Internal.restartJei()`，事件路只作 fallback 并 WARN。`RecipeViewerReloadBridge`。

### 4.2 方块掉落双份 + 紫黑物品 ✅（`021b546`）→ §2.8。

### 4.3 装任意第三方枪包进不去存档 ✅（`c8837d2`）
`GunSmithTableSerializer` 的 MapCodec 用 `Ingredient.CODEC.fieldOf("item")` **急切**解析材料；旧包语法（`{"tag": ...}` 等）
解析失败 → 整个 `RECIPE` 注册表加载失败 → 存档打不开。改为 `ExtraCodecs.JSON.fieldOf("item")` 存原始 JSON，
`GunSmithTableIngredient` 惰性解析 + `getRawItem()`，编码时优先编 resolved、否则回写 raw。26.3 把配方进了注册表加载流程，
所以 26.2 上「一条配方坏了只 WARN」的行为在 26.3 变成「全盘拒绝」。**你们只要枪匠台配方走 codec 就一样中招。**

### 4.4 高模第一人称漂移（只有朝北正确）+ 光影下拉伸成片 ✅（`65fbaef`）→ §4.6。

### 4.5 GPU 烘焙首帧回退 collector ✅（`42b0bb2`）
消费点搬进 pass 内后，`RenderType.prepare()` 的贴图**懒加载上传**撞「pass 内不许发其他命令」
（`IllegalStateException: Close the existing render pass before performing additional commands`）。
修：`submitBone/submitBoneWorld` 提交时刻（pass 未开）`touchTexture(id)` 触发懒加载，按 id 去重、资源重载清空。
同时 collector 回退路径的第一人称也套 `ScopeBodyRenderTypes.clipForViewmodel`（否则回退时开镜不裁高模枪身）。

### 4.6 高模 GPU 消费点的正确位置 ✅
- ❌ `GameRenderer#renderItemInHand` RETURN：MV 栈已 `popMatrix()`，取到 MV=I → 枪固定在视角空间；
  Iris 下更是跑在 `LevelRenderer.render` 括号外，VBO 是宽 stride（Iris `IrisVertexFormats.ENTITY`）却按 36 字节解读 → 拉伸成片。
- ✅ `FeatureRenderDispatcher#renderAllFeatures` 内 `INVOKE PreparedFrame.executeSolid, shift AFTER`，**把形参 `renderPass` 传下去复用录制**。
  vanilla 与 Iris `HandRenderer` 都在各自 push/pop 之间调它，MV 栈顶 = 手部 MV；Iris 下仍在 render 括号内，格式与 HAND program 一致。
  加 `isRenderShadow()` 早退。

### 4.7 光影下高模法线错、开枪瞬间才对 ✅（`3cddfb7`）
根因见 §3.1 末行。修：`IrisGlCommandEncoderMixin` `@Shadow lastPipeline`，`setupDraw` HEAD 若
`PolyMeshGpuRenderer.isForcingPipelineRebind()` 则置 null，强制每 draw 重 bind；`drawViaRenderTypeCore` 绘制段前后置/清标志。
代价：每骨骼一次 program bind（p90 111 次），仅光影 + GPU poly 期间。

### 4.8 专服：枪匠台材料全空 + mod 的 `minecraft:crafting_*` 配方 JEI 查不到 ✅（`bba3b60`）→ §4.9。

### 4.9 配方同步（26.3 新机制，**Fabric / NeoForge 实现完全不同**）

**事实**：26.3 `ClientboundUpdateRecipesPacket` 只剩物品属性集 + 切石机；服务端不再发全量配方。
JEI 靠加载器提供的 opt-in 通道拿服务端配方；**服务端没装 JEI 时**通道为空 → 客户端 JEI 走
`VanillaClientRecipeLoader` 兜底：① 只读 vanilla 数据包（mod 数据包里的 `crafting_shaped/shapeless` 全部消失）；
② 它跑完 vanilla-only 的 TagLoader 后**把客户端静态注册表的 tag 整体换成 vanilla 集合**，服务端同步来的 `c:*`
全部被抹 → 枪匠台 `#c:ingots/iron` 等 449 个材料 `Missing tag` → 空槽位。单人局不触发。

**Fabric 做法**（我们）：`RecipeSynchronization.synchronizeRecipeSerializer(serializer)`，按**序列化器**登记，两端都要调。

**NeoForge 做法**（26.3.x 源码实读，未在 NeoForge 上验证）：按 **RecipeType** 登记，事件驱动：
```java
// 服务端（common 总线）
NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> e.sendRecipes(
        RecipeType.CRAFTING,            // 我们数据包里的 crafting_* 配方
        ModRecipe.GUN_SMITH_TABLE_CRAFTING));
```
- `PlayerList.placeNewPlayer` / `reloadResources` 打补丁：post `OnDatapackSyncEvent` 后
  `CommonHooks.sendRecipes(player, event.getRecipeTypesToSend(), recipeMap)` 发 `RecipeContentPayload`
  （`neoforge:recipe_content`，只对 `connectionType.isNeoForge()` 的客户端发）。
- 客户端收包后 post `RecipesReceivedEvent(recipeTypes, RecipeMap)`；JEI NeoForge 主类
  （`NeoForge/src/main/java/mezz/jei/neoforge/JustEnoughItems.java:18`）自己只 `sendRecipes(CRAFTING, STONECUTTING, SMELTING, SMOKING, BLASTING, CAMPFIRE_COOKING, SMITHING)`。
- **结论与 Fabric 相同**：服务端只装 TaCZ 不装 JEI 时，`CRAFTING` 不会被任何人登记 → 同样的空槽位/配方消失。
  TaCZ 自己在 `OnDatapackSyncEvent` 里 `sendRecipes(RecipeType.CRAFTING, GUN_SMITH_TABLE_CRAFTING)` 即可；
  与 JEI 重复登记是 Set 幂等。**两端都要是新 build**。
- 请顺带核实：你们的枪匠台 JEI 类别是从 `RecipeManager` 拿配方还是从 TaCZ 自己的网络缓存拿；
  如果是前者，在 26.3 客户端 `RecipeManager` 是空的，必须改从 `RecipesReceivedEvent` 的 `RecipeMap` 或 TaCZ 自己的同步缓存取。

---

## 5. 26.3 上被摘除/替换的第三方兼容，及门面写法

| 模块 | 处理 | 门面 |
|---|---|---|
| REI + Architectury | `compat/rei/**` 整包排除，入口点摘除 | `RecipeViewerReloadBridge` 用反射访问 REI，无编译期依赖，保留 |
| Zoomify | 排除 `ZoomifyCompatInner` | `ZoomifyCompat.getFov` 直接回传原值 + 启动 log 一行「No Zoomify integration in this build」 |
| Shoulder Surfing Reloaded | 排除 `ShoulderSurfingCompatInner` / `ShoulderSurfingPlugin`，`shouldersurfing_plugin.json` 摘（存档在 `docs/patch/shouldersurfing_plugin.json.disabled-26.3`） | `ShoulderSurfingCompat.isModLoaded` 恒 false |

README/CHANGELOG 措辞：「**禁用**（上游无 26.3 构件），非修复；上游发布后回补」。

---

## 6. Fabric 特有、**不要照抄**的部分

| refab 实现 | 为什么是 Fabric 特有 | NeoForge 对应 |
|---|---|---|
| `TaCZFabric#registerRecipeSync`（`RecipeSynchronization.synchronizeRecipeSerializer`） | Fabric API `fabric-recipe-api-v1` | §4.9 `OnDatapackSyncEvent#sendRecipes` |
| `CustomIngredientSerializer.register(TaczNbtIngredient…)` | Fabric ingredient API | NeoForge `ICustomIngredient` / `IngredientType` 注册（你们已有） |
| `ClientTickEvents.END_CLIENT_TICK.register(ScopePipelinePrewarm::tick)` | Fabric 事件 | `ClientTickEvent.Post` |
| `ForgeConfigApiPort` 26.3.0 | Fabric 上的 config 桥 | 原生 NeoForge config |
| `fabric.mod.json` 入口点 / `tacz.mixins.json` 增删（`FirstPersonHandsAndItems*Mixin`、`IrisFrontendRenderPassMixin`、`GameRendererProjectionAccessor`、`LivingEntityDamageCooldownAccessor`，删 `PreparedFrameSolidMixin`、`ItemInHandRendererMixin`） | 文件形态 | `neoforge.mods.toml` + 你们的 mixin json；Accessor 类可换 AT |
| `PathPackResources` / `DelegatingPackResources` | 这两个类本来就是从 Forge 抄来给 Fabric 用的 | 你们用 NeoForge 原生的，但 §2.2 那两行 API 改名一样要做 |
| `BufMapCodec` 手写 map 读写 | 我们的包仍用 `FriendlyByteBuf` 手写 | 若已迁 `StreamCodec` 则无事 |

---

## 7. 建议的移植顺序与验收清单（按我们实际踩坑顺序排，能省最多来回）

1. **依赖 + 包迁移 + 小改名**（§1、§2.1、§2.2）→ 目标：编译绿。这一步纯机械，半天。
2. **第一人称拆分**（§2.3）+ **pass 归属**（§2.4）+ **shader 方言**（§2.6）→ 进游戏（无光影）：
   - [ ] 第一人称枪在八个朝向都跟手、切枪动画正常、收枪保持正常
   - [ ] 开镜：掩码、准星、PIP、镜内文字正常；**水下/失明再开一次**（§3.4）
   - [ ] 日志无 `no matching overloaded function`、无 `Couldn't find source for … shader`
3. **战利品表 + 配方 codec**（§2.8、§4.3）→
   - [ ] 挖工作台上半/下半各一次：各掉**一个**、有名字、保留 BlockId
   - [ ] 装一个旧语法第三方枪包，能进存档
4. **配方同步**（§4.9）→ 开专服、**服务端不装 JEI**：
   - [ ] JEI 里能搜到 mod 的 crafting 配方；枪匠台材料槽不空；日志无 `Missing tag: 'c:`、无 `does not have JEI installed`
5. **预热 + Iris**（§2.7、§3）→ 开光影：
   - [ ] 首次开镜不崩
   - [ ] 光影下裁剪生效（镜身/准星/文字三类各看一次；低倍 sight 确认镜身不被啃洞）
   - [ ] 日志无 `NoSuchFieldException`、无 `InvalidInjectionException`（后者在启动时）
6. **高模**（§4.4–4.7）→ 无光影 + 光影各一遍：
   - [ ] 无光影：八朝向跟手；日志出现 `drew N bones … on hand pass`（没有 `falling back to collector`）
   - [ ] 光影：形态正常（不是切片）、法线/阴影稳定（不是开枪瞬间才对）
   - [ ] 开镜时高模枪身被裁

**每一步都改 README 与 CHANGELOG 时区分「实机 PASS / 仅编译」**，这是两仓共同的红线。

---

## 8. 索引：本线各项的原始记录（要细节去这里翻）

- 执行计划与依赖矩阵：`docs/investigations/PORT_26_3_PLAN_2026_09_17.md`
- 可行性评估（pre-1 时代，部分已被上文修正）：`docs/investigations/PORT_26_3_FEASIBILITY_2026_09_02.md`
- 26.3 vs 26.2 渲染差异 + 全部实机定案（§8–§17）：`docs/investigations/SCOPE_26_3_VS_26_2_DELTA_ANALYSIS_2026_09_20.md`
- 高模：`docs/MESH_LOADER.md`
- 26.3 R1 变更清单：`docs/CHANGELOG_26_3_R1.md`
- 提交（实质，按时间）：`9419eeb` scope mode / fog → `7b736a5` JEI restart → `021b546` loot → `c8837d2` recipe codec →
  `65fbaef` mesh hand pass → `42b0bb2` texture preload + clip → `3cddfb7` Iris rebind → `bba3b60` recipe sync
