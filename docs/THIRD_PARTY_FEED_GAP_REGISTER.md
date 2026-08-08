# 第三方供弹细分材质 / 功能缺口登记册

此文件由 `tools/generate_third_party_feed_gap_register.py` 生成，供作者和 CI 使用；
普通玩家不需要运行 Python。它不会启用任何 `gun_feed`，只登记当前已经审计的数据中：

- 没有精确细分材质、仍使用中性/家族级材料的实体载具；
- 已有事实 profile、但故意保持 `legacy` runtime 的枪械功能缺口。

机器可读的完整逐 family / 逐枪记录位于：

```text
tools/industry/third_party_feed_gap_registry.json
```

## 状态定义

| 状态 | 含义 |
|---|---|
| `exact_existing_material` | 有当前 family 的精确既有材料映射。 |
| `family_level_material` | 复用同类材料（如 exposed belt），不声称精确网格。 |
| `neutral_generic_material` | 使用中性通用弹匣 / belt-box 材料；功能真实，细分美术仍待补。 |
| `legacy` function record | 事实已记录，但没有足够证据启用物理 carrier / 专用 reload route。 |

## 按命名空间汇总

| Namespace | 实体 family | 缺细分材质 family | 缺细分功能枪数 |
|---|---:|---:|---:|
| `bf1` | 11 | 11 | 19 |
| `ccrp` | 110 | 110 | 21 |
| `cib` | 66 | 64 | 22 |
| `cibs` | 14 | 12 | 2 |
| `classicr` | 25 | 24 | 7 |
| `hamster` | 5 | 5 | 23 |
| `murasamet` | 21 | 16 | 48 |
| `rainforest` | 12 | 10 | 2 |
| `suffuse` | 31 | 30 | 13 |
| `wemql_r` | 8 | 5 | 0 |
| `ww` | 32 | 32 | 9 |

## 当前需要补细分材质的 family

下列条目没有 `exact_existing_material`。`gun_ids` 是受影响的已审计接收机；
它们的服务器库存、容量与制造出口已经生效，缺的是细分授权美术，而不是功能。

| Family | Ammo | Mechanism | Capacities | 当前材料状态 |
|---|---|---|---|---|
| `aug_556` | `tacz:556x45` | `detachable_magazine` | 30, 40, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `bf1_chauchat_3006` | `tacz:30_06` | `detachable_magazine` | 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `bf1_de_lisle_45acp` | `tacz:45acp` | `detachable_magazine` | 7, 9, 12, 14 | `neutral_generic_material` → `tacz:item/magazine` |
| `bf1_lewis_308_pan` | `tacz:308` | `detachable_magazine` | 47, 97 | `neutral_generic_material` → `tacz:item/magazine` |
| `bf1_m1916_308` | `tacz:308` | `detachable_magazine` | 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `bf1_mg0815_762x54_belt` | `tacz:762x54` | `belt` | 200, 250 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `bf1_mg42_762x54_belt` | `tacz:762x54` | `belt` | 50, 75, 250 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `bf1_smg0818_9x19_drum` | `tacz:9mm` | `detachable_magazine` | 80 | `family_level_material` → `tacz_extra:item/mag_rpk_drum` |
| `bf1_vg15_762x39` | `tacz:762x39` | `detachable_magazine` | 10, 30, 40, 80 | `neutral_generic_material` → `tacz:item/magazine` |
| `bf1_vp1915_9x19_twin` | `tacz:9mm` | `detachable_magazine` | 50, 60, 80, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `bf1_welrod_9x19` | `tacz:9mm` | `detachable_magazine` | 6 | `neutral_generic_material` → `tacz:item/magazine` |
| `bf1_zk383_9x19` | `tacz:9mm` | `detachable_magazine` | 30, 32, 35, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_a545_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 45, 60, 95 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aac_honeybadger_300blk` | `tacz:300blk` | `detachable_magazine` | 30, 32, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_af2011_45acp` | `tacz:45acp` | `detachable_magazine` | 7, 9, 12, 14 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aics_m700_65cm` | `ccrp:65cm` | `detachable_magazine` | 5, 6, 10, 12 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ak103_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 40, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ak47_spent_bullet_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 34, 37, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ak74_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ak74m_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aks74u_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 35, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_am17_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 35, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_apc_9k_pro_g_9mm` | `tacz:9mm` | `detachable_magazine` | 25, 40, 45, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ar57_57x28` | `tacz:57x28` | `detachable_magazine` | 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aug_a3_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 42, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aug_a3_dmr_556x45` | `tacz:556x45` | `detachable_magazine` | 10, 20, 30, 42 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aug_a3_m2kit_556x45_m995` | `ccrp:556x45_m995` | `detachable_magazine` | 30, 42, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aug_a3s_300blk` | `tacz:300blk` | `detachable_magazine` | 30, 45, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aug_camg_kit_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 42, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aug_hbar_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 42, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_aug_para_9mm` | `tacz:9mm` | `detachable_magazine` | 25, 32, 35, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_block_17_9mm` | `tacz:9mm` | `detachable_magazine` | 17, 20, 25, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_brn_180_bullpup_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 32, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_camg_m4_sopmod2_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_camg_mk18_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_car_15_556x45` | `tacz:556x45` | `detachable_magazine` | 20, 30, 32, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_chisato_m1911_45acp` | `tacz:45acp` | `detachable_magazine` | 7, 12, 18, 22 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_cr300_300blk` | `tacz:300blk` | `detachable_magazine` | 20, 30, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ddm4_556x45_m995` | `ccrp:556x45_m995` | `detachable_magazine` | 30, 35, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ddm4_pdw_300blk` | `tacz:300blk` | `detachable_magazine` | 20, 32, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ddm4_v7_pro_556x45_m855a2_f` | `ccrp:556x45_m855a2_f` | `detachable_magazine` | 32, 35, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ddm4a1_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_deagle_nightingale_44mag` | `ccrp:44mag` | `detachable_magazine` | 7, 8, 10, 12 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_deagle_xix_50ae` | `tacz:50ae` | `detachable_magazine` | 7, 8, 10, 12 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_dsa_sa58_308` | `tacz:308` | `detachable_magazine` | 15, 20, 30, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_f90_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 42, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_f90_mbr_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_fightlite_scr_hg_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_g95a1_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_glock20gen5mos_10mm` | `tacz:10mm` | `detachable_magazine` | 15, 33, 50, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_glock40gen5mos_10mm` | `tacz:10mm` | `detachable_magazine` | 15, 33, 50, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_hk21_308_belt` | `tacz:308` | `belt` | 200 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `ccrp_hk416_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_hk416_sopmod_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_hk416a8_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_hk416c_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_hk417_308` | `tacz:308` | `detachable_magazine` | 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_hk433_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_hk_g28_308` | `tacz:308` | `detachable_magazine` | 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_hk_g28_patrol_308` | `tacz:308` | `detachable_magazine` | 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_honeybadger_300blk` | `tacz:300blk` | `detachable_magazine` | 30, 32, 35, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_kac_ks_1_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 32, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_keltec_p50_57x28` | `tacz:57x28` | `detachable_magazine` | 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_km_ak74m_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 45, 60, 95 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m110_308` | `tacz:308` | `detachable_magazine` | 15, 25, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m110a3_65cm` | `ccrp:65cm` | `detachable_magazine` | 15, 20, 25, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m14_hbar_308` | `tacz:308` | `detachable_magazine` | 20, 30, 50, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m16a3_556x45` | `tacz:556x45` | `detachable_magazine` | 20, 30, 32, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m231_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 33, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m249_saw_556x45_belt` | `tacz:556x45` | `belt` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `ccrp_m27_iar_556x45` | `tacz:556x45` | `detachable_magazine` | 40, 50, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m305a_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 45, 50, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m39_emr_308` | `tacz:308` | `detachable_magazine` | 10, 20, 25, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m4_cqbr_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m4_sopmod2_fsp_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_m4_ss_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mcx_spear_tombstone_68x51fury` | `tacz:68x51fury` | `detachable_magazine` | 20, 22, 25, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mcx_virtus_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mg36_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mk13_mod5_300wm` | `ccrp:300wm` | `detachable_magazine` | 5, 6, 10, 12 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mk17_308` | `tacz:308` | `detachable_magazine` | 20, 22, 25, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mk18_mjolnir_338` | `tacz:338` | `detachable_magazine` | 7, 10, 12, 15 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mk556_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mp5_sd_9mm` | `tacz:9mm` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mp5k_9mm` | `tacz:9mm` | `detachable_magazine` | 15, 30, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mp5k_pdw_9mm` | `tacz:9mm` | `detachable_magazine` | 30, 50, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mp7a3_46x30` | `tacz:46x30` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_mpx_9mm` | `tacz:9mm` | `detachable_magazine` | 25, 30, 35, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_p90_effen_90_57x28` | `tacz:57x28` | `detachable_magazine` | 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_p90_paw_57x28` | `tacz:57x28` | `detachable_magazine` | 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_p90_shround_s_57x28` | `tacz:57x28` | `detachable_magazine` | 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_psa_ak556_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 32, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_qbu_191_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_qbz_191_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 45, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_rd704_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 35, 40, 45 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_ro635_9mm` | `tacz:9mm` | `detachable_magazine` | 30, 35, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_rpk74m_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 45, 60, 95 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_rpk_203_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 40, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_sai_gry_lite_black_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_samurai_edge_45acp` | `tacz:45acp` | `detachable_magazine` | 20, 23, 26, 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_scar16_ariana_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 32, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_scar17_armarise_308` | `tacz:308` | `detachable_magazine` | 10, 15, 20, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_scar_16s_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 32, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_scar_17s_65cm` | `ccrp:65cm` | `detachable_magazine` | 10, 15, 20, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_scar_sc_300blk` | `tacz:300blk` | `detachable_magazine` | 30, 45, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_shield_ots33_9mm` | `tacz:9mm` | `detachable_magazine` | 18, 23, 27, 33 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_sig277_68x51fury` | `tacz:68x51fury` | `detachable_magazine` | 20, 25, 30, 45 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_solgw_mk1_556x45_m855a1` | `ccrp:556x45_m855a1` | `detachable_magazine` | 20, 30, 32, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_sr25_308` | `tacz:308` | `detachable_magazine` | 15, 25, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_sr_3m_9x39` | `tacz:9x39` | `detachable_magazine` | 30, 35, 45, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_timeless_50_50ae` | `tacz:50ae` | `detachable_magazine` | 7, 8, 10, 14 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_troy_m14_sass_308` | `tacz:308` | `detachable_magazine` | 20, 30, 50, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_tti_mpx_9mm` | `tacz:9mm` | `detachable_magazine` | 30, 36, 50, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_tti_tr1_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 32, 60, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_type_95_longbow_58x42` | `tacz:58x42` | `detachable_magazine` | 60, 75, 100, 150 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_type_97_gen2_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_uzi45_45acp` | `tacz:45acp` | `detachable_magazine` | 20, 32, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_vector10_10mm` | `tacz:10mm` | `detachable_magazine` | 15, 33, 50, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_x95_smg_9mm` | `tacz:9mm` | `detachable_magazine` | 32, 38, 45, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_x95r_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 45, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_zenit_ak104_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 40, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `ccrp_zenit_ak105_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_9a91_9x39mm` | `cib:9x39mm` | `detachable_magazine` | 20, 30, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_ak105_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_ak24_556` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_asval_9x39mm` | `cib:9x39mm` | `detachable_magazine` | 20, 30, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_awp_308` | `tacz:308` | `detachable_magazine` | 5, 10, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_cs_ak47_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 45, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_cslr3_58x42` | `tacz:58x42` | `detachable_magazine` | 10, 12, 13, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_cslr4_308` | `tacz:308` | `detachable_magazine` | 5, 10, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_evo3_9mm` | `tacz:9mm` | `detachable_magazine` | 20, 30, 45, 65 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_fal_308` | `tacz:308` | `detachable_magazine` | 20, 30, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_g18c_9mm` | `tacz:9mm` | `detachable_magazine` | 20, 30, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_g19_9mm` | `tacz:9mm` | `detachable_magazine` | 15, 17, 19, 33 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_g3sg1_308` | `tacz:308` | `detachable_magazine` | 20, 22, 25, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_galil_556x45` | `tacz:556x45` | `detachable_magazine` | 35, 45, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_galilace32_762x39` | `tacz:762x39` | `detachable_magazine` | 35, 45, 55, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_galilace_556x45` | `tacz:556x45` | `detachable_magazine` | 35, 45, 55, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_hk433_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_js9_9mm` | `tacz:9mm` | `detachable_magazine` | 20, 30, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_k2_556x45` | `tacz:556x45` | `detachable_magazine` | 20, 30, 35, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_k2c1_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_la89_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 40, 50, 65 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_m16a4_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_m99_127x108` | `cib:127x108` | `detachable_magazine` | 5, 10, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_mg3_308_belt` | `tacz:308` | `belt` | 65, 75, 100, 150 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `cib_negev_556x45_belt` | `tacz:556x45` | `belt` | 65, 75, 100, 150 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `cib_origin12_12g` | `tacz:12g` | `detachable_magazine` | 8, 10, 20, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_ots14_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_p250_9mm` | `tacz:9mm` | `detachable_magazine` | 13, 15, 20, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_pkp_762x54_belt` | `tacz:762x54` | `belt` | 70, 100, 150, 200 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `cib_pm9_9mm` | `tacz:9mm` | `detachable_magazine` | 25, 30, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_pp19_9mm` | `tacz:9mm` | `detachable_magazine` | 50, 53, 60, 64 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_ppk_32acp` | `cib:32acp` | `detachable_magazine` | 7, 9, 12, 14 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_ppsh41_762x25` | `tacz:762x25` | `detachable_magazine` | 20, 35, 45, 71 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbu10_127x108` | `cib:127x108` | `detachable_magazine` | 5, 10, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbu191_58x42` | `tacz:58x42` | `detachable_magazine` | 10, 15, 20, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbu201_127x108` | `cib:127x108` | `detachable_magazine` | 5, 10, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbu202_338` | `tacz:338` | `detachable_magazine` | 5, 8, 10, 15 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbu203_308` | `tacz:308` | `detachable_magazine` | 5, 10, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbu88_58x42` | `tacz:58x42` | `detachable_magazine` | 10, 15, 20, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbz03_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 35, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbz191_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbz192_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbz951_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 35, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qbz95b1_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 35, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qcq171_9mm` | `tacz:9mm` | `detachable_magazine` | 30, 35, 40, 70 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qcw05_58x21` | `cib:58x21` | `detachable_magazine` | 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qjb951_58x42` | `tacz:58x42` | `detachable_magazine` | 75, 80, 85, 90 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qjy201_308_belt` | `tacz:308` | `belt` | 100, 150, 200, 250 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `cib_qjy88_58x42_belt` | `tacz:58x42` | `belt` | 90, 100, 150, 200 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `cib_qjz171_127x108_belt` | `cib:127x108` | `belt` | 60, 80, 90, 100 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `cib_qsz193_9mm` | `tacz:9mm` | `detachable_magazine` | 7, 9, 10, 11 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_qsz92_58x21` | `cib:58x21` | `detachable_magazine` | 20, 23, 25, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_sig552_556x45` | `tacz:556x45` | `detachable_magazine` | 20, 30, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_sig556_556` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_ssg08_308` | `tacz:308` | `detachable_magazine` | 5, 8, 10, 15 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_sv98_762x54` | `tacz:762x54` | `detachable_magazine` | 10, 15, 20, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_svd_762x54` | `tacz:762x54` | `detachable_magazine` | 10 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_t91_556x45` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_type14_8x22` | `cib:8x22` | `detachable_magazine` | 8 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_type20_556` | `tacz:556x45` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_type56_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 45, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_type79_762x25` | `tacz:762x25` | `detachable_magazine` | 20, 30, 35, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_usas12_12g` | `tacz:12g` | `detachable_magazine` | 10, 15, 20, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `cib_usp_45acp` | `tacz:45acp` | `detachable_magazine` | 12, 15, 20, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_ak12_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 35, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_b93r_9mm` | `tacz:9mm` | `detachable_magazine` | 15, 24, 27, 33 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_dp28_pan_762x54r` | `tacz:762x54r` | `detachable_magazine` | 47 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_fal_308` | `tacz:308` | `detachable_magazine` | 20, 25, 30, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_glock18_9mm` | `tacz:9mm` | `detachable_magazine` | 17, 24, 27, 33 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_m1a1_thompson_45acp` | `tacz:45acp` | `detachable_magazine` | 30, 40, 45, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_m60_308_belt` | `tacz:308` | `belt` | 100, 150, 175, 200 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `classicr_m82a2_50bmg` | `tacz:50bmg` | `detachable_magazine` | 8, 10, 12, 15 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_m92fs_9mm` | `tacz:9mm` | `detachable_magazine` | 15, 17, 20, 24 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_mac10_9mm` | `tacz:9mm` | `detachable_magazine` | 30, 35, 40, 45 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_mk47_762x39copper` | `tacz:762x39copper` | `detachable_magazine` | 30, 45, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_mp7_46x30` | `tacz:46x30` | `detachable_magazine` | 20, 30, 40, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_mp9_9mm` | `tacz:9mm` | `detachable_magazine` | 30, 34, 37, 46 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_mrad_338` | `tacz:338` | `detachable_magazine` | 10, 12, 13, 15 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_mrad_elr_416barrett` | `tacz:416barrett` | `detachable_magazine` | 10, 12, 13, 15 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_msr_3006` | `tacz:30_06` | `detachable_magazine` | 10 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_ngsw_r_68x51` | `tacz:68x51fury` | `detachable_magazine` | 20, 25, 35, 45 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_qbz191_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 45, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_scar_mk20_308` | `tacz:308` | `detachable_magazine` | 10, 20, 30, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_spr15_556` | `tacz:556x45` | `detachable_magazine` | 20, 25, 38, 45 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_sti2011_9mm` | `tacz:9mm` | `detachable_magazine` | 17, 19, 23, 27 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_tec9_9mm` | `tacz:9mm` | `detachable_magazine` | 17, 24, 33, 72 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_tti_g34_9mm` | `tacz:9mm` | `detachable_magazine` | 19, 24, 27, 33 | `neutral_generic_material` → `tacz:item/magazine` |
| `classicr_udp9_9mm` | `tacz:9mm` | `detachable_magazine` | 22, 30, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `hamster_lugerp08_compact_8` | `hamster:compact_ammo` | `detachable_magazine` | 8 | `neutral_generic_material` → `tacz:item/magazine` |
| `hamster_madsen_long_30` | `hamster:long_ammo` | `detachable_magazine` | 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `hamster_makarov_compact_8` | `hamster:compact_ammo` | `detachable_magazine` | 8 | `neutral_generic_material` → `tacz:item/magazine` |
| `hamster_mg1417_long_belt` | `hamster:long_ammo` | `belt` | 100 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `hamster_mp18_compact_32` | `hamster:compact_ammo` | `detachable_magazine` | 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `m14_308` | `tacz:308` | `detachable_magazine` | 10, 20, 30, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_boys_50bmg` | `tacz:50bmg` | `detachable_magazine` | 5 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_dp_pan_762x54` | `tacz:762x54` | `detachable_magazine` | 47 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_kp31_9mm` | `tacz:9mm` | `detachable_magazine` | 70 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_lanchester_9mm` | `tacz:9mm` | `detachable_magazine` | 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_m3_45acp` | `tacz:45acp` | `detachable_magazine` | 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_mg34_792x57_belt` | `tacz:792x57` | `belt` | 75 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `murasamet_mg42_792x57_belt` | `tacz:792x57` | `belt` | 75 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `murasamet_mp28_9mm` | `tacz:9mm` | `detachable_magazine` | 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_mp38_40_9mm` | `tacz:9mm` | `detachable_magazine` | 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_ppsh_762x25` | `tacz:762x25` | `detachable_magazine` | 25, 35, 50, 71 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_sten_9mm` | `tacz:9mm` | `detachable_magazine` | 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_thompson_drum_45acp` | `tacz:45acp` | `detachable_magazine` | 20, 30, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_tt33_762x25` | `tacz:762x25` | `detachable_magazine` | 8 | `neutral_generic_material` → `tacz:item/magazine` |
| `murasamet_vz61_9mm` | `tacz:9mm` | `detachable_magazine` | 20, 25, 35, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `rainforest_em2_308` | `tacz:308` | `detachable_magazine` | 20, 25, 30, 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `rainforest_fal_308` | `tacz:308` | `detachable_magazine` | 20, 25, 30, 32 | `family_level_material` → `tacz_extra:item/magazine_fal_308_20_tacz_308` |
| `rainforest_famas_556` | `tacz:556x45` | `detachable_magazine` | 25, 30, 32, 35 | `neutral_generic_material` → `tacz:item/magazine` |
| `rainforest_frf2_308` | `tacz:308` | `detachable_magazine` | 5, 7, 9, 10 | `neutral_generic_material` → `tacz:item/magazine` |
| `rainforest_m60_308_belt` | `tacz:308` | `belt` | 20, 35, 50 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `rainforest_pm12s_9x19` | `tacz:9mm` | `detachable_magazine` | 20, 25, 30, 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `rainforest_pm63_9x19` | `tacz:9mm` | `detachable_magazine` | 15, 25, 30, 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `rainforest_rpd_762x39_belt` | `tacz:762x39` | `belt` | 35, 45, 50, 75, 100 | `family_level_material` → `tacz_extra:item/mag_rpk_drum` |
| `rainforest_vz64_9x19` | `tacz:9mm` | `detachable_magazine` | 10, 12, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `rainforest_vz68_9x19` | `tacz:9mm` | `detachable_magazine` | 10, 12, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `scar_h_308` | `tacz:308` | `detachable_magazine` | 20, 30, 45, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_aks74u_545x39` | `suffuse:545x39` | `detachable_magazine` | 30, 34, 37, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_ar57_57x28` | `tacz:57x28` | `detachable_magazine` | 34, 37, 40, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_ash12_127x55` | `suffuse:12.7x55` | `detachable_magazine` | 10, 15, 20, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_aw50_50bmg` | `tacz:50bmg` | `detachable_magazine` | 5, 6, 8, 10 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_axmc_axsr_338` | `tacz:338` | `detachable_magazine` | 10, 12, 14, 16 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_dvl10_308` | `tacz:308` | `detachable_magazine` | 10, 12, 14, 16 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_gepardpdw_9mm` | `tacz:9mm` | `detachable_magazine` | 40, 43, 45, 47 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_gm6_50bmg` | `tacz:50bmg` | `detachable_magazine` | 6, 8, 10 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_kacpdw_6x35` | `suffuse:6x35mm` | `detachable_magazine` | 30, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_m200_408ct` | `suffuse:.408ct` | `detachable_magazine` | 6, 7, 8, 10 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_mas38_7_65x20mm` | `suffuse:7.65x20mm` | `detachable_magazine` | 32 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_mpdr_556` | `tacz:556x45` | `detachable_magazine` | 20, 40, 50, 60 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_np762_762x25` | `tacz:762x25` | `detachable_magazine` | 10, 12, 15, 17 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_pkp_762x54_belt` | `tacz:762x54` | `belt` | 120 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `suffuse_qbu191_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 35, 50, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_qbz191_58x42` | `tacz:58x42` | `detachable_magazine` | 20, 30, 40, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_qbz192_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 35, 50, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_qbz951_58x42` | `tacz:58x42` | `detachable_magazine` | 30, 35, 50, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_rm277_68tvcm` | `suffuse:6.8tvcm` | `detachable_magazine` | 20, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_saddam_ak_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 34, 37, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_svd_762x54` | `tacz:762x54` | `detachable_magazine` | 10, 12, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_tec9_9mm` | `tacz:9mm` | `detachable_magazine` | 15, 20, 30, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_tt33_762x25` | `tacz:762x25` | `detachable_magazine` | 7, 10, 13, 16 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_tti2011_9mm` | `tacz:9mm` | `detachable_magazine` | 12, 20, 25, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_ump45_45acp` | `tacz:45acp` | `detachable_magazine` | 20, 25, 30, 50 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_usp45_45acp` | `tacz:45acp` | `detachable_magazine` | 12, 15, 18 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_usp45_black_45acp` | `tacz:45acp` | `detachable_magazine` | 12, 15, 18 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_viper2011_9mm` | `tacz:9mm` | `detachable_magazine` | 12, 15, 20, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_webley1913_45acp` | `tacz:45acp` | `detachable_magazine` | 7, 10, 13, 16 | `neutral_generic_material` → `tacz:item/magazine` |
| `suffuse_xm7_308` | `tacz:308` | `detachable_magazine` | 20, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `wemql_r_ak12_545x39` | `tacz:545x39` | `detachable_magazine` | 30, 40, 60, 75 | `neutral_generic_material` → `tacz:item/magazine` |
| `wemql_r_m7_68x51` | `tacz:68x51fury` | `detachable_magazine` | 20, 25, 30, 45 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_anm2_30_06_belt` | `tacz:30_06` | `belt` | 100, 150, 250, 500 | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| `ww_as44_762x39` | `tacz:762x39` | `detachable_magazine` | 30, 34, 37, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_bar_3006` | `tacz:30_06` | `detachable_magazine` | 20, 30, 35, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_cph_32acp` | `ea:32acp` | `detachable_magazine` | 7, 9, 12, 14 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_dp28_762x54` | `tacz:762x54` | `detachable_magazine` | 47 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_g43_792x57` | `ea:792x57` | `detachable_magazine` | 10, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_m1911a1_45acp` | `tacz:45acp` | `detachable_magazine` | 7, 9, 12, 14 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_m1919a6_3006_belt` | `tacz:30_06` | `belt` | 100, 150, 250, 500 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `ww_m1_m2_carbine_30c` | `ww:30c` | `detachable_magazine` | 15, 20, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_m2s_30c` | `ww:30c` | `detachable_magazine` | 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_m50_45acp` | `tacz:45acp` | `detachable_magazine` | 20, 21, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_m712_763` | `ww:763` | `detachable_magazine` | 10, 20, 25, 30 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_mg34_792x57_belt` | `ea:792x57` | `belt` | 50, 100, 150, 200 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `ww_mg42_792x57_belt` | `ea:792x57` | `belt` | 50, 100, 150, 200 | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| `ww_mp28_9mm` | `tacz:9mm` | `detachable_magazine` | 30, 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_mp34_9mm` | `tacz:9mm` | `detachable_magazine` | 32, 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_mp38_40_41_9mm` | `tacz:9mm` | `detachable_magazine` | 32, 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_p08_765` | `ww:765` | `detachable_magazine` | 8, 19, 23, 27 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_p38_9mm` | `tacz:9mm` | `detachable_magazine` | 8, 19, 23, 27 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_pps_762x25` | `tacz:762x25` | `detachable_magazine` | 35, 71 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_s1100_763` | `ww:763` | `detachable_magazine` | 32, 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_sten_mk2_9mm` | `tacz:9mm` | `detachable_magazine` | 32, 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_stg44_792x33` | `ea:792x33` | `detachable_magazine` | 30, 34, 37, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_svt_avt_762x54` | `tacz:762x54` | `detachable_magazine` | 10, 15, 20 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_t100_8mm` | `ww:8mm` | `detachable_magazine` | 30, 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_t100l_8mm` | `ww:8mm` | `detachable_magazine` | 30, 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_t14_8mm` | `ww:8mm` | `detachable_magazine` | 8, 9, 12, 14 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_t20_3006` | `tacz:30_06` | `detachable_magazine` | 20, 21, 22, 25 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_t96_65a` | `ww:65a` | `detachable_magazine` | 30, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_t99_77a` | `ww:77a` | `detachable_magazine` | 30, 40 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_tbe_763` | `ww:763` | `detachable_magazine` | 50, 75, 100 | `neutral_generic_material` → `tacz:item/magazine` |
| `ww_thompson_45acp` | `tacz:45acp` | `detachable_magazine` | 20, 30 | `neutral_generic_material` → `tacz:item/magazine` |

## 当前保持 legacy 的功能记录

这些枪不是“未处理”：每条都已有明确事实 profile。只有补足真实物理 carrier、膛内/转轮状态或原包脚本 feed 点后，
才会从 legacy 转为 active，不允许用一张视觉弹匣替代该功能。

| GunId | 已知 device | Action | 原因 |
|---|---|---|---|
| `bf1:ef46` | `utility` | `flamethrower` | Fuel weapon remains legacy. |
| `bf1:f_faust` | `single_shot` | `launcher` | Launcher remains legacy. |
| `bf1:handgun` | `revolver` | `revolver` | Action-ambiguous handgun remains legacy until a cylinder route is audited. |
| `bf1:kolibri` | `unknown` | `surveyed_pistol` | Tiny pistol carrier form remains unproven. |
| `bf1:liu` | `stripper_clip` | `gas_operated_rifle` | Clip script route remains legacy until audited. |
| `bf1:lunge_mine` | `single_shot` | `launcher` | Single-use launcher remains legacy. |
| `bf1:m2_2` | `utility` | `flamethrower` | Fuel weapon remains legacy. |
| `bf1:man_m95` | `en_bloc_clip` | `bolt_action` | Mannlicher en-bloc behavior is recorded, but pack script insertion/ejection route is not yet audited. |
| `bf1:martini` | `single_shot` | `single_shot` | Single-shot rifle remains legacy. |
| `bf1:mhgl` | `single_shot` | `launcher` | Grenade launcher remains legacy. |
| `bf1:model10` | `tube` | `pump_action_shotgun` | Tube/loop route remains legacy. |
| `bf1:obrez` | `stripper_clip` | `bolt_action` | Clip script route remains legacy until audited. |
| `bf1:rorsch_mk4` | `unknown` | `surveyed_precision_rifle` | Custom Rorsch mechanism remains unproven. |
| `bf1:rsc1917` | `internal_box` | `gas_operated_rifle` | RSC 1917 fixed/internal implementation remains legacy until its exact carrier/clip route is audited. |
| `bf1:sjogren` | `tube` | `recoil_operated_shotgun` | Tube/loop route remains legacy. |
| `bf1:sw_model3` | `revolver` | `revolver` | Model 3 revolver remains legacy until speedloader route is audited. |
| `bf1:syringe` | `utility` | `utility` | Medical syringe is not a cartridge feed device. |
| `bf1:tg1918` | `single_shot` | `single_shot` | Single-shot anti-materiel implementation remains legacy. |
| `bf1:wex` | `utility` | `flamethrower` | Fuel weapon remains legacy. |
| `ccrp:camg_cheetah40` | `unknown` | `surveyed_smg` | Custom CAMG Cheetah 40 remains legacy pending a real feed identity audit. |
| `ccrp:camg_dexterous` | `unknown` | `surveyed_rifle` | Custom CAMG Dexerous platform has no sufficiently audited external-feed evidence. |
| `ccrp:camg_krait` | `unknown` | `surveyed_rifle` | Custom CAMG Krait platform has no sufficiently audited external-feed evidence. |
| `ccrp:camg_m1014` | `tube` | `gas_operated_shotgun` | Custom reload script owns a non-audited shotgun route; retain legacy tube semantics. |
| `ccrp:crow_and_egret` | `unknown` | `surveyed_action` | Action-ambiguous custom weapon remains legacy. |
| `ccrp:cslr_42a` | `unknown` | `surveyed_rifle` | CSLR-42A platform has no sufficiently audited detachable carrier evidence. |
| `ccrp:cslr_43a` | `unknown` | `surveyed_rifle` | CSLR-43A platform has no sufficiently audited detachable carrier evidence. |
| `ccrp:cslr_44` | `unknown` | `surveyed_rifle` | CSLR-44 platform has no sufficiently audited detachable carrier evidence. |
| `ccrp:lastwar` | `tube` | `pump_action_shotgun` | Loop/tube reload candidate remains legacy. |
| `ccrp:lmt_m203` | `single_shot` | `launcher` | 40 mm launcher remains legacy; no fabricated detachable carrier. |
| `ccrp:m1887_long` | `tube` | `lever_action` | Loop/tube reload candidate remains legacy. |
| `ccrp:m38_spr` | `unknown` | `surveyed_rifle` | M38 SPR platform has no sufficiently audited detachable carrier evidence. |
| `ccrp:m4_bolter` | `unknown` | `launcher` | Custom bolter/launcher remains legacy until its actual feed source is audited. |
| `ccrp:marlin_1895` | `tube` | `lever_action` | Loop/tube reload candidate remains legacy. |
| `ccrp:mp9_thunder` | `utility` | `energy_smg` | Thunder-cell weapon remains legacy; no cartridge magazine is fabricated. |
| `ccrp:msh41` | `unknown` | `surveyed_smg` | MSH-41 remains legacy until its real carrier form is established. |
| `ccrp:requiem` | `unknown` | `surveyed_action` | Action-ambiguous custom weapon remains legacy. |
| `ccrp:silence_meteor` | `unknown` | `surveyed_rifle` | Silence Meteor custom platform has no sufficiently audited detachable carrier evidence. |
| `ccrp:springfield1873_tube_mag` | `tube` | `lever_action` | Loop/tube reload candidate remains legacy. |
| `ccrp:type_192` | `unknown` | `surveyed_rifle` | Type 192 custom platform has no sufficiently audited detachable carrier evidence. |
| `ccrp:v308` | `unknown` | `surveyed_rifle` | V308 platform has no sufficiently audited detachable carrier evidence. |
| `cib:686` | `single_shot` | `break_action` | Two-round shotgun remains legacy; no fabricated detachable carrier. |
| `cib:881` | `unknown` | `surveyed_rifle` | Custom 881 platform has no sufficiently audited carrier evidence. |
| `cib:882` | `unknown` | `surveyed_rifle` | Custom 882 platform has no sufficiently audited carrier evidence. |
| `cib:ar2` | `utility` | `surveyed_energy_rifle` | Battery-fed AR2 remains legacy; no cartridge magazine is fabricated. |
| `cib:dprkrpg` | `single_shot` | `launcher` | RPG launcher remains legacy. |
| `cib:dzj08` | `single_shot` | `launcher` | Single-shot launcher remains legacy. |
| `cib:error` | `unknown` | `surveyed_unknown` | Invalid/error gun identity remains legacy. |
| `cib:hawk97_1` | `tube` | `pump_action_shotgun` | Loop/tube script route remains legacy. |
| `cib:hawk97_2` | `unknown` | `surveyed_shotgun` | Current evidence does not safely resolve tube versus detachable box. |
| `cib:mini` | `unknown` | `rotary` | Rotary/minigun source has no audited removable belt/box source. |
| `cib:nova` | `tube` | `pump_action_shotgun` | Loop/tube script route remains legacy. |
| `cib:origin12db` | `unknown` | `surveyed_shotgun` | High-capacity custom shotgun implementation remains legacy pending a real carrier audit. |
| `cib:qba221` | `unknown` | `surveyed_shotgun` | Shotgun box/tube mechanism remains unproven. |
| `cib:qba221_burst` | `unknown` | `surveyed_shotgun` | Custom burst shotgun mechanism remains unproven. |
| `cib:qbs09` | `tube` | `pump_action_shotgun` | Loop/tube script route remains legacy. |
| `cib:qjb201` | `unknown` | `surveyed_lmg` | Custom QJB201 script path remains legacy until actual feed point is audited. |
| `cib:qlu11` | `unknown` | `launcher` | Grenade launcher carrier mechanism remains unproven. |
| `cib:r8` | `revolver` | `revolver` | R8 is a revolver and remains legacy until a dedicated speedloader route is audited. |
| `cib:type11` | `unknown` | `surveyed_lmg` | Type11 source/feed form remains unproven. |
| `cib:type38` | `stripper_clip` | `bolt_action` | Type38 fixed box / clip route remains legacy until actual script feed is audited. |
| `cib:type73` | `unknown` | `surveyed_lmg` | Type73 feed form remains unproven. |
| `cib:widow` | `unknown` | `surveyed_shotgun` | Shotgun carrier form remains unproven. |
| `cibs:hawk97_2_gold` | `unknown` | `surveyed_shotgun` | Skin variant inherits unproven Hawk97_2 carrier form. |
| `cibs:qjb201_sf2403` | `unknown` | `surveyed_lmg` | Skin variant inherits unproven QJB201 script/feed route. |
| `classicr:aa410` | `unknown` | `surveyed_shotgun` | Current survey and accessible pack evidence do not safely distinguish its tube/box implementation; it remains legacy rather than receiving a guessed removable carrier. |
| `classicr:colt_python` | `revolver` | `revolver` | Colt Python is a cylinder revolver. The factual profile deliberately preserves legacy runtime behavior until a real speedloader/clear route is audited. |
| `classicr:kar98` | `stripper_clip` | `bolt_action` | Kar98 is a fixed internal box loaded by stripper clips. Its custom script remains legacy until an actual clip feed route is audited. |
| `classicr:m24_renewed` | `unknown` | `surveyed_precision_rifle` | The uploaded facts alone do not prove whether this renewed M24 implementation is fixed or detachable. It remains legacy rather than choosing from its name/manual bolt/capacity. |
| `classicr:mauser_c96` | `internal_box` | `recoil_operated_pistol` | Mauser C96 standard fixed internal box remains legacy; its source extension values do not convert it into a detachable carrier. |
| `classicr:mgl_40mm` | `revolver` | `revolver_grenade_launcher` | MGL is a rotary grenade launcher. It remains legacy until a dedicated physical cylinder/reload-route transaction is audited. |
| `classicr:minigun` | `unknown` | `rotary` | The survey does not establish an audited removable belt/box source for this rotary implementation. It remains legacy rather than inheriting a belt from its class or capacity. |
| `hamster:auto5` | `tube` | `recoil_operated_shotgun` | Tube shotgun route remains legacy. |
| `hamster:berthier` | `stripper_clip` | `bolt_action` | Berthier clip route remains legacy until script feed is audited. |
| `hamster:colt1873` | `revolver` | `revolver` | Revolver route remains legacy. |
| `hamster:coltm1851` | `revolver` | `revolver` | Revolver route remains legacy. |
| `hamster:coltm1892` | `revolver` | `revolver` | Revolver route remains legacy. |
| `hamster:coltm1892pair` | `revolver` | `revolver` | Paired revolver route remains legacy. |
| `hamster:flaregun` | `single_shot` | `single_shot` | Flare gun remains legacy. |
| `hamster:gras1874` | `single_shot` | `single_shot` | Single-shot rifle remains legacy. |
| `hamster:krag` | `internal_box` | `bolt_action` | Krag internal magazine route remains legacy. |
| `hamster:lebel1886` | `tube` | `bolt_action` | Tube rifle route remains legacy. |
| `hamster:luger1906` | `unknown` | `surveyed_precision_rifle` | Custom Luger 1906 route remains legacy. |
| `hamster:m1879revolver` | `revolver` | `revolver` | Revolver route remains legacy. |
| `hamster:m1887` | `tube` | `lever_action` | Tube/loop route remains legacy. |
| `hamster:martinihenry` | `single_shot` | `single_shot` | Single-shot rifle remains legacy. |
| `hamster:nagantcarbine` | `revolver` | `revolver` | Nagant cylinder route remains legacy. |
| `hamster:nagantm1895` | `revolver` | `revolver` | Nagant cylinder route remains legacy. |
| `hamster:one_barrel` | `single_shot` | `single_shot` | Single-barrel shotgun remains legacy. |
| `hamster:sharps` | `single_shot` | `single_shot` | Single-shot rifle remains legacy. |
| `hamster:sks` | `internal_box` | `gas_operated_rifle` | SKS internal box route remains legacy. |
| `hamster:smle_mk3` | `stripper_clip` | `bolt_action` | SMLE clip route remains legacy until script feed is audited. |
| `hamster:sw_mk2_41` | `revolver` | `revolver` | Revolver variant remains legacy. |
| `hamster:win1873` | `tube` | `lever_action` | Tube/lever route remains legacy. |
| `hamster:win1894` | `tube` | `lever_action` | Tube/lever route remains legacy. |
| `murasamet:abe_pipe_gun` | `unknown` | `surveyed_shotgun` | Primitive custom powder weapon remains legacy. |
| `murasamet:anantha_action` | `unknown` | `surveyed_rifle` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:at4` | `single_shot` | `launcher` | Single-shot launcher remains legacy. |
| `murasamet:auv` | `unknown` | `surveyed_smg` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:barstard` | `unknown` | `surveyed_smg` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:blunderbuss` | `single_shot` | `single_shot` | Primitive powder weapon remains legacy. |
| `murasamet:brownbess` | `single_shot` | `single_shot` | Primitive powder weapon remains legacy. |
| `murasamet:brownbess_tactical` | `single_shot` | `single_shot` | Primitive powder weapon remains legacy. |
| `murasamet:colt_1917` | `revolver` | `revolver` | M1917 is a revolver; no speedloader route is activated without script evidence. |
| `murasamet:flint_grenade_launcher` | `single_shot` | `launcher` | Single-shot grenade launcher remains legacy. |
| `murasamet:fp45` | `single_shot` | `single_shot` | Single-shot pistol remains legacy. |
| `murasamet:garand` | `en_bloc_clip` | `gas_operated_rifle` | M1 Garand en-bloc behavior is recorded, but this pack script route is not yet audited for physical clip insertion/ejection. |
| `murasamet:giddings_ssc410` | `unknown` | `surveyed_rifle` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:girandoni` | `unknown` | `surveyed_rifle` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:glock_1` | `unknown` | `surveyed_pistol` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:glock_999` | `unknown` | `surveyed_rpg` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:hand_connon` | `single_shot` | `single_shot` | Primitive powder weapon remains legacy. |
| `murasamet:kar98b` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:kar98k` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:kittygun` | `unknown` | `surveyed_pistol` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:mika` | `unknown` | `surveyed_smg` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:miyako` | `unknown` | `surveyed_smg` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:mosin_nagant1891` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:mosin_nagant38` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:mosin_nagant44` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:nagant1895` | `revolver` | `revolver` | Nagant 1895 cylinder route remains legacy. |
| `murasamet:natsu` | `unknown` | `surveyed_pistol` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:natsu_micro_uzi` | `unknown` | `surveyed_pistol` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:panzerfaust` | `single_shot` | `launcher` | Single-shot launcher remains legacy. |
| `murasamet:powder_craft` | `unknown` | `surveyed_shotgun` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:rpg7_og7he` | `single_shot` | `launcher` | Single-shot launcher remains legacy. |
| `murasamet:rpg7_pg7heat` | `single_shot` | `launcher` | Single-shot launcher remains legacy. |
| `murasamet:rpg7_pg7vr_tandem_heat` | `single_shot` | `launcher` | Single-shot launcher remains legacy. |
| `murasamet:russian1895` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:russian1895_carbine` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:shambler` | `unknown` | `surveyed_shotgun` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:sks` | `stripper_clip` | `gas_operated_rifle` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:steeltube` | `unknown` | `surveyed_shotgun` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:sw_1917` | `revolver` | `revolver` | M1917 is a revolver; no speedloader route is activated without script evidence. |
| `murasamet:tdf141ls` | `unknown` | `surveyed_sniper` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:tdf45sg` | `unknown` | `surveyed_shotgun` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:tdf739mg` | `unknown` | `surveyed_mg` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:tdf74e` | `unknown` | `surveyed_sniper` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:tdf97ar` | `unknown` | `surveyed_rifle` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:tianqing` | `unknown` | `surveyed_sniper` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:type11` | `unknown` | `surveyed_mg` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `murasamet:type56` | `stripper_clip` | `gas_operated_rifle` | Fixed internal box / stripper route remains legacy until this pack script is audited. |
| `murasamet:vallgarda_375` | `unknown` | `surveyed_sniper` | No sufficiently audited physical carrier / reload route; preserve legacy rather than guessing from class, name, ammo, bolt, or capacity. |
| `rainforest:at4` | `single_shot` | `launcher` | Single-shot disposable launcher semantics remain legacy; no fabricated removable carrier is supplied. |
| `rainforest:m72` | `single_shot` | `launcher` | Single-shot disposable launcher semantics remain legacy; no fabricated removable carrier is supplied. |
| `suffuse:aiyasinrpg` | `single_shot` | `launcher` | Launcher remains legacy. |
| `suffuse:an94` | `unknown` | `surveyed_rifle` | Custom AN-94 script route remains legacy until audited. |
| `suffuse:ks23m` | `tube` | `pump_action_shotgun` | Tube/loop route remains legacy. |
| `suffuse:lifecard` | `single_shot` | `single_shot` | Single-shot pocket pistol remains legacy. |
| `suffuse:m1895` | `tube` | `lever_action` | Tube/loop route remains legacy. |
| `suffuse:m203` | `single_shot` | `launcher` | 40 mm launcher remains legacy. |
| `suffuse:m79` | `single_shot` | `launcher` | 40 mm launcher remains legacy. |
| `suffuse:pf98a` | `single_shot` | `launcher` | Launcher remains legacy. |
| `suffuse:pp19` | `unknown` | `surveyed_smg` | Helical/unique PP19 carrier remains legacy until its physical removal route is audited. |
| `suffuse:python` | `revolver` | `revolver` | Revolver route remains legacy. |
| `suffuse:qlu11` | `unknown` | `launcher` | Grenade launcher carrier mechanism remains unproven. |
| `suffuse:spas12` | `tube` | `pump_action_shotgun` | Tube/loop route remains legacy. |
| `suffuse:trapper50cal` | `single_shot` | `single_shot` | Single-shot custom weapon remains legacy. |
| `ww:c96` | `internal_box` | `recoil_operated_pistol` | Standard C96 fixed internal box remains legacy. |
| `ww:kar98k` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until script feed is audited. |
| `ww:lee` | `unknown` | `bolt_action` | Lee-Enfield pack implementation remains legacy pending fixed/detachable route proof. |
| `ww:m1897` | `tube` | `pump_action_shotgun` | Tube/loop route remains legacy. |
| `ww:m1903` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until script feed is audited. |
| `ww:m1912` | `tube` | `pump_action_shotgun` | Tube/loop route remains legacy. |
| `ww:m91` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until script feed is audited. |
| `ww:type38` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until script feed is audited. |
| `ww:type99` | `stripper_clip` | `bolt_action` | Fixed internal box / stripper route remains legacy until script feed is audited. |
