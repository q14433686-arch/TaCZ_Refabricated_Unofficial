# 工业图标覆盖清单（精确身份）

此文件由 `tools/generate_industry_content.py` 生成。它不是按“弹药/弹匣/工业物品”
这种宽泛类别罗列，而是逐个列出当前运行时实际可出现的 `item + NBT selector` 身份。
完整可供程序处理的源数据是 `extras/icon_packs/TACZ_industry_icon_catalog.json`。

## 判定规则

- **exact**：已有该具体身份的图；
- **family**：已有有意复用的同工艺视觉族（例如新鲜黄铜手枪壳）；
- **placeholder**：暂时能画出来，但不能冒充完成品（例如已击发弹壳仍借用新壳图）；
- **supplied_block_model**：已由用户提供的实体方块模型/贴图覆盖；
- **runtime_fallback**：没有映射条目，运行时退回原有 TACZ 通用图；它保证物件可见，但绝不表示已有该型号的精确视觉；
- `needs_art = true` 的每一行都是仍需补图的具体身份。某些真实工业物件可明确接受通用回退，精确/通用数量仍会分列显示。

## 汇总

| 类别 | 总身份数 | 可渲染（含通用回退） | 仍需补图 | exact | family | placeholder | supplied block model | runtime fallback |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| action_fixture | 32 | 32 | 0 | 0 | 32 | 0 | 0 | 0 |
| carrier_component | 69 | 69 | 0 | 0 | 69 | 0 | 0 | 0 |
| carrier_tooling | 35 | 35 | 0 | 0 | 35 | 0 | 0 | 0 |
| cartridge_case_die | 24 | 24 | 0 | 24 | 0 | 0 | 0 | 0 |
| cartridge_gauge | 24 | 24 | 0 | 5 | 19 | 0 | 0 | 0 |
| cartridge_projectile_die | 24 | 24 | 0 | 24 | 0 | 0 | 0 | 0 |
| cartridge_reverse_gauge | 48 | 48 | 0 | 0 | 48 | 0 | 0 | 0 |
| dossier_archive | 4 | 4 | 0 | 0 | 4 | 0 | 0 | 0 |
| feed_device | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 |
| fresh_cartridge_case | 23 | 23 | 0 | 1 | 22 | 0 | 0 | 0 |
| loose_ammo | 24 | 24 | 0 | 24 | 0 | 0 | 0 | 0 |
| physical_magazine | 34 | 34 | 0 | 22 | 12 | 0 | 0 | 0 |
| platform_acceptance_tool | 40 | 40 | 0 | 0 | 40 | 0 | 0 | 0 |
| platform_blueprint | 53 | 53 | 0 | 53 | 0 | 0 | 0 | 0 |
| platform_component | 265 | 265 | 0 | 265 | 0 | 0 | 0 | 0 |
| platform_component_die | 265 | 265 | 0 | 265 | 0 | 0 | 0 | 0 |
| platform_furniture_kit | 53 | 53 | 0 | 53 | 0 | 0 | 0 | 0 |
| projectile_core | 24 | 24 | 0 | 4 | 20 | 0 | 0 | 0 |
| shared_ammunition_intermediate | 5 | 5 | 0 | 4 | 1 | 0 | 0 | 0 |
| shared_gun_intermediate | 6 | 6 | 0 | 6 | 0 | 0 | 0 | 0 |
| spent_cartridge_case | 23 | 23 | 0 | 23 | 0 | 0 | 0 | 0 |
| static_industrial_item | 5 | 5 | 0 | 3 | 0 | 0 | 2 | 0 |
| surveying_ammunition | 4 | 4 | 0 | 0 | 4 | 0 | 0 | 0 |
| surveying_component | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 |
| surveying_tooling | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 |
| tooling_template_blank | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 0 |
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
| `tacz_extra:item/base_bolt` |
| `tacz_extra:item/base_bolt_blank` |
| `tacz_extra:item/base_case_blank` |
| `tacz_extra:item/base_case_die` |
| `tacz_extra:item/base_frame` |
| `tacz_extra:item/base_furniture_blank` |
| `tacz_extra:item/base_furniture_kit` |
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
| `tacz_extra:item/carbon_dust` |
| `tacz_extra:item/cinnabar_dust` |
| `tacz_extra:item/generic_barrel` |
| `tacz_extra:item/generic_bolt` |
| `tacz_extra:item/generic_component_die` |
| `tacz_extra:item/generic_frame` |
| `tacz_extra:item/generic_furniture_kit` |
| `tacz_extra:item/generic_receiver` |
| `tacz_extra:item/generic_recoil` |
| `tacz_extra:item/generic_slide` |
| `tacz_extra:item/generic_trigger` |
| `tacz_extra:item/high_carbon_steel_ingot` |
| `tacz_extra:item/high_carbon_steel_plate` |
| `tacz_extra:item/industrial_propellant` |
| `tacz_extra:item/mag_awm` |
| `tacz_extra:item/mag_deagle357gold` |
| `tacz_extra:item/mag_deagle50` |
| `tacz_extra:item/mag_eternal50z` |
| `tacz_extra:item/mag_hk416` |
| `tacz_extra:item/mag_lonestar3006` |
| `tacz_extra:item/mag_m134_belt` |
| `tacz_extra:item/mag_m700` |
| `tacz_extra:item/mag_m95` |
| `tacz_extra:item/mag_springfield1873` |
| `tacz_extra:item/mag_type81` |
| `tacz_extra:item/pig_iron_ingot` |
| `tacz_extra:item/primer` |
| `tacz_extra:item/sulfur_dust` |

## 尚不存在可绑定身份的设计项

- `internal_feed_carriers`：Tube, revolver, double-barrel, and internal-box guns currently store rounds in gun data, not in a physical tacz:magazine ItemStack. Their gun/feed UI needs a separate renderer contract; they are deliberately not mislabeled as missing MagazineFamily icons.
