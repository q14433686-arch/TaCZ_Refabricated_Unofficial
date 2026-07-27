# 第 17 轮进度报告

**日期**：2026-07-25
**验收**：第 16 轮配件修复 ✅ 已由你实机通过

> ⚠️ **未实机验收**：沙盒无 GPU。② 的改动需你实机确认观感。

---

## 一、本轮结论

| # | 你的问题 | 结论 |
|---|---|---|
| ① | 镜内放大（GIF 目标效果） | 📋 已理解目标，属 PIP 范畴，见 §四 |
| ② | 低倍/高倍镜差异化策略 | ✅ **你的推测基本正确**，已核对上游并实现 |
| ③ | 除瞄准镜/弹匣外其他配件真生效吗？ | ✅ **确实生效**，已逐项举证 |

---

## 二、② 上游确实对低倍/高倍分了策略（你猜对了）

### 2.1 核对结果：上游有三条独立渲染路径

```java
// 上游 BedrockAttachmentModel#render
if (isScope && isSight)      renderBoth(...);    // 两者兼具
else if (isScope)            renderScope(...);   // 高倍镜
else if (isSight)            renderSight(...);   // 低倍镜/红点
```

关键差异：

| 路径 | 目镜遮罩 | 说明 |
|---|---|---|
| `renderSight`（低倍） | **完全不画** | 只调 `renderDivisionOnly()`，压根不碰目镜 |
| `renderScope`（高倍） | **画** | 调 `renderOcularAndDivision(..., selective=false)` |
| `renderBoth` | 按节点区分 | `selective=true`，按 `ocular_scope`/`ocular_sight` 前缀分别处理 |

### 2.2 但机制和你想的略有不同 —— 这点很重要

你的推测是「低倍镜内部为空，高倍镜不开镜时为贴图」。
**前半句完全正确，后半句需要修正**：上游高倍镜不开镜时<b>不是贴图，而是被遮罩铺满</b>。

上游的真实做法是用 stencil 挖一个**半径随开镜进度缩放**的圆：

```java
float rad = 80 * scopeViewRadiusModifier;
rad *= getClientAimingProgress(...);      // 不开镜 -> rad = 0
// 配合 RenderSystem.stencilOp(..., GL11.GL_INVERT) 挖洞
```

- 不开镜 → `rad = 0` → 圆退化成一点 → **遮罩铺满整个目镜**
- 开镜 → 圆张开 → **遮罩只剩圆环外缘** → 镜内透亮 + 边缘暗角

所以上游是**连续渐变**的，不是「贴图/放大」二选一。它省性能的方式是
「不开镜时根本不做镜内渲染，直接让遮罩盖住」——这个思路和你说的一致。

### 2.3 26.2 的限制与我的取舍

26.2 无 stencil（第 9/10 轮已逐项确认），**挖不出这个洞**，只能二选一：铺满 or 不画。

于是按上游的分类精神实现 `shouldDrawOcularMask()`：

```java
private boolean shouldDrawOcularMask() {
    if (isSight && !isScope) return false;                        // 低倍镜：永远不画
    return currentAimingProgress() > OCULAR_MASK_MIN_PROGRESS;    // 高倍镜：仅开镜时画
}
```

| 类型 | 不开镜 | 开镜 |
|---|---|---|
| 低倍镜 sight（19 个） | 透空 | 透空 |
| 高倍镜 scope（11 个） | 透空 | 画遮罩 |
| both（3 个） | 透空 | 画遮罩 |

逻辑单测 **PASS**。

**与第 16 轮的区别**：上轮我对所有瞄具一刀切「开镜才画」，
这轮按上游把低倍镜单独摘出来——低倍镜任何时候都不该有黑镜片。

**坦白一点**：高倍镜「开镜画遮罩」是**临时降级**。理想状态是开镜时贴 PIP 离屏纹理
（就是你 GIF 里的效果），现在只能先用遮罩占位。等 PIP 做完这里要重写。

数据分布（默认枪包 101 个配件）：scope-only **11**、sight-only **19**、both **3**、其余 68 非瞄具。

---

## 三、③ 其他配件确实生效 —— 逐项举证

我把 101 个配件 data 文件全部跑了一遍真实解析管线，统计每个 modifier 被成功解析的次数：

```
=== modifier 解析成功统计 (共 101 个配件) ===
  ads                  91        recoil               36
  weight_modifier      94        inaccuracy           30
  effective_range       8        head_shot             5
  ammo_speed            5        armor_ignore          5
  damage                4        pierce                3
  rpm                   3        explosion             1
  ignite                1

=== 各类配件实际生效的 modifier ===
  MUZZLE     [ads, ammo_speed, effective_range, head_shot, inaccuracy, recoil, rpm, weight]
  GRIP       [ads, inaccuracy, recoil, weight]
  STOCK      [ads, inaccuracy, recoil, weight]
  LASER      [ads, head_shot, inaccuracy, weight]
  SCOPE      [ads, weight]
  MAG/AMMO   [ads, ammo_speed, armor_ignore, damage, explosion, head_shot,
              ignite, inaccuracy, pierce, rpm, weight]
```

并确认这些缓存值都有**真实消费方**：

| modifier | 消费点 |
|---|---|
| `ads`（开镜速度） | `LocalPlayerAim:90`、`LivingEntityAim:72` |
| `recoil`（后坐力） | `CameraSetupEvent:179` |
| `silence`（消音） | `LocalPlayerShoot:322` |
| `rpm`（射速） | `GunData:306` |
| `effective_range` | `EffectiveRangeModifier:63` |

**结论：枪口、握把、枪托、镭射的属性加成都是通的。**

### 3.1 一个差点误报的插曲（值得记录）

测试中 `silence` 9 次全部抛 `NullPointerException`，我一度以为消音器坏了。
深挖后发现异常真正来源是：

```
NullPointerException: Cannot invoke "FabricLauncher.isDevelopment()"
  because FabricLauncherBase.getLauncher() is null
```

——是我的测试**没跑在 Fabric 加载器里**，`ForgeConfigSpec` 取值失败。
游戏中 `TaCZFabric#onInitialize` 会正常注册配置，**不会发生**。

**这是沙盒假象，不是 bug。** 差一点就报了个假警。

### 3.2 另一个查证：`AimInaccuracyModifier` 未注册

我发现 `AimInaccuracyModifier.java` 存在但从未注册，而数据里 `aim_inaccuracy` 用了 39 次。
一度怀疑是移植漏注册——**核对上游后确认上游也没注册**，
且 `InaccuracyModifier` 内部已经遍历了包含 `AIM` 在内的所有 `InaccuracyType`：

```java
for (InaccuracyType type : InaccuracyType.values()) {
    switch (type) {
        case AIM -> { if (aimInaccuracy != null) jsonProperties.put(type, aimInaccuracy); }
        ...
```

**`aim_inaccuracy` 是生效的**，`AimInaccuracyModifier` 属上下游共有的死代码。不动它。

---

## 四、① 镜内放大 —— GIF 已理解

从 GIF 逐帧提取分析，目标效果是：

1. **镜内画面真实放大**（镜内云朵尺度显著大于镜外）
2. **镜内是实时世界画面**，不是贴图
3. 十字线 + 测距刻度叠加在画面之上
4. 开镜过程有平滑过渡，镜筒边缘有暗角

这正是 `SCOPE_PIP_PLAN.md` 里 P2~P4 的目标。

### 4.1 你对性能和 Iris 的顾虑，我完全同意

你说的「要考虑性能，要有裁切叠加蒙版的准备，因渲染管线重构和 Iris 要谨慎」——
这三点恰好是方案里标红的三大风险。我的应对：

| 顾虑 | 对策 |
|---|---|
| **性能** | ①离屏 RT 降分辨率（镜内本就模糊，512² 足够）②仅 `aimingProgress > 0.9` 时启用 ③隔帧更新（复用 `textureIsReadyToBlit`）④配置开关 |
| **裁切/蒙版** | 自定义 fragment shader 采样蒙版纹理，`out.a = mask.r`，边缘可羽化（比 stencil 硬边更好） |
| **不影响其他渲染层** | 严格用 `outputColorTextureOverride` 的「保存→重定向→还原」三段式，与 `LevelRenderer#addAlwaysOnTopPass` 完全同构；配独立 depth 纹理，不碰主 RT |
| **Iris** | 检测到 `IrisCompat.isUsingRenderPack()` 直接降级为当前的遮罩方案，不尝试兼容 |
| **递归渲染** | 静态 `isRenderingScope` 守卫 |

### 4.2 我的建议：先做 P1

**P1 = 搭离屏 RT + 递归守卫，镜内先渲染纯色。**

理由：能以极低成本验证「`outputColorTextureOverride` 在世界渲染阶段确实可用」
这个核心假设。若 P1 就失败，后面全部设计推翻，早发现早改。

P1 交付时你只会看到「镜片变成一块纯色」——但那意味着管线打通了。

---

## 五、本轮改动

| 文件 | 改动 |
|---|---|
| `client/model/BedrockAttachmentModel.java` | 新增 `shouldDrawOcularMask()`；`renderOcularStencil` 与 `renderOcularAndDivision` 改用统一策略判定 |

字节码核对：`shouldDrawOcularMask` ×3。

---

## 六、TODO

### 请验收

- [ ] **低倍镜/红点**（EXP3、OKP-7 等 19 个）：不开镜、开镜**都不该有黑镜片**
- [ ] **高倍镜**（TA31、6x 等 11 个）：不开镜透空；开镜时镜片有遮罩（暂时是纯色，非放大画面）
- [ ] 第 16 轮配件修复未回归（扩容弹匣、瞄准镜识别）

### 需要你拍板（这轮必须定，否则 PIP 没法开工）

1. **是否先做 P1？**（低成本验证核心假设，我强烈建议）
2. **性能预算**：镜内渲染默认开还是默认关？
3. **Iris 策略**：确认「检测到光影就降级为遮罩」可以吗？

### 未解决

1. PIP 镜内放大（①）—— 等你拍板
2. UI 整体重绘 / 左侧 3D 模型
3. 后坐力偏左右、子弹从眼部生成 —— 上游既有行为
4. 副手开枪 —— 上游不支持
5. 一批 compat 仍是 no-op

---

## 七、自我复盘

1. **第 16 轮我对瞄具一刀切是不够准确的**。当时只看了「官方宣传图不开镜时透空」，
   就对所有瞄具统一处理，没去核对上游是否分类型。你这轮的追问是对的——
   **低倍镜和高倍镜本来就该不一样**。

2. **差点误报消音器故障**。测试环境缺 Fabric 加载器导致的 NPE，
   如果我不深挖直接写进报告，就会浪费你一轮时间去验一个不存在的 bug。
   **教训：沙盒里的异常先分清是"代码问题"还是"环境问题"。**

3. **③ 这类"是否真生效"的问题，靠读代码不够**，必须跑真实数据统计。
   这次 101 个文件全量跑一遍，才敢给出肯定答复。
