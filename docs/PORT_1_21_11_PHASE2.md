# 1.21.11 移植 · 阶段 2 执行报告（源码编译打通）

执行日期：2026-08-13
分支：`port/1.21.11-phase1`
承接：`docs/PORT_1_21_11_PHASE1.md`（构建层）

## 结论

```
./gradlew compileJava   BUILD SUCCESSFUL   0 error
./gradlew remapJar      BUILD SUCCESSFUL   产出 remapped jar（含 refmap + 5 个内嵌库）
```

首轮 146 个编译错误已全部清零。**这是编译与打包层面的完成，不是功能验收**——
沙箱 2 GB 内存 + 无显示设备，`runClient` / `runServer` 一次都没跑过。
真正的验收仍以交接清单第六节为准。

## 错误收敛过程

| 轮次 | 错误数 | 本轮动作 |
|---:|---:|---|
| 01 | 146 | 阶段 1 结束时的首轮基线 |
| 03 | 19 | GuiGraphics 改名族 + render-state 包迁移 |
| 04 | 150 | ↑ 导入修好后 javac 才能进入语义检查，错误“变多”属正常现象 |
| 05 | 74 | GUI `extract*`→`render*` 覆写族、FAPI 0.141 入口回退 |
| 06 | 40 | Player 消息 API、RenderType/管线、粒子 |
| 07 | 24 | 修正上一轮过度改名 |
| 08 | 9 | 动态物品模型、若干单点 |
| 09 | **0** | 最后 9 个单点 |

首轮 11 个族的实际收敛方式（全部以 `javap` 对两个 merged jar 逐符号核实，
不做“看起来像”的猜测）：

### 族 1：`GuiGraphicsExtractor`（86 错 / 32 文件）

26.1 把 `GuiGraphics` 整体改名为 `GuiGraphicsExtractor`，**并且**把它的方法名
一起改短了。回退时两件事都要做：

| 26.1.2 | 1.21.11 |
|---|---|
| `text` | `drawString` |
| `centeredText` | `drawCenteredString` |
| `textWithWordWrap` | `drawWordWrap` |
| `textWithBackdrop` | `drawStringWithBackdrop` |
| `item` / `fakeItem` / `itemDecorations` | `renderItem` / `renderFakeItem` / `renderItemDecorations` |
| `outline` | `renderOutline` |
| `horizontalLine` / `verticalLine` | `hLine` / `vLine` |
| `tooltip` | `renderTooltip` |
| `map`/`entity`/`skin`/`book`/`bannerPattern`/`sign`/`profilerChart` | `submit*RenderState` 系列 |
| `itemCooldown`（private） | `renderItemCooldown`（private） |

完整表见 `docs/migrate_family1.py`。`fill` / `blit` / `blitSprite` / `pose` /
`setTooltipForNextFrame` 等描述符两版一致，未动。

顺带发现：`GuiGraphics.scissorStack`、`guiRenderState` 与内部类 `ScissorStack`
在 1.21.11 分别是 private / 包级私有 / 包级私有（26.1 都是 public），
工作台预览的 PIP 需要读它们，已加 3 条最小 AW 条目。

### 族 2/3/5/8/9：包迁移

```
renderer.state.level.CameraRenderState        -> renderer.state.CameraRenderState
renderer.state.level.QuadParticleRenderState  -> renderer.state.QuadParticleRenderState
renderer.state.gui.pip.PictureInPictureRenderState
                                              -> client.gui.render.state.pip.*
resources.model.cuboid.ItemTransform(s)       -> renderer.block.model.*
resources.model.sprite.TextureSlots           -> renderer.block.model.TextureSlots
```

### 族 4/7：GUI 覆写族 `extract*` → `render*`

26.1 把整个原版 GUI 的 `render*` 家族改名成 `extract*`。涉及
`Screen`、`AbstractContainerScreen`、`AbstractWidget`/`AbstractButton`、
`AbstractSelectionList`、列表 `Entry`、`ClientTooltipComponent`。

**这里最容易误伤**，以下名字虽然也叫 `extract*`，但在 1.21.11 是真实存在的
（或本来就是 TACZ 自己的 API），已在脚本里显式排除：

* `EntityRenderer#extractRenderState` / `BlockEntityRenderer#extractRenderState`
* `SingleQuadParticle#extract` / `#extractRotatedQuad`
* `SpecialModelRenderer#extractArgument`
* `IItemHandler#extractItem`（TACZ 自有）
* `AccessorSparseUtils#extractIndices`（TACZ 自有）

第 06→07 轮就是在修我自己第一版脚本对 6 个 renderer 类的误改。

### 族 6/10：FAPI 0.155 → 0.141 名称回退

| 26.1.2（FAPI 0.155） | 1.21.11（FAPI 0.141） |
|---|---|
| `ServerEntityLevelChangeEvents.AFTER_*_CHANGE_LEVEL` | `ServerEntityWorldChangeEvents.AFTER_*_CHANGE_WORLD` |
| `KeyMappingHelper.registerKeyMapping` | `KeyBindingHelper.registerKeyBinding` |
| `ClientTooltipComponentCallback` | `TooltipComponentCallback` |
| `ParticleProviderRegistry` | `ParticleFactoryRegistry` |
| `FriendlyByteBufs` | `PacketByteBufs` |
| `PictureInPictureRendererRegistry` | `SpecialGuiElementRegistry`（`ctx.bufferSource()`→`vertexConsumers()`） |
| `ExtendedMenuType` / `ExtendedMenuProvider` | `ExtendedScreenHandlerType` / `ExtendedScreenHandlerFactory` |
| `PayloadTypeRegistry.serverboundPlay/clientboundPlay` | `playC2S()` / `playS2C()` |
| `ServerPlayNetworking.createClientboundPacket` | `createS2CPacket` |
| `PlayerLookup.level` | `PlayerLookup.world` |
| `CustomIngredient#items/display` | `getMatchingItems` / `toDisplay` |
| `CustomIngredientSerializer#getStreamCodec` | `getPacketCodec` |

### 族 11 及其它单点

* `ItemStackTemplate` 1.21.11 不存在 → `SlotDisplay.ItemStackSlotDisplay(ItemStack)` 直接收 ItemStack；
* `Recipe#assemble` 补回 `HolderLookup.Provider` 形参；
* `RecipeSerializer` 在 1.21.11 是**接口** → 匿名实现替代 `new`；
* `LightCoordsUtil.pack` → `LightTexture.pack`；
* `ItemStack#typeHolder` → `getItemHolder`；
* `Camera#getCameraEntityPartialTicks(DeltaTracker)` → `getPartialTickTime()`；
* `Player#sendSystemMessage/sendOverlayMessage` → `displayClientMessage(Component, boolean)`
  （注意：`ServerPlayer` 和 `CommandSourceStack` 上 `sendSystemMessage` 仍存在，那些调用点没动）；
* `ModelManager#getBlockStateModelSet` → `getBlockModelShaper().getParticleIcon()`；
* `AbstractContainerScreen` 构造器无宽高参数 → 构造后写 `imageWidth/imageHeight`；
  且 `renderBg` 是抽象方法，必须实现（本界面背景在 `renderBackground` 里画，故留空）；
* `AbstractMinecartRenderer#submitMinecartContents` 第二参 `BlockModelRenderState` → 裸 `BlockState`；
* REI `EntryIngredients.slotDisplayContext()` → 原版 `SlotDisplayContext.fromLevel(level)`；
* PAL 1.1.9 的 `get3DTransform` 返回 `PlayerAnimBone`（1.2.5 返回 void）；
* 删除死代码 `MinecraftAccessor`（`pausePartialTick` 在 1.21.11 不存在且全仓无引用）。

### 动态物品模型（Tacz / Lr 两份）

三处接口差异：

1. `SpecialModelRenderer#submit` 在 1.21.11 **多一个 `ItemDisplayContext` 形参** ——
   反而更好：display context 不必再塞进 `RenderArgument` 偷渡；
2. `ItemModel.Unbaked#bake` **少了** `Matrix4fc inheritedTransform` 形参；
3. `LayerRenderState#setLocalTransform` 在 1.21.11 **不存在**。
   模型自带的 transformation 改为随 `RenderArgument` 下传，在 `submit()` 里
   用 `poseStack.last().pose().mul(...)` 手动施加（push/pop 包裹）。

## ⚠️ 两处必须实机验证的遗留问题

### 1. 瞄具 `CompareOp.ALWAYS_PASS` 在 1.21.11 无直接等价物（高风险）

26.1.2 的 depth-aperture 用 `CompareOp.ALWAYS_PASS` 表达
「深度测试恒通过、但仍写深度」。1.21.11 的 `DepthTestFunction` 只有
`NO_DEPTH_TEST / EQUAL / LEQUAL / LESS / GREATER`，**没有 ALWAYS**。

逐字节码确认（`GlCommandEncoder` / `GlConst` / `GlConst$1`）：

* `NO_DEPTH_TEST` 这一支走 `GlStateManager._disableDepthTest()`；
* switchmap 序号 NO_DEPTH_TEST=1 / EQUAL=2 / LESS=3 / GREATER=4，
  `toGl` 对应 1→519(GL_ALWAYS)、2→514、3→513、4→516(GL_GREATER)、default→515(GL_LEQUAL)。

即 **GL_ALWAYS 只出现在会 `glDisable(GL_DEPTH_TEST)` 的那一支**，
而 OpenGL 在深度测试禁用时连深度写入一并丢弃。所以「恒通过 + 写深度」
在这一版的 pipeline 状态里无法直接表达。

当前取 `GREATER_DEPTH_TEST`：
* **depth-cleanup 语义等价**（把更远的世界深度写回近处手部深度之上，新 > 旧，GL_GREATER 通过）；
* **etched / visible reticle 不等价**——原本依赖无条件通过，GL_GREATER 会丢弃
  位于已写入镜面深度之前的准星像素。

若实机出现准星缺失/闪烁：不要换枚举，正确解法是在既有的
`GlCommandEncoderScopeDepthCopyMixin`（已 hook `drawFromBuffers` HEAD）里，
对这几条管线在 vanilla 应用完状态后补一次 `_depthFunc(GL_ALWAYS)`，
保持 `GL_DEPTH_TEST` 启用。代码里已用 `TODO(1.21.11 scope)` 标注。

### 2. Controllable 是向下跨版本

1.21.11 最新 Fabric 构建是 **0.25.7**，26.1.2 用的是 0.26.0。
阶段 10 恢复 `compat/controllable/**` 时必须重新核对符号。

## 首轮可关闭清单的实际状态

| 模块 | 状态 |
|---|---|
| Iris | **已关闭**（`sourceSets` 排除 `mixin/client/iris/**`，`tacz.iris.mixins.json` 的 client 列表清空）。`compat/iris/IrisCompat` 门面仍编译 |
| Accelerated Rendering | 保持 26.1.2 的关闭状态 |
| KubeJS / OptiFine / KosmX | 保持 26.1.2 的关闭状态 |
| PAL / JEI / REI / Cloth / ModMenu / Zoomify / Shoulder Surfing / Controllable | **全部参与编译且已通过** |

注：`tacz.compat.acceleratedrendering.mixins.json` 引用了被排除的
`BedrockPartMixin`，但该配置未列入 `fabric.mod.json` 的 mixins，不会被加载
（26.1.2 就是这个状态，未改动）。

## 产物验证

`build/libs/TACZ-Refabricated-1.21.11-1.1.8+fabric.1.21.11.R1.jar`（约 57.8 MB）

* ✅ `tacz.refmap.json` 存在，44 个 mixin 类，内容为 intermediary（`class_` / `method_`）名；
* ✅ `tacz.accesswidener` 头部已被 remap 成 `intermediary`，条目全部转为 `class_*`；
* ✅ 5 个 `include()` 库已内嵌于 `META-INF/jars/` 并在 `fabric.mod.json` 的 `jars` 段声明
  （bcel / commons-math3 / luaj-core / luaj-jse / mae）；
* ✅ `fabric.mod.json` 变量替换正确：version `1.1.8+fabric.1.21.11.R1`，
  depends minecraft `1.21.11`、java `>=21`、fabric-api `>=0.141.6`、forgeconfigapiport `>=21.11.1`；
* ✅ 已加载的 4 个 mixin 配置里，每个 mixin 类都存在于源码树；
* ✅ 类文件引用 intermediary 名，确认为 remapped artifact。

## 剩余编译警告

只剩 1 条 `Cannot find target method`：`HumanoidModelMixin` 的
`setupAnim(LivingEntity,FFFFF)V`。该类**未注册**到任何已加载的 mixin 配置，
其类注释（第 42 轮）已明确判定为永久废弃——26.1.2 分支就是这个状态，本次未改动。

`LivingEntityRendererMixin` 原有的两条同类警告**已修复**（它是**已注册**的，
不修必定启动崩溃）：
* `submit(...)` 描述符里的 `state/level/CameraRenderState` → `state/CameraRenderState`；
* `render(LivingEntity,LivingEntityRenderState,float)` 在 1.21.11 不存在 → `extractRenderState(...)`。

其余警告为 `@Inject` 描述符无法静态判定（mixin AP 的常规提示）与
两条“public 目标建议写进 value”的风格提示，不影响运行。

## 下一步（交接清单阶段 5 起）

编译与打包已通，往下都必须在**有图形界面的真机**上做：

1. `./gradlew runClient`，开 `-Dmixin.debug.verbose=true -Dmixin.debug.export=true`，
   一次只放开一族 mixin；
2. 阶段 6 工作台与资源：枪包加载、recipe codec、GUI 预览、`/reload`、服务端同步；
3. 阶段 7 vanilla 瞄具——**重点验证上面第 1 条遗留问题**；
4. 阶段 8 LRTactical；
5. 阶段 9 Iris（需先把 `sourceSets` 的排除去掉并重对 `ShaderCreator` 注入点）；
6. 阶段 10 其余可选兼容，Controllable 注意版本倒退。

### 沙箱适配项（真机请改回）

`gradle.properties` 的 `org.gradle.jvmargs` 现为 `-Xmx1024m -XX:MaxMetaspaceSize=384m`。
这是为 2 GB 沙箱压出来的：`remapSourcesJar` 在更高堆下会被 OOM killer 杀掉
（`build` 因此失败，`remapJar` 本身没问题）。真机建议改回 `-Xmx2G` 或更高，
届时 `./gradlew build` 应可一次跑完（含 sourcesJar）。

---

# 附录：首次实机启动崩溃修复（2026-08-13）

## 崩溃

```
InvalidInjectionException: @Inject annotation on tacz$callInteractionPickInput
could not find any targets matching 'pickBlockOrEntity' in net/minecraft/class_310
```

## 根因：编译期警告掩盖了运行期崩溃

mixin AP 对**不带描述符**的注入目标（`method = "foo"` 而非 `method = "foo(...)V"`）
只能发 warning「Unable to determine descriptor」，**不会报错**。
于是名字写错照样编译通过，直到启动才炸。

阶段 2 的日志里有 6 条这种 warning，我当时判断为「mixin AP 常规提示，不影响运行」——
**这个判断是错的**。

## 已修

用 `docs/verify_mixin_targets.py` 对**全部已注册 mixin**逐一核验（javap 打真 jar），
共查 98 个目标 / 44 个原版类，查出 2 处真实错误：

| 文件 | 26.1.2 | 1.21.11 | 说明 |
|---|---|---|---|
| `MinecraftMixin` | `pickBlockOrEntity` | **`pickBlock`** | 26.1 改的名 |
| `CameraMixin` | `calculateFov` / `calculateHudFov` / `update` | **全都不存在** | 见下 |

### CameraMixin 需要重构，不是改名

26.1 把 FOV 计算搬进了 `Camera`；1.21.11 的 `Camera` 里这三个方法一个都没有：

* **FOV** 仍在 `GameRenderer#getFov(Camera, float, boolean)`，
  布尔参 `true`=世界 FOV、`false`=手部/HUD FOV
  （字节码确认：只有 true 分支读 `options.fov()` 并乘 `fovModifier`）。
  → 两个 FOV 事件合并到 `GameRendererMixin` 的这一个方法上，按布尔参分派；
* **相机角度** 入口是
  `Camera#setup(Level, Entity, boolean detached, boolean thirdPersonReverse, float partialTick)`，
  对应 26.1 的 `update`。partialTick 由形参直接给出，
  顺带去掉了上一轮临时用的 `getPartialTickTime()`（那只是缓存值，形参更准）。

影响的是 **ADS 开镜缩放** 与 **后坐力/相机晃动**——都是核心手感，
若这两处失效，表现为开镜不缩放、开火不震动。

### 5 处误报（已确认不是问题）

`setSprinting` / `stopUsingItem` / `turn` / `getItemInHand` 等 `@At` target
报「找不到」，实为**继承自 `Entity` / `LivingEntity`** 的方法。
校验脚本已改为沿超类链向上查找，避免误伤。

## 校验脚本

`docs/verify_mixin_targets.py`，仓库根目录执行：

```bash
python3 docs/verify_mixin_targets.py
```

检查三类：
1. `method = "name"` —— 名字是否存在于目标类（含继承）；
2. `method = "name(desc)"` —— 该描述符是否精确存在；
3. `@At(target = "Lowner;name(desc)ret")` —— 被调用成员是否存在于 owner。

**每次改动 mixin 后都应跑一遍**——它能在启动前抓出这类崩溃。
当前状态：98 个目标全部通过。

## 仍未验证

本次只解决了「启动阶段第一个崩溃」。后续阶段（工作台、瞄具、LRTactical、Iris）
仍需实机逐项验收。若再崩，日志里的 `could not find any targets matching 'X'`
就是下一个要查的名字，方法同上。

## 第二次实机崩溃：注入方法形参不匹配（2026-08-13）

`pickBlock` 修复后启动推进了很多（TACZ 初始化、枪包扫描、Iris 管线注册全部完成），
在 `GameRenderer` 上撞到下一个：

```
Invalid descriptor on GameRendererMixin->@Inject::tacz$beginHandPass!
Expected (FZLorg/joml/Matrix4f;...)V
but found (Lnet/minecraft/class_12075;FLorg/joml/Matrix4fc;...)V
```

**这是与上次不同的一类问题**：方法名是对的，但注入方法（handler）的**形参列表**
必须与目标方法逐一对应，而我们带的是 26.1 的形参。

| 方法 | 26.1.2 | 1.21.11（javap 核实） |
|---|---|---|
| `renderItemInHand` | `(CameraRenderState, float, Matrix4fc)` | `(float, boolean, Matrix4f)` |
| `bobHurt` | `(CameraRenderState, PoseStack)` | `(PoseStack, float)` |
| `bobView` | `(CameraRenderState, PoseStack)` | `(PoseStack, float)` |

三处都是：26.1 加了 `CameraRenderState` 首参；`renderItemInHand` 还多一个 `boolean`
且 `Matrix4fc`→`Matrix4f`（接口 vs 实现类，描述符不同）。
`cameraState` 在这三个 handler 里本来就没用到，删掉无影响；
`bobHurt` 原先手工从 `DeltaTracker` 取 partialTick，现在形参直接给，更准。

`render(DeltaTracker, boolean)` 与新加的 `getFov` 签名本来就是对的，未动。

### 校验脚本已升级（关键）

上一版只校验方法**名**，所以放过了这次的形参问题。现在 `verify_mixin_targets.py`
增加第 4 类检查：**`@Inject` handler 的前 N 个形参必须与目标方法形参一致**
（允许尾部截断，那是 mixin 合法的部分捕获）。

当前状态：**101 项全部通过**（含形参校验）。

每次改 mixin 后务必执行：

```bash
./gradlew help            # 确保 1.21.11 named jar 在 Loom 缓存里
python3 docs/verify_mixin_targets.py
```

---

## 第三次启动故障：黑屏（非崩溃）—— shader `#moj_import` 悬空

### 现象

游戏不崩溃，日志无 mixin 报错，卡在黑屏。日志末尾停在字体加载
（`Found unifont_jp_patch-17.0.01.hex, loading`），进程仍活着。

### 日志里的两条线索

```
[13:01:12] [Render thread/INFO]: Caught error loading resourcepacks, removing all selected resourcepacks
java.lang.NullPointerException: Cannot invoke "net.minecraft.class_3298.method_43039()"
        because the return value of "java.util.Map.get(Object)" is null
    at net.minecraft.class_10151$1.method_34233(class_10151.java:118)
    at net.minecraft.class_5913.method_34232 / method_34229
    at net.minecraft.class_10151.method_62939 / method_62942
```

用 Loom 的 `mappings.tiny` 反查中间名（**不是 atlas/精灵加载器**，早前的猜测是错的）：

| intermediary | 官方名 |
|---|---|
| `class_10151` | `net.minecraft.client.renderer.ShaderManager` |
| `class_5913`  | `com.mojang.blaze3d.preprocessor.GlslPreprocessor` |
| `class_3298`  | `net.minecraft.server.packs.resources.Resource` |

`GlslPreprocessor` 出现在栈里 ⇒ 这是**处理 `#moj_import` 的代码路径**。
`Map.get()` 返回 null ⇒ 某个 import 指向了不存在的 include 文件。

### 根因

`assets/tacz/shaders/core/scope_body.vsh` 是从 **26.2 的 `entity.vsh` 逐字节抄来**的
（文件头注释自己写明了这点）。它包含：

```glsl
#moj_import <minecraft:sample_lightmap.glsl>
...
lightMapColor = sample_lightmap(Sampler2, UV2);
```

`sample_lightmap.glsl` 是 **26.x 才引入的 include，1.21.11 没有**。
1.21.11 原版 `shaders/include/` 只有 8 个文件：

```
animation_sprite  chunksection  dynamictransforms  fog
globals  light  matrix  projection
```

1.21.11 的 `entity.vsh` 是直接内联采样的：

```glsl
lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
```

这一条悬空 import 让 `ShaderManager` 抛 NPE → 整个资源重载失败 → MC 卸掉所有资源包
重来一遍 → 客户端停在黑屏。**这就是黑屏的直接原因。**

### 修复

新增 `src/main/resources/assets/tacz/shaders/core/scope_body.vsh`（覆盖 bundle 里的旧版）：

1. 删掉 `#moj_import <minecraft:sample_lightmap.glsl>`；
2. `sample_lightmap(Sampler2, UV2)` → `texelFetch(Sampler2, UV2 / 16, 0)`；
3. `light.glsl` 的 import 去掉 `#if` 包裹，与 1.21.11 原版一致。

`scope_body.fsh` / `scope_flash_clip.fsh` / `scope_reticle_mask.fsh` / `scope_depth_cleanup.fsh`
的 import 全部核对过，只用到 `fog` / `dynamictransforms` / `globals`，均存在，无需改动。
`globals.glsl` 提供 `ScreenSize`，`dynamictransforms.glsl` 提供 `ColorModulator`，
`scope_body.fsh` 里用到的 uniform 都有来源。1.21.11 的 `shaders/core/` 不需要 `.json` 定义文件。

### 顺带修掉的 `sounds.json`

```
Invalid sounds.json in resourcepack: 'tacz'
com.google.gson.JsonSyntaxException: Expected entry to be a JsonObject, was "Sou...e."
```

来自 `assets/lrtactical/sounds.json` 的第一个键：

```json
"_comment": "Sound index for LRTactical. The tinnitus clip was supplied by ... crossfade."
```

`sounds.json` 的 schema 要求**每个顶层条目都是 JsonObject**，不允许字符串形式的注释键。
已重写该文件去掉 `_comment`。（这条只是让音效索引整体失效，不会导致黑屏，但同样要修。）

### 构建侧加固

`processResources` 之前依赖 `DuplicatesStrategy.EXCLUDE` 的「先加入者胜」来让
`src/main/resources` 覆盖 bundle。这个顺序是隐式的，一旦失效，bundle 里的旧文件会
静默盖掉修复。现改为**显式枚举** `src/main/resources` 下的所有文件并从 bundle 的
copy spec 里 `exclude` 掉。

### 新增校验脚本 `docs/verify_shader_imports.py`

把所有自定义 shader（源码树 + 资源 bundle）的 `#moj_import` 目标与原版 jar 里
真实存在的 `shaders/include/**` 比对。这类 bug **编译期查不出**（GLSL 不过 javac），
**mixin 校验也查不出**（不是 mixin），只能靠它。

修复前：`共检查 16 条 #moj_import` → `!! 1 条悬空 import`（精确指到 bundle 里的 scope_body.vsh）。

### 教训

- **"从 26.2 逐字节复制 vanilla 文件" 这个做法对资源文件同样危险**，不只是 Java 代码。
  凡是注释里写着「与 26.2 的 xxx 逐字节相同」的文件，都必须对着 1.21.11 原版重新核对。
- 反查 intermediary 类名要用 `mappings.tiny`（含 named 列），
  `intermediary-v2.tiny` 只有 official↔intermediary 两列，查不到官方名。
- **非崩溃的黑屏同样有明确根因**，不要因为没有 crash report 就去猜。
  `Caught error loading resourcepacks` 上方的那个异常就是答案。

---

## 第四次启动故障：`lambda$loadResources$2` 找不到目标

### 好消息先说

黑屏已修复。本次日志 `Last reload: Reload number 1 / Finished: Yes`，
资源重载完整跑通，游戏进入主菜单。上一节的 shader 悬空 import 确认就是黑屏根因。

### 现象

在世界选择界面崩溃（`class_525` = `CreateWorldScreen` 路径首次加载 `class_5350`）：

```
Mixin apply for mod tacz failed tacz.fabric.mixins.json:common.ReloadableResourcesMixin
Critical injection failure: @ModifyArg annotation on tacz$addReloadListener
could not find any targets matching 'lambda$loadResources$2' in net/minecraft/class_5350
```

注意崩溃时机：不是启动时，而是**第一次真正加载 `ReloadableServerResources` 这个类**时
（点世界列表触发）。mixin 是懒应用的，所以这类错误会潜伏到对应类被加载才爆。

### 根因

```java
@ModifyArg(method = "lambda$loadResources$2", ...)
```

`lambda$loadResources$N` 是 **javac 为 lambda 生成的合成名**。这个写法在 26.x 能用，
因为 26.x 的 Minecraft **不混淆**，类里真的存在这个名字的方法。

1.21.11 **是混淆版本**。它的 lambda 在 intermediary 映射里有正式名字，
而 refmap 里**根本没有 `lambda$` 开头的条目** —— 实测导出的 `tacz.refmap.json` 中
`ReloadableResourcesMixin` 只有 `@At` 那条 `SimpleReloadInstance.create` 的映射，
`method` 那一项没有任何对应。名字被原样传给 mixin，在混淆类里当然找不到。

`javap` 确认 1.21.11 的 `ReloadableServerResources` 里没有任何 `lambda$` 方法，
那个 lambda 叫 **`method_58296`**：

```
private static CompletionStage method_58296(
    FeatureFlagSet, Commands$CommandSelection, List, PermissionSet,
    ResourceManager, Executor, Executor, ReloadableServerRegistries$LoadResult)
```

字节码核对（三个捕获全部对得上）：

| 需求 | 字节码证据 |
|---|---|
| `@At` 注入点 | `45: invokestatic SimpleReloadInstance.create:(...)` ✓ |
| `index = 1` | create 的第 2 个实参是 `aload 8` → `listeners()` 返回的 List ✓ |
| `@Local(ordinal = 0) ReloadableServerResources` | `21: astore 8` 新建实例存 slot 8 ✓ |
| `@Local(argsOnly = true) LoadResult` | 最后一个形参就是 `LoadResult` ✓ |

`method_58296` 在 `intermediary-v2.tiny` 里有正式条目（obf 名 `a`），
**不是**裸合成名，所以 refmap 会正常把它重映射过去。

### 修复

`method` 改为 intermediary 名 **+ 完整描述符**（带描述符可避免将来重载歧义）：

```java
@ModifyArg(
    method = "method_58296(Lnet/minecraft/world/flag/FeatureFlagSet;...)Ljava/util/concurrent/CompletionStage;",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(...)"),
    index = 1)
```

### 校验脚本的盲区（本次真正的教训）

`docs/verify_mixin_targets.py` 上一版第 171 行写着：

```python
if t.startswith(('lambda$', 'method_')):
    continue     # <-- 正是这一行放过了导致崩溃的目标
```

**它主动跳过了 lambda 目标**，所以 101 项全 OK 的报告是有水分的。
已修正为：

- `lambda$...` → **直接判定为错误**（混淆版本下这个名字不可能存在）；
- `method_NNNNN` → 不再跳过，走正常的名称/描述符校验（javap 输出里本来就有）。

回归验证：把源码临时改回 `lambda$loadResources$2`，脚本报
`[LAMBDA] ReloadableResourcesMixin.java ... 混淆版本下不存在此合成名`，退出码 1；
改回正确写法后 **102 项全部 OK**（比之前多 1 项，就是这个以前被跳过的目标）。

顺带核对了另外两处早已使用中间名的目标，均真实存在，无需改动：
`ChannelAccess$ChannelHandle.method_19737`、`SoundEngine.method_19757`。

### 教训

- **「从 26.x 抄过来」在混淆/非混淆的边界上一律要重新核对**。
  这已经是同一个根源的第三种表现：Java 签名（`renderItemInHand`）、
  资源文件（`sample_lightmap.glsl`）、现在是 lambda 合成名。
- **校验脚本里每一个 `continue` 都是一个潜在盲区**。写 skip 分支时必须问：
  被跳过的这类东西，出错时会不会崩？会崩就不能跳，要么校验要么直接报错。
- **mixin 是懒应用的**：目标类没被加载就不会报错。启动无异常 ≠ mixin 全部有效，
  必须走到对应功能路径（本例是点开世界选择界面）才知道。

---

## 首次实机渲染反馈：镜内准星缺失 / 光影下镜内无水体 / 准星溢出镜框

游戏已能正常进入世界并开镜（截图 09、27）。三个渲染问题，分属两个不同根因。

### 症状 1 + 2 是同一个东西的两面

| | 无光影 | 有光影(BSL) |
|---|---|---|
| 镜内准星 | **完全不显示** | 正常显示 |
| 镜内水体/水面/粒子/云/雾 | 正常 | **不渲染** |

「有无光影表现相反」这件事本身就是最强的线索：说明两条代码路径是**分开**的，
各自坏在不同地方。

#### 症状 1（无光影下准星消失）—— `GREATER_DEPTH_TEST` 兜底是错的

这正是移植时留在 `ScopeRenderTypes` 里的 `TODO(1.21.11 scope)` 所预测的情况。

26.1.2 用 `CompareOp.ALWAYS_PASS` 表达「深度测试恒通过、但仍然写深度」。
1.21.11 的 `DepthTestFunction` **没有 ALWAYS**，而 `NO_DEPTH_TEST` 会
`glDisable(GL_DEPTH_TEST)` —— OpenGL 在深度测试禁用时**连深度写入一并丢弃**。
当时退而求其次全部用了 `GREATER_DEPTH_TEST`。

对 depth-cleanup 它是对的（把**远**的世界深度写回近处，new > old，语义等价）。
但对两条 reticle 管线是**致命**的：

```
depth-cleanup 刚把目镜区域写成【世界远深度】
准星几何位于【手部近深度】
GL_GREATER 要求 new > old  →  near < far 不成立  →  准星像素全部被丢弃
```

开光影时 Iris 用自己的 HAND 程序和状态，绕开了这条 vanilla 管线状态，
所以准星反而是好的 —— 完美解释了上面那张对比表的第一行。

**当时的 R5 初步修复**（即 TODO 里写明的正解）：两条 reticle 管线声明为
`NO_DEPTH_TEST`，再在已有的 `GlCommandEncoderScopeDepthCopyMixin`（已 hook 在
`drawFromBuffers` HEAD，位于 vanilla 应用完管线状态之后、真正 `glDraw*` 之前）补：

```java
GlStateManager._enableDepthTest();          // 顺序要紧：先 enable
GlStateManager._depthFunc(GL11.GL_ALWAYS);  // 再设成恒通过
GlStateManager._depthMask(true);            // R5 的旧实现：后来已删除
```

该版本意在补出 1.21.11 枚举无法表达的「恒通过 + 仍写深度」，识别方式为
`IdentityHashMap` 白名单 `FORCE_ALWAYS_DEPTH_PIPELINES`，只影响 TACZ 自己的两条管线。

**后续更正**：R7 已删除 `_depthMask(true)` 并令两条 reticle 管线保持
`depthWrite=false`；其后的 `GlRenderPipeline.info()` 反射实现也被确认不适用于混淆运行时。
当前直接 typed accessor、Iris 晚期准星提交的修复，以及“雾/水覆盖并非已由深度状态完全解释”
的结论，见本文最后的 **R8** 一节。

depth-cleanup **保持** `GREATER`（本来就正确，实机也正常）。

#### 症状 2（光影下镜内无水体/云/雾）—— Iris mixin 仍处于禁用状态

`build.gradle` 里 `exclude 'com/tacz/guns/mixin/client/iris/**'`，
`tacz.iris.mixins.json` 的 client 列表为空 —— 这是阶段 1 就定下的**已知取舍**
（Iris 只在 `-PwithIris` 的 runtimeOnly 配置里，编译期不可见，mixin AP 会报错）。

被禁用的 `IrisDepthRestoreShaderMixin` 的职责，正是把
depth-restore 和目镜掩码分支注入 Iris 的**手部片元着色器**。没有它，
Iris 的 translucent/composite 阶段（水体、水面、云、雾、粒子都在这些阶段）
不知道目镜区域该显示世界内容，于是镜内这些效果整体缺失。

**这不是回归，是尚未开工的阶段 9。** 本轮不动它 —— 恢复它需要
对着 Iris 1.10.7 重新核对注入点（26.1.2 用的是 1.11.2，内部结构不同），
属于独立的一块工作。

### 症状 3（准星溢出目镜到镜框）—— 与前两者无关，暂未修

无论有无光影都会溢出，说明它既不在 vanilla 深度路径也不在 Iris 路径上，
而在**掩码判据**本身：`scope_reticle_mask.fsh` 用

```glsl
apertureDepth < worldDepth - TACZ_MASK_EPSILON   // 1.0e-6
```

判断像素是否落在目镜内。这个 epsilon 是**绝对值**，而深度缓冲是非线性的：
在远距离处相邻深度值的差远小于近处，边缘一圈像素的 aperture/world 深度差
会退化到 1e-6 附近，判据在镜框边缘变得不稳定，于是准星"漏"出去一圈。

要正确修需要实机采样边缘处的实际深度差来定标（或改用线性化深度 / 相对判据），
不能凭空调参 —— 盲目放大 epsilon 会让镜内准星整体被裁掉，反而更糟。
**留待下一轮，需要用户配合做一次对比测试。**

### 顺带发现（本轮未改，无风险）

编译期两条 mixin 警告：

- `HumanoidModelMixin.setupAnim(LivingEntity;FFFFF)V` —— 1.21.11 是 `setupAnim(T)`（单参渲染态）；
- `ShapedRecipeMixin.itemStackFromJson` —— 该方法已不存在。

两者**都没有注册在任何 mixin config 里**（`fabric.mod.json` 的 4 个配置中均无），
属于死代码，不会在运行期应用，因此不会崩。记录在此以免将来有人把它们加回配置。

---

## 收尾：Iris 镜内效果缺失（阶段 9）+ 准星溢出镜框

R5 的准星修复实机通过。本轮处理剩下的两个已知问题，**两者根因完全不同**。

### 一、Iris 下镜内不渲染水体/水面/粒子/云/雾 —— 恢复阶段 9

#### 原来为什么被禁

`IrisDepthRestoreShaderMixin` 用 `@Mixin(targets = "...ShaderCreator")` 打进 Iris 内部。
移植时 Iris 只挂在 `-PwithIris` 的 `modRuntimeOnly` 上，**编译期不可见**，
mixin AP 报 "target could not be found"，只能整包 `exclude`。
而这个 mixin 的职责正是把目镜掩码/深度恢复分支注入 Iris 的**手部片元着色器** ——
没有它，Iris 的 translucent/composite 阶段（水、云、雾、粒子都在这些阶段）
不知道目镜区域该显示世界内容，于是镜内这些效果整体缺失。

#### 做法

把 Iris 改为 **`modCompileOnly`**（不进成品、不进普通运行 classpath），
编译期即可见，mixin AP 正常解析，`exclude` 随之移除，
`tacz.iris.mixins.json` 恢复 `plugin` + `client` 列表。

双重兜底保证未装 Iris 的用户不受影响：
`required: false` + `IrisCompatMixinPlugin.shouldApplyMixin()` 的 `isModLoaded("iris")`。

#### 对 Iris 1.10.7 逐条核实注入点（这是关键，不能想当然）

下载真实的 `iris-fabric-1.10.7+mc1.21.11.jar` 用 javap 核对：

| 断言 | 结果 |
|---|---|
| `ShaderCreator.link(String×6, VertexFormat, boolean)` 存在 | ✓ |
| `link` 内 `createShader` 调用顺序 | VERTEX / GEOMETRY / TESSELATION_CONTROL / TESSELATION_EVAL / **FRAGMENT** |
| 故 `@ModifyArgs(ordinal = 4)` 指向 FRAGMENT | ✓ 正确 |
| `create`/`createFallback`/`createFallbackShadow`/`createShadow` 是否都走这个 link | ✓ 四条路径全部汇聚，patch 一处即可 |
| 传入的 `name` 是什么 | `ShaderKey.getName()`，其实现是 `toString().toLowerCase(ROOT)` |
| 即 `HAND_CUTOUT` → `"hand_cutout"`，与 `tacz$isHandProgram` 的小写判据一致 | ✓ |

编译验证：AP 的报错从 "target could not be found" 变成
"Mixin target ... is public and should be specified in value"（仅风格提示），
**说明目标已能正常解析**。BUILD SUCCESSFUL。

### 二、准星溢出目镜到镜框 —— 是绘制顺序，不是 epsilon

上一轮我猜是 `scope_reticle_mask.fsh` 里 `1e-6` 这个绝对 epsilon 在非线性深度缓冲上
失稳。**这个猜测是错的**，真正原因查明如下。

各批次的提交顺序原本是：

```
aperture(-3) → body(-2) → depth cleanup(-1) → gun(0) → ocular_ring(1) → reticle(2)
```

准星的「是否在镜内」判据读的是 `APERTURE_TARGET`，而这张深度快照是在
**order -2（body 绘制边界）** 就拷贝好的 —— 那个时刻 `ocular_ring`（物理镜框）
**根本还没画**，掩码里没有镜框的任何信息。于是压在镜框上的准星像素顺利通过判据，
表现为准星"漏"到镜框上。

这也解释了为什么**有无光影都会溢出**：它与深度测试函数无关、与 Iris 无关，
纯粹是提交顺序问题。

上游 1.21.1 的顺序本来就是「**先准星、后 ocular_ring**」，用不透明镜框盖住溢出部分；
移植时把两者调换了。改回上游顺序：

```
... → gun(0) → reticle(1) → ocular_ring(2)
```

**没有动 epsilon** —— 盲目放大它会把镜内准星一起裁掉，是更糟的做法。

### 教训

- **「上游是怎么做的」永远要先查一遍再自己推理**。溢出问题我第一轮从深度精度入手，
  方向就偏了；实际只是移植时把两个 order 常数写反了。
- **快照类的掩码要问清楚「快照在哪一刻拍的」**。`APERTURE_TARGET` 拍于 -2，
  任何 -2 之后才绘制的几何体都不在掩码里，这是这套 depth-aperture 方案的固有约束。
- 编译期 mixin AP 的报错措辞可以当作验证信号：
  "target could not be found"（找不到类）vs "should be specified in value"（找到了）。

---

## R8：`GlRenderPipeline.info()` 混淆反射故障与 Iris 晚期准星提交（2026-08-13）

R7 将两条 reticle 管线改为 `depthWrite=false`，这仍然是正确的：准星不应覆盖
`depth-cleanup` 刚恢复的世界深度。但原报告把这项深度保护直接定性为“雾/水覆盖准星”的
完整根因，是过度结论。后续日志还暴露了一条**确定的、独立的运行期 Bug**。

### 一、确定 Bug：反射里的 `info` 不会 remap

`GlCommandEncoderScopeDepthCopyMixin` 的目标形参原先写为：

```java
@Coerce Object glRenderPipeline
```

之后通过：

```java
glRenderPipeline.getClass().getMethod("info")
```

读取 pipeline info。`GlRenderPipeline`（运行时 `class_10867`）在源码映射中有 public
record accessor `info()`；但是 1.21.11 是混淆运行时，实际方法名为 `comp_3801()`。

普通 Java 调用会由 Loom 处理：`glRenderPipeline.info()` 会在成品中变为正确的运行时调用。
反射 API 中的 `"info"` 只是普通字符串，Loom 不会修改它。因此旧实现会在运行时寻找一个
不存在的 `info` 方法，抛 `NoSuchMethodException`，再记录：

```text
[TACZ Scope] Cannot read GlRenderPipeline.info(); reticle depth override disabled.
```

这与此前 `lambda$loadResources$2` 在混淆版本中失效属于同一类错误：把仅在开发映射中
成立的名字藏进不参与 remap 的字符串。

### 二、修复：直接使用类型安全 accessor

mixin 现在保留 `glRenderPass` 的 `@Coerce Object`，但将 pipeline 参数改为真实类型：

```java
import com.mojang.blaze3d.opengl.GlRenderPipeline;

private void tacz$copyScopeDepth(
        @Coerce Object glRenderPass,
        int baseVertex,
        int firstIndex,
        int indexCount,
        VertexFormat.IndexType indexType,
        GlRenderPipeline glRenderPipeline,
        int instanceCount,
        CallbackInfo ci
) {
    // ...
    tacz$forceAlwaysDepthIfNeeded(glRenderPipeline);
}

@Unique
private static void tacz$forceAlwaysDepthIfNeeded(GlRenderPipeline glRenderPipeline) {
    if (glRenderPipeline == null
            || !ScopeRenderTypes.needsForcedAlwaysDepth(glRenderPipeline.info())) {
        return;
    }
    GlStateManager._enableDepthTest();
    GlStateManager._depthFunc(GL11.GL_ALWAYS);
}
```

同时删除 `java.lang.reflect.Method`、`tacz$pipelineInfo(...)`、Method 缓存与失败日志；
`needsForcedAlwaysDepth` 也收紧为 `RenderPipeline` 参数。这样 accessor 调用会进入 Loom
正常的 remap 链路。

**不要**恢复 `withDepthWrite(true)` 或 `_depthMask(true)`。两条 reticle 管线继续使用：

```text
GL_ALWAYS（由 encoder mixin 补出）
+ depthWrite=false
```

这使准星可以写颜色，同时保留 cleanup 恢复的世界深度给后续 pass 使用。

### 三、这项 Bug 不能单独解释水/雾覆盖

反射失败时，reticle 的实际状态是：

```text
NO_DEPTH_TEST + depthWrite=false
```

修复后是：

```text
GL_ALWAYS + depthWrite=false
```

对“画颜色、但不写深度”而言，两者的可见结果非常接近。所以反射故障是必须修的明确
运行期 Bug，却不是“BSL 水面/雾为何仍可改变准星颜色”的充分解释。

首次 BSL 回归已经确认反射故障修复后，水雾仍会覆盖准星。因此问题正式归类为
Iris hand shader 的绘制时序/着色问题，不能再靠重新开启 reticle 深度写入修补。

### 四、R8 局部重构：冻结 3D 快照，延后到 `HAND_TRANSLUCENT`

对 Iris 1.10.7 源码逐项核实：

```text
HandRenderer.renderSolid()
  → HAND_SOLID
  → TACZ 枪 / scope body / cleanup
  → Iris beginTranslucents()
  → 世界水面、雾、粒子、天气
  → HandRenderer.renderTranslucent()
  → HAND_TRANSLUCENT
  → composite / final
```

但 `HandRenderer#renderTranslucent()` 有一个额外门禁：没有原版半透明手持方块时，
`isAnyHandTranslucent()` 为 false，方法直接 `endFrame()` 返回。TACZ 枪不是 `BlockItem`，
所以仅把 RenderPipeline 分类改为 `HAND_TRANSLUCENT` 不会实际生成晚期 hand pass。

R8 的实现边界如下：

1. solid pass 保留 aperture、body、depth cleanup；它们仍在原 hand FBO/深度附件里完成；
2. `BedrockAttachmentModel` 不再在 solid pass 直接提交 reticle，而是冻结已经包含原始
   3D 位姿、ADS、后坐和晃动的不可变 `BedrockRenderSnapshot`；
3. `IrisHandRendererReticlePassMixin` 仅在有待提交 reticle 时把
   `isAnyHandTranslucent()` 的结果提升为 true，使 Iris 运行一次自己的 late hand pass；
4. 该 mixin 在 Iris 已设为 `HAND_TRANSLUCENT`、但尚未进入其 `endBatch()` 前，将快照
   提交到 Iris 原有 `SubmitNodeStorage`。实际 GPU draw、FBO、program 和 sampler 仍完全由
   Iris 正常调度；
5. 蚀刻和发光 reticle 都映射为 `HAND_TRANSLUCENT`（Iris `HandWater`）；
   `ocular_ring` 使用独立 late cutout pipeline 且 order 更高，在准星之后重画，继续遮盖边缘溢出；
6. reticle 继续 `GL_ALWAYS + depthWrite=false`，不覆盖 cleanup 恢复的世界深度。

这不是 HUD、PIP 或第二次世界渲染：移动的是同一份 3D 快照的**提交时机**，不是模型、
相机或世界的渲染方式。

顺带修正 `IrisCompat#shouldRenderInCurrentHandPhase`：Iris 1.10.7 的实际 API 是
`isHandTranslucent(InteractionHand)`，旧代码反射 `ItemStack` 参数会失败并 fail-open。
若 late pass 被触发，这会让完整不透明枪体错误地再提交一次；现优先调用
`InteractionHand.MAIN_HAND`，只在其他 Iris 线保留旧 `ItemStack` overload 的 fallback。

### 五、R8 实机验收

启用 BSL、实际开镜且超过准星淡入阈值后，日志必须看到：

```text
[TACZ Scope] Queued reticle for Iris HAND_TRANSLUCENT.
[TACZ Scope] Deferred reticle and ocular rim to Iris HAND_TRANSLUCENT.
```

再验证无光影、BSL、水下、隔水看目标、浓雾和雨天。若上述两行出现但水雾仍改写准星，
剩余根因就在 BSL 的 `HandWater` fragment 或其后的 composite，需要针对 shader-pack
着色语义继续处理；不能回退到 `depthWrite=true`。

### 六、下一轮日志待办（本次仅记录）

1. **弹药盒染色配方无效**：`data/tacz/recipe/ammo_box_dyed.json` 使用
   `minecraft:crafting_dye`，而 1.21.11 没有该 recipe serializer，配方不会加载。
2. **船实体白名单失效**：`minecraft:boat` 与 `minecraft:chest_boat` 已不能作为通用
   `EntityType` 直接引用；需要改用正确 tag 或所有具体船类型。
3. **默认枪包音效缺失**：例如 `tacz:aug/aug_reload_empty`、
   `tacz:aug/aug_reload_tactical`；需核对 bundle、音效索引及资源路径。
4. **recipe placement 警告**：`can't be placed due to empty ingredients and will be ignored`
   可能只是 TACZ 自定义工作台不参与原版 recipe-book 自动放置，也可能是
   `PlacementInfo` 回退不完整。必须在工作台实际合成验证，不能只根据日志忽略。
