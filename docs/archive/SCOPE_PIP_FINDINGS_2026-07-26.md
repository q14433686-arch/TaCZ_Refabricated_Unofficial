# 瞄准镜「镜内放大」到底要做什么 · 反编译 + 上游对照结论

**日期**：2026-07-26
**方法**：上游 1.21.1 源码逐行读 + 26.2 字节码反查 + 默认枪包 33 个瞄具模型 / 101 个配件 display 全量统计
**结论等级**：**推翻 `SCOPE_PIP_PLAN.md` 的核心前提**，PIP 的工作量比原估计小一个数量级

---

## 0. 一句话结论（先说最重要的）

> **上游 TACZ 从来没有「把世界渲染两遍」。**
> 「镜内放大」是**摄像机 FOV 变焦**做的，不是离屏渲染做的。
> stencil 只负责**挖一个圆形孔洞**（决定哪块像素能看见世界），**与放大无关**。
>
> 而 **FOV 变焦这条链路在我们仓库里已经完整移植好了**（`CameraSetupEvent` 与上游逐行一致，
> 只有 3 处 26.2 改名差异）。
>
> **所以我们缺的不是「PIP 离屏渲染」，只是「圆形遮罩」这一个零件。**

---

## 1. 上游的真实实现（逐行证据）

### 1.1 放大 = 改摄像机 FOV，与镜内渲染完全无关

`CameraSetupEvent#applyScopeMagnification`（上游 1.21.1 第 89~114 行）：

```java
public static void applyScopeMagnification(ViewportEvent.ComputeFov event) {
    if (!event.usedConfiguredFov()) return;      // 只改【世界】渲染的 fov
    float zoom = iGun.getAimingZoom(stack);
    float aimingProgress = gunOperator.getClientAimingProgress(partialTick);
    float fov = WORLD_FOV_DYNAMICS.update(
        MathUtil.magnificationToFov(1 + (zoom - 1) * aimingProgress, event.getFOV()));
    event.setFOV(fov);                            // ← 整个世界的 FOV 被压小 = 放大
}
```

配套的 `applyGunModelFovModifying`（第 116~156 行）走 `usedConfiguredFov() == false` 分支，
**只改手部模型的 fov**，让枪不跟着一起被拉伸。

**两个 FOV 是分开的**，这正是「镜内看起来放大、枪身比例不变」的原理。
`Camera` 在 26.2 里也确实是两个独立方法（字节码确认）：

```
net.minecraft.client.Camera:
    M private calculateFov(F)F        ← 世界 FOV
    M private calculateHudFov(F)F     ← 手部/HUD FOV
    F private fov F
    F private hudFov F
```

### 1.2 stencil 只做「圆形孔洞」，不做放大

`renderOcularAndDivision`（上游第 246~297 行）核心：

```java
RenderSystem.stencilOp(GL_KEEP, GL_KEEP, GL_INVERT);
RenderSystem.colorMask(false, false, false, false);   // 只写模板，不写颜色
float rad = 80 * scopeViewRadiusModifier;
rad *= getClientAimingProgress(partialTick);          // 不开镜 -> rad=0
// 用 TRIANGLE_FAN 画一个 90 边形的圆，把模板值 INVERT
...
// 然后：模板==i+1 的地方画【黑色目镜遮罩】，模板==~(i+1) 的地方画【分划】
```

语义是：

| 区域 | 模板值 | 画什么 | 视觉结果 |
|---|---|---|---|
| 圆**外**（镜筒边缘） | `i+1` | `ocular` 黑色实体几何 | 不透明黑边 |
| 圆**内**（镜片中央） | `~(i+1)` | 只画 `division` 分划 | **透空 → 直接看见已被 FOV 放大的世界** |

**关键**：圆内不是「贴一张离屏渲染的纹理」，而是**什么都不画**——
让主画面（已经被 §1.1 的 FOV 放大过）**原样透出来**。

半径随 `aimingProgress` 从 0 长到 80，形成开镜时圆孔张开的过渡。

### 1.3 全仓验证：上游没有任何二次世界渲染

grep 上游全部源码：

```
renderLevel / TextureTarget / outputColorTexture / renderWorld  → 0 命中
RenderTarget 相关仅 2 处，且都是 tacz$enableStencil()（给主 RT 开模板缓冲）
```

**上游根本没有 PIP。** `SCOPE_PIP_PLAN.md` 里「多渲一遍世界」的性能焦虑，
**是我们自己臆想出来的需求**，上游从未这么做，也不需要这么做。

---

## 2. 我们的现状：放大其实**已经能用了**

把我们仓库的 `CameraSetupEvent` 第 83~157 行与上游第 86~160 行做
whitespace-insensitive diff，**只有 3 行差异，全是 26.2 改名**：

| 上游 1.21.1 | 我们（26.2） |
|---|---|
| `event.getCamera().getEntity()` | `event.getCamera().entity()` |
| `ResourceLocation` | `Identifier` |

事件也已正确接线（`TaCZFabricClient` 第 68~69 行）：

```java
ViewportEvent.FOV.register(CameraSetupEvent::applyScopeMagnification);
ViewportEvent.FOV.register(CameraSetupEvent::applyGunModelFovModifying);
```

而 `CameraMixin` 的两个注入点也已用字节码确认存在：
`Camera#calculateFov(F)F`、`Camera#calculateHudFov(F)F`（均 private，`@ModifyReturnValue` 可用）。

> **推论**：开镜时世界**应该已经在放大了**。第 17 轮用户说「镜内没有放大」，
> 很可能实际现象是**「放大了，但镜片被一块黑色 ocular 糊住了看不见」**——
> 而那正是第 18 轮修掉的问题（关闭 ocular 可见性）。
>
> ⚠️ **这一条需要你实机确认**：装 6 倍镜（`scope_1873_6x`，zoom=6）开镜，
> 看远处地形是否明显变大。如果**是**，那 PIP 的主体工作其实已经完成了。

---

## 3. 「适配各种瞄准镜」靠的是数据，不是代码分支

我把默认枪包 **33 个瞄具几何模型** + **101 个配件 display** 全量跑了一遍。

### 3.1 节点结构统计（决定遮罩怎么画）

| 节点 | 出现次数 | 作用 |
|---|---|---|
| `scope_view` | 33 | **瞄准定位锚点**（摄像机对齐到这里） |
| `division` | 33 | 分划/十字线（镂空贴图） |
| `scope_body` | 32 | 镜身 |
| `ocular` | 29 | 目镜遮罩（单目镜型） |
| `ocular_ring` | 14 | 目镜外圈 |
| `scope_view_2` / `division_2` | 4 | **双目镜型的第二组** |
| `ocular_scope` / `ocular_scope_2` | 4 | 双目镜型的**高倍**目镜 |
| `ocular_sight` / `ocular_sight_2` | 4 | 双目镜型的**低倍**目镜 |

**三种形态**（上游 `render()` 第 156~162 行的三分支正对应）：

| 形态 | 数量 | 代表 | 处理 |
|---|---|---|---|
| **纯低倍 sight** | 19 | `sight_t1/t2/exp3/okp7/rmr_dot`… | `renderSight`：**完全不画目镜遮罩**，只画分划 |
| **纯高倍 scope** | 10 | `scope_98k`、`scope_1873_6x`、`scope_acog_ta31`… | `renderScope`：画圆形遮罩 |
| **组合镜 both** | 4 | `scope_hamr`、`scope_mk5hd`、`scope_vudu`、`scope_standard_8x` | `renderBoth`：按 `ocular_scope*` / `ocular_sight*` 前缀**分别**处理 |

组合镜的 4 个都同时有 `scope_view` + `scope_view_2`，靠 `views[]` 索引切换。

### 3.2 倍率数据（决定放大多少）

`display/attachments/*.json` 里已有现成字段，**无需新增任何数据**：

```
scope_1873_6x     zoom=[6]          fov=33.0
scope_vudu        zoom=[6.5, 1.35]  ← 双倍率，数组长度=2
scope_elcan_4x    zoom=[4.25, 1.25]
scope_hamr        zoom=[3.25, 1.25]
sight_t1/okp7     zoom=[1.5]        fov=45.0
scope_acog_ta31   zoom=[2.5]        fov=45.0
```

切换逻辑（上游 `FirstPersonRenderGunEvent` 第 151~172 行）：

```java
int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
int[] views = index.getViews();
viewIndex = views[zoomNumber % views.length] - 1;     // 取模 -> 循环切档
List<BedrockPart> scopeViewPath = attachmentModel.getScopeViewPath(viewIndex);
aimingNodePath.addAll(scopeViewPath);                  // 摄像机对齐到对应目镜
```

`zoomNumber` 由 `ZoomNumber` NBT 持久化 —— **正是第 18 轮修好的那个写回**
（`LivingEntityAim#zoom` 补 `setAttachmentTag`）。所以组合镜切档现在应该是通的。

**结论**：适配各种瞄具**不需要写任何 per-scope 特判**。
只要遵守节点命名约定（`ocular*` / `scope_view*` / `division*`）+ display 里的 `zoom[]`，
第三方枪包也自动适配。这套机制我们已经完整继承。

---

## 4. 唯一真正缺失的零件：圆形遮罩

26.2 移除 stencil 后，我们只是**把 ocular 整个关掉**（第 18 轮），
于是：圆外的黑边也没了 → 镜筒没有边缘遮挡感，但**能看见放大的世界**（可用，只是不够像）。

要补回「圆外黑、圆内透」，26.2 下有三条路，**推荐 A**：

### 方案 A：Scissor 矩形裁剪（最省，先做）

字节码确认 26.2 有：

```
RenderSystem.enableScissorForRenderTypeDraws(int x, int y, int w, int h)
RenderSystem.disableScissorForRenderTypeDraws()
```

（vanilla `GuiItemAtlas` 自己在用，是活 API。）

做法：提交 ocular 遮罩几何**之前**开 scissor，把绘制限制在目镜圆的**外接矩形之外**。
- 优点：零着色器、零离屏 RT，改动 20 行以内
- 缺点：**矩形不是圆形**，边缘是方的

> 适合作为第一步验证「遮罩能不能按 `aimingProgress` 动态收放」。

### 方案 B：自定义 fragment shader 采样圆形蒙版贴图（最像上游，推荐最终形态）

用 `submitCustomGeometry`（签名已确认）提交一个覆盖目镜区域的四边形，
自定义 pipeline 用 `ColorTargetState` 的 `WRITE_*` 掩码 + 蒙版纹理：

```
com.mojang.blaze3d.pipeline.ColorTargetState:
    WRITE_RED / WRITE_GREEN / WRITE_BLUE / WRITE_ALPHA / WRITE_COLOR / WRITE_ALL / WRITE_NONE
    <init>(Optional<BlendFunction>, GpuFormat, int writeMask)
```

在片元里 `discard` 掉圆内像素、保留圆外 → 等价 stencil 的 INVERT 效果，
且**边缘可羽化，比 stencil 硬边更好看**。

半径公式直接沿用上游：`rad = 80 * scopeViewRadiusModifier * aimingProgress`。

### 方案 C（**不推荐**）：离屏二次渲染世界

即原 `SCOPE_PIP_PLAN.md` 的 P2~P5。
**没有必要** —— 上游就不这么做，放大已由 FOV 解决。
而且 r19 已证实 26.2 地形硬取 `mainRenderTarget`、不看 `outputColorTextureOverride`，
要做必须 Mixin 换 target + 完整跑一遍 `renderLevel`，**性能代价巨大而收益为零**。

**建议把 `SCOPE_PIP_PLAN.md` 的 P2~P5 整体作废。**

---

## 5. 修正后的路线图

| 步骤 | 内容 | 成本 | 风险 |
|---|---|---|---|
| **0（先做）** | **实机确认 FOV 放大是否已生效**（6 倍镜开镜看远处） | 0 | — |
| 1 | 若已生效 → 只补圆形遮罩；方案 A 先验证收放逻辑 | 小 | 低 |
| 2 | 方案 B 换成真圆 + 羽化边缘 | 中 | 低（纯 GUI 层，不碰世界渲染） |
| 3 | 组合镜（4 个）按 `ocular_scope*`/`ocular_sight*` 分别遮罩 | 小 | 低 |
| ~~4~~ | ~~离屏二次渲染世界~~ | — | **作废** |

**性能问题不复存在**：方案 A/B 都只画一个屏幕空间四边形，
相比「多渲一遍世界」是**零开销**。第 15/17/19 轮反复纠结的性能预算、
隔帧更新、降分辨率、Iris 降级——**全部不需要了**。

---

## 6. 与既有文档的冲突裁决

| 文档 | 原说法 | 裁决 |
|---|---|---|
| `SCOPE_PIP_PLAN.md` §1 | 「镜内放大需要 PIP 离屏渲染」 | ❌ **推翻**。放大 = FOV，上游无 PIP |
| `SCOPE_PIP_PLAN.md` §4.5 / §8.4 | 「性能：世界渲染 ×2，需默认关闭 + 隔帧」 | ❌ **前提不成立**，无需二次渲染 |
| `PROGRESS_ROUND19.md` §3 | 「地形不看 outputColorTextureOverride」 | ✅ 事实正确，但**结论用不上了**（不需要重定向） |
| `PROGRESS_ROUND18.md` P1 | 「离屏 RT 验证 PASS」 | ✅ 结论有效，但**这条路不必再走** |
| `PROGRESS_ROUND17.md` §2 | 「低倍/高倍分类策略」 | ✅ **完全正确**，且与本次 33 个模型统计吻合 |
| `PROGRESS_ROUND9.md` | 「26.2 无 stencil」 | ✅ 正确（`RenderSystem` 无任何 stencil 方法） |

---

## 6.5 【第 21 轮实测反馈】判断条件写反了，已修

用户实测（2026-07-26 22:31 截图）：**除低倍镜外，所有中/高倍镜开镜后镜片仍是全黑，没有被掏空。**

### 根因：`shouldDrawOcularMask()` 与上游语义相反

把上游 `renderOcularAndDivision` 的 stencil 逐行推成真值表：

```
stencilOp(KEEP, KEEP, GL_INVERT)  +  rad = 80 * modifier * aimingProgress

[不开镜] rad=0  -> 圆退化成一点 -> 目镜模板保持 i+1
         -> stencilFunc(EQUAL, i+1) 通过 -> 画 ocular 黑遮罩  => 整片黑
[开镜]   rad=80 -> 圆张开 -> 圆【内】模板被 INVERT 成 ~(i+1)
         -> EQUAL(i+1) 不通过 -> ocular 不画      => 掏空
         -> EQUAL(~(i+1)) 通过 -> 画 division      => 分划浮在中央
         -> 圆【外】模板仍是 i+1 -> 画 ocular      => 镜筒黑边
```

**上游语义是「开镜 → 掏空」，而 r17/r18 写成了「开镜 → 画遮罩」，整个反了。**

### 修正

```java
// 修正前（r17/r18）
return currentAimingProgress() >  OCULAR_MASK_MIN_PROGRESS;
// 修正后（r21）
return currentAimingProgress() <= OCULAR_MASK_MIN_PROGRESS;
```

修正后真值表（已逐项验证，与上游一致）：

| 类型 | 不开镜 | 开镜 |
|---|---|---|
| 低倍镜 sight（10） | 掏空 | 掏空 |
| 高倍镜 scope（8） | 黑镜片 | **掏空** |
| 组合镜 `ocular_scope*`（2） | 黑镜片 | **掏空** |
| 组合镜 `ocular_sight*` | 掏空 | 掏空 |

> 修正了 §3.1 的分类计数：按 display 里的 `scope` / `sight` 布尔字段实测为
> **scope-only 8、sight-only 10、both 2**（`scope_hamr`、`scope_vudu`），
> 此前根据模型节点估的「11/19/3」不准。

### 为什么取「掏空」这一侧

26.2 无 stencil，画不出「圆外黑、圆内透」的渐变，只能二选一。
取掏空是因为玩家真正需要的是**透过镜片看到已被 FOV 放大的世界**（§1）。
代价是暂时没有镜筒黑边暗角 —— 这正是 §4 那个「圆形遮罩」零件要补回来的东西。

### 顺带澄清一处死代码

`renderOcularStencil` / `renderOcularAndDivision` 属 legacy `render()` 路径，
内部绘制全走 `renderTempPart(...)`，而后者在 26.2 是**彻底的 no-op**（r18 已确认）。
**这两个方法整体不产生任何画面**，真正生效的是 `submit()` 里对 ocular 节点
`visible` 的控制。已在代码里加注，避免后人再在此处误改。

---

## 7. 未验证 / 需你确认

1. **FOV 放大是否实机生效** —— §2 的推论，这是整个结论的地基。
   第 21 轮修好掏空后**才第一次有可能看见镜内画面**，请重点确认：
   装 6 倍镜（`scope_1873_6x`，zoom=6）开镜，远处地形是否明显变大。
   - 若**放大了** → §1 结论成立，剩下只需补圆形遮罩（§4）恢复镜筒暗角；
   - 若**没放大**（镜内世界与镜外一样大）→ `ViewportEvent.FOV` 链路有断点，我再查。
2. **组合镜**（`scope_hamr` / `scope_vudu`）切档后两个目镜是否都表现正常。
3. **回归**：低倍镜/红点应保持透空（不应因本次修改变黑）。
4. 沙盒无 GPU、无 `javac`，全部结论基于源码/字节码静态分析；
   第 21 轮的代码改动只做了括号配平与真值表验证，**未编译**。

---

## 附：证据索引

| 结论 | 证据 |
|---|---|
| 放大 = FOV | 上游 `CameraSetupEvent.java:89-114`（世界）/ `:116-156`（手部） |
| 26.2 双 FOV 方法存在 | 字节码 `Camera#calculateFov(F)F` / `calculateHudFov(F)F` |
| stencil 只挖圆 | 上游 `BedrockAttachmentModel.java:246-297` |
| 上游无二次渲染 | 全仓 grep `renderLevel|TextureTarget|renderWorld` = 0 |
| 三种瞄具形态 | 上游 `BedrockAttachmentModel.java:156-162` + 33 模型统计 |
| 倍率数据现成 | `display/attachments/*.json` 的 `zoom[]` / `fov` |
| 档位切换 | 上游 `FirstPersonRenderGunEvent.java:151-172` |
| scissor 可用 | 字节码 `RenderSystem#enableScissorForRenderTypeDraws(IIII)V`，`GuiItemAtlas` 在用 |
| 颜色写掩码可用 | 字节码 `ColorTargetState.WRITE_*` |
| 自定义几何可用 | 字节码 `SubmitNodeCollector#submitCustomGeometry(PoseStack,RenderType,CustomGeometryRenderer)` |
