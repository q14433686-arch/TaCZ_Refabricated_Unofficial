# 把 26.3 的进度分别移回三条 Fabric 线

**这是交接单，不是兼容公告。** 三条接收线都从自己的 R3-hotfix4 起步，各自交付 JAR 和验证记录。合并进 `26.3`、或一条旧线通过测试，都不会使另外两条线完成移植。

| 接收线（核对时的 HEAD） | 平台与资源来源 | 从这里开工 | 状态 |
|---|---|---|---|
| `26.2(main)` · `f9ae55a` | Java 25，无 remap；源码资源树，离屏目镜掩码 | [26.2 执行单](26.2.md) | OPEN |
| `26.1.2` · `569869f` | Java 25，无 remap；资源 bundle + 源文件覆盖，深度孔径 | [26.1.2 执行单](26.1.2.md) | OPEN |
| `1.21.11` · `aac343a` | Java 21，Loom remap；资源 bundle + 显式排除覆盖项，深度孔径 | [1.21.11 执行单](1.21.11.md) | OPEN |

**拿哪一版当参照？** 旧 26.3 是 [`cc990c3`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/cc990c3f784aa31c8fd7340f8cc158609c9bd16d)；以已构建的 [`de43f82`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/de43f82cfb00ff61ac81acb4384896e740074a85) 为本轮**功能**快照。`26.3_tacz-1.2.X@f6678ba` 是中途版本，不含后来的账号 HUD 和普通合成修复。上游行为参照是 [Sh1roCu 的 Fabric 1.21.1 `356bdf9`](https://github.com/Sh1roCu/TACZ-Refabricated/tree/356bdf9d48f2225bac6ceb56adc5576a418517a8)，**不是**让目标游戏加载其 1.21.1 JAR。下列命令在已取得这些提交的仓库根执行，不改变分支：

```sh
git diff --stat cc990c3 de43f82 -- src/main/java/com/tacz/guns/resource
# 查最终实现及其演进，不从中间失败的提交批量 cherry-pick：
git log --oneline cc990c3..de43f82 -- src/main/java/cn/sh1rocu/tacz/compat/rei
git show de43f82:docs/investigations/ORDINARY_CRAFTING_26_3.md
```

## 分批移植：带走行为和测试，重写平台接口

各线可以并行，表中顺序是**同一条线内**的依赖关系。HUD 的纯数值/存储部分可独立开始。

| 批次与 26.3 源码入口 | 在目标版接什么 | 目标版怎样才算通过 |
|---|---|---|
| 1 · 资源/网络：`GunPackLoader`、`GunPackOverlayResolver`、`GunPackSync`、`UniversalPackConverter` | 目录与 ZIP 的 overlay 顺序；按对端能力选择旧通道或有界压缩分片，完整收到才换索引。转换器用**目标游戏**的 DataVersion、DFU、配方/loot codec 和 overlay 名，不改输入包、版本谓词或 Lua。 | 目录/ZIP 各加载一次；真正 `/reload` 后重建，断线重连不沿用前一服分片。转换先 `check`、另目录 `export`、再把导出包载入目标实例；不能等价转换的报 `partial`/跳过，不计成功。参照 `PackServerSmoke`、`ConversionReloadSmoke`。 |
| 2 · 物品/玩法：`SlotAdapterHelper`、配件 variant/OEM、`AmmoSource`、Lua 动作和客户端输入 | 客户端展示选择，服务端校验并结算；保留目标线弹药源与具名扩展 hook。按实际按键分派，不复制 26.3 的按键冲突假设。 | 旧栈读回，虚拟配件不返真实物；装/拆/卸弹、切枪、换服后库存守恒，取消的动作不重复发事件。用 `DefaultRefitSmoke`、`GunActionSmoke`、`PortInputSmoke` 作样例。 |
| 3 · 方块与内容：`AmmoStorageBlockEntity`、`DisplayRackRenderer`、`DEFAULT_PACK_120.tsv` | 三档弹药库、转移事务、连柜锁、染色挂板、掉落/升级、空槽预览需要注册、持久化、菜单、同步和渲染一起接。数据注册就绪后再导入对应默认内容；按目标版生成 recipe、advancement 与 loot。 | 专服存取、事务回滚、拆除只掉一次；保存重开不丢组件。客户端空槽预览不改库存、锁柜不预览。比对**成品**的有效包路径/索引，而不是 `src` 文件数。参照 `AmmoStorageSmoke`、`DisplayRackSmoke`、`RackPreviewSmoke`。 |
| 4 · 普通合成与查看器：`RecipeViewerReloadBridge`、`REIClientPlugin`、`ReiPackRefresh` | 区分原版 3×3、配方书解锁、枪匠台专用配方。REI 只替换插件拥有的展示；JEI 使用该版自己的生命周期；缺席时不加载实现类。不要把“输出是 TaCZ 物品”当作展示所有权。 | 专服手摆能产出 `tacz:gun_smith_table`；取得铁块后书中可见。JEI/REI **分开装**：先收到服务器普通配方，再同步枪包，确认普通配方仍在、双向查询更新；再载入仅服务器数据包新增的普通配方重测。见[三层验收与用例](../../investigations/ORDINARY_CRAFTING_26_3.md)。 |
| 5 · 画面：`GunRigBaker`、`ScopeLensFrame`、`WorldEmitterManager`、枪口/曳光、PAL 与 TML | 保留目标版原生的手部、世界、深度、透明排序和光影路径；按该版时序截取姿态/光照，不复制 RenderPearl 或 1.21.1 SBM。 | 同一机位拍经典/PIP、无遮挡/遮挡、第一/第三人称，先无光影再单测 Iris。普通挂枪与单挂配件不是整枪静态 VBO；64 枪夹具只计单板 CPU 提取，不是 FPS。参照[实测与未覆盖项](../../investigations/PORT_GAPS_26_3_VALIDATION.md)。 |
| 6 · 持枪位置与 HUD：`client/tuning`、`ViewTuningScreen`、`RenderCrosshairEvent` | 先移数值、分享码与档案，再接目标版暂停菜单、HUD 和第一人称变换。客户端按**启动器 UUID**存 `config/tacz/view-profiles/`，无档案才用旧 TOML 作初值；优先级：单枪 → 枪包类别 → 账号默认。 | 两账号 A→B→A 保存/取消互不覆盖；同类别的两把枪分别验证覆盖/继承，开镜、改装、F5 不被腰射 HUD 或 XYZ 干扰。检查旧 HUD1、HUD2、POS1 与错误码不写回。参照 `AccountHudSmoke`、`ViewTuningSmoke`、[实景对照](../../investigations/HUD_RETICLE_26_3.md)。 |

**资源与许可：** [默认包清单](../../../DEFAULT_PACK_120.tsv)是 **26.3** 的 4,176 个有效目的地及来源散列，不是三个目标 JAR 的现成校验和。按目标版覆盖层、codec 和退役路径生成本线清单；保留 TACZ Dev Team 等署名、[资源与粒子代码许可](../../../LICENSES.md)。不要把整个旧游戏 jar、测试夹具或重复 MAE 打进成品。

## 如何交接结果

26.3 的[构建、专服与普通客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44482591156)、[Iris](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44482591124)、[独立 JEI/REI](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44481576437)及[测试源码](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/de43f82cfb00ff61ac81acb4384896e740074a85/src/gametest/java/com/tacz/guns/test)是**对照**，不是旧线成绩。旧线没有同名 GameTest 任务时，移植夹具或记录同操作的实际进服结果，不要将缺任务视为通过。

每条线分别在[交接账本](../HANDOFF_LEDGER.md)回填：**目标 SHA / 该线 JAR 与 CI 链接 / 无可选模组的专服＋客户端 / 单测过的可选组合 / 剩余或不适用项及原因**。未验证项维持 OPEN。26.3 的模组版本已单独升为 `1.2.0+fabric.26.3.R1`，三个接收分支不跟随改号，也不能以版本号或编译绿灯宣称完整包兼容。
