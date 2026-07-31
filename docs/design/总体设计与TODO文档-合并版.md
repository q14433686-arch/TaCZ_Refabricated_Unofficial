# TACZ 工业化重构 ·《总体设计与 TODO 文档》索引

> 项目代号：**TACZ-INDUSTRIAL（命名空间建议 `taczind`）**
> 基于 `TaCZ_Refabricated_Unofficial`（Fabric 26.2，数据组件化版本）
> 文档版本：v1.0 · 2026-07-31 · 阶段一产出物

本目录是《总体设计与 TODO 文档》的分章存放区。完整单文件合并版见：
**`docs/design/总体设计与TODO文档-合并版.md`**

## 阅读顺序

| 序号 | 文件 | 内容 |
|---|---|---|
| 00 | [00-research-sources.md](00-research-sources.md) | 联网调研信息来源清单与摘要、参考模组对比 |
| 01 | [01-design-philosophy-stages.md](01-design-philosophy-stages.md) | 设计哲学、五阶段总览、核心循环、数值哲学 |
| 02 | [02-A-manufacturing.md](02-A-manufacturing.md) | **A. 制造与工业链路系统**（材料树/五阶段机器/蓝图/公差） |
| 03 | [03-B-ammunition.md](03-B-ammunition.md) | **B. 弹药系统**（弹壳/底火/发射药/弹头/复装） |
| 04 | [04-C-ballistics.md](04-C-ballistics.md) | **C. 弹道与精度系统**（含【AI补充】环境修正成本收益分析） |
| 05 | [05-D-recoil-ergonomics.md](05-D-recoil-ergonomics.md) | **D. 后坐力与人体工学系统** |
| 06 | [06-E-state-machine-malfunctions.md](06-E-state-machine-malfunctions.md) | **E. 枪械状态机与故障(卡壳)系统**（状态枚举+转移表+清除交互） |
| 07 | [07-F-barrel-burst.md](07-F-barrel-burst.md) | **F. 炸膛系统**（触发条件/分级/概率模型） |
| 08 | [08-G-overheat.md](08-G-overheat.md) | **G. 过热系统**（含【AI补充】温度-炸膛联动建议） |
| 09 | [09-H-maintenance.md](09-H-maintenance.md) | **H. 保养与维护系统**（积碳/锈蚀/环境模块/保养流程） |
| 10 | [10-I-modular-durability.md](10-I-modular-durability.md) | **I. 模块化耐久系统**（部件独立耐久/弹簧疲劳） |
| 11 | [11-J-modular-attachments.md](11-J-modular-attachments.md) | **J. 模块化改装系统**（导轨兼容/归零/消音器衰减） |
| 12 | [12-K-acoustics-stealth.md](12-K-acoustics-stealth.md) | **K. 声学与隐蔽系统** |
| 13 | [13-L-logistics.md](13-L-logistics.md) | **L. 后勤与仓储系统**（弹药箱/武器架/携行具） |
| 14 | [14-M-create-integration.md](14-M-create-integration.md) | **M. Create·飞翔版 自动化联动**（含完整示例工厂布局） |
| 15 | [15-N-ai-supplements.md](15-N-ai-supplements.md) | **N.【AI补充】供弹具机构/扳机组/节奏系统/分水岭机制** |
| 16 | [16-roadmap.md](16-roadmap.md) | **实施路线图 P0–P6**（验收标准 DoD + 依赖关系图） |
| 17 | [17-data-structure-master-table.md](17-data-structure-master-table.md) | **数据结构总表**（全部 NBT/组件/方块实体/状态枚举汇总） |
| 18 | [18-open-questions.md](18-open-questions.md) | **开放问题清单**（待源码调研/技术验证项） |
| 19 | [19-progress-board.md](19-progress-board.md) | **进度看板**（每系统状态追踪，持续维护） |
| 20 | [20-glossary.md](20-glossary.md) | **术语表**（中英对照，命名唯一来源） |
| 21 | [21-performance-engineering.md](21-performance-engineering.md) | **性能预算与工程规范**（tick 预算/网络/测试 DoD） |
| 22 | [22-weapon-lineage-journey.md](22-weapon-lineage-journey.md) | **五阶段枪线谱系 + 玩家旅程验证 + UI/键位总表** |

## 实现记录（阶段二持续追加）

| 日期 | 记录 | 范围 |
|---|---|---|
| 2026-08-01 | [../impl-log/P0-feed-device-data-system.md](../impl-log/P0-feed-device-data-system.md) | P0 补充：供弹具数据系统（27 类/六机构/规则层/组件注册） |

## 子系统章节统一模板（A–N 每章均含五节）

1. **现实原理简述**（游戏化抽象的依据；不含任何现实武器制造工艺细节）
2. **游戏化抽象方案**（机制/数值/状态如何落地为玩法）
3. **所需道具/机器/工作台清单**（物品 ID 建议、方块建议名、用途）
4. **数据结构建议**（DataComponent 字段名+类型，适配本仓库 26.2 组件体系）
5. **与 TACZ 现有系统的衔接方式**（扩展哪个类/接口/JSON；不确定处标注"需读源码确认"）

## 关键约定

- **数据驱动优先**：一切数值进 JSON/数据包，代码里不出现硬编码平衡数。
- **向后兼容**：对 TACZ 现有枪包 JSON 只做**增量字段扩展**，缺失字段走默认值，不破坏存量枪包。
- **安全红线**：全文只做"游戏化抽象"，不出现任何现实火工品配方比例、化学工艺参数或武器机加工细节。
- **性能红线**：弹道/流水线禁止逐 Tick 全量计算，采用事件驱动+分帧+对象池。
# 第 00 章 · 联网调研信息来源清单与摘要

> 本章按任务要求在正式设计前列出全部参考资料，并简要总结每类资料对本设计的输入。
> 重申安全红线：所有现实资料仅用于**概念级参考**（原理分类、术语、历史进程），本文档不输出、后续实现也不依赖任何现实危险品制造细节。

## 0.1 TACZ 模组本体（一手资料：本仓库源码 + 官方仓库）

| 来源 | 用途 |
|---|---|
| 本仓库 `TaCZ_Refabricated_Unofficial`（Fabric 26.2 fork）源码实读 | 确认 DataComponent 化、枪包 JSON 结构、ItemDataAccessor 体系、配方类型 |
| [GitHub: MCModderAnchor/TACZ](https://github.com/MCModderAnchor/TACZ) | 上游 Forge 1.20.1 原版架构、1221+ commits、LGPL/AGPL 授权 |
| [deepwiki: TACZ Gun System](https://deepwiki.com/MCModderAnchor/TACZ/2-gun-system) | 官方 Wiki 级架构说明：数据驱动枪包、GunData 中心模型、client/server 分层 |
| [mcmod.cn: 机械动力×TaCZ 原价弹药配方数据包](https://www.mcmod.cn/post/4886.html) | 社区已有的 Create×TaCZ 联动先例（机械动力辊压仿制弹药配方生成器），证明联动需求真实存在 |
| [mcmod.cn: TACZ 战利品表 NBT 教程](https://www.mcmod.cn/post/4589.html) | 确认 `GunId`/`GunFireMode` NBT 注入惯例 |

**仓库实读结论（设计的地基）**：

- 版本：Fabric `26.2`，`fabric_version=0.155.2+26.2`，ItemStack 布局为 `{id, count, components:{...}}`，旧 `tag` 子键已不存在（`GunItemDataAccessor` 内有明确注释）。
- 枪械数据入口：`com.tacz.guns.resource.pojo.data.gun.GunData`（gson POJO），关键字段：`ammo`、`ammo_amount`、`extended_mag_ammo_amount[]`、`bolt(open_bolt/closed_bolt/manual_action)`、`rpm`、`bullet{life,damage,bullet_amount,speed,gravity,friction,knockback,pierce,tracer_count_interval,ignite,explosion,extra_damage{armor_ignore,head_shot_multiplier,damage_adjust[]}}`、`draw_time/put_away_time/sprint_time/aim_time/bolt_action_time/bolt_feed_time`、`fire_sound{fire_multiplier,silence_multiplier}`、`reload{type,feed,…}`(FeedType: magazine/manual/fuel/inventory)、`fire_mode[]`、`fire_mode_adjust`、`burst_data`、`crawl_recoil_multiplier`、`recoil`(关键帧)、`inaccuracy{stand/move/sneak/lie/aim}`、`movement_speed{base/aim/reload}`、`melee`、`heat{heatMax=100, heatPerShot=3, over_heat_time}`。
- 枪内运行时字段：`GunItemDataAccessor` 常量——`GunId、GunFireMode、HasBulletInBarrel、GunCurrentAmmoCount、Attachment、GunLevelExp、DummyAmmo、MaxDummyAmmo、AttachmentLock、GunDisplayId、LaserColor、HeatAmount、OverHeated` —— **过热字段雏形已存在**，G 章直接扩展。
- 弹药物品：`AmmoItemDataAccessor` 只有 `AmmoId` 一个语义字段——**B 章弹药子系统基本等于新建**。
- 配件：`IAttachment` + `AttachmentType` + 附件 `*_data.json` + modifier 体系（`resource/modifier/`）。
- 配方：`tacz:gun_smith_table_crafting`（materials→result，支持 `#c:` 标签与 `count`）；自定义工作台方块通过 `BlockItemDataAccessor.BLOCK_ID="BlockId"` 写进 `minecraft:custom_data`。
- 散布模型：`InaccuracyType` 五姿态枚举 + 每姿态 float 查表，AIM 时 0.15f——**C/D 章在此表上叠加动态修正**。

## 0.2 同类枪械/工业模组参考（≥3 个）

| 模组 | 设计思路 | 对本项目的启发 |
|---|---|---|
| **Immersive Engineering**([CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-engineering/files/2250602)、[minecraft-guides wiki](https://www.minecraft-guides.com/mod/immersive-engineering/)) | 左轮+多种弹药；**蓝图(Blueprint)解锁弹药配方**；金属冲压机(Metal Press)+模具(mold)造弹壳；工程师工作台装配；多方块机器、真实电力网络 | ①蓝图=配方解锁载体 → 本设计 A 章蓝图系统直接借鉴；②"模具"概念 → 冲压机膛模/弹头模；③工业美学与多方块机器的体验目标 |
| **Create（机械动力）**([mc-mod.net 水轮教程](https://www.mc-mod.net/how-to-set-up-a-water-wheel-for-power-in-create/)、Reddit r/CreateMod) | 动力=**应力单位 SU**×转速 RPM；水车 512 SU/个；大小齿轮组 2:1 变速；动力网络强依赖实体布局；传送带/机械臂/工作盆构成流水线 | ①M 章的机器能耗一律用 **SU 容量需求 + RPM 工作速率** 双指标；②"动力中断即停产"是原生机制，直接复用；③齿轮变速比可作为"动力稳定性"公差因子的物理来源 |
| **Vic's Modern Warfare / Vic's Point Blank**([ChampBop 对比文](https://champbop.com/minecraft/best-minecraft-gun-mods/)、r/feedthebeast) | MW(1.12.2 已停更)：最强视听反馈；Point Blank(1.20.1，Fabric/Forge/NeoForge 三端)：动画系统、**生存模式可合成**、移动状态影响精度 | "手感即口碑"：动画/音效优先级高于数值深度；移动-精度联动已证明被玩家接受 |
| **MrCrayfish's Gun Mod / Decimation**（同上 Reddit 讨论） | CGM：轻量框架、数据包扩展配件；Decimation：沉浸丧尸生存、枪不可合成 | 反面教材：过度简化(CGM)与剥夺制造乐趣(Decimation)都不可取 → 本项目的"五级工业链"正是填空 |
| **Escape from Tarkov（非 MC，机制参考）**([NamuWiki](https://en.namu.wiki/w/Escape%20from%20Tarkov/%EB%AC%B4%EA%B8%B0)、[gigabeath 机制解析](https://www.youtube.com/watch?v=FNnF2653Ihs)) | 耐久<93%才开始出故障；故障四类：瞎火/抛壳失败/进弹失败/卡弹(普通与硬卡两档)；故障红绿灯式 HUD 提示("不现实但更沉浸"的设计权衡)；消音器加速过热与耐久损耗 | ①"故障≠折磨"的门槛设计：低耐久才惩罚，高耐久安心；②HUD 状态指示器直接借鉴到 E 章；③过热联动故障率的加权模型参考 |

## 0.3 真实枪械原理资料（概念级，仅术语与原理分类）

**闭锁/自动原理分类**（[NRA Museum Glossary](https://www.nramuseum.org/media/914983/glossary.pdf)、[Wikipedia: Firearm actions](https://en.wikipedia.org/wiki/Category:Firearm_actions)、[Military-history wiki](https://military-history.fandom.com/wiki/Firearm_action)）：

- 手动类：转轮(revolver)、旋转后拉/直拉枪机(bolt)、杠杆(lever)、泵动(pump/slide)、起落式(falling block)、滚轮式(rolling block)、撅把(break-action)。
- 自动类三大族：**自由枪机(blowback)**及延迟族(滚轮延迟 roller-delayed：G3/MP5；杠杆延迟；气体延迟：HK P7；肘节延迟：Luger/佩德森)、**枪管后坐式**（短后坐：M1911/Glock/M2/MG42；长后坐：Auto-5）、**导气式**（直接导气 DI、短行程活塞、长行程活塞：AK）。
- 开膛/闭膛待机(open/closed bolt)、Headspace(弹壳定位间隙)等术语 → D 章后坐曲线差异化与 E 章状态机的理论依据。

**弹道学基础**（[Miller 缠距公式多方交叉验证](https://www.getzenquery.com/tools/twist-rate-calculator/)、[rifleconfigurator](https://www.rifleconfigurator.com/tools/twist-rate-calculator)）：

- 陀螺稳定因子 Miller 公式 `Sg = 30m / (T²·D³·l·(1+l²))`：Sg<1.0 翻滚(keyhole/钥孔弹孔)；1.0–1.3 勉强；1.3–2.0 理想；>2.0 完全稳定。**过稳定基本无害**（"err fast"原则）→ C 章按此设计"缠距过慢惩罚、过快仅轻微副作用"。
- 初速-枪管长度：收益递减型关系；寒冷稠密空气降低稳定性。
- （数值均为游戏平衡引用的简化版，非真实弹道计算。）

**枪械故障分类学**（[ORM-TS](https://orm-ts.com/what-are-firearm-malfunctions/)、[GunGoddess](https://www.gungoddess.com/blogs/troubleshooting/how-to-manage-handgun-malfunctions)、[firearmshistory blog](https://firearmshistory.blogspot.com/2012/09/firearm-malfunctions-types-of.html?m=1)、[Wing Tactical](https://www.wingtactical.com/blog/8-common-handgun-malfunctions-explained/)）：

- 机械类：进弹失败(FTF)、抽壳失败(FTE→双进弹)、抛壳失败( stovepipe 烟囱式)、Rim Lock(底缘互卡)、Hammer Follow、Out-of-Battery、Slamfire。
- 弹药类：Dud(哑弹/瞎火不发)、**Hangfire(迟发火，处置标准：保持指向安全方向等 30–60 秒)**、**Squib(弹头留膛；下一发命中留膛弹头→炸膛)**、Case Head Separation(弹壳头部分离)。
- 清除规程：Tap-Rack-Bang(拍-拉-打)；双进弹需卸弹匣；Squib 必须用通条从枪口捅出、禁止续射。→ E 章六类故障与差异化清除交互的直接蓝本。

**保养与弹药史**（[ammo.com: Berdan 底火史](https://ammo.com/primer-type/berdan)、[ammo.com: 腐蚀性弹药](https://ammo.com/primer-type/corrosive-ammo)、firearmstalk/go2gbo 社区帖）：

- **Boxer**：底火自带火台，易退壳复装（欧美民用主流）；**Berdan**：火台在弹壳底火室内联体，复装困难（军用/剩余弹药常见）——结构与"是否腐蚀性"无必然关系。
- 腐蚀性底火（氯酸盐系，1950 年代前军用常见）：燃烧残渣吸湿成盐→快速锈蚀枪膛 → H 章"腐蚀性弹药"词条：必须用溶剂中和工作。
- 黑火药 vs 无烟火药：前者积碳重、烟雾大、发火敏感度低阈值低；后者残渣少但早期底火仍可能腐蚀。

**过热与枪管寿命**（[Axis History Forum: FG-42](https://forum.axishistory.com/viewtopic.php?t=203787)、[National WWII Museum: MG-42](https://www.nationalww2museum.org/war/articles/mg-42-machine-gun)）：

- MG42 约 200–250 发需换管，熟练组 <20 秒完成，标配多达 6 根备用管；弹匣供弹的"自动步枪"(BAR/FG-42)因换弹间隙自限温升；热膨胀引发卡滞、闭膛武器过热有 cook-off(自燃入膛弹)风险。
- → G 章速换枪管机制与"闭膛 cook-off"的玩法化依据。

## 0.4 调研对设计的总体输入摘要

1. **工艺演进主轴**：手搓(火绳/前装时代作坊)→小作坊(膛线拉刀、手工冲压)→初级工业(Create 动力)→中级工业(无烟火药化学)→重度自动化，与真实枪械史(燧发→击发→定装弹→无烟火药→冲压 AK)同构，玩家能"玩通一部轻武器史"。
2. **可靠性=资源管理**：参考 Tarkov 的"高耐久免罚"原则，故障/炸膛是**玩家选择与保养决策的结果**，而非随机惩罚。
3. **数据驱动一切**：TACZ 枪包 JSON 体系是本项目最大资产，所有新系统以增量字段+新标签(tag)形式挂接，存量枪包零迁移。
# 第 01 章 · 总体设计哲学、五阶段总览与核心循环

## 1.1 设计哲学（五条不可妥协的原则）

1. **从矿石到成品（Ore-to-Firearm）**：任何一把枪都可以被拆解为一棵完整的材料树，玩家始终是"兵工厂主"而不仅是"射手"。两个铁锭合一把枪的过家家在本项目中**禁用**——所有原版 TACZ 枪械工作台配方默认"简易模式"，可通过配置切换为"硬核模式"（配方全部关闭，只能走工业链）。
2. **个体差异即收藏性**：同一型号武器因公差评分产生个体差异（精度/可靠性/耐久区间），催生"匠造名枪"与"工坊出品 vs 手搓土枪"的收藏与交易玩法——这是整个公差系统的存在意义。
3. **可靠性是一种资源，而非随机惩罚**：高保养、高公差、好弹药的武器几乎不故障；故障是玩家忽视维护的**可预期后果**。参考塔科夫"耐久>93% 几乎不故障"的门槛设计。
4. **五个时代一部史**：玩家通关过程=重走轻武器史（燧发/击发→定装黑火药弹→栓动/杠杆→无烟火药+导气自动→冲压量产 AK），每个科技阶段解锁历史上对应的机构。
5. **TACZ 是平台，本项目是上层建筑**：不重写 TACZ 的渲染/动画/网络层，只做**数据层扩展 + 独立工业子系统 + 事件挂接**。

## 1.2 五级科技阶段总览

| 阶段 | 名称 | 历史对应 | 核心动力 | 可造机构 | 弹药时代 | 标志性解锁 |
|---|---|---|---|---|---|---|
| T1 | 手搓（Handcraft） | 火绳/燧发/击发时代 | 人力+炭火 | 前装、撅把、单发 | 火帽+黑火药（现成药筒不可复装） | 坩埚、锻炉热度条、手锤成型 |
| T2 | 小作坊（Workshop） | 19 世纪定装弹时代 | 手摇/脚踏 | 杠杆、转轮、双管、早期栓动 | 黄铜定装弹（黑火药装填，可复装） | 手摇车床、手动冲压机、膛线拉刀、退火炉 |
| T3 | 初级工业（Early Industry） | 一战前机械厂 | **Create 旋转动力网** | 成熟栓动、泵动霰弹、半自动手枪 | 黑火药+早期无烟火药（弹药厂直供） | 动力冲压/车床/拉膛线机，公差第一次可控 |
| T4 | 中级工业（Smokeless Era） | 二战 | Create 动力+**化学反应釜** | 导气半自动、冲锋枪、狙击系统 | 无烟火药全谱系、曳光/穿甲/亚音速弹头 | 硝化棉与无烟发射药产线、精密热处理、半自动装配线 |
| T5 | 重度自动化（Full Automation） | 冷战冲压 AK vs 铣削 AR | 全自动并行产线 | 突击步枪/机枪全系、公差极限产品 | 全自动装填检验包装 | **冲压机匣 vs 精密铣削机匣**双工艺路线、全自动"矿石进枪弹出" |

## 1.3 核心循环（Core Loop）

```
采矿/贸易 ──► 冶金中间品 ──► 零件生产 ──► 组装(蓝图×公差) ──► 试射/校枪
   ▲                                                          │
   │                                                          ▼
弹药复装◄──弹药消耗◄── 战斗/狩猎/防卫 ◄── 配装(配件×归零×消音)
   ▲                          │
   └── 弹壳回收/部件更换 ◄────┴── 保养维护(积碳/锈蚀/弹簧疲劳/过热烧蚀)
```

每个循环节点都对应后文一章：上游=A/B/M，战斗=C/D/E/F/G/K/N，维护=H/I，配装=J，后勤=L。

## 1.4 数值哲学与统一计量

- **时间**：游戏内统一用 tick（20 tick=1 秒），配方向玩家显示秒。
- **热量**：`HeatAmount` 0–100（沿用 TACZ 现有 `GunHeatData.heatMax` 默认 100），超过 `heatMax` 进入 `OverHeated`。
- **公差评分（Tolerance Score, TS）**：0–100 整数，越高越好。零件粒度，最终武器取加权。
- **耐久**：每部件双轨制——`Wear`（磨损量，只增不减，决定"状态等级"）+ `MaxWear`（设计寿命上限）。
- **概率**：所有故障/炸膛判定用**千分率基点（‰ bp）**存储与显示，内部 double 计算，便于 JSON 调参。
- **声音特征**：`NoiseSignature` 以"有效传播半径（格）"计量，与 TACZ `fire_sound` 乘数兼容。
- **一切常量化入 JSON**：`/data/taczind/balance/*.json` 为全局平衡表，热更生效（见 18 章开放问题 Q-07）。

## 1.5 命名与注册规范

- 模组 ID：`taczind`（TACZ Industrial Overhaul），作为本 fork 内的独立子系统包 `cn.sh1rocu.tacz.industry`（或拆分为附属 mod，见开放问题 Q-01）。
- 物品命名规范：`taczind:<类别>_<材料>_<品名>`，如 `taczind:part_steel_barrel_blank`。
- 数据包规范：TACZ 枪包增量字段统一命名空间 `taczind`（在枪包 `data/<ns>/data/guns/*_data.json` 顶层增加 `taczind:` 对象，详见各章"衔接方式"）。
- 所有新语言键：`item.taczind.* / block.taczind.* / gui.taczind.* / tooltip.taczind.*`。

## 1.6 难度模块开关（全局配置 `taczind-common.toml`）

| 模块 | 默认 | 说明 |
|---|---|---|
| `hardcoreRecipes` | false | 关闭原版枪匠台简易配方，强制走工业链 |
| `malfunctions` | true | 故障系统总开关 |
| `burstRisk` | true | 炸膛系统总开关 |
| `rustAndFouling` | true | 锈蚀/积碳 |
| `environmentModule` | false | 沙尘/严寒环境交互（进阶难度） |
| `ballisticWind` | false | 横风修正（进阶弹道） |
| `atmosphereCorrection` | false | 温度/气压修正（AI 补充，默认关，理由见 04-C 章） |
| `toleranceVariance` | true | 公差个体差异 |
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
# 第 B 章（03）· 弹药系统（独立于枪械的完整子系统）

> TACZ 现状：弹药以 `AmmoId` 单层数据定义（伤害/初速等直接写在 **枪** 的 `bullet` 节点里），弹壳、底火、发射药、弹头四要素完全不存在。本章把弹药升级为一个**四件套物理对象 + 复装生命周期**的独立子系统。

---

## B-1. 现实原理简述

一发定装弹 = **弹壳(Case) + 底火(Primer) + 发射药(Propellant) + 弹头(Projectile)**。
- **弹壳材质**：黄铜延展性好、弹性贴膛密封、可反复复装（有加工硬化与壳头分离寿命上限）；钢壳廉价省铜但硬、磨损抽壳钩、漆层破损即锈蚀，基本不复装；铝壳更轻但不可复装、强度低。
- **底火结构**：Boxer 底火自带火台、中心单传火孔，手工即可退壳换新底火（复装体系）；Berdan 底火的火台在弹壳底部联体、双传火孔，退装困难（军用一次使用体系）。底火装药分腐蚀性（1950s 前军用普遍，残渣吸湿成盐锈蚀枪膛）与无腐蚀性。
- **发射药**：黑火药（低能量、燃速快、积碳重、烟雾大→暴露射手位置）；无烟火药（硝化棉系，能量密度高、按造粒形状分快/慢燃速，**燃速必须与口径容积和枪管长度匹配**：过快在短管内未充分利用→未燃药渣与枪口焰巨大，过慢则膛压曲线后移→长管武器威力过剩、短管武器初速不足，极端匹配错误可致过压）。
- **弹头**：软铅圆头（早期）、全金属被甲 FMJ（标准）、空尖 HP（命中扩张、面杀伤、停止作用高、穿透低）、穿甲 AP（钢芯/硬芯，穿透加成）、曳光 Tracer（尾部发光剂示踪弹道、暴露射手、可燃物引燃）、亚音速（配合消音器避开音爆啸声）。

## B-2. 游戏化抽象方案

弹药从"枚举物品"变为"**装配件**"：成品弹药物品上带 `taczind:cartridge` 组件记录四要素与装填参数。伤害/初速不再只由枪决定，而是：

```
最终伤害 = 枪的 BulletData.damage × 弹头类型系数 × 药量系数 × 整枪TS初速偏差
最终初速 = 枪的 BulletData.speed × 发射药能量系数 × 燃速匹配系数 × 装药量系数 × 弹头质量系数
```

**燃速匹配系数（游戏公式，平衡用途）**：
设枪管容积比 `V = 口径容积系数 × 枪管长度档`（枪 JSON 增量字段 `taczind.ballistic.barrel_class ∈ {SHORT, STD, LONG}` 与口径共同查表得 V），弹药燃速 `B ∈ {FAST, MID, SLOW}`：

| 匹配 | 系数 | 副作用 |
|---|---|---|
| B 恰配 V | ×1.00 | 无 |
| B 快一档于 V（快药长管） | ×0.97 初速 | 积碳+50%（药早燃尽残渣多），无害 |
| B 慢一档于 V（慢药短管） | ×0.88 初速 | 枪口焰+噪音特征+30%（未燃药在枪口二次发光）→ K 章 |
| B 慢两档（慢药超短管） | ×0.75 | 枪口焰极大 + "未燃药渣"掉落物 |
| 错配+超量装药叠加 | —— | **过压标记 OverPressure**（F 章炸膛权重++) |

## B-3. 弹壳系统

### 材质与生命周期

| 材质 | 复装上限 | 特性 |
|---|---|---|
| 黄铜 `BRASS` | 默认 6 次（可配置） | 每次复装后"裂纹风险"累加；潮湿环境不锈 |
| 钢 `STEEL` | 0（不可复装） | 造价-30%；抽壳磨损+（枪机耐久消耗×1.15）；未上漆弹壳在雨中/湿地 3 游戏日后生锈（锈弹壳进弹失败率权重+） |
| 铝 `ALUMINUM` | 0 | 重量-40%（携行向）；膛压容忍低，禁止超量装药（判定直接×2 炸膛权重） |

弹壳物品（拾取物）组件 `taczind:shell_case`：
| 字段 | 类型 | 说明 |
|---|---|---|
| `material` | enum(BRASS/STEEL/ALUMINUM) | 材质 |
| `primer_type` | enum(BOXER/BERDAN) | 底火结构（见 B-4） |
| `reload_count` | int | 已复装次数 |
| `crack_risk` | float 0–1 | 裂纹风险（reload_count 与冲压质量推导） |
| `state` | enum(FIRED_SPENT/INSPECTED_OK/CRACKED/DEFORMED) | 检查结果 |
| `rusted` | boolean | 锈蚀 |

抛壳规则（衔接 E 章）：射击时在 `EntityShootEvent` 后按枪的抛壳方向生成弹壳拾取物（**并非每发必掉**：每发 60% 掉落可拾取壳，战利品表中"弹壳雨"开关联动性能，见开放问题 Q-05）。

## B-4. 底火系统

| 底火类型 | 复装性 | 腐蚀性 | 获取 |
|---|---|---|---|
| Boxer（可复装体系） | `taczind:primer_boxer` 独立物品，可在复装台装入 | 默认无 | T2 起可造/购 |
| Berdan（一次体系） | 弹壳出身即固化，人工不可退 | 默认有（对应 T1–T3 军剩弹） | 战利品/商人 |

腐蚀性底火 → H 章 `corrosive_fouling` 标记：射击后 24 游戏小时内不用溶剂清洁，锈蚀累积加倍。
底火装药质量（`lot_quality`）影响 E 章**瞎火率**（劣底火权重+，好底火几乎为 0）。

## B-5. 发射药与装药量

- 黑火药装药：可手工量斗装填（T1–T2），装填量默认"标准"，玩家可选择 ±15% 装药量（UI 滑条）；
- 超装（+5%~+15%）：初速 +3%~+8%，但打上过压权重（`OverPressureTag` 分数 = 超装% × 弹壳材质系数 × 枪管材料屈服系数[抽象]），**不立即爆炸**，效果为：①炸膛概率项加权（F 章）②部件磨损速率 ×(1+超装%)。**欠装（<90%）**：初速降；≤"临界装药量"（如 <55%，JSON 可调）时判定为 **Squib 风险药量**（E 章哑弹卡膛）。
- 量具升级链：手量斗（误差±8%）→ 天平量药器（±3%）→ 自动装药机（±0.5%，T4 产线件）——误差直接写进弹药组件 `powder_charge_dev`，在射击瞬间掷骰决定落到哪一档。

## B-6. 弹头体系

| 弹头 | ID | 伤害系数 | 穿透 | 特殊 |
|---|---|---|---|---|
| 铅圆头 LRN | `projectile_lrn` | ×1.0（对无甲 ×1.1） | ×0.6 | T1–T2 铸造易得 |
| FMJ | `projectile_fmj` | ×1.0 | ×1.0 | 基准 |
| 空尖 HP | `projectile_hp` | 命中肉体 ×1.25（`armor_ignore` 折算为负） | ×0.5 | T3 模具冲压 |
| 穿甲 AP | `projectile_ap` | 肉体 ×0.9 | `armor_ignore`+0.4、方块穿透等级+1 | T4 硬芯工艺 |
| 曳光 Tracer | `projectile_tracer` | ×0.95 | ×0.9 | 弹道可见（复用 TACZ `tracer_count_interval`）；夜间暴露射手（K 章）；概率引燃干草/树叶 |
| 亚音速 Subsonic | `projectile_subsonic` | ×0.85 | ×0.9 | 初速钳到音速阈值以下；与消音器叠加触发"隐蔽射击"（K-2） |

## B-7. 复装系统完整流程

六个工位（可整合为 1 台 `taczind:reloading_bench` 六页签 GUI，自动化期拆成 6 台产线机器）：

1. **回收**：拾取/翻找战场掉落壳，入"待检弹壳"堆。
2. **检查**（`taczind:case_inspector` 检具或目检小游戏）：按 `crack_risk` 掷骰显示 OK/CRACKED/DEFORMED；未检壳直接复装→保留隐藏风险（隐形裂纹=抽壳断裂与膛压泄漏权重）。
3. **退底火/整形**（仅 Boxer）：工具 `taczind:decapping_die`；Berdan 壳此步灰色不可用。
4. **装底火**：`primer_seat_press`；选腐蚀/无腐蚀底火。
5. **装药**：量具工位，滑条选装药量（见 B-5 误差链）。
6. **压弹头**：`bullet_seating_press`；选择弹头类型，生成最终弹药。

复装弹药打上 `handload` 标记：与同 lot 工厂弹区分，品质取决于各工位质量，**允许出现比工厂弹更强的"精选手工弹"**（均衡装药误差小时散布-8%——奖励工坊玩家）。

## B-8. 数据结构建议

`taczind:cartridge` 组件（单发/整盒共用）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `case_material` | enum | 弹壳材质 |
| `primer_type` | enum | Boxer/Berdan |
| `primer_quality` | float | 底火质量（瞎火权重源） |
| `primer_corrosive` | boolean | 腐蚀性 |
| `propellant_type` | enum(BLACK/SMOKELESS_SINGLE/DOUBLE) | 药型 |
| `burn_rate` | enum(FAST/MID/SLOW) | 燃速档 |
| `powder_charge_pct` | int(85–115) | 名义装药量 |
| `powder_charge_dev` | float | 实际偏差（掷骰后写死） |
| `projectile_type` | enum | 弹头类型 |
| `projectile_mass_class` | enum(LIGHT/STD/HEAVY) | 质量档（供缠距匹配 C 章） |
| `overpressure_score` | float | 过压权重（预计算缓存） |
| `squib_risk` | float | 哑弹风险（欠装推导） |
| `handload` | boolean | 手工弹标记 |
| `lot_id` | long | 批次 |

## B-9. 与 TACZ 现有系统衔接

- **正向衔接**：TACZ 弹药 JSON（`data/<ns>/data/ammo/*_data.json`）增量扩展对象 `taczind`：声明该口径的可用弹头集、标准装药、燃速档、弹壳默认材质——缺失时按默认（FMJ/STD/MID/黄铜 Boxer），**旧枪包零改动可用**。
- **射击时注入**：子弹实体生成处（TACZ `api/entity`/`EntityBullet` 构造口，需读源码确认具体类名 Q-06）读取 `taczind:cartridge` 组件，按 B-2 公式乘算伤害/初速/曳光/点火属性。
- **弹药消耗路径**：复装台消耗的是"空壳"而非新物品——需要让弹匣内弹药携带组件信息。TACZ 现状枪内只存 `GunCurrentAmmoCount` int——**这是最大冲突点**：本设计引入 `taczind:ammo_stack` 组件（枪内弹药队列 List<组件摘要>）旁路记录，换弹/射击时与 TACZ 整数同步（详见 E-6 状态机与 17 总表，需验证同步点 Q-06）。
# 第 C 章（04）· 弹道与精度系统

> TACZ 现状：`BulletData` 已有 `speed/gravity/friction/pierce/damage_adjust/extra_damage`，`InaccuracyType` 五姿态散布表已是雏形。本章在其上做**增量公式层**，不替换实体弹道层。

---

## C-1. 现实原理简述（游戏平衡用途的简化版）

- **初速模型**：内弹道学核心是"发射药燃速×药量 vs 弹后空间扩张"。简化结论：①药量正相关初速但边际递减；②枪管加长初速提升但收益递减（对数型）；③口径增大（同药量）初速降但动能升。
- **外弹道**：重力下坠 `drop = g·t²/2`，飞行时间 `t = 距离/v`，远距离必须抬高提前量；横风造成侧偏，偏流约与飞行时间的平方和风速成正比（简化）。
- **散布**：静态精度（枪械加工+弹药一致性）决定基准圆概率偏差；动态误差（姿态/移动/后坐累积）以乘性叠加。
- **陀螺稳定**：膛线缠距(Twist Rate)与弹头长度匹配用 Miller 稳定因子 Sg 衡量；Sg<1 弹头翻滚（钥孔弹孔、精度暴跌）；Sg≫2 仅轻微负效应——"宁快勿慢"。（详见第 00 章研究引文。）
- **穿透与跳弹**：动能与截面比动能决定穿透；入射角浅于临界角时跳弹（硬质表面临界角更宽容）。

## C-2. 游戏化抽象方案（全部为平衡用途公式，非真实弹道）

### C-2.1 初速模型增量
```
v_final = v_base(gunJSON.bullet.speed)
        × (1 + k_len × ln(L/L0))          // 枪管长度修正，L=游戏枪管长度档，L0=口径基准长，k_len≈0.08
        × 装药系数 (B 章 powder_charge/100 ± dev)
        × 燃速匹配系数 (B-2 表)
        × 弹头质量系数 (HEAVY×0.94 / STD×1.0 / LIGHT×1.05)
        × TS 初速偏差 (A-8d 表)
```
所有 `k_*` 常量在 `data/taczind/balance/ballistics.json` 可调。

### C-2.2 弹道下坠与飞行时间
- 沿用 TACZ `gravity`/`friction`/`life` 实体模拟（已有飞行时间效应），**新增口径级增量**：`taczind.ballistic.gravity_scale` 允许枪包按弹药年代覆盖默认（黑火药低空速弹=彩虹弹道，教学级下坠体验；无烟弹=平直弹道）。
- 提前量提示仅通过 J 章"归零"与曳光视觉反馈教学，不做自动落点计算（保持硬核）。

### C-2.3 横风（可选模块 `ballisticWind`）
世界风力 `wind ∈ [-1,1]`（雷暴天幅值大，区块噪声缓变）。侧偏 `drift = k_w × wind × t²` 在子弹实体 tick 时按已有 tick 钩子施加横向加速度——复用 TACZ 子弹 tick，几乎零成本。**默认关闭**，开启时 GUI 风速袋（`taczind:wind_sock` 方块）提供读数。

### C-2.4 散布模型（核心）
```
最终散布 = InaccuracyType[姿态基准值]            // TACZ 现有查表
         × TS散布系数 (A-8d)
         × 枪管热度散布系数 (1 + heat_ratio × k_heat)   // G 章热浮动
         × 部件耐久系数 (1 + wear_factor × k_wear)      // I 章
         × 缠绕失稳系数 (C-3)
         × 动态散布叠加 (D 章连发累积 bloom)
         × 依托修正 (两脚架×0.55 / 掩体依托×0.7，J 章)
         × 批次一致性 (同lot×0.98，B-5【AI补充】)
         × 环境光/烟尘 (K 章暴露惩罚，可选)
```

## C-3. 弹头稳定性：缠距与弹头匹配

- 枪管组件 `twist`（A-3 拉膛线工序写入）+ 弹头 `projectile_mass_class/length_class`（B 章）查"匹配表"得 Sg 档游戏值：

| Sg 档 | 名称 | 散布系数 | 终端表现 |
|---|---|---|---|
| Sg < 0.8 | 失稳翻滚 | ×2.5 | 曳光弹可见弹幕螺旋乱飞；命中判定保留但钥匙孔特效（粒子） |
| 0.8–1.3 | 勉强稳定 | ×1.35 | 正常 |
| 1.3–2.5 | 理想 | ×0.95（奖励匹配良好） | 正常 |
| > 2.5 | 过稳定 | ×1.0 | 简化为无害（遵循"宁快勿慢"，避免惩罚玩家的常识） |

匹配表（JSON：`data/taczind/balance/twist_table.json`）示例逻辑：`项目质量等级LIGHT↔慢缠距(1:14+)匹配；HEAVY↔快缠距(1:7~1:9)`。**无缠线滑膛管（T1 前装）固定 Sg<0.8 档**——这就是"时代惩罚"，让玩家直观感到膛线是工业革命的馈赠。

## C-4. 穿透与跳弹系统

### C-4.1 穿透力表
弹头穿透等级 `P ∈ 0..5`（B 章弹头类型决定基础，AP+1），方块材质表（可数据包扩展 `tags/blocks/taczind/penetrable_*.json`）：

| 穿透需求 P | 方块类别举例 | 穿透后伤害衰减 |
|---|---|---|
| 0 | 玻璃、树叶、沙土 | -10% |
| 1 | 木板、木门、书架 | -25% |
| 2 | 石材、陶瓦 | -40% |
| 3 | 混凝土类、铁栏杆 | -55% |
| 4 | 铁块、矿车 | -70% |
| 5 | 黑曜石/下界合金块 | -85% |

复用 TACZ `bullet.pierce`（穿透实体数）并新增"方块穿透"旁路：**需读源码确认现有子弹是否对实心方块即停**（Q-08）；若即停，在子弹 tick 射线检测命中方块时按表判定是否"穿透重生"（生成衰减版新实体，方向保留）——分帧处理避免 tick 内多次 raycast。

### C-4.2 跳弹判定
入射角 `θ`（命中面法线夹角）：`θ > 75°`（浅角度）且命中面硬度≥石材 → 跳弹判定：概率 `p_ricochet = k × (θ-75)/15 × 材质系数`，跳弹方向=镜面反射+随机扰动，伤害×0.4，一次跳弹上限。对生物护甲：铁套以上+浅角→叮声+火花粒子+伤害×0.3。

## C-5.【AI补充】温度/气压弹道修正：成本收益分析

| 方案 | 实现成本 | 玩法收益 | 结论 |
|---|---|---|---|
| 不实现 | 0 | — | 基础版采用 |
| 温度影响稳定因子（冷=稠密=不稳） | 低（查群系温度×0.1 扰动 Sg 档） | 雪原用重弹头翻车，有叙事感 | **建议做，藏进 Sg 查表**，成本 1 个乘法 |
| 海拔气压修正下坠/初速 | 中（y>96 起算系数） | 与 MC 建筑高度(通常<100)不符，感知弱 | **不建议**主动做；留增量字段 `air_density_scale` 给数据包作者 |
| 湿度影响黑火药发火率 | 低 | 雨天黑火药时代可靠性-，强化时代差异 | **建议做**：并入 H 章锈蚀/潮湿系统，雨天黑火药瞎火权重+0.5‰ |

## C-6. 所需道具/机器清单

| ID | 名称 | 用途 |
|---|---|---|
| `taczind:ballistic_target` | 弹道靶方块 | 显示命中散布圆、Sg 提示（教学工具） |
| `taczind:wind_sock` | 风向袋方块 | 横风模块读数 |
| `taczind:chronograph` | 测速门 | 实测当前武器+弹药初速（研发/质检玩法，T3+） |

## C-7. 与 TACZ 衔接

- 散布：`InaccuracyType` 查表结果出厂后乘 C-2.4 系数链——**注入点候选：`InaccuracyType.getInaccuracyType` 调用处或 `IGunOperator` 的瞄准同步事件**（需读源码确认散布最终消费点 Q-08）。
- 下坠/初速/穿透：子弹实体属性在生成时覆写（同 B 章 Q-06 注入点）。
- 所有系数 JSON 热更。
# 第 D 章（05）· 后坐力与人体工学系统

> TACZ 现状：`GunRecoil` 关键帧后坐模型 + `crawlRecoilMultiplier` + `aim_time` + `movement_speed{base/aim/reload}` 已存在。本章在**不改动画管线**的前提下做"手感差异化的参数生成层"。

---

## D-1. 现实原理简述

- **自由后坐冲量**：枪械后坐的物理来源是动量守恒（弹丸+燃气前喷 → 枪身后坐）。不同自动原理改变能量在时域上的**分布形状**：
  - **直吹式(自由枪机)**：枪机直接受弹壳推力后退，冲击靠前、峰值低但持续长（"拍击感"），多见于手枪口径冲锋枪；
  - **导气式**：先一硬脉冲（弹头过导气孔起）再复进撞击，呈"双峰"曲线；活塞长行程（AK）第二峰更重，短行程/DI 更平滑；
  - **管退式(枪管后坐)**：枪管先一起后坐再开锁，首峰钝而深、枪口明显上抬后回弹（"推然后拉"），典型 M1911 手感；
  - **滚轮延迟**：开锁延迟造成压缩感，节奏干脆、回位快（MP5/G3 式"短促"）。
- **人体工学**：重枪后坐小但举枪慢、移动慢；长枪稳定但近战转身慢；据枪久则呼吸晃动与肌肉疲劳累积；两脚架/依托/背带把武器质量"交给骨架与大地"。

## D-2. 游戏化抽象方案

### D-2.1 后坐类型曲线（挂接自动原理）
枪 JSON 增量字段 `taczind.action_type`（枚举，E 章状态机共用）映射到 TACZ `GunRecoil` 关键帧生成参数：

| 自动原理 | 曲线特征（游戏参数） | 手感标签 |
|---|---|---|
| `BLOWBACK` 直吹 | 初速低、衰减慢、总行程长 | "持续推肩" |
| `GAS_LONG_PISTON` | 首峰×1.0 + 延迟 80ms 第二峰×0.55 | "双击感" |
| `GAS_SHORT_PISTON/DI` | 单峰×0.85、恢复快 | "干脆" |
| `RECOIL_SHORT` 管退 | 首峰×1.15 钝、含 1 帧下沉再上抬 | "点头" |
| `ROLLER_DELAYED` | 首峰×0.95 极窄、回位最快 | "缝纫机" |
| 手动/转轮 | 全量单峰、无后续 | "一锤定音" |

实现：写一个 `RecoilProfileFactory`，按上述参数**生成** TACZ 关键帧数组喂给现有 `GunRecoil`——不动动画层（需读源码确认 `GunRecoil` 关键帧可否运行期替换 Q-09）。

### D-2.2 连发后坐累积与恢复
```
Bloom(连发开口) 模型：
  bloom_t+1 = min(bloom_t + kick(att_type, TS, 依托), bloom_max)
  无开火时：bloom_t = bloom_0 × exp(-t / τ)     // τ = 恢复时间常数（枪型+握把配件）
  散布最终值 = 静态散布 × (1 + bloom × k_bloom)
视角抬升(pitch kick) 独立同模型，恢复曲线由配件 J 章加成。
```
客户端平滑插值，服务端只做事件式累加（每发一次加法，无逐 tick 扫描）。

### D-2.3 ADS 速度与重量/长度
```
aim_time_final = gunJSON.aim_time × (1 + k_w × (weight - w0)/w0) × (1 + k_l × (length_class - 1))
weight = 部件重量Σ + 配件重量Σ（近战枪托/短管减重直接降 aim_time 与移动惩罚）
```
写入 `taczind:gun_build.weight_kg_game`（游戏克数），在手持 tick 缓存计算（仅组件变化时重算）。

### D-2.4 重量移动惩罚
复用 TACZ `movement_speed.base/aim/reload`：增量组件按 weight 档位查表乘算（轻卡宾×1.0 → 重机枪×0.82）。**不再依赖枪包手填**，硬核模式统一由重量推导。

### D-2.5 待机晃动与疲劳
- **呼吸摆动**：瞄准状态下准星/视角做 Lissajous 微轨迹（客户端渲染层，纯视觉锚定准确度上限）。摆动幅值随**连续瞄准时长**缓慢上升（30s 达 +60%）；
- **屏息键**（默认左 Alt）：压摆动 4 秒、之后摆动反弹 +30%（10 秒内）——一次呼吸循环的取舍，狙击玩法核心技巧（可配置开关）；
- **疲劳**：是否与体力资源挂钩？设计决策：**不引入新体力条**（避免与饱食度系统抢 UI），疲劳=瞄准时长函数+冲刺后 10s 内偏移 ×1.3 即可。

### D-2.6 依托与稳定加成

| 装备/姿态 | 效果 |
|---|---|
| 两脚架展开（趴下或对矮墙） | bloom 增长×0.5、散布×0.55、摆动-70% |
| 掩体依托（准星对准可依托边缘方块自动提示） | 散布×0.7、bloom×0.75 |
| 背带（`taczind:sling`，挂 `strap` 槽） | 切枪/收枪时间-25%、跑动掉落武器事件免疫、行走摆动-20% |
| 握把类配件 | J 章矩阵详表 |

## D-3. 所需道具/机器清单

| ID | 名称 | 用途 |
|---|---|---|
| `taczind:sling_leather` / `sling_quick` | 背带 | 挂 `strap` 槽配件（新配件类型，见 J 章新增 AttachmentType 候补） |
| `taczind:bipod_foldable` | 可折叠两脚架 | 挂 `underbarrel` 槽，部署判定见 C-2.4 |
| —（无新方块） | 依托判定系统 | 客户端渲染提示线，服务端验证 |

## D-4. 数据结构建议

`taczind:ergonomics` 组件（枪）：`{weight_g:int, length_class:int, ads_mult:float, bloom_tau:float}`（缓存值，组件变化重算）。
运行时（不进存档的玩家会话态）：`{bloom:float, last_shot_tick:long, aim_hold_ticks:int, breath_state:enum}` —— 存玩家能力或 `IGunOperator` 字段（Q-09）。

## D-5. 与 TACZ 衔接

- `GunRecoil` 关键帧生成器（Q-09）；`aim_time`/`movement_speed` 在 `GunData` 读取链上以 Modifier 方式覆写；
- 姿态判定复用 `InaccuracyType.getInaccuracyType`（已含 AIM/LIE/SNEAK）；
- 依托判定：客户端 raycast+提示渲染，服务端命中校验（防作弊）——新增独立客户端 gameplay 类，位于 `client/gameplay` 同层。
# 第 E 章（06）· 枪械状态机与故障（卡壳）系统

> 本项目的"分水岭章节"。TPS/Minecraft 枪械模组普遍只有"有弹→能射"一个布尔值，本章建立**显式枪机循环状态机**，并让所有故障都是状态机的**合法异常驻留态**——不清除就无法继续循环，清除方式因病而异。

---

## E-1. 现实原理简述

自动武器的射击循环：**进弹(feed)→闭锁(lock)→击发(fire)→开锁(unlock)→抽壳(extract)→抛壳(eject)→复进(feed 下一发)**，弹尽时多数枪械进入空仓挂机状态。任何一个环节失效都有专名（见第 00 章故障分类学引文）：进弹失败 FTF、抽壳失败 FTExtract（常衍化双进弹 Double Feed）、抛壳失败 FTEject（烟囱式 Stovepipe）、瞎火/迟发火 Misfire/Hangfire（处置标准：保持指向 30–60 秒再排障）、哑弹 Squib（弹头留膛；**续射=炸膛**）、走火 Slamfire（复进惯性意外击发，失控连发）。清除手段各不相同：排障拉壳(拍弹匣-拉机柄-继续)、卸弹匣清空供弹路径、通条捅膛。

## E-2. 游戏化抽象方案：状态机定义

### E-2.1 状态枚举（`GunCycleState`，存枪组件，int 序数持久化）

| 序 | 状态 | 说明 |
|---|---|---|
| 0 | `SAFE` | 保险（切枪默认态；不可击发） |
| 1 | `EMPTY_READY` | 空仓挂机/弹尽待装（套筒/枪机停后） |
| 2 | `FEEDING` | 复进进弹中（瞬时态，时长=bolt_feed_time 或循环间隔） |
| 3 | `CHAMBERED` | 已上膛待击 |
| 4 | `FIRING` | 击发瞬间（1 tick） |
| 5 | `UNLOCKING` | 开锁（瞬时态） |
| 6 | `EXTRACTING` | 抽壳（瞬时态） |
| 7 | `EJECTING` | 抛壳（瞬时态） |
| 8 | `COOKING_OFF_RISK` | 过热闭膛风险态（G 章联动，仅 closed_bolt 高温时进入的概率门） |
| 9 | `JAM_FTF` | 卡壳：进弹失败（弹头蹭坡、弹匣唇口坏） |
| 10 | `JAM_FTEXTRACT` | 卡壳：抽壳失败（膛内留空壳） |
| 11 | `JAM_DOUBLE_FEED` | 双进弹（旧壳未出+新弹顶入） |
| 12 | `JAM_STOVEPIPE` | 烟囱式抛壳失败（壳卡抛壳窗） |
| 13 | `HANGFIRE_PENDING` | 迟发火挂起（击发后 6–40 tick 随机才真着火；玩家可见"咔哒"） |
| 14 | `DUD_IDENTIFIED` | 哑弹确认（等待期过后判定不发） |
| 15 | `SQUIB_OBSTRUCTED` | 弹头留膛（**隐藏态**！见 E-3.5） |
| 16 | `SLAMFIRE_RUNAWAY` | 走火失控（自动枪进入 1–6 发不可控连射） |
| 17 | `MAINTENANCE_OPEN` | 分解保养态（H 章；不可射击） |
| 18 | `BURST_DAMAGED` | 炸膛损毁态（F 章终态） |

### E-2.2 合法状态转移表（核心节选；故障注入点即打★处）

| 当前态 | 事件 | 下一态 | 条件/守卫 |
|---|---|---|---|
| SAFE | 玩家按保险键 | CHAMBERED / EMPTY_READY | 视膛内状态 |
| EMPTY_READY | 插入有弹匣 | EMPTY_READY→FEEDING | 需拉机柄（开膛枪自动复进） |
| FEEDING | 复进完成 | CHAMBERED | ★FTF 掷骰失败→JAM_FTF |
| CHAMBERED | 扳机 | FIRING | fire_mode 许可 |
| FIRING | 弹药判定 | UNLOCKING / HANGFIRE_PENDING / DUD_IDENTIFIED | ★瞎火掷骰（底火质量+撞针磨损+黑火药湿度） |
| UNLOCKING | 过程完成 | EXTRACTING | — |
| EXTRACTING | 抽壳判定 | EJECTING | ★抽壳失败→JAM_FTEXTRACT（钢壳磨损+积碳+抽壳钩磨损加权） |
| EJECTING | 抛壳判定 | FEEDING(有弹)/EMPTY_READY(弹尽) | ★抛壳失败→JAM_STOVEPIPE；★上膛残留+新弹→JAM_DOUBLE_FEED |
| HANGFIRE_PENDING | 随机延迟到点(6–40t) | UNLOCKING（正常燃烧，带 30% 精度惩罚瞬时抖动） | 玩家提前拉机柄→DUD_IDENTIFIED（战术抉择！） |
| DUD_IDENTIFIED | 拉机柄排壳 | FEEDING | 排出 dud 弹（可回收组件化） |
| JAM_FTF | 排障动作(拉机柄×1) | FEEDING | 90% 成功，失败重复 |
| JAM_STOVEPIPE | 排障动作(拉机柄×1) | CHAMBERED(原弹仍在膛) | — |
| JAM_FTEXTRACT | 拉机柄+退弹匣 | EMPTY_READY | 需"卸弹匣"清除供弹源，否则转 DOUBLE_FEED |
| JAM_DOUBLE_FEED | 卸弹匣+拉机柄×2 | EMPTY_READY | 清出 1 壳 1 弹落地 |
| SQUIB_OBSTRUCTED | （无显式解）检查枪管动作 | →解除 | 必须"检查枪管"揭示；携带该态再击发→F 章炸膛判定 |
| CHAMBERED(任何室) | 撞针疲劳掷骰 | （保持态）哑火率上升 | I 章撞针耐久联动 |
| 任何自动循环态 | 走火掷骰(阻铁磨损+高温) | SLAMFIRE_RUNAWAY | 走空或弹尽/玩家松手判定结束；期间扳机无效 |
| CHAMBERED+红热 | cook-off 掷骰 | 意外 FIRING | G 章（closed_bolt 限定） |

### E-2.3 故障率模型（统一加权掷骰，千分率 bp）

每发循环在三个 ★ 节点各掷一次：
```
P(故障x) = base_x (JSON) × M_ts (A-8d) × M_wear(关联部件耐久) × M_fouling(H章积碳)
         × M_env(H章环境) × M_ammo(B章弹种系数) × M_spring(I章弹簧疲劳) × M_heat(G章)
若 malfunctions 难度关闭 → 全部 P=0。
```

## E-3. 六类故障的差异化清除交互（禁止"一键修理"）

| 故障 | 玩家察觉 | 清除操作（默认键位，均可配置） | 失误风险 |
|---|---|---|---|
| FTF 进弹失败 | "咔"空仓声+HUD 图标黄 | [排障键] 拉机柄检膛 1 次（0.6s 动画） | 无；反复发生提示检查弹匣（弹簧!) |
| ST stovepipe | 抛壳窗壳直立可见+HUD 黄 | [排障] 抖壳+拉机柄（0.8s） | 无 |
| FTExtract 抽壳失败 | 击发无声+拉不开感 HUD 橙 | [卸弹匣]+[排障×1] | 直接硬拉→50% 衍化 DOUBLE_FEED |
| DOUBLE_FEED | 枪机卡中途+HUD 红 | [卸弹匣]+[排障×2]+[重装匣]（2.5s 全套） | 缩短操作=高概率复发 |
| HANGFIRE 迟发火 | 击针声但无膛口烟，准星旁"沙漏"图标 2s 倒计时 | ①等待(安全,损失节奏) ②[排障]立刻退弹(快，但该弹浪费+若骰中迟发火早发=走火自伤 10%) | 战术抉择点 |
| SQUIB 哑弹卡膛 | **只有"闷噗"弱声响+无烟提示**（无 HUD！） | [检查枪管]动作（1.2s，枪口朝下用通条/目视）→揭示;再 [清膛] 通条捅弹（消耗通条 1 耐久，工具见 H 章） | **未检查续射→F 章炸膛判定（必查表）** |
| SLAMFIRE 走火 | 失控连射 1–6 发+HUD 红闪 | [卸弹匣]立即终止 / 等待弹尽 | 暴露+浪费弹药；松扳机无效（真实） |

设计要点：**故障信息通过音效+动画+小 HUD 图标传递**（参考塔科夫红绿灯式故障指示的"沉浸优先"权衡），SQUIB 刻意弱提示以惩罚"无脑按住扳机"。

## E-4. 撞针磨损→瞎火关联

I 章撞针（`firing_pin` 部件）耐久每降一档（100/70/40/10），瞎火 bp 基线 ×1/×1.5/×3/×8；打空包（dummy）练习也磨损撞针（TACZ 已有 DummyAmmo 机制，联动自然）。

## E-5. 数据结构建议

枪组件 `taczind:cycle`：`{state:int, pending_tick:long(hangfire截止), squib:boolean, squib_known:boolean, mal_bp_cache:{ftf,fte,fex,hf,squib,slam}(float[6] 缓存), jam_streak:int}`。
HUD 同步：官服通过自定义 S2C 包广播 state 变化（事件驱动，0 轮询）。

## E-6. 与 TACZ 衔接（重点）

- `HasBulletInBarrel`、`GunCurrentAmmoCount` 已存在 → 状态机作为**旁路权威层**：射击判定入口（服务端 `AbstractGunItem.shoot` 链或 `api/event` 射击事件，**需读源码确认服务端击发总入口类与方法** Q-06）先过状态机守卫，不通过则拦截并进入对应故障态。TACZ 原生"能射/不能射"逻辑退化为本状态机的子集。
- 排障键：新键位注册（client/input 同层），复用现有"检视(inspect)"动画轨插入排障动画资源位；无自制动画时用"拉机柄 bolt_action"现有动画剪辑。
- 弹壳/哑弹落地物：B 章弹壳拾取物同一来源。
- HUD：`client/gui/overlay` 层新增故障指示组件。
# 第 F 章（07）· 炸膛系统（Catastrophic Failure）

> 定位：**低频-高冲击**事件。炸膛不是随机天灾，而是玩家可完全规避的"玩火代价"——它把 B/E/G 各章的风险权重收敛成一个终局判定。

---

## F-1. 现实原理简述

炸膛（膛炸）的本质：膛压超过承压件（弹膛/枪管/闭锁面）强度。现实诱因可归为五类：①过压弹药（错装药量的复装弹）；②膛内异物（Squib 留膛弹头被下一发撞击形成"管内串联"；泥沙/水灌入枪管未清理）；③金属疲劳/材料缺陷（枪管钢材差、热处理不良——即本设计的低 TS 枪管）；④极端过热（红热枪管强度骤降，cook-off 叠加）；⑤闭锁未到位击发（Out-of-Battery）。后果从弹壳头分离喷焰到机匣解体伤人不等。

## F-2. 游戏化抽象方案

### F-2.1 触发条件汇总（→ 统一风险池）

| 来源 | 权重项 | 出处 |
|---|---|---|
| 过压弹药 | `cartridge.overpressure_score`（超装×弹壳材质×枪管材料系数） | B 章 B-5 |
| Squib 未清续射 | **直接进"特别判定表"**（最高危行为） | E 章 E-3.5 |
| 枪管异物（泥/水） | `barrel_obstruction` 标记（涉水/趴泥射击后未清理） | H 章环境模块 |
| 低 TS 枪管 | `A-8d` TS 区间（土造×4.0 基线即由此来） | A 章 |
| 红热射击 | `heat_ratio > 0.85` 时权重陡增 | G 章（见 G-5 联动决议） |
| OOB 击发 | 状态机守卫漏洞=0（状态机拦截，禁止发生），仅走火态保留小概率 | E 章 |

### F-2.2 统一概率模型（隐藏加权判定）

```
风险池 RiskPool = Σ 各来源权重项 × 全局系数 k_global(默认 1.0，见 Q-10 平衡)
只在"击发"事件时结算：
  roll = random() × 1000 (‰)
  if roll < RiskPool → 炸膛事件，严重度按权重构成取样
  else → 正常射击，RiskPool 自然回落(保养/散热后清零)
设计红线：
  ① 玩家永远看不到 RiskPool 确切数值（只可感知：过热红、哑弹风险、土造枪管）
  ② 但每个权重项都有可观测前兆（闷噗声、过热变色、复装超装自己的选择）
  ③ 永不出现"全新好保养枪+合规弹药"炸膛（基线恒 0）
```

### F-2.3 后果分级（掷严重度）

| 级别 | 概率构造 | 后果 | 状态机去向 |
|---|---|---|---|
| I 轻微（喷焰/泄气） | 风险池低段触发 | 枪管/机匣耐久 -30%；**永久精度衰减一档**（写入 `build.ts_degraded`，不可修复仅可换管）；玩家 1s 耳鸣+击退 | `BURST_DAMAGED`→检修后归位 |
| II 中等（部件损毁） | 中段 | 随机 1–2 关键部件报废（枪管/枪机优先）：耐久清零+`broken=true`，必须回工作台更换；玩家受小伤害+致盲 0.5s | 同上 |
| III 严重（解体） | 高段（通常=Squib 续射或满池过压） | 武器整体报废（变成 `taczind:scrap_gun` 可回收部分金属）；玩家 4–7 ❤ 伤害+视野受损；周围 2 格生物 1–2 ❤ 波及 | 武器移除 |

### F-2.4 概率叙事护栏
- 每次炸膛事件生成"事故报告"tooltip（写进报废件/枪的组件）：列出贡献最高的 2 个权重来源（"主要成因：哑弹卡膛后续射；次要：红热射击"）——把死亡变成**可读的教学反馈**。
- 配置 `burstRisk=false` 时风险池仅造成 I 级耐久惩罚，不损坏不伤玩家。

## F-3. 所需道具/机器清单

| ID | 名称 | 用途 |
|---|---|---|
| `taczind:scrap_gun` | 报废枪械残骸 | III 级产物；回收炉返 40% 金属 |
| `taczind:bore_inspection_lamp` | 验膛灯 | 检查枪管动作提速 50%（E 章联动，T3） |
| `taczind:carry_mud_guard` | 枪口防尘套 | 涉水/沙尘环境防 `barrel_obstruction`（消耗品，H 章共用） |

## F-4. 数据结构建议

枪组件补：`{burst_risk_cache:float, barrel_obstruction:boolean, burst_history:[{tick,severity,top_causes}]}`（历史仅留最近 3 条）。
严重度枚举：`BurstSeverity {MINOR, MODERATE, CATASTROPHIC}` 存 byte。

## F-5. 与 TACZ 衔接

- 击发入口拦截同 E-6（Q-06）；伤害用原版 DamageSource 自定义 `taczind:barrel_burst`。
- 武器报废：ItemStack 替换为 scrap 件（保留部分 NBT 用于事故报告展示）。
- 音效：走 TACZ `sound` 包注册新音效事件（爆音+耳鸣滤波，客户端 mixin 到音效引擎，低侵入）。
# 第 G 章（08）· 过热系统

> 好消息：TACZ 已内置热量骨架——`GunData.heat{heatMax, heatPerShot, over_heat_time}` 与枪组件 `HeatAmount/OverHeated`。本章把"锁射式过热条"升级为**热物理玩法层**（精度浮动+烧蚀+换管+cook-off）。

---

## G-1. 现实原理简述

连发能量大部分转化为枪管热：枪管升温 →(1) 热膨胀+热飘（热浮动 Mirage/POI shift）精度下降；(2) 金属高温屈服强度下降；(3) 到达红热时闭膛武器膛内弹可被高温自燃（cook-off）；(4) 长期过热循环烧蚀膛线起始部（throat erosion），精度永久衰减。史实机枪战术即围绕散热组织：MG42 每 200–250 发速换枪管（<20 秒，副射手携 6 根备管），水冷套筒是一代解法；弹匣供弹的自动步枪因换弹间隙天然限温。

## G-2. 游戏化抽象方案

### G-2.1 温度累积模型
```
每发：Heat += heatPerShot(gunJSON) × 药量系数(B章) × 消音器系数(J章, 1.25)
冷却：dHeat/dt = -cooling(heat_ratio 查表：热→冷非线性，高温段散热快)
     × 环境系数(雪天×1.3 / 下界×0.6 / 水中浸泡立刻归 30% 并打 obstruction 标记)
自然冷却沿用 over_heat_time 作为"100→0 的总时长"锚点。
实现：记录 last_update_tick，读取时一次性结算（惰性冷却），零逐 tick 成本。
```

### G-2.2 过热的实时影响（heat_ratio = Heat/heatMax）

| 区间 | 名称 | 影响 |
|---|---|---|
| 0–40% | 冷态 | 无 |
| 40–70% | 温热 | 散布 ×(1+0.15×t)；瞄准线微扰（客户端） |
| 70–90% | 热 | 散布 ×1.35；瞄具上方热浪粒子（海市蜃楼）；抽壳阻力上升→E 章 FTExtract 权重+ |
| 90–100% | 红热 | 散布 ×1.8；触发 TACZ 原生 `OverHeated` 锁射（保留！）；**cook-off 掷骰窗开启**；G-2.3 烧蚀加速 |
| >100%(过冲) | 白炽(仅消音器+机枪极端连射) | F 章风险池高热权重项点燃 |

### G-2.3 长期烧蚀（Erosion）
`barrel_erosion`（0–100）只增不减：红热区间每发 +0.06，热区 +0.02，温区 +0.002。烧蚀值直接压低该枪管**精度档与耐久上限**（I 章枪管部件读此值）；换管即归零——枪管成为真正的消耗品。

### G-2.4 速换枪管机制（机枪类）
- 枪 JSON 增量 `taczind.qcb: true`（quick change barrel）。
- 交互：长按 [换管键]（需背包内备用枪管物品），2.5s 动画（戴石棉手套 `taczind:asbestos_mitt` 则 1.5s，徒手拿红热管=每 0.5s 0.5❤），旧管变 `hot=true` 的枪管物品进背包（不占格拖拽丢弃冷却，冷却需 90s 才可复装）。
- 备管是完整物品：`taczind:spare_barrel`（含 B/A 章全部枪管组件字段——鼓励常备 2 根）。

### G-2.5 cook-off（闭膛专属）
closed_bolt 且 heat_ratio≥0.9 且弹在膛：每 tick 0.1%（可配）触发意外击发（方向=当前指向）。设计为**风险告知**：HUD 出现"膛内红热"图标，玩家应主动退弹。open_bolt 免疫（历史正确！开膛待机就是为散热而生）。

## G-3.【AI补充】炸膛-温度联动设计决议

**需要做，但做成"阈值权重"而非线性相关**，理由：①现实中红热枪管强度下降是炸膛主共谋之一，删除会损失真实感；②线性叠加会产生"连射机枪必然炸"的迭代体验灾难；③阈值式（heat_ratio>0.85 才注入权重，且 0.9 以上加权陡增）让"打红一根管"成为明确的风险决策点而非隐形累积税。公式见 F-2.2，常量入 JSON。

## G-4. 所需道具/机器清单

| ID | 名称 | 用途 |
|---|---|---|
| `taczind:spare_barrel` | 备用枪管 | QCB 武器换管消耗位 |
| `taczind:asbestos_mitt` | 石棉手套 | 换管提速防烫（胸槽装备） |
| `taczind:barrel_cooling_pouch` | 水冷套（重武器座射用） | 重机枪部署态散热 ×2，移动不可 |
| `taczind:heat_shield_handguard` | 隔热护木 | 配件槽：手温惩罚移除（否则红热持握持续小伤害），散热微降 |

## G-5. 数据结构建议

枪组件 `taczind:thermal`：`{heat:float, last_tick:long, erosion:float, hot_barrel_inserted:boolean, qcb:boolean(缓存)}`。
备管物品组件 `taczind:barrel_item`：`{caliber, twist, heat, ts, wear, erosion, hot_until_tick}`。

## G-6. 与 TACZ 衔接

- **最大复用点**：`HeatAmount/OverHeated/GunHeatData` 直接扩展——原生锁射行为保留（`over_heat_time` 语义转为冷却曲线锚点）。
- 热浪粒子/海市蜃楼：客户端渲染层（`client/render` 新增 overlay/mixin）。
- 换管键位：E 章同键位体系；动画走 TACZ 状态机插槽（`client/animation/statemachine`）。
- 性能：惰性冷却结算 + heat 每 10 tick 才同步一次客户端（插值平滑）。
# 第 H 章（09）· 保养与维护系统

> 定位：E/F/G 的"逆系统"——玩家投入时间与耗材，把积碳、锈蚀、异物等负面状态清除。它把"战斗循环"与"工坊循环"缝合成一个完整节奏。

---

## H-1. 现实原理简述

枪械保养四件套：**通条(cleaning rod)、溶剂(solvent，溶解积碳/铜垢/中和腐蚀性底火残渣)、刷子(bore brush)、润滑油/枪油(lubricant)**。标准流程：验枪卸弹→分解(field strip)→刮/刷去积碳→溶剂处理（腐蚀性弹药必须！）→擦净→薄油润滑关键摩擦面→复装→功能检查。黑火药易燃积碳重、残渣吸湿腐蚀（打完当天必须清洗）；无烟火药残渣少，现代枪可数百发一清。沙尘环境油多反吸灰、严寒环境油凝稠（自动循环变慢），野外掉入泥水必须先清膛。

## H-2. 游戏化抽象方案

### H-2.1 两条累积轨

| 轨道 | 字段 | 累积 | 影响 |
|---|---|---|---|
| 积碳 Fouling（0–100） | `taczind:maintenance.fouling` | 每发：黑火药 +0.8 / 无烟火药 +0.2 / 快燃药错配再 ×1.5（B 章）；100 到顶 | Fouling>40：E 章各故障 bp 加成 ×(1+f/50)；Fouling>80：枪机循环动画粘滞+故障再 ×2 |
| 锈蚀 Rust（0–100） | `taczind:maintenance.rust` | 腐蚀性底火射击后 24h 未溶剂清洁 +8/h；泡雨 +2/h；水下射击 +20 即时；长时间(3 天)不保养 +1/h | Rust>30：部件耐久磨损速率+f/2；Rust>60：衬面咬死→FTExtract bp ×3；Rust 100：枪机锈死，完全不可用直到全面保养 |

锈蚀视觉：**贴图阶段叠加**——枪视模/图标按 rust 三档（30/60/100）叠棕色锈斑 shader 遮罩（客户端渲染层 mix-in，成本低效果好，Q-11）。

### H-2.2 环境交互（可选难度模块 `environmentModule`）

| 环境 | 效果 |
|---|---|
| 沙尘（沙漠/恶地/下界灵魂沙峡谷） | 进弹/抽壳 bp ×2；佩戴防尘套(F 章枪口套)免疫；油润滑>70 时反噬（吸沙，bp 再 +0.5‰——"油多吸灰"） |
| 严寒（雪原/冻洋） | 润滑油凝滞：润滑<50 时枪机循环时间 +20%（射速体感下降）；烘烤怀炉或换低温油(`taczind:oil_arctic`，T3)免疫 |
| 潮湿/雨/水下 | 黑火药瞎火 bp×2（C-5 决议）；钢壳弹药锈蚀加速；涉水后获得 `barrel_obstruction` 6s（须甩枪/检查枪管清除） |

### H-2.3 保养操作流程（进度条+轻小游戏）

跳过完整 mini-game Hell：采用"**工序进度条+一次关键时机校验**"混合模式——在保养台 GUI 放入四件工具，点击开始后按顺序播 4 段进度条（分解→清洗→擦干→润滑复装共约 20s，可快进为 8s（技能/工具加成），润滑段有一次"适量油"节奏校验（滑块停在绿区=润滑 70 黄金值，过油/欠油见 H-2.2 反噬）。

循环节奏设计目标：**T2 军剩弹时代=每战斗日必保养；T4 无烟+无腐蚀时代=3–4 日一保养**——保养负担随科技下降本身就是时代奖励。

### H-2.4 清洁工具与耗材

| ID | 名称 | 类型 | 用途 |
|---|---|---|---|
| `taczind:cleaning_kit` | 野战通条组 | 工具(耐久 60) | 野外快速保养（仅锈/积碳减 50%，可清 Squib！E 章必需） |
| `taczind:bore_brush` | 膛线刷 | 工具(耐久 40) | 保养台内提升清洁效率 50% |
| `taczind:solvent` | 枪械溶剂 | 流体/瓶装 | 中和腐蚀性残渣必需 |
| `taczind:gun_oil` / `oil_arctic` | 枪油 / 低温油 | 瓶装 | 润滑工序；低温油解锁严寒免疫 |
| `taczind:maintenance_bench` | 保养台 | 方块 | 全面保养+部件检测(显示各部件耐久) |
| `taczind:gun_vise` | 枪钳台 | 方块(保养台升级) | 保养耗时-30%，放开"全面拆解"页签(I 章换件) |

## H-3. 数据结构建议

`taczind:maintenance` 组件：`{fouling:float, rust:float, lube:float(0–100), corrosive_pending:boolean, last_clean_day:int, obstruction:boolean}`。
方块实体（保养台）：仅 GUI 容器态，无 tick 逻辑（操作结束时一次性结算）。

## H-4. 与 TACZ 衔接

- 独立方块+GUI（`client/gui` 新增）；对枪组件读写用 `ItemDataAccessor` 同款访问器风格，例如 `MaintenanceDataAccessor`。
- 锈斑 shader：`client/render` 层给枪模型 post-process 叠 mask（**需读源码确认抢渲染管线 hook 点** Q-11）；降级方案=换图标贴图。
- 涉水/环境事件：订阅 Fabric 事件（实体入水 tick、群系变更）打标记——低频率事件，性能无感。
# 第 I 章（10）· 模块化耐久系统

> 定位：把"一把枪一根耐久条"拆成**会骗保的十二件小事**——每个部件独立寿命、独立故障签名、独立更换经济。这是连接 A（造零件）与 H（保养）的经济循环枢纽。

---

## I-1. 现实原理简述

枪械各部件寿命差异巨大且失效模式不同：撞针（疲劳断裂/磨圆→瞎火）、抽壳钩（断爪→抽不出壳）、复进簧与弹匣弹簧（**弹簧"疲软"（set）**——循环次数多了自由长度缩短、力度下降→复进不到位/供弹不畅）、枪管（膛线烧蚀寿命数千发计）、机匣（铆接松旷/导轨磨损）、扳机组（阻铁磨损→走火风险）。军队做法是部件级互换维修——军械士换件不换枪。

## I-2. 游戏化抽象方案

### I-2.1 部件清单与耐久参数

| 部件 slot | 中文 | 耐久上限基准(发数) | 主要损耗源 | 失效签名 |
|---|---|---|---|---|
| `barrel` | 枪管 | 4000×耐久倍率(A-8d) | 每发+烧蚀(G 章) | 精度档下滑；烧蚀满=精度地板 |
| `receiver` | 机匣 | 12000 | 每发微损+炸膛事件重伤 | 冲压机匣铆接"松旷"：散布缓升；铣削高但怕炸 |
| `bolt` | 枪机 | 6000 | 每发微损+积碳加成 | FTF/FTExtract bp 缓升 |
| `extractor` | 抽壳钩 | 2500 | 钢壳弹药×1.15 | 断爪：FTExtract 突发高发 |
| `firing_pin` | 撞针 | 3000 | 每发+空击×2 | 磨圆：瞎火 bp ×档(E-4) |
| `recoil_spring` | 复进簧 | 5000 循环 | 每循环+高温加速 | 疲软：循环时间+10%→FTF 上升 |
| `mag_spring` | 弹匣弹簧 | 3000 循环(随弹匣物品!) | 装弹/射击循环 | 供弹不畅→FTF bp 升；可与匣体分离更换 |
| `trigger_group` | 扳机组 | 8000 | 每发微损+沙暴环境 | 阻铁磨损→SLAMFIRE bp 升（<15% 耐久起） |
| `muzzle_device` | 枪口装置 | 2000 | 每发+过压加成 | 消音挡片烧蚀→K 章效果衰减；制退器效率降 |
| `gas_system` | 导气系统(导气枪) | 5000 | 每发+积碳×2 | 导气孔堵→循环慢/短后坐(复进能量不足) |

### I-2.2 加权影响公式（整体可靠性）

```
ReliabilityFactor = Π (1 - wear_ratio_i × criticality_i)   // 各部件连乘
criticality: barrel .8 / bolt .9 / extractor .7 / firing_pin .8 / springs .6 / trigger .5 / receiver .8 / muzzle .3 / gas .7
散布加成 = 1 + barrel_wear×0.6 + receiver_wear×0.25 + crown(muzzle)_wear×0.2
各故障 bp = base × (1 + Σ 相关部件 wear_ratio × k_i)
```

### I-2.3 弹簧疲劳细化
弹簧双参数：`free_length_pct`（100→70 疲软趋势）与 `set_cycles`。弹满匣压弹簧存放会加速 set（真实惯例：弹匣不满存——游戏化为"满匣存放每游戏日 -2"，鼓励轮换弹匣）。疲软阈值 85%：FTF bp 开始抬升；70%：循环卡滞肉眼可见（动画减速）。

### I-2.4 维修/更换体系

| 磨损区间 | 名称 | 处置 |
|---|---|---|
| 0–60% | 轻度 | 保养台"调整"页签：恢复等效 20% 磨损（限 3 次/部件——挡圈研磨类比） |
| 60–100% | 重度 | 必须**更换部件**：保养台+枪钳台拆件（部件落回背包成为可交易物品），装入新件（消耗对应 A 章零件/成品部件） |
| 100%＋事件 | 报废 | 部件 `broken=true` 只能回炉回收 30% 金属 |

由此形成市场：玩家间交易"二手枪管/名厂机匣"，TS 与磨损共同定价（内建交易不成，但为服务器经济提供标的物）。

## I-3. 道具/机器清单

| ID | 名称 | 用途 |
|---|---|---|
| `taczind:part_*`（12 种部件物品，多个材料/TS 变体） | 备件 | 更换/交易/产线产物 |
| `taczind:parts_tray` | 零件托盘 | 保养台拆件暂存展示方块 |
| `taczind:spring_tester` | 簧力计 | 显示弹簧 free_length 精确值（T3 测具线） |

## I-4. 数据结构建议

`taczind:parts` 组件（枪）：`Map<slot, PartState>`，`PartState{part_id, wear:float, max_wear:float, broken:boolean, service_count:int, spring_free:float?, ts:int(出生即写死)}`。
弹匣作为**独立物品**拥有 `mag_spring` 部件态（TACZ 当前无独立弹匣物品→见 N 章供弹具系统决议，Q-12）。

## I-5. 与 TACZ 衔接

- 不替换 TACZ 任何耐久逻辑（TACZ 枪无耐久）——纯旁路组件层；
- 磨损挂钩：射击事件链（Q-06 同点）+每次循环状态转移事件自增；
- 换件 UI：保养台第二页签；`GunItemDataAccessor` 同款访问器模式 `PartsDataAccessor`。
# 第 J 章（11）· 模块化改装系统

> TACZ 已有完整配件体系：`AttachmentType`（scope/muzzle/grip/stock/laser/extended_mag…）+ modifier 数据驱动属性修正。本章做三件事：**导轨标准（不再无脑通用）、枪管级改装（长度/缠距/口径）、归零与消音器损耗**。

---

## J-1. 现实原理简述

配件接口有事实标准：燕尾槽（AK 系侧轨）、皮轨 MIL-STD-1913（Picatinny）、M-LOK/KeyMod（现代轻量化），互不兼容需转接。枪管是**性能根源**（长度定初速、缠距定弹种适配、厚度定热容与重量）。光学瞄具使用按距离归"零"（某距离上弹着=瞄准点），换距离不调整=系统性偏差。消音器靠内部挡片(baffle)消耗燃气能量；挡片逐发烧蚀，湿热条件下"湿式消音"增效；超音速弹丸仍产生音爆N波——**消音不消音爆**，所以隐蔽射击=亚音速弹+消音器成套使用。

## J-2. 游戏化抽象方案

### J-2.1 导轨/挂载点兼容性
枪 JSON 增量 `taczind.mount_systems`（数组，如 `["picatinny","m-lok"]`）；配件增量 `taczind.mount: "picatinny"`。装配校验：不匹配→灰显+tooltip"需转接"。
新增**转接座配件**（`taczind:adapter_dovetail_to_pic` 等）：占用一个槽位、+50g、TS-2 惩罚，让"混搭"成为有代价的决策。

### J-2.2 可更换部位完整清单

| 部位 | 挂槽(AttachmentType 复用/新增) | 可变参数 | 性能影响 |
|---|---|---|---|
| 枪管 | `barrel`（**新增类型**） | 长度档 S/M/L、缠距、口径(限同族转换) | 初速(C-2.1)、重量、热容(G)、匹配 Sg(C-3) |
| 枪口装置 | `muzzle` | 消焰/制退/消音 | 见 J-2.6 矩阵 |
| 瞄具 | `scope` | 机械/红点/低倍/高倍 | 归零(J-2.3)、ADS 速度、摆动 |
| 握把 | `grip` | 垂直/直角/轻型 | bloom 恢复 τ、散布 |
| 枪托 | `stock` | 固定/折叠/重型 | 后坐、ADS、重量 |
| 两脚架 | `underbarrel` | — | D-2.6 依托表 |
| 弹匣井/供弹转换 | `magwell`（**新增类型**） | 口径族转换套件 | 见 N 章供弹具 |
| 背带 | `strap`（**新增类型**） | — | D-2.6 |
| 激光/灯 | `laser` | TACZ 原生 | — |

### J-2.3 瞄具归零（Zeroing）
- 机械瞄具/红点/倍镜均可"归零"：默认零位 50 格；玩家按 [PgUp/PgDn] 在瞄具距离档（25/50/100/200/300…按瞄具能力）切换；切换播"咔哒"调节声+0.3s。
- 偏差计算：弹着相对瞄准点的偏移 = 该距离真实弹道下坠 - 当前归零档预期下坠；**不归零远程射击必然打低**——把 C 章弹道知识转为玩家技能。
- 倍镜密位版：T4 高倍镜带"测距分划"（对准目标 0.5s 显示距离估算，目镜内 UI）。

### J-2.4 消音器专属机制
- `taczind:suppressor` 组件：`{baffle_wear:0–100, wetted:boolean}`。
- 每发烧蚀挡片：+0.4（亚音速减半）；消音效果 = 基础值 × (1 - baffle_wear/150)（到 100 时仅剩 33% 效果+噪音特征回升，**可拆洗保养**回复到 60% 上限，换新挡片回 0）。
- 匹配亚音速弹（B-6）才可达 K-2 隐蔽阈值；超音速+消音：初段枪声降但保留"音爆裂响"标志声（传播半径-40% 但不可隐匿）。
- 消音器副作用：背压→积碳 ×1.5、过热增速 ×1.25（G 章）、背压增循环可靠性（游戏化衡量：loop 故障 bp-10%——导气枪）——取舍立体。

### J-2.5 配件数值加成矩阵（节选；全表入 `data/taczind/balance/attachment_matrix.json`）

| 配件 | 散布 | bloom | bloom恢复 | ADS | 重量 | 噪音 | 特殊 |
|---|---|---|---|---|---|---|---|
| 两脚架(展开) | ×0.55 | ×0.5 | — | — | +300g | — | 部署判定 |
| 直角握把 | — | — | τ×0.8 | ×0.95 | +120g | — | 近战流派 |
| 垂直握把 | — | ×0.85 | — | — | +150g | — | 扫射流派 |
| 重型枪托 | ×0.9 | ×0.9 | — | ×1.1 | +400g | — | 蹲射流派 |
| 轻型折叠托 | — | ×1.05爬升 | — | ×0.9 | -200g | — | 机动力 |
| 制退器 | — | ×0.75(纵向) | — | — | +80g | +30%侧向噪音 | 机枪抑制 |
| 消焰器 | — | — | — | — | +60g | 枪口焰可见性↓↓(K) | 夜战 |
| 消音器 | — | — | — | ×1.05 | +450g | 见 J-2.4 | 过热/背压 |
| 红点 | — | — | — | ×1.0 | +90g | — | 归零 25–200m |
| 4倍镜 | — | — | — | ×1.15 | +300g | — | 归零 50–600m+测距 |

### J-2.6 换枪管=换性格的改装
同枪身换长管→DMR 化（初速+重量+ADS 慢），短管→CQB 化；缠距选项与弹种市场联动（重弹头亚音速狙需快缠管=高价配件）。口径转换套件（`magwell`+`barrel`+`bolt_face` 三件齐换）让 5.56 平台打手枪弹——真实"口径族"玩法。

## J-3. 数据结构建议

枪组件增量：`taczind:build_ext`：`{barrel_slot:{len_class,twist,heat...}, zero_m:int(当前归零档), suppressor:{baffle_wear,wetted}}`。
配件物品组件复用 TACZ `AttachmentId` + 新增 `taczind.mount` 与各自磨损字段。

## J-4. 与 TACZ 衔接

- AttachmentType 新枚举值（barrel/magwell/strap）：**需读源码确认 AttachmentType 是否可外部扩展/注册**（Q-04 系列）；若枚举硬编码→旁路"伪配件"方案（表现为普通配件+标签驱动行为）。
- 归零输入：新键位+客户端 scope 渲染层（`client/render/scope` 已有 per-scope 渲染管线，增量加"归零档"读数与弹着偏移计算）。
- 配件 modifier：扩散等修正全部走 TACZ `resource/modifier` JSON 通道；本系统独有的（噪音特征、baffle_wear）写 `taczind` 旁路组件。
# 第 K 章（12）· 声学与隐蔽系统

> 目标：让"开火会暴露你"成为真实机制——声音、枪口焰、曳光、烟尘四条暴露通道，配合 J/B 章的消音/亚音速/消焰手段构成对抗博弈。

---

## K-1. 现实原理简述

枪声 = 膛口冲击波（主能量，随口径/装药增长）+ 弹丸音爆 N 波（超音速固有，消音器无法消除）+ 机械运动声（低量级）。暴露手段还有夜间枪口焰（消焰器压制）、曳光弹发光示踪、黑火药大团白烟。侦测方靠声源方向+强度与视野内光迹定位射手。

## K-2. 游戏化抽象方案

### K-2.1 声音传播模型
```
NoiseRadius(m) = base_by_caliber (手枪 24 / 中间威力 48 / 全威力 64 / 大口径 96, JSON)
               × fire_sound.fire_multiplier(TACZ 原生乘数, 联动)
               × suppressor_effect (J-2.4, 亚音速最小 0.12)
               × weather (雨 ×0.8 / 雷暴 ×0.65)
               × biome_enclosure(洞穴 ×1.2 回响)
事件广播：射击点 NoiseEvent{pos, radius, shooter_uuid, signature_id} —— 事件驱动一次性广播，不做持续辐射。
```
- **惊动判定**：生物 AI 挂接 vanilla 的 `game events`（潜行侦测同款通道）或自建轻量监听表：半径内敌对生物→警戒/索敌该点；袭击中单位→增援权重。村民-逃散，动物-惊跑。
- PvP：玩家在半径内听到方向性枪声（3D 音效原生），不做"小地图红点"（硬核）。可配置服务器选项。

### K-2.2 隐蔽射击阈值
定义隐蔽分级（供玩法目标/成就与未来 NPC 模组使用）：

| 组合 | NoiseRadius | 判定标签 |
|---|---|---|
| 裸枪大口径 | 96m | 暴露 EXPOSED |
| 消音+超音速 | ~28m 含音爆裂响 | 压音 SUPPRESSED |
| 消音+亚音速+夜+消焰 | ≤6m | **隐匿 COVERT**（生物 8m 外不觉察；成就"幽灵枪手"解锁线） |

### K-2.3 视觉暴露通道

| 通道 | 机制 | 对策 |
|---|---|---|
| 枪口焰 | 夜间 32m 内可见闪光源（客户端光事件+生物侦测同 K-2.1）；慢药短管 ×1.5 时长 | 消焰器↓↓、无焰火匹配正确 |
| 曳光弹 | 飞行发光（TACZ 原生曳光渲染）+ 落点暴露射手方位 | 富官:白天用、夜战禁用或混装(每4发1曳引导) |
| 黑火药烟 | 射击点 6s 滞留白色烟团粒子、射手短暂"标记"（生物追踪该点） | 无烟药换代 |
| 热源 | （可选）红热消音器散微光 | 冷却轮换双消音 |

## K-3. 道具清单

| ID | 名称 | 用途 |
|---|---|---|
| `taczind:suppressor_*` 系列 | 消音器 | J 章联动产物 |
| `taczind:scent_wick?` 否——保留纯声学，不扩展气味系统（克制范围蔓延） | — | — |
| `taczind:ear_muffs` | 射击耳罩 | 玩家自保：连射耳鸣减免（头戴槽） |

## K-4. 数据结构建议

- `NoiseSignature`（枪 JSON 增量）：`{base_radius:int, sup_capable:boolean, crack_signature:boolean}`。
- 运行时 NoiseEvent：纯事件对象，不进存档；生物侦测结果写入生物自身 Brain memory（vanilla 机制），零新存储。

## K-5. 与 TACZ 衔接
- `fire_sound.fire_multiplier / silence_multiplier` 原生乘数直接进公式；
- 音效引擎走 TACZ `sound` 注册表；
- 生物 AI：mixin 注入 `Mob` 的目标选取或 Fabric `entity` 事件挂 NoiseEvent 监听（**需读源码确认本仓库是否已有 sound-event→mob 通知链** Q-13）。
# 第 L 章（13）· 后勤与仓储系统

> 定位：产业成果的"终点站"与战斗准备的"出发站"。含弹药箱、武器架、携行具、军械贸易四个子块。

---

## L-1. 现实原理简述

弹药的后勤形态：散装→桥夹/漏夹预组装→弹药箱(防潮金属罐)→弹药库。携行具决定**可用弹量在时间与空间上的分布**：胸挂快拔、背包大容量慢取、弹链箱供班组机枪。历史上的携弹量标准化（每名步兵多少发）本质是后勤数学。

## L-2. 游戏化抽象方案

### L-2.1 存储展示方块

| ID | 名称 | 类型/容量 | 机制 |
|---|---|---|---|
| `taczind:ammo_crate_small` | 弹药箱(小) | 方块 9 格 | 存散装/整盒；防潮（雨季防锈） |
| `taczind:ammo_can_metal` | 密封弹药罐 | 方块 6 格 | 完全防潮+弹壳防锈 |
| `taczind:ammo_box_item` 变体兼容 | 弹链箱 | 方块 | 供 N 章弹链供弹具；可与机枪部署位联动"放箱即供弹" |
| `taczind:weapon_rack_wall` | 壁挂枪架 | 方块(4 位) | 展示+快速取用；枪在架上不进"潮湿锈蚀"（室内判定） |
| `taczind:weapon_crate` | 武器运输箱 | 方块(2 枪位) | 打包枪(含全部组件)+防锈；长途贸易/服务器快递 |
| `taczind:display_case` | 展示柜 | 方块 | 查看枪的完整 build 报告（TS、部件、战绩刻痕 kill_count——【AI补充】武器履历系统） |

### L-2.2 携行具系统（胸挂/腰包/背包）

新增装备槽"携行具"（复用胸甲槽的 trinkets 式旁挂槽——Fabric 生态按 Q-14 决议用自研槽位避免依赖）。

| 携行具 | 等级 | 备用弹匣位 | 换弹速度修正 | 负面 |
|---|---|---|---|---|
| 无 | — | 0（背包摸弹+60% 时间惩罚） | ×1.6 慢 | — |
| `taczind:mag_pouch_belt` 腰包 | T1 | 2 | ×1.15 | — |
| `taczind:chest_rig` 胸挂 | T2–T3 | 4 | ×1.0（基准） | 占护甲视觉层 |
| `taczind:chest_rig_molle` 模块化胸挂 | T4 | 6 | ×0.85 | +800g |
| `taczind:assault_pack` 突击背包 | T3+ | 2匣+弹药 27 格 | ×1.1 | 换弹略慢但量大 |

实现：携行具 NBT 存"弹匣位内容列表"（弹匣=N 章独立物品）；换弹速度修正写入 D 章 runtime ergonomics 缓存。**战术换弹（留弹匣）/空仓换弹（弹匣掉落）**两个分支已在 TACZ reload JSON 内，携行具只加速率系数。

### L-2.3 军械贸易（村民/经济接口）
新增村民职业"军械师"（工作站点=`taczind:inspection_bench` 或挂枪架）：收购 TS 高产品、出售蓝图与消耗品（溶剂/油/底火），按 A-8d 称号溢价倍率定价——给公差系统一个直接经济出口。配置项关闭（服务器可自接经济插件）。

## L-3. 数据结构建议
- 存储方块 BE：标准容器+`sealed:boolean`（防潮）。
- 携行具物品组件 `taczind:rig`：`{slots_count:int, mags:List<ItemStack摘要>, draw_mult:float}`。
- 枪履历 `taczind:gun_history`：`{kill_count:int, shots_fired:long, crafted_by:String, factory_tag:String}`（展示柜读取）。

## L-4. 与 TACZ 衔接
- `weapon_rack` 展示渲染用 TACZ 的 LOD 模型管线的 block 版（`client/renderer/block` 已有范例）；
- 携行具换弹加速 = reload feed/cooldown 数值 Modifier（TACZ reload JSON 已分 empty/tactical 两时档，本项目乘系数）；
- 弹匣位=N 章供弹具物品。**需读源码确认 TACZ 是否有独立弹匣物品**（结论：没有，`extended_mag` 是配件抽象）→ N-1 决议为"旁路弹匣物品系统"（Q-12）。
# 第 M 章（14）· Create·飞翔版 自动化联动系统

> 定位：T3 之后一切产能的发动机。原则：**机器没有动力网就是摆设**，动力中断=停产，没有离弦产量。

---

## M-1. 现实原理/生态原理简述

Create 的动力学核心是**应力(Stress)**：每个动力源提供应力容量（如水车轮 512 SU），每台挂网机器占用应力（SU = RPM × 单位应力），转速 RPM 决定机器工作速率；容量超限=全网静止(overstressed)。传送带、漏斗、机械臂( depot/机械手 )构成物流。

（注："Create·飞翔版"按社区对 Fabric 移植 Create 的常用叫法理解，API 面与 Forge 版同构；实际依赖坐标见 Q-02。）

## M-2. 游戏化抽象方案

### M-2.1 必须接动力网的机器清单

| 机器 | SU 需求 | 最佳 RPM | 单位应力 | 说明 |
|---|---|---|---|---|
| `power_press` 动力冲压 | 1024 | 128–256 | 8 SU/RPM·台 | 弹壳引伸/机匣冲压 |
| `power_lathe` 动力车床 | 768 | 96–192 | 8 | 枪机/管件 |
| `kinetic_rifler` 拉膛线机 | 512 | 64–128 | 8 | 膛线 |
| `rolling_mill` 轧机 | 1536 | 64–128 | 24 | 板材 |
| `chem_stirrer` 化学搅拌(反应釜部件) | 1024 | 32–64 | 32 | 硝化搅拌；**RPM 掉出区间即风险累积**(A-5) |
| `powder_press` 自动压弹台 | 512 | 64–128 | 8 | B 章复装产线化工位 |
| `assembly_arm_station` | 依赖 Create 机械臂 | — | 机械臂自身 | 总装 |

`energy_stability`（动力稳定性，→A-8 公差）：取挂网 RPM 的 5s 滑动平均波动率；Create 网络因负载变化转速波动→产品 TS 抖动。玩家想要稳定高 TS，就得建**专用稳速支路**（齿轮箱定比+冗余动力源）——动力工程成为品质工程。

### M-2.2 动力中断的影响
- overstressed/断轴：机器 **立即停工**（配方进度保留）；硝化釜例外——搅拌停了**风险累积计时开始**，30s 后产物报废（A-5 危险工位），这教会玩家给化学区配独立动力冗余。
- 不存在"离线生产"：BE 全部走 Create 网络 tick 内事件，不独立登记世界 tick。

### M-2.3 物流衔接设计（矿石进→成品出）

物流骨干：Create 传送带 + 漏斗/漏斗槽 + 机械臂( depot 工位抓取 ) + 鼓风机(可选批量清洗/淬火)。

| 接口 | 实现 |
|---|---|
| 机器上下料 | 本模组机器全面实现 vanilla `Container`/`WorldlyContainer`（漏斗协议），Create 机械臂(放置在 depot 上交互)天然兼容 |
| 弹壳回收 | 战场无方案；产线内"废壳溜槽"漏斗回分拣 |
| 质检分拣 | 检测台输出按 TS 打不同出口（黄铜漏斗分流） |
| 弹药装盒 | `powder_press` 输出→Create 辊压/包装工位→`ammo_crate` |

## M-3. 示例工厂布局（完整说明）："红石兵工厂"标准车间

空间规划建议 **21×33 格主厂房 + 水侧动力廊**，五区一线：

```
[动力廊(北墙外)] 水车轮×8(4096 SU) → 主轴(齿轮箱定比 128RPM) → 南北贯穿主轴干线
        │直交分流(离合齿轮：每区独立可断)
[1.原料区 21×6]  西侧门进矿：料仓(木桶/物品保险库) → 粉碎轮(Create)→ 粗炼炉排×6
        │板条物流带 A →
[2.冶金零件区 21×9] 坩埚炉组、轧机×2(啮合分流) → 板材仓 ‖ 动力车床×4 / 动力冲压×4(直挂干线)
        │带 B (零件托盘化：Create 智能溜槽按物品过滤分流三条支线)
[3.化学区 21×6·独立隔断+独立动力支路×2冗余] 反应釜×2 硝化釜×1 造粒×1（危险工位远离主产线，泄爆墙=朝向厂房外）
        │带 C (流体管+物品带混合)
[4.弹药区 21×7]  装药机→装底火→压弹台→检验(测速门抽检玩法)→装盒→弹药箱仓
[5.总装区 21×11] 三条支线汇入总装带：机匣工位(冲压/铣削双线)→枪管工位→枪机工位
        → 机械臂×3 顺序装配站 → 终检台(读 TS，低于门槛回零件区) → 武器架墙/装箱
        → 出货口(火车装卸站台，Create 列车远距离贸易)
人均节拍目标(T5 参考值)：满网 4096SU 供两条装配线 ≈ 1 整枪/90s、1 盒弹/12s。
```

布局经验法则（写进游戏内手册 Ponder 场景，Create 有 Ponder 教学体系可挂接 Q-15）：
- 化学区独立动力冗余+泄爆朝向；
- 质检工位全部逆向回路（不合格件回炉物流环）；
- 皮带层高立体化：原料带在下、零件带在中、成品带在上，十字交叉用隧道/竖直传动箱。

## M-4. 数据结构建议
- 动力机器 BE 公共基类 `TaczindKineticBlockEntity`（继承 Create `KineticBlockEntity`，Q-02）：`{su_need, rpm_opt_min/max, wear, energy_stability, queue[]}`。
- 产线配方 JSON：`data/taczind/machine_recipes/*.json`（含 `rpm_min/max`、`su` 字段，数据驱动）。

## M-5. 与 TACZ 衔接
不直接耦合 TACZ——衔接点是**产物**：总装台最终调用 `GunItemBuilder` 输出标准 TACZ 枪械。软依赖检测：无 Create 环境隐藏全部动力配方（T2 手动链仍可达），见 18 章 Q-02。
# 第 N 章（15）·【AI补充】任务书未列出的关键子系统

> 本章全部为主动补充。判断标准：缺少这些机制，硬核玩家一眼看出"敷衍"；补齐则构成与主流枪械模组的**分水岭**。

---

## N-1. 供弹具机构差异化系统（最重要补充）

### 现实原理简述
"弹匣"只是供弹具的一种。真实谱系：**转轮弹巢**(rebel：逐膛单发、慢装、闭气好)、**管状弹仓**(tubular：杠杆/霰弹枪，尖头弹危险故用平头——历史细节)、**桥夹装固定弹仓**(stripper→内仓，李恩菲尔德)、**漏夹**(en-bloc 整体入仓、打空弹出"叮"，M1 加兰德)、**可拆盒式弹匣**(现代主流)、**弹链**(belt：机枪，供弹复杂故故障模式独特)、**弹鼓/弹盘**(常翻车的高故障件)。不同机构交互逻辑、故障模型、携行经济完全不同——**绝不能用一套"弹匣容量 int"全包**。

### 游戏化抽象方案
引入 `FeedDeviceType` 枚举与独立数据结构（TACZ 原生 `FeedType` 只有 magazine/manual/fuel/inventory 四类，增量挂接）：

| 机构 | 枚举 | 数据/交互要点 | 故障签名 |
|---|---|---|---|
| 转轮弹巢 | `CYLINDER` | 按"膛位数组"存弹（含空膛/哑弹位置记忆！）；逐着装/退壳杆全退；**空仓挂机概念不存在，数弹靠记忆/"抖出看"** | 哑弹只能逐膛排查；结构防 double feed |
| 管状弹仓 | `TUBE` | 队列式容量；装一发进一发（打断装填自然顶弹）；尖弹头禁装（数据校验，历史正确） | 弹簧管疲劳(部件)；卡管故障 |
| 桥夹固定仓 | `INTERNAL_CLIP` | 有"随仓弹夹"物件；装填=插桥夹压弹一次动作 | 桥夹缺失时装填×3 慢 |
| 漏夹仓 | `ENBLOC` | 漏夹随弹入膛；打空"叮"+弹出漏夹(50%可回收)；**中途换弹被迫抛整夹**（真实争议点做成玩法） | 漏夹弯折废品 |
| 盒式弹匣 | `BOX_MAG` | 弹匣=独立物品(自带部件态 mag_spring，I 章)；L 章携行具循环 | 弹匣唇口变形→FTF |
| 弹链 | `BELT` | 链节物品、可散/不可散链；供弹机勾弹——**供弹故障率天生最高**，弹链在弹药箱内计量 | 勾弹失败/链卡；受弹器盖开盖检修动作 |
| 弹鼓/弹盘 | `DRUM/PAN` | 大容量+上发条(鼓)小游戏；**故障率=同容量弹匣 3 倍**（历史正确：弹鼓多数不可靠） | 发条疲软 |

### 与 TACZ 衔接
枪 JSON 增量 `taczind.feed_device`（若缺省=按 TACZ 原生 FeedType 行为，零兼容负担）。**独立弹匣物品系统**：GUN 内 `GunCurrentAmmoCount` 与"随枪当前接合的弹匣组件镜像"双轨同步（同 B-9 Q-06/Q-12 联合验证）。转轮/管仓机构用 `manual_actions` 现有动画轨拓展。

---

## N-2. 扳机组细节系统

### 现实原理简述
扳机不只是一个布尔：单动(SA：击锤需先压起，行程短而脆)、双动(DA：扳机全程压起击锤，行程长而重)、单双动混合(DA/SA：首发重随后轻)、扳机力(pull weight)大小直接影响"扣动瞬间的扰动"——竞赛枪 1kg 级，军规枪 3–5kg 防走火。转轮双动长行程是"扣到底叠射击"的手感来源。

### 游戏化抽象方案
- 枪 JSON 增量 `taczind.trigger`：`{type:SA/DA/DASA, pull_weight_class:LIGHT/STD/HEAVY}`。
- 轻扳机：按下→击发延迟 -80ms 且**首发散布-15%**，但 I 章扳机组磨损与走火 bp ×1.5（竞赛扳机的安全代价，真实权衡）；
- 双动转轮首发：延迟+200ms、散布+10%（扣动扰动），换来无需待击的可靠性；
- 半自动 SAO 手枪需手动"压击锤/上膛"（键位 E 章），教学深度。

---

## N-3. 武器重量与战斗节奏系统（系统性整合）

把 A(材料)/D(人机)/J(改装)的重量账汇总成**节奏总账** `combat tempo index`：
- 重量 → 移动速(D-2.4)、ADS(D-2.3)、摆速(转身惯性=视角灵敏度软上限，客户端)、体力无关；
- 长度 → 狭窄空间"抵墙"判定（枪口顶墙自动抬枪收枪——Escape from Tarkov/叛乱式体验，**近战收枪数据可配**）；
- 由此产生真实的室内/野外流派分化，数值全 JSON。

## N-4. 武器履历与铭刻系统
`gun_history` 组件（L 章已列）：击杀计数、发射计数、历任“名义工匠”（A-8 factory_tag）、"传承溢价"（军械师回收价+）。高击杀名枪在展示柜陈列——无玩法增益，纯荣誉层，社区服务器竞选"名枪"的社交燃料。

## N-5. 干火训练与安全系统
- **干火(dry fire)**：TACZ 已有 DummyAmmo——扩展：干火少量磨损撞针（I 章）但训练"瞄准稳定度熟练度"。
- **保险交互**：E-2 状态机 SAFE 态，跨枪传递；携带未保险枪跌落/受击小概率走火（硬核开关，默认关）。
- **教练弹道靶**（C 章 `ballistic_target`）配套训练手册（补丁书 Patchouli/Ponder 任选 Q-15）。

## N-6. 拆解与再制造系统（补经济闭环）
任何 TACZ 枪可在逆向台拆解：**在其注册的 GunId 与枪包未提供 taczind 蓝图字段时**，按 TS 折损得散件或"逆向图纸残页"（A-7）。存量枪包枪械由此进入工业循环而非外挂——这是"不另起炉灶"的经济桥。

## N-7. 其他分水岭机制速览（候选池，Roadmap 分配 P4–P6）
| 机制 | 一句话 | 依赖 |
|---|---|---|
| 弹着观测员合作玩法 | 队友用望远镜`spotting_scope`标记，射手 HUD 显示距离/横风读数 | C |
| 阵地部署重机枪 | 三脚架部署位+弹链箱联动；部署/收拢动画 | L/N-1 |
| 弹药批次抽检 | 测速门+弹道靶联动判定装药工失误 | B/C |
| 战地抢修 | 无保养台时用清洁组+零件现场换（时间×3） | H/I |
| 枪械老化外观 | 高磨损枪身烤蓝磨损贴图阶段 | H 渲染链 |
# 第 16 章 · 实施路线图（P0–P6）、验收标准（DoD）与依赖关系

> 推进铁律：**按 P0→P6 顺序，不跳跃**（例：没有状态机就没有过热挂点，没有 A/B 数据层就没有故障权重来源）。

## 依赖关系总图（缩略）

```
P0 基础设施(数据层/平衡JSON/看板)
 ├─► P1 制造地基: A材料树T1/T2 + A-8公差 + B弹药四要素(C 注入最小闭环)
 │     └─► P2 状态机 E 全量 + D 后坐参数化
 │           ├─► P3 可靠性三角: I部件耐久 + H保养 + F炸膛
 │           │     └─► P4 热与声: G过热扩展 + K声学 + J-2.3/2.4归零消音 (含E/F联动)
 │           └─► P4 供弹具 N-1 + 携行 L-2.2  (与P4并行团队通道)
 └─► P5 工业自动化: A T3/T4/T5机器 + M Create联动 + 化学产线 + A-7蓝图全量
       └─► P6 扩展润色: N-2~N-7 候选池 + 贸易/经济 + 手册场景 + 平衡大战
```

## P0 · 基础设施与技术验证（Spike 阶段）

**系统清单**：`taczind` 模块骨架；DataComponent 注册管线；全局平衡 JSON 加载器（热更）；射击总入口与 Modifier 扩展点的源码确认（Q-04/Q-06/Q-09，全部 Spike 结论写入 `18-open-questions.md` 回答栏）；19 章进度看板建立。

**DoD**：①`18` 章所有 Q-xx 均有结论（继续/绕行方案）；②空 mod 可加载，注册一个带 5 字段组件的测试物品且存档往返无损；③平衡 JSON 热更实测生效；④CI 可 build。

## P1 · 制造地基（手搓+小作坊时代）

**系统清单**：A-1 材料树（T1/T2 部分）；A-2 热度条锻打全机制；A-3 四台小作坊机器+模具；A-8 公差全公式（手摇/手动机床档）；B-1~B-7 弹药四要素+复装台（黑火药/黄铜/Boxer 范围）；C-2.1/2.4 初速与散布注入最小闭环（让"手搓枪打得出、打得不太准"）；N-6 拆解入口（基础版）。

**DoD**：生存模式从铁矿起步，①按热度条流程锻出第一根滑膛管；②冲压复装一枚黄铜壳黑火药弹并射出；③同型号两把手搓枪 TS 不同且散布可感知不同；④全部配方来自 JSON；⑤旧枪包无损加载（回归测试）。

**依赖**：P0 完成（注入点必须确认）。

## P2 · 状态机与手感（E+D）

**系统清单**：E-2 状态机 19 态全实现+转移表；六类故障+差异化清除交互+HUD；撞针磨损联动占位（读 I 章接口，P3 落地）；D-2 后坐曲线类型生成器/bloom 模型/ADS 重量推导/屏息/依托判定（两脚架逻辑+1 件两脚架配件）；J-2.6 换管数据结构预埋（不开放合成）。

**DoD**：①状态机全转移有单元测试覆盖（服务端 headless 测试）；②Squib→检查→清膛→或续射→（P3 前为警告占位）链路可走通；③四种自动原理后坐手感可分辨（内部评审盲测）；④排障全部有独立交互而非一键。

**依赖**：P1（故障权重需要 TS/弹药/积碳数据源——积碳源 H 暂用占位系数）。

## P3 · 可靠性三角（I+H+F）

**系统清单**：I 章 10 部件全耐久+弹簧疲劳+更换体系；H 章保养台/四件工具/双累积轨/环境模块基础；F 章炸膛全分级+事故报告；E/H 联动（Squib 续射进 F）；G-2.3 烧蚀字段与枪管消耗闭环。

**DoD**：①恶意玩家"超装药+烂枪管+不保养"可在可控次数内复现 III 级炸膛；②良好维护玩家 2000 发内 0 故障期望（统计测试 1 万发模拟）；③事故报告 tooltip 正确显示权重主因；④部件可拆可换可交易（物品化往返无损）。

**依赖**：P2（炸膛挂状态机）；P1（部件来自制造链）。

## P4 · 热、声与供弹具（G+K+J 深改+N-1+L-2.2）

**系统清单**：G 章全量（热浮动、速换管、烧蚀联动、cook-off、温度-炸膛阈值权重）；K 章声学全量（半径模型/生物惊动/隐蔽分级/暴露通道）；J-2.3 归零系统；J-2.4 消音器磨损；N-1 七种供弹具（独立弹匣物品+携行具联动）；L-2 携行具三种。

**DoD**：①MG42 类武器 250 发换管循环可玩通；②亚音速+消音走廊规避僵尸听觉实测成立（半径≤6m 判定）；③100/200/300m 归零弹道偏差与视觉一致；④转轮/管仓/漏夹/弹链四种机构交互手感各异且无 double feed 类不适用故障；⑤热更 JSON 可关掉整个 K 章而不报错。

**依赖**：P2/P3。

## P5 · 工业自动化（A T3–T5 + M）

**系统清单**：Create 软依赖集成（KineticBlockEntity 机器×6、能量稳定性→TS）；化学反应釜/硝化/造粒线（危险工位）；精密热处理；冲压/铣削双路线+总装产线；A-7 蓝图/研究桌/逆向台全量；M-3 示例工厂 Ponder 教学场景；无 Create 环境降级（T2 封顶）。

**DoD**：①示例工厂蓝图（schematic）存档内复现"矿石进、整枪+盒弹出"；②断主轴 30s 硝化区报废行为正确；③冲压 vs 铣削机匣属性差异符合 A-6 表；④无 Create 实例可完整玩到 T2；⑤多人 20 机器同网 tick 开销 <1mspt（性能测试报告入档）。

**依赖**：P1（产线产品=P1 材料的延伸）；P4 不作硬依赖但建议先行。

## P6 · 润色与经济（N 候选池+经济+手册）

**系统清单**：N-2 扳机组细节；N-4 履历铭刻；N-5 培训/保险；L-3 军械师村民；展示柜；老化外观贴图阶段；全量平衡战役（模拟+人工）；翻译/keybind/UI 打磨。

**DoD**：全部 P0–P5 回归绿；性能预算达标；17 章数据总表零漂移（实现与设计同步）；发布候选版。

## 里程碑风险登记（最高 5 项）
| 风险 | 影响 | 缓解 |
|---|---|---|
| TACZ 射击入口不可侵入（Q-06 结论否定） | E/F/B 全失效 | P0 Spike 优先做；备选=Mixin 高优先级拦截，仍不可行则 fork 补丁 |
| 独立弹匣物品与 TACZ 弹药计数同步竞态（Q-12） | N-1 数据错乱 | 单一权威源=状态机；整数镜像只作渲染；头部测试覆盖换弹并发 |
| Create API 版本漂移 | P5 阻塞 | 软隔离层 TaczindKinetic 适配器；无 Create 构建 CI |
| 性能（子弹+自动化同屏） | 服务器卡 | 对象池+惰性结算+分帧；P5 DoD 性能门槛 |
| 平衡失控（硬核 vs 大众） | 口碑 | `taczind-common.toml` 难度开关矩阵+默认居中 |
# 第 17 章 · 数据结构总表（唯一权威源 SSoT）

> 用途：实现期防冲突。所有字段名以此表为准；任何新增字段必须先在此表登记。
> 载体优先级：**DataComponent（26.2 原生机制，本仓库已确认 ItemStack=components 布局）**，方块实体用 NBT，玩家态用 PersistentData/能力。

## 17.1 组件注册总表（DataComponentType，域 `taczind`）

| 组件 ID | 载体物品 | 章节 | 字段（名:类型） |
|---|---|---|---|
| `taczind:workpiece` | 工件/半成品 | A-2 | `heat:int`, `process_id:ResourceLocation`, `progress:float`, `quality_seed:long`, `material:ResourceLocation` |
| `taczind:mold` | 模具 | A-3 | `die_type:string`, `wear:int`, `max_wear:int`, `ts:int` |
| `taczind:barrel_part` | 枪管零件/备用枪管 | A-3/G | `caliber:string`, `length:int`, `twist:int`, `bore_finish:float`, `material:ResourceLocation`, `ts:int`, `wear:float`, `erosion:float`, `hot_until_tick:long` |
| `taczind:receiver_part` | 机匣零件 | A-6 | `route:enum(STAMPED,MILLED)`, `material:ResourceLocation`, `ts:int`, `rivet_quality:float?` |
| `taczind:gun_build` | 成品枪 | A-8 | `ts:int`, `parts_ts:Map<string,int>`, `build_serial:string`, `factory_tag:string?`, `ts_degraded:int`, `weight_g:int`, `length_class:int` |
| `taczind:cartridge` | 弹药/弹壳同源 | B | `case_material:enum`, `primer_type:enum(BOXER,BERDAN)`, `primer_quality:float`, `primer_corrosive:boolean`, `propellant_type:enum`, `burn_rate:enum(FAST,MID,SLOW)`, `powder_charge_pct:int`, `powder_charge_dev:float`, `projectile_type:enum`, `projectile_mass_class:enum`, `overpressure_score:float`, `squib_risk:float`, `handload:boolean`, `lot_id:long` |
| `taczind:shell_case` | 空弹壳 | B-3 | `material:enum`, `primer_type:enum`, `reload_count:int`, `crack_risk:float`, `state:enum(FIRED_SPENT,INSPECTED_OK,CRACKED,DEFORMED)`, `rusted:boolean` |
| `taczind:powder_lot` | 发射药堆 | A-5/B | `burn_rate:enum`, `lot_quality:float`, `lot_id:long` |
| `taczind:cycle` | 枪 | E | `state:int`, `pending_tick:long`, `squib:boolean`, `squib_known:boolean`, `mal_bp_cache:float[6]`, `jam_streak:int` |
| `taczind:thermal` | 枪 | G | `heat:float`, `last_tick:long`, `erosion:float`, `hot_barrel_inserted:boolean`, `qcb:boolean` |
| `taczind:maintenance` | 枪 | H | `fouling:float`, `rust:float`, `lube:float`, `corrosive_pending:boolean`, `last_clean_day:int`, `obstruction:boolean`, `barrel_obstruction:boolean` |
| `taczind:parts` | 枪 | I | `parts:Map<slot, PartState>` — `PartState{part_id:string, wear:float, max_wear:float, broken:boolean, service_count:int, spring_free:float, ts:int}` |
| `taczind:build_ext` | 枪 | J | `barrel_slot:{len_class:int, twist:int}`, `zero_m:int`, `suppressor:{baffle_wear:float, wetted:boolean}` |
| `taczind:gun_history` | 枪 | L/N-4 | `kill_count:int`, `shots_fired:long`, `crafted_by:string`, `factory_tag:string` |
| `taczind:rig` | 携行具 | L-2.2 | `slots_count:int`, `mags:List<ItemStack>`, `draw_mult:float` |
| `taczind:mag` | 独立弹匣物品 | N-1 | `feed_type:enum(FeedDeviceType)`, `capacity:int`, `loaded:List<taczind:cartridge摘要>`, `mag_spring:PartState`, `device_state:Map<string,string>`(转轮膛位等机构态) |
| `taczind:ammo_stack` | 枪（弹仓镜像） | B-9/N-1 | `queue:List<taczind:cartridge摘要>`, `device:enum(FeedDeviceType)` |
| `taczind:unlocks` | 玩家(PersistentData) | A-7 | `Set<ResourceLocation>` |

## 17.2 方块实体（BlockEntity）NBT 字段表

| BE | 机器 | 字段 |
|---|---|---|
| `CrucibleBE` | 坩埚炉(A-2) | `heat:int`, `contents:ItemStackHandler`, `bellows_boost:boolean` |
| `ManualMachineBE` | 手动机床(A-3) | `progress:float`, `mold:ItemStack`, `output_ts_pending:int` |
| `KineticMachineBE`（Create 软依赖基类） | 动力机器(A-4/M) | `su_need:int`, `rpm_opt_min:int`, `rpm_opt_max:int`, `wear:int`, `energy_stability:float`, `queue:List<ItemStack>` |
| `ChemReactorBE` | 反应釜(A-5) | `recipe_id:ResourceLocation`, `progress:float`, `stirring_rpm:int`, `temp_game:int`, `risk_accum:float`, `tanks:FluidTank[]` |
| `MaintenanceBenchBE` | 保养台(H) | `tools:ItemStackHandler`(4槽), `session_progress:float`（非 tick，操作结算） |
| `StorageBE` | 弹药箱/枪架/携行 | `inv:ItemStackHandler`, `sealed:boolean` |

## 17.3 TACZ 原生字段互操作映射表（不可重定义）

| TACZ 字段（常量源） | 所在 | 本系统用法 |
|---|---|---|
| `GunId` / `GunFireMode` | GunItemDataAccessor | 直读不动 |
| `HasBulletInBarrel` / `GunCurrentAmmoCount` | 同上 | E 状态机旁路镜像的**显示源**；权威=`taczind:cycle`/`ammo_stack` |
| `Attachment` | 同上 | J 章配件挂载（新增 AttachmentType 值走 Q-04 决议路径） |
| `HeatAmount` / `OverHeated` | 同上 | G 章直接读写（复用原生锁射） |
| `DummyAmmo` / `MaxDummyAmmo` | 同上 | N-5 干火训练 |
| `heat{heatMax,heatPerShot,over_heat_time}` | GunData JSON | G 章冷却锚点 |
| `inaccuracy{stand/move/sneak/lie/aim}` | GunData JSON | C-2.4 基础散布查表 |
| `bolt(open/closed/manual)` | GunData JSON | E 状态机+G cook-off 免疫判定 |
| `FeedType(magazine/manual/fuel/inventory)` | GunData JSON | N-1 机构映射父类 |
| `fire_sound{fire_multiplier,silence_multiplier}` | GunData JSON | K-2.1 公式乘数 |
| `recoil`(GunRecoil 关键帧) | GunData JSON | D-2.1 生成器输出目标 |
| `movement_speed{base,aim,reload}` | GunData JSON | D-2.4 重量推导覆写 |

## 17.4 枪包 JSON 增量字段统一格式（嵌入 `GunData`/`AmmoData` 顶层）

```jsonc
{
  // ……TACZ 原生字段原样不动……
  "taczind": {
    "action_type": "GAS_LONG_PISTON",        // D/E
    "feed_device": "BELT",                    // N-1
    "trigger": {"type": "SA", "pull": "STD"}, // N-2
    "ballistic": {"barrel_class": "STD", "gravity_scale": 1.0, "air_density_scale": 1.0}, // C
    "barrel": {"twist": 10, "qcb": true},     // C/G
    "mount_systems": ["picatinny", "m-lok"],  // J
    "noise": {"base_radius": 48, "crack_signature": true}, // K
    "manufacture": {"required_blueprint": "taczind:bp_ak_series", "route": "STAMPED"}, // A
    "maintain": {"fouling_rate": 0.2}         // H
  }
}
```
缺省即旧枪包按上文各章默认值运行——**零迁移承诺**。

## 17.5 状态枚举登记（Java 枚举，`taczind` 包内唯一定义）

| 枚举 | 值 | 章节 |
|---|---|---|
| `GunCycleState` | SAFE, EMPTY_READY, FEEDING, CHAMBERED, FIRING, UNLOCKING, EXTRACTING, EJECTING, COOKING_OFF_RISK, JAM_FTF, JAM_FTEXTRACT, JAM_DOUBLE_FEED, JAM_STOVEPIPE, HANGFIRE_PENDING, DUD_IDENTIFIED, SQUIB_OBSTRUCTED, SLAMFIRE_RUNAWAY, MAINTENANCE_OPEN, BURST_DAMAGED | E-2.1 |
| `BurstSeverity` | MINOR, MODERATE, CATASTROPHIC | F |
| `ActionType` | MANUAL, REVOLVER, BLOWBACK, ROLLER_DELAYED, GAS_DI, GAS_SHORT_PISTON, GAS_LONG_PISTON, RECOIL_SHORT, RECOIL_LONG | D/E/N |
| `FeedDeviceType` | INTERNAL_CLIP, ENBLOC, TUBE, CYLINDER, BOX_MAG, DRUM, PAN, BELT | N-1 |
| `CaseMaterial / PrimerType / PropellantType / BurnRate / ProjectileType / MassClass` | 见 03-B | B |
| `TriggerType(SA/DA/DASA) , PullWeight(LIGHT/STD/HEAVY)` | 见 15-N | N-2 |
| `BarrelClass(SHORT/STD/LONG)` , `TwistClass` | 见 04-C | C |
| `BreathState(CALM/HOLDING/RECOVERING)` | 见 05-D | D |

## 17.6 网络包登记（S2C/C2S，事件驱动禁轮询）

| 包 | 方向 | 载荷 | 触发 |
|---|---|---|---|
| `CycleStateSync` | S2C | gun uuid+state+pending | 状态转移 |
| `MalfunctionAlert` | S2C | 故障类型+提示级别 | ★掷骰失败 |
| `NoiseEventPacket` | S2C | pos+radius+signature | 击发 |
| `ZeroAdjustC2S` | C2S | zero_m | 调零键 |
| `ClearJamC2S / InspectBarrelC2S / ChangeBarrelC2S` | C2S | 动作+手持槽 | E/G 交互键 |

---

# 17.7 【2026-08-01 落地修订】P0 供弹具数据系统（已实现部分以本节为准）

> 对应实现记录：`docs/impl-log/P0-feed-device-data-system.md`。本节为已编码字段的**最终权威**；上方设计节如与本节冲突，以本节为准。

## 17.7.1 已注册 DataComponent 实况（`IndustryComponents`，命名空间 taczind）

| 组件 | 类型 | Codec 键/字段 |
|---|---|---|
| `taczind:feed_device_data` | `FeedDeviceData`（密封多态） | `"feed_system"` 分派六机构 |
| `taczind:gun_state_data` | `GunStateData` | `chambered_round:Optional<LoadedRound>`、`barrel_obstruction:bool`、`obstruction_known:bool` |
| `taczind:loaded_round` | `LoadedRound` | 单发个体完整数据（下详） |

## 17.7.2 LoadedRound 字段实况（record，Codec=RecordCodecBuilder）

| JSON 键 | Java 类型 | 默认 | 说明 |
|---|---|---|---|
| `cartridge` | Identifier | （必填） | →CartridgeRegistry |
| `bullet_type` | Identifier | `taczind:fmj` | →BulletRegistry |
| `case_material` | enum | `brass` | brass/steel/aluminum |
| `case_state` | enum | `factory_new` | factory_new/fired_spent/inspected_ok/cracked/deformed |
| `primer_type` | enum | `boxer` | boxer/berdan |
| `corrosive_primer` | boolean | false | 腐蚀性独立于结构 |
| `charge_deviation` | float | 0 | +超装/-欠装；≤-0.45=Squib 风险档 |

## 17.7.3 FeedSystemType → 数据形状实况（class:`cn.sh1rocu.tacz.industry.api.feed.device`）

| 枚举(序列化名) | record | 独有字段 |
|---|---|---|
| `box_magazine` | BoxMagazineData | rounds:LIFO(栈顶=末位)、spring_fatigue、feed_lip_damage |
| `tubular` | TubularMagazineData | rounds:严格FIFO、spring_fatigue |
| `cylinder` | CylinderData | slots:固定槽位数组【嵌套 CylinderSlot{state:empty/loaded/spent, round?}】、aligned_index |
| `belt` | BeltData | rounds:FIFO、link_type(disintegrating/non_disintegrating)、has_link_tail |
| `stripper_clip` | StripperClipData | rounds、consumed(一次性) |
| `en_bloc` | EnBlocClipData | rounds、ejected(强制整体弹出) |

公共字段（接口层）：`cartridge:Identifier`、`capacity:int`；公共行为：`peekNext/ejectNext/tryLoad`。
**物品规则：承载 feed_device_data 的物品必须 stacksTo(1)（FeedItemRules 断言）。**

## 17.7.4 GunData 增量字段实况（gson POJO，键名已定型）

| JSON 键 | 类型 | 缺省行为 |
|---|---|---|
| `taczind_chambered_cartridge` | Identifier? | null→回退 ammoId（FeedCompatibility.resolveChamberCartridge） |
| `taczind_compatible_feed_device_tag` | Identifier? | null→全兼容（旧枪包语义） |

> 注：设计稿曾计划 `taczind` 顶层嵌套对象；实现期改为**扁平双键**，理由：gson POJO 纯字段追加零侵入、与 TACZ 现有键风格一致、无需嵌套适配器。判定函数在规则层 `FeedCompatibility`（canChamber/acceptsFeedDeviceTag/canLoadFromDevice）。

## 17.7.5 数据驱动注册表（loader：IndustryDataLoader）

| 注册表 | JSON 路径 | 来源优先级 |
|---|---|---|
| CartridgeRegistry | `data/<ns>/cartridge/<name>.json` | 代码默认(12条) ← 数据包覆盖 |
| BulletRegistry | `data/<ns>/bullet/<name>.json` | 代码默认(6条) ← 数据包覆盖 |

## 17.7.6 镜像一致性写入规范（GunStateData ↔ TACZ HasBulletInBarrel）

权威=`taczind:gun_state_data.chambered_round`；`HasBulletInBarrel` 仅作显示/动画镜像。
**任何 chambered 变更必须双写**；读一律读组件。后续刘状枪膛状态（E 章）在此组件上扩展字段，不回头加布尔。
# 第 18 章 · 开放问题清单（实现前必须调研/验证）

> 约定：Q-xx 在 P0 Spike 阶段逐一回答并回写"结论"列。阻断型=不回答不允许进 P1/P2。

| # | 问题 | 所属 | 调研方式 | 阻断型 | 结论（待填） |
|---|---|---|---|---|---|
| Q-01 | 本系统作为**仓库内子包**(`cn.sh1rocu.tacz.industry`)还是**独立附属 mod**(modid `taczind`)?涉及打包/发布形态 | 全局 | 与维护者确认；技术均可 | 否 | ☐ |
| Q-02 | Create 的实际依赖坐标（"飞翔版" Fabric 移植的 maven/版本）与 `KineticBlockEntity` API 兼容面 | M | P0 拉依赖跑通最小挂网 BE | 是(P5 前) | ☐ |
| Q-03 | `GunSmithTableRecipe` 是否可继承/`RecipeSerializer` 可否包装饰加 `required_blueprint` 字段 | A-7 | 读 `crafting/` 源码 | 是(P1 前) | ☐ |
| Q-04 | `AttachmentType` 是否硬编码枚举？modifier 体系能否注册自定义来源（用于 TS→散布、携行具→换弹等） | J/C/L | 读 `AttachmentType` 与 `resource/modifier/` | 是(P0) | ☐ |
| Q-05 | 弹壳拾取物 60% 投掷是否造成实体风暴？是否需要"批次合并拾取" | B-3 | 性能白盒（TickProfiler） | 否 | ☐ |
| Q-06 | **射击总入口**：服务端从 `AbstractGunItem.shoot` 到实体生成的完整链路；伤害/初速在何处可注入乘算；打断点（状态机守卫）放哪层 | B/C/E/F | 读 `AbstractGunItem`、`api/event`、`entity/` | 是(P0，最高优先级) | ☐ ETA |
| Q-07 | 平衡 JSON 热更通道：复用 TACZ 资源重载（GunPackLoader 生命周期）还是独立 datapack listener | 全局 | 读 `resource/GunPackLoader` | 是(P0) | ☐ |
| Q-08 | 散布最终消费点（服务端命中 or 客户端方向扩散?）与子弹撞方块逻辑现状（即停 or 支持穿透） | C | 读 `entity/projectile` 与命中判定 | 是(P2 前) | ☐ |
| Q-09 | `GunRecoil` 关键帧可否运行期替换；`IGunOperator` 有哪些同步字段可挂 bloom/呼吸态 | D | 读 `GunRecoil`+`IGunOperator` | 是(P2 前) | ☐ |
| Q-10 | 炸膛全局系数默认值：`风险池‰` 的玩家可接受频次（白盒模拟定 0.5–2/1000 发） | F | 模拟器跑 10 万发分布 | 否(调参) | ☐ |
| Q-11 | 枪渲染管线的贴图替换/hook 点：锈斑、老化磨损、名牌刻印叠层的最佳实现（shader mask vs 换贴图） | H/N-7 | 读 `client/render/`+模型 LOD | 否 | ☐ |
| Q-12 | 独立弹匣物品与 `GunCurrentAmmoCount` 的同步权威源策略（状态机镜像方案的边界：丢弃/拾取/多人并发） | N-1/B-9 | 写 PoC 联测 | 是(P4 前) | ☐ |
| Q-13 | 生物惊动走 vanilla game event 通道是否足够，还是要自建 NoiseEvent→Brain memory 监听表 | K | 读原版 Mob goal 机制 | 否 | ☐ |
| Q-14 | 携行具槽位：自研槽 vs Curios/Trinkets 移植版依赖，26.2 生态现状 | L | 生态调研 | 否 | ☐ |
| Q-15 | 游戏内手册选型：Ponder(Create 自带) vs Patchouli；蓝图/教学场景写哪套 | M/N-5 | 生态调研 | 否 | ☐ |
| Q-16 | 枪内弹药队列（牵引 Squib/曳光位置记忆）的最大长度与存档膨胀评估（弹链 250 发 JSON 摘要体积） | B-9 | 体积估算+压缩 | 是(P4 前) | ☐ |
| Q-17 | 26.2 Fabric 端键位注册与 TACZ 现有 `client/input` 的冲突矩阵（排障/屏息/归零/换管/保险至少 5 个新键） | E/D/J/G | 读 `client/input/` | 是(P2 前) | ☐ |
| Q-18 | 服务器侧模拟测试框架：headless 射击循环 10 万发的可运行单元（P2/P3 DoD 依赖） | 全局 | 搭 JUnit+FakePlayer harness | 是(P2 前) | ☐ |
| Q-19 | 弹壳/漏夹等拾取物模型与渲染成本（床岩实体或 ItemEntity） | B/N-1 | 渲染压测 | 否 | ☐ |
| Q-20 | 与既有社区"Create×TaCZ 配方包"（调研 0.1）的版本共存策略：我们的硬核电是否与其配方冲突 | M | 社区兼容说明文档 | 否 | ☐ |
| Q-21 | 沙箱无 JVM/Gradle 发行版与 Maven 源不可达：完整 `./gradlew compileJava` 编译级验收需在可联网环境补执行（本轮已用符号/字节码签名/逻辑沙盒三层替代验证，见 impl-log） | 全局 | 联网环境跑一次编译并回写结果 | 是(下一次代码合并前) | ☐ 已建 JDK25(jdk4py,JRE only) 可复用 |
| Q-12(更新) | 枪内固定仓机构（管仓/内仓/漏夹装入枪后）的持有者机制：`GunStateData` 已预留扩展位；权威口径=FeedDeviceData 副本进"枪内 feed 槽"（P2 状态机落地时定稿） | N-1 | P2 实现时验证 | 是(P2) | 部分结论（2026-08-01） |
| Q-16(更新) | 弹链队列体积：loaded_round 单发 codec 字段实测 7 键（~80B JSON 等价）；250 发链≈20KB 未压缩，CompoundTag 内 varint/枚举名优化后 <8KB，可接受 | B-9 | items 阶段复估 | 否 | 初步结论：可行（2026-08-01） |
# 第 19 章 · 进度看板（持续维护）

> 状态枚举：`未开始 / 设计中 / 开发中 / 测试中 / 完成 / 阻塞(原因)`
> 更新规则：阶段二每完成/推进一个系统就更新本表一次，与实现记录（`docs/impl-log/`）互链。

## 系统总览

| # | 系统 | 文档章节 | 路线图 | 状态 | 负责人 | 最近更新 | 备注 |
|---|---|---|---|---|---|---|---|
| A-1 | 材料树 | 02-A | P1 | ✅设计中 | — | 2026-07-31 | 阶段一设计完成 |
| A-2 | 手工阶段(热度条锻打) | 02-A | P1 | 设计中 | — | 2026-07-31 | |
| A-3 | 小作坊机器 | 02-A | P1 | 设计中 | — | 2026-07-31 | |
| A-4 | 初级工业动力机 | 02-A | P5 | 未开始 | — | | 依赖 Q-02 |
| A-5 | 中级工业化学线 | 02-A | P5 | 未开始 | — | | |
| A-6 | 重度自动化双路线 | 02-A | P5 | 未开始 | — | | |
| A-7 | 蓝图/图纸 | 02-A | P5(前置字段 P1) | 设计中 | — | | 依赖 Q-03 |
| A-8 | 公差系统 | 02-A | P1 | 设计中 | — | | 依赖 Q-04 |
| B | 弹药四要素+复装 | 03-B | P1 | 设计中 | — | | 依赖 Q-06 |
| C | 弹道与精度 | 04-C | P1 最小 + P2 全量 | 设计中 | — | | 依赖 Q-08 |
| D | 后坐力与人机 | 05-D | P2 | 未开始 | — | | 依赖 Q-09 |
| E | 状态机与故障 | 06-E | P2 | 设计中 | — | | 依赖 Q-06/17 |
| F | 炸膛 | 07-F | P3 | 未开始 | — | | |
| G | 过热 | 08-G | P1 字段 + P4 全量 | 设计中 | — | | 复用 TACZ HeatAmount |
| H | 保养维护 | 09-H | P3 | 未开始 | — | | |
| I | 模块化耐久 | 10-I | P3 | 未开始 | — | | |
| J | 模块化改装 | 11-J | P2 预埋 + P4 全量 | 设计中 | — | | 依赖 Q-04 |
| K | 声学隐蔽 | 12-K | P4 | 未开始 | — | | 依赖 Q-13 |
| L | 后勤仓储携行 | 13-L | P4 | 未开始 | — | | 依赖 Q-14 |
| M | Create 联动 | 14-M | P5 | 未开始 | — | | 依赖 Q-02 |
| N-1 | 供弹具机构 | 15-N | P4 | 🔨开发中 | — | 2026-08-01 | **数据层已完成**（六机构+规则层+组件注册，见 impl-log/P0-feed-device-data-system）；物品层后置 |
| P0-补 | 供弹具数据系统 | 17.7 | P0 | ✅完成 | — | 2026-08-01 | CartridgeType/BulletType/LoadedRound/GunStateData + 注册表 + loader；编译级验收待 Q-21 |
| N-2~N-7 | 扳机/履历/训练/拆解等 | 15-N | P6 | 未开始 | — | | |

## 里程碑

| 里程碑 | 定义 | 状态 |
|---|---|---|
| M1 阶段一完成 | 本文档全章节+路线图+数据总表+开放问题 | ✅ 2026-07-31 |
| M2 P0 Spike 结束 | Q-xx 全回答、骨架可 build | ☐ |
| M3 第一对手搓枪 | P1 DoD 达成 | ☐ |
| M4 状态机可玩 | P2 DoD 达成 | ☐ |
| M5 可靠性闭环 | P3 DoD 达成 | ☐ |
| M6 隐蔽与热 | P4 DoD 达成 | ☐ |
| M7 自动化工厂 | P5 DoD 达成 | ☐ |
| M8 RC 候选 | P6 DoD 达成 | ☐ |

## 变更日志
| 日期 | 变更 |
|---|---|
| 2026-07-31 | 阶段一文档首版建立（v1.0） |
| 2026-08-01 | P0 补充：供弹具数据系统落地（27 个新类；抽象层/规则层/嵌套规则层完成，物品层按计划后置；GunData 增 2 增量字段；实现记录 docs/impl-log/P0-feed-device-data-system.md） |
# 第 20 章 · 术语表（中英对照，实现期键名统一来源）

> 用途：语言文件 key、JSON 字段、代码命名的统一词库，避免"同物三名"。按系统分组。

## 20.1 通用/工业

| 中文 | English | 键名建议 | 说明 |
|---|---|---|---|
| 公差评分 | Tolerance Score | `ts` | 0–100，见 A-8 |
| 名匠级 | Masterwork | `masterwork` | TS 90+ 称号 |
| 土造 | Crude | `crude` | TS<30 称号 |
| 炉温单位 | Heat Unit | `heat` | 工件热度 0–1000 |
| 加工硬化 | Work Hardening | `work_hardened` | 退火消除 |
| 动力稳定性 | Energy Stability | `energy_stability` | Create RPM 滑动波动 |
| 应力单位 | Stress Unit | `su` | Create 原生 |
| 冲压机匣 / 精密铣削机匣 | Stamped / Milled Receiver | `stamped/milled` | A-6 双路线 |
| 逆向图纸残页 | Reverse Blueprint Fragment | `rev_fragment` | N-6 |
| 厂牌 | Factory Tag | `factory_tag` | A-8【AI补充】 |

## 20.2 弹药

| 中文 | English | 键名 | 说明 |
|---|---|---|---|
| 弹壳 | Cartridge Case | `case` | |
| 底火 | Primer | `primer` | Boxer/Berdan |
| 发射药 | Propellant | `propellant` | |
| 弹头 | Projectile | `projectile` | 与 bullet（整弹）区分 |
| 全被甲 | Full Metal Jacket | `fmj` | |
| 空尖弹 | Hollow Point | `hp` | |
| 穿甲弹 | Armor Piercing | `ap` | |
| 曳光弹 | Tracer | `tracer` | |
| 亚音速弹 | Subsonic | `subsonic` | |
| 复装 | Reloading/Handload | `handload` | |
| 裂纹风险 | Crack Risk | `crack_risk` | 弹壳 |
| 装药量偏差 | Charge Deviation | `powder_charge_dev` | |
| 燃速档 | Burn Rate | `burn_rate` | FAST/MID/SLOW |
| 批次 | Lot | `lot_id` | 一致性加成来源 |

## 20.3 弹道/精度

| 中文 | English | 键名 | 说明 |
|---|---|---|---|
| 初速 | Muzzle Velocity | `velocity` | |
| 缠距 | Twist Rate | `twist` | 记 1:X 的 X |
| 陀螺稳定因子 | Gyroscopic Stability | `sg` | 游戏化查表 |
| 翻滚失稳/钥孔弹 | Tumbling / Keyhole | `keyhole` | Sg<0.8 |
| 跳弹 | Ricochet | `ricochet` | |
| 穿透等级 | Penetration Class | `pen_class` | 0–5 |
| 散布 | Inaccuracy/Spread | `inaccuracy` | 沿用 TACZ 词 |
| 连发扩散 | Bloom | `bloom` | D-2.2 |
| 归零 | Zeroing | `zero_m` | 单位：格 |
| 测距分划 | Rangefinder Reticle | `rangefinder` | |

## 20.4 状态机/故障

| 中文 | English | 键名 | 说明 |
|---|---|---|---|
| 空仓挂机 | Bolt Hold Open | `empty_ready` | 状态 EMPTY_READY |
| 待击/已上膛 | Chambered | `chambered` | |
| 进弹失败 | Failure to Feed | `jam_ftf` | |
| 抽壳失败 | Failure to Extract | `jam_ftextract` | |
| 抛壳失败(烟囱) | Stovepipe | `jam_stovepipe` | |
| 双重进弹 | Double Feed | `jam_double_feed` | |
| 瞎火(总) | Misfire | `misfire` | 统称 |
| 迟发火 | Hangfire | `hangfire` | 30–60s 等待规则转 30–40 tick |
| 哑弹卡膛 | Squib | `squib` | **隐藏态** |
| 走火 | Slamfire | `slamfire` | |
| 膛内自燃 | Cook-off | `cook_off` | 闭膛红热 |
| 炸膛 | Barrel Burst / Catastrophic Failure | `burst` | |
| 检查枪管 | Bore Inspection | `inspect_barrel` | 揭示 Squib |
| 排障 | Immediate Action | `clear_jam` | 拍-拉-打抽象 |
| 未闭锁击发 | Out-of-Battery | `oob` | 状态机拦截 |

## 20.5 热/保养/耐久

| 中文 | English | 键名 | 说明 |
|---|---|---|---|
| 热浮动（精度漂移） | Thermal Drift | `thermal_drift` | |
| 枪管烧蚀 | Barrel Erosion | `erosion` | 只增不减 |
| 速换枪管 | Quick Change Barrel | `qcb` | |
| 积碳 | Fouling | `fouling` | |
| 锈蚀 | Rust | `rust` | |
| 润滑 | Lubrication | `lube` | 70 黄金值 |
| 腐蚀性底火 | Corrosive Primer | `corrosive_primer` | |
| 野战分解 | Field Strip | `field_strip` | |
| 弹簧疲软 | Spring Set/Fatigue | `spring_free` | 自由长度百分比 |
| 撞针 | Firing Pin | `firing_pin` | |
| 抽壳钩 | Extractor | `extractor` | |
| 复进簧 | Recoil Spring | `recoil_spring` | |
| 导气系统 | Gas System | `gas_system` | |

## 20.6 供弹具/机构

| 中文 | English | 键名 | 说明 |
|---|---|---|---|
| 转轮弹巢 | Cylinder | `cylinder` | |
| 管状弹仓 | Tubular Magazine | `tube` | |
| 桥夹 | Stripper Clip | `stripper_clip` | |
| 漏夹 | En-bloc Clip | `enbloc` | |
| 盒式弹匣 | Box Magazine | `box_mag` | |
| 弹鼓/弹盘 | Drum / Pan Magazine | `drum/pan` | |
| 弹链 | Ammunition Belt | `belt` | |
| 单动/双动 | Single/Double Action | `sa/da/dasa` | 扳机 |
| 扳机力 | Trigger Pull Weight | `pull` | LIGHT/STD/HEAVY |
| 直吹枪机 | Blowback | `blowback` | |
| 滚轮延迟 | Roller Delayed | `roller_delayed` | |
| 直接导气 | Direct Impingement | `gas_di` | |
| 长/短行程活塞 | Long/Short Stroke Piston | `gas_long/gas_short` | |
| 枪管短后坐 | Short Recoil | `recoil_short` | |
# 第 21 章 · 性能预算与工程规范（横切关注点）

> 用户阶段二铁律之一："性能意识"。本章把散见各章的性能约束收敛成**预算表与工程规范**，实现期按表验收。

## 21.1 tick 预算总则

- 服务端目标：单区块满配工厂（机器 40≤ / 传送带实体 60≤）+4 名玩家连射场景，附加开销 **≤ 1.0 mspt**（毫秒/tick，TickProfiler 测量）；
- 客户端目标：连续 600RPM 射击下渲染帧耗时增量 ≤ 0.5ms（含粒子/音效/后坐插值）。

## 21.2 各子系统计算策略矩阵

| 子系统 | 策略 | 依据 |
|---|---|---|
| 弹道飞行 | 沿用 TACZ 实体 tick；穿透判定**单 tick 至多 1 次 raycast**，穿透重生品设"冷却 2 tick" | C-4.1 |
| 散布/后坐/bloom | **事件式累加**（每发 O(1)），恢复用指数惰性结算（读时按 Δt 一次算，不逐 tick 衰减） | D-2.2 |
| 过热 | **惰性冷却结算**：记 `last_tick`，读取时补算热衰减；客户端每 10 tick 同步+本地插值 | G-2.1 |
| 积碳/锈蚀 | 锈蚀按**游戏日事件**结算（每天一次遍历手持/背包枪械，玩家粒度），非逐 tick | H-2.1 |
| 状态机 | 纯事件驱动；`pending_tick` 用服务端 tick 计数器比较（无定时任务注册） | E-2 |
| 故障掷骰 | 只在击发事件掷 3 次 PRNG；权重表组件化缓存，组件变化才重算 | E-2.3 |
| 嗓音传播 | 一次性事件广播；生物监听表挂在区块级别（开盒即用），无持续查询 | K-2.1 |
| 动力机器 | 挂在 Create 网络 tick 的生命周期内；配方进度按"过网 RPM 事件"推进；**不注册独立 world ticker** | M-2.2 |
| 工件退温 | 方块实体 5 tick 批处理容器，且仅温度>环境时才 tick（自注销唤醒模式） | A-2 |
| 弹壳拾取物 | 掉落率 60%+**批次合并实体**（同区块同材质壳自动并堆）；总存活 <40s | B-3/Q-05 |

## 21.3 对象与内存规范

- 子弹相关临时对象（弹道段、命中段）：对象池复用（若 TACZ 已有池则复用其池，Q-08 确认）；
- 枪组件 `PartState` map 用次序固定的小数组而非 HashMap（序列化省空间）；
- `taczind:ammo_stack` 队列上限：弹链 250 发/枪；单发摘要用可变长编码（ints+enum 序数），单发 ≤ 24B→整链 ≤ 6KB，存档可接受（Q-16 验证项）；
- 蓝图/解锁集合用 `Set<short>`（注册表内部 id 压缩），非字符串。

## 21.4 网络规范

- 禁止：每 tick 同步 heat/fouling 浮点。允许：阈值变化（进入新区间）、状态转移、用户提供输入事件；
- 声音事件包：64Byte 内，不上 RTP 队列高峰（射击战高峰 10pkt/s/玩家即上限）；
- 瞄准态/bloom 客户端权威渲染预测，服务端只做最终命中验算（与 TACZ 现有 client-server 分工一致，Q-08）。

## 21.5 Mod 兼容与部署规范

- Create：全部 import 收敛在 `compat/create/` 单包；`ModList.isLoaded("create")` 门控类加载，防止 NoClassDefFound；
- 数据包优先级：`taczind` 平衡 JSON 允许被整合包数据包覆盖（标准 datapack override 语义）；
- 服务端无 Create 或客户端无资源包时功能降级的行为表（写入 16 章 DoD 的回归用例）。

## 21.6 测试与基准规范（DoD 通用条款）

1. **模拟器**：服务端 headless `BattleSimulator`（Q-18）：给定枪/弹药/维护脚本跑 N 发，输出故障分布/炸膛率/TS 衰减曲线（JSON 报告进 CI artifacts）；P3/P4 的统计型 DoD 全部由它背书。
2. **场景基准**：P5 示例工厂 + 8 玩家机器人脚本压测（Carpet/自研 bot），报告 mspt 峰值与均值。
3. **回归基线**：原版 TACZ 行为集（射击/换弹/配件/合成）快照测试，每次合并必须 100% 通过——"深度重构不给上游引入回归"是本项目对社区的承诺。
# 第 22 章 · 五阶段枪线产品谱系与玩家旅程示例

> 用途：①验证各科技阶段"造得出什么"与历史同构；②给内容策划（枪包作者）一份参考产品表；③用一条玩家旅程把 A–N 各系统串成可感的体验，验证系统间咬合无缺口。

## 22.1 产品谱系总表（历史对齐；仅作内容设计参考，最终以枪包 JSON 为准）

| 阶段 | 代表产品线 | 机构(ActionType) | 供弹具 | 弹药时代 | 工艺烙印（玩法表现） |
|---|---|---|---|---|---|
| T1 手搓 | 《手炮》单发前装；《火铳》滑膛；《双管撅把霰弹》 | MANUAL/撅把(独立 enum 候选) | 单发膛装 | 黑火药+铅丸；腐蚀性底火概念的前身 | TS≤45；无膛线 Sg<0.8；哑火率看天(湿度联动)；打猎与自卫起步 |
| T2 小作坊 | 《杠杆步枪》；《单动转轮》；《双管猎枪(定装壳)》；早期《中折手枪》 | MANUAL 杠杆 / REVOLVER | TUBE / CYLINDER / 单发 | 黄铜定装黑火药弹（Boxer，可复装！经济起点） | 手工膛线诞生→精度跨越；弹壳回收复装经济开启 |
| T3 初级工业 | 《栓动步枪》；《泵动霰弹枪》；《半自动手枪(管退)》；军剩《旧式步枪(腐蚀 Berdan 弹药)》 | MANUAL 栓动 / RECOIL_SHORT / BLOWBACK | INTERNAL_CLIP / ENBLOC / BOX_MAG(早期) | 工厂黑火药弹+初尝无烟火药弹(T4 前置战利品) | 动力机床→TS 首次稳上 55+；漏夹"叮"玩法登场 |
| T4 中级工业 | 《导气半自动步枪》；《冲锋枪(自由枪机)》；《全威力狙击系统(精管+快缠)》；《通用机枪(QCB)》 | GAS_LONG/SHORT / BLOWBACK / ROLLER_DELAYED | BOX_MAG / BELT | 无烟火药全谱系：AP/曳光/亚音速/HP | 精热处理+装配线 TS 冲击 75–90；速换枪管战术；隐蔽射击竣工 |
| T5 重度自动化 | 《冲压突击步枪(AK 式)》；《精铣模块化步枪(AR 式)》；特种《重管狙》 | GAS 系为主 | BOX_MAG / DRUM / BELT | 全自动装检包装批次一致性拉满 | 两条工艺路线对应"量大可靠"vs"极限精度"；厂牌名枪诞生 |

谱系设计要点：每阶段都同时给"玩法提升"与"生产提升"，防止"为了造枪而造枪"的工薪感；军剩弹(T3 战利品)=腐蚀性 Berdan 弹药，让 H 章保养在非制造玩家身上也强制体验一次。

## 22.2 玩家旅程示例（剧本化验证，覆盖 A–N 全系统）

**第 1 章 求生（T1，游戏第 1–5 日）**：玩家收集铁/煤/硫磺，搭粗炼炉与锻砧。捧出生铁在热度条区间里反复回炉、锤打、淬火，得到 TS=38 的滑膛手炮（A-2、A-8）。黑火药装填、雨天瞎火惊魂（C-5）教会"干燥与保养"（H 占位体验）。第一只僵尸近距离轰倒——后坐像被锤肩（D:MANUAL 单峰曲线）。
> 触及系统：A1/A2/A8/B(原始装填)/C(滑膛 Sg)/D/H(黑火药积碳 3 发就明显)

**第 2 章 工坊（T2，5–15 日）**：手摇车床与手动冲压机落地。玩家冲压第一批黄铜壳，发明"捡壳子"习惯（B-3）；拉膛线节奏小游戏第一次让弹道收束到可瞄准的水平（A-3/C-3 Sg 跳档）。在掠夺者营地捡到 AK 时代军剩弹与一张残破图纸（A-7 任务钩）。夜战：黑火药烟团暴露位置，K 章第一次产生"被发现"的压迫感。
> 触及：A3/A7/B3/B7/C3/K-2.3/L-1(铁皮弹药箱防潮)

**第 3 章 工厂（T3，15–30 日）**：水车动力廊并网，动力冲压机轰鸣（M）。TS 稳定 60 档的栓动步枪下产线，配漏夹装填——N-1 "叮"声成瘾。军剩 Berdan 弹药腐蚀枪的教训→溶剂常备（B-4/H-2.1 腐蚀轨）。第一次故障教学：连续两周目未保养→抽壳失败，玩家学会卸弹匣+排障（E-3 排障三式）。
> 触及：A4/M/N-1/B4/H/E/F(炸膛警告信：朋友超装药枪在靶场 II 级损毁，事故报告可读)

**第 4 章 无烟火药（T4，30–50 日）**：化学区上险，硝化釜配独立动力冗余（A-5/M-2.2 惊险记忆点：一次断闸报废三批药）。无烟弹药换装日=射表革命：初速涨、烟迹消失、保养周期 3 倍放宽。狙击小组玩法成装：快缠管+重弹+4 倍镜归零 300m（J-2.3），队友观测镜报横风（C-2.3/N-7）。机枪阵地：250 发换管+石棉手套+弹链箱（G-2.4/L/N-1 BELT）。潜入夜间掠夺者营地：亚音速+消音 COVERT 七进七出（K-2.2 成就）。
> 触及:A5/B5/B6/C 全量/G/K/J/I(部件更换经济)/N-2 扳机

**第 5 章 兵工厂（T5，50 日+）**：冲压 AK 线跑量武装队友/村民卫队；精铣线产出 TS 92 的"名匠"步枪被挂进展柜，铭刻 214 杀（N-4）。服务器市场的"名枪拍卖"成为周末活动（L-3/A-8 factory_tag）。工厂观光 Ponder 场景教会新玩家动力冗余。
> 触及：A6/A7/L/N-4/全部系统总装验收

**设计结论**：旅程剧本中每个系统至少被自然激活一次，且系统之间相互给彼此提供"动机"（公差↔经济↔维护↔风险）——咬合自洽，无孤儿系统。

## 22.3 UI/键位总表（实现期窗口登记，防键位冲突）

| 键位(默认) | 功能 | 系统 | 备注 |
|---|---|---|---|
| `V` | 排障/拉机柄 | E | 情境复用：开膛上膛同键 |
| 按住 `B` | 检查枪管揭示 Squib | E/F | 带进度环 |
| `X` | 保险开关 | E/N-5 | |
| `左Alt` | 屏息 | D | 客户端预测 |
| `PgUp/PgDn` | 归零调节 | J | 瞄具持有中有效 |
| `Y` | 速换枪管 | G | QCB 枪限定 |
| `U` | 两脚架部署 | D/J | 依托检测成功才有动画 |
| `N` | 弹匣管理界面(看携行具) | L/N-1 | GUI |
| `H` | 保养提示书(手册) | 全局 | 手册 mod 热键 |
| *(现有)* TACZ 射击/瞄准/换弹/检视 | — | 原生 | 冲突矩阵 Q-17 核对 |

GUI 清单：锻砧(热度条)、车床(走刀)、拉膛线机(缠距选择)、复装台(6 页签)、反应釜(流体+风险表)、研究桌(蓝图)、保养台(2 页签:保养/拆装)、装配台(配方+TS 预测)、展示柜(履历)、携行具(弹匣位)、枪钳台。全部走 `MenuType`+客户端 Screen 标准范式，配色沿用 TACZ 枪匠台暗黑工业风以保持视觉一体。
