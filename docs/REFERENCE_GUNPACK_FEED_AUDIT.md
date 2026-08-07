# main 参考枪包的供弹适配审计

> 审计范围：`origin/26.2(main)` 的提交 `9a574f7f8ed28e4f5105f9d77ceab0f41b8f90ac` 中随仓库保留的四个枪包 ZIP。本文是**离线数据/脚本结构审计**，不是 Gradle 构建或游戏实机回归结果。

## 目的与边界

这四个包确实覆盖了现代可拆卸弹匣、弹链箱、固定内仓、桥夹、漏夹、转轮、管仓、单发、燃料/工具弹药以及大量自定义 Lua reload 的常见组合。因此它们很适合检验适配层的边界。

但它们也证明了一个不能绕过的事实：**TACZ 原始 `reload.type` 不是现实供弹机构字段。** 本项目不会根据它、枪名、模型、动画文件名或命名空间自动把第三方枪改成 `InstalledMagazine`。

适配仍须满足：

```text
明确的人类/包作者证据
+ 独立 industry/gun_feed sidecar 声明
+ 当前加载 GunIndex/GunData 的服务器验证
+ 对脚本换弹时序的实际回归
```

没有通过这条链的枪保持原包 legacy reload，而不是得到一张“看起来像弹匣”的错误 NBT。

## 检查方法

- 直接读取 ZIP 中的 `data/<namespace>/data/guns/*_data.json`；带注释、尾逗号的旧式 JSONC 按 TACZ 的宽松解析语义读取；
- 同时记录 `ammo`、`ammo_amount`、`extended_mag_ammo_amount`、`bolt`、`reload.type` 与 `script`；
- 不把任一模型/名称当作自动机制判据；
- 不修改四个原始 ZIP，也不向默认枪包资源目录写入数据。

## 原始数据统计

| 枪包 | namespace | GunData 条目 | 显式 `reload.type=magazine` | 缺失 `reload`（GunData 默认仍为 magazine） | `fuel` | 有 `extended_mag_ammo_amount` | 有自定义 script |
|---|---:|---:|---:|---:|---:|---:|---:|
| `Apocalypse_v1.1.7_G.zip` | `bf1` | 30 | 25 | 1 | 4 | 10 | 9 |
| `Cold War from 1947-1991 v0.51.zip` | `rainforest` | 18 | 18 | 0 | 0 | 17 | 0 |
| `GunpowderRevolution_gunpack v1.2.7.zip` | `hamster` | 36 | 36 | 0 | 0 | 12 | 25 |
| `[TaCZ] Enlisted Gun Pack v1.2.1.3.zip` | `ww` | 52 | 50 | 2 | 0 | 42 | 9 |
| **合计** | — | **136** | **129** | **3** | **4** | **81** | **43** |

也就是说，136 条中有 132 条在运行时落入 `MAGAZINE` 这一历史 API 分类；其中实际世界里显然混有固定仓、转轮、管仓、漏夹、桥夹、可拆卸弹匣和弹链。这正是禁止“见 `reload.type=magazine` 就接管为实体弹匣”的可复现实证。

### 已观察到的代表性分歧

- `rainforest:56`、`rainforest:fal`、`rainforest:m60` 都是 `reload.type=magazine`，但它们不能共享同一套自动推断：已有 `56` 的桥夹适配是单独审计的；FAL 与 M60 若要接入，则分别需要明确的可拆卸载具/弹链证据和回归。
- `hamster:m1garand` 与 `ww:m1g` 的原始分类也是 `magazine`，但已分别以 `en_bloc_clip` 接入，因为可以审计到 8 发装入、最后一发空夹退出的专门状态；这不是从名称或容量 `8` 猜出来的。
- `hamster:gew98`、`m1903`、`mosin91`、`mosin9130`、`type99` 使用可核对的循环/桥夹脚本分支，故现有 profile 才能声明 `stripper_clip` 与 `reload_routes`。
- `hamster:sw_mk2`、`webley` 的速度装填器同样依赖已审计的脚本分支；不能把所有 6 发枪都推成 speedloader。
- `bf1`、`rainforest`、`ww` 中既有标准默认 reload，也有 `tacz:xmag_reload_logic` 或包自定义脚本。外部载具只能在该枪确实会经过服务器 `FEEDING → FINISHING` 事务，且实机验证没有双扣/漏扣后接入。

## 扩容容量是第二个不能省略的事实

四包中 **81/136** 条 GunData 存在 `extended_mag_ammo_amount`。绝大多数是三个等级；`rainforest` 还存在三条写了四个数值的旧数据。当前 TACZ 运行时的 `AttachmentDataUtils.getAmmoCountWithAttachment` 只会选择前三个扩容等级，因此第四个数不能被当作可制造实体弹匣的依据。

这直接促成了 `carrier_variants`：

```text
基础 magazine_capacity
→ 已声明且可制造的 level-0 载具
真实 GunData 的某个可选扩容等级
+ 显式 carrier_variants 条目
→ 对应容量的独立实体载具
```

没有匹配的变体时，迁移逻辑会拒绝把超出基础容量的旧备弹截断到小弹匣；它会保留 legacy 状态。这样不会为了“适配”而吞弹，也不会在 GUI/REI 里伪造扩容。

详细字段与示例见 [`THIRD_PARTY_MAGAZINE_ADAPTER.md`](THIRD_PARTY_MAGAZINE_ADAPTER.md)。

## 首个带扩容实体载具的参考包适配

本轮只将 `rainforest:fal` 作为受版本 guard 的外部载具样本接入：

```text
基础：20 发
实际 GunData 扩容等级：25 / 30 / 32 发
机制：detachable_magazine
脚本：无自定义 Lua，走 TACZ 默认服务器 reload 状态机
```

它有单独的 `rainforest/industry/gun_feed/fal.json` 和参考档案；`carrier_variants` 的每一种容量都由当前 GunData 精确验证。其原动画包含 `magrelease` 与 `additional_magazine` 的空仓/战术 reload 路径，因此不是仅从 `reload.type` 写出的泛化 JSON。包缺失时它休眠；Ammo、基础容量或任一扩容数组值变化时它会被拒绝，而不会接管升级后的枪。

物理 FAL 弹匣本身是容量部件：20/25/30/32 发都应直接被同族 FAL 接收机识别，不要求再装原包的虚拟 `extended_mag` 配件；插入后的实体容量会覆盖 HUD、tooltip 与脚本容量读数。

## Cold War 第一批 cohort 审计

根据 main 中 `Cold War from 1947-1991 v0.51` 的实际 index、GunData、默认/指定动画及导出的 `review_cohorts`，首批明确接入的不是“所有 rifle/smg 自动弹匣化”，而是以下逐枪审计后的机制：

| 枪 | 机制 | family / 说明 |
|---|---|---|
| EM-2、FAMAS F1 | detachable magazine | 各自专用实体族 |
| FR-F2 | detachable magazine | 专用 .308 狙击枪弹匣族 |
| HK SPG1（PSG-1 资料/ G3 动画） | detachable magazine | `g3_308`，可使用已声明 G3 族载具 |
| L85A1、L86A2 | detachable magazine | `stanag_556`；L86 是支持步枪但仍使用 STANAG，而非因为 class=mg 就造 belt |
| M60 | belt | 专用 .308 弹链箱族 |
| RPD、RPD-MS | belt | 共用 7.62×39 弹链/鼓式容器族；同容量变体共享显示身份 |
| PM12S、PM-63、Vz.64、Vz.68 | detachable magazine | 各自专用 9 mm 族 |

这些 profile 的 Ammo、基础容量和可选载具容量均由当前 Cold War GunData 校验；每个额外容量必须等于该 GunData 可选择的前三项扩容值。它们的制造出口仍是 surveyed Gunsmith Table 的真实多槽事务，不能伪装成默认枪包的高保真几何 Create 线。

`56` 保持已审计的桥夹固定仓；AT4/M72 保持低容量发射器路径。此批接入不为 `ccrp`、`cib`、`ww` 等其他 cohort 自动创建任何实体弹匣。

## Apocalypse / BF1 第一批 cohort 审计

`bf1` cohort 没有因为“smg/rifle/mg”而整批自动启用。仅在实际枪名、GunData、动画与公开机械供弹资料相互一致时接入：

| 枪 | 机制 | 说明 |
|---|---|---|
| Chauchat、Lewis Gun | detachable magazine | 半月形 / 顶部盘式可拆卸供弹器 |
| De Lisle、Selbstlader M1916、Welrod | detachable magazine | 各自专用可拆卸盒式/握把供弹器 |
| SMG08/18、Villar Perosa、ZK-383、VG1-5 | detachable magazine | 鼓式、双盒式或盒式可拆卸供弹器 |
| MG08/15、MG42 | belt | 可拆卸弹链箱 / 鼓式容器语义 |

其中 M1916 的 25 发可拆卸盒式弹匣、Villar Perosa 的双 25 发可拆卸盒式供弹、VG1-5 的可拆卸盒式弹匣、Welrod 的可拆卸握把弹匣和 ZK-383 的可拆卸盒式弹匣均与其主包身份和导出数据相符。RSC 1917、Mannlicher M1895、刘将军步枪、Obrez、Model 10-A、Sjögren、转轮、火焰/医疗/低容量发射器仍不在此批强制变成实体可拆卸弹匣。

## GunpowderRevolution / Hamster 外部载具补充

已有桥夹、漏夹和快装器 profile 保持原样；本批只补充能够由 GunData、枪名和动画直观确认的外部载具：

| 枪 | 机制 | 说明 |
|---|---|---|
| Madsen | detachable magazine | 专用可拆卸盒式弹匣 |
| MG14/17 | belt | 弹链箱语义；其自定义 reload logic 仍使用同一服务器物品交换点 |
| Luger P08、Makarov、MP18 | detachable magazine | 各自专用手枪/冲锋枪可拆卸弹匣 |

Berthier、SKS、SW Mk2 41 及其他转轮/历史 clip 仍保持候选或原有物理装填路线，不能因为同为 `pistol`/`rifle` 就自动接管。

## 当前可安全使用的工作流

1. 在装有目标枪包的**服务器**执行：

   ```text
   /tacz industry feed inspect <namespace:gun_id>
   ```

   它输出当前真正加载的 Ammo、基础容量、原始扩容数组、reload API 分类、bolt、script 与已验证适配状态。
2. 根据包作者资料、实际 reload 脚本、动画时序和实机装填行为，人工确认机制；不要由上一步输出自动选择机制。
3. 在兼容数据包中放置 `data/<target namespace>/industry/gun_feed/<path>.json`，写入机制、family、Ammo、基础容量及（若有）每个真实扩容载具。
4. 重载后检查：

   ```text
   /tacz industry audit
   /tacz industry feed inspect <namespace:gun_id>
   ```

   只有 `accepted` 的定义才会同步给客户端、进入创造栏、接管真实 reload 事务，并为 surveyed 平台生成真实 Gunsmith Table 多槽供弹器委托。
5. 用实体弹匣/弹链箱进行生存模式回归：插入、战术换弹、空仓换弹、退匣、满背包掉落、重连、扩容等级切换与中断 reload。自定义 Lua 枪必须特别验证“恰好一次”的库存转移。

普通玩家不需要运行 Python。仓库中的 Python 生成器和离线 ZIP 审计仅是作者/CI 工作，不是游戏安装步骤。

## 不会做的捷径

- 不扫描模型骨骼或 `mag_out` 动画名后自动接管枪；
- 不根据枪名、真实世界常识或口径自动写入第三方 `gun_feed`；
- 不把第四个、运行时不可选的扩容数组元素制造成实体弹匣；
- 不用创造栏样品、REI 图标或 JSON 配方代替真实服务器库存事务；
- 不在没有恢复出口的 legacy 第三方枪上强加随机维护锁止。
