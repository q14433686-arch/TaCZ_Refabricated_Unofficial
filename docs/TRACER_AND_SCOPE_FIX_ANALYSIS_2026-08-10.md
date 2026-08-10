# 26.2 历史遗留渲染问题分析：曳光弹枪口偏移 & 镜内裁切（2026-08-10）

**方法**：对 26.2 合并 jar（`.gradle/loom-cache/.../minecraft-merged-6f7fc6e6bc-26.2.jar`，
official 映射）逐方法反汇编，与 Sh1roCu/TACZ-Refabricated 1.21.1 上游源码逐行比对，
再配合本项目 `main` 分支现行代码与历史文档交叉验证。
**结论等级**：两个问题都找到了**确切的 26.2 特有根因**，并给出经数值仿真验证的修法。

---

## 问题一：第一人称曳光弹起点不在枪口

### 1.1 症状（用户 + 历史文档）

- 曳光弹起点不从枪口射出，与枪口有明显偏移；
- 转视角后偏移依然存在（不是「转过去就对了」的时序问题）；
- 多个版本（r9 / r10 / r24 / r25…）修过，均未根治。

### 1.2 26.2 手部渲染的真实坐标空间（字节码证据）

`GameRenderer#renderItemInHand`（26.2）核心字节码：

```java
PoseStack poseStack = new PoseStack();
poseStack.pushPose();
poseStack.mulPose(projection.invert(new Matrix4f()));   // ← 逆投影矩阵被乘进 PoseStack 基座
Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
modelViewStack.pushMatrix();
modelViewStack.mul(projection);                          // ← 投影矩阵被用作 ModelView
this.bobHurt(cameraState, poseStack);
this.bobView(cameraState, poseStack);
this.itemInHandRenderer.submitHandsWithItems(partialTick, poseStack, ...);
this.featureRenderDispatcher.renderAllFeatures(...);     // 手部 pass 在此执行
modelViewStack.popMatrix();
poseStack.popPose();
```

手部 pass 的 shader 计算是 `gl_Position = ProjMat * ModelViewMat * pos`：

- `ProjMat` = 手部投影 `P_hud`（`renderLevel` 里
  `hudProjection.setupPerspective(0.05f, 100f, hudFov, w, h)` 后
  `RenderSystem.setProjectionMatrix(...)` 设置）；
- `ModelViewMat` = `P_hud`（上面 push 的 modelViewStack 顶部）；
- `pos` = 物品顶点在 PoseStack 空间的值，而该 PoseStack 的基座是 `P_hud⁻¹`。

三者相乘：`P_hud · P_hud · (P_hud⁻¹ · B · v) = P_hud · B · v`，即
**手部物品的最终变换 B 仍然落在“摄像机对齐的视图空间”**（原点在相机、轴与相机对齐），
`P_hud⁻¹` 只是被用来抵消 ModelView 里多乘的那个 `P_hud`，**它对顶点没有净效果**。

**但关键在于**：它对手持 PoseStack 的**矩阵分量**有净效果。`renderFirstPerson` 里
`cacheMuzzlePosition` 直接读 `poseStack.last().pose()` 的平移分量，而那个矩阵是
`P_hud⁻¹ · B`，不是 `B`：

```
设 M = P_hud⁻¹ · B（M 就是捕获时 poseStack 的矩阵）
则 M 的第 4 列 = P_hud⁻¹ 的第 4 列 与 B 的第 4 列合成：
    m30 = (f/as)·B.x        （f = 1/tan(halfFov)，as = 宽高比）
    m31 = (1/f)·B.y
    m32 = −1                 ← 深度信息被 P_hud⁻¹ 的投影行吃掉，恒为 −1！
    m33 = 与 B.z 相关的量（≈30 量级，不是 1）
```

数值验证（fov=33°，16:9，近=0.05 远=100，枪口视图坐标 (0.05, −0.2, −1.8)）：

| 量 | 值 |
|---|---|
| 捕获到的 m30, m31, m32 | 0.0263, −0.0592, **−1.0** |
| 旧代码按“视图空间”直接用 | (0.0263, −0.0592, −0.423) |
| 旧代码在世界 pass 的屏幕落点 | x=0.050, y=**−0.200**（NDC） |
| 枪口真实屏幕落点 | x=0.053, y=**−0.375**（NDC） |

**旧代码把深度当成 −1·(f_l/f_h) ≈ −0.42 格**（无论枪口实际在 −1.8 还是 −3 格），
x/y 又带 FOV/aspect 畸变，于是起点既不在枪口深度、x/y 也不对 ——
这就是「固定在某处、转视角仍偏」的直接来源。

### 1.3 为什么历史文档说它是「视图空间」

历史文档（r25）反汇编了 `ItemInHandRenderer#submitHandsWithItems`，发现
「从入口到 submitArmWithItem 之间只有两条 mulPose（0.1 系数）」，
于是断定 PoseStack 停留在视图空间。**这条结论只对了一半**：
那两条 0.1 的旋转确实保留，但**文档漏看了它的调用方 `GameRenderer#renderItemInHand`
在更早处乘进去的 `projection.invert()`** —— 26.2 特有的手部管线改动，
1.21.1 的同一路径没有这一步。这就是「26.2 新引入、旧版本没有」的 bug 来源，
也是为什么上游 1.21.1 的「直接平移」能work、移植到 26.2 却不行。

（r25 日志里测到的 `gz≈−1.83` 来自 26.1.2 时代的日志文件 —— 26.1.2 的手部管线
还没有 `P_hud⁻¹` 基座，测出来的自然是“真视图空间”。26.2 改了管线后同一段代码
语义已变，日志结论不能照搬。）

### 1.4 修法（已验证）

在 `cacheMuzzlePosition` 里把捕获矩阵左乘手部投影，还原真视图空间枪口坐标：

```
B' = P_hud · M
B'.x = m00·m30        m00 = 1/(tan(fov/2)·as)
B'.y = m11·m31        m11 = 1/tan(fov/2)
B'.z = m22·m32 + m23·m33     （m22/m23 按 P_hud 的 z 行，含 zZeroToOne）
```

再对真深度施加 FOV 缩放：`offset = (B'.x, B'.y, B'.z · tan(itemFov/2)/tan(levelFov/2))`。
数值仿真：`P_level · offset` 的屏幕 NDC = `P_hud · B` 的屏幕 NDC，**逐分量相等**
（GL 与 Vulkan 两种 zZeroToOne 均验证通过）。曳光弹渲染侧（`EntityBulletRenderer`
里的 `rotate(camera.rotation())`）**无需改动**。

需要重建的 `P_hud` 参数与 vanilla 完全一致：
`setPerspective(fov=ITEM_MODEL_FOV_DYNAMICS, aspect=窗口宽高比, 100f, 0.05f, zZeroToOne)`
—— 注意 vanilla 的实参顺序是 `(fov, aspect, zFar, zNear, zZeroToOne)`，
必须原样复刻（近/远互换会让 z 行符号翻转）。

---

## 问题二：镜内裁切不正确（黑边缺失 + 枪体/配件未被裁掉）

### 2.1 症状（用户）

1. 目镜内那一圈黑色边缘（黑环）没有保留；
2. 镜内能看到枪体、配件（枪管、准星、枪口装置…），没有被裁掉。

### 2.2 现状：掩码 = 目镜几何的屏幕投影（两个症状的共同来源）

当前实现（`ScopeMaskRenderer` + `ScopeMaskGeometry`）把**目镜几何本身**画进离屏掩码：

- 掩码形状 = 目镜模型的投影形状；
- 镜身/目镜黑片用「盖到就 discard」的 shader 裁剪（`scope_body.fsh`）；
- 准星用「没盖到才 discard」的反向裁剪。

由此直接推出两个症状：

1. **黑边丢失**：掩码覆盖的是**整个目镜盘面**，而目镜黑片（ocular）也以
   同一个「盖到就 discard」类型提交 —— 整个盘面内全部被丢弃，
   盘面外才画镜身。于是目镜圆盘边缘没有任何「镜筒黑环」；
   上游的圆环来自「黑片画在圆外」—— 圆（半径 80·progress）比目镜盘面小，
   盘面外圈那一圈黑片就是黑环。我们的掩码没有“圆”的概念，只有“盘”，黑环无从产生。
2. **枪体/配件透进镜内**：枪体（`BedrockGunModel`）和非瞄具配件
   （`AttachmentRender#submitAttachment`）用的是普通 RenderType，
   **根本不吃掩码**。镜内（掩码内）是透明的，枪体画在透明区里自然可见。

另外，用「目镜几何投影」当掩码对非实心目镜是错的（文档 §3.5 已实测）：
第三方 PU 镜的 ocular 是 6 根细辐条，掩码就成了 6 条细缝 —— 准星只在缝里可见、
镜身只在缝外可见，观感完全不对。

### 2.3 上游到底怎么做的（逐行核实 1.21.1 `renderScope`）

```
1  clearStencil(0)
2  ocular_ring                    stencilFunc(ALWAYS,0)  正常画外环
3  renderOcularStencil            colorMask(false) + stencilOp(REPLACE)
                                  → 目镜投影区写模板 i+1（不写颜色）
4  scope_body                     stencilFunc(EQUAL,0)  只在目镜【外】画镜身
5  renderOcularAndDivision
   - 画圆（半径 80·scopeViewRadiusModifier·开镜进度，圆心=目镜投影中心），
     stencilOp(INVERT) → 圆内模板取反
   - 圆外（模板==i+1）画 ocular 黑片          → 镜筒黑环
   - 圆内（模板==~(i+1)）画 division 分划     → 镜内准星
6  关模板
7  super.render()                 其余枪体（无模板裁剪）
```

要点：
- 裁剪区是**屏幕空间圆**（圆心来自目镜、半径是常数×进度），不是目镜几何投影；
- 黑环 = 圆外的 ocular 黑片；
- 镜内 = 圆内什么都不画（世界透出）+ 分划。

### 2.4 26.2 等价实现（本修复）

沿用已被实测验证安全的「阶段边界离屏掩码」架构（r51 教训），只改**掩码内容**与
**消费方**：

1. **掩码从“目镜几何投影”改为“屏幕空间圆”**：
   - 圆心 = 当帧目镜几何在掩码空间（即视图空间，掩码 pass 用 identity ModelView）的
     质心投影；
   - 半径 = 目镜几何在该空间 XY 平面上的最大外延 × `SCOPE_CIRCLE_RATIO`(0.9)
     × 开镜进度 —— 半径随开镜进度从 0 长到 0.9×目镜外径，语义与上游
     `rad = 80 · progress` 一致，但按目镜实际尺寸自标定（对任意枪包都成立，
     不再依赖“80”这个经验常数，也顺带修掉 PU 镜“6 条细缝”问题）；
   - 掩码绿通道写 1.0，让 `scope_body.fsh` 里那套旧的“屏幕空间收缩”分支休眠
     （半径已编码进度，双重收缩会缩小镜区）。

2. **黑环恢复**：ocular 黑片仍以「盖到就 discard」类型提交 —— 圆内被丢弃、
   圆外（盘面外圈）保留 → 目镜黑片形成镜筒黑环（上游 step 5 的语义）。

3. **枪体/配件裁进镜内**：`renderFirstPerson` 在开镜用筒镜时，
   把**整把枪**（`BedrockGunModel`）与**全部配件**（`BedrockAttachmentModel`）
   的 RenderType 切换为掩码裁剪版（圆内 discard）。这样镜内只见世界与分划，
   枪管/前准星/枪口装置等在镜内的部分全部消失 —— 满足用户对
   「镜内裁切掉枪体、配件」的要求。（这是相对上游的增强：上游 step 7 其实
   不裁枪体，只是默认枪包几何恰好大多落在圆外；用户明确要求裁掉。）

   用静态“强制裁剪”标志实现：`renderFirstPerson` 前置判定（开镜进度、
   瞄具确有 ocular、掩码可用、非光影降级）后置位，`BedrockAttachmentModel.submit`
   在标志位生效时对无 ocular 的配件也走裁剪版 RenderType。

4. **回退安全**：任何一环不满足（掩码不可用/开关关闭/光影降级）都退回原渲染类型，
   与现状一致 —— 最坏退回「镜内能看到镜筒内壁」，不会更糟。

### 2.5 与 r46「固定圆」失败的差别

r46 失败在「圆心取了**屏幕中心** + 半径常数」；r29 失败在用了
`Camera#getViewRotationProjectionMatrix` 这种与手部管线不匹配的投影方法。
本方案圆心取**当帧目镜几何在掩码空间的实际投影质心**（与现有几何掩码同一套
`RenderSystem.getModelViewMatrixCopy() × poseStack` 烘焙矩阵，已被 33 个瞄具
实测验证过位置正确），半径按目镜实体外延自标定 —— 不依赖任何经验常数与
不匹配的投影 API。

---

## 改动清单

| 文件 | 改动 |
|---|---|
| `GunItemRendererWrapper.cacheMuzzlePosition` | 左乘手部投影还原真视图空间枪口坐标，再施加 FOV 缩放 |
| `ScopeMaskRenderer` | 掩码改为画屏幕空间圆（质心+外延×比例×进度）；管线拓扑 QUADS→TRIANGLES；绿通道=1；新增强制枪体裁剪标志 |
| `ScopeMaskGeometry` | Entry 不变（pose+cubes），半径由渲染器从 cubes 现算 |
| `BedrockAttachmentModel` | `resolveBodyRenderType`：强制标志生效时对无 ocular 配件也用裁剪版；新增 `hasOcularParts()`；`AIM_CLIP_START` 转 public |
| `GunItemRendererWrapper.renderFirstPerson` | 前置判定（瞄具为筒镜/组合镜 + 开镜进度 + 掩码可用）后，整枪/配件切换裁剪版 RenderType |
| `EntityBulletRenderer` | 无需改动（旋转链路已验证正确） |

## 实机验证步骤

**曳光弹**：
1. 开 `TracerDebug`，静止连续开火，看日志 `globalMuzzle=` 的 z 是否落在
   `−1.5 ~ −2.5`（真视图空间深度）而不是 `−1·(f_l/f_h)`（旧代码的钉死值）；
2. 第一人称腰射与开镜各打几发，起点应贴合枪口；转头 90°/180° 起点不再漂移。

**镜内裁切**：
1. 装筒镜开镜：镜内应是一个圆形的透明视窗（能看到被放大的世界 + 准星），
   圆外到目镜边缘有一圈黑色镜筒边缘（黑环）；
2. 开镜时枪管/前准星/枪口装置不应再出现在镜内；
3. 第三方 PU 镜（辐条状 ocular）：镜内应是实心圆视窗而非 6 条细缝；
4. 关 `ScopeMaskEnable` 应回到旧行为（镜内可见镜筒内壁）；
5. 黑环宽窄如需调整：`ScopeMaskRenderer.SCOPE_CIRCLE_RATIO`（0.9，越小环越宽）。

**已知取舍**：
- 半透明材质的枪（`enablesTransparency`）不做整枪裁剪（裁剪版基于
  ENTITY_CUTOUT，会丢半透明）—— 镜内仍可能看到这类枪的枪体；
- 第三人称无镜内裁剪（与上游一致，镜内只对本人有意义）。
