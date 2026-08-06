# 工业方块模型与材质覆盖

此文件由 `tools/generate_industry_content.py` 生成。它区分已经能正确解析的实际模型/PNG
和仍待补齐的高保真身份图，不把“已有基础后备图”误报成完成。

## 已应用的用户方块资源

| 方块 | 源模型 | 纹理 | 图集尺寸 | 元素数 | 面数 | 绑定状态 |
| --- | --- | --- | ---: | ---: | ---: | --- |
| `tacz:cartridge_assembly_machine` | `tacz_extra:block/cartridge_assembly_machine` | `tacz_extra:block/cartridge_assembly_machine` | 128×128 | 88 | 528 | bound |
| `tacz:industrial_salvage_station` | `tacz_extra:block/industrial_salvage_station` | `tacz_extra:block/industrial_salvage_station` | 128×128 | 81 | 486 | bound |

两个模型保留在 `tacz_extra` 命名空间；实际注册方块通过 `tacz:block/...` 父模型包装引用它们。
方块现在拥有水平 `facing` 状态，对应用户包提供的四个旋转 blockstate 变体；旧世界无此状态的方块会采用默认北向。

## 仍待补齐的高保真视觉身份

- 仍缺 **746** 个精确运行时视觉身份；
- 按可共享图键归并后是 **731** 个待办组；
- 这不是“当前会紫黑”的文件数：所有已注册工业物品/方块仍有后备模型或贴图。

| 类别 | 仍缺精确身份数 |
| --- | ---: |
| cartridge_case_die | 24 |
| cartridge_gauge | 5 |
| cartridge_projectile_die | 24 |
| fresh_cartridge_case | 1 |
| loose_ammo | 5 |
| physical_magazine | 7 |
| platform_blueprint | 53 |
| platform_component | 265 |
| platform_component_die | 265 |
| platform_furniture_kit | 53 |
| projectile_core | 3 |
| shared_ammunition_intermediate | 4 |
| shared_gun_intermediate | 6 |
| spent_cartridge_case | 23 |
| static_industrial_item | 3 |
| visible_projectile_intermediate | 5 |

### 仍缺的静态工业物品图

- `tacz:magazine_blank` — `static:tacz:magazine_blank`
- `tacz:magazine_loader` — `static:tacz:magazine_loader`
- `tacz:magazine_pouch` — `static:tacz:magazine_pouch`

完整到每个 `item + NBT selector` 的清单见：
`extras/icon_packs/TACZ_industry_icon_catalog.json` 与 `docs/INDUSTRY_ICON_COVERAGE.md`。
