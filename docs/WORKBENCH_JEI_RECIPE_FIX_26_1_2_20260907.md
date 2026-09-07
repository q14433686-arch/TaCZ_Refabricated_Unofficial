# 其他枪包工作台 JEI 无配方 / 合成无结果 —— 26.1.2 线诊断与修复（2026-09-07）

> 对应工单症状（用户报告，维护者确认本线 26.1.2 独有、26.2 / 1.21.11 / Neo 26.1.2 均无）：
> 「The other workbenches from other gunpacks wont show their recipes by JEI.
> Even when I craft them, it did not show the result.」
>
> 即：其他枪包里的工作台，① JEI 里不显示该工作台的自定义配方分类；② 工作台 GUI 里
> 配方列表为空、合成拿不到结果。

---

## 一、根因（已定位，2026-09-07 晚 23:00 定案）

**根因：26.1.2 线移植时误删了 `cn/sh1rocu/tacz/util/RecipeCompat`（旧枪包原版配方兼容层）
及其在 `DelegatingPackResources` / `PathPackResources` 里的接入，导致旧布局枪包
（1.20.1 / 1.21.1 时代）放在 `data/<ns>/recipes/`（复数）目录里的原版合成配方
（工作台物品合成等）对 26.x 原版 `RecipeManager`（只扫单数 `recipe`，注册表常量）
完全不可见 ⇒ 合成台/JEI 无配方、合成不出结果。**

### 证据链（维护者复现日志 `latest.log`，2026-09-07 上传）

1. 复现环境：TACZ `1.1.8+fabric.26.1.2.R3` + 单人 + 三个枪包
   （`duyupack+1.21.1.zip`、`KhanPowder_v0.8.99_hotfix.zip`（1.20.1 布局）、
   `tacz_default_gun`），全部正常加载，无版本检查拒绝；
2. 数据管线正常：`[TACZ Recipe Viewer] Refreshing after gun-pack sync
   (5 table(s), 293 recipe(s))`，JEI 二次注册成功（`tacz:jei` 107ms/27ms）——
   即 TACZ 自有同步通道（工作台 GUI / 自定义配方分类）工作正常；
3. 症状为 (a)：其他枪包的**工作台物品**在原版合成台/JEI 无合成配方——
   而默认工作台（配方在 mod jar 的 `data/tacz/recipe/`，单数）正常。
   两代旧包（1.20.1 的 KhanPowder、1.21.1 的 duyupack）的原版配方都在复数
   `recipes/` 目录；26.1.2/26.2/1.21.11 三线的原版 `RecipeManager` 都只扫
   单数 `recipe`（26.2/1.21.11 反编译源码 `Registries.RECIPE =
   createRegistryKey("recipe")` + 本仓 `GunSmithTableMenu` 字节码核验注释），
   **唯一差异是 26.2 线带 `RecipeCompat` 兼容层（PackResources 层把
   `recipes/`→`recipe/` 回退映射 + 旧 JSON 格式自动转换：`result.item`→`result.id`、
   `result.nbt`→`components.minecraft:custom_data`、`{"tag":...}`→`"#tag"`、
   `{"item":...}`→id），26.1.2 移植时把它当作 26.2 专用件删掉了**；
4. 顺带确认：日志里 duyupack 14 条 `Couldn't parse data file ... No key fabric:type`
   是**原版 RecipeManager 的 MapCodec 通道**解析 TACZ 工作台配方（`tacz:nbt`/
   `{"tag":...}` 旧式材料）的预期噪音——工作台 GUI/JEI 自定义分类走 TACZ 自己的
   Gson 延迟解析通道（第 14 轮），不受影响，与工单无关（26.2 线同样存在）。

### 修复（与 26.2 逐文件一致）

- 新增 `src/main/java/cn/sh1rocu/tacz/util/RecipeCompat.java`（原样移植 26.2）；
- `src/main/java/cn/sh1rocu/tacz/util/forge/DelegatingPackResources.java`、
  `PathPackResources.java` 换用 26.2 版（基类逻辑与本线旧版相同，仅增
  RecipeCompat 接入：`listResources` 复数目录回退列出 + 重映射 + 原版配方转换；
  `getResource` 单数未命中时回退复数）。
- 符号核验（26.1.2 反编译源码）：`PathPackResources.listPath(String,Path,List,
  ResourceOutput)` 公开静态存在；`PackResources.ResourceOutput` 为
  `BiConsumer<Identifier, IoSupplier<InputStream>>`；`Identifier.fromNamespaceAndPath`
  存在；`TableRecipeManager` 与 26.2 逐字一致（两线同形，无双重扫描回归风险）。
- 影响面：仅枪包 PackResources（mod 自身 jar 数据不经此层）；只对
  `minecraft:*` 类型配方做 JSON 转换，`tacz:gun_smith_table_crafting` 等自定义
  配方原样透传；`SERVER_DATA` 侧才启用，客户端资源侧不受影响。

### 已排除项（防重复劳动）

- **枪包 PackType（`810fb04f`）——行为无操作**：26.1.2 原版 `PackRepository` 无
  PackType 字段/无类型过滤；`Pack.readMetaAndCreate` 元数据与当前版本同源（恒
  compatible）；资源解析按查询时 type。该行保留为与 26.2 的一致性卫生项。
  （26.2 分支 `810fb04f` 注释描述的是 26.2 原版仓库行为，不能照搬。）
- 枪包 `dependencies` 版本检查：日志证实三包全部通过（无 `Mod version mismatch`）。
- `RecipeViewerReloadBridge` 回退护栏（对齐 26.2）：真实加固，已保留。

## 二、（历史记录）初轮误判

> ⚠️ **2026-09-07 晚间更正（重要）**：
> 维护者反馈单人可复现且本修复无效。随后我逐类读取了 26.1.2 原版反编译源码
> （`ma4z-sys/Minecraft-26.1.2`，Mojang mapping）验证 `PackType` 机制，**结论：
> 本线的 packType 一行在 26.1.2 原版下是行为无操作（no-op）**，不是本 bug 的修复：
>
> 1. 26.1.2 `PackRepository` **根本没有 `PackType` 字段**，`discoverAvailable()`
>    对 source 返回的 Pack 不做任何类型过滤（与 26.2 的仓库结构不同）；
> 2. `Pack.readMetaAndCreate(location, resources, packType, config)` 里
>    `currentPackVersion` 与 `readPackMetadata` 的 metadata 请求**用同一个
>    `packType` 参数**，`PackMetadataSection.forPackType` 两种类型的 section 名
>    都是 `"pack"`（mod 的 `DelegatingPackResources#getMetadataSection` 按名匹配，
>    两种类型都返回同一份 `packMeta`），`supported_formats` 范围与“当前版本”同源
>    ⇒ `PackCompatibility` 恒为 compatible，Pack 创建永远成功；
> 3. mod 自有的 `DelegatingPackResources` / `PathPackResources`（Forge 移植件，
>    与 26.1.2 原版 `AbstractPackResources` 体系一致）按**查询时传入的 type**
>    解析 `<pack>/<type.getDirectory()>/<ns>/<path>`（`assets`/`data`），与 Pack
>    存储类型无关；
> 4. 集成服务器走 `WorldOpenFlows → ServerPacksSource.createPackRepository`
>    （我们的 `ServerPacksSourceMixin` 命中）→ `MinecraftServer.configurePackRepository`
>    → `reload()`；枪包 `PackSelectionConfig(required=true)` 恒入选。
>    服务端重载监听 mixin（`ReloadableResourcesMixin` → `lambda$loadResources$2`，
>    6 参 `SimpleReloadInstance.create`）与 26.2 逐字相同、目标存在。
>
> 也就是说：**单人 26.1.2 的「枪包 → 服务端数据仓库 → 数据扫描 → 同步」链路在
> 代码/原版层面与 26.2 对齐，静态层面找不到与工单症状对应的 26.1.2 独有缺陷**。
> 真实断点需要复现环境的运行期日志才能定位（见 §三 末尾的追问清单）。
> 两处改动仍保留：packType 一行与 26.2 对齐（卫生项，行为无操作）；
> bridge 护栏是真实加固。
>

### 1. 枪包 `PackType` 按仓库动态设定（卫生对齐 26.2 `810fb04f`，**本线下为 no-op**）

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

## 三、待定位：复现环境信息需求（2026-09-07 追加，单人复现确认）

静态排查（含 26.1.2 原版反编译源码逐类核验）未找到与症状对应的 26.1.2 独有缺陷，
下一步需要复现环境的运行期证据。请提供：

1. **症状二选一（关键）**：
   - (a) 其他枪包的**工作台物品**：在原版合成台/JEI 里**没有合成配方**、合成不出该工作台；
     而**默认 TACZ 工作台（gun_smith_table）能正常合成**？
     → 若是：指向该枪包的 `data/<ns>/recipe/`（工作台物品原版配方）或服务端
       `RecipeManager` 读不到该枪包数据（选择性故障，大概率枪包级：版本检查/目录结构）。
   - (b) 工作台**物品能合成出来**，但**打开工作台后**配方列表为空/合成不出东西、
     JEI 无该工作台的自定义配方分类（默认工作台正常或同样空白？）
     → 若是：指向 `CommonAssetsManager`（block index / table recipes / tabs）数据链。
   - 请同时说明：**默认 TACZ 工作台在你复现环境里是否正常**（正常/同样空白/未测）。
2. **枪包身份**：装了哪个/哪些第三方枪包（名字 + 版本 + 目录还是 zip +
   其 `gunpack.meta.json` 的 `dependencies` 原文，若方便）。
3. **`latest.log` grep 结果**（复现进过存档的那次启动）：
   - `Start scanning for gun packs`（含紧随的 `- <包名>, Main namespace:` 行与
     `Found N possible gunpack(s)`）；
   - `Mod version mismatch`（整包被版本检查拒绝）；
   - `Missing metadata in pack` / `Failed to read pack`（26.1.2 原版
     `Pack.readPackMetadata` 的新日志行）；
   - `there is no corresponding data file` / `Failed to parse recipe`（数据加载器）;
   - `[TACZ Recipe Viewer]`（`Refreshing after gun-pack sync (N table(s), M recipe(s))`
     的 N/M 数值：为 0 = 服务端数据缓存本身就是空的）。
4. 复现用的构建：R3 发布版，还是本 PR 分支的构建？JEI 版本号。

## 四、原验证清单（对两处已保留改动）

1. `./gradlew build`（CI 门）。
2. 单人 + 至少一个第三方枪包：bridge 护栏生效路径日志（`[TACZ Recipe Viewer]`
   回退日志每连接至多一次；断线重连后刷新路径可再进入）。
3. 专用服务器回归：packType 一行在双 JVM 场景为幂等，行为应与修复前一致。

*证据级别（AGENTS §2）：26.1.2 原版行为=ma4z-sys/Minecraft-26.1.2 反编译源码
（Mojang mapping）逐类阅读：PackRepository / ServerPacksSource / Pack /
PackMetadataSection / PackType / MultiPackResourceManager /
ReloadableServerResources / MinecraftServer / WorldOpenFlows / IntegratedServer；
本线改动=同形移植 + 符号核验，编译门待过、**实机未验**。*
