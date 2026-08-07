# 工业维护、卡壳与模块化维修：代码审计与实施设计

> 状态：已完成代码结构审计；尚未把耐久/卡壳强行接入战斗逻辑。
> 原则：先建立完整的服务端事务、清障动作与真实维修路线，再启用会妨碍开火的随机状态。

## 1. 审计结论

### 已经可复用的基础

| 现有基础 | 位置 | 对维护系统的意义 |
|---|---|---|
| 工业成枪来源标记 | `IndustryAssemblyPlatform` / `IndustryAssemblyRecipe` | 可以只对真实工业制造的枪启用维护，旧战利品和未知第三方枪默认不被突然加故障。 |
| 五类平台结构件 | `receiver`、`bolt`、`barrel`、`trigger`、`recoil` | 已是模块化制造的真实 NBT 身份，可成为可拆、可修、可重装的维修单元。 |
| 真实多槽 GUI | Gunsmith Table、弹药装配机、工业回收站 | 可做真实多槽拆解/复装，不把多件拆出的零件伪装成置物台输入。 |
| 单工件 Create 语义 | 部署器、冲压、顺序装配 | 每一件损坏组件可在传送带/置物台上独立进行机械维修，工具由部署器持有。 |
| 服务端逐发扣弹钩子 | `ModernKineticGunScriptAPI#shootOnce` → `reduceAmmoOnce` | 卡壳检查可以放在真正消耗弹药之前，避免“子弹被吞但没有射击”。 |
| 热量 / 过热 | `GunHeatData`、`HeatAmount`、`OverHeated` | 可作为高温额外风险倍率；不能取代全局耐久，因为只有声明 `heat` 的枪才拥有该数据。 |
| 破坏性回收站 | `IndustrialSalvageStationBlockEntity` / `IndustrialSalvageService` | 已实现的是报废回收：枪 → 中性毛坯/钢板，不是可逆维修；应保留为报废路线。 |

### 不能直接拿来当维修的部分

1. **过热不是耐久。** 当前热量只在实际射击后写入 NBT，并只影响声明了 `heat` 的枪。许多普通步枪没有 `heat` 数据，直接拿它作为“枪况”会让系统只覆盖少数机枪。
2. **工业回收站不可逆。** 它只有一输入、九输出，并有意把工业枪拆成中性毛坯；直接把它改成维修台会破坏现有安全回收语义。
3. **现有 reload / bolt 动画不能被随意借来伪装清障。** 如果服务器产生卡壳但客户端没有同一条清障动画/事务，玩家会看到枪在动作而服务器状态不同步。因此卡壳要晚于“清障协议”上线。
4. **第三方测绘成枪目前不总有默认枪的 `IndustryAssemblyPlatform` 标记。** 在给测绘 fallback 启用可维修耐久前，必须在其最终产物上补入明确的工业来源/平台记录，不能根据 GunId 猜测。

## 2. 已恢复的枪械熟练度（独立于维护）

原先 `ModernKineticGunItem#getLevel/getExp/getMaxLevel` 全部返回 `0`，虽然 NBT 与 Tooltip/Toast 外壳还在，进度永远不会增长。现已恢复为每把**实体枪**独立的 0–10 熟练度：

```text
命中：+8 XP
击杀：+28 XP
爆头：额外 +4 XP
等级阈值：level² × 100，总上限 10000 XP
```

投射物发射时会绑定发射枪的稳定 NBT token；命中延迟发生后只会给这同一把实体枪记经验，不会因为玩家切换到另一把同 GunId 的枪而串经验。当前熟练度只恢复进度条、Tooltip 与升级 Toast，**不改变伤害、可靠性、耐久或维修成本**，因此不会与下文的 Condition/Fouling 混成一个数值。

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
IndustryJam:               none | feed | cycle | lockout
IndustryMaintenanceSeed:   long
IndustryMaintenanceShots:  long
```

组件初始条件为 `10000`。枪 HUD/Tooltip 显示为“良好 / 需保养 / 需维修 / 停用”，不强迫玩家记忆整数。

### 3.2 组件上的状态

拆解后，`tacz:gun_component` 保留它原有的：

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
  "heat_stress_multiplier": 1.0,
  "jam": {
    "warning_condition": 6000,
    "critical_condition": 1500,
    "max_chance": 0.08
  },
  "service": {
    "fixture": "bolt_action",
    "tooling_scope": "critical_gauge"
  }
}
```

这只是游戏内维护平衡数据，不映射现实武器寿命或故障参数。默认 53 把枪可由作者工具按现有 `IndustryAssemblyTier` / `IndustryAssemblyActionProfile` 生成基线；第三方必须显式提供档案或使用清楚标注的 `surveyed` 通用档。

## 5. 卡壳状态机

### 4.1 触发点

真正的检查点应在服务端的：

```text
ModernKineticGunScriptAPI#shootOnce
  → 成功之前的 reduceAmmoOnce
```

而不是只放在客户端按键层，也不是在 `LivingEntityShoot#shoot` 一开始就随机。原因：Lua 连发、burst、栓动、闭膛自动上膛和第三方脚本最终都要经过这里的“是否真的消耗一发”判断。

初版只实现两种可解释状态：

| 状态 | 弹药 | 后果 | 解除 |
|---|---|---|---|
| `feed` | 本次不扣弹 | 本次击发取消，枪进入卡壳 | 服务器验证的清障动作 |
| `lockout` | 本次不扣弹 | 组件状态过低，禁止继续射击 | 服务台维修/更换组件 |

`stovepipe`、双重供弹、卡壳中遗留已击发壳等会影响膛内/壳体状态，必须等独立动画与状态模型齐备后再做；不能先用随机扣一发弹冒充。

### 4.2 风险计算

建议只在以下条件叠加后开启风险：

```text
conditionDeficit = 1 - min(componentCondition) / 10000
foulingFactor    = fouling / 10000
heatFactor       = HeatAmount / heatMax（仅 gun 有 heat 数据）
chance = clamp(
  conditionDeficit² * maintenanceProfile
  + foulingFactor * maintenanceProfile
  + heatFactor² * heatStressMultiplier,
  0,
  maxChance
)
```

`IndustryMaintenanceSeed + IndustryMaintenanceShots` 生成服务器确定性的抽样，避免客户端预测、退出重进或多人时序成为刷概率手段。满状态且清洁的枪不应因“纯随机”卡壳；达到 `critical_condition` 后应改为确定性的 `lockout`，提醒玩家维修而不是赌下一发。

### 4.3 清障协议先于卡壳启用

启用 `feed` 卡壳前，必须完成：

```text
客户端请求清障
→ 服务器验证 Jam / 手持枪 / 无换弹 / 无切枪 / 工具条件
→ 选择已审计的 clear-jam 动画或已有 bolt 动画
→ 到服务器 feed 点清除 Jam，绝不扣弹
→ 同步 HUD、声音和第三人称状态
```

没有 `jam_clear_animation` 或可证明可复用 bolt 动画的第三方枪，维护档案只能启用磨损/污垢警告与 `lockout`，不能启用随机 `feed` 卡壳。这样不会制造“服务器卡住、客户端不知道怎么解”的半成品机制。

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

### 5.2 Create Fly 中的维修

拆出的组件进入真实单工件维修线：

```text
受损组件（唯一传送带工件）
+ 部署器持勤务扳手/夹具（保留）
+ 部署器供入已命名维修件（消耗）
+ 动力冲压机压合
→ 修复后的同身份组件
```

维修材料必须走：

```text
原料 → 中性维修毛坯 → 模板/量规校准的命名维修件 → 组件维修
```

不能直接用铁锭、木板或皮革对着最终枪“点一下恢复 100%”。枪管、枪机、复进组件等可各自要求不同的维修件；轻度污垢可用可复用清洁工具处理，不必每次完整拆枪。

### 5.3 工具与耐久

新增 `armorer_wrench` / `service_fixture`：

- 使用 26.2 原生 `DataComponents.MAX_DAMAGE` / `DAMAGE`；项目的 LRTactical 近战物已验证这一组件写法；
- 拆解和复装成功后才损耗一点，失败、槽位不匹配或输出堵塞绝不损耗；
- 工具自身可通过 Create 线维护，不把“扳手坏了”变成重新造整枪的惩罚；
- 生产模板保持工装身份，不把它变成一次性耗材。

## 7. 分阶段实施

### A. 维护数据和可视化基础

1. `IndustryMaintenanceProfile` manager、网络同步、Tooltip/HUD 条；
2. 工业成枪首次获得满 Condition；旧工业枪安全补齐；
3. 不启用随机卡壳，只记录 Shot / Fouling / Condition；
4. 为默认枪和已审计测绘枪补完整来源标记。

### B. 拆解、Create 修件、复装

1. 新工业勤务台 block entity、menu、screen、C2S 事务；
2. 五组件保真拆解和复装；
3. Create 单工件维修配方、勤务扳手和命名维修件；
4. 完整回归附件、实体弹匣、桥夹、内部弹药与输出堵塞。

### C. 卡壳与清障

1. `feed` / `lockout` 服务端状态；
2. `ShootResult.JAMMED`、同步消息、HUD/声音；
3. 先为默认包和有已审计 clear 动画的第三方枪启用；
4. 最后才按维护档案逐步放开第三方测绘枪。

## 8. 发现的未完成 / 未完全替换项目

| 优先级 | 项目 | 当前事实 | 建议 |
|---|---|---|---|
| P0 | 最新桥夹路线的外部编译与实机验证 | `3eae889` 后尚无 Windows Gradle / 游戏结果 | 先执行 `./gradlew build`；重点测 Kar98、Mosin、桥夹归零保留。 |
| P0 | 漏夹 `en_bloc_clip` | 已完成独立 `InstalledEnBlocClip`、逐发扣除、空夹自动返还；首个样本为 `hamster:m1garand` | 外部 Gradle/实机验证后，为更多已审计漏夹枪补 profile。 |
| P1 | 工业维护 / 模块维修 | 当前不存在；回收站是破坏性报废，不是维修 | 按本文件 A → B → C 实施。 |
| P1 | 长按 R 供弹器选择圆盘 | 服务器预留已存在，客户端选择 UI 尚未实现 | 在 route/选择槽位协议稳定后实现，不越过服务器事务。 |
| P1 | 速度装填器代表包 | 已接入 GunpowderRevolution `hamster:webley`：完整 6 发快装器触发原 `reload_loader`，无完整器件走逐发 | 外部验证后再审计 Webley 以外的转轮；不同脚本不套用。 |
| P1 | RPG 发动机壳体 | 已新增 `motor_housing` 可见 ItemStack 与 `finish_case_rpg_rocket` 单工件结束站 | 在 Create/装弹机实机回归中确认它不能绕过为最终 case。 |
| P2 | 第三方高保真 Create 终端 | 当前安全回退是 Gunsmith Table 测绘多槽路径 | 只为资料足够的高置信枪包做显式终端，不动态猜结构。 |
| P2 | 测绘弹药精确视觉 / 特殊抛壳 | 未声明口径使用标准材质族 | 引入独立 ammo reference profile 后补精确贴图与特殊语义。 |
| P2 | 枪械等级 | 已恢复 0–10 的实体枪熟练度：命中/击杀按发射时绑定的实体枪 token 记经验，Tooltip 与 level-up toast 生效 | 当前只恢复可见进度，不暗中改变伤害/可靠性；后续如要加收益必须单独平衡。 |
| P3 | 26.2 客户端表现待办 | 激光束、枪口火焰、部分文字渲染、Accelerated Rendering 兼容仍有明确 TODO | 与工业语义无直接耦合，按渲染 API 稳定程度单独处理。 |
| P3 | LRTactical 未完移植 | 多种投掷物、网络同步/音频和部分实体效果仍标注未实现 | 与 TACZ 工业线分开排期，避免混入维护提交。 |

### 文档债务

`INDUSTRIAL_TACTICAL_DESIGN.md` 的早期章节仍写“拆解台后续实现”，但后文已记录工业回收站落地。应明确区分：**回收站已经完成，模块化勤务台尚未实现。**
