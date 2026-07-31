# 实现记录：P0 补充 · 供弹具数据系统（Feed Device Data System）

> 日期：2026-08-01 · 对应设计章节：03-B、13-N（N-1）、17 数据总表、06-E（枪膛状态前置）
> 状态：**数据层完成；物品层按计划后置**（遵循"先把抽象层/规则层/嵌套规则层做完，再加物品"的指示）

## 1. 做了什么

### 1.1 抽象层（数据形状，全部新建）
| 类 | 包 | 说明 |
|---|---|---|
| `CartridgeType` (+`RimType`/`PressureClass`) | `industry.api.cartridge` | 口径档案：底缘/膛压档/弹壳长/弹径。**职责=物理兼容**，不含终端弹道 |
| `BulletType` (+`BulletMassClass`) | `industry.api.bullet` | 弹头档案：伤害系数/穿甲/穿透档/曳光/亚音速/扩张。**职责=终点弹道效果** |
| `LoadedRound` (record) | `industry.api.ammo` | 单发完整个体：口径+弹头+壳材质+壳状态+底火类型+腐蚀性+装药偏差。序列化 Codec 内建 |
| `CaseMaterial/CaseState/PrimerType` | `industry.api.ammo` | 弹壳三要素枚举（含复装性/可装性内嵌规则） |
| `GunStateData` (record) | `industry.api.gun` | 枪内运行时态：`Optional<LoadedRound> chamberedRound` + 枪管异物双标记 |

### 1.2 六机构密封体系（抽象层核心）
`FeedDeviceData` sealed interface（permits 显式列表）+ 每机构独立 record + 专属 MapCodec：

| 机构 | 数据形状（需求逐字落地） | 嵌套规则实现 |
|---|---|---|
| `BoxMagazineData` | 有序 List + **LIFO**（栈顶=末位）+ `springFatigue` + `feedLipDamage` 双磨损轨 | 口径/容量/破壳拒装统一走 `rejectsLoad` |
| `TubularMagazineData` | **严格 FIFO** 队列 | 队首出/队尾入 |
| `CylinderData` | **定长槽位数组**（`CylinderSlot` 嵌套 record，三态 EMPTY/LOADED/SPENT 独立格态）+ `alignedIndex` 对齐枪管索引 | 击发格转 SPENT 后索引前进；`ejectAllSpent()`=退壳杆；哑弹格位记忆由 SPENT 格保留个体数据承担 |
| `BeltData` | **FIFO** + `BeltLinkType`（可散/不可散）+ `hasLinkTail`（可否对接下一条弹药箱） | `joinWith()` 校验口径/链型/容量并续接 |
| `StripperClipData` | 固定容量 + `consumed` **一次性消耗**标记 | 弹尽即消耗；中间态拒绝补弹 |
| `EnBlocClipData` | 固定容量 + `ejected` **强制整体弹出**标记 | 最后一发离夹瞬间置弹出态（"叮"事件挂钩点）；废夹拒装 |

多态存档：单一 Codec 入口 `FeedDeviceData.CODEC` 按 JSON 键 `"feed_system"` 分派（DFU `dispatch` 已按源码签名验证）。

### 1.3 规则层
| 类 | 职责 |
|---|---|
| `FeedCompatibility` | 口径/型号兼容判定函数（任务要求 6）：`resolveChamberCartridge`(GunData→口径id，缺省回退 ammoId)、`canChamber`×3、`acceptsFeedDeviceTag`、`canLoadFromDevice`（一条路径组合判定）。**GunData 保持纯 POJO，规则全在此** |
| `FeedItemRules` | 任务要求 4 的执行保障：`requireUnstackable(props, name)` 注册期断言供弹具物品 stacksTo(1)；`isValidNow` 运行期校验 |

### 1.4 注册与装载层
| 类 | 说明 |
|---|---|
| `CartridgeRegistry` | 数据驱动注册表（类物品注册）：代码内置 12 条默认口径（含 TACZ 默认枪包对齐兜底）+ 数据包 JSON 覆盖合并 |
| `BulletRegistry` | 同构注册表，内置 6 类弹头；未知 id 兜底 FMJ 并报 error |
| `IndustryComponents` | 三个 DataComponentType 注册：`taczind:feed_device_data` / `gun_state_data` / `loaded_round`（26.2 typed component，非 NBT bag） |
| `IndustryDataLoader` | `IdentifiableResourceReloadListener` 实现，26.2 新签名 `reload(SharedState, Executor, PreparationBarrier, Executor)`；后台线程解析、主线程重建注册表 |
| `IndustryModule` | 总入口，挂入 `TaCZFabric.onInitialize` |

### 1.5 对已有代码的修改（仅 2 处）
1. **`GunData.java`**（任务要求 6）：新增 `taczind_chambered_cartridge`、`taczind_compatible_feed_device_tag` 两字段与 getter。**纯 POJO 字段，零逻辑侵入**；gson 序列化复用现有 Identifier 适配器。
2. **`TaCZFabric.java`**：onInitialize 内新增一行 `IndustryModule.init()`（置于 GunMod.setup() 后、LRTactical 前）。

### 1.6 数据文件
`data/taczind/cartridge/{44_henry_rimfire, 577_450_martini}.json`、`data/taczind/bullet/{fmj, ap, subsonic}.json` —— 既是默认内容也是数据包作者参考样例。

## 2. 对已完成 P0 部分的字段级修改与职责重划（用户点名说明项）

### 2.1 关于 `AmmoData` —— 审计事实与重划
**审计事实：仓库当前不存在 `AmmoData` 这个类。** TACZ 弹药现状只有两层：`AmmoItemDataAccessor.AmmoId`（弹药物品上的 id 引用）与**写在 GunData.bullet 里的弹道参数**。本次补充后职责表：

| 概念 | 旧载体 | 新载体（本次后） | 职责 |
|---|---|---|---|
| 口径物理兼容 | 散在 ammo id 字符串里（无结构） | **`CartridgeType` + `CartridgeRegistry`** | 能不能装进枪/供弹具 |
| 终点弹道效果 | GunData.bulletData（枪侧，无弹头维度） | **`BulletType`（JSON: data/\*/bullet/）** | 伤害/穿甲/曳光/亚音速等弹头级效果（注入见 C 章，P2 落点） |
| "这一发"个体 | 无 | **`LoadedRound`** | 混装/腐蚀/过压/哑弹判定的个体数据 |
| 工厂弹商品描述 | AmmoId+index（显示层） | 不变（后续如需"工厂弹批次"扩展走 `taczind:loaded_round` 组件模板） | 显示与旧逻辑兼容 |

### 2.2 关于 `GunStateData` —— 从布尔到 `Optional<LoadedRound>`
- 任务要求 5 落地：`chamberedRound: Optional<LoadedRound>`（field: `chambered_round`，codec optionalFieldOf）。
- **与 TACZ `HasBulletInBarrel` 的关系**：原布尔保留为显示/动画镜像，**权威状态迁移到 `taczind:gun_state_data`**。写入规范（进 17 总表）：任何 chamberedRound 变更必须同步镜像布尔；读操作一律以新组件为准。
- 附加字段：`barrel_obstruction`（枪管异物——Squib 留膛/泥沙）+ `obstruction_known`（是否已被检查动作揭示）——这是 E 章隐藏式 Squib 玩法的数据基础，刻意与 chamberedRound 分离（弹膛是弹膛，枪管是枪管）。

### 2.3 与历史 P0（阶段一设计文档）的偏差
| 项 | 文档原设计 | 本次实现 | 原因 |
|---|---|---|---|
| FeedDeviceType 枚举 8 值 | 含 INTERNAL_CLIP、DRUM、PAN | 先落地任务指定的 **6 值**；DRUM/PAN 留 P4 | 按追加指令精确执行；sealed permits 允许低成本追加 |
| 弹匣口径字段 | 设计未显式列出 | 每个 device 增加 `cartridge: Identifier` | 无口径则 tryLoad 兼容校验无处落地（嵌套规则必需） |

## 3. 验证与审计结果（如实记录）

### 3.1 编译验证 —— 环境受限，如实声明
本沙箱无任何 JVM 且网络白名单仅放行 github/pypi/npm：Gradle 发行版、Maven 依赖、完整 JDK（javac）均不可获取（已穷尽 apt/pypi/npm/gh release/codeload/镜像站共 14 条通道）。**完整 `./gradlew compileJava` 验证待有网环境执行**（见 18-open-questions Q-21）。

### 3.2 替代验证（已全部执行通过 ✅）
1. **结构扫描**：27 个新文件括号/包声明/类型声明全部完整。
2. **符号审计**：全部外部 import 与真实类源对表——MC 类对 minecraft-merged-26.2.jar 字节码、DFU/Gson 对 GitHub 源码、TACZ 类对仓库源码，**22/22 通过**。
3. **API 签名验证（字节码级）**：`Identifier.tryParse/fromNamespaceAndPath/getNamespace/getPath`、`Registry.register`、`DataComponentType.builder()+Builder.persistent+build`、`PreparableReloadListener.SharedState.resourceManager()`、`PreparationBarrier.wait()`、GsonHelper 全系、ResourceManager.listResources —— 全部在 26.2 merged jar 常量池中证实存在。
4. **DFU 源码签名校验**：`Codec.dispatch(String, Function<E,A>, Function<A,MapCodec<? extends E>>)` 与 `FeedSystemType::dataCodec` 泛型精确匹配；`mapCodec/validate/optionalFieldOf/comapFlatMap` 均存在（并因此中途将初稿的 `MapCodec.recorder(...)` 写法修正为 `RecordCodecBuilder.mapCodec(...)`，避免依赖高版本 API）。
5. **接口模式纠偏**：初稿用 `SimpleSynchronousResourceReloadListener`（仓库无现存用法），审计后改为仓库自证可用的 `IdentifiableResourceReloadListener` + 26.2 新版 `reload(...)` 签名（与 `ClientAssetsManager` 匿名实现同模式）。
6. **逻辑沙盒**：按最终代码语义复刻六机构算法跑断言——LIFO/FIFO/转轮三态与对齐前进/弹链对接四门校验/桥夹一次性/漏夹强制弹出+废夹拒装，**7 组断言全过**。

## 4. 遗留 TODO（进看板）
- [x] Q-21：CI 编译闭环已建成并逐层修通（2026-08-01）；首轮 javac 真实错误已修复（见 §5）
- [x] 复验一轮：`group()/::new` 错误**并非** sealed 连带（修正当时推断）——真因是 `mapCodec(...).validate(...)` 单链导致 O 泛型推断坠入 Object（javac 候选提示 "equality constraints: Object" 实锤）。六机构统一改原版两步式后，`compileJava BUILD SUCCESSFUL in 1m 8s`、`gradlew exit_code=0`（cf974bd）
- [ ] 物品层（后置计划）：弹匣/桥夹/漏夹/弹链物品注册时必须走 `FeedItemRules.requireUnstackable`
- [ ] `springFatigue/feedLipDamage` 写入点（I 章耐久系统 P3）——当前数据轨已备好
- [ ] 枪内固定仓类机构（管仓/内仓/漏夹装入枪后）的持有者机制（Q-12，P2 状态机落地时配套）
- [ ] 平衡值进 JSON：LoadedRound.SQUIB_RISK_DEVIATION 等语义常量待挪入数据包（P3）
- [ ] 代码瑕疵备查：`FeedItemRules.requireUnstackable` 目前实际行为是"强制覆盖为不可堆叠"而非 Javadoc 所说的"断言并抛异常"（Item.Properties 无法读回配置做真断言）——要么改文档措辞，要么在物品注册处补校验钩子

## 5. 首轮实机编译错误与修复（2026-08-01，CI 回推 gradle-raw.log 实证）

首轮真实 javac 产生 4 类错误（66 条含重复打印），**其中只有 2 类是独立真错**，2 类是连带：

| 错误 | 性质 | 修复 |
|---|---|---|
| `class FeedDeviceData in unnamed module cannot extend a sealed class in a different package`（×6 连带打印） | **独立真错**：Java language rule——无名模块下 sealed 类型的 permits 子类必须与 sealed 类型同包。设计稿把六机构放在 `api.feed.device` 子包，接口在 `api.feed` → 编译决裂 | 六机构全部并入 `api.feed` 同包（`git mv`，`device/` 目录废除）。**包结构最终态以代码为准**，17.7 修订注记之 |
| `rebuild(Map) is not public / cannot be applied`（CartridgeRegistry + BulletRegistry） | **独立真错**：registry 包私有方法被 loader 包调用 | 两处改 `public` |
| 六机构 `no suitable method found for group(...)` ×6 | 疑似连带的错误（sealed 决裂毒化继承树的方法解析） | 待本轮复验——理论上随 sealed 修复自愈 |
| `incompatible types: invalid method reference`（各机构 `::new`）×6 | 同上 | 同上 |

**事中纠偏**：设计阶段的符号/字节码/DFU 源码三层审计全部有效（没有任何一条抽象层签名是错的），
漏网的是两条 **Java 语言规则级** 问题（sealed 同包规则、包私有可见性）——它们不体现在 API 签名里，
符号级审计天然盲区。结论：**高风险 API 继续坚持三层审计；Java 语言规则（sealed/模块/可见性）
纳入新增审计项**，且 CI 编译闭环已常驻（每次 push 自动复验），同类问题的发现成本已降到一轮以内。

本次事件期间遭遇的外部环境故障（如实记录）：GitHub App 令牌在攻坚中段到期（401），
推动本地先沉淀修复；令牌恢复后完成推送、复验与 PR #9 创建。

## 6. 实机编译三轮全谱系（Q-21 关闭定案，2026-08-01）

| 轮 | commit | 错误数（权威回推） | 独立真错 | 结局 |
|---|---|---|---|---|
| 1 | 9eb96ee | 66 | sealed 跨包×6（`cannot extend a sealed class in a different package`）、`rebuild` 包私有跨包×2 | 修复：六机构并入 `api.feed` 同包；registry 两方法改 public |
| 2 | 4f4afe6 | 42 | `mapCodec(...).validate(...)` 单链 → O 推断坠入 Object → 24 group + 18 methodref（**先前几轮推测的"连带"被推翻，诚实更正**） | 修复：六机构统一原版两步式 `SHAPE_CODEC` 直接赋值 + `CODEC=SHAPE_CODEC.validate(...)` |
| 3 | cf974bd | **0** | — | **BUILD SUCCESSFUL in 1m 8s，gradlew exit_code=0** |

**方法论沉淀（进审计清单的永久条目）**：
1. Java 语言规则纳入静态审计：sealed 同包/模块名、跨包可见性——这类约束不进符号表，符号级审计天然盲区。
2. 泛型方法链式调用断目标类型推断：`generic(...).chain()` 的链中段不享受赋值目标多态（poly expression）——DFU Codec 一律照原版两步式写法。
3. CI 编译闭环常驻：每 push 自动复验 + 四层日志回推——同类问题的发现成本已恒定为 ≤1 轮 push。
