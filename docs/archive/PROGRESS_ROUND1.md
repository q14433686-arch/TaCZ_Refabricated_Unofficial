# 进度报告 · 第 1 轮（2026-07-25）

环境：Temurin JDK 25.0.3 + Gradle 9.5.1 + Fabric Loom 1.17.17 + MC 26.2
对照源：`minecraft-merged-deobf-26.2.jar` 经 Vineflower 1.11.1 反编译（下称"26.2 反编译源"）
上游对照：`Sh1roCu/TACZ-Refabricated` 分支 `1.21.1`

---

## 0. 环境搭建结果

| 项 | 结果 |
|---|---|
| 仓库 clone | OK（9,259 文件，506 MB） |
| 沙盒自带 JDK | **仅 JDK 11**，Gradle 9.5.1 无法运行 → 已自行安装 Temurin 25.0.3 |
| `./gradlew` 可执行位 | 仍缺失（须 `bash gradlew`），与审计报告 §1.2 描述一致，**属实** |
| `compileJava` | PASS |
| `build`（含 jar） | PASS，产物 `TACZ-Refabricated-26.2-0.0.0-26.2-audit.jar` 57.2 MB |
| 反编译对照 | 已产出 `net.minecraft.client.gui.**`、`renderer.item.**`、`ItemInHandRenderer`、`resources.model.**` |

---

## 1. 文档核查：哪些说法属实、哪些不属实

读了 `AUDIT_REPORT_2026-07-25.md`、`STAGE1_COMPLETION_REPORT.md`、`FIX_LOG_STAGE1.md`、`FIX_SUMMARY_QUICK.md`。

### 属实

| 文档说法 | 核实方式 | 结论 |
|---|---|---|
| `gradlew` 缺可执行位 | `ls -l gradlew` | ✅ 属实 |
| 26.2 `BlockEntityRendererRegistry.register(BlockEntityType, Provider)` 存在 | `javap` | ✅ 属实 |
| 26.2 `ItemInHandRenderer.submitHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)` 存在 | 反编译源 L352 | ✅ 属实，mixin 目标签名可加载 |
| `HumanoidOffhandRender.renderGun` 是空实现 TODO | 读源码 | ✅ 属实 |
| 资源已补入（PNG/OGG 非空） | 清点 | ✅ 属实 |

### 不属实 / 与事实冲突（本轮新发现）

| 文档说法 | 实际情况 |
|---|---|
| `STAGE1_COMPLETION_REPORT.md`：把"第一人称看不见枪"列为待查，怀疑 `TaczDynamicItemModel` / `ItemInHandLayer` 提交逻辑 | **方向错误**。提交链路本身是通的；真正原因是 **item 模型 JSON 的 display transform**（见 §2.2）。与代码逻辑无关 |
| `FIX_SUMMARY_QUICK.md`：`AttachmentRender`、`ShellRender`、`MuzzleFlashRender` 已完成 collector 适配 | 与同仓 `AUDIT_REPORT` §2.B 自相矛盾（后者称其为 no-op/TODO）。两份文档结论互斥，**不可同时为真**，本轮暂未逐一定级 |
| `AUDIT_REPORT` §2.B：称"第一人称枪代码路径存在，待实机验收" | 路径存在属实，但**存在已知缺陷**，并非仅"待验收"（见 §2.2） |
| `FirstPersonRenderEvent.onRenderHand` 相关注释暗示其为第一人称入口 | **已是死代码**。`TaCZFabricClient.java:81` 该注册被注释掉，SBM `RenderHandEvent` 无任何 fire 点。实际入口是 `AnimateGeoItemRenderer#render` 的 `mode.firstPerson()` 分支 |

---

## 2. 本轮定位并修复的两个问题

### 2.1 【问题一】工作方块 UI 与槽位整体错位 —— 已修复

**根因（经 26.2 反编译源确认）**

1.21.1 的 `AbstractContainerScreen#renderBg` 运行在**未平移**坐标系，所以旧代码里 `gui.blit(..., leftPos, topPos, ...)` 自带偏移是正确的。

26.2 把渲染拆成了两个阶段（`Screen#extractRenderStateWithTooltipAndSubtitles`，反编译源 L116-120）：

```java
graphics.nextStratum();
this.extractBackground(graphics, mouseX, mouseY, a);   // 未平移
graphics.nextStratum();
this.extractRenderState(graphics, mouseX, mouseY, a);
```

而 `AbstractContainerScreen#extractContents`（反编译源 L99-115）**内部先做了平移**：

```java
public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
   int xo = this.leftPos;
   int yo = this.topPos;
   super.extractRenderState(graphics, mouseX, mouseY, a);
   graphics.pose().pushMatrix();
   graphics.pose().translate(xo, yo);      // ← 关键
   this.extractLabels(...);
   this.extractSlotHighlightBack(graphics);
   this.extractSlots(graphics, mouseX, mouseY);   // 槽位在此坐标系内绘制
   this.extractSlotHighlightFront(graphics);
   graphics.pose().popMatrix();
}
```

本 mod 的 `GunSmithTableScreen` 覆写了 `extractContents` 并在里面用 `leftPos/topPos` 画背景：

```java
// 修复前
public void extractContents(GuiGraphicsExtractor gui, ...) {
    gui.blit(..., SIDE, leftPos, topPos, ...);          // 平移后再加一次 → 偏移翻倍
    gui.blit(..., TEXTURE, leftPos + 136, topPos + 27, ...);
}
```

于是背景被画在 `(2*leftPos, 2*topPos)`，而槽位/高亮/tooltip 命中判定仍按真实 `leftPos/topPos` 走 → **背景与槽位、文字、按钮系统性错位**，且窗口越大偏移越大。这与用户描述的"功能 UI 和 UI 内槽位 UI 都是错位的"完全吻合。

**交叉验证**：反编译源里 **20 个**原版容器界面（`ContainerScreen`、`CraftingScreen`、`AbstractFurnaceScreen`、`ItemCombinerScreen`、`BrewingStandScreen`、`HopperScreen`、`LoomScreen`、`EnchantmentScreen`…）**无一例外**都在 `extractBackground` 里画背景，没有任何一个在 `extractContents` 里画。

**修复**：`GunSmithTableScreen.java` —— 把背景绘制从 `extractContents` 迁到 `extractBackground`，并调用 `super`。坐标值保持 `leftPos/topPos` 不变（因为 `extractBackground` 不平移，与 1.21.1 语义一致）。已附详细对齐注释。

**顺带核实**：`GunRefitScreen` 继承的是普通 `Screen` 而非 `AbstractContainerScreen`，不经过该平移，无需改动；各 Button 子类覆写 `extractContents` 是**正确**的 —— `AbstractButton#extractWidgetRenderState` 确实是 `final` 并转发到抽象的 `extractContents`（反编译源已确认），这部分写法无误。

---

### 2.2 【问题二】第一人称手持枪械与摄像机视角错位 —— 已修复

**先纠正一个前提**：用户怀疑第一人称用了低精度 LOD 模型。**经确认不是。**
- `GunItemRendererWrapper#renderFirstPerson`（L168+）直接用 `display.getGunModel()`，**根本不查 LOD**；
- LOD 只在 `renderByItem`（第三人称/掉落物）里通过 `RenderDistance.inRenderHighPolyModelDistance` 选择，且 `AttachmentRender` 等处的 LOD 分支都显式带 `&& !transformType.firstPerson()` 排除第一人称。

所以"低精度模型"不是原因。真正原因如下。

**根因（经 26.2 反编译源 + 上游 1.21.1 对照确认）**

26.2 中 `ItemStackRenderState.LayerRenderState#submit`（反编译源）：

```java
private void submit(PoseStack poseStack, SubmitNodeCollector c, int light, int overlay, int outline) {
   poseStack.pushPose();
   this.applyTransform(poseStack.last());          // ← 先套用 display transform
   if (this.specialRenderer != null) {
      this.specialRenderer.submit(argumentForSpecialRendering, poseStack, c, ...);  // TACZ 在这里渲染
   }
   ...
}

private void applyTransform(Pose localPose) {
   this.itemTransform.apply(displayContext.leftHand(), localPose);   // 来自模型 JSON 的 display
   localPose.mulPose(this.localTransform);
}
```

即 **模型 JSON 的 `display` transform 会在 TACZ 自己的 special renderer 之前被叠加进 PoseStack**。

而 `ModelRenderProperties.fromResolvedModel` → `ResolvedModel.getTopTransforms()` → `findTopTransform` 会**沿 parent 链向上继承** transform（反编译源 L105-119）。

对照上游 1.21.1：

| 模型 | 上游 1.21.1 | 本仓库（移植后） |
|---|---|---|
| `models/item/modern_kinetic_gun.json` | `"parent": "builtin/entity"` | `"parent": "minecraft:item/generated"` |
| `gun_smith_table` / `workbench_a/b/c` | `"parent": "builtin/entity"` | `"parent": "minecraft:item/generated"` |
| `ammo` / `attachment` | `"parent": "builtin/entity"` + 自带 display | `"parent": "minecraft:item/generated"` + 自带 display |

`builtin/entity` **不携带任何 display transform**（26.2 已移除该 builtin，jar 内 `models/builtin/*` 数量为 0）。
但 `minecraft:item/generated` 携带（实测自 26.2 client jar）：

```json
"firstperson_righthand": { "rotation": [0,-90,25], "translation": [1.13,3.2,1.13], "scale": [0.68,0.68,0.68] },
"thirdperson_righthand": { "translation": [0,3,1], "scale": [0.55,0.55,0.55] },
"ground": {...}, "head": {...}, "fixed": {...}
```

于是第一人称链路变成：

```
vanilla 手臂变换
  → item/generated 的 firstperson_righthand（旋转 -90° / 25°、位移 1.13,3.2,1.13、缩放 0.68）  ← 多余
  → TACZ renderFirstPerson 自己的摄像机/持枪姿态变换
```

**两套第一人称定位被串联**，模型被额外旋转近 90° 并平移缩放 → 表现正是用户说的"不是消失，只是严重和摄影机视角错位"。`modern_kinetic_gun.json` 自身没有 `display` 块，完全继承了 vanilla 的这套偏移，因此枪械受影响最严重。

**修复**：把这 7 个 item 模型改为**无 parent**（26.2 中 `ModelDiscovery#isRoot` 判定 `parent()==null` 即为合法根模型，反编译源已确认），从而复现 `builtin/entity` 的恒等变换语义：
- `modern_kinetic_gun` / `gun_smith_table` / `workbench_a` / `workbench_b` / `workbench_c`：无 parent、无 display；
- `ammo` / `attachment`：无 parent，但**保留上游原本就显式声明的 display 块**（与上游逐字段比对一致）；
- `layer0` 改为 `particle`（无 parent 时不再走 `generated` 的 layer 展开，`particle` 才是 `resolveParticleMaterial` 实际读取的槽位）。

---

## 3. 构建与验证状态

| 检查 | 状态 |
|---|---|
| `compileJava` | ✅ PASS |
| `build`（jar 打包） | ✅ PASS |
| 产物内 7 个 item JSON parent/display 校验 | ✅ 全部 `parent=None`，display 与上游一致 |
| 实机画面验收 | ❌ **未做** —— 沙盒 2 GB 内存 + 无 GPU，无法起客户端到主菜单 |

> 声明：本轮结论全部基于**反编译源码比对 + 上游逐字段对照 + 构建产物校验**，未经实机画面确认。修复方向有明确的反编译证据支撑，但最终效果仍需真实 GPU 双端环境验收。

---

## 4. TODO

### P0 · 需你实机验收本轮修复
- [ ] 打开三种工作台，确认背景与槽位/文字/按钮对齐、tooltip 命中正确
- [ ] 不同窗口尺寸/GUI Scale 下复测（该 bug 随尺寸放大，是关键判据）
- [ ] 第一人称持枪，确认模型回到准星前方、瞄准（ADS）对位正确
- [ ] 第三人称、掉落物、物品栏、头部（head）四个 context 回归，确认改 parent 未引入新偏移
- [ ] 配件 / 弹药 的第一人称与第三人称对位

### P1 · 已定位、下一轮处理
- [ ] `HumanoidOffhandRender.renderGun` 空实现 + `ItemInHandLayerMixin` 取消 vanilla 左手 → 第三人称持枪手臂消失。需按 26.2 `ItemInHandLayer#submitArmWithItem` 签名重写或撤销 cancel
- [ ] 清理死代码：`FirstPersonRenderEvent.onRenderHand` 与 SBM `RenderHandEvent` stub 已无 fire 点，保留会持续误导排查
- [ ] 裁决 `FIX_SUMMARY_QUICK.md` 与 `AUDIT_REPORT` §2.B 关于 `ShellRender`/`MuzzleFlashRender`/`AttachmentRender`/`BeamRenderer` 适配状态的**互斥结论**，逐个 `BedrockModel.render` 调用点定级
- [ ] 逐一核对其余 GUI 组件（`GunPackList`、`HSVSliderGroup`、`GunPropertyDiagrams`、各 overlay）在 26.2 分层坐标系下的偏移

### P2 · 工程卫生
- [ ] 修 `gradlew` 可执行位
- [ ] 补 README / LICENSE；删除 `build.gradle` 中指向不存在文档的注释
- [ ] 加 GameTest：断言 item 模型 display transform 为恒等（防止本轮修复被回改）
