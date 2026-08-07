# 工业参考档案、身份别名与运行时审计

第三方枪包的 `GunId`、工作台结果 ID、`GunData.reload.type` 和现实供弹结构不是同一件事。
本层把它们拆开，避免把历史包中的转轮、管式、漏夹、弹链、燃料武器误判成可拆卸弹匣枪。

它是**运行时数据层**：玩家安装枪包后只需重载资源；不运行 Python，不修改枪包 ZIP，也不依赖游戏内联网查百科。

## 三层身份

```text
工作台 recipe id
  → 结果 GunId / AmmoId / AttachmentId
  → 已加载 Index + Data
  → IndustryReferenceProfile（实际结构/供弹/弹药类别）
  → 高保真声明或未来的“测绘通用工业线”
```

- **工作台结果**说明配方当前想产出什么；
- **Index + Data**说明该 ID 在当前资源集合中是否真实存在，以及 `ammo`、`ammo_amount` 等可核对事实；
- **参考档案**说明实际动作、供弹器、弹药类别和制造投影；
- 对每一把已成功加载 Index/Data 的枪，运行时还会生成一条保守的 `automatic_candidate`：它记录 GunId、实际 AmmoId、Index 类型和 `surveyed` 制造层级，但供弹固定为 `legacy`，不会猜测可拆卸弹匣；
- 三者不一致时，TACZ 默认保留原行为并报告问题，绝不按文件名猜测后静默改枪。

## 参考档案位置

资源 id 就是目标枪 ID：

```text
data/<gun namespace>/industry/reference/guns/<gun path>.json
```

例如：

```text
data/ww/industry/reference/guns/mg42.json
→ ww:mg42
```

兼容数据包可以在自己的 GPL 数据层中提供 `data/ww/...`，不必修改 Enlisted 的原 ZIP。目标枪包未安装时，这类外部 namespace profile 保持 dormant，不会刷错误；目标 GunIndex 出现后才校验并激活。内置默认包 53 把枪的参考档案由作者生成器从现有工业平台/供弹声明生成，作为完整 schema 示例；游戏玩家不需要执行生成器。

## `IndustryReferenceProfile` schema v1

```json
{
  "schema_version": 1,
  "canonical_model": "ww/mg42",
  "display_name": "MG 42",
  "action": "roller_locked_recoil",
  "feed": {
    "device": "belt",
    "runtime_mechanism": "belt",
    "carrier_behavior": "inserted_retained",
    "family": "ww_mg42_792x57_belt",
    "capacity": 50,
    "reload_batch": 0
  },
  "ammunition": {
    "class": "cartridge",
    "nominal": "792x57",
    "expected_ammo": "ww:792x57"
  },
  "manufacturing": {
    "profile": "belt_fed_service",
    "tier": "service"
  },
  "confidence": "curated",
  "evidence": [
    "pack index/data inspected for this version",
    "curated historical/feed reference"
  ]
}
```

### 字段约束

| 字段 | 含义 | 强制核对 |
|---|---|---|
| `canonical_model` | 稳定参考键；不要求等于 GunId | 小写安全 token |
| `action` | 实际动作/结构族，如 `gas_rifle`、`bolt_action`、`long_recoil`、`launcher` | 小写安全 token；不能拿 `reload.type` 代替 |
| `feed.device` | 现实/设计上的供弹设备 | 见下表 |
| `feed.runtime_mechanism` | 当前 TACZ 已可执行的机制 | 只能是现有 `FeedMechanism` 或 `legacy` |
| `family` + `capacity` | 实体供弹器或装填工具规格 | `detachable_magazine` / `belt` 必填；桥夹/快装器声明运行时机制时也应填写族；容量均核对 `GunData.ammo_amount` |
| `ammunition.expected_ammo` | 当前包实际应加载的 AmmoId | 若写出，必须精确等于 `GunData.ammo` |
| `manufacturing.tier` | `legacy` / `service` / `advanced` / `precision` / `surveyed` | 受限枚举 |
| `confidence` | `curated` / `pack_declared` / `world_confirmed` / `automatic_candidate` | 非自动档案必须给 `evidence` |

### 供弹设备不是一律“弹匣”

| `feed.device` | 语义 | 当前运行时机制 |
|---|---|---|
| `detachable_magazine` | 可插拔并保留余弹的实体弹匣 | `detachable_magazine` |
| `belt` | 可插拔实体弹链箱/弹鼓 | `belt` |
| `internal_box` | 固定内部仓 | `internal_box` |
| `tube` | 管式、逐发装填 | `tube` |
| `revolver` | 转轮 | `revolver` |
| `single_shot` | 单发后膛/发射管 | `single_shot` |
| `stripper_clip` | 可复用夹条；装填工具，不是插入式弹匣 | `stripper_clip`：向内部仓增量转入；转空仍保留夹条 |
| `en_bloc_clip` | 漏夹；入枪、打空弹出 | `en_bloc_clip`：独立已安装漏夹状态，最后一发离枪后自动弹出 |
| `speedloader` | 转轮快速装弹器 | `speedloader`：向内部转轮增量转入 |
| `fuel_canister` / `utility` | 燃料、医疗、工具类消耗物 | `legacy` |

这使资料表能准确记录 M1 Garand、转轮、霰弹枪、火焰/医疗设备，即使当前实体供弹代码尚未支持每一种设备；**记录事实不等于错误启用行为**。桥夹、快装器、漏夹与未来长按 R 选择圆盘的完整状态机见 [`FEED_DEVICE_AND_CLIP_DESIGN.md`](FEED_DEVICE_AND_CLIP_DESIGN.md)。

## 明确身份别名

旧枪包可能让 `recipes/gun/56.json` 输出 `lol141:56`，而实际加载的枪是 `rainforest:56`。TACZ 不会根据文件名自动改写；兼容作者必须提供显式别名：

```text
data/<any namespace>/industry/id_aliases/rainforest_56.json
```

```json
{
  "recipe": "rainforest:gun/56",
  "kind": "gun",
  "target": "rainforest:56",
  "expected_ammo": "tacz:762x39",
  "expected_capacity": 10,
  "confidence": "curated",
  "reason": "Upstream recipe retained an old lol141 namespace."
}
```

别名在工作台配方解析前应用，但必须同时满足：

1. `target` 的 Index/Data 已实际加载；
2. `kind` 与原配方 `result.type` 相同；
3. gun alias 若声明 `expected_ammo` / `expected_capacity`，必须和当前 `GunData` 精确一致；ammo alias 可声明 `expected_stack_size`，必须等于当前 AmmoIndex；
4. 没有两条别名争夺同一个 recipe id。

失败时不会改写原配方，只会记录错误。内置兼容 alias 的 target 包未安装时会保持 dormant（debug 级提示），不会让可选枪包缺失变成服务端错误；目标 Index 出现后才自动激活。

## 运行时审计

`CREATE_FLY` 启用时，参考档案加载器会在工作台配方完成后记录汇总：

```text
TACZ industry runtime audit:
  gun / ammo / attachment result count
  direct-resolved count
  explicit-alias count
  unresolved count
  curated-profile count
  safe “surveyed” gun candidate count
```

对无法解析的结果只列出有限数量的精确 `recipe id → declared id → reason`，避免第三方大包刷屏。未解析项保持旧工作台行为，不会被自动工业门槛伪装成可制造的工业对象。

管理员可在服务器执行：

```text
/tacz industry audit
/tacz industry reference <namespace:path>
```

前者显示直接解析/别名/未解析/已校验/测绘候选总数，并额外报告已接受、可选包休眠、或因 Ammo/容量不符被拒绝的 `gun_feed` 适配声明；后者显示一把枪的实际或保守测绘参考行、运行时供弹状态、弹药事实、制造档位和证据。它是检查表，不会借查询操作改变供弹机制。

另有 gun-pack 预检与资源包装层：它会识别 `data/.../recipes`、`index/...`、`data/...` 中“内容像 JSON 但缺 `.json` 后缀”的文件，并只对这些受限 TACZ 数据目录公开同路径的虚拟 `.json` 别名。原文件仍存在、ZIP 不被修改；若同路径已有真实 `.json`，真实文件优先，虚拟别名不会覆盖它。

## 第三方作者最小接入层级

1. **零文件但结果身份已解析**：`CREATE_FLY` 会生成明确标注的测绘 fallback：测绘档案包 + 空白工装页 + 测绘夹具 → master dossier → production template；五种中性结构毛坯 + template + fixture → 测绘平台结构套件；原枪包的真实材料表再加 kit/template/fixture，仍在 Gunsmith Table 这个真实多槽 GUI 中完成终端制造。未知供弹保持 `legacy`。
2. **仅 ReferenceProfile**：测绘流程会显示真实动作、供弹和弹药事实，但不会自动猜出尚未实现的 clip/fuel 行为。
3. **ReferenceProfile + id_aliases**：修复旧包错误 ID，得到稳定身份与受事实守卫的测绘工艺。
4. **再提供 `industry/gun_feed` / 高保真 Create 声明**：通过运行时 Ammo/容量验证后启用实体弹匣、弹链箱或完整专用组件线；已通过验证的测绘枪还会获得真实 Gunsmith Table 供弹器委托。手工高保真声明优先，测绘 GUI fallback 不会覆盖它。完整字段、制造出口和拒绝边界见 [`THIRD_PARTY_MAGAZINE_ADAPTER.md`](THIRD_PARTY_MAGAZINE_ADAPTER.md)。

测绘 fallback 故意不声称知道第三方模型的真实闭锁块/供弹盘几何：它只称“测绘平台结构套件”，并保留原材料账单。动态 dossier/template/kit 的显示名会追加 `[namespace:gun]`，可以在 Gunsmith Table 搜索中按实际 GunId 区分，不需要伪造一个未经证实的部件名。

对已解析、非 `tacz:`、且至少有一把普通 `magazine` / `manual` 消费枪的第三方散装弹，运行时还会生成测绘弹药链：

```text
原弹药材料表 + 中性弹药基准量规毛坯 + 测绘夹具
→ [namespace:ammo] 测绘弹药量规

中性弹壳毛坯 + 量规 + 夹具 → 对应测绘口径弹壳
中性弹头毛坯 + 量规 + 夹具 → 对应测绘口径弹头
测绘弹壳 + 测绘弹头 + 底火 + 工业推进药
→ 专用四槽弹药装配机成品批次
```

原工作台弹药配方在该链有效时会移除，避免绕过专用装配机。测绘普通弹会生成精确 AmmoId/口径身份的已击发弹壳，并由量规/夹具 GUI 工艺重新整形；没有指定口径贴图时，弹壳、已击发弹壳、弹头使用低优先级标准工业材质族，已有精确口径映射会覆盖它。玩家可见名称优先使用该 AmmoIndex 的实际成品名，例如 `Long Ammo · 测绘弹药量规`，内部 `surveyed/<namespace>/<ammo>` 只用于唯一 NBT 匹配和不会直接显示为物品名。`fuel`、`inventory`、默认 `tacz:` 弹药、无消费者或未解析弹药仍不进入这条链。

后续内存生成的 Create 终端只会消费“身份已解析且有足够结构证据”的枪，避免覆盖作者明确提供的工艺。
