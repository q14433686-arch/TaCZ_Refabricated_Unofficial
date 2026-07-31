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
