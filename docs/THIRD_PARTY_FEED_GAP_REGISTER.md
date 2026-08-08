# 第三方供弹细分材质 / 功能缺口登记册

此文件由 `tools/generate_third_party_feed_gap_register.py` 生成，供作者和 CI 使用；
普通玩家不需要运行 Python。它不会启用任何 `gun_feed`，只登记当前已经审计的数据中：

- 没有精确细分材质、仍使用中性/家族级材料的实体载具；
- 每个缺口载具当前可见的中文/英文名称和容量变体；
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

## 细分材质分类汇总

这里的分类按**当前视觉替代方式**，不是按枪种猜测。每一项都是已有 `gun_feed` 的真实实体载具；
缺失的是授权/精确美术，不是库存、容量、制造或换弹功能。

| 细分材质类别 | family 数 |
|---|---:|
| 复用同类弹链箱/弹链图（非精确） (`family_reused_belt_box`) | 10 |
| 复用同类可拆卸弹匣图（非精确） (`family_reused_detachable_magazine`) | 2 |
| 中性通用弹链箱（缺专用细节图） (`neutral_belt_box`) | 11 |
| 中性通用可拆卸弹匣（缺专用细节图） (`neutral_detachable_magazine`) | 282 |

## 当前需要补细分材质的 family

下列条目没有 `exact_existing_material`。`gun_ids` 是受影响的已审计接收机；
它们的服务器库存、容量与制造出口已经生效，缺的是细分授权美术，而不是功能。

| 玩家可见名称（全部容量） | Family | Ammo | Mechanism | 细分材质分类 | 当前材料状态 |
|---|---|---|---|---|---|
| 30 发：AUG 5.56×45 30 发弹匣<br>40 发：测绘 AUG 5.56×45 40 发弹匣<br>45 发：测绘 AUG 5.56×45 45 发弹匣<br>60 发：测绘 AUG 5.56×45 60 发弹匣 | `aug_556` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 绍沙 20 发弹匣 | `bf1_chauchat_3006` | `tacz:30_06` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 德利尔卡宾枪 7 发弹匣<br>9 发：测绘 德利尔卡宾枪 9 发弹匣<br>12 发：测绘 德利尔卡宾枪 12 发弹匣<br>14 发：测绘 德利尔卡宾枪 14 发弹匣 | `bf1_de_lisle_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 47 发：测绘 刘易斯机枪 47 发弹匣<br>97 发：测绘 刘易斯机枪 97 发弹匣 | `bf1_lewis_308_pan` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 25 发：测绘 M1916自动装填步枪 25 发弹匣 | `bf1_m1916_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 200 发：测绘 MG08/15 200 发弹链箱<br>250 发：测绘 MG08/15 250 发弹链箱 | `bf1_mg0815_762x54_belt` | `tacz:762x54` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 50 发：测绘 MG42 50 发弹链箱<br>75 发：测绘 MG42 75 发弹链箱<br>250 发：测绘 MG42 250 发弹链箱 | `bf1_mg42_762x54_belt` | `tacz:762x54` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 80 发：测绘 SMG08/18 80 发弹匣 | `bf1_smg0818_9x19_drum` | `tacz:9mm` | `detachable_magazine` | `family_reused_detachable_magazine` | `family_level_material` → `tacz_extra:item/mag_rpk_drum` |
| 10 发：测绘 VG1-5 10 发弹匣<br>30 发：测绘 VG1-5 30 发弹匣<br>40 发：测绘 VG1-5 40 发弹匣<br>80 发：测绘 VG1-5 80 发弹匣 | `bf1_vg15_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 维拉·佩罗萨 50 发弹匣<br>60 发：测绘 维拉·佩罗萨 60 发弹匣<br>80 发：测绘 维拉·佩罗萨 80 发弹匣<br>100 发：测绘 维拉·佩罗萨 100 发弹匣 | `bf1_vp1915_9x19_twin` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 6 发：测绘 威尔洛德 6 发弹匣 | `bf1_welrod_9x19` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 ZK-383 30 发弹匣<br>32 发：测绘 ZK-383 32 发弹匣<br>35 发：测绘 ZK-383 35 发弹匣<br>40 发：测绘 ZK-383 40 发弹匣 | `bf1_zk383_9x19` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP A545 545x39 30 发弹匣<br>45 发：测绘 CCRP A545 545x39 45 发弹匣<br>60 发：测绘 CCRP A545 545x39 60 发弹匣<br>95 发：测绘 CCRP A545 545x39 95 发弹匣 | `ccrp_a545_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AAC HONEYBADGER 300blk 30 发弹匣<br>32 发：测绘 CCRP AAC HONEYBADGER 300blk 32 发弹匣<br>45 发：测绘 CCRP AAC HONEYBADGER 300blk 45 发弹匣<br>60 发：测绘 CCRP AAC HONEYBADGER 300blk 60 发弹匣 | `ccrp_aac_honeybadger_300blk` | `tacz:300blk` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 CCRP AF2011 45acp 7 发弹匣<br>9 发：测绘 CCRP AF2011 45acp 9 发弹匣<br>12 发：测绘 CCRP AF2011 45acp 12 发弹匣<br>14 发：测绘 CCRP AF2011 45acp 14 发弹匣 | `ccrp_af2011_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CCRP AICS M700 65cm 5 发弹匣<br>6 发：测绘 CCRP AICS M700 65cm 6 发弹匣<br>10 发：测绘 CCRP AICS M700 65cm 10 发弹匣<br>12 发：测绘 CCRP AICS M700 65cm 12 发弹匣 | `ccrp_aics_m700_65cm` | `ccrp:65cm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AK103 762x39 30 发弹匣<br>40 发：测绘 CCRP AK103 762x39 40 发弹匣<br>60 发：测绘 CCRP AK103 762x39 60 发弹匣<br>75 发：测绘 CCRP AK103 762x39 75 发弹匣 | `ccrp_ak103_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AK47 SPENT BULLET 762x39 30 发弹匣<br>34 发：测绘 CCRP AK47 SPENT BULLET 762x39 34 发弹匣<br>37 发：测绘 CCRP AK47 SPENT BULLET 762x39 37 发弹匣<br>40 发：测绘 CCRP AK47 SPENT BULLET 762x39 40 发弹匣 | `ccrp_ak47_spent_bullet_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AK74 545x39 30 发弹匣<br>45 发：测绘 CCRP AK74 545x39 45 发弹匣<br>60 发：测绘 CCRP AK74 545x39 60 发弹匣 | `ccrp_ak74_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AK74M 545x39 30 发弹匣<br>45 发：测绘 CCRP AK74M 545x39 45 发弹匣<br>60 发：测绘 CCRP AK74M 545x39 60 发弹匣 | `ccrp_ak74m_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AKS74U 545x39 30 发弹匣<br>35 发：测绘 CCRP AKS74U 545x39 35 发弹匣<br>45 发：测绘 CCRP AKS74U 545x39 45 发弹匣<br>60 发：测绘 CCRP AKS74U 545x39 60 发弹匣 | `ccrp_aks74u_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AM17 545x39 30 发弹匣<br>35 发：测绘 CCRP AM17 545x39 35 发弹匣<br>50 发：测绘 CCRP AM17 545x39 50 发弹匣<br>60 发：测绘 CCRP AM17 545x39 60 发弹匣 | `ccrp_am17_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 25 发：测绘 CCRP APC 9K PRO G 9mm 25 发弹匣<br>40 发：测绘 CCRP APC 9K PRO G 9mm 40 发弹匣<br>45 发：测绘 CCRP APC 9K PRO G 9mm 45 发弹匣<br>50 发：测绘 CCRP APC 9K PRO G 9mm 50 发弹匣 | `ccrp_apc_9k_pro_g_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 CCRP AR57 57x28 50 发弹匣 | `ccrp_ar57_57x28` | `tacz:57x28` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AUG A3 556x45 30 发弹匣<br>42 发：测绘 CCRP AUG A3 556x45 42 发弹匣<br>60 发：测绘 CCRP AUG A3 556x45 60 发弹匣<br>100 发：测绘 CCRP AUG A3 556x45 100 发弹匣 | `ccrp_aug_a3_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CCRP AUG A3 DMR 556x45 10 发弹匣<br>20 发：测绘 CCRP AUG A3 DMR 556x45 20 发弹匣<br>30 发：测绘 CCRP AUG A3 DMR 556x45 30 发弹匣<br>42 发：测绘 CCRP AUG A3 DMR 556x45 42 发弹匣 | `ccrp_aug_a3_dmr_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AUG A3 M2KIT 556x45_m995 30 发弹匣<br>42 发：测绘 CCRP AUG A3 M2KIT 556x45_m995 42 发弹匣<br>60 发：测绘 CCRP AUG A3 M2KIT 556x45_m995 60 发弹匣<br>100 发：测绘 CCRP AUG A3 M2KIT 556x45_m995 100 发弹匣 | `ccrp_aug_a3_m2kit_556x45_m995` | `ccrp:556x45_m995` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AUG A3S 300blk 30 发弹匣<br>45 发：测绘 CCRP AUG A3S 300blk 45 发弹匣<br>50 发：测绘 CCRP AUG A3S 300blk 50 发弹匣<br>60 发：测绘 CCRP AUG A3S 300blk 60 发弹匣 | `ccrp_aug_a3s_300blk` | `tacz:300blk` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AUG CAMG KIT 556x45 30 发弹匣<br>42 发：测绘 CCRP AUG CAMG KIT 556x45 42 发弹匣<br>60 发：测绘 CCRP AUG CAMG KIT 556x45 60 发弹匣<br>100 发：测绘 CCRP AUG CAMG KIT 556x45 100 发弹匣 | `ccrp_aug_camg_kit_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP AUG HBAR 556x45 30 发弹匣<br>42 发：测绘 CCRP AUG HBAR 556x45 42 发弹匣<br>60 发：测绘 CCRP AUG HBAR 556x45 60 发弹匣<br>100 发：测绘 CCRP AUG HBAR 556x45 100 发弹匣 | `ccrp_aug_hbar_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 25 发：测绘 CCRP AUG PARA 9mm 25 发弹匣<br>32 发：测绘 CCRP AUG PARA 9mm 32 发弹匣<br>35 发：测绘 CCRP AUG PARA 9mm 35 发弹匣<br>40 发：测绘 CCRP AUG PARA 9mm 40 发弹匣 | `ccrp_aug_para_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 17 发：测绘 CCRP BLOCK 17 9mm 17 发弹匣<br>20 发：测绘 CCRP BLOCK 17 9mm 20 发弹匣<br>25 发：测绘 CCRP BLOCK 17 9mm 25 发弹匣<br>30 发：测绘 CCRP BLOCK 17 9mm 30 发弹匣 | `ccrp_block_17_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP BRN 180 BULLPUP 556x45 30 发弹匣<br>32 发：测绘 CCRP BRN 180 BULLPUP 556x45 32 发弹匣<br>45 发：测绘 CCRP BRN 180 BULLPUP 556x45 45 发弹匣<br>60 发：测绘 CCRP BRN 180 BULLPUP 556x45 60 发弹匣 | `ccrp_brn_180_bullpup_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP CAMG M4 SOPMOD2 556x45 30 发弹匣<br>45 发：测绘 CCRP CAMG M4 SOPMOD2 556x45 45 发弹匣<br>60 发：测绘 CCRP CAMG M4 SOPMOD2 556x45 60 发弹匣<br>100 发：测绘 CCRP CAMG M4 SOPMOD2 556x45 100 发弹匣 | `ccrp_camg_m4_sopmod2_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP CAMG MK18 556x45 30 发弹匣<br>45 发：测绘 CCRP CAMG MK18 556x45 45 发弹匣<br>60 发：测绘 CCRP CAMG MK18 556x45 60 发弹匣<br>100 发：测绘 CCRP CAMG MK18 556x45 100 发弹匣 | `ccrp_camg_mk18_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP CAR 15 556x45 20 发弹匣<br>30 发：测绘 CCRP CAR 15 556x45 30 发弹匣<br>32 发：测绘 CCRP CAR 15 556x45 32 发弹匣<br>60 发：测绘 CCRP CAR 15 556x45 60 发弹匣 | `ccrp_car_15_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 CCRP CHISATO M1911 45acp 7 发弹匣<br>12 发：测绘 CCRP CHISATO M1911 45acp 12 发弹匣<br>18 发：测绘 CCRP CHISATO M1911 45acp 18 发弹匣<br>22 发：测绘 CCRP CHISATO M1911 45acp 22 发弹匣 | `ccrp_chisato_m1911_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP CR300 300blk 20 发弹匣<br>30 发：测绘 CCRP CR300 300blk 30 发弹匣<br>45 发：测绘 CCRP CR300 300blk 45 发弹匣<br>60 发：测绘 CCRP CR300 300blk 60 发弹匣 | `ccrp_cr300_300blk` | `tacz:300blk` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP DDM4 556x45_m995 30 发弹匣<br>35 发：测绘 CCRP DDM4 556x45_m995 35 发弹匣<br>45 发：测绘 CCRP DDM4 556x45_m995 45 发弹匣<br>60 发：测绘 CCRP DDM4 556x45_m995 60 发弹匣 | `ccrp_ddm4_556x45_m995` | `ccrp:556x45_m995` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP DDM4 PDW 300blk 20 发弹匣<br>32 发：测绘 CCRP DDM4 PDW 300blk 32 发弹匣<br>45 发：测绘 CCRP DDM4 PDW 300blk 45 发弹匣<br>60 发：测绘 CCRP DDM4 PDW 300blk 60 发弹匣 | `ccrp_ddm4_pdw_300blk` | `tacz:300blk` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 CCRP DDM4 V7 PRO 556x45_m855a2_f 32 发弹匣<br>35 发：测绘 CCRP DDM4 V7 PRO 556x45_m855a2_f 35 发弹匣<br>45 发：测绘 CCRP DDM4 V7 PRO 556x45_m855a2_f 45 发弹匣<br>60 发：测绘 CCRP DDM4 V7 PRO 556x45_m855a2_f 60 发弹匣 | `ccrp_ddm4_v7_pro_556x45_m855a2_f` | `ccrp:556x45_m855a2_f` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP DDM4A1 556x45 30 发弹匣<br>45 发：测绘 CCRP DDM4A1 556x45 45 发弹匣<br>60 发：测绘 CCRP DDM4A1 556x45 60 发弹匣 | `ccrp_ddm4a1_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 CCRP DEAGLE NIGHTINGALE 44mag 7 发弹匣<br>8 发：测绘 CCRP DEAGLE NIGHTINGALE 44mag 8 发弹匣<br>10 发：测绘 CCRP DEAGLE NIGHTINGALE 44mag 10 发弹匣<br>12 发：测绘 CCRP DEAGLE NIGHTINGALE 44mag 12 发弹匣 | `ccrp_deagle_nightingale_44mag` | `ccrp:44mag` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 CCRP DEAGLE XIX 50ae 7 发弹匣<br>8 发：测绘 CCRP DEAGLE XIX 50ae 8 发弹匣<br>10 发：测绘 CCRP DEAGLE XIX 50ae 10 发弹匣<br>12 发：测绘 CCRP DEAGLE XIX 50ae 12 发弹匣 | `ccrp_deagle_xix_50ae` | `tacz:50ae` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CCRP DSA SA58 308 15 发弹匣<br>20 发：测绘 CCRP DSA SA58 308 20 发弹匣<br>30 发：测绘 CCRP DSA SA58 308 30 发弹匣<br>50 发：测绘 CCRP DSA SA58 308 50 发弹匣 | `ccrp_dsa_sa58_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP F90 556x45 30 发弹匣<br>42 发：测绘 CCRP F90 556x45 42 发弹匣<br>50 发：测绘 CCRP F90 556x45 50 发弹匣<br>60 发：测绘 CCRP F90 556x45 60 发弹匣 | `ccrp_f90_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP F90 MBR 556x45 30 发弹匣<br>45 发：测绘 CCRP F90 MBR 556x45 45 发弹匣<br>60 发：测绘 CCRP F90 MBR 556x45 60 发弹匣<br>100 发：测绘 CCRP F90 MBR 556x45 100 发弹匣 | `ccrp_f90_mbr_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP FIGHTLITE SCR HG 556x45 30 发弹匣<br>40 发：测绘 CCRP FIGHTLITE SCR HG 556x45 40 发弹匣<br>50 发：测绘 CCRP FIGHTLITE SCR HG 556x45 50 发弹匣<br>100 发：测绘 CCRP FIGHTLITE SCR HG 556x45 100 发弹匣 | `ccrp_fightlite_scr_hg_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP G95A1 556x45 30 发弹匣<br>45 发：测绘 CCRP G95A1 556x45 45 发弹匣<br>60 发：测绘 CCRP G95A1 556x45 60 发弹匣<br>100 发：测绘 CCRP G95A1 556x45 100 发弹匣 | `ccrp_g95a1_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CCRP GLOCK20GEN5MOS 10mm 15 发弹匣<br>33 发：测绘 CCRP GLOCK20GEN5MOS 10mm 33 发弹匣<br>50 发：测绘 CCRP GLOCK20GEN5MOS 10mm 50 发弹匣<br>100 发：测绘 CCRP GLOCK20GEN5MOS 10mm 100 发弹匣 | `ccrp_glock20gen5mos_10mm` | `tacz:10mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CCRP GLOCK40GEN5MOS 10mm 15 发弹匣<br>33 发：测绘 CCRP GLOCK40GEN5MOS 10mm 33 发弹匣<br>50 发：测绘 CCRP GLOCK40GEN5MOS 10mm 50 发弹匣<br>100 发：测绘 CCRP GLOCK40GEN5MOS 10mm 100 发弹匣 | `ccrp_glock40gen5mos_10mm` | `tacz:10mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 200 发：测绘 CCRP HK21 308 200 发弹链 | `ccrp_hk21_308_belt` | `tacz:308` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 30 发：测绘 CCRP HK416 556x45 30 发弹匣<br>45 发：测绘 CCRP HK416 556x45 45 发弹匣<br>60 发：测绘 CCRP HK416 556x45 60 发弹匣<br>100 发：测绘 CCRP HK416 556x45 100 发弹匣 | `ccrp_hk416_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP HK416 SOPMOD 556x45 30 发弹匣<br>45 发：测绘 CCRP HK416 SOPMOD 556x45 45 发弹匣<br>60 发：测绘 CCRP HK416 SOPMOD 556x45 60 发弹匣<br>100 发：测绘 CCRP HK416 SOPMOD 556x45 100 发弹匣 | `ccrp_hk416_sopmod_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP HK416A8 556x45 30 发弹匣<br>45 发：测绘 CCRP HK416A8 556x45 45 发弹匣<br>60 发：测绘 CCRP HK416A8 556x45 60 发弹匣 | `ccrp_hk416a8_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP HK416C 556x45 30 发弹匣<br>40 发：测绘 CCRP HK416C 556x45 40 发弹匣<br>50 发：测绘 CCRP HK416C 556x45 50 发弹匣<br>60 发：测绘 CCRP HK416C 556x45 60 发弹匣 | `ccrp_hk416c_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP HK417 308 20 发弹匣 | `ccrp_hk417_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP HK433 556x45 30 发弹匣<br>45 发：测绘 CCRP HK433 556x45 45 发弹匣<br>60 发：测绘 CCRP HK433 556x45 60 发弹匣<br>100 发：测绘 CCRP HK433 556x45 100 发弹匣 | `ccrp_hk433_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP HK G28 308 20 发弹匣 | `ccrp_hk_g28_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP HK G28 PATROL 308 20 发弹匣 | `ccrp_hk_g28_patrol_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP HONEYBADGER 300blk 30 发弹匣<br>32 发：测绘 CCRP HONEYBADGER 300blk 32 发弹匣<br>35 发：测绘 CCRP HONEYBADGER 300blk 35 发弹匣<br>60 发：测绘 CCRP HONEYBADGER 300blk 60 发弹匣 | `ccrp_honeybadger_300blk` | `tacz:300blk` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP KAC KS 1 556x45 30 发弹匣<br>32 发：测绘 CCRP KAC KS 1 556x45 32 发弹匣<br>45 发：测绘 CCRP KAC KS 1 556x45 45 发弹匣<br>60 发：测绘 CCRP KAC KS 1 556x45 60 发弹匣 | `ccrp_kac_ks_1_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 CCRP KELTEC P50 57x28 50 发弹匣 | `ccrp_keltec_p50_57x28` | `tacz:57x28` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP KM AK74M 545x39 30 发弹匣<br>45 发：测绘 CCRP KM AK74M 545x39 45 发弹匣<br>60 发：测绘 CCRP KM AK74M 545x39 60 发弹匣<br>95 发：测绘 CCRP KM AK74M 545x39 95 发弹匣 | `ccrp_km_ak74m_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CCRP M110 308 15 发弹匣<br>25 发：测绘 CCRP M110 308 25 发弹匣<br>50 发：测绘 CCRP M110 308 50 发弹匣 | `ccrp_m110_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CCRP M110A3 65cm 15 发弹匣<br>20 发：测绘 CCRP M110A3 65cm 20 发弹匣<br>25 发：测绘 CCRP M110A3 65cm 25 发弹匣<br>50 发：测绘 CCRP M110A3 65cm 50 发弹匣 | `ccrp_m110a3_65cm` | `ccrp:65cm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP M14 HBAR 308 20 发弹匣<br>30 发：测绘 CCRP M14 HBAR 308 30 发弹匣<br>50 发：测绘 CCRP M14 HBAR 308 50 发弹匣<br>100 发：测绘 CCRP M14 HBAR 308 100 发弹匣 | `ccrp_m14_hbar_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP M16A3 556x45 20 发弹匣<br>30 发：测绘 CCRP M16A3 556x45 30 发弹匣<br>32 发：测绘 CCRP M16A3 556x45 32 发弹匣<br>60 发：测绘 CCRP M16A3 556x45 60 发弹匣 | `ccrp_m16a3_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP M231 556x45 30 发弹匣<br>33 发：测绘 CCRP M231 556x45 33 发弹匣<br>60 发：测绘 CCRP M231 556x45 60 发弹匣<br>100 发：测绘 CCRP M231 556x45 100 发弹匣 | `ccrp_m231_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP M249 SAW 556x45 30 发弹链<br>45 发：测绘 CCRP M249 SAW 556x45 45 发弹链<br>60 发：测绘 CCRP M249 SAW 556x45 60 发弹链<br>100 发：测绘 CCRP M249 SAW 556x45 100 发弹链 | `ccrp_m249_saw_556x45_belt` | `tacz:556x45` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 40 发：测绘 CCRP M27 IAR 556x45 40 发弹匣<br>50 发：测绘 CCRP M27 IAR 556x45 50 发弹匣<br>60 发：测绘 CCRP M27 IAR 556x45 60 发弹匣<br>100 发：测绘 CCRP M27 IAR 556x45 100 发弹匣 | `ccrp_m27_iar_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP M305A 762x39 30 发弹匣<br>45 发：测绘 CCRP M305A 762x39 45 发弹匣<br>50 发：测绘 CCRP M305A 762x39 50 发弹匣<br>75 发：测绘 CCRP M305A 762x39 75 发弹匣 | `ccrp_m305a_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CCRP M39 EMR 308 10 发弹匣<br>20 发：测绘 CCRP M39 EMR 308 20 发弹匣<br>25 发：测绘 CCRP M39 EMR 308 25 发弹匣<br>50 发：测绘 CCRP M39 EMR 308 50 发弹匣 | `ccrp_m39_emr_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP M4 CQBR 556x45 30 发弹匣<br>40 发：测绘 CCRP M4 CQBR 556x45 40 发弹匣<br>50 发：测绘 CCRP M4 CQBR 556x45 50 发弹匣<br>60 发：测绘 CCRP M4 CQBR 556x45 60 发弹匣 | `ccrp_m4_cqbr_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP M4 SOPMOD2 FSP 556x45 30 发弹匣<br>45 发：测绘 CCRP M4 SOPMOD2 FSP 556x45 45 发弹匣<br>60 发：测绘 CCRP M4 SOPMOD2 FSP 556x45 60 发弹匣<br>100 发：测绘 CCRP M4 SOPMOD2 FSP 556x45 100 发弹匣 | `ccrp_m4_sopmod2_fsp_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP M4 SS 556x45 30 发弹匣<br>45 发：测绘 CCRP M4 SS 556x45 45 发弹匣<br>60 发：测绘 CCRP M4 SS 556x45 60 发弹匣<br>100 发：测绘 CCRP M4 SS 556x45 100 发弹匣 | `ccrp_m4_ss_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP MCX SPEAR TOMBSTONE 68x51fury 20 发弹匣<br>22 发：测绘 CCRP MCX SPEAR TOMBSTONE 68x51fury 22 发弹匣<br>25 发：测绘 CCRP MCX SPEAR TOMBSTONE 68x51fury 25 发弹匣<br>30 发：测绘 CCRP MCX SPEAR TOMBSTONE 68x51fury 30 发弹匣 | `ccrp_mcx_spear_tombstone_68x51fury` | `tacz:68x51fury` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP MCX VIRTUS 556x45 30 发弹匣<br>40 发：测绘 CCRP MCX VIRTUS 556x45 40 发弹匣<br>50 发：测绘 CCRP MCX VIRTUS 556x45 50 发弹匣<br>60 发：测绘 CCRP MCX VIRTUS 556x45 60 发弹匣 | `ccrp_mcx_virtus_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP MG36 556x45 30 发弹匣<br>50 发：测绘 CCRP MG36 556x45 50 发弹匣<br>75 发：测绘 CCRP MG36 556x45 75 发弹匣<br>100 发：测绘 CCRP MG36 556x45 100 发弹匣 | `ccrp_mg36_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CCRP MK13 MOD5 300wm 5 发弹匣<br>6 发：测绘 CCRP MK13 MOD5 300wm 6 发弹匣<br>10 发：测绘 CCRP MK13 MOD5 300wm 10 发弹匣<br>12 发：测绘 CCRP MK13 MOD5 300wm 12 发弹匣 | `ccrp_mk13_mod5_300wm` | `ccrp:300wm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP MK17 308 20 发弹匣<br>22 发：测绘 CCRP MK17 308 22 发弹匣<br>25 发：测绘 CCRP MK17 308 25 发弹匣<br>50 发：测绘 CCRP MK17 308 50 发弹匣 | `ccrp_mk17_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 CCRP MK18 MJOLNIR 338 7 发弹匣<br>10 发：测绘 CCRP MK18 MJOLNIR 338 10 发弹匣<br>12 发：测绘 CCRP MK18 MJOLNIR 338 12 发弹匣<br>15 发：测绘 CCRP MK18 MJOLNIR 338 15 发弹匣 | `ccrp_mk18_mjolnir_338` | `tacz:338` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP MK556 556x45 30 发弹匣<br>45 发：测绘 CCRP MK556 556x45 45 发弹匣<br>60 发：测绘 CCRP MK556 556x45 60 发弹匣<br>75 发：测绘 CCRP MK556 556x45 75 发弹匣 | `ccrp_mk556_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP MP5 SD 9mm 30 发弹匣<br>40 发：测绘 CCRP MP5 SD 9mm 40 发弹匣<br>50 发：测绘 CCRP MP5 SD 9mm 50 发弹匣<br>60 发：测绘 CCRP MP5 SD 9mm 60 发弹匣 | `ccrp_mp5_sd_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CCRP MP5K 9mm 15 发弹匣<br>30 发：测绘 CCRP MP5K 9mm 30 发弹匣<br>50 发：测绘 CCRP MP5K 9mm 50 发弹匣<br>60 发：测绘 CCRP MP5K 9mm 60 发弹匣 | `ccrp_mp5k_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP MP5K PDW 9mm 30 发弹匣<br>50 发：测绘 CCRP MP5K PDW 9mm 50 发弹匣<br>60 发：测绘 CCRP MP5K PDW 9mm 60 发弹匣<br>100 发：测绘 CCRP MP5K PDW 9mm 100 发弹匣 | `ccrp_mp5k_pdw_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP MP7A3 46x30 30 发弹匣<br>40 发：测绘 CCRP MP7A3 46x30 40 发弹匣<br>50 发：测绘 CCRP MP7A3 46x30 50 发弹匣<br>60 发：测绘 CCRP MP7A3 46x30 60 发弹匣 | `ccrp_mp7a3_46x30` | `tacz:46x30` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 25 发：测绘 CCRP MPX 9mm 25 发弹匣<br>30 发：测绘 CCRP MPX 9mm 30 发弹匣<br>35 发：测绘 CCRP MPX 9mm 35 发弹匣<br>50 发：测绘 CCRP MPX 9mm 50 发弹匣 | `ccrp_mpx_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 CCRP P90 EFFEN 90 57x28 50 发弹匣 | `ccrp_p90_effen_90_57x28` | `tacz:57x28` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 CCRP P90 PAW 57x28 50 发弹匣 | `ccrp_p90_paw_57x28` | `tacz:57x28` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 CCRP P90 SHROUND S 57x28 50 发弹匣 | `ccrp_p90_shround_s_57x28` | `tacz:57x28` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP PSA AK556 556x45 30 发弹匣<br>32 发：测绘 CCRP PSA AK556 556x45 32 发弹匣<br>45 发：测绘 CCRP PSA AK556 556x45 45 发弹匣<br>60 发：测绘 CCRP PSA AK556 556x45 60 发弹匣 | `ccrp_psa_ak556_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP QBU 191 58x42 30 发弹匣<br>40 发：测绘 CCRP QBU 191 58x42 40 发弹匣<br>50 发：测绘 CCRP QBU 191 58x42 50 发弹匣<br>60 发：测绘 CCRP QBU 191 58x42 60 发弹匣 | `ccrp_qbu_191_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP QBZ 191 58x42 30 发弹匣<br>45 发：测绘 CCRP QBZ 191 58x42 45 发弹匣<br>60 发：测绘 CCRP QBZ 191 58x42 60 发弹匣<br>75 发：测绘 CCRP QBZ 191 58x42 75 发弹匣 | `ccrp_qbz_191_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP RD704 762x39 30 发弹匣<br>35 发：测绘 CCRP RD704 762x39 35 发弹匣<br>40 发：测绘 CCRP RD704 762x39 40 发弹匣<br>45 发：测绘 CCRP RD704 762x39 45 发弹匣 | `ccrp_rd704_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP RO635 9mm 30 发弹匣<br>35 发：测绘 CCRP RO635 9mm 35 发弹匣<br>40 发：测绘 CCRP RO635 9mm 40 发弹匣<br>50 发：测绘 CCRP RO635 9mm 50 发弹匣 | `ccrp_ro635_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP RPK74M 545x39 30 发弹匣<br>45 发：测绘 CCRP RPK74M 545x39 45 发弹匣<br>60 发：测绘 CCRP RPK74M 545x39 60 发弹匣<br>95 发：测绘 CCRP RPK74M 545x39 95 发弹匣 | `ccrp_rpk74m_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP RPK 203 762x39 30 发弹匣<br>40 发：测绘 CCRP RPK 203 762x39 40 发弹匣<br>60 发：测绘 CCRP RPK 203 762x39 60 发弹匣<br>75 发：测绘 CCRP RPK 203 762x39 75 发弹匣 | `ccrp_rpk_203_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP SAI GRY LITE BLACK 556x45 30 发弹匣<br>45 发：测绘 CCRP SAI GRY LITE BLACK 556x45 45 发弹匣<br>60 发：测绘 CCRP SAI GRY LITE BLACK 556x45 60 发弹匣<br>75 发：测绘 CCRP SAI GRY LITE BLACK 556x45 75 发弹匣 | `ccrp_sai_gry_lite_black_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP SAMURAI EDGE 45acp 20 发弹匣<br>23 发：测绘 CCRP SAMURAI EDGE 45acp 23 发弹匣<br>26 发：测绘 CCRP SAMURAI EDGE 45acp 26 发弹匣<br>32 发：测绘 CCRP SAMURAI EDGE 45acp 32 发弹匣 | `ccrp_samurai_edge_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP SCAR16 ARIANA 556x45 30 发弹匣<br>32 发：测绘 CCRP SCAR16 ARIANA 556x45 32 发弹匣<br>45 发：测绘 CCRP SCAR16 ARIANA 556x45 45 发弹匣<br>60 发：测绘 CCRP SCAR16 ARIANA 556x45 60 发弹匣 | `ccrp_scar16_ariana_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CCRP SCAR17 ARMARISE 308 10 发弹匣<br>15 发：测绘 CCRP SCAR17 ARMARISE 308 15 发弹匣<br>20 发：测绘 CCRP SCAR17 ARMARISE 308 20 发弹匣<br>25 发：测绘 CCRP SCAR17 ARMARISE 308 25 发弹匣 | `ccrp_scar17_armarise_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP SCAR 16S 556x45 30 发弹匣<br>32 发：测绘 CCRP SCAR 16S 556x45 32 发弹匣<br>45 发：测绘 CCRP SCAR 16S 556x45 45 发弹匣<br>60 发：测绘 CCRP SCAR 16S 556x45 60 发弹匣 | `ccrp_scar_16s_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CCRP SCAR 17S 65cm 10 发弹匣<br>15 发：测绘 CCRP SCAR 17S 65cm 15 发弹匣<br>20 发：测绘 CCRP SCAR 17S 65cm 20 发弹匣<br>25 发：测绘 CCRP SCAR 17S 65cm 25 发弹匣 | `ccrp_scar_17s_65cm` | `ccrp:65cm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP SCAR SC 300blk 30 发弹匣<br>45 发：测绘 CCRP SCAR SC 300blk 45 发弹匣<br>60 发：测绘 CCRP SCAR SC 300blk 60 发弹匣<br>100 发：测绘 CCRP SCAR SC 300blk 100 发弹匣 | `ccrp_scar_sc_300blk` | `tacz:300blk` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 18 发：测绘 CCRP SHIELD OTS33 9mm 18 发弹匣<br>23 发：测绘 CCRP SHIELD OTS33 9mm 23 发弹匣<br>27 发：测绘 CCRP SHIELD OTS33 9mm 27 发弹匣<br>33 发：测绘 CCRP SHIELD OTS33 9mm 33 发弹匣 | `ccrp_shield_ots33_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP SIG277 68x51fury 20 发弹匣<br>25 发：测绘 CCRP SIG277 68x51fury 25 发弹匣<br>30 发：测绘 CCRP SIG277 68x51fury 30 发弹匣<br>45 发：测绘 CCRP SIG277 68x51fury 45 发弹匣 | `ccrp_sig277_68x51fury` | `tacz:68x51fury` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP SOLGW MK1 556x45_m855a1 20 发弹匣<br>30 发：测绘 CCRP SOLGW MK1 556x45_m855a1 30 发弹匣<br>32 发：测绘 CCRP SOLGW MK1 556x45_m855a1 32 发弹匣<br>60 发：测绘 CCRP SOLGW MK1 556x45_m855a1 60 发弹匣 | `ccrp_solgw_mk1_556x45_m855a1` | `ccrp:556x45_m855a1` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CCRP SR25 308 15 发弹匣<br>25 发：测绘 CCRP SR25 308 25 发弹匣<br>50 发：测绘 CCRP SR25 308 50 发弹匣 | `ccrp_sr25_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP SR 3M 9x39 30 发弹匣<br>35 发：测绘 CCRP SR 3M 9x39 35 发弹匣<br>45 发：测绘 CCRP SR 3M 9x39 45 发弹匣<br>50 发：测绘 CCRP SR 3M 9x39 50 发弹匣 | `ccrp_sr_3m_9x39` | `tacz:9x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 CCRP TIMELESS 50 50ae 7 发弹匣<br>8 发：测绘 CCRP TIMELESS 50 50ae 8 发弹匣<br>10 发：测绘 CCRP TIMELESS 50 50ae 10 发弹匣<br>14 发：测绘 CCRP TIMELESS 50 50ae 14 发弹匣 | `ccrp_timeless_50_50ae` | `tacz:50ae` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP TROY M14 SASS 308 20 发弹匣<br>30 发：测绘 CCRP TROY M14 SASS 308 30 发弹匣<br>50 发：测绘 CCRP TROY M14 SASS 308 50 发弹匣<br>100 发：测绘 CCRP TROY M14 SASS 308 100 发弹匣 | `ccrp_troy_m14_sass_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP TTI MPX 9mm 30 发弹匣<br>36 发：测绘 CCRP TTI MPX 9mm 36 发弹匣<br>50 发：测绘 CCRP TTI MPX 9mm 50 发弹匣<br>100 发：测绘 CCRP TTI MPX 9mm 100 发弹匣 | `ccrp_tti_mpx_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP TTI TR1 556x45 30 发弹匣<br>32 发：测绘 CCRP TTI TR1 556x45 32 发弹匣<br>60 发：测绘 CCRP TTI TR1 556x45 60 发弹匣<br>100 发：测绘 CCRP TTI TR1 556x45 100 发弹匣 | `ccrp_tti_tr1_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 60 发：测绘 CCRP TYPE 95 LONGBOW 58x42 60 发弹匣<br>75 发：测绘 CCRP TYPE 95 LONGBOW 58x42 75 发弹匣<br>100 发：测绘 CCRP TYPE 95 LONGBOW 58x42 100 发弹匣<br>150 发：测绘 CCRP TYPE 95 LONGBOW 58x42 150 发弹匣 | `ccrp_type_95_longbow_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP TYPE 97 GEN2 556x45 30 发弹匣<br>45 发：测绘 CCRP TYPE 97 GEN2 556x45 45 发弹匣<br>60 发：测绘 CCRP TYPE 97 GEN2 556x45 60 发弹匣<br>75 发：测绘 CCRP TYPE 97 GEN2 556x45 75 发弹匣 | `ccrp_type_97_gen2_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CCRP UZI45 45acp 20 发弹匣<br>32 发：测绘 CCRP UZI45 45acp 32 发弹匣<br>40 发：测绘 CCRP UZI45 45acp 40 发弹匣<br>50 发：测绘 CCRP UZI45 45acp 50 发弹匣 | `ccrp_uzi45_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CCRP VECTOR10 10mm 15 发弹匣<br>33 发：测绘 CCRP VECTOR10 10mm 33 发弹匣<br>50 发：测绘 CCRP VECTOR10 10mm 50 发弹匣<br>100 发：测绘 CCRP VECTOR10 10mm 100 发弹匣 | `ccrp_vector10_10mm` | `tacz:10mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 CCRP X95 SMG 9mm 32 发弹匣<br>38 发：测绘 CCRP X95 SMG 9mm 38 发弹匣<br>45 发：测绘 CCRP X95 SMG 9mm 45 发弹匣<br>50 发：测绘 CCRP X95 SMG 9mm 50 发弹匣 | `ccrp_x95_smg_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP X95R 556x45 30 发弹匣<br>45 发：测绘 CCRP X95R 556x45 45 发弹匣<br>50 发：测绘 CCRP X95R 556x45 50 发弹匣<br>60 发：测绘 CCRP X95R 556x45 60 发弹匣 | `ccrp_x95r_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP ZENIT AK104 762x39 30 发弹匣<br>40 发：测绘 CCRP ZENIT AK104 762x39 40 发弹匣<br>60 发：测绘 CCRP ZENIT AK104 762x39 60 发弹匣<br>75 发：测绘 CCRP ZENIT AK104 762x39 75 发弹匣 | `ccrp_zenit_ak104_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CCRP ZENIT AK105 545x39 30 发弹匣<br>45 发：测绘 CCRP ZENIT AK105 545x39 45 发弹匣<br>60 发：测绘 CCRP ZENIT AK105 545x39 60 发弹匣 | `ccrp_zenit_ak105_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB 9A91 9x39mm 20 发弹匣<br>30 发：测绘 CIB 9A91 9x39mm 30 发弹匣<br>40 发：测绘 CIB 9A91 9x39mm 40 发弹匣<br>50 发：测绘 CIB 9A91 9x39mm 50 发弹匣 | `cib_9a91_9x39mm` | `cib:9x39mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 AK-105 545x39 30 发弹匣<br>40 发：测绘 AK-105 545x39 40 发弹匣<br>50 发：测绘 AK-105 545x39 50 发弹匣<br>60 发：测绘 AK-105 545x39 60 发弹匣 | `cib_ak105_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIBS AK24 TEXAS 556x45 30 发弹匣<br>40 发：测绘 CIBS AK24 TEXAS 556x45 40 发弹匣<br>50 发：测绘 CIBS AK24 TEXAS 556x45 50 发弹匣<br>60 发：测绘 CIBS AK24 TEXAS 556x45 60 发弹匣 | `cib_ak24_556` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB ASVAL 9x39mm 20 发弹匣<br>30 发：测绘 CIB ASVAL 9x39mm 30 发弹匣<br>40 发：测绘 CIB ASVAL 9x39mm 40 发弹匣<br>50 发：测绘 CIB ASVAL 9x39mm 50 发弹匣 | `cib_asval_9x39mm` | `cib:9x39mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CS AWP 308 5 发弹匣<br>10 发：测绘 CS AWP 308 10 发弹匣<br>15 发：测绘 CS AWP 308 15 发弹匣<br>20 发：测绘 CS AWP 308 20 发弹匣 | `cib_awp_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB CS AK47 762x39 30 发弹匣<br>45 发：测绘 CIB CS AK47 762x39 45 发弹匣<br>50 发：测绘 CIB CS AK47 762x39 50 发弹匣<br>60 发：测绘 CIB CS AK47 762x39 60 发弹匣 | `cib_cs_ak47_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CIB CSLR3 58x42 10 发弹匣<br>12 发：测绘 CIB CSLR3 58x42 12 发弹匣<br>13 发：测绘 CIB CSLR3 58x42 13 发弹匣<br>25 发：测绘 CIB CSLR3 58x42 25 发弹匣 | `cib_cslr3_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CIB CSLR4 308 5 发弹匣<br>10 发：测绘 CIB CSLR4 308 10 发弹匣<br>15 发：测绘 CIB CSLR4 308 15 发弹匣<br>20 发：测绘 CIB CSLR4 308 20 发弹匣 | `cib_cslr4_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB EVO3 9mm 20 发弹匣<br>30 发：测绘 CIB EVO3 9mm 30 发弹匣<br>45 发：测绘 CIB EVO3 9mm 45 发弹匣<br>65 发：测绘 CIB EVO3 9mm 65 发弹匣 | `cib_evo3_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB FAL 308 20 发弹匣<br>30 发：测绘 CIB FAL 308 30 发弹匣<br>40 发：测绘 CIB FAL 308 40 发弹匣<br>50 发：测绘 CIB FAL 308 50 发弹匣 | `cib_fal_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB G18C 9mm 20 发弹匣<br>30 发：测绘 CIB G18C 9mm 30 发弹匣<br>40 发：测绘 CIB G18C 9mm 40 发弹匣<br>50 发：测绘 CIB G18C 9mm 50 发弹匣 | `cib_g18c_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 CIB G19 9mm 15 发弹匣<br>17 发：测绘 CIB G19 9mm 17 发弹匣<br>19 发：测绘 CIB G19 9mm 19 发弹匣<br>33 发：测绘 CIB G19 9mm 33 发弹匣 | `cib_g19_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB G3SG1 308 20 发弹匣<br>22 发：测绘 CIB G3SG1 308 22 发弹匣<br>25 发：测绘 CIB G3SG1 308 25 发弹匣<br>30 发：测绘 CIB G3SG1 308 30 发弹匣 | `cib_g3sg1_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 35 发：测绘 CIB GALIL 556x45 35 发弹匣<br>45 发：测绘 CIB GALIL 556x45 45 发弹匣<br>50 发：测绘 CIB GALIL 556x45 50 发弹匣<br>60 发：测绘 CIB GALIL 556x45 60 发弹匣 | `cib_galil_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 35 发：测绘 Galil ACE 32 762x39 35 发弹匣<br>45 发：测绘 Galil ACE 32 762x39 45 发弹匣<br>55 发：测绘 Galil ACE 32 762x39 55 发弹匣<br>60 发：测绘 Galil ACE 32 762x39 60 发弹匣 | `cib_galilace32_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 35 发：测绘 CIB GALILACE 556x45 35 发弹匣<br>45 发：测绘 CIB GALILACE 556x45 45 发弹匣<br>55 发：测绘 CIB GALILACE 556x45 55 发弹匣<br>60 发：测绘 CIB GALILACE 556x45 60 发弹匣 | `cib_galilace_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB HK433 556x45 30 发弹匣<br>40 发：测绘 CIB HK433 556x45 40 发弹匣<br>50 发：测绘 CIB HK433 556x45 50 发弹匣<br>60 发：测绘 CIB HK433 556x45 60 发弹匣 | `cib_hk433_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB JS9 9mm 20 发弹匣<br>30 发：测绘 CIB JS9 9mm 30 发弹匣<br>40 发：测绘 CIB JS9 9mm 40 发弹匣<br>50 发：测绘 CIB JS9 9mm 50 发弹匣 | `cib_js9_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB K2 556x45 20 发弹匣<br>30 发：测绘 CIB K2 556x45 30 发弹匣<br>35 发：测绘 CIB K2 556x45 35 发弹匣<br>40 发：测绘 CIB K2 556x45 40 发弹匣 | `cib_k2_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB K2C1 556x45 30 发弹匣<br>40 发：测绘 CIB K2C1 556x45 40 发弹匣<br>50 发：测绘 CIB K2C1 556x45 50 发弹匣<br>60 发：测绘 CIB K2C1 556x45 60 发弹匣 | `cib_k2c1_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB LA89 762x39 30 发弹匣<br>40 发：测绘 CIB LA89 762x39 40 发弹匣<br>50 发：测绘 CIB LA89 762x39 50 发弹匣<br>65 发：测绘 CIB LA89 762x39 65 发弹匣 | `cib_la89_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB M16A4 556x45 30 发弹匣<br>40 发：测绘 CIB M16A4 556x45 40 发弹匣<br>50 发：测绘 CIB M16A4 556x45 50 发弹匣<br>60 发：测绘 CIB M16A4 556x45 60 发弹匣 | `cib_m16a4_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CIB M99 127x108 5 发弹匣<br>10 发：测绘 CIB M99 127x108 10 发弹匣<br>15 发：测绘 CIB M99 127x108 15 发弹匣<br>20 发：测绘 CIB M99 127x108 20 发弹匣 | `cib_m99_127x108` | `cib:127x108` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 65 发：测绘 CIB MG3 308 65 发弹链<br>75 发：测绘 CIB MG3 308 75 发弹链<br>100 发：测绘 CIB MG3 308 100 发弹链<br>150 发：测绘 CIB MG3 308 150 发弹链 | `cib_mg3_308_belt` | `tacz:308` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 65 发：测绘 CIB NEGEV 556x45 65 发弹链<br>75 发：测绘 CIB NEGEV 556x45 75 发弹链<br>100 发：测绘 CIB NEGEV 556x45 100 发弹链<br>150 发：测绘 CIB NEGEV 556x45 150 发弹链 | `cib_negev_556x45_belt` | `tacz:556x45` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 8 发：测绘 CIB ORIGIN12 12g 8 发弹匣<br>10 发：测绘 CIB ORIGIN12 12g 10 发弹匣<br>20 发：测绘 CIB ORIGIN12 12g 20 发弹匣<br>30 发：测绘 CIB ORIGIN12 12g 30 发弹匣 | `cib_origin12_12g` | `tacz:12g` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB OTS14 762x39 30 发弹匣<br>40 发：测绘 CIB OTS14 762x39 40 发弹匣<br>50 发：测绘 CIB OTS14 762x39 50 发弹匣<br>60 发：测绘 CIB OTS14 762x39 60 发弹匣 | `cib_ots14_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 13 发：测绘 CIB P250 9mm 13 发弹匣<br>15 发：测绘 CIB P250 9mm 15 发弹匣<br>20 发：测绘 CIB P250 9mm 20 发弹匣<br>25 发：测绘 CIB P250 9mm 25 发弹匣 | `cib_p250_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 70 发：测绘 CIB PKP 762x54 70 发弹链<br>100 发：测绘 CIB PKP 762x54 100 发弹链<br>150 发：测绘 CIB PKP 762x54 150 发弹链<br>200 发：测绘 CIB PKP 762x54 200 发弹链 | `cib_pkp_762x54_belt` | `tacz:762x54` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 25 发：测绘 CIB PM9 9mm 25 发弹匣<br>30 发：测绘 CIB PM9 9mm 30 发弹匣<br>40 发：测绘 CIB PM9 9mm 40 发弹匣<br>50 发：测绘 CIB PM9 9mm 50 发弹匣 | `cib_pm9_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 CIB PP19 9mm 50 发弹匣<br>53 发：测绘 CIB PP19 9mm 53 发弹匣<br>60 发：测绘 CIB PP19 9mm 60 发弹匣<br>64 发：测绘 CIB PP19 9mm 64 发弹匣 | `cib_pp19_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 CIB PPK 32acp 7 发弹匣<br>9 发：测绘 CIB PPK 32acp 9 发弹匣<br>12 发：测绘 CIB PPK 32acp 12 发弹匣<br>14 发：测绘 CIB PPK 32acp 14 发弹匣 | `cib_ppk_32acp` | `cib:32acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB PPSH41 762x25 20 发弹匣<br>35 发：测绘 CIB PPSH41 762x25 35 发弹匣<br>45 发：测绘 CIB PPSH41 762x25 45 发弹匣<br>71 发：测绘 CIB PPSH41 762x25 71 发弹匣 | `cib_ppsh41_762x25` | `tacz:762x25` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CIB QBU10 127x108 5 发弹匣<br>10 发：测绘 CIB QBU10 127x108 10 发弹匣<br>15 发：测绘 CIB QBU10 127x108 15 发弹匣<br>20 发：测绘 CIB QBU10 127x108 20 发弹匣 | `cib_qbu10_127x108` | `cib:127x108` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CIB QBU191 58x42 10 发弹匣<br>15 发：测绘 CIB QBU191 58x42 15 发弹匣<br>20 发：测绘 CIB QBU191 58x42 20 发弹匣<br>30 发：测绘 CIB QBU191 58x42 30 发弹匣 | `cib_qbu191_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CIB QBU201 127x108 5 发弹匣<br>10 发：测绘 CIB QBU201 127x108 10 发弹匣<br>15 发：测绘 CIB QBU201 127x108 15 发弹匣<br>20 发：测绘 CIB QBU201 127x108 20 发弹匣 | `cib_qbu201_127x108` | `cib:127x108` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CIBS QBU202 LINGCHE 338 5 发弹匣<br>8 发：测绘 CIBS QBU202 LINGCHE 338 8 发弹匣<br>10 发：测绘 CIBS QBU202 LINGCHE 338 10 发弹匣<br>15 发：测绘 CIBS QBU202 LINGCHE 338 15 发弹匣 | `cib_qbu202_338` | `tacz:338` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CIB QBU203 308 5 发弹匣<br>10 发：测绘 CIB QBU203 308 10 发弹匣<br>15 发：测绘 CIB QBU203 308 15 发弹匣<br>20 发：测绘 CIB QBU203 308 20 发弹匣 | `cib_qbu203_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CIB QBU88 58x42 10 发弹匣<br>15 发：测绘 CIB QBU88 58x42 15 发弹匣<br>20 发：测绘 CIB QBU88 58x42 20 发弹匣<br>25 发：测绘 CIB QBU88 58x42 25 发弹匣 | `cib_qbu88_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB QBZ03 58x42 30 发弹匣<br>35 发：测绘 CIB QBZ03 58x42 35 发弹匣<br>40 发：测绘 CIB QBZ03 58x42 40 发弹匣<br>50 发：测绘 CIB QBZ03 58x42 50 发弹匣 | `cib_qbz03_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 QBZ-191 58x42 30 发弹匣<br>40 发：测绘 QBZ-191 58x42 40 发弹匣<br>50 发：测绘 QBZ-191 58x42 50 发弹匣<br>60 发：测绘 QBZ-191 58x42 60 发弹匣 | `cib_qbz191_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB QBZ192 58x42 30 发弹匣<br>40 发：测绘 CIB QBZ192 58x42 40 发弹匣<br>50 发：测绘 CIB QBZ192 58x42 50 发弹匣<br>60 发：测绘 CIB QBZ192 58x42 60 发弹匣 | `cib_qbz192_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 QBZ-95-1 58x42 30 发弹匣<br>35 发：测绘 QBZ-95-1 58x42 35 发弹匣<br>40 发：测绘 QBZ-95-1 58x42 40 发弹匣<br>50 发：测绘 QBZ-95-1 58x42 50 发弹匣 | `cib_qbz951_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB QBZ95B1 58x42 30 发弹匣<br>35 发：测绘 CIB QBZ95B1 58x42 35 发弹匣<br>40 发：测绘 CIB QBZ95B1 58x42 40 发弹匣<br>50 发：测绘 CIB QBZ95B1 58x42 50 发弹匣 | `cib_qbz95b1_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 QCQ-171 9mm 30 发弹匣<br>35 发：测绘 QCQ-171 9mm 35 发弹匣<br>40 发：测绘 QCQ-171 9mm 40 发弹匣<br>70 发：测绘 QCQ-171 9mm 70 发弹匣 | `cib_qcq171_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 CIB QCW05 58x21 50 发弹匣 | `cib_qcw05_58x21` | `cib:58x21` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 75 发：测绘 CIB QJB951 58x42 75 发弹匣<br>80 发：测绘 CIB QJB951 58x42 80 发弹匣<br>85 发：测绘 CIB QJB951 58x42 85 发弹匣<br>90 发：测绘 CIB QJB951 58x42 90 发弹匣 | `cib_qjb951_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 100 发：测绘 CIB QJY201 308 100 发弹链<br>150 发：测绘 CIB QJY201 308 150 发弹链<br>200 发：测绘 CIB QJY201 308 200 发弹链<br>250 发：测绘 CIB QJY201 308 250 发弹链 | `cib_qjy201_308_belt` | `tacz:308` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 90 发：测绘 CIB QJY88 58x42 90 发弹链<br>100 发：测绘 CIB QJY88 58x42 100 发弹链<br>150 发：测绘 CIB QJY88 58x42 150 发弹链<br>200 发：测绘 CIB QJY88 58x42 200 发弹链 | `cib_qjy88_58x42_belt` | `tacz:58x42` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 60 发：测绘 CIB QJZ171 127x108 60 发弹链<br>80 发：测绘 CIB QJZ171 127x108 80 发弹链<br>90 发：测绘 CIB QJZ171 127x108 90 发弹链<br>100 发：测绘 CIB QJZ171 127x108 100 发弹链 | `cib_qjz171_127x108_belt` | `cib:127x108` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 7 发：测绘 CIB QSZ193 9mm 7 发弹匣<br>9 发：测绘 CIB QSZ193 9mm 9 发弹匣<br>10 发：测绘 CIB QSZ193 9mm 10 发弹匣<br>11 发：测绘 CIB QSZ193 9mm 11 发弹匣 | `cib_qsz193_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB QSZ92 58x21 20 发弹匣<br>23 发：测绘 CIB QSZ92 58x21 23 发弹匣<br>25 发：测绘 CIB QSZ92 58x21 25 发弹匣<br>30 发：测绘 CIB QSZ92 58x21 30 发弹匣 | `cib_qsz92_58x21` | `cib:58x21` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB SIG552 556x45 20 发弹匣<br>30 发：测绘 CIB SIG552 556x45 30 发弹匣<br>40 发：测绘 CIB SIG552 556x45 40 发弹匣<br>50 发：测绘 CIB SIG552 556x45 50 发弹匣 | `cib_sig552_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 SIG 556 556x45 30 发弹匣<br>40 发：测绘 SIG 556 556x45 40 发弹匣<br>50 发：测绘 SIG 556 556x45 50 发弹匣<br>60 发：测绘 SIG 556 556x45 60 发弹匣 | `cib_sig556_556` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 CIB SSG08 308 5 发弹匣<br>8 发：测绘 CIB SSG08 308 8 发弹匣<br>10 发：测绘 CIB SSG08 308 10 发弹匣<br>15 发：测绘 CIB SSG08 308 15 发弹匣 | `cib_ssg08_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CIB SV98 762x54 10 发弹匣<br>15 发：测绘 CIB SV98 762x54 15 发弹匣<br>20 发：测绘 CIB SV98 762x54 20 发弹匣<br>25 发：测绘 CIB SV98 762x54 25 发弹匣 | `cib_sv98_762x54` | `tacz:762x54` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CIB SVD 762x54 10 发弹匣 | `cib_svd_762x54` | `tacz:762x54` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIB T91 556x45 30 发弹匣<br>40 发：测绘 CIB T91 556x45 40 发弹匣<br>50 发：测绘 CIB T91 556x45 50 发弹匣<br>60 发：测绘 CIB T91 556x45 60 发弹匣 | `cib_t91_556x45` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 8 发：测绘 CIB TYPE14 8x22 8 发弹匣 | `cib_type14_8x22` | `cib:8x22` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 Type 20 556x45 30 发弹匣<br>40 发：测绘 Type 20 556x45 40 发弹匣<br>50 发：测绘 Type 20 556x45 50 发弹匣<br>60 发：测绘 Type 20 556x45 60 发弹匣 | `cib_type20_556` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 CIBS TYPE56 COMMEMORATE 762x39 30 发弹匣<br>45 发：测绘 CIBS TYPE56 COMMEMORATE 762x39 45 发弹匣<br>50 发：测绘 CIBS TYPE56 COMMEMORATE 762x39 50 发弹匣<br>60 发：测绘 CIBS TYPE56 COMMEMORATE 762x39 60 发弹匣 | `cib_type56_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 CIB TYPE79 762x25 20 发弹匣<br>30 发：测绘 CIB TYPE79 762x25 30 发弹匣<br>35 发：测绘 CIB TYPE79 762x25 35 发弹匣<br>40 发：测绘 CIB TYPE79 762x25 40 发弹匣 | `cib_type79_762x25` | `tacz:762x25` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 CIB USAS12 12g 10 发弹匣<br>15 发：测绘 CIB USAS12 12g 15 发弹匣<br>20 发：测绘 CIB USAS12 12g 20 发弹匣<br>25 发：测绘 CIB USAS12 12g 25 发弹匣 | `cib_usas12_12g` | `tacz:12g` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 12 发：测绘 USP 45acp 12 发弹匣<br>15 发：测绘 USP 45acp 15 发弹匣<br>20 发：测绘 USP 45acp 20 发弹匣<br>25 发：测绘 USP 45acp 25 发弹匣 | `cib_usp_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 AK-12 545x39 30 发弹匣<br>35 发：测绘 AK-12 545x39 35 发弹匣<br>45 发：测绘 AK-12 545x39 45 发弹匣<br>60 发：测绘 AK-12 545x39 60 发弹匣 | `classicr_ak12_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 B93R 9mm 15 发弹匣<br>24 发：测绘 B93R 9mm 24 发弹匣<br>27 发：测绘 B93R 9mm 27 发弹匣<br>33 发：测绘 B93R 9mm 33 发弹匣 | `classicr_b93r_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 47 发：测绘 DP-28 762x54r 47 发弹匣 | `classicr_dp28_pan_762x54r` | `tacz:762x54r` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 FAL Tactical 308 20 发弹匣<br>25 发：测绘 FAL Tactical 308 25 发弹匣<br>30 发：测绘 FAL Tactical 308 30 发弹匣<br>50 发：测绘 FAL Tactical 308 50 发弹匣 | `classicr_fal_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 17 发：测绘 Glock 18 9mm 17 发弹匣<br>24 发：测绘 Glock 18 9mm 24 发弹匣<br>27 发：测绘 Glock 18 9mm 27 发弹匣<br>33 发：测绘 Glock 18 9mm 33 发弹匣 | `classicr_glock18_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 M1A1 Thompson 45acp 30 发弹匣<br>40 发：测绘 M1A1 Thompson 45acp 40 发弹匣<br>45 发：测绘 M1A1 Thompson 45acp 45 发弹匣<br>50 发：测绘 M1A1 Thompson 45acp 50 发弹匣 | `classicr_m1a1_thompson_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 100 发：测绘 M60 308 100 发弹匣<br>150 发：测绘 M60 308 150 发弹匣<br>175 发：测绘 M60 308 175 发弹匣<br>200 发：测绘 M60 308 200 发弹匣 | `classicr_m60_308_belt` | `tacz:308` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 8 发：测绘 M82A2 50bmg 8 发弹匣<br>10 发：测绘 M82A2 50bmg 10 发弹匣<br>12 发：测绘 M82A2 50bmg 12 发弹匣<br>15 发：测绘 M82A2 50bmg 15 发弹匣 | `classicr_m82a2_50bmg` | `tacz:50bmg` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 M92FS 9mm 15 发弹匣<br>17 发：测绘 M92FS 9mm 17 发弹匣<br>20 发：测绘 M92FS 9mm 20 发弹匣<br>24 发：测绘 M92FS 9mm 24 发弹匣 | `classicr_m92fs_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 MAC-10 9mm 30 发弹匣<br>35 发：测绘 MAC-10 9mm 35 发弹匣<br>40 发：测绘 MAC-10 9mm 40 发弹匣<br>45 发：测绘 MAC-10 9mm 45 发弹匣 | `classicr_mac10_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 MK47 762x39copper 30 发弹匣<br>45 发：测绘 MK47 762x39copper 45 发弹匣<br>60 发：测绘 MK47 762x39copper 60 发弹匣<br>75 发：测绘 MK47 762x39copper 75 发弹匣 | `classicr_mk47_762x39copper` | `tacz:762x39copper` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 MP7 46x30 20 发弹匣<br>30 发：测绘 MP7 46x30 30 发弹匣<br>40 发：测绘 MP7 46x30 40 发弹匣<br>60 发：测绘 MP7 46x30 60 发弹匣 | `classicr_mp7_46x30` | `tacz:46x30` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 MP9 9mm 30 发弹匣<br>34 发：测绘 MP9 9mm 34 发弹匣<br>37 发：测绘 MP9 9mm 37 发弹匣<br>46 发：测绘 MP9 9mm 46 发弹匣 | `classicr_mp9_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 MRAD 338 10 发弹匣<br>12 发：测绘 MRAD 338 12 发弹匣<br>13 发：测绘 MRAD 338 13 发弹匣<br>15 发：测绘 MRAD 338 15 发弹匣 | `classicr_mrad_338` | `tacz:338` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 MRAD ELR 416barrett 10 发弹匣<br>12 发：测绘 MRAD ELR 416barrett 12 发弹匣<br>13 发：测绘 MRAD ELR 416barrett 13 发弹匣<br>15 发：测绘 MRAD ELR 416barrett 15 发弹匣 | `classicr_mrad_elr_416barrett` | `tacz:416barrett` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 MSR 30_06 10 发弹匣 | `classicr_msr_3006` | `tacz:30_06` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 NGSW-R 68x51fury 20 发弹匣<br>25 发：测绘 NGSW-R 68x51fury 25 发弹匣<br>35 发：测绘 NGSW-R 68x51fury 35 发弹匣<br>45 发：测绘 NGSW-R 68x51fury 45 发弹匣 | `classicr_ngsw_r_68x51` | `tacz:68x51fury` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 QBZ-191 58x42 30 发弹匣<br>45 发：测绘 QBZ-191 58x42 45 发弹匣<br>60 发：测绘 QBZ-191 58x42 60 发弹匣<br>75 发：测绘 QBZ-191 58x42 75 发弹匣 | `classicr_qbz191_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 SCAR MK20 308 10 发弹匣<br>20 发：测绘 SCAR MK20 308 20 发弹匣<br>30 发：测绘 SCAR MK20 308 30 发弹匣<br>50 发：测绘 SCAR MK20 308 50 发弹匣 | `classicr_scar_mk20_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 SPR-15 556x45 20 发弹匣<br>25 发：测绘 SPR-15 556x45 25 发弹匣<br>38 发：测绘 SPR-15 556x45 38 发弹匣<br>45 发：测绘 SPR-15 556x45 45 发弹匣 | `classicr_spr15_556` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 17 发：测绘 STI 2011 9mm 17 发弹匣<br>19 发：测绘 STI 2011 9mm 19 发弹匣<br>23 发：测绘 STI 2011 9mm 23 发弹匣<br>27 发：测绘 STI 2011 9mm 27 发弹匣 | `classicr_sti2011_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 17 发：测绘 TEC-9 9mm 17 发弹匣<br>24 发：测绘 TEC-9 9mm 24 发弹匣<br>33 发：测绘 TEC-9 9mm 33 发弹匣<br>72 发：测绘 TEC-9 9mm 72 发弹匣 | `classicr_tec9_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 19 发：测绘 TTI G34 9mm 19 发弹匣<br>24 发：测绘 TTI G34 9mm 24 发弹匣<br>27 发：测绘 TTI G34 9mm 27 发弹匣<br>33 发：测绘 TTI G34 9mm 33 发弹匣 | `classicr_tti_g34_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 22 发：测绘 UDP-9 9mm 22 发弹匣<br>30 发：测绘 UDP-9 9mm 30 发弹匣<br>45 发：测绘 UDP-9 9mm 45 发弹匣<br>60 发：测绘 UDP-9 9mm 60 发弹匣 | `classicr_udp9_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 8 发：测绘 鲁格P08 8 发弹匣 | `hamster_lugerp08_compact_8` | `hamster:compact_ammo` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 麦德森 30 发弹匣 | `hamster_madsen_long_30` | `hamster:long_ammo` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 8 发：测绘 马卡洛夫 8 发弹匣 | `hamster_makarov_compact_8` | `hamster:compact_ammo` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 100 发：测绘 MG14/17 100 发弹链箱 | `hamster_mg1417_long_belt` | `hamster:long_ammo` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 32 发：测绘 MP18 32 发弹匣 | `hamster_mp18_compact_32` | `hamster:compact_ammo` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：M14 .308 10 发弹匣<br>20 发：测绘 M14 .308 20 发弹匣<br>30 发：测绘 M14 .308 30 发弹匣<br>50 发：测绘 M14 .308 50 发弹匣 | `m14_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 博伊斯反坦克步枪 .50 BMG 5 发弹匣 | `murasamet_boys_50bmg` | `tacz:50bmg` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 47 发：测绘 DP/DPM 7.62×54R 47 发盘式弹匣 | `murasamet_dp_pan_762x54` | `tacz:762x54` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 70 发：测绘 KP/-31 9 mm 70 发鼓式弹匣 | `murasamet_kp31_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 兰彻斯特 9 mm 32 发弹匣 | `murasamet_lanchester_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 M3/M3A1 .45 ACP 30 发弹匣 | `murasamet_m3_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 75 发：测绘 MG34 7.92×57 75 发弹链 | `murasamet_mg34_792x57_belt` | `tacz:792x57` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 75 发：测绘 MG42 7.92×57 75 发弹链 | `murasamet_mg42_792x57_belt` | `tacz:792x57` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 32 发：测绘 MP28 9 mm 32 发弹匣 | `murasamet_mp28_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 MP38/MP40 9 mm 32 发弹匣 | `murasamet_mp38_40_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 25 发：测绘 PPSh 7.62×25 25 发弹匣<br>35 发：测绘 PPSh 7.62×25 35 发弹匣<br>50 发：测绘 PPSh 7.62×25 50 发弹匣<br>71 发：测绘 PPSh 7.62×25 71 发弹匣 | `murasamet_ppsh_762x25` | `tacz:762x25` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 斯登 Mk II/Mk V 9 mm 32 发弹匣 | `murasamet_sten_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 汤普森 .45 ACP 20 发弹匣<br>30 发：测绘 汤普森 .45 ACP 30 发弹匣<br>40 发：测绘 汤普森 .45 ACP 40 发弹匣<br>50 发：测绘 汤普森 .45 ACP 50 发弹匣 | `murasamet_thompson_drum_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 8 发：测绘 TT-33 7.62×25 8 发弹匣 | `murasamet_tt33_762x25` | `tacz:762x25` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 Vz.61 9 mm 20 发弹匣<br>25 发：测绘 Vz.61 9 mm 25 发弹匣<br>35 发：测绘 Vz.61 9 mm 35 发弹匣<br>50 发：测绘 Vz.61 9 mm 50 发弹匣 | `murasamet_vz61_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 EM-2 20 发弹匣<br>25 发：测绘 EM-2 25 发弹匣<br>30 发：测绘 EM-2 30 发弹匣<br>32 发：测绘 EM-2 32 发弹匣 | `rainforest_em2_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 FAL 20 发弹匣<br>25 发：测绘 FAL 25 发扩容弹匣<br>30 发：测绘 FAL 30 发扩容弹匣<br>32 发：测绘 FAL 32 发扩容弹匣 | `rainforest_fal_308` | `tacz:308` | `detachable_magazine` | `family_reused_detachable_magazine` | `family_level_material` → `tacz_extra:item/magazine_fal_308_20_tacz_308` |
| 25 发：测绘 FAMAS F1 25 发弹匣<br>30 发：测绘 FAMAS F1 30 发弹匣<br>32 发：测绘 FAMAS F1 32 发弹匣<br>35 发：测绘 FAMAS F1 35 发弹匣 | `rainforest_famas_556` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 FR-F2 5 发弹匣<br>7 发：测绘 FR-F2 7 发弹匣<br>9 发：测绘 FR-F2 9 发弹匣<br>10 发：测绘 FR-F2 10 发弹匣 | `rainforest_frf2_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 M60 20 发弹链箱<br>35 发：测绘 M60 35 发弹链箱<br>50 发：测绘 M60 50 发弹链箱 | `rainforest_m60_308_belt` | `tacz:308` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 20 发：测绘 PM12S 20 发弹匣<br>25 发：测绘 PM12S 25 发弹匣<br>30 发：测绘 PM12S 30 发弹匣<br>32 发：测绘 PM12S 32 发弹匣 | `rainforest_pm12s_9x19` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 PM-63 15 发弹匣<br>25 发：测绘 PM-63 25 发弹匣<br>30 发：测绘 PM-63 30 发弹匣<br>32 发：测绘 PM-63 32 发弹匣 | `rainforest_pm63_9x19` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 35 发：测绘 RPD 35 发弹链箱<br>45 发：测绘 RPD 45 发弹链箱<br>50 发：测绘 RPD-MS 50 发弹链箱<br>75 发：测绘 RPD 75 发弹链箱<br>100 发：测绘 RPD-MS 100 发弹链箱 | `rainforest_rpd_762x39_belt` | `tacz:762x39` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_rpk_drum` |
| 10 发：测绘 Vz.64 10 发弹匣<br>12 发：测绘 Vz.64 12 发弹匣<br>15 发：测绘 Vz.64 15 发弹匣<br>20 发：测绘 Vz.64 20 发弹匣 | `rainforest_vz64_9x19` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 Vz.61 10 发弹匣<br>12 发：测绘 Vz.61 12 发弹匣<br>15 发：测绘 Vz.61 15 发弹匣<br>20 发：测绘 Vz.61 20 发弹匣 | `rainforest_vz68_9x19` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：SCAR-H .308 20 发弹匣<br>30 发：测绘 SCAR-H .308 30 发弹匣<br>45 发：测绘 SCAR-H .308 45 发弹匣<br>60 发：测绘 SCAR-H .308 60 发弹匣 | `scar_h_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 AKS-74U 545x39 30 发弹匣<br>34 发：测绘 AKS-74U 545x39 34 发弹匣<br>37 发：测绘 AKS-74U 545x39 37 发弹匣<br>40 发：测绘 AKS-74U 545x39 40 发弹匣 | `suffuse_aks74u_545x39` | `suffuse:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 34 发：测绘 SUFFUSE AR57 57x28 34 发弹匣<br>37 发：测绘 SUFFUSE AR57 57x28 37 发弹匣<br>40 发：测绘 SUFFUSE AR57 57x28 40 发弹匣<br>50 发：测绘 SUFFUSE AR57 57x28 50 发弹匣 | `suffuse_ar57_57x28` | `tacz:57x28` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 ASh-12 12.7x55 10 发弹匣<br>15 发：测绘 ASh-12 12.7x55 15 发弹匣<br>20 发：测绘 ASh-12 12.7x55 20 发弹匣<br>30 发：测绘 ASh-12 12.7x55 30 发弹匣 | `suffuse_ash12_127x55` | `suffuse:12.7x55` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 5 发：测绘 AW50 50bmg 5 发弹匣<br>6 发：测绘 AW50 50bmg 6 发弹匣<br>8 发：测绘 AW50 50bmg 8 发弹匣<br>10 发：测绘 AW50 50bmg 10 发弹匣 | `suffuse_aw50_50bmg` | `tacz:50bmg` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 AXMC 338 10 发弹匣<br>12 发：测绘 AXMC 338 12 发弹匣<br>14 发：测绘 AXMC 338 14 发弹匣<br>16 发：测绘 AXMC 338 16 发弹匣 | `suffuse_axmc_axsr_338` | `tacz:338` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 DVL-10 308 10 发弹匣<br>12 发：测绘 DVL-10 308 12 发弹匣<br>14 发：测绘 DVL-10 308 14 发弹匣<br>16 发：测绘 DVL-10 308 16 发弹匣 | `suffuse_dvl10_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 40 发：测绘 SUFFUSE GEPARDPDW 9mm 40 发弹匣<br>43 发：测绘 SUFFUSE GEPARDPDW 9mm 43 发弹匣<br>45 发：测绘 SUFFUSE GEPARDPDW 9mm 45 发弹匣<br>47 发：测绘 SUFFUSE GEPARDPDW 9mm 47 发弹匣 | `suffuse_gepardpdw_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 6 发：测绘 GM6 Lynx 50bmg 6 发弹匣<br>8 发：测绘 GM6 Lynx 50bmg 8 发弹匣<br>10 发：测绘 GM6 Lynx 50bmg 10 发弹匣 | `suffuse_gm6_50bmg` | `tacz:50bmg` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 KAC PDW 6x35mm 30 发弹匣<br>40 发：测绘 KAC PDW 6x35mm 40 发弹匣<br>50 发：测绘 KAC PDW 6x35mm 50 发弹匣<br>60 发：测绘 KAC PDW 6x35mm 60 发弹匣 | `suffuse_kacpdw_6x35` | `suffuse:6x35mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 6 发：测绘 M200 .408ct 6 发弹匣<br>7 发：测绘 M200 .408ct 7 发弹匣<br>8 发：测绘 M200 .408ct 8 发弹匣<br>10 发：测绘 M200 .408ct 10 发弹匣 | `suffuse_m200_408ct` | `suffuse:.408ct` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 SUFFUSE MAS38 7.65x20mm 32 发弹匣 | `suffuse_mas38_7_65x20mm` | `suffuse:7.65x20mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 MPDR 556x45 20 发弹匣<br>40 发：测绘 MPDR 556x45 40 发弹匣<br>50 发：测绘 MPDR 556x45 50 发弹匣<br>60 发：测绘 MPDR 556x45 60 发弹匣 | `suffuse_mpdr_556` | `tacz:556x45` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 SUFFUSE NP762 762x25 10 发弹匣<br>12 发：测绘 SUFFUSE NP762 762x25 12 发弹匣<br>15 发：测绘 SUFFUSE NP762 762x25 15 发弹匣<br>17 发：测绘 SUFFUSE NP762 762x25 17 发弹匣 | `suffuse_np762_762x25` | `tacz:762x25` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 120 发：测绘 PKP Pecheneg 762x54 120 发弹匣 | `suffuse_pkp_762x54_belt` | `tacz:762x54` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 30 发：测绘 QBU-191 58x42 30 发弹匣<br>35 发：测绘 QBU-191 58x42 35 发弹匣<br>50 发：测绘 QBU-191 58x42 50 发弹匣<br>75 发：测绘 QBU-191 58x42 75 发弹匣 | `suffuse_qbu191_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 QBZ-191 58x42 20 发弹匣<br>30 发：测绘 QBZ-191 58x42 30 发弹匣<br>40 发：测绘 QBZ-191 58x42 40 发弹匣<br>75 发：测绘 QBZ-191 58x42 75 发弹匣 | `suffuse_qbz191_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 QBZ-192 58x42 30 发弹匣<br>35 发：测绘 QBZ-192 58x42 35 发弹匣<br>50 发：测绘 QBZ-192 58x42 50 发弹匣<br>75 发：测绘 QBZ-192 58x42 75 发弹匣 | `suffuse_qbz192_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 QBZ-95-1 58x42 30 发弹匣<br>35 发：测绘 QBZ-95-1 58x42 35 发弹匣<br>50 发：测绘 QBZ-95-1 58x42 50 发弹匣<br>75 发：测绘 QBZ-95-1 58x42 75 发弹匣 | `suffuse_qbz951_58x42` | `tacz:58x42` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 RM277 6.8tvcm 20 发弹匣<br>30 发：测绘 RM277 6.8tvcm 30 发弹匣 | `suffuse_rm277_68tvcm` | `suffuse:6.8tvcm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 Saddam Golden AK 762x39 30 发弹匣<br>34 发：测绘 Saddam Golden AK 762x39 34 发弹匣<br>37 发：测绘 Saddam Golden AK 762x39 37 发弹匣<br>40 发：测绘 Saddam Golden AK 762x39 40 发弹匣 | `suffuse_saddam_ak_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 SVD 762x54 10 发弹匣<br>12 发：测绘 SVD 762x54 12 发弹匣<br>15 发：测绘 SVD 762x54 15 发弹匣<br>20 发：测绘 SVD 762x54 20 发弹匣 | `suffuse_svd_762x54` | `tacz:762x54` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 15 发：测绘 SUFFUSE TEC9 9mm 15 发弹匣<br>20 发：测绘 SUFFUSE TEC9 9mm 20 发弹匣<br>30 发：测绘 SUFFUSE TEC9 9mm 30 发弹匣<br>50 发：测绘 SUFFUSE TEC9 9mm 50 发弹匣 | `suffuse_tec9_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 SUFFUSE TT33 762x25 7 发弹匣<br>10 发：测绘 SUFFUSE TT33 762x25 10 发弹匣<br>13 发：测绘 SUFFUSE TT33 762x25 13 发弹匣<br>16 发：测绘 SUFFUSE TT33 762x25 16 发弹匣 | `suffuse_tt33_762x25` | `tacz:762x25` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 12 发：测绘 SUFFUSE TTI2011 9mm 12 发弹匣<br>20 发：测绘 SUFFUSE TTI2011 9mm 20 发弹匣<br>25 发：测绘 SUFFUSE TTI2011 9mm 25 发弹匣<br>30 发：测绘 SUFFUSE TTI2011 9mm 30 发弹匣 | `suffuse_tti2011_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 SUFFUSE UMP45 45acp 20 发弹匣<br>25 发：测绘 SUFFUSE UMP45 45acp 25 发弹匣<br>30 发：测绘 SUFFUSE UMP45 45acp 30 发弹匣<br>50 发：测绘 SUFFUSE UMP45 45acp 50 发弹匣 | `suffuse_ump45_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 12 发：测绘 SUFFUSE USP45 45acp 12 发弹匣<br>15 发：测绘 SUFFUSE USP45 45acp 15 发弹匣<br>18 发：测绘 SUFFUSE USP45 45acp 18 发弹匣 | `suffuse_usp45_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 12 发：测绘 SUFFUSE USP45 BLACK 45acp 12 发弹匣<br>15 发：测绘 SUFFUSE USP45 BLACK 45acp 15 发弹匣<br>18 发：测绘 SUFFUSE USP45 BLACK 45acp 18 发弹匣 | `suffuse_usp45_black_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 12 发：测绘 SUFFUSE VIPER2011 9mm 12 发弹匣<br>15 发：测绘 SUFFUSE VIPER2011 9mm 15 发弹匣<br>20 发：测绘 SUFFUSE VIPER2011 9mm 20 发弹匣<br>25 发：测绘 SUFFUSE VIPER2011 9mm 25 发弹匣 | `suffuse_viper2011_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 SUFFUSE WEBLEY1913 45acp 7 发弹匣<br>10 发：测绘 SUFFUSE WEBLEY1913 45acp 10 发弹匣<br>13 发：测绘 SUFFUSE WEBLEY1913 45acp 13 发弹匣<br>16 发：测绘 SUFFUSE WEBLEY1913 45acp 16 发弹匣 | `suffuse_webley1913_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 XM7 308 20 发弹匣<br>30 发：测绘 XM7 308 30 发弹匣 | `suffuse_xm7_308` | `tacz:308` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 WEMQL_R AK-12 5.45×39 30 发弹匣<br>40 发：测绘 WEMQL_R AK-12 5.45×39 40 发弹匣<br>60 发：测绘 WEMQL_R AK-12 5.45×39 60 发弹匣<br>75 发：测绘 WEMQL_R AK-12 5.45×39 75 发弹匣 | `wemql_r_ak12_545x39` | `tacz:545x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 WEMQL_R M7 6.8×51 20 发弹匣<br>25 发：测绘 WEMQL_R M7 6.8×51 25 发弹匣<br>30 发：测绘 WEMQL_R M7 6.8×51 30 发弹匣<br>45 发：测绘 WEMQL_R M7 6.8×51 45 发弹匣 | `wemql_r_m7_68x51` | `tacz:68x51fury` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 100 发：测绘 WW ANM2 30_06 100 发弹链<br>150 发：测绘 WW ANM2 30_06 150 发弹链<br>250 发：测绘 WW ANM2 30_06 250 发弹链<br>500 发：测绘 WW ANM2 30_06 500 发弹链 | `ww_anm2_30_06_belt` | `tacz:30_06` | `belt` | `neutral_belt_box` | `neutral_generic_material` → `tacz_extra:item/mag_m249_box` |
| 30 发：测绘 AS-44 7.62×39 30 发弹匣<br>34 发：测绘 AS-44 7.62×39 34 发弹匣<br>37 发：测绘 AS-44 7.62×39 37 发弹匣<br>40 发：测绘 AS-44 7.62×39 40 发弹匣 | `ww_as44_762x39` | `tacz:762x39` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 BAR .30-06 20 发弹匣<br>30 发：测绘 BAR .30-06 30 发弹匣<br>35 发：测绘 BAR .30-06 35 发弹匣<br>40 发：测绘 BAR .30-06 40 发弹匣 | `ww_bar_3006` | `tacz:30_06` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 WW CPH 32acp 7 发弹匣<br>9 发：测绘 WW CPH 32acp 9 发弹匣<br>12 发：测绘 WW CPH 32acp 12 发弹匣<br>14 发：测绘 WW CPH 32acp 14 发弹匣 | `ww_cph_32acp` | `ea:32acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 47 发：测绘 WW DP28 762x54 47 发弹匣 | `ww_dp28_762x54` | `tacz:762x54` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 Gewehr 43 7.92×57 10 发弹匣<br>15 发：测绘 Gewehr 43 7.92×57 15 发弹匣<br>20 发：测绘 Gewehr 43 7.92×57 20 发弹匣 | `ww_g43_792x57` | `ea:792x57` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 7 发：测绘 WW M1911A1 45acp 7 发弹匣<br>9 发：测绘 WW M1911A1 45acp 9 发弹匣<br>12 发：测绘 WW M1911A1 45acp 12 发弹匣<br>14 发：测绘 WW M1911A1 45acp 14 发弹匣 | `ww_m1911a1_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 100 发：测绘 M1919A6 .30-06 100 发弹链<br>150 发：测绘 M1919A6 .30-06 150 发弹链<br>250 发：测绘 M1919A6 .30-06 250 发弹链<br>500 发：测绘 M1919A6 .30-06 500 发弹链 | `ww_m1919a6_3006_belt` | `tacz:30_06` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 15 发：测绘 M1/M2 卡宾枪 .30 Carbine 15 发弹匣<br>20 发：测绘 M1/M2 卡宾枪 .30 Carbine 20 发弹匣<br>30 发：测绘 M1/M2 卡宾枪 .30 Carbine 30 发弹匣 | `ww_m1_m2_carbine_30c` | `ww:30c` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 WW M2S 30c 30 发弹匣 | `ww_m2s_30c` | `ww:30c` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 WW M50 45acp 20 发弹匣<br>21 发：测绘 WW M50 45acp 21 发弹匣<br>30 发：测绘 WW M50 45acp 30 发弹匣 | `ww_m50_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 WW M712 763 10 发弹匣<br>20 发：测绘 WW M712 763 20 发弹匣<br>25 发：测绘 WW M712 763 25 发弹匣<br>30 发：测绘 WW M712 763 30 发弹匣 | `ww_m712_763` | `ww:763` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 MG34 7.92×57 50 发弹链<br>100 发：测绘 MG34 7.92×57 100 发弹链<br>150 发：测绘 MG34 7.92×57 150 发弹链<br>200 发：测绘 MG34 7.92×57 200 发弹链 | `ww_mg34_792x57_belt` | `ea:792x57` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 50 发：测绘 MG42 7.92×57 50 发弹链<br>100 发：测绘 MG42 7.92×57 100 发弹链<br>150 发：测绘 MG42 7.92×57 150 发弹链<br>200 发：测绘 MG42 7.92×57 200 发弹链 | `ww_mg42_792x57_belt` | `ea:792x57` | `belt` | `family_reused_belt_box` | `family_level_material` → `tacz_extra:item/mag_m134_belt` |
| 30 发：测绘 MP28 9 mm 30 发弹匣<br>50 发：测绘 MP28 9 mm 50 发弹匣<br>75 发：测绘 MP28 9 mm 75 发弹匣<br>100 发：测绘 MP28 9 mm 100 发弹匣 | `ww_mp28_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 MP34 9 mm 32 发弹匣<br>50 发：测绘 MP34 9 mm 50 发弹匣<br>75 发：测绘 MP34 9 mm 75 发弹匣<br>100 发：测绘 MP34 9 mm 100 发弹匣 | `ww_mp34_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 MP38/40/41 9 mm 32 发弹匣<br>50 发：测绘 MP38/40/41 9 mm 50 发弹匣<br>75 发：测绘 MP38/40/41 9 mm 75 发弹匣<br>100 发：测绘 MP38/40/41 9 mm 100 发弹匣 | `ww_mp38_40_41_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 8 发：测绘 WW P08 765 8 发弹匣<br>19 发：测绘 WW P08 765 19 发弹匣<br>23 发：测绘 WW P08 765 23 发弹匣<br>27 发：测绘 WW P08 765 27 发弹匣 | `ww_p08_765` | `ww:765` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 8 发：测绘 WW P38 9mm 8 发弹匣<br>19 发：测绘 WW P38 9mm 19 发弹匣<br>23 发：测绘 WW P38 9mm 23 发弹匣<br>27 发：测绘 WW P38 9mm 27 发弹匣 | `ww_p38_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 35 发：测绘 WW PPS 762x25 35 发弹匣<br>71 发：测绘 WW PPS 762x25 71 发弹匣 | `ww_pps_762x25` | `tacz:762x25` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 WW S1100 763 32 发弹匣<br>50 发：测绘 WW S1100 763 50 发弹匣<br>75 发：测绘 WW S1100 763 75 发弹匣<br>100 发：测绘 WW S1100 763 100 发弹匣 | `ww_s1100_763` | `ww:763` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 32 发：测绘 斯登 Mk II 9 mm 32 发弹匣<br>50 发：测绘 斯登 Mk II 9 mm 50 发弹匣<br>75 发：测绘 斯登 Mk II 9 mm 75 发弹匣<br>100 发：测绘 斯登 Mk II 9 mm 100 发弹匣 | `ww_sten_mk2_9mm` | `tacz:9mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 StG 44 7.92×33 30 发弹匣<br>34 发：测绘 StG 44 7.92×33 34 发弹匣<br>37 发：测绘 StG 44 7.92×33 37 发弹匣<br>40 发：测绘 StG 44 7.92×33 40 发弹匣 | `ww_stg44_792x33` | `ea:792x33` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 10 发：测绘 SVT/AVT 7.62×54R 10 发弹匣<br>15 发：测绘 SVT/AVT 7.62×54R 15 发弹匣<br>20 发：测绘 SVT/AVT 7.62×54R 20 发弹匣 | `ww_svt_avt_762x54` | `tacz:762x54` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 WW T100 8mm 30 发弹匣<br>50 发：测绘 WW T100 8mm 50 发弹匣<br>75 发：测绘 WW T100 8mm 75 发弹匣<br>100 发：测绘 WW T100 8mm 100 发弹匣 | `ww_t100_8mm` | `ww:8mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 WW T100L 8mm 30 发弹匣<br>50 发：测绘 WW T100L 8mm 50 发弹匣<br>75 发：测绘 WW T100L 8mm 75 发弹匣<br>100 发：测绘 WW T100L 8mm 100 发弹匣 | `ww_t100l_8mm` | `ww:8mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 8 发：测绘 WW T14 8mm 8 发弹匣<br>9 发：测绘 WW T14 8mm 9 发弹匣<br>12 发：测绘 WW T14 8mm 12 发弹匣<br>14 发：测绘 WW T14 8mm 14 发弹匣 | `ww_t14_8mm` | `ww:8mm` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 T20 加兰德 .30-06 20 发弹匣<br>21 发：测绘 T20 加兰德 .30-06 21 发弹匣<br>22 发：测绘 T20 加兰德 .30-06 22 发弹匣<br>25 发：测绘 T20 加兰德 .30-06 25 发弹匣 | `ww_t20_3006` | `tacz:30_06` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 WW T96 65a 30 发弹匣<br>40 发：测绘 WW T96 65a 40 发弹匣 | `ww_t96_65a` | `ww:65a` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 30 发：测绘 WW T99 77a 30 发弹匣<br>40 发：测绘 WW T99 77a 40 发弹匣 | `ww_t99_77a` | `ww:77a` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 50 发：测绘 WW TBE 763 50 发弹匣<br>75 发：测绘 WW TBE 763 75 发弹匣<br>100 发：测绘 WW TBE 763 100 发弹匣 | `ww_tbe_763` | `ww:763` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |
| 20 发：测绘 汤普森 .45 ACP 20 发弹匣<br>30 发：测绘 汤普森 .45 ACP 30 发弹匣 | `ww_thompson_45acp` | `tacz:45acp` | `detachable_magazine` | `neutral_detachable_magazine` | `neutral_generic_material` → `tacz:item/magazine` |

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
