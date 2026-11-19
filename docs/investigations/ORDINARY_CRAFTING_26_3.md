# 26.3：进服后普通合成消失，分三层排查

以 `tacz:gun_smith_table` 为例：**被制作的物品**是枪械制造台；**用来制作它的容器**是原版 3×3 工作台。这个 ID 是 `minecraft:crafting_shaped`，不是 `tacz:gun_smith_table_crafting`。枪包缓存里的工作台配方条数不能证明这个 ID 在服务器上存在，也不能证明它出现在配方书或 REI。

| 玩家看到什么 | 真正负责的一层 | 此次改动 |
|---|---|---|
| 材料按图摆好，结果槽有没有物品 | 服务器当前数据包、材料标签，以及是否启用限制合成 | 没有在客户端造替代配方，也不绕过服主禁用的配方。专服 GameTest 用真实 3×3 输入按 `RecipeType.CRAFTING` 匹配并产出制造台。 |
| 原版工作台的绿色配方书里有没有 | 该玩家在该服务器解锁的 recipe display，还受「仅可合成」筛选影响 | 给模组本体的 20 个普通合成（含 LRT 工作台、染色及存储升级）补了无展示的解锁进度。例如取得铁块解锁枪械制造台；不会把枪包专用配方塞进配方书。 |
| REI 按 R/U 能不能查到 | REI 当前客户端展示列表；和解锁进度不是同一份数据 | 晚到的枪包只替换插件自己标记的展示，并刷新枪包物品变体。服务器下发的普通合成留在原 holder 中，不按输出是否为 TaCZ 物品来删除。 |

**为什么是进服后才消失？** 反馈日志中的 REI 26.3.823 先收到服务器的 SET（2946 个展示），随后 TaCZ 安装枪包（21 个台、1066 个专用配方）又触发 REI 整套插件 START/END。前者没有逐个打印配方 ID，这不是“服务器少装了某个具体配方”的证据。核对对应的 26.3 实现：

- [`DisplaySyncPacket.handle`](https://github.com/shedaniel/RoughlyEnoughItems/blob/dd80869c8ca672ec5068ef7e59e7737d83f483d4/runtime/src/main/java/me/shedaniel/rei/impl/common/networking/DisplaySyncPacket.java#L100-L125) 将 SET/APPEND 排为一次性任务，标记 `SYNCED`；[客户端注册表](https://github.com/shedaniel/RoughlyEnoughItems/blob/dd80869c8ca672ec5068ef7e59e7737d83f483d4/runtime/src/main/java/me/shedaniel/rei/impl/client/registry/display/DisplayRegistryImpl.java#L156-L194) 执行完即清任务队列。
- [`AbstractDisplayRegistry.startReload`](https://github.com/shedaniel/RoughlyEnoughItems/blob/dd80869c8ca672ec5068ef7e59e7737d83f483d4/runtime/src/main/java/me/shedaniel/rei/impl/common/registry/displays/AbstractDisplayRegistry.java#L52-L69) 换掉整个 holder。客户端单独重载不会让远端服务器认为其展示版本已过期。先 SET 后全量刷新正好会丢这批展示；反过来的包序不一定出现症状。
- 本分支不再发起这次 REI 全量重载。等 REI 自己的任务结束后，只用 holder 的单条删除操作处理 `PACK_ORIGIN`；保留其他展示与索引。JEI 仍用自己的生命周期 listener，不伪造收到配方包的事件。

**如果更新后仍有问题**：客户端和服务器都更新，进服后重新取得一个铁块，关掉配方书的「仅可合成」。分别试手摆、绿色配方书和 REI 查询，记录具体配方 ID 及哪一步失败即可。只有手摆也不出结果时，应先查服务端数据包是否移除／覆盖该 ID、材料标签及限制合成规则；客户端日志本身不能做这个判断。新增解锁针对本体配方，不会替第三方包或服主批量授权。

### 验证边界

- [专服 GameTest、完整构建与原生客户端（`de43f82`）](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44482591156/job/135418163014)：实际配方管理器匹配普通 3×3 并产出制造台；原有默认包的 23 个公开进度与新增 19+1 个配方解锁均解码。
- [REI 26.3.823（`fb3dde4`）](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44481576437/job/135414816745)：在真实联机包路径的**集成服**测试中，取得铁块后，客户端原版配方书收到了制造台。REI 的 `tacz:gun_smith_table`、`minecraft:crafting_table` 都带有 `SYNCED` 标记。特意在两条都到达之后触发枪包刷新：枪包查询对象被换新，两条普通配方展示保持**原对象**。真正重载数据包后，只有测试服务器新包提供的无序普通合成 `reload_smoke:paper` 也通过相同检查；新增枪出现在侧栏和双向查询中，没有重复页面。[JEI 单独环境](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44481576437/job/135414817020)通过配方书与新枪页面回归，没有用 REI 的测试冒充 JEI 的对象身份断言。
- `de43f82` 与该查看器测试版本的配方、REI/JEI 代码和查看器测试相同。以上不是登录反馈者的私人服务器复测；未上传其日志。

### 原版合成在，但客机查不到默认包的制作配方

REI 的服务器 SET 数量和 TaCZ 的 `pack displays` 总数都不能说明枪械台上有多少配方：后者还统计枪↔配件、枪↔弹药等用法页。三个默认台共用服务端的 `data/tacz/recipe_filters/default.json`；使用自己过滤器的附加包工作台不一定跟着消失。

- **同名文件要区分规则和配方。** 不同枪包对同一过滤器的白名单、黑名单是累加的，黑名单优先；同包内仍是选中的高优先级 overlay 覆盖原文件。旧组合资源包只把第一个过滤器交给加载器，可能盖掉默认可见规则，也可能丢掉附加包的排除项。普通配方文件仍按资源优先级取一条，不做合并。
- **客机只用服务器的制作数据。** 只写黑名单的过滤器序列化后带 `"whitelist":[]`。旧客机将其读成一个永远不匹配的白名单；现在空数组和未写白名单一样，不会屏蔽未被拉黑的配方。不会从本地默认包补造服主移除或禁用的制作配方，也不动 REI 收到的原版合成。
- **复测方法：** 服主和客机都换同一修复构建，重启服务器再重新进服，不必删除枪包。空手打开枪械台、配件台、弹药台，或在 REI 按 R 查 AK47、ACOG TA31、9mm **的制作页**。若仍缺，提供对应一项的 ID 和两行客户端日志即可：`[TaCZ Workbench Sync]` 的 `tacz namespace: … received, … decoded` 是服务器下发和成功解码的条数；`[TACZ Recipe Viewer]` 的 `distinct tacz workbench recipes displayed` 是按工作台筛选后注册到 REI 的不同 `tacz:` 制作配方数。这些是命名空间统计，不把所有 `tacz:` ID 都当作一定来自原装包。收到就是零时先看服务端的包是否加载；收到但没显示时再查相关台的过滤器、页签及附加包规则。

[修前回归（只加测试，未改实现）](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44579673619/job/135756602328)：两种包序分别丢了默认规则或附加包规则；只带黑名单的服务端对象经同一 Gson 往返后，客机错误拒绝了 `tacz:gun/ak47`。这复现了反馈的症状，不等于知道该私服具体用了哪份过滤器。
