# 进度报告 · 第 3 轮（实机反馈修复）2026-07-25

基于你的实机反馈。对照源仍为 26.2 反编译 + 上游 1.21.1，另外这轮新增了一个关键对照源：
**SimpleBedrockModel-Fabric 本体源码**（`Sh1roCu/SimpleBedrockModel-Fabric`）——
它决定了上游第一人称渲染的注入点，是本轮 #2/#3 的破案关键。

---

## 逐条回应你的五点

### ① 工作台界面不显示任何物品贴图 —— 已修（与第 1 轮的错位是两个独立问题）

**先确认一件事**：从你的截图看，**第 1 轮的 UI 错位修复是成功的** ——
背景、槽位框、标签页、Craft 按钮、滚动条现在都对齐了，"手枪"tab 的 tooltip 也正常。
剩下的"图标空白"是<b>另一个独立缺陷</b>，一直存在，只是之前被错位掩盖了。

**根因**（26.2 反编译）：26.2 的 GUI 物品渲染有两条路径，由包围盒大小决定：

```java
// GuiItemRenderState 构造函数
oversizedItemBounds = itemStackRenderState.isOversizedInGui()
        ? calculateOversizedItemBounds() : null;

// calculateOversizedItemBounds()
AABB aabb = itemStackRenderState.getModelBoundingBox();   // ← 来自 visitExtents
int actualXSize = Mth.ceil(aabb.getXsize() * 16.0);
int actualYSize = Mth.ceil(aabb.getYsize() * 16.0);
if (actualXSize <= 16 && actualYSize <= 16) return null;  // 普通 GuiItemAtlas 路径
else ...                                                   // OversizedItemRenderer（PIP 离屏渲染）
```

而 `TaczDynamicItemModel.EXTENTS` 被写死为 **±1.5** → 包围盒边长 3.0 → `3.0 × 16 = 48 px ≫ 16`，
于是**所有 TACZ 物品都被判定为 oversized**，强制走 `OversizedItemRenderer` 的 PIP 离屏路径。
该路径按 48px 包围盒布局，而 TACZ 的 slot 贴图实际只有 1 格（16px），
最终在 16×16 槽位里被缩放/偏移到看不见的位置 —— 就是你看到的全空白。

**修复**：`EXTENTS` 改为 ±0.5（边长 1.0 → 正好 16px），走与原版物品一致的 `GuiItemAtlas` 路径。
这也与 TACZ 自己的 `renderSlotTexture`（画一个 1×1 格四边形）语义吻合。

> 补充：`items/*.json` 里的 `"oversized_in_gui": true` 只是"允许超框"，
> 真正决定走哪条路径的是包围盒尺寸，两者不是一回事。

---

### ② 第一人称枪械相对摄像机位置/大小不对（开镜尤其明显）—— 已修

**你的判断是对的，而且这正是关键线索。** 但根因不在"相对位置信息"数据，而在**注入点**。

我拉了 SimpleBedrockModel-Fabric 的实际源码，它的 mixin 是：

```java
@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
private void sbm$renderHand(...) {
    var event = new RenderHandEvent(...);
    RenderHandEvent.EVENT.invoker().post(event);
    if (event.isCanceled()) ci.cancel();     // ← HEAD 拦截并取消
}
```

也就是说上游 TACZ 拿到的 PoseStack 是**只经过视角回摆、尚未经过任何手臂变换**的干净矩阵。

而 26.2 移植改走了客户端 ItemModel 路径，渲染发生在 `renderItem(...)` 内部 ——
那时 vanilla 已经额外施加了（反编译 `ItemInHandRenderer#submitArmWithItem` 确认）：

```java
this.applyItemArmTransform(poseStack, arm, inverseArmHeight);
// = poseStack.translate(invert * 0.56F, -0.52F + inverseArmHeight * -0.6F, -0.72F);
```

**多出来的 `translate(±0.56, −0.52, −0.72)` 就是位置偏移的直接来源**，
且 ADS 时因为瞄准定位组要求精确对齐摄像机光轴，这个固定偏移会被放大得最明显 ——
与你观察到的"机瞄开镜时尤为明显"完全吻合。

**修复**：在 `submitArmWithItem` 的 HEAD 拦截并 `ci.cancel()`，直接调用 TACZ 的第一人称渲染，
注入点与取消语义**与 SBM 完全一致**，从而拿回与 1.21.1 相同语义的干净 PoseStack。
`AnimateGeoItemRenderer#render` 的 firstPerson 分支同步改为直接 return，避免双重渲染。

---

### ③ 持械奔跑/移动时手和枪一同抖动、动画不连贯 —— 同一个根因，已一并修复

同样出自上面那段 vanilla 代码，紧跟在 `applyItemArmTransform` 之后：

```java
} else {
    this.applyItemArmTransform(poseStack, arm, inverseArmHeight);
    switch (itemStack.getSwingAnimation().type()) {
        case 2: this.swingArm(attack, poseStack, invert, arm); break;          // ← 挥动动画
        case 3: SpearAnimations.firstPersonAttack(attack, poseStack, invert, arm);
    }
}
```

vanilla 的 `swingArm` 挥动动画 + `inverseArmHeight` 装备抬手动画，
与 TACZ 自己的动画状态机（走路摇摆、后坐力、收放枪）**同时作用在同一个矩阵上**，
两套动画互相打架 → 抖动、不连贯。上游因为在 HEAD 就 cancel 了，根本不会执行到这些。

修复方式同 ②：HEAD 拦截取消，这些 vanilla 动画不再叠加。

---

### ④ 左手能"拿枪"但无法用副手使用任何功能 —— 这是上游设计，非移植缺陷

核实结论：**TACZ 从设计上就不支持副手开枪**，不是 26.2 移植弄坏的。

全部输入按键的门禁都是 `IGun.mainHandHoldGun(player)`（只看主手）：

| 文件 | 门禁 |
|---|---|
| `ShootKey` | `player.getMainHandItem()` |
| `AimKey` / `ReloadKey` / `FireSelectKey` / `InteractKey` / `RefitKey` / `CrawlKey` | `IGun.mainHandHoldGun(player)` |

全仓 `OFF_HAND` 的业务用法只有一处 KubeJS 事件常量，没有任何副手开火/换弹/瞄准逻辑。
上游 1.21.1 的 `ShootKey` 同样只读 `getMainHandItem()`。

所以副手枪的定位就是"**可以拿着、但只作为背挂展示**"，这也正是
`HumanoidOffhandRender`（第 2 轮我实现的那个）存在的意义。

> 如果你**希望**副手能开枪，那是一个新功能需求（需要改所有输入门禁 + 网络包带 hand 参数 +
> 动画状态机支持双持），不是 bug 修复。要做的话我可以单独评估，但会是较大改动。

---

### ⑤ 装配台手持模型太大 —— 已修（这是我第 1 轮引入的回归，向你确认一下）

**先说明**：这一条我要更正第 1 轮的一个判断。我当时把 5 个方块物品模型的 parent 一起去掉了，
理由是"上游用 builtin/entity（无 transform）"。这对**枪械**是对的，但对**工作台**是错的 ——
工作台走的是另一条渲染链，它的缩放来自**枪包数据**而不是模型 JSON。

**真正的根因**（上游 vs 本仓库逐行对照）：

上游 `GunSmithTableItemRenderer#renderByItem` 有这么一段：

```java
ItemTransforms transforms = index.getTransforms();
if (transforms != null) {
    poseStack.translate(0.5F, 0.5F, 0.5F);
    transforms.getTransform(transformType).apply(false, poseStack);
    poseStack.translate(-0.5F, -0.5F, -0.5F);
}
```

而移植版**把这段整个删掉了**，连带 `ClientBlockIndex` 里的 `checkTransforms(...)` 和
`getTransforms()` 也一并删除，`BlockDisplay.transforms` 的类型从 `ItemTransforms`
退化成了裸 `JsonObject`（因为 26.2 的 `ItemTransform.Deserializer` 变成 protected 内部类、
`ItemTransforms` 也不再暴露公开 Codec，移植时大概是嫌麻烦就放弃了）。

默认包 `display/blocks/gun_smith_table.json` 里明确声明：
```json
"firstperson_righthand": { "rotation": [0, 90, 0], "scale": [0.25, 0.25, 0.25] },
"thirdperson_righthand": { "rotation": [0,90,0], "translation": [0,0.25,-2], "scale": [0.24,0.24,0.24] },
```
**scale 0.25 完全没被应用** → 模型按方块原始尺寸（1m³）渲染 → **正好大 4 倍**，与你看到的一致。

**修复**：
1. 新增 `BlockTransformParser`，按 26.2 反编译的 `ItemTransform.Deserializer` **逐行语义**
   重实现解析（translation ×0.0625 后 clamp ±5、scale clamp ±4、缺省 rotation/translation=0 scale=1）；
2. `ClientBlockIndex` 恢复 `checkTransforms` / `getTransforms`；
3. `GunSmithTableItemRenderer` 恢复应用 transforms。

**26.2 的三处必要差异**（均经反编译确认）：
- `ItemTransform#apply` 第二参数是 `PoseStack.Pose` 而非 `PoseStack`；
- `apply` 内部已自带 `translate(-0.5,-0.5,-0.5)`，调用方不再补最后那次；
- 左手上下文传 `applyLeftHandFix=true`（上游硬编码 `false`，左手镜像其实是错的，这里顺手修正）。

另一处刻意差异：上游用 `Preconditions.checkArgument(transforms != null)` 硬性要求枪包提供
transforms，缺失即整个 index 加载失败。我改成回退 `NO_TRANSFORMS`，
避免第三方枪包因缺该字段而整包加载不出来。

---

## 构建验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| mixin 目标签名 | ✅ `submitArmWithItem(AbstractClientPlayer,float,float,InteractionHand,float,ItemStack,float,PoseStack,SubmitNodeCollector,int)` 与反编译完全一致 |
| 字节码引用 | ✅ `ItemTransform.apply(ZLPoseStack$Pose;)V`、`ClientBlockIndex.getTransforms`、`renderFirstPerson` 全部解析到真实签名 |
| 实机画面 | ❌ 仍未做（沙盒无 GPU） |

---

## 本轮 TODO / 验收重点

- [ ] **工作台图标**是否显示（①）
- [ ] **第一人称枪械位置/缩放**，特别是**机瞄 ADS 对位**（②）——这是最关键的判据
- [ ] **奔跑/移动时**手与枪是否还抖动、动画是否连贯（③）
- [ ] **装配台/工作台手持模型大小**是否正常了（⑤）
- [ ] 回归：切枪动画、开火后坐力、抛壳/枪焰是否仍正常（②③ 改了注入点，需确认没打坏已经好的部分）
- [ ] 回归：第三人称持枪、背挂枪显示是否仍正常

## 已知仍未解决

- 瞄具 stencil/PIP：镜内仍会看到枪体
- 副手开枪：上游即不支持，如需为新功能
- 一批 compat（Iris/ImmediatelyFast/Shoulder Surfing 等）仍是 no-op
