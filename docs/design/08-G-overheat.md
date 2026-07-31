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
