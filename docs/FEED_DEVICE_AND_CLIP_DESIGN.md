# 可拆卸弹匣、桥夹、漏夹与快装器：供弹器件设计

`reload.type = "magazine"` 是 TACZ 历史动画/API 分类，不是现实供弹结构，也不能说明一个枪包是否有“逐发循环装填”的动画。工业档不能拿它直接决定“是否装实体弹匣”或擅自伪造逐发装填。

## 器件生命周期

| 机制 | 枪内状态 | 背包器件状态 | 换弹事务 | 用尽行为 |
|---|---|---|---|---|
| `detachable_magazine` | `InstalledMagazine` | 半满弹匣保留余弹 | 替换已安装供弹器 | 可退匣/可回收 |
| `belt` | `InstalledMagazine` | 弹链箱保留余弹 | 替换已安装供弹器 | 可退回/可回收 |
| `stripper_clip` | `InternalFeedAmmoCount` + 有序 `InternalFeedRounds` | 桥夹保存自身余弹/顺序 | 向固定内仓增量转入 | **空夹仍保留，可再次压弹** |
| `speedloader` | `InternalFeedAmmoCount` + 有序 `InternalFeedRounds` | 快装器保存自身余弹/顺序 | 向内部转轮增量转入 | **空快装器仍保留，可再次压弹** |
| `en_bloc_clip` | 独立 `InstalledEnBlocClip` 状态及有序 round list | 漏夹随枪保存余弹/顺序 | 装入枪内后逐发扣除 | 最后一发真正离开枪后自动弹出空夹 |
| `tube` / `revolver` / `internal_box` / `single_shot` | `InternalFeedAmmoCount` + 有序 `InternalFeedRounds` | 无强制外部器件 | 从散装弹或明确器件装填 | 枪内状态继续保存 |

桥夹、快装器使用的 registry item 当前复用 `tacz:magazine` 的成熟“容量、余弹、库存交互”数据容器，但强制带：

```text
FeedDeviceKind = stripper_clip | speedloader | en_bloc_clip
```

这只是复用可靠的 ItemStack 存储与装卸 UI；它们不会进入 `InstalledMagazine`，也不会触发普通弹匣的替换逻辑。

## 已有材质的诚实复用规则

新接入的枪包不需要等待新 PNG 才能获得真实服务器事务，但也不能把一张错误的 AK/STANAG 图冒充成任何新枪的精确弹匣。客户端映射现在按 `FeedDeviceKind` 优先复用已有材料：

| 器件 | 复用材质 | 说明 |
|---|---|---|
| 普通可拆卸盒式弹匣 | `tacz:item/magazine` | 中性既有弹匣材质；仅说明“可拆卸弹匣”，不伪称精确外形 |
| 弹链箱 / 带盒 | `tacz_extra:item/mag_m249_box` | 既有弹链箱材料；M249/Evolys 的精确 family 图仍优先 |
| 暴露式布带 | `tacz_extra:item/mag_m134_belt` | MG08/15、MG42、MG14/17、WW M1919A6/MG34/MG42 与 Suffuse PKP 复用现有 belt 材料，标记为 family 近似而非精确网格 |
| RPD 带鼓容器 | `tacz_extra:item/mag_rpk_drum` | RPD/RPD-MS 的带鼓式容器复用鼓式材料，非精确 RPD 网格 |
| 桥夹 | `tacz_extra:item/base_m_loader` | 已有桥夹/装填器材料族，保留 `stripper_clip` 语义 |
| 漏夹 | `tacz_extra:item/base_m_loader` | 与桥夹复用金属夹条材料，但仍由 `en_bloc_clip` 状态和自动弹出区分 |
| 快装器 | `tacz_extra:item/mag_speedloader22` | 已有圆柱快装器材料族 |
| 已审计 FAL 系列 | 既有 FAL 弹匣图 | `rainforest_fal_308` 复用 FAL 家族材质，标记为 family 而非每容量精确模型 |
| SMG08/18 鼓式弹匣 | `tacz_extra:item/mag_rpk_drum` | 复用既有鼓式材料，明确是 family 级近似而不是 SMG08 精确复刻 |

以下类型仍**不适合**硬套已有方盒弹匣图，当前保留中性材质，直到有合适的可授权资源：

```text
Lewis / DP28 / DPM 顶部盘式弹匣
Villar Perosa 双上置盒式弹匣
PPSh / KP/-31 等真正的蜗牛盘、异形双联、专有顶部供弹器
```

它们的机制、余弹、制造和图标选择仍是实际生效的；“中性材质”只是诚实地不把错误几何说成精确美术。资源包作者可用 `industry_icons` 的 `magazine_family` 或 `feed_device_kind` selector 以更高优先级提供精确覆盖。

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

这里的 **2 发不会消失，也不会自动变成另一只桥夹**：它们保存在原桥夹的 `MagazineRounds` / `MagazineAmmoCount` 镜像中，可在下一次兼容装填时继续使用。桥夹和快装器都可在真实背包/容器多槽界面中立即装入或取出；同口径的 profile 可按顶部顺序混装，输出受阻时不会吞掉余弹。即使余弹变为 `0 / capacity`，服务器也**绝不**在换弹事务中删除该 ItemStack，空夹仍可重新压弹。

桥夹/快装器的正常批量动画在其真实 feed 点由服务端扣除。预留期间会绑定具体物理器件实例；若玩家中途移动、替换或改写该槽中的器件，事务失败关闭，绝不临时选择另一只桥夹或改扣其他来源。

## 漏夹：安装状态与自动弹出

`en_bloc_clip` 不走桥夹的“背包内剩余几发 → 向枪内加几发”逻辑。它会把完整的物理 ItemStack 写入枪 NBT：

```text
InstalledEnBlocClip = configured tacz:magazine ItemStack
```

枪的当前备弹直接读取这只已安装漏夹；每次真正消耗一发时同步减少漏夹内的 `MagazineAmmoCount`。当且仅当：

```text
漏夹余弹 = 0
且枪膛内也没有最后一发
```

服务器才把同一只空漏夹退出枪 NBT，并从枪手右前方生成带短暂拾取延迟的真实 `ItemEntity`；它会可见地弹出、落地，并由原版玩家/漏斗拾取。它不会在装入时被消耗，也不会因 NBT 整数归零而静默删除。潜行 + R 是明确的人工退夹路径：这条人工路径仍直接安全返还到背包，可取回尚有余弹的漏夹。

已审计样本包括 GunpowderRevolution 的 `hamster:m1garand`（8 发 `hamster:long_ammo`，原包状态机带有 `last_shoot`、clip bone 与空仓 ping）和 Enlisted 的 `ww:m1g`（8 发 `tacz:30_06`，带 M1 ping/装夹音效）。两者的漏夹族与弹药必须分别匹配；它们只接受已实际装入弹药的物理漏夹，无漏夹时不借散装弹伪造 M1 装夹动画。

## 快装器：完整转轮替换语义

快装器是可复用物理器件，但不等于“每次从快装器拿一发”。已审计样本为 GunpowderRevolution `hamster:webley` 与 `hamster:sw_mk2`：原包 `hamster:speedloader` 附件以 `extended_mag_level = 1` 选择 `reload_loader` / `double_reload_loader` 动画，服务端脚本先清空转轮再一次装入 6 发。

工业兼容层把该选择器绑定到一只完整 6 发 Webley 快装器：

```text
完整 6 发快装器 → 原 reload_loader 动画 → 原转轮旧弹按上游语义清除 → 一次转入 6 发
无完整快装器但有散装弹 → 原逐发 reload_loop
半满快装器 → 不伪造完整 reload_loader；保留器件，走散装逐发或拒绝
```

这样保留了该枪包明确声明的“快速装填会丢弃未击发旧弹”行为；不会把快装器误当成可安装弹匣，也不会在转空后删除快装器。

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

## 条件化桥夹 / 逐发双路线

莫辛、Gewehr 98 一类不能只写成“桥夹枪”或“逐发枪”。它们的原包服务端逻辑和客户端状态机本来就会按条件在两条真实动画之间切换；**背包里是否有一只足量、兼容的实体桥夹也必须成为该条件之一。**

`reload_routes` 按顺序声明经过审计的分支。服务器先计算并锁定分支与具体器件槽位；客户端用同一份已同步数据做短暂预测，随后以服务器同步的 route 为准。客户端的预测永远不能制造弹药，服务器仍在每个原 Lua feed 点验证并扣除实际来源。

GunpowderRevolution 的 Mosin / Gewehr 98 家族使用的实际模式为：

```text
完整兼容 5 发桥夹 + 空仓 + 无瞄具 + 缺满 5 发
  → 原包桥夹批量动画；服务器从这一只桥夹转入 5 发

没有满足上述条件的桥夹，但有散装弹
  → 原包逐发循环动画；一次 R 连续逐发从散装弹转入
```

示例数据：

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

`animation_force_attachment_present` **不安装、不给予、也不渲染假配件**。它只在这一段已经审计的原 Lua reload 脚本与状态机读取配件条件时，令其走原本的“逐发”分支；真实的瞄具、枪械属性和 NBT 完全不变。这个兼容桥只可用于同时核对过服务端 Lua、客户端状态机、动画名称和实际 feed 调用的枪，不能成为通用猜测开关。

当前已按该模式接入：默认 `tacz:kar98`，以及 GunpowderRevolution 的 `hamster:gew98`、`m1903`、`mosin91`、`mosin9130`、`type99`。SMLE、Berthier、Krag、转轮、漏夹等相似但条件不同的枪必须各自完成四层审计后才会加入，不能套用莫辛规则。

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
  "feed_device_reusable": true,
  "reload_batch": 5,
  "loose_reload_mode": "none",
  "ammo": "example:762x39",
  "display_name": "item.example.type56_stripper_clip"
}
```

- `magazine_capacity`：枪内固定仓容量；
- `feed_device_capacity`：一只桥夹/快装器可装几发；
- `reload_batch`：一次完整动画最多从**这一只**器件转入几发；
- `feed_device_reusable`：保留的兼容字段；桥夹/快装器始终可复用，转空也会保留；
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

测绘平台的 Gunsmith Table「实体供弹器件」页会额外生成该桥夹的制造委托：

```text
弹匣壳体毛坯 + 测绘生产工装（保留）+ 测绘夹具（保留）
→ Type 56 Stripper Clip（空）
```

它可以像其他物理供弹器一样在背包/容器界面中立即装入/取出同口径散装弹，但在换弹时只向枪内固定仓转移弹药。

## 延后的选择轮盘 UI

长按 R 的圆盘当前**没有实现，也没有长按输入、轮盘 UI 或选定槽位网络预留代码**；`R` 在按下时立即执行正常换弹。已经确认但暂缓的“选择实体载具并立刻换装”方案、服务端双重校验、独立 AmmoId、机制排除边界和恢复开发前的验收清单，见 [`RELOAD_WHEEL_DEFERRED_DESIGN.md`](RELOAD_WHEEL_DEFERRED_DESIGN.md)。

漏夹仍拥有独立已安装器件状态和打空自动弹出钩子；桥夹、漏夹、快装器、内部仓和 legacy 路线继续各走已经审计的服务器事务。未来的选择轮盘不能越过这些机制或伪造不存在的动画 feed 点。
