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
