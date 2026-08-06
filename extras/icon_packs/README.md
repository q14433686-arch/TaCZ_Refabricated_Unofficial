# TACZ Extra Icons：修复批次、完整包与运行时映射

本目录保留两代用户提供的 `tacz_extra` 图源：

| 文件 | 用途 |
| --- | --- |
| `TACZ_icons_pack_fixed.zip` | 第一批 61 张图标的可验证像素修复结果；其修复报告仍见 `TACZ_icons_pack_repair_report.json`。 |
| `TACZ_extra_COMPLETE.zip` | 当前完整高保真图源：833 张 32×32 RGBA 物品 PNG、835 个物品模型、两台工业机器模型，以及 746 条精确视觉身份映射。 |

## 第一批修复记录

`TACZ_icons_pack_fixed.zip` 从用户最初提供的 `TACZ_icons_pack.zip` 生成：

- 将旧独立包 `pack_format` 从 15 更新为 26.2 的 88；
- 对 61 张 PNG 做逐像素 RGBA 检查；
- 清理 3 张图的错误不透明画布、124 个边缘扫描线/条纹；
- 恢复 46 个被透明 alpha 吞掉且被前景包围的像素；
- 修复工具：

```bash
python3 tools/repair_tacz_icon_pack.py INPUT.zip OUTPUT.zip --report REPORT.json
```

完整包会覆盖其中同名的旧物品图；旧修复包及报告仍保留，用于追溯第一批资源来源。

## 完整包在 26.2 环境中的结论

完整包的**艺术资源和身份覆盖是正确的**，但原 ZIP 不能直接作为本项目的独立资源包：

1. 它的 `pack.mcmeta.pack_format` 仍为 `15`，而 Minecraft 26.2 需要资源格式 `88`；
2. `assets/tacz_extra/industry_icons/industry_icon_exact.json` 使用的是
   `identity -> texture_name` 作者格式，不是 TACZ 运行时要求的 `entries[]` + Item/NBT selector 格式；
3. `industry_icon_rules.json` 是家族/染色规则作者语言，当前 Java 映射器不执行该规则语言；
4. 示例 `example_platform_family.json` 是作者说明，不是当前可直接加载的数据包文件。

因此整合采用**适配而非误读**：生成器嵌入模型/PNG，但不把三份原始 authoring JSON 放到运行时
`assets/tacz_extra/industry_icons/`；它会生成合法的：

```text
assets/tacz/industry_icons/default.json   # 原有基础表
assets/tacz/industry_icons/complete.json  # 完整包转换出的 746 条精确 NBT 映射
```

兼容性报告见：

```text
TACZ_extra_COMPLETE_compatibility_report.json
docs/TACZ_EXTRA_COMPLETE_COMPATIBILITY.md
```

## 当前游戏内覆盖

- 完整包的 **746 条 exact identity** 与当前生成器发现的 **746 条原有精确缺图身份一一对应；**
- 合并后，当前 **825 个**实际 `item + NBT selector` 身份全部有图；
- 三个普通静态物品也改为实际模型父包装：

```text
tacz:magazine_blank
tacz:magazine_loader
tacz:magazine_pouch
```

- 两台实体机器模型/贴图由
  [`extras/industry_packs/`](../industry_packs/README.md) 单独管理；
- 玩家不需要运行 Python，也不需要额外安装这些 ZIP：资源已嵌入模组；
- 第三方包仍可在 `assets/<namespace>/industry_icons/*.json` 中提供合法 runtime mapping，
  详细 schema 见 [`docs/INDUSTRY_ICON_MAPPING.md`](../../docs/INDUSTRY_ICON_MAPPING.md)。

## 审计入口

```text
extras/icon_packs/TACZ_industry_icon_catalog.json
docs/INDUSTRY_ICON_COVERAGE.md
docs/INDUSTRY_BLOCK_ASSET_COVERAGE.md
```

`RPG motor housing` 仍没有真实 ItemStack/NBT 中间态；它不是“缺图”，而是尚未存在可绑定的生产阶段。
管式、转轮、双管和内部弹仓同样存于枪械数据而非 `tacz:magazine`，不应伪报成弹匣图标缺项。
