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
