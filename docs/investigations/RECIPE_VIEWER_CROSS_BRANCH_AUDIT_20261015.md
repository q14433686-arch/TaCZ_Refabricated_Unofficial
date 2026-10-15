# 26.3 / 26.1.2 / 1.21.11 配方双向代码审计

日期：2026-10-15。范围：原反馈中的十种普通工作台配方、枪包同步、JEI/REI 刷新与真正的服务端合成。
本次只读取其它分支的固定提交，不切换分支、不修改这些分支、不发布回移修复。

## 1. 结论：存在同类风险，但不能说三个固定依赖都已复现同一 bug

| 本仓库快照 | 编译依赖 JEI / REI | JEI 刷新结论 | 另一个同步缺口 |
|---|---|---|---|
| `26.3@6339040` | `31.8.0.48` / `26.3.823` | 优先 `Internal.restartJei()`；正常入口避免伪造事件，但失败后仍回退到危险事件 | 已登记 vanilla serializer 同步 |
| `26.1.2@1ec4429` | `29.5.0.26` / `26.1.819` | 直接伪造 `AFTER_RECIPES_UPDATED`；遇到带缓存清理的新 JEI 会有同类风险；旧 tag 基线尚无清缓存逻辑 | 没有主动登记 vanilla serializer 同步 |
| `1.21.11@bdf18af` | `27.23.0.71` / `21.11.816` | 同上；旧 tag 基线不能被当成已证实受影响 | 没有主动登记 vanilla serializer 同步 |

另发现：**三个分支都调用 REI 全量插件重载；所查 REI 上游源码会替换显示表，而已接收的服务端显示不保证重放。**
这条路径同样可能造成“枪包自定义配方恢复了，普通配方反而消失”，需要独立处理，不能用 JEI 修复覆盖它。

证据边界：

- 已确认本仓库调用路径和所查上游源码的状态变化。
- 已比较 JEI 旧 tag 与相应版本线当前源码，不能以最新源码冒充旧发布物。
- **没有拿到六个固定版本的实际发行 jar 做逐一字节码验证，也没有启动游戏复现。**
- 因此，本文的“条件性缺陷/风险”不是“三个编译依赖确定都会清缓存”的结论。
- JEI/REI 的客户端配方显示丢失，不等于服务端普通工作台不能手动合成。

## 2. 固定快照和证据链接

本仓库完整提交：

- [26.3：63390406c82bb85b0145e7b173c856be3bb5d8f8](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/63390406c82bb85b0145e7b173c856be3bb5d8f8)
- [26.1.2：1ec4429100929f360b0281bc360cca7ad2785ec8](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/1ec4429100929f360b0281bc360cca7ad2785ec8)
- [1.21.11：bdf18af9696f34eb2b8dba988375c6502fbef1e4](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/bdf18af9696f34eb2b8dba988375c6502fbef1e4)

所查上游当前分支提交（另外用 GitHub commits API 确认其为 commit SHA）：

| 上游 | 26.3 | 26.1 | 1.21.11 |
|---|---|---|---|
| JEI | `d9a1a1af4e341f665d9fd220ed7019c273249124` | `3f982442d1b3cbf984c0574e475a3c5fb8d9dca3` | `6685347837c7e650641bc76233be31a66a2df870` |
| REI | `b3bc5013d33f12bbea5737bba5fb76652e2b07d8` | `3de45768a3e2307566c5356234e95d3cb44e7761` | `1a8053d02e2028e641ee2f4f57db2b12404c8303` |

额外检查了 JEI `v29.5.0`、`v27.23.0` tag；`v31.8.0` tag 查询为 404。
源码 tag 与 `29.5.0.26` / `27.23.0.71` 的完整构建号不是已核实的一对一关系。
REI 当前分支源码也不能冒充 `823/819/816` 三个发行 jar。

## 3. 正向追踪：枪包数据怎样到达配方查看器

三个分支都具有以下活动路径，不只是注释中提到它：

```text
服务端 CommonAssetsManager.OnDatapackSync
  -> ServerMessageSyncGunPack
客户端 handle -> Minecraft.execute -> doSync
  -> 远程连接时清除本地 CommonAssetsManager 实例
  -> CommonNetworkCache.INSTANCE.fromNetwork
  -> ClientIndexManager.reload
  -> RecipeViewerReloadBridge.requestReload
  -> END_CLIENT_TICK / tick（等待 level、player；合并请求）
  -> refreshJei / refreshRei
```

数据来源复查：

- JEI `GunModPlugin` 和 REI `GunModREIPlugin` 的活动注册循环均从
  `CommonAssetsManager.get().getAllTableRecipes()` 取数据，并对配方调用 `init()`。
- `get()` 返回服务端管理器实例或客户端 `CommonNetworkCache`，不是无条件读取服务端单例。
- 因而不能把当前代码诊断成“多人客户端还在直接读 `getInstance()` 的旧问题”；相关旧注释不是活动逻辑。
- 这条枪包同步传的是 TaCZ 数据，**不是给 JEI 同步普通 vanilla crafting 配方的替代品**。

## 4. JEI 反向追踪：刷新为什么会损坏普通配方

带代理服切换修复的上游 `ClientLifecycleHandler`：

```text
真正 Fabric 配方同步 -> setClientSyncedRecipes -> receivedRecipeSync = true
真正原版配方更新事件 -> 检查标志 -> 重启 JEI -> 标志复位 false
TaCZ 再伪造一次更新事件 -> 标志为 false -> clearClientRecipes -> 再次重启
```

同一连接中的正常 runtime stop 本身保留配方缓存；问题是事件监听器中的显式清理。

上游源码证据：

- [JEI 26.1 当前生命周期处理器](https://github.com/mezz/JustEnoughItems/blob/3f982442d1b3cbf984c0574e475a3c5fb8d9dca3/Fabric/src/main/java/mezz/jei/fabric/startup/ClientLifecycleHandler.java)
- [JEI 1.21.11 当前生命周期处理器](https://github.com/mezz/JustEnoughItems/blob/6685347837c7e650641bc76233be31a66a2df870/Fabric/src/main/java/mezz/jei/fabric/startup/ClientLifecycleHandler.java)
- 26.1 的清理变更：[134917c9](https://github.com/mezz/JustEnoughItems/commit/134917c9e42eb2c9f780442d3c34e17ff876aec0)。
- 1.21.11 的清理变更：[4e934960](https://github.com/mezz/JustEnoughItems/commit/4e934960916a2042b2b6dfb408118d96af2303aa)。
- [旧 v29.5.0 处理器](https://github.com/mezz/JustEnoughItems/blob/v29.5.0/Fabric/src/main/java/mezz/jei/fabric/startup/ClientLifecycleHandler.java)、
  [旧 v27.23.0 处理器](https://github.com/mezz/JustEnoughItems/blob/v27.23.0/Fabric/src/main/java/mezz/jei/fabric/startup/ClientLifecycleHandler.java)：没有上述事件清缓存分支。

这解释了为何不能直接把 26.2 的诊断无条件套到旧依赖。
但运行时 JEI 不是由本模组锁死到 gradle 中的构建号，玩家更换为带新逻辑的 JEI 后，直接伪造事件会成为现实兼容问题。

26.3 已有 `restartJei()` 优先分支，属于部分修复，不是三个版本都原样缺陷。
其 catch 后仍执行危险事件：找不到方法、调用失败等情况会退回旧路径。
建议去掉该回退；需要兼容旧 JEI 时，使用实际生命周期实例的 `getReloadListener()`，而非伪造网络语义事件。
旧 tag 和当前三条 JEI 版本线都查到了这个实例 listener，但静态 `restartJei()` 并非所有旧版本都有。

## 5. 独立缺口：专服不装 JEI 时，配方可能从未同步

26.3 的 `TaCZFabric.registerRecipeSync()` 主动登记 `minecraft:*` serializer；另外两条分支没有这个登记。

JEI 的 common initializer 才会在服务端登记 vanilla serializers：
[旧 v29.5.0](https://github.com/mezz/JustEnoughItems/blob/v29.5.0/Fabric/src/main/java/mezz/jei/fabric/JustEnoughItems.java)、
[旧 v27.23.0](https://github.com/mezz/JustEnoughItems/blob/v27.23.0/Fabric/src/main/java/mezz/jei/fabric/JustEnoughItems.java)。
客户端则在 `ClientRecipeSynchronizedEvent` 接收真正的配方集合。

Fabric 的 RecipeSyncImpl 读取服务端按已登记 serializer 分组的配方，再按客户端支持列表发送；
没有集合就不发相关配方。可核对
[1.21.11 RecipeSyncImpl](https://github.com/FabricMC/fabric-api/blob/1.21.11/fabric-recipe-api-v1/src/main/java/net/fabricmc/fabric/impl/recipe/sync/RecipeSyncImpl.java)
及该模块 recipe sync mixins（此链接是分支链接，不作为固定发行物证明）。

所以在 **26.1.2 / 1.21.11 专服只装 TaCZ、客户端装 JEI、也没有其它服务端模组代为登记这些 serializer** 的条件下，
即便使用旧 JEI、事件不会清缓存，也仍可能缺失这些模组普通配方——不是“收到后被清空”，而是“从未通过这条链同步”。
纯原版 fallback 不能补齐服务端/模组数据包配方。
这个代码缺口已确认，具体 Fabric API 发行物和整合包环境还需要运行验证。

修复时不能简单登记所有 TaCZ 自定义 serializer：枪匠台另有同步链路，未解析/空材料还可能导致发包失败。
26.3 对同步列表另有过滤编码失败配方的逻辑；这不是清空服务端实际普通配方表的证据。

## 6. REI 反向追踪：全量重载会丢掉什么

三个本仓库分支的 `refreshRei()` 都反射调用两参数 `reloadPlugins(null, null)`。
所查 REI 三条上游源码的关键逻辑一致：

```text
RoughlyEnoughItemsCoreClient.reloadPlugins(null, null)
  -> ReloadManagerImpl：stage == null，遍历全部阶段
  -> DisplayRegistry 的 END 阶段 startReload
  -> AbstractDisplayRegistry.startReload：holder = holderFactory.get()
  -> 插件 registerDisplays（TaCZ 自定义配方被重新注册）
  -> DisplayRegistryImpl.endReload：fallback 注入 + 待执行 jobs
```

关键不是“全量重载”这个名字，而是服务端数据的补回路径：

1. `DisplaySyncPacket.SET/APPEND` 收到时把显示加入 jobs，随后写入当前 holder；jobs 执行后清空。
2. 普通 recipe-book ADD 事件也通过 job 调用 `addRecipes(mapped)`；没有在默认插件注册时重新遍历配方书恢复这些 entries。
3. `DefaultClientPlugin.registerDisplays()` 注册 fillers、堆肥/酿造等静态显示，**不会重放旧的服务器同步显示集合**。
4. `ClientRecipeFallback.AUTO` 在收到 server display sync 后关闭本地注入；所以不能指望 fallback 总能补回来。
5. 未收到 REI server display sync、走配方书+fallback 时，`serverProvidedIds` 去重也可能使已被擦掉的配方书显示不再由 fallback 补入。
6. 服务端 `ServerDisplayRegistryImpl` 用 `playerVersionMap` 判断玩家是否需要更新。客户端这次本地 reload 没有回报清空、也没有请求全量重发。
   服务端若无变化，会认为玩家已经是最新版本；不能假设几 tick 后必然自动补齐。
7. multiplayer 客户端无 integrated server；服务端注册器的 `fillRecipes()` 在无 server 时跳过，不是另一个补回入口。

确定的条件性推演：先收到并应用服务端显示，jobs 已消费；之后枪包同步触发本地全量重载；期间没有新的全量同步或等价重放。
此时旧 holder 被替换，原有服务器显示不再有来源，而 TaCZ 插件显示重新出现。
初次进服时若服务器同步恰好在刷新之后到达，可能掩盖问题；不能因此证明路径安全。

上游固定源码入口（以 26.1 为例）：

- [AbstractDisplayRegistry](https://github.com/shedaniel/RoughlyEnoughItems/blob/3de45768a3e2307566c5356234e95d3cb44e7761/runtime/src/main/java/me/shedaniel/rei/impl/common/registry/displays/AbstractDisplayRegistry.java)
- [DisplayRegistryImpl](https://github.com/shedaniel/RoughlyEnoughItems/blob/3de45768a3e2307566c5356234e95d3cb44e7761/runtime/src/main/java/me/shedaniel/rei/impl/client/registry/display/DisplayRegistryImpl.java)
- [DisplaySyncPacket](https://github.com/shedaniel/RoughlyEnoughItems/blob/3de45768a3e2307566c5356234e95d3cb44e7761/runtime/src/main/java/me/shedaniel/rei/impl/common/networking/DisplaySyncPacket.java)
- [DefaultClientPlugin](https://github.com/shedaniel/RoughlyEnoughItems/blob/3de45768a3e2307566c5356234e95d3cb44e7761/default-plugin/src/main/java/me/shedaniel/rei/plugin/client/DefaultClientPlugin.java)
- [ServerDisplayRegistryImpl](https://github.com/shedaniel/RoughlyEnoughItems/blob/3de45768a3e2307566c5356234e95d3cb44e7761/runtime/src/main/java/me/shedaniel/rei/impl/common/registry/displays/ServerDisplayRegistryImpl.java)

`AbstractDisplayRegistry.java` 和 `DisplaySyncPacket.java` 在所查三条 REI 上游分支逐文件 SHA-256 相同。
这能确认所查源码的行为一致，仍不能代替三个固定发行 jar 的验证。

**对当前 26.2 PR #109 的影响：它只修 JEI 路径，保留的 REI 全量刷新不能因此被宣称安全。**
后续 REI 修复应优先考虑仅替换 TaCZ 自有显示/催化剂；若必须全量 reload，必须同时设计带 origin 的保存重放或正式 resync，
并验证重复显示、服务端 REMOVE/SET、连接切换和异步重载，不能简单复制私有 map。

## 7. 真正合成链与数据反查

对每条分支检查以下十个配方：

`gun_smith_table`、`lrtactical:smith_table`、`attachment_workbench`、`ammo_workbench`、
`iron_ammo_box`、`gold_ammo_box`、`diamond_ammo_box`、`target`、`statue`、`target_minecart`（未标命名空间者为 `tacz`）。

结果：

- 三条分支的这十个 JSON 分别与 26.2 基线语义一致，共 30 个静态检查通过。
- 位于 `data/<ns>/recipe/`；类型为 `minecraft:crafting_shaped`；字符串 ingredient、`result.id`、`components` 格式；没有旧 `result.item/nbt`。
- pattern 与 key 对应，非 Minecraft 的材料 tag 声明存在且 values 非空。
- 工作台身份通过 `minecraft:custom_data.BlockId` 输出；`BlockItemDataAccessor` 经 `ItemNbtUtils.getTag()` 读取 `CUSTOM_DATA`。
- 弹药箱 `Level` 同样从 `CUSTOM_DATA` 读取，铁/金/钻等级与输出数据相接，不是遗失旧 NBT 的单向迁移。
- 未在内置枪包中发现同名十个普通配方的覆盖文件；外部枪包/数据包覆盖不在本次静态排除范围内。
- 26.x 中保留的旧 `ShapedRecipeMixin` 是历史源码，不应把未启用的旧注入当作活动配方解析器。

枪匠台本身又是独立链路：`GunSmithTableMenu.getRecipe()` 从服务端 common manager 取 table recipe 并 `init()`，
检查工作台 filter/tab；`doCraft()` 在服务端检查材料、扣料、产出，未解析材料不会直接放行。
它与十个 vanilla shaped 配方的服务端 RecipeManager 合成不能混为一谈。

因此当前证据没有定位到“这十个内置 JSON 全部写错”或“viewer refresh 删除了服务端十个配方”。
若不装查看器也无法手摆合成，必须另查服务端实际资源加载、外部包覆盖、live tag、配方冲突及 crafting 匹配日志。
静态 JSON 通过不是 Minecraft codec 或游戏内合成通过。

## 8. 生命周期附带风险（不冒充已复现根因）

- 三条分支都有 DISCONNECT handler，但对 `isMemoryConnection()` 早退。单人退出时不能据此认为 viewer 状态必定清理。
  26.3 源码也明确提醒该生命周期问题尚未验证；不能为了“看起来一致”直接删除豁免。
- `1.21.11.clear()` 只清 `reloadRequested`，不清 `reloadInProgress`，且没有另外两条分支的每连接一次 fallback 限制。
  fallback 跨断线的异步完成与新连接请求存在状态隔离不足的风险；尚无证据证明必然永久锁死或必然循环重载。
- 三条路径都在客户端线程安装缓存后排队 tick 刷新，不能再归因成已经明确修过的“网络线程直接重建全部客户端索引”。

## 9. 验证记录与后续门槛

可复现静态检查：

```bash
python3 scripts/audit_recipe_viewer_branches.py
python3 scripts/verify_recipe_viewer_reload.py
```

第一条只读取固定 Git 提交，检查 30 个内置 JSON 并输出三条分支的源码特征；布尔值不是安全性判定。
第二条只检查当前 26.2 JEI 修复结构，不验证其它分支、REI 或游戏行为。
当前 26.2 `build-reports/compile-java.log` 记录对修复提交 `4397753` 的 `compileJava` 成功；不是这三个分支的编译或实机验证。

下一步运行验证至少覆盖：

1. 每个 Minecraft 版本：固定 JEI 构建 + 带新清理逻辑的 JEI 构建；服务器装/不装 JEI，各自验证普通配方同步。
2. REI-only：先让 server displays/recipe-book entries 完整到达，再触发枪包刷新；对比刷新前后普通 crafting 和 TaCZ displays 数量、origin。
3. REI server sync AUTO fallback 与仅配方书路径分别验证；记录新 SET/APPEND/REMOVE，不能以重进服恢复替代热刷新验证。
4. 无 viewer / JEI / REI 分开测试十种手摆合成、结果 `BlockId/Level`、JEI/REI 查询和枪匠台扣料。
5. 初次进服、重复枪包同步、`/reload`、资源重载、退出重连、跨服和单人退出；对异步完成做连接代次隔离测试。

修复优先级：取消 JEI 伪造事件（26.3 包括失败回退）→ 补齐专服普通配方同步缺口（26.1.2/1.21.11，检查编码安全）→
独立修 REI 非破坏性刷新 → 生命周期收尾。各版本按 Java 21/remap 或 Java 25/unobfuscated 分别适配，不整段盲拷。
