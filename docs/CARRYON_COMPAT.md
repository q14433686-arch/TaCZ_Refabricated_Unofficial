# Carry On 搬运兼容说明（Carry On Compatibility）

> 适用：Carry On（Fabric）1.21.11 线 2.9.x。本文记录 TACZ 工作台（`gun_smith_table` /
> `workbench_a` / `workbench_b` / `workbench_c`）与 Carry On 搬运交互的四个已知问题及修复。
> Carry On 行为均以其 1.21.11 分支源码（github.com/Tschipp/CarryOn）实测核实。

## 工作机制（Carry On 侧）

- **拾取**（`PickupHandler.tryPickUpBlock`）：
  - 默认只允许搬运**有方块实体**的方块（`blockEntity == null && !pickupAllBlocks` 时拒绝）；
  - 拒绝一切含「与门相同属性值类型」的方块（`hasPropertyType(state, DoorBlock.HALF)`，
    比较的是 `EnumProperty` 的 **value class**，即 `DoubleBlockHalf.class`）；
  - 搬运 = 保存 `BlockState` + 方块实体 `saveWithId` 的 NBT，然后 `removeBlock`。
- **放置**（`PlacementHandler.tryPlaceBlock`）：`level.setBlockAndUpdate(pos, state)` +
  `setBlockEntity`。**不调用 `setPlacedBy`**。
- **手持渲染**（`CarryRenderHelper.getRenderItemStack`）：构造 `new ItemStack(block)`
  （**无 NBT**），经物品模型管线渲染。方块实体 NBT 通过玩家附件（synced）同步到客户端。

## 四个问题与修复

### 1. 两格工作台放置后只剩一格
`setBlockAndUpdate` 不触发 `setPlacedBy`，HEAD/UPPER 半块永远不会生成。
**修复**：`GunSmithTableBlockB` / `GunSmithTableBlockC` 把第二格补全从 `setPlacedBy`
移到 `onPlace`（自愈补全）。任何 `setBlock` 型放置（搬运放置、/setblock、结构方块）
都会重建完整结构；原版物品放置路径先经 `getStateForPlacement` 校验，行为不变。
第二格位置不可替换时跳过补全，避免覆盖他人方块。

### 2. 配件工作台（workbench_c，1x2x1）无法搬运
`GunSmithTableBlockC` 使用原版 `BlockStateProperties.DOUBLE_BLOCK_HALF`，value class
是 `DoubleBlockHalf.class`，被 Carry On 的 `hasPropertyType` 拒绝。
**修复**：改用自定义枚举 `GunSmithTableBlockC.TableHalf`（值名仍为 `lower/upper`，
属性名仍为 `"half"`）。blockstate JSON 按值名匹配、已保存世界按 `half=lower/upper`
反序列化，均完全兼容；仅 Java 侧类型不同，从而绕过检查。

### 3. 手持搬运模型紫黑（缺失贴图）
`getRenderItemStack` 的无 NBT 物品栈在 `workbench_a/b/c` 上没有 BlockId，
`GunSmithTableItemRenderer` 落入 MissingTexture 占位分支；旧版 `gun_smith_table`
（`DefaultTableItem`，id 硬编码）不受影响——与现象一致。
**修复**：新增客户端 mixin `CarryOnRenderHelperMixin`（字符串 target + 反射，
无编译期依赖）：物品栈缺 BlockId 时，从玩家已同步的 Carry On 数据里反射重建被搬运
方块实体，读出 `BlockId` 补回物品栈。未装 Carry On 时由 `CarryOnCompatMixinPlugin`
跳过（`tacz.carryon.mixins.json`，`required=false`）。

> 26.1.2 / 26.2 的 Carry On（2.10+/2.11+）把 `getRenderItemStack` 的返回值改成
> `ItemStackTemplate`（渲染处再 `.create()`），移植到 26.x 时需改用
> `ItemStackTemplate` 变体，见 `docs/PORT_TO_26x.md`。

### 4. 搬运右半格产生「有碰撞箱的空气」幽灵方块
非 root 半块（HEAD/UPPER）此前也有方块实体（`newBlockEntity` 无条件创建），
Carry On 会把 invisible 的半块当正常方块搬走：放下后该状态不渲染（非 root 不画）
但保留完整碰撞箱，同时原工作台因 `updateShape` 缺另一半而整体消失。
**修复**：`AbstractGunSmithTableBlock#newBlockEntity` 只给 root 部分创建方块实体。
默认配置下 Carry On 拒绝搬运无方块实体的半块（右击回归打开菜单，与放下的工作台
行为一致）。渲染/菜单/掉落本就经 `getRootPos` 读 root 方块实体，不受影响。

> 残留风险：若玩家开启 Carry On 的 `pickupAllBlocks` 配置，仍可手动搬运半块产生幽灵
> 方块——这是该配置的既有语义（连普通无 NBT 方块都可搬），不在修复范围内。

## 黑名单调整

`data/carryon/tags/blocks/block_blacklist.json` 移除五个工作台条目（支持正确搬运），
保留 `tacz:target` 与 `tacz:statue`（非本任务范围，维持原状）。
