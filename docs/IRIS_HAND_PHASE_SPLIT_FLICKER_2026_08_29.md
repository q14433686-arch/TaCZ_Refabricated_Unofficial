# Iris 光影下第一人称枪身 PBR 闪烁 — 双遍手部提交取证与修复

> 分支：`arena/01a04d12-tacz-refabricated-unofficial`
> 日期：2026-08-29
> 性质：**修复（FIX），在体 A/B 验证 PASS。**
> 用户报告：Complementary 系列光影 + Iris 1.11.2+mc26.2，开启光影内
> labPBR / SEUS PBR 材质选项后，第一人称枪身在反射光源时整块明暗跳变闪烁；
> 关闭 PBR 选项消失；仅第一人称（第三人称走 LOD 低模，无此现象）。
> 验证：2026-08-29 用户回报 **PASS** —— 默认开启 `IrisHandPhaseSplitFix` 后
> 上述闪烁消失。开关保留（默认 true），false 可秒回退。

---

## 0. 一句话结论

Iris 26.x 的 `HandRenderer` 一帧跑两遍手部 pass（实心 + 半透明）。Iris 自己会在
「物品不属于当前遍」时于 `submitArmWithItem` HEAD 取消绘制，但本仓用
`@WrapOperation` 替换了 `submitArmWithItem` 的**调用点本身**，Iris 的取消对
TACZ 视模永远不生效 ⇒ 枪身（`entityCutout`）每帧被提交两遍：实心遍画进
`gbuffers_hand`，半透明遍画进 `gbuffers_hand_water`。光影包在这两个 program 里对
PBR 材质的照明不同（labPBR/SEUS PBR 开启时差异可见），两层叠加即为用户目击的闪烁。
本仓新增 `IrisHandPhaseSplitFix`（`[FIX]` 开关，**默认 true**）让视模只提交实心遍。
**2026-08-29 用户在体 A/B 验证 PASS**；开关保留 false 秒回退。

---

## 1. 证据链（全部为代码级实读，非推测）

### 1.1 Iris 一帧两遍手部 pass（Iris 26.2 分支实读）

`net.irisshaders.iris.pathways.HandRenderer`：

- `renderSolid(...)`：`pipeline.setPhase(HAND_SOLID)` →
  `itemInHandRenderer.iris$renderHandsWithCustomRenderer(...)` → `submitHandsWithItems`。
- `renderTranslucent(...)`：`pipeline.setPhase(HAND_TRANSLUCENT)` →
  **再次** `iris$renderHandsWithCustomRenderer(...)` → **再次** `submitHandsWithItems`。

两次都走 `MixinItemInHandRenderer.iris$renderHandsWithCustomRenderer`，即本仓
`ItemInHandRendererMixin` 的注入点每次都被调用。

### 1.2 Iris 对实心物品的半透明遍取消，恰好被 TACZ 绕开

Iris `MixinItemInHandRenderer.iris$skipTranslucentHands`（`submitArmWithItem` HEAD）：

```java
if (HandRenderer.INSTANCE.isRenderingSolid() == HandRenderer.INSTANCE.isHandTranslucent(itemStack)) {
    ci.cancel();
}
```

- 实心遍：`isRenderingSolid()==true`、TACZ 枪非 BlockItem ⇒ `isHandTranslucent==false`
  ⇒ `true == false` 不取消，正常画；
- 半透明遍：`isRenderingSolid()==false` ⇒ `false == false` ⇒ **本应取消**。

但本仓 `ItemInHandRendererMixin.tacz$submitArmWithAnimatedItem` 是
`@WrapOperation(target = "submitArmWithItem")` —— TACZ 视模直接执行
`geoRenderer.renderFirstPerson(...)`，**从不进入 `submitArmWithItem`**，
HEAD 注入无从触发 ⇒ 半透明遍里枪身照画不误。普通物品走
`original.call(...)` 才会被 Iris 正常取消 —— 也就是说只有 TACZ 视模是例外。

### 1.3 半透明遍里枪身被画进 hand water program（Iris 26.2 分支实读）

`IrisPipelines`：`assignToMain(RenderPipelines.ENTITY_CUTOUT, p -> getCutout(p))`，
而 `getCutout` 在手部渲染激活时返回
`isRenderingSolid() ? ShaderKey.HAND_CUTOUT_DIFFUSE : ShaderKey.HAND_WATER_DIFFUSE`。
`HAND_WATER_DIFFUSE` = `ProgramId.HandWater`（gbuffers_hand_water）。

⇒ 实心遍：枪身 → gbuffers_hand；半透明遍：枪身 → gbuffers_hand_water。

### 1.4 两遍几何逐位一致 ⇒ 合成结果 = 「水面遍照明」覆盖在实心遍之上

- 两遍的投影/基座同源（`setupGlState` 同参数）；
- 动画为时间驱动（`ObjectAnimationChannel.update(timeNs, ...)`，墙钟时间），
  一帧内两次 `update()` 姿态相同；
- `cleanAnimationTransform()` 在每次 `renderFirstPerson` 末尾把 offset/quaternion
  复位，第二遍的摆动累加与第一遍一致。

所以两层几何重合、合成确定性叠加：最终画面是 gbuffers_hand_water 照明的枪。
PBR 关闭时两个 program 对不透明物体照明接近 ⇒ 不可见；labPBR/SEUS PBR 开启时
两遍的材质/高光处理不同 ⇒ 可见。**「整块明暗跳变」的逐像素时间域机制**
（两层的 depth/alpha 交互细节）**未在实机取证**，见 §3。

### 1.5 附加损害（无论是否闪烁根因，均客观存在）

- 动画状态机每帧 `update()` 两次（`AnimationStateMachine.update` 每调用完整跑一遍）；
- 整枪几何每帧多提交一遍（合成开销）；
- `RenderConfig` 中 `isHandRenderingSolid` 的既有注释「Iris 的 HandRenderer 一帧
  调用两次」与本结论互洽（该判断早已用于 scope PIP 的半透明遍限制）。

---

## 2. 改动清单

| 文件 | 改动 |
|---|---|
| `src/main/java/com/tacz/guns/config/client/RenderConfig.java` | 新增 `IRIS_HAND_PHASE_SPLIT_FIX`（`[FIX]`，**默认 `true`**），字段与 builder 两处注释记录完整证据链与在体 PASS 结论。 |
| `src/main/java/com/tacz/guns/mixin/client/ItemInHandRendererMixin.java` | 在 `tacz$submitArmWithAnimatedItem` 主手 TACZ 视模分支里，半透明遍早退：`IRIS_HAND_PHASE_SPLIT_FIX.get() && IrisCompat.isHandRendererActive() && !IrisCompat.isHandRenderingSolid()` 时 `return`，视模只提交实心遍。 |

改动语义 = 复刻 Iris 对普通实心物品的行为（`iris$skipTranslucentHands`）。
Sulkan / 无光影 / 原版路径不受影响（`isHandRendererActive()` 恒 false 时开关惰性）。

副作用（待验证，见 §4）：枪口火光/抛壳随之只走实心遍（它们本就指派到 HAND
program；此前水面遍的重复叠加是多余绘制）。掩码登记、scope PIP 合成不依赖
半透明遍的枪身提交（掩码几何在实心遍登记并被消费，PIP 在掩码之后合成）。

---

## 3. 验证记录

- **2026-08-29 用户回报 PASS**：默认开启 `IrisHandPhaseSplitFix` 后，
  Complementary + labPBR/SEUS PBR 下第一人称枪身反射光源处的闪烁消失。
- 回归面（枪口火光 / 抛壳 / 镜内裁切 / PIP / 换弹动画 / 帧率）以用户实测为准，
  未见书面负面回报；若有，关回 false 即秒回退。
- 仍未取证（不影响修复结论）：「两层叠加 ⇒ 整块明暗跳变」的逐像素时间域机制，
  以及 Complementary 的 gbuffers_hand 与 gbuffers_hand_water 在 labPBR/SEUS PBR 下
  的照明差异细节（shader 源码不在本仓）。

---

## 4. 验证协议（2026-08-29 已执行，PASS）

1. `[render] IrisHandPhaseSplitFix` **默认 true**（`config/tacz*.toml`，
   Forge Config API Port 生成），直接进世界即可；
2. 开 Complementary 光影 + labPBR（或 SEUS PBR），站在有光源/阳光反射处看枪身：
   - 闪烁是否消失？（主判据）→ **PASS**
   - 改成 false 重启是否复现？（对照）→ 用户以 PASS 总括回报
3. 观察回归面：枪口火光、抛壳、开镜镜内裁切/PIP、换弹动画、FPS 是否正常。
4. 附加观察：开关开启后帧率是否回升（双遍提交被去掉）。

## 5. 裁决后的后续

- ✅ **已执行**：验证通过 → 默认保持 `true`、注释升级 `[FIX]`（沿用
  `ScopeOcularRingFix` 的「Default on」惯例）；26.1.2 / 1.21.11 两条分支的移植
  提示已写进各自的 handoff 文件（`docs/handoff/HANDOFF_TO_26_1_2.md`、
  `docs/handoff/HANDOFF_TO_1_21_11.md`），本会话分支不跨分支改代码。
- **备用路线（若日后回归）**：
  - 枪口火光/抛壳在实心遍异常 → 拆细闸门，只跳过枪身实体提交，functional
    渲染器（火光/抛壳）保持半透明遍提交；
  - 闪烁复现 → 抓取 gbuffers_hand_water 输出对照截图，向 Complementary/Iris
    上游取证 hand water 对「被水面 program 画的不透明几何」的照明行为。
