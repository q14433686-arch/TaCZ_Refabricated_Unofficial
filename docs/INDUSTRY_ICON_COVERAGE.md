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
| cartridge_case_die | 24 | 0 | 24 | 0 | 0 | 0 | 0 | 24 |
| cartridge_gauge | 5 | 0 | 5 | 0 | 0 | 0 | 0 | 5 |
| cartridge_projectile_die | 24 | 0 | 24 | 0 | 0 | 0 | 0 | 24 |
| fresh_cartridge_case | 23 | 22 | 1 | 0 | 22 | 1 | 0 | 0 |
| loose_ammo | 24 | 19 | 5 | 19 | 0 | 0 | 0 | 5 |
| physical_magazine | 22 | 15 | 7 | 15 | 0 | 0 | 0 | 7 |
| platform_blueprint | 53 | 0 | 53 | 0 | 0 | 0 | 0 | 53 |
| platform_component | 265 | 0 | 265 | 0 | 0 | 0 | 0 | 265 |
| platform_component_die | 265 | 0 | 265 | 0 | 0 | 0 | 0 | 265 |
| platform_furniture_kit | 53 | 0 | 53 | 0 | 0 | 0 | 0 | 53 |
| projectile_core | 24 | 21 | 3 | 1 | 20 | 1 | 0 | 2 |
| shared_ammunition_intermediate | 4 | 0 | 4 | 0 | 0 | 0 | 0 | 4 |
| shared_gun_intermediate | 6 | 0 | 6 | 0 | 0 | 0 | 0 | 6 |
| spent_cartridge_case | 23 | 0 | 23 | 0 | 0 | 23 | 0 | 0 |
| static_industrial_item | 5 | 2 | 3 | 0 | 0 | 0 | 2 | 3 |
| visible_projectile_intermediate | 5 | 0 | 5 | 0 | 0 | 0 | 0 | 5 |

## 仍缺失的精确视觉身份

下表每一行都可以直接变成 `assets/<namespace>/industry_icons/*.json` 中的一条映射；
`需要的图键` 相同表示一张有意共享的视觉族图可以覆盖多个身份。

| 类别 | 精确身份 | 运行时 selector | 当前状态 | 需要的图键 |
| --- | --- | --- | --- | --- |
| cartridge_case_die | `case_die:12g` | `tacz:press_die ; cartridge_caliber=12g ; industry_part_kind=case_die` | runtime_fallback | `case_die:12g` |
| cartridge_case_die | `case_die:22wmr` | `tacz:press_die ; cartridge_caliber=22wmr ; industry_part_kind=case_die` | runtime_fallback | `case_die:22wmr` |
| cartridge_case_die | `case_die:308` | `tacz:press_die ; cartridge_caliber=308 ; industry_part_kind=case_die` | runtime_fallback | `case_die:308` |
| cartridge_case_die | `case_die:30_06` | `tacz:press_die ; cartridge_caliber=30_06 ; industry_part_kind=case_die` | runtime_fallback | `case_die:30_06` |
| cartridge_case_die | `case_die:338` | `tacz:press_die ; cartridge_caliber=338 ; industry_part_kind=case_die` | runtime_fallback | `case_die:338` |
| cartridge_case_die | `case_die:357mag` | `tacz:press_die ; cartridge_caliber=357mag ; industry_part_kind=case_die` | runtime_fallback | `case_die:357mag` |
| cartridge_case_die | `case_die:40mm` | `tacz:press_die ; cartridge_caliber=40mm ; industry_part_kind=case_die` | runtime_fallback | `case_die:40mm` |
| cartridge_case_die | `case_die:45_70` | `tacz:press_die ; cartridge_caliber=45_70 ; industry_part_kind=case_die` | runtime_fallback | `case_die:45_70` |
| cartridge_case_die | `case_die:45acp` | `tacz:press_die ; cartridge_caliber=45acp ; industry_part_kind=case_die` | runtime_fallback | `case_die:45acp` |
| cartridge_case_die | `case_die:46x30` | `tacz:press_die ; cartridge_caliber=46x30 ; industry_part_kind=case_die` | runtime_fallback | `case_die:46x30` |
| cartridge_case_die | `case_die:500mag` | `tacz:press_die ; cartridge_caliber=500mag ; industry_part_kind=case_die` | runtime_fallback | `case_die:500mag` |
| cartridge_case_die | `case_die:50ae` | `tacz:press_die ; cartridge_caliber=50ae ; industry_part_kind=case_die` | runtime_fallback | `case_die:50ae` |
| cartridge_case_die | `case_die:50bmg` | `tacz:press_die ; cartridge_caliber=50bmg ; industry_part_kind=case_die` | runtime_fallback | `case_die:50bmg` |
| cartridge_case_die | `case_die:545x39` | `tacz:press_die ; cartridge_caliber=545x39 ; industry_part_kind=case_die` | runtime_fallback | `case_die:545x39` |
| cartridge_case_die | `case_die:556x45` | `tacz:press_die ; cartridge_caliber=556x45 ; industry_part_kind=case_die` | runtime_fallback | `case_die:556x45` |
| cartridge_case_die | `case_die:57x28` | `tacz:press_die ; cartridge_caliber=57x28 ; industry_part_kind=case_die` | runtime_fallback | `case_die:57x28` |
| cartridge_case_die | `case_die:58x42` | `tacz:press_die ; cartridge_caliber=58x42 ; industry_part_kind=case_die` | runtime_fallback | `case_die:58x42` |
| cartridge_case_die | `case_die:68x51fury` | `tacz:press_die ; cartridge_caliber=68x51fury ; industry_part_kind=case_die` | runtime_fallback | `case_die:68x51fury` |
| cartridge_case_die | `case_die:762x25` | `tacz:press_die ; cartridge_caliber=762x25 ; industry_part_kind=case_die` | runtime_fallback | `case_die:762x25` |
| cartridge_case_die | `case_die:762x39` | `tacz:press_die ; cartridge_caliber=762x39 ; industry_part_kind=case_die` | runtime_fallback | `case_die:762x39` |
| cartridge_case_die | `case_die:762x54` | `tacz:press_die ; cartridge_caliber=762x54 ; industry_part_kind=case_die` | runtime_fallback | `case_die:762x54` |
| cartridge_case_die | `case_die:792x57` | `tacz:press_die ; cartridge_caliber=792x57 ; industry_part_kind=case_die` | runtime_fallback | `case_die:792x57` |
| cartridge_case_die | `case_die:9mm` | `tacz:press_die ; cartridge_caliber=9mm ; industry_part_kind=case_die` | runtime_fallback | `case_die:9mm` |
| cartridge_case_die | `case_die:rpg_rocket` | `tacz:press_die ; cartridge_caliber=rpg_rocket ; industry_part_kind=case_die` | runtime_fallback | `case_die:rpg_rocket` |
| cartridge_gauge | `cartridge_gauge:46x30` | `tacz:press_die ; cartridge_caliber=46x30 ; industry_part_kind=cartridge_gauge` | runtime_fallback | `cartridge_gauge:46x30` |
| cartridge_gauge | `cartridge_gauge:545x39` | `tacz:press_die ; cartridge_caliber=545x39 ; industry_part_kind=cartridge_gauge` | runtime_fallback | `cartridge_gauge:545x39` |
| cartridge_gauge | `cartridge_gauge:68x51fury` | `tacz:press_die ; cartridge_caliber=68x51fury ; industry_part_kind=cartridge_gauge` | runtime_fallback | `cartridge_gauge:68x51fury` |
| cartridge_gauge | `cartridge_gauge:762x25` | `tacz:press_die ; cartridge_caliber=762x25 ; industry_part_kind=cartridge_gauge` | runtime_fallback | `cartridge_gauge:762x25` |
| cartridge_gauge | `cartridge_gauge:762x54` | `tacz:press_die ; cartridge_caliber=762x54 ; industry_part_kind=cartridge_gauge` | runtime_fallback | `cartridge_gauge:762x54` |
| cartridge_projectile_die | `projectile_die:12g:shot` | `tacz:press_die ; cartridge_caliber=12g ; projectile_type=shot ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:12g:shot` |
| cartridge_projectile_die | `projectile_die:22wmr:fmj` | `tacz:press_die ; cartridge_caliber=22wmr ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:22wmr:fmj` |
| cartridge_projectile_die | `projectile_die:308:fmj` | `tacz:press_die ; cartridge_caliber=308 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:308:fmj` |
| cartridge_projectile_die | `projectile_die:30_06:fmj` | `tacz:press_die ; cartridge_caliber=30_06 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:30_06:fmj` |
| cartridge_projectile_die | `projectile_die:338:fmj` | `tacz:press_die ; cartridge_caliber=338 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:338:fmj` |
| cartridge_projectile_die | `projectile_die:357mag:fmj` | `tacz:press_die ; cartridge_caliber=357mag ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:357mag:fmj` |
| cartridge_projectile_die | `projectile_die:40mm:he` | `tacz:press_die ; cartridge_caliber=40mm ; projectile_type=he ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:40mm:he` |
| cartridge_projectile_die | `projectile_die:45_70:fmj` | `tacz:press_die ; cartridge_caliber=45_70 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:45_70:fmj` |
| cartridge_projectile_die | `projectile_die:45acp:fmj` | `tacz:press_die ; cartridge_caliber=45acp ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:45acp:fmj` |
| cartridge_projectile_die | `projectile_die:46x30:fmj` | `tacz:press_die ; cartridge_caliber=46x30 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:46x30:fmj` |
| cartridge_projectile_die | `projectile_die:500mag:fmj` | `tacz:press_die ; cartridge_caliber=500mag ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:500mag:fmj` |
| cartridge_projectile_die | `projectile_die:50ae:fmj` | `tacz:press_die ; cartridge_caliber=50ae ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:50ae:fmj` |
| cartridge_projectile_die | `projectile_die:50bmg:fmj` | `tacz:press_die ; cartridge_caliber=50bmg ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:50bmg:fmj` |
| cartridge_projectile_die | `projectile_die:545x39:fmj` | `tacz:press_die ; cartridge_caliber=545x39 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:545x39:fmj` |
| cartridge_projectile_die | `projectile_die:556x45:fmj` | `tacz:press_die ; cartridge_caliber=556x45 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:556x45:fmj` |
| cartridge_projectile_die | `projectile_die:57x28:fmj` | `tacz:press_die ; cartridge_caliber=57x28 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:57x28:fmj` |
| cartridge_projectile_die | `projectile_die:58x42:fmj` | `tacz:press_die ; cartridge_caliber=58x42 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:58x42:fmj` |
| cartridge_projectile_die | `projectile_die:68x51fury:fmj` | `tacz:press_die ; cartridge_caliber=68x51fury ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:68x51fury:fmj` |
| cartridge_projectile_die | `projectile_die:762x25:fmj` | `tacz:press_die ; cartridge_caliber=762x25 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:762x25:fmj` |
| cartridge_projectile_die | `projectile_die:762x39:fmj` | `tacz:press_die ; cartridge_caliber=762x39 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:762x39:fmj` |
| cartridge_projectile_die | `projectile_die:762x54:fmj` | `tacz:press_die ; cartridge_caliber=762x54 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:762x54:fmj` |
| cartridge_projectile_die | `projectile_die:792x57:fmj` | `tacz:press_die ; cartridge_caliber=792x57 ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:792x57:fmj` |
| cartridge_projectile_die | `projectile_die:9mm:fmj` | `tacz:press_die ; cartridge_caliber=9mm ; projectile_type=fmj ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:9mm:fmj` |
| cartridge_projectile_die | `projectile_die:rpg_rocket:heat` | `tacz:press_die ; cartridge_caliber=rpg_rocket ; projectile_type=heat ; industry_part_kind=projectile_die` | runtime_fallback | `projectile_die:rpg_rocket:heat` |
| fresh_cartridge_case | `case:40mm` | `tacz:cartridge_case ; cartridge_caliber=40mm ; industry_part_kind=case` | placeholder | `fresh_case:40mm` |
| loose_ammo | `ammo:tacz:500mag` | `tacz:ammo ; ammo_id=tacz:500mag` | runtime_fallback | `ammo:500mag` |
| loose_ammo | `ammo:tacz:545x39` | `tacz:ammo ; ammo_id=tacz:545x39` | runtime_fallback | `ammo:545x39` |
| loose_ammo | `ammo:tacz:57x28` | `tacz:ammo ; ammo_id=tacz:57x28` | runtime_fallback | `ammo:57x28` |
| loose_ammo | `ammo:tacz:68x51fury` | `tacz:ammo ; ammo_id=tacz:68x51fury` | runtime_fallback | `ammo:68x51fury` |
| loose_ammo | `ammo:tacz:792x57` | `tacz:ammo ; ammo_id=tacz:792x57` | runtime_fallback | `ammo:792x57` |
| physical_magazine | `magazine:evolys_308_belt:75:tacz:308` | `tacz:magazine ; magazine_family=evolys_308_belt ; magazine_ammo_id=tacz:308 ; magazine_capacity=75` | runtime_fallback | `magazine:evolys_308_belt:75:tacz:308` |
| physical_magazine | `magazine:fal_308:20:tacz:308` | `tacz:magazine ; magazine_family=fal_308 ; magazine_ammo_id=tacz:308 ; magazine_capacity=20` | runtime_fallback | `magazine:fal_308:20:tacz:308` |
| physical_magazine | `magazine:g36_556:30:tacz:556x45` | `tacz:magazine ; magazine_family=g36_556 ; magazine_ammo_id=tacz:556x45 ; magazine_capacity=30` | runtime_fallback | `magazine:g36_556:30:tacz:556x45` |
| physical_magazine | `magazine:m14_308:10:tacz:308` | `tacz:magazine ; magazine_family=m14_308 ; magazine_ammo_id=tacz:308 ; magazine_capacity=10` | runtime_fallback | `magazine:m14_308:10:tacz:308` |
| physical_magazine | `magazine:m9_9x19:17:tacz:9mm` | `tacz:magazine ; magazine_family=m9_9x19 ; magazine_ammo_id=tacz:9mm ; magazine_capacity=17` | runtime_fallback | `magazine:m9_9x19:17:tacz:9mm` |
| physical_magazine | `magazine:mk23_45acp:12:tacz:45acp` | `tacz:magazine ; magazine_family=mk23_45acp ; magazine_ammo_id=tacz:45acp ; magazine_capacity=12` | runtime_fallback | `magazine:mk23_45acp:12:tacz:45acp` |
| physical_magazine | `magazine:mp5_9x19:30:tacz:9mm` | `tacz:magazine ; magazine_family=mp5_9x19 ; magazine_ammo_id=tacz:9mm ; magazine_capacity=30` | runtime_fallback | `magazine:mp5_9x19:30:tacz:9mm` |
| platform_blueprint | `blueprint:ak` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=ak` | runtime_fallback | `blueprint:ak` |
| platform_blueprint | `blueprint:ar` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=ar` | runtime_fallback | `blueprint:ar` |
| platform_blueprint | `blueprint:default_aa12` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_aa12` | runtime_fallback | `blueprint:default_aa12` |
| platform_blueprint | `blueprint:default_ai_awp` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_ai_awp` | runtime_fallback | `blueprint:default_ai_awp` |
| platform_blueprint | `blueprint:default_aug` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_aug` | runtime_fallback | `blueprint:default_aug` |
| platform_blueprint | `blueprint:default_b93r` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_b93r` | runtime_fallback | `blueprint:default_b93r` |
| platform_blueprint | `blueprint:default_cz75` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_cz75` | runtime_fallback | `blueprint:default_cz75` |
| platform_blueprint | `blueprint:default_db_long` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_db_long` | runtime_fallback | `blueprint:default_db_long` |
| platform_blueprint | `blueprint:default_db_short` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_db_short` | runtime_fallback | `blueprint:default_db_short` |
| platform_blueprint | `blueprint:default_deagle` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_deagle` | runtime_fallback | `blueprint:default_deagle` |
| platform_blueprint | `blueprint:default_deagle_golden` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_deagle_golden` | runtime_fallback | `blueprint:default_deagle_golden` |
| platform_blueprint | `blueprint:default_fn_evolys` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_fn_evolys` | runtime_fallback | `blueprint:default_fn_evolys` |
| platform_blueprint | `blueprint:default_g36k` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_g36k` | runtime_fallback | `blueprint:default_g36k` |
| platform_blueprint | `blueprint:default_hk416d` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_hk416d` | runtime_fallback | `blueprint:default_hk416d` |
| platform_blueprint | `blueprint:default_hk_g3` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_hk_g3` | runtime_fallback | `blueprint:default_hk_g3` |
| platform_blueprint | `blueprint:default_hk_mk23` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_hk_mk23` | runtime_fallback | `blueprint:default_hk_mk23` |
| platform_blueprint | `blueprint:default_kar98` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_kar98` | runtime_fallback | `blueprint:default_kar98` |
| platform_blueprint | `blueprint:default_lonetrail` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_lonetrail` | runtime_fallback | `blueprint:default_lonetrail` |
| platform_blueprint | `blueprint:default_m1014` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m1014` | runtime_fallback | `blueprint:default_m1014` |
| platform_blueprint | `blueprint:default_m107` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m107` | runtime_fallback | `blueprint:default_m107` |
| platform_blueprint | `blueprint:default_m16a1` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m16a1` | runtime_fallback | `blueprint:default_m16a1` |
| platform_blueprint | `blueprint:default_m16a4` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m16a4` | runtime_fallback | `blueprint:default_m16a4` |
| platform_blueprint | `blueprint:default_m249` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m249` | runtime_fallback | `blueprint:default_m249` |
| platform_blueprint | `blueprint:default_m320` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m320` | runtime_fallback | `blueprint:default_m320` |
| platform_blueprint | `blueprint:default_m700` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m700` | runtime_fallback | `blueprint:default_m700` |
| platform_blueprint | `blueprint:default_m870` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m870` | runtime_fallback | `blueprint:default_m870` |
| platform_blueprint | `blueprint:default_m95` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_m95` | runtime_fallback | `blueprint:default_m95` |
| platform_blueprint | `blueprint:default_minigun` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_minigun` | runtime_fallback | `blueprint:default_minigun` |
| platform_blueprint | `blueprint:default_mk14` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_mk14` | runtime_fallback | `blueprint:default_mk14` |
| platform_blueprint | `blueprint:default_p320` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_p320` | runtime_fallback | `blueprint:default_p320` |
| platform_blueprint | `blueprint:default_qbz_191` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_qbz_191` | runtime_fallback | `blueprint:default_qbz_191` |
| platform_blueprint | `blueprint:default_qbz_95` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_qbz_95` | runtime_fallback | `blueprint:default_qbz_95` |
| platform_blueprint | `blueprint:default_rhino357` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_rhino357` | runtime_fallback | `blueprint:default_rhino357` |
| platform_blueprint | `blueprint:default_rpg7` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_rpg7` | runtime_fallback | `blueprint:default_rpg7` |
| platform_blueprint | `blueprint:default_rpk` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_rpk` | runtime_fallback | `blueprint:default_rpk` |
| platform_blueprint | `blueprint:default_scar_h` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_scar_h` | runtime_fallback | `blueprint:default_scar_h` |
| platform_blueprint | `blueprint:default_scar_l` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_scar_l` | runtime_fallback | `blueprint:default_scar_l` |
| platform_blueprint | `blueprint:default_sks_tactical` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_sks_tactical` | runtime_fallback | `blueprint:default_sks_tactical` |
| platform_blueprint | `blueprint:default_spas_12` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_spas_12` | runtime_fallback | `blueprint:default_spas_12` |
| platform_blueprint | `blueprint:default_spr15hb` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_spr15hb` | runtime_fallback | `blueprint:default_spr15hb` |
| platform_blueprint | `blueprint:default_springfield1873` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_springfield1873` | runtime_fallback | `blueprint:default_springfield1873` |
| platform_blueprint | `blueprint:default_taurus500` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_taurus500` | runtime_fallback | `blueprint:default_taurus500` |
| platform_blueprint | `blueprint:default_timeless50` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_timeless50` | runtime_fallback | `blueprint:default_timeless50` |
| platform_blueprint | `blueprint:default_type_81` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_type_81` | runtime_fallback | `blueprint:default_type_81` |
| platform_blueprint | `blueprint:default_uzi` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_uzi` | runtime_fallback | `blueprint:default_uzi` |
| platform_blueprint | `blueprint:default_vector45` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=default_vector45` | runtime_fallback | `blueprint:default_vector45` |
| platform_blueprint | `blueprint:fal` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=fal` | runtime_fallback | `blueprint:fal` |
| platform_blueprint | `blueprint:glock` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=glock` | runtime_fallback | `blueprint:glock` |
| platform_blueprint | `blueprint:m1911` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=m1911` | runtime_fallback | `blueprint:m1911` |
| platform_blueprint | `blueprint:m9` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=m9` | runtime_fallback | `blueprint:m9` |
| platform_blueprint | `blueprint:mp5` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=mp5` | runtime_fallback | `blueprint:mp5` |
| platform_blueprint | `blueprint:p90` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=p90` | runtime_fallback | `blueprint:p90` |
| platform_blueprint | `blueprint:ump` | `tacz:gun_blueprint ; industry_part_kind=blueprint ; industry_platform=ump` | runtime_fallback | `blueprint:ump` |
| platform_component | `component:ak:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=ak` | runtime_fallback | `component:ak:barrel` |
| platform_component | `component:ak:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=ak` | runtime_fallback | `component:ak:bolt` |
| platform_component | `component:ak:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=ak` | runtime_fallback | `component:ak:receiver` |
| platform_component | `component:ak:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=ak` | runtime_fallback | `component:ak:recoil` |
| platform_component | `component:ak:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=ak` | runtime_fallback | `component:ak:trigger` |
| platform_component | `component:ar:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=ar` | runtime_fallback | `component:ar:barrel` |
| platform_component | `component:ar:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=ar` | runtime_fallback | `component:ar:bolt` |
| platform_component | `component:ar:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=ar` | runtime_fallback | `component:ar:receiver` |
| platform_component | `component:ar:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=ar` | runtime_fallback | `component:ar:recoil` |
| platform_component | `component:ar:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=ar` | runtime_fallback | `component:ar:trigger` |
| platform_component | `component:default_aa12:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_aa12` | runtime_fallback | `component:default_aa12:barrel` |
| platform_component | `component:default_aa12:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_aa12` | runtime_fallback | `component:default_aa12:bolt` |
| platform_component | `component:default_aa12:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_aa12` | runtime_fallback | `component:default_aa12:receiver` |
| platform_component | `component:default_aa12:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_aa12` | runtime_fallback | `component:default_aa12:recoil` |
| platform_component | `component:default_aa12:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_aa12` | runtime_fallback | `component:default_aa12:trigger` |
| platform_component | `component:default_ai_awp:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_ai_awp` | runtime_fallback | `component:default_ai_awp:barrel` |
| platform_component | `component:default_ai_awp:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_ai_awp` | runtime_fallback | `component:default_ai_awp:bolt` |
| platform_component | `component:default_ai_awp:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_ai_awp` | runtime_fallback | `component:default_ai_awp:receiver` |
| platform_component | `component:default_ai_awp:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_ai_awp` | runtime_fallback | `component:default_ai_awp:recoil` |
| platform_component | `component:default_ai_awp:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_ai_awp` | runtime_fallback | `component:default_ai_awp:trigger` |
| platform_component | `component:default_aug:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_aug` | runtime_fallback | `component:default_aug:barrel` |
| platform_component | `component:default_aug:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_aug` | runtime_fallback | `component:default_aug:bolt` |
| platform_component | `component:default_aug:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_aug` | runtime_fallback | `component:default_aug:receiver` |
| platform_component | `component:default_aug:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_aug` | runtime_fallback | `component:default_aug:recoil` |
| platform_component | `component:default_aug:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_aug` | runtime_fallback | `component:default_aug:trigger` |
| platform_component | `component:default_b93r:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_b93r` | runtime_fallback | `component:default_b93r:barrel` |
| platform_component | `component:default_b93r:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_b93r` | runtime_fallback | `component:default_b93r:frame` |
| platform_component | `component:default_b93r:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_b93r` | runtime_fallback | `component:default_b93r:recoil` |
| platform_component | `component:default_b93r:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_b93r` | runtime_fallback | `component:default_b93r:slide` |
| platform_component | `component:default_b93r:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_b93r` | runtime_fallback | `component:default_b93r:trigger` |
| platform_component | `component:default_cz75:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_cz75` | runtime_fallback | `component:default_cz75:barrel` |
| platform_component | `component:default_cz75:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_cz75` | runtime_fallback | `component:default_cz75:frame` |
| platform_component | `component:default_cz75:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_cz75` | runtime_fallback | `component:default_cz75:recoil` |
| platform_component | `component:default_cz75:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_cz75` | runtime_fallback | `component:default_cz75:slide` |
| platform_component | `component:default_cz75:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_cz75` | runtime_fallback | `component:default_cz75:trigger` |
| platform_component | `component:default_db_long:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_db_long` | runtime_fallback | `component:default_db_long:barrel` |
| platform_component | `component:default_db_long:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_db_long` | runtime_fallback | `component:default_db_long:bolt` |
| platform_component | `component:default_db_long:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_db_long` | runtime_fallback | `component:default_db_long:receiver` |
| platform_component | `component:default_db_long:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_db_long` | runtime_fallback | `component:default_db_long:recoil` |
| platform_component | `component:default_db_long:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_db_long` | runtime_fallback | `component:default_db_long:trigger` |
| platform_component | `component:default_db_short:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_db_short` | runtime_fallback | `component:default_db_short:barrel` |
| platform_component | `component:default_db_short:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_db_short` | runtime_fallback | `component:default_db_short:bolt` |
| platform_component | `component:default_db_short:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_db_short` | runtime_fallback | `component:default_db_short:receiver` |
| platform_component | `component:default_db_short:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_db_short` | runtime_fallback | `component:default_db_short:recoil` |
| platform_component | `component:default_db_short:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_db_short` | runtime_fallback | `component:default_db_short:trigger` |
| platform_component | `component:default_deagle:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_deagle` | runtime_fallback | `component:default_deagle:barrel` |
| platform_component | `component:default_deagle:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_deagle` | runtime_fallback | `component:default_deagle:frame` |
| platform_component | `component:default_deagle:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_deagle` | runtime_fallback | `component:default_deagle:recoil` |
| platform_component | `component:default_deagle:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_deagle` | runtime_fallback | `component:default_deagle:slide` |
| platform_component | `component:default_deagle:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_deagle` | runtime_fallback | `component:default_deagle:trigger` |
| platform_component | `component:default_deagle_golden:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_deagle_golden` | runtime_fallback | `component:default_deagle_golden:barrel` |
| platform_component | `component:default_deagle_golden:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_deagle_golden` | runtime_fallback | `component:default_deagle_golden:frame` |
| platform_component | `component:default_deagle_golden:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_deagle_golden` | runtime_fallback | `component:default_deagle_golden:recoil` |
| platform_component | `component:default_deagle_golden:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_deagle_golden` | runtime_fallback | `component:default_deagle_golden:slide` |
| platform_component | `component:default_deagle_golden:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_deagle_golden` | runtime_fallback | `component:default_deagle_golden:trigger` |
| platform_component | `component:default_fn_evolys:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_fn_evolys` | runtime_fallback | `component:default_fn_evolys:barrel` |
| platform_component | `component:default_fn_evolys:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_fn_evolys` | runtime_fallback | `component:default_fn_evolys:bolt` |
| platform_component | `component:default_fn_evolys:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_fn_evolys` | runtime_fallback | `component:default_fn_evolys:receiver` |
| platform_component | `component:default_fn_evolys:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_fn_evolys` | runtime_fallback | `component:default_fn_evolys:recoil` |
| platform_component | `component:default_fn_evolys:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_fn_evolys` | runtime_fallback | `component:default_fn_evolys:trigger` |
| platform_component | `component:default_g36k:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_g36k` | runtime_fallback | `component:default_g36k:barrel` |
| platform_component | `component:default_g36k:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_g36k` | runtime_fallback | `component:default_g36k:bolt` |
| platform_component | `component:default_g36k:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_g36k` | runtime_fallback | `component:default_g36k:receiver` |
| platform_component | `component:default_g36k:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_g36k` | runtime_fallback | `component:default_g36k:recoil` |
| platform_component | `component:default_g36k:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_g36k` | runtime_fallback | `component:default_g36k:trigger` |
| platform_component | `component:default_hk416d:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_hk416d` | runtime_fallback | `component:default_hk416d:barrel` |
| platform_component | `component:default_hk416d:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_hk416d` | runtime_fallback | `component:default_hk416d:bolt` |
| platform_component | `component:default_hk416d:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_hk416d` | runtime_fallback | `component:default_hk416d:receiver` |
| platform_component | `component:default_hk416d:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_hk416d` | runtime_fallback | `component:default_hk416d:recoil` |
| platform_component | `component:default_hk416d:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_hk416d` | runtime_fallback | `component:default_hk416d:trigger` |
| platform_component | `component:default_hk_g3:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_hk_g3` | runtime_fallback | `component:default_hk_g3:barrel` |
| platform_component | `component:default_hk_g3:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_hk_g3` | runtime_fallback | `component:default_hk_g3:bolt` |
| platform_component | `component:default_hk_g3:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_hk_g3` | runtime_fallback | `component:default_hk_g3:receiver` |
| platform_component | `component:default_hk_g3:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_hk_g3` | runtime_fallback | `component:default_hk_g3:recoil` |
| platform_component | `component:default_hk_g3:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_hk_g3` | runtime_fallback | `component:default_hk_g3:trigger` |
| platform_component | `component:default_hk_mk23:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_hk_mk23` | runtime_fallback | `component:default_hk_mk23:barrel` |
| platform_component | `component:default_hk_mk23:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_hk_mk23` | runtime_fallback | `component:default_hk_mk23:frame` |
| platform_component | `component:default_hk_mk23:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_hk_mk23` | runtime_fallback | `component:default_hk_mk23:recoil` |
| platform_component | `component:default_hk_mk23:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_hk_mk23` | runtime_fallback | `component:default_hk_mk23:slide` |
| platform_component | `component:default_hk_mk23:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_hk_mk23` | runtime_fallback | `component:default_hk_mk23:trigger` |
| platform_component | `component:default_kar98:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_kar98` | runtime_fallback | `component:default_kar98:barrel` |
| platform_component | `component:default_kar98:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_kar98` | runtime_fallback | `component:default_kar98:bolt` |
| platform_component | `component:default_kar98:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_kar98` | runtime_fallback | `component:default_kar98:receiver` |
| platform_component | `component:default_kar98:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_kar98` | runtime_fallback | `component:default_kar98:recoil` |
| platform_component | `component:default_kar98:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_kar98` | runtime_fallback | `component:default_kar98:trigger` |
| platform_component | `component:default_lonetrail:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_lonetrail` | runtime_fallback | `component:default_lonetrail:barrel` |
| platform_component | `component:default_lonetrail:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_lonetrail` | runtime_fallback | `component:default_lonetrail:frame` |
| platform_component | `component:default_lonetrail:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_lonetrail` | runtime_fallback | `component:default_lonetrail:recoil` |
| platform_component | `component:default_lonetrail:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_lonetrail` | runtime_fallback | `component:default_lonetrail:slide` |
| platform_component | `component:default_lonetrail:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_lonetrail` | runtime_fallback | `component:default_lonetrail:trigger` |
| platform_component | `component:default_m1014:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m1014` | runtime_fallback | `component:default_m1014:barrel` |
| platform_component | `component:default_m1014:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m1014` | runtime_fallback | `component:default_m1014:bolt` |
| platform_component | `component:default_m1014:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m1014` | runtime_fallback | `component:default_m1014:receiver` |
| platform_component | `component:default_m1014:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m1014` | runtime_fallback | `component:default_m1014:recoil` |
| platform_component | `component:default_m1014:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m1014` | runtime_fallback | `component:default_m1014:trigger` |
| platform_component | `component:default_m107:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m107` | runtime_fallback | `component:default_m107:barrel` |
| platform_component | `component:default_m107:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m107` | runtime_fallback | `component:default_m107:bolt` |
| platform_component | `component:default_m107:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m107` | runtime_fallback | `component:default_m107:receiver` |
| platform_component | `component:default_m107:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m107` | runtime_fallback | `component:default_m107:recoil` |
| platform_component | `component:default_m107:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m107` | runtime_fallback | `component:default_m107:trigger` |
| platform_component | `component:default_m16a1:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m16a1` | runtime_fallback | `component:default_m16a1:barrel` |
| platform_component | `component:default_m16a1:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m16a1` | runtime_fallback | `component:default_m16a1:bolt` |
| platform_component | `component:default_m16a1:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m16a1` | runtime_fallback | `component:default_m16a1:receiver` |
| platform_component | `component:default_m16a1:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m16a1` | runtime_fallback | `component:default_m16a1:recoil` |
| platform_component | `component:default_m16a1:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m16a1` | runtime_fallback | `component:default_m16a1:trigger` |
| platform_component | `component:default_m16a4:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m16a4` | runtime_fallback | `component:default_m16a4:barrel` |
| platform_component | `component:default_m16a4:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m16a4` | runtime_fallback | `component:default_m16a4:bolt` |
| platform_component | `component:default_m16a4:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m16a4` | runtime_fallback | `component:default_m16a4:receiver` |
| platform_component | `component:default_m16a4:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m16a4` | runtime_fallback | `component:default_m16a4:recoil` |
| platform_component | `component:default_m16a4:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m16a4` | runtime_fallback | `component:default_m16a4:trigger` |
| platform_component | `component:default_m249:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m249` | runtime_fallback | `component:default_m249:barrel` |
| platform_component | `component:default_m249:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m249` | runtime_fallback | `component:default_m249:bolt` |
| platform_component | `component:default_m249:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m249` | runtime_fallback | `component:default_m249:receiver` |
| platform_component | `component:default_m249:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m249` | runtime_fallback | `component:default_m249:recoil` |
| platform_component | `component:default_m249:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m249` | runtime_fallback | `component:default_m249:trigger` |
| platform_component | `component:default_m320:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m320` | runtime_fallback | `component:default_m320:barrel` |
| platform_component | `component:default_m320:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m320` | runtime_fallback | `component:default_m320:bolt` |
| platform_component | `component:default_m320:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m320` | runtime_fallback | `component:default_m320:receiver` |
| platform_component | `component:default_m320:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m320` | runtime_fallback | `component:default_m320:recoil` |
| platform_component | `component:default_m320:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m320` | runtime_fallback | `component:default_m320:trigger` |
| platform_component | `component:default_m700:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m700` | runtime_fallback | `component:default_m700:barrel` |
| platform_component | `component:default_m700:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m700` | runtime_fallback | `component:default_m700:bolt` |
| platform_component | `component:default_m700:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m700` | runtime_fallback | `component:default_m700:receiver` |
| platform_component | `component:default_m700:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m700` | runtime_fallback | `component:default_m700:recoil` |
| platform_component | `component:default_m700:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m700` | runtime_fallback | `component:default_m700:trigger` |
| platform_component | `component:default_m870:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m870` | runtime_fallback | `component:default_m870:barrel` |
| platform_component | `component:default_m870:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m870` | runtime_fallback | `component:default_m870:bolt` |
| platform_component | `component:default_m870:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m870` | runtime_fallback | `component:default_m870:receiver` |
| platform_component | `component:default_m870:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m870` | runtime_fallback | `component:default_m870:recoil` |
| platform_component | `component:default_m870:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m870` | runtime_fallback | `component:default_m870:trigger` |
| platform_component | `component:default_m95:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_m95` | runtime_fallback | `component:default_m95:barrel` |
| platform_component | `component:default_m95:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_m95` | runtime_fallback | `component:default_m95:bolt` |
| platform_component | `component:default_m95:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_m95` | runtime_fallback | `component:default_m95:receiver` |
| platform_component | `component:default_m95:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_m95` | runtime_fallback | `component:default_m95:recoil` |
| platform_component | `component:default_m95:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_m95` | runtime_fallback | `component:default_m95:trigger` |
| platform_component | `component:default_minigun:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_minigun` | runtime_fallback | `component:default_minigun:barrel` |
| platform_component | `component:default_minigun:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_minigun` | runtime_fallback | `component:default_minigun:bolt` |
| platform_component | `component:default_minigun:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_minigun` | runtime_fallback | `component:default_minigun:receiver` |
| platform_component | `component:default_minigun:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_minigun` | runtime_fallback | `component:default_minigun:recoil` |
| platform_component | `component:default_minigun:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_minigun` | runtime_fallback | `component:default_minigun:trigger` |
| platform_component | `component:default_mk14:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_mk14` | runtime_fallback | `component:default_mk14:barrel` |
| platform_component | `component:default_mk14:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_mk14` | runtime_fallback | `component:default_mk14:bolt` |
| platform_component | `component:default_mk14:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_mk14` | runtime_fallback | `component:default_mk14:receiver` |
| platform_component | `component:default_mk14:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_mk14` | runtime_fallback | `component:default_mk14:recoil` |
| platform_component | `component:default_mk14:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_mk14` | runtime_fallback | `component:default_mk14:trigger` |
| platform_component | `component:default_p320:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_p320` | runtime_fallback | `component:default_p320:barrel` |
| platform_component | `component:default_p320:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_p320` | runtime_fallback | `component:default_p320:frame` |
| platform_component | `component:default_p320:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_p320` | runtime_fallback | `component:default_p320:recoil` |
| platform_component | `component:default_p320:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_p320` | runtime_fallback | `component:default_p320:slide` |
| platform_component | `component:default_p320:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_p320` | runtime_fallback | `component:default_p320:trigger` |
| platform_component | `component:default_qbz_191:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_qbz_191` | runtime_fallback | `component:default_qbz_191:barrel` |
| platform_component | `component:default_qbz_191:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_qbz_191` | runtime_fallback | `component:default_qbz_191:bolt` |
| platform_component | `component:default_qbz_191:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_qbz_191` | runtime_fallback | `component:default_qbz_191:receiver` |
| platform_component | `component:default_qbz_191:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_qbz_191` | runtime_fallback | `component:default_qbz_191:recoil` |
| platform_component | `component:default_qbz_191:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_qbz_191` | runtime_fallback | `component:default_qbz_191:trigger` |
| platform_component | `component:default_qbz_95:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_qbz_95` | runtime_fallback | `component:default_qbz_95:barrel` |
| platform_component | `component:default_qbz_95:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_qbz_95` | runtime_fallback | `component:default_qbz_95:bolt` |
| platform_component | `component:default_qbz_95:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_qbz_95` | runtime_fallback | `component:default_qbz_95:receiver` |
| platform_component | `component:default_qbz_95:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_qbz_95` | runtime_fallback | `component:default_qbz_95:recoil` |
| platform_component | `component:default_qbz_95:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_qbz_95` | runtime_fallback | `component:default_qbz_95:trigger` |
| platform_component | `component:default_rhino357:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_rhino357` | runtime_fallback | `component:default_rhino357:barrel` |
| platform_component | `component:default_rhino357:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_rhino357` | runtime_fallback | `component:default_rhino357:frame` |
| platform_component | `component:default_rhino357:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_rhino357` | runtime_fallback | `component:default_rhino357:recoil` |
| platform_component | `component:default_rhino357:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_rhino357` | runtime_fallback | `component:default_rhino357:slide` |
| platform_component | `component:default_rhino357:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_rhino357` | runtime_fallback | `component:default_rhino357:trigger` |
| platform_component | `component:default_rpg7:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_rpg7` | runtime_fallback | `component:default_rpg7:barrel` |
| platform_component | `component:default_rpg7:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_rpg7` | runtime_fallback | `component:default_rpg7:bolt` |
| platform_component | `component:default_rpg7:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_rpg7` | runtime_fallback | `component:default_rpg7:receiver` |
| platform_component | `component:default_rpg7:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_rpg7` | runtime_fallback | `component:default_rpg7:recoil` |
| platform_component | `component:default_rpg7:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_rpg7` | runtime_fallback | `component:default_rpg7:trigger` |
| platform_component | `component:default_rpk:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_rpk` | runtime_fallback | `component:default_rpk:barrel` |
| platform_component | `component:default_rpk:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_rpk` | runtime_fallback | `component:default_rpk:bolt` |
| platform_component | `component:default_rpk:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_rpk` | runtime_fallback | `component:default_rpk:receiver` |
| platform_component | `component:default_rpk:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_rpk` | runtime_fallback | `component:default_rpk:recoil` |
| platform_component | `component:default_rpk:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_rpk` | runtime_fallback | `component:default_rpk:trigger` |
| platform_component | `component:default_scar_h:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_scar_h` | runtime_fallback | `component:default_scar_h:barrel` |
| platform_component | `component:default_scar_h:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_scar_h` | runtime_fallback | `component:default_scar_h:bolt` |
| platform_component | `component:default_scar_h:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_scar_h` | runtime_fallback | `component:default_scar_h:receiver` |
| platform_component | `component:default_scar_h:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_scar_h` | runtime_fallback | `component:default_scar_h:recoil` |
| platform_component | `component:default_scar_h:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_scar_h` | runtime_fallback | `component:default_scar_h:trigger` |
| platform_component | `component:default_scar_l:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_scar_l` | runtime_fallback | `component:default_scar_l:barrel` |
| platform_component | `component:default_scar_l:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_scar_l` | runtime_fallback | `component:default_scar_l:bolt` |
| platform_component | `component:default_scar_l:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_scar_l` | runtime_fallback | `component:default_scar_l:receiver` |
| platform_component | `component:default_scar_l:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_scar_l` | runtime_fallback | `component:default_scar_l:recoil` |
| platform_component | `component:default_scar_l:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_scar_l` | runtime_fallback | `component:default_scar_l:trigger` |
| platform_component | `component:default_sks_tactical:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_sks_tactical` | runtime_fallback | `component:default_sks_tactical:barrel` |
| platform_component | `component:default_sks_tactical:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_sks_tactical` | runtime_fallback | `component:default_sks_tactical:bolt` |
| platform_component | `component:default_sks_tactical:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_sks_tactical` | runtime_fallback | `component:default_sks_tactical:receiver` |
| platform_component | `component:default_sks_tactical:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_sks_tactical` | runtime_fallback | `component:default_sks_tactical:recoil` |
| platform_component | `component:default_sks_tactical:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_sks_tactical` | runtime_fallback | `component:default_sks_tactical:trigger` |
| platform_component | `component:default_spas_12:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_spas_12` | runtime_fallback | `component:default_spas_12:barrel` |
| platform_component | `component:default_spas_12:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_spas_12` | runtime_fallback | `component:default_spas_12:bolt` |
| platform_component | `component:default_spas_12:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_spas_12` | runtime_fallback | `component:default_spas_12:receiver` |
| platform_component | `component:default_spas_12:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_spas_12` | runtime_fallback | `component:default_spas_12:recoil` |
| platform_component | `component:default_spas_12:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_spas_12` | runtime_fallback | `component:default_spas_12:trigger` |
| platform_component | `component:default_spr15hb:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_spr15hb` | runtime_fallback | `component:default_spr15hb:barrel` |
| platform_component | `component:default_spr15hb:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_spr15hb` | runtime_fallback | `component:default_spr15hb:bolt` |
| platform_component | `component:default_spr15hb:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_spr15hb` | runtime_fallback | `component:default_spr15hb:receiver` |
| platform_component | `component:default_spr15hb:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_spr15hb` | runtime_fallback | `component:default_spr15hb:recoil` |
| platform_component | `component:default_spr15hb:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_spr15hb` | runtime_fallback | `component:default_spr15hb:trigger` |
| platform_component | `component:default_springfield1873:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_springfield1873` | runtime_fallback | `component:default_springfield1873:barrel` |
| platform_component | `component:default_springfield1873:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_springfield1873` | runtime_fallback | `component:default_springfield1873:bolt` |
| platform_component | `component:default_springfield1873:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_springfield1873` | runtime_fallback | `component:default_springfield1873:receiver` |
| platform_component | `component:default_springfield1873:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_springfield1873` | runtime_fallback | `component:default_springfield1873:recoil` |
| platform_component | `component:default_springfield1873:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_springfield1873` | runtime_fallback | `component:default_springfield1873:trigger` |
| platform_component | `component:default_taurus500:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_taurus500` | runtime_fallback | `component:default_taurus500:barrel` |
| platform_component | `component:default_taurus500:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_taurus500` | runtime_fallback | `component:default_taurus500:frame` |
| platform_component | `component:default_taurus500:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_taurus500` | runtime_fallback | `component:default_taurus500:recoil` |
| platform_component | `component:default_taurus500:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_taurus500` | runtime_fallback | `component:default_taurus500:slide` |
| platform_component | `component:default_taurus500:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_taurus500` | runtime_fallback | `component:default_taurus500:trigger` |
| platform_component | `component:default_timeless50:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_timeless50` | runtime_fallback | `component:default_timeless50:barrel` |
| platform_component | `component:default_timeless50:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=default_timeless50` | runtime_fallback | `component:default_timeless50:frame` |
| platform_component | `component:default_timeless50:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_timeless50` | runtime_fallback | `component:default_timeless50:recoil` |
| platform_component | `component:default_timeless50:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=default_timeless50` | runtime_fallback | `component:default_timeless50:slide` |
| platform_component | `component:default_timeless50:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_timeless50` | runtime_fallback | `component:default_timeless50:trigger` |
| platform_component | `component:default_type_81:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_type_81` | runtime_fallback | `component:default_type_81:barrel` |
| platform_component | `component:default_type_81:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_type_81` | runtime_fallback | `component:default_type_81:bolt` |
| platform_component | `component:default_type_81:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_type_81` | runtime_fallback | `component:default_type_81:receiver` |
| platform_component | `component:default_type_81:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_type_81` | runtime_fallback | `component:default_type_81:recoil` |
| platform_component | `component:default_type_81:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_type_81` | runtime_fallback | `component:default_type_81:trigger` |
| platform_component | `component:default_uzi:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_uzi` | runtime_fallback | `component:default_uzi:barrel` |
| platform_component | `component:default_uzi:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_uzi` | runtime_fallback | `component:default_uzi:bolt` |
| platform_component | `component:default_uzi:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_uzi` | runtime_fallback | `component:default_uzi:receiver` |
| platform_component | `component:default_uzi:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_uzi` | runtime_fallback | `component:default_uzi:recoil` |
| platform_component | `component:default_uzi:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_uzi` | runtime_fallback | `component:default_uzi:trigger` |
| platform_component | `component:default_vector45:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=default_vector45` | runtime_fallback | `component:default_vector45:barrel` |
| platform_component | `component:default_vector45:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=default_vector45` | runtime_fallback | `component:default_vector45:bolt` |
| platform_component | `component:default_vector45:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=default_vector45` | runtime_fallback | `component:default_vector45:receiver` |
| platform_component | `component:default_vector45:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=default_vector45` | runtime_fallback | `component:default_vector45:recoil` |
| platform_component | `component:default_vector45:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=default_vector45` | runtime_fallback | `component:default_vector45:trigger` |
| platform_component | `component:fal:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=fal` | runtime_fallback | `component:fal:barrel` |
| platform_component | `component:fal:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=fal` | runtime_fallback | `component:fal:bolt` |
| platform_component | `component:fal:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=fal` | runtime_fallback | `component:fal:receiver` |
| platform_component | `component:fal:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=fal` | runtime_fallback | `component:fal:recoil` |
| platform_component | `component:fal:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=fal` | runtime_fallback | `component:fal:trigger` |
| platform_component | `component:glock:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=glock` | runtime_fallback | `component:glock:barrel` |
| platform_component | `component:glock:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=glock` | runtime_fallback | `component:glock:frame` |
| platform_component | `component:glock:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=glock` | runtime_fallback | `component:glock:recoil` |
| platform_component | `component:glock:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=glock` | runtime_fallback | `component:glock:slide` |
| platform_component | `component:glock:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=glock` | runtime_fallback | `component:glock:trigger` |
| platform_component | `component:m1911:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=m1911` | runtime_fallback | `component:m1911:barrel` |
| platform_component | `component:m1911:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=m1911` | runtime_fallback | `component:m1911:frame` |
| platform_component | `component:m1911:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=m1911` | runtime_fallback | `component:m1911:recoil` |
| platform_component | `component:m1911:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=m1911` | runtime_fallback | `component:m1911:slide` |
| platform_component | `component:m1911:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=m1911` | runtime_fallback | `component:m1911:trigger` |
| platform_component | `component:m9:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=m9` | runtime_fallback | `component:m9:barrel` |
| platform_component | `component:m9:frame` | `tacz:gun_component ; industry_part_kind=frame ; industry_platform=m9` | runtime_fallback | `component:m9:frame` |
| platform_component | `component:m9:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=m9` | runtime_fallback | `component:m9:recoil` |
| platform_component | `component:m9:slide` | `tacz:gun_component ; industry_part_kind=slide ; industry_platform=m9` | runtime_fallback | `component:m9:slide` |
| platform_component | `component:m9:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=m9` | runtime_fallback | `component:m9:trigger` |
| platform_component | `component:mp5:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=mp5` | runtime_fallback | `component:mp5:barrel` |
| platform_component | `component:mp5:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=mp5` | runtime_fallback | `component:mp5:bolt` |
| platform_component | `component:mp5:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=mp5` | runtime_fallback | `component:mp5:receiver` |
| platform_component | `component:mp5:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=mp5` | runtime_fallback | `component:mp5:recoil` |
| platform_component | `component:mp5:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=mp5` | runtime_fallback | `component:mp5:trigger` |
| platform_component | `component:p90:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=p90` | runtime_fallback | `component:p90:barrel` |
| platform_component | `component:p90:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=p90` | runtime_fallback | `component:p90:bolt` |
| platform_component | `component:p90:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=p90` | runtime_fallback | `component:p90:receiver` |
| platform_component | `component:p90:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=p90` | runtime_fallback | `component:p90:recoil` |
| platform_component | `component:p90:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=p90` | runtime_fallback | `component:p90:trigger` |
| platform_component | `component:ump:barrel` | `tacz:gun_component ; industry_part_kind=barrel ; industry_platform=ump` | runtime_fallback | `component:ump:barrel` |
| platform_component | `component:ump:bolt` | `tacz:gun_component ; industry_part_kind=bolt ; industry_platform=ump` | runtime_fallback | `component:ump:bolt` |
| platform_component | `component:ump:receiver` | `tacz:gun_component ; industry_part_kind=receiver ; industry_platform=ump` | runtime_fallback | `component:ump:receiver` |
| platform_component | `component:ump:recoil` | `tacz:gun_component ; industry_part_kind=recoil ; industry_platform=ump` | runtime_fallback | `component:ump:recoil` |
| platform_component | `component:ump:trigger` | `tacz:gun_component ; industry_part_kind=trigger ; industry_platform=ump` | runtime_fallback | `component:ump:trigger` |
| platform_component_die | `component_die:ak:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ak ; die_target_kind=barrel` | runtime_fallback | `component_die:ak:barrel` |
| platform_component_die | `component_die:ak:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ak ; die_target_kind=bolt` | runtime_fallback | `component_die:ak:bolt` |
| platform_component_die | `component_die:ak:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ak ; die_target_kind=receiver` | runtime_fallback | `component_die:ak:receiver` |
| platform_component_die | `component_die:ak:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ak ; die_target_kind=recoil` | runtime_fallback | `component_die:ak:recoil` |
| platform_component_die | `component_die:ak:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ak ; die_target_kind=trigger` | runtime_fallback | `component_die:ak:trigger` |
| platform_component_die | `component_die:ar:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ar ; die_target_kind=barrel` | runtime_fallback | `component_die:ar:barrel` |
| platform_component_die | `component_die:ar:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ar ; die_target_kind=bolt` | runtime_fallback | `component_die:ar:bolt` |
| platform_component_die | `component_die:ar:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ar ; die_target_kind=receiver` | runtime_fallback | `component_die:ar:receiver` |
| platform_component_die | `component_die:ar:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ar ; die_target_kind=recoil` | runtime_fallback | `component_die:ar:recoil` |
| platform_component_die | `component_die:ar:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ar ; die_target_kind=trigger` | runtime_fallback | `component_die:ar:trigger` |
| platform_component_die | `component_die:default_aa12:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aa12 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_aa12:barrel` |
| platform_component_die | `component_die:default_aa12:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aa12 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_aa12:bolt` |
| platform_component_die | `component_die:default_aa12:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aa12 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_aa12:receiver` |
| platform_component_die | `component_die:default_aa12:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aa12 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_aa12:recoil` |
| platform_component_die | `component_die:default_aa12:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aa12 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_aa12:trigger` |
| platform_component_die | `component_die:default_ai_awp:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_ai_awp ; die_target_kind=barrel` | runtime_fallback | `component_die:default_ai_awp:barrel` |
| platform_component_die | `component_die:default_ai_awp:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_ai_awp ; die_target_kind=bolt` | runtime_fallback | `component_die:default_ai_awp:bolt` |
| platform_component_die | `component_die:default_ai_awp:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_ai_awp ; die_target_kind=receiver` | runtime_fallback | `component_die:default_ai_awp:receiver` |
| platform_component_die | `component_die:default_ai_awp:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_ai_awp ; die_target_kind=recoil` | runtime_fallback | `component_die:default_ai_awp:recoil` |
| platform_component_die | `component_die:default_ai_awp:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_ai_awp ; die_target_kind=trigger` | runtime_fallback | `component_die:default_ai_awp:trigger` |
| platform_component_die | `component_die:default_aug:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aug ; die_target_kind=barrel` | runtime_fallback | `component_die:default_aug:barrel` |
| platform_component_die | `component_die:default_aug:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aug ; die_target_kind=bolt` | runtime_fallback | `component_die:default_aug:bolt` |
| platform_component_die | `component_die:default_aug:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aug ; die_target_kind=receiver` | runtime_fallback | `component_die:default_aug:receiver` |
| platform_component_die | `component_die:default_aug:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aug ; die_target_kind=recoil` | runtime_fallback | `component_die:default_aug:recoil` |
| platform_component_die | `component_die:default_aug:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_aug ; die_target_kind=trigger` | runtime_fallback | `component_die:default_aug:trigger` |
| platform_component_die | `component_die:default_b93r:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_b93r ; die_target_kind=barrel` | runtime_fallback | `component_die:default_b93r:barrel` |
| platform_component_die | `component_die:default_b93r:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_b93r ; die_target_kind=frame` | runtime_fallback | `component_die:default_b93r:frame` |
| platform_component_die | `component_die:default_b93r:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_b93r ; die_target_kind=recoil` | runtime_fallback | `component_die:default_b93r:recoil` |
| platform_component_die | `component_die:default_b93r:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_b93r ; die_target_kind=slide` | runtime_fallback | `component_die:default_b93r:slide` |
| platform_component_die | `component_die:default_b93r:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_b93r ; die_target_kind=trigger` | runtime_fallback | `component_die:default_b93r:trigger` |
| platform_component_die | `component_die:default_cz75:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_cz75 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_cz75:barrel` |
| platform_component_die | `component_die:default_cz75:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_cz75 ; die_target_kind=frame` | runtime_fallback | `component_die:default_cz75:frame` |
| platform_component_die | `component_die:default_cz75:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_cz75 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_cz75:recoil` |
| platform_component_die | `component_die:default_cz75:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_cz75 ; die_target_kind=slide` | runtime_fallback | `component_die:default_cz75:slide` |
| platform_component_die | `component_die:default_cz75:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_cz75 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_cz75:trigger` |
| platform_component_die | `component_die:default_db_long:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_long ; die_target_kind=barrel` | runtime_fallback | `component_die:default_db_long:barrel` |
| platform_component_die | `component_die:default_db_long:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_long ; die_target_kind=bolt` | runtime_fallback | `component_die:default_db_long:bolt` |
| platform_component_die | `component_die:default_db_long:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_long ; die_target_kind=receiver` | runtime_fallback | `component_die:default_db_long:receiver` |
| platform_component_die | `component_die:default_db_long:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_long ; die_target_kind=recoil` | runtime_fallback | `component_die:default_db_long:recoil` |
| platform_component_die | `component_die:default_db_long:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_long ; die_target_kind=trigger` | runtime_fallback | `component_die:default_db_long:trigger` |
| platform_component_die | `component_die:default_db_short:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_short ; die_target_kind=barrel` | runtime_fallback | `component_die:default_db_short:barrel` |
| platform_component_die | `component_die:default_db_short:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_short ; die_target_kind=bolt` | runtime_fallback | `component_die:default_db_short:bolt` |
| platform_component_die | `component_die:default_db_short:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_short ; die_target_kind=receiver` | runtime_fallback | `component_die:default_db_short:receiver` |
| platform_component_die | `component_die:default_db_short:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_short ; die_target_kind=recoil` | runtime_fallback | `component_die:default_db_short:recoil` |
| platform_component_die | `component_die:default_db_short:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_db_short ; die_target_kind=trigger` | runtime_fallback | `component_die:default_db_short:trigger` |
| platform_component_die | `component_die:default_deagle:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle ; die_target_kind=barrel` | runtime_fallback | `component_die:default_deagle:barrel` |
| platform_component_die | `component_die:default_deagle:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle ; die_target_kind=frame` | runtime_fallback | `component_die:default_deagle:frame` |
| platform_component_die | `component_die:default_deagle:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle ; die_target_kind=recoil` | runtime_fallback | `component_die:default_deagle:recoil` |
| platform_component_die | `component_die:default_deagle:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle ; die_target_kind=slide` | runtime_fallback | `component_die:default_deagle:slide` |
| platform_component_die | `component_die:default_deagle:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle ; die_target_kind=trigger` | runtime_fallback | `component_die:default_deagle:trigger` |
| platform_component_die | `component_die:default_deagle_golden:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle_golden ; die_target_kind=barrel` | runtime_fallback | `component_die:default_deagle_golden:barrel` |
| platform_component_die | `component_die:default_deagle_golden:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle_golden ; die_target_kind=frame` | runtime_fallback | `component_die:default_deagle_golden:frame` |
| platform_component_die | `component_die:default_deagle_golden:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle_golden ; die_target_kind=recoil` | runtime_fallback | `component_die:default_deagle_golden:recoil` |
| platform_component_die | `component_die:default_deagle_golden:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle_golden ; die_target_kind=slide` | runtime_fallback | `component_die:default_deagle_golden:slide` |
| platform_component_die | `component_die:default_deagle_golden:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_deagle_golden ; die_target_kind=trigger` | runtime_fallback | `component_die:default_deagle_golden:trigger` |
| platform_component_die | `component_die:default_fn_evolys:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_fn_evolys ; die_target_kind=barrel` | runtime_fallback | `component_die:default_fn_evolys:barrel` |
| platform_component_die | `component_die:default_fn_evolys:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_fn_evolys ; die_target_kind=bolt` | runtime_fallback | `component_die:default_fn_evolys:bolt` |
| platform_component_die | `component_die:default_fn_evolys:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_fn_evolys ; die_target_kind=receiver` | runtime_fallback | `component_die:default_fn_evolys:receiver` |
| platform_component_die | `component_die:default_fn_evolys:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_fn_evolys ; die_target_kind=recoil` | runtime_fallback | `component_die:default_fn_evolys:recoil` |
| platform_component_die | `component_die:default_fn_evolys:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_fn_evolys ; die_target_kind=trigger` | runtime_fallback | `component_die:default_fn_evolys:trigger` |
| platform_component_die | `component_die:default_g36k:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_g36k ; die_target_kind=barrel` | runtime_fallback | `component_die:default_g36k:barrel` |
| platform_component_die | `component_die:default_g36k:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_g36k ; die_target_kind=bolt` | runtime_fallback | `component_die:default_g36k:bolt` |
| platform_component_die | `component_die:default_g36k:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_g36k ; die_target_kind=receiver` | runtime_fallback | `component_die:default_g36k:receiver` |
| platform_component_die | `component_die:default_g36k:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_g36k ; die_target_kind=recoil` | runtime_fallback | `component_die:default_g36k:recoil` |
| platform_component_die | `component_die:default_g36k:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_g36k ; die_target_kind=trigger` | runtime_fallback | `component_die:default_g36k:trigger` |
| platform_component_die | `component_die:default_hk416d:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk416d ; die_target_kind=barrel` | runtime_fallback | `component_die:default_hk416d:barrel` |
| platform_component_die | `component_die:default_hk416d:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk416d ; die_target_kind=bolt` | runtime_fallback | `component_die:default_hk416d:bolt` |
| platform_component_die | `component_die:default_hk416d:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk416d ; die_target_kind=receiver` | runtime_fallback | `component_die:default_hk416d:receiver` |
| platform_component_die | `component_die:default_hk416d:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk416d ; die_target_kind=recoil` | runtime_fallback | `component_die:default_hk416d:recoil` |
| platform_component_die | `component_die:default_hk416d:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk416d ; die_target_kind=trigger` | runtime_fallback | `component_die:default_hk416d:trigger` |
| platform_component_die | `component_die:default_hk_g3:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_g3 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_hk_g3:barrel` |
| platform_component_die | `component_die:default_hk_g3:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_g3 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_hk_g3:bolt` |
| platform_component_die | `component_die:default_hk_g3:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_g3 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_hk_g3:receiver` |
| platform_component_die | `component_die:default_hk_g3:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_g3 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_hk_g3:recoil` |
| platform_component_die | `component_die:default_hk_g3:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_g3 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_hk_g3:trigger` |
| platform_component_die | `component_die:default_hk_mk23:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_mk23 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_hk_mk23:barrel` |
| platform_component_die | `component_die:default_hk_mk23:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_mk23 ; die_target_kind=frame` | runtime_fallback | `component_die:default_hk_mk23:frame` |
| platform_component_die | `component_die:default_hk_mk23:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_mk23 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_hk_mk23:recoil` |
| platform_component_die | `component_die:default_hk_mk23:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_mk23 ; die_target_kind=slide` | runtime_fallback | `component_die:default_hk_mk23:slide` |
| platform_component_die | `component_die:default_hk_mk23:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_hk_mk23 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_hk_mk23:trigger` |
| platform_component_die | `component_die:default_kar98:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_kar98 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_kar98:barrel` |
| platform_component_die | `component_die:default_kar98:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_kar98 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_kar98:bolt` |
| platform_component_die | `component_die:default_kar98:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_kar98 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_kar98:receiver` |
| platform_component_die | `component_die:default_kar98:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_kar98 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_kar98:recoil` |
| platform_component_die | `component_die:default_kar98:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_kar98 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_kar98:trigger` |
| platform_component_die | `component_die:default_lonetrail:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_lonetrail ; die_target_kind=barrel` | runtime_fallback | `component_die:default_lonetrail:barrel` |
| platform_component_die | `component_die:default_lonetrail:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_lonetrail ; die_target_kind=frame` | runtime_fallback | `component_die:default_lonetrail:frame` |
| platform_component_die | `component_die:default_lonetrail:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_lonetrail ; die_target_kind=recoil` | runtime_fallback | `component_die:default_lonetrail:recoil` |
| platform_component_die | `component_die:default_lonetrail:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_lonetrail ; die_target_kind=slide` | runtime_fallback | `component_die:default_lonetrail:slide` |
| platform_component_die | `component_die:default_lonetrail:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_lonetrail ; die_target_kind=trigger` | runtime_fallback | `component_die:default_lonetrail:trigger` |
| platform_component_die | `component_die:default_m1014:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m1014 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m1014:barrel` |
| platform_component_die | `component_die:default_m1014:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m1014 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m1014:bolt` |
| platform_component_die | `component_die:default_m1014:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m1014 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m1014:receiver` |
| platform_component_die | `component_die:default_m1014:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m1014 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m1014:recoil` |
| platform_component_die | `component_die:default_m1014:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m1014 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m1014:trigger` |
| platform_component_die | `component_die:default_m107:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m107 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m107:barrel` |
| platform_component_die | `component_die:default_m107:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m107 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m107:bolt` |
| platform_component_die | `component_die:default_m107:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m107 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m107:receiver` |
| platform_component_die | `component_die:default_m107:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m107 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m107:recoil` |
| platform_component_die | `component_die:default_m107:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m107 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m107:trigger` |
| platform_component_die | `component_die:default_m16a1:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a1 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m16a1:barrel` |
| platform_component_die | `component_die:default_m16a1:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a1 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m16a1:bolt` |
| platform_component_die | `component_die:default_m16a1:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a1 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m16a1:receiver` |
| platform_component_die | `component_die:default_m16a1:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a1 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m16a1:recoil` |
| platform_component_die | `component_die:default_m16a1:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a1 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m16a1:trigger` |
| platform_component_die | `component_die:default_m16a4:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a4 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m16a4:barrel` |
| platform_component_die | `component_die:default_m16a4:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a4 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m16a4:bolt` |
| platform_component_die | `component_die:default_m16a4:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a4 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m16a4:receiver` |
| platform_component_die | `component_die:default_m16a4:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a4 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m16a4:recoil` |
| platform_component_die | `component_die:default_m16a4:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m16a4 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m16a4:trigger` |
| platform_component_die | `component_die:default_m249:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m249 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m249:barrel` |
| platform_component_die | `component_die:default_m249:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m249 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m249:bolt` |
| platform_component_die | `component_die:default_m249:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m249 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m249:receiver` |
| platform_component_die | `component_die:default_m249:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m249 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m249:recoil` |
| platform_component_die | `component_die:default_m249:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m249 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m249:trigger` |
| platform_component_die | `component_die:default_m320:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m320 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m320:barrel` |
| platform_component_die | `component_die:default_m320:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m320 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m320:bolt` |
| platform_component_die | `component_die:default_m320:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m320 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m320:receiver` |
| platform_component_die | `component_die:default_m320:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m320 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m320:recoil` |
| platform_component_die | `component_die:default_m320:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m320 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m320:trigger` |
| platform_component_die | `component_die:default_m700:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m700 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m700:barrel` |
| platform_component_die | `component_die:default_m700:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m700 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m700:bolt` |
| platform_component_die | `component_die:default_m700:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m700 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m700:receiver` |
| platform_component_die | `component_die:default_m700:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m700 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m700:recoil` |
| platform_component_die | `component_die:default_m700:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m700 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m700:trigger` |
| platform_component_die | `component_die:default_m870:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m870 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m870:barrel` |
| platform_component_die | `component_die:default_m870:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m870 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m870:bolt` |
| platform_component_die | `component_die:default_m870:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m870 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m870:receiver` |
| platform_component_die | `component_die:default_m870:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m870 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m870:recoil` |
| platform_component_die | `component_die:default_m870:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m870 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m870:trigger` |
| platform_component_die | `component_die:default_m95:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m95 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_m95:barrel` |
| platform_component_die | `component_die:default_m95:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m95 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_m95:bolt` |
| platform_component_die | `component_die:default_m95:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m95 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_m95:receiver` |
| platform_component_die | `component_die:default_m95:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m95 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_m95:recoil` |
| platform_component_die | `component_die:default_m95:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_m95 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_m95:trigger` |
| platform_component_die | `component_die:default_minigun:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_minigun ; die_target_kind=barrel` | runtime_fallback | `component_die:default_minigun:barrel` |
| platform_component_die | `component_die:default_minigun:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_minigun ; die_target_kind=bolt` | runtime_fallback | `component_die:default_minigun:bolt` |
| platform_component_die | `component_die:default_minigun:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_minigun ; die_target_kind=receiver` | runtime_fallback | `component_die:default_minigun:receiver` |
| platform_component_die | `component_die:default_minigun:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_minigun ; die_target_kind=recoil` | runtime_fallback | `component_die:default_minigun:recoil` |
| platform_component_die | `component_die:default_minigun:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_minigun ; die_target_kind=trigger` | runtime_fallback | `component_die:default_minigun:trigger` |
| platform_component_die | `component_die:default_mk14:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_mk14 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_mk14:barrel` |
| platform_component_die | `component_die:default_mk14:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_mk14 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_mk14:bolt` |
| platform_component_die | `component_die:default_mk14:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_mk14 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_mk14:receiver` |
| platform_component_die | `component_die:default_mk14:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_mk14 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_mk14:recoil` |
| platform_component_die | `component_die:default_mk14:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_mk14 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_mk14:trigger` |
| platform_component_die | `component_die:default_p320:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_p320 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_p320:barrel` |
| platform_component_die | `component_die:default_p320:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_p320 ; die_target_kind=frame` | runtime_fallback | `component_die:default_p320:frame` |
| platform_component_die | `component_die:default_p320:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_p320 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_p320:recoil` |
| platform_component_die | `component_die:default_p320:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_p320 ; die_target_kind=slide` | runtime_fallback | `component_die:default_p320:slide` |
| platform_component_die | `component_die:default_p320:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_p320 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_p320:trigger` |
| platform_component_die | `component_die:default_qbz_191:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_191 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_qbz_191:barrel` |
| platform_component_die | `component_die:default_qbz_191:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_191 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_qbz_191:bolt` |
| platform_component_die | `component_die:default_qbz_191:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_191 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_qbz_191:receiver` |
| platform_component_die | `component_die:default_qbz_191:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_191 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_qbz_191:recoil` |
| platform_component_die | `component_die:default_qbz_191:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_191 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_qbz_191:trigger` |
| platform_component_die | `component_die:default_qbz_95:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_95 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_qbz_95:barrel` |
| platform_component_die | `component_die:default_qbz_95:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_95 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_qbz_95:bolt` |
| platform_component_die | `component_die:default_qbz_95:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_95 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_qbz_95:receiver` |
| platform_component_die | `component_die:default_qbz_95:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_95 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_qbz_95:recoil` |
| platform_component_die | `component_die:default_qbz_95:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_qbz_95 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_qbz_95:trigger` |
| platform_component_die | `component_die:default_rhino357:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rhino357 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_rhino357:barrel` |
| platform_component_die | `component_die:default_rhino357:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rhino357 ; die_target_kind=frame` | runtime_fallback | `component_die:default_rhino357:frame` |
| platform_component_die | `component_die:default_rhino357:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rhino357 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_rhino357:recoil` |
| platform_component_die | `component_die:default_rhino357:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rhino357 ; die_target_kind=slide` | runtime_fallback | `component_die:default_rhino357:slide` |
| platform_component_die | `component_die:default_rhino357:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rhino357 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_rhino357:trigger` |
| platform_component_die | `component_die:default_rpg7:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpg7 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_rpg7:barrel` |
| platform_component_die | `component_die:default_rpg7:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpg7 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_rpg7:bolt` |
| platform_component_die | `component_die:default_rpg7:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpg7 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_rpg7:receiver` |
| platform_component_die | `component_die:default_rpg7:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpg7 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_rpg7:recoil` |
| platform_component_die | `component_die:default_rpg7:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpg7 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_rpg7:trigger` |
| platform_component_die | `component_die:default_rpk:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpk ; die_target_kind=barrel` | runtime_fallback | `component_die:default_rpk:barrel` |
| platform_component_die | `component_die:default_rpk:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpk ; die_target_kind=bolt` | runtime_fallback | `component_die:default_rpk:bolt` |
| platform_component_die | `component_die:default_rpk:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpk ; die_target_kind=receiver` | runtime_fallback | `component_die:default_rpk:receiver` |
| platform_component_die | `component_die:default_rpk:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpk ; die_target_kind=recoil` | runtime_fallback | `component_die:default_rpk:recoil` |
| platform_component_die | `component_die:default_rpk:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_rpk ; die_target_kind=trigger` | runtime_fallback | `component_die:default_rpk:trigger` |
| platform_component_die | `component_die:default_scar_h:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_h ; die_target_kind=barrel` | runtime_fallback | `component_die:default_scar_h:barrel` |
| platform_component_die | `component_die:default_scar_h:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_h ; die_target_kind=bolt` | runtime_fallback | `component_die:default_scar_h:bolt` |
| platform_component_die | `component_die:default_scar_h:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_h ; die_target_kind=receiver` | runtime_fallback | `component_die:default_scar_h:receiver` |
| platform_component_die | `component_die:default_scar_h:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_h ; die_target_kind=recoil` | runtime_fallback | `component_die:default_scar_h:recoil` |
| platform_component_die | `component_die:default_scar_h:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_h ; die_target_kind=trigger` | runtime_fallback | `component_die:default_scar_h:trigger` |
| platform_component_die | `component_die:default_scar_l:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_l ; die_target_kind=barrel` | runtime_fallback | `component_die:default_scar_l:barrel` |
| platform_component_die | `component_die:default_scar_l:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_l ; die_target_kind=bolt` | runtime_fallback | `component_die:default_scar_l:bolt` |
| platform_component_die | `component_die:default_scar_l:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_l ; die_target_kind=receiver` | runtime_fallback | `component_die:default_scar_l:receiver` |
| platform_component_die | `component_die:default_scar_l:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_l ; die_target_kind=recoil` | runtime_fallback | `component_die:default_scar_l:recoil` |
| platform_component_die | `component_die:default_scar_l:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_scar_l ; die_target_kind=trigger` | runtime_fallback | `component_die:default_scar_l:trigger` |
| platform_component_die | `component_die:default_sks_tactical:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_sks_tactical ; die_target_kind=barrel` | runtime_fallback | `component_die:default_sks_tactical:barrel` |
| platform_component_die | `component_die:default_sks_tactical:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_sks_tactical ; die_target_kind=bolt` | runtime_fallback | `component_die:default_sks_tactical:bolt` |
| platform_component_die | `component_die:default_sks_tactical:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_sks_tactical ; die_target_kind=receiver` | runtime_fallback | `component_die:default_sks_tactical:receiver` |
| platform_component_die | `component_die:default_sks_tactical:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_sks_tactical ; die_target_kind=recoil` | runtime_fallback | `component_die:default_sks_tactical:recoil` |
| platform_component_die | `component_die:default_sks_tactical:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_sks_tactical ; die_target_kind=trigger` | runtime_fallback | `component_die:default_sks_tactical:trigger` |
| platform_component_die | `component_die:default_spas_12:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spas_12 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_spas_12:barrel` |
| platform_component_die | `component_die:default_spas_12:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spas_12 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_spas_12:bolt` |
| platform_component_die | `component_die:default_spas_12:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spas_12 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_spas_12:receiver` |
| platform_component_die | `component_die:default_spas_12:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spas_12 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_spas_12:recoil` |
| platform_component_die | `component_die:default_spas_12:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spas_12 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_spas_12:trigger` |
| platform_component_die | `component_die:default_spr15hb:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spr15hb ; die_target_kind=barrel` | runtime_fallback | `component_die:default_spr15hb:barrel` |
| platform_component_die | `component_die:default_spr15hb:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spr15hb ; die_target_kind=bolt` | runtime_fallback | `component_die:default_spr15hb:bolt` |
| platform_component_die | `component_die:default_spr15hb:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spr15hb ; die_target_kind=receiver` | runtime_fallback | `component_die:default_spr15hb:receiver` |
| platform_component_die | `component_die:default_spr15hb:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spr15hb ; die_target_kind=recoil` | runtime_fallback | `component_die:default_spr15hb:recoil` |
| platform_component_die | `component_die:default_spr15hb:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_spr15hb ; die_target_kind=trigger` | runtime_fallback | `component_die:default_spr15hb:trigger` |
| platform_component_die | `component_die:default_springfield1873:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_springfield1873 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_springfield1873:barrel` |
| platform_component_die | `component_die:default_springfield1873:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_springfield1873 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_springfield1873:bolt` |
| platform_component_die | `component_die:default_springfield1873:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_springfield1873 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_springfield1873:receiver` |
| platform_component_die | `component_die:default_springfield1873:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_springfield1873 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_springfield1873:recoil` |
| platform_component_die | `component_die:default_springfield1873:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_springfield1873 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_springfield1873:trigger` |
| platform_component_die | `component_die:default_taurus500:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_taurus500 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_taurus500:barrel` |
| platform_component_die | `component_die:default_taurus500:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_taurus500 ; die_target_kind=frame` | runtime_fallback | `component_die:default_taurus500:frame` |
| platform_component_die | `component_die:default_taurus500:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_taurus500 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_taurus500:recoil` |
| platform_component_die | `component_die:default_taurus500:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_taurus500 ; die_target_kind=slide` | runtime_fallback | `component_die:default_taurus500:slide` |
| platform_component_die | `component_die:default_taurus500:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_taurus500 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_taurus500:trigger` |
| platform_component_die | `component_die:default_timeless50:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_timeless50 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_timeless50:barrel` |
| platform_component_die | `component_die:default_timeless50:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_timeless50 ; die_target_kind=frame` | runtime_fallback | `component_die:default_timeless50:frame` |
| platform_component_die | `component_die:default_timeless50:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_timeless50 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_timeless50:recoil` |
| platform_component_die | `component_die:default_timeless50:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_timeless50 ; die_target_kind=slide` | runtime_fallback | `component_die:default_timeless50:slide` |
| platform_component_die | `component_die:default_timeless50:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_timeless50 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_timeless50:trigger` |
| platform_component_die | `component_die:default_type_81:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_type_81 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_type_81:barrel` |
| platform_component_die | `component_die:default_type_81:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_type_81 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_type_81:bolt` |
| platform_component_die | `component_die:default_type_81:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_type_81 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_type_81:receiver` |
| platform_component_die | `component_die:default_type_81:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_type_81 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_type_81:recoil` |
| platform_component_die | `component_die:default_type_81:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_type_81 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_type_81:trigger` |
| platform_component_die | `component_die:default_uzi:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_uzi ; die_target_kind=barrel` | runtime_fallback | `component_die:default_uzi:barrel` |
| platform_component_die | `component_die:default_uzi:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_uzi ; die_target_kind=bolt` | runtime_fallback | `component_die:default_uzi:bolt` |
| platform_component_die | `component_die:default_uzi:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_uzi ; die_target_kind=receiver` | runtime_fallback | `component_die:default_uzi:receiver` |
| platform_component_die | `component_die:default_uzi:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_uzi ; die_target_kind=recoil` | runtime_fallback | `component_die:default_uzi:recoil` |
| platform_component_die | `component_die:default_uzi:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_uzi ; die_target_kind=trigger` | runtime_fallback | `component_die:default_uzi:trigger` |
| platform_component_die | `component_die:default_vector45:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_vector45 ; die_target_kind=barrel` | runtime_fallback | `component_die:default_vector45:barrel` |
| platform_component_die | `component_die:default_vector45:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_vector45 ; die_target_kind=bolt` | runtime_fallback | `component_die:default_vector45:bolt` |
| platform_component_die | `component_die:default_vector45:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_vector45 ; die_target_kind=receiver` | runtime_fallback | `component_die:default_vector45:receiver` |
| platform_component_die | `component_die:default_vector45:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_vector45 ; die_target_kind=recoil` | runtime_fallback | `component_die:default_vector45:recoil` |
| platform_component_die | `component_die:default_vector45:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=default_vector45 ; die_target_kind=trigger` | runtime_fallback | `component_die:default_vector45:trigger` |
| platform_component_die | `component_die:fal:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=fal ; die_target_kind=barrel` | runtime_fallback | `component_die:fal:barrel` |
| platform_component_die | `component_die:fal:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=fal ; die_target_kind=bolt` | runtime_fallback | `component_die:fal:bolt` |
| platform_component_die | `component_die:fal:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=fal ; die_target_kind=receiver` | runtime_fallback | `component_die:fal:receiver` |
| platform_component_die | `component_die:fal:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=fal ; die_target_kind=recoil` | runtime_fallback | `component_die:fal:recoil` |
| platform_component_die | `component_die:fal:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=fal ; die_target_kind=trigger` | runtime_fallback | `component_die:fal:trigger` |
| platform_component_die | `component_die:glock:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=glock ; die_target_kind=barrel` | runtime_fallback | `component_die:glock:barrel` |
| platform_component_die | `component_die:glock:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=glock ; die_target_kind=frame` | runtime_fallback | `component_die:glock:frame` |
| platform_component_die | `component_die:glock:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=glock ; die_target_kind=recoil` | runtime_fallback | `component_die:glock:recoil` |
| platform_component_die | `component_die:glock:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=glock ; die_target_kind=slide` | runtime_fallback | `component_die:glock:slide` |
| platform_component_die | `component_die:glock:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=glock ; die_target_kind=trigger` | runtime_fallback | `component_die:glock:trigger` |
| platform_component_die | `component_die:m1911:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m1911 ; die_target_kind=barrel` | runtime_fallback | `component_die:m1911:barrel` |
| platform_component_die | `component_die:m1911:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m1911 ; die_target_kind=frame` | runtime_fallback | `component_die:m1911:frame` |
| platform_component_die | `component_die:m1911:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m1911 ; die_target_kind=recoil` | runtime_fallback | `component_die:m1911:recoil` |
| platform_component_die | `component_die:m1911:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m1911 ; die_target_kind=slide` | runtime_fallback | `component_die:m1911:slide` |
| platform_component_die | `component_die:m1911:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m1911 ; die_target_kind=trigger` | runtime_fallback | `component_die:m1911:trigger` |
| platform_component_die | `component_die:m9:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m9 ; die_target_kind=barrel` | runtime_fallback | `component_die:m9:barrel` |
| platform_component_die | `component_die:m9:frame` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m9 ; die_target_kind=frame` | runtime_fallback | `component_die:m9:frame` |
| platform_component_die | `component_die:m9:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m9 ; die_target_kind=recoil` | runtime_fallback | `component_die:m9:recoil` |
| platform_component_die | `component_die:m9:slide` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m9 ; die_target_kind=slide` | runtime_fallback | `component_die:m9:slide` |
| platform_component_die | `component_die:m9:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=m9 ; die_target_kind=trigger` | runtime_fallback | `component_die:m9:trigger` |
| platform_component_die | `component_die:mp5:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=mp5 ; die_target_kind=barrel` | runtime_fallback | `component_die:mp5:barrel` |
| platform_component_die | `component_die:mp5:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=mp5 ; die_target_kind=bolt` | runtime_fallback | `component_die:mp5:bolt` |
| platform_component_die | `component_die:mp5:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=mp5 ; die_target_kind=receiver` | runtime_fallback | `component_die:mp5:receiver` |
| platform_component_die | `component_die:mp5:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=mp5 ; die_target_kind=recoil` | runtime_fallback | `component_die:mp5:recoil` |
| platform_component_die | `component_die:mp5:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=mp5 ; die_target_kind=trigger` | runtime_fallback | `component_die:mp5:trigger` |
| platform_component_die | `component_die:p90:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=p90 ; die_target_kind=barrel` | runtime_fallback | `component_die:p90:barrel` |
| platform_component_die | `component_die:p90:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=p90 ; die_target_kind=bolt` | runtime_fallback | `component_die:p90:bolt` |
| platform_component_die | `component_die:p90:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=p90 ; die_target_kind=receiver` | runtime_fallback | `component_die:p90:receiver` |
| platform_component_die | `component_die:p90:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=p90 ; die_target_kind=recoil` | runtime_fallback | `component_die:p90:recoil` |
| platform_component_die | `component_die:p90:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=p90 ; die_target_kind=trigger` | runtime_fallback | `component_die:p90:trigger` |
| platform_component_die | `component_die:ump:barrel` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ump ; die_target_kind=barrel` | runtime_fallback | `component_die:ump:barrel` |
| platform_component_die | `component_die:ump:bolt` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ump ; die_target_kind=bolt` | runtime_fallback | `component_die:ump:bolt` |
| platform_component_die | `component_die:ump:receiver` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ump ; die_target_kind=receiver` | runtime_fallback | `component_die:ump:receiver` |
| platform_component_die | `component_die:ump:recoil` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ump ; die_target_kind=recoil` | runtime_fallback | `component_die:ump:recoil` |
| platform_component_die | `component_die:ump:trigger` | `tacz:press_die ; industry_part_kind=component_die ; industry_platform=ump ; die_target_kind=trigger` | runtime_fallback | `component_die:ump:trigger` |
| platform_furniture_kit | `furniture_kit:ak` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=ak` | runtime_fallback | `furniture_kit:ak` |
| platform_furniture_kit | `furniture_kit:ar` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=ar` | runtime_fallback | `furniture_kit:ar` |
| platform_furniture_kit | `furniture_kit:default_aa12` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_aa12` | runtime_fallback | `furniture_kit:default_aa12` |
| platform_furniture_kit | `furniture_kit:default_ai_awp` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_ai_awp` | runtime_fallback | `furniture_kit:default_ai_awp` |
| platform_furniture_kit | `furniture_kit:default_aug` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_aug` | runtime_fallback | `furniture_kit:default_aug` |
| platform_furniture_kit | `furniture_kit:default_b93r` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_b93r` | runtime_fallback | `furniture_kit:default_b93r` |
| platform_furniture_kit | `furniture_kit:default_cz75` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_cz75` | runtime_fallback | `furniture_kit:default_cz75` |
| platform_furniture_kit | `furniture_kit:default_db_long` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_db_long` | runtime_fallback | `furniture_kit:default_db_long` |
| platform_furniture_kit | `furniture_kit:default_db_short` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_db_short` | runtime_fallback | `furniture_kit:default_db_short` |
| platform_furniture_kit | `furniture_kit:default_deagle` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_deagle` | runtime_fallback | `furniture_kit:default_deagle` |
| platform_furniture_kit | `furniture_kit:default_deagle_golden` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_deagle_golden` | runtime_fallback | `furniture_kit:default_deagle_golden` |
| platform_furniture_kit | `furniture_kit:default_fn_evolys` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_fn_evolys` | runtime_fallback | `furniture_kit:default_fn_evolys` |
| platform_furniture_kit | `furniture_kit:default_g36k` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_g36k` | runtime_fallback | `furniture_kit:default_g36k` |
| platform_furniture_kit | `furniture_kit:default_hk416d` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_hk416d` | runtime_fallback | `furniture_kit:default_hk416d` |
| platform_furniture_kit | `furniture_kit:default_hk_g3` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_hk_g3` | runtime_fallback | `furniture_kit:default_hk_g3` |
| platform_furniture_kit | `furniture_kit:default_hk_mk23` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_hk_mk23` | runtime_fallback | `furniture_kit:default_hk_mk23` |
| platform_furniture_kit | `furniture_kit:default_kar98` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_kar98` | runtime_fallback | `furniture_kit:default_kar98` |
| platform_furniture_kit | `furniture_kit:default_lonetrail` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_lonetrail` | runtime_fallback | `furniture_kit:default_lonetrail` |
| platform_furniture_kit | `furniture_kit:default_m1014` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m1014` | runtime_fallback | `furniture_kit:default_m1014` |
| platform_furniture_kit | `furniture_kit:default_m107` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m107` | runtime_fallback | `furniture_kit:default_m107` |
| platform_furniture_kit | `furniture_kit:default_m16a1` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m16a1` | runtime_fallback | `furniture_kit:default_m16a1` |
| platform_furniture_kit | `furniture_kit:default_m16a4` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m16a4` | runtime_fallback | `furniture_kit:default_m16a4` |
| platform_furniture_kit | `furniture_kit:default_m249` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m249` | runtime_fallback | `furniture_kit:default_m249` |
| platform_furniture_kit | `furniture_kit:default_m320` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m320` | runtime_fallback | `furniture_kit:default_m320` |
| platform_furniture_kit | `furniture_kit:default_m700` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m700` | runtime_fallback | `furniture_kit:default_m700` |
| platform_furniture_kit | `furniture_kit:default_m870` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m870` | runtime_fallback | `furniture_kit:default_m870` |
| platform_furniture_kit | `furniture_kit:default_m95` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_m95` | runtime_fallback | `furniture_kit:default_m95` |
| platform_furniture_kit | `furniture_kit:default_minigun` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_minigun` | runtime_fallback | `furniture_kit:default_minigun` |
| platform_furniture_kit | `furniture_kit:default_mk14` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_mk14` | runtime_fallback | `furniture_kit:default_mk14` |
| platform_furniture_kit | `furniture_kit:default_p320` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_p320` | runtime_fallback | `furniture_kit:default_p320` |
| platform_furniture_kit | `furniture_kit:default_qbz_191` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_qbz_191` | runtime_fallback | `furniture_kit:default_qbz_191` |
| platform_furniture_kit | `furniture_kit:default_qbz_95` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_qbz_95` | runtime_fallback | `furniture_kit:default_qbz_95` |
| platform_furniture_kit | `furniture_kit:default_rhino357` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_rhino357` | runtime_fallback | `furniture_kit:default_rhino357` |
| platform_furniture_kit | `furniture_kit:default_rpg7` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_rpg7` | runtime_fallback | `furniture_kit:default_rpg7` |
| platform_furniture_kit | `furniture_kit:default_rpk` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_rpk` | runtime_fallback | `furniture_kit:default_rpk` |
| platform_furniture_kit | `furniture_kit:default_scar_h` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_scar_h` | runtime_fallback | `furniture_kit:default_scar_h` |
| platform_furniture_kit | `furniture_kit:default_scar_l` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_scar_l` | runtime_fallback | `furniture_kit:default_scar_l` |
| platform_furniture_kit | `furniture_kit:default_sks_tactical` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_sks_tactical` | runtime_fallback | `furniture_kit:default_sks_tactical` |
| platform_furniture_kit | `furniture_kit:default_spas_12` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_spas_12` | runtime_fallback | `furniture_kit:default_spas_12` |
| platform_furniture_kit | `furniture_kit:default_spr15hb` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_spr15hb` | runtime_fallback | `furniture_kit:default_spr15hb` |
| platform_furniture_kit | `furniture_kit:default_springfield1873` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_springfield1873` | runtime_fallback | `furniture_kit:default_springfield1873` |
| platform_furniture_kit | `furniture_kit:default_taurus500` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_taurus500` | runtime_fallback | `furniture_kit:default_taurus500` |
| platform_furniture_kit | `furniture_kit:default_timeless50` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_timeless50` | runtime_fallback | `furniture_kit:default_timeless50` |
| platform_furniture_kit | `furniture_kit:default_type_81` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_type_81` | runtime_fallback | `furniture_kit:default_type_81` |
| platform_furniture_kit | `furniture_kit:default_uzi` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_uzi` | runtime_fallback | `furniture_kit:default_uzi` |
| platform_furniture_kit | `furniture_kit:default_vector45` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=default_vector45` | runtime_fallback | `furniture_kit:default_vector45` |
| platform_furniture_kit | `furniture_kit:fal` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=fal` | runtime_fallback | `furniture_kit:fal` |
| platform_furniture_kit | `furniture_kit:glock` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=glock` | runtime_fallback | `furniture_kit:glock` |
| platform_furniture_kit | `furniture_kit:m1911` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=m1911` | runtime_fallback | `furniture_kit:m1911` |
| platform_furniture_kit | `furniture_kit:m9` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=m9` | runtime_fallback | `furniture_kit:m9` |
| platform_furniture_kit | `furniture_kit:mp5` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=mp5` | runtime_fallback | `furniture_kit:mp5` |
| platform_furniture_kit | `furniture_kit:p90` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=p90` | runtime_fallback | `furniture_kit:p90` |
| platform_furniture_kit | `furniture_kit:ump` | `tacz:gun_component ; industry_part_kind=furniture_kit ; industry_platform=ump` | runtime_fallback | `furniture_kit:ump` |
| projectile_core | `projectile:12g:shot` | `tacz:projectile_core ; cartridge_caliber=12g ; projectile_type=shot ; industry_part_kind=projectile` | placeholder | `projectile:12g:shot` |
| projectile_core | `projectile:40mm:he` | `tacz:projectile_core ; cartridge_caliber=40mm ; projectile_type=he ; industry_part_kind=projectile` | runtime_fallback | `projectile:40mm:he` |
| projectile_core | `projectile:rpg_rocket:heat` | `tacz:projectile_core ; cartridge_caliber=rpg_rocket ; projectile_type=heat ; industry_part_kind=projectile` | runtime_fallback | `projectile:rpg_rocket:heat` |
| shared_ammunition_intermediate | `ammunition:case_blank` | `tacz:cartridge_case_blank ; industry_part_kind=case_blank ; industry_platform=ammunition` | runtime_fallback | `ammunition:case_blank` |
| shared_ammunition_intermediate | `ammunition:case_die_blank` | `tacz:press_die ; industry_part_kind=case_die_blank ; industry_platform=ammunition` | runtime_fallback | `ammunition:case_die_blank` |
| shared_ammunition_intermediate | `ammunition:projectile_blank` | `tacz:projectile_blank ; industry_part_kind=projectile_blank ; industry_platform=ammunition` | runtime_fallback | `ammunition:projectile_blank` |
| shared_ammunition_intermediate | `ammunition:projectile_die_blank` | `tacz:press_die ; industry_part_kind=projectile_die_blank ; industry_platform=ammunition` | runtime_fallback | `ammunition:projectile_die_blank` |
| shared_gun_intermediate | `machining:barrel_blank` | `tacz:gun_component_blank ; industry_part_kind=barrel_blank ; industry_platform=machining` | runtime_fallback | `machining:barrel_blank` |
| shared_gun_intermediate | `machining:bolt_blank` | `tacz:gun_component_blank ; industry_part_kind=bolt_blank ; industry_platform=machining` | runtime_fallback | `machining:bolt_blank` |
| shared_gun_intermediate | `machining:furniture_blank` | `tacz:gun_component_blank ; industry_part_kind=furniture_blank ; industry_platform=machining` | runtime_fallback | `machining:furniture_blank` |
| shared_gun_intermediate | `machining:receiver_blank` | `tacz:gun_component_blank ; industry_part_kind=receiver_blank ; industry_platform=machining` | runtime_fallback | `machining:receiver_blank` |
| shared_gun_intermediate | `machining:recoil_blank` | `tacz:gun_component_blank ; industry_part_kind=recoil_blank ; industry_platform=machining` | runtime_fallback | `machining:recoil_blank` |
| shared_gun_intermediate | `machining:trigger_blank` | `tacz:gun_component_blank ; industry_part_kind=trigger_blank ; industry_platform=machining` | runtime_fallback | `machining:trigger_blank` |
| spent_cartridge_case | `spent_case:12g` | `tacz:cartridge_case ; cartridge_caliber=12g ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_shotgun` |
| spent_cartridge_case | `spent_case:22wmr` | `tacz:cartridge_case ; cartridge_caliber=22wmr ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rimfire` |
| spent_cartridge_case | `spent_case:308` | `tacz:cartridge_case ; cartridge_caliber=308 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_brass` |
| spent_cartridge_case | `spent_case:30_06` | `tacz:cartridge_case ; cartridge_caliber=30_06 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_brass` |
| spent_cartridge_case | `spent_case:338` | `tacz:cartridge_case ; cartridge_caliber=338 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_brass` |
| spent_cartridge_case | `spent_case:357mag` | `tacz:cartridge_case ; cartridge_caliber=357mag ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_pistol_brass` |
| spent_cartridge_case | `spent_case:40mm` | `tacz:cartridge_case ; cartridge_caliber=40mm ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_bigbore` |
| spent_cartridge_case | `spent_case:45_70` | `tacz:cartridge_case ; cartridge_caliber=45_70 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_bigbore` |
| spent_cartridge_case | `spent_case:45acp` | `tacz:cartridge_case ; cartridge_caliber=45acp ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_pistol_brass` |
| spent_cartridge_case | `spent_case:46x30` | `tacz:cartridge_case ; cartridge_caliber=46x30 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_bottleneck` |
| spent_cartridge_case | `spent_case:500mag` | `tacz:cartridge_case ; cartridge_caliber=500mag ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_bigbore` |
| spent_cartridge_case | `spent_case:50ae` | `tacz:cartridge_case ; cartridge_caliber=50ae ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_pistol_brass` |
| spent_cartridge_case | `spent_case:50bmg` | `tacz:cartridge_case ; cartridge_caliber=50bmg ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_50bmg` |
| spent_cartridge_case | `spent_case:545x39` | `tacz:cartridge_case ; cartridge_caliber=545x39 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_steel` |
| spent_cartridge_case | `spent_case:556x45` | `tacz:cartridge_case ; cartridge_caliber=556x45 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_brass` |
| spent_cartridge_case | `spent_case:57x28` | `tacz:cartridge_case ; cartridge_caliber=57x28 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_bottleneck` |
| spent_cartridge_case | `spent_case:58x42` | `tacz:cartridge_case ; cartridge_caliber=58x42 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_steel` |
| spent_cartridge_case | `spent_case:68x51fury` | `tacz:cartridge_case ; cartridge_caliber=68x51fury ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_brass` |
| spent_cartridge_case | `spent_case:762x25` | `tacz:cartridge_case ; cartridge_caliber=762x25 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_pistol_brass` |
| spent_cartridge_case | `spent_case:762x39` | `tacz:cartridge_case ; cartridge_caliber=762x39 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_steel` |
| spent_cartridge_case | `spent_case:762x54` | `tacz:cartridge_case ; cartridge_caliber=762x54 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_steel` |
| spent_cartridge_case | `spent_case:792x57` | `tacz:cartridge_case ; cartridge_caliber=792x57 ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_rifle_brass` |
| spent_cartridge_case | `spent_case:9mm` | `tacz:cartridge_case ; cartridge_caliber=9mm ; industry_part_kind=spent_case` | placeholder | `spent_variant:casing_pistol_brass` |
| static_industrial_item | `static:tacz:magazine_blank` | `tacz:magazine_blank` | runtime_fallback | `static:tacz:magazine_blank` |
| static_industrial_item | `static:tacz:magazine_loader` | `tacz:magazine_loader` | runtime_fallback | `static:tacz:magazine_loader` |
| static_industrial_item | `static:tacz:magazine_pouch` | `tacz:magazine_pouch` | runtime_fallback | `static:tacz:magazine_pouch` |
| visible_projectile_intermediate | `projectile_body:40mm` | `tacz:projectile_blank ; cartridge_caliber=40mm ; industry_part_kind=projectile_body_40mm` | runtime_fallback | `projectile_body:40mm` |
| visible_projectile_intermediate | `projectile_body:rpg_rocket` | `tacz:projectile_blank ; cartridge_caliber=rpg_rocket ; industry_part_kind=projectile_body_rpg_rocket` | runtime_fallback | `projectile_body:rpg_rocket` |
| visible_projectile_intermediate | `projectile_payload:40mm:1` | `tacz:projectile_blank ; cartridge_caliber=40mm ; industry_part_kind=projectile_payload_40mm_1` | runtime_fallback | `projectile_payload:40mm:1` |
| visible_projectile_intermediate | `projectile_payload:rpg_rocket:1` | `tacz:projectile_blank ; cartridge_caliber=rpg_rocket ; industry_part_kind=projectile_payload_rpg_rocket_1` | runtime_fallback | `projectile_payload:rpg_rocket:1` |
| visible_projectile_intermediate | `projectile_payload:rpg_rocket:2` | `tacz:projectile_blank ; cartridge_caliber=rpg_rocket ; industry_part_kind=projectile_payload_rpg_rocket_2` | runtime_fallback | `projectile_payload:rpg_rocket:2` |

## 已提供但尚未绑定的图

这些 PNG 已嵌入运行时 `tacz_extra` 命名空间，但当前默认工业数据没有对应的实际身份。
它们保留给以后新增物理供弹器或第三方映射，未被强行套到不匹配的枪械上。

| 纹理 |
| --- |
| `tacz_extra:item/bullet_ap` |
| `tacz_extra:item/bullet_hp` |
| `tacz_extra:item/bullet_lead` |
| `tacz_extra:item/bullet_rimfire` |
| `tacz_extra:item/bullet_tracer` |
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
