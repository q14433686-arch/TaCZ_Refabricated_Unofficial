# 进度报告 · 第 8 轮 2026-07-25

基线：`tacz-26.2-r7-src.zip`。四个问题全部定位到根因并修复。

> 其中 ① 是**撤销我第 2 轮的错误改动**；② 你的"多层皮肤错位"判断完全正确，
> 我前几轮把它误判成"手臂缺失"，方向一直是错的。

---

## ① 换弹时枪上的弹匣不渲染 —— 已修（撤销第 2 轮错误）

**根因**：模型里两个弹匣节点语义不同：

| 节点 | 含义 |
|---|---|
| `magazine` | 换弹时**跟着手走**的那一个 |
| `additional_magazine` | **留在枪身上**的那一个 |

上游 1.21.1 的做法是：在 `additional_magazine` 的变换下，把 `magazine` 的网格
**再画一遍**（同一份几何渲染两次）。默认枪包动画确认了这一点 —— `ak47.animation.json`
的 `reload_tactical`/`reload_empty`/`inspect` 同时驱动 `mag_and_bullet` 与 `additional_magazine`。

第 2 轮我把该 provider 改成了 `return null`，理由是"`magazine` 本来就在模型树里会被遍历到"。
**那个判断是错的** —— 树里那份是跟手的，留在枪上的那份只能靠这里补画。
症状即：换弹/空仓换弹时枪上弹匣消失，只剩手里那个。

**修复**：新增标记接口 `IMirrorGeometry`，由 `BedrockRenderSnapshot` 原生处理
"在本节点变换下把另一节点几何再画一遍"。之所以不照抄上游返回裸 lambda：
快照遍历器会跳过非 `IFunctionalSubmitter` 的 renderer（第 2 轮的坑），
且镜像几何必须与枪身共用同一 `RenderType`/DrawCommand 批次才能保证材质与顺序。

---

## ② 第三人称手部"多层皮肤错位" —— 已修

**你的判断是对的**，这不是"手臂缺失"，而是袖子层（第二层皮肤）与手臂错位。
这也解释了为什么它在**所有能看到第三人称模型的场合**都出现，包括物品栏里的缩略模型。

**根因**：1.21.1 里 `leftSleeve`/`rightSleeve` 是 `PlayerModel` 的**兄弟**部件，
不会自动跟随手臂，所以上游必须显式：

```java
this.rightSleeve.copyFrom(this.rightArm);
this.leftSleeve.copyFrom(this.leftArm);
```

26.2 改成了**子**部件（反编译 `PlayerModel` 构造函数确认）：

```java
this.leftSleeve  = this.leftArm.getChild("left_sleeve");
this.rightSleeve = this.rightArm.getChild("right_sleeve");
```

子部件渲染时**自动继承父级变换**。而移植时保留了这次拷贝
（写成 `loadPose(arm.storePose())`）—— 等于把手臂变换**又叠加一遍**到袖子上，
袖子相对手臂偏移一倍，看起来就是"多出一层没对齐的手部皮肤"。

而且上游那次拷贝只在 `ageInTicks == 0`（第一人称）分支内，移植版把它移到了分支**外面**，
连第三人称也一起执行，所以第三人称必现。

**修复**：删除该同步。vanilla 的 `PlayerModel#setupAnim` 每帧只设置袖子 `visible`，
姿态完全交给父子继承，不应干预。

---

## ③ 标靶车模型与碰撞箱错位、方向固定 —— 已修

**你的推断"疑似硬编码"是对的。**

上游是 `extends MinecartRenderer<TargetMinecart>`，只覆写 `renderMinecartContents(...)` ——
矿车的**位置插值、朝向、沿轨道姿态、受击摇晃**全部由父类 `AbstractMinecartRenderer` 负责。

移植时改成了直接 `extends EntityRenderer<...>` + 自定义 State，
在 `submit` 里手写 `translate + scale + 两个固定角度 mulPose`，
于是**父类的全部定位/朝向逻辑都丢了**：模型永远朝同一方向，也不跟随矿车插值位置 ——
而碰撞箱与交互由服务端实体决定，所以是正确的，两者就对不上。

**修复**：恢复 `extends AbstractMinecartRenderer<TargetMinecart, MinecartRenderState>`，
只覆写 `submitMinecartContents(...)`（26.2 中 `render*` 已改名 `submit*`）。
GameProfile 通过扩展的 `TargetMinecartRenderState` 在 extract 阶段携带。

---

## ④ 击碎方块时天上出现会变大的怪异方片 —— 已修

**根因：方法重载被静默错绑，颜色被当成了坐标。**

原代码：

```java
this.extractRotatedQuad(state, quaternion, red, green, blue, alphaFade);
```

看着像"旋转 + 颜色"，但 26.2 的 `SingleQuadParticle` **没有**接收颜色的重载
（反编译确认只有两个）：

```java
extractRotatedQuad(QuadParticleRenderState, Camera, Quaternionf, float partialTick)
extractRotatedQuad(QuadParticleRenderState, Quaternionf, float x, float y, float z, float partialTick)
```

于是它被绑到第二个 —— **r/g/b 被当作 x/y/z 坐标**，alpha 被当作 partialTick。
颜色是 0~1 浮点，所以四边形被画在"相对摄像机 (r, g, b)"这个**固定偏移**处
（+x 偏东、+z 偏南、+y 在上方），与真实弹孔位置无关。

逐条对上你的描述：

| 现象 | 解释 |
|---|---|
| 固定出现在西-西南方向屏幕上方 | 坐标恒为 (r,g,b)，相对摄像机固定 |
| 不同枪械出现在右上角不同位置 | 颜色取自枪械/弹药的 **tracerColor**，不同枪颜色不同 |
| 逐渐变大后消失、约 3 秒 | `colorPercent` 衰减 → 坐标趋近摄像机原点 → 视觉变大；生命周期约 60 tick ≈ 3s |

**修复**：改用带 `Camera` 的重载让父类自行计算相机相对坐标；
颜色通过 `rCol/gCol/bCol/alpha` 字段传递（父类内部用
`ARGB.colorFromFloat(alpha, rCol, gCol, bCol)` 取值），并在渲染后还原基色。

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| ① `IMirrorGeometry` 入 jar，Builder 中 6 处引用 | ✅ |
| ② `PlayerModelMixin` 已无 `loadPose/storePose` | ✅ 0 处 |
| ③ 继承 `AbstractMinecartRenderer<TargetMinecart, MinecartRenderState>` | ✅ |
| ④ 使用 `extractRotatedQuad(..., Camera, ...)` 重载 | ✅ |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

## 验收重点

- [ ] **换弹/空仓换弹**：枪上与手上应同时各有一个弹匣（①）
- [ ] **第三人称/物品栏缩略模型**：手部皮肤不再多一层、不再错位（②）
- [ ] **标靶车**：模型应跟随朝向与位置，与碰撞箱重合（③）
- [ ] **击碎标靶/冰/玻璃**：天上不应再出现会变大的方片；弹孔应正常贴在被击中的面上（④）
- [ ] 回归：行走动画平滑与速率、第一人称对位、tooltip 文字、物品栏图标、工作台

## 仍未解决

- 瞄具 stencil/PIP：镜内仍会看到枪体
- 副手开枪：上游即不支持，属新功能
- 一批 compat（Iris/ImmediatelyFast/Shoulder Surfing 等）仍是 no-op
