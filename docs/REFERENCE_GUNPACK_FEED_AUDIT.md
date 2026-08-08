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

## Enlisted / WW 第一批外部供弹 cohort

本批没有把 survey 的 `ww` 分组直接转成 JSON。实际人工审计重新读取了用户上传在
`origin/26.2(main)` 的 `[TaCZ] Enlisted Gun Pack v1.2.1.3.zip`：归档 SHA-256 为
`a0dc616286fc43146e2c6b94ee3bae141a4e6aff65a3fece2370f8388ab057f5`。每把启用枪均核对了
`data/ww/index/guns/<id>.json`、`data/ww/data/guns/<id>_data.json`、其 display/geometry/animation
资源和原始 Gunsmith Table 成枪 recipe；模型节点和动画只作人工佐证，**不是**启用规则。

这轮明确写入 25 个 `data/ww/industry/gun_feed/*.json` sidecar 和同数的
`industry/reference/guns/*.json`。运行时仍以当前加载 GunData 的 Ammo、基础容量和声明的可选
扩容数组校验；包升级后不匹配就拒绝，而不会继续接管换弹。

| 已核对接收机 | 机制 / 显式 family | 基础与已制造的实体容量 | 人工边界 |
|---|---|---|---|
| `m1918`、`m1918a1`、`m1918a2` | detachable / `ww_bar_3006` | 20 | 只在三种 BAR 变体之间共享实际 20 发 BAR 盒式弹匣。 |
| `m1921`、`m1928a1`、`m1a1`、`m1t`、`m28s` | detachable / `ww_thompson_45acp` | 20、30 | 审核的是汤普森 20/30 发盒式路线；没有把鼓式外形伪称成精确模型。 |
| `m1`、`m2` | detachable / `ww_m1_m2_carbine_30c` | 15、20、30 | 仅 M1/M2 Carbine 这对经审核的实际互插族。 |
| `m1919` | belt / `ww_m1919a6_3006_belt` | 100、150、250、500 | M1919A6 弹链独立；其 client state machine 只负责动画，服务器仍使用普通 reload 事务。 |
| `mg34` | belt / `ww_mg34_792x57_belt` | 50、100、150、200 | 即使与 MG42 同口径也不自动共享 family。 |
| `mg42` | belt / `ww_mg42_792x57_belt` | 50、100、150、200 | 独立链族；不从 BF1 MG42 或名称借用互插关系。 |
| `mp28`、`mp34` | detachable / 各自专用 family | 30 或 32；各自 50、75、100 | 同为 9 mm 也不合并。 |
| `mp38`、`mp40`、`mp41` | detachable / `ww_mp38_40_41_9mm` | 32、50、75、100 | 这是明确审核过的 MP38/40/41 盒式弹匣互插组。 |
| `sten` | detachable / `ww_sten_mk2_9mm` | 32、50、75、100 | Sten Mk II 保留独立 family，不借“9 mm + 32”猜测互插。 |
| `avt_40`、`svt_40` | detachable / `ww_svt_avt_762x54` | 10、15、20 | 显式审核的 SVT/AVT 10 发弹匣组。 |
| `g43` | detachable / `ww_g43_792x57` | 10、15、20 | Gewehr 43 专用盒式弹匣。 |
| `as44` | detachable / `ww_as44_762x39` | 30、34、37、40 | `tacz:xmag_reload_logic` 的三条实际扩容 feed 路径逐项受容量 guard。 |
| `stg44` | detachable / `ww_stg44_792x33` | 30、34、37、40 | 与 AS-44 的口径、family 都独立。 |
| `t20` | detachable / `ww_t20_3006` | 20、21、22、25 | 已确认可拆卸盒式供弹，但**没有**在没有硬证据时宣称与 BAR 互插。 |

扩容值只取当前 GunData 前三个可选层中实际存在的数值；汤普森与 M1/M2 的重复值及 `1` 发旧占位值
没有伪造成新的实体弹匣。上述每个基础/扩容载具都由 surveyed Gunsmith Table 的真实多槽委托产出：

```text
中性弹匣壳体毛坯（消耗）
+ 对应 WW 平台结构套件（消耗）
+ 对应 production template（保留）
+ survey fixture（保留）
→ 空的、带 MagazineFamily / Ammo / Capacity / FeedDeviceKind 的实体载具
```

所以这不是创造物品、REI 图标或传送带多输入假配方；`detachable_magazine` 和 `belt` 都进入现有服务器
库存交换事务。`ea:792x57` / `ea:792x33` 仍严格引用原枪包的 AmmoId：若安装环境缺少提供这些 Ammo 的
依赖包，不能用本层伪造替代弹药，原枪和实体载具都会保持其依赖边界。

视觉上，普通盒式弹匣继续使用已有的中性 detachable material，绝不冒充某一把枪的精确网格。M1919A6、MG34
和 MG42 的外露弹链复用 `tacz_extra:item/mag_m134_belt`，映射明确标为 **family-level material**，不是这些
型号的精确 belt geometry。DP-28 顶部盘、AN/M2、Type 96/99、C96、Lee-Enfield、其余手枪/冲锋枪和所有
未列出的 `ww` survey 候选本轮仍保持 legacy；它们没有因名称、class、弹匣节点或 survey 条目被自动开启。

这仍是离线结构审计，不是 Gradle 构建或游戏实机回归。后续实机应重点覆盖空仓/战术换弹、潜行退匣、满背包掉落、
重连、实体容量与 HUD/tooltip、以及 AS-44/StG44 的三档容量切换；MG34/MG42/G43/StG44 还应在实际安装 EA
弹药依赖的服务器上验证一次完整装填与卸载。

## Delta Force: Storm Assault / WEMQL_R 完整常规外部供弹 cohort

这批是对“survey 不只有仓库内四个 ZIP”的首个实际回应：它不从本仓库复制或修改 Delta Force
资源。证据链来自用户上传的运行日志与 survey：`latest.log` 明确记录当前加载
`Delta Force-Storm Assault-v2.5.zip`、主 namespace `wemql_r`；schema-2 survey 的 SHA-256 为
`919d71b5e0217bc061e45e4b386328f8f7aaf27b00ec29e72a43e85938e3a20a`。该包公开武器清单也明确是
6 rifle、6 DMR、1 SMG，并提供自己的工作台。每个下列 GunId 都按当前 survey 的 Ammo、基础容量、
全部前三档可选容量、`xmag_reload_logic` 或默认 reload contract 逐项复核后才进入 sidecar。

| 已核对接收机 | family 决定 | 实体容量 | 说明 |
|---|---|---|---|
| `ak12` | 私有 `wemql_r_ak12_545x39` | 30、40、60、75 | AK-12 的可拆卸 5.45 盒式弹匣；不因 AK 外形自动接入任何其他 5.45 family。 |
| `akm`、`akm_long` | `ak_762x39` | 30、35、40、45 | 人工确认同一 AKM 7.62×39 接收机；40 发明确复用现有 RPK 实体身份。 |
| `aug` | `aug_556` | 30、40、45、60 | 明确是 AUG 专用可拆卸弹匣，而不是 STANAG 猜测。 |
| `hk416` | `stanag_556` | 30、40、45、60 | 人工确认的 STANAG 5.56 接收机；这是物理标准契约，不是同口径自动归类。 |
| `kriss_vector` | `vector_45acp` | 20、30、45、50 | 只接入既有 Vector .45 平台族，不把其他 .45 手枪/SMG 一并接入。 |
| `m14_long`、`m14_long_foot_stool`、`m14_short`、`m14_short_foot_stool` | `m14_308` | 10、20、30、50 | 四种 M14 EBR 长度/外装变体明确共享 M14 可拆卸弹匣。 |
| `m7`、`m7_prismatic` | 私有 `wemql_r_m7_68x51` | 20、25、30、45 | 仅项目明确命名的 M7 与 Prismatic Offensive Gen2 共族；30 发 M7 Prismatic 使用同一物理 identity。 |
| `scar_h` | `scar_h_308` | 20、30、45、60 | 只接入既有 SCAR-H .308 family，绝不与 STANAG/M14 混用。 |

共 **13 把**、**8 个明确 family 决定**。其中 AKM、AUG、HK416、Vector、M14 与 SCAR-H 复用已经存在的
标准 family/材质身份；私有 AK-12 与 M7 只使用已有中性可拆卸弹匣材质，避免把不相符的图说成精确模型。

这仍不是从 GunId、`reload.type`、容量或 xmag 脚本自动推导：这些字段只是当前版本 guard。每把都有独立
`gun_feed` 与 reference profile；GunData 的 Ammo 或基础容量变化会 fail closed。已有 surveyed carrier factory
只会在该 GunId 的**实际加载 Gunsmith Table 成枪 recipe**存在时，生成壳体毛坯 + 平台套件 + 保留模板/fixture 的
真实多槽实体载具委托；本批没有添加创造样品或另起一条假制造路线。

仍需实机验证 M7/M7 Prismatic 的跨容量互插、AKM/RPK 40 发身份、以及各 xmag 三档实体容量切换。未列出的
`ccrp`、`classicr`、`cib`、`cibs`、`murasamet`、`suffuse` 等候选继续 legacy，等下一组人工审计；它们没有被
本批“批量”二字自动实体化。

## KhanPowder / Murasamet 历史枪械批量 cohort

本轮按“供弹本身无争议、同平台重复变体”批量审计 `murasamet`，但没有用 `mg` / `smg` class 自动决定 belt
或 detachable。用户提供的 survey 环境日志记录 `KhanPowder_v0.8.99_hotfix.zip` 对应 namespace `murasamet`；
当前 schema-2 survey 仍以 SHA-256
`919d71b5e0217bc061e45e4b386328f8f7aaf27b00ec29e72a43e85938e3a20a` 提供每把枪的真实 Ammo、基础容量、脚本与
可选容量 guard。下列 **27 把**均有独立 sidecar/reference，不是从 cohort 文字直接批量生成。

| 已核对接收机 | 机构 / family | 容量 | 人工边界 |
|---|---|---|---|
| `amd65` | detachable / `ak_762x39` | 20、30、45、75 | 明确审核 AMD-65 的 AK 7.62×39 接口；不是按“AK 名称”盲合并。 |
| `g3a3` | detachable / `g3_308` | 20、15、18 | 明确 G3 盒式弹匣接口；15/18 是独立 survey 物理容量。 |
| `l85a3` | detachable / `stanag_556` | 30、45、60 | 明确 STANAG；源数组重复的 30 不会再制造一张假变体。 |
| `dp28`、`dpm` | detachable / 私有 47 发 top-pan | 47 | 同一 DP/DPM 顶部盘式接口；仍保留中性视觉，不伪称方盒/精确盘式网格。 |
| `m1918`、`m1918a1`、`m1918a2` | detachable / `ww_bar_3006` | 20、30、35、40 | 三种 BAR 明确共享盒式弹匣；这也说明 MG class 绝不等于 belt。 |
| `mg34`、`mg42` | belt / 各自私有 belt family | 各 75 | 两把均人工确认弹链供弹，但不因名称或 7.92×57 自动共享 family。 |
| `m1921`、`m1928a1` | detachable / 私有 Thompson drum-capable family | 20、30、40、50 | 可以使用本包实际 40/50 容量路线。 |
| `m1a1` | detachable / `ww_thompson_45acp` | 20、30 | 刻意只保留盒式路线，不让前两把的 40/50 身份泄漏进 M1A1。 |
| `m3`、`m3a1` | detachable / 私有 M3 .45 family | 30 | 明确共享 Grease Gun 弹匣。 |
| `mp28`；`mp38`、`mp40`；`sten_mk2`、`sten_mk5`；`lanchester` | detachable / 四个明确 family | 各 32 | 只共享 MP38/40、Sten Mk II/V 这两对；其余即使同为 9 mm / 32 发也不臆测互插。 |
| `ppsh`、`kp31` | detachable / 各自私有 box/drum family | PPSh 25、35、50、71；KP/-31 70 | 机构真实，但没有合适授权模型时仍使用中性视觉。 |
| `m1911a1`、`tt33`、`vz61`、`micro_uzi` | detachable / 对应 M1911、TT-33、Vz.61、Uzi family | 7；8；20、25、35、50；20、25、35、50 | M1911 和 Micro Uzi 才复用已有标准实体族；TT-33/Vz.61 保持私有。 |

MG34/MG42 的载具复用现有 `tacz_extra:item/mag_m134_belt`，并在 icon mapping 中明确标记为 family-level
exposed-belt material，不是 MG34/MG42 的精确网格。DP/DPM 顶部盘、PPSh 与 KP/-31 的鼓式/异形供弹器没有被
硬套 RPK 或方盒贴图；它们继续走已有中性 fallback，真实服务器库存、容量、退匣和 surveyed Gunsmith Table 多槽
制造委托仍照常生效。

本批刻意保留 KhanPowder 中的 `mika`、`miyako`、`natsu`、`kittygun`、`barstard`、各转轮、逐发/clip 枪和其他
未明机制条目为 legacy。批量只缩短重复平台的人工核对，不降低 unknown gun 的 fail-closed 边界。

## CIBR / CIB + CIBS 镜像平台批量 cohort

这一批专门利用 survey 里可验证的“主枪 + 同平台皮肤”重复组，而不是见到 `cibs` namespace 就自动复制机制。
用户提供的 survey 环境日志记录 `[Tacz1.1.7+]CIBR_GunsPack_v0.3_1.1.7.zip` 主 namespace 为 `cib`；每个
CIB/CIBS 对在写 sidecar 前都逐项比较了当前 Ammo、基础容量、完整前三档扩容数组、bolt 和 script。只有这些
运行时合同完全相同、且人工确认现实供弹机构无争议的对才进入本批。

| 主枪 / 镜像枪 | family | 实体容量 | 边界 |
|---|---|---|---|
| `ak103` / `ak103_laffey` | `ak_762x39` | 30、45、50、60 | 明确 AK-103 同接收机皮肤组。 |
| `ak105` / `ak105_kaltsit` | 私有 `cib_ak105_545x39` | 30、40、50、60 | 同一镜像组，但未把它自动并入 AK-74/AK-12。 |
| `galilace32` / `galilace_lesh` | 私有 Galil ACE 32 family | 35、45、55、60 | 同平台皮肤，不从 7.62×39 数字推成 AK 互插。 |
| `m4` / `m4_koei`；`mk18` / `mk18_jianjiu` | `stanag_556` | 30、40、50、60 | 四把均人工确认 STANAG 接收机。 |
| `qbz191` / `qbz191_warrior` | 私有 QBZ-191 family | 30、40、50、60 | 仅这一对；不自动并 QBZ-95。 |
| `qbz951` / `qbz951_asiimov` | 私有 QBZ-95-1 family | 30、35、40、50 | 仅这一对；不自动并 QBZ-191。 |
| `sig556` / `sig556_shiroko` | 私有 SIG 556 family | 30、40、50、60 | 5.56 不等于自动 STANAG family。 |
| `type20` / `type20_hibiki` | 私有 Type 20 family | 30、40、50、60 | 同平台皮肤的显式契约。 |
| `cs_awp` / `awp_hm` | 私有 AWP .308 family | 5、10、15、20 | 手动 bolt 仍人工确认可拆卸盒式弹匣。 |
| `qcq171` / `qcq171_ocean` | 私有 QCQ-171 9 mm family | 30、35、40、70 | 只接入同一 SMG 皮肤组。 |
| `usp` / `usps` | 私有 USP .45 family | 12、15、20、25 | 只接入 USP/USP-S 对，不扩展到其他 .45 手枪。 |

共 **24 把** active profile。所有私有 family 保持已有中性 detachable material；AK 与 STANAG 复用已有实体
标准族和材质。为避免相同物理 NBT 身份因来源枪包不同而显示不同名称，本批还统一了已声明
`ak_762x39 + tacz:762x39 + capacity` 及 `stanag_556 + tacz:556x45 + capacity` 的 display key：Rainforest、
WEMQL_R、KhanPowder 和本批 CIB 的相同容量现在使用同一个稳定显示身份。旧世界物品仍保存旧 key，匹配/余弹
语义不受影响。

这不启用 CIB/CIBS 的未知机枪、霰弹枪、固定仓、转轮、火箭、`error` 条目或未配对候选。所有已接入载具仍经当前
GunData fail-closed 验证，并仅在实际 loaded Gunsmith Table 成枪 recipe 存在时由 surveyed factory 给出真实多槽
制造出口。

## Suffuse GunSmoke 核心步枪 / 精确步枪 / PKP 批量 cohort

本批针对 `suffuse` 中能够由实际平台身份、当前 survey contract 和人工供弹资料共同确认的核心外部载具。
用户提供的 survey 环境日志记录 `Suffuse-GunSmoke-Pack1.0.7--hotfix.zip`；每把均以当前 Ammo、基础容量、
脚本、前三档扩容数组作版本 guard，而不是仅凭 class 或名字启用。

| 已核对接收机 | 机构 / family | 容量 | 边界 |
|---|---|---|---|
| `aks74u` | detachable / 私有 5.45 family | 30、34、37、40 | 自定义 AmmoId，不自动并入其他 AK。 |
| `ash12`、`kacpdw`、`mpdr`、`rm277`、`xm7` | detachable / 各自私有 family | 10/30/20 等对应 survey 变体 | 机构明确，但没有凭相似口径或未来平台猜 family。 |
| `n4` | detachable / `stanag_556` | 30、40、50、60 | 明确 STANAG 接口。 |
| `aw50`、`axmc`、`axsr`、`dvl10`、`gm6`、`m200`、`svd` | detachable / 精确步枪专用 family（AXMC/AXSR 共族） | 各自 survey 容量 | 手动 bolt 不被误判为固定仓；只在 AXMC/AXSR 这对明确共享。 |
| `pkp` | belt / 私有 PKP belt family | 120 | 真正弹链，复用现有 exposed-belt material，但明确不是精确 PKP 网格。 |
| `qbu191`、`qbz191`、`qbz192`、`qbz951`、`qbz951s` | detachable / QBZ/QBU 私有族（951/951S 共族） | 对应 survey 容量 | 相同 5.8×42 不自动合并不同代际/用途接收机。 |
| `saddam_golden_ak` | detachable / 私有 family | 30、34、37、40 | 特殊 AK 平台不从名称继承跨包 AK 互插。 |

共 **21 把** profile。PKP 的 `tacz_extra:item/mag_m134_belt` 映射仍标为 family-level material；其余专用、精确、
异形载具使用中性 detachable material，避免宣称不存在的精确网格。`an94` 的专用脚本、AR-57/P90 异形顶置、PP19
螺旋仓、霰弹枪、逐发/clip 路线、转轮、低容量发射器及未列出的候选均继续 legacy。

所有本批 sidecar 仍用 GunData Ammo/基础容量 fail-closed，并由实际 loaded Gunsmith Table 成枪 recipe 触发 surveyed
多槽载具委托；没有独立创造样品或伪造制造出口。

## ClassicR 完整枪包审计（33 / 33）

按“每轮至少完整完成一个枪包”的规则，本轮对 `classicr` 的 **全部 33 条** survey entry 给出明确结论，而不是只
挑可拆卸弹匣枪。用户提供的运行日志将 ClassicR 与 CCRP 1.1.6 release hotfix2 对应；当前 survey 继续对每个
结论的 Ammo、基础容量、脚本与可选容量做版本 guard。

- **26 把**已获得 `gun_feed`：真实 detachable / belt、服务器库存事务和实际 surveyed Gunsmith Table 多槽载具出口；
- **7 把**仅获得 factual `industry/reference`，显式保持 legacy，绝不因“完整审计”而伪造一个弹匣。

| 已启用的主要组 | 机制 / family 原则 |
|---|---|
| AK-12、B93R、FAL Tactical、Glock 18、M92FS、MAC-10、MK47、MP7、MP9、STI 2011、TEC-9、TTI G34、UDP-9 | 明确可拆卸，但除已审核标准外各自 private family。 |
| HK416 A5、MK18 Mod 1 | 明确 `stanag_556`；同一 capacity 使用稳定 STANAG display identity。 |
| M1A1 Thompson | 可拆卸，但 ClassicR 40/45/50 容量保持私有，不污染 earlier 20/30 M1A1 盒式 family。 |
| DP-28 | 可拆顶置盘，不是 belt；中性视觉，不硬套方盒。 |
| M60 | 真正 belt / 私有 belt-box family。 |
| M82A2、MRAD、MRAD ELR、MSR、NGSW-R、QBZ-191、SCAR MK20、SPR-15 | 手动 bolt / precision class 仍逐项确认为可拆卸，不以 class 代替机构。 |

以下七条是**完成审计后的明确 legacy 决定**：

| GunId | 记录的真实/安全结论 | 为什么不启用实体外部载具 |
|---|---|---|
| `aa410` | `unknown` | 当前证据不能安全区分 tube / box。 |
| `colt_python` | `revolver` | 保留原转轮；尚无真实 speedloader / clear route。 |
| `kar98` | `stripper_clip` | 固定内仓 + bridge clip；其自定义 script route 尚未接入。 |
| `m24_renewed` | `unknown` | 名称、bolt、容量不足以判定固定或可拆。 |
| `mauser_c96` | `internal_box` | 标准 C96 固定内仓，不把扩容数组变成弹匣井。 |
| `mgl_40mm` | `revolver` | 旋转榴弹发射器需独立 cylinder 事务。 |
| `minigun` | `unknown` | 没有审计到实际 removable belt/box source，不从 class/capacity 猜。 |

因此 ClassicR 现在没有“未处理的 survey 枪”：每把要么有 fail-closed 实体供弹与真实制造出口，要么有可审计的
legacy 边界记录。旧资源、默认枪包和未授权模型均未修改。

## CCRP 完整枪包审计（131 / 131）

ClassicR 完成后，本轮按同一收口标准审计 `ccrp` 的全部 **131** 条 survey entry。这里的“完整”不等于把所有
`reload.type = magazine` 变成实体弹匣：每条都有 reference profile，明确外部机构的才有 active sidecar。

- **110 条**：已进入 `detachable_magazine` 或 `belt` 路线；每条都用当前 Ammo、基础容量、前三档可选容量作为
  fail-closed guard，并由实际 loaded Gunsmith Table 成枪 recipe 驱动 surveyed 多槽载具出口；
- **21 条**：保留 legacy，并有对应事实记录和原因；没有用假弹匣、假 belt 或创造样品“补齐数量”。

本包中规模较大的 AK、AR、AUG、SCAR、HK、M4、MP、P90、精确步枪、手枪及支持步枪组均逐项有 sidecar；为
避免“同口径 + 同容量”误成互插，本轮绝大多数 CCRP active 载具使用稳定的私有 `ccrp_<gun>_<ammo>` family。
这不是降级：它使每个 receiver 获得真实物理载具、容量和制造出口，同时把跨平台互插留给以后有明确物理契约的
二次审计。

几个容易被 class 误导的反例已明确处理：

| GunId | 结论 |
|---|---|
| `aug_hbar`、`m27_iar`、`mg36`、`rpk74m`、`rpk_203`、`type_95_longbow` | active detachable；MG class 不被误推成 belt。 |
| `hk21`、`m249_saw` | active belt；不是普通盒式弹匣。 |
| `aics_m700`、`m110`、`m110a3`、`m14_hbar`、`m39_emr`、`mk13_mod5`、`mk18_mjolnir`、`sr25`、`troy_m14_sass` | active detachable；manual/precision 不被误推成 fixed internal。 |
| AK、AR、AUG、SCAR、P90、MP5/MPX、手枪等明确外部平台 | active detachable，但默认 private family，未偷做大范围共享。 |

完整审计后的 **14 条 legacy 决定**：

| GunId | 结论 |
|---|---|
| `camg_dexterous`、`camg_krait`、`camg_cheetah40`、`msh41` | 自定义平台的真实 carrier 未证，保持 unknown / legacy。 |
| `cslr_42a`、`cslr_43a`、`cslr_44`、`m38_spr`、`silence_meteor`、`type_192`、`v308` | 身份/外部载具接口未证；不从 rifle class、Ammo、容量或名称猜测 detachable。 |
| `camg_m1014`、`lastwar`、`m1887_long`、`marlin_1895`、`springfield1873_tube_mag` | 当前真实 loop/tube script 路线未接入，保持 tube legacy。 |
| `crow_and_egret`、`requiem` | action-ambiguous custom weapon，保持 legacy。 |
| `lmt_m203` | 单发 40 mm launcher，不伪造可拆载具。 |
| `m4_bolter` | 自定义 launcher / bolter 供弹未证，保持 legacy。 |
| `mp9_thunder` | thunder-cell utility weapon，不伪造成金属弹药筒弹匣。 |

因此 `ccrp` 现在和 ClassicR 一样不存在“没有结论的 survey 枪”：每把要么有严格验证的实体供弹，要么有明确、可见、
不改变原包行为的 legacy 原因。所有 private family 使用中性既有 detachable/belt material；没有将未经授权的第三方
模型或错误图标冒充精确美术。

## CIBR 完整枪包审计（CIB 89 / 89，CIBS 17 / 17）

CIBR 的主 namespace 是 `cib`，皮肤/镜像内容在 `cibs`；两者属于同一发行包，因此本轮一并收口 **106 / 106**
条 survey entry，而不是只处理此前的镜像组。

- `cib`：**67 active / 22 legacy**；
- `cibs`：**15 active / 2 legacy**；
- 合计：**82 条**真实 detachable/belt sidecar，**24 条**明确的 legacy factual profile。

已明确接入的内容包括 AK、AS Val、FAL、Galil、HK、K2、M4/M16/MK18、OTs、QBZ/QBU、SIG、T91、Type 20/56、
EVO/JS9/PM9/PP19/PPSh/QCQ/Type79、常规手枪、AWP/G3SG1/M99/QBU/SSG08/SV98/SVD、Origin12/USAS12，以及
MG3/Negev/PKP/QJY/QJZ belt。先前已审镜像枪与本轮主枪保持同一 family；新补条目默认 private family，防止仅因
口径/容量相同发生跨平台互插。

| 易误判组 | 完整审计结论 |
|---|---|
| `mg3`、`negev`、`pkp`、`qjy201`、`qjy88`、`qjz171` | active belt。 |
| `qjb951`、`origin12`、`usas12`、QBU/QBZ、各精确步枪 | active detachable；MG / shotgun / sniper class 不自动决定机构。 |
| `686`、`dprkrpg`、`dzj08`、`lmt_m203`、`qlu11` | single-shot / launcher legacy。 |
| `hawk97_1`、`nova`、`qbs09` | tube legacy，保留原 script loop。 |
| `hawk97_2`、`origin12db`、`qba221`、`qba221_burst`、`widow` | box/tube/特殊高容量实现未证，legacy。 |
| `r8`、`type38`、`mini`、`qjb201`、`type11`、`type73`、`881`、`882`、`ar2`、`error` | 分别为转轮、clip、rotary、未审脚本、未证 LMG、特殊/无效身份；均有 factual legacy record。 |

对于“没有细分材质和功能”的记录，本轮不再只散落在说明文字中：新增作者/CI gap register，机器可读文件
`tools/industry/third_party_feed_gap_registry.json` 按 family 列出当前 `exact` / `family-level` / `neutral generic`
材质状态，并逐枪列出 runtime `legacy` 功能缺口；人可读汇总见
[`THIRD_PARTY_FEED_GAP_REGISTER.md`](THIRD_PARTY_FEED_GAP_REGISTER.md)。该登记册不会启用任何供弹，只用于后续授权
贴图、专用盘/鼓/双联模型和脚本 reload route 的补齐排序。

## KhanPowder / Murasamet 完整枪包审计（76 / 76）

此前已接入的 27 把历史外部载具在本轮扩展为完整 `murasamet` 审计：全部 **76 条** survey entry 都已有 reference
profile。

- **28 条 active**：AMD-65、G3A3、L85A3、DP/DPM 顶盘、BAR 组、MG34/MG42 belt、Thompson/M3/MP/Sten/Lanchester、
  PPSh、KP/-31、M1911/TT-33/Vz.61/Micro Uzi，以及新增的 Boys 反坦克步枪可拆 5 发弹匣；
- **48 条 legacy**：原包的火绳/粉枪、单发/火箭、固定仓+桥夹、漏夹、转轮、tube/loop、空气枪、能量武器和未证自定义
  平台均有 factual reference，不用“完整”之名把它们错误实体弹匣化。

| 典型组 | 完整审计结论 |
|---|---|
| DP-28/DPM | active detachable top-pan，不是 belt；细分盘式美术在 gap register 中记录为待补。 |
| M1918/A1/A2 | active detachable BAR；MG class 不决定 belt。 |
| MG34/MG42 | active belt，各自 private family，现有 exposed-belt material 仅是 family-level。 |
| Boys | active detachable 五发反坦克步枪弹匣；RPG class 不决定 single-shot。 |
| Garand | `en_bloc_clip` 事实已记录，但本包 script 未审，runtime 保持 legacy。 |
| Kar98/Mosin/SKS/Type56/俄制步枪 | `stripper_clip` 事实已记录，真实 script feed route 未审，runtime legacy。 |
| M1917/Nagant | `revolver` legacy；无 speedloader route 不假装成外部弹匣。 |
| 火绳枪、粉枪、AT4/Panzerfaust/RPG、wave-energy/TDF、未知动漫平台 | 单发/utility/unknown legacy。 |

因此 Murasamet 没有未决 survey entry；每把都有 active 或 explicit legacy 的可见结论，且新增 legacy 记录已同步进入
细分材质/功能缺口登记册。

## Apocalypse / BF1 完整枪包审计（30 / 30）

原有的 11 把明确外部供弹器保留；本轮为剩余 19 条补充 factual legacy reference，因此 `bf1` 的全部 **30 条**
survey entry 都有明确结论。

- **11 条 active**：Chauchat、De Lisle、Lewis、M1916、MG08/15、MG42、SMG08/18、VG1-5、Villar Perosa、
  Welrod、ZK-383；
- **19 条 legacy**：燃料/医疗设备、单发/发射器、转轮、bridge/en-bloc clip、tube、未证自定义精确枪和微型手枪。

漏夹/桥夹不是被忽略：`man_m95` 记为 `en_bloc_clip` 事实，`liu`、`obrez` 记为 `stripper_clip` 事实，但因为该包
对应脚本的真实物理插入/逐发/空夹弹出 route 还未审计，runtime 仍保持 legacy。它们已进入功能缺口登记册，未来
路线完成后直接复用已有 `base_m_loader` 的桥夹/漏夹材料与实体状态设计，而不是另造一套视觉假物品。

其余例如 EF46/M2-2/Wex fuel、Syringe、Lunge Mine/Faust/MHGL、Martini/TG1918 单发、Model10/Sjögren tube、
Model3 revolver 都明确不进入 detachable/belt。至此 Apocalypse 没有未覆盖 survey entry。

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
