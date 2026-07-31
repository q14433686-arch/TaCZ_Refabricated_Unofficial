# 第 A 章（02）· 制造与工业链路系统

> 篇幅最大的一章，是其余一切系统的上游。分 A-1 材料树、A-2 手工阶段、A-3 小作坊阶段、A-4 初级工业、A-5 中级工业、A-6 重度自动化、A-7 蓝图/图纸系统、A-8 质量公差系统。每节按"现实原理→游戏化抽象→道具机器清单→数据结构→与 TACZ 衔接"五段展开。

---

## A-1. 五级材料树总设计

### 现实原理简述

真实枪械制造史是材料与工艺协同演进的历史：熟铁/低碳钢时代只能做前装滑膛；坩埚钢与后来的平炉钢让后膛闭锁强度达标；黄铜因延展性好且弹性密封膛室成为定装弹壳标准；铅合金做弹头；铝合金做轻量化机匣；无烟火药（硝化棉系）的普及依赖硫酸/硝酸的工业化生产（ contacts 法与硝化工艺的概念级抽象）。本设计只借用**工艺流程顺序与材料用途对应关系**，不引用任何真实冶金温度、酸碱配比或化学工艺参数。

### 游戏化抽象方案

材料树分三层：**冶金中间品 → 化学中间品 → 枪械零件 → 成品**。每种材料有三个元数据字段：`等级 tier`、`可加工列表`、`公差加成`。零件无配方冲突时优先"按材料分轨"：同一零件可用不同等级材料制造，成品属性不同（如"熟铁枪管" vs "工具钢枪管" vs "镀铬枪管"）。

### 材料树总表

| 层 | ID 建议 | 材料 | 上游 | 主要工艺 | 主要用途 | 阶段 |
|---|---|---|---|---|---|---|
| 矿 | minecraft:* | 铁矿、铜矿、煤矿、硫磺矿(新增可配置)、硝石矿(新增)、铝土(联动或新增)、铅矿(联动或新增) | — | 采掘 | — | T0 |
| 冶金 | `taczind:ingot_pig_iron` | 生铁 | 铁矿+燃料 | 熔炉 | 灌钢原料、铸坯 | T1 |
| 冶金 | `taczind:ingot_wrought_iron` | 熟铁 | 生铁 | 锻打除渣（砧+热度条） | 前装枪管、民用件 | T1 |
| 冶金 | `taczind:ingot_steel` | 钢 | 生铁+锻打/坩埚 | 坩埚钢、渗碳(抽象) | 闭锁件、撞针毛坯 | T2 |
| 冶金 | `taczind:ingot_tool_steel` | 工具钢 | 钢+合金剂 | 合金熔炼 | 拉刀、钻头、模具、撞针 | T2 |
| 冶金 | `taczind:ingot_alloy_steel` | 合金钢(铬钼系抽象) | 钢+铬/镍中间品 | 合金熔炼 | 高级枪管、枪机 | T3 |
| 冶金 | `taczind:ingot_brass` | 黄铜 | 铜+锌(联动或新增) | 熔炼配比(抽象) | **弹壳**、弹壳毛坯 | T2 |
| 冶金 | `taczind:ingot_lead_alloy` | 铅合金 | 铅+锑锡(抽象) | 熔炼 | **弹头铸件** | T2 |
| 冶金 | `taczind:ingot_aluminum` | 铝 | 铝土(电解抽象) | 电解槽(T3+) | 轻量机匣、铝弹壳 | T3 |
| 冶金 | `taczind:plate_steel` 等 | 板材系列 | 对应锭 | 轧机(T3) | **冲压机匣** | T3+ |
| 化学 | `taczind:dust_sulfur` | 硫磺粉 | 硫磺矿/下界 | 粉碎 | 化学链 | T2 |
| 化学 | `taczind:dust_niter` | 硝石粉 | 硝石矿/堆肥转化 | 粉碎 | 氧化剂(抽象) | T2 |
| 化学 | `taczind:black_powder` | 黑火药(粗/细两档) | 硫+硝+炭 | 碾磨混合(危险工序!见F章厂规) | 前装/早期定装发射药 | T1 |
| 化学 | `taczind:acid_sulfuric` (流体) | 硫酸(抽象) | 硫磺+接触釜(抽象) | 化学釜 T4 | 硝化链 | T4 |
| 化学 | `taczind:acid_nitric` (流体) | 硝酸(抽象) | 硝石+酸釜(抽象) | 化学釜 T4 | 硝化链 | T4 |
| 化学 | `taczind:nitrocotton` | 硝化棉(抽象) | 棉纤维+混酸(抽象) | **硝化釜(危险!)** | 无烟发射药基料 | T4 |
| 化学 | `taczind:smokeless_powder` | 无烟发射药(单基/双基两档,抽象) | 硝化棉+安定处理(抽象) | 造粒干燥产线 | 全系现代弹药 | T4 |
| 化学 | `taczind:primer_compound` | 底火装药(腐蚀/无腐蚀两种抽象) | 化学釜 | 微量封装 | 底火 | T2(腐蚀版)/T4(无腐蚀版) |
| 零件 | 见 A-2~A-6 各节 | 枪管/机匣/枪机/簧/握把…… | 上述材料 | 各阶段机床 | 组装 | 全 |

### 与 TACZ 衔接
- **新增独立子系统**：注册在 `taczind` 命名空间下的 Item/Block/Fluid。不侵入 TACZ 注册表。
- 机器方块实体参考 TACZ 现有 `block/entity` 包风格（已有枪匠台 BlockEntity 范式会按 `<flavor>Pickup</flavor>`自定义）。
- 配方体系：新增自定义 `RecipeSerializer`（`taczind:press_recipe`、`taczind:lathe_recipe`、`taczind:reactor_recipe`…），**优先做成数据包可配**，结构模仿 TACZ `resource/pojo/data/recipe` 的极简风格（materials+result）。

---

## A-2. 手工阶段（T1）：熔炉、坩埚、砧板+锤+热度条、手摇工具

### 现实原理简述
锻造的核心是**把金属加热到可塑区间再成型**：过冷打不动（开裂），过热烧损（氧化脱碳）。传统铁匠靠颜色估温。坩埚炼钢则是把生铁在密闭坩埚内重熔除渣获得均质钢。

### 游戏化抽象方案
- **热度条机制**：新增 `Heat` 状态（0–1000，游戏化"炉温单位"）。砧上加工要求工件温度落在目标区间（如锻造区间 650–900）。工件离炉后按环境梯度降温（tick 级查表，非逐 tick 计算，见性能节）。
- **锻打工序**：砧板交互是节奏小游戏：每次锤击消耗温度，温度跌出区间必须回炉。锻打工序有"完成度进度 + 类星露谷质量判定"：在理想区间收锤得高质量。
- 手摇工具（手摇钻、手拉弓弦锉之类）用于低精度零件，无动力需求但产出**公差上限低**（见 A-8，TS≤45）。

### 道具/机器清单

| ID 建议 | 名称 | 类型 | 用途 |
|---|---|---|---|
| `taczind:crude_furnace` | 粗炼炉 | 方块 | 生铁冶炼，煤/木炭燃料 |
| `taczind:crucible` | 坩埚炉 | 方块(多方块核心) | 生铁→坩埚钢，渗碳(抽象) |
| `taczind:blasting_bellows` | 鼓风箱 | 方块附件 | 提高炉温上限与升温速率 |
| `taczind:smithing_anvil` | 锻砧 | 方块 | 热度条锻打工序 UI |
| `taczind:tongs` | 火钳 | 工具 | 拿取热工件（无火钳拿热件掉血/加速降温） |
| `taczind:hand_hammer` / `hand_hammer_fine` | 锻锤/整形锤 | 工具 | 粗成型/精修 |
| `taczind:hand_drill` | 手摇钻 | 工具方块 | 低精度孔加工（发火孔、销孔） |
| `taczind:quench_tank` | 淬火槽 | 方块(装水/油) | 热处理工序末端（T1 简化版） |

### 数据结构建议（DataComponent）
工件物品（半成品）挂 `taczind:workpiece` 组件：

| 字段 | 类型 | 说明 |
|---|---|---|
| `heat` | int | 当前炉温单位 0–1000 |
| `process_id` | ResourceLocation | 正在进行的工序配方 ID |
| `progress` | float 0–1 | 工序完成度 |
| `quality_seed` | long | 收锤质量判定的随机种子(防刷) |
| `material` | ResourceLocation | 材料 ID（中间品混料时用） |

### 与 TACZ 衔接
完全独立子系统。UI 参考 Create/农夫乐事的多步骤合成界面范式；热度 tick 用**方块实体每 5 tick 批量处理容器内工件**而非每工件每 tick。

---

## A-3. 小作坊阶段（T2）：手动冲压机、手摇车床、手动膛线拉刀、退火炉

### 现实原理简述
19 世纪枪匠铺的三大件：脚踏/手摇车床（切削外圆、镗孔）、冲床（薄板成型、弹壳引伸）、**膛线拉床**（用单钩拉刀逐次切出螺旋膛线——一次走刀只切一丝，反复几十次）。退火用于消除加工硬化便于下道工序。

### 游戏化抽象方案
- **手动冲压机**：曲柄交互（按住=冲压一次），弹壳/小冲压件专用。引入"模具(Mold/ Die)"物品：冲弹头模、冲弹壳模、冲底火室模……模具自身有耐久与公差（模具磨损会遗传到零件！见 A-8 公差血统）。
- **手摇车床**：GUI 内"走刀次数 vs 精度"的取舍：少刀快出=公差差。
- **手动膛线拉刀**：进度条小游戏，玩家控制拉速稳定度（节奏按钮），稳定度 → 膛线光洁度 → 枪管精度上限。产出零件带缠绕距字段（见 C 章缠距系统）。
- **退火炉**：消除零件"加工硬化"标记；不做退火的零件进入下道工序有开裂损耗率。

### 道具/机器清单

| ID 建议 | 名称 | 类型 | 用途 |
|---|---|---|---|
| `taczind:hand_press` | 手动冲压机 | 方块 | 弹壳引伸、小件冲压；可插模具 |
| `taczind:mold_shell` / `mold_bullet_tip` / `mold_receiver_blank` | 系列模具 | 物品 | 模具装配；自带耐久与 TS 衰减 |
| `taczind:hand_lathe` | 手摇车床 | 方块(带 GUI) | 枪机、撞针、管件外圆加工 |
| `taczind:bench_rifler` | 手动拉膛线机 | 方块 | 枪管膛线切削，含缠距参数选择 |
| `taczind:annealing_furnace` | 退火炉 | 方块 | 去加工硬化 |
| `taczind:rifling_cutter` | 拉刀 | 工具物品 | 消耗品，工具钢制造，分口径型号 |

### 数据结构建议
- 模具物品组件 `taczind:mold`：`{die_type:string, wear:int, max_wear:int, ts:int}`（模具 TS 会分给产品，见 A-8）。
- 枪管零件组件 `taczind:barrel_part`：`{caliber:string, length:int(mm游戏值), twist:int(1:X 的 X), bore_finish:float, material:resource, ts:int}`。

### 与 TACZ 衔接
独立机器；但拉膛线的**缠距字段将成为枪包 JSON 增量字段**的来源——产出成品枪时写回 `GunData` 增量对象 `taczind.barrel.twist`，驱动 C 章弹道模型。

---

## A-4. 初级工业阶段（T3）：接入 Create 动力网

### 现实原理简述
第一次工业革命对枪械业的意义：**动力驱动+可互换零件**。机床由蒸汽/水力天轴驱动，皮带传动到各工位。

### 游戏化抽象方案
- T2 机器全面推出**动力版**：动力冲压机、动力车床、机械拉膛线机。必须从**顶面/侧面接入 Create 旋转轴**（实现 Create 的 `IRotate`/`KineticBlockEntity` 范式——通过 Create API，见 M 章依赖声明）。
- 每台动力机器定义：**SU 容量需求**（挂网即占用应力）、**最佳 RPM 区间**（过慢无产出增速，过快增加机器磨损与公差波动——对应"动力稳定性"公差因子）。
- 动力网络过载（Create 原生 overstressed）=全线停机，符合"动力中断即停产"。

### 道具/机器清单

| ID 建议 | SU 需求(建议) | 最佳RPM | 用途 |
|---|---|---|---|
| `taczind:power_press` | 1024 | 128–256 | 冲压弹壳/机匣件，速率=RPM/k |
| `taczind:power_lathe` | 768 | 96–192 | 车削件自动走刀 |
| `taczind:kinetic_rifler` | 512 | 64–128 | 拉膛线自动化 |
| `taczind:rolling_mill` | 1536 | 64–128 | 锭→板材（冲压机匣前置） |
| `taczind:gearbox_assembly_jig` | —(无动力手工夹具) | — | 部件试装与公差检测 |

### 数据结构建议
动力机器方块实体字段：`{su_need:int, rpm_opt_min:int, rpm_opt_max:int, wear:int, process_queue:list, energy_stability:float(0-1, 由 RPM 波动滑动平均计算)}`。`energy_stability` 参与成品 TS（A-8 公式）。

### 与 TACZ 衔接
独立方块实体；Create 联动用软依赖（`ModList` 检测，无 Create 时动力机器不可合成但 T2 手摇版保留——保证无 Create 也能玩到 T2 封顶，见开放问题 Q-02）。

---

## A-5. 中级工业阶段（T4）：无烟火药时代

### 现实原理简述
无烟火药的量产是化学工业（强酸工业化→硝化纤维）的副产品；枪械由此进入高初速、低残渣时代。精密热处理（可控气氛渗碳、淬火回火曲线）让闭锁件寿命数量级提升。流水线+专用工装夹具让半自动装配成为可能。

### 游戏化抽象方案
- **化学反应釜**：多方块结构（釜体+搅拌+加热+流体接口），处理硫酸、硝酸、硝化棉、无烟发射药造粒四条"化学配方"（全部抽象为"投入物+时间+搅拌 RPM →产物"，无任何真实工艺参数）。
- **危险工位**：硝化工位引入"工艺安全"玩法——断电搅拌=局部过热=产物报废（不炸工厂，损失物料+釜体耐久），呼应 F 章风险美学但不惩罚基地建设。
- **精密热处理炉**：可编程"温度-时间曲线"两段小游戏，输出"热处理质量分"，直接加成闭锁件耐久上限。
- **传送带+机械臂装配台**：半成品在 Create 传送带上流转，机械臂自动执行装底火/装簧/铆接，人工只做质检。

### 道具/机器清单

| ID 建议 | 类型 | 用途 |
|---|---|---|
| `taczind:chem_reactor` | 多方块(2×2×3) | 抽象化学中间品生产 |
| `taczind:nitration_vat` | 多方块(危险工位皮肤) | 硝化棉生产 |
| `taczind:powder_granulator` | 方块 | 无烟药造粒（决定"燃烧速率档"：快/中/慢，见 B 章） |
| `taczind:precision_heat_treat` | 方块 | 精密热处理 |
| `taczind:assembly_arm_station` | 与 Create 机械臂绑定 | 自动装配工位 |
| `taczind:inspection_bench` | 方块 | 质检：读取零件 TS，不合格返工 |

### 数据结构建议
化学釜 BE：`{recipe_id, progress, stirring_rpm, temp_game:int, risk_accum:float}`。弹药物料组件 `taczind:powder_lot`：`{burn_rate:enum(FAST/MID/SLOW), lot_quality:float, lot_id:long}` —— 批次系统让"同一批弹药一致性"成为 C 章散布修正来源（【AI补充】批次一致性：同 lot 弹药散布系数 -2%）。

### 与 TACZ 衔接
- 无烟药弹药 = 扩展 TACZ 弹药 JSON 的增量字段 `taczind.propellant`（见 B 章）。
- 装配线最终产物调用 `GunItemBuilder`（TACZ 现成 builder）生成带 `GunId` + 本模组组件（TS 总评、部件序列号表）的完整枪支。

---

## A-6. 重度自动化阶段（T5）：双工艺路线

### 现实原理简述
AK 的传奇一半在**冲压机匣**：薄钢板冲模成型、铆接锁定，公差要求低、单件成本递减、对机床依赖小；AR 系**精密铣削机匣**：切削成型、配合紧密、精度上限高但吃机床与工时。两条路线是历史上真实存在的"成本 vs 极限性能"权衡。

### 游戏化抽象方案
两条**并行解锁**的机匣产线，玩家按资源与目标选择（也可双修）：

| 维度 | 冲压机匣线（AK 式） | 精密铣削机匣线（AR 式） |
|---|---|---|
| 上游材料 | 钢板（轧机） | 合金钢锻坯（锻压） |
| 核心机器 | `taczind:hydraulic_press_line`（多工位冲压） | `taczind:cnc_mill`（铣削中心） |
| 单件耗时 | 快（并行多工位） | 慢 |
| 产线占地 | 大（多工位） | 小（单机多工序） |
| 成品 TS 区间 | 55–80 | 70–95 |
| 机匣耐久上限 | 中（铆接松弛累积，见 I 章） | 高 |
| 重量 | 轻 | 重（可用铝坯减重） |
| 解锁图纸 | `blueprint_stamped_receiver` | `blueprint_milled_receiver` |

并行产线布局：`机匣线 ∥ 枪管线 ∥ 枪机线 ∥ 弹药线`，汇流于总装带（见 M 章示例工厂）。

### 数据结构建议
机匣零件组件 `taczind:receiver_part`：`{route:enum(STAMPED/MILLED), material, ts, rivet_quality:float?}`。成品枪组件 `taczind:gun_build` 记录三个核心部件 TS 与路线（用于 I 章耐久上限与二手交易显示）。

### 与 TACZ 衔接
总装台（`taczind:final_assembly`）读取蓝图，校验物料齐套，输出 `tacz:modern_kinetic_gun` ItemStack（调用 `GunItemBuilder`）+ `taczind` 组件。**TACZ 枪匠台在硬核模式下不可合成枪支**，只保留配件改装功能。

---

## A-7. 蓝图/图纸系统

### 现实原理简述
Immersive Engineering 的蓝图模板证明该玩法在 MC 成立；真实工业中图纸=工艺许可。

### 游戏化抽象方案
- 蓝图是**消耗性知识载体（可研究）+ 配方钥匙**。持有并研读（10 秒读条）后永久解锁配方（按玩家维度记录，团队共享开关见配置）。
- 解锁层级：**通用蓝图**（一类机构，如"转轮手枪系"）→ **型号蓝图**（具体枪）→ **高级工艺蓝图**（精铣、精锻、镀铬、无烟火药产线）。
- 获取途径：战利品表（村庄铁匠铺/掠夺者营地/地牢/要塞图书馆）、村民新增"军械师"职业交易（见 L 章）、残骸考古（刷子考古掉落，【AI补充】）、进阶=拆解已有机枪逆向（拆出"逆向图纸残页×N"合成完整蓝图，仿制 TS 上限 -15 惩罚——【AI补充】逆向工程系统）。

### 道具清单

| ID 建议 | 用途 |
|---|---|
| `taczind:blueprint_t1_lock` … `blueprint_t5_stamped/milled` | 各级图纸物品（家长样式：卷轴icon+烫印名称） |
| `taczind:research_desk` | 研究桌方块：读条解锁、蓝图存档展示 |
| `taczind:reverse_engineering_bench` | 逆向台：拆解成品→残页 |

### 数据结构建议
玩家能力：`taczind:unlocks` 持久化组件（挂在玩家 `PersistentData`，set<resource_location>）。蓝图物品：`{blueprint_id, fragment:boolean, fragment_count:int}`。

### 与 TACZ 衔接
配方校验钩子：装配台与枪匠台（硬核）在读取 TACZ `gun_smith_table_crafting` 配方前调用 `UnlockManager.has(player, required_blueprint)`；`required_blueprint` 为配方 JSON 新增可选字段——**对 TACZ RecipeSerializer 做反射级扩展 or 包一层自定义序列化器**（需读源码确认 `GunSmithTableRecipe` 是否可继承扩展，见开放问题 Q-03）。

---

## A-8. 质量公差系统（Tolerance Score）

### 现实原理简述
工业制造没有"名义尺寸"，只有**尺寸公差带**。公差等级由机床精度、刀具磨损、热稳定性、材料均匀性共同决定；装配件总公差是各件公差的加权合成，且关键配合（膛线、闭锁面、导气孔）影响最大。

### 游戏化抽象方案
**TS（Tolerance Score, 0–100）** 贯穿零件→部件→整枪：

```
零件TS = clamp( 机器基础TS(表A-8a)
              + 材料加成(表A-8b)
              + 动力稳定性加成(energy_stability×10, 手摇=手艺小游戏评分×10)
              + 模具血统(模具TS-50)/10
              + 随机抖动N(0, σ=阶段值) , 0, 100)

部件TS = 零件TS 的关键件加权平均（表A-8c 权重）
整枪TS = 部件TS 的再次加权（枪管0.35 / 枪机0.25 / 机匣0.15 / 簧系0.10 / 其它0.15）
```

整枪 TS 映射属性区间（表 A-8d）：

| TS 区间 | 称号 | 静态散布系数 | 故障率基线倍率 | 部件耐久上限倍率 | 初速偏差 |
|---|---|---|---|---|---|
| 0–30 | 土造 (Crude) | ×1.6 | ×4.0 | ×0.6 | ±6% |
| 30–55 | 可用 (Serviceable) | ×1.25 | ×2.0 | ×0.85 | ±3% |
| 55–75 | 制式 (Standard) | ×1.0 | ×1.0 | ×1.0 | ±1.5% |
| 75–90 | 精良 (Fine) | ×0.85 | ×0.6 | ×1.15 | ±0.8% |
| 90–100 | 名匠 (Masterwork) | ×0.7 | ×0.35 | ×1.3 | ±0.4% |

机器基础 TS（表 A-8a 摘）：手摇工具 25–45；T2 手动机床 35–60；T3 动力机床 55–80（吃动力稳定性）；T4 精机 70–90；T5 铣削中心 80–95 / 冲米线 55–80。

### 数据结构建议
见各层组件中的 `ts` 字段；整枪组件 `taczind:gun_build`：`{ts:int, parts_ts:{barrel,bolt,receiver,spring,magwell,trigger}, build_serial:string, factory_tag:string?}`
`factory_tag` = 生产线所在区块哈希的"厂牌"（【AI补充】：名牌枪厂系统，下游 L 章交易增值）。

### 与 TACZ 衔接
- TS → 散布系数：挂接 TACZ `inaccuracy` 计算链（注入附件式 Modifier，`resource/modifier` 体系是现成的扩展点——**需读源码确认 Modifier 可否注册自定义来源**，Q-04）。
- TS → 初速偏差：在实体子弹创建事件（IGun API 有 `api/entity` 与射击事件）中按 TS 扰动 speed。
- 耐久上限倍率：I 章内部系统消费。
