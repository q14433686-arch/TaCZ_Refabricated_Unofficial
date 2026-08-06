# 可拆卸弹匣、桥夹、漏夹与快装器：供弹器件设计

`reload.type = "magazine"` 是 TACZ 历史动画/API 分类，不是现实供弹结构。工业档不能拿它直接决定“是否装实体弹匣”。

## 器件生命周期

| 机制 | 枪内状态 | 背包器件状态 | 换弹事务 | 用尽行为 |
|---|---|---|---|---|
| `detachable_magazine` | `InstalledMagazine` | 半满弹匣保留余弹 | 替换已安装供弹器 | 可退匣/可回收 |
| `belt` | `InstalledMagazine` | 弹链箱保留余弹 | 替换已安装供弹器 | 可退回/可回收 |
| `stripper_clip` | `InternalFeedAmmoCount` | 桥夹保存自身余弹 | 向固定内仓增量转入 | 空夹按数据保留或消耗 |
| `speedloader` | `InternalFeedAmmoCount` | 快装器保存自身余弹 | 向内部转轮增量转入 | 空快装器按数据保留或消耗 |
| `en_bloc_clip` | 未来独立“已安装漏夹”状态 | 漏夹保存自身余弹 | 装入枪内后逐发扣除 | 打空自动弹出 |
| `tube` / `revolver` / `internal_box` / `single_shot` | `InternalFeedAmmoCount` | 无强制外部器件 | 从散装弹或明确器件装填 | 枪内状态继续保存 |

桥夹、快装器使用的 registry item 当前复用 `tacz:magazine` 的成熟“容量、余弹、库存交互”数据容器，但强制带：

```text
FeedDeviceKind = stripper_clip | speedloader
```

这只是复用可靠的 ItemStack 存储与装卸 UI；它们不会进入 `InstalledMagazine`，也不会触发普通弹匣的替换逻辑。

## 桥夹选择与扣除规则

普通弹匣当前的自动策略是“候选余弹必须大于当前已装载供弹器余弹”才替换。桥夹绝不能沿用它。

桥夹的有效装填量是：

```text
missing = internal_capacity - current_internal_rounds
transfer = min(clip_rounds, missing, reload_batch)
```

只要 `transfer > 0`，桥夹就是有效候选；按可实际转入发数从大到小选取，同分时保持背包/热键栏顺序。

例：

```text
固定内仓：7 / 10
桥夹：5 / 5
reload_batch：5

transfer = min(5, 3, 5) = 3
结果：枪内 10 / 10，桥夹 2 / 5
```

没有桥夹但有散装弹时，桥夹枪仍可装填：每次完整 R 动画只转入 `loose_reload_batch` 发，未声明时桥夹/快装器默认 1 发。于是桥夹是快速批量装填，散装弹是慢速逐发压入；不需要伪造原枪包没有的桥夹动画。

桥夹在换弹动画的 `FEEDING → FINISHING` 点由服务端扣除；若玩家中途移动/替换该背包槽中的器件，事务失败关闭，绝不临时选择另一只桥夹或改扣其他来源。

## `gun_feed` 桥夹声明

```json
{
  "mechanism": "stripper_clip",
  "magazine_family": "example_stripper",
  "magazine_capacity": 10,
  "feed_device_capacity": 5,
  "feed_device_reusable": false,
  "reload_batch": 5,
  "ammo": "example:762x39",
  "display_name": "item.example.type56_stripper_clip"
}
```

- `magazine_capacity`：枪内固定仓容量；
- `feed_device_capacity`：一只桥夹/快装器可装几发；
- `reload_batch`：一次完整动画最多从**这一只**器件转入几发；
- `feed_device_reusable`：空器件保留以重新压弹，或在转空时消耗；
- `magazine_family`：这里是器件兼容族，不表示它会成为可装入枪内的弹匣。

## 当前首个兼容样本

Cold War `rainforest:56` 已声明为：

```text
10 发固定内仓
5 发桥夹
tacz:762x39
FeedDeviceKind = stripper_clip
```

测绘平台的 Gunsmith Table Misc 页会额外生成该桥夹的制造委托：

```text
弹匣壳体毛坯 + 测绘生产工装（保留）+ 测绘夹具（保留)
→ Type 56 Stripper Clip（空）
```

它可以像其他物理供弹器一样装入/取出散装弹，但在换弹时只向枪内固定仓转移弹药。

## 选择圆盘 UI

长按 R 的圆盘是后续客户端层，不应先于服务端事务实现。它将发送一个明确的背包槽位选择给服务器：

```text
selectedSlot + expected ItemStack components
```

服务器仍会检查兼容性、预留槽位、在动画 feed 点执行。这样圆盘只改变“选哪只弹匣/桥夹”，不会绕过弹药扣除或让客户端伪造余弹。

漏夹 `en_bloc_clip` 需要单独的枪内已安装器件状态和“打空自动弹出”钩子，不能借桥夹的增量装填流程伪造；它在桥夹/快装器服务端回归完成后再实现。
