# 瞄具裁剪失效：26.2→26.3 vanilla / Iris 差异逐项分析

- **日期**：2026-09-20
- **症状**：26.3 分支上，开镜（ADS）时目镜贴图不被裁剪。
- **本轮动作**：瞄具裁剪链路已**整体退回 26.2 基线**，只保留「无它则编译失败或
  开镜即崩」的最小适配（提交 `a1e9f46`）。本文是配套的事后归因分析。
- **证据级别说明**：
  - **【实证】**：26.2/26.3 两分支逐文件 diff、Iris GitHub `26.2`/`26.3`
    分支源码（26.3 侧锁定在 `IrisShaders-Iris-b388d57`）、本仓实机日志行。
  - **【仓内引证】**：本仓注释/文档中对 26.3 反编译/字节码的行号引用（形如
    `GR:399` = GameRenderer、`LR:443` = LevelRenderer），本沙箱无 JDK 无法复核，
    但与 Iris 26.3 源码反映出的 vanilla 结构完全互洽。
  - **【假设】**：尚无实机证据的推断，逐条标注。

---

## 0. TL;DR

| # | 差异 | 谁改的 | 与「不裁剪」的关系 |
|---|---|---|---|
| V-r1 | `GlRenderPipeline#info()` 被删，后端管线不再持有前端 `RenderPipeline` 引用 | vanilla | **光影下的裁剪主死因**：26.2 靠它反查管线 location 决定 `tacz_ScopeMaskMode`，26.3 反射拿不到 → mode 恒 0 → 光影下不裁 |
| I-r1 | Iris 程序重定向从 `GlDevice#getOrCompilePipeline` 搬到 `RenderSystem#getCompiledPipelineNullable`，新建的 `GlRenderPipeline` 是**后端专用构造**（无前端引用） | Iris | 与 V-r1 同源：26.2 重定向后 `info()` 仍能拿回我们的 location；26.3 实体里没有可拿的 |
| I-r2 | `GlCommandEncoder#trySetup(GlRenderPass, Collection):boolean` → `setupDraw(GlRenderPass):void`，类同时搬家到 `renderpearl.backend.opengl` | vanilla/Iris | 我们的两个 hook 挂在旧名上，`require=0` 软注入**静默不装** → uniform 永不写入（已按新名适配保留） |
| I-r3 | `ExtendedShader#iris$setupState` 形参 `(HashMap, GpuTextureView)` → `(List<BindGroupLayout.UniformDescription>)`；调用点从 `GlCommandEncoder` 挪到 `GlRenderPipeline#bind` | Iris | 旧签名的处理器在 mixin APPLY 阶段抛 `InvalidInjectionException`，**整个 hook murai丢弃并报错**（已改空形参适配保留） |
| V-r2 | 掩码采样约定 `gl_FragCoord.xy / ScreenSize` 的两个前提在 26.3 不再显然：① renderpearl 起用 `GL_ARB_clip_control`；② `Globals` UBO（ScreenSize）在手部 pass 的绑定状态存疑 | vanilla | **无光影下的裁剪嫌疑**；前分支的 `scopeUv` 修复即冲它而来，但未证实已回滚（§5 候选 B） |
| V-r3 | Render pass 归属倒置 + `RenderSystem.output{Color,Depth}TextureOverride` 删除 + `FrontendRenderPass#setPipeline` 附件数校验 | vanilla | 开镜**崩溃**类（不是不裁类）。适配已保留 |

**一句话**：光影下（Iris）的断点是 V-r1/I-r1（管线身份不可反查）；
无光影下的断点最可能是 V-r2（掩码采样的屏幕坐标来源），但两版都未实机定案，
回滚后的 26.2 基线正好是验证这两条的干净起点。

---

## 1. 裁剪链路的两个采样约定（分析前先把话讲清）

本 mod 的瞄具裁剪在 26.2 时代有两条互不相干的执行路径，它们对「26.3 哪些差异
会弄死它」的答案完全不同：

**路径甲 · 无光影（vanilla 链路）**——我们自己的 shader 全程运行：

1. `ScopeMaskRenderer.renderAtPhaseBoundary()` 在手持帧图的边界处，把当帧所有
   目镜几何画进离屏掩码纹理（白=镜内，绿通道=开镜进度）；
2. 镜身走 `scope_body.fsh`（SCOPE_MASK）：`maskUv = gl_FragCoord.xy / ScreenSize`，
   采样掩码，镜内 `discard`；准星/镜内文字走反相分支，镜外 `discard`。

此路径的软硬前提：**(a)** 掩码纹理确实画上了形状；**(b)** `gl_FragCoord` 的原点
方向与掩码纹理行序一致；**(c)** `Globals` UBO 里的 `ScreenSize` 在手部 pass
有效（非 0）。

**路径乙 · 光影（Iris 链路）**——我们的 shader 被整段替换，裁剪靠「注入分支
+ 逐 draw 写 uniform」：

1. `assignScopePipelineToHand` 把 6 条 scope 管线映射进 Iris 的 HAND 程序表，
   于是绘制时这些管线跑的是 **Iris/光影包的程序**，我们的 `scope_body.fsh`
   根本不参与链接；
2. `IrisShaderCreatorMixin` 在 HAND 程序链接前把一段
   `if (tacz_ScopeMaskMode == 1/2) { 采样 tacz_ScopeMaskSampler 并 discard }`
   的 dormant 分支注进它的源码；
3. `IrisGlCommandEncoderMixin`（每次 draw setup）与 `IrisExtendedShaderMixin`
   （Iris 每次 setup 程序状态）把 `tacz_ScopeMaskMode` 写给当前程序：
   **mode 怎么定？看正在画的这条管线是不是我们的 scope 管线**——26.2 的取法是
   `pass.pipeline.info().getLocation()`，匹配常量表给 1（镜身/枪口焰）或 2
   （准星/镜内文字），其余一切给 0。

此路径的软硬前提：**(a)** 源码注入成功（uniform 存在）；**(b)** 两个 hook 真的
装上了；**(c)** 「正在画的管线是谁」能答出来。26.3 打掉的是 (b) 和 (c)。

---

## 2. Vanilla 26.2→26.3：逐项差异

### 2.1 渲染底层重构（blaze3d → renderpearl 三分）【实证】

26.3 把 `com.mojang.blaze3d` 里「管线/缓冲/纹理/命令」一层搬进了
`com.mojang.renderpearl`：

| 层 | 内容 | 26.2 | 26.3 |
|---|---|---|---|
| 前端 API | `RenderPipeline`、`ColorTargetState`、`BindGroupLayout`、`DepthStencilState`、`BlendFunction`、`PrimitiveTopology`、`GpuFormat`、`VertexFormat`、`IndexType`、`CompiledRenderPipeline`、`RenderPass`（接口）、`CommandEncoder`（接口）、`GpuBuffer(Slice)`、`GpuTexture(View)`、`GpuSampler`、`FilterMode` | `blaze3d.pipeline/.buffers/.textures/.systems` | `renderpearl.api.*` |
| 后端 | `GlCommandEncoder`、`GlRenderPass`、`GlRenderPipeline`、`GlProgram`、`GlDevice`、`GlStateManager` | `blaze3d.opengl` | `renderpearl.backend.opengl` |
| 前端执行 | `FrontendRenderPass`、`FrontendRenderPipeline`（record：`name()` + `backendRenderPipeline()` + uniforms/colorTargetStates…） | 不存在（26.2 由 `PreparedRenderType`/encoder 直接面对后端） | `renderpearl.frontend` |
| **没搬** | `RenderSystem`、`RenderTarget`、`TextureTarget`、`PoseStack`、`VertexConsumer`、`BufferBuilder`、`DefaultVertexFormat`、`ProjectionType`、`GraphicsResourceAllocator` | `blaze3d.*` | 不变 |

证据：Iris 26.3 的 import 清单（`MixinGlCommandEncoder`、`MixinShaderManager_Overrides`
头部）与本仓 `PORT_26_3_PLAN_2026_09_17.md` §2.1 的逐行对照表一致；Fabric API 26.3
同样从新包 import。

**与症状的关系**：纯改名本身无害（机械替换即可），但它标志着下面 2.2 的
实体结构变化。

### 2.2 `GlRenderPipeline#info()` 删除 —— 光影链路的断点（V-r1）【实证】

- 26.2：`GlRenderPipeline(renderPipeline, program)` 的构造里**留着前端
  `RenderPipeline` 引用**，`info()` 返回它。Iris 26.2 的
  `MixinGlCommandEncoder#iris$bypassSetup` 里就写着
  `RenderPipeline pipeline = glRenderPass.pipeline.info();` 再用它查
  depth/blend/cull 状态——**26.2 的后端管线是可反查前端的**。
- 26.3：构造改为 `GlRenderPipeline(device, createInfo, program, vertexArray)`——
  只剩设备、CreateInfo、GL 程序、顶点数组，**前端引用整个不在场**。Iris 26.3
  全源码树里对 `info()` 的调用为 0（26.2 时代它在用）；其
  `MixinGlCommandEncoder` 的 `@Shadow lastPipeline` 类型也从
  `RenderPipeline` 变成了 `GlRenderPipeline`。

连锁后果（26.2 时代我们的 `IrisScopeMaskState#resolveModeUncached`）：

```
pass.pipeline                          // 后端 GlRenderPipeline，26.2/26.3 都有
  .info()                              // 26.2 ✔ → 前端 RenderPipeline
                                       // 26.3 ✘ 方法不存在
  .getLocation()                       // "tacz:pipeline/scope_body_clipped"
  → mode = 1/2
```

26.3 下的实际行为：反射 `getMethod("info")` 抛 `NoSuchMethodException`，被
catch 住**静默返回 0**——`tacz_ScopeMaskMode` 永远 0，注入进 HAND 程序的分支
永不执行。日志一个字都不会有。「光影下开镜不裁剪目镜」的最直接成因。

> **前分支的被否掉的尝试**：`program().getDebugLabel()` 反查（后端程序是
> Iris 自己的程序对象，label 是 Iris 程序名），`FrontendRenderPass#setPipeline`
> 前端 name() 登记（实机探针只登记到未参与重定向的 `scope_mask` 自己），
> `getCompiledPipelineNullable` 重走重定向后按键对表（bindingSync=6/6 但
> 绘制期 0 命中）。三条路 2026-09-19 实机全部证否，本轮已随回滚摘除。
> **注意**：这不等于「问题无解」，只等于「按对象身份反查」这个思路在
> Iris 26.3 下不可行；可行的方向见 §6。

### 2.3 Render pass 归属倒置（V-r3）【实证+仓内引证】

- 26.2：`GameRenderer#renderItemInHand` → `renderAllFeatures(storage)` →
  `PreparedFrame.executeSolid()` 内部**自开自关** render pass；阶段之间是
  「无 pass 状态」。我们的掩码绘制（`renderAtPhaseBoundary`）与
  `PolyMeshGpuRenderer.renderWorldAfterSolid` 都是**在阶段边界自开 pass**。
- 26.3：**调用方**先 `createRenderPass("Item in hand")` / `createRenderPass("Solid")`，
  再把 pass 一路传进去：`FeatureRenderDispatcher.renderAllFeatures(renderPass,
  frame)`（变静态方法）、`LevelRenderer#executeSolid(..., renderPass)`。
  阶段边界身处 vanilla 的 pass 之内——在那里 `createRenderPass` 直接撞

  ```
  Close the existing render pass before creating a new one!
  ```

  （2026-09-18 实机日志：掩码绘制整条被这句挡死，随后镜身管线拿不到掩码纹理
  跟着崩。**这是「开镜崩溃」的头号成因**。）

- 配套删除：`RenderSystem.outputColorTextureOverride` / `outputDepthTextureOverride`
  这两个 26.2 的全局重定向量**被删**，输出目标改由 `createRenderPass` 的
  附件实参显式携带（`ScopeFinalOverlayState` 的目镜框后置重绘已按
  「自己开 pass、显式指定主 target 颜色+深度」适配，方向与 Iris 26.3
  `HandRenderer:128` 的五参写法一致）。
- 配套新增：`FrontendRenderPass#setPipeline` 校验「pass 颜色附件数 ==
  管线 color target state 数」，不等即抛。26.2 可不写由引擎兜底，26.3 必须
  显式 `withColorTargetState(...)`（scope_body 抄本用 `DEFAULT`、scope_text
  用 `TRANSLUCENT`）。**不写 = 开镜即抛**（崩溃类成因之二）。

本轮保留的相应适配：`FeatureRenderDispatcherMixin` 掩码锚点搬到
`prepareFrame` RETURN（在 upload 之后、vanilla 开 pass 之前，见该文件头注）、
`LevelRendererWorldPassMixin` 复用 vanilla 传入的 pass、
`GameRendererMixin` 手部 GPU 绘制挪到 `renderItemInHand` RETURN、
各管线显式 color target。

### 2.4 第一人称渲染一分为三【实证】

`ItemInHandRenderer` 删除，替代为：

- `net.minecraft.client.player.FirstPersonHandsAndItems`：状态/tick 侧
  （`mainHandItem`、装备高度，`tick(LocalPlayer)`），**每 LocalPlayer 一个实例**
  （`player.firstPersonHandsAndItems()`，`KeepingItemRenderer` 的落点）；
- `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer`：渲染侧，
  `submitHandsWithItems(...)` / `submitArmWithItem(...)`，**玩家实体不再传入**，
  改传 `PlayerRenderState` + `FirstPersonHandsAndItemsRenderState`；
- `...state.level.FirstPersonHandsAndItemsRenderState`：渲染状态对象。

证据：Fabric API 26.3 的同名 mixin；Iris 26.3 `HandRenderer.java` 里
`gameRenderer.firstPersonHandsAndItemsRenderer.submitHandsWithItems(tickDelta,
new PoseStack(), submitNodeCollector, playerRenderState, state)` 的实参形态。

**与症状的关系**：签名适配（已完成并保留，含 `tick` 空注入点——
`cancelEquippedProgress` 刻意留空，与上游 1.21.1 一致）。不直接导致裁剪失效，
但它决定了 `KeepingItemRenderer.getRenderer()` 可能为 null（玩家未就绪），
因此调用点统一走 `getCurrentRenderItem()` 空安全封装。

### 2.5 `renderLevel` / `render` / `renderItemInHand` 签名漂移【仓内引证】

- `GameRenderer#render(DeltaTracker, boolean)` → `render()`（无参；partial tick
  改由 `minecraft.getDeltaTracker()` 现取，同帧同对象）；
- `GameRenderer#renderLevel` / `LevelRenderer#render`：去掉 `DeltaTracker`
  与 `Matrix4fc viewRotation`（后者改从 `cameraState.viewRotationMatrix` 取），
  尾部新增 `consistentDepthRequired`（有 post chain 时 true；镜内那一遍传
  `false` = 直接用主深度，与 26.2 无此步骤的行为一致）；
- `GameRenderer#renderItemInHand(CameraRenderState, float, Matrix4fc)` →
  `(CameraRenderState, PlayerRenderState, GpuTextureView)`；
- 手部 pass 绘制前投影换成 `hudProjection`（hudFov、0.05F 近平面，
  GR:679-683；与 Iris `HandRenderer#setupGlState` 里
  `projection.setupPerspective(0.05F, ..., camera.hudFov, ...)` 同构）。

**与症状的关系**：注入点若声明了旧形参，mixin APPLY 阶段抛
`InvalidInjectionException`（2026-09-18 日志 line 108 即此），整条链路
（含掩码）失效——崩溃/全失类成因。已改「空形参」注入，签名免疫，保留。

### 2.6 投影矩阵 UBO 不再可读回【仓内引证+实机日志】

26.2 我们在 CPU 侧做目镜投影凸包（writeHullFill / computeMaskBounds）时，
把 `RenderSystem.getProjectionMatrixBuffer()` 的 UBO `map(true,false)` 读回来
——与着色器消费严格同源。26.3 起该 buffer 无 READ 用途位，
`GlBuffer$Direct.map` 直接抛 `IllegalStateException: Buffer is not readable`
（2026-09-18 实机日志）。虽被 catch，但后果是**每帧回退逐立方体描摹、
掩码形状退化**（高倍镜裁剪边缘不准），且 `computeMaskBounds` 失败是**完全
静默**的——PIP 合成的剪裁包围盒随之缺失。

**保留的适配**：`GameRendererProjectionAccessor` 直接取 CPU 侧
`GameRenderer#hudProjection`（GR:683 正是把它交给 ProjectionMatrixBuffer 编码
进 UBO 的那个 Projection，同源性不降反升；且必须是 hud 系投影而非世界的
`cameraState.projectionMatrix`，否则掩码整体错位）。

### 2.7 Shader 工具链：shaderc + SPIR-V【实证+实机日志】

- `#moj_import <...>` 废除 → `#include <...>`（include callback 解析）。
  旧写法在 26.3 下的实机表现：shaderc 把 `#moj_import` 当未知预处理指令，
  `fog.glsl` 没被引入，报 `'fog_cylindrical_distance': no matching overloaded
  function found`（2026-09-18 实机日志第 1 行）——**开镜即 shader 编译失败**。
- 顶点属性与 varying 必须显式 `layout(location = N)`，且需要
  `#extension GL_ARB_separate_shader_objects : require`（26.3 的 vanilla
  core shader 全部如此，scope 系列是 entity/text/screenquad 的逐字抄本，
  必须同步）。
- vanilla 26.3 的 `entity.fsh/vsh` 新增 **GLINT**（`GlintSampler`/`texCoordGlint`/
  `GlintAlpha`）与 **OIT**（`oit.glsl`：`OIT_ACCUMULATE`/`OIT_ALPHA_ONLY`/
  `executeAlphaOnlyPhase`/`sampleColorForAccumulation`）两段；`text.fsh` 同样
  重排（see-through 分支的 `ColorModulator` 乘序变化）。不跟着同步的后果：
  管线按 define 变体（glint 附魔光效、改进半透明）编译时链接失败或画错。
- 另：`BindGroupLayout.withSampler(name)` →
  `withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER)`；
  `RenderPass#bindTexture` → `setUniform`；
  `TextureTarget` 形参序 `(label,w,h,useDepth,GpuFormat)` →
  `(label,w,h,GpuFormat colorFormat,GpuFormat depthFormat)`（不要深度传 null）。

以上均为「不修则编译/链接失败或开镜崩溃」级，全部保留。

### 2.8 （疑点）`gl_FragCoord` / `ScreenSize` 前提松动（V-r2）【假设】

26.2 掩码采样的三个前提中，有两条在 26.3 出现裂纹：

1. **clip control**：实机日志出现 `GL_ARB_clip_control`。开启后 NDC 的 z 域
   与「纹理行序 vs 帧缓冲像素行序」的对应关系可能被改写——若掩码 target 的
   纹素行序与 `gl_FragCoord` 的 y 向镜像，`maskUv` 采到的是上下翻转的掩码
   （裁剪区域反了，或者全黑→全不裁/全裁）。
2. **`Globals` UBO（`ScreenSize`）在手部 pass 的绑定状态**：vanilla 26.3 的
   `entity.fsh` 只在 `GLINT` 下才引 `globals.glsl`——暗示引擎不保证
   `Globals` 在所有实体类 pass 都有效。若手部 pass 该 UBO 未绑（0 值），
   `gl_FragCoord.xy / ScreenSize` = inf/NaN，掩码采样恒落到边界外
   → 镜身 `insideOcular` 恒 false → **一个像素都不裁**（与「不裁剪」表象一致，
   且完全静默）。

**当前状态：已定案（2026-09-20 §8）——两条假设均被实机截图证伪**。
无光影截图上裁剪与 PIP 全部正常，说明 `clip control` 与 `Globals` UBO 前提
在 vanilla 路径依然成立；scopeUv 永久否决。本节保留作排查记录。

---

## 3. Iris 26.2→26.3：逐项差异（GitHub 双分支实读）

Iris 侧锚定：`IrisShaders/Iris` 分支 `26.2` 与 `26.3`（后者 `b388d57`）。

### 3.1 程序重定向的落点与「后端管线身份」（I-r1）【实证】

- **26.2** `MixinShaderManager_Overrides`：`@Mixin(GlDevice.class)`，钩
  `getOrCompilePipeline` HEAD，命中时
  `cir.setReturnValue(new GlRenderPipeline(renderPipeline, program))`——
  **替换出来的后端管线仍然抱着我们的前端 RenderPipeline**，所以哪怕程序已被
  Iris 换成 HAND，`pass.pipeline.info().getLocation()` 依旧答得出
  `tacz:pipeline/scope_body_clipped`。这是 26.2 光影下 mode 解析成立的原因。
- **26.3** 同名 mixin：`@Mixin(RenderSystem.class)`，钩
  `getCompiledPipelineNullable` RETURN（`redirectIrisProgram`），命中时自建

  ```java
  BackendRenderPipeline.CreateInfo createInfo = iris$createInfo(old.getCreateInfo(), program, vertexFormats);
  FrontendRenderPipeline replacement = new FrontendRenderPipeline(old2.name(),
      new GlRenderPipeline(device, createInfo, program, vertexArray),
      vertexFormats, old2.uniformIndices(), old2.uniforms(),
      old2.colorTargetStates(), old2.wantsDepthTexture(), old2.pushConstantSize());
  ```

  后端那条只剩 GL 状态（见 §2.2），**没有 `info()` 可问**。前端 record 虽有
  `name()` 且按 `old2.name()` 传递，但实机探针显示绘制期据此反查 0 命中
  （§2.2 尾部），对象身份路线不可依赖。

- 附带行为差异：未被 override 表覆盖的管线（我们的 `scope_mask` 正是如此）
  在 26.3 会吃到一句 `Iris.logger.error("Missing program tacz:pipeline/... in
  override list. This is not a critical problem...")`——**无害但吵**，属于预期噪音。

### 3.2 `GlCommandEncoder`：方法与类一起改名（I-r2）【实证】

| | 26.2 | 26.3 |
|---|---|---|
| 类 | `com.mojang.blaze3d.opengl.GlCommandEncoder` | `com.mojang.renderpearl.backend.opengl.GlCommandEncoder` |
| draw setup | `boolean trySetup(GlRenderPass, Collection<String>)` | `void setupDraw(GlRenderPass)` |
| per-draw 状态入口 | `iris$setupState` 注在 trySetup RETURN | **搬走**（见 3.3） |
| custom pass 分支 | HEAD `setReturnValue(true)` + 手写状态 | `setupDraw` 内 FIELD 点 `cir.cancel()` |

我们的 `IrisGlCommandEncoderMixin` 26.2 版同时钩在旧类名+旧方法名上，
`require=0` 软注入 → 26.3 下**静默整条不装**：`applyToGlRenderPass` 从不出场，
`resolveMode` 连跑的机会都没有（这是它自己注释里说的「第二个原因」）。
已按新类名+新方法名适配（保留），空形参 RETURN 无条件应用——custom pass
分支 cancel 时 RETURN 处理器同样不跑，与旧「true 分支」语义等价。

### 3.3 `iris$setupState`：签名变更 + 搬家（I-r3）【实证】

- 26.2：`ExtendedShader#iris$setupState(HashMap<String, TextureViewAndSampler>
  samplers, GpuTextureView albedoTex)`，由 `MixinGlCommandEncoder` 在
  trySetup RETURN 处调（每次 draw setup 一次）。
- 26.3：`iris$setupState(List<BindGroupLayout.UniformDescription> samplers)`
  （`ExtendedShader.java:208`，`FallbackShader`/`IrisProgram` 同签名），
  调用点搬到 **`GlRenderPipeline#bind` RETURN**
  （`MixinGlRenderPipeline#iris$bind`：每次 `setPipeline`/bind 时
  `iris$setupBindings(createInfo.uniforms(), ...)` + `iris$setupState(createInfo.uniforms())`）。

后果：26.2 版 `IrisExtendedShaderMixin` 的处理器声明了
`(HashMap, GpuTextureView, CallbackInfo)`，mixin APPLY 阶段逐个对形参
对不上 → `InvalidInjectionException`，**这条 hook 整个被丢弃并在日志报错**
（2026-09-18 01:49 日志 line 189）——`applyToShaderProgram` 同样从不出场。
已改「空形参」处理器（只要「Iris 刚 setup 完这个程序」这个时机，一个形参
不读），保留。顺带：26.3 的调用频度从「每 draw」变成「每 bind」，语义上
仍覆盖每次绘制（bind 先于 draw），两处谁后写谁生效的顺序无关性不变。

### 3.4 `HandRenderer`：手部 pass 的 26.3 形态【实证】

26.3 版（`pathways/HandRenderer.java`）关键形态：

- 实体提交：`gameRenderer.firstPersonHandsAndItemsRenderer.submitHandsWithItems(
  tickDelta, new PoseStack(), submitNodeCollector, playerRenderState, state)`——
  用的是 **vanilla 渲染器 + Iris 自己的 `SubmitNodeStorage`**；
- 执行：Iris **自己的** `FeatureRenderDispatcher` 实例 → `prepareFrame(storage)`
  → `createRenderPass("Terrain", 主target颜色, 主target深度)` →
  静态 `renderAllFeatures(renderPass, frame)`；
- 一帧两遍（renderSolid=HAND_SOLID / renderTranslucent=HAND_TRANSLUCENT），
  与 26.2 一致；`isHandRendererActive()`/`isHandRenderingSolid()` 的既有
  判据仍然成立；`backup/restoreProjectionMatrix` 包住全场。

**好消息**：我们的 `FeatureRenderDispatcherMixin`（prepareFrame RETURN 锚点）
是**类级注入**，Iris 的自有 dispatcher 实例同样会触发——掩码在 Iris 手部
帧图同样能画。`GameRenderer#renderItemInHand` 在光影下被 Iris 接管为 no-op，
`inHandPass` 由 `IrisCompat.isHandRendererActive()` 补充判定，这一既有设计
在 26.3 依然必要且方向正确。

### 3.5 其他可供参考的同名同构用法【实证】

- `MixinGlRenderPipeline` 在 26.3 还证明：`createInfo.colorTargetStates().size()`
  是 bind 期合法属性（我们的「显式 color target」适配与引擎自检同源）；
- Iris 26.3 `HandRenderer:128` / `MixinLevelRenderer:265` 均按
  「`createRenderPass(名字, 主颜色view, Optional.empty(), 主深度view,
  OptionalDouble.empty())`」开 pass——与我们 `ScopeFinalOverlayState` /
  `PolyMeshGpuRenderer` 的保留写法逐字同构，说明该写法在光影激活下是受支持
  的用法。

---

## 4. 症状 ↔ 差异的对应关系（归因）

| 场景 | 断点 | 差异编号 | 本轮处置 |
|---|---|---|---|
| **光影 + 开镜不裁** | `tacz_ScopeMaskMode` 恒 0：管线身份不可反查（`info()` 删除 + 重定向实体不含前端引用） | V-r1 / I-r1 | ~~退回 26.2 的 `info()` 反射~~ **§8 已修**：按 draw 采样器判别 mode |
| **光影 + 链路全灭（连崩溃日志都有）** | 两个 hook 因改名/签名漂移装不上 | I-r2 / I-r3 | **已保留适配**（新类名/新方法名/空形参）；与回滚不冲突 |
| **无光影 + 开镜不裁** | 掩码采样前提松动（clip control 行序 / Globals UBO 未绑） | V-r2 | **已证伪（§8 截图）**：vanilla 路径健康 |
| **开镜即崩** | 阶段边界自开 pass 撞断言；管线缺显式 color target；mixin 旧形参 APPLY 抛异常；投影 UBO 读回抛异常 | V-r3 / §2.5 / §2.6 | **已保留全部对应的最小适配** |
| **少数枪包目镜不裁（其余部件正常）** | 目镜建模在 `ocular_ring` 子树内，被「物理目镜框无裁剪重画」路径连带收进快照 | 非 26.3 差异，26.2 即存在 | 修复随回滚摘除，列为候选 A（§5） |

## 5. 回滚摘除的两个「行为修复」候选（待实机定案后单独回加）

下面两项不是 26.3 适配，是 09-19 前后的行为修复尝试。本轮一并摘除，
但各自有独立的诊断价值，实机验证后可**单独、最小化**回加：

### 候选 A：目镜嵌在 ocular_ring 子树时被无裁剪重画（强嫌疑 · 与版本无关）

`BedrockAttachmentModel#submitOcularRingPlain` 用
`BedrockRenderSnapshot.captureSubtree(ocularRingPart, ...)` 把整棵
`ocular_ring` 子树收进快照、以**未裁剪**的原版 RenderType 重画（这是「物理
目镜框」的设计行为，上游 `stencilFunc(ALWAYS)` 的等价物）。若枪包把**目镜
镜片**建模在 `ocular_ring` 的子树里（默认包 `scope_aug_default` 即如此），
镜片会跟着这份快照被无裁剪地再画一遍，盖在正确裁剪的结果之上——**观感正是
「目镜贴图没被裁剪」，且与光影无关**。当时实现的修法（快照期间临时摘除
“在 ring 子树内的 ocular 部件、finally 还原”）逻辑自洽、与我行我素链路正交。
**建议：回滚基线上实机先复现 `scope_aug_default` 的不裁现象确认此路径，再
单独回加该修复**（原实现可从摘除前的提交 `7b15f6b` 提取，约 40 行）。

### 候选 B：scopeUv varying（V-r2 的对症但未经证实的药）

顶点侧 `scopeUv = (gl_Position.xy / gl_Position.w) * 0.5 + 0.5` 传入片元
替代 `gl_FragCoord.xy / ScreenSize`。优点：与原点约定、clip control、
`Globals` UBO 绑定状态全部解耦。缺点：当时与其他投机修复捆绑进场，无独立
实机对照（单独上 scopeUv 前后的 AB 对比不存在）。**若 §6 的两态验证证实
V-r2 两条假设之一成立，scopeUv 可作为根治回加**（它比「修 UBO 绑定」
更不依赖引擎内部行为）；若证伪，候选 B 永不再提。

## 6. 建议的下一步（实机验证清单，按成本排序）

1. **无光影 · 掩码可见性**：`RenderConfig.SCOPE_MASK_DEBUG=true` 的 HUD
   预览（ClientSetupEvent 注册的 scope_mask_debug 元素仍在），开镜看左上
   是否有随枪动的白色形状。
   - 有形状但不裁 → 采样侧（V-r2）：把 `scope_body.fsh` 的 SCOPE_MASK 首行
     暂换成 `vec2 maskUv = gl_FragCoord.xy / vec2(textureSize(ScopeMaskSampler, 0));`
     做一次 5 分钟实验：能裁 = `Globals/ScreenSize` 假设成立（候选 B 回加）；
     还不裁再看 y 翻转：`maskUv.y = 1.0 - maskUv.y`（clip control 假设）。
   - 无形状 → 掩码根本没画上：查 `drawMask` 的日志行
     （`Ocular mask drawn: N indices`）与 `ScopeMaskGeometry` 登记路径。
2. **候选 A 复现**：默认包 `scope_aug_default` 开镜，其余部件裁而目镜不裁
   → 实锤 ring 子树路径，回加候选 A。
3. **光影链路**：HUD 掩码确认有形状后开光影，日志里
   `resolve scope render pass` 的一次性 warn（`NoSuchMethodException: info`）
   是 26.3 的**预期**坏点（V-r1），届时评估 §7 的根治方向。

## 7. 光影下 mode 判定的根治方向（备选，按侵入度排序）

1. **按 sampler 存在性判定**：HAND 程序绘制时 `GlRenderPass.samplers` 里
   出现 `ScopeMaskSampler` 的条目，说明该 draw 的前端 RenderType 绑了我们的
   掩码采样器（Iris 不换 sampler 表，只换程序）——与对象身份无关。被裁掉的
   09-19 探针本来就为验证它埋了点（`probeSamplerPresence` 只统计不改渲染），
   样本表明该 key 确实随 draw 到场。可进一步：samplers 里除
   `ScopeMaskSampler` 外仍无法区分 mode=1（镜身）与 mode=2（准星）——
   需要给两类 RenderType 各绑一个**定值区分用的小 uniform/哑纹理**。
2. **按附件/阶段判定**：mode 只关心「这次 draw 属不属于 scope 几何」；
   而 scope 几何只可能出现在手部 pass 且由我们的 6 条 RenderType 发出。
   可以反过来：不设 uniform，让 `IrisShaderCreatorMixin` 注入的 dormant 分支
   读一个**由我们每帧一处写入的全局 uniform**（如 iris 自定义 uniform 管线
   或一张 1×1 数据纹理），Key=「本帧手 pass 内 scope 绘制进行中」，
   由 `ScopeBodyRenderTypes` 在自定义 RenderType 的 draw 前后打括号置位。
3. **保持 26.2 方案 + 等 vanilla 给回查口**：`FrontendRenderPipeline` 已含
   `name()`（26.3 新事实，Iris 重定向时也传递了 `old2.name()`）。若后续版本
   （或 Fabric API 增补）把「draw 期可取当前前端管线名」变成官方通道，
   `info()` 方案可原位复活。目前不作为主路的唯一原因：绘制期拿不到那个
   前端对象（GlRenderPass.pipeline 是后端对象）。

> 方向 1/2 的实现量都不大，但必须在「无光影链路已验证恢复健康」之后再做，
> 否则会重演「同时修三个断点、每个都没修死」的本轮教训。

---

## 8. 实机截图定案与最终修复（2026-09-20 补记）

**截图定案**。回滚构建上的两张实机截图（16.16.32 无光影 / 16.16.35
Complementary Reimagined）：
- 无光影：掩码、裁剪准星、PIP 放大全部正常 —— **§2.8（V-r2）的两条假设
  双双证伪**，vanilla 路径健康，「无光影链路恢复」这一前置条件达成。
- 开光影：镜内纯黑、HUD 掩码预览与无光影时**逐像素一致** —— 掩码内容
  正常、采样路径一致，唯一断点收束为 **`tacz_ScopeMaskMode` 恒 0**
  （V-r1/I-r1）：注入分支存在但 body 永不 discard，黑屏 quad/镜身把 PIP
  合成结果盖死。候选 B（scopeUv）**永久否决**，不再回加。

**已落地的修复 = §7 方向 1**（前置条件已满足，允许施工）：
- mode 判别不再依赖任何管线对象身份。`IrisScopeMaskState#resolveMode`
  改按本条 draw 的 `GlRenderPass#samplers` key 集合判别（该绑定表按 draw
  携带，Iris 换程序时不受影响；26.2/26.3 两版 vanilla 均存在——同文件的
  `resolveMaskTextureId` 早就在读它）。
- mode 1/2 的区分手段（§7-1 预留的「各绑一个哑纹理」）落地为**标记采样
  器** `ScopeMaskMode2Sampler`：准星（reticle / reticle_emissive）与裁字
  （scope_text_clipped）三类渲染类型的 bind group 多声明一个
  COMBINED_IMAGE_SAMPLER，绑的还是同一张掩码纹理；GLSL 声明但从不采样
  （`scope_body.fsh` / `scope_text.fsh` 各加两行）。key 存在→mode 2，
  只有 `ScopeMaskSampler`→mode 1，都没有→走 26.2 的 `info()` 老路兜底
  （26.2 分支行为逐字节不变）。
- 涉及文件：`IrisScopeMaskState`（resolveMode 主路更换）、
  `ScopeBodyRenderTypes` / `ScopeTextRenderTypes`（layout 声明 + 绑定）、
  两个 fsh（uniform 声明）。注入 GLSL 零改动——注入分支的掩码 UV 本来就用
  `textureSize(tacz_ScopeMaskSampler, 0)`，不吃 ScreenSize。

**Iris 26.3 钩子链路静态复核（全 PASS，零适配改动）**：`ShaderCreator.link`
七参签名与四处 `createShader(name, ShaderType, source)` 调用点（:186-195）
→ `@ModifyArgs` 命中；FRAGMENT 名卫未变；`ShaderKey.HAND_*` 全家在
（:82/96/102…）；`IrisApi#assignPipeline(RenderPipeline, IrisProgram)`
与 `IrisProgram.HAND` 在 `common/src/api/java` 源码集原样保留；
`MixinGlRenderPipeline.java:98` 仍是每次 bind 的
`iris$setupState(createInfo.uniforms())` 调用点（`IrisScopeMaskState`
的 RETURN 注入位）。

**状态声明（2026-09-20 二轮更新）**：编译通过（CI 绿）。首轮实机验证被
一个**更早的崩溃**抢先拦截（开光影开镜 NPE，§9 —— 与本修复无关的
vanilla 编译缓存冷 + Iris 不查 null），mode 采样器判别本身**尚未被实机评估**。
§9 预热修复落地后，验证清单不变：光影开镜看 PIP 恢复；低倍 sight
（reticle-only 掩码）确认镜身不被啃洞；MK5HD 镜内文字旧案复测。

---

### 附：本轮回滚的具体摘除物（存档索引）

| 摘除物 | 原位置 | 摘除理由 |
|---|---|---|
| 探针计数器组（probe\* 15 枚）+ `logProbeOnce()` | `IrisScopeMaskState` | 诊断装置，非修复；链路未定前不留 |
| `NAME_BY_BACKEND_PIPELINE` 弱键表 + `notePipelineBinding` / `noteCompiledBinding` / `syncIrisPipelineBindings` | `IrisScopeMaskState` / `ScopeBodyRenderTypes` | 「按对象身份反查」路线，实机证否 |
| `pipelinePath()` 三路反查（debugLabel 回退等） | `IrisScopeMaskState` | 同上 |
| `hasMaskSampler` / `probeSamplerPresence` | `IrisScopeMaskState` | 与 §7-1 知识点一并存档，非当前修复 |
| `FrontendRenderPassPipelineMixin` | `mixin/client/iris/` + iris json | 前端登记装置，实机证否 |
| `ScopeClipProbeTally` / `tacz$tallyScopeClipProbe` | `BedrockAttachmentModel` | 诊断装置 |
| ring 子树快照修复 | `BedrockAttachmentModel` | 候选 A，待复现后单独回加 |
| `scopeUv` varying（4 个 shader） | `scope_body.*` / `scope_text.*` | 候选 B，待两态验证 |
| `RenderCrosshairEvent#renderMaskDebug` blit 挂钩 | `RenderCrosshairEvent` | 与 HUD 元素预览重复（后者保留） |

---

## 9. 开光影开镜直接崩溃：根因与预热修复（2026-09-20 二轮）

**实机反馈**：§8 修复构建上，**开光影开镜直接崩溃**（`RawOutput.log`）：
`NullPointerException: Cannot invoke FrontendRenderPipeline.backendRenderPipeline()`
位于 Iris 26.3 挂在 `RenderSystem.getCompiledPipelineNullable` RETURN 的
`redirectIrisProgram` 处理器（RenderSystem.java:581）——**它从不检查
`cir.getReturnValue()` 是否为 null**，连「查 override 表」都排在取旧程序之后。
崩溃帧位于 `HandRenderer.renderSolid → renderAllFeatures →
PreparedRenderType.drawFromBuffer` —— 我们自定义 scope 管线在某个 pass 里的
第一次 draw。同一秒 `Worker-Main` 上有两条
`Couldn't find source for VERTEX shader (tacz:core/scope_body)`。

**根因链**：我们的 scope 管线是类加载时静态构建、**首次使用才进 vanilla
编译缓存**（vanilla 的资源重载预编译波覆盖不到它们）。26.3 的按需编译
不是即用的（miss/异步未完成 ⇒ `getCompiledPipelineNullable` 返回 null）。
于是：开光影（Iris 世界管线已接管）后第一次开镜 → 第一次 scope draw 撞上
缓存冷 → null → Iris 处理器 NPE。无光影链路没有这个处理器，永不触发。
「couldn't find source」是【缓存为什么冷】的同帧并发证据，两者关系待
实机复测界定（§10 有对应的观测点）。

**修复（已落地）**：新类 `ScopePipelinePrewarm`（+ 各持管线类的
`prewarmCompiledPipelines()`），在 `END_CLIENT_TICK` 对全部自定义管线
（ScopeBodyRenderTypes 7 条、ScopeTextRenderTypes、ScopeMaskRenderer、
ScopePipRenderer、PolyMeshGpuRenderer 3 条）逐条
`RenderSystem.getCompiledPipeline`：缓存提前热，Iris 处理器永远拿不到
null。三个细节：
- 每发调用用 Iris 自己的 `ImmediateState.bypass`（public static boolean，
  26.3 源码实读存在）包一层 —— 否则【预热调用本身】就是同款 NPE 的触发器；
- 反射找不到该字段时降级为裸调（仅无光影安全），不炸；
- 快照式日志：只在管线状态变化时各打一行（全 OK / 有 PENDING / FAILED），
  失败管线进 200 tick 退避，不每 tick 重编刷屏。

预期效果：崩溃消失；若某条管线连预热都编不过（「couldn't find source」
一族复现），预热的 FAILED 日志会直接点名 —— 那将直接锁定 §10 的待决项。

---

## 10. 无光影、特定环境不裁剪（Nether / End / 夜 / 水下）：现状与判定协议

**现象**：无光影时，白天主世界正常（§8 截图 16.16.32），但**下界、末地、
主世界夜晚、水下**开镜不裁剪。四个条件的公共特征是**天空光弱/环境昏暗**。

**已排除（静态）**：
- 我方 Java 链路无任何环境/维度分支（亮度、流体、日夜、维度全清
  `grep` 无命中）；掩码锚点 `FeatureRenderDispatcher#prepareFrame` RETURN、
  `isInHandPass`、`ScopeMaskGeometry` 登记与清空时序全部环境无关。
- 掩码管线 `core/position` 的 `apply_fog` 在 ~0.5m 视距处衰减≈0，
  「雾把掩码颜色染暗」不成立；
- `resolveBodyRenderType` 的 gate 链（config / IrisCompat / 几何非空 /
  viewmodelClip / syncToMaskTarget）无环境输入。

**实机判定（2026-09-20 三轮，用户）**：**B 族成立**——掩码预览正常却不裁；
且补充关键线索：**封闭空间无恙，仅开放空间发作**。四个发作条件的公共面
因此收窄为「开放天空、天空光弱/自定义雾」，而 B 族的采样算式里唯一可动
的引擎全局量就是 `Globals` UBO 的 `ScreenSize`（maskUv = gl_FragCoord /
ScreenSize）。

**硬化已落地（同日）**：`scope_body.fsh` / `scope_text.fsh` 两处 maskUv
分母改为 `vec2(textureSize(ScopeMaskSampler, 0))` —— 掩码 target 与主
target 同尺寸，UBO 健康时与 `/ScreenSize` 逐位相等；UBO 一旦被写歪/未绑，
本算式不受其影响。与注入 Iris 的 GLSL 使用的算式自此全对齐。
若硬化后 Nether/End/夜/水下仍不裁 → 轮到 C 族（render-type 回退路径），
届时补 debug-gated 一次性日志再定。

**同轮事故记录（二轮崩溃的另一半）**：第二轮实机又报「无光影开启高倍镜
直接崩溃」——崩溃管线是 `scope_body_clipped`，根因是 marker 采样器的
GLSL 声明放进了共享 fsh 的 `#ifdef SCOPE_MASK` 下：mode-1 管线的 shader
定义了 `ScopeMaskMode2Sampler` 却没有对应 bind-group 条目，
`generateBackendCreateInfo` 校验抛
`Unable to find shader defined uniform (ScopeMaskMode2Sampler)`。
已移位到 `#if defined(SCOPE_MASK) && defined(SCOPE_MASK_INVERT)` ——
声明方与 layout 方一一对应。教训与 r46（Fog 缺声明 → 编译不过）互为对偶：
**layout 声明与 GLSL 声明必须逐管线逐名字双向配对**。

**同批日志里的未决项**：两轮崩溃日志均有
`couldn't find source/preload shader tacz:shaders/core/scope_body.vsh`
（文件确在 jar，构建无过滤）。疑似运行时资源管理器瞬时态（枪包同步
重建 tacz_resources 命名空间时并发命中懒编译波）。§9 预热器的
FAILED/PENDING 日志会在实机上直接点名——若属瞬时态，退避重试自然愈合。

## 十一、四轮实机（2026-09-20 晚）：shader 路径根因落地 + vanilla 问题 2 精确化

### 11.1 shader 路径不裁剪 —— 根因实锤

`latest.log`（18:15:48 起）抓到决定性一行：

```
WARN [TACZ Scope] Iris scope-mask bridge failed to resolve scope render pass
  java.lang.NoSuchFieldException: samplers
    at IrisScopeMaskState.resolveMode(...)
  ← com.mojang.renderpearl.backend.opengl.GlCommandEncoder.setupDraw 回调栈
```

26.3 的 `GlRenderPass`（包已从 26.1 的 `blaze3d` 系挪到 `renderpearl`）
**没有 `samplers` 字段**——renderpearl 重构把它改了名。`resolveMode`
按单名反射 → 抛异常 → 被 catch → `logOnce` 打一次 WARN → **每条 draw
都返回 mode 0**。注入 HAND 着色器的 `if (ScopeMaskMode == ...)` 分支全部
走 else → 没有任何裁剪。这正是「掩码本身一切正常（预览可见、prewarm
13/13、diag 表全 OK）却完全不裁」的完整成因链。

**对照**：纹理 id 反射同样读 `samplers`，但它失败时静默回退
`ScopeMaskTarget.current()`（本就是我们的 FBO，永远正确）——所以 mask
采样一直健康，「死掉的只有 mode 判别」。这一次的崩溃恰好把异常打到了
日志里才得以定位。

**修复（本轮提交）**：两个调用点（`resolveMode` /
`resolveMaskTextureId`）改为共用的容错查询 `samplersMap(pass)`：

1. 名字列表直取（`samplers` 排最前 = 26.2 兼容，其余为猜测名）；
2. 兜底：该类（含父类）第一个实例级 `Map` 字段 —— GlRenderPass 上
   Map 字段密度极低，错拿最坏后果是 containsKey 恒 false（= 回到未修复
   状态，不会更糟）；
3. 再兜底：一次性 WARN dump 该类全部实例字段（名:类型）——实机日志
   一轮即能读出 26.3 真实字段名，可再点名 hardcode。

曾计划加 `vanilla-internals-dump` CI 工作流（javap loom 缓存的 26.3
merged jar，26.1+ 无混淆故字段名真实可读），但本沙箱的 GitHub 凭据
没有 `workflows` 权限、无法推送 `workflows/` 下的新增文件，已放弃此路。
真实字段名改由第 3 级兜底的一次性 WARN dump 从实机日志回收 ——
若两轮容错（名字列表/Map 扫描）任一命中，其实根本不需要真名。

### 11.2 vanilla 路径问题 2 —— 用户精确化（推翻「全图不裁」框架）

用户四轮反馈：**vanilla 路径从来不是全不裁**。镜身/世界的裁剪在所有
环境都正常，**只有目镜贴图那一小块不裁**；HUD 掩码预览正常；且与
光照强相关：白天开阔地无恙，但 **Y ≤ -39 以下的白天也发作**、下界/
末地/夜晚/露天水下发作，封闭照明空间无恙。Y = -39 = 主世界
minY(-64) + 25，是原版基岩雾变暗带的上边界 —— 但「下界无天空光」
同样发作，说明机制不是雾带本身，而是**环境暗 ⇒ 目镜玻璃可见**。

**用户判据回答（四轮，2026-09-20 晚，附图三张）**：
- 纠正记录：用户从未说过「亮房间正常」，原文是「**主世界白天的封闭
  房间内正常**」；且强调 Y≤-39 的白天也发作 ⇒ **判定''与亮度
  无关''**，此前『暗玻璃在暗背景显形』假说连同『亮房间无恙』一起
  作废，改录『发作环境 = Nether/End/夜/水下/深 Y（Y≤-39）』这组
  共同点待重定（雾存在性是当前候选，基岩雾带、水雾、维度雾都在列）。
- 异物形态（截图实测）：**饱和亮的绿色多面棱块**（菱状部面+细点纹理，
  **铁定晶体检貌/玻璃质棱面**），贴满孔径，vanilla（水下）
  与光影（Complementary 夜）下同时存在 —— 不是黑玻碟，不是蚀刻线。

**静态取证（同轮，全部判定）：**
- 默认枪包 14 个瞄具的 `ocular*/division*/crosshair*/red/dot` 骨
  骼贴图 UV 区逐个采样平均色：全部近似中性深灰（rgb ≤ 90），
  **无一绿色** => 绿色不来自瞄具贴图原色。
- mod 自身资源/枪包其余贴图：同样无匹配的饱和绿 => 不来自资源。
- `ocular_ring` 在全部 geo 中与 `oculjar` 同级：环重画（含光影下
  ScopeFinalOverlayState 那条 ring-final）**不会带出镜片** => 排除。
- `scope_pip.fsh`（合成着色器）逐行读：只采样画面拷贝 + 内掩码
  约束，**自身不画任何镜片/绿调色** => 绿不是合成器生成。
- 环境输入扫描：整个 ocular/body/准星提交链 **无任何环境字段**；
  唯一雾输入在 PIP 抓取（renderScopeView 的 FogRenderer buffer）。

**存疑两端（需实机判据收敛）：**
- **甲：绿=PIP 放大内容本身**。水下海藻恰好是柠檬绿系点纹；Catmull-Rom
  在暗环境低信噪比下重建出的块状伪影与截图形状相似度不低。若用户
  的「贴死镜片」观感实为画面中心锁定（合成以屏幕中心放大），
  整案翻转为「暗环境 PIP 内容劣化」，不再是不裁剪。
- **乙：绿=某条几何贴片绕裁**。那必是一笔未被 mode-1 discard 的提交
  （同批镜身都裁了÷仅它没裁），但静态扫描未找到该路径。

**判据设计（与用户同办，四轮终）：**
1. 摇枪：绿块「钉死镜片」（视角动它不动）还是「随视角移动」（=世界内容）？
2. 同一失败环境对纯夜空/纯色墙瞄准：绿还在 → 几何贴片；绿消失 → 放大内容。
3. `ScopePipDebugPaintLens`=true：孔径变纯品红且绿没了 → 绿=合成内容；
   品红+绿并存 → 绿在合成之上（几何）；完全没有品红 → PIP 没在跑（绿色
   与 PIP 无关）。
4. 26.2 同环境对照截图：若 26.2 也出 → 上游模型/绘制老问题，不是 26.3 新病。
- vsh-FileNotFound 谜团：本批日志未再现（预热器落地后两轮干净），
  维持「枪包资源重建并发命中懒编译」瞬时态论，不单独投入。


## 十二、五轮（2026-09-20 深夜）：对照 26.3 真源码定案 —— §11.1 的容错查询在 26.3 上**不可能命中**

> 数据来源：GitHub `mc-dataminning/build-changes`（26.3 反编译源，
> `src/com/mojang/renderpearl/...`）与 Iris `26.3` 分支源码，均逐字读过。
> 沙箱无 JDK，本节改动只经 CI `compile-check` 验证，**未做实机验证**。

### 12.1 §11.1 三级容错为何仍是死路

26.3 `GlRenderPass` 全部实例字段（真源）：
`encoder, device, defaultScissorState, pipeline(GlRenderPipeline), vertexBuffers[16],
vertexBufferDirty, indexBuffer, indexType, indexBufferDirty, scissorState,
scissorStateDirty, uniforms(ReferenceList<Object>), dirtyUniforms(BooleanList),
anyUniformDirty, pushConstants(GpuBufferSlice), pushConstantsDirty, colorAttachmentCount`。

- **没有任何 `Map` 字段**：绑定表是按 uniform **下标**存放的
  `ReferenceList<Object>`，名字信息在后端已彻底丢失 ⇒ §11.1 的
  「名字列表 / 首个 Map 字段」两级都不可能命中，只会走到第 3 级 WARN dump。
- `GlRenderPipeline` 也**没有 `info()`**（26.2 老路 `resolveModeUncached`
  按 location 反查）⇒ 恒 0。
- 结论：26.3 上 `resolveMode` 两条路全死，`tacz_ScopeMaskMode` 永远 0，
  与 §11.1 描述的症状链完全一致；但 §11.1 那次提交并不能修好它。

### 12.2 名字信息在哪：前端 `FrontendRenderPass` / `FrontendRenderPipeline`

- `FrontendRenderPass` 字段：`backend(RenderPassBackend), device,
  boundPipeline(FrontendRenderPipeline), vertexBuffers, indexBuffer,
  protected HashMap<String,Object> uniforms, constantsPushed`。
  `setUniform(name, ...)` 先写这张按名字的表，再按
  `boundPipeline.uniformIndices()` 转发到后端下标；`setPipeline` 时把整张表
  重新按新管线下标灌一遍。
- `FrontendRenderPipeline` 是 record：`name, backendRenderPipeline,
  vertexFormats, uniformIndices(Object2IntMap<String>), uniforms(List<UniformDescription>), ...`。
- **Iris 26.3 `MixinShaderManager_Overrides`** 重定向到 HAND 程序时新建的
  `FrontendRenderPipeline` **原样沿用 `old2.uniformIndices()/uniforms()`** ⇒
  即便管线被换成 Iris 的，「这条管线声明了 `ScopeMaskSampler` /
  `ScopeMaskMode2Sampler`」这个事实在前端对象上仍然保留，且按 draw 精确
  （`PreparedRenderType#draw` 每次先 `setPipeline`）。
- 这与 §2.2 被实机否掉的「按管线**身份**（location/debugLabel/对象表）反查」
  不同：这里查的是管线的**uniform 声明表**，Iris 换管线不会改它。

### 12.3 本轮修复（三处，均有 26.2 兜底）

1. `IrisFrontendRenderPassMixin`（新，`@Pseudo` + `require=0`）：在
   `FrontendRenderPass` 构造 RETURN 处调 `IrisScopeMaskState.noteFrontendPass(this)`，
   建立 **后端 GlRenderPass → 前端 FrontendRenderPass** 的弱键弱值表
   （构造器首参即 `backend`；两者一对一同生同灭）。
2. `IrisScopeMaskState.resolveMode`：在 `setupDraw(GlRenderPass)` hook 里
   由后端找回前端 → `boundPipeline` → `uniforms()` 声明表按名判 mode
   （含 `ScopeMaskMode2Sampler`→2，含 `ScopeMaskSampler`→1，否则 0），
   结果按 `FrontendRenderPipeline` 实例缓存。声明表拿不到时退到前端
   `uniforms` HashMap 按 key 查（注意：那张表在同一 pass 内跨 draw 只增不删，
   仅作最后兜底）。`samplersMap()` 同步改为优先返回前端表，
   `resolveMaskTextureId` 因而也能按名字拿到 `TextureViewAndSampler`
   （`getGlTextureId` 已会 `view()` 解包），拿不到仍回退 `ScopeMaskTarget.current()`。
3. **掩码 pass 的雾污染**（与光影无关，vanilla 亦受影响）：
   `MASK_PIPELINE` 用 `core/position.fsh`，输出
   `apply_fog(ColorModulator, ...)`；而 `bindDefaultUniforms` 绑的 `Fog` 是
   `RenderSystem.getShaderFog()`，**手部 pass 期间仍是 `FogMode.WORLD`**
   （`GameRenderer` 直到屏幕特效之后才 `setShaderFog(NONE)`，真源 l.712）。
   于是掩码的 R=1 / G=进度会按顶点距离往 `FogColor` 混：
   - 水下：环境雾 start=-8、end=96×waterVision，刚入水 waterVision≈0 ⇒
     end≈0，距相机 0.1 的手部几何雾系数≈0.9 ⇒ R≈0.1 <0.5 ⇒ **不裁**；
   - 失明/黑暗效果（雾终点只有几格）同理；
   - 「预览看着正常」是因为雾色暗时 R 只是"红得没那么亮"，肉眼难辨。
   修复：`ScopeMaskRenderer.drawMask` 在 `bindDefaultUniforms` 之后显式
   `pass.setUniform("Fog", fogRenderer.getBuffer(FogMode.NONE))`（空雾 UBO：
   FogColor=0、起止=MAX_VALUE ⇒ `apply_fog` 恒等），`FogRenderer` 经
   `GameRendererProjectionAccessor#tacz$getFogRenderer` 取得。
   这条**只解释水下/失明类**环境；Nether（dimension fog_start 10）与夜晚
   开阔地手部几何距离远小于雾起点，不受此机制影响 —— §11.2 的绿棱块
   问题仍按其判据协议独立追。

### 12.4 另一个实锤：Iris 26.3 根本不会在后端绑定我们的自定义采样器

Iris `MixinGlProgram.iris$samplerBinding` 只认识 `Sampler0/1/2/CloudFaces`
（+Sodium 名），`ScopeMaskSampler` / `ScopeMaskMode2Sampler` 得到 binding -1
⇒ 光影下走 `setUniform` 的 mask 纹理**永远不会被后端绑定**。因此光影路径
的 mask 纹理**必须**继续由 `IrisScopeMaskState` 的 GL 直绑路径
（`resolveMaskTextureId` → `ScopeMaskTarget.current()`）供给 —— 现状即如此，
本节只是把「为什么 setUniform 那条路在 Iris 下无效」记成定论，勿再回头试。

`Missing program tacz:pipeline/scope_mask` 是 Iris 覆盖表未命中的一次性
提示（非致命），与裁剪无关。

## 十三、附带案：原版类型配方在 JEI 中「静默消失」（2026-09-20 深夜）

**症状**：`data/tacz/recipe/*.json` 与枪包里所有 `minecraft:crafting_*` 配方
在 JEI 查不到；`tacz:gun_smith_table_crafting` 不受影响。

**证据链（latest.log 18:15:47）**：
`[TACZ Recipe Viewer] Refreshing after gun-pack sync` → `Stopping JEI` →
`Loaded 2042 vanilla recipes from the client recipe registry` → 聊天栏
`This fabric server does not provide recipes to JEI`。

**根因（JEI 26.2/26.3 源码 `ClientLifecycleHandler#registerEvents`）**：
`AFTER_RECIPES_UPDATED` 的监听器是
`if (!receivedRecipeSync) Internal.clearClientRecipes(); receivedRecipeSync=false; stop; start`。
`receivedRecipeSync` 只由 Fabric 真实的配方同步包置位、被第一次启动消费。
我们的 `RecipeViewerReloadBridge` 在枪包缓存到达后手工触发该事件 ⇒ 第二次
进入时标志为 false ⇒ **服务端同步的配方表被清空**，JEI 退到
`VanillaClientRecipeLoader`（只从 vanilla pack 加载）。2042 正好是原版配方数。
`gun_smith_table_crafting` 幸存是因为我们的 JEI 插件从枪包缓存构建，不走配方表。

**修复**：`refreshJei()` 改为优先调用 JEI 26.3 的
`mezz.jei.common.Internal#restartJei()`（同样 stop/start，但**不清**同步配方）；
该 API 不存在（老 JEI）时才回退旧事件并 WARN。
未实机验证，验收判据：日志中不再出现 "Loaded N vanilla recipes from the client
recipe registry" / "does not provide recipes to JEI"，JEI 可查 `tacz:gun_smith_table` 等配方。

## 十四、附带案：方块掉落「双份 + 紫黑无名物品」（2026-09-20 深夜）

**症状**：挖两格方块掉两个；挖 workbench_a/b/c 掉落物贴图紫黑、名字
`block.tacz.workbench_x`（gun_smith_table/statue/target/target_minecart 不受影响）。

**根因（对照 26.3 反编译 `LootPool`/`LootPoolEntryContainer`/`LootItemConditionTypes`/
`LootItemFunctions` 与 `generated/data/minecraft/loot_table/blocks/*.json`）**：
26.3 重写了战利品表 JSON 架构，而我们 6 份 `data/tacz/loot_table/blocks/*.json` 仍是旧架构。
`RecordCodecBuilder` **忽略未知键**，所以旧文件「解析成功」但：
- `"conditions": [...]` → 26.3 是 `"condition": {...}`（单值，多条用 `all_of`）→ 旧键被丢弃 ⇒
  `block_state_property`（26.3 已改名 `match_block {blocks, state}`）根半边过滤失效 ⇒ 两半各掉一个；
- `"functions": [...]` → 26.3 是 `"modifier": ...`（单对象或内联数组）；函数对象内 `"function"` → `"type"`
  ⇒ `copy_custom_data BlockId` 被丢弃 ⇒ 掉落物无 BlockId ⇒ `GunSmithTableItem` 读到 `tacz:empty`
  ⇒ 无模型（紫黑）、无索引名（回退 `block.tacz.workbench_x`）。
  gun_smith_table/statue 不需要 BlockId 所以只有「双份」问题；target 只丢了 copy_name。
- 另外 `AbstractGunSmithTableBlock#playerWillDestroy` 对「挖非根半边」手工 `popResource` 一份克隆，
  与根半边经 `updateShape→AIR→destroyBlock(drop=true)` 的战利品表掉落叠加，也会双份。

**修复**：
1. 6 份战利品表重写为 26.3 架构（`condition`/`modifier`/`type`/`match_block`/`random_sequence`）。
2. 删除 `playerWillDestroy` 里的手工掉落（战利品表 `match_block` 已保证只根半边掉、且拷 BlockId）。
3. 枪包 `tacz_loot_injectors/*.json` 走 `LootTableInjection.fromJson`，新增
   `LegacyLootCompat#migrateSchema`：`conditions/functions/function/condition/block_state_property/
   alternative/裸{min,max}` → 26.3 写法，幂等；默认枪包的 `spawn_bonus_chest_taurus943.json`
   （旧 `functions`+`set_nbt`）由此覆盖。未实机验证。

## 十五、附带案：装任意第三方枪包即进不去存档（2026-09-21 latest.log 00:15:57）

**日志**：`Registry loading errors: Errors in registry minecraft:recipe → Failed to parse
duyupack:attachments/1p87 ... No key fabric:type in MapLike[{"type":"tacz:nbt",...}] /
Not a string: {"tag":"c:ingots/iron"}` × 十余条 → `Failed to load registries due to errors`。

**根因**：26.3 把 `minecraft:recipe` 当动态注册表加载（`RegistryDataLoader`），任一元素
codec 失败即整表失败、存档拒绝加载。`GunSmithTableSerializer.INGREDIENT_CODEC` 直接用
`Ingredient.CODEC.fieldOf("item")`，而枪包材料的旧写法（`{"tag":..}` / `{"type":"tacz:nbt",..}`）
只在 `GunSmithTableIngredient#getIngredient()` 的 `normalizeLegacy` 里被改写 —— 那是 Gson
路径（GUI/JEI）走的；注册表路径绕过了它。默认枪包材料恰好全是新写法，故此前未暴露。

**修复**：`INGREDIENT_CODEC` 的 `item` 改用 `ExtraCodecs.JSON` 读原始 JSON，交给延迟解析构造器，
与 Gson 路径统一（解析失败只是该材料为空 + ERROR 日志，不再炸注册表）。编码方向回写解析后的
Ingredient 或原文。未实机验证；验收：装 duyupack 能进存档，且 log 无 `Registry loading errors`。

## 十六、附带案：第一人称高模（GPU 烘焙路径）两症状同源（2026-09-21）

**症状**：① 无光影：mesh 枪在视界内相对漂移，只有朝正北才跟手；② 光影下：第一人称
mesh 枪「拉伸成很多片」。第三人称/掉落物两种模式都正常（世界表消费点没动过）。

**根因（单一改动）**：26.3 移植时把 `PolyMeshGpuRenderer.renderAfterSolid()` 从
`renderAllFeatures` 内 executeSolid 之后挪到了 `GameRenderer#renderItemInHand` RETURN
（理由：pass 内不能再开 pass）。该点两个前提同时失守：
- 26.3 `renderItemInHand` 源码：`modelViewStack.pushMatrix().mul(viewRotationMatrix)`
  … `renderAllFeatures` … `modelViewStack.popMatrix()` → RETURN 处 MV 栈已 pop，
  绘制核心取到的 MV_draw = I，pose_bone 丢相机旋转层 ⇒ 枪固定在视角空间（正北
  viewRotation 只剩俯仰故近似正常）。与 26.2 首版 0ea0fb6 / 世界表 renderLevel 560 同病。
- Iris 26.3 `MixinGameRenderer` 把 vanilla `submitHandsWithItems` Redirect 成 no-op，
  手部由 `HandRenderer#renderSolid/renderTranslucent` 在 `LevelRenderer.render` 内自
  调 `renderAllFeatures`。HAND_DRAWS 在那里登记、VBO 也在那里烘焙
  （`ImmediateState.isRenderingLevel=true` ⇒ `MixinBufferBuilder` 换成 `IrisVertexFormats.ENTITY`
  宽 stride）；拖到 vanilla RETURN 消费时已出 render 括号，`MixinRenderType#format` 不再
  扩展 ⇒ 用 36 字节 stride 解读宽格式 VBO ⇒ 「拉伸成片」（MESH_LOADER.md 记录过的同形态）。
  A5 stride 哨兵测的是 `DefaultVertexFormat.ENTITY`，Iris 换的是另一个格式对象，不响。

**修复**：`FeatureRenderDispatcherMixin#tacz$polyMeshAfterHandSolid` —
`@Inject(renderAllFeatures, INVOKE PreparedFrame.executeSolid, shift AFTER)`，把形参
`renderPass` 传进 `renderAfterSolid(RenderPass)` 复用录制（世界表同法），不再自开 pass。
vanilla 与 Iris HandRenderer 都在各自 push/pop 之间调用 renderAllFeatures，MV 栈顶 = 手部
MV；Iris 下仍在 render 括号内，格式与 HAND program 一致。另加 `isRenderShadow` 早退
（Iris ShadowRenderer:603 也调 renderAllFeatures）。GameRendererMixin RETURN 处的调用删除。
**未实机验证**；验收：无光影八朝向跟手；光影下第一人称 mesh 枪形态正常且有光影光照。

### 十六-2 复测回报（2026-09-21 latest.log 00:52）：位置/形态 PASS，但 GPU 烘焙首帧即回退

`[TacZMeshLoader] GPU hand mesh pass failed; falling back to collector path for this session`
`Caused by: IllegalStateException: Close the existing render pass before performing additional commands`
栈：`RenderType.prepare → RenderSetup.prepareTextures → TextureManager.getTexture → registerAndLoad`
← `drawViaRenderTypeCore` ← `renderAfterSolid` ← `HandRenderer.renderSolid:129`。

- **烘焙没跑（帧数）**：§16 把消费点搬进 pass 内部（复用 externalPass）后，`prepare()` 的
  贴图懒加载上传撞了「pass 内不许发其他命令」——kar98un 全 GPU 提交，没有 collector 兄弟先
  请求贴图。首帧失败 ⇒ 整个会话 collector。修：`submitBone/submitBoneWorld` 提交时刻
  （pass 尚未开）`touchTexture` 触发懒加载（按 id 去重，资源重载清空）。
- **开镜不裁高模枪身**：裁剪只在 GPU 表做（`clipForViewmodelAtDraw`），回退 collector 后
  `submitPolyMesh` 用的是裸 `entityCutout`。修：collector 首人称路径同样套
  `clipForViewmodel`（判据与立方体枪身一致）。
- **法线/反光**：本局实际跑的是 collector 路径（与 26.2 同码）；GPU 路径修复后走 Iris 的
  iris_NormalMat（栈顶 MV 逆转置，见 drawViaRenderTypeCore 注释）。无新证据前不动，等复测。
