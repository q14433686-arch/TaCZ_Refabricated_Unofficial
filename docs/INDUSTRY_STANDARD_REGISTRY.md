# 工业弹药与供弹接口标准注册表

> 这是 TACZ Create Fly 的严格、数据驱动统一层。它借鉴“统一弹药”的目标，
> 但**不**按枪名、GunIndex class、模型、容量或 `reload.type` 自动把枪合并。
>
> 标准资源由服务器加载并同步给客户端；普通玩家不需要运行仓库中的 Python 工具。

## 为什么要分三层

“能用同一种子弹”与“能插同一只弹匣”是两件事。统一层显式分成：

```text
CartridgeStandard
  = 弹壳 / 弹头 / 膛室的尺寸标准
  = 例如 9 mm、5.56×45、7.62×39

FeedInterfaceStandard
  = 弹匣井 / 卡笋 / 供弹唇 / 外部载具机制标准
  = 例如 STANAG、AK、Glock、MP5、FAL

AmmoProfile
  = 每一发的具体弹种与弹道
  = FMJ / AP / HP / Slug / 作者自定义类型
```

因此：

- 内置首批注册了 24 个默认 `CartridgeStandard` 与 32 个默认 `FeedInterfaceStandard`；
- 已经在兼容 sidecar 中**明确使用同一 family / mechanism / native AmmoId** 的 27 条跨包声明被迁移到对应标准；这是复用已有审计事实，不是从枪名、class 或模型猜测出来的；
- 同一个 `CartridgeStandard` 可以有多个独立 AmmoId、独立 AP/HP/Slug 弹头、弹道与已击发壳；
- 同一个 `FeedInterfaceStandard` 可以让已经审计确认互插的多个 receiver 共用实体载具；
- 同口径但接口不同的 Glock、MP5、Uzi、FAL、G3、M14 等不会被自动合并；
- 同接口但 native AmmoId 不同的跨包枪，只有在作者明确提供 canonical-profile 和同一 feed standard 后才会共享。

## 1. CartridgeStandard

路径：

```text
data/<namespace>/industry/cartridge_standards/<standard-path>.json
```

示例：新建一套独立 6.5 mm 标准：

```json
{
  "schema_version": 1,
  "canonical_ammo": "my_pack:65creedmoor",
  "cartridge_caliber": "65creedmoor"
}
```

约束：

1. 一个 `canonical_ammo` 只能由一个标准资源拥有；重复声明会 fail closed；
2. `canonical_ammo` 必须有真实 AmmoIndex；
3. `cartridge_caliber` 是 case/projectile/gauge 的实际 NBT 尺寸身份，不是显示名；
4. 已有的 `tacz:` 标准由内置 24 条默认弹药清单生成；第三方若要复用 5.56×45，应引用 `tacz:556x45`，而不是再声明一个同 canonical ammo 的副本。

`industry/ammo_profiles` 的 `caliber_ammo` 必须解析到一个已有标准。例如第三方 native AmmoId 复用默认 5.56×45：

```json
{
  "schema_version": 1,
  "ammo": "my_pack:556x45_fmj",
  "caliber_ammo": "tacz:556x45",
  "kind": "fmj",
  "damage_multiplier": 1.0,
  "armor_ignore_multiplier": 1.0,
  "armor_ignore_addend": 0.0,
  "pierce_add": 0,
  "pierce_override": 0,
  "speed_multiplier": 1.0,
  "projectile_count_override": 0
}
```

这不是把该 AmmoId 改名：它仍须有自己的 AmmoIndex、制造路线、精确弹头与最终 Cartridge Assembly 输出。

## 2. FeedInterfaceStandard

路径：

```text
data/<namespace>/industry/feed_standards/<standard-path>.json
```

示例：一个经审计的 5.56×45 外部弹匣接口：

```json
{
  "schema_version": 1,
  "mechanism": "detachable_magazine",
  "magazine_family": "my_pack_f2000_556",
  "cartridge_standard": "tacz:556x45",
  "accepted_capacities": [30]
}
```

字段含义：

- `mechanism` 只能为 `detachable_magazine` 或 `belt`；
- `magazine_family` 是稳定物理接口名；一个 mechanism + family 只能有一个标准；
- `cartridge_standard` 是上节的标准资源 ID；
- `accepted_capacities` 是已经审计、可实际制造的载具容量，不是任意 NBT 容量列表。

标准资源不会凭空证明枪可拆卸。它只定义一个已经存在、经过审计的接口。每把枪仍需要自己的 `industry/gun_feed` 声明，并且该声明仍会严格核对当前 GunData 的 AmmoId 和基础容量。

## 3. GunFeed 绑定

可拆卸弹匣或弹链箱的 `gun_feed` 明确引用标准：

```json
{
  "mechanism": "detachable_magazine",
  "magazine_family": "my_pack_f2000_556",
  "feed_standard": "my_pack:f2000_556",
  "magazine_capacity": 30,
  "ammo": "my_pack:556x45_fmj",
  "display_name": "item.my_pack.magazine.f2000"
}
```

运行时必须同时通过：

```text
GunData.ammo == gun_feed.ammo
GunData.ammo_amount == gun_feed.magazine_capacity
feed_standard.mechanism == gun_feed.mechanism
feed_standard.magazine_family == gun_feed.magazine_family
resolve(gun_feed.ammo) 的 CartridgeStandard
  == feed_standard.cartridge_standard
所有实体容量 ∈ accepted_capacities
```

任一条件不成立，该枪的声明被拒绝并保留 legacy 行为。

### 复用已有标准

例如经实物结构和脚本审计确认一把第三方枪使用 STANAG：

```json
{
  "mechanism": "detachable_magazine",
  "magazine_family": "stanag_556",
  "feed_standard": "tacz:stanag_556",
  "magazine_capacity": 30,
  "ammo": "my_pack:556x45_fmj",
  "display_name": "item.my_pack.magazine.stanag"
}
```

还必须为 `my_pack:556x45_fmj` 提供指向 `tacz:556x45` 的 explicit ammo profile。若新枪需要 40 发等未列入 `tacz:stanag_556.accepted_capacities` 的容量，作者必须通过兼容数据包扩展该**标准资源**，并提供真实可制造载具；不能只改枪 JSON 或 NBT 数字。

## 4. 实体制造与旧世界

新制造的标准化外部载具及其量规、壳体、托弹板/弹簧组件会携带：

```text
MagazineFeedStandard
MagazineFamily
MagazineAmmoId（该标准的 canonical ammo）
MagazineCapacity
```

`MagazineRounds` 仍是逐发精确 AmmoId 队列，绝不被标准化层压扁。

旧世界中没有 `MagazineFeedStandard` 的实体弹匣不被删除：它继续按已经显式声明的 `magazine_family + canonical calibre` 参与普通换弹。新标准 tag 用于审计、制造、容量治理和识别冲突标准，**不能**把原本可用的通用实体弹匣排除出换弹候选。

## 5. 边界

- 不因 `reload.type = magazine`、枪名、模型、GunIndex class、命名空间或容量自动绑定标准；
- `stripper_clip`、`speedloader`、`en_bloc_clip` 不进入外部 `FeedInterfaceStandard` 路线；它们仍需精确器件身份及已审计脚本/动画；
- Unidict 风格“pistol/rifle/shotgun 一类共用一种弹”的玩法属于另一个可选规则集，不能作为这里的物理接口证据；
- 所有标准、profile、弹匣内容和开火弹道最终都由服务端验证。
