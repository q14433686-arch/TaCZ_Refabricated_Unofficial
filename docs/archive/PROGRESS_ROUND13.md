# 进度报告 · 第 13 轮 2026-07-25

基线：`tacz-26.2-r12-src.zip`。**修复我第 12 轮引入的回归。**

---

## 所有配方不可见（含 JEI）—— 已修

### 我第 12 轮漏了什么

r12 把配方来源从服务端 `RecipeManager` 改为经 `DataType.RECIPES` 同步的
`TableRecipe` POJO，通道本身是通的，但**漏了一步初始化**。

`GunSmithTableResult` 有两种状态：

```java
private ItemStack result = ItemStack.EMPTY;   // 真正的产物
private Identifier group = null;              // 分类页签
@Nullable private RawGunTableResult raw = null;   // 未解析的原始数据

public void init() {
    if (raw != null) {
        GunSmithTableResult r = RawGunTableResult.init(raw);
        this.result = r.getResult();
        if (group == null || group.equals(EMPTY_GROUP)) this.group = r.getGroup();
        this.raw = null;
    }
}
```

`GunSmithTableResultSerializer` 反序列化 `"type": "gun"/"ammo"/"attachment"` 时，
只构造 `RawGunTableResult`（因为此时物品注册表/枪包索引未必就绪），
**真正的 `ItemStack` 与 `group` 要等 `init()` 才解析出来**。

原先走 `RecipeManager` 路径时，`CommonAssetsManager#onReload` 里有一句
`recipe.init()` 兜底；我改用同步数据后，**没有任何地方再调用它**。

后果：每条配方的 `getResult()` 恒为 `ItemStack.EMPTY`、`getGroup()` 为 `null`，
于是在 `recipeKeys.containsKey(groupName)` 处**全部被过滤掉** ——
表现就是工作台和 JEI 里一条配方都没有。这比 r12 之前更糟（原先 JEI 在单人是能用的），
是我的责任。

### 修复

在三处由 `TableRecipe` 构造 `GunSmithTableRecipe` 之后补 `recipe.init()`：

| 位置 | 说明 |
|---|---|
| `GunSmithTableScreen#classifyRecipes` | 列表构建 |
| `GunSmithTableScreen#getSelectedRecipe` | 选中项 |
| `GunModPlugin#registerRecipes`（JEI） | JEI 展示 |

`init()` 是**幂等**的（首次执行后 `raw = null`，再调用直接返回），重复调用无副作用。

---

## 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` / `build` | ✅ PASS |
| 界面字节码含 `GunSmithTableRecipe.init` | ✅ 2 处（列表 + 选中项） |
| JEI 字节码含 `GunSmithTableRecipe.init` | ✅ 1 处 |
| 服务端合成校验未改动 | ✅ 仍走 `serverLevel.recipeAccess()` |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

## 验收重点

- [ ] **JEI 应重新看到配方**（先确认回归已消除）
- [ ] **单人**：三种工作台列出配方、点选、显示材料、成功合成
- [ ] **多人/专用服务器**：同上（r12 本来要修的场景）
- [ ] 分类页签过滤、材料 `x/y` 数量与颜色是否正确

## 教训记录

这轮的问题在于：我把数据源从"已完成初始化的 `RecipeManager` 配方"换成了
"刚反序列化的原始 POJO"，却默认两者可以直接互换。
后续若再替换数据来源，必须先确认目标对象**是否需要额外的生命周期调用**
（本例即 `init()`）。已在 `HANDOVER.md` 的注意事项中补充此条。
