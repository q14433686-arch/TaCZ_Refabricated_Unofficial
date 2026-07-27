# 进度报告 · 第 12 轮 2026-07-25

基线：`tacz-26.2-r11-src.zip`。本轮只做 ①（工作台配方同步），②③ 按你的意见暂不动。

---

## ① 工作台无功能（配方列表空） —— 已修

### 根因回顾

`GunSmithTableScreen#classifyRecipes` 从 `CommonAssetsManager.getInstance()` 取配方，
但那是**纯服务端**实例（`recipeManager` 只在 `AddReloadListenerEvent` 里由
`event.getServerResources()` 赋值）。多人客户端上恒为 `null` → 配方列表必然为空。

而且**同一个方法里两种取法混用**：配方走 `getInstance()`（服务端），
方块索引走 `TimelessAPI` → `CommonAssetsManager.get()`（客户端会回退到网络缓存）。
这正是"页签能显示、配方列表却空"的直接原因。

不能照抄上游的 `recipeManager.getAllRecipesFor(...)`：26.2 客户端**没有**完整配方表
（`ClientLevel#recipeAccess()` 返回的 `RecipeAccess` 只有 `propertySet(...)`
和 `stonecutterRecipes()`），原版只下发配方书需要的那部分。

### 修复：接上此前"只声明未接线"的 `DataType.RECIPES` 通道

`DataType` 枚举里早就有 `RECIPES`，但全仓 grep 零命中 —— 服务端没注册、客户端没处理。
本轮把这条通道补齐，**完全复用既有同步管线**，没有新增网络协议：

| 位置 | 改动 |
|---|---|
| `CommonAssetsManager` | 注册 `CommonDataManager<>(DataType.RECIPES, TableRecipe.class, GSON, "recipe", "TableRecipeLoader")`；新增 `getTableRecipe / getAllTableRecipes` |
| `ICommonResourceProvider` | 新增上述两个访问器 |
| `CommonNetworkCache`（客户端） | 新增 `tableRecipe` 缓存、`case RECIPES ->` 分支、reload 时清理 |
| `GunSmithTableScreen` | `getInstance()` → `get()`；由同步来的 `TableRecipe` 构造 `GunSmithTableRecipe` |
| `GunModPlugin`（JEI） | 同样从 `getInstance()` 改为 `get()` |

几点说明：

- **目录选择**：`FileToIdConverter.json("recipe")` 对应 `data/<ns>/recipe/**.json`，
  正是默认枪包配方所在位置（已核对 `data/tacz/recipe/ammo/12g.json` 等）。
- **无需改协议**：`CommonAssetsManager#getNetworkCache()` 是遍历所有已注册 listener
  的通用循环，新 manager 自动被打包进 `ServerMessageSyncGunPack`。
- **JEI 也一并修了**：它同样依赖 `getInstance()`，单人能用只是因为同 JVM 共享了服务端实例，
  连专用服务器时会和工作台一样空。
- **不存在信任客户端的问题**：合成仍是 `ClientMessageCraft` → 服务端
  `GunSmithTableMenu#doCraft`，服务端继续用真实 `serverLevel.recipeAccess()` 校验，
  本轮完全没动这条路径。客户端拿到的配方只用于**界面展示**。

---

## ② 后坐力左右偏 —— 按你的意见不动

已确认与上游 1.21.1 **完全一致**（`PerlinNoise#getValue()` 按墙钟时间推进，
同一噪声周期内符号相同；`SHOOT_X_SWAY_NOISE` 周期 400 ms 短于全自动射速）。
属上游既有行为，非移植缺陷。若日后想改成"每发独立随机"，
入口是 `PerlinNoise#setReverse(true)` 或在 `GunFireEvent` 时重新采样。

## ③ 子弹从眼部而非枪口生成 —— 按你的意见不动

同样是上游既有设计：服务端在射击者眼部生成 `EntityKineticBullet`，
`muzzleRenderOffset` 仅为第一人称本机的视觉补偿。要真正从枪口射出需改弹道生成点
并同步服务端校验，属玩法改动。

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| 服务端注册 `DataType.RECIPES` | ✅ 字节码确认 |
| 客户端 `case RECIPES` 处理 | ✅ 字节码确认（6 处 TableRecipe 引用） |
| 界面改用 `get()` | ✅ `getInstance()` 在该类中已 **0 处** |
| 服务端合成校验未改动 | ✅ 仍走 `serverLevel.recipeAccess()` |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

## 验收重点

- [ ] **单人**：三种工作台应能列出配方、点选、显示材料、成功合成
- [ ] **多人/专用服务器**：同上（这是本轮真正要修的场景）
- [ ] 材料数量显示（`x/y`，够/不够的颜色）是否正确
- [ ] 不同工作台的**分类过滤**是否正确（`RecipeFilter` 仍走原路径）
- [ ] JEI 配方展示应仍正常
- [ ] 缺料时点 Craft 应无反应；够料时正常产出（服务端校验未变）
