# TACZ Industry Blocks：已应用的用户模型包

`TACZ_industry_blocks.zip` 是用户上传到 `26.2(main)` 的原始资源包，SHA-256：

```text
bc348da760f92ee33b5df9ff71133f18a3a89e4c3d80037cbce8ce4047c23ac6
```

这是用户随后上传的更新版：两份 Blockbench 方块模型增加了 `ambientocclusion: false`，
用于避免精细机械内部小面被环境遮蔽压成不应出现的大块暗面。

其 `assets/tacz_extra/**` 内容已由工业生成器逐字节嵌入模组资源；玩家只安装模组即可看到
实际方块模型，不需要另放该 ZIP。原 ZIP 的 `pack.mcmeta` 仍是旧的独立包格式，因此它作为
作者源档保留，而不是被直接当作 26.2 独立资源包分发。

## 已绑定

| 实际方块 ID | 用户模型 | 图集 | 元素数 |
| --- | --- | --- | ---: |
| `tacz:cartridge_assembly_machine` | `tacz_extra:block/cartridge_assembly_machine` | 128×128 | 88 |
| `tacz:industrial_salvage_station` | `tacz_extra:block/industrial_salvage_station` | 128×128 | 81 |

运行时路径是：

```text
tacz:block/<machine>                 -> tacz_extra:block/<machine>
tacz:item/<machine>                  -> tacz:block/<machine>
tacz:blockstate/<machine>/facing=*   -> tacz:block/<machine> 的 0/90/180/270° 变体
```

两个方块已增加水平 `facing` 状态，放置时朝向玩家反方向；旧世界没有该属性的状态会落到默认北向，
不会要求迁移或删除方块实体。

## 审计

- `TACZ_industry_blocks_asset_report.json`：模型/PNG/包装模型/方块状态的机器可读核对结果；
- [`docs/INDUSTRY_BLOCK_ASSET_COVERAGE.md`](../../docs/INDUSTRY_BLOCK_ASSET_COVERAGE.md)：
  已应用方块和剩余高保真视觉待办的可读汇总；
- [`docs/INDUSTRY_ICON_COVERAGE.md`](../../docs/INDUSTRY_ICON_COVERAGE.md)：精确到每个
  `item + NBT selector` 的完整清单。
