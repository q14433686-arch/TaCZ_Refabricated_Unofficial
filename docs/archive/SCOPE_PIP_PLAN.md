# 26.2 镜内渲染（PIP）技术方案

> ## ⛔ 【2026-07-26 作废通知】本文核心前提已被推翻，请先读 `SCOPE_PIP_FINDINGS_2026-07-26.md`
>
> 逐行对照上游 1.21.1 源码后确认：
>
> 1. **「镜内放大」是摄像机 FOV 变焦做的，不是离屏渲染做的。**
>    上游 `CameraSetupEvent#applyScopeMagnification` 直接改世界 FOV，
>    `applyGunModelFovModifying` 单独改手部 FOV —— 两者分离，这就是放大的全部实现。
> 2. **上游从来没有 PIP。** 全仓 grep `renderLevel` / `TextureTarget` /
>    `outputColorTextureOverride` / `renderWorld` **零命中**。
> 3. **stencil 的作用只是「挖一个圆孔」**，让主画面（已被 FOV 放大）从孔里原样透出来，
>    **不是**把离屏纹理贴上去。
> 4. **FOV 变焦这条链路我们已经完整移植好了**（与上游仅 3 处 26.2 改名差异）。
>
> **因此本文 §4.x / §8.x 关于「二次世界渲染」「性能预算」「隔帧更新」「Iris 降级」
> 的全部论述均为无的放矢，P2~P5 阶段整体作废。**
> 真正缺的只有「圆形遮罩」一个零件，成本比本文估计小一个数量级。
>
> 本文以下内容仅保留作为 26.2 PIP 框架的 API 调研记录（那部分反编译事实仍然正确）。

---

> 本文回应第 15 轮的第 ④ 项要求：**反编译 26.2 新渲染 API，思考如何实现镜内放大与叠加蒙版**。
> 全部结论来自反编译，标注了出处。**本文只是方案，尚未动手实现**——按项目惯例，先出方案再改代码。

---

## 一、结论先行

**26.2 反而比 1.21.x 更容易做镜内渲染。** 因为官方自己引入了一套完整的
「离屏渲染 → 采样回贴」框架（PIP，Picture-In-Picture），我们可以直接复用它的机制。

| 能力 | 1.21.x 上游做法 | 26.2 可用方案 |
|---|---|---|
| 镜内画面 | 无（上游也没做真正的镜内透视） | **`RenderSystem.outputColorTextureOverride` 离屏渲染** |
| 圆形裁切 | Stencil 模板缓冲 | **蒙版纹理 + alpha 混合**（stencil 已彻底移除） |
| 放大 | 改 FOV | 二次世界渲染时用窄 FOV 投影矩阵 |

---

## 二、反编译证据

### 2.1 官方 PIP 框架存在

jar 内确认存在这些类：

```
net/minecraft/client/gui/render/pip/PictureInPictureRenderer.class
net/minecraft/client/gui/render/pip/OversizedItemRenderer.class
net/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState.class
```

### 2.2 核心机制：输出纹理重定向

`PictureInPictureRenderer#prepare` 的关键片段（反编译原文）：

```java
this.prepareTexturesAndProjection(needsAResize, width, height);
RenderSystem.outputColorTextureOverride = this.textureView;   // <<< 重定向颜色输出
RenderSystem.outputDepthTextureOverride = this.depthTextureView;
Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
modelViewStack.pushMatrix();
PoseStack poseStack = new PoseStack();
poseStack.translate(width / 2.0F, this.getTranslateY(height, guiScale), 0.0F);
float scale = guiScale * renderState.scale();
poseStack.scale(scale, scale, -scale);
this.renderToTexture(renderState, poseStack, this.submitNodeStorage);
featureRenderDispatcher.renderAllFeatures(this.submitNodeStorage);   // <<< 真正绘制
modelViewStack.popMatrix();
RenderSystem.outputColorTextureOverride = null;                // <<< 还原
RenderSystem.outputDepthTextureOverride = null;
this.blitTexture(renderState, guiRenderState);                 // <<< 贴回去
```

字段本体（`RenderSystem.java` 第 73/75 行）：

```java
public static GpuTextureView outputColorTextureOverride;
public static GpuTextureView outputDepthTextureOverride;
```

### 2.3 【关键】这套机制对**世界渲染**同样有效

这是最重要的一条证据——`LevelRenderer` 自己就在用它（`LevelRenderer.java` 第 500~512 行）：

```java
private void addAlwaysOnTopPass(final FrameGraphBuilder frame, ...) {
   ...
   pass.executes(() -> {
      RenderSystem.setShaderFog(fog);
      PoseStack poseStack = new PoseStack();
      RenderTarget mainRenderTarget = (RenderTarget)mainTarget.get();
      RenderSystem.outputColorTextureOverride = mainRenderTarget.getColorTextureView();
      RenderSystem.outputDepthTextureOverride = mainRenderTarget.getDepthTextureView();
      RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mainRenderTarget.getDepthTexture(), 0.0);
      featureFrame.executeAlwaysOnTop();
      RenderSystem.outputColorTextureOverride = null;
      RenderSystem.outputDepthTextureOverride = null;
      this.checkPoseStack(poseStack);
   });
}
```

**意义**：`outputColorTextureOverride` 不是 GUI 专属的 hack，而是引擎自己在世界渲染管线里
用来切换渲染目标的正规手段。我们可以在世界渲染阶段照此办理。

### 2.4 纹理创建方式（可直接照抄）

```java
GpuDevice device = RenderSystem.getDevice();
this.texture = device.createTexture(() -> "UI ... texture", 13,
        GpuFormat.RGBA8_UNORM, width, height, 1, 1);
this.textureView = device.createTextureView(this.texture);
this.depthTexture = device.createTexture(() -> "UI ... depth texture", 9,
        GpuFormat.D32_FLOAT, width, height, 1, 1);
this.depthTextureView = device.createTextureView(this.depthTexture);
device.createCommandEncoder().clearColorAndDepthTextures(
        this.texture, GuiRenderer.CLEAR_COLOR, this.depthTexture, 0.0);
```

注意 `depthTexture` 用的是 **`GpuFormat.D32_FLOAT`（纯深度，无 stencil 位）**——
进一步印证 26.2 不打算让你用 stencil。

### 2.5 Stencil 确认已死（复核第 9/10 轮结论）

`RenderPipeline.Builder` 的全部 `with*` 方法：

```
withLocation / withFragmentShader / withVertexShader / withShaderDefine
withBindGroupLayout / withPolygonMode / withCull
withColorTargetState / withUnusedColorTargetState
withDepthStencilState        <-- 名字带 Stencil，但 DepthStencilState 里无 stencil 字段
withVertexBinding / withPrimitiveTopology
```

`DepthStencilState` 只有 `depthTest / writeDepth / depthBiasScaleFactor / depthBiasConstant`。
**没有任何 API 能设置 stencil 比较函数或写入掩码。**

但同时注意到两个**可用**的替代能力：
- `withFragmentShader(...)` —— 可以写自定义片元着色器
- `withColorTargetState(...)` —— 可以配置混合模式
- `withBindGroupLayout(...)` —— 可以绑定额外纹理（蒙版）

这就是替代 stencil 的路子。

---

## 三、方案设计

### 3.1 整体流程（每帧，仅在「瞄准 + 已装瞄具 + 第一人称」时执行）

```
┌─ 1. 准备离屏 RT（尺寸随 GUI scale / 镜片像素半径，带缓存，尺寸不变就复用）
│
├─ 2. 保存现场
│     old = RenderSystem.outputColorTextureOverride / outputDepthTextureOverride
│     递归守卫 isRenderingScope = true
│
├─ 3. 重定向输出到离屏 RT + clear
│     RenderSystem.outputColorTextureOverride = scopeColorView
│     RenderSystem.outputDepthTextureOverride = scopeDepthView
│
├─ 4. 用「窄 FOV」再渲染一次世界
│     - FOV = baseFov / zoom（zoom 取自 AttachmentDisplay 的 zoom[] / views_fov[]）
│     - 相机位置/朝向沿用主相机（若要做瞄具偏移，用 scope_view 节点的世界变换）
│     - 必须跳过：手部/手持物渲染、HUD、粒子（可选）、以及本 PIP 自身
│
├─ 5. 还原输出目标 + 递归守卫置回
│
└─ 6. 把离屏纹理作为「镜片」贴到 ocular 节点位置
      - 用自定义 RenderPipeline：片元着色器采样 (离屏纹理, 蒙版纹理)
      - out.rgb = scene.rgb;  out.a = mask.r;   // 圆形裁切
      - 再叠加 division（十字线）作为独立一层
```

### 3.2 圆形蒙版怎么做（替代 stencil）

三选一，推荐 **A**：

**A. 蒙版纹理 + alpha 混合（推荐）**
- 准备一张圆形渐变蒙版贴图（中心 alpha=1，边缘 alpha=0，带 2~3px 羽化）
- 自定义 fragment shader：
  ```glsl
  vec4 scene = texture(SceneSampler, uv);
  float m     = texture(MaskSampler, uv).r;
  fragColor   = vec4(scene.rgb, m);
  ```
- 通过 `withBindGroupLayout` 绑两张纹理，`withColorTargetState` 开正常 alpha 混合
- 优点：边缘可羽化（比 stencil 的硬边更好看）、完全可控、无兼容风险

**B. 纯几何裁切**
- 直接把镜片做成一个高分段圆形 mesh，UV 映射到离屏纹理
- 不需要自定义 shader，但边缘是多边形硬边，且要改模型

**C. 在 shader 里用 SDF 算圆**
- `float m = 1.0 - smoothstep(r-fw, r, length(uv-0.5));`
- 连蒙版贴图都省了，但灵活性差（异形镜片做不了）

### 3.3 放大倍率

数据已经就位，不用新增字段：

| 来源 | 字段 | 现状 |
|---|---|---|
| `AttachmentDisplay` | `zoom[]` | TA31 = `[2.5]` |
| `AttachmentDisplay` | `fov` | TA31 = `45.0` |
| `AttachmentDisplay` | `views_fov[]` | 可选，缺省回退到 `fov` |
| `ClientAttachmentIndex` | `getViewsFov()` / `getViews()` | 已实现，缺省 `{1}` |

离屏渲染时的投影 FOV 直接取 `viewsFov[zoomNumber % len]`。

---

## 四、风险与难点（必须正视）

| # | 风险 | 严重度 | 对策 |
|---|---|---|---|
| 1 | **递归渲染**：二次世界渲染时又触发镜内渲染 → 无限递归爆栈 | 🔴 高 | 静态 `boolean isRenderingScope` 守卫，第二层直接跳过 |
| 2 | **性能**：世界渲染 ×2，低端机可能腰斩帧数 | 🔴 高 | ①降低离屏 RT 分辨率（镜内本来就模糊，512² 足够）②只在 `aimingProgress > 0.9` 时启用 ③配置项可关 ④隔帧更新（`textureIsReadyToBlit` 就是干这个的） |
| 3 | **Iris/Sodium 冲突**：光影包会接管渲染管线 | 🟠 中 | 检测到 Iris 时降级为「静态贴图镜片」；已有 `IrisCompat.isUsingRenderPack()` |
| 4 | **实体/粒子重复渲染**：二次渲染会重复提交实体 | 🟠 中 | 复用主渲染的 `LevelRenderState`，或在二次渲染时裁剪不必要的 pass |
| 5 | **手部遮挡**：镜内不该看到自己的枪 | 🟡 低 | 二次渲染时跳过手部/手持物（本来就要跳过） |
| 6 | 深度纹理格式差异导致的 z-fighting | 🟡 低 | 离屏用独立 depth，与主 RT 无关，不会互相干扰 |

**风险 2 是最需要你拍板的**：真正的镜内渲染代价就是多渲染一遍世界，这是物理上绕不开的。
上游 1.21.1 之所以没做，很可能也是这个原因。

---

## 五、建议的实施步骤（分阶段，每阶段可独立验收）

| 阶段 | 内容 | 产出 | 风险 |
|---|---|---|---|
| **P0** | 先修「瞄准镜优先级」（本轮第①项），不涉及 PIP | 瞄准时正确对齐 scope_view 节点 | 低 |
| **P1** | 搭离屏 RT + 递归守卫，镜内**先渲染纯色**验证管线通 | 镜片变成一块纯色 = 管线打通 | 中 |
| **P2** | 镜内渲染真实世界（不放大、无蒙版，方形） | 能看到画面 | 高 |
| **P3** | 加窄 FOV 放大 | 倍率正确 | 低 |
| **P4** | 加圆形蒙版 + 羽化 | 视觉完成 | 中 |
| **P5** | 性能优化（隔帧、降分辨率、配置开关）+ Iris 降级 | 可用 | 中 |

**强烈建议至少做到 P1 就先给你验一次**——因为 P1 能以极低成本证明
「`outputColorTextureOverride` 在世界渲染阶段确实可用」这个核心假设。
如果 P1 就失败，后面的设计全都要推翻，早发现早改。

---

## 六、我需要你决策的问题

1. **性能预算**：镜内渲染必然要多渲一遍世界。你能接受吗？还是希望默认关闭、由玩家在配置里开？
2. **优先级**：是先做 P0（修瞄准对齐，小改动、见效快），还是直接冲 PIP？
3. **降级策略**：装了 Iris 光影时，是「关闭镜内渲染回退静态贴图」还是「尝试兼容」？
   （我倾向前者，兼容 Iris 的成本极高）

---

## 七、参考文件位置

| 用途 | 路径 |
|---|---|
| 官方 PIP 范例 | `net/minecraft/client/gui/render/pip/PictureInPictureRenderer` |
| 世界渲染中使用 override 的范例 | `net/minecraft/client/renderer/LevelRenderer#addAlwaysOnTopPass` |
| 输出重定向字段 | `com/mojang/blaze3d/systems/RenderSystem` 第 73/75 行 |
| 管线构建器 | `com/mojang/blaze3d/pipeline/RenderPipeline$Builder` |
| 我们的瞄准逻辑 | `com/tacz/guns/client/event/FirstPersonRenderGunEvent` 第 144~190 行 |
| 瞄具模型节点 | `BedrockAttachmentModel`（`scope_view` / `ocular` / `division` / `ocular_ring`） |

---

## 八、第 19 轮更新：P1 已通过 + P2 路径修正（**重要**）

### 8.1 P1 结果：PASS

用户实机验证，三条判据全部满足：

| 判据 | 结果 |
|---|---|
| 主画面无差别 | ✅ |
| 帧率无差别 | ✅ |
| 日志出现成功行、无失败行 | ✅ |

**核心假设成立**：`outputColorTextureOverride` 在世界渲染阶段可用，
且借用后能干净还原、不污染其他渲染层。地基可用。

### 8.2 但 P2 的原设想被推翻了（反编译发现）

原方案设想「重定向输出 → 再调一次 `levelRenderer.render(...)` → 世界就画进离屏纹理」。
**这条路走不通。** 反编译逐级确认：

**(a) 只有「立即绘制」路径尊重 override** ——
`PreparedRenderType#drawFromBuffer`（第 32~37 行）：

```java
GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
   ? RenderSystem.outputColorTextureOverride
   : renderTarget.getColorTextureView();
```

**(b) 地形（世界主体）完全不看 override** ——
`ChunkSectionsToRender#renderGroup`（第 42~50 行）直接用：

```java
RenderTarget renderTarget = group.outputTarget();
RenderPass renderPass = ...createRenderPass(
      () -> "Section layers for " + group.label(),
      renderTarget.getColorTextureView(),   // <<< 硬取，不看 override
      ...);
```

而 `ChunkSectionLayerGroup#outputTarget()` 返回的是
`minecraft.gameRenderer.mainRenderTarget()`（或 translucentTarget）。

**(c) `LevelRenderer#render` 内部也硬编码主 RT** ——
clear pass 直接写 `this.gameRenderer.mainRenderTarget().getColorTexture()`。

**结论**：`outputColorTextureOverride` 只能覆盖 immediate-draw（实体、粒子、手部等），
**覆盖不了地形**。靠它做整世界镜内渲染 = 镜内只有实体没有地形。

### 8.3 P2 修正后的可行路径

关键发现：`RenderTarget#getColorTextureView()` 是**普通可覆写方法**，
且 `TextureTarget extends RenderTarget` **可直接实例化**：

```java
public class TextureTarget extends RenderTarget {
   public TextureTarget(@Nullable String label, int width, int height,
                        boolean useDepth, GpuFormat format) { ... }
}
```

于是有两条路，**推荐 A**：

**A. 临时替换 `gameRenderer.mainRenderTarget`（推荐）**
- 用 Mixin/Accessor 把 `GameRenderer.mainRenderTarget` 字段临时指向我们的 `TextureTarget`
- 调一次 `renderLevel`/`levelRenderer.render`
- 还原字段
- 优点：地形、实体、粒子**全部**自动画进离屏 RT，无需逐路径改
- 风险：字段被多处缓存引用；必须严格 try/finally 还原；要处理尺寸不一致

> ⚠️ **实施前必读（第 20 轮字节码核查补充）**：
> `GameRenderer.mainRenderTarget` 是 **`private final`** 字段
> （`F private final mainRenderTarget Lcom/mojang/blaze3d/pipeline/RenderTarget;`）。
> 直接用 `@Accessor` 写 final 字段需要额外加 `@Mutable`，且 final 字段可能被 JIT
> 常量折叠、也容易被别处缓存住引用。
>
> **更安全的等价做法**：不动字段，改注入 **`GameRenderer.mainRenderTarget()` 方法**
> （public，已确认存在），用 `@Inject(at=@At("HEAD"), cancellable=true)` +
> `cir.setReturnValue(ourTarget)`，仅在 `isRenderingScope` 守卫为真时生效。
> 这样所有**通过方法**取 target 的调用方（含 `ChunkSectionLayerGroup#outputTarget()`，
> 它内部正是走 `minecraft.gameRenderer.mainRenderTarget()`）都会自动拿到离屏 RT，
> 而不需要碰 final 字段。
>
> `TextureTarget` 构造函数已确认为 public：`(String label, int w, int h, boolean useDepth, GpuFormat)`。

**B. Mixin 注入 `ChunkSectionLayerGroup#outputTarget()`**
- 镜内渲染期间让它返回我们的 RT
- 优点：改动面小
- 缺点：还要同时处理 `LevelRenderer#render` 里硬编码的 clear pass 和其他 target

### 8.4 对性能预算的影响（需重新评估）

原估「多渲一遍世界」，现在确认**必须走完整 `renderLevel`**（含地形区块重新提交），
代价比预想更高。因此第 5 节的性能对策要加码：

- **必须**默认关闭，由玩家显式开启
- **必须**隔帧更新（`textureIsReadyToBlit` 机制），例如每 2~3 帧才更新一次镜内画面
- 离屏分辨率进一步压低（256²~512²）
- 仅在 `aimingProgress > 0.9` 时启用
- 考虑降低镜内渲染距离（独立的 renderDistance）

### 8.5 建议

**P2 拆成 P2a / P2b：**

- **P2a**：用方案 A 把世界渲染进离屏 RT，但**先不管镜片**，
  改为把离屏纹理**直接平铺到屏幕角落**（调试 HUD 形式）。
  这样能一眼看出「镜内画面到底渲染出来没有、内容对不对、性能掉多少」，
  且完全不碰瞄具渲染，出问题容易回退。
- **P2b**：确认 P2a 的画面和性能可接受后，再把纹理贴到镜片上（配合蒙版）。

这样每一步都可独立验收，避免「一次改太多、坏了不知道是哪块」。
