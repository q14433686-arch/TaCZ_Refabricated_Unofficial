# 上游瞄准镜渲染机制：逐行核实（r43）

**日期**：2026-07-27
**方法**：逐行精读 `Sh1roCu/TACZ-Refabricated` 1.21.1 分支的
`BedrockAttachmentModel`，并对默认枪包的几何数据做实测比对。
**目的**：验证用户提出的假设 ——「上游是在镜内做了一个矩形遮罩区域，
里面是一张大贴图被瞄准镜模型裁剪而成，镜内不渲染除世界和该蒙版之外的任何东西」。

---

## 0. 结论速览

| 用户的判断 | 核实结果 |
|---|---|
| 「最偷懒的方式」 | ✅ **对**。没有 PIP、没有二次渲染世界、没有光学模拟 |
| 「镜内是一块遮罩区域」 | ✅ **对**，但形状是**圆形**不是矩形 |
| 「一张大贴图被裁剪」 | ⚠️ **半对**。被裁剪的确实是一张大平面贴图（`division`），<br>但裁剪它的**不是瞄准镜模型**，而是一个**代码里现算的圆** |
| 「裁剪由瞄准镜模型决定」 | ❌ **不对**。模型只决定圆心**位置**，半径是写死的 `80 × modifier × 开镜进度` |
| 「镜内不渲染其他任何效果」 | ✅ **完全正确**，而且是靠 stencil 强制实现的 |
| 「低倍镜/组合镜低倍部分与我们现在一致，只是准星刻画不同」 | ✅ **基本正确**，见 §4 |

**一句话**：上游确实偷懒，但偷懒的方式是
**「用一个屏幕空间的圆做 stencil 模板，把一张大贴图切成圆形」**，
而不是「用模型去裁贴图」。

---

## 1. 那个「遮罩」到底是什么 —— 是圆，不是矩形

核心在 `renderOcularAndDivision`（上游 L246-297）：

```java
RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INVERT);
RenderSystem.colorMask(false, false, false, false);   // 只写模板，不写颜色
RenderSystem.depthMask(false);

float rad = 80 * scopeViewRadiusModifier;
rad *= getClientAimingProgress(...);                  // 不开镜 rad=0，开镜张开到 80

Vector3f ocularCenter = getBedrockPartCenter(matrixStack, ocularNodePaths.get(i));
float centerX = ocularCenter.x() * 16 * 90;
float centerY = ocularCenter.y() * 16 * 90;

BufferBuilder builder = Tesselator.getInstance()
        .begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
builder.addVertex(matrixStack.last(), centerX, centerY, -90.0F)...;
for (int j = 0; j <= 90; j++) {                       // 90 段扇形 = 一个圆
    float angle = j * (PI * 2) / 90.0F;
    builder.addVertex(matrixStack.last(),
            centerX + cos(angle) * rad,
            centerY + sin(angle) * rad, -90.0F)...;
}
```

**要点：**

- `TRIANGLE_FAN` + 90 段 = **一个圆**，不是矩形；
- `colorMask(false,...)` → 这个圆**根本不画到屏幕上**，它只是往模板缓冲写值；
- `stencilOp(..., GL_INVERT)` → 圆覆盖处的模板值被**取反**；
- 半径 `rad` 是**硬编码 80**（注释原文：「80是一个随便找的大小合适的数值」）
  乘以枪包可调的 `scopeViewRadiusModifier`，再乘开镜进度；
- 只有**圆心**来自模型：`getBedrockPartCenter(ocularNodePaths[i])`。

> 所以「裁剪的部分由瞄准镜模型决定」这句要修正为：
> **模型只决定圆心在哪，圆多大跟模型无关。**

---

## 2. 被裁剪的「大贴图」确实存在 —— 就是 `division`

实测默认枪包（数值为 Bedrock 模型单位）：

| 模型 | `division` cube 尺寸 | 说明 |
|---|---|---|
| `scope_acog_ta31` | `52.4 × 52.4`（z=-101）<br>另一块 `66.9 × 125.1` | 一整张 128×128 UV 的大平面 |
| `scope_98k` | `11.7 × 26.8`、`11.8 × 26.8`（z=-17.0）| 共 14 个 cube 拼成的分划板 |
| `sight_exp3` | `division` cubes = **0** | 低倍镜没有蚀刻分划 |

`scope_acog_ta31` 的第一块 cube UV 是 `uv_size: [128, 128]` —— 确实是**一整张大贴图**。
它位于 z=-101（物镜前方极远处），尺寸远大于镜筒口径。
**如果不裁剪，它会糊满整个屏幕** —— 这正是我们第 9 轮踩过的坑
（当时画出来是一大块黑方块，第 10 轮撤销）。

所以用户说的「一张大贴图被裁剪成镜内画面」是**成立的**，
只是裁剪工具是上面那个圆形 stencil。

---

## 3. 「镜内不渲染其他任何效果」—— 完全正确，且是强制的

`renderScope` 的完整顺序（上游 L358-388）：

```
1. enableItemEntityStencilTest() + clearStencil(0)
2. ocular_ring        : stencilFunc(ALWAYS, 0)     → 正常画外环
3. renderOcularStencil: colorMask(false) + stencilOp(REPLACE)
                        → 目镜只写模板值 i+1，【不画颜色】
4. scope_body         : stencilFunc(EQUAL, 0)
                        → 只在【模板==0】即目镜圆【之外】画镜身
5. renderOcularAndDivision:
     - 圆形 INVERT     → 圆内模板 i+1 → ~(i+1)
     - stencilFunc(EQUAL, i+1)  画 ocular  → 圆【外】的目镜=黑色遮罩
     - stencilFunc(EQUAL, ~(i+1)) 画 division → 圆【内】才画分划
6. disableItemEntityStencilTest()
7. super.render(...)   → 其余枪械部件
```

**第 4 步是关键**：`scope_body`（整根镜筒，`scope_98k` 有 131 个 cube、
z 从 -13.75 延伸到 +4.12）被限制在圆外绘制。
落在目镜圆内的镜筒内壁几何被模板测试**直接丢弃**。

于是镜内区域只剩下：
- **世界**（因为镜筒和目镜都没画在那里）
- **`division` 分划**（被圆切出来的那部分）

—— 与用户描述的「镜内不渲染除了外部世界和那个蒙版之外的任何效果」**完全一致**。

补充：`renderDivisionOnly` 里还有 `RenderSystem.disableDepthTest()`，
配合 `_illuminated` 节点的满亮度（`BedrockModel` 构造时置 `illuminated=true`），
让准星永远浮在最前、不被任何几何遮挡。

---

## 4. 低倍镜 / 组合镜 —— 用户判断正确

`renderSight`（上游 L334-356）**极其简单**：

```java
enableItemEntityStencilTest(); clearStencil(0);
renderOcularStencil(...);      // 目镜写模板
renderDivisionOnly(...);       // disableDepthTest + 画分划
stencilFunc(ALWAYS, 0); disableItemEntityStencilTest();
if (scopeBodyPath != null) renderTempPart(..., scopeBodyPath);   // ← 无条件画镜身
super.render(...);
```

对比筒镜路径，低倍镜：
- **没有**那个圆形 INVERT 模板；
- **没有**目镜黑色遮罩；
- `scope_body` **无条件绘制**（不做任何裁剪）。

这与我们 r34 修好后的现状**语义一致**（我们也是：sight 不剔除镜身、目镜恒掏空）。

差异只在**分划的画法**：上游用 `stencilFunc(EQUAL, i+1)` 把分划限制在
目镜模板区域内，我们没有 stencil，是靠白名单直接重画发光节点。
对红点这种「分划本来就只有一个小红点」的情况，两者观感几乎无差别 ——
这也解释了为什么低倍镜我们一直没出问题。

**组合镜**（`renderBoth`）= 对 `ocular_scope*` 走筒镜逻辑、
对 `ocular_sight*` 走低倍逻辑，靠 `selective` 参数分流。
用户说的「组合镜上的低倍镜和我们目前效果一致」也成立。

---

## 5. 我们移植时丢掉的一个关键细节

`getBedrockPartCenter`（上游 L185-195）第一行：

```java
poseStack.pushPose();
poseStack.last().pose().mulLocal(RenderSystem.getModelViewMatrix());   // ← 这一句
for (BedrockPart part : path) part.translateAndRotateAndScale(poseStack);
Vector3f result = new Vector3f(pose.m30(), pose.m31(), pose.m32());
```

**我们当前的实现没有这一句**（`BedrockAttachmentModel#getBedrockPartCenter`）。

上游是把「传入的 PoseStack」再左乘一次全局 `ModelViewMatrix`，
才得到可用于屏幕空间的坐标；随后 `centerX = x * 16 * 90` 里的 `90`
与圆顶点的 `z = -90.0F` 是配套的一组经验常数。

这解释了第 29 轮「圆心跑偏、且偏移量随各瞄具 ocular 方位而异」的现象 ——
当时我用 `Camera#getViewRotationProjectionMatrix` 自行投影，
与上游这套「ModelView 左乘 + 固定 z=-90」的约定完全不是一回事。

> ⚠️ 但注意：`RenderSystem.getModelViewMatrix()` 在 26.2 **已不存在**。
> 实测 `RenderSystem` 上与之相关的只剩：
> ```
> modelViewStack : org.joml.Matrix4fStack     （字段）
> getModelViewMatrixCopy()                    （方法，返回副本）
> ```
> 也就是说等价物**存在但换了形式**（`getModelViewMatrixCopy()` 或直接读
> `modelViewStack`）。不过第 26 轮已确认另一件事：
> `RenderType#prepare` 用的 `ModelViewMat` 就是 `getModelViewMatrixCopy()`，
> **与传给 `submitCustomGeometry` 的 PoseStack 无关** ——
> 所以照抄 `mulLocal(...)` 之前，必须先确认 26.2 手部渲染链路里
> 这个全局矩阵到底处于什么状态，不能想当然。

---

## 6. 对我们的意义

### 6.1 好消息：不需要 PIP，也不需要"光学模拟"

上游的镜内画面**不是**把世界重渲染一遍，就是「让镜内什么都别画，
于是透出后面本来就渲染好的世界」+「圆形切出的分划贴图」。
第 25/26 轮定的方向（放大靠 FOV 变焦，不做 PIP）是对的。

### 6.2 坏消息：核心依赖仍是 stencil

整套机制的每一步都建立在模板缓冲上：
- 目镜写模板 → 镜身按模板裁剪 → 圆形 INVERT → 分划按反转后的模板绘制。

而 26.2 的渲染抽象层（`RenderPipeline` / `DepthStencilState` /
`VulkanRenderPipeline`）**完全没有 stencil 概念**（第 25 轮已逐类确认，
GL 与 Vulkan 双后端的公共抽象里就不存在）。

### 6.3 可行的替代路径

既然已经确认「遮罩是一个**屏幕空间的圆**、圆心来自模型、半径是常数×进度」，
那么用 **fragment shader 做圆形裁剪** 就是语义等价的替代 ——
这正是 `SCOPE_STENCIL_DEBT.md` §4 指出的方向，现在有了更明确的参数：

| 需要传给 shader 的量 | 上游对应 |
|---|---|
| 圆心（屏幕空间） | `getBedrockPartCenter(ocular)` × 16 × 90 |
| 半径 | `80 × scopeViewRadiusModifier × aimingProgress` |
| 裁剪方向（内/外） | 镜身 = 圆外保留；分划 = 圆内保留 |

**但前提仍未解决**：第 29/31 轮两次失败都卡在
「圆心的屏幕坐标怎么算才对」。上游那句 `mulLocal(getModelViewMatrix())`
是重要线索，但该 API 在 26.2 已改名/改语义，需要单独查证后再动手。

---

## 7. 建议

1. **不要再改瞄具渲染代码**，直到「圆心屏幕坐标」这一个问题单独验证通过；
2. 验证方式建议：先做一个**只画一个纯色圆**的调试渲染（不接任何裁剪逻辑），
   在游戏里肉眼确认它是否稳定贴在目镜中心、开镜时是否正确张开。
   这一步成本低、结论明确，比直接写 shader 稳妥得多；
3. 圆心稳定后，再决定是走 shader `discard` 还是别的手段。
