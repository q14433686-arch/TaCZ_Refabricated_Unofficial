# 进度报告 · 第 5 轮 2026-07-25

基线：`tacz-26.2-r4-src.zip`。对照：26.2 反编译 + 上游 1.21.1。

> **先认错**：第 4 轮我对①和②的判断有误，其中①的"修复"是**倒退**，让症状更明显。
> 你这次补充的两条细节直接推翻了它们，非常关键。

---

## ① 第三人称残缺手臂 —— 已修（含撤销 r4 的错误修复）

你的更正："**第三人称本身就会触发**，只是第一人称能截获并持久化"。
这否定了我 r4 的假设（我当时以为是第一人称渲染污染了第三人称）。

### 根因 A：r4 的"还原 PlayerModel"本身就是 bug（已撤销）

反编译 `SubmitNodeCollection#submitModel`：

```java
Pose pose = poseStack.last().copy();                        // 只拷贝【矩阵】
Submit<S> submit = new Submit(renderType, pose, model, ...);  // model 是【引用】
```

而 `submitModelPart` 内部是 `new Model.Simple(modelPart, ...)`，持有**活的 ModelPart 根引用**；
真正遍历顶点发生在稍后的 `FeatureRenderDispatcher#renderAllFeatures`。

**即：矩阵被快照了，骨骼姿态没有。** r4 在 submit 之后立刻把 `arm.visible`/`zRot`/pose
还原回去，等到真正绘制时读到的就是**被还原后的错误状态** —— 这正是"手臂残缺"的直接来源，
也解释了为什么 r4 之后症状更明显。已完全撤销，恢复 vanilla 语义（写完即走）。

### 根因 B：TACZ 在第三人称下仍进入了第一人称手臂渲染（真正触发点）

`ItemInHandRenderer` 实例是**全局共享**的。`GameRenderer#renderItemInHand` 虽有
`isFirstPerson()` 门禁，但第三人称视角 mod（Shoulder Surfing 等）与 26.2 自身的某些
PIP/离屏路径仍可能进入 `submitArmWithItem`。一旦进入，我 r3 加的 mixin 就会调用
`renderFirstPerson` → `Left/RightHandRender` → `AvatarRenderer#renderHand`，
后者强制 `arm.visible=true`、`zRot=±0.1`、袖子可见性且**从不还原**。

配合根因 A 的"活引用延迟绘制"，这些被强制打开的手臂部件就会在**第三人称玩家实体上再画一遍**
—— 就是那两条多余、残缺的手臂。换成非枪械物品后不再走该路径，
`PlayerModelMixin#setupAnim` 每帧重新摆正姿态，于是立刻消失，与你的描述完全吻合。

**修复**：在 mixin 里显式判定 `options.getCameraType().isFirstPerson()`，
第三人称一律放行给 vanilla；TACZ 的第三人称枪械由 `renderByItem` + `PlayerModelMixin` 负责。

---

## ④ 物品栏图标空白 —— 已修（r4 的 identity 判断不是主因）

**根因**：`submitCustomGeometry` 的回调**用错了矩阵**。反编译语义：

```java
public void submitCustomGeometry(PoseStack poseStack, RenderType type, CustomGeometryRenderer r) {
    var submit = new CustomFeatureRenderer.Submit(poseStack.last().copy(), type, r);  // 拷贝快照
    ...
}
```

提交时把当前 Pose **拷贝**存进 Submit，稍后执行时把该拷贝作为回调首参 `pose` 传回。
但 TACZ 的回调体里用的是**外层那个可变 `poseStack`**：

```java
collector.submitCustomGeometry(poseStack, type, (pose, buffer) ->
        SLOT_MODEL.renderToBuffer(poseStack, buffer, ...));   // ← 用错了，应该用 pose
```

等回调真正执行时，外层 `poseStack` 早已被 `popPose()`/复用，矩阵不再是提交那一刻的值，
四边形被画到任意错误位置（通常在可视区外）—— 就是**物品栏一片空白**。

**修复**：回调内改用参数 `pose` 重建矩阵。共 **7 处回调 / 4 个文件**
（枪械、弹药、配件、工作台的 slot 贴图路径）。

> 说明：r3 的 `EXTENTS ±1.5→±0.5`（避免误入 OversizedItemRenderer PIP 路径）仍然必要且保留；
> r4 的 identity key 改动无害也保留，但**不是**主因 —— 真正的主因是这个矩阵错用。

---

## ② 陆地移动时手+枪整体抖动 —— **未解决**，本轮排除了三个假设

你的更正很重要：是**手和枪作为整体**抖动（不是视角），且 r4 后**频率反而更高**。

本轮验证并**排除**的假设：

| 假设 | 结论 |
|---|---|
| r4 的 walk 插值改错了 | **排除**。数学验证 `position(pt) = (position - speed*(1-pt)) * scale` 跨 tick 边界连续单调；与 vanilla `LivingEntityRenderer` 用法一致 |
| `partialTicks` 传错 | **排除**。mixin 传的 `frameInterp` 就是 `renderItemInHand` 的 partialTick，`updateContext` 也正确写入上下文 |
| `bobView` 未被取消 | **排除**。`GameRendererMixin` 的 `renderItemInHand`/`bobView` 注入签名与 26.2 反编译完全一致，`tacz$renderingItemInHand` 标志链路成立 |
| 每帧渲染两次（主手+副手各一次） | **排除**。`submitArmWithItem` 确实每帧调两次，但 mixin 对 OFF_HAND 直接 cancel 返回 |
| rootNode 变换累加不清零 | **排除**。`root` 取自 `modelMap`，`cleanAnimationTransform()` 会重置 offset/quaternion |

**下一步建议**（需要实机日志才能继续收敛）：
频率随帧率上升、且是"手+枪整体"，最可能是 `renderFirstPerson` 里那段
**逆转原版视角延滞**的补偿与 26.2 实际施加量不匹配（26.2 的
`submitHandsWithItems` 用的是 `xBob/yBob`，而 TACZ 用
`getViewXRot(partialTick) - lerp(partialTick, xBobO, xBob)` 重算）。
若能提供一段**缓慢直线行走**的录屏/或打开 F3 观察抖动是否与 `xBob` 同频，
我可以据此定位。也可以先做个 A/B：临时注释掉 `renderFirstPerson` 开头那 4 行
`mulPose(XP/YP …*-0.1F)` 与 rootNode 补偿，看抖动是否消失。

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| ① r4 的 loadPose 还原已移除 | ✅ 字节码 0 处 |
| ① 第一人称门禁 `CameraType.isFirstPerson` | ✅ 字节码存在 |
| ④ slot 回调改用 pose 快照 | ✅ 字节码 5 处 Pose 访问 |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

## 验收重点

- [ ] **第三人称持枪**：不应再有多余/残缺手臂（①的关键判据）
- [ ] 第三人称 ↔ 第一人称来回切、切枪多次，确认不再"持久化"
- [ ] **物品栏/创造栏图标**应显示（④）
- [ ] 回归：第一人称对位、ADS、开火、抛壳/枪焰、tooltip 文字、工作台
- [ ] ② 抖动预计**仍存在**，请按上面的 A/B 建议协助定位
