# TACZ 工业图标数据驱动映射

## 目的与范围

TACZ 的散装弹药、实体弹匣和工业件是少量注册物品加 NBT 身份的模型：例如所有散装弹药
都是 `tacz:ammo`，所有实体弹匣都是 `tacz:magazine`。本层在**客户端资源重载**时将该身份
映射为一张图标纹理，不重复注册数十种物品，也不把图标选择写死在 Java 中。

它覆盖：

- `tacz:ammo` 的 GUI 槽位图；
- `tacz:magazine` 的实体可拆卸弹匣/弹链箱图；
- `IndustryTaggedItem`：`cartridge_case_blank`、`cartridge_case`、`projectile_blank`、
  `projectile_core`、`press_die`、`gun_component_blank`、`gun_component`、`gun_blueprint`。

无匹配项时，弹药继续使用枪包原有 slot 纹理；弹匣/工业件继续使用原有
`tacz:item/<item-path>` 图，因此错误或缺失的外部映射不会把物品渲染成空白。

此层只影响视觉，**不是数据包配方系统**，不改服务端物品、配方、弹道、库存或 NBT。
玩家不需要运行任何 Python；`tools/generate_industry_content.py` 只是仓库作者/CI 用于同步
内置映射、修复包嵌入资源与覆盖目录。

## 放置位置

任意资源包可增加一个或多个文件：

```text
assets/<你的命名空间>/industry_icons/<任意路径>.json
```

例如第三方枪包 `my_pack`：

```text
assets/my_pack/industry_icons/my_pack_icons.json
assets/my_pack/textures/item/my_pack_68_case.png
```

资源重载（进入世界、`F3+T` 或 TACZ 资源重载）后生效。映射 JSON 可以与纹理来自不同命名空间。

内置默认表：

```text
assets/tacz/industry_icons/default.json
```

作者源（生成器会检查它）：

```text
tools/industry/icon_mapping.json
```

## JSON 格式

```json
{
  "schema_version": 1,
  "entries": [
    {
      "id": "my_pack_68x51_case",
      "item": "tacz:cartridge_case",
      "texture": "my_pack:item/68x51_case",
      "priority": 500,
      "match": {
        "industry_part_kind": "case",
        "cartridge_caliber": "68x51fury"
      },
      "coverage": "exact"
    }
  ]
}
```

必填字段：

| 字段 | 含义 |
| --- | --- |
| `id` | 文件内唯一、稳定的映射 ID；也作为最终平局的确定性排序键。 |
| `item` | 注册物品 ID，例如 `tacz:ammo`、`tacz:magazine`、`tacz:projectile_core`。 |
| `texture` | 不带 `.png` 的纹理 ID，例如 `my_pack:item/68x51_case` 对应 `assets/my_pack/textures/item/68x51_case.png`。 |

可选字段：

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `priority` | `0` | 数值越高越优先。第三方要有意覆盖默认表，请使用高于默认表 `300` 的值，例如 `500`。 |
| `match` | `{}` | 下表的任意非空 selector；全部为 AND 条件。空 `match` 可作为该注册物品的后备图。 |
| `coverage` | `exact` | 作者覆盖审计字段：`exact`、`family`、`placeholder`。运行时忽略它，不影响选择。 |
| `note` | 无 | 作者备注；运行时忽略。 |

### 支持的 selector

| JSON selector | NBT/物品来源 | 示例 |
| --- | --- | --- |
| `ammo_id` | `IAmmo#getAmmoId`，后备为 `AmmoId` | `tacz:556x45` |
| `magazine_family` | `MagazineFamily` | `stanag_556` |
| `magazine_ammo_id` | `MagazineAmmoId` | `tacz:556x45` |
| `magazine_capacity` | `MagazineCapacity`（正整数） | `40` |
| `cartridge_caliber` | `CartridgeCaliber` | `50bmg` |
| `projectile_type` | `ProjectileType` | `fmj`、`he`、`heat` |
| `industry_part_kind` | `IndustryPartKind` | `spent_case`、`component_die`、`furniture_kit` |
| `industry_platform` | `IndustryPlatform` | `default_m4a1` |
| `die_target_kind` | `DieTargetKind` | `barrel`、`projectile` |

`item` 总是精确匹配；`match` 中的每个字段也都必须精确匹配。字段不会把弹匣余弹数、
物品数量等高频变化数据纳入选择/图集缓存键，因此装卸子弹不会造成图标图集不断重建。

## 选择顺序与覆盖

对同一 ItemStack 的多个候选项，客户端按以下顺序选择：

1. `priority` 降序；
2. `match` 中 selector 数量降序（更具体的身份优先）；
3. 映射 ID / 资源路径的稳定字典序。

因此“来自更高优先级资源包”本身**不会**隐式赢过另一条相同 selector 的表项；覆盖者必须明确
提高 `priority`。这避免文件加载顺序变化时图标随机跳变。

## 第三方示例

### 新枪包的实体弹匣

```json
{
  "schema_version": 1,
  "entries": [
    {
      "id": "my_pack_f2000_magazine",
      "item": "tacz:magazine",
      "texture": "my_pack:item/f2000_556_mag",
      "priority": 500,
      "match": {
        "magazine_family": "my_pack_f2000_556"
      }
    }
  ]
}
```

### 已击发弹壳不再复用新壳图

```json
{
  "schema_version": 1,
  "entries": [
    {
      "id": "my_pack_spent_762x39",
      "item": "tacz:cartridge_case",
      "texture": "my_pack:item/casing_762x39_spent",
      "priority": 500,
      "match": {
        "industry_part_kind": "spent_case",
        "cartridge_caliber": "762x39"
      }
    }
  ]
}
```

### 平台专属外装套件

```json
{
  "schema_version": 1,
  "entries": [
    {
      "id": "my_pack_f2000_exterior_kit",
      "item": "tacz:gun_component",
      "texture": "my_pack:item/f2000_exterior_kit",
      "priority": 500,
      "match": {
        "industry_platform": "my_pack_f2000",
        "industry_part_kind": "furniture_kit"
      }
    }
  ]
}
```

## 内置 `tacz_extra` 图标包

修复后的 61 张用户提供图标已嵌入 `assets/tacz_extra/**`，所以默认映射无需额外 ZIP。
若用户将 `TACZ_icons_pack_fixed.zip` 放入资源包目录，其相同命名空间纹理可以照常覆盖内置纹理。

映射与缺图审计的当前状态见：

- [`extras/icon_packs/TACZ_industry_icon_catalog.json`](../extras/icon_packs/TACZ_industry_icon_catalog.json)
- [`INDUSTRY_ICON_COVERAGE.md`](INDUSTRY_ICON_COVERAGE.md)
