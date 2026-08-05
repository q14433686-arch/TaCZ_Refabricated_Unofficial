# TACZ Extra Icons：26.2 修复包与运行时映射

`TACZ_icons_pack_fixed.zip` 由用户提供的 `TACZ_icons_pack.zip` 修复生成，保留其原有
`tacz_extra` 命名空间、模型和中文名称。

## 已修复

- `pack.mcmeta` 的资源包格式从旧的 `15` 更新为 Minecraft **26.2** 的资源格式 `88`；
- 对全部 61 张 32×32 PNG 做逐像素 RGBA 检查；
- 移除了 3 张弹药/火箭图标（12G、40 mm、RPG-7）边缘连通的错误不透明画布；
- 移除了 124 个与图标主体断开的黑色边缘扫描线/条纹伪影；
- 恢复了 46 个被透明 alpha 错误吞掉、且被前景像素包围的彩色/描边像素；
- 结果详情、源 ZIP SHA-256 和逐图标修复计数在
  `TACZ_icons_pack_repair_report.json` 中。

修复工具是：

```bash
python3 tools/repair_tacz_icon_pack.py INPUT.zip OUTPUT.zip --report REPORT.json
```

它使用 ImageMagick `convert`，只处理可验证的透明像素遗漏、边缘条纹和异常画布；
不会用 AI 重绘或模糊/缩放原像素画。

## 现在的游戏内绑定

绑定层已经实现，不再只是一个独立 ZIP：

1. 修复后的 `assets/tacz_extra/**` 已逐字节嵌入本模组资源，因此玩家**不必**额外安装 ZIP
   才能看到已绑定的图标；ZIP 仍保留为可单独使用/覆盖的资源包；
2. 客户端资源重载会读取任意资源包中的
   `assets/<namespace>/industry_icons/*.json`；
3. 映射按 `priority`、匹配字段数量、映射 ID 的顺序选择，支持：
   `AmmoId`、`MagazineFamily`、`MagazineAmmoId`、`MagazineCapacity`、
   `CartridgeCaliber`、`ProjectileType`、`IndustryPartKind`、`IndustryPlatform`、
   `DieTargetKind`；
4. 当前默认映射在
   `src/main/resources/assets/tacz/industry_icons/default.json`，作者源文件在
   `tools/industry/icon_mapping.json`；
5. `tacz:ammo` 的 GUI 图标、`tacz:magazine`、以及所有 NBT 工业件（弹壳、弹头、模具、
   组件、蓝图、毛坯）都通过该层解析。没有匹配时会回退到原有 TACZ 图，而不会变成空白。

这是一层**客户端视觉数据**：不新增重复物品、不改枪包配方/弹道数据，也不要求玩家运行
Python。第三方枪包作者只需在自己的资源包中添加映射 JSON 和纹理；详见
[`docs/INDUSTRY_ICON_MAPPING.md`](../../docs/INDUSTRY_ICON_MAPPING.md)。

## 精确缺图清单（不是宽泛分类）

完整且可机器核对的清单是：

- `TACZ_industry_icon_catalog.json`：当前 **825 个**实际 `item + NBT selector` 身份、
  每一项的当前贴图、覆盖级别和所需图键；
- [`docs/INDUSTRY_ICON_COVERAGE.md`](../../docs/INDUSTRY_ICON_COVERAGE.md)：同一清单的
  人类可读表格。每一条 `needs_art = true` 都是一项具体的可绑定身份。

当前最紧急、已有实际运行时身份而没有专用图的项目是：

| 范围 | 精确缺项 |
| --- | --- |
| 默认散装弹药（5） | `.500 Magnum`、`5.45×39`、`5.7×28`、`6.8×51 Fury`、`7.92×57` |
| 实体供弹器（7） | `fal_308`、`g36_556`、`mp5_9x19`、`m14_308`、`m9_9x19`、`mk23_45acp`、`evolys_308_belt` |
| 爆炸弹头/中间态（9） | 12G 铅丸/装药芯、40 mm HE 芯、RPG-7 HEAT 芯、40 mm 榴弹壳、40 mm 榴弹体、40 mm 高爆装药榴弹体、RPG-7 战斗部弹体、RPG-7 装药战斗部、RPG-7 聚能破甲战斗部预制件 |
| 已击发弹壳 | 23 个实际口径身份当前只是复用新壳图；目录按 `casing_*` 家族归并出应制作的暗化/压痕变体 |
| 工业链 | 24 个弹壳模具、24 个弹头模具、5 个口径量规、6 个中性毛坯、53 个蓝图、265 个平台组件、265 个组件模具、53 个命名外装套件；每个 selector 都已列在精确清单中 |
| 静态工业物品 | `magazine_blank`、`cartridge_assembly_machine`、`industrial_salvage_station`、`magazine_pouch`、`magazine_loader` 的最终图 |

`RPG motor housing` 目前**没有对应的真实 ItemStack/NBT 中间态**，因此没有把它伪造成
“可映射但缺图”的项目；先在生产链中增加实际阶段，再给它绑定图。管式、转轮、双管和内部
弹仓同理：它们当前存于枪械数据而不是 `tacz:magazine`，不能错误计入 `MagazineFamily` 图标缺项。
