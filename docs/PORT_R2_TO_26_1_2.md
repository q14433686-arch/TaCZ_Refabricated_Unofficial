# 将 1.21.11 R2 改动移植到 `26.1.2`

> 日期：2026-08-16
>
> 目标基线：`origin/26.1.2` = `6c409eea0cfe01e070d0ed3c921b63a7a96cb50d`
>
> 源功能基线：`f488a822c92175c4a5930bbf671b3e4d802c7bcb`，再加包含本文的 R2 元数据提交
>
> 目标发布号：`1.1.8+fabric.26.1.2.R2`

本文是 `26.1.2` 的独立执行手册。只把 1.21.11 R2 的功能语义移过去；不要覆盖目标分支已有的
Java 25、非混淆 Loom、渲染、LRTactical、README 或审计说明，也不要整栈 cherry-pick。

## 1. 必须移植的范围与来源

按下面顺序做，每组单独提交并验证：

| 顺序 | 功能 | 1.21.11 来源 |
|---|---|---|
| 1 | 可替换弹药源 API | `28aa9bb` |
| 2 | P0/P1 具名 gameplay hooks | `ce9d4b2` |
| 3 | 已批准的 P2-min：Lua helper 与契约 Javadoc | `729df98`、`c637e9c` |
| 4 | 多格工作台结构与 Carry On 兼容 | `5b149f3`、`166cf67`、`3a02c22`、`3ee41fa` |
| 5 | 内置 JEI/REI Ammo Query | `f488a82` |
| 6 | 枪包同步后的 recipe-viewer 刷新桥 | `4a98325` 基线中的 `RecipeViewerReloadBridge` 及三个调用点 |
| 7 | 目标分支 API 文档、R2 release notes 与版本元数据 | 本文第 8 节 |

固定约束：不新增依赖；不改现有公共类名、包名或 mod id；不改变 dummy/creative/infinite、
FeedType、Bolt、客户端预测和服务端权威逻辑的判断顺序；`INPUT_BOLT` 必须仍是历史值 `"blot"`。

## 2. 动手前必须联网复核

本文记录的是上述 SHA 在 2026-08-16 的快照。实施 Agent 必须先联网做以下检查，并把结果写进
PR；不能只信本文的版本号或旧注释。

1. `git fetch origin`，确认目标头是否仍包含上述目标基线；如有前进，重新比较本节全部目标文件。
2. 查询 JEI、REI 的 Modrinth/Maven metadata 与对应源码/Javadocs。当前 pin 是
   JEI `29.5.0.26`、REI `26.1.819`，但必须确认实施时实际解析的 jar 仍提供目标 API。
3. 查询 Carry On 的 Modrinth/CurseForge 文件，并核对
   <https://github.com/Tschipp/CarryOn/tree/26.1>。快照 HEAD 为
   `e609245b4952e8705ed4e5957e81673e8785b0c9`、源码版本 `2.10.0`；至少复核：
   - `PickupHandler#tryPickUpBlock(ServerPlayer, BlockPos, Level, BiFunction)`；
   - `PlacementHandler#tryPlaceBlock(ServerPlayer, BlockPos, Direction, BiFunction)`；
   - `CarryOnDataManager#getCarryData(Player)` 与
     `CarryOnData#getBlockEntity(BlockPos, HolderLookup.Provider)`；
   - `CarryRenderHelper#getRenderItemStack(Player)` 返回 `ItemStackTemplate`，且
     `CarriedObjectRender` 随后调用 `.create()` 得到真正的 `ItemStack`；
   - 双格禁令仍按 `DoorBlock.HALF` 的 value class 判断；放置仍走 `setBlockAndUpdate` 而不走
     `setPlacedBy`；黑名单仍使用 `carryon:block_blacklist` 数据标签。
4. 在线或从实际依赖 jar 查看 26.1.2 的 `ItemStackTemplate` 组件写入/复制 API。渲染修复必须让
   `.create()` 后的栈带 TACZ `BlockId`；不能只把 1.21.11 mixin 的回调泛型从 `ItemStack`
   机械改名。
5. 复核 JEI/REI 的“运行中重建插件注册”入口。1.21.11 的
   `JeiLifecycleEvents.AFTER_RECIPES_UPDATED` 与
   `RoughlyEnoughItemsCoreClient.reloadPlugins(null, null)` 仅是语义参考，禁止未经 jar/source
   确认直接复制到 26.1.2。

建议记录实际 URL、版本、类名、方法描述符和查询日期。若发布文件与源码分支不一致，以最终测试
所装 jar 为准。

## 3. API、P0/P1 与 P2-min

### 3.1 操作位置

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

- `ModernKineticGunItem.java`、`ModernKineticGunScriptAPI.java`：
  `resolveScriptFunction(...)`，后者另有 `runLuaCycleTask(...)`；
- `LivingEntityAmmoCheck.java`、`LivingEntityShoot.java`、`ModernKineticGunItem.java`：只补契约
  Javadoc，不改算法。

### 3.2 应用方式和不变量

在本基线上，四份 Java patch 已按顺序通过静态 `git apply --check`。仍应按提交逐份生成 Java
patch，不复制 1.21.11 README/changelog：

```bash
git diff 28aa9bb^ 28aa9bb -- src/main/java > /tmp/r2-ammo-api.patch
git diff ce9d4b2^ ce9d4b2 -- src/main/java > /tmp/r2-hooks.patch
git diff 729df98^ 729df98 -- src/main/java > /tmp/r2-p2-lua.patch
git diff c637e9c^ c637e9c -- src/main/java > /tmp/r2-p2-doc.patch
for p in /tmp/r2-ammo-api.patch /tmp/r2-hooks.patch /tmp/r2-p2-lua.patch /tmp/r2-p2-doc.patch; do
  git apply --check "$p" && git apply --index "$p" || exit 1
  git diff --cached --check || exit 1
  # 审查后提交，再继续下一份
 done
```

必须保持：

- provider 按注册顺序首个非 `null` 胜出；无 provider 时回退原 `IItemHandler`；
- `hasAmmo` 只读，`consumeAmmo` 返回值 clamp 到 `0..requestedAmount`，弹药箱耗尽重置 ammo id；
- P0 是 `GunAnimationStateContext#hasAmmoToConsumeInEntity(Entity)`；不要重新暴露 `lambda$...`；
- `LocalPlayerShoot.SHOOT_LOCKED_CONDITION` 仍是同一静态单例，身份判断仍用 `==`；
- 每个 Lua 调用点原有 `orElse`/fallback、参数、调用次数、异常和 cycle 终止语义不变；
- reload/heat fallback 与服务端 charge 校验只补 Javadoc，不放宽安全边界。

复制 `docs/AMMO_SOURCE_API.md` 后，把 Compatibility notice 中的 “1.21.11 changelog” 改成
“26.1.2 changelog/release notes”，并在目标 README 增加链接。

## 4. 工作台结构修复（Carry On 前置）

先移植 `5b149f3` 的三个文件；在所钉基线上该 patch 已通过 `git apply --check`：

- `src/main/java/com/tacz/guns/block/AbstractGunSmithTableBlock.java`
- `src/main/java/com/tacz/guns/block/GunSmithTableBlockB.java`
- `src/main/java/com/tacz/guns/block/GunSmithTableBlockC.java`

目标语义：

1. 只有 root 创建 `GunSmithTableBlockEntity`；companion 不拥有菜单/`BlockId`。
2. 抽象类提供 `getCompanionPos`、`getCompanionState`，通用 `onPlace` 在所有 block-state
   放置路径恢复 companion；B 生成 HEAD，C 生成 UPPER。
3. C 的属性名和值仍为 `half=lower|upper`，但 Java value class 改为本类 `TableHalf`，绕过
   Carry On 2.10 对 vanilla `DoubleBlockHalf` 的通用拒绝；旧世界序列化值不变。
4. `updateShape`、创造破坏、掉落、`isRoot/getRootPos` 必须全部同步使用新枚举。
5. 保留 26.1.2 原有菜单、掉落和映射注释；若目标头前进，按方法合并，不整文件覆盖。

单独验证普通物品放置/破坏/菜单后，再做 Carry On mixin；否则无法区分结构 bug 与兼容 bug。

## 5. Carry On 2.10 正向兼容

### 5.1 文件和加载边界

以 `166cf67`/`3a02c22`/`3ee41fa` 为语义模板，新增或适配：

- `src/main/java/com/tacz/guns/compat/carryon/CarryOnReflection.java`
- `src/main/java/com/tacz/guns/mixin/carryon/CarryOnCompatMixinPlugin.java`
- `CarryOnPickupHandlerMixin.java`
- `CarryOnPlacementHandlerMixin.java`
- `CarryOnRenderHelperMixin.java`
- `src/main/resources/tacz.carryon.mixins.json`
- `src/main/resources/data/carryon/tags/blocks/block_blacklist.json`
- `src/main/resources/fabric.mod.json`

不增加 Carry On 编译依赖。目标 JSON 使用 `JAVA_25`；mixins 保持 `required:false`，plugin 仅在
mod id `carryon` 存在时应用；可选目标使用字符串 target、`@Pseudo`、`remap=false`、
`require=0`。把 `tacz.carryon.mixins.json` 加入 `fabric.mod.json` 的 `mixins` 数组。

### 5.2 三个行为钩子

- **拾取**：在 Carry On 做距离、权限、脚本、NBT 保存和移除之前，把 B HEAD/C UPPER 映射
  到 root。成功搬运应消费交互，不应从右半/上半打开 UI；未按搬运键的普通右键仍可开 UI。
- **放置**：严格镜像 2.10 的目标格与朝向计算；在脚本执行、世界修改或 `carry.clear()` 之前，
  检查 companion 的世界边界、建造高度、`mayInteract`、可替换性与实体碰撞。失败播放同类失败
  音效并返回 `false`，世界不变且玩家继续持有工作台。
- **渲染**：只对缺失身份数据的 `GunSmithTableItem` 补 `BlockId`。从同步的 CarryOnData 重建
  `GunSmithTableBlockEntity`；默认工作台或已有有效 id 的栈不得覆盖。2.10 返回的是
  `ItemStackTemplate`：选择经实际 API 验证的 template 组件复制方案，或在 `.create()` 后、提交
  item model 前修改 `ItemStack`；最终断言应检查真正渲染的栈，而非只检查回调返回类型。

`CarryOnReflection` 的类名/签名以 2.10 实际 jar 为准。反射失败必须安全回退并记录可诊断日志，
不能影响未装 Carry On 的启动。

### 5.3 黑名单

现有标签包含六项。删掉以下四项：

- `tacz:gun_smith_table`
- `tacz:workbench_a`
- `tacz:workbench_b`
- `tacz:workbench_c`

继续保留 `tacz:target` 与 `tacz:statue`。`fabric.mod.json` 当前建议 `carryon >=2.10.0`；只有在
联网与实机确认更高最低版本后才调整。

## 6. 内置 Ammo Query

四个新增 Java 文件和三份语言文件在所钉目标上可直接应用：

- `com/tacz/guns/compat/recipeviewer/AmmoQueryEntry.java`
- `com/tacz/guns/compat/jei/category/AmmoQueryCategory.java`
- `cn/sh1rocu/tacz/compat/rei/category/AmmoQueryCategory.java`
- `cn/sh1rocu/tacz/compat/rei/display/AmmoQueryDisplay.java`
- `assets/tacz_ammo_query/lang/en_us.json`
- `assets/tacz_ammo_query/lang/zh_cn.json`
- `assets/tacz_ammo_query/lang/zh_tw.json`

不要把键单独写进 `assets/tacz/lang/*.json`，否则构建资源 bundle 可能覆盖完整语言文件。共享 entry
必须保持：按枪 `sort` 后 id 排序；按弹药 `sort` 后 id 排序；每种被至少一把枪使用的弹药一条；
前 60 把固定显示，其余作为 overflow 轮换组；JEI/REI 使用同一数据源。

两个注册文件需按目标现有 Attachment Query API 形状手工合并：

- `com/tacz/guns/compat/jei/GunModPlugin.java`：注册 category，并在 `registerRecipes` 加入
  `AmmoQueryEntry.getAllAmmoQueryEntries()`；
- `cn/sh1rocu/tacz/compat/rei/REIClientPlugin.java`：增加 `AMMO_QUERY` identifier，注册 category
  与 displays。

不要覆盖这两个文件中 26.1.2 已有的坏配方隔离和工作台注册逻辑。若在线核对发现 JEI/REI
签名变化，以目标的 Attachment Query 为模板重写适配层，不改变共享 entry 语义。

## 7. 枪包同步后的 viewer 刷新桥

只注册 category/display 不够：远程服务端枪包 cache 可能晚于 JEI/REI 首轮注册到达。新增
`src/main/java/com/tacz/guns/client/compat/RecipeViewerReloadBridge.java`，但按第 2 节核实的 26.1.2
viewer API 重写反射入口和注释。

完整调用点：

1. `ServerMessageSyncGunPack#handle` 用 `Minecraft.execute(...)` 把 cache/index/viewer 操作串行到
   客户端线程；`doSync` 顺序固定为 cache 安装 → `ClientIndexManager.reload()` →
   `RecipeViewerReloadBridge.requestReload()`。
2. `cn/sh1rocu/tacz/client/TaCZFabricClient#subscribeEvents` 把
   `RecipeViewerReloadBridge::tick` 注册到 `END_CLIENT_TICK`。
3. `CommonNetworkCacheEvent#onClientPlayerLoggingIn` 在现有 memory-connection early return **之后**
   调用 `RecipeViewerReloadBridge.clear()`；不要借此改变现有缓存生命周期。

桥必须合并重复请求、防重入、等待 `client.level/player`，仅刷新已安装的 viewer。轻量入口失败时
一次性回退 `client.reloadResourcePacks()`，异步完成/异常都复位状态；两个 viewer 同装时全部尝试，
但最多触发一次 fallback。未安装 JEI/REI 时不做任何事。

## 8. 目标 R2 元数据与文档

手工更新，不复制 1.21.11 文档：

- `gradle.properties`：`mod_version=1.1.8+fabric.26.1.2.R2`，并把附近仅描述当前发布身份的 R1
  注释改为 R2；必须保留 `+` build metadata，禁止写成 `1.1.8-R2`。
- `fabric.mod.json`：name/description 改为 26.1.2 R2，说明稳定弹药/gameplay hooks、Carry On
  工作台兼容、内置 JEI/REI Ammo Query；保留原免责声明和 `1.1.8` SemVer 核心。
- `README.md`：只改“当前源码版本”、当前依赖表、当前 build metadata，并增加 API/功能链接；
  历史 R1 描述保留或写成“自 R1 起”。
- 新建 26.1.2 R2 release notes/changelog；不要覆盖 `docs/UPDATE_REPORT_26_1_2_R1.md`。
- `docs/AMMO_SOURCE_API.md` 的 Compatibility notice 改为 26.1.2 语境。
- 可复制 `docs/CARRYON_COMPAT.md` 的验收矩阵，但版本/API 描述必须改为经核实的 2.10 行为。

## 9. 验收

### 9.1 静态与编译

```bash
git diff --check 6c409eea0cfe01e070d0ed3c921b63a7a96cb50d...HEAD
python3 -m json.tool src/main/resources/fabric.mod.json >/dev/null
python3 -m json.tool src/main/resources/tacz.carryon.mixins.json >/dev/null
for f in src/main/resources/assets/tacz_ammo_query/lang/*.json; do python3 -m json.tool "$f" >/dev/null; done
rg -n 'AmmoSourceRegistry\.(hasAmmo|consumeAmmo)|hasAmmoToConsumeInEntity' src/main/java/com/tacz/guns
rg -n 'resolveScriptFunction|runLuaCycleTask' src/main/java/com/tacz/guns/item
rg -n 'AmmoQuery|RecipeViewerReloadBridge' src/main/java
rg -n 'ConfigLoaderMixin|addForbiddenTiles' src/main/java || true
grep -F 'INPUT_BOLT = "blot"' src/main/java/com/tacz/guns/client/animation/statemachine/GunAnimationConstant.java
```

用 JDK 25 执行：

```bash
java -version
./gradlew compileJava --no-daemon
./gradlew test --no-daemon       # 仅在目标已有可执行测试时
./gradlew build --no-daemon
```

再分别做“未装 Carry On/JEI/REI”、只装 JEI、只装 REI、Carry On 2.10 的启动检查。工具链缺失、
依赖下载失败或 OOM 不能写成编译通过。

### 9.2 游戏内必测

- 弹药源：无 provider fallback、首个非 null、只读查询、部分/足量消费、越界 clamp、弹药箱耗尽；
  dummy、creative、infinite、fuel/magazine、三种 Bolt 不回归。
- 动作：单发/连发/蓄力、dry fire、开火/换弹/取消/战术换弹/拉栓及对应动画和音效。
- 工作台：默认及枪包 A/B/C 从任一半搬起；放下结构、朝向、菜单、模型、`BlockId` 完整；
  `pickupAllBlocks=true` 也不产生幽灵格；companion 被方块、实体、权限、高度/边界阻挡时原子失败且
  搬运数据不丢；普通放置/破坏不回归。
- Ammo Query：JEI、REI 分别能由弹药查枪；第三方枪包、排序、超过 60 把 overflow、语言均正确。
- 远程同步：viewer 先完成注册、随后收到服务端枪包时会刷新；连续同步合并；断线清除 pending；
  轻量 hook 人为失效时只 fallback 一次且无 reload loop。

PR 描述必须列出来源提交、目标实际基线、联网核对结果、手工冲突、编译命令和每项实机结果；
未执行项明确标注，不得以另一个 Minecraft 分支的结果代替。
