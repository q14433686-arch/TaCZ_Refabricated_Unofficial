# Step 2 失败复盘：VK_ERROR_DEVICE_LOST（r51）

**状态**：已回滚到 Step 1（用户验证通过的状态）。
**结论先行**：Step 2 的失败**不是参数没调好**，而是撞上了 26.2 延迟渲染架构的一条硬约束。
继续在这条路上试错代价过高，建议改走 §5 的替代方案。

---

## 1. 崩溃现象

```
com.mojang.blaze3d.GpuDeviceLossException: VK_ERROR_DEVICE_LOST: Failed to acquire image
Queue 0x2b798f90b00
 TOP_OF_PIPE    = END_RENDER_PASS Immediate draw with minecraft:pipeline/entity_cutout
 BOTTOM_OF_PIPE = END_RENDER_PASS Immediate draw with minecraft:pipeline/entity_cutout
    at VulkanGpuSurface.acquireNextTexture
    at Minecraft.renderFrame
```

这比前几次严重：**不是 Java 层异常，而是 Vulkan 驱动层面的设备丢失**。
GPU 执行队列出错后整个设备失效，可能污染驱动状态 —— 所以第一时间回滚，
而不是让用户反复触发去试参数。

---

## 2. 排除掉的可能

### 2.1 不是「掩码纹理不可采样」

一度怀疑 `TextureTarget` 建出来的纹理缺少 `USAGE_TEXTURE_BINDING`。
反汇编 `RenderTarget#createBuffers` 确认**不是**：

```java
// 偏移 81 / 116 都是 bipush 15
createTexture(labelSupplier, /* usage = */ 15, format, w, h, 1, 1);
```

`15 = 1|2|4|8 = COPY_DST | COPY_SRC | TEXTURE_BINDING | RENDER_ATTACHMENT`
（各常量值由 `GpuTexture` 的 ConstantValue 属性读出）。
所以 target 的颜色纹理**本身就是可采样、可作附件**的，创建方式没问题。

### 2.2 也不是 shader 编译失败

那类问题会报 `Couldn't compile pipeline ...`（r46 就是），并抛
`IllegalStateException: Pipeline is not valid`，**不会**导致设备丢失。
日志里没有编译错误。

---

## 3. 真正的问题：跨 OutputTarget 的绘制被交错了

崩溃信息里最关键的一行：

```
TOP_OF_PIPE = END_RENDER_PASS Immediate draw with minecraft:pipeline/entity_cutout
```

出错时 GPU 正在处理 **`entity_cutout`**（主画面的枪械渲染管线），
而不是我们的 `tacz:pipeline/scope_mask`。这说明问题不在掩码 pass 自身，
而在**它与主画面 pass 的交错方式**。

回看 `PreparedRenderType#drawFromBuffer` 的结构：

```java
RenderTarget rt = this.outputTarget.getRenderTarget();   // ← 按 RenderType 各自的 target
try (RenderPass pass = encoder.createRenderPass(..., rt.getColorTextureView(), ...)) {
    pass.setPipeline(this.pipeline);
    ...
    pass.drawIndexed(...);
}
```

`SubmitNodeCollector` 会把同帧提交按 RenderType 分组批量执行。
我们插了一个 outputTarget 不同的 RenderType 进去，于是同一批绘制里出现了
**「主 target → 掩码 target → 主 target」的反复切换**。

每次切换都要 `createRenderPass` / `close`，而 `close` 会触发
`END_RENDER_PASS`。在 Vulkan 后端下，这种高频跨 attachment 的
pass 切换需要正确的图像布局转换（layout transition）与同步屏障 ——
引擎的批量执行器**并未为「同帧内多个 RenderTarget 交替」这种用法做保证**。

> r48 调查时我确实注意到了这个风险，当时写的是：
> 「⚠️ 仍需实机验证：同一帧内跨 OutputTarget 的绘制顺序是否严格遵循 order。
> 若不遵循，掩码可能滞后一帧。」
>
> **实际后果比预想的严重得多** —— 不是「滞后一帧」，而是直接设备丢失。
> 我低估了 Vulkan 对 pass 切换的严格程度。

---

## 4. 为什么 vanilla 自己没事

vanilla 也有多个 `OutputTarget`（`OUTLINE_TARGET` / `WEATHER_TARGET` /
`ITEM_ENTITY_TARGET`），但它们的使用方式与我们截然不同：

| | vanilla | 我们的 Step 2 |
|---|---|---|
| 切换频率 | 每帧**整个阶段**切一次（如所有发光实体轮廓一起画） | 每个瞄具几何**穿插**在主画面绘制中间 |
| 时机 | 在 `FeatureRenderDispatcher` 的**阶段边界** | 在 solid 阶段**内部** |
| 数量 | 一帧个位数 | 与瞄具数量成正比 |

vanilla 的模式是「**成批地、在阶段边界切换**」；
我们做成了「**零散地、在阶段内部切换**」。后者正是 Vulkan 最不喜欢的用法。

---

## 5. 替代方案

### 5.1 方案 B（推荐）：只让 ocular 不输出颜色

这是我在 r47 就提出、被暂时搁置的退路，现在看它的性价比明显更高：

- **做什么**：给 `ocular` 一个 `colorMask` 全关（或直接 discard 全部片元）的
  RenderType，让它像上游一样「存在但不可见」。
- **能解决**：你实测反馈的「有镜片在遮挡」—— 那块挡视线的正是 `ocular` 几何本身。
  上游从来就没画过它（`colorMask(false,false,false,false)`）。
- **代价**：镜内仍能看到镜筒内壁（无区域裁剪）。
- **成本**：一个只输出 `discard` 的 fsh + 一个 RenderType，**几十行**。
- **风险**：极低。不涉及离屏 target、不涉及 pass 切换，就是普通的一次绘制。

### 5.2 方案 C：阶段边界批量渲染掩码

若仍想要完整的区域裁剪，正确做法是**遵循 vanilla 的模式**：
在 `FeatureRenderDispatcher` 的阶段边界，用一个 mixin 把**当帧所有**瞄具的
ocular 一次性画进掩码，而不是零散穿插。

但这需要：
- mixin 注入 `renderAllFeatures` 的阶段之间；
- 自行收集当帧所有瞄具几何（跨 `BedrockAttachmentModel` 实例）；
- 自建顶点缓冲（不能再走 collector，因为要脱离它的批次）。

即回到 r48 §3.2a 那条被放弃的路线，工作量数倍于 Step 2，
且仍有 Iris/Sodium 兼容的未知数。

### 5.3 我的建议

**先做方案 B**，把「镜片挡视线」这个实际影响体验的问题解决掉；
方案 C 作为长期选项，等有明确需求再评估。

理由：方案 B 用极低成本拿到大部分收益；而完整区域裁剪的边际收益
（镜内不见镜筒内壁）与其风险、工作量不成比例 ——
何况我们已经在这条路上失败了三次（r29 圆心、r46 固定圆、r51 设备丢失）。

---

## 6. 给后来者的教训

1. **`OutputTarget` 是 public 构造 ≠ 可以随意用。**
   API 可访问性不代表用法自由，引擎对「怎么用」有隐含假设。
2. **在 Vulkan 后端下，跨 RenderTarget 的 pass 切换必须成批、在阶段边界做。**
   零散穿插会触发驱动层错误，且报错位置（`entity_cutout`）与真凶
   （我们的 mask pass）不在同一处，极易误判。
3. **风险评估里标注「需实机验证」的项，要按最坏后果准备。**
   r48 我预估的最坏情况是「掩码滞后一帧」，实际是设备丢失 ——
   两者的止损成本差了一个量级。
