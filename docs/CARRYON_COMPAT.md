# Carry On 2.9.2 工作台搬运兼容

> 适用范围：Minecraft 1.21.11、Carry On Fabric 2.9.2。本修复只进入 1.21.11 线；
> `26.1.2` 与 `26.2(main)` 不在本轮修改范围。

## 核验基线

Carry On 官方 `1.21.11` 分支（核验提交
`c65c63fbe1f6a089fc5afb1fc38d469f9f36262c`）的 `gradle.properties` 标明
`version=2.9.2`、`minecraft_version=1.21.11`。本兼容层按该版本的以下行为实现：

- `PickupHandler.tryPickUpBlock` 保存当前格的完整 `BlockState` 和方块实体 NBT，随后直接
  删除当前方块实体与当前格；
- 同一方法通过属性 **value class** 与 `DoorBlock.HALF` 比较，拒绝全部使用
  `DoubleBlockHalf` 的方块；
- `PlacementHandler.tryPlaceBlock` 调用 `setBlockAndUpdate` 并恢复方块实体，但不调用方块的
  `setPlacedBy`；
- `CarryRenderHelper.getRenderItemStack(Player)` 默认返回由方块构造、没有 TACZ `BlockId`
  的临时 `ItemStack`；
- `CarryOnDataManager.getCarryData(Player)` 返回同步的搬运数据，`CarryOnData#getBlockEntity`
  可以从其中保存的 tile NBT 重建 TACZ 工作台方块实体。

官方 TACZ `1.20.1` 的 `GunSmithTableBlockC` 同样使用
`BlockStateProperties.DOUBLE_BLOCK_HALF`。因此配件工作台无法拾取是 Carry On 2.9.2 的
通用双格禁令与 TACZ 既有设计发生冲突，并非本 Fabric 移植错误。

## 四项修复

### 1. 双格工作台放下后只剩 root

`AbstractGunSmithTableBlock#onPlace` 现在负责恢复多格工作台的 companion，而不再依赖
只有物品放置流程才会调用的 `setPlacedBy`。`GunSmithTableBlockB` 提供横向 HEAD 状态，
`GunSmithTableBlockC` 提供上方 UPPER 状态。Carry On 放置 root 时会同步恢复完整结构；
root 方块实体随后仍由 Carry On 从原 NBT 重建，因此枪包自定义 `BlockId` 不丢失。

普通物品放置仍先经过原有 `getStateForPlacement` 空间检查；菜单、掉落和方块实体渲染仍
从 root 读取身份数据。

### 2. 配件工作台无法拾取

`GunSmithTableBlockC` 的 `half` 属性改用本类的 `TableHalf` 枚举。属性名仍为 `half`，
序列化值仍为 `lower` / `upper`，所以已有世界中的 blockstate 数据和值名不变；Java value
class 不再是 `DoubleBlockHalf`，从而不会被 Carry On 2.9.2 的门类属性检查误拒绝。

`target` 与 `statue` 不属于本任务，继续保留原版 `DoubleBlockHalf` 和 Carry On 黑名单。

### 3. 枪包工作台手持模型显示紫黑缺失贴图

客户端 `CarryOnRenderHelperMixin` 在 Carry On 生成临时渲染栈后，仅对缺少身份数据的
`GunSmithTableItem` 生效。它通过无编译期依赖的反射桥读取客户端已同步的
`CarryOnData`，重建 `GunSmithTableBlockEntity`，再把原 `BlockId` 写回渲染栈。

默认 `gun_smith_table` 的 `DefaultTableItem` 身份是硬编码值，本来就不缺失；已有有效
`BlockId` 的渲染栈也不会被覆盖。

### 4. 拾取非 root 半格产生幽灵方块

修复包含两层边界：

1. 非 root 半格不再创建自己的 `GunSmithTableBlockEntity`；只有 root 保存菜单与
   `BlockId`；
2. `CarryOnPickupHandlerMixin` 在拾取入口把 HEAD/UPPER 映射到所属 root，再让 Carry On
   对 root 执行原有的距离、配置、权限回调、NBT 保存和移除流程。

第二层是必须的：Carry On 的 `pickupAllBlocks=true` 会绕过“必须有方块实体”的默认
限制。入口映射确保从任一半格发起搬运都会拾取完整工作台，而不会搬走 companion、让原
结构掉落并生成有碰撞但不可见的幽灵格。按下 Carry On 搬运键时成功拾取会消费本次交互，
因此不会意外打开菜单；未按搬运键的普通右键仍按原行为从任一半格打开菜单。

## 原子放置边界

`CarryOnPlacementHandlerMixin` 在 Carry On 执行放置脚本、修改世界或清空搬运数据之前，
按 Carry On 相同的目标格与朝向规则计算 companion 位置，并检查：

- 世界边界和建造高度；
- 玩家对 companion 格的交互权限；
- companion 当前方块可替换；
- companion 状态没有实体碰撞阻挡。

任一检查失败都会返回 `false` 并播放 Carry On 使用的失败音效；世界保持不变、玩家继续
持有原工作台，可换位置重试。这样不会出现只放下一格后清空搬运数据的半成功状态。

## 可选加载与黑名单

`tacz.carryon.mixins.json` 为 `required=false`，并由
`CarryOnCompatMixinPlugin` 在 `carryon` 未安装时跳过全部兼容 mixin。兼容代码使用字符串
mixin target 和反射，不新增依赖库，也不会把 Carry On 打入发行包。

支持完成后，`carryon:block_blacklist` 只保留任务外的：

- `tacz:target`
- `tacz:statue`

`fabric.mod.json` 的建议版本同步改为当前 1.21.11 实际版本 `carryon >=2.9.2`。

## 外部运行验证矩阵

当前开发容器没有 JDK，以下项目必须在 Java 21 游戏环境完成：

| 场景 | 预期结果 |
|---|---|
| 搬运/放下 `gun_smith_table`、`workbench_a` | 单格状态、菜单和模型正常 |
| 搬运/放下 `workbench_b` | FOOT/HEAD 两格完整，朝向正确 |
| 搬运/放下 `workbench_c` | 可以拾取，LOWER/UPPER 两格完整 |
| 搬运附属枪包的 A/B/C 工作台 | 手持模型不紫黑，放下后 `BlockId`、菜单和模型保持 |
| 从 B 的 HEAD 或 C 的 UPPER 发起搬运 | 默认配置及 `pickupAllBlocks=true` 均转为拾取 root；完整结构被搬起，不打开菜单、不生成幽灵格 |
| companion 被实体方块、权限或高度边界阻挡 | 放置失败，世界无半结构，玩家仍持有工作台 |
| 生存/创造破坏 root 与非 root | 掉落规则、创造抑制和自定义 `BlockId` 保持原行为 |
| 未安装 Carry On 启动客户端与服务端 | 可选 mixin 被跳过，TACZ 正常加载 |
