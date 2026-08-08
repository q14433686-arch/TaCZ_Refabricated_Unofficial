# TACZ Create Fly：剩余工作清单

> 本清单把“仍需实现”、 “等待 Windows / 实机回归”、 “缺精确美术” 与“用户明确暂缓”分开。
> 它不把 JSON、NBT、REI/JEI 条目或中性贴图误写成已经完成的机械功能。

## 已完成，不应重新排入待办

- 服务端真实实体载具、逐发 `MagazineRounds`、同口径不同 AmmoId 混装、独立弹道/已击发壳；
- 背包/容器中的实体载具装填、退顶部连续同 AmmoId、装弹器装填均为**即时**交互；用户已经要求删除逐发倒计时，不能恢复后台 tick/计时工作台；
- 默认 Create 工业链、四槽 Cartridge Assembly、默认 Cartridge/Feed 标准注册表；
- Gunsmith Table 跨页检索，以及 JEI/REI `+` 锁定真实工作台配方；
- JEI/REI 的“枪械供弹查询”关系页；
- 工业勤务台、Condition/Fouling、首批 server-authoritative `feed` / `service_lockout` 路线。

## P0：需要先获得外部验证

| 项目 | 当前状态 | 验收条件 |
|---|---|---|
| Windows 构建 | Arena 不运行 Gradle | 在 Windows 工作副本执行 `.\gradlew build`，贴出完整失败日志；不要把 Arena 的 Python 内容检查误称为 Java 编译。 |
| Gunsmith Table / JEI / REI | 已接入锁定与供弹查询，但尚无本机实测 | 打开真实工作台后分别验证 JEI/REI 的 `+` 锁定、`×` 解锁、错误工作台拒绝；对枪、Ammo、配置实体弹匣执行查看配方/用途。 |
| 名称解析 | 测绘 tooltip 已改为运行时读取 GunIndex/AmmoIndex 名称 | 验证 `suffuse:trapper50cal` 显示上游枪名、`ww:77a` 显示 7.7×58 mm Arisaka 弹药名；F3+H 才显示原始 id。 |
| 通用弹匣回归 | 已修复标准层曾造成的换弹候选回归 | 同 family + canonical calibre 的旧无标准 tag、新标准 tag、跨包载具必须互换；两个明确冲突的标准必须拒绝。 |

## P1：真实功能 / 数据审计缺口

| 项目 | 不能用什么替代 | 后续条件 |
|---|---|---|
| 第三方 legacy 供弹机制 | 不能用 `reload.type`、枪种 class、模型或容量猜测 detachable magazine | `THIRD_PARTY_FEED_GAP_REGISTER.md` 当前记录 166 个 legacy 功能缺口；逐把补真实机构、脚本 feed 点、实体 source 与服务器事务。 |
| 复杂故障物理状态 | 不能用随机扣弹伪造 stovepipe、double-feed 或 extractor 故障 | 先实现膛内/已击发壳/双供弹的持久服务端状态、对应动画与清障路线，再启用。 |
| 更多桥夹、漏夹、快装器 | 不能把另一把枪的动画借来 | 以每个枪包的真实脚本 route、容量、AmmoId 和实体 device 为单位审计。 |
| 第三方高保真 Create 终端 | 不能由猜测 JSON 直接给出平台结构 | 当前 surveyed Gunsmith Table 多槽回退真实可用；只有资料、结构与授权资源足够的枪包才补专用 Create 工艺线。 |

## P1：精细材质 / 图标资产

细分美术与物理功能分开登记。当前已审计第三方实体载具中：

| 分类 | family 数 | 当前状态 |
|---|---:|---|
| 中性通用可拆卸弹匣 | 282 | 功能、容量、制造均真实；缺专用形状/细节图。 |
| 中性通用弹链箱 | 11 | 功能、容量、制造均真实；缺专用箱体/弹链细节图。 |
| 复用同类可拆卸弹匣图 | 2 | 可见但不是对应型号的精确图。 |
| 复用同类弹链箱/弹链图 | 10 | 可见但不是对应型号的精确图。 |

完整的**中文/英文显示名、每个容量变体、family、弹药、当前后备图和分类**由
[`THIRD_PARTY_FEED_GAP_REGISTER.md`](THIRD_PARTY_FEED_GAP_REGISTER.md) 与
`tools/industry/third_party_feed_gap_registry.json` 列出；所有第三方载具、AP/HP/slug 与测绘材质的分类说明见
[`FINE_MATERIAL_ART_CATALOG.md`](FINE_MATERIAL_ART_CATALOG.md)。它们是美术需求目录，不会启用或改变任何枪的供弹逻辑。

另外仍缺：

- AP / HP / Slug 的专用弹头图；当前机械链和 AmmoId 已真实存在，但不能把同口径 FMJ 复用图称为精确弹种美术；
- 未声明第三方 AmmoId 的测绘弹壳、弹头、已击发壳专用图；当前诚实使用标准材质族；
- 固定内仓、转轮、双管等不是 `tacz:magazine` ItemStack 的视觉合同。它们需要独立 gun/feed renderer，不能在“缺弹匣图”名单里伪造一个 detachable magazine。

## P2：明确暂缓，除非重新确认

| 项目 | 状态 | 原因 |
|---|---|---|
| 长按 R 供弹器选择轮盘 | **暂缓** | 用户已要求保留按下即走的正常换弹；当前不能暗中加长按判定、客户端预留包或倒计时。设计见 [`RELOAD_WHEEL_DEFERRED_DESIGN.md`](RELOAD_WHEEL_DEFERRED_DESIGN.md)。 |
| 背包/容器逐发计时 | **已删除，不应恢复** | 装退弹、装弹器均应即时完成；不能重建 `InventoryRoundHandlingService`、后台 tick 或新的处理台。 |

## P3：26.2 移植与表现层（与工业语义分开）

- 激光束、枪口火焰、部分文字/特效渲染仍需要适配 26.2 的 SubmitNodeCollector / 新渲染管线；
- Accelerated Rendering、Sulkan 等依赖上游 26.2 API 后才可恢复，不应靠 no-op 伪称兼容；
- LRTactical 的部分投掷物、网络同步、音频与实体效果仍是独立移植工作；
- 每一项都需真实 GPU 客户端与多人服务器回归，不能由静态源码检查替代。

## 推荐推进顺序

1. 先完成 Windows `build` 和工作台/换弹/名称显示的实机回归；
2. 选择一组最常用第三方枪包，按已有**显示名分类清单**补授权的精细弹匣/弹链箱图；
3. 对有真实脚本证据的 legacy 枪逐条补 bridge clip / speedloader / en-bloc / internal-feed，而非批量猜测；
4. 最后再讨论长按 R 轮盘和复杂物理卡壳状态。
