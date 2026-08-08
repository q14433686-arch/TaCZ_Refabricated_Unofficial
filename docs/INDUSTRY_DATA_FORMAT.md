# 工业制造数据格式

工业化不应靠 Java 里堆材料数量。`CREATE_FLY` 档的扩展点分为三层：**真实 Create 工艺、终端顺序装配声明、供弹声明**。

所有 JSON 都可由内容包在自己的命名空间下提供；默认枪包不需要被修改。

内置平台的重复资源由作者工具生成，源定义、命令和碰撞验证规则见 [`INDUSTRY_GENERATION.md`](INDUSTRY_GENERATION.md)。第三方内容包仍可直接提供普通 JSON，不需要运行生成器。

第三方枪包的“工作台结果 ID、实际 GunData、现实供弹结构、工业投影”由独立的
[`INDUSTRY_REFERENCE_PROFILE.md`](INDUSTRY_REFERENCE_PROFILE.md) 契约约束。它提供 ReferenceProfile、明确 ID alias、运行时审计与缺 `.json` 后缀资源预检；不会把旧包的 `reload.type = magazine` 误当成真实可拆卸弹匣。

## 运行时自动识别与工业替代

`CREATE_FLY` 档默认开启 `AutoDiscoverIndustryReplacements`。服务器在**资源重载时**扫描所有未手工声明的枪械工作台配方；玩家不需要、也不应运行 Python 工具。

运行时能可靠识别原配方的结果类型、结果 ID、原材料和枪包数据，但不能从任意第三方 JSON 推断真实枪机/机匣几何。因此采用双层策略：

1. 已声明 `industry/assembly` 的平台保留高保真蓝图、模具、组件和顺序总装路线；
2. 没有声明的枪和配件保留原材料表，并在内存中追加通用工业门槛（枪械追加高碳钢板、枪械组件毛坯、黄铜板；配件追加高碳钢板、黄铜板）；
3. 有真实 AmmoIndex、至少一把普通消费枪且不属于 fuel/inventory 的弹药，不再把“弹壳毛坯 + 弹头毛坯 + 底火 + 推进药”堆回旧工作台。服务器保留原材料账单一次用于测绘量规委托，随后只允许精确 NBT 弹壳、弹头、底火和推进药进入四槽弹药装配机。

弹药接管按 **AmmoId** 而非旧配方文件名判定：一旦某 AmmoId 有显式或测绘装配定义，所有枪包中输出同一 AmmoId 的旧 Gunsmith Table 捷径都会在 `CREATE_FLY` 下移除。这样不同命名空间下重复的铜锭/火药配方不能绕过新工艺；`LEGACY` 档则完整保留它们。自动替代结果通过现有 `RECIPES` 同步通道传给枪械工作台、JEI 和 REI；不会写入默认枪包，不会要求客户端运行外部脚本。若某平台日后加入手工高保真声明，它会自动优先于通用回退。同口径 alternate AmmoId 的 `industry/ammo_profiles`、有序混装载具与背包直接交互格式另见 [`MIXED_AMMO_AND_TIMED_HANDLING_DESIGN.md`](MIXED_AMMO_AND_TIMED_HANDLING_DESIGN.md)。

---

## 1. Create Fly 工艺与 REI

把真实加工配方放在：

```text
data/<namespace>/recipe/create/<任意路径>.json
```

例如，多种材料的批量装弹属于 **工作盆（Basin）压实**，不是置物台：

```json
{
  "fabric:load_conditions": [
    { "condition": "fabric:all_mods_loaded", "values": ["create"] }
  ],
  "type": "create:compacting",
  "ingredients": ["yourmod:case", "yourmod:primer"],
  "results": [{ "id": "yourmod:cartridge", "count": 16 }]
}
```

### 置物台/工作盆的硬边界

这不是文案约定，而是 Create 的实际容器约束：

| 工艺 | 物理位置 | 可同时参与的物品 |
|---|---|---|
| `create:pressing` | 置物台或传送带 | **一个**输入堆；动力冲压机不接受多输入伪配方 |
| `create:deploying` | 置物台/传送带上的目标工件 + 部署器手持栏 | 置物台上始终只有**一个目标工件**；部署器只持有**一种**工具/零件 |
| `create:mixing` / `create:compacting` | 工作盆（Basin） | 可以由漏斗/传送带送入多种输入；这里才允许 `ingredients` 数组 |
| `create:sequenced_assembly` | 传送带/置物台上的过渡工件 | 始终只有**一个**过渡工件；每个后续工位依序部署一种零件/工具 |

因此，任何需要把“弹壳 + 弹头 + 底火”或“五种枪械组件”**同时堆在置物台上**的方案都是无效设计。多输入应改用工作盆；多部件装枪应改用下面的顺序装配，或另行使用有独立格位的机械合成器。

TACZ 会读取该目录中的支持工艺，向客户端同步一份工艺投影，并在 REI 中注册研磨、粉碎、加热搅拌、工作盆压实、单件冲压、部署器成型、真实多槽机械合成器和顺序枪械装配类别。

这条同步通道存在的原因是 Create Fly 当前 26.2 构建在其 `build.gradle` 中排除了 REI source set；JEI 能显示 Create 工艺而 REI 没有原生类别。TACZ 的桥接层不调用 Create 内部 Java API，只从实际配方 JSON 构建 REI 的输入/输出树。

- 直接物品 ID、`#item_tag` 与 TACZ 注册的 `forge:partial_nbt` 都可显示；
- `create:sequenced_assembly` 会把首个工件和各部署工位的供料汇总显示；`∞` 标记表示部署器保留其手持模板/模具；
- REI 的顺序装配页会明确标注“置物台/传送带始终只有一个工件”；列表中的其余输入是**按站依序**供给的，不是同时放到置物台；
- 其他复杂 Create 自定义 ingredient 仍建议由内容包同时提供一个直接物品显示入口；
- `fabric:load_conditions` 必须保留，避免没有 Create Fly 时让原版 RecipeManager 解析未知 `create:*` 类型。

---

## 2. 终端枪械顺序装配

一把枪有两份互相引用的数据：

1. 一个终端声明，用来关闭同 ID 的旧 TACZ 枪械工作台捷径；
2. 一个真正的 `create:sequenced_assembly` 配方，用来在 Create 生产线上产出成枪。

声明放在：

```text
data/<namespace>/industry/assembly/<原枪械工作台配方路径>.json
```

路径必须与原 TACZ 工作台配方 ID 一致。例如默认 AK 的旧配方 ID 是 `tacz:gun/ak47`，声明应为：

```text
data/tacz/industry/assembly/gun/ak47.json
```

示例：

```json
{
  "platform": "ak",
  "blueprint_display_name": "item.yourmod.gun_blueprint.ak",
  "terminal_process": "yourmod:create/industry/assemble_ak47",
  "components": [
    { "kind": "receiver", "display_name": "item.yourmod.component.ak_receiver" },
    { "kind": "bolt", "display_name": "item.yourmod.component.ak_bolt" },
    { "kind": "barrel", "display_name": "item.yourmod.component.ak_barrel" },
    { "kind": "trigger", "display_name": "item.yourmod.component.ak_trigger" },
    { "kind": "recoil", "display_name": "item.yourmod.component.ak_recoil" }
  ],
  "materials": [
    { "item": "minecraft:oak_planks", "count": 4 }
  ]
}
```

其中 `terminal_process` 是实际资源 ID；上例必须对应：

```text
data/yourmod/recipe/create/industry/assemble_ak47.json
```

其核心形状如下（示意，省略完整 NBT）：

```json
{
  "type": "create:sequenced_assembly",
  "ingredient": { "fabric:type": "forge:partial_nbt", "items": ["yourmod:component"], "nbt": {"IndustryPartKind": "receiver"} },
  "transitional_item": {
    "id": "yourmod:component",
    "components": { "minecraft:custom_data": {"IndustryPartKind": "incomplete_ak47"} }
  },
  "result": {
    "id": "tacz:modern_kinetic_gun",
    "components": { "minecraft:custom_data": {"GunId": "yourmod:ak47", "GunFireMode": "AUTO", "GunCurrentAmmoCount": 0, "HasBulletInBarrel": false} }
  },
  "sequence": [
    {
      "type": "create:deploying",
      "target": "$ingredient",
      "ingredient": { "fabric:type": "forge:partial_nbt", "items": ["yourmod:gun_blueprint"], "nbt": {"IndustryPartKind": "blueprint"} },
      "keep_held_item": true,
      "results": ["$result"]
    },
    {
      "type": "create:pressing",
      "ingredient": "$ingredient",
      "results": ["$result"]
    },
    {
      "type": "create:deploying",
      "target": "$ingredient",
      "ingredient": { "fabric:type": "forge:partial_nbt", "items": ["yourmod:component"], "nbt": {"IndustryPartKind": "bolt"} },
      "results": ["$result"]
    },
    {
      "type": "create:pressing",
      "ingredient": "$ingredient",
      "results": ["$result"]
    }
  ]
}
```

语义必须严格是：

1. 首个机匣/枪身进入传送带或置物台，成为**唯一**流动的过渡工件；
2. 第一台持蓝图的**部署/供料机械工位**以 `keep_held_item: true` 保留蓝图；随后工件必须经过一台**动力冲压机**完成首次压合；
3. 后续每个主要组件都按“部署器供入一种组件 → 动力冲压机压合”的真实交替站点推进：枪机、枪管、击发组、复进组件不能只被机械手一碰就完成；
4. 木料、皮革、黄铜、玻璃等**不会**在终端线上显示成“安装石头/皮革”之类的原料步骤：它们先在机械合成器变为中性外装毛坯，再由部署器持对应蓝图校准为有名称的平台外装套件；
5. 终端线只部署“AK 外装套件”“Glock 外装套件”等可理解的子总成，并由最终动力冲压机压合；
6. 工件按顺序经过工位，最终才变为带完整 `GunId`、首个有效射击模式、空膛/空备弹状态的 TACZ 枪；
7. 置物台上从头到尾没有、也不需要同时放入多个物品。

实际搭线时，把首件放上**传送带或置物台**，然后按 REI 顺序装配页底部的 `D→P→…` 图例沿线布置：`D` 是持有对应模板/组件/外装套件的供料工位，`P` 是动力冲压机，`F` 预留给液体喷嘴。工件通过一个站才会变成下一阶段的未完成总成，因此可以用一条直线传送带完成，或用回环生产线便于集中供料；不能把所有输入堆到一个置物台。

`CREATE_FLY` 档只有在下列条件都满足时才移除旧工作台成枪配方：

- `terminal_process` 指向的配方资源存在；
- 类型为 `create:sequenced_assembly`；
- 每个序列步骤都是单工件部署、单工件冲压或单工件灌装，且没有 `ingredients` 多输入数组。

声明丢失、ID 写错或工艺形状不合法时，TACZ 会记录警告并**保留旧工作台配方**，不会把成枪变成不可获得物品。

默认枪包全部 53 把枪都已使用此路径：已校准平台保留专用组件命名，其余默认枪由内置平台策略自动生成独立 NBT 平台。所有组件、模板、成枪结果都通过 `forge:partial_nbt` / `minecraft:custom_data` 确认身份；不同枪的平台件不能互相替代，模板保留，组件与辅料按工位消耗。

### 模板不再必须由对应成枪反推

原有的工作盆/压实模板制造路线**保留**，作为愿意自己制图的保底路线；但默认玩法不再要求“先有这把枪，才能得到制造它的模板”。生成器现在为全部 53 种模板同时生成两条独立来源：

- **26.2 数据驱动村民贸易**：`data/tacz/villager_trade/weaponsmith/5/blueprint_<platform>.json` 定义模板商品，并通过 `data/minecraft/tags/villager_trade/weaponsmith/level_5.json` 追加到大师武器匠的原版 trade tag。26.2 原版 `trade_set` 会从该 tag 的候选中随机抽取有限商品，因此每位**新生成/新升级**的大师武器匠提供的是可刷新的蓝图选项，而不是 53 页固定报价；
- **世界箱子**：`data/tacz/tacz_loot_injectors/industrial_blueprint_cache.json` 由 TACZ 既有战利品注入层处理，在武器匠/工具匠村庄箱、废弃矿井、要塞、掠夺者前哨、林地府邸、古城与试炼密室稀有奖励箱中按概率放入一张随机模板。

两条来源输出的都是同一枚带 `IndustryPlatform` / `IndustryPartKind=blueprint` 的真实模板物品，可直接被部署器持有；没有通过把成枪当作模板材料来伪造关联。数据包作者可覆盖这些 trade/loot 资源调整价格、箱子池或完全关闭其中一条来源。

枪械结构件的前段遵循“结构毛坯 → **中性组件模具体** → 部署器持对应结构毛坯选定几何 → 部署器持平台模板校准平台模具 → 部署器持模具成型组件”。这里的中性组件模具体只有一条工作盆压实来源；机匣/枪机/枪管/击发组/复进组件毛坯作为部署器中不消耗的实体量规，明确选择 `DieTargetKind`。这样不会再出现“五条相同 Basin 输入、却期望产出五种模具体”的不可合成配方。

外装件是独立的可理解子总成：原始木料/皮革/黄铜/玻璃先经**机械合成器**成为统一的 `furniture_blank`，再由部署器持平台蓝图、保留蓝图地校准成平台专属 `furniture_kit`。因此终端枪线只会安装命名套件，而不会把原始世界方块伪装成某个“下一步安装”的枪械零件；相同原料签名只生成同一种中性毛坯，平台差异完全由真实蓝图校准决定。

同样地，每个 `create:deploying` 步骤都只有一个置物台目标与一个部署器手持物。

---

## 3. 弹药身份与旧工作台弹药替换

弹药同样不靠材料数量区分：单输入动力冲压机只负责产出中性的 `tacz:cartridge_case_blank` 与 `tacz:projectile_blank`。之后由 Create 部署器持有一枚 NBT 标识的可复用 `tacz:press_die`，通过带 `keep_held_item: true` 的 `create:deploying` 工艺真正压制出指定身份。

压实阶段也不能让多条相同材料表直接各自产出不同口径模具。现在先压实**中性弹壳模具体**、**中性弹头模具体**和一枚**中性弹药基准量规毛坯**。有对应默认枪的口径由部署器持同口径完整枪，先把量规毛坯校准成带精确 `CartridgeCaliber` 的可复用口径基准量规；这同一枚量规再分别校准弹壳模具和弹头模具。默认包虽然提供散装弹、但没有任何对应枪械的 4.6×30、5.45×39、6.8×51 Fury、7.62×25、7.62×54R，则由真正的 Create **机械合成器**多槽 datum 配方直接制造对应淬硬口径量规；绝不使用不相干的枪冒充量规，也不使用同输入/改数量的 Basin 分支伪造口径。

成品 `tacz:cartridge_case` 保存 `CartridgeCaliber`，`tacz:projectile_core` 同时保存 `CartridgeCaliber` 与 `ProjectileType`。5.56 弹壳不能进入 9 mm 装弹工艺；以后增加 HP、AP、slug 等弹头，只需新增数据配方与模具，不需要再在 Java 里加口径分支。

这些精确身份也构成逆向证据：新／已击发弹壳只可消耗性地生成 `case_datum_gauge` 并校准对应弹壳模；弹头芯只可生成 `projectile_datum_gauge` 并校准对应弹头模；一发完整 `tacz:ammo` 样本才可生成同时服务两枚模具的完整 `cartridge_gauge`。因此不会把“捡到一个壳”误报为掌握完整弹头工艺。

40 mm HE 与 RPG-7 HEAT 不再把 TNT 和毛坯藏在一个不可见的顺序临时态里。它们的战斗部有实际可存放/可查看的中间物品：

```text
RPG-7：4 弹壳毛坯 --顺序部署/发动机壳体模--> RPG-7 火箭发动机壳体
      --部署器持发动机壳体模--> RPG-7 火箭发动机总成（可送入装弹机）

       4 弹头毛坯 --机械合成器--> RPG-7 战斗部弹体
      --部署 TNT--> RPG-7 装药战斗部
      --部署 TNT--> RPG-7 聚能破甲战斗部预制件
      --部署器持 HEAT 模具--> RPG-7 聚能破甲战斗部芯
```

RPG 发动机壳体是带 `IndustryPartKind: motor_housing` 的可见 `tacz:cartridge_case`，不能直接放进弹药装配机；必须经过 `finish_case_rpg_rocket` 的单工件部署站成为 `IndustryPartKind: case` 的火箭发动机总成。40 mm 同样先形成“40 mm 榴弹弹体”，再部署 TNT 形成“40 mm 高爆装药榴弹体”，最后持 HE 模具成型。每一步都是独立真实输出，失败、断线或物流中断时不会凭空丢失“中间态”。

最终装弹不再交给 `create:compacting` 的 Basin，也不再把四种物料伪装成传送带上的单一工件。它由 TACZ 的**弹药装配机**完成：GUI 中有独立的弹壳、弹头、底火、推进药四个输入槽与一个成品槽；按钮请求只发到服务端，服务端按数据定义验证 NBT、扣除四件材料并输出弹药。

定义放在：

```text
data/<namespace>/industry/cartridge_assembly/<任意名称>.json
```

```json
{
  "case_item": "yourmod:cartridge_case",
  "case_caliber": "556x45",
  "case_display_name": "item.yourmod.cartridge_case.556x45",
  "projectile_item": "yourmod:projectile_core",
  "projectile_caliber": "556x45",
  "projectile_type": "fmj",
  "projectile_display_name": "item.yourmod.projectile.556x45_fmj",
  "primer_item": "yourmod:primer",
  "propellant_item": "yourmod:propellant",
  "ammo": "yourmod:556x45",
  "count": 16,
  "case_count": 16,
  "projectile_count": 16,
  "primer_count": 16,
  "propellant_count": 3,
  "eject_case": true,
  "spent_case_display_name": "item.yourmod.cartridge_case.spent_556x45"
}
```

这不是“配方显示出来就算能做”的工作盆推断：四个 GUI 槽位分别验证，错口径弹壳、错弹头类型、错误底火或推进药会被服务端拒绝。定义会同步到客户端供 JEI/REI 显示弹药装配机配方；整枪和实体弹匣仍走各自既有的工业路线。

### 批量装配与弹药平衡

`count` 是一次完成后输出的散装弹数量；`case_count`、`projectile_count`、`primer_count` 是实际扣除的对应物理件数量，默认内置路线均等于 `count`，不会把一枚壳或一枚弹头凭空复制为整批弹药。`propellant_count` 则按弹种功率单独确定。清单中的 `case_blank_count` / `projectile_blank_count` 还规定每个最终壳/弹头需要的中性黄铜壳坯/弹头坯质量：普通手枪与中间威力为 1，常规全威力步枪为 2，马格南为 3，.50 BMG 与 RPG 壳体为 4。大口径成型会实际多经过若干部署器供料站，不是只把相同的一枚毛坯改个显示名。默认 24 种弹的平衡数据在 `tools/industry/cartridges.json`：

| 等级 | 典型弹种 | 每次输出 | 推进药 |
|---|---|---:|---:|
| rimfire / 手枪 / PDW | .22 WMR、9 mm、.45 ACP、4.6×30、5.7×28 | 20–32 | 1–2 |
| 中间威力 | 5.45、5.56、5.8、7.62×39 | 16 | 3 |
| 全威力步枪 | .308、.30-06、7.62×54R、7.92×57、6.8 Fury | 10–12 | 4 |
| 马格南 / 大口径 | .338、.45-70、.50 AE、.500、.50 BMG | 4–8 | 4–8 |
| 霰弹 / 爆炸物 | 12G、40 mm、RPG-7 | 8 / 1 / 1 | 3 / 2 / 6 |

生成器同时读取默认枪包旧弹药配方的“每发火药”比例；任何新清单若把 `propellant_count` 降到旧比例以下，或让 `batch_count` 超过成品堆叠上限/旧配方批量，`--check` 会拒绝。HE/HEAT 弹头此前还已单独消耗 TNT 战斗部，因此爆炸弹不会只靠普通推进药获得爆炸威力。

装配机也可接入物流：顶部与四个侧面均可输入物品，机器会按数据定义把弹壳、弹头、底火、推进药路由到各自唯一的槽位；**底面只允许取出成品**。给予红石信号后，机器每 40 tick 自动完成一次有效装配；无红石时仍可通过 GUI 手动点击“装配”。输入改变、配方不匹配、任一输入数量不足或成品槽无法容纳整批输出时进度会重置且绝不扣料。

### 真实抛壳与再整形

若定义设置 `eject_case: true`，服务端只会在一次 `reduceAmmoOnce()` **实际成功消耗**后生成一个原生 `ItemEntity`：

```text
tacz:cartridge_case
  IndustryPartKind: "spent_case"
  CartridgeCaliber: "<精确口径>"
  SpentCartridgeCase: true
```

它不是客户端抛壳动画、也不会按霰弹的 pellet 数重复生成；会像普通掉落物一样受物理、合并、漏斗和玩家拾取处理。实体起点按射手**右手侧**计算（面北时向东、面南时向西），客户端会让旧的纯视觉抛壳为这枚同步实体让位，避免看见两枚壳。该物品与可装填的 `IndustryPartKind: "case"` 不同，不能直接放回装配机：必须让**匹配口径**的弹壳模具由部署器持有，将它经 `recondition_case_<caliber>` 整形为未装填弹壳，之后仍需消耗新底火、推进药和弹头。RPG-7 在默认定义中明确不抛壳，因为火箭本体已被发射和消耗。

### 弹壳/弹头单格上限

成型后的 `tacz:cartridge_case`、`tacz:projectile_core`、顺序工艺中的对应过渡件、重新整形的弹壳和服务端抛出的 `spent_case` 都携带 `minecraft:max_stack_size`。它从同一 `AmmoId` 的最终散装弹 `stack_size` 读取，并按 26.2 的 `[1, 99]` 上界夹取：例如 9 mm 弹药上限为 60，则 9 mm 弹壳、FMJ 弹头和已击发弹壳也都是 60；40 mm 为 6。中性毛坯仍是无口径的通用物流件，不能预先冒充某种成品口径。旧存档中没有该组件的壳/弹头会在进入实体背包时按其精确口径/类型安全补正；无法唯一识别的数据包变体不会被随意猜测。

### 工业回收站、弹匣袋与装弹器

`industrial_salvage_station` 是一输入、九输出的独立 GUI/红石自动化机器，不把多件回收产物伪装成置物台合成：

- 空的已配置实体弹匣/弹链箱 → `magazine_blank` 中性壳体毛坯；重新使用时仍要用规格量规成型命名壳体，并另行制作供弹组件。带余弹的供弹器会被拒绝，必须先卸弹；
- 已配置的模具或淬硬口径量规 → 高碳钢板和黄铜粒；
- 由工业终端实际产出的枪（带 `IndustryAssemblyPlatform`） → 按实际 `GunData.weight` 回收 3–5 件可重新成型的结构毛坯、1–4 块高碳钢板，并返还 60%（向上取整）的原始外装材料；枪内置供弹/枪膛的散装弹会安全返还到输出槽。轻型手枪、普通长枪、重型精确/机枪不再得到同样的回收量。

回收站会拒绝仍装有**实体外部供弹器**或任意玩家安装配件的枪，避免静默销毁弹匣、光学件或枪口件。先正常退匣、拆配件，再送入回收站。普通旧枪、战利品枪或只靠相同 `GunId` 伪造的枪没有工业终端来源标记，不能成为回收工业零件的捷径。

`magazine_pouch` 保存最多四只完整实体弹匣（余弹、有序 `MagazineRounds`、口径和兼容族均保留）；手持袋在背包中右击实体弹匣收纳，右击空槽指定取出，或手持袋在空中右击取出一只。`magazine_loader` 是可复用的背包装弹工具：手持它右击实体载具会立即从首个兼容散装弹堆转入弹药。实体弹匣、桥夹、漏夹和快装器在真实背包/容器多槽界面中立即装入/取出；这些交互不把实体供弹器改回整数计数，也不绕过同口径 profile 校验。

`PartialNBTIngredient` / `StrictNBTIngredient` 仍定义了语义相等性，供内容包确实需要在 Basin 中重复同类 NBT 输入时使用，但内置最终装弹不再把这项内部行为当作能否制造的前提。

若某种弹药已交给 Create 工艺生产，可在：

```text
data/<namespace>/industry/ammo/<任意名称>.json
```

声明需要移除的老工作台配方：

```json
{
  "legacy_recipe": "yournamespace:ammo/your_caliber"
}
```

只有 `CREATE_FLY` 档会接管它；`LEGACY` 保持所有旧配方。接管以该配方解析出的实际 `AmmoId` 为单位：如果另一个枪包也用不同 recipe id 输出同一 AmmoId，那个重复旧捷径也会被移除，不能靠改文件路径重新获得铜锭/火药直出弹药。

### 可拆卸供弹器：规格量规、壳体与供弹组件

默认供弹器不再把完整枪当成模具，也不再从一个中性壳体直接产出可用成品。内置的作者清单是：

```text
tools/industry/magazine_carriers.json
```

它逐项校验 `gun_feed` 中全部 `detachable_magazine` / `belt` 的族、弹种、容量、兼容枪与制造质量档。它生成如下真实链条：

```text
Basin → 中性弹匣壳体毛坯
Basin → 中性供弹组件毛坯（弹簧/托弹板/弹链节料坯）
Basin → 中性供弹器规格量规毛坯

规格量规毛坯 + 兼容枪的平台生产工装模板（部署器保留）
  → 指定族/弹种/容量的可复用规格量规

或：规格量规毛坯 + 对应完全卸空的实体供弹器（样本消耗）
  → 同一规格量规

一个壳体毛坯 + 额外壳体毛坯逐站供入 + 规格量规（保留）
  → 命名供弹器壳体

一个供弹组件毛坯 + 额外组件毛坯逐站供入 + 同一规格量规（保留）
  → 命名托弹板/弹簧或弹链节/供弹盘组件

命名壳体（唯一移动工件） + 命名供弹组件（部署器供入并消耗）
  → 空的实体弹匣或实体弹链箱
```

- 多输入金属原料只在 Basin 中出现；每段顺序装配的传送带/置物台始终只有一个工作件；
- 容量更高、异形顶部供弹、弹链箱的额外材料通过**逐站增加中性毛坯**体现，不通过相同 Basin 输入改数字伪造不同结果；
- 规格量规的 `MagazineFamily`、`MagazineAmmoId`、`MagazineCapacity` 是壳体/供弹组件/成品之间的稳定身份，JEI/REI 可以连续追踪；
- 空成品供弹器可逆向生成量规，但装有余弹的供弹器不会匹配零余弹证据条件；
- 旧世界的 `tacz:magazine_blank` 仍可作为新壳体路线的中性输入；工业回收站也只返还该中性壳体，而不会把旧成品直接变种。

默认包的 34 种独立可拆卸供弹器身份都由该清单覆盖。`MagazineFamily` 是数据驱动的实体接口父标准，`Ammo`/canonical calibre 则只是弹药规格：前者控制壳体、卡笋、供弹唇和脚本已审计的互插关系，后者控制可装入哪些 FMJ/AP/HP/Slug 轮次。共享 STANAG/QBZ 等标准时，任一声明兼容枪的**生产模板**都可校准同一把规格量规；不会任意规定某一把成枪是其他枪弹匣的唯一来源。运行时以同一 `MagazineFamily + mechanism + resolved canonical calibre` 识别已声明的跨平台实体载具，因此 20 发 M16A1 可插入已经制造的 30 发 STANAG；审计确认兼容、但 native AmmoId 不同的第三方枪还必须用显式 `industry/ammo_profiles` 映射到同一 canonical calibre，才会共享该标准。成品外部载具保存统一 canonical `MagazineAmmoId`，逐发队列仍保存精确 AmmoId。SCAR-H 的 `.308` 载具则不会被误认为 M4A1 的 5.56 STANAG。同口径但未显式审计为同一接口的载具绝不自动互插。

第三方内容包无需、也不应要求玩家运行本项目的 Python 工具。它可在自己的命名空间提供 `industry/gun_feed/<gun>.json` 以启用实体供弹逻辑，并同时提供普通的 `recipe/create/...` JSON，遵循上面的“中性毛坯 → 真实量规 → 命名子总成 → 单工件总装”契约。TACZ 的工艺查看器会在资源重载时读取其 `recipe/create/` 工艺；没有这类高保真声明的枪仍保留运行时工业材料门槛/旧供弹行为，不会由客户端猜测不存在的弹匣几何。

### 压实冲突检查

TACZ 在 `CREATE_FLY` 资源重载时会扫描 `recipe/create/` 内的 `create:compacting` 工艺。若多条配方具有完全相同的无序物品/流体输入定义（包括相同 tag 或相同 partial-NBT 输入），或某条配方的最小输入能同时满足另一条**相同输入种类数**的配方（例如 3 钢 + 生铁与 2 钢 + 生铁），会记录错误。Create 的 Basin 优先级只看输入**种类数**，不看同种材料的总数；这类配方会依赖加载顺序，至少有一条无法可靠制造。热量也不能充当可靠选择器：在更高温度下低热量要求仍会匹配。

这是对“REI 能显示多条配方”与“机器真的能执行多条配方”的显式区分。要表达最终身份，使用中性毛坯 + 部署器中的非消耗量规/模具，而不是复制一组 Basin 输入、仅改变材料数量或更换结果。

---

## 4. 物理供弹声明

可拆卸实体弹匣仍使用：

```text
data/<namespace>/industry/gun_feed/<枪 ID 路径>.json
```

```json
{
  "mechanism": "detachable_magazine",
  "magazine_family": "ak_762x39",
  "magazine_capacity": 30,
  "ammo": "yournamespace:762x39",
  "display_name": "item.yourmod.magazine.ak_762"
}
```

没有此文件的枪保持旧供弹行为。供弹机制可为：

```text
detachable_magazine  外部实体弹匣
belt                 外部实体弹链箱（复用实体供弹器 ItemStack）
internal_box         内置弹仓
tube                 管式弹仓
revolver             转轮
single_shot          单发后膛
stripper_clip        桥夹，向固定内仓增量装填
speedloader          快装器，向内部转轮增量装填
en_bloc_clip         漏夹（独立装入枪 NBT，打空自动弹出）
```

`internal_box`、`tube`、`revolver`、`single_shot` 的余弹保存为枪 NBT 中受服务端控制的内置供弹状态，而不是伪造可退卸弹匣。`reload_batch` 是一次原生完整动作可从**单一来源**转入的最大批量；`script_loop` 则不按批量结算，而是在枪包每一个真实 Lua feed 调用处逐发扣除/转入。RPG/M320 等容量为 1 的枪仍自然只填一发。`belt` 则使用带容量和余弹的外部实体弹链箱。

桥夹、快装器与漏夹都使用物理 `tacz:magazine` ItemStack 保存族、口径、容量和余弹，但生命周期不同：桥夹/快装器留在背包作为可复用装填工具；漏夹在 reload feed 点写入枪 NBT 的 `InstalledEnBlocClip`，最后一发实际离枪后自动弹出。漏夹示例：

```json
{
  "mechanism": "en_bloc_clip",
  "magazine_family": "example_m1_3006_enbloc",
  "magazine_capacity": 8,
  "feed_device_capacity": 8,
  "feed_device_reusable": true,
  "reload_batch": 8,
  "loose_reload_mode": "none",
  "ammo": "example:30_06",
  "display_name": "item.example.m1_enbloc"
}
```

桥夹/快装器额外声明：

```json
{
  "mechanism": "stripper_clip",
  "magazine_family": "example_stripper",
  "magazine_capacity": 10,
  "feed_device_capacity": 5,
  "feed_device_reusable": true,
  "reload_batch": 5,
  "loose_reload_mode": "none",
  "ammo": "yournamespace:762x39",
  "display_name": "item.yourmod.example_stripper_clip"
}
```

其中 `magazine_capacity` 是枪内固定仓容量，`feed_device_capacity` 才是一只桥夹/快装器自身可装的发数。桥夹不比较“自身余弹是否比枪内余弹多”；服务端只计算 `min(器件余弹, 内仓缺弹, reload_batch)` 的实际转入量，部分使用后的余弹继续保存在**同一件**桥夹中，可再次使用、取出或补装。桥夹/快装器都是可复用的物理装填工具，转空也绝不被换弹事务删除；`feed_device_reusable` 保留仅为旧数据兼容。

`loose_reload_mode` 不能从旧 `reload.type` 自动推断：

```text
none           不开放散装弹路径，避免伪造原包没有的动画
single_action  一次原生完整动作转入 loose_reload_batch（未写时为 reload_batch）
script_loop    一次 R 启动原枪包的真实逐发循环；每个脚本 feed 点独立扣除/转入
```

桥夹/快装器未声明时默认 `none`，而不是“每按一次 R 加一发”。只有已核验存在逐发 Lua 循环的枪才可选择 `script_loop`。

### 条件化 `reload_routes`

莫辛 / Gewehr 98 一类枪的原包脚本可能同时有“完整桥夹批量动画”和“散装逐发循环动画”。这时不要仅写 `loose_reload_mode`，而要显式声明按顺序尝试的 `reload_routes`：

```json
{
  "reload_routes": [
    {
      "id": "stripper_clip_batch",
      "source": "loading_device",
      "script_driven": true,
      "min_missing_rounds": 5,
      "max_missing_rounds": 5,
      "require_tactical": false,
      "min_source_rounds": 5,
      "max_transfer_rounds": 5,
      "require_attachment_empty": "scope"
    },
    {
      "id": "loose_round_loop",
      "source": "loose_ammo",
      "script_driven": true,
      "min_missing_rounds": 1,
      "animation_force_attachment_present": "scope"
    }
  ]
}
```

- `source` 是 `loading_device` 或 `loose_ammo`；前者只有背包里存在兼容、足量的**具体物理桥夹/快装器**时才匹配；
- `min_missing_rounds` / `max_missing_rounds`、`min_source_rounds`、`require_tactical`、`require_attachment_empty` / `require_attachment_present` 是对原包真实分支条件的逐项记录；
- `script_driven: true` 表示不在 FINISHING 阶段补发整批，而是在原 Lua 每一个 `consumeAmmoFromPlayer` / `putAmmoInMagazine` / `setAmmoInBarrel` feed 调用处实际转移；
- `extra_source_rounds` 用于原脚本把一发先上膛、其余压入内仓的批量动画；
- `animation_force_attachment_present` 只可在已审计的旧状态机把“配件是否存在”当作**动画分支选择器**时使用。它不会安装或渲染假配件，也不会改变枪械数值；服务器和客户端同时使用同一条 route，客户端仅预测、服务器最终验证；
- `animation_force_mag_extent_level` 同理只覆盖旧脚本/状态机的扩容等级选择器，可用于已有 `reload_loader` 快装器动画；
- `force_animation_rounds` 允许已审计的完整快装器路线明确要求一次完整装入量；`script_remove_mode: discard` 只用于原包明确声明“先丢弃转轮旧弹”的脚本，不能泛用。

因此“背包有完整桥夹/快装器”是选择对应动画的条件之一；没有完整器件但有散装弹时会进入真实逐发动画；半满器件若没有对应的部分装填动画分支，不会被静默拿来伪造完整动画。完整审计规则见 `docs/FEED_DEVICE_AND_CLIP_DESIGN.md`。
