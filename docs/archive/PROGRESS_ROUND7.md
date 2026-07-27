# 进度报告 · 第 7 轮 2026-07-25

基线：`tacz-26.2-r6-src.zip`。

> 本轮修一个**我在第 6 轮亲手留下的**问题：动画速率对了，但不平滑、像掉帧。

---

## 行走动画发涩／像被抽帧 —— 已修

### 我第 6 轮的错误判断

r6 把驱动量从 `walkAnimation.position()` 改成 `moveDist`，修正了"快 6.7 倍"。
但当时我在注释里写了这么一句：

> "`moveDist` 没有配套的 `moveDistO`，无法插值。但它每 tick 增量很小
> （步行约 0.06，占 2.0 周期的 3%），直接取值不会产生可见的阶梯感。"

**这个判断是错的。** 3% 的跳变换算成角度/位移在近距离手持模型上完全可见，
而且渲染帧率越高（60~144 Hz），同一个值被重复使用的帧数越多，
阶梯感越明显 —— 就是你说的"像被抽帧/帧率低了"。

### 根因

上游 1.21.1 的驱动量本来就是**插值后**的：

```java
entity.walkDist + (entity.walkDist - entity.walkDistO) * partialTicks
```

26.2 把 `walkDist` 更名为 `moveDist`，但**没有保留** `walkDistO`
（javap 确认：`Entity` 只有 `public float moveDist`）。
r6 因此只取了 `moveDist` 本身 —— 量纲对了，但丢掉了插值，变成 20 Hz 阶梯。

### 修复：重建 `walkDistO`

新增 `IMoveDistTracker` 接口，由 `EntityMixin` 实现：
在每个 `Entity#tick()` 的 **HEAD** 记录上一 tick 的 `moveDist`。

```java
@Inject(method = "tick", at = @At("HEAD"))
private void tacz$captureMoveDistO(CallbackInfo ci) {
    this.tacz$moveDistO = ((Entity) (Object) this).moveDist;
    this.tacz$moveDistInit = true;
}
```

`GunAnimationStateContext#tacz$walkDistance` 随即做与上游**完全一致**的插值：

```java
float moveDist = livingEntity.moveDist;
if (livingEntity instanceof IMoveDistTracker tracker) {
    float moveDistO = tracker.tacz$getMoveDistO();
    return moveDist + (moveDist - moveDistO) * this.partialTicks;
}
return moveDist;
```

未初始化时 `getMoveDistO()` 返回当前值，使增量为 0，避免首帧跳变。

### 数值验证

以步行 0.06/tick 模拟，跨 tick 边界逐帧输出：

```
pt=0.0 -> 1.0000   pt=0.25 -> 1.0150   pt=0.5 -> 1.0300   pt=0.75 -> 1.0450
下一 tick: 1.0600 -> 1.0750 -> 1.0900
step deltas: [0.015, 0.015, 0.015, 0.0144, 0.0006, 0.015, 0.015]   单调递增 ✓
```

> 说明：上游这个式子是**外推**（`+ (dist - distO) * pt`），tick 边界会有极轻微重叠，
> 但这正是 1.21.1 的原始行为 —— 与上游保持一致优先于"数学上更完美"。

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| `IMoveDistTracker` 已入 jar | ✅ |
| `EntityMixin` 含 tick HEAD 钩子与字段 | ✅ 字节码 4 处 |
| `GunAnimationStateContext` 调用 `tacz$getMoveDistO` | ✅ |
| `EntityMixin` 已在 `tacz.fabric.mixins.json` 注册 | ✅（原有条目） |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

## 验收重点

- [ ] **持枪行走/奔跑动画应平滑**，不再有掉帧/发涩感
- [ ] 速率仍应正常（约 1.67 s 一循环），不要因插值又变快
- [ ] 高帧率（>100 FPS）下复测 —— 帧率越高越能暴露阶梯感
- [ ] 缓慢/迅捷药水下平滑度与速率均应正常
- [ ] 回归：第三人称无多余手臂、物品栏图标、第一人称对位、tooltip 文字

## 仍未解决

- 瞄具 stencil/PIP：镜内仍会看到枪体
- 副手开枪：上游即不支持，属新功能
- 一批 compat（Iris/ImmediatelyFast/Shoulder Surfing 等）仍是 no-op
