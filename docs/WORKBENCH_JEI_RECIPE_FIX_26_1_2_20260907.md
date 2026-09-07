# 其他枪包工作台 JEI 无配方 / 合成无结果 —— 26.1.2 线诊断与修复（2026-09-07）

> 对应工单症状（用户报告，维护者确认本线 26.1.2 独有、26.2 / 1.21.11 / Neo 26.1.2 均无）：
> 「The other workbenches from other gunpacks wont show their recipes by JEI.
> Even when I craft them, it did not show the result.」
>
> 即：其他枪包里的工作台，① JEI 里不显示该工作台的自定义配方分类；② 工作台 GUI 里
> 配方列表为空、合成拿不到结果。

---

## 一、根因（已修复）

### 1. 枪包 `PackType` 在单人模式下被写死为 `CLIENT_RESOURCES`（主修）

**断点**：`GunMod#onInitialize` 第 23 行
```java
GunPackLoader.INSTANCE.packType = side == EnvType.CLIENT ? PackType.CLIENT_RESOURCES : PackType.SERVER_DATA;
```
只在初始化时按环境类型写一次。而本仓的 `AddPackFindersEvent` 由两个 mixin 分别在
**客户端构造资源包仓库**（`MinecraftMixin`，`CLIENT_RESOURCES`）和
**服务器构造数据包仓库**（`ServerPacksSourceMixin`，`SERVER_DATA`）时各触发一次。
单人模式下两者在**同一个 JVM**、共用同一个 `GunPackLoader.INSTANCE`：

- 客户端仓库：拿到 `CLIENT_RESOURCES` 类型的枪包 ✓（assets/ 可见）；
- 集成服务器仓库：拿到的仍是 `CLIENT_RESOURCES` 类型的枪包 ✗。

于是**服务端读不到任何枪包的 `data/`**（`index/blocks`、`data/blocks`、table 配方、
过滤配置）。而工作台的 GUI 标签页/配方、JEI 分类、合成校验走的都是
`CommonAssetsManager`（服务端 reload 后由 `ServerMessageSyncGunPack` 同步到客户端的
那份缓存）⇒ 症状与工单逐条对上：

| 工单症状 | 机制 |
|---|---|
| JEI 不显示其他枪包工作台的配方 | 服务端 blockIndex/tabs/tableRecipe 为空 → 同步包里没有这些工作台的条目 → JEI 无分类可建 |
| 工作台里合成不出结果 | 客户端 GUI 的 `getCommonBlockIndex(blockId)` 为空 → 无标签页、无配方条目、`TableRecipe` 匹配不到 |
| 工作台物品本身还能合出来 | 工作台物品的**原版**合成配方在 mod 自身 `data/tacz/recipe/`、`data/lrtactical/recipe/`（mod jar 直出，不经过枪包仓库），不受影响 |
| 26.2 / 1.21.11 / Neo 无此问题 | 26.2 已带 `810fb04f` 修复；本线从 1.21.11 移植时该提交未带入 |

**修复**（`src/main/java/com/tacz/guns/init/CommonRegistry.java`）——逐字移植 26.2 `810fb04f`：
```java
public static void onAddPackFinders(AddPackFindersEvent event) {
    GunPackLoader.INSTANCE.packType = event.getPackType();
    event.addRepositorySource(GunPackLoader.INSTANCE);
}
```
每次仓库查询按该仓库的实际 `PackType` 设定，客户端/服务端各拿对应类型的包。

**专用服务器场景**：客户端与服务端是两个 JVM，各自的 `GunPackLoader` 实例各自
`setup()`（客户端 JVM=CLIENT、服务器 JVM=SERVER），本断点不触发；该场景下 26.1.2 与
26.2 的 gunpack 数据管线代码逐文件相同（见下节排查记录），若仍复现则属环境差异，
按 §三 的日志 grep 定位。

### 2. `RecipeViewerReloadBridge` 回退无护栏、`clear()` 复位不全（加固，对齐 26.2 现版本）

同步完成后的 `requestReload()` → `tick()` 里，若 JEI/REI 的轻量刷新钩子（反射探测
`JeiLifecycleEvents.AFTER_RECIPES_UPDATED` / `reloadPlugins`）都不可用，会回退到整段
`reloadResourcePacks()`。26.1.2 线此前：

- 回退无次数限制（两个 viewer 同时失败、或 tick 再次进入时会二次触发整段重载）；
- `clear()` 只复位 `reloadRequested`——若断线时回退仍在进行（`reloadInProgress=true`
  未复位），重连后 `tick()` 的守卫条件永远为真，**刷新路径整体死掉**。

**修复**（`src/main/java/com/tacz/guns/client/compat/RecipeViewerReloadBridge.java`）：
补齐 26.2 现版本的 `resourceFallbackUsed` 一次性护栏（每次连接最多整段重载一次，
断线 `clear()` 复位）与 `clear()` 的 `reloadInProgress` 复位。功能代码与 26.2 逐行一致，
仅保留本线 26.1.2 版本文案（JEI/REI 版本号说明）。

---

## 二、排查记录（排除项，防重复劳动）

1. **枪包 `dependencies` 版本校验静默拒绝其他枪包 —— 排除**。
   fabric-loader 0.19.3（本线 loader）`SemanticVersionImpl#compareTo` 只比数值段与
   prerelease，**build metadata（`+...`）从不参与排序** ⇒ `1.1.8+fabric.26.1.2.R3`
   对任何 `>=1.1.8` 形式的枪包版本要求都成立；`lrtactical` 硬编码 `0.3.0` 的分支逻辑
   与 26.2 逐字相同。
2. **「工作台物品自身的合成配方在 26.1.2 坏了」—— 排除**。
   mod 自带的 `data/tacz/recipe/ammo_workbench.json`、`data/lrtactical/recipe/smith_table.json`
   均为 `minecraft:crafting_shaped` 现代 `result:{id,components}` 格式、位于 26.1.2 的
   单数 `recipe/` 目录（`GunSmithTableMenu#getRecipe` 内字节码核验注释确认该目录即
   26.1.2 vanilla 目录）。
3. **26.2 ↔ 26.1.2 全量 java 增删改比对（15A/57D/184M）**：所有增/删文件均为
   渲染/scope/iris/AR/KubeJS/playeranimator 域 + `util/RecipeCompat.java`（D，
   26.2 格式专用辅助，删除正确）+ `ForgeConfigSpecAccessor`（A，配置域）；
   gunpack 数据管线（`resource/`、`network/`、`init/`）无文件级漂移，
   差异只剩本记录修复的两处方法级缺口。
4. **`fabric.mod.json` / `mod_version`**：两线同为 `1.1.8+...` 核心版本，版本面等价。
5. **上游 issue**：Sh1roCu/TACZ-Refabricated 无开放的 workbench/JEI 配方类 issue
   （历史 #51/#8/#27/#40/#20 均已关闭且不相关）；本仓近期 issue 区全部是 PR。

---

## 三、实机验证清单（本沙箱无 JDK，未编译未实机）

1. `./gradlew build`（CI 门）。
2. **单人模式**（主修路径）：安装 TACZ 26.1.2 + 至少一个第三方枪包（如 LRTactical /
   Arcana），进任意存档：
   - 合成并放置该枪包的工作台，打开 GUI：标签页与配方应正常列出、可合成出结果；
   - JEI（按 R）：应出现该工作台的自定义配方分类；
   - 对照日志：服务端不再出现枪包 `data/` 相关缺失；`latest.log` grep
     `Start scanning for gun packs` 应能看到所有枪包，且 GUI 不再空白。
3. **专用服务器**（回归）：客户端/服务端各装同版本 + 同枪包，验证 JEI 与工作台
   行为与修复前一致（本修复对双 JVM 场景为幂等：事件携带的类型与 setup() 写的一致）。
4. 若专用服务器仍复现（本线代码面已排除），在**服务端** `latest.log` grep：
   - `Mod version mismatch`（枪包 `dependencies` 不满足，整包被拒）；
   - `Expected to find pack` / `Failed to parse`（枪包目录/元数据问题）；
   - `there is no corresponding data file`（某枪包缺 data/）；
   在**客户端** `latest.log` grep `[TACZ Recipe Viewer]`（刷新路径日志：
   `Refreshing after gun-pack sync (N table(s), M recipe(s))` 里 N/M 若为 0，
   说明同步包本身为空 → 服务端没加载到枪包；若 N/M 正常但 JEI 仍空 →
   记录当时 JEI 版本号，对照 `RecipeViewerReloadBridge` 的 fallback 日志）。

*证据级别（AGENTS §2）：断点=26.2 分支提交 810fb04f 维护者注释 + 本线符号逐一核验；
本线=同形移植、编译门待过、**实机未验**。*
