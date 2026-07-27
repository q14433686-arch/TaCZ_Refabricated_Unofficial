# 第 15 轮进度报告

**日期**：2026-07-25
**验收结果**：第 14 轮配方修复 ✅ 已由你实机证实

> ⚠️ **未实机验收**：沙盒无 GPU。本轮 ②③ 的根因与修复已有运行时实验 + 字节码证据，
> 但**渲染/UI 的实际观感仍需你实机确认**。① 未修复（说明见下），④ 只出方案未动代码。

---

## 一、本轮结论速览

| # | 问题 | 状态 | 依据 |
|---|---|---|---|
| ① | 瞄准优先级不在瞄准镜上 | ⚠️ **未修复** | 数据链完整，需实机调试，不敢瞎改 |
| ② | 卸除配件无限复制 | ✅ **已修复** | 运行时实验证实根因+修复 |
| ③ | UI 简陋 / 字体溢出 | 🟡 **部分修复** | 修了叠印和溢出，重绘未做 |
| ④ | PIP 镜内渲染方案 | ✅ **方案已出** | `SCOPE_PIP_PLAN.md`，有重大好消息 |

---

## 二、② 卸除配件无限复制（这是个**刷物品漏洞**，本轮最高优先级）

### 2.1 根因：`ItemStack.CODEC` 编码不了 `ItemStack.EMPTY`

反编译 `ItemStack.java` 第 113~124 行：

```java
public static final MapCodec<ItemStack> MAP_CODEC = MapCodec.recursive("ItemStack",
   subCodec -> RecordCodecBuilder.mapCodec(i -> i.group(
      Item.CODEC_WITH_BOUND_COMPONENTS.fieldOf("id").forGetter(ItemStack::typeHolder),
      ExtraCodecs.optionalAlwaysPresentFieldOf(ExtraCodecs.intRange(1, 99), "count", 1)
          .forGetter(ItemStack::getCount),      // <<< count 必须在 [1, 99]
      ...
```

`ItemStack.EMPTY` 的 count 是 **0**，**超出 `intRange(1, 99)`**。

运行时实测（真实 26.2 类路径）：

```
=== ItemStack.CODEC 编码 EMPTY ===
  success = false
  error   = Value must be within range [1;99]: 0

=== ItemStack.OPTIONAL_CODEC 编码 EMPTY ===
  success = true
  结果    = {}
=== OPTIONAL_CODEC 回读空标签 ===
  回读成功 = true, isEmpty = true
```

### 2.2 漏洞是怎么形成的

`ClientMessageUnloadAttachment#handle` 原本这么写：

```java
if (!attachmentItem.isEmpty() && inventory.add(attachmentItem)) {   // ① 先把配件给玩家
    iGun.unloadAttachment(gunItem, attachmentType);                 // ② 再清枪上的 NBT
```

而 ② 内部走 `saveItemStack(ItemStack.EMPTY)` → **必然抛异常** → 枪上配件 NBT 没被清空。

结果：**物品已到手（①成功），配件还在枪上（②失败）= 无限复制**。
和你描述的「按卸除会复制一份，但配件卸不掉」完全一致。

### 2.3 修复（两处，双保险）

**A. `ItemNbtUtils` 改用 `OPTIONAL_CODEC`**（治本）

```java
// save: ItemStack.CODEC -> ItemStack.OPTIONAL_CODEC
// load: 同样对称改为 OPTIONAL_CODEC，这样 {} 能正确回读为 EMPTY
```

验证：

```
=== 修复后：saveItemStack(ItemStack.EMPTY) ===
  成功，结果 = {}
  回读 isEmpty = true
  >>> PASS
```

**B. `ClientMessageUnloadAttachment` 改为 fail-closed**（防御纵深）

```java
iGun.unloadAttachment(gunItem, attachmentType);
if (!iGun.getAttachment(gunItem, attachmentType).isEmpty()) {
    return;                          // 卸载没生效 -> 绝不发物品
}
if (!inventory.add(attachmentItem)) {
    player.drop(attachmentItem, false);   // 背包满 -> 掉地上，不让配件蒸发
}
```

即使将来 NBT 层再出别的问题，也不会再退化成刷物品。

> **影响面提示**：`saveItemStack/loadItemStack` 是全局工具方法，但调用点只有 3 处
> （`GunItemDataAccessor` 第 268/310/320 行），语义一致，已逐一确认无回归风险。

---

## 三、③ UI 问题

### 3.1 已修：「Cmilaft」文字叠印

你截图里 Craft 按钮上那串乱码，是**两段文字画在同一位置**：

- `addCraftButton()` 创建的 `Button` 用的是硬编码 `Component.literal("Craft")`，Button 自身会绘制标签
- 第 527 行又手动 `drawModCenteredString(..., "gui.tacz.gun_smith_table.craft")`，中文是「制造」

两者位置几乎重合 → `Craft` 和 `制造` 叠印 → 看起来像 `Cmilaft`。

修复：按钮改用本地化 key，删掉手动绘制那行。

### 3.2 已修：配方名溢出

`ResultButton` 直接整串绘制名称，无宽度限制。按钮宽 94px、图标占到 x+20，
像 `.30-06 孤星 手炮` 这种长名会画到按钮外面（你截图里能看到）。

修复：用 26.2 的 `Font#plainSubstrByWidth(str, width)`（已反编译确认签名存在）
截断并补 `...`，预留省略号宽度。

### 3.3 未做：UI 整体重绘

参考图 `1783342143_1213321` 与我们当前版本的差距，我识别出这些：

| 项 | 参考图 | 我们 | 工作量 |
|---|---|---|---|
| 左侧预览 | **3D 旋转枪械模型** | 扁平小图标 | 大 |
| 材料区 | 竖排 + 名称 + 数量 | 横排纯图标 | 中 |
| 配方列表 | 彩色品质名 + 图标 | 已接近 | 小 |
| 页签栏 | 图标清晰、有选中态 | 已接近 | 小 |

**左侧 3D 模型是主要差距**。当前 `renderLeftModel` 里是这样的（现存注释已经写明）：

```java
// 26.2 GUI extractor owns item extraction; scaled custom GUI item rendering requires a full
// submit-state rewrite, so keep the item visible at the computed center.
graphics.item(result, ...);   // 退化成画个 16x16 图标
```

要做成参考图那样，需要走 §四 里 PIP 框架同款的离屏渲染路子（`OversizedItemRenderer` 就是范例）。
**建议与 PIP 一起做**，因为技术栈完全相同，分开做等于同样的调研做两遍。

### 3.4 关于「防字体溢出」

你说「这貌似在你之前就可能存在」——**确实如此**，`ResultButton` 从最初移植就没做截断。
但我本可以在第 1 轮做 UI 时就发现，没发现是我的疏漏。本轮已修这一处；
其他 GUI 组件我还没逐个排查，如果你看到别处也溢出，告诉我具体位置。

---

## 四、④ PIP 镜内渲染方案 —— **有重大好消息**

完整方案见 **`SCOPE_PIP_PLAN.md`**（随源码包交付）。核心结论：

### 4.1 26.2 反而比 1.21.x 更容易做

因为**官方自带了一套 PIP 离屏渲染框架**：

```
net/minecraft/client/gui/render/pip/PictureInPictureRenderer.class
net/minecraft/client/gui/render/pip/OversizedItemRenderer.class
```

关键 API（`RenderSystem` 第 73/75 行）：

```java
public static GpuTextureView outputColorTextureOverride;
public static GpuTextureView outputDepthTextureOverride;
```

### 4.2 最关键的一条证据：它对**世界渲染**也有效

`LevelRenderer#addAlwaysOnTopPass` 自己就在用（第 500~512 行）：

```java
RenderSystem.outputColorTextureOverride = mainRenderTarget.getColorTextureView();
RenderSystem.outputDepthTextureOverride = mainRenderTarget.getDepthTextureView();
RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(...);
featureFrame.executeAlwaysOnTop();
RenderSystem.outputColorTextureOverride = null;
RenderSystem.outputDepthTextureOverride = null;
```

**这不是 GUI 专属 hack，而是引擎在世界渲染管线里切换渲染目标的正规手段。**
第 10 轮我判断「26.2 无 stencil，PIP 是唯一出路但很难」——**现在看这条路比预想好走得多**。

### 4.3 圆形蒙版替代 stencil

`RenderPipeline.Builder` 确认无 stencil 配置项，但有：
`withFragmentShader` / `withColorTargetState`（混合）/ `withBindGroupLayout`（绑蒙版纹理）。

推荐做法：自定义片元着色器采样蒙版贴图，`out.a = mask.r`。
**比 stencil 更好**——边缘能羽化，不是硬锯齿边。

### 4.4 放大倍率：数据已就位，无需新增字段

TA31 的 display 里已有 `"zoom": [2.5]`、`"fov": 45.0`；
`ClientAttachmentIndex#getViewsFov()` 已实现，缺省回退逻辑也在。

### 4.5 风险（必须正视）

| 风险 | 严重度 | 对策 |
|---|---|---|
| 递归渲染爆栈 | 🔴 | 静态 `isRenderingScope` 守卫 |
| **性能：世界渲染 ×2** | 🔴 | 降分辨率 / 隔帧 / 仅瞄准时 / 配置开关 |
| Iris 光影冲突 | 🟠 | 检测到就降级为静态贴图 |

**风险「性能」需要你拍板**：真镜内渲染就是要多渲一遍世界，物理上绕不开。
上游 1.21.1 没做，很可能也是这个原因。

### 4.6 建议分阶段

**P1 = 搭离屏 RT + 递归守卫，镜内先渲染纯色。**
成本极低，但能验证「`outputColorTextureOverride` 在世界渲染阶段确实可用」这个核心假设。
如果 P1 失败，后面全要推翻——**早发现早改**。

---

## 五、① 瞄准优先级：为什么我没改

我排查了整条链路，**数据和代码都是对的**：

| 检查项 | 结果 |
|---|---|
| `FirstPersonRenderGunEvent` 第 153~174 行分支逻辑 | ✅ 装了瞄具就走 `scopePosPath + scopeViewPath` |
| 枪模型 `scope_pos` 节点 | ✅ 存在（aa12_geo.json 已确认） |
| 瞄具模型 `scope_view` 节点 | ✅ 存在（scope_acog_ta31_geo.json 已确认） |
| `views` 缺省值 | ✅ `{1}` → `viewIndex = 0`，合法 |
| 与上游 1.21.1 对照 | ✅ 逻辑一致 |

数据链完整，说明问题在**运行时状态**。最可疑的是 `currentViewIndex` 这个
**static 字段**（第 71 行），它在所有枪之间共享，切枪时不重置——
但这只是推测，我**没有证据**。

**我选择不改。** 第 9 轮我在没验证的情况下动了瞄具渲染，造成黑方块回归、第 10 轮才撤销。
同样的错误不该犯第二次。

**需要你配合定位**：换不同枪械/瞄具时，是否「某些枪一直不对、某些枪一直正常」，
还是「同一把枪有时对有时不对」？如果是后者，且**切换枪械后出现**，那就能坐实
`currentViewIndex` 的静态状态污染，我就有把握改了。

---

## 六、本轮改动文件

| 文件 | 改动 |
|---|---|
| `util/ItemNbtUtils.java` | `CODEC` → `OPTIONAL_CODEC`（save + load 对称） |
| `network/message/ClientMessageUnloadAttachment.java` | 改为 fail-closed：先卸载→校验→再发物品；背包满则掉落 |
| `client/gui/GunSmithTableScreen.java` | Craft 按钮用本地化文本；删除重复绘制的标题 |
| `client/gui/components/smith/ResultButton.java` | 名称防溢出截断 + 省略号 |
| `SCOPE_PIP_PLAN.md` | **新增**：PIP 完整技术方案 |

字节码核对：`OPTIONAL_CODEC` ×2、`plainSubstrByWidth` ×1、`drop` ×2，无残留裸 `CODEC`。

---

## 七、TODO

### 请优先验收

- [ ] **卸除配件不再复制**，且配件确实从枪上卸下来了（最重要，这是刷物品漏洞）
- [ ] 背包满时卸配件 → 配件掉在地上，不凭空消失
- [ ] Craft 按钮显示正常的「制造」，不再叠印乱码
- [ ] 长名称配方（如 `.30-06 孤星 手炮`）不再溢出按钮
- [ ] 第 14 轮的配方修复没有回归

### 需要你决策

1. **PIP 性能预算**：接受多渲一遍世界？还是默认关闭、玩家自行开启？
2. **下一轮做什么**：
   - (a) P1 验证 PIP 核心假设（低成本、高价值）
   - (b) UI 整体重绘（含左侧 3D 模型，与 PIP 技术栈相同）
   - (c) 先集中修 ①（需要你先提供上面 §五 的现象细节）
3. **Iris 降级策略**：装光影时关闭镜内渲染回退静态贴图，可以吗？

### 未解决（延续）

1. 瞄准镜优先级（①）—— 待现象细节
2. UI 整体重绘 / 左侧 3D 模型
3. 后坐力固定偏左右 —— 上游既有行为，你说不急
4. 子弹从眼部生成 —— 上游既有设计
5. 副手开枪 —— 上游不支持，属新功能
6. 一批 compat 仍是 no-op

---

## 八、自我复盘

1. **② 是真漏洞，本该更早发现**。`ItemStack.CODEC` 对 EMPTY 失败这件事，
   在第 1 轮做 NBT 迁移时就该测——当时只测了「存非空物品能不能读回来」，
   没测边界值 EMPTY。**教训：codec 迁移必须测空值/边界值。**

2. **③ 的叠印问题肉眼可见，我却没发现**。因为我从来只能看你的截图，
   而前几轮截图里 Craft 按钮区域要么被遮挡要么没细看。
   这类「一眼可见」的问题，你直接指出来最有效率。

3. **① 我这次忍住没改**。数据链查完是对的，就不该凭猜测动手。
   第 9 轮的教训（没验证就改→回归→下一轮撤销）值得记一辈子。
