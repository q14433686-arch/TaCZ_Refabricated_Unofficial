# 将 1.21.11 R2 改动移植到 `26.2(main)`

> 日期：2026-08-16
>
> 目标基线：`origin/26.2(main)` = `99b472a6a8e1438f22a29abe8b3804b349cb5dfd`
>
> 源功能基线：`f488a822c92175c4a5930bbf671b3e4d802c7bcb`，再加包含本文的 R2 元数据提交
>
> 目标发布号：`1.1.8+fabric.26.2.R2`

本文是 `26.2(main)` 的独立执行手册。只移植 1.21.11 R2 的功能语义；不要覆盖目标分支已有的
Java 25、非混淆 Loom、26.2 渲染链、调试探针、LRTactical、README 或审计记录，也不要整栈
cherry-pick。

## 1. 必须移植的范围与来源

按下列顺序做，每组单独提交并验证：

| 顺序 | 功能 | 1.21.11 来源 |
|---|---|---|
| 1 | 可替换弹药源 API | `28aa9bb` |
| 2 | P0/P1 具名 gameplay hooks | `ce9d4b2` |
| 3 | 已批准的 P2-min：Lua helper 与契约 Javadoc | `729df98`、`c637e9c` |
| 4 | 多格工作台结构与 Carry On 兼容 | `5b149f3`、`166cf67`、`3a02c22`、`3ee41fa` |
| 5 | 内置 JEI/REI Ammo Query | `f488a82` |
| 6 | 枪包同步后的 recipe-viewer 刷新桥 | `4a98325` 基线中的 `RecipeViewerReloadBridge` 及三个调用点 |
| 7 | 目标分支 API 文档、R2 release notes 与版本元数据 | 本文第 8 节 |

固定约束：不新增依赖；不改公共类名、包名或 mod id；不改变 dummy/creative/infinite、FeedType、
Bolt、客户端预测和服务端权威逻辑的判断顺序；`INPUT_BOLT` 必须保持历史值 `"blot"`。

## 2. 动手前必须联网复核

本文记录的是上述 SHA 在 2026-08-16 的快照。实施 Agent 必须联网完成下列检查，并把 URL、版本、
类名/描述符和日期写进 PR：

1. `git fetch origin`，确认目标头仍包含上述目标基线；如果分支前进，重新比较本手册全部目标文件。
2. 查询 JEI、REI 的 Modrinth/Maven metadata 及对应源码/Javadocs。当前 pin 是
   JEI `30.13.0.86`、REI `26.2.820`；`build.gradle` 里提到 JEI `.80` 的注释已经与属性不一致，
   不能当作事实。
3. 查询 Carry On 的 Modrinth/CurseForge 文件并核对
   <https://github.com/Tschipp/CarryOn/tree/26.2>。快照源码 HEAD 为
   `e50ddbc1c7461f381c62af5f4960db9d97751d16`、版本 `2.11.1`，而核查时公开 Fabric 文件仍为
   2.11.0；必须以最终实测 jar 为准。至少复核：
   - `PickupHandler#tryPickUpBlock(ServerPlayer, BlockPos, Level, BiFunction)`；
   - `PlacementHandler#tryPlaceBlock(ServerPlayer, BlockPos, Direction, BiFunction)`；
   - `CarryOnDataManager#getCarryData(Player)` 与
     `CarryOnData#getBlockEntity(BlockPos, HolderLookup.Provider)`；
   - `CarryRenderHelper#getRenderItemStack(Player)` 返回 `ItemStackTemplate`，且
     `CarriedObjectRender` 随后调用 `.create()`；
   - 双格拒绝仍检查 `DoorBlock.HALF` 的 value class；放置仍走 `setBlockAndUpdate` 而不调用
     `setPlacedBy`；黑名单仍是 `carryon:block_blacklist` 数据标签。
4. 在线或从实际依赖 jar 查看 26.2 的 `ItemStackTemplate` 组件复制/写入 API。最终必须保证
   `.create()` 后的真实 `ItemStack` 带 TACZ `BlockId`，不能只机械替换 mixin 回调泛型。
5. 核对 JEI/REI 的运行中插件重建入口。1.21.11 使用的
   `JeiLifecycleEvents.AFTER_RECIPES_UPDATED` 和
   `RoughlyEnoughItemsCoreClient.reloadPlugins(null, null)` 只代表刷新语义；未经 30.13/26.2
   实际 jar 或源码验证，禁止照抄反射类名、字段和参数。

## 3. API、P0/P1 与 P2-min

### 3.1 文件范围

新增：

- `src/main/java/com/tacz/guns/api/item/ammo/AmmoSource.java`
- `src/main/java/com/tacz/guns/api/item/ammo/AmmoSourceProvider.java`
- `src/main/java/com/tacz/guns/api/item/ammo/AmmoSourceRegistry.java`
- `docs/AMMO_SOURCE_API.md`

修改弹药调用路径：

- `api/item/gun/AbstractGunItem.java`
- `client/animation/statemachine/GunAnimationStateContext.java`
- `entity/shooter/LivingEntityShoot.java`
- `item/ModernKineticGunScriptAPI.java`

P0/P1 另修改：

- `client/event/InventoryEvent.java`
- `client/gameplay/LocalPlayerBolt.java`
- `client/gameplay/LocalPlayerReload.java`
- `client/gameplay/LocalPlayerShoot.java`
- `entity/shooter/LivingEntityBolt.java`
- `entity/shooter/LivingEntityReload.java`
- `item/ModernKineticGunItem.java`

P2-min 只涉及：

- `ModernKineticGunItem.java`、`ModernKineticGunScriptAPI.java`：提取
  `resolveScriptFunction(...)`，后者另有 `runLuaCycleTask(...)`；
- `LivingEntityAmmoCheck.java`、`LivingEntityShoot.java`、`ModernKineticGunItem.java`：只补契约
  Javadoc，不改算法。

### 3.2 应用与 26.2 冲突

先按提交生成只含 Java 的 patch；禁止复制 1.21.11 README/changelog：

```bash
git diff 28aa9bb^ 28aa9bb -- src/main/java > /tmp/r2-ammo-api.patch
git diff ce9d4b2^ ce9d4b2 -- src/main/java > /tmp/r2-hooks.patch
git diff 729df98^ 729df98 -- src/main/java > /tmp/r2-p2-lua.patch
git diff c637e9c^ c637e9c -- src/main/java > /tmp/r2-p2-doc.patch
```

在所钉基线上，弹药 API patch 可直接应用。P0/P1 的已知冲突是
`client/gameplay/LocalPlayerShoot.java`：保留 26.2 的 scheduler/主线程说明，并让提交到
Minecraft 事件循环的任务调用具名 `applyClientFireEffects(...)`；不得把动画、声音或事件移回
scheduler 线程。

必须同时保留 `LocalPlayerReload.java` 中受 `RenderConfig.RECOIL_DEBUG` 门禁的
`TACZ Case08 RELOAD_START` 探针，不能用 1.21.11 整文件覆盖。建议：

```bash
git apply --check /tmp/r2-ammo-api.patch
git apply --index /tmp/r2-ammo-api.patch
# 审查、提交 API 后：
git apply --3way --index /tmp/r2-hooks.patch || true
git diff --name-only --diff-filter=U
# 应只有 LocalPlayerShoot；若更多文件冲突，停止并重新比较目标头。
```

解决并提交 P0/P1 后，再按方法合并 P2-min。`ModernKineticGunScriptAPI`、
`LivingEntityShoot`、`ModernKineticGunItem` 带 26.2 专属 Lua ABI/dormant progression/Javadoc
说明；保留这些说明并只加入 helper/Javadoc，不能以整文件替换处理 patch context 冲突。

必须保持：

- provider 首个非 `null` 胜出，无 provider 回退原 `IItemHandler`；`hasAmmo` 只读；
  `consumeAmmo` clamp 到 `0..requestedAmount`，弹药箱耗尽重置 ammo id；
- P0 是 `GunAnimationStateContext#hasAmmoToConsumeInEntity(Entity)`，不暴露 `lambda$...`；
- `LocalPlayerShoot.SHOOT_LOCKED_CONDITION` 仍是同一个静态单例，身份判断仍用 `==`；
- Lua 调用点原有 `orElse`/fallback、参数、次数、异常与 cycle true/false 终止语义不变；
- reload/heat fallback 和服务端 charge 校验只补 Javadoc，不放宽 finite/阈值/最大进度/抖动边界。

复制 `docs/AMMO_SOURCE_API.md` 后，把 Compatibility notice 改为 26.2 changelog/release notes，并
在目标 README 增加链接。

## 4. 工作台结构修复（Carry On 前置）

先按方法移植 `5b149f3` 的三个文件；所钉基线上的 Java patch 已通过 `git apply --check`：

- `src/main/java/com/tacz/guns/block/AbstractGunSmithTableBlock.java`
- `src/main/java/com/tacz/guns/block/GunSmithTableBlockB.java`
- `src/main/java/com/tacz/guns/block/GunSmithTableBlockC.java`

目标语义：

1. 只有 root 创建 `GunSmithTableBlockEntity`；companion 不拥有菜单或 `BlockId`。
2. 抽象类提供 `getCompanionPos/getCompanionState`；通用 `onPlace` 在所有 block-state 放置路径
   恢复 companion；B 生成 HEAD，C 生成 UPPER。
3. C 保持序列化 `half=lower|upper`，但 Java value class 改为本类 `TableHalf`，绕过 Carry On
   2.11 对 vanilla `DoubleBlockHalf` 的通用拒绝；旧世界保存值不变。
4. `updateShape`、创造破坏、掉落、`isRoot/getRootPos` 全部同步使用新枚举。
5. `AbstractGunSmithTableBlock` 在 26.2 还有菜单/掉落说明，必须保留；不要复制 1.21.11 整文件。

先单独验证普通物品放置、破坏、菜单与枪包 `BlockId`，再做 Carry On mixin。

## 5. Carry On 2.11 正向兼容

### 5.1 先清理失效旧实现

目标树仍有：

- `src/main/java/cn/sh1rocu/tacz/mixin/compat/carryon/ConfigLoaderMixin.java`
- `src/main/java/com/tacz/guns/compat/carryon/BlackList.java`

但 `build.gradle` 明确 exclude 这两个包；它们依赖旧 ConfigLoader/ListHandler API，既未编译也不是
有效兼容。不要重新启用。应删除/替换这两个失效源码，并移除专为“Carry On 无 26.2 构建”设置的
两条 source-set exclude。新的兼容层只能用字符串 mixin target、反射和数据标签，不新增编译依赖。

同时更新旧审计文档中“26.2 无 Carry On”的历史结论，或至少加醒目标识说明已被 R2 兼容取代；
不要静默保留互相矛盾的现状说明。

### 5.2 新文件与加载边界

以 `166cf67`/`3a02c22`/`3ee41fa` 为语义模板，新增或适配：

- `src/main/java/com/tacz/guns/compat/carryon/CarryOnReflection.java`
- `src/main/java/com/tacz/guns/mixin/carryon/CarryOnCompatMixinPlugin.java`
- `CarryOnPickupHandlerMixin.java`
- `CarryOnPlacementHandlerMixin.java`
- `CarryOnRenderHelperMixin.java`
- `src/main/resources/tacz.carryon.mixins.json`
- `src/main/resources/data/carryon/tags/blocks/block_blacklist.json`
- `src/main/resources/fabric.mod.json`

目标 mixin JSON 使用 `JAVA_25`；保持 `required:false`，plugin 仅在 mod id `carryon` 存在时应用；
可选目标使用字符串 target、`@Pseudo`、`remap=false`、`require=0`。把该 JSON 加入
`fabric.mod.json` 的 `mixins` 数组。

### 5.3 三个行为钩子

- **拾取**：在 Carry On 距离、权限、脚本、NBT 保存和移除之前，将 B HEAD/C UPPER 映射到
  root。成功搬运不应打开 UI；未按搬运键的普通右键仍可从任一半开 UI。
- **放置**：严格镜像最终 2.11 jar 的目标格/朝向计算；在执行脚本、修改世界或清空数据之前，
  检查 companion 的世界边界、建造高度、交互权限、可替换性与实体碰撞。失败返回 `false`、播放
  同类失败音效，世界不变且搬运数据仍在。
- **渲染**：只给缺失身份的 `GunSmithTableItem` 补 `BlockId`。由 CarryOnData 重建
  `GunSmithTableBlockEntity`；默认工作台和已有有效 id 不覆盖。2.11 返回
  `ItemStackTemplate`，必须采用经实际 API 验证的 template 组件复制方案，或在 `.create()` 后、
  item model 提交前修改真实 `ItemStack`；不能保留 1.21.11 的 `CallbackInfoReturnable<ItemStack>`。

反射解析失败应安全回退并给出可诊断日志，不能让未装 Carry On 的客户端或服务端加载相关类。

### 5.4 数据标签与建议版本

新增 `data/carryon/tags/blocks/block_blacklist.json`，只保留：

- `tacz:target`
- `tacz:statue`

四种工作台不得在 blacklist 中。给 `fabric.mod.json` 增加 `suggests.carryon`，最低版本填写最终
实测公开 jar（预计 `>=2.11.0`，但若实现依赖仅在 2.11.1 出现则必须写 `>=2.11.1`，不得猜测）。

## 6. 内置 Ammo Query

以下新增文件在所钉目标上可直接应用：

- `com/tacz/guns/compat/recipeviewer/AmmoQueryEntry.java`
- `com/tacz/guns/compat/jei/category/AmmoQueryCategory.java`
- `cn/sh1rocu/tacz/compat/rei/category/AmmoQueryCategory.java`
- `cn/sh1rocu/tacz/compat/rei/display/AmmoQueryDisplay.java`
- `assets/tacz_ammo_query/lang/en_us.json`
- `assets/tacz_ammo_query/lang/zh_cn.json`
- `assets/tacz_ammo_query/lang/zh_tw.json`

不要向 `assets/tacz/lang/*.json` 只写少量新增键，以免覆盖资源 bundle 的完整语言文件。共享 entry
必须保持：枪械与弹药分别按 `sort` 后 id 排序；每种被至少一把枪使用的弹药一条；前 60 把固定
显示，其余作为 overflow 轮换组；JEI/REI 数据完全一致。

按 26.2 已有 Attachment Query 的 API 形状手工修改：

- `com/tacz/guns/compat/jei/GunModPlugin.java`：注册 Ammo category，并在 `registerRecipes` 加入
  `AmmoQueryEntry.getAllAmmoQueryEntries()`；
- `cn/sh1rocu/tacz/compat/rei/REIClientPlugin.java`：增加 `AMMO_QUERY` identifier，注册 category
  和 displays。

不要覆盖目标插件中的坏配方隔离、工作台或 26.2 专属注释。若在线核对发现 API 变化，重写
viewer 适配层而不改变共享 entry 语义。

## 7. 枪包同步后的 viewer 刷新桥

只注册 category/display 不够：远程枪包 cache 可能在 JEI/REI 首轮注册后到达。新增
`src/main/java/com/tacz/guns/client/compat/RecipeViewerReloadBridge.java`，但反射入口和注释必须按
第 2 节核实的 26.2 API 重写。

完整调用点：

1. `ServerMessageSyncGunPack#handle` 用 `Minecraft.execute(...)` 把 cache/index/viewer 操作放到
   客户端线程；`doSync` 顺序固定为 cache 安装 → `ClientIndexManager.reload()` →
   `RecipeViewerReloadBridge.requestReload()`。
2. `cn/sh1rocu/tacz/client/TaCZFabricClient#subscribeEvents` 在 `END_CLIENT_TICK` 注册
   `RecipeViewerReloadBridge::tick`。
3. `CommonNetworkCacheEvent#onClientPlayerLoggingIn` 保留现有长篇取证 Javadoc和
   memory-connection early return，只在 early return **之后**增加
   `RecipeViewerReloadBridge.clear()`；禁止借 R2 顺手改变缓存生命周期。

桥必须合并请求、防重入、等待 `client.level/player`，仅刷新已安装 viewer。轻量入口失败时最多
一次回退 `client.reloadResourcePacks()`；异步完成和异常都复位状态。JEI/REI 同装时两者都尝试，
但只触发一次 fallback；未安装 viewer 时无动作，不得形成 resource reload loop。

## 8. 目标 R2 元数据与文档

手工更新，不复制 1.21.11 文档：

- `gradle.properties`：`mod_version=1.1.8+fabric.26.2.R2`；把附近仅描述当前发布身份的 R1 注释
  改为 R2。必须保留 `+` build metadata，禁止 `1.1.8-R2`。
- `fabric.mod.json`：name/description 改为 26.2 R2，说明稳定弹药/gameplay hooks、Carry On
  工作台兼容和内置 JEI/REI Ammo Query；保留免责声明与 `1.1.8` SemVer 核心；按实测添加
  Carry On suggest。
- `README.md`：只改当前源码版本、当前依赖表和当前 build metadata，增加 API/功能链接；历史
  R1 说明保留或改成“自 R1 起”。
- 新建 26.2 R2 release notes/changelog；不要把 `docs/archive` 中历史审计当当前 release notes。
- `docs/AMMO_SOURCE_API.md` 的 Compatibility notice 改为 26.2 语境。
- 可复制 `docs/CARRYON_COMPAT.md` 的验收矩阵，但所有 2.9.2/1.21.11 描述都必须改成经核实的
  2.11/26.2 行为。

## 9. 验收

### 9.1 静态与编译

```bash
git diff --check 99b472a6a8e1438f22a29abe8b3804b349cb5dfd...HEAD
python3 -m json.tool src/main/resources/fabric.mod.json >/dev/null
python3 -m json.tool src/main/resources/tacz.carryon.mixins.json >/dev/null
for f in src/main/resources/assets/tacz_ammo_query/lang/*.json; do python3 -m json.tool "$f" >/dev/null; done
rg -n 'AmmoSourceRegistry\.(hasAmmo|consumeAmmo)|hasAmmoToConsumeInEntity' src/main/java/com/tacz/guns
rg -n 'resolveScriptFunction|runLuaCycleTask' src/main/java/com/tacz/guns/item
rg -n 'AmmoQuery|RecipeViewerReloadBridge' src/main/java
rg -n 'RenderConfig\.RECOIL_DEBUG|TACZ Case08|RELOAD_START' \
  src/main/java/com/tacz/guns/client/gameplay/LocalPlayerReload.java
test ! -e src/main/java/cn/sh1rocu/tacz/mixin/compat/carryon/ConfigLoaderMixin.java
test ! -e src/main/java/com/tacz/guns/compat/carryon/BlackList.java
grep -F 'INPUT_BOLT = "blot"' src/main/java/com/tacz/guns/client/animation/statemachine/GunAnimationConstant.java
```

人工检查 `build.gradle`：不得残留两条 Carry On 包 exclude，也不得新增 Carry On compile/runtime
依赖。然后用 JDK 25：

```bash
java -version
./gradlew compileJava --no-daemon
./gradlew test --no-daemon       # 仅当目标已有可执行测试
./gradlew build --no-daemon
```

分别检查未装 Carry On/JEI/REI、只装 JEI、只装 REI、最终选定 Carry On 2.11.x 的启动。
工具链缺失、下载失败或 OOM 不能写成编译通过。

### 9.2 游戏内必测

- 弹药源：无 provider fallback、首个非 null、只读查询、部分/足量消费、越界 clamp、弹药箱耗尽；
  dummy、creative、infinite、fuel/magazine 与三种 Bolt 不回归。
- 动作：单发/连发/蓄力、dry fire、开火/换弹/取消/战术换弹/拉栓和动画音效；开启
  `RECOIL_DEBUG` 仍打印 Case08，关闭后不增加日志。
- 工作台：默认及枪包 A/B/C 可从任一半搬起；放下结构、朝向、菜单、模型、`BlockId` 完整；
  `pickupAllBlocks=true` 不产生幽灵格；companion 被方块、实体、权限、高度/边界阻挡时原子失败且
  搬运数据不丢；普通放置/破坏不回归。
- Ammo Query：JEI 与 REI 分别能由弹药查枪；第三方枪包、排序、超过 60 把 overflow 和语言正确。
- 远程同步：viewer 首轮注册后收到服务端枪包会刷新；连续同步合并；断线清 pending；轻量 hook
  人为失效时只 fallback 一次且无 reload loop。

PR 描述必须列来源提交、目标实际基线、联网核对结果、删除的失效 Carry On 路径、手工冲突、
编译命令和实机矩阵；未执行项明确标注，不得用另一个 Minecraft 分支的结果代替。
