# 进度报告 · 第 9 轮 2026-07-25

基线：`tacz-26.2-r8-src.zip`。

---

## ① 曳光弹不从枪口射出，而是固定位置 —— 已修

**根因**：移植版在这段留了两处偏差（`EntityBulletRenderer#renderTracerAmmo`）：

```java
// 原代码
bullet.setCameraXRot(0); // cameraState.getYaw() 或其他替代方法
bullet.setCameraYRot(0); // cameraState.getPitch() 或其他替代方法
```

1. **摄像机旋转被硬编码为 0**，原注释写"无法获取相机旋转，暂时设置默认值"。
   于是下面"旋转 → 平移 → 反旋转"退化为在**未旋转坐标系**里做平移，
   `muzzleRenderOffset`（枪口相对摄像机的偏移）被当成世界轴偏移 ——
   **无论朝哪个方向开枪，曳光弹起点都固定在同一处**。

2. 上游那对"旋转/反旋转"**只在 Iris 光影启用时**才需要
   （1.21.1+ 渲染坐标空间已不需要手动转换，但 Iris 仍是老样子）。
   移植版**无条件执行**，即使拿到正确角度也会引入多余变换。

**修复**：
- 摄像机从 `Minecraft.getInstance().gameRenderer.mainCamera()` 取得，
  记录开火瞬间的 `xRot()/yRot()`（整条弹道沿用，避免转视角时曳光弹跟着甩）。
- 那对旋转/反旋转改为仅在 `IrisCompat.isUsingRenderPack()` 为真时执行，与上游一致。

> 26.2 API 变更（javap 确认）：`GameRenderer#getMainCamera()` → `mainCamera()`；
> `Camera#getXRot()/getYRot()` → `xRot()/yRot()`。
> 上游用的 `IrisCompat.isPackInUseQuick()` 在本移植版中对应 `isUsingRenderPack()`
> （同样是反射查询 `IrisApi#isShaderPackInUse`，无硬依赖）。

---

## ② 瞄准镜渲染 —— 已实现（分划/准星刻度），但**无法完全复刻上游**

### 先说一个必须交代的限制：26.2 移除了模板缓冲

上游用 **stencil（模板测试）** 实现"镜内不渲染枪体"。反编译确认 26.2 已**彻底移除**该能力：

| 检查项 | 结果 |
|---|---|
| `RenderSystem.stencilFunc / stencilOp / stencilMask / clearStencil` | **全部不存在** |
| `DepthStencilState` 字段 | 只有 `depthTest / writeDepth / depthBias*`，**无任何 stencil 字段** |
| `RenderPipeline.Builder` | 只有 `withDepthStencilState(...)`，无 stencil 设置项 |
| `RenderTarget` | 只有 `depthTexture`，**无 stencil 附件** |

（`GpuFormat` 里虽仍有 `D24_UNORM_S8_UINT`/`S8_UINT` 等格式常量，
但没有任何 API 能配置 stencil 的比较函数与操作，等于用不上。）

所以"镜内遮蔽枪体"在 26.2 **不可能按上游方式实现**，需要另做 PIP 离屏渲染方案，
不在本轮范围。

### 本轮实际修复的问题：分划（准星刻度）完全不显示

排查真实瞄具模型（`sight_uh1_geo.json`）后确认骨骼层级：

```
scope_body   parent=None     <- 根骨骼
division     parent=None     <- 根骨骼
ocular       parent=None     <- 根骨骼
scope_view   parent=None     <- 定位节点(无 cubes)
```

`scope_body` 与 `ocular` 都是**根骨骼**，本来就在 `shouldRender` 里，
由 `super.submit(...)` 正常绘制 —— 所以镜身和目镜其实是可见的。

问题出在 `division`：构造函数里执行了 `divisionModel.setHidden(true)`（即 `visible=false`），
因为上游的设计是"分划只在开镜时、由 `renderDivisionOnly` 在**关闭深度测试**下单独绘制，
以保证始终浮在镜筒内部之上"。而 26.2 移除 stencil 后，`renderScope/renderSight/renderBoth`
连同 `renderTempPart` 全部退化成 no-op —— **分划再也没被画出来**，
表现就是"瞄准镜没有准星刻度 / 看起来没做"。

**修复**：
- 新增 `BedrockRenderSnapshot#captureSubtree(...)`：以单个节点为根做几何快照
  （用于不在主渲染列表里、需按需单独绘制的部件）。
- `BedrockAttachmentModel#submit` 在**第一人称且为瞄具**时补提交 `division`。
- 由于 `division` 的 `visible=false` 会被快照跳过，提交时**临时置为可见、
  完成后 finally 还原**，避免影响其它渲染路径。

**刻意没做**：不重复提交 `scope_body`/`ocular`（它们已在 `shouldRender` 中，
重复提交会造成 Z-fighting 与重影）。这一点我在实现中途验证模型层级后做了修正。

### 已知残留

- 分划仍带深度测试，若镜筒内部几何挡在前面可能被遮挡（上游靠关深度测试规避）。
- 镜内仍会看到枪体（stencil 不可用，见上）。

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| ① 字节码含 `mainCamera` + `Camera.xRot/yRot`（3 处） | ✅ |
| ① 旋转由 `IrisCompat.isUsingRenderPack()` 门控 | ✅ |
| ② `captureSubtree` 入 jar 并被 `BedrockAttachmentModel` 调用 | ✅ |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

## 验收重点

- [ ] **曳光弹应从枪口射出**，朝各个方向开枪都正确（①）
- [ ] 开火后快速转视角，曳光弹不应跟着甩（①，沿用开火瞬间角度）
- [ ] 装 Iris 光影时曳光弹位置同样正确（①，走另一分支）
- [ ] **开镜时应能看到准星刻度/分划**（②）
- [ ] 各类瞄具（红点 sight / 长筒 scope / 组合镜）分别验证
- [ ] 回归：换弹双弹匣、第三人称手部皮肤、标靶车、弹孔粒子、行走动画

## 仍未解决

- **镜内遮蔽枪体**：26.2 移除 stencil，需 PIP 离屏方案重做（本轮已确认技术路径不可用）
- 副手开枪：上游即不支持，属新功能
- 一批 compat（Iris/ImmediatelyFast/Shoulder Surfing 等）仍是 no-op
