# 第三方枪包实体弹匣适配

## 结论

第三方附属枪包的实体弹匣不能由：

```text
reload.type = magazine
枪名
模型看起来像弹匣井
GunData.ammo_amount
```

任一单独事实自动推导。

`reload.type = magazine` 在 TACZ 历史数据中同时被管式霰弹枪、转轮、固定内仓枪使用；模型和名称也不是服务器可验证的供弹语义。错误把它们改成 `InstalledMagazine` 会破坏真实原包 reload 脚本、桥夹、漏夹或内部仓状态。

正确路径是：**附属包作者或兼容数据包显式声明 `industry/gun_feed`，运行时再用实际加载的 GunIndex/GunData 验证。** 没有通过验证的声明保持 legacy，不会产生实体弹匣。

仓库 `main` 中四个参考枪包的离线审计、容量统计和安全边界见 [`REFERENCE_GUNPACK_FEED_AUDIT.md`](REFERENCE_GUNPACK_FEED_AUDIT.md)。

## 放置位置

资源 ID 即目标 GunId。因此目标为：

```text
my_addon:f2000
```

的枪需要：

```text
data/my_addon/industry/gun_feed/f2000.json
```

兼容数据包可以提供另一个枪包的 `data/my_addon/...` 路径；不必、也不应修改附属枪包 ZIP。

## 最小可拆卸弹匣声明

```json
{
  "mechanism": "detachable_magazine",
  "magazine_family": "my_addon_f2000_556",
  "magazine_capacity": 30,
  "ammo": "my_addon:556x45",
  "display_name": "item.my_addon.magazine.f2000"
}
```

服务器接受该声明前会核对：

1. `my_addon:f2000` 的 GunIndex 与 GunData 确实已加载；
2. `ammo` 精确等于当前 `GunData.ammo`；
3. `magazine_capacity` 精确等于当前 `GunData.ammo_amount`；
4. `mechanism`、弹匣族、显示键及外部供弹字段完整有效；
5. 若声明 `carrier_variants`，每一个额外容量都精确匹配当前 GunData 的可选扩容等级，且有自己的显示键。

任一项不一致，日志会说明拒绝原因，客户端也不会同步该定义、不会在创造栏出现错误弹匣、不会接管原包换弹。

`belt` 使用相同结构，只把 `mechanism` 改成 `belt`；成品仍是有独立余弹的 `tacz:magazine`，但作为弹链箱/弹鼓语义而非 STANAG 弹匣。

## 已审计的扩容载具变体

不少附属包在真实 `GunData.extended_mag_ammo_amount` 中暴露了扩容等级。它不能被当成“把 30 发 NBT 改成 60 发”的权限；每个额外容量都必须有自己的、可制造的实体载具声明：

```json
{
  "mechanism": "detachable_magazine",
  "magazine_family": "my_addon_f2000_556",
  "magazine_capacity": 30,
  "ammo": "my_addon:556x45",
  "display_name": "item.my_addon.magazine.f2000_30",
  "carrier_variants": [
    {
      "capacity": 40,
      "display_name": "item.my_addon.magazine.f2000_40"
    },
    {
      "capacity": 50,
      "display_name": "item.my_addon.magazine.f2000_50"
    }
  ]
}
```

运行时会逐项拒绝以下任一种情况：

- `carrier_variants` 被写在 `internal_box`、`tube`、桥夹等非外部载具上；
- 容量重复、等于基础容量，或没有独立 `display_name`；
- 额外容量不精确等于**当前加载** `GunData.extended_mag_ammo_amount` 的可选等级之一。

TACZ 当前的扩容等级最多使用该数组的前三项。少数旧枪包虽然写有第四个历史数值，但运行时无对应等级，故不能借此生成不可实际选用的实体弹匣。

基础载具仍是 `magazine_capacity` 对应的 level-0 实体。对 `detachable_magazine` / `belt` 而言，**实体载具本身**才是容量部件：25/30/50 发物件不要求再在枪上安装一张旧 TACZ `extended_mag` 配件。旧配件在原包中只是“虚拟弹匣容量”的表示；物理供弹启用后，已安装 `tacz:magazine` 的容量会成为 HUD、tooltip、脚本容量和换弹事务的权威值。

`extended_mag_ammo_amount` 仍是严格的版本 guard：它证明当前包确实公开过该容量状态，并防止兼容层伪造任意容量。未声明能容纳旧备弹数的实体载具时，旧世界迁移会**保持 legacy 状态并拒绝交换**，而不是把超出的子弹悄悄截断。

已验证的第三方 surveyed 平台会为基础容量和每个 `carrier_variants` 条目各生成一条真实 Gunsmith Table 多槽供弹器委托；结果是不同容量的独立 `tacz:magazine`，不是 GUI/REI 上显示相同物品的假扩容。

## 与现有弹匣的共享

`magazine_family + ammo + mechanism` 是真实互插契约，`capacity` 是该族中某个已制造实体的尺寸：

- 想共享 30 发 STANAG 时，各枪明确声明同一个 `stanag_556`；当前已验证的同族实体容量可互插。例如 M4A1、M16A4、HK416D、SCAR-L 的 30 发 STANAG 是同一物件，20 发 M16A1 也可使用这张已经声明、已经制造的 30 发 STANAG；
- 想使用专用壳体时，声明新的稳定 family；
- 额外容量必须仍由某个同 family、同 Ammo、同 mechanism 的已验证声明或 `carrier_variants` 实体化，不能只因枪的口径相同就接受；
- `SCAR-H` 是 `.308` / 7.62×51 接收机，不与 5.56 的 M4A1/STANAG 互插；它需要自己的 `scar_h_308` 20 发实体弹匣；
- 仅口径相同并不代表可互插，不能省略 family。

## 对测绘第三方枪的实际制造出口

通过验证的第三方外部供弹声明不再只允许用创造/调试方式拿到 `tacz:magazine`。

若该枪已经进入真实的 surveyed Gunsmith Table 工业终端，运行时会生成一条真实多槽**供弹器委托**：

```text
中性弹匣壳体毛坯（消耗）
+ 同一把测绘枪的平台结构套件（消耗）
+ 该枪 production template（保留）
+ 测绘夹具（保留）
→ 空的、已配置的实体弹匣或弹链箱
```

这不是把完整成枪当模具，也不是置物台多输入伪装。它是 Gunsmith Table 的真实多槽事务；附属包若要获得高保真 Create 生产线，仍可自行提供实际 carrier 工艺数据。

已声明高保真平台的默认 **34** 个可拆卸供弹器身份继续使用既有 Basin → 量规 → 壳体/供弹组件 → 单工件总装路线，不会被 survey 委托替换。

## 非可拆卸供弹绝不能套用此文件

以下机制有各自实际状态与装填事务：

| mechanism | 正确状态 |
|---|---|
| `internal_box` / `tube` / `revolver` / `single_shot` | `InternalFeedAmmoCount` 或原枪内部状态 |
| `stripper_clip` / `speedloader` | 物理装填器 + 已审计 reload route |
| `en_bloc_clip` | `InstalledEnBlocClip`，最后一发后真实 ItemEntity 弹出 |
| `legacy` | 完整保留原包整数备弹/脚本行为 |

附属包如果没有已核验的动作、脚本 feed 点和供弹结构，就应该声明 `legacy` 或完全不提供 `gun_feed`。诚实地保留原行为优于一张“看起来合理”的错误弹匣 JSON。

## 诊断

重载资源后，管理员可执行：

```text
/tacz industry audit
/tacz industry reference my_addon:f2000
/tacz industry feed inspect my_addon:f2000
```

`industry audit` 现在会额外显示供弹适配统计：已接受、可选附属包未安装而休眠、以及因 Ammo/容量/字段不符被拒绝的声明数量。

`feed inspect` 直接读取服务器已加载的 `GunData`，报告 Ammo、基础/原始扩容容量、`reload.type`、bolt、脚本和当前已验证的适配状态。它是为兼容包作者收集**可验证事实**的命令；输出会明确提醒：它不会也不能据此自动选择 `detachable_magazine`、`belt`、管仓或转轮机制。

玩家不需要运行仓库 Python 工具；`tools/generate_industry_content.py --check` 只用于作者/CI 验证内置默认供弹器清单。
