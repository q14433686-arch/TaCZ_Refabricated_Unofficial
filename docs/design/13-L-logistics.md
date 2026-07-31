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
