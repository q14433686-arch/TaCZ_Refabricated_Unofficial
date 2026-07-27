# 上游瞄具渲染机制 · 完整精读（推翻我此前的两套自创方案）

**日期**：2026-07-26（第 25 轮）
**背景**：用户三次指出「做法不对」，已 `revert` 全部 r22~r24 代码改动回到 r21。
本文是动手前的**完整精读**，不含任何代码改动。

---

## 0. 先认错：我此前错在哪

| 我做的 | 上游实际 | 性质 |
|---|---|---|
| 按「cube 到光轴距离」逐块判定内壁/外壁并摘掉 | **完全没有这种东西** | 我自己发明的几何近似 |
| 用矩阵旋转推算夹角、做「准直光学」平移补偿 | **完全没有这种东西**（全仓 grep `collimat/parallax/billboard` 零命中） | 我自己发明的光学模拟 |

两套方案都是我在**没读透上游**的情况下臆造的。用户的判断是对的。

---

## 1. 「剔除」的真相：不是删几何，是把节点**移出主渲染列表**

### 1.1 关键代码：`renderTempPart` 的 visible 副作用

```java
private void renderTempPart(PoseStack poseStack, ..., List<BedrockPart> path) {
    poseStack.pushPose();
    for (int i = 0; i < path.size() - 1; ++i) {
        path.get(i).translateAndRotateAndScale(poseStack);   // 只套父链
    }
    BedrockPart part = path.get(path.size() - 1);
    part.visible = true;              // ← 临时打开
    ...
    part.render(poseStack, transformType, vertexConsumer, light, overlay);
    ...
    part.visible = false;             // ★★★ 画完【永久关闭】★★★
    poseStack.popPose();
}
```

**最后那行 `part.visible = false` 是整个机制的枢纽。**

### 1.2 于是 `super.render()` 会自动跳过它们

`BedrockModel#render` 的主循环：

```java
for (BedrockPart model : shouldRender) {
    model.render(matrixStack, transformType, builder, light, overlay, ...);
}
```

而 `BedrockPart#render` 第一件事就是 `if (this.visible)`。

**串起来**：

```
renderScope 执行时：
  ocular_ring → renderTempPart → 画完 visible=false
  ocular      → renderTempPart → 画完 visible=false
  scope_body  → renderTempPart → 画完 visible=false
  division    → 构造函数已 setHidden(true)
  ↓
  super.render() 遍历 shouldRender
  → 这四类节点 visible 全为 false → 主循环【自动跳过】
```

### 1.3 结论

**上游根本不需要「剔除内壁几何」。** 它做的是：

> 把 `ocular` / `ocular_ring` / `scope_body` / `division` 这几个节点
> **从常规渲染流程里摘出来**，改为在 stencil 控制下**按精确顺序手动重画**。

其中「镜身只画在目镜圆之外」是靠**屏幕空间的模板测试**：

```java
RenderSystem.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);   // 只在模板==0 处通过
renderTempPart(..., scopeBodyPath);
```

**与几何位置无关**，与 cube 到光轴的距离**更无关**。
我那套「按半径分内外壁」纯属无中生有 —— 难怪 33 个模型里要靠一堆启发式和安全阀去兜。

---

## 2. 「全息」的真相：没有准直光学，只有 `disableDepthTest`

### 2.1 上游全仓零命中

```
grep -rn "collimat|parallax|billboard" upstream/src/main/java  →  0 结果
```

### 2.2 唯一的「特殊处理」是关深度测试

`renderDivisionOnly`（低倍镜 / 红点走这条）：

```java
private void renderDivisionOnly(...) {
    if (!divisionNodePaths.isEmpty()) {
        RenderSystem.disableDepthTest();          // ★ 关深度
        for (int i = 0; i < divisionNodePaths.size(); i++) {
            RenderSystem.stencilFunc(GL_EQUAL, i + 1, 0xFF);   // 只在目镜圆内
            renderTempPart(..., divisionNodePaths.get(i));
        }
        RenderSystem.enableDepthTest();
    }
}
```

### 2.3 那「浮动感」从哪来

三个要素叠加，**没有一个是光学模拟**：

| 要素 | 实现 | 效果 |
|---|---|---|
| **准星恒在最上** | `disableDepthTest()` | 不被镜身/镜片遮挡，像浮在玻璃上 |
| **只在目镜圆内可见** | `stencilFunc(EQUAL, i+1)` | 超出目镜边界自动被裁掉 |
| **发光** | 节点名 `_illuminated` → `LightTexture.pack(15,15)` | 满亮度红点 |

**准星几何本身是刚性焊在枪体上的**，上游从未对它做任何位置补偿。

### 2.4 那用户观察到的「随视角移动而非随枪械移动」是什么

这是**真实光学在游戏里的天然副产品**，不是代码实现的：

- TACZ 开镜时摄像机会对齐到 `scope_view` 节点（`FirstPersonRenderGunEvent` 的 `aimingNodePath`）；
- 准星几何位于镜筒**前端**、离目镜有相当距离；
- 于是**视角微动 → 摄像机相对准星平面的角度变化 → 准星在目镜圆内的投影位置移动**；
- 而 `stencilFunc` 保证它一旦偏出目镜圆就被裁掉。

**换言之：视差感是「准星离目镜有距离」+ 「圆形裁剪」自然产生的透视效果**，
根本不需要我那套矩阵分解和夹角补偿。我把一个透视现象误当成需要主动模拟的光学系统。

---

## 2.5 【决定性发现】26.2 有 Vulkan 后端 —— 原生 GL stencil 这条路彻底堵死

我此前只查了 `RenderSystem` 没有 `stencilFunc`，就下结论「26.2 移除了 stencil」。
**这个结论对，但理由不完整**，而且漏掉了一个更关键的事实。

### 2.5.1 上游用的其实是**裸 LWJGL GL 调用**，不是 RenderSystem API

`RenderHelper#enableItemEntityStencilTest`（上游）：

```java
GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, ...);
GlStateManager._texImage2D(..., GL30.GL_DEPTH24_STENCIL8, ...);
GlStateManager._glFramebufferTexture2D(..., GL30.GL_DEPTH_STENCIL_ATTACHMENT, ...);
GL11.glEnable(GL11.GL_STENCIL_TEST);
```

也就是说，即便 `RenderSystem` 不暴露 stencil，
**理论上仍可以绕过它直接调 `GL11.glEnable(GL_STENCIL_TEST)`** —— 我之前没想到这一层。

### 2.5.2 但两个事实把这条路堵死了

**事实一：`GlStateManager` 在 26.2 已完全不存在。**

```
grepm "GlStateManager"  →  0 命中
```
上游那套 `GlStateManager._texImage2D` / `_glFramebufferTexture2D` 无从谈起。

**事实二（决定性）：26.2 自带完整 Vulkan 后端。**

```
com/mojang/blaze3d/opengl/   GlBackend, GlCommandEncoder, GlConst, ...
com/mojang/blaze3d/vulkan/   VulkanBackend, VulkanDevice, VulkanRenderPass,
                             VulkanRenderPipeline, VulkanCommandEncoder, ...  (40+ 类)
```

渲染后端已被抽象成 `GpuDevice` / `CommandEncoder` / `RenderPass`，
`GlBackend` 和 `VulkanBackend` 是**两个平行实现**。

**在 Vulkan 后端下，任何 `GL11.glEnable(...)` 都是无效甚至危险的**
（没有 GL 上下文）。而玩家用哪个后端不由我们决定。

**事实三：抽象层对 stencil 零支持。**

```
VulkanConst          → 无任何 stencil 映射
VulkanRenderPipeline → 无任何 stencil 字段
DepthStencilState    → 只有 depthTest/writeDepth/depthBias*（名字带 Stencil 但无 stencil 功能）
```

即：**不是「MC 没暴露 stencil」，而是整个新渲染抽象层就没有 stencil 这个概念。**

### 2.5.3 结论

| 路径 | 可行性 |
|---|---|
| `RenderSystem.stencilFunc` | ❌ 不存在 |
| 裸 `GL11.glEnable(GL_STENCIL_TEST)` | ❌ Vulkan 后端下失效；`GlStateManager` 也已删除 |
| 自定义 RenderPipeline 带 stencil | ❌ `RenderPipeline.Builder` 无此选项，Vulkan 侧也没实现 |
| **片元着色器里按距离 `discard`** | ✅ **唯一可行**——不依赖 stencil，两个后端都支持 |

**这就是为什么必须走「蒙版着色器」而不是「复刻 stencil」。**
上游那套 stencil 流程在 26.2 **原理上不可移植**，只能用等价效果替代。

---

## 3. 26.2 移植的真正难点（重新定位）

上游三个要素在 26.2 的可用性：

| 要素 | 上游 API | 26.2 状态 |
|---|---|---|
| 从主列表摘除节点 | `part.visible = false` | ✅ **完全可用**（纯逻辑，与渲染 API 无关） |
| 手动重画单个节点 | `renderTempPart` → `MultiBufferSource` | ❌ 已移除，需改用 `submitCustomGeometry` + 快照 |
| 圆内/圆外裁剪 | `stencilFunc` | ❌ 已移除，且**裸 GL 也不可行**（Vulkan 后端 + `GlStateManager` 已删，见 §2.5）。**唯一硬缺口** |
| 准星恒在最上 | `disableDepthTest()` | ⚠️ 需换成 `RenderTypes.eyes()` 之类无深度写入的 RenderType |
| 发光 | `illuminated` → 满亮度 | ✅ 已完整继承 |

**所以真正缺的只有「圆形裁剪」一件事**，而且它同时决定：
- 镜身画在圆外（`EQUAL 0`）
- 准星画在圆内（`EQUAL i+1`）
- 目镜遮罩画在圆环（`INVERT` 后的 `EQUAL ~(i+1)`）

**三者是同一个圆的不同区域**。这就是为什么零敲碎打地补「掏空」「剔除」「准星」都不对 ——
它们本来是一个整体，缺了圆就全都不成立。

---

## 4. 正确的实施路线（建议，未动手）

### 阶段 1：先把节点摘除机制原样搬过来（零风险）

在 26.2 的 `submit()` 路径里复现 `visible=false` 的语义：
让 `ocular` / `ocular_ring` / `scope_body` / `division` 不进入 `super.submit()`，
改由我们自己按顺序提交。**这一步不需要 stencil**，纯逻辑，可独立验证。

### 阶段 2：补「圆形裁剪」——唯一的硬缺口

26.2 下的候选（按推荐度）：

| 方案 | 说明 | 风险 |
|---|---|---|
| **自定义片元着色器按距离 `discard`** | 等价 stencil 且边缘可羽化；**不依赖 stencil，GL/Vulkan 双后端都支持** | 中，但这是**唯一原理上可行**的路（见 §2.5） |
| `enableScissorForRenderTypeDraws` | 矩形裁剪，边缘是方的 | 低，但只能近似 |
| ~~复刻上游 stencil~~ | ~~裸 GL 调用~~ | ❌ **原理上不可行**（Vulkan 后端 + `GlStateManager` 已删） |
| 不裁剪，仅靠 `visible` 开关 | 就是我 r21~r24 做的，已被证明不对 | — |

### 阶段 3：准星用无深度 RenderType 提交

`RenderTypes.eyes(...)` 或 `entityTranslucentEmissive(...)` 替代 `disableDepthTest()`。

**准星几何位置保持原样，不做任何补偿** —— 视差感会自然出现（见 §2.4）。

---

## 5. 给用户的说明

1. **代码已 revert 到 r21**，即只保留「开镜掏空 ocular」的最小修复，
   不含任何我自创的剔除与准直逻辑。
2. 本文是精读结论，**未动任何代码**。
3. 下一步需要你拍板：是否按 §4 的三阶段走？
   其中阶段 2（自定义蒙版管线）是唯一真正的技术攻坚，
   我建议先做阶段 1（零风险、可独立验证），确认摘除机制正确后再攻圆形裁剪。

---

## 附：证据索引

| 结论 | 证据位置 |
|---|---|
| `renderTempPart` 结尾 `visible=false` | 上游 `BedrockAttachmentModel.java:204,211` |
| 主循环按 visible 跳过 | 上游 `BedrockModel.java:360` + `BedrockPart.java:67` |
| 镜身 `stencilFunc(EQUAL,0)` | 上游 `renderScope` |
| 准星 `disableDepthTest` + `stencilFunc(EQUAL,i+1)` | 上游 `renderDivisionOnly` |
| 上游无准直/视差代码 | 全仓 grep `collimat/parallax/billboard` = 0 |
| 发光靠 `_illuminated` → `LightTexture.pack(15,15)` | 上游 `BedrockPart.java:63` |
