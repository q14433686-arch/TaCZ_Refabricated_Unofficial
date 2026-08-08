# 工业维护、卡壳与模块化维修：代码审计与实施设计

> 状态：A 阶段维护数据、B 阶段工业勤务台、C.0/C.1/C.2 清障、C.3 的真实 HeatData/环境/清洁，以及 C.4 首批按动作族分级的故障模型均已接入；没有真实维修出口的旧/未知枪不会被随机锁死。
> 原则：先建立完整的服务端事务、清障动作与真实维修路线，再启用会妨碍开火的随机状态；任何未声明/未验证清障动作的枪保持 `clear_action = none`。

## 1. 审计结论

### 已经可复用的基础

| 现有基础 | 位置 | 对维护系统的意义 |
|---|---|---|
| 工业成枪来源标记 | `IndustryAssemblyPlatform` / `IndustryAssemblyRecipe` | 可以只对真实工业制造的枪启用维护，旧战利品和未知第三方枪默认不被突然加故障。 |
| 五类平台结构件 | `receiver`、`bolt`、`barrel`、`trigger`、`recoil` | 已是模块化制造的真实 NBT 身份，可成为可拆、可修、可重装的维修单元。 |
| 真实多槽 GUI | Gunsmith Table、弹药装配机、工业回收站 | 可做真实多槽拆解/复装，不把多件拆出的零件伪装成置物台输入。 |
| 单工件 Create 语义 | 部署器、冲压、顺序装配 | 每一件损坏组件可在传送带/置物台上独立进行机械维修，工具由部署器持有。 |
| 服务端逐发扣弹钩子 | `ModernKineticGunScriptAPI#shootOnce` → `reduceAmmoOnce` | C.2 只在真实扣弹**之后**决定下一次供弹是否卡滞；后续触发在扣弹前被服务器拒绝，因此不吞已击发这一发，也不额外吞下一发。 |
| 热量 / 过热 | `GunHeatData`、`HeatAmount`、`OverHeated` | C.3 只对真正声明 `heat` 的枪按当前原生热量比例放大 wear/Fouling；没有 HeatData 的枪严格保持热倍率 1，不拿枪名或类别伪造温度。 |
| 破坏性回收站 | `IndustrialSalvageStationBlockEntity` / `IndustrialSalvageService` | 已实现的是报废回收：枪 → 中性毛坯/钢板，不是可逆维修；应保留为报废路线。 |

### 不能直接拿来当维修的部分

1. **过热不是耐久。** 当前热量只在实际射击后写入 NBT，并只影响声明了 `heat` 的枪。许多普通步枪没有 `heat` 数据，直接拿它作为“枪况”会让系统只覆盖少数机枪。
2. **工业回收站不可逆。** 它只有一输入、九输出，并有意把工业枪拆成中性毛坯；直接把它改成维修台会破坏现有安全回收语义。
3. **现有 reload / bolt 动画不能被随意借来伪装清障。** 如果服务器产生卡壳但客户端没有同一条清障动画/事务，玩家会看到枪在动作而服务器状态不同步。因此卡壳要晚于“清障协议”上线。
4. **第三方测绘成枪不能只根据 GunId 猜测来源。** A 阶段已让测绘 fallback 的最终枪写入 `IndustryAssemblyPlatform = surveyed/<namespace>/<gun>`、测绘 tier/工装来源；未经过该最终多槽委托的同 GunId 战利品仍不会默认参与维护。

## 2. 已恢复的枪械熟练度（独立于维护）

原先 `ModernKineticGunItem#getLevel/getExp/getMaxLevel` 全部返回 `0`，虽然 NBT、Tooltip 与升级通知外壳还在，进度永远不会增长。现已恢复为每把**实体枪**独立的 0–10 熟练度：

```text
命中：+8 XP
击杀：+28 XP
爆头：额外 +4 XP
等级阈值：level² × 100，总上限 10000 XP
```

投射物发射时会绑定发射枪的稳定 NBT token；命中延迟发生后只会给这同一把实体枪记经验，不会因为玩家切换到另一把同 GunId 的枪而串经验。实现层现命名为 `GunLevelImplementation`：它直接复用原有 `GunLevelExp` NBT、`IGun#getLevel/getExp/getMaxLevel`、Tooltip 与升级包；旧 `GunExperienceService` 仅保留为向后兼容转发层，不再是平行经验存档。熟练度现在不再只是进度条：服主可配置地让 0–10 级实体枪逐步缩短 ADS 时间、降低**服务器实际投射物**散布，并降低本地后坐镜头。它不增加直接伤害、护甲穿透、维修折扣，也不绕过 C.1/C.2/C.4 的真实维护故障，因此不会与 Condition/Fouling 混成一个数值。

## 3. 推荐总原则

```text
永久结构磨损（组件 Condition）
+ 临时积碳/污垢（Fouling）
+ 当前热量（Heat，仅有 heat 数据时）
→ 服务端计算“本次是否产生卡壳”
```

- **新枪、清洁、低温时不应凭空随机卡壳。** 常态风险为 0 或接近 0；卡壳是低状态、高污垢、高热量叠加后的后果。
- **不做爆炸、报废消失、吞弹失败。** 初版卡壳只产生可解释的机械阻塞；不会毁枪、删弹匣或在失败时悄悄扣走一发弹。
- **耐久低不等于只能再造整枪。** 正确修复路径是拆出受损模块，经 Create 工位修复或更换，再用真实多槽台复装。
- **旧存档安全优先。** 默认只影响带工业来源标记的枪；管理员可选择 `ALL_GUNS`，但第一次迁移仍从满状态开始。

## 4. 数据与 NBT 模型

### 3.1 枪上的持久状态

建议使用 `minecraft:custom_data`，不占用原版 Item damage 条，也不与第三方枪包的 ItemStack 耐久冲突：

```text
IndustryMaintenanceSchema: 1
IndustryConditionReceiver: 0..10000
IndustryConditionBolt:     0..10000
IndustryConditionBarrel:   0..10000
IndustryConditionTrigger:  0..10000
IndustryConditionRecoil:   0..10000
IndustryFouling:           0..10000
IndustryJam:               none | feed | lockout
IndustryMaintenanceSeed:   long
IndustryMaintenanceShots:  long
```

组件初始条件为 `10000`。枪 HUD/Tooltip 显示为“良好 / 需保养 / 需维修 / 停用”，不强迫玩家记忆整数。维护 profile 还声明玩家可见的 `durability_grade` 与 `expected_barrel_shots`：它是可审计、可覆盖的工业耐久分级与预计枪管勤务区间，而不是把所有枪压成数百发即报废的统一条，也不是对现实型号做未经证据的寿命断言。

### 3.2 组件上的状态

拆解后，专用的 `tacz:service_component`（旧世界带 `IndustryPartCondition` 的 `tacz:gun_component` 会迁移）保留它原有的：

```text
IndustryPlatform
IndustryPartKind
```

并附带：

```text
IndustryPartCondition
IndustryServiceGunId
IndustryServiceOrigin
```

这样 AK 枪机不能被当成 Kar98 枪机修好后装回去；生产模板、验收量规和组件身份仍是实际匹配条件。

### 3.3 数据驱动维护档案

不要把磨损参数硬编码到 `if (gunId == ...)`。新增独立资源层：

```text
data/<namespace>/industry/maintenance/guns/<gun-path>.json
```

概念结构：

```json
{
  "schema_version": 1,
  "eligibility": "industrial_assembly",
  "maintenance_class": "service_rifle",
  "wear_per_shot": {
    "receiver": 1,
    "bolt": 2,
    "barrel": 3,
    "trigger": 1,
    "recoil": 2
  },
  "fouling_per_shot": 3,
  "heat_stress_multiplier": 1.35,
  "operation": {
    "wear_multiplier": 1.0,
    "fouling_multiplier": 1.0,
    "submerged_wear_multiplier": 1.35,
    "submerged_fouling_multiplier": 1.75,
    "rain_wear_multiplier": 1.10,
    "rain_fouling_multiplier": 1.25,
    "contaminant_wear_multiplier": 1.15,
    "contaminant_fouling_multiplier": 1.45
  },
  "jam": {
    "warning_condition": 6000,
    "critical_condition": 1500,
    "max_chance": 0.04,
    "clear_action": "none",
    "fault_mode": "service_lockout"
  },
  "service": {
    "fixture": "bolt_action",
    "tooling_scope": "critical_gauge"
  }
}
```

这只是游戏内维护平衡数据，不映射现实武器寿命或故障参数。`heat_stress_multiplier` 是满原生 HeatData 时的最大倍率：没有真实 `GunHeatData` 的枪永远不从该字段获得额外磨损。`rain_*` 只在服务端确认玩家实际淋雨时使用；浸没或 `#tacz:maintenance_wet_exposure` 接触使用更强的 submerged 对；`#tacz:maintenance_contaminants` 可由数据包扩展泥、沙、雪、土等接触污染。默认 53 把枪可由作者工具按现有 `IndustryAssemblyTier` / `IndustryAssemblyActionProfile` 生成基线；第三方必须显式提供档案或使用清楚标注的 `surveyed` 通用档。

## 5. 卡壳状态机

### 4.1 触发点

C.2 的创建点与阻止点刻意分开，均在服务端：

```text
ModernKineticGunScriptAPI#shootOnce
  → reduceAmmoOnce() 成功
  → 记录 Condition / Fouling / Shots
  → 对已审计 profile 作确定性 feed 抽样
  → 写 IndustryJam = feed（只影响下一次触发）

LivingEntityShoot / 延迟 burst-Lua cycle
  → 发现 IndustryJam
  → 在任何 chamber / reduceAmmoOnce 之前返回 JAMMED
```

因此这不是客户端按键层的假状态，也不会把刚刚已经发射的弹药“反悔”或吞掉。Lua 连发、burst、栓动、闭膛自动上膛和使用 `shootOnce` 的第三方脚本都会经过上述服务器门。

当前实现的两种状态：

| 状态 | 弹药 | 后果 | 解除 |
|---|---|---|---|
| `feed` | 触发它的当前一发已按正常语义真实扣除；后续触发不扣弹 | 下一次触发被服务器拒绝 | 显式 C2S 清障请求 → 已验证的手动拉栓完成且真实上膛 |
| `service_lockout` | 触发它的当前一发已按正常语义真实扣除；后续触发不扣弹 | C.4 机械勤务故障，服务端锁止 | 工业勤务台拆解、维修组件并复装 |
| `lockout` | 后续触发不扣弹 | 组件状态到临界值，禁止继续射击 | 工业勤务台拆解、维修组件并复装 |

`stovepipe`、双重供弹、卡壳中遗留已击发壳等会影响膛内/壳体状态，必须等独立动画与状态模型齐备后再做；不能先用随机扣一发弹冒充。

### 4.2 风险计算

C.4 的直接抽样读取已保存的结构/污垢，并以本次真实 C.3 HeatData、雨/湿/污染 Exposure 作封顶放大。每枪 profile 按动作族声明 `warning_condition`、`critical_condition`、`max_chance` 与 `fault_mode`；对 `warning_condition > minCondition > critical_condition`：

```text
conditionRisk    = clamp((warning_condition - minCondition)
                         / (warning_condition - critical_condition), 0, 1)
foulingRisk      = clamp(Fouling / 10000, 0, 1)
exposureStress   = clamp(real heat / rain-or-wet / contaminant stress, 1, 2)
chance           = max_chance × conditionRisk³
                   × (0.15 + 0.85 × foulingRisk) × exposureStress
```

满状态的 `conditionRisk = 0`，故不会凭空故障；达到 `critical_condition` 前后由确定性的 `lockout` 优先。`IndustryMaintenanceSeed + IndustryMaintenanceShots` 经固定 64 位混合生成**服务器确定性**抽样，避免客户端预测、退出重进或多人时序成为刷概率手段。

### 4.3 清障协议先于卡壳启用

C.2 已落地的清障协议：

```text
玩家在 Tooltip 显示“供弹卡滞”时再次按开火
→ 客户端只发送 c2s_clear_feed_jam，并触发已有的手动 bolt 动画/声音
→ 服务器验证：IndustryJam=feed、同一把手持工业枪、profile.clear_action=bolt、
  当前 GunData 真正是 MANUAL_ACTION、无换弹/切枪/射击冷却冲突
→ 正常 LivingEntityBolt / tickBolt 完整执行
→ 仅在 bolt 已结束且 hasBulletInBarrel=true 时删除 IndustryJam=feed
→ 服务器把完整 held ItemStack 快照 S2C 回传；HUD/Tooltip 和本地自动 bolt 一同按该快照更新
```

普通自动拉栓的 C2S 包在 `feed` 状态下被服务器拒绝，不能静默清障。默认资源只为 `AI AWP`、`Kar98`、`M107`、`M700`、`M870`、`M95`、`SPAS-12` 写入 `clear_action: "bolt"`：作者生成器还逐项核验未修改的默认枪包中存在 `manual_action` 数据、显示状态机和命名 `bolt` 动画。其余默认 46 把、所有 `surveyed` 通用档案以及未知第三方枪都是 `clear_action: "none"`，不会随机 `feed` 卡滞。第三方作者若要 opt-in，必须显式声明 `clear_action: "bolt"`，并且仍会被运行时 `MANUAL_ACTION` 验证挡住伪造动作。

## 6. 模块化维修流程

### 5.1 新机器：工业勤务台 / 拆解复装台

不要改造现有 `industrial_salvage_station`。新增一个真正多槽的 `industrial_service_bench`：

```text
拆解模式：
空枪（无已装供弹器、无配件、无枪内散装弹）
+ 对应生产模板（保留）
+ 对应验收量规/勤务夹具（保留）
+ 装甲匠扳手（消耗耐久）
→ 5 个带 Condition 的平台组件

复装模式：
5 个同平台、同 GunId 的组件
+ 生产模板（保留）
+ 验收量规/勤务夹具（保留）
+ 装甲匠扳手（消耗耐久）
→ 同一 GunId 的工业枪，清除 Jam、重置 Fouling，保留各组件修后状态
```

这是实际多槽 GUI；不把五个组件伪装成传送带上同时存在的工件。

拆解前必须正常退匣/取桥夹、卸配件、清空内部弹药。若物品仍带这些状态，服务器拒绝操作而不是静默删除。

### 5.2 工业勤务台内的组件维修

旧的 `service_part_blank` / `service_part` 与 Create 单工件维修线已完全退出新流程；它们仅作为旧世界兼容物保留，不再有新配方。新维修是工业勤务台内的真实多槽服务器事务，不需要机械动力、部署器、置物台、传送带或顺序装配：

```text
5 个同 GunId / 同来源的 service_component
+ 对应 production 模板（保留）
+ 对应检具（保留）
+ armorer_wrench（损耗 1 点）
+ high_carbon_steel_plate / create:brass_sheet（按实际 Condition 缺口扣除）
→ 仅受损组件恢复满 Condition
```

钢/黄铜是维修材料而不是“对着整枪点一下”的修复按钮：服务器先校验五槽组件、模板、检具、扳手与全部材料槽，再原子扣料并只写回受损组件。复装仍是独立按钮，只保留组件 Condition、清洁 Fouling 并解除可解除的故障；不能把拆出的耐久组件带回普通总装配方。

C.3 另增**不拆枪的清洁事务**，仍在同一工业勤务台：安全清空的完整工业枪 + 同平台 production 模板 + 检具 + 扳手 + `tacz:maintenance_cleaning_kit` → 输出槽中的同一把枪。清洁套件必须先在真实加热 Basin 以碳粉、纸和黏液球混合制得；服务器按 `ceil(IndustryFouling / 2500)` 扣套件并将 Fouling 清到 0。它不会恢复任一 Condition、不会清除 `feed`、也不会解除 `lockout`，因此不是伪装成“万能修枪”的按钮。

### 5.3 工具与耐久

新增 `armorer_wrench` / `service_fixture`：

- 使用 26.2 原生 `DataComponents.MAX_DAMAGE` / `DAMAGE`；项目的 LRTactical 近战物已验证这一组件写法；
- 拆解和复装成功后才损耗一点，失败、槽位不匹配或输出堵塞绝不损耗；
- 工具自身可通过 Create 线维护，不把“扳手坏了”变成重新造整枪的惩罚；
- 生产模板保持工装身份，不把它变成一次性耗材。

## 7. 分阶段实施

### A. 维护数据和可视化基础（已实施，待外部实机回归）

1. `IndustryMaintenanceProfile` manager 已经通过独立 `INDUSTRY_MAINTENANCE` 网络通道同步；生成了默认 53 枪的 `industry/maintenance/guns/*.json`，Tooltip/HUD 显示最差组件枪况与污垢；
2. `INDUSTRIAL_ASSEMBLY` 是默认安全范围：Create 总装枪和测绘最终枪首次在服务器使用时得到满 Condition/清洁状态，旧工业 NBT 同样补齐；`ALL_GUNS` 是管理员显式 opt-in，首次迁移仍满状态；
3. 只在 `reduceAmmoOnce()` 成功后记录 Shot / Fouling / Condition；创造模式免费射击不磨损。C.2 仅在少数显式 `clear_action: bolt` 档案上、同一成功扣弹之后写 `feed`，并只阻止**后续**触发；其余 profile 仍没有随机卡滞、吞弹或额外扣弹；
4. 默认总装输出已有来源标签，测绘 Gunsmith Table fallback 现在也写入完整 `surveyed/...` 来源、tier 与工装标签。

### B. 拆解、Create 修件、复装（B.1 已开始）

1. 已新增独立 `industrial_service_bench` block entity、十三槽 menu、screen 与服务端 C2S 事务；它不复用或改写破坏性回收站；
2. 已实现五组件保真拆解和复装：空枪 + 生产模板 + 对应动作/平台检具 + 原生耐久的军械勤务扳手 → 专用 `service_component`（receiver/bolt/barrel/trigger/recoil 的真实平台种类），复装严格要求同 GunId、同工业来源；带 `IndustryPartCondition` 的勤务组件与普通 `gun_component` 是不同 registry item，因此不能再进入常规枪械总装配方。客户端保留相同的 `IndustryPlatform + IndustryPartKind`，优先允许资源包提供专用耐久组件图；没有专用图时复用该上游普通组件的精确映射和通用组件底图，不出现紫黑缺图块；附件、实体供弹器、漏夹、内部/膛内弹药未清空时拒绝；
3. 维修已收束到独立工业勤务台的真实多槽事务，不要求机械动力或隐藏的 Create 序列：五个拆出组件 + 对应 production 模板 + 检具 + 扳手 + 高碳钢板/黄铜板进入工作台；服务器按每个组件的实际 Condition 缺口计算材料需求，只修复损坏组件并在槽内写回满 Condition。复装按钮只保留组件 Condition、清洁 Fouling，绝不直接满修；旧 service_part 物品保留为世界兼容物但不再是维修路径；
4. 完整回归附件、实体弹匣、桥夹、内部弹药与输出堵塞。

### C. 卡壳、环境与清障（C.0–C.4 首批已实施，待外部实机回归）

1. C.0 已扩展默认 53 枪维护 profile：操作结构的基础 wear/fouling multiplier、浸没 multiplier、`#tacz:maintenance_contaminants` 地面污染 multiplier 均由数据决定；服务端只在实际扣弹后读取浸没/污染暴露并计入 Condition/Fouling，不按枪名硬编码现实“可靠性”；
2. C.1 已启用确定性的 `lockout`：组件最低 Condition 到 profile `critical_condition` 后，在**下一次**扣弹前通过 `ShootResult.JAMMED` 阻止射击；Tooltip/HUD 显示“勤务锁止”，只能拆解、维修组件并复装后解除。它不吞弹、不制造未同步的随机动作；
3. C.2 已启用确定性 `feed` 抽样，但范围严格限于通过默认资源审计、且运行时为 `MANUAL_ACTION` 的枪。它发生在已成功扣除当前一发之后，下一次触发前被服务器阻止；玩家按开火键发起独立 C2S 清障，只有真实拉栓结束并确认上膛才解除。普通自动 bolt 不可绕过，S2C 完整枪快照消除客户端自动 bolt 的竞态；
4. C.3 已将原生 `GunHeatData` 接入实际 wear/Fouling：满热时按 profile 的 `heat_stress_multiplier` 放大，服主可用全局开关、额外 wear/fouling scale 覆盖；未声明 heat 的枪不受影响。服务端还区分淋雨、浸没/湿接触、以及可数据扩展的接触污染标签。工业勤务台新增 13 号清洁材料槽和独立清洁按钮，使用 Basin 制成的套件降低 Fouling，不改变结构 Condition 或故障状态；
5. C.4 首批将每个生成的默认工业枪按 `maintenance_class` 写入不同 warning/critical/max-chance 与 `fault_mode`。有已审计 manual bolt 的平台维持 `feed`；其余默认动作族使用 `service_lockout`：当前一发照常真实射出，随后由服务端锁止，必须走工业勤务台的拆解、维修、复装，绝不伪造 closed/open-bolt、转轮或折管的“拉栓清障”动画。热、雨/湿/污染只通过本次服务端 Exposure 放大实际故障风险；
6. C.4 的安全边界：`service_lockout` 只会施加给确实带工业来源、因此能被真实勤务台复装的默认/测绘枪。管理员的 `ALL_GUNS` 旧战利品若没有工业来源和对应工装，仍只记录维护数据，不能被随机锁成没有维修出口的物品。后续扩展更多第三方故障种类前，仍须先提供其真实、服务器可验证的清障或勤务路径；不能仅按 `reload.type`、命名空间或一张 JSON 伪造处理能力。

## 8. 发现的未完成 / 未完全替换项目

| 优先级 | 项目 | 当前事实 | 建议 |
|---|---|---|---|
| P0 | 最新桥夹路线的外部编译与实机验证 | `3eae889` 后尚无 Windows Gradle / 游戏结果 | 先执行 `./gradlew build`；重点测 Kar98、Mosin、桥夹归零保留。 |
| P0 | 漏夹 `en_bloc_clip` | 已完成独立 `InstalledEnBlocClip`、逐发扣除、空夹真实 ItemEntity 自动弹出；首个样本为 `hamster:m1garand` | 外部 Gradle/实机验证后，为更多已审计漏夹枪补 profile。 |
| P1 | 工业维护 / 模块维修 | A/B 已完成 Condition/Fouling、来源迁移、Tooltip/HUD、工业勤务台拆解/维修/复装；C.2 `feed` 只给已审计手动拉栓枪；C.3 已接可配置原生热/雨/湿/污染与 Basin 清洁；C.4 默认动作族已获得 feed 或 bench-only `service_lockout` 分型 | 外部实机回归 C.2–C.4 的无吞弹、S2C 同步、热配置、熟练度操控和 service lockout 的真实勤务台出口；再审计第三方动作族。 |
| P1 | 长按 R 供弹器选择圆盘 | 按用户要求暂缓；当前没有长按输入、轮盘 UI、选槽位包或服务器预留，单点 R 已恢复为按下即换弹 | 设计冻结在 [`RELOAD_WHEEL_DEFERRED_DESIGN.md`](RELOAD_WHEEL_DEFERRED_DESIGN.md)；重新确认交互并完成多弹种/服务端事务验收后再实现。 |
| P1 | 速度装填器代表包 | 已接入 GunpowderRevolution `hamster:webley`：完整 6 发快装器触发原 `reload_loader`，无完整器件走逐发 | 外部验证后再审计 Webley 以外的转轮；不同脚本不套用。 |
| P1 | RPG 发动机壳体 | 已新增 `motor_housing` 可见 ItemStack 与 `finish_case_rpg_rocket` 单工件结束站 | 在 Create/装弹机实机回归中确认它不能绕过为最终 case。 |
| P2 | 第三方高保真 Create 终端 | 当前安全回退是 Gunsmith Table 测绘多槽路径 | 只为资料足够的高置信枪包做显式终端，不动态猜结构。 |
| P2 | 测绘弹药精确视觉 / 特殊抛壳 | 未声明口径使用标准材质族 | 引入独立 ammo reference profile 后补精确贴图与特殊语义。 |
| P2 | 枪械等级 | 已恢复 0–10 的实体枪熟练度：命中/击杀按发射时绑定的实体枪 token 记经验，Tooltip 与升级消息生效 | 当前只恢复可见进度，不暗中改变伤害/可靠性；后续如要加收益必须单独平衡。 |
| P3 | 26.2 客户端表现待办 | 激光束、枪口火焰、部分文字渲染、Accelerated Rendering 兼容仍有明确 TODO | 与工业语义无直接耦合，按渲染 API 稳定程度单独处理。 |
| P3 | LRTactical 未完移植 | 多种投掷物、网络同步/音频和部分实体效果仍标注未实现 | 与 TACZ 工业线分开排期，避免混入维护提交。 |

### 文档债务

`INDUSTRIAL_TACTICAL_DESIGN.md` 的早期章节仍写“拆解台后续实现”，但后文已记录工业回收站落地。应明确区分：**回收站已经完成，模块化勤务台尚未实现。**
