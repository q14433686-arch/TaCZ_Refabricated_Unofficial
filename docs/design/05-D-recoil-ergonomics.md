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
