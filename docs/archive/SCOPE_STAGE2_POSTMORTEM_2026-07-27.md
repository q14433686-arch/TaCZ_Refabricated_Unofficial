# 阶段 2/3 失败复盘 · 三个症状同一个根因

**日期**：2026-07-27（第 30 轮）
**状态**：代码已 revert 回阶段 1（`34ee09a`，用户已测试通过）
**本文**：查清根因，给出修正方案。**未动代码**。

---

## 0. 用户反馈的三个症状

| # | 症状 | 我的初判 |
|---|---|---|
| 1 | 部分瞄准镜（QMK152、坎尔埃/ELCAN、毛瑟/98k 等）效果错乱 | 圆心算错 |
| 2 | 准星贴图挂在**物镜前方**，而非镜筒内靠近目镜处 | 裁剪失效后几何原形毕露 |
| 3 | 部分高级镜（斥候/scout）开镜后目镜**闪烁**，疑似两套东西重叠 | 圆心错导致边界抖动 |

**三个症状其实是同一个根因的不同表现。**

---

## 1. 根因：投影矩阵用错，视角旋转被应用了两次

### 1.1 我写的代码

```java
Matrix4f viewProj = gameRenderer.mainCamera().getViewRotationProjectionMatrix(new Matrix4f());
Vector3f centerNdc = viewProj.transformProject(new Vector3f(cx, cy, cz));
```

### 1.2 那个矩阵到底是什么（字节码）

`Camera#getViewRotationProjectionMatrix`：

```java
this.getViewRotationMatrix(cachedViewRotMatrix);              // 视角旋转
this.projection.getMatrix(cachedViewRotProjMatrix);           // 投影
cachedViewRotProjMatrix.mul(cachedViewRotMatrix);             // 投影 × 视角旋转
return dest.set(cachedViewRotProjMatrix);
```

即 **`ProjMat × ViewRotation`**。它是给**世界坐标**用的 ——
`GameRenderer#projectPointToScreen` 的用法是先 `worldPos.subtract(camera.position())` 再乘它。

### 1.3 但我传进去的坐标已经是相机空间的

第一人称手部渲染的 `PoseStack` **已经包含**视角回摆与手臂变换，
其平移分量本身就是「相对相机」的坐标。

**再乘一次视角旋转 = 旋转被应用两次。**

### 1.4 这如何解释全部三个症状

- **圆心跑偏**，且偏移量取决于目镜相对相机的方位 →
  不同瞄具的 `ocular` 位置不同（见下表），偏移各异 →
  **正好解释「部分瞄准镜错乱、另一些看着还行」（症状 1）**
- 圆心跑偏 → INSIDE 管线裁不到正确区域 → `division` 那些大平面整片露出 →
  **症状 2 的表象**
- OUTSIDE（镜身）与 INSIDE（准星）共用同一个错误圆，但几何不同，
  边界处一会儿被裁一会儿不被裁 → **症状 3 的闪烁**

---

## 2. 关于症状 2 的额外认识：`division` 本来就在物镜前方

即便圆心算对，也必须理解这一点。实测各瞄具的 z 坐标（模型空间，−z 为枪口方向）：

| 模型 | `ocular` z | `division` z | 相距 |
|---|---|---|---|
| `scope_98k` | +3.58 | −17.0 | 20.6 |
| `scope_qmk152` | +2.05 | −26.1 | 28.1 |
| `scope_elcan_4x` | +2.06 | −77.4 | **79.5** |
| `scope_scout` | +4.06 | −99.75 | **103.8** |
| `scope_1873_6x` | +4.25 | −111.0 | **115.3** |

**`division` 根本不是贴在目镜上的薄片，而是建模在镜筒前方几十甚至上百单位处的一组大平面。**

上游之所以看起来正常，是因为 stencil 把它们在**屏幕空间**裁成目镜圆的形状 ——
玩家看到的是「目镜里的十字线」，而不是「前方悬浮的大平面」。

所以用户那句「你完全没有把他当作瞄准镜内靠近目镜的东西，而是挂在物镜前方」
描述的是**模型的真实几何位置**；裁剪一旦失效，它就原形毕露。

> 这也说明：**屏幕空间圆形裁剪是必须的**，不是可选优化。

---

## 3. 修正方案：把投影交给 GPU，Java 侧不再重建

### 3.1 为什么之前的思路本身就脆弱

我在 Java 侧「重建一遍投影」，就必须精确复现引擎当前用的矩阵 ——
一旦对坐标空间的假设有偏差（正如本次），结果就完全错位。
而且 `Camera.projection` 是 **private 且无 public 访问器**，
硬做还得加 mixin，进一步增加出错面。

### 3.2 关键事实：提交时顶点已烘焙到相机空间

`BedrockRenderSnapshot#write` 里 `cube.compile(pose, ...)` 的 `pose`
是快照时记录的**完整矩阵**（含瞄具父链 + 手部 + 相机变换）。
而 `submitCustomGeometry` 传的是 **identity PoseStack**：

```java
PoseStack identity = new PoseStack();
collector.submitCustomGeometry(identity, renderType, (entryPose, consumer) -> snapshot.write(consumer));
```

于是顶点着色器里：

```glsl
gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
//                      ↑ ≈ 单位矩阵
```

**送进 GPU 的顶点坐标本身就是相机空间的。**

### 3.3 因此正确做法

Java 侧**只传相机空间坐标**，不做任何投影：

```
TextureMat[0].xyz = 目镜中心（相机空间）
TextureMat[1].xyz = 目镜边缘点（相机空间，中心 + 通光孔半径）
TextureMat[2].x   = 羽化宽度
TextureMat[2].y   = 启用标志
```

顶点着色器用**它当前真正在用的 `ProjMat`** 投影这两个点：

```glsl
vec4 c = ProjMat * vec4(maskCenterView, 1.0);
vec4 e = ProjMat * vec4(maskEdgeView,   1.0);
vec2 cNdc = c.xy / c.w;
vec2 eNdc = e.xy / e.w;
// 传给片元，片元换算成像素后比较距离
```

**优点**：
1. 不需要 `Camera.projection`，不需要 mixin；
2. 用的就是绘制这批几何的同一个矩阵，**不可能错配**；
3. 圆心自动跟随瞄具，不同瞄具无需特判 → 症状 1 消失；
4. 裁剪正确 → 大平面被裁成目镜圆 → 症状 2 消失；
5. OUTSIDE/INSIDE 共用同一套正确的圆 → 边界一致 → 症状 3 消失。

---

## 4. 仍需注意的两点

### 4.1 `ocular` 的坐标空间

`computeOcularApertureRadius` 目前直接读 cube 的 `minX/maxX`，
但那是**相对自身骨骼 pivot** 的局部值。半径是「尺寸差」所以不受 pivot 平移影响，
**这一项是对的**；但圆心必须取自 `PoseStack` 在套完整条父链后的平移分量
（相机空间），不能用 cube 坐标。

### 4.2 组合镜的多目镜

`scope_hamr` / `scope_vudu` 等有两个目镜。当前取 `ocularNodePaths.get(0)`。
上游用 `selective` 分支按 `ocular_scope*` / `ocular_sight*` 区分。
**建议先用单目镜跑通，再处理组合镜** —— 否则一次引入两个变量，出问题难定位。

---

## 5. 下一步

按 §3.3 重写阶段 2，并保留阶段 1（已通过测试）不动。
实施前会先确认 `ProjMat` 在自定义 shader 中的可用性
（`#moj_import <minecraft:projection.glsl>`，vanilla `entity.vsh` 已在用）。

**本轮未改代码，仅 revert + 本文。**
