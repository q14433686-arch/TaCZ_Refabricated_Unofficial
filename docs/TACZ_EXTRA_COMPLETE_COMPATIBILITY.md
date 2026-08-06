# TACZ Extra Complete 包兼容性与整合结果

用户提供的 `TACZ_extra_COMPLETE.zip` 已逐项核对。它是完整高保真图源，
但其原始映射 JSON 不是本项目运行时直接读取的 schema，因此由生成器转换而不是原样加载。

## 资源核对

- 原 ZIP SHA-256：`e9e0ce54070cf689e4b42c0fc68e812638a88afff9171d4e4cabf2c7bc48f150`；
- 物品 PNG：841，尺寸分布：{'32x32': 841}；
- 物品模型：843（generated：841，方块父模型：2，自定义 display：0）；
- 用户 exact 身份映射：746；与当前精确缺图身份一一对应；
- 原料静态物品映射：8；
- 生成的运行时 NBT 映射：746 条；
- 额外静态物品模型包装：11 条。

## 原写法在本环境中的结论

- `pack.mcmeta.pack_format = 15`，不是 26.2 standalone 包格式 88：**不能直接当独立资源包安装**；
- `industry_icon_exact.json`（`identity -> texture_name`）语义正确，但不是 `assets/*/industry_icons/*.json` 的 `entries[]` runtime schema；
- `industry_icon_rules.json` 的 family/tint 规则是作者规则语言，当前 Java 映射器不会执行；
- 因此整合方式是：嵌入模型/PNG，生成 `assets/tacz/industry_icons/complete.json`，不嵌入那三份原始 authoring JSON 以免被错误解析。

## 已覆盖的静态物品

- `tacz:carbon_dust` → `tacz_extra:item/carbon_dust`
- `tacz:cinnabar_dust` → `tacz_extra:item/cinnabar_dust`
- `tacz:high_carbon_steel_ingot` → `tacz_extra:item/high_carbon_steel_ingot`
- `tacz:high_carbon_steel_plate` → `tacz_extra:item/high_carbon_steel_plate`
- `tacz:industrial_propellant` → `tacz_extra:item/industrial_propellant`
- `tacz:magazine_blank` → `tacz_extra:item/static_tacz_magazine_blank`
- `tacz:magazine_loader` → `tacz_extra:item/static_tacz_magazine_loader`
- `tacz:magazine_pouch` → `tacz_extra:item/static_tacz_magazine_pouch`
- `tacz:pig_iron_ingot` → `tacz_extra:item/pig_iron_ingot`
- `tacz:primer` → `tacz_extra:item/primer`
- `tacz:sulfur_dust` → `tacz_extra:item/sulfur_dust`

完整 source/report 见：
- `extras/icon_packs/TACZ_extra_COMPLETE.zip`
- `extras/icon_packs/TACZ_extra_COMPLETE_compatibility_report.json`
- `extras/icon_packs/TACZ_industry_icon_catalog.json`
