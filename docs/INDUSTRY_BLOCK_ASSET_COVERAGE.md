# 工业方块模型与材质覆盖

此文件由 `tools/generate_industry_content.py` 生成。它区分已经能正确解析的实际模型/PNG
和仍待补齐的高保真身份图，不把“已有基础后备图”误报成完成。

## 已应用的用户方块资源

| 方块 | 源模型 | 图集尺寸 | 元素数 | 面数 | AO | 绑定状态 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `tacz:cartridge_assembly_machine` | `tacz_extra:block/cartridge_assembly_machine` | 128×128 | 88 | 528 | False | bound |
| `tacz:industrial_salvage_station` | `tacz_extra:block/industrial_salvage_station` | 128×128 | 81 | 486 | False | bound |

两个模型保留在 `tacz_extra` 命名空间；实际注册方块通过 `tacz:block/...` 父模型包装引用它们。
方块现在拥有水平 `facing` 状态，对应用户包提供的四个旋转 blockstate 变体；旧世界无此状态的方块会采用默认北向。

## 仍待补齐的高保真视觉身份

- 仍缺 **0** 个精确运行时视觉身份；
- 按可共享图键归并后是 **0** 个待办组；
- 这不是“当前会紫黑”的文件数：所有已注册工业物品/方块仍有后备模型或贴图。
- 此处的 0 仅表示没有硬缺图；第三方弹匣/弹链箱的中性或家族复用细分美术待办另列于 `THIRD_PARTY_FEED_GAP_REGISTER.md`。

| 类别 | 仍缺精确身份数 |
| --- | ---: |

### 仍缺的静态工业物品图


完整到每个 `item + NBT selector` 的清单见：
`extras/icon_packs/TACZ_industry_icon_catalog.json` 与 `docs/INDUSTRY_ICON_COVERAGE.md`。
