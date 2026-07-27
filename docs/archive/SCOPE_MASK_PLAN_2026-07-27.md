# 方案 A：用目镜投影做遮罩 —— 可行性调查与设计（r48）

**目的**：复刻上游「用 `ocular` 几何自身的屏幕投影裁剪镜身」，而非 r46 那个错误的固定圆。
**状态**：**可行性已确认，尚未动手实现**。本文是动手前的技术底稿与风险交底。

---

## 1. 先厘清上游到底怎么做的（r47 已确认，此处固化）

```java
// renderScope 关键三步
renderOcularStencil(...);                     // ① 用 ocular 几何写模板
    colorMask(false,false,false,false);       //    只写模板、不写颜色
    stencilOp(KEEP, KEEP, GL_REPLACE);
    stencilFunc(GL_GREATER, i+1);
    // → 模板 == i+1 的区域 = 目镜几何【在屏幕上的实际投影形状】

scope_body: stencilFunc(GL_EQUAL, 0);         // ② 只在目镜【没盖到】处画镜身

renderOcularAndDivision(...);                 // ③ 圆形 INVERT，在目镜区域内再切
```

**裁剪区域是「目镜模型的屏幕投影」，不是几何圆。**
`ocular` 跟着枪走，所以投影天然随瞄准镜移动、天然只覆盖镜筒内那块、天然不碰枪体。
第 ③ 步的圆作用在**已写好的模板之上**，是二次细分，不是裁剪区域本身。

---

## 2. 26.2 能否复刻：**能**，逐项已验证

| 能力 | 26.2 API | 状态 |
|---|---|---|
| 离屏渲染目标 | `TextureTarget(String,int,int,boolean,GpuFormat)` | ✅ public 构造 |
| 指定目标开 pass | `CommandEncoder#createRenderPass(Supplier, GpuTextureView, Optional, ...)` | ✅ |
| 清空 | `clearColorTexture` / `clearColorAndDepthTextures` | ✅ |
| 绑定纹理给 shader | `RenderPass#bindTexture(String, GpuTextureView, GpuSampler)` | ✅ |
| 自定义管线 + shader | `RenderPipeline.builder(...)` + `assets/tacz/shaders/` | ✅（r46 已跑通编译） |

### 2.1 最关键的约束：pass 不能嵌套

`CommandEncoder#createRenderPass` 偏移 0-16 字节码：

```java
if (this.isInRenderPass) {
    throw new IllegalStateException("Close the existing render pass before creating a new one!");
}
```

**所以不能在绘制回调里顺手开一个离屏 pass。**

### 2.2 但存在可用的时机空隙

`PreparedRenderType#drawFromBuffer` 的结构是：

```
createRenderPass(...) → setPipeline → bindTexture → drawIndexed → close()
```

**每次绘制自己开一个 pass、画完立刻关。**
而 `FeatureRenderDispatcher#renderAllFeatures` 是：

```java
PreparedFrame f = prepareFrame(storage);   // 只做准备，不绘制
f.executeSolid();                          // ← 各阶段之间不在 pass 内
f.executeTranslucent();
f.executeTranslucentAfterTerrain();
f.executeAlwaysOnTop();
f.close();
```

**结论**：在 `executeSolid()` 之前插入我们自己的离屏 pass 是**合法**的。

---

## 3. 实现设计

### 3.1 三趟渲染

```
[Pass 1] 目镜掩码（离屏）
    目标：TextureTarget(1×屏幕尺寸, R8 或 RGBA8)
    清空为 0
    只画 ocular 几何，fragment 输出 1.0
    → 得到一张「哪些像素属于目镜」的掩码图

[Pass 2] 镜身（主目标，采样掩码）
    正常画 scope_body 等几何
    fsh：mask = texture(MaskSampler, gl_FragCoord.xy / screenSize).r;
         if (mask > 0.5) discard;        // 目镜盖到的地方不画镜身
                                          // 等价于上游 stencilFunc(EQUAL, 0)

[Pass 3] 分划（主目标）
    与上游 renderDivisionOnly 一致
```

### 3.2 与现有架构的接口

难点在于**我们的几何是通过 `SubmitNodeCollector` 延迟提交的**，
而 Pass 1 需要在提交之外单独绘制 `ocular`。两条路：

- **3.2a** 用 `StagedVertexBuffer` 自建顶点缓冲，手动走
  `appendDraw → getVertexBuilder → upload → getExecuteInfo → drawIndexed`。
  可控，但要自己管理缓冲生命周期。
- **3.2b** 给 `ocular` 单独建一个输出到掩码 target 的 `RenderType`，
  仍走 collector 提交，靠 `OutputTarget` 把它导向离屏目标。

### ✅ 3.2b 已验证可行，且大幅简化了方案

```java
public class OutputTarget {
    // public 构造！
    public OutputTarget(String name, Supplier<RenderTarget> renderTargetSupplier)
    public RenderTarget getRenderTarget()
    // vanilla 自己就有四个：MAIN_TARGET / OUTLINE_TARGET / WEATHER_TARGET / ITEM_ENTITY_TARGET
}
```

而 `RenderSetup.RenderSetupBuilder` 有 `setOutputTarget(OutputTarget)`（r46 已确认）。
且 `PreparedRenderType#drawFromBuffer` 的第一行就是
`this.outputTarget.getRenderTarget()` —— 它**按 RenderType 各自的 outputTarget
决定往哪画**，并为此单独 `createRenderPass`。

**这意味着：**
- **不需要**自建顶点缓冲（放弃 3.2a）；
- **不需要** mixin 注入时机 —— 引擎会在绘制该 RenderType 时自动开对应的 pass；
- **不需要**担心 pass 嵌套 —— 每个 RenderType 的 pass 本就是独立开关的；
- 只需：建一个 `OutputTarget` 指向我们的 `TextureTarget`，
  给 `ocular` 用一个绑定它的 `RenderType`，照常 `submitCustomGeometry` 即可。

唯一需要保证的是**绘制顺序**：掩码必须先于镜身完成。
`SubmitNodeCollector#order(int)` 提供了排序能力（r22 已用过），
可据此让掩码 RenderType 排在镜身之前。

> ⚠️ 仍需实机验证的点：同一帧内跨 OutputTarget 的绘制顺序是否严格遵循 order。
> 若不遵循，掩码可能滞后一帧（表现为快速转动视角时裁剪区域"追不上"）。
> 这正是 Step 2 要验证的内容之一。

---

## 4. 风险与代价（必须先说清楚）

| 项 | 评估 |
|---|---|
| **工作量** | 大于 r46，但因 3.2b 成立而**显著低于初估**：1 个离屏 target 管理类、1 套掩码 shader、1 套采样掩码的镜身 shader。**不需要** mixin，**不需要**手写顶点缓冲 |
| **每帧开销** | 多一趟全屏 pass + 一张全屏纹理。开镜时才启用可以缓解，但仍是实打实的成本 |
| **失败模式** | 掩码 target 尺寸/缩放对不上 → 偏移；pass 顺序错 → 掩码是上一帧的；生命周期没管好 → 显存泄漏 |
| **Iris/Sodium 兼容** | 两者都重写渲染管线。我们插入的离屏 pass 很可能与之冲突，**未评估** |
| **调试成本** | 沙盒无 GPU、无法编译，全部只能靠你实机验证。掩码类 bug 往往表现为「全黑」或「全没」，不易区分是哪一环坏 |

---

## 5. 建议的推进方式（分步可验证）

不要一次写完，按下面顺序，每步都能单独看出对错：

1. **Step 1：离屏 target 能否建起来**
   建 `TextureTarget`、每帧清成纯红、**把它 blit 到屏幕角落**。
   看得到红方块 = 离屏渲染链路通。失败也不影响正常渲染。
2. **Step 2：能否把 ocular 画进掩码**
   Pass 1 画 ocular，仍 blit 到角落。
   角落里出现一个跟着枪动的白色形状 = 掩码正确。
   **这一步直接验证了「投影形状」这个核心假设。**
3. **Step 3：镜身采样掩码**
   接上 discard，此时才可能看到最终效果。
4. **Step 4：分划/准星** 按上游顺序补齐。

**Step 2 是关键分水岭** —— 它花的成本不高，但能一次性证明或证伪整个方案。
如果 Step 2 的白色形状不跟枪动、或形状不对，就说明还有更深的误解，
此时止损远比继续往下写划算。

---

## 6. 一个诚实的替代提示

如果 Step 1/2 卡住，或你觉得代价不值，随时可以退到：
**只让 `ocular` 不输出颜色**（上游 `colorMask(false)` 的等价物）。
那是几行改动，能立刻消除「镜片挡视线」，代价是镜内仍见镜筒内壁。
本方案与该退路**不冲突**，可以随时切换。
