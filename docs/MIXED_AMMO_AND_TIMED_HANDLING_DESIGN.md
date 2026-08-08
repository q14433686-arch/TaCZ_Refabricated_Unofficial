# 多弹种混装与背包装退弹设计

> **状态：实施中。** 本文记录 2026-08-08 后替换旧“一件实体载具只存一种 AmmoId”约束的设计。只有已经接入的代码与资源才可被称为完成；设计文字本身不是功能。
>
> **计时交互已移除。** 背包/容器内的装弹、退弹与装弹器不再启动逐发倒计时，也没有后台库存事务或对应的配置项。

## 目标与不变量

玩家可以把**同一口径**的不同弹种（例如 FMJ、AP、HP，或 12 gauge 的 shot / slug）装进同一只实体弹匣、漏夹或桥夹。这里的“同一口径”由数据驱动的 `caliber_ammo` / canonical AmmoId 判定，不凭显示名称、枪名或模型猜测。

仍然必须满足：

1. 每一发是独立 `AmmoId`；AP、HP、Slug 不是附件图标、显示名或一张泛用 NBT。
2. 载具的 `MagazineAmmoId` 继续表示**受弹口径/载具规格**，不是“当前整只载具只装这一种弹”。
3. 真正的内容写为有序 `MagazineRounds`：每项是一发的 AmmoId。旧世界只有 `MagazineAmmoCount` 时，首次需要逐发读写时按原 `MagazineAmmoId` 无损展开。
4. 现实供弹顺序采用**后装先打**：最后压入的弹在最上方，最先进入枪；退弹同样先退出顶部那一发。UI 必须显示下一发与成分统计，不能把混装内容压扁成单个名字。
5. 只有同一 canonical calibre 的 profile 能进入同一载具。不同口径、未知 alternate AmmoId、没有有效 AmmoIndex 的项目一律拒绝。
6. 弹匣、桥夹、漏夹、内部仓和膛内弹都必须保留实际下一发的 AmmoId；不能在“桥夹 → 固定仓”或“弹匣 → 膛”时退化回默认弹种。

## 数据模型

```text
AmmoProfileDefinition
  AmmoId                 例如 tacz:556x45_ap
  CanonicalAmmoId        例如 tacz:556x45
  Kind                   fmj | ap | hp | slug | 作者自定义字符串
  弹道修正               伤害、护甲忽略、穿透、初速、弹丸数覆盖

实体载具
  MagazineAmmoId         tacz:556x45（受弹口径）
  MagazineRounds         [tacz:556x45_fmj, tacz:556x45_ap, ...]
                           底 → 顶；列表末端是下一发
  MagazineAmmoCount      旧 HUD/Lua 兼容镜像 = MagazineRounds.size()
```

`AmmoProfileDefinition` 通过独立 `industry/ammo_profiles` 数据同步给客户端；未知 AmmoId 默认只能和自身兼容，不能借“同一枪能打”推断为可混装。第三方作者要显式新增：

```json
// data/<namespace>/industry/ammo_profiles/<alternate_ammo_path>.json
{
  "schema_version": 1,
  "ammo": "<namespace>:<alternate_ammo_path>",
  "caliber_ammo": "<namespace>:<canonical_base_ammo>",
  "kind": "ap",
  "damage_multiplier": 0.92,
  "armor_ignore_multiplier": 1.42,
  "armor_ignore_addend": 0.06,
  "pierce_add": 1,
  "speed_multiplier": 1.05
}
```

该 AmmoId 还必须有真实 AmmoIndex、独立 projectile core/die 和 Cartridge Assembly 输出；profile JSON 单独存在并不会解锁假弹药。

### 首批实际内容范围

首批独立 AmmoId 与制造/弹道 profile 为：

```text
9 mm / .45 ACP / 5.56×45 / 7.62×39 / .308：AP + HP
.50 BMG：AP
12 gauge：Slug
```

每个条目都有独立 AmmoIndex、`industry/ammo_profiles`、真实 Create 加热 Basin 的类型预制坯、保留口径量规的部署器 profile 印刻、独立 projectile die、独立 projectile core、四槽 Cartridge Assembly 输出和服务端 profile。类型预制坯先由“中性弹头毛坯 + AP/HP/Slug 的实体材料”形成，但尚无口径或 AmmoId；只有后续部署器持有的实体口径量规才能写入精确 calibre/AmmoId。这样不同 AP/HP/Slug die 不会以同一套 Create 物理输入竞争不同输出，且不再借 Gunsmith Table 选择弹种。弹壳按真实口径可在同口径 profile 间共用，弹头/模具与最终 AmmoId 不可混用。当前没有把 .50 BMG HP 或其他未经明确内容定义的类型假装成首批支持。

```text
中性弹头毛坯 + 类型实体材料
  → Create 加热 Basin / 机械压实
  → AP / HP / Slug 类型预制坯（尚未定口径）

类型预制坯 + 对应口径量规（部署器持有并保留）
  → 精确 calibre + AmmoId 的 profile blank

profile blank + 弹头模具毛坯
  → Create 部署器：专用弹头模具

profile blank + 专用模具（部署器持有并保留）
  → 精确 AP / HP / Slug 弹头

弹壳 + 精确弹头 + 底火 + 发射药
  → 四槽 Cartridge Assembly：最终独立 AmmoId
```

首批 alternate 弹药暂时复用**同口径**的既有 cartridge display 资源，以避免伪称有一张并不存在的精确 AP/HP/Slug 外观；名称、AmmoId、投射物/已击发壳身份、制造和服务端弹道均独立，精确弹种美术仍应作为后续资源缺口登记。默认包已有的 `ammo_mod_*` 附件仍是附件系统，不能被当作这些独立弹药内容的替代品。

## 服务器弹道与抛壳

开火前服务端先从实际供弹位置读取“这次即将离开的 AmmoId”，再执行原有扣弹。该 ID 同时用于：

- `EntityKineticBullet` 的 `ammoId` 与 profile 修正；
- AP/HP/Slug 的伤害、护甲忽略、穿透、初速、slug 单弹丸等行为；
- `SpentCartridgeService.ejectAfterFiring` 的精确已击发壳身份；
- 弹孔、弹道粒子和后续案例回收。

客户端不能声明自己打的是哪种弹；所有 profile、队列弹出和投射物参数都由服务器决定。

## 背包多槽 GUI：即时装填/退弹

玩家已有的背包/容器界面仍是操作实体载具的真实多槽 GUI，但本轮**删除**了计时、潜行连续任务、后台 tick、`RoundHandlingInstanceId` 和相关配置。没有新增处理台。

```text
光标：实体弹匣 / 桥夹 / 漏夹 / 快装器
右击同 canonical calibre 的散装弹槽：立即转入该弹堆可容纳的数量
右击空槽：立即退出顶部连续且 AmmoId 相同的一段弹药
手持装弹器右击实体载具：立即从玩家背包中首个兼容散装弹堆装入
```

- 只有已经配置的实体弹匣、桥夹、漏夹和快装器走这条路径；不会把未知 legacy 枪强制变成实体供弹。
- 每次来源交互保持来源 `AmmoId` 的真实身份，并以该堆转入顺序追加到 `MagazineRounds` 顶部；要混装时按想要的顺序分别点击 FMJ/AP/HP 等弹堆。
- 退弹只输出顶部连续且身份相同的一段，因此不会把相邻不同 profile 合并、伪装或改写为 canonical 弹种。
- 装弹器只选择玩家背包中首个兼容的真实散装弹堆；它是即时批量工具，不再具有“更快的逐发倒计时”语义。要精确安排混装顺序，直接拿载具点击所选弹堆。
- 口径、AmmoIndex 和 canonical-profile 校验仍在服务端逻辑中执行；失败时不改动来源、输出或载具内容。

## 明确边界

- 本设计不因为 `reload.type = magazine`、枪名、模型、GunIndex class、弹匣井骨骼或命名空间自动给第三方枪混装能力。
- 已审计的桥夹/漏夹仍必须沿用各自真实脚本/动画 feed 点；背包交互只改变它们背包内的物理内容，不伪造装入枪内的动画。
- 长按 R 轮盘仍保持暂缓，见 [`RELOAD_WHEEL_DEFERRED_DESIGN.md`](RELOAD_WHEEL_DEFERRED_DESIGN.md)。未来若恢复，候选必须显示载具下一发与混装统计，不能只显示一个虚假的 AmmoId。
- 默认枪包资源仍不修改；新增 AmmoIndex、profile、制造数据和 GPL Java 逻辑均在独立资源/代码层。

## 验收顺序

1. 有序载具内容与旧世界无损迁移；
2. 背包/容器中即时装入同口径 profile、按顶部连续身份退弹、装弹器首个兼容来源与失败不变更库存；
3. AP、HP、Slug 的独立 AmmoId、独立制造、独立投射物与精确已击发壳；
4. 弹匣、桥夹、漏夹、内部仓和膛内跨 feed 点保持混装顺序；
5. 专用服务器和 Windows 游戏内回归：混装顺序、每发弹道、无复制/吞弹、创造/无限弹边界。

作者/CI 可用 `python3 tools/verify_mixed_ammo_content.py --check` 检查每个 alternate AmmoId 是否确实拥有 AmmoIndex、profile、模具/弹头和四槽装配出口；普通玩家不需要运行 Python。
