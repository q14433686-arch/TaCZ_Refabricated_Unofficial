# 实现记录 · P1 制造地基——数据层与规则层（A-1 材料树 / A-2 热度条与工序 / A-8 公差系统）

> 日期：2026-08-01 ｜ 分支：arena/019fb90c-tacz-refabricated-unofficial ｜ 状态：**数据层+规则层完成，实机编译全绿（BUILD SUCCESSFUL in 1m 12s @ f189908）**
> 本记录对应设计文档：02-A-manufacturing.md（A-1/A-2/A-8 节）、16-roadmap.md（P1）、17-data-structure-master-table.md（17.8 落地登记）、21-performance-engineering.md。
> 范围纪律：按阶段二铁律"先抽象层→规则层→嵌套规则层，后物品"——本轮**不含**任何物品/方块/GUI/网络包；B 章弹药四要素、C-2.1/2.4 注入最小闭环（需 Q-06 射击入口 Spike）属 P1 后续批次。

---

## 1. 先读后写结论（写码前对 TACZ 源码的核查）

| 核查对象 | 结论 | 决策 |
|---|---|---|
| `com.tacz.guns.resource.pojo.data.gun.GunHeatData`（max/per_shot/cooling_multiplier/over_heat_time…） | 这是 **G 章枪管过热** 系统（每发积热、停射冷却后坐手感），与 A-2"工件离炉降温"**不同源** | 不复用不混淆；G 章落地时才消费它。A-2 热度条全新建 |
| `com.tacz.guns.resource.pojo.data.recipe.TableRecipe`（`{materials, result}`） | TACZ 枪匠台配方的极简风格确认；但机器**工序≠MC Recipe**（机器内过程，不进 RecipeManager） | 本轮只做数据驱动"工序注册表"；RecipeSerializer（`taczind:*_recipe`）留到物品/机器落地批次（与 Q-03 一并确认可扩展性） |
| TACZ 全库材料/公差/工件概念 | 不存在 | A-1/A-2/A-8 全新增，**对 TACZ 代码零侵入**（本轮无 TACZ 文件改动） |

## 2. 本轮交付（做了什么）

### 2.1 A-1 材料树（材料定义数据层）
- `api/material/MaterialCategory`（ore/metallurgy/chemical 三层枚举，大小写/连字符包容解析）
- `api/material/MaterialType`：**A-1 三元数据**——`tier`(0–5)、`workTags`（可加工自由标签，新机器工艺=新标签零代码扩展）、`toleranceBonus`（A-8b 材料加成源头）+ `upstream`（材料树的边）+ `itemHint`（物品化预留，允许悬空）
- `registry/MaterialRegistry`：纯数据包驱动（与 CartridgeRegistry 的"代码默认+JSON 覆盖"有意区分——材料树是本模组自有内容无旧包包袱；空表一律拒绝式兜底不崩档）
- **24 条材料 JSON**（`data/taczind/material/`）：T0 矿×3、T1/T2 冶金×6、T3 冶金×3、化学×9（含黑火药/两种底火/硝化链**概念级抽象**——仅"顺序与用途"命名，安全红线审查通过：无比例、无工艺参数）

### 2.2 A-2 热度条与工序（热加工数据层+规则层）
- `api/heat/HeatUnits`（0–1000 游戏化炉温刻度语义）+ `HeatBand`（可工作带/理想带四界 + `qualityGradient` 带内梯度）
- `api/heat/HeatWorkData`：**工件 DataComponent**（`heat/processId/progress/qualitySeed/material`，设计原文五字段全量落地）→ 注册 `taczind:workpiece`（工件物品同样必须不可堆叠）
- `api/heat/CoolingCurve` + `registry/CoolingCurveRegistry`：冷却介质阶梯表数据驱动（air/water/oil 三条 JSON），内置 air 常量作失包安全网
- `api/process/StationType`（anvil/crucible/quench_tank/hand_tool，P1 四席）+ `WorkProcessType`（工序定义：station/入料/产出/温度带/锤次数/每锤耗温/坩埚盛放 tick/质量抖动/理想带加成）+ `registry/WorkProcessRegistry`（入料悬空只告警不拒载——模块化数据包组合友好）
- **4 道 T1 工序 JSON**：锻打除渣（生铁→熟铁 8 锤）、枪管毛坯（12 锤）、坩埚钢（非锤击 1200t）、闭锁毛坯（10 锤）
- **规则层（纯函数、无世界依赖、可单测）**：
  - `HeatRules`：`cool(曲线,热,ticks)` 任意时长一次结算 O(档数)（21 章性能承诺的落地：方块实体 ≥5tick 批量调用，禁止每工件每 tick）；`StrikeOutcome` 四象限（理想/弱/过冷/过热）；`canWork`
  - `ProcessRules`：准入（材料匹配+工序延续性）、单锤结算（推进进度+扣温）、可收锤判定；返回不可变 `StrikeResult`
  - `QualityRules`：收锤质量公式 `clamp(梯度+理想带加成+jitter(seed),0,1)`，**qualitySeed 防刷**（同工件重进存档结果恒等，A-2 明确要求）+ 五档展示标签

### 2.3 A-8 公差系统（TS 全公式数据层+规则层）
- 三表数据驱动（`data/taczind/tolerance/`）：
  - `machine_ts/`×6（A-8a 手摇 25–45 → T5 铣削 80–95，含 stability_kind 声明）
  - `grade_band/`×5（A-8d 土造→名匠四系数全量：散布/故障/耐久/初速偏差）
  - `weights/default_gun.json`（A-8c 枪管0.35/枪机0.25/机匣0.15/簧系0.10/其它0.15）
- `registry/ToleranceTables`：三表合管；**分级带失包硬兜底**（人造 Crude 语义档，宁可全线"土造"不可计算断裂）；权重缺失则是显式交互拒绝（不静默兜底——两种缺数据的语义区分是有意的）
- `ToleranceRules`（纯函数）：
  - `partTs(window, 材料加成, 稳定性, 模具TS, σ, seed)` 全公式：`clamp(机器基础+材料+稳定×10+(模具TS-50)/10+N(0,σ),0,100)`；返回 **`PartTsBreakdown` 分项明细 + `dominantFactor()` 权重主因**——这是 P3 F 章事故报告"权重主因"展现的前置投资（同一数据源）
  - `assembleTs` 运行时归一化加权（缺键按在场者归一；全缺=坏装配返回 0）
  - `gradeOf` 分级带线性查询（≤10 条线性优于区间树，21 章立场）

### 2.4 loader 重构（P0 两目录硬编码 → SPECS 通用多目录模式）
`IndustryDataLoader` 重构为 `DirSpec<T>(dir, parser, sink)` 声明式清单，八个目录统一调度；后台解析、barrier 后主线程 sink。P0 的目录约定文档化（一类实体一目录；带层级子目录 `tolerance/machine_ts/` 验证可行）。

## 3. 验证记录（按替代验证→实机编译顺序，全部如实）

1. **逻辑沙盒（Python 复刻 Java 语义）：20 组断言全过**——冷却分段结算（含跨档与室温收敛）、梯度四界、收锤恒等防刷、partTs 同 seed 恒等 + **300 样本手搓(25-45)/T5铣削(80-95)均值分层检验（差值>40）**、装配归一化、五档边界命中
2. **结构扫描**：46 个 java 文件包声明/括号/Codec 两步式铁律扫描全部合规
3. **实机编译（CI 回推 gradle-raw.log 实证）**：
   - 轮 1（288fe48）：1 独立错误 ×3 打印——`cannot infer type arguments for ParsedDir<>`（`new ParsedDir<>(spec, ...)` 在方法实参语境是钻石推断黑洞）
   - 轮 2（f189908）：提取 `parseSpec` 泛型助手让通配符捕获流入 → **BUILD SUCCESSFUL in 1m 12s，exit_code=0**

## 4. 偏差及原因（与设计文档的差异，全部登记）

| 设计 | 实现 | 原因 |
|---|---|---|
| A-1 表列出"板材系列 T3"等完整树 | 本轮 JSON 已含 T3/T4 材料定义（24 条），但**仅定义**；对应工艺（轧机/电解/化学釜）属 P5 | 数据定义先行零成本；物品/机器后置纪律 |
| A-8 公式"随机抖动 N(0, σ=阶段值)" | σ 做成代码常量分级（5/3.5/2.5/1.5/1.0）而非 JSON | σ 是平衡基石，故意不让数据包魔改（数据驱动承诺的例外，与设计讨论一致——tier 语义防篡改）；文档已注明 |
| A-8a"机器基础 TS 区间" | partTs 以 seed 在窗口内均匀抽样 | 设计原文"机器基础 TS(表 A-8a)"仅给区间；抽样语义是公式可执行化的最小解释，已入 impl-log 备审 |
| A-2 quality_seed "(防刷)" | seed==0 时按无种子处理（确定性回归，抖动归零） | 种子来源（物品起源信息派生）属物品层代码，本轮预留语义；0 值行为显式定义避免隐式随机 |

## 5. 遗留 TODO（进看板）

- [ ] B-1~B-7 弹药四要素 + 复装台数据层（P1 批次 2：黑火药/黄铜/Boxer 范围；与 LoadedRound/PrimerType 现有字段对接）
- [ ] C-2.1/2.4 初速与散布注入最小闭环——**前置 Q-06 射击总入口 Spike**（最高优先级开放问题）+ Q-04 Modifier 自定义来源确认
- [ ] A-3 四台小作坊机器+模具的数据形状（mold 组件 `{}die_type,wear,ts` 设计稿已定型）——待"物品层开门"批次
- [ ] N-6 拆解入口数据层（逆向残页逻辑与 A-7 联动，P1 末批或随 A-7）
- [ ] HeatWorkData 组件虽已注册但尚无物品挂载（按纪律后置）；`FeedItemRules` 同款不可堆叠断言需覆盖工件物品注册点
- [ ] 工序 output 悬空零件（`taczind:part_barrel_blank` 等）→ 物品化映射表（物品层批次统一处理，当前设计允许悬空已文档化）
- [ ] CI contamination 已知：settings 探针在 gradlew 的 sed 脏树下 rebase 失败噪音（不影响闭环；v3 workflow 待用户替换后自然统一）

## 6. 安全红线自查

本轮全部内容为**游戏化抽象数值**（炉温单位、锤击次数、TS 系数）：不引用任何现实冶金温度、材料配比、化学工艺参数；化学链仅保留"流程顺序与用途对应"的命名级抽象（与设计文档 00 章安全承诺一致）。
