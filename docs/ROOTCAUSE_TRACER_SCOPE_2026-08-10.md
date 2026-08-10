# 两个历史遗留问题根因分析（2026-08-10）

**方法**：以 `minecraft-merged-6f7fc6e6bc-26.2.jar` 字节码为唯一事实来源，
逐方法反汇编核对；历史文档（`COMPAT_AND_ROADMAP.md §9`、`EntityBulletRenderer` 第 25 轮注释、
`SCOPE_*` 系列）仅作线索，凡与字节码冲突处一律以字节码为准。

---

## 问题一：曳光弹起点不固定在枪口，随朝向漂移

### 1.1 26.2 第一人称手部渲染的真相（字节码）

`GameRenderer#renderItemInHand`（26.2，逐条反汇编）：

```
PoseStack poseStack = new PoseStack();
poseStack.pushPose();
poseStack.mulPose(viewRotationMatrix.invert(new Matrix4f()));  // ★ 关键
RenderSystem.getModelViewStack().pushMatrix();
RenderSystem.getModelViewStack().mul(viewRotationMatrix);
bobHurt(cameraState, poseStack);
if (options.bobView) bobView(cameraState, poseStack);
itemInHandRenderer.submitHandsWithItems(partialTick, poseStack, ...);
featureRenderDispatcher.renderAllFeatures(handAndScreenSubmitNodeStorage);
RenderSystem.getModelViewStack().popMatrix();
poseStack.popPose();
```

而 `Camera#getViewRotationMatrix` 的字节码是：

```
cachedViewRotMatrix.set(rotation().conjugate());   // 世界→视图
```

`Camera#setRotation`：

```
rotation = rotationYXZ(PI - yaw*DEG, -pitch*DEG, 0);
FORWARDS(0,0,-1).rotate(rotation);                  // 视图 -Z → 世界朝向，即 视图→世界
```

结论（三处互相印证）：

1. **手部 poseStack 根部带着 `R = camera.rotation()`（视图→世界）**——
   `renderItemInHand` 乘的是 `invert(viewRotationMatrix)` = `rotation()`。
   着色器层 ModelView = `viewRotationMatrix`（世界→视图），二者在 GPU 上精确抵消，
   所以画面正确；但**任何从该 poseStack 读出来的平移分量都是世界轴、相对相机的**。
2. 实体管线（`LevelRenderer#submitEntities` + `EntityRenderDispatcher#submit`）只做
   `poseStack.translate(entity.pos - camera.pos)`，**无旋转**；着色器层再乘
   `viewRotationMatrix`。即实体 poseStack 是**相机相对的世界轴**空间。
3. 1.21.1 上游的手部 poseStack 不带 R（没有 `mulPose(invert(viewRot))` 这一步），
   所以上游捕获到的枪口偏移天然是**视图空间**——26.2 多出来的正是这一个旋转。

### 1.2 第 25 轮文档错在哪

`EntityBulletRenderer` 第 25 轮注释的「证据三」只反汇编了
`ItemInHandRenderer#submitHandsWithItems`（从入口到 `submitArmWithItem` 之间确实只有
两条 0.1 系数的 mulPose），**漏掉了调用者 `GameRenderer#renderItemInHand` 在进入
submitHandsWithItems 之前就乘好的 `mulPose(invert(viewRotationMatrix))`**。
于是它断言「poseStack 始终停留在视图空间」——与字节码不符。

「证据一」声称 `muzzleRenderOffset` 在 307° 偏航跨度上几乎不变（gz≈-1.83 常量），
由此反推它是视图空间常量。但按字节码，捕获值是 `R·M_v`（世界轴、相对相机），
玩家转向时必然整周摆动。该日志结论与字节码矛盾，判定文档分析不可信
（样本很可能取自同一朝向，或取自旧构建的另一字段）。

### 1.3 当前代码的实际错误

`cacheMuzzlePosition`（与上游 1.21.1 逐行相同）把

```
muzzleRenderOffset = (pose.m30, pose.m31, pose.m32 × k)     // k = tan(itemFov/2)/tan(levelFov/2)
```

当作「视图空间偏移」存了下来。在 26.2 里它实际是 `R·M_v`（世界轴），
而且 k 被乘在了**世界 z** 上（本应乘在**视图 z** 上）。

曳光弹侧（`EntityBulletRenderer.renderTracerAmmo`）再执行
`worldOffset = muzzleRenderOffset.rotate(camera.rotation())`，实体管线又乘一次
`viewRotationMatrix`——两次旋转互相抵消后，曳光弹的视图空间偏移等于
「`R·M_v` 的分量、k 乘在世界 z 上」，而正确值应是「`M_v` 的分量、k 乘在视图 z 上」。

二者仅当 `R ≈ I`（面朝北、yaw≈180°）或 `k ≈ 1`（未开镜）时重合。
**这就是「面朝不同方向，出发点各不相同」且「面北时刚好对」的精确来源。**

### 1.4 修复

只改 `GunItemRendererWrapper#cacheMuzzlePosition` 一处：
捕获后先乘 `camera.rotation().conjugate()`（世界→视图），把偏移还原成真正的
视图空间，**再**做 k 的 z 换算。这样与上游 1.21.1 的「视图空间捕获」语义完全一致，
`EntityBulletRenderer` 现有的 `rotate(camera.rotation())` 恰好成为正确的世界轴换算。

Iris 手部路径（`isHandRendererActive()`）绕过了 `renderItemInHand`、poseStack 不含 R，
因此该抵消必须跳过（与上游 Iris 分支「手动 YN/XN 旋转」语义对应）。

---

## 问题二：镜内裁切不正确（缺目镜黑圈 + 镜内未裁掉枪体/配件）

### 2.1 上游 1.21.1 的真实结构（已逐行核对 Sh1roCu/TACZ-Refabricated 1.21.1 源码）

`renderScope`（纯筒镜）：

```
① 清模板；ocular_ring 正常画
② renderOcularStencil：ocular 只写模板 i+1（colorMask=false），不画颜色
③ scope_body: stencilFunc(EQUAL, 0)   → 镜身只在目镜投影【外】画
④ renderOcularAndDivision：
   - 画一个屏幕空间圆（半径 80×modifier×progress，圆心=目镜投影中心），stencilOp=INVERT
   - stencilFunc(EQUAL, i+1)   画 ocular → 圆【外】的目镜 = 黑色遮罩（黑圈）
   - stencilFunc(EQUAL, ~(i+1)) 画 division → 圆【内】才画分划
⑤ 关模板；super.render() 其余部件（枪体等不受裁切）
```

关键点：
- 裁剪区域 A = **目镜几何的屏幕投影**（写进模板的那一步）；
- 窗口 B = 屏幕空间圆（半径随开镜进度从 0 长到 80×modifier），**全开时黑圈依然存在**；
- **黑圈 = 目镜几何画在 A−B 里**（这就是「目镜内那一圈黑色边缘」）；
- 枪体/配件在上游也不被 stencil 裁切，只是摄像机对齐 scope_view 后它们
  通常不落入窗口；玩家期望「镜内只剩世界+准星」。

### 2.2 当前移植版的问题

| 元素 | 上游 | 当前移植版 | 问题 |
|---|---|---|---|
| 镜身 scope_body | A 内不画 | 窗口内不画（全开时 `progress<0.999` 跳过收缩=整个 A） | 过渡期 A−B 环带露出镜筒内壁 |
| 目镜黑圈 | 画在 A−B | **整块被镜身裁切版 discard**（`super.submit` 整棵树只有一个 RenderType） | **黑圈缺失**（用户问题 2-a） |
| 准星 | 只画在窗口内 | 画在 A 内 | 全开时无黑圈所以看不出，修完黑圈后必须同步改为窗口内 |
| 枪体/配件 | 不裁（靠几何避开） | 不裁 | **窗口内能看到枪体/前瞄/消音器**（用户问题 2-b） |

### 2.3 修复方案（复用现有离屏掩码管线，不引入 CPU 投影）

掩码纹理 R 通道 = A（目镜投影），G 通道 = 开镜进度（现有）。片元着色器用
现有「掩码距离场」（环形采样）派生窗口 B = A 向内收缩 `0.055·(1−0.5·progress·(1−0.65))`
宽度的带。全开时仍保留 `0.65×0.055 ≈ 3.6% 屏高` 的黑圈带宽（不再有
`progress<0.999` 的跳过）。

三种裁切模式（同一份 fsh，靠 define 区分）：
- `SCOPE_MASK`（镜身）：A 内 discard（上游 `EQUAL 0`，过渡期也不露内壁）；
- `SCOPE_MASK_WINDOW`（新增，目镜黑圈/枪体/配件）：窗口内 discard——
  目镜因此只画在 A−B 的黑圈里；枪体因此镜内不可见；
- `SCOPE_MASK_INVERT`（准星）：只保留窗口内。

配套改动：
- `BedrockAttachmentModel`：maskable 时把筒镜组目镜从主提交摘出，
  用 `SCOPE_MASK_WINDOW` 渲染类型单独提交（只出黑圈）；非瞄具配件在
  「本帧有筒镜开镜」（新静态开关 `ScopeClipState`）时改用窗口裁切版；
- `GunItemRendererWrapper#renderFirstPerson`：筒镜开镜时把枪体 RenderType
  换成窗口裁切版（含透明枪的混合版本）；开镜进度 ≤ 阈值或光影包启用时回退原样；
- Iris GL 桥同步：`IrisScopeMaskState` 新增 mode 3（窗口裁切），
  `IrisShaderCreatorMixin` 注入的 GLSL 分支改为与 scope_body.fsh 相同的
  A/B 分离 + 全开保留黑圈语义（否则光影包下新管线会落到 mode 0，目镜黑圈
  会退化成整块黑镜片 —— 比修复前更糟）。

---

## 附：验证过的 26.2 事实清单（字节码行号）

| 事实 | 位置 |
|---|---|
| 手部 poseStack 根部 `mulPose(invert(viewRotationMatrix))` | `GameRenderer#renderItemInHand` L350 |
| 手部 modelViewStack `mul(viewRotationMatrix)` | 同上 L352 |
| 手部 pass 的 ProjMat = hudFov 投影 | `GameRenderer#renderLevel` L592-593 |
| 世界 pass 的 ProjMat = `projection×bob`（bob 烘焙进投影） | `GameRenderer#renderLevel` L579-580 |
| 实体提交只 `translate(entity.pos-camera.pos)` | `LevelRenderer#submitEntities` L641；`EntityRenderDispatcher#submit` L153 |
| 实体 pass 的 ModelView = `viewRotationMatrix` | `LevelRenderer#render` L176-177 |
| `viewRotationMatrix = conjugate(rotation())`（世界→视图） | `Camera#getViewRotationMatrix` L396-397 |
| `rotation = rotationYXZ(PI-yaw,-pitch,0)`（视图→世界） | `Camera#setRotation` L346 |
| 每 draw 的 DynamicTransforms = `RenderSystem.getModelViewMatrixCopy()` | `RenderType#prepare` L66 |
| `submitFeatures` 用 `new PoseStack()`（单位阵） | `LevelRenderer#submitFeatures` L287 |
| 掩码 pass 在 `executeSolid` 之前 | `FeatureRenderDispatcherMixin`（本仓库） |
