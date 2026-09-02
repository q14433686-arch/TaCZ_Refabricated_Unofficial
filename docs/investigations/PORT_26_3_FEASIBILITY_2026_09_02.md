# 26.3 移植可行性评估：现在开工 vs 等 stable（2026-09-02）

- **日期**：2026-09-02（26.3-pre-1 发布后第 2 天）
- **提问**：维护者 —— 「是否现在就开始移植到 26.3-pre，早做早解脱？Fabric API / 生态还会变多少？」
- **结论（一句话）**：**Fabric API 侧几乎不会再变（实测 41 个我们用到的类里 39 个字节级相同）；
  真正的大破坏来自 Mojang 侧的渲染引擎换代（blaze3d → renderpearl + OIT），而这些破坏
  早在 6/30–8/12 的 snapshot-2/3/5/8 就已定型，等 stable 不会减少一分工作量。**
  但今天开分支也换不到「解脱」：**8 个编译期依赖里 6 个没有 26.3 构件，其中 FCAP 与
  Cloth Config 是硬运行期依赖**，且 OIT 对镜内合成的影响必须真机验证。
  ⇒ **建议：不开分支，改为现在就在 26.2 上做「减损重构 + 预写移植脚本包」，stable 一到一次性机械移植。**
- **证据级别**：
  - **仓库级实证**（可复核 commit sha / 文件清单 / blob 比对）：Fabric API 面、生态项目分支与 release、本仓暴露面计数。
  - **官方 changelog 引证**（minecraft.net / minecraft.wiki / Mojang feedback）：OIT、shader 编译链、类改名。
  - **推断（未验证）**：标注为「未知」的包映射、OIT 对 scope 的语义影响、`#version 330` 在 ShaderC 下是否仍可用。
  - **沙箱限制**：本环境**不能编译、不能进游戏**，`meta.fabricmc.net` / `api.modrinth.com` /
    `maven.fabricmc.net` / `piston-meta.mojang.com` 全部 SSL 不可达 ⇒ 构件可用性以 **GitHub 分支/release** 为代理指标
    （不能排除某项目已在 Modrinth 发了 26.3-pre 构建但 GitHub 无分支；上线前需复核一次）。

---

## 0. 决策要点速览

| 维度 | 实测结论 | 对「现在开工」的意义 |
|---|---|---|
| Fabric API 0.159.1+26.3 是否可用 | ✅ 已发（09-01），钉 `minecraft_version=26.3-pre-1` | 现在就能解析 |
| 我们用到的 41 个 Fabric API 类 | **39 个 blob 完全相同**；2 个变动（1 个只是 javadoc） | 移植几乎零成本 |
| 4 个关键 API 包的类清单 | `client/rendering/v1`=38、`.../hud`=5、`networking/v1`=18、`client/networking/v1`=9，**两分支逐字一致** | 无删类/改包 |
| Loom / Loader | Fabric 26.3 自己钉 loom `1.17.20` + loader `0.19.3`；我们是 `1.17-SNAPSHOT` + `0.19.3` | **无需大版本升级** |
| Mojang 渲染层 | **blaze3d → renderpearl**（snapshot-3）+ **OIT**（snapshot-2）+ **ShaderC / `#include`**（snapshot-5） | 主战场，且**已定型** |
| 第一人称渲染类 | `ItemInHandRenderer` → `net.minecraft.client.player.FirstPersonHandsAndItems`（snapshot-8，**连包都搬了**） | 命中本模组最核心 mixin |
| 生态（Iris/Sodium/Voxy/SBM/Cloth/FCAP/REI/Zoomify/PAL/SSR） | **全部没有 26.3**；只有 ModMenu（alpha，钉 snapshot-5）与 JEI（分支，snapshot-7）动了 | 今天开分支 = 不能实测、不能发布 |
| stable ETA | 26.3 计划 9 月下旬；26.2 周期先例 pre-1(5/26) → release(6/16) ≈ 3 周 | **等待成本只有 ~3 周** |

---

## 1. 时间线：26.3 已经走到哪儿了

**Mojang 侧**

| 日期 | 版本 | 与本模组相关的关键变更 |
|---|---|---|
| 2026-06-30 | 26.3-snapshot-2 | **OIT（Order-Independent Transparency）取代 "Improved Transparency"**：可乱序渲染半透明物体、不再排序；新增 `core/oit_composite.fsh`、`include/oit*.glsl`；**删除 `post_effect/transparency.json` 与 `shaders/post/transparency.fsh`**；新增 define `OIT` / `OIT_DEPTH_BOUNDS` / `OIT_TRANSMITTANCE` / `OIT_ACCUMULATE` / `OIT_ALPHA_ONLY` / `OIT_ADDITIVE` / `OIT_OPAQUE_PARTS_THRESHOLD` / `WAVELET_RANK` / `COEFF_ATTACHMENT_COUNT` / `B3D_IS_ZERO_TO_ONE`；受影响的 core shader 含 `item`、`entity`、`particle`、`text`、`terrain`、`clouds`、`block`、`world_border`；新增 F3+X 调试开关 |
| 2026-07-07 | 26.3-snapshot-3 | **`com.mojang.blaze3d.*` → `com.mojang.renderpearl.*` 包迁移**（Fabric 侧 port 提交 `bca58169`，79 文件）。RenderPearl 是 Mojang 新渲染后端（Vulkan  capable），社区类比 Bedrock 的 RenderDragon |
| 2026-07-21 | 26.3-snapshot-5 | `B3D_IS_ZERO_TO_ONE` → `RENDERPEARL_IS_ZERO_TO_ONE`；**OpenGL 下也改用 ShaderC 编译 shader**；**shader include 由 ShaderC 处理，`#moj_import` → `#include`** |
| 2026-08-12 | 26.3-snapshot-8 | **`ItemInHandRenderer` → `FirstPersonHandsAndItems`**（Fabric port 提交 `d4a3cef7`，其 mixin 同步改名并 +9/-14 行）；`OIT_FORCE_ZERO_DEPTH` 移除（see-through 文字改走 OIT 外单独一遍）；`core/text_background.{fsh,vsh}` 移除；item model tint source `minecraft:map_color` 移除 |
| 2026-08-17 / 08-25 | snapshot-9 / 10 | Fabric port 提交 `c00f1dd` / `47f74c9` |
| 2026-09-01 | **26.3-pre-1** | data version 5017、资源包 97.1、数据包 119.0、protocol 1073742157、最低 Java 25；技术变更主要是 Number Provider 大改（`simple_state_provider`→`simple`、`weighted_state_provider`→`weighted`）、烹饪配方 `cookingtime` 变必填、macOS 全屏/菜单栏选项。**pre-1 里没有新的渲染层破坏** |
| 预计 9 月下旬 | 26.3 stable | 26.2 周期先例：pre-1 (5/26) → release (6/16)，约 3 周 |

**Fabric 侧**：26.3 分支自 `26.3-snapshot-10`(08-25) 之后只有功能新增与修 bug
（`ItemClickBehaviorCallback`、`AfterForeground`、Block Transformer 改版、HUD status bar 改名、
Fluid/Permission 去实验性、FRAPI port 修复……），**渲染层没有再动**。

> **推论（本文最重要的一条）**：破坏性变更全部落在 snapshot 阶段并已冻结 ~3 周。
> 「早做」不会避开任何未来改动；「等 stable」也不会增加任何工作量。
> 二者差别只在：**你能不能把成果编译出来、跑起来验证**。

---

## 2. Fabric API 侧：实测「几乎零风险」

方法：抓 `FabricMC/fabric` 分支 `26.2` 与 `26.3` 的完整 git tree，按 blob sha 逐文件比对；
再把本仓 `src/main/java` 里所有 `net.fabricmc.fabric.*` import 抽成清单逐个回查。

**全库量级**：共同 blob 2956 个 —— 内容变了 **405**、26.3 新增 **68**、删除 **41**。
变动最多的模块是 `fabric-convention-tags-v2`(54)、`fabric-data-generation-api-v1`(37)、
`fabric-biome-api-v1`(27)、`fabric-rendering-v1`(25)、`fabric-renderer-api-v1`(19)、
`fabric-item-api-v1`(16)、`fabric-resource-loader-v1`(15)；`fabric-networking-api-v1` 只 9 个。

**本仓暴露面**：我们 import 的 **41 个** Fabric API 类（清单见 `/tmp/our_fabric_imports.txt`，
含 `FabricOrderedSubmitNodeCollector`、`SubmitRenderPhases`、`HudElementRegistry`、
`PayloadTypeRegistry`、`FriendlyByteBufs`，以及一个 **internal accessor**
`net.fabricmc.fabric.mixin.networking.client.accessor.ClientHandshakePacketListenerImplAccessor`）：

- **39 个 blob sha 完全相同**（26.2 与 26.3 一字不差）；
- **2 个有变动**，且都不影响我们：
  - `KeyMappingHelper`：**只是 javadoc 示例**改了（`InputConstants.Type.KEYSYM`→`KEYBOARD`、`GLFW.GLFW_KEY_P`→`InputConstants.KEY_P`），API 签名未动；
  - `SubmitRenderPhases`：3 个常量内部的 lambda 字段引用改名
    （`seeThroughNameTags`→`seeThrough`、`gizmos`→`translucentGizmos`、`alwaysOnTop`→`alwaysOnTopGizmos`）。
    **公开常量名与类型未变**（我们只用 `SubmitRenderPhases.SOLID`），但这三个改名说明
    **vanilla 的半透明 submit 目标结构变了** —— 与 OIT 同源，属于 §3 的风险而非 Fabric API 的风险。
- 四个关键 API 包的**类清单逐字一致**（无删类、无改包、无新增）：
  `api/client/rendering/v1` 38 个、`api/client/rendering/v1/hud` 5 个、`api/networking/v1` 18 个、`api/client/networking/v1` 9 个。
- Fabric 26.3 分支自身钉：`version=0.159.1`、`minecraft_version=26.3-pre-1`、`prerelease=true`、
  `fabric-loom=1.17.20`、`fabric-loader=0.19.3`。
  ⇒ 我们的 `loom 1.17-SNAPSHOT` / `loader 0.19.3` **不需要跨线升级**；
  Java 25 也已经是本线现状（`fabric.mod.json` 里 `java: ">=25"`）。

**顺带发现（与 26.3 无关，但值得单独处理）**：Fabric API 在 09-01 同时发了
`0.159.0+26.2`，而我们 26.2 分支钉的是 `0.155.2+26.2` —— **落后 4 个小版本**。
这是纯 26.2 线内的低风险追平；但按 AGENTS.md §1，改 `gradle.properties` 里**任何依赖版本**
都要同步 README 6 处（本例主要是「支持环境表」的 Fabric API 格子）。**是否追平请维护者裁定**，本文不擅自改。

---

## 3. Mojang 侧：真正要付的账

### 3.1 `blaze3d` → `renderpearl` 包迁移（已确认的映射表）

取自 Fabric 自己的 port 提交 `bca58169`（26.3-snapshot-3）里 import 行的增删：

| 26.2（旧） | 26.3（新） |
|---|---|
| `com.mojang.blaze3d.pipeline.RenderPipeline` | `com.mojang.renderpearl.api.pipeline.RenderPipeline` |
| `com.mojang.blaze3d.pipeline.BindGroupLayout` | `com.mojang.renderpearl.api.pipeline.BindGroupLayout` |
| `com.mojang.blaze3d.pipeline.ColorTargetState` | `com.mojang.renderpearl.api.pipeline.ColorTargetState` |
| `com.mojang.blaze3d.pipeline.DepthStencilState` | `com.mojang.renderpearl.api.pipeline.DepthStencilState` |
| `com.mojang.blaze3d.pipeline.BlendFunction` | `com.mojang.renderpearl.api.pipeline.BlendFunction` |
| `com.mojang.blaze3d.platform.PolygonMode` | `com.mojang.renderpearl.api.pipeline.PolygonMode` |
| `com.mojang.blaze3d.PrimitiveTopology` | `com.mojang.renderpearl.api.pipeline.PrimitiveTopology` |
| `com.mojang.blaze3d.buffers.GpuBuffer` / `GpuBufferSlice` | `com.mojang.renderpearl.api.buffers.*` |
| `com.mojang.blaze3d.textures.GpuSampler` / `GpuTextureView` | `com.mojang.renderpearl.api.textures.*` |
| `com.mojang.blaze3d.vertex.VertexFormat` | `com.mojang.renderpearl.api.vertex.VertexFormat` |
| `com.mojang.blaze3d.systems.RenderPass` | `com.mojang.renderpearl.api.commands.RenderPass` |
| `com.mojang.blaze3d.systems.GpuBackend` | `com.mojang.renderpearl.api.device.GpuBackend` |
| `com.mojang.blaze3d.opengl.DirectStateAccess` / `GlCommandEncoder` | `com.mojang.renderpearl.backend.opengl.*` |
| `com.mojang.blaze3d.vulkan.VulkanGpuSurface` | `com.mojang.renderpearl.backend.vulkan.VulkanGpuSurface` |

**确认「没搬走」的（好消息，命中我们最高频的 import）**：
`blaze3d.vertex.PoseStack`、`blaze3d.vertex.VertexConsumer`、`blaze3d.vertex.QuadInstance`、
`blaze3d.vertex.SheetedDecalTextureGenerator`、`blaze3d.vertex.DefaultVertexFormat`、
`blaze3d.platform.InputConstants`（另有新增 `blaze3d.platform.SDLEventHandler`）。

**未知（Fabric 自己不用，故无代理证据；只能靠编译期报错确认）**：
`RenderSystem`(我们用 9 处)、`CommandEncoder`(3)、`RenderTarget`(9)、`TextureTarget`(4)、
`FilterMode`(5)、`GpuTexture`(2)、`GpuFormat`(4)、`ProjectionType`(2)、`IndexType`、
`Window`(1，Fabric 侧该类引用在 26.3 被删，疑似搬家)、`Lighting`(1)、
`resource.GraphicsResourceAllocator`/`CrossFrameResourcePool`(各 1)、`audio.Channel`/`Library`(7，推测不动)。

### 3.2 `RenderPipeline.Snippet` 构造签名变了（语义级，不是改名）

Fabric 的 `RenderPipelineBuilderMixin` 在 26.3 上的 `@ModifyArg` 目标签名：

```
26.2: (Optional<Identifier> vertexShader, Optional<Identifier> fragmentShader, shaderDefines,
       bindGroupLayouts, colorTargetStates, activeColorTargetStateCount, depthStencilState,
       polygonMode, cull, vertexFormatPerBuffer, vertexFormatMode)
26.3: (Map<ShaderType, Identifier> shaders, shaderDefines, bindGroupLayouts, colorTargetStates,
       activeColorTargetStateCount, depthStencilState, polygonMode, cull, vertexFormatPerBuffer,
       vertexFormatMode, int pushConstantSize)
```

即：**分离的 vertex/fragment 两个可选 shader → 按 `ShaderType` 索引的 shader map**，并新增 **push constant 大小**。
这正是 OIT 多遍（depth bounds / transmittance / accumulate / composite）所需要的管线形态。
`RenderPipeline` 的 import 也从 `blaze3d.pipeline` 换到 `renderpearl.api.pipeline`。

### 3.3 OIT 改变半透明合成语义（**最大的未知**）

- 半透明几何**不再排序**、可乱序绘制；OIT 是近似算法，Mojang 自己说明会引入新的轻微视觉瑕疵
  （例：透过多层半透明表面看云时颜色略偏），且**性能开销高于旧的 Improved Transparency**。
- 旧 `post_effect/transparency.json` + `shaders/post/transparency.fsh` **被删除**（本仓未引用，已 grep 确认）。
- 26.2 移植时我们已经吃过一次同类亏（`PORTING_NOTES.md` §3.1：stencil 路线被废、scope 掩码改走
  `ColorTargetState.WRITE_NONE` / mask sampler）。**OIT 是同一类破坏的加强版**：
  镜内遮罩、PIP、目镜环、镜内文字、枪口焰、半透明配件、`seeThrough` 文字的选相
  （对应 `SubmitRenderPhases` 内部改名的那三个 submit 目标）都可能改变合成次序/结果。
- **这一项无法静态推断**，必须真机在 F3+X（OIT 开关）两态下逐条比对。

### 3.4 第一人称渲染类改名 + 搬包

`net.minecraft.client.renderer.ItemInHandRenderer` → **`net.minecraft.client.player.FirstPersonHandsAndItems`**
（证据：Fabric 26.3 分支 `fabric-item-api-v1` 的 mixin 由 `ItemInHandRendererMixin`
改名为 `FirstPersonHandsAndItemsMixin`，`@Mixin(FirstPersonHandsAndItems.class)`，
import 为 `net.minecraft.client.player.FirstPersonHandsAndItems`；其中 `tick` 方法仍存在）。

**同名类风险对照（Fabric mixin 文件名作为代理证据）**：

| 我们的高危 mixin 目标 | 26.3 状态 |
|---|---|
| `ItemInHandRenderer` | ❌ **已改名 + 搬包** → `player.FirstPersonHandsAndItems` |
| `GameRenderer` | ✅ Fabric 侧 `GameRendererMixin`/`Accessor` 名字未变 |
| `LevelRenderer`（accessor + world pass 两处） | ✅ `LevelRendererMixin` 名字未变 |
| `LivingEntityRenderer` | ✅ 未变 |
| `GuiGraphics` | ✅ 未变 |
| `Minecraft` | ✅ 未变 |
| `ItemInHandLayer`（第三人称手持层） | ⚠️ **未知**（Fabric 没有对应 mixin，无代理证据；与 `ItemInHandRenderer` 同族，需编译期确认） |
| `CreateWorldScreen` / `Screen` | ⚠️ 未知，低风险 |

---

## 4. 本仓暴露面量化（`grep` 实测，26.2 分支 `fcd3b4a`）

### 4.1 `com.mojang.blaze3d.*` import：**201 行 / 109 个文件**

| 子包 | 我们的用量 | 26.3 判定 |
|---|---|---|
| `vertex.*`（PoseStack 72、VertexConsumer 26、BufferBuilder 3、MeshData 2、DefaultVertexFormat 2、ByteBufferBuilder 2、通配 1） | **108** | ✅ 已确认不动 |
| `platform.*`（InputConstants 13、Window 1、Lighting 1） | 15 | InputConstants ✅ 不动；Window/Lighting ⚠️ 未知 |
| `pipeline.*`（RenderTarget 9、RenderPipeline 6、TextureTarget 4、ColorTargetState 4、BindGroupLayout 3、DepthStencilState 2、BlendFunction 1） | 29 | 状态类 ❌ 搬 renderpearl；`RenderTarget`/`TextureTarget` ⚠️ 未知 |
| `systems.*`（RenderSystem 9、RenderPass 3、CommandEncoder 3） | 15 | RenderPass ❌ 搬 `api.commands`；其余 ⚠️ 未知 |
| `textures.*`（FilterMode 5、GpuTextureView 4、GpuTexture 2、GpuSampler 1） | 12 | GpuTextureView/GpuSampler ❌ 搬 `api.textures`；FilterMode/GpuTexture ⚠️ 未知 |
| 根包（GpuFormat 4、ProjectionType 2、PrimitiveTopology 2） | 8 | PrimitiveTopology ❌ 搬 `api.pipeline`；其余 ⚠️ 未知 |
| `buffers.*`（GpuBufferSlice 3、GpuBuffer 2） | 5 | ❌ 搬 `api.buffers` |
| `audio.*`（Channel 4、Library 3） | 7 | ✅ 推测不动（非渲染层） |
| `resource.*`（GraphicsResourceAllocator、CrossFrameResourcePool） | 2 | ⚠️ 未知 |

⇒ **机械改名的确定部分约 30 行 import**，未知部分约 45 行；**约 128 行（含全部 PoseStack/VertexConsumer）确认无需动**。
改名部分是纯 `sed` 工作，且**编译期必然暴露**（类不存在 = javac 直接报错）。

### 4.2 自建渲染管线与 shader（语义级风险集中区）

- `RenderPipeline` 出现 **47 次 / 11 文件**；`ColorTargetState` 17/5、`DepthStencilState` 11/4、
  `BindGroupLayout` 10/3、`PrimitiveTopology` 11/3。
- 管线构建链上的方法调用计数：`withShaderDefine` **38**、`withBindGroupLayout` **23**、
  `withVertexShader` **13**、`withFragmentShader` **13**、`withCull` 11、`withColorTargetState` 9、
  `withPrimitiveTopology` 8、`withDepthStencilState` 7、`withVertexBinding` 6、`withSampler` 3。
  ⇒ **13 处 `withVertexShader` + 13 处 `withFragmentShader` 正对 §3.2 的签名变更**（builder 层是否保留同名方法未知）。
- 底座文件（改动会集中在这里）：`PolyMeshGpuRenderer`（SBM compat，3 条自建管线，用
  `RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)`）、`ScopeBodyRenderTypes`、
  `ScopeTextRenderTypes`、`ScopeMaskRenderer`、`ScopeMaskTarget`、`ScopePipRenderer`、`ScopePipTarget`、
  `ScopeTextSubmitter`、`MuzzleFlashRender`、`BedrockAttachmentModel`、`IrisCompat`/`IrisScopeMaskState`。
- GUI/HUD 侧 30+ 处只是 `blit(RenderPipelines.GUI_TEXTURED, ...)` —— **不碰底座，风险低**
  （前提是 `RenderPipelines.GUI_TEXTURED` 与 `MATRICES_FOG_SNIPPET` 两个 vanilla 常量在 26.3 仍存在，未验证）。
- **6 个自写 core shader**：`assets/tacz/shaders/core/scope_body.{fsh,vsh}`、`scope_pip.fsh`、
  `scope_ring_final.fsh`、`scope_text.{fsh,vsh}`，共 **17 行 `#moj_import <minecraft:*.glsl>`**，头部 `#version 330`。
  ⇒ 26.3 需改 `#include`（ShaderC 语义）；**`#version 330` 在 ShaderC/OpenGL 统一编译下是否仍可用未验证**；
  OIT 相关 define 是否需要适配（我们不在 vanilla 受影响 shader 之列，但 scope 是全屏半透明叠加，需实测）。

### 4.3 其它渲染面计数

`RenderType` **189 次 / 42 文件**、`SubmitNodeCollector` **139 / 44**、`LevelRenderer` 59 / 15、
`ItemInHandRenderer` 15 / 9（+`tacz.mixins.json`）、`TranslucentSubmit` 3 / 2、`SubmitNode` 3 / 1、`ItemRenderer` 3 / 2。

### 4.4 mixin 面

- **8 个 mixin 配置文件**、**73 个声明的 mixin 类**、**49 个不同的 `@Mixin` 目标类**（57 处引用）。
- 其中 `fabric.mod.json` 只加载 7 个 —— `tacz.compat.acceleratedrendering.mixins.json` 是**孤儿配置**
  （有 `ARCompatMixinPlugin` 但未登记，永不加载；1.21.11 线已删）。26.3 初期正好可以照这个范式
  把 compat mixin 整体摘掉。
- compat mixin 组：`iris` 5 个、`voxy` 3 个、`mesh` 4 个、`carryon` 3 个（+孤儿 AR 1 个）。
- 26.2 线**没有** `verify_mixin_targets.py`（那是 1.21.11 的工具）；
  类级改名会被 `compileJava` 抓到（`@Mixin(X.class)` 里 X 不存在即编译失败），
  **方法级注入点失效只能靠运行期** ⇒ M1 的 CI 绿灯**不等于** mixin 都能注入成功。

### 4.5 对外 API 的 breaking change

`BeforeRenderHandEvent`（我们的公开事件 API）与 `KeepingItemRenderer` 都直接引用 `ItemInHandRenderer` 类型。
26.3 改名后，**事件签名会变** ⇒ 属于对枪包/附属模组的 breaking change，必须在 `PORTING_NOTES.md`
与 CHANGELOG 里明写，不能悄悄改（AGENTS.md §2）。

---

## 5. 生态就绪度（2026-09-02 GitHub 实测）

| 项目 | 我们钉的版本 / 用法 | 26.3 状态 | 判定 |
|---|---|---|---|
| Fabric API | `0.155.2+26.2` → 需 `0.159.1+26.3` | ✅ 09-01 已发，钉 pre-1 | **就绪** |
| Fabric Loader | `0.19.3` | ✅ 最新 `0.19.5`（08-28）；Fabric 26.3 自己仍钉 `0.19.3` | **就绪** |
| Loom | `1.17-SNAPSHOT` | ✅ Fabric 26.3 用 `1.17.20`（同 1.17 线） | **就绪** |
| **Forge Config API Port** | `implementation ...:26.2.1`；`fabric.mod.json` **`depends: forgeconfigapiport >=26.2.1`** | ❌ 仓库 `Fuzss/forgeconfigapiport` 只有 `26.2.x` 分支；最近 release `v26.2.1-mc26.2`(06-21) | **硬阻塞**（编译 + 运行都缺） |
| **Cloth Config** | `implementation me.shedaniel.cloth:cloth-config-fabric:26.2.155` | ❌ 只有 `v26.2` 分支 | **硬阻塞**（配置界面） |
| ModMenu | `implementation maven.modrinth:modmenu:20.0.1` | ⚠️ 有 `26.3` 分支，但钉 **`26.3-snapshot-5`** + `fabric 0.155.3+26.3`；已发 `v21.0.0-alpha.1`(07-23, alpha) | **半就绪**（对 pre-1 需重编） |
| JEI | `compileOnly maven.modrinth:jei:30.13.0.86` | ⚠️ 分支 `fabric-26.3-snapshot-7`，无 release | 未就绪 |
| REI | `compileOnly me.shedaniel:RoughlyEnoughItems-{api,default-plugin}-fabric:26.2.820` | ❌ 只有 `26.2` / `feature/26.2` 分支 | 未就绪 |
| Zoomify | `compileOnly ...:2.16.1+26.2` | ❌ 最近 release `2.16.1+26.2`(06-16) | 未就绪 |
| Player Animation Lib | `compileOnly ...:1.2.5` | ❌ `KosmX/minecraftPlayerAnimator` 最后 push 2025-12-28，无 26.x 分支 | 未就绪 |
| Shoulder Surfing Reloaded | `compileOnly ...:26.2-5.0.7+fabric` | ❌ `Exopandora/ShoulderSurfing` push 08-30，无 26.3 分支/tag | 未就绪 |
| Architectury | `compileOnly 13.0.11`（gradle.properties 已注明「无 26.2 构建、源码不需要」） | ❌ | 移植时直接删该行 |
| Iris | `tacz.iris.mixins.json`（5 个 compat mixin） | ❌ 仓库只有 `26.2` 分支（GitHub release 停在 2024，发布走 Modrinth） | **光影路径无法实测** |
| Sodium | 运行环境常配 | ❌ 最新 26.x 构件 `mc26.2-0.9.2-alpha.4`(08-07) | 无 26.3 |
| Voxy | `tacz.voxy.mixins.json`（3 个） | ❌ `MCRcortex/voxy` push 08-29，无 26.3 | 无法实测 |
| SimpleBedrockModel-Fabric | `tacz.mesh.mixins.json`（4 个）+ 内置 TML 高模 | ❌ `Sh1roCu/SimpleBedrockModel-Fabric` push 08-17，无 26.3 分支 | **高模/GPU 路径无法实测** |
| Carry On | `suggests` + `tacz.carryon.mixins.json`（3 个） | ⚠️ 本轮探测脚本中断，未确认 | 待查 |
| MAE 1.1.1 / luaj-figura / bcel / commons-math3 | `include` 打包，非 MC 版本绑定 | ✅ | 不受影响 |

**关键结论**：`build.gradle` 里 8 个「必须能解析」的外部 mod 构件（FCAP、Cloth、ModMenu、JEI、REI、
Zoomify、PAL、SSR）中，**只有 ModMenu 有 26.3 分支且停在 snapshot-5**。
Gradle 的 `compileOnly` 同样要求构件可解析 ⇒ **今天在 26.3 分支上连 `compileJava` 都跑不起来**，
除非把这 6 个依赖连同用它们的代码一起注释掉（26.2 移植时对 architectury / carry-on / kubejs 就是这么做的，
有先例，但那是「本来就不用」，这次是「要挖掉在用的功能」）。
连带后果：**CI 也无法验证**（compile-check.yml 第一步就是依赖解析）。

---

## 6. 「早做早解脱」这句话，拆开看哪半对

| 说法 | 判定 | 依据 |
|---|---|---|
| 「Fabric API 还会大改，早动手会白做」 | ❌ **不成立** | 41 个类 39 个字节级相同；渲染层 API 包类清单一致；26.3 分支近 3 周只有新增/修 bug |
| 「早做能避开未来的破坏性变更」 | ❌ **不成立** | 破坏全在 snapshot-2/3/5/8 落地并冻结；pre-1 changelog 里没有新的渲染层改动 |
| 「早做能把机械工作量提前消化」 | ✅ **成立** | 包迁移(~30 确定 + ~45 未知 import 行)、类改名(12 文件)、shader include(6 文件/17 行) 都是可脚本化的死力活 |
| 「现在开分支就能早点解脱」 | ❌ **不成立** | 6/8 依赖无 26.3 构件 ⇒ 编不出来；FCAP+Cloth 是硬运行期依赖 ⇒ 跑不起来；OIT/scope 必须真机验证而 Iris/Sodium/Voxy/SBM 都没 26.3 ⇒ 验不了 |

⇒ 「早做」的正确形态不是**开分支**，而是**在 26.2 上把未来的改动面收窄 + 把移植脚本预先写好**。

---

## 7. 建议路线（我的倾向）

### A. 现在就在 26.2 分支做：「26.3 减损重构」（零功能风险，CI 可验证）

目标：**让 stable 那天的移植只剩 `sed` 与实测**，且这些重构本身对 26.2 也有正收益（可读性/收敛度）。

| # | 动作 | 收益 | 风险 |
|---|---|---|---|
| **A1** | **管线工厂收敛**：把散在 11 个文件的 `RenderPipeline.builder(...)` 链（13×`withVertexShader`、13×`withFragmentShader`、38×`withShaderDefine`、23×`withBindGroupLayout`）收到单一 `TaczRenderPipelines` 工厂，vanilla 常量（`RenderPipelines.MATRICES_FOG_SNIPPET` / `GUI_TEXTURED`、`BindGroupLayouts.SAMPLER0/2`）只在这里出现 | §3.2 的 Snippet/ShaderType/pushConstantSize 变更**只改一处** | 低（纯提取，CI 编译即可验证） |
| **A2** | **底座文件收敛**：确认 `ColorTargetState`/`DepthStencilState`/`PrimitiveTopology`/`GpuBufferSlice` 等「会搬家」的类型只在 §4.2 列的 ~11 个底座文件里出现；GUI/HUD 那 30+ 个 `blit` 文件不得引入底座类型 | 包迁移时 `sed` 只扫一小片，且可加 CI 静态检查（禁止清单）守住 | 低 |
| **A3** | **`ItemInHandRenderer` 引用面收敛**：12 个文件的直接类型引用压到 3–4 个中转点（`HandRendererRef` 之类）；`BeforeRenderHandEvent` 的签名影响**预先写进 `PORTING_NOTES.md`**（标注 26.3 是 breaking change） | 改名只改中转点；对外 API 的破坏有明文交代（AGENTS.md §2） | 中（触碰刚修完的 put-away 路径 ⇒ 改完必须重跑收枪/拔枪实测项） |
| **A4** | **shader include 清单化**：6 个 scope shader 的 17 行 `#moj_import` 抽成一份清单（或公共 `.glsl`），并在文档里记下「26.3 改 `#include`、`#version 330` 待验证」 | 26.3 那天只改一处 | 低（**注意**：抽公共 `.glsl` 会改变资源加载路径，需实测 scope 仍正常；若不想冒险，只做「清单文档」不抽文件） |
| **A5** | **把本文 §3.1 映射表 + §3.4 改名 + §4 计数落进 `PORTING_NOTES.md` 的「26.3 待办」章节** | 移植者不必重跑本次调研 | 无 |
| **A6** | **compat mixin 可摘除性核对**：确认 `iris`(5)/`voxy`(3)/`mesh`(4)/`carryon`(3) 四组能整体停用而主路径不受影响（现成范式 = 孤儿配置 `tacz.compat.acceleratedrendering.mixins.json`：不登记进 `fabric.mod.json` 即不加载）；顺手裁定那条孤儿配置的去留（§B0-3 in `docs/ci/INSTALL_MATRIX_20260902.md`） | 26.3 初期没有这些 mod 时可以直接摘掉，不阻塞主线 | 低 |
| **A7** | **预写「26.3 移植脚本包」**：`scripts/port263/`（或 `docs/patch/`）放 ①包迁移 `sed` 脚本 ②`ItemInHandRenderer`→`FirstPersonHandsAndItems` 改名脚本（含 `tacz.mixins.json`、mixin 类改名、refmap 影响说明）③shader `#moj_import`→`#include` 脚本 ④`gradle.properties` 版本矩阵模板 ⑤`fabric.mod.json` 的 `minecraft` / `forgeconfigapiport` 约束改法 ⑥README 6 处同步清单（AGENTS.md §1）⑦CI 三件套复制清单 | **「早做早解脱」的正当形态**：stable 一到，跑脚本 + 看编译错误即可 | 无（脚本不落地到源码） |

> A1/A2/A5/A6/A7 都不改运行时行为、不动 `gradle.properties` ⇒ **不触发 AGENTS.md §1 的 README 6 处同步**。
> A3/A4 若改到实装代码，改完必须重跑相关实测项（本文不宣称「已验证」，只宣称「已实装 + 待实测」）。

### B. stable（预计 9 月下旬）+ FCAP/Cloth Config 出 26.3 之后：开 `26.3(main)` 分支，做 M1

按 AGENTS.md §3「新 MC 线 = 新分支」，M1 = **机械移植到编译绿**：

1. 新分支 `26.3(main)`；`gradle.properties`：`minecraft_version=26.3`、`fabric_version=0.159.x+26.3`、
   `loader_version=0.19.5`（或跟 Fabric 钉 `0.19.3`）、`mod_version=1.1.8+fabric.26.3.R1`，
   以及 cloth / FCAP / modmenu / jei / rei / zoomify / pal / ssr 全部换成 26.3 构件（**缺哪个就先摘哪个功能，并在 README 明写**）。
2. 跑 A7 的脚本包 → `compileJava` → 用编译错误清单补齐 §4.1 的「未知映射」。
3. `fabric.mod.json`：`minecraft: "26.3"`、`forgeconfigapiport` 约束、（如摘 compat）删对应 `mixins` 条目。
4. README 6 处同步（AGENTS.md §1 强制）+ `docs/README_26_1_2.md` 类蓝本是否需要新增 26.3 版由维护者裁定。
5. CI 三件套（build / compile-check / consistency）按 `docs/ci/INSTALL_MATRIX_20260902.md` 的方式给新线装上。
6. **M1 出口条件**：compile-check 绿 + 全量 build 绿 + 「未知映射」全部落进 `PORTING_NOTES.md`。
   ⚠️ M1 绿**不代表 mixin 注入成功**（方法级注入点只能运行期验证）。

### C. M2 = 真机实测（OIT 专项，本文无法替代）

- F3+X 两态（OIT 开/关）下逐条比对：镜内遮罩、PIP、目镜环、镜内文字、枪口焰、半透明配件、`seeThrough` 文字。
- `SubmitRenderPhases` 三个改名过的 submit 目标（`seeThrough` / `translucentGizmos` / `alwaysOnTopGizmos`）
  是否仍是我们该选的相（我们用 `SOLID`，但 scope/PIP 走自建管线 + `submitCustom`，需确认落点）。
- 6 个 scope shader 在 ShaderC 下能否编译（`#version 330`、`#include`、mask sampler）。
- Iris 26.3 到位后重跑光影下的 HAND 接管链路（`IrisCompat` / `IrisScopeMaskState`）。
- 收枪/拔枪动画（刚修的 `keep()` 路径）在 `FirstPersonHandsAndItems` 上是否仍成立 —— **`tick` 方法确认还在**，但注入点需复验。
- 高模 GPU 路径（SBM/TML）：`PolyMeshGpuRenderer` 三条自建管线在 renderpearl 下的行为。

---

## 8. 如果坚持现在就开分支（最小可行路径 + 代价清单）

1. 从 `26.2(main)` 开 `26.3(main)`；`minecraft_version=26.3-pre-1`、`fabric_version=0.159.1+26.3`。
2. **注释掉 6 个不可解析依赖**（JEI、REI×2、Zoomify、PAL、SSR）+ 挖掉/桩化用它们的代码。
3. **FCAP 与 Cloth Config 是硬骨头**：两者都是 `implementation`（运行期也要），且 FCAP 写进了
   `fabric.mod.json` 的 `depends` ⇒ 要么桩化整个配置系统（工作量不小、且是「扔掉的东西」），
   要么等 Fuzss / shedaniel 出 26.3 构件（**这一步不受我们控制**）。
4. ModMenu 只能拿到 `v21.0.0-alpha.1`（snapshot-5 时代）⇒ 对 pre-1 大概率要自己重编。
5. 产出物：**一个不能发布、不能实测、砍掉配置界面/JEI/REI/Zoomify/SSR/ModMenu 入口的中间态**；
   stable 之后还要回来补依赖、再走一遍 M2。
6. 唯一实质收益：**把 §4.1 的「未知映射」用真实 javac 错误一次性打出来**（沙箱不能编译，
   这一步必须在维护者机器或 CI 上跑；而 CI 今天会因为依赖解析失败而跑不到编译）。

> 折中方案（如果只想要第 6 条那个收益）：**不必开正式分支** —— 在本地临时 worktree 里
> 把 6 个 compileOnly 依赖注释掉、FCAP/Cloth 桩化，只为拿到一份 `compileJava` 错误日志，
> 然后把日志交回来补全 §4.1 的映射表，分支本身丢弃。成本约 1 小时，收益是把「未知」变成「已知」。

---

## 9. 未知项与验证方式（诚实清单）

| 未知 | 怎么验 | 谁来做 |
|---|---|---|
| §4.1 约 45 行「未知映射」import | 26.3 环境下 `compileJava` 的错误日志 | 维护者（沙箱不能编译） |
| `RenderPipeline.Builder` 是否保留 `withVertexShader`/`withFragmentShader`/`withShaderDefine` | 同上；或查 26.3 的 `renderpearl.api.pipeline.RenderPipeline` 源码 | 维护者 |
| `RenderPipelines.MATRICES_FOG_SNIPPET` / `GUI_TEXTURED` / `BindGroupLayouts.SAMPLER0/2` 是否仍在 | 同上 | 维护者 |
| `ItemInHandLayer`、`CreateWorldScreen` 是否改名 | 同上（`@Mixin(X.class)` 编译期即报） | 维护者 |
| OIT 对 scope/PIP/枪口焰/半透明配件的语义影响 | **只能真机**（F3+X 两态比对） | 维护者 |
| `#version 330` + `#include` 在 ShaderC 下是否可用 | 真机加载 scope | 维护者 |
| Iris / Sodium / Voxy / SBM / FCAP / Cloth / REI / Zoomify / PAL / SSR 是否已在 Modrinth 发 26.3-pre 构件（GitHub 无分支不代表 Modrinth 无构件） | Modrinth 页面复核（沙箱 `api.modrinth.com` 不可达） | 维护者或后续联网环境 |
| Carry On 的 26.3 状态 | 本轮探测脚本中断，未查 | 下次补 |

---

## 10. 数据来源（可复核）

**GitHub API（本轮实测，2026-09-02）**

- `repos/FabricMC/fabric/git/trees/{26.2,26.3}?recursive=1` —— blob sha 逐文件比对（2956 共同 / 405 变 / 68 增 / 41 删）；41 个本仓 import 类的存在性与内容一致性；4 个 API 包的类清单。
- `repos/FabricMC/fabric/contents/gradle.properties?ref=26.3` —— `version=0.159.1`、`minecraft_version=26.3-pre-1`、`prerelease=true`。
- `repos/FabricMC/fabric/contents/gradle/libs.versions.toml?ref=26.3` —— `fabric-loom=1.17.20`、`fabric-loader=0.19.3`。
- `repos/FabricMC/fabric/commits/bca58169`（26.3-snapshot-3，2026-07-07，79 文件）—— renderpearl 映射表来源。
- `repos/FabricMC/fabric/commits/d4a3cef7`（26.3-snapshot-8，2026-08-12）—— `ItemInHandRendererMixin` → `FirstPersonHandsAndItemsMixin` 改名。
- `repos/FabricMC/fabric/contents/.../FirstPersonHandsAndItemsMixin.java?ref=26.3` —— `@Mixin(FirstPersonHandsAndItems.class)`、import `net.minecraft.client.player.FirstPersonHandsAndItems`、`@Inject(method="tick")`。
- `repos/FabricMC/fabric/contents/.../RenderPipelineBuilderMixin.java?ref={26.2,26.3}` —— Snippet 签名变更。
- `repos/FabricMC/fabric/contents/.../SubmitRenderPhases.java?ref={26.2,26.3}`、`.../KeyMappingHelper.java` —— 唯二变动的两个类。
- 生态：`repos/{IrisShaders/Iris, CaffeineMC/sodium, shedaniel/cloth-config, Prospector/ModMenu, shedaniel/RoughlyEnoughItems, mezz/JustEnoughItems, isXander/Zoomify, MCRcortex/voxy, tr7zw/FirstPersonModel, tr7zw/NotEnoughAnimations, Fuzss/forgeconfigapiport, KosmX/minecraftPlayerAnimator, Exopandora/ShoulderSurfing, Sh1roCu/SimpleBedrockModel-Fabric}/{branches,releases,tags}`。
- `repos/Prospector/ModMenu/contents/gradle.properties?ref=26.3` —— `minecraft_version=26.3-snapshot-5`、`fabric_version=0.155.3+26.3`。
- `repos/FabricMC/fabric-loom/releases`（最新 tag `1.17`，2026-06-07）、`repos/FabricMC/fabric-loader/releases`（`0.19.5` 08-28 / `0.19.4` 08-26 / `0.19.3` 06-01）。

**官方 changelog / wiki（引证）**

- minecraft.net《Minecraft 26.3 Snapshot 2》（2026-06-30）：OIT 取代 Improved Transparency、新增/删除的 shader 与 define、受影响 core shader 清单。
- minecraft.wiki《Java Edition 26.3 Snapshot 2》（同上）、《Java Edition 26.3 Snapshot 5》、《Java Edition 26.3 Snapshot 8》（OIT_FORCE_ZERO_DEPTH 移除、text_background 移除、map_color tint source 移除）。
- minecraft.net《Minecraft 26.3 Snapshot 5》（2026-07-21）：`B3D_IS_ZERO_TO_ONE`→`RENDERPEARL_IS_ZERO_TO_ONE`、ShaderC on OpenGL、`#moj_import`→`#include`。
- minecraft.wiki《Java Edition 26.3 Pre-Release 1》（2026-09-01）：data version 5017、资源包 97.1、数据包 119.0、protocol 1073742157、Java SE 25。
- minecraft.wiki《Java Edition 26.3》：计划 2026 年 9 月发布（内容：dappled forest、poplar、shelf mushrooms、wool/concrete 楼梯台阶、straw beds、cushions、abandoned camp）。
- 26.2 周期先例：pre-1 = 2026-05-26，release = 2026-06-16（≈3 周）。
- 「RenderPearl = Mojang 的 Vulkan 渲染器命名」一说来自社区（r/Minecraft 26.3 Snapshot 5 讨论串引 Mojira 崩溃日志里的 `com.mojang.renderpearl`），**非官方命名说明**，仅作背景。

**本仓实测（`fcd3b4a`，26.2 分支）**：§4 的全部计数由 `grep`/`find` 直接产出；
mixin 配置与目标由解析 8 个 `*.mixins.json` + 扫描 `@Mixin(...)` 得出；`fabric.mod.json` 的 `depends`/`mixins` 直接读取。

---

## 11. 附：本文没有回答的问题

- **26.3 stable 的确切日期**（只有「9 月下旬」的量级判断）。
- **FCAP / Cloth Config 什么时候出 26.3**（这两个是硬阻塞，节奏完全在别人手上；
  历史观察：FCAP 上一版 `v26.2.1-mc26.2` 在 26.2 发布(6/16)后 5 天(6/21)出；
  Cloth Config 的 `v26.2` 分支也在同期。若沿用该节奏，26.3 stable 后一周内应可到位）。
- **renov（NeoForge）线的 26.3 情况**：NeoForge 侧的渲染层迁移与 Fabric 不同源，
  本文全部结论**只适用于 refab（Fabric）线**；renov 需另做一轮（NeoForge 对 26.3 的支持节奏通常更慢）。
- **是否值得为 26.3 保留 26.2 线**：26.2 是当前唯一有完整生态（Iris/Sodium/Voxy/SBM/Cloth/FCAP）
  的版本；参照 26.1.2 与 1.21.11 仍在维护的先例，26.2 线在 26.3 发布后仍应保留一段时间。
