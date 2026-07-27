# 瞄准镜「镜内掏空 + 真实准星」设计方案

**日期**：2026-07-26（第 21 轮后续）
**回应**：用户「镜内掏空也不对……目镜内直接能看到远处，但准星还是『真实的』，也就是现代的『全息准星』，还有那种绘制的老式的，但这要预留几个不同的接口」

---

## 0. 我确认一下我理解对了

你说的是 FPS 游戏里的经典做法 —— **镜片本身透明（能直接看到远处世界），但准星是一层独立的、贴在镜片上的发光图形**：

| 类型 | 现实原型 | 视觉特征 |
|---|---|---|
| **全息 / 红点**（现代） | EOTech、Aimpoint、RMR | 一个**发光红点/圆环**浮在透明玻璃上，**不随镜身遮挡**，永远最亮 |
| **蚀刻分划**（老式） | 98k、PU 镜、ACOG 蚀刻线 | 黑色**实体十字线/密位尺**，画在镜片玻璃上，**不发光**，靠环境光 |
| **混合**（现代高倍镜） | ACOG TA31、Vudu | 平时是黑色蚀刻线，**开夜间照明后中心一段变红发光** |

而**不是**「整个镜片糊一块黑」，也**不是**「整个镜片什么都没有」——
后者正是我第 21 轮改完的状态：**掏空了，但准星也一起没了**，所以你说「掏空也不对」。

---

## 1. 为什么现在「掏空也不对」—— 根因

### 1.1 `division` 节点在构造函数里被永久隐藏，且再没人打开

`BedrockAttachmentModel` 构造函数第 100~107 行（我们的代码，与上游一致）：

```java
ModelRendererWrapper divisionModel = modelMap.get(DIVISION_NODE);
path = getPath(modelMap.get(DIVISION_NODE));
int i = 2;
while (path != null) {
    divisionNodePaths.add(path);
    divisionModel.setHidden(true);        // ← 准星被永久隐藏
    divisionModel = modelMap.get(DIVISION_NODE + '_' + i++);
    path = getPath(divisionModel);
}
```

上游之所以敢隐藏，是因为它后面会用 stencil **手动**把 division 画回来
（`renderDivisionOnly` / `renderOcularAndDivision` 里的 `renderTempPart`）。

**而我们的 `renderTempPart` 是彻底的 no-op**（r18 已确认，26.2 移除了 `MultiBufferSource`）。
于是：

```
构造函数隐藏 division  →  唯一能画回来的 renderTempPart 是空的  →  准星永远不显示
```

**这就是「掏空后什么都看不见」的直接原因。** 掏空是对的，缺的是把准星画回来。

### 1.2 全息准星的机制其实已经现成了

好消息：TACZ 用**节点命名约定**表达「这段几何要发光」，而这套机制我们**已经完整继承**：

```java
// BedrockModel 构造函数（我们的代码 L54-57，与上游逐字一致）
if (name != null && name.endsWith("_illuminated")) {
    rendererWrapper.getModelRenderer().illuminated = true;
}
```

```java
// BedrockRenderSnapshot#captureGeometry / capturePart（我们的代码 L168 / L181）
int partLight = part.illuminated ? 15728880 : inheritedLight;   // 15728880 = 满亮度
```

**`_illuminated` 后缀的节点会被强制成满光照（0xF000F0）**，这就是「全息红点发光」的实现。
而且这条路径走的是**活的 submit 快照链路**，不是 no-op。

### 1.3 实测数据：默认枪包的准星节点分类

我把 129 个 geo 模型全扫了一遍：

| 节点名 | 出现次数 | 含义 |
|---|---|---|
| `division` | 33 | **蚀刻分划**（老式，不发光） |
| `division_illuminated` | 22 | **发光分划**（现代混合镜的红色中心） |
| `divisions` | 9 | 复数形式变体 |
| `lens_illuminated` | 7 | 发光镜片层 |
| `dot_illuminated` | 4 | **纯红点**（Aimpoint 类） |
| `division_2` | 4 | 组合镜第二组 |
| `sight_division_illuminated` / `scope_division_illuminated` | 各 2 | 组合镜分别的发光分划 |
| `crosshair_illuminated` / `cross_illuminated` / `red_illuminated` | 各 1 | 十字线变体 |

**分类结果**：

| 形态 | 数量 | 代表 |
|---|---|---|
| 同时有 `division` + `*_illuminated` | **31** | `scope_acog_ta31`、`sight_t1`、`sight_exp3`… |
| 只有 `division`（纯蚀刻，无发光） | **2** | `scope_98k`、`scope_retro_2x` |
| 只有 `*_illuminated`（非瞄具，激光/手电） | 6 | `laser_peq6`、`grip_vertical_ranger`… |

**这正好对应你说的三类**，而且**数据已经在枪包里了，不用新增任何字段**。

`scope_acog_ta31` 就是典型混合镜：
```
bone division              5 个 cube  ← 黑色蚀刻分划 + 遮光板
bone division_illuminated  1 个 cube  origin=[-1.25,-10.4375,-99.875] size=[2.5,12.25,0]
                                      ← 一根 2.5×12.25 的发光竖线
```

`sight_exp3`（全息镜）只有 `division_illuminated`，就是纯全息准星。

---

## 2. 一个必须先解决的坑：`division` 里混着「遮光板」

`scope_1873_6x` 的 `division` 节点有 **10 个 cube**，其中：

```
origin=[-14, 10.78125, -111]   size=[32, 0.1875, 0]    ← 细线（真准星）
origin=[-21, 3.70837, -111]    size=[7, 14.08326, 0]   ← 中等面
origin=[-14.0625, -37.1875,-111] size=[32, 32, 0]      ← 32×32 大面（遮光板！）
origin=[1.9375, 10.8125, -111]   size=[32, 32, 0]      ← 32×32 大面（遮光板！）
```

**那两块 32×32 的大面就是第 9 轮闯祸的元凶** —— 我当时无差别把整个 `division` 画出来，
它们就成了糊在屏幕上的大黑方块（第 10 轮撤销）。

上游靠 stencil 的 `EQUAL(~(i+1))` 把它们裁在圆外，所以看不见。
**我们没有 stencil，如果无脑全画，第 9 轮的黑方块会原样复现。**

> ⚠️ 这是本方案**最大的风险点**，必须用「圆形裁剪」或「只画发光子集」来规避。

---

## 3. 方案：三层分离 + 可插拔准星接口

### 3.1 分层

把目镜区域拆成三个**独立可控**的层：

```
┌─ Layer C: 准星层（reticle）      ← 最上，永远可见，不被镜身遮挡
│    · 全息/红点：_illuminated 节点，满亮度，无深度测试
│    · 蚀刻分划：division 的细线子集，正常光照
├─ Layer B: 镜片层（lens）         ← 中间，透明或轻微染色
│    · 掏空 = 不画 ocular
├─ Layer A: 镜筒遮罩（vignette）   ← 最下，圆外黑边（待圆形遮罩实现）
└─ 背后：已被 FOV 放大的真实世界
```

### 3.2 预留的接口（你要求的「几个不同的接口」）

新增一个策略接口，按瞄具类型选择实现：

```java
package com.tacz.guns.client.render.scope;

/**
 * 准星（分划）绘制策略。按瞄具类型选择不同实现，
 * 让「全息红点 / 老式蚀刻 / 混合 / 自定义」各走各的路径。
 */
public interface IReticleRenderer {

    /** 本策略是否适用于该瞄具（由节点构成自动判定）。 */
    boolean matches(ScopeNodeSet nodes);

    /**
     * 提交准星几何。
     * @param aimingProgress 开镜进度 0~1，可用于淡入淡出
     * @param nodes          已解析的瞄具节点集合
     */
    void submitReticle(ScopeRenderContext ctx, ScopeNodeSet nodes, float aimingProgress);

    /** 优先级，数值大者优先（用于第三方枪包覆盖内置策略）。 */
    default int priority() { return 0; }
}
```

配套的节点集合（构造时解析一次，缓存）：

```java
public final class ScopeNodeSet {
    public final List<BedrockPart> etchedDivision;      // division / divisions（不发光）
    public final List<BedrockPart> illuminatedReticle;  // *_illuminated（发光）
    public final List<BedrockPart> ocular;              // 目镜遮罩
    public final boolean hasEtched;
    public final boolean hasIlluminated;
}
```

**内置四个实现**（注册到一个 `ReticleRendererRegistry`）：

| 实现类 | matches 条件 | 行为 | 覆盖 |
|---|---|---|---|
| `HolographicReticleRenderer` | 只有 `*_illuminated` | 满亮度 + **关深度测试**，永远浮在最上 | 全息/红点 |
| `EtchedReticleRenderer` | 只有 `division` | 正常光照，**但只画细线子集**（见 §3.3） | 98k、retro_2x |
| `HybridReticleRenderer` | 两者都有 | 蚀刻线按环境光 + 发光段满亮度 | ACOG、31 个混合镜 |
| `NoReticleRenderer` | 都没有 | 什么都不画（兜底，不崩） | 激光/手电等非瞄具 |

第三方枪包可以自己注册 `priority() > 0` 的实现来覆盖，接口就是为这个预留的。

### 3.3 关键：如何只画「细线」不画「遮光板」

两种判据，**建议 A + B 双保险**：

**A. 按 cube 尺寸过滤（零配置，对现有枪包立即生效）**

统计发现真准星线的特征是**至少一维极薄**：

```
size=[32, 0.1875, 0]     ← 细线：短边 0.1875
size=[2.5, 12.25, 0]     ← 发光竖线：短边 2.5
size=[32, 32, 0]         ← 遮光板：两维都 ≥32
```

规则：`min(sizeX, sizeY) <= 阈值` 才算准星线（阈值取 8，可配置）。
`scope_1873_6x` 用此规则可正确剔除那两块 32×32。

**B. 按子节点名白名单（推荐枪包作者用）**

约定 `division` 下面再分 `division_line*` / `division_mask*` 子骨骼，
新枪包可显式声明。旧枪包 fallback 到 A。

> ⚠️ **A 是启发式，不是万无一失**。所以要配 §3.4 的开关，
> 万一某个枪包被误判，能一键退回「只画发光层」。

### 3.4 配置开关

`RenderConfig` 新增：

```
ScopeReticleMode = AUTO | ILLUMINATED_ONLY | ALL | OFF
```

- `AUTO`（默认）：按 §3.2 策略自动选
- `ILLUMINATED_ONLY`：**只画 `_illuminated` 层**（最安全，绝不会出现黑方块）
- `ALL`：全画（调试用，可能复现第 9 轮黑方块）
- `OFF`：完全不画准星

---

## 4. 技术实现要点（均已用 26.2 字节码确认）

### 4.1 发光：现成机制，零成本

`BedrockPart.illuminated == true` → 快照阶段直接给 `15728880`（满亮度）：

```java
// BedrockRenderSnapshot L168 / L181（现有代码，不用改）
int partLight = part.illuminated ? 15728880 : inheritedLight;
```

只要让 `_illuminated` 节点 `visible = true` 并进入 submit，就自动发光。

### 4.2 「浮在最上、不被遮挡」用什么 RenderType

26.2 可用（字节码确认存在于 `net.minecraft.client.renderer.rendertype.RenderTypes`）：

| API | 用途 |
|---|---|
| `eyes(Identifier)` | **末影人眼睛**用的：加算混合 + 不写深度 → **最贴近全息红点** |
| `entityTranslucentEmissive(Identifier)` | 半透明 + 自发光 |
| `textSeeThrough(Identifier)` | 穿透式文字（可透过方块看见） |
| `lightning()` | 加算混合 |
| `beaconBeam(Identifier, boolean)` | 信标光束，半透明加算 |

**推荐**：全息红点用 `eyes(...)`（加算混合让红点在暗处也醒目、亮处不刺眼），
蚀刻分划继续用现有的 `entityCutout(...)`（`AttachmentRender` 现在就用这个）。

### 4.3 提交路径

走**活路径** `submit()`，绝不碰 no-op 的 `renderTempPart`：

```java
// 在 BedrockAttachmentModel#submit 里，super.submit(...) 之后
// （准星要盖在镜身之上）
reticleRenderer.submitReticle(ctx, nodeSet, aimingProgress);
```

内部用 `BedrockRenderSnapshot#captureSubtree(...)`（第 9 轮加的通用工具，
第 10 轮撤销黑方块时**特意保留了**）以单节点为根做快照。

### 4.4 可见性还原（第 4/18 轮的教训）

`ModelRendererWrapper` **跨帧共享**，改 `visible`/`hidden` 后必须 `finally` 还原，
否则污染第三人称与物品栏。第 18 轮已有现成写法，照抄即可。

---

## 5. 与「镜内掏空」的关系（完整效果拼图）

| 层 | 状态 | 负责 |
|---|---|---|
| FOV 放大 | ✅ **已完成** | `CameraSetupEvent`（与上游逐行一致） |
| 镜片掏空 | ✅ **第 21 轮已修** | `shouldDrawOcularMask()` 反转 |
| **准星** | ❌ **本方案要做** | `IReticleRenderer` 三层策略 |
| 镜筒黑边暗角 | ⬜ 待做 | 圆形遮罩（scissor / 蒙版贴图，见 FINDINGS §4） |

做完准星，「镜内能看到远处 + 准星真实」就齐了；
镜筒暗角属于锦上添花，可以最后补。

---

## 6. 实施步骤（建议分两步验收）

| 步骤 | 内容 | 风险 |
|---|---|---|
| **P1** | 只做 `ILLUMINATED_ONLY`：把 `*_illuminated` 节点放出来 + 用 `eyes()` 提交 | **极低**（发光节点都是小几何，不可能是遮光板） |
| **P2** | 加 `IReticleRenderer` 四策略 + cube 尺寸过滤，支持老式蚀刻分划 | 中（需防第 9 轮黑方块，靠 §3.3 + §3.4 兜底） |

**P1 能立刻让 31 个混合镜 + 全息镜有准星**，且几乎不可能出黑方块；
P2 再补 `scope_98k` / `scope_retro_2x` 这两个纯蚀刻镜。

---

## 7. 需要你拍板

1. **先做 P1 吗？**（我建议是 —— 低风险、立刻见效、覆盖 31/33 个瞄具）
2. **全息红点用 `eyes()` 加算混合**可以吗？（红点会「透亮」，暗处更明显；
   若你想要「实心不透明红点」，改用 `entityTranslucentEmissive`）
3. **准星是否要随开镜进度淡入**？上游没做（stencil 直接硬切），
   但我们既然要重写，可以做成 `alpha = aimingProgress`，观感更顺。
4. **老式蚀刻镜的遮光板**：确认走「cube 尺寸过滤」这个启发式？
   还是干脆只在 `ILLUMINATED_ONLY` 模式下运行、老式镜就不画分划？

---

## 附：证据索引

| 结论 | 证据 |
|---|---|
| division 被永久隐藏 | 我们 `BedrockAttachmentModel` L100-107（与上游一致） |
| renderTempPart 是 no-op | 我们 L359-363，r18 已确认 |
| `_illuminated` 自动满亮度 | `BedrockModel` L54-57 + `BedrockRenderSnapshot` L168/L181 |
| 满亮度常量 15728880 | `BedrockPart#render` L62-64 |
| 准星节点分类统计 | 129 个 geo 模型全量扫描（本轮） |
| 遮光板 32×32 cube | `scope_1873_6x_geo.json` 的 `division` 骨骼 |
| `eyes` / `entityTranslucentEmissive` 等存在 | 26.2 字节码 `RenderTypes` |
| `captureSubtree` 可用 | 第 9 轮新增，第 10 轮保留 |
