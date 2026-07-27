# 进度报告 · 第 4 轮（实机反馈修复）2026-07-25

基线：`tacz-26.2-r3-src.zip`。对照源：26.2 反编译 + 上游 1.21.1。

> 本轮四个问题**全部定位到根因并修复**，且三个根因是同一类 26.2 破坏性变更的不同表现。
> 仍然**未实机验收**（沙盒无 GPU）。

---

## ① 第三人称多出残缺手臂（切回第一人称仍在，换非枪械物品才消失）

**根因**：反编译 `AvatarRenderer#renderHand` 发现它会**直接改写共享的 `PlayerModel`**，
然后才提交（`submitModelPart` 是**延迟**绘制）：

```java
arm.resetPose();
arm.visible = true;
model.leftSleeve.visible  = hasSleeve;
model.rightSleeve.visible = hasSleeve;
model.leftArm.zRot  = -0.1F;
model.rightArm.zRot =  0.1F;
submitNodeCollector.submitModelPart(arm, ...);
```

`EntityRenderDispatcher#getPlayerRenderer` 返回的是**全局唯一**的 `AvatarRenderer`，
其 `PlayerModel` 被第一人称手臂与第三人称玩家实体**共用**。上述写操作立即生效且**从不还原**，
于是 `arm.visible=true`、`zRot=±0.1`、袖子可见性会**污染第三人称玩家实体**的渲染 ——
就是你看到的那条姿态错误、残缺的手臂。

这也完美解释了你观察到的两个现象：
- **为什么切回第一人称还在**：状态被"粘"在共享模型上，没人还原；
- **为什么换成非枪械物品就立刻消失**：不再走 TACZ 这条渲染路径，vanilla 每帧
  `setupAnim` 会重新写回正常姿态。

**修复**：`RenderHelper#renderFirstPersonArm` 对被写入的字段做快照 + `finally` 还原。
手臂自身的提交已完成，还原只影响后续复用，不改变本次外观。

> 与"第三人称动画兼容（PAL）"**无关**，是纯粹的共享状态泄漏。

---

## ② 陆地行走/奔跑剧烈抖动（游泳、飞行、边跳边走都不抖）

**根因**：上游 1.21.1 的行走距离是**手动插值**的：

```java
entity.walkDist + (entity.walkDist - entity.walkDistO) * partialTicks
```

1.21.2+ 把 `walkDist/walkDistO` 收进了 `WalkAnimationState`。移植时改成了
`livingEntity.walkAnimation.position()` —— **无参重载返回的是未插值、每游戏刻（20Hz）
才更新一次的原始值**。而渲染按帧跑（60~144Hz），于是行走动画的驱动量变成**阶梯状跳变**。

**你提供的"游泳/飞行/边跳边走不抖"这条线索是决定性的**：那些状态下脚不沾地，
`WalkAnimationState#update` 传入的 speed 近 0，`position` 几乎不变，自然没有阶梯跳变 ——
完全吻合这个根因，也排除了"bobView 未取消"等其它猜想。

**修复**：改用 26.2 提供的带参重载 `position(float partialTick)`（javap 已确认存在），
语义与上游手动插值一致。`anchorWalkDist()` 与 `getWalkDist()` 两处都已改。

---

## ③④ 物品无说明文字 + 物品栏图标空白

这两个是**不同根因**，但都源于 26.2 移除了 1.21.x 的隐式兜底。

### ③ 说明文字缺失 —— 26.2 静默丢弃 alpha==0 的文本

反编译 `GuiGraphicsExtractor#text`：

```java
public void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean dropShadow) {
   if (ARGB.alpha(color) != 0) {          // ← 关键
      this.guiRenderState.addText(...);
   }
}
```

1.21.x 的 `GuiGraphics#drawString` 会在 alpha 为 0 时**自动补成不透明**，所以历史代码里
大量使用 6 位 RGB 字面量（`0x777777`、`0xaaaaaa`…）一直是安全的。
26.2 移除了该兜底 → 这些文本 alpha=0 → **被整段丢弃**。

这精确解释了你截图里的现象：tooltip **外框和标题正常**（标题走 vanilla 的
`ClientTextTooltip`，用的是 `-1` 即不透明白色），但**正文一片空白**，而框的尺寸又是对的
（`getHeight/getWidth` 照常计算，只是文字没画出来）—— 也就是你说的"大小被硬编码了"的错觉来源。

**修复**：批量为 `text()/centeredText()` 调用中的 6 位色值补 `0xFF` 前缀，
共 **43 处 / 10 个文件**（tooltip、工作台界面、按钮、JEI 类目等）。
用 AST 感知的脚本只改这两个方法的实参，不碰字符串字面量与其它常量。

### ④ 物品栏图标空白 —— GUI 图标缓存 key 每帧失效

`GuiItemAtlas#getOrUpdate` 用 `TrackingItemStackRenderState#getModelIdentity()`
（本质是 `modelIdentityElements` 这个 `List`）作为 key 去 `DynamicAtlasAllocator`
分配/复用图标槽位，依赖 `List.equals` → 逐元素 `equals`。

原先 `TaczDynamicItemModel#update` 把 `RenderArgument` 整个塞进了 identity。
record 的 `equals` 会逐字段比较，而 **`ItemStack` 在 26.2 中没有覆写 `equals/hashCode`**
（javap 确认：只有静态的 `ItemStack.matches`），走对象身份比较；
上面又是 `stack.copy()` —— **每帧都是新对象**。

结果 identity 每帧都不相等 → atlas 认为每帧都是全新物品 → 不断重新分配槽位、
反复 clear/重画 → **图标一片空白**。

**修复**：identity 只放具备**值语义**且真正影响图标外观的量：
`Item` 单例 + `ItemDisplayContext` + gun/attachment/ammo/block 的 `Identifier`。
刻意**不**含 `ItemStack` 本身与弹药数等高频字段（GUI 图标走的是 slot 贴图，与弹药数无关；
纳入会再次导致每帧失效）。

> 注：第 3 轮把 `EXTENTS` 从 ±1.5 改成 ±0.5 让物品走回了正常的 `GuiItemAtlas` 路径
> （不再误入 OversizedItemRenderer 的 PIP 离屏路径），那一步是必要的但不充分 ——
> 进了正确路径后，缓存 key 失效才暴露出来。两处修复叠加才完整。

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| ① 字节码含 `storePose`/`loadPose` 还原 | ✅ 6 处 |
| ② 字节码调用 `WalkAnimationState.position:(F)F`（带参插值版） | ✅ |
| ③ 色值常量变为负数（`-5592406`=0xFFAAAAAA、`-8947849`=0xFF777777） | ✅ |
| ④ 字节码含 `identityKeyOf` + `List.of` | ✅ |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

---

## 验收重点

- [ ] 持枪切第三人称：**不应**再出现多余/残缺手臂；来回切视角、切枪多次复测
- [ ] 陆地行走/奔跑：抖动应消失，动画连贯；再回归游泳/飞行/跳跃仍正常
- [ ] 枪械/配件/弹药的 tooltip：**说明文字应完整显示**（伤害、等级、类型、包名等）
- [ ] 物品栏/创造栏：枪械、配件图标**应正常显示**（不再空白）
- [ ] 回归：第一人称对位、ADS、开火动画、抛壳/枪焰、工作台 UI 与手持大小

## 仍未解决

- 瞄具 stencil/PIP：镜内仍会看到枪体
- 副手开枪：上游即不支持（第 3 轮已说明），属新功能
- 一批 compat（Iris/ImmediatelyFast/Shoulder Surfing 等）仍是 no-op
