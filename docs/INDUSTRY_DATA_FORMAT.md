# 工业制造数据格式

工业化不应靠 Java 里堆材料数量。`CREATE_FLY` 档的扩展点分为三层：**真实 Create 工艺、终端顺序装配声明、供弹声明**。

所有 JSON 都可由内容包在自己的命名空间下提供；默认枪包不需要被修改。

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

TACZ 会读取该目录中的支持工艺，向客户端同步一份工艺投影，并在 REI 中注册研磨、粉碎、加热搅拌、工作盆压实、单件冲压、部署器成型和顺序枪械装配类别。

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
      "type": "create:deploying",
      "target": "$ingredient",
      "ingredient": { "fabric:type": "forge:partial_nbt", "items": ["yourmod:component"], "nbt": {"IndustryPartKind": "bolt"} },
      "results": ["$result"]
    }
  ]
}
```

语义必须严格是：

1. 首个机匣/枪身进入传送带或置物台，成为**唯一**流动的过渡工件；
2. 第一台部署器持蓝图，`keep_held_item: true`，蓝图不消耗；
3. 后续每台部署器分别持枪机、枪管、击发组、复进组件、木料等**一种**供料；各站消耗自己的物品；
4. 工件按顺序经过工位，最终才变为带完整 `GunId`、首个有效射击模式、空膛/空备弹状态的 TACZ 枪；
5. 置物台上从头到尾没有、也不需要同时放入多个物品。

`CREATE_FLY` 档只有在下列条件都满足时才移除旧工作台成枪配方：

- `terminal_process` 指向的配方资源存在；
- 类型为 `create:sequenced_assembly`；
- 每个序列步骤都是单工件部署、单工件冲压或单工件灌装，且没有 `ingredients` 多输入数组。

声明丢失、ID 写错或工艺形状不合法时，TACZ 会记录警告并**保留旧工作台配方**，不会把成枪变成不可获得物品。

当前内置 AK-47、M4A1、Glock 17 已使用此路径。它们的组件、模板、成枪结果都通过 `forge:partial_nbt` / `minecraft:custom_data` 确认身份：AK 机匣不能代替 AR 机匣，Glock 枪身不能代替 AR 机匣；模板保留，组件与辅料按工位消耗。

枪械组件的前段遵循“结构毛坯 → **中性组件模具体** → 部署器持对应结构毛坯选定几何 → 部署器持平台模板校准平台模具 → 部署器持模具成型组件”。这里的中性组件模具体只有一条工作盆压实来源；机匣/枪机/枪管/击发组/复进组件毛坯作为部署器中不消耗的实体量规，明确选择 `DieTargetKind`。这样不会再出现“五条相同 Basin 输入、却期望产出五种模具体”的不可合成配方。

同样地，每个 `create:deploying` 步骤都只有一个置物台目标与一个部署器手持物。

---

## 3. 弹药身份与旧工作台弹药替换

弹药同样不靠材料数量区分：单输入动力冲压机只负责产出中性的 `tacz:cartridge_case_blank` 与 `tacz:projectile_blank`。之后由 Create 部署器持有一枚 NBT 标识的可复用 `tacz:press_die`，通过带 `keep_held_item: true` 的 `create:deploying` 工艺真正压制出指定身份。

压实阶段也不能让四条相同材料表直接各自产出四种口径模具。现在先压实**中性弹壳模具体**或**中性弹头模具体**；再由部署器持有一把对应口径的完整枪械作为不消耗的膛室/口径量规，校准成 9 mm、5.56×45、7.62×39 或 12G 的最终模具。枪在部署器手持栏，模具体是置物台/传送带上的唯一目标，因此既是实际的物理选择，也不违反置物台单工件约束。

成品 `tacz:cartridge_case` 保存 `CartridgeCaliber`，`tacz:projectile_core` 同时保存 `CartridgeCaliber` 与 `ProjectileType`。5.56 弹壳不能进入 9 mm 装弹工艺；以后增加 HP、AP、slug 等弹头，只需新增数据配方与模具，不需要再在 Java 里加口径分支。

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
  "count": 1
}
```

这不是“配方显示出来就算能做”的工作盆推断：四个 GUI 槽位分别验证，错口径弹壳、错弹头类型、错误底火或推进药会被服务端拒绝。定义会同步到客户端供 JEI/REI 显示弹药装配机配方；整枪和实体弹匣仍走各自既有的工业路线。

装配机也可接入物流：顶部与四个侧面均可输入物品，机器会按数据定义把弹壳、弹头、底火、推进药路由到各自唯一的槽位；**底面只允许取出成品**。给予红石信号后，机器每 40 tick 自动完成一次有效装配；无红石时仍可通过 GUI 手动点击“装配”。输入改变、配方不匹配或成品槽无法容纳时进度会重置且绝不扣料。

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

只有 `CREATE_FLY` 档会移除它；`LEGACY` 保持所有旧配方。

### 弹匣壳体与枪械量规校准

同一组钢板/黄铜片不能可靠地让 Basin 在 Glock、AK、G36、FAL、MP5 等多个弹匣结果间作选择；Create 会在同优先级匹配中只选中其中一个。因此内置流程是：

```text
高碳钢板 + 黄铜片 + 黄铜粒 --工作盆压实--> tacz:magazine_blank
对应成枪（部署器持有，不消耗） + magazine_blank --部署器--> 精确平台/容量实体弹匣
```

每个 `data/<namespace>/recipe/create/magazine/<gun-id>.json` 都是一个部署器校准配方：

- `target` 为中性的 `tacz:magazine_blank`；
- `ingredient` 是带精确 `GunId` 的 `tacz:modern_kinetic_gun`，并设 `keep_held_item: true`；
- `results` 才写入 `MagazineFamily`、`MagazineAmmoId`、`MagazineCapacity` 和显示名。

成枪在这里是可复用的实际弹匣量规，不是材料数量暗号；错误枪不能把毛坯校准成其他枪的平台弹匣。内容包可以按同一模式增加新的枪械量规配方。

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

没有此文件的枪保持旧供弹行为；管式、转轮、弹链和单发枪不应伪造为 detachable magazine。
