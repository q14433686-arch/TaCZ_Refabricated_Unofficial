# 第 21 轮进度报告 —— 对照上游核查两条社区反馈

**日期**：2026-08-01
**触发**：社区反馈

> "Awesome port! I just happen to come across not seeing the guns I equipped in 3rd person.
> Also I can't zoom on the models in the workbench."

**方法**：`git clone -b 1.21.1 Sh1roCu/TACZ-Refabricated` 逐文件对照，
所有 26.2 侧结论均以 `minecraft-merged-6f7fc6e6bc-26.2.jar` 的字节码为准。

---

## 零、先给结论

| 反馈 | 判定 | 处置 |
|---|---|---|
| ① 第三人称看不到装备的枪 | **部分成立**：上游语义确实不是"无条件挂载"，用户的记忆是对的；但核查中发现<b>左利手玩家主手枪不渲染</b>是真 bug | 已修 |
| ② 工作台模型不能缩放 | **完全成立，且是移植缺陷而非上游行为** | 已修 |
| ③ 顺带核查的 GUI 还原度 | 发现 4 处独立缺陷（过滤器行高错位 / 勾选框贴图失效 / 两处 alpha=0 文字不显示 / HUD 版本号溢出） | 已修 |

---

## 一、反馈① —— 你的记忆是对的，但比你说的要多一点

### 1.1 上游到底挂载什么

`HumanoidOffhandRender#renderGun`（上游 1.21.1，逐行核对）做两件事：

```java
renderOffhandGun(...);   // 副手的枪
renderHotbarGun(...);    // 快捷栏 0..8 中【非当前选中】的枪
```

关键在 `renderHotbarGun` 的门禁：

```java
var hotbarShow = display.getHotbarShow();
if (hotbarShow == null || hotbarShow.isEmpty()) return;
if (!hotbarShow.containsKey(inventoryIndex)) return;   // ← 必须显式配了这个槽位
```

也就是说**背挂与否完全由枪包 display JSON 的 `hotbar_show` 决定**，
不配就不挂 —— 这一点你说得对，**不是无条件挂载背包里任意枪**。

但有个细节值得补充：默认枪包 54 个 `*_display.json` **全部**配了
`"hotbar_show": { "0": {...} }`（已脚本统计，54/54 命中且仅有 `"0"` 这一个键）。
所以在默认枪包下，**放在快捷栏第 1 格的枪，只要不是当前手持，就会背在身上**。
这既是上游行为，也是我们的行为 —— 本移植的
`HumanoidOffhandRender` 与 `GunDisplayInstance#checkLayerGunShow` 与上游逐行等价，
没有偏差。

**结论**：反馈者若指的是"背包里的枪没挂在身上"，那是上游设计，不是 bug。

### 1.2 但核查中查出一个真 bug：左利手玩家的主手枪不渲染

这个是上游就有、我们照搬过来的缺陷，**在 26.2 下依然成立**。

**上游的两处写法都把「左手」直接当成「副手」**：

```java
// ItemInHandLayerMixin（上游）
if (IGun.mainHandHoldGun(livingEntity) && arm == HumanoidArm.LEFT) ci.cancel();

// GunItemRendererWrapper#renderByItem（上游）
if (transformType == THIRD_PERSON_LEFT_HAND) return;   // 注释写的是「第三人称副手也不渲染了」
```

对左利手玩家（设置里"惯用手：左"，`getMainArm() == LEFT`）这个等式不成立：
**他的主手就是左手**。于是主手那把枪：

* 要么在 mixin 处被 `ci.cancel()` 掉，
* 要么走到 `renderByItem` 被 `return` 掉。

两条路都画不出来 —— 症状正是"第三人称看不到装备的枪"。

我们此前只修了 mixin 那一半（改用 `state.mainArm` 判定），
`renderByItem` 那一半仍是上游的 `THIRD_PERSON_LEFT_HAND` 硬判定，
所以 bug 只是从"两处触发"变成"一处触发"，并没有真正消失。

**为什么 26.2 下不能只靠 display context 区分**：
`ArmedEntityRenderState#extractArmedEntityRenderState` 的字节码（偏移 67-102）显示，
context 是**按左右手写死**的：

```
右手 -> THIRD_PERSON_RIGHT_HAND
左手 -> THIRD_PERSON_LEFT_HAND
```

与主副手无关。因此 `renderByItem` 单看 context 永远无法判断这是主手还是副手。

**修法**：`ArmedEntityRenderState` 在 26.2 明确带了 `mainArm` 字段（字节码确认），
由 mixin 在 `submitArmWithItem` 的 HEAD 置位
`GunItemRendererWrapper.IS_MAIN_HAND_SUBMIT = (arm == state.mainArm)`，
TAIL 清除；`renderByItem` 改判
`transformType == THIRD_PERSON_LEFT_HAND && !IS_MAIN_HAND_SUBMIT`。

**为什么普通 static 布尔就够，不需要 ThreadLocal**：
`ItemStackRenderState#submit` 的字节码是**同步直调**
`LayerRenderState#submit` → `SpecialModelRenderer#submit`，
中间没有任何延迟队列。也就是 `renderByItem` 就执行在 mixin 的 HEAD 与 TAIL 之间，
同一渲染线程、同一调用栈，不存在跨帧残留。

**降级安全**：mixin 若因故未应用，标志恒为 false，行为退回上游语义，不会更糟。

**未复现的部分**：右利手玩家（绝大多数）第三人称持枪渲染，
在本轮静态核查中没有发现断点 —— 链路
`extractArmedEntityRenderState → updateForLiving → ItemModel.update(tacz:dynamic_item)
→ setupSpecialModel → ItemStackRenderState#submit → renderByItem` 完整。
若反馈者不是左利手，需要更多信息（截图 / 是否装了第三人称视角 mod / 枪包）才能定位。

---

## 二、反馈② —— 工作台模型缩放：确认是移植缺陷，不是"还原度不高"

这一条比反馈者说得还严重：**不是"缩放效果差"，而是缩放按钮完全没接线**。

### 2.1 现状

移植版的 `renderLeftModel` 全部有效代码就一句：

```java
float modelScale = Math.min(scaleX, scaleY) * this.scale / 10.0f;   // 算了，但没人用
graphics.item(result, posX + width/2 - 8, posY + height/2 - 8);     // 画 16×16 物品图标
```

`modelScale` 是**死变量**，`this.scale` 字段被 `+` / `-` / `R` 三个按钮改来改去，
**从头到尾没有任何地方读它**。所以：不旋转、不缩放、只有一个指甲盖大的图标。

### 2.2 为什么当初会降级成这样

上游靠的是这一套（1.21.1）：

```java
Matrix4fStack posestack = RenderSystem.getModelViewStack();
posestack.pushMatrix();  posestack.translate(xPos, yPos, 200);
posestack.scale(scale, scale, scale);
posestack.mul(Axis.XP.rotationDegrees(15)); posestack.mul(Axis.YP.rotationDegrees(rot));
RenderSystem.applyModelViewMatrix();
itemRenderer.renderStatic(..., FIXED, ...);
bufferSource.endBatch();
```

26.2 里这套**整体不存在**：GUI 改成了 extract（收集 render state）→ 统一绘制两段式，
extract 阶段改 `RenderSystem` 的模型视图矩阵没有任何意义，
`renderStatic` / `endBatch` 也都被移除了。

### 2.3 26.2 的正解：PictureInPictureRenderer

26.2 里唯一能在 GUI 内做「带自定义投影 / 变换的 3D 绘制」的官方通道就是 PIP：
vanilla 自己的实体预览（`GuiEntityRenderer`）、超框物品（`OversizedItemRenderer`）、
书本 / 旗帜预览无一例外。它把内容画到离屏纹理，再作为一次 blit 合回 GUI。

新增两个类：

* `client/gui/preview/GunPreviewRenderState` —— `PictureInPictureRenderState` 实现；
* `client/gui/preview/GunPreviewRenderer` —— `PictureInPictureRenderer` 实现；
* 在 `TaCZFabricClient#onInitializeClient` 用 Fabric 的
  `PictureInPictureRendererRegistry.register` 注册（该类在 26.1 由
  `SpecialGuiElementRegistry` 更名而来）。

**几何参数逐项照抄上游**（不是重新设计）：

| 项 | 上游值 | 本实现 |
|---|---|---|
| 预览框 | `(leftPos+3, topPos+16)` 128×99 | 同（`x0/y0/x1/y1`） |
| 缩放基准 | `scale = 70`，`+/-` 步进 20，范围 10..200 | 同（直接传 `this.scale`） |
| 自转周期 | 8 秒 | 同 |
| 俯角 | 15° | 同 |
| 裁剪 | `RenderSystem.enableScissor` | PIP 的 `scissorArea`（与外层 scissor 取交集） |
| 模型原点 | `(leftPos+68, topPos+58)` | 用 `offsetX/offsetY = (+1, -7.5)` 从预览框中心补偿到同一点 |

**`scale()` 的语义正好对得上**：`PictureInPictureRenderer#prepare` 的字节码里是
`poseStack.scale(guiScale * scale, guiScale * scale, -(guiScale * scale))`，
即「1 个模型单位 = scale 个 GUI 像素」—— 与上游那句
`posestack.scale(scale, scale, scale)` 完全同义，所以 70 这个基准值可以原样沿用。

**刻意不覆写 `textureIsReadyToBlit`**：基类默认返回 false（每帧重画）。
预览模型一直在自转，缓存纹理会把它冻住。

---

## 三、顺带核查 GUI 还原度，又查出 4 处独立缺陷

反馈者说"还有个 GUI 问题还能接受"，核查下来其实不只是"风格不够还原"，
下面几条都是**功能性失效**。

### 3.1 过滤器面板：构造参数错位导致整个面板不可用（真因）

```java
// 26.2 AbstractSelectionList 构造签名（字节码确认）
AbstractSelectionList(Minecraft, int width, int height, int y, int itemHeight)
                                                              ^^^^^^^^^^^^^^ 每行高度
```

移植时把 `pY1`（≈ `topPos + imageHeight + 1`，两百多）当成了第 5 个参数，
于是**每一行的高度都成了两百多像素**：

* `getNextY()` 逐行累加行高 → 第 2 行往后的 y 直接飞出可视区；
* `extractListItems` 有 `entry.getY() > getBottom() 就跳过` 的剔除 → 除第一行外全不绘制；
* `Entry#extractContent` 又会把自己的 x/y 同步给内部 widget → 点击热区一起跑偏，勾选框点不中。

**这才是"过滤器功能无实际作用"的直接原因**，与过滤逻辑本身无关
（`classifyRecipes` 里的 `namespaceList()` / `getSearchText()` / `isByHandSelected()`
三条过滤链路与上游逐行等价，本身是对的）。

改为传 `pItemHeight`（调用方给的 15，与上游一致）。

### 3.2 过滤器勾选框：贴图路径在 26.2 已不存在

原代码用 `textures/gui/checkbox.png` 这张整图手切 UV（0/10 两档）。
该文件在 26.2 jar 内**不存在** —— 勾选框改成了 GUI sprite atlas 里的四张独立精灵：

```
widget/checkbox
widget/checkbox_highlighted
widget/checkbox_selected
widget/checkbox_selected_highlighted
```

缺图 → 取到 missing texture → **打勾与不打勾视觉上无法区分**。
改用 `blitSprite` + 四张精灵，选取逻辑与 vanilla `Checkbox#extractContents` 字节码逐条一致。

### 3.3 又两处 alpha = 0 文字不显示（`PORTING_NOTES.md §1` 的第 5、6 例）

| 位置 | 原值 | 症状 |
|---|---|---|
| `GunPackList.Checkbox` 标签 | `14737632` = `0x00E0E0E0` | 勾选框旁边的**枪包名一个字都不显示** |
| `GunPackProgressScreen` 标题与进度 | `16777215` = `0x00FFFFFF` | **枪包加载进度界面全空白** |

十进制写法把这两处从之前的排查网里漏掉了 —— 之前的 grep 只扫了 `0x` 开头的六位色。
已补 alpha 并在本文记录：**以后排查要连十进制字面量一起扫**。

### 3.4 HUD 版本号溢出（反馈者点名的那处）

上游写死 0.5 倍字号、从 `x = width - 70` 起画，因为它的串是
`"1.21.1-1.1.8"`（≈56 字体像素 → 屏上 28px），70px 余量绰绰有余。

本移植的版本号带 SemVer 构建元数据：
`"26.2-1.1.8+fabric.26.2.Beta-2"` ≈146 字体像素 → **0.5 倍下仍占 73px > 70px**，
直接顶出屏幕右边缘。反馈者说"这个甚至没做防溢出"，属实。

改为按可用宽度**自适应字号**（0.5 为上限，下限 0.25），
缩到下限仍放不下时才截断加省略号 —— 优先保证版本号可读（利于反馈问题），
同时保证任何情况下都不会画出屏幕。

---

## 四、改动清单

| 文件 | 改动 |
|---|---|
| `client/gui/preview/GunPreviewRenderState.java` | **新增** —— 工作台预览的 PIP render state |
| `client/gui/preview/GunPreviewRenderer.java` | **新增** —— 对应的 PIP 渲染器 |
| `client/gui/GunSmithTableScreen.java` | `renderLeftModel` 改走 PIP，恢复缩放/自转 |
| `cn/sh1rocu/tacz/client/TaCZFabricClient.java` | 注册 `GunPreviewRenderer` |
| `client/gui/components/GunPackList.java` | 行高参数错位；勾选框改用 sprite；标签补 alpha |
| `client/gui/GunPackProgressScreen.java` | 两处文字补 alpha |
| `client/gui/overlay/GunHudOverlay.java` | 版本号自适应字号 + 兜底截断 |
| `client/renderer/item/GunItemRendererWrapper.java` | 新增 `IS_MAIN_HAND_SUBMIT`；副手判定改用主副手 |
| `mixin/client/ItemInHandLayerMixin.java` | 置位/清除 `IS_MAIN_HAND_SUBMIT` |

---

## 五、待实机验证（本环境无 JDK / 无外网 Maven，未能编译）

> 沙箱内 `gradlew` 无法下载 Gradle 发行版与 Fabric 依赖（除 GitHub 外均不可达），
> 因此本轮全部改动**只做了逐符号的字节码核对，未经编译与实机验证**。
> 所用到的每一个 26.2 API（`PictureInPictureRenderer` 的四个抽象/可覆写方法、
> `PictureInPictureRenderState` 的六个抽象方法与静态 `getBounds`、
> `GuiGraphicsExtractor.scissorStack` / `guiRenderState` 的可见性、
> `ItemModelResolver#updateForTopItem`、`ItemStackRenderState#submit`、
> `AbstractSelectionList` 构造签名、`Font#plainSubstrByWidth`、
> `Lighting.Entry.ITEMS_3D`、四张 checkbox 精灵）
> 均已在 jar 内逐条确认存在且签名匹配。

- [ ] 工作台：`+` / `-` / `R` 三键确实改变预览大小；模型自转；不越出预览框
- [ ] 工作台：切换配方后预览跟着换；关掉再开不残留上一把
- [ ] 过滤器：面板能列出全部枪包、可滚动、勾选框能点中且勾/不勾可区分、枪包名可见
- [ ] 过滤器：按名称搜索、"仅主手可用"筛选生效
- [ ] 枪包加载界面：标题与百分比可见
- [ ] HUD：默认分辨率与 GUI 缩放 1/2/3/4 下版本号都不越出右边缘
- [ ] **左利手**（设置 → 惯用手 → 左）第三人称：主手枪正常渲染，副手枪仍为背挂姿态
- [ ] 右利手第三人称：主手枪、副手背挂枪、快捷栏第 1 格背挂枪，三者均与本轮改动前一致（无回归）
