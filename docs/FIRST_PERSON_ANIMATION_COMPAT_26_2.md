# 26.2 第一人称身体 / 手部动画 Mod 兼容核对

日期：2026-08-12

## 结论

26.2 Fabric 下的主流第一人称身体、手部、viewmodel 调整 Mod 都可以采用同一条兼容契约：

- 普通物品继续由外部 Mod 渲染和变换；
- 当前主手是拥有实际 Bedrock 模型的 TACZ/LRTactical `AnimateGeoItemRenderer` 时，外部层让出；
- 枪、手雷、刀及其双手继续由 TACZ/LRTactical 的原生第一人称状态机提交；
- 内容包模型不存在时不接管，保留 vanilla/外部 Mod 的回退模型；
- 放下动画物品后立即恢复外部 Mod，不永久修改用户配置。

这里实现的是**按物品自动交还渲染权**，不是把两套手臂骨骼动画叠加。

## 调研范围与实际路径

### First-person Model 2.7.2 + Not Enough Animations 1.12.4

- FPM 2.7.2 有 26.2 Fabric 版；公开
  `FirstPersonAPI.registerPlayerHandler(ActivationHandler)`。
- 通过该公开 API 注册动态 handler：TACZ viewmodel 出现时返回
  `preventFirstperson=true`，避免额外绘制第一人称第三人称身体。
- NEA 的 `PlayerTransformer#renderingFirstPersonArm(boolean)` 本来由
  `ItemInHandRenderer#renderPlayerArm` 包裹；TACZ 直接调用 `AvatarRenderer`，会绕过它。
  现通过其公开字段/方法反射桥接相同 guard，避免第三人称平滑动作污染枪械手部骨骼。
- 不添加硬依赖；目标 Mod 不存在时无类加载。

### Punchy 2.6.2

Punchy 没有面向 Java 调用者的统一“禁用当前物品”公开 API，但它有正式的物品 blacklist
状态路径；2.3 起支持正则 blacklist，2.5 重做 blacklist UI，2.5.8 又专门修过 TACZ
黑名单物品的行走 bob。

当前使用可选 `@Pseudo` mixin 接入这条既有语义，而不是改用户的
`config/punchy/punchy_config.json`：

- `HandEquipStateMachine#wasItemBlacklisted`：TACZ viewmodel 返回 true；
- `PunchyArmRenderer#renderFirstPerson`：TACZ 持有期间不提交第二套手臂；
- `MovementStateMachine`：不叠加 walk/sprint/camera-lag 矩阵；
- `HandRenderBobContext`：不采样/暴露 Punchy walk bob。

目标类或方法在未来版本改名时，`@Pseudo + require=0` 不会让没有 Punchy 的实例崩溃；
更新 Punchy 后仍需按测试矩阵重新核对。

### Hide Hands 4.6、SkyHands、Viewmodel Changer、Swing Animation Plus

这些 Mod 都修改 vanilla `ItemInHandRenderer#submitArmWithItem` 或其内部调用：

- Collective/Hide Hands：在 `submitArmWithItem` HEAD 发取消事件；
- SkyHands：修改 `renderItem`、`renderPlayerArm`、equip/swing transform；
- Viewmodel Changer：直接 `@Overwrite submitArmWithItem`；
- Swing Animation Plus：redirect equip/swing transform；
- Swing Speed 一类只修改 vanilla swing duration。

TACZ 现不再注入被它们共同修改/覆盖的 `submitArmWithItem`，而是在上层
`submitHandsWithItems -> submitArmWithItem` 调用点使用可链式 `@WrapOperation`：

- TACZ viewmodel：直接提交 TACZ renderer，不调用下游方法；
- 普通物品：`original.call(...)`，完整保留上述 Mod 的所有行为。

因此不需要逐个依赖它们的私有 API，也不会全局关闭用户的手部动画设置。

### 不属于 26.2 Fabric 当前目标的项目

- Hold My Items：当前官方发布线没有 26.2 Fabric 文件；
- C.I.M.I.H：官方页面只列 1.21.7 / 1.21.8 / 1.21.11，没有 26.2 Fabric 文件；
- Epic Fight：属于完整战斗/玩家骨架接管，不是当前 26.2 Fabric 的通用手部变换层；
- 资源包/CEM 任意替换玩家模型：没有统一 Mod/API 边界，不能声称泛化兼容。

## 源码核对点

- FirstPersonModel `eef8f91206c9f0ad1681111235c0d802349f986a`；
- NotEnoughAnimations `9c96caf941d065136e055e4b058ac5b828f3f5c6`；
- SkyHands `1ed558cf60208d8c610c48c8d566b9046a4c4258`；
- Viewmodel Changer `2bdb5dc6776efe11b1a874a6db3dee7b670880d2`；
- Hide Hands `1558717ef429978f6051b6c908c421ded26dde06`；
- Collective `ef9d94efdaddaa7a1ef2ab1c27c7e9507c2a65cb`；
- Punchy 本体为 ARR、未公开源码；其 2.6.2 元数据/更新记录、公开配置结构，以及
  Scorched Guns `a93b5417a032e9a61cf169d1d4183b0ded8b232d` 和 Epic Fight Compat
  `4aa4666f336e43922690ac246d911bd869cc0f10` 对 Punchy 2.5.3+ 的实际 target 一并交叉核对。

## 上游对照

直接 Fabric 上游 1.21.1 同样在 SBM `RenderHandEvent` 中取消 vanilla 手持渲染，
由 TACZ 自己提交枪和手；KosmX PlayerAnimator 的本地第一人称事件也明确跳过。
官方 TACZ 仓库仍有 First-person Model 冲突报告，并建议用 FPM 的按物品禁用配置。
所以“外部层在持枪时让出”是上游架构的正确延伸，不是 26.2 特有降级。

## 同时修正的上游语义

主手 TACZ viewmodel 已包含作者绑定的双手。现在主手由 TACZ 接管时会跳过独立 offhand
pass，恢复上游 `FirstPersonRenderEvent` 的行为，避免外部 Mod 再画副手/重复手臂。
普通物品和没有模型的 LRTactical 空壳不受影响。

## 运行测试矩阵

每一项都要先测试普通物品，确认外部 Mod 没被全局禁用；再测试枪、手雷和刀：

1. **FPM + NEA**：普通物品仍有身体；持枪时身体让出，ADS/换弹/射击正常；收枪恢复。
2. **Punchy**：普通工具动画正常；枪/刀/手雷无双手、walk bob、冲刺摆臂和检视层叠；切回工具恢复。
3. **Hide Hands**：即使设置 always hide，TACZ viewmodel 仍完整；普通物品继续遵守 Hide Hands。
4. **SkyHands**：普通物品的 offset/scale/swing 生效；TACZ viewmodel 不吃这些变换。
5. **Viewmodel Changer**：普通物品位置缩放生效；TACZ ADS 和手部定位不偏。
6. **Swing Animation Plus / Swing Speed**：普通武器挥动生效；TACZ 状态机不被 vanilla swing 覆盖。
7. **组合测试**：FPM + NEA + Punchy；Punchy + Viewmodel Changer；Punchy + Hide Hands。
8. **LRTactical**：有内容包时测试 throwable/melee；无内容包时确认仍回退 vanilla 模型。
9. **视角切换**：第一/第三人称快速切换后无残留手臂、身体或动画状态。
10. **副手**：主手持枪时不额外绘制副手；切回普通主手后副手恢复。

本仓库环境没有 JDK；提交前只做静态、JSON、源码和 26.2 字节码核对，必须由 Java 25
环境执行 `./gradlew build`，并按上表进行运行测试。
