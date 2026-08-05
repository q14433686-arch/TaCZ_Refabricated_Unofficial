# 工业制造数据格式

工业化不应靠 Java 里堆材料数量。`CREATE_FLY` 档的扩展点分为三层：**真实 Create 配方、终端装配声明、供弹声明**。

所有 JSON 都可由内容包在自己的命名空间下提供；默认包不需要被修改。

---

## 1. Create Fly 工艺与 REI

把真实加工配方放在：

```text
data/<namespace>/recipe/create/<任意路径>.json
```

例如：

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

TACZ 会读取该目录中的 `create:*` 配方，向客户端同步一份工艺投影，并在 REI 中注册研磨、粉碎、加热搅拌、炽热搅拌、冲压、压实类别。

这条同步通道存在的原因是 Create Fly 当前 26.2 构建在其 `build.gradle` 中排除了 REI source set；JEI 能显示 Create 工艺而 REI 没有原生类别。TACZ 的桥接层不调用 Create 内部 Java API，只从实际配方 JSON 构建 REI 的输入/输出树。

- 直接物品 ID、`#item_tag` 与 TACZ 注册的 `forge:partial_nbt` 都可显示；
- 其他复杂 Create 自定义 ingredient 仍建议由内容包同时提供一个直接物品显示入口；
- `fabric:load_conditions` 必须保留，避免没有 Create Fly 时让原版 RecipeManager 解析未知 `create:*` 类型。

---

## 2. 终端枪械装配

把一把枪的工业化终端要求放在：

```text
data/<namespace>/industry/assembly/<枪械工作台配方路径>.json
```

配方路径必须与原 TACZ 工作台配方 ID 一致。例如默认 AK 的原配方 ID 是 `tacz:gun/ak47`，文件应为：

```text
data/tacz/industry/assembly/gun/ak47.json
```

示例：

```json
{
  "platform": "ak",
  "blueprint_display_name": "item.yourmod.gun_blueprint.ak",
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

在 `CREATE_FLY` 档，组件的实际生产遵循“结构毛坯 → 模具毛坯 → 部署器持模板校准模具 → 部署器持模具成型组件”的过程；两个 `create:deploying` 步骤都用 `keep_held_item: true` 保留模板/模具。

TACZ 会把原工作台配方材料替换为：

1. 带同一 `platform` 的 `tacz:gun_blueprint`，`consume: false`；
2. 带同一 `platform + kind` 的 `tacz:gun_component`，会被消耗；
3. JSON 声明的额外材料。

这依赖现有的 `forge:partial_nbt` Fabric 兼容实现，因此不是“只看数量”的配方：AK 机匣、AR 机匣和 Glock 枪身即使使用同一个注册物品 ID，也会按 custom data 严格区分。

弹药同样如此：单输入的动力冲压机只负责产出中性的 `tacz:cartridge_case_blank` 与 `tacz:projectile_blank`。之后由 Create 部署器持有一枚 NBT 标识的可复用 `tacz:press_die`，通过带 `keep_held_item: true` 的 `create:deploying` 工艺真正压制出指定身份。

枪械组件也走同一原则：结构毛坯和模具毛坯先由压实工序生产；部署器持装配模板校准出带 `DieTargetKind` 的平台模具；再由部署器持该模具把对应毛坯成型为最终机匣、枪机、枪管、击发组或复进组件。

成品 `tacz:cartridge_case` 保存 `CartridgeCaliber`，`tacz:projectile_core` 同时保存 `CartridgeCaliber` 与 `ProjectileType`。5.56 弹壳不能进入 9 mm 装弹工艺；以后增加 HP、AP、slug 等弹头，只需新增数据配方与模具，不需要再在 Java 里加口径分支。

---

## 3. 替换旧工作台弹药配方

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
