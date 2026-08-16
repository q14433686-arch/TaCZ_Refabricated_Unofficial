# Carry On 2.11 工作台搬运兼容（Minecraft 26.2）

> 适用范围：Minecraft **26.2**、Fabric、Carry On **2.11.x**。这是 TaCZ Refabricated
> 26.2 R2 的可选兼容层；它不把 Carry On 打进发行包，也不为其增加编译或运行时依赖。

## 2026-08-16 核验记录

- [Carry On 的 26.2 源分支](https://github.com/Tschipp/CarryOn/tree/26.2) HEAD 是
  [`e50ddbc1c7461f381c62af5f4960db9d97751d16`](https://github.com/Tschipp/CarryOn/commit/e50ddbc1c7461f381c62af5f4960db9d97751d16)，
  `gradle.properties` 为 `version=2.11.1`、`java_version=25`；该提交的 API 源码用于描述符核验。
- [Modrinth 公开 Fabric 26.2 文件](https://api.modrinth.com/v2/project/joEfVgkn/version/EzG8eAml)
  是 **2.11.0**，发布日期 2026-08-02，文件名
  `carryon-fabric-26.2-2.11.0.jar`，SHA-1
  `fc9daa0278cf8fa12dbe59e56b54554596bc0b5a`。因此 `fabric.mod.json` 建议版本是
  `carryon >=2.11.0`，而不是源码树的尚未公开 2.11.1。
- 已按 26.2 源码核对下列签名和调用顺序：
  - `PickupHandler#tryPickUpBlock(ServerPlayer, BlockPos, Level, BiFunction<BlockState, BlockPos, Boolean>)`；
  - `PlacementHandler#tryPlaceBlock(ServerPlayer, BlockPos, Direction, BiFunction<BlockPos, BlockState, Boolean>)`；
  - `CarryOnDataManager#getCarryData(Player)` 与
    `CarryOnData#getBlockEntity(BlockPos, HolderLookup.Provider)`；
  - `CarryRenderHelper#getRenderItemStack(Player)` 返回 `ItemStackTemplate`，而
    `CarriedObjectRender#drawBlock` 随后立即调用 `.create()`；
  - 双格拒绝使用 `hasPropertyType(state, DoorBlock.HALF)`，放置使用
    `level.setBlockAndUpdate(pos, state)`、不调用 `setPlacedBy`，黑名单为
    `carryon:block_blacklist`。

`ItemStackTemplate` 是 26.2 的不可变组件模板；本兼容层不猜测其组件补丁的内部写法。
它在 `CarriedObjectRender#drawBlock` 调用 `.create()` 时重定向到真实的 `ItemStack`，并在
提交 item model 前写入 TACZ 的 `BlockId`。这样不会只改错回调泛型，也不会覆盖已经存在的有效
身份数据。

## 多格工作台的结构语义

`AbstractGunSmithTableBlock` 只为 root 创建 `GunSmithTableBlockEntity`；菜单和枪包
`BlockId` 只属于 root。B 工作台的 companion 是 HEAD，C 工作台的 companion 是 UPPER。
抽象基类的 `onPlace` 负责在所有 block-state 放置路径恢复 companion，因此 Carry On 的
`setBlockAndUpdate` 路径不依赖不会调用的 `setPlacedBy`。

C 的状态仍序列化为 `half=lower|upper`，但 Java value class 改为本地 `TableHalf`，从而避开
Carry On 对 vanilla `DoubleBlockHalf`（`DoorBlock.HALF`）的通用拒绝。已有世界的状态键和值
保持不变；`updateShape`、创造破坏、掉落、`isRoot` 和 `getRootPos` 均使用这个枚举。

## 搬运钩子

### 拾取

`CarryOnPickupHandlerMixin` 在 `tryPickUpBlock` 的参数入口把 B 的 HEAD 和 C 的 UPPER 映射为
root。之后 Carry On 自己继续执行距离、权限、脚本、NBT 保存、移除和音效流程。此顺序确保：

- `pickupAllBlocks=true` 也不会搬走无形 companion 或产生幽灵格；
- 按下搬运键的成功交互会被 Carry On 正常消费，不会打开菜单；
- 没有按搬运键的普通右键依然从任一半格通过 TaCZ 的 root 菜单路径打开 UI。

### 放置

`CarryOnPlacementHandlerMixin` 严格复制 2.11 `tryPlaceBlock` 的目标根格计算：先以输入格建立
`BlockPlaceContext`，目标不可替换时向 `facing` 平移一格，再使用同样的上下文推导工作台状态。
在 Carry On 执行脚本、改动世界或清空 CarryOnData 之前，兼容层检查 companion 的世界边界、
建造高度、交互权限、可替换性和实体碰撞。

任何 companion 检查失败都会返回 `false`、播放 Carry On 同类的 `LAVA_POP` 失败音效；世界没有
半结构，玩家的搬运数据也没有被清空。root 本身仍由 Carry On 原有的 `canSurvive`、权限、替换和
碰撞检查处理。

### 渲染

`CarryOnRenderHelperMixin` 只处理缺少身份的 `GunSmithTableItem`。它经反射从已同步
`CarryOnData` 重建 `GunSmithTableBlockEntity`，并仅在 `BlockId` 是空值且重建结果提供了有效 id
时写回真实渲染栈。默认工作台及已有有效 id 都不会被覆盖。

反射的类名/描述符未解析或调用失败时会安全回退；解析失败会写入一次可诊断日志，不会使未安装
Carry On 的客户端或服务端加载 Carry On 类。

## 可选加载、标签和历史代码

`tacz.carryon.mixins.json` 使用 `required:false`、`JAVA_25`、字符串 target、`@Pseudo`、
`remap=false` 与 `require=0`；其 plugin 只有检测到 mod id `carryon` 才应用 mixin。

旧的 `ConfigLoaderMixin` / `BlackList` 属于 1.21.x 的 `ConfigLoader` / `ListHandler` API，曾被
source-set exclude，因此既没有编译也没有实际兼容效果。R2 删除它们并移除两条专属 exclude；
新层只使用反射、字符串 mixin target 和数据标签。

`data/carryon/tags/blocks/block_blacklist.json` 只保留任务外的：

- `tacz:target`
- `tacz:statue`

四种工作台不在黑名单内。

## 验收矩阵

| 场景 | 预期结果 |
| --- | --- |
| 普通放置/破坏 `gun_smith_table`、A/B/C | 原有掉落、菜单、模型和 root `BlockId` 正常 |
| 从 B 的 FOOT/HEAD 或 C 的 LOWER/UPPER 拾取 | 全部解析为 root，完整结构被搬起且不打开菜单/不产生幽灵格 |
| 放下 B/C | HEAD/UPPER 被恢复，朝向、菜单、模型和 `BlockId` 保持 |
| 放下枪包 A/B/C | 手持及放置模型不紫黑，`BlockId` 完整 |
| companion 被方块、实体、权限、高度或边界阻挡 | 原子失败、同类失败音效、世界不变、CarryOnData 保留 |
| `pickupAllBlocks=true` | 不会搬走 companion，仍从任一半格搬起 root |
| 未装 Carry On | client/server 均跳过可选 mixin，TACZ 正常启动 |

需要在最终的 2.11.0 实际 jar 游戏矩阵中执行这些项目；源码描述符核验不替代运行测试。
