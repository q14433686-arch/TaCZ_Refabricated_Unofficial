# TACZ Extra Icons：26.2 修复包

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

它使用 ImageMagick 的 `convert`，只处理可验证的透明像素遗漏、边缘条纹和异常画布；
不会用 AI 重绘或模糊/缩放原像素画。

## 使用状态

该 ZIP 是一个可直接放入资源包目录的**独立图标库**。它的模型/语言仍使用
`tacz_extra` 命名空间；TACZ 当前的 `tacz:ammo`、`tacz:magazine`、
`cartridge_case` 和 `projectile_core` 是 NBT 泛型物品，尚未自动将每个 NBT
变体绑定到这些图标。绑定层应在补齐缺失图标并确认最终视觉映射后一起实现，避免
错误把一个枪包图标强行映射到第三方内容。

## 仍建议补充的图标

### 默认弹药口径（当前 ZIP 缺失）

- 5.45×39；
- 5.7×28；
- 6.8×51 Fury；
- 7.92×57。

### 当前工业链专用中间件

- 12G 铅丸/装药坯；
- 40 mm 榴弹弹体、40 mm 高爆装药榴弹体；
- RPG-7 战斗部弹体、RPG-7 装药战斗部、RPG-7 聚能破甲战斗部预制件；
- 40 mm 榴弹壳与 RPG 火箭发动机壳体；
- 已击发弹壳的暗化/压痕变体。

### 实体供弹器与工业装备

- FN FAL、G36、MP5、Mk14、M9、MK23、FN Evolys .308 弹链箱等缺失弹匣/弹链箱；
- .357 / .500 转轮快速装填器，以及管式霰弹枪的装填管/壳托视觉；
- 工业蓝图、平台模具、口径量规、外装套件、外装毛坯的视觉族；
- 弹药装配机、工业回收站、弹匣袋、装弹器的最终高分辨率物品/方块贴图。

完成这些图标后，下一步应建立 `tacz_extra` 图标与 TACZ NBT 身份
（AmmoId、MagazineFamily、CartridgeCaliber、ProjectileType、IndustryPartKind）
之间的数据驱动映射，而不是重新注册数十个重复物品。
