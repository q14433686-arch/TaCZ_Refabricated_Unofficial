# 第三方枪包兼容性审计基线

审计对象由维护者上传至 `26.2(main)`，只在隔离临时目录读取 ZIP；本仓库**不复制、不重新分发**这些枪包资源。

> 审计范围：ZIP 路径、`gunpack.meta.json`、`index`、`data`、`recipes` 的静态结构和当前 TACZ 加载契约。
>
> 这不是实机游戏通过声明。实际加载仍须由外部 Java 25 环境验证。

## 汇总

| 枪包 | namespace | 有效 GunIndex + GunData | 启用本兼容层后可安全对应的成枪工作台身份 | 主要阻塞项 |
|---|---|---:|---:|---|
| Apocalypse v1.1.7 G | `bf1` | 30 | 30 | 15 个 JSON-looking 数据文件没有 `.json` 后缀，其中 6 个是工作台 recipe；虚拟 `.json` 别名会恢复 `lunge_mine` |
| Cold War 1947–1991 v0.51 | `rainforest` | 17 | 16 | 4 条明确 alias 修复旧结果 ID；AT4 没有上传的成枪工作台 recipe |
| GunpowderRevolution v1.2.7 | `hamster` | 36 | 35 | 42 个 Index 中有 6 个引用不存在的 gun-data；存在大量转轮、管式、杠杆、夹条/内部仓语义 |
| Enlisted v1.2.1.3 | `ww` | 52 | 52 | `ww:i37` 缺 gun-data；AS-44 alias 已修复，`wsp:sten9` 仍无可验证 target；部分 ammo 输出错配已单独 alias |
| **合计** | — | **135** | **133** | 剩余 2 把有效枪没有可验证的工作台成枪来源，不能靠文件名或 `reload.type` 自动猜测 |

这里的“安全对应”表示结果 ID 已有加载的同 ID GunIndex/GunData，或已通过带 ammo/capacity guard 的显式 alias 修复；不代表其已经获得高保真工业结构资料。

## 已确认的当前兼容行为

- 四包都使用旧目录 `data/<ns>/recipes/`；`TableRecipeManager` 已并行扫描这个目录，因此带 `.json` 的工作台配方会进入 TACZ 自建同步通道；
- 旧式 `{ "tag": ... }` / `{ "item": ... }` 材料写法由 `GunSmithTableIngredient` 延迟规范化；这不是本次新增问题；
- `DelegatingPackResources` 现在会为受限 TACZ 数据目录中的 extensionless JSON 公开虚拟 `.json` 别名，因此 `bf1` 的这六个资源不再因文件后缀被静默漏扫；原 ZIP 不被改写，真实 `.json` 同名文件仍优先；
- 当前已审计 gun result 会获得测绘 GUI fallback：测绘档案/生产工装/五种结构毛坯组成的平台结构套件会被加入原枪包真实材料账单；它仍不声称知道第三方的真实组件几何，也还没有生成专用弹药机或实体供弹器；
- 新增 `IndustryReferenceProfile`、`industry/id_aliases` 和运行时审计后，无法解析的结果不再被自动工业门槛伪装成可安全处理的对象。

## 重点反例

### 不能把 `reload.type = magazine` 当现实供弹机制

- GunpowderRevolution 的 Auto-5 使用 `tacz:m870_gun_logic`，但旧数据仍写 `reload.type = magazine`；
- 四包中存在转轮、双管、管式、固定内部仓、历史夹条/漏夹、弹链箱、火箭筒和燃料设备；
- 因此缺少 `industry/reference` 或 `industry/gun_feed` 的枪必须保持 `legacy` 供弹，直到有证据资料，不得自动生成实体弹匣。

### 弹药也不是一律金属弹药筒

上传包合计引用 16 个非默认 AmmoId。可直接作为普通盒式弹药候选的只有一部分：

- `hamster` 的 4 种自定义 ammo 有一对一 Index 与 table ammo recipe，是动态四槽弹药机的优先试验样本；
- `bf1:fuel` / `bf1:medkit` 分别属于燃料或医疗/工具语义，不能伪装成弹壳 + 弹头；
- `ww:303`、`ww:765` 有 Index，但其上传配方结果分别错误输出为其他 AmmoId；
- `ea:*` 等跨 namespace 引用在上传集合中没有对应 Index，必须等待依赖包或显式 alias/descriptor。

## 已内置的显式别名修复

兼容层已提供且仅在目标 Index 存在时才激活的别名：

- `rainforest:gun/56 → rainforest:56`；
- `rainforest:gun/m72 → rainforest:m72`；
- `rainforest:gun/spg1 → rainforest:hk_spg1`；
- `rainforest:gun/vz68 → rainforest:vz68`；
- `ww:as44 → ww:as44`（修正原结果 `tacz:as44`）；
- `ww:303 → ww:303`、`ww:765 → ww:765`（修正错配 ammo 输出）。

每条 gun alias 带 `expected_ammo` 和 `expected_capacity`；两条 ammo alias 带 `expected_stack_size`。如果包升级后这些事实变化，alias 会失效而不是错误套用。`wsp:sten9`、缺失 gun-data 的条目、跨包 `ea:*` 引用仍故意保持 unresolved，直到存在可验证 target 或上游修复。

## 下一阶段的使用方式

1. 安装包后运行 `/tacz industry audit`，先得到 direct / alias / unresolved / curated / surveyed 数量；
2. 对尚未覆盖的错误结果 ID 写 `industry/id_aliases`，且用 `expected_ammo`、`expected_capacity` 防止错误覆盖；
3. 对需要真实工业化的枪写 `industry/reference/guns/<gun>.json`，先记录动作、实际供弹设备和弹药类别；
4. 只有明确的 `detachable_magazine` / `belt` 才进入实体供弹器路线；`stripper_clip`、`en_bloc_clip`、`speedloader`、`fuel_canister` 先记录事实、保留 legacy，等待各自真实机制；
5. 后续内存生成资源层只接收当前已审计的 133 条安全工作台身份，默认包的手工高保真路线始终优先。
