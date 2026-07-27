# 进度报告 · 第 6 轮 2026-07-25

基线：`tacz-26.2-r5-src.zip`。

> **你的测试直接定位了根因。** "不是抖动，是被加速的行走动画；速度越快越快，缓慢药水后变慢"
> —— 这条线索把问题从"渲染抖动"重新归类为"动画驱动量量纲错误"，一击命中。

---

## ② 持枪行走动画过快（此前误判为"抖动"）—— 已修

### 根因：用错了量，动画速率快约 6.7 倍

枪包 Lua 的驱动写法是固定的 2.0 单位一个行走周期：

```lua
context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
```

上游 1.21.1 的 `getWalkDist()` 取的是 `Entity#walkDist`：

```java
entity.walkDist + (entity.walkDist - entity.walkDistO) * partialTicks
```

移植时误换成了 `livingEntity.walkAnimation.position(...)`。**这两者不是同一个量**（反编译确认）：

| 量 | 每 tick 增量 | 走完 2.0 周期 |
|---|---|---|
| `moveDist`（= 旧 `walkDist`） | `+= 水平位移 * 0.6` | 约 33 tick ≈ **1.67 s** |
| `walkAnimation.position` | `+= min(位移 * 4.0, 1.0)`，0.4 缓动 | 约 5 tick ≈ **0.25 s** |

以正常步行 0.1 格/tick 计算，后者约为前者的 **6.7 倍**。
两者都与移动速度**线性相关**，所以表现为"走得越快抖得越快、喝缓慢药水后变慢" ——
本质不是抖动，而是**被加速 6.7 倍的持枪行走动画**，与你的观察完全一致。

### 修复

26.2 中 `Entity.walkDist` 已更名为 `Entity.moveDist`（javap 确认：`public float moveDist`，
增量仍是 `位移 * 0.6`），语义与旧 `walkDist` 完全一致，因此改用它。

> 关于插值：`moveDist` 没有配套的 `moveDistO`，无法像上游那样做前后帧插值。
> 但它每 tick 增量很小（步行约 0.06，占 2.0 周期的 3%），直接取值不会有可见阶梯感；
> 而 `walkAnimation.position(partialTick)` 虽然平滑，**量纲是错的**。正确量纲优先。

### 同源第二处（顺带修复）

`InaccuracyType#isMove` 也把上游的 `Math.abs(walkDist - walkDistO)` 换成了
`walkAnimation.speed()`，同样是 6.7 倍量纲差，导致 `0.05` 阈值被放大 ——
极慢速移动也被判定为"移动中"，影响精度惩罚。
因 26.2 没有 `moveDistO`，改用 `getDeltaMovement().horizontalDistance() * 0.6` 还原量纲。
（玩家分支下面会用实际速度覆盖，故此处主要影响非玩家实体。）

### 前几轮在此问题上的弯路（记录备查）

- **r4**：把 `position()` 换成 `position(partialTick)`。方向对（消除 20Hz 阶梯），
  但没发现**量纲本身就是错的**，所以只是让"过快的动画"变得更平滑连续 ——
  这正是你说的"频率反而更高了"。
- **r5**：数学验证了 `position(pt)` 跨 tick 连续单调，据此**排除**了插值假设，
  但没往"量纲"方向想。是你的缓慢药水实验补上了这一环。

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| 字节码使用 `Entity.moveDist` | ✅ |
| 字节码不再出现 `WalkAnimationState.position` | ✅ 0 处 |
| `InaccuracyType` 不再用 `walkAnimation.speed` | ✅ 0 处 |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

## 验收重点

- [ ] **持枪步行/奔跑**：行走动画速率应回归正常（约 1.67 s 一个循环），不再"抖"
- [ ] 缓慢/迅捷药水下动画速率应随之变化，但**比例正常**
- [ ] 回归第 5 轮两项：第三人称无多余残缺手臂、物品栏图标正常
- [ ] 回归：第一人称对位、ADS、开火、抛壳/枪焰、tooltip 文字、工作台

## 仍未解决

- 瞄具 stencil/PIP：镜内仍会看到枪体
- 副手开枪：上游即不支持，属新功能
- 一批 compat（Iris/ImmediatelyFast/Shoulder Surfing 等）仍是 no-op
