# 真实弹匣 MVP：人工回归清单

> 对应实现：工业化 Phase 1 / 可拆卸实体弹匣
> 前置：Minecraft 26.2、Fabric、TACZ 当前分支、Create Fly（mod id 为 `create`）

## 0. 配置与预期范围

服务端配置：

```toml
IndustryProfile = "CREATE_FLY"
PhysicalMagazines = true
```

- `CREATE_FLY` 但未安装 Create Fly 时，服务器启动会记录明确错误，实体弹匣逻辑不会启用；
- `LEGACY` 时所有枪维持旧的 `GunCurrentAmmoCount` 行为；
- 首期只处理 `industry/gun_feed/*.json` 内标记为 `detachable_magazine` 的枪；
- 管式霰弹枪、转轮、单发、弹链枪暂不进入这个分支。

当前内置 MVP 涵盖 Glock/CZ/M9/M1911/MK23/P320、AK/RPK、AR/STANAG、G36、FAL/G3/M14、P90、MP5/UMP/Uzi/Vector、QBZ 等 24 个默认枪条目。每条定义都已按默认枪包的 `ammo` 与 `ammo_amount` 核对。

## 1. 获取和装填弹匣

1. 在 Create Fly 的 Basin 中执行相应 `create:compacting` 弹匣配方；所有配方仅在 `create` 已加载时由 Fabric resource condition 放行。
2. 将所得空弹匣拿在鼠标上，右键点击匹配的 TACZ 散装子弹堆。
3. 预期：
   - 鼠标上的弹匣增加 `MagazineAmmoCount`；
   - 被点击的散装子弹减少；
   - Tooltip 显示 `装填：当前 / 容量` 与弹种；
   - 不匹配口径的子弹不能装入。
4. 右键点击空槽位。
   - 预期：弹匣吐出一正常堆叠的散装子弹，余弹减少；
   - 重复直到弹匣为空，不能复制或丢失子弹。

## 2. 空仓换弹

以 AK-47、M4A1、Glock 17、P90、RPK 各测一次：

1. 持一把空枪（或把现有枪打空），背包中放入同平台、已装填的弹匣；
2. 按换弹键；
3. 预期：
   - 现有换弹动画仍播放；
   - 动画的 feed 结束点才完成物品交换；
   - 闭膛/栓动枪会从新弹匣转移一发到枪膛；
   - HUD 的数字等于“弹匣余量 + 枪膛一发”；
   - 子弹射击时实际减少的是 `InstalledMagazine` 中的 `MagazineAmmoCount`。

## 3. 战术换弹、回收与退匣

1. 先装入半满弹匣并射几发；背包放一只装填数更高的兼容弹匣；
2. 正常按换弹键；
3. 预期：旧的半满弹匣回到背包，余弹保留，新弹匣进入枪内；
4. 将背包塞满后重复：旧弹匣应掉在玩家脚边，绝不能消失；
5. 按住潜行键再按换弹键：应只退出现有弹匣，不播放换弹动画，不影响膛内一发。

## 4. 存档迁移和模式回退

1. 用旧版本/旧 NBT 的带 `GunCurrentAmmoCount` 枪进入 `CREATE_FLY` 档；
2. 放入一只新弹匣并换弹，或潜行退匣；
3. 预期：原整数余弹被物化成一只对应的弹匣，数量不变；
4. 退出后把 `IndustryProfile` 改为 `LEGACY`，确认枪仍以镜像整数正常射击/换弹；
5. 再改回 `CREATE_FLY`：已插入弹匣的余弹不得回退为旧值。

## 5. 多人与安全性

- 客户端和服务端安装完全相同的 Create Fly 与 TACZ；
- 旁观者在玩家换弹/退匣时不应崩溃；
- 用背包、箱子、掉落物转移半满弹匣后，余弹和口径必须保留；
- 断线重连后检查 `InstalledMagazine`、`MagazineAmmoCount`、`GunCurrentAmmoCount` 镜像一致；
- 用 NBT 编辑器篡改客户端物品不能让服务端增加弹药——所有抽取/退还发生在服务端 `PhysicalMagazineService`。

## 6. 当前明确未完成项

- 生铁、高碳钢、硫粉、底火、弹壳、枪身/枪机/枪管等完整产业链；
- 默认枪械成品配方从直接铁锭转换为平台件装配；
- 管式、转轮、弹链和单发的专属供弹实现；
- 弹匣耐久、故障、装弹器、弹匣袋与手动选择优先级 UI；
- Create Fly 版本矩阵与 JEI/REI 的实机兼容认证。
