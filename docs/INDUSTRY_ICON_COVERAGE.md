# 工业图标覆盖清单（精确身份）

此文件由 `tools/generate_industry_content.py` 生成。它不是按“弹药/弹匣/工业物品”
这种宽泛类别罗列，而是逐个列出当前运行时实际可出现的 `item + NBT selector` 身份。
完整可供程序处理的源数据是 `extras/icon_packs/TACZ_industry_icon_catalog.json`。

## 判定规则

- **exact**：已有该具体身份的图；
- **family**：已有有意复用的同工艺视觉族（例如新鲜黄铜手枪壳）；
- **placeholder**：暂时能画出来，但不能冒充完成品（例如已击发弹壳仍借用新壳图）；
- **supplied_block_model**：已由用户提供的实体方块模型/贴图覆盖；
- **runtime_fallback**：没有映射条目，运行时退回原有 TACZ 图；
- `needs_art = true` 的每一行都是仍需补图的具体身份。

## 汇总

| 类别 | 总身份数 | 已满足 | 仍需补图 | exact | family | placeholder | supplied block model | runtime fallback |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| cartridge_case_die | 24 | 24 | 0 | 24 | 0 | 0 | 0 | 0 |
| cartridge_gauge | 5 | 5 | 0 | 5 | 0 | 0 | 0 | 0 |
| cartridge_projectile_die | 24 | 24 | 0 | 24 | 0 | 0 | 0 | 0 |
| fresh_cartridge_case | 23 | 23 | 0 | 1 | 22 | 0 | 0 | 0 |
| loose_ammo | 24 | 24 | 0 | 24 | 0 | 0 | 0 | 0 |
| physical_magazine | 22 | 22 | 0 | 22 | 0 | 0 | 0 | 0 |
| platform_blueprint | 53 | 53 | 0 | 53 | 0 | 0 | 0 | 0 |
| platform_component | 265 | 265 | 0 | 265 | 0 | 0 | 0 | 0 |
| platform_component_die | 265 | 265 | 0 | 265 | 0 | 0 | 0 | 0 |
| platform_furniture_kit | 53 | 53 | 0 | 53 | 0 | 0 | 0 | 0 |
| projectile_core | 24 | 24 | 0 | 4 | 20 | 0 | 0 | 0 |
| shared_ammunition_intermediate | 4 | 4 | 0 | 4 | 0 | 0 | 0 | 0 |
| shared_gun_intermediate | 6 | 6 | 0 | 6 | 0 | 0 | 0 | 0 |
| spent_cartridge_case | 23 | 23 | 0 | 23 | 0 | 0 | 0 | 0 |
| static_industrial_item | 5 | 5 | 0 | 3 | 0 | 0 | 2 | 0 |
| visible_projectile_intermediate | 5 | 5 | 0 | 5 | 0 | 0 | 0 | 0 |

## 仍缺失的精确视觉身份

下表每一行都可以直接变成 `assets/<namespace>/industry_icons/*.json` 中的一条映射；
`需要的图键` 相同表示一张有意共享的视觉族图可以覆盖多个身份。

| 类别 | 精确身份 | 运行时 selector | 当前状态 | 需要的图键 |
| --- | --- | --- | --- | --- |

## 已提供但尚未绑定的图

这些 PNG 已嵌入运行时 `tacz_extra` 命名空间，但当前默认工业数据没有对应的实际身份。
它们保留给以后新增物理供弹器或第三方映射，未被强行套到不匹配的枪械上。

| 纹理 |
| --- |
| `tacz_extra:item/base_barrel` |
| `tacz_extra:item/base_barrel_blank` |
| `tacz_extra:item/base_billet` |
| `tacz_extra:item/base_blueprint` |
| `tacz_extra:item/base_bolt` |
| `tacz_extra:item/base_bolt_blank` |
| `tacz_extra:item/base_cartridge_gauge` |
| `tacz_extra:item/base_case_blank` |
| `tacz_extra:item/base_case_die` |
| `tacz_extra:item/base_die_blank` |
| `tacz_extra:item/base_frame` |
| `tacz_extra:item/base_furniture_blank` |
| `tacz_extra:item/base_furniture_kit` |
| `tacz_extra:item/base_m_assembly` |
| `tacz_extra:item/base_m_loader` |
| `tacz_extra:item/base_m_salvage` |
| `tacz_extra:item/base_magazine_blank` |
| `tacz_extra:item/base_magazine_pouch` |
| `tacz_extra:item/base_payload_charge` |
| `tacz_extra:item/base_payload_cone` |
| `tacz_extra:item/base_pbody_40mm` |
| `tacz_extra:item/base_pbody_rpg` |
| `tacz_extra:item/base_projectile_blank` |
| `tacz_extra:item/base_projectile_die` |
| `tacz_extra:item/base_receiver` |
| `tacz_extra:item/base_receiver_blank` |
| `tacz_extra:item/base_recoil` |
| `tacz_extra:item/base_recoil_blank` |
| `tacz_extra:item/base_slide` |
| `tacz_extra:item/base_trigger` |
| `tacz_extra:item/base_trigger_blank` |
| `tacz_extra:item/bullet_ap` |
| `tacz_extra:item/bullet_hp` |
| `tacz_extra:item/bullet_lead` |
| `tacz_extra:item/bullet_rimfire` |
| `tacz_extra:item/bullet_tracer` |
| `tacz_extra:item/generic_barrel` |
| `tacz_extra:item/generic_blueprint` |
| `tacz_extra:item/generic_bolt` |
| `tacz_extra:item/generic_component_die` |
| `tacz_extra:item/generic_frame` |
| `tacz_extra:item/generic_furniture_kit` |
| `tacz_extra:item/generic_receiver` |
| `tacz_extra:item/generic_recoil` |
| `tacz_extra:item/generic_slide` |
| `tacz_extra:item/generic_trigger` |
| `tacz_extra:item/mag_awm` |
| `tacz_extra:item/mag_deagle357gold` |
| `tacz_extra:item/mag_deagle50` |
| `tacz_extra:item/mag_eternal50z` |
| `tacz_extra:item/mag_hk416` |
| `tacz_extra:item/mag_lonestar3006` |
| `tacz_extra:item/mag_m134_belt` |
| `tacz_extra:item/mag_m700` |
| `tacz_extra:item/mag_m95` |
| `tacz_extra:item/mag_speedloader22` |
| `tacz_extra:item/mag_springfield1873` |
| `tacz_extra:item/mag_type81` |

## 尚不存在可绑定身份的设计项

- `rpg_motor_housing`：The current RPG route has a warhead body, explosive-charge body, shaped-charge preform, and final HEAT core, but no separate motor-housing ItemStack/NBT stage. Add that real stage before attempting to bind an icon.
- `internal_feed_carriers`：Tube, revolver, double-barrel, and internal-box guns currently store rounds in gun data, not in a physical tacz:magazine ItemStack. Their gun/feed UI needs a separate renderer contract; they are deliberately not mislabeled as missing MagazineFamily icons.
