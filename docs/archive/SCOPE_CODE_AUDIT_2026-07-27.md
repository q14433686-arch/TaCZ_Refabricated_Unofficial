# 瞄具渲染改动全量审计（r44，替换前清点）

**目的**：在按上游语义做等价替换之前，先把我们历轮改动**全部摊开**，
明确哪些是活代码、哪些是死代码、哪些是残留。
避免「改来改去、删改不干净」——尤其是自带瞄具的枪械排查会很痛苦。

---

## 0. 一句话现状

`BedrockAttachmentModel` 相对初始提交 **+299 / -19 行**（856 → 1136 行）。
其中**约 500 行是在 26.2 根本不会执行的死代码**，
真正生效的瞄具逻辑只有 `submit()` 里的 **3 段**。

---

## 1. 关键发现：整条 `render()` 链在 26.2 是死代码

### 1.1 调用链核实

```
BedrockGunModel.render(...)          ← 【无任何调用者】(grep 确认)
  └─ AttachmentRender.renderAttachment(...)
       └─ BedrockAttachmentModel.render(...)
            ├─ renderBoth()      ┐
            ├─ renderSight()     ├─ 6 个 legacy 方法
            ├─ renderScope()     │
            ├─ renderBothAccelerated()    ┐
            ├─ renderSightAccelerated()   ├─ AR 加速版
            └─ renderScopeAccelerated()   ┘
```

26.2 实际走的是另一条：

```
BedrockGunModel.submit(...)          ← 活的
  └─ AttachmentRender.submitAttachment(...)
       └─ BedrockAttachmentModel.submit(...)   ← 真正生效的只有这里
```

### 1.2 三重死亡证据

| 证据 | 内容 |
|---|---|
| ① `BedrockGunModel.render` 无调用者 | grep 全仓，只有 `BedrockModel#render`（部件级）和 `ShellRender` 的同名方法，与之无关 |
| ② `ARCompat.shouldAccelerate()` 硬编码 `return false` | 三个 `*Accelerated` 方法永不进入 |
| ③ `renderTempPart` 是彻底 no-op | 方法体只有 TODO 注释。而 6 个 legacy 方法**全部**通过它绘制 → 即使被调用也画不出任何东西 |

**结论**：`render()` + 6 个 `render*` + `renderOcularStencil` +
`renderDivisionOnly` + `renderOcularAndDivision` + `renderTempPart`
+ `getBedrockPartCenter`，**共约 500 行，全是死代码**。

它们的存在是历史包袱：这些方法里塞满了
`// TODO[26.2-Vulkan]: stencil/raw-GL removed` 注释掉的 stencil 调用，
每次排查都要重新确认一遍「这段到底跑不跑」——正是用户说的"最麻烦的没删干净"。

---

## 2. 真正生效的代码：`submit()` 里的 3 段

### 2.1 段一：目镜可见性（L462-484）

```java
boolean showOcular = shouldDrawOcularMask();
for (OcularWrapper w : ocularWrappers) {
    boolean show;
    if (isScope && isSight) {                       // 组合镜
        boolean isActiveGroup = activeViewGroup == 0 || (activeViewGroup == 2) == w.isScope;
        show = isActiveGroup ? (w.isScope && showOcular) : savedVisible[i];
    } else {
        show = showOcular;
    }
    w.renderer.setHidden(!show);
}
```

- 依赖 `shouldDrawOcularMask()`：sight 恒 false；scope 看开镜进度
- 依赖 r35 加的 `activeViewGroup`（由 `FirstPersonRenderGunEvent` 写入）

### 2.2 段二：镜身剔除（L485-490）

```java
boolean cullScopeBody = scopeBodyPart != null
        && isScope && !isSight                      // r34/r35 加的门禁
        && currentAimingProgress() > OCULAR_MASK_MIN_PROGRESS;
if (cullScopeBody) scopeBodyPart.visible = false;
```

**这是与上游语义偏离最大的一处**：上游是「圆内不画、圆外画」的空间二分，
我们退化成「开镜时整根不画」的全局布尔。

### 2.3 段三：准星重绘（L580-595）

```java
if (transformType.firstPerson() && !reticleNodes.isEmpty()) {
    ScopeNodeSet active = filterReticleByActiveView(reticleNodes);
    IReticleRenderer reticle = ReticleRendererRegistry.select(active);
    reticle.submitReticle(new IReticleRenderer.Context(...), active);
}
```

依赖自建的 5 个类（见 §3）。

---

## 3. 自建的 7 个类：4 活 3 死

`com/tacz/guns/client/render/scope/`

| 类 | 状态 | 说明 |
|---|---|---|
| `ScopeNodeSet` | ✅ 活 | 准星节点集合，`submit()` 用 |
| `IReticleRenderer` | ✅ 活 | 策略接口 |
| `IlluminatedReticleRenderer` | ✅ 活 | 唯一实现，含 `applyParallax()`（**臆造的视差近似**，见 §4） |
| `ReticleRendererRegistry` | ✅ 活 | 策略选择 |
| `ReticleKind` | ⚠️ 半死 | 只被 `ScopeNodeSet` 内部用来分类，无外部消费 |
| `ScopePipTest` | ❌ 死 | 由 `RenderConfig.SCOPE_PIP_TEST`（默认 `false`）门禁 |
| `ScopeRenderTarget` | ❌ 死 | 只被 `ScopePipTest` 引用 |

`ScopePipTest` 是第 26 轮做 PIP 可行性验证的产物，结论已记录在
`SCOPE_PIP_FINDINGS`（放大靠 FOV 不靠 PIP），**该实验已结束**。

---

## 4. 需要重点清理的「臆造逻辑」

### 4.1 `IlluminatedReticleRenderer#applyParallax`

```java
private static final float PARALLAX_PUSH = 0.75f;
poseStack.translate(0.0f, 0.0f, -push / 16.0f);
```

**上游没有任何对应物。** 第 30 轮已确认：上游全仓 grep
`collimat`/`parallax`/`billboard` **零命中**，准星几何刚性焊在枪体上，
从未做位置补偿。这是当时为模拟"全息漂浮感"自己加的，
属于用户三次指出的「自己发明几何近似」之一，**应删**。

### 4.2 `FADE_IN_START = 0.35f` 淡入

上游是 stencil 硬切（要么全有要么全无），没有淡入过渡。
属于我们自加的观感修饰，可保留但需标注为「非上游行为」。

### 4.3 `RETICLE_ILLUMINATED_PATTERN` 白名单

```java
"^(.*_)?(division|divisions|dot|cross|crosshair|reticle|red)(_\\d+)?_illuminated\\d*$"
```

上游不需要白名单——它用 stencil 限制**区域**，不管节点叫什么。
我们没有 stencil，只能靠名字猜哪些是准星。
**已知缺陷**：`scope_hamr`/`scope_mk5hd` 的 `lens_illuminated` 不在白名单，
`scope_vudu` 用无前缀命名无法按组过滤（r35 已记录）。

---

## 5. 与上游语义的偏离清单

| 项目 | 上游 | 我们 | 偏离性质 |
|---|---|---|---|
| 遮罩形状 | 屏幕空间**圆**（TRIANGLE_FAN 90 段） | 无 | 能力缺失 |
| 圆半径 | `80 × modifier × 开镜进度` | 无 | 能力缺失 |
| 镜身裁剪 | `stencilFunc(EQUAL,0)` 圆外画 | 开镜时**整根隐藏** | 退化近似 |
| 目镜遮罩 | 圆外画黑、圆内挖空 | 全画或全不画 | 退化近似 |
| 分划绘制 | `stencilFunc(EQUAL,~(i+1))` 圆内画 | 按名字白名单重画 | 手段替换 |
| 准星视差 | **无** | `applyParallax` 前推 0.75 | ❌ 臆造 |
| 准星淡入 | **无**（硬切） | 0.35→1.0 线性 | ⚠️ 自加 |
| 低倍镜 | 镜身无条件画、无圆 | 同 | ✅ 一致 |

---

## 6. 替换方案建议

### 6.1 第一步：清理（本轮可做，零风险）

删除**确定不执行**的代码，不改变任何运行时行为：

1. `BedrockAttachmentModel` 的 6 个 legacy `render*` 方法
   + `render()` + `renderTempPart` + `renderOcularStencil`
   + `renderDivisionOnly` + `renderOcularAndDivision` + `getBedrockPartCenter`
2. `AttachmentRender.renderAttachment` + `BedrockGunModel.render`
   + `renderAccelerated`（同属死链）
3. `ScopePipTest` + `ScopeRenderTarget`（实验已结束）
4. `IlluminatedReticleRenderer#applyParallax`（臆造逻辑）

> ⚠️ 注意：`BedrockGunModel.render` 的删除要谨慎核对 —— 它虽无调用者，
> 但可能被第三方枪包/其他 mod 反射调用。建议保留方法签名但清空实现，
> 或先只删 `BedrockAttachmentModel` 内部的部分。

### 6.2 第二步：等价替换（需先解决圆心问题）

按 `SCOPE_UPSTREAM_TRUTH_2026-07-27.md` §6.3 的参数表实现 shader 圆形裁剪。
**前提**：先用「只画一个纯色圆」的调试渲染验证圆心屏幕坐标算法正确。

**不要在圆心未验证前动 §6.1 之外的任何渲染代码** —— 这是第 29/31 轮
两次失败的直接教训。

---

## 6.3 【本轮已执行】清理结果

| 文件 | 变化 | 删除内容 |
|---|---|---|
| `BedrockAttachmentModel` | **1137 → 588** 行（−549） | `render()` + 6 个 `render*` + `renderTempPart` + `renderOcularStencil` + `renderDivisionOnly` + `renderOcularAndDivision` + `getBedrockPartCenter` + 4 个死 import |
| `BedrockGunModel` | 554 → 439 行（−115） | `render(...)` + `renderAccelerated(...)` + 4 个死 import |
| `AttachmentRender` | 139 → 107 行（−32） | `renderAttachment` 两个重载；`render(...)` 清空为 no-op（接口约定不能删签名） |
| `ScopePipTest` / `ScopeRenderTarget` | **删除** | PIP 可行性实验已结束，结论见 `SCOPE_PIP_FINDINGS` |
| `RenderConfig` | −2 行 | `SCOPE_PIP_TEST` 配置项 |
| `IlluminatedReticleRenderer` | −20 行 | `applyParallax()` + `PARALLAX_PUSH`（臆造逻辑） |

**合计删除约 720 行死代码，零行为变更。**

已做的安全校验：
- 全部 6 个文件大括号/圆括号配平；
- 全仓扫描 15 个已删符号，**无任何非注释残留引用**；
- 保留 `AttachmentRender#render` 空实现（`IFunctionalRenderer` 接口约定）。

> 踩到并已修的一个坑：删 `BedrockAttachmentModel#render` 后，
> `AttachmentRender:56` 仍在调它 → 会编译失败。
> 说明**跨类死链必须一起删**，这正是「删不干净」的典型表现。

---

## 6.4 【r45 重构】三段逻辑处置结果

用户指出：既然已确认 26.2 不可能有区域裁剪，**「镜身剔除」这个概念本身就该消失**，
要的是重构而非继续打补丁。据此重新审视 §2 的三段：

| 段 | 处置 | 依据 |
|---|---|---|
| ① 目镜遮罩 `shouldDrawOcularMask` | ❌ **删除** | **上游从来不画目镜**。`renderOcularStencil` 第一行就是 `colorMask(false,false,false,false)` —— 目镜只写模板值、完全不写颜色。我们当年把 `colorMask` 注释掉后，那块本该隐形的几何被实打实画成黑片，于是又加一个「遮罩开关」去补救 —— **补救建立在对上游的误读上** |
| ② 镜身剔除 `cullScopeBody` | ❌ **删除** | 上游 `stencilFunc(EQUAL,0)` 的含义是「镜身只在目镜圆**之外**绘制」，是一次**屏幕空间区域二分**，不是「把镜身整根删掉」。退化成全局布尔后连累红点/组合镜，r34/r35 两轮都在给这个错误概念打补丁 |
| ③ 准星重绘 | ✅ **保留** | 这是真实需求：`division_illuminated` 是被 `setHidden(true)` 的父节点的子节点，不单独重画就永远不可见。与 stencil 无关，属独立机制 |

**连带删除的死状态**（全部只剩「声明+赋值」、零消费）：
`OcularWrapper` 内部类、`ocularWrappers`、`scopeBodyPart`、`ocularNodePaths`、
`divisionNodePaths`、`isScopeOcular`、`scopeBodyPath`、`ocularRingPath`、
`scopeViewRadiusModifier` + `setScopeViewRadiusModifier`（**全仓无调用者**）、
5 个只被自己引用的节点名常量、`TreeMap`/`Matcher` import。

### 结果

**`BedrockAttachmentModel`：1137 → 338 行**（累计 −70%）。

`submit()` 现在只做两件事：

```java
super.submit(...);              // 所有几何按模型原样提交，无任何可见性开关
submitReticle(...);             // 准星层（仅第一人称，按镜组过滤）
```

**红点 / 筒镜 / 组合镜走完全相同的一条路径**，不再有「按形态分类的可见性开关」。
自带瞄具（`built_in_attachment`）出问题时无旁路可藏 —— 这正是本轮的目的。

### 代价（必须明确）

镜内会看到**镜筒内壁**。这是 26.2 无区域裁剪能力的**硬约束**，
不是回归。此前用「开镜时整根隐藏」掩盖它，代价是红点被误删、
组合镜错乱等一连串次生问题 —— 那笔交易并不划算。

真正的解法仍是屏幕空间圆形裁剪（shader `discard`），
参数已在 `SCOPE_UPSTREAM_TRUTH §6.3` 列出，但**必须先单独验证圆心坐标算法**。

---

## 7. 清理后的预期收益

- `BedrockAttachmentModel` 从 1136 行降到 **约 600 行**
- 排查瞄具问题时不再需要反复确认「这段跑不跑」
- 自带瞄具的枪械（`built_in_attachment`）出问题时，
  调用链一眼可见：`submit()` → 3 段逻辑，没有旁路
