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
