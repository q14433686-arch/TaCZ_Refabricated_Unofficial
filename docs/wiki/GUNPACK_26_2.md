# 枪包 Wiki：适配 Fabric 26.2 / TaCZ 1.2.0

这是本移植的 26.2 补充说明，不是 TaCZ 官方 Wiki。通用美术和数据字段可查[上游枪包 Wiki 源稿](https://github.com/MCModderAnchor/tacwiki/tree/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack)；本线的目录、版本和运行边界以本页为准。只想安装游玩的读者请看[玩家指南](../PLAYER_GUIDE_26_2.md)。

## 包被识别了，为什么没加载？

目录与 ZIP 都放在实例 `tacz/` 下。`gunpack.meta.json` 必须在**包根**，不能隔着一层同名目录。区分三个版本：

| 位置 | 26.2 的含义 |
|---|---|
| 模组 `fabric.mod.json` 的 `version` | `1.2.0+fabric.26.2.R1`；`dependencies.tacz` 比较的是核心 **1.2.0**。`>=1.1.8` 与 `[1.2.0]` 匹配，精确 `1.1.8` 与 `[1.1.8,1.2.0)` 不匹配。 |
| `overlays.entries[].minecraft` | 按 **Minecraft** 版本选择，例如 `[26.2]`，不是按模组版本或资源包格式号选择。后列出的匹配层覆盖前者，匹配层优先于包根。 |
| `assets/<命名空间>/gunpack_info.json` | 作者填写的包信息和许可。内置包的[原始署名文件](../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/gunpack_info.json)仍写 `1.1.8`，不是装错了模组，也不用改它。 |

例如，一个确实面向此游戏版本、需要 1.2 功能的包可以这样声明；若不使用覆盖层，可省去整个 `overlays`：

```json
{
  "namespace": "example",
  "dependencies": { "tacz": ">=1.2.0", "minecraft": "[26.2]" },
  "overlays": { "entries": [{ "directory": "compat_26_2", "minecraft": "[26.2]" }] }
}
```

在这个示例中，`compat_26_2/` 必须真实存在，按需放 `assets/`、`data/`。日志里 `mod dependency mismatch` 指模组依赖未满足；版本匹配也不代表资源、Lua、模型或配方都能用。服务器会同步索引数据，不会替客户端安装私有模型、贴图和音效。

## 从已有包迁移

已有 `gunpack.meta.json` 的旧包，先以管理员运行 `/tacz convert universal "Example Pack.zip" check` 看报告，需要时再用 `export`。输出在 `tacz-converted/`；不会改写源包，也不会替你安装。保留原件，在**独立实例**只装导出件验证。更老的无元数据布局另走 `tacz_backup/` 和 `/tacz convert`。只改 `dependencies.tacz` 不能移植内容。

- 原版合成与专用台配方不是同一套界面。26.2 使用单数 `data/<ns>/recipe/`；可对照[默认专用台配方](../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/recipe/gun/ak47.json)和[工作台过滤器](../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/recipe_filters/default.json)。不要把 26.3 的物品修饰符格式照搬到本线。
- 转换报告的 `targetFormatValidated` 是格式检查，不是 `gameValidated`。用对应版本的两端实际加载，检查配方、物品组件、显示和脚本；完整步骤见[转换指南](../GUNPACK_CONVERSION_26_2.md)。

## 镜片、分划和热像：先查实际引用

以默认包的[标准 8× 索引](../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/index/attachments/scope_standard_8x.json)为例，它引用的是 [`scope_scout_display`](../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/attachments/scope_scout_display.json)，其中 `model` 指向 `tacz:attachment/scope_scout_geo`，`zoom` 两档是 4.5 与 10。按 **索引 → display → 模型与纹理**排查，不能凭商品名猜同名文件。倍率档与 `views` 选择的视角也不是一回事。

镜身镀膜、`division` 刻线和自发光标记应分开判断。热镜则先比较可见光目标与暴露热像，再放实心方块检查遮挡；不要拿显示字、准星或眼前特效当成墙后热源。[r5.8.1 调查](../investigations/ADS_COMPLEMENTARY_R581_26_2.md)附逐档与遮挡数据，覆盖默认包而非全部自制包。

[本线变更与验收](../CHANGELOG_26_2_1_2_0.md) · [默认包来源清单](../../DEFAULT_PACK_120.tsv) · [代码和资源的不同许可](../../LICENSES.md)
