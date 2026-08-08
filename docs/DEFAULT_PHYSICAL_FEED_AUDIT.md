# 默认枪包实体供弹复核

> 本文只描述 GPL 工业数据层；不修改、复制或重新分发
> `assets/tacz/custom/tacz_default_gun/**` 中的默认枪包资源。

## 本轮纠正的两个错误

### 1. 实体扩容弹匣不应要求再装一张旧 `extended_mag`

FAL 的 20/25/30/32 发实体载具都已被服务器接受，但旧实现把
`GunData.extended_mag_ammo_amount` 错当成“接收机必须另装虚拟扩容配件”的门槛。
因此未安装旧配件时只允许基础 20 发，这是错误的双重弹匣模型。

正确模型是：

```text
旧 TACZ extended_mag attachment
= 原包用来表示虚拟容量的配件

实体 detachable_magazine / belt
= 真正决定当前容量的库存物件
```

现在已安装实体载具的 `MagazineCapacity` 会成为 HUD、tooltip、Lua/API
最大容量和换弹事务的权威值。`extended_mag_ammo_amount` 仍只作为版本
验证：声明出来的额外实体容量必须是当前加载 GunData 实际公开的容量状态。

### 2. `MagazineFamily` 才是跨枪互插契约

同 family、同 mechanism、且同一显式 resolved canonical calibre 的**已声明实体身份**可以跨平台互插。若 native AmmoId 不同，还必须有已加载 AmmoIndex 与明确 `industry/ammo_profiles` canonical 映射；外部载具保存统一 canonical `MagazineAmmoId`。
这不是“同口径都能插”：仍须有明确 family 和审计确认的互插事实。

| 平台 | family | 结果 |
|---|---|---|
| M4A1 / M16A4 / HK416D / SCAR-L | `stanag_556` | 共用已制造的 30 发 STANAG |
| M16A1 | `stanag_556` | 基础 20 发，也可插入同族已声明的 30 发 STANAG |
| SCAR-H | `scar_h_308` | 使用专用 `.308` 20 发实体弹匣，不与 M4A1 5.56 STANAG 互插 |

所以：如果提问中的“SL-H”是 **SCAR-L**，它本来就应与 M4A1 共用
`stanag_556`；如果是 **SCAR-H**，它不是同一种弹匣，但此前漏了专用
`scar_h_308`，现已补上。

## 默认包复核结果

默认 GunData 共 54 个 ID；现在有 52 条明确 `gun_feed`：

- **38 条**外部实体供弹声明，归并为 **34 种**真正制造的族/弹药/容量身份；
- 其余为管仓、桥夹、固定内仓、转轮或单发；
- 仅 `lonetrail`（1 发手炮，未发现可验证外部载具）和 `minigun`
  （原包 `reload.type = inventory`、没有 `ammo_amount`）保持 legacy，而不是被猜成弹匣枪；这两个例外写入 `tools/industry/default_gun_policy.json` 的 `explicit_legacy_feed_ids`，作者/CI 会拒绝任何未声明、未说明理由的默认 GunData 漏网。

本轮从“错误 internal / 缺失”修正为外部实体供弹的默认枪：

| 枪 | 基础实体供弹器 | 审计依据摘要 |
|---|---|---|
| AA-12 | 12 号 8 发盒式弹匣 | GunData 8 发、扩容数组、`magazine` 动画骨骼 |
| AI AWP | .338 5 发弹匣 | AWM 身份、5 发数据、可拆卸狙击枪供弹结构 |
| AUG | 5.56 30 发专用弹匣 | GunData、`magazine` 动画骨骼 |
| B93R | 9 mm 20 发弹匣 | xmag reload、`magrelease` 动画 |
| Desert Eagle / Golden | .50 AE 7 发 / .357 9 发 | xmag reload、`magazine`/`magrelease` 动画 |
| M107 / M95 | .50 BMG 10 发 / 5 发弹匣 | 手动动作数据、`magrelease` 动画 |
| SCAR-H | .308 20 发专用弹匣 | xmag reload、`magazine`/`magrelease` 动画 |
| SPR-15 HB | 5.56 15 发专用弹匣 | xmag reload、`magazine`/`mag_release` 动画 |
| Timeless .50 | .50 AE 8 发弹匣 | xmag reload、`magazine` 动画 |
| 81-1 式 | 7.62×39 30 发专用弹匣 | GunData、`magazine`/`mag_release` 动画 |

这些不是由 `reload.type = magazine` 自动扫描出来的；每条都已经有独立
`data/tacz/industry/gun_feed/*.json`、对应 `MagazineFamily`、真实 Create
Basin → 量规 → 壳体/供弹组件 → 单工件总装线路。新加的 .50 BMG、盒式
霰弹、马格南手枪供弹器也使用更高的中性壳体/供弹组件毛坯数量，而不是
用同一份输入改 NBT 数字。

其中新增的 12 种默认供弹器暂时使用 `tacz:magazine` 的**中性运行时回退图**；
图标覆盖报告会明确列为 `runtime_fallback`，不会把 AK、STANAG 或其他现有
型号贴图冒充成 AA-12、SCAR-H、M107 等专用壳体。服务器身份、制造和库存
事务均为真实实现，专用美术可后续独立补齐。

## 尚需实机回归

本文件是静态复核，不能代替游戏回归。优先验证：

1. FAL 20/25/30/32 四种实体容量无需旧扩容配件即可插入，且 HUD/tooltip
   显示对应容量；
2. M16A1 能插入 M4A1/SCAR-L 制造的 30 发 STANAG，退匣后仍是同一个
   独立余弹 ItemStack；
3. SCAR-H 拒绝 `stanag_556`，只接受 `scar_h_308`；
4. AA-12、AWP、M95、M107、AUG、B93R、Desert Eagle、SPR-15 HB、
   Timeless .50、81-1 式均经过空仓/战术换弹、潜行退匣、掉落、重连和
   满背包回退测试；
5. `lonetrail` 与 `minigun` 保持 legacy，不因本轮“补覆盖率”而获得虚构
   `InstalledMagazine`。
