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
| 2026-08-01 | [../impl-log/P1-manufacturing-foundation-data-layer.md](../impl-log/P1-manufacturing-foundation-data-layer.md) | P1 数据层+规则层：A-1 材料树/A-2 热度条工序/A-8 公差系统（17 类/43 JSON/实机编译绿） |

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
