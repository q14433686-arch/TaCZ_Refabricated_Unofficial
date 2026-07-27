# 进度报告 · 第 11 轮 2026-07-25

基线：`tacz-26.2-r10-src.zip`。

> 本轮把两个问题的根因都查清了，但**只交付诊断、未改代码**——理由见文末"为什么这轮不动手"。

---

## ① 工作台无功能（配方列表空），而 JEI 正常 —— 根因已确认

### 结论：客户端拿不到配方，`DataType.RECIPES` 是一条**从未接线的死枚举**

上游 1.21.1 的取配方方式是**客户端自己的** recipe manager：

```java
RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();
List<RecipeHolder<GunSmithTableRecipe>> recipeList =
        recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING);
```

移植版改成了从 `CommonAssetsManager` 取：

```java
CommonAssetsManager assetsManager = CommonAssetsManager.getInstance();   // ← 只在服务端赋值
if (assetsManager != null && assetsManager.getRecipeManager() != null) { ... }
```

而 `recipeManager` 字段只在 `onReload(AddReloadListenerEvent)` 里被赋值：

```java
INSTANCE.recipeManager = event.getServerResources().getRecipeManager();
```

**这是纯服务端路径。** 于是：

| 场景 | JEI | 工作台界面 |
|---|---|---|
| 单人（同 JVM，`INSTANCE` 非 null） | ✅ | 取决于时序，可能空 |
| 多人客户端（`INSTANCE == null`） | 走自己的插件路径 | ❌ **必然为空** |

这解释了你看到的"JEI 能识别配方、工作台里却什么都没有"。

### 为什么不能照抄上游

26.2 的客户端**没有完整配方表**（反编译确认）：

- `ClientLevel#recipeAccess()` 返回的是 `RecipeAccess` 接口，
  只有 `propertySet(...)` 与 `stonecutterRecipes()` 两个方法；
- 客户端实现 `ClientRecipeContainer` 里只有 `itemSets` 和 `stonecutterRecipes`；
- 也就是说 `getAllRecipesFor(...)` / `getRecipes()` 这类"列举全部配方"的能力
  **在 26.2 客户端已不存在**，原版只把配方书需要的那部分下发。

所以正确做法是 **mod 自己把配方同步到客户端**。

### 已有但未接线的基础设施

`DataType` 枚举里**已经有** `RECIPES`：

```java
public enum DataType {
    GUN_DATA, ATTACHMENT_DATA, AMMO_INDEX, GUN_INDEX, ATTACHMENT_INDEX,
    RECIPES,          // ← 声明了
    RECIPE_FILTER, ATTACHMENT_TAGS, ALLOW_ATTACHMENT_TAGS, BLOCK_DATA, BLOCK_INDEX,
}
```

但全仓 grep `DataType.RECIPES` **零命中**：
- 服务端没有任何 manager 以该类型注册（`CommonAssetsManager#reloadAndRegister` 里没有它）；
- 客户端 `CommonNetworkCache` 的 `switch(type)` 里也**没有 `case RECIPES`**。

即：这条同步通道被声明了却从未接上。**这就是要补的那一块。**

### 修复方案（建议下轮实施）

1. 服务端：为 `TableRecipe`（`data/recipes`）注册一个
   `CommonDataManager<>(DataType.RECIPES, ...)`，随其它数据一并下发；
2. 客户端：`CommonNetworkCache` 增加 `case RECIPES ->` 存入 `Map<Identifier, TableRecipe>`，
   并在 `ICommonResourceProvider` 上暴露访问器；
3. `GunSmithTableScreen#classifyRecipes` / `getSelectedRecipe` 改为经
   `CommonAssetsManager.get()`（会在客户端回退到网络缓存）读取，
   与同文件里 `TimelessAPI.getCommonBlockIndex(...)` 的取法保持一致
   —— 注意目前**同一个方法里两种取法混用**，这正是 bug 的来源。
4. 合成本身走 `ClientMessageCraft` → 服务端 `GunSmithTableMenu#doCraft`，
   服务端仍用真实 `RecipeManager` 校验，**不存在信任客户端的问题**。

---

## ② 开镜后坐力动画固定偏左/偏右 —— 根因已确认

`FirstPersonRenderGunEvent#applyShootSwayAndRotation`：

```java
rootNode.offsetX += SHOOT_X_SWAY_NOISE.getValue() / 16 * progress * (1 - aimingProgress);
rootNode.additionalQuaternion.mul(Axis.YP.rotation(SHOOT_Y_ROTATION_NOISE.getValue() * progress));
```

关键在 `PerlinNoise#getValue()` 的实现——它**不是按开火事件推进的，而是按墙钟时间**：

```java
long periodTime = new Date().getTime() - prevTime;
long repeat = periodTime / periodMs;      // periodMs = 400 (X 向) / 100 (Y 旋转)
...
double x = easeInterpolate((double) partialTime / (double) periodMs);
return (float) (prevNum * (1 - x) + num * x);
```

也就是说：**同一时刻取值恒定**。若两次开火落在同一个噪声周期内，
`getValue()` 返回的符号（左/右）就是同一个 —— 表现为"这一阵子都往左偏 / 都往右偏"。
`SHOOT_X_SWAY_NOISE` 周期 400 ms，全自动射速通常远快于此，
于是**一个连发序列里整串后坐力都朝同一侧**，与你的观察吻合。

至于"疑似和移动方向有关"：移动本身不参与这段计算，
更可能是移动时你更容易注意到偏移方向，或是与行走动画的相位叠加产生了错觉。
（这一点我没有实机手段验证，标注为**未证实**。）

**注意**：这段逻辑与上游 1.21.1 **完全一致**，属于上游原有行为，不是移植缺陷。
若你希望"每发独立随机左右"，那是**行为变更**而非 bug 修复，需要你确认是否要改
（做法：开火时调用 `PerlinNoise#setReverse(true)`，或改为每次 `GunFireEvent` 重新采样）。

---

## ③ 关于曳光弹：你的推测很可能是对的

你说"枪模型偏右、但子弹实体从胸口位置生成，曳光弹挂在子弹上所以看着不从枪口出"——
这与代码一致：`EntityKineticBullet` 由服务端在**射击者眼部位置**生成，
`muzzleRenderOffset` 只是给**第一人称本机**做的视觉补偿（`isFirstPerson` 分支）。
补偿量对了，起点依然是眼部而非枪口；第三人称/他人视角则完全没有补偿。

这属于**上游的既有设计**（1.21.1 同样如此），不是 26.2 移植引入的。
若要"真正从枪口射出"，需要改子弹生成位置并同步给服务端做弹道校验，
属于玩法层面的改动，建议单独立项。

---

## 为什么这轮不动手改代码

①的修复要动**网络同步协议**（新增一类数据的序列化/下发/客户端缓存），
②是**行为变更**需你拍板，③属于新功能。

我这里没有实机验证手段，而最近两轮（r9 的瞄具黑方块、r4 的手臂还原）
都是"没验证就改、反而更糟"。网络协议改错的代价比渲染更高——
轻则配方列表仍空，重则加入世界时握手异常。

因此本轮交付**诊断 + 明确方案**，代码保持 r10 状态（即已撤销黑方块回归的干净版本）。
请你确认下面几点后，我下一轮直接实施：

- [ ] ① 按上述四步补 `DataType.RECIPES` 同步通道 —— **是否开工？**
- [ ] ② 后坐力左右偏：**保持上游行为**，还是改成每发独立随机？
- [ ] ③ 子弹从枪口生成：**是否要做**（涉及服务端弹道，改动较大）？

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS（与 r10 代码一致） |
| 本轮代码改动 | 无（仅新增本报告） |
| 实机画面 | ❌ 未做（沙盒无 GPU） |
