# 可拆卸弹匣、桥夹、漏夹与快装器：供弹器件设计

`reload.type = "magazine"` 是 TACZ 历史动画/API 分类，不是现实供弹结构，也不能说明一个枪包是否有“逐发循环装填”的动画。工业档不能拿它直接决定“是否装实体弹匣”或擅自伪造逐发装填。

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

## 桥夹选择、余弹与扣除规则

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
结果：枪内 10 / 10，桥夹仍是同一件物品，余弹 2 / 5
```

这里的 **2 发不会消失，也不会自动变成另一只桥夹**：它们保存在原桥夹的 `MagazineAmmoCount` 中，可在下一次兼容装填时继续使用；玩家也可像实体弹匣一样把桥夹与同口径散装弹右键叠放来补弹，或对空槽右键取出余弹。只有器件真的被转空且 `feed_device_reusable = false` 时，空器件才会被消耗。`true` 则保留空夹/空快装器供再次压弹。

桥夹/快装器的正常批量动画在其真实 feed 点由服务端扣除。预留期间会绑定具体物理器件实例；若玩家中途移动、替换或改写该槽中的器件，事务失败关闭，绝不临时选择另一只桥夹或改扣其他来源。

## 逐发散装弹：必须有真实循环动画

“逐发装填”指的是：**按一次 R 后，枪包自身的 `start_reload` / `tick_reload` 循环动画连续播放，每一个已存在的脚本 feed 点各转移一发（或该动画明确的一组）真实弹药。** 中断后，已经播放并转移的发数保留；尚未到达 feed 点的散装弹仍留在背包。

这不是“按一次 R 只加一发，再要求玩家重复按 R”。为了避免混淆，`gun_feed` 使用显式字段：

```json
"loose_reload_mode": "script_loop"
```

只有兼容作者已核验该枪包存在这种服务器侧循环脚本时才能填写它。服务端会在每次 `consumeAmmoFromPlayer → putAmmoInMagazine` 或 `setAmmoInBarrel` 的实际动画 feed 调用处扣除/转入一发；枪满、散装弹耗尽、玩家打断、或选中的物理来源失效时停止。不能仅从 `reload.type` 或模型文件猜测。

可用值：

| 值 | 含义 |
|---|---|
| `none` | 不开放散装弹路径；在没有对应动画前保持诚实的器件装填。 |
| `single_action` | 原枪包的一次完整装填动画只转入一个数据声明的批次。 |
| `script_loop` | 一次 R 启动原枪包真实逐发循环；每次循环 feed 都是独立服务器事务。 |
| `auto` / 未声明 | 非器件内置供弹保持原有完整动作；桥夹/快装器默认 `none`，不凭空创造手装动画。 |

默认包的 M870、M1014、SPAS-12 已被标记为 `script_loop`，因为它们确实带有反复 `tick_reload` feed 调用。它们按一次 R 即可连续逐发装填，且中断时只保留已经实际压入/上膛的弹。

### Cold War Type 56 的当前边界

上传的 Cold War `rainforest:56` 数据实际使用 `tacz:sks_tactical` 的普通批量换弹动画和 `reload.type = "magazine"`，没有它自己的逐发 `tick_reload` 循环。因此它的兼容声明是：

```json
"mechanism": "stripper_clip",
"loose_reload_mode": "none"
```

这意味着它目前可以用真正的 5 发桥夹进行一次真实批量转入；**不能把“无桥夹”错误实现成按一次 R 只加一发**，也不能谎称它有原包没有的逐发动画。若要给它开放散装逐发压弹，下一步必须提供独立、可审计的 GPL 兼容动画/逻辑层并明确标为 Type 56 手装模式，而不是修改或伪造第三方资源。

## `gun_feed` 桥夹声明

```json
{
  "mechanism": "stripper_clip",
  "magazine_family": "example_stripper",
  "magazine_capacity": 10,
  "feed_device_capacity": 5,
  "feed_device_reusable": false,
  "reload_batch": 5,
  "loose_reload_mode": "none",
  "ammo": "example:762x39",
  "display_name": "item.example.type56_stripper_clip"
}
```

- `magazine_capacity`：枪内固定仓容量；
- `feed_device_capacity`：一只桥夹/快装器可装几发；
- `reload_batch`：一次完整动画最多从**这一只**器件转入几发；
- `feed_device_reusable`：空器件保留以重新压弹，或在转空时消耗；
- `loose_reload_mode`：散装弹是否有经过核验的真实动画路径；
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
弹匣壳体毛坯 + 测绘生产工装（保留）+ 测绘夹具（保留）
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
