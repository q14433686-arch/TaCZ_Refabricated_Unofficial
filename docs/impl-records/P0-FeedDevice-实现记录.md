# P0 补充：供弹具数据系统实现记录 + 架构评审修正

> 实施日期：2026-07-31
> 阶段：P0 基础架构与数据层补充

## 1. 概述

在 P0 基础数据层完成后、进入 P1 之前，补充了供弹具数据系统，
并进行了架构评审修正（移除 CartridgeType 中的衍生值字段）。

核心设计原则：
- **CartridgeType 只保留物理规格常量**：伤害/初速/射程是过程计算的结果，不是静态属性
- **CartridgeType 与 BulletType 职责分离**：口径决定物理兼容性，弹头类型决定终点弹道效果
- **LoadedRound 单发级数据**：突破原有 AmmoData 的堆叠级限制，支持混装弹药、装药过量判定
- **FeedDeviceData 密封接口**：针对 7 种供弹机制实现独立数据结构，不能以单一通用结构覆盖
- **GunStateData 枪膛追踪**：从布尔值升级为 Optional\<LoadedRound\>，为哑弹/瞎火/炸膛判定提供数据基础
- **AmmoData ↔ LoadedRound 同步契约**：AmmoData 是唯一的"事实来源"，往返一致性必须保证

## 2. 架构评审修正

### 2.1 🔴 严重：CartridgeType 中的衍生值字段已移除

**原问题**：CartridgeType 包含 `baseDamage`、`baseMuzzleVelocity`、`baseRange`、`basePowderWeight` 字段，
这些是"过程计算的结果"被当成"静态属性"存储，违背了终末弹道系统的核心哲学：
- 伤害不是固定数值——由命中解剖位置、弹头设计、动能综合决定
- 初速不是恒定的——由枪管长度、装药量、弹头质量综合决定

**修正**：移除所有衍生值字段，只保留物理规格常量：

| 移除的字段 | 原因 |
|-----------|------|
| `baseDamage` | 伤害由终末弹道公式在命中时实时计算 |
| `baseMuzzleVelocity` | 初速由弹道公式在开火时实时计算 |
| `baseRange` | 射程由弹道/风偏/散布综合决定 |
| `basePowderWeight` | 装药量由 LoadedRound.powderCharge 决定，口径只需提供弹壳容积 |

**新增的物理规格常量**：

| 新增字段 | 说明 |
|---------|------|
| `rimDiameter` | 底缘直径（mm），决定抽壳钩兼容 |
| `rimType` | 底缘类型（凸缘/无凸缘/半凸缘/缩缘/带式），决定抽壳钩设计和管状弹仓安全性 |
| `standardBulletMass` | 标准弹头质量（克），用于动能计算的输入 |
| `caseCapacity` | 弹壳容积（cm³），决定装药上限 |

**新增枚举**：`RimType`（5 个值：RIMLESS/RIMMED/SEMI_RIMMED/REBATED/BELTED）

### 2.2 🟡 中等：AmmoData ↔ LoadedRound 同步契约已建立

**新增文件**：`AmmoDataRoundContract.java`

铁律：
- AmmoData 是唯一的"事实来源"(Source of Truth)
- 任何字段的增删改，先改 AmmoData，再同步 LoadedRound
- `verifyRoundTrip()` 方法可断言往返一致性
- `toAmmoData()` 现在保留 cartridgeType（不再丢失）

## 3. 新增文件清单

### 3.1 口径类型系统（2个文件）

| 文件 | 路径 | 说明 |
|------|------|------|
| CartridgeType | `api/item/cartridge/CartridgeType.java` | 口径物理规格记录，10个字段（全部是恒定物理事实） |
| CartridgeTypeManager | `api/item/cartridge/CartridgeTypeManager.java` | 数据驱动注册表，含兼容性判定/安全装填判定 |

### 3.2 单发弹药数据（1个文件）

| 文件 | 路径 | 说明 |
|------|------|------|
| LoadedRound | `api/item/component/LoadedRound.java` | 一发子弹的完整个体数据，9个字段 |

### 3.3 同步契约（1个文件）

| 文件 | 路径 | 说明 |
|------|------|------|
| AmmoDataRoundContract | `api/item/component/AmmoDataRoundContract.java` | AmmoData↔LoadedRound同步契约，含verifyRoundTrip() |

### 3.4 供弹具辅助类型（3个枚举 + 1个记录）

| 文件 | 路径 | 说明 |
|------|------|------|
| RimType | `api/item/enums/RimType.java` | 底缘类型：RIMLESS/RIMMED/SEMI_RIMMED/REBATED/BELTED |
| ChamberState | `api/item/enums/ChamberState.java` | 转轮弹巢每格状态：EMPTY/LOADED/SPENT |
| BeltLinkType | `api/item/enums/BeltLinkType.java` | 弹链链节类型：DISINTEGRATING/NON_DISINTEGRATING |
| CylinderChamber | `api/item/component/CylinderChamber.java` | 转轮弹巢单格弹膛数据 |

### 3.5 供弹具数据体系（8个文件）

| 文件 | 路径 | 说明 |
|------|------|------|
| FeedDeviceData | `api/item/component/FeedDeviceData.java` | 密封接口 + 分派 Codec/StreamCodec |
| BoxMagazineData | `api/item/component/BoxMagazineData.java` | 盒式弹匣（LIFO + 弹簧疲劳 + 供弹口损伤） |
| TubularMagazineData | `api/item/component/TubularMagazineData.java` | 管状弹仓（严格 FIFO） |
| CylinderData | `api/item/component/CylinderData.java` | 转轮弹巢（固定数组 + 对齐索引） |
| BeltData | `api/item/component/BeltData.java` | 弹链（FIFO + 链节类型 + 可对接） |
| StripperClipData | `api/item/component/StripperClipData.java` | 桥夹（固定容量 + 一次性消耗） |
| EnBlocClipData | `api/item/component/EnBlocClipData.java` | 漏夹（固定容量 + 强制弹出） |
| DrumMagazineData | `api/item/component/DrumMagazineData.java` | 弹鼓（大容量 + 发条张力） |

## 4. 修改文件清单

### 4.1 AmmoData（字段修改）

| 变更 | 说明 |
|------|------|
| +cartridgeType: @Nullable Identifier | 新增口径类型字段，位于所有字段首位 |
| CODEC/STREAM_CODEC 更新 | 新增 cartridge_type optionalFieldOf |
| DEFAULT/BLACK_POWDER_DEFAULT 更新 | 新增 tacz:9mm 默认口径 |
| +withCartridgeType() | 新增口径修改方法 |
| +getEffectiveCartridgeType() | 新增口径查询方法 |

### 4.2 GunStateData（字段修改）

| 变更 | 说明 |
|------|------|
| +chamberedRound: @Nullable LoadedRound | 替代"是否已上膛"布尔值概念 |
| canFire() 更新 | 新增 hasChamberedRound() 条件 |
| +getCatastrophicRiskAssessment() | 炸膛风险评估 |
| +withChamberedRound() / withChamberedRoundFired() | 上膛/击发方法 |

### 4.3 GunData（字段修改）

| 变更 | 说明 |
|------|------|
| +chamberedCartridge | 枪膛口径规格 |
| +compatibleFeedDeviceTag | 兼容供弹具型号标签 |
| +isCartridgeCompatible() | 口径兼容性判定 |
| +isFeedDeviceCompatible() | 供弹具兼容性判定 |
| +isFullyCompatible() | 综合兼容性判定 |

### 4.4 其他修改

| 文件 | 变更 |
|------|------|
| AmmoItemBuilder | 新增 cartridgeType 字段和 setCartridgeType() |
| ModDataComponents | 新增 FEED_DEVICE_DATA 组件注册 |
| GunMod.setup() | 新增 CartridgeTypeManager.init() |
| IGun | 新增 6 个方法：getFeedDeviceData/setFeedDeviceData/hasFeedDevice/getChamberedRound/setChamberedRound/hasChamberedRound |
| LoadedRound.fromAmmoData() | 添加同步契约注释 |
| LoadedRound.toAmmoData() | 保留 cartridgeType，添加同步契约注释 |

## 5. CartridgeType 字段对照表

### 修正前（含衍生值 ❌）

| 字段 | 类型 | 问题 |
|------|------|------|
| baseDamage | float | ❌ 伤害由终末弹道公式在命中时实时计算 |
| baseMuzzleVelocity | float | ❌ 初速由弹道公式在开火时实时计算 |
| baseRange | float | ❌ 射程由弹道/风偏/散布综合决定 |
| basePowderWeight | float | ❌ 装药量由 LoadedRound.powderCharge 决定 |

### 修正后（仅物理规格常量 ✅）

| 字段 | 类型 | 说明 |
|------|------|------|
| displayName | String | 口径显示名称 |
| bulletDiameter | float | 弹头直径（mm） — 恒定物理事实 |
| caseLength | float | 弹壳长度（mm） — 恒定物理事实 |
| overallLength | float | 全弹长（mm） — 恒定物理事实 |
| rimDiameter | float | 底缘直径（mm） — 恒定物理事实 ✨新增 |
| rimType | RimType | 底缘类型 — 恒定物理事实 ✨新增 |
| maxSafePressure | float | 最大安全膛压（MPa） — 恒定物理事实 |
| standardBulletMass | float | 标准弹头质量（克） — 恒定参考值 ✨新增 |
| caseCapacity | float | 弹壳容积（cm³） — 恒定物理事实 ✨新增 |
| techLevel | int | 制造所需最低科技阶段 — 游戏设计常量 |

## 6. 衍生值计算公式（P4/P5 实现时的参考）

```
实际初速 = f(cartridge.standardBulletMass, loadedRound.powderCharge, gunData.barrelLength, ...)
实际动能 = 0.5 × 弹头质量 × 初速²
最终伤害 = f(命中区域, 弹头类型, 动能是否足够穿透护甲, ...)
装药过量判定 = loadedRound.powderCharge > cartridge.getMaxSafePowderCharge()
膛压估算 = f(cartridge.caseCapacity, loadedRound.powderCharge, loadedRound.powderChargeDeviation)
炸膛判定 = 膛压估算 > cartridge.maxSafePressure × (1 - gunState.barrelDamageLevel × 0.1)
```

## 7. P1 进入点确认

P1 从"供弹操作原语"开始，而不是直接写状态机：

| 步骤 | 内容 |
|------|------|
| P1-1 | 定义 FeedOperation 接口（stripNextRound/chamberRound/extractFromChamber/ejectCase/insertRound） |
| P1-2 | 7 种供弹具各自实现全套原语（物理差异收敛在各自实现中） |
| P1-3 | GunCycleState 状态机（合法转移表，非法转移被拒绝） |
| P1-4 | 手动循环验证（栓动枪全流程） |
| P1-5 | 卡壳挂钩（每种 MalfunctionType 由对应原语失败触发） |
| P1-6 | 口径校验 + 弹簧疲劳挂钩 |
