# 第 14 轮进度报告

**日期**：2026-07-25
**本轮处理**：① 除「高爆弹」外所有工作台配方不可见　② 材料数量仅在持有物品时才显示

> ⚠️ **未实机验收**：沙盒无 GPU，无法启动游戏。本轮结论全部来自 26.2 反编译 + 真实配方数据的
> JVM 运行时实验 + 字节码核对。渲染类改动（②）**仍需你实机确认**。

---

## 一、用户反馈原文

> 很神奇，有且仅有"高爆弹"能被查询到合成配方并合成，其余任何要在"装配台"上合成的枪械、配件等
> 都没有任何显示，而且这个高爆弹揭示的问题也很明显，仅在玩家持有合成所需物品时才显示需要物品的
> 个数，否则不显示

这条反馈**信息量极大**。"有且仅有高爆弹"这个细节直接指向了根因——见下。

---

## 二、问题 ①：只有高爆弹可见

### 2.1 突破口：高爆弹到底特殊在哪

先做了一次全量数据统计：

```
总配方数: 172
不含 "#tag" 材料的配方:
  ./attachments/ammo_mod_he.json      <-- 高爆弹，且是唯一一个
```

`ammo_mod_he.json` 的材料是 `minecraft:crying_obsidian` + `minecraft:end_crystal`，
**全是纯物品 ID**。其余 171 条配方至少含一个 `#c:xxx` 标签材料（如 `#c:ingots/copper`）。

**172 选 1 的巧合概率为零** —— 根因必然与「tag 解析」有关。

### 2.2 根因链（逐级反编译证实）

`GunSmithTableIngredientSerializer` 在 Gson 反序列化**当场**调用 `Ingredient.CODEC.parse(...)`。
这在 26.2 上是**错误时机**：

| # | 反编译证据 | 结论 |
|---|---|---|
| 1 | `Ingredient.CODEC = ExtraCodecs.nonEmptyHolderSet(NON_AIR_HOLDER_SET_CODEC)`；`NON_AIR_HOLDER_SET_CODEC = HolderSetCodec.create(Registries.ITEM, ...)` | tag 走 HolderSetCodec |
| 2 | `HolderSetCodec#decode` → `lookupTag(registry, tag)`；未绑定时返回 `DataResult.error("Missing tag: ...")` | tag 没绑定就报错 |
| 3 | `MappedRegistry#get(TagKey)` 读 `allTags`，需 `PendingTags#apply()` 后才有内容 | 绑定动作 = `apply()` |
| 4 | `ReloadableServerResources#loadResources` 只把 `postponedTags` **存起来**；`updateComponentsAndStaticRegistryTags()`（内部 `postponedTags.forEach(PendingTags::apply)`）在 `MinecraftServer#reloadResources` 的 `thenAcceptAsync` 里调用 | **`apply()` 晚于所有 reload listener** |

而我们的配方加载器 `CommonDataManager` **正是一个 reload listener**，
所以它 `apply()` 时 item tag 一律查不到：

```
含 #tag 的材料 → Missing tag → 抛 JsonParseException
   → JsonDataManager#apply 把异常 catch 住，只打一行 error
   → 整条配方被静默丢弃
```

只有不含 tag 的高爆弹幸存。**与用户观察 100% 吻合。**

### 2.3 为什么上游 1.21.1 没这个问题

上游同样在反序列化当场解析，但**上游配方走 vanilla `RecipeManager` 通道**，
其 ops 来自 `ReloadableServerResources` 的 `loadingContext = fullRegistries.lookupWithUpdatedTags()`
——**那是一份已经带上新 tag 的 lookup**。

我们在**第 12 轮**把配方改走自己的 `DataType.RECIPES` 同步通道（因为 26.2 客户端已无完整配方表），
就失去了这份 lookup。**这是 r12 架构改动的一个未预见的副作用**，责任在我。

### 2.4 修复：材料改为「延迟解析」

反序列化时只保存 `"item"` 字段原文，首次取用（开 GUI / JEI 注册 / 合成扣材料）时才解析——
那时无论单人还是多人，tag 都早已绑定。

- `GunSmithTableIngredient`：新增 `rawItem` 字段 + `getIngredient()` 惰性解析（失败只打一次日志，
  **不缓存失败结果**，避免过早的一次调用把材料永久毒化）
- `GunSmithTableIngredientSerializer`：只做结构校验 + 存原文

### 2.5 运行时实验证据

**实验 A：证明根因**（在真实 26.2 类路径下跑 `Ingredient.CODEC.parse`）

```
"minecraft:crying_obsidian"  ->  OK
"#c:ingots/copper"           ->  FAIL: Missing tag: 'c:ingots/copper' in 'minecraft:item'
"minecraft:end_crystal"      ->  OK
```

高爆弹的两个材料恰好都是 OK 的那一类。**根因坐实。**

**实验 B：证明修复**（拿真实的 172 个配方 JSON 跑完整管线）

```
=== STAGE 1: 反序列化（reload-listener 阶段，tag 未绑定）===
配方文件总数      = 172
反序列化成功      = 172      <-- 修复前只有 1 条能活下来
反序列化失败      = 0

=== STAGE 2: 用 vanilla prepareTagReload + PendingTags::apply 真正绑定 tag 后 ===
材料总数            = 460
解析成功            = 460
解析失败            = 0
全部材料可用的配方  = 172
仍有材料缺失的配方  = 0
>>> PASS：tag 绑定后 172 条配方全部可用（延迟解析生效）。
```

第二步用的是 vanilla 真实的 `Registry#prepareTagReload(...)` + `PendingTags::apply()`，
即 `updateComponentsAndStaticRegistryTags()` 内部所做之事，不是模拟。

### 2.6 连带的判空处理

材料现在可能返回 `null`（tag 真的不存在时），所有取用点都已处理：

| 位置 | 处理方式 |
|---|---|
| `GunSmithTableScreen#renderIngredient` | 画空槽，**配方本身仍列出** |
| `GunSmithTableScreen#getPlayerIngredientCount` | 按「持有 0 个」计 |
| `GunSmithTableMenu#doCraft` | **拒绝合成**（fail-closed，防白嫖成品） |
| JEI / REI `getInput` | 返回 `ItemStack.EMPTY` |
| `GunSmithTableSerializer`（网络编码 / MapCodec） | 用 `getIngredientOrThrow()`，此路径要求必须已解析 |

`doCraft` 的 fail-closed 是刻意的：宁可合不出来，也不能因为材料校验被跳过而让玩家凭空拿到枪。

---

## 三、问题 ②：材料数量文字不显示

### 3.1 根因：又是 26.2 的 alpha 静默丢弃

`renderIngredient` 里：

```java
int color = count <= hasCount ? 0xFFFFFF : 0xFF0000;   // 6 位，alpha = 0
```

反编译 `GuiGraphicsExtractor#text`：

```java
public void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean dropShadow) {
   if (ARGB.alpha(color) != 0) {        // <-- alpha=0 直接不画
      this.guiRenderState.addText(...);
   }
}
```

1.21.x 的 `drawString` 对 alpha=0 会自动补不透明，26.2 **静默丢弃**。

注意 `0xFF0000`（红）恰好也是 alpha=0，所以**两种情况都不显示**。
用户看到的"仅在持有所需物品时才显示"，其实显示的是**创造模式那条 `%d/∞` 分支**
（它用的是 8 位的 `0xFFFFFFFF`，所以一直可见）——截图里正是 `3/∞`、`1/∞`。

修复：`0xFFFFFFFF` / `0xFFFF0000`。

### 3.2 顺带发现并修复了 2 处同类遗漏

第 4 轮做过一次全项目 alpha 普查，但**漏了这个文件的 2 处**：

| 行 | 内容 | 原值 | 修正 |
|---|---|---|---|
| 519 | 左侧「预览」标题 | `0x555555` | `0xFF555555` |
| 527 | 「Craft」按钮标题 | `0xFFFFFF` | `0xFFFFFFFF` |

（这解释了截图里预览区标题栏为何空白。）

随后对全项目重新扫了一遍 `text(...)` / `drawModCenteredString(...)` 的 6 位色值，**已无残留**。
字节码核对：`GunSmithTableScreen.class` 中已不含 `16777215` / `16711680` / `5592405` 常量。

---

## 四、验证状态（如实标注）

| 项目 | 状态 | 依据 |
|---|---|---|
| 编译通过 | ✅ | `BUILD SUCCESSFUL` |
| jar 完整性 | ✅ | `unzip -t` 无错误 |
| 根因定位（①） | ✅ **运行时实验证实** | 实验 A |
| 修复有效性（①） | ✅ **运行时实验证实** | 实验 B：172/172、460/460 |
| 字节码核对 | ✅ | 延迟解析字段就位；Serializer 已不引用 Ingredient；alpha 常量已清零 |
| **实机表现** | ❌ **未验收** | 沙盒无 GPU |

**我能给出的保证**：配方数据链路（加载→存活→tag 绑定后解析）已由真实数据+真实 vanilla API 验证。
**我不能保证**：GUI 上的实际观感（文字位置、颜色、槽位对齐）——这些必须你实机看。

---

## 五、本轮改动文件

| 文件 | 改动 |
|---|---|
| `crafting/GunSmithTableIngredient.java` | **重写**：延迟解析（`rawItem` + 惰性 `getIngredient()` + `getIngredientOrThrow()`） |
| `resource/serialize/GunSmithTableIngredientSerializer.java` | **重写**：不再当场解析，只存原文 |
| `client/gui/GunSmithTableScreen.java` | 判空 ×2；alpha 修复 ×3（数量、预览标题、Craft 标题） |
| `inventory/GunSmithTableMenu.java` | `doCraft` fail-closed |
| `compat/jei/category/GunSmithTableCategory.java` | `getInput` 判空 |
| `cn/sh1rocu/tacz/compat/rei/category/GunSmithTableCategory.java` | `getInput` 判空 |
| `crafting/GunSmithTableSerializer.java` | 网络/Codec 路径改用 `getIngredientOrThrow()` |
| `patch_r14.py` | 新增，可重放本轮全部改动 |

---

## 六、TODO

### 请优先验收（本轮）

- [ ] **三种工作台都能列出配方**（不再只有高爆弹）—— 核心
- [ ] 材料数量 `x/y` 在**非创造模式**下可见；材料不足时为**红色**，足够时为**白色**
- [ ] 左侧「预览」标题、「Craft」按钮标题文字可见
- [ ] 实际合成成功，且**材料被正确扣除**
- [ ] 材料不足时**点 Craft 无反应**（不应白嫖到成品）
- [ ] JEI/REI 中配方与材料显示正常
- [ ] **专用服务器**下同样正常（r12 本要修的场景）
- [ ] 分类页签过滤、搜索、按手持物品过滤仍工作

### 未解决（延续自前轮）

1. **瞄准镜镜内透视（PIP）** —— 26.2 已彻底移除 stencil，只能走离屏 RenderTarget +
   每帧二次世界渲染 + 递归渲染防护。属独立大改动，**需先出方案再动手**。
2. **瞄准镜优先级不高于机瞄** —— 需与 PIP 一并处理。
3. **后坐力固定偏左/偏右** —— 已确认与上游 1.21.1 完全一致（`PerlinNoise` 按墙钟时间推进），
   属上游既有行为。你说过不急。
4. **子弹从眼部而非枪口生成** —— 上游既有设计（服务端在射击者眼部生成弹实体），你的推测正确。
5. **副手开枪** —— 上游即不支持，属新功能。
6. 一批 compat 仍是 no-op：Iris/Sulkan、ImmediatelyFast、Shoulder Surfing、Controllable、
   Carry On、KubeJS、AcceleratedRendering。

---

## 七、自我复盘

1. **r12 的架构改动埋了雷**：把配方从 vanilla `RecipeManager` 通道搬到自建同步通道时，
   我只考虑了「客户端拿不到配方表」，**没意识到同时也失去了 `lookupWithUpdatedTags()`
   这份带 tag 的 lookup**。r13 修了 `init()` 缺失，但没发现更底层的 tag 时序问题——
   因为 r13 只看了「为什么 group 为 null」，没去数「到底活下来几条配方」。
   **教训：改数据来源时，要把原通道提供的每一项隐含保障都列出来逐个确认。**

2. **r4 的 alpha 普查不彻底**：漏了本文件 2 处。这次已对全项目重新扫描确认无残留。

3. **用户的观察是这轮的关键**：「有且仅有高爆弹」这个细节，比任何日志都更快地锁定了根因。
   如果只说"配方不显示"，排查会慢得多。
