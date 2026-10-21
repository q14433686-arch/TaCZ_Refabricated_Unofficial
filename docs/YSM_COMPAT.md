# YSM（Yes Steve Model）适配说明

适配对象：[`hongshaoluobotou/ModernYSM`](https://github.com/hongshaoluobotou/ModernYSM) 的
`port/26.3` 分支（Fabric，mod id **`yes_steve_model`**，MIT）。本页记录 **TaCZ 这一侧**做了什么、
YSM 那一侧复原时必须对齐的契约，以及哪些结论**还没有实机验证**。

> 结论先行：TaCZ 侧提供的门面是 `com.tacz.guns.compat.ysm.YsmCompat`（9 个 molang 变量读取 +
> 第一人称渲染归属握手 + 枪口火光/抛壳 `isSelf` 成对开关）。
> **YSM 侧目前还没有任何代码调用它** —— ModernYSM 的 `port/26.3` 把整个 `rip/ysm/compat/**`
> 排除出编译、只留 `compat-stubs` 空实现（`TacCompat.isLoaded()` 恒 `false`），
> 所以现在装在一起是「YSM 模型 + TaCZ 枪，但没有任何持枪/开火动画」。
> 真正恢复动画需要 YSM 那边把 compat 补回来；本文给出它要调的每一个入口。

## 1. 现状：两边各自是什么状态（2026-10-21 核对）

| 事项 | 状态 |
|---|---|
| YSM 26.3 分支 | `port/26.3` 在移植中；模型渲染、动画驱动、第一人称手臂接管已重接（其 `AGENTS.md` 记录） |
| YSM 的 TaCZ 兼容 | **未恢复**：`rip/ysm/compat/**` 被 build.gradle 排除，`compat-stubs` 里 `TacCompat` 全是 `return false/null` |
| TaCZ 侧对 YSM 的感知 | 本次新增 `YsmCompat` 与 `YsmCompat` 配置项；此前**完全没有** |
| 两侧联调 | **没有**。本文的所有结论都是源码/契约层面的，没有一起进游戏跑过 |

## 2. YSM 1.20.1 的实现依赖了 TaCZ 的哪些东西

来源：ModernYSM `1.20.1-forge` 分支
`forge/src/main/java/com/elfmcys/yesstevemodel/client/compat/gun/tacz/`
（`TacCompat` / `TacBinding` / `TacAnimHandler` / `event/GunFireReloadEvent`）。
逐项核对过本仓 `26.3` 源码，**全部仍在**，见 `scripts/verify_ysm_compat.py`：

| YSM 用到的 TaCZ 符号 | 本仓状态 | 备注 |
|---|---|---|
| `IGun.getIGunOrNull(ItemStack)` | ✅ | 可空 |
| `IGun.mainhandHoldGun(LivingEntity)` | ✅（`@Deprecated`） | 26.x 新增非弃用的 `mainHandHoldGun`，旧名保留 |
| `IGun.getMainHandFireMode(LivingEntity)` | ✅ | 旧名 `getMainhandFireMode` 同样保留 |
| `IGun#getGunId(ItemStack)` → `Identifier` | ✅ | 26.x 是 `Identifier`（即 1.20.1 的 `ResourceLocation`） |
| `IGunOperator.fromLivingEntity` + `getSynShootCoolDown/getSynMeleeCoolDown/getSynDrawCoolDown/getSynReloadState().getCountDown()/getSynAimingProgress()` | ✅ | **无检查强制转型**，对没挂 TaCZ mixin 的实体抛 `ClassCastException`（见 §3） |
| `TimelessAPI.getCommonGunIndex(Identifier)` → `CommonGunIndex#getType()` | ✅ | 返回值即 `GunTabType` 的小写名 |
| `GunTabType.PISTOL/RIFLE/RPG/...` | ✅ | YSM 用 `name().toLowerCase()` 匹配枪包动画前缀 |
| `IClientPlayerGunOperator.getClientShootCoolDown()` | ✅ | 本地玩家连发动画要用它补网络延迟 |
| `MuzzleFlashRender.isSelf` / `ShellRender.isSelf` | ✅ public static | YSM 在自绘 pass 里置位 |
| `GunFireEvent` / `GunMeleeEvent` / `GunReloadEvent` | ✅ 但**语义不同** | 见 §2.1 |
| 枪械物品能被 `ItemRenderer#renderStatic(stack, FIXED, ...)` 画出来 | ✅ | 见 §2.2 |

### 2.1 事件：Forge `@SubscribeEvent` → Fabric `Event<Callback>`

YSM 1.20.1 的 `GunFireReloadEvent` 是 Forge 事件总线订阅；本仓这些事件是
Fabric API 的 `Event<Callback>`，注册方式完全不同：

```java
// 1.20.1 Forge（YSM 旧代码）
@SubscribeEvent public void onGunFire(GunFireEvent event) { ... }

// 26.3 Fabric（要改成这样）
GunFireEvent.CALLBACK.register(event -> { ... });
GunMeleeEvent.CALLBACK.register(event -> { ... });
GunReloadEvent.CALLBACK.register(event -> { ... });
```

- `GunFireEvent#getShooter()`、`GunReloadEvent#getEntity()`、`GunMeleeEvent#getShooter()` 签名与 1.20.1 一致。
- 回调没有 `Event` 泛型里的 `@SubscribeEvent` 注解可依托，**必须自己判空**并在客户端/服务端两侧
  都考虑（本仓在这两侧都会 post，`BaseEvent` 上是 `LogicalSide`）。
- 这些回调在**渲染线程也可能被触发**（客户端本地开火路径），不要在里面做重活。

### 2.2 第三人称/第一人称「把枪画在你的骨骼定位点上」

YSM 的做法是 `itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, NO_OVERLAY, pose, bufferSource, level, seed)`。
本仓枪械走的是自定义 `ItemModel`（`tacz:dynamic_item` → `TaczDynamicItemModel`），
`update()` 会拿到当前 `ItemDisplayContext` 并 `setupSpecialModel`，`submit()` 再把
`displayContext` 原样交给 `AnimateGeoItemRenderer#render`。也就是说 **`renderStatic` 这条路是通的**，
`FIXED` 会按枪包 `display` JSON 的第三人称定档绘制，不需要额外注册 BEWLR。

注意 26.3 的 `renderStatic` 在 `SubmitNodeCollector` 体系下是「提交」而非立即绘制，
YSM 侧若需要与自己的模型同帧同 collector，应把 `submit` 放进同一个 collector 的作用域。

## 3. TaCZ 侧新增：`com.tacz.guns.compat.ysm.YsmCompat`

一个**只读状态 + 渲染归属握手**的门面，全部方法对空值/异常安全，永不抛：

| 方法 | 对应 molang 变量 | 语义（对齐 YSM 1.20.1） |
|---|---|---|
| `isHoldingGun(LivingEntity)` | `tac_hold_gun` | 主手是 `IGun` |
| `getGunType(LivingEntity)` / `getGunType(ItemStack)` | `tac_gun_type` | `CommonGunIndex#getType()`，未知返回 `""` |
| `getGunId(LivingEntity)` / `getGunId(ItemStack)` | `tac_gun_id` | `tacz:ak47` 形式 |
| `isFiring(LivingEntity)` | `tac_is_fire` | `getSynShootCoolDown() > 0`，本地玩家再与 `getClientShootCoolDown()` 取大 |
| `isAiming(LivingEntity)` | `tac_is_aim` | `getSynAimingProgress() > 0` |
| `isReloading(LivingEntity)` | `tac_is_reload` | `getSynReloadState().getCountDown() > 0` |
| `isMelee(LivingEntity)` | `tac_is_melee` | `getSynMeleeCoolDown() > 0` |
| `isDrawing(LivingEntity)` | `tac_is_draw` | `getSynDrawCoolDown() > 0` |
| `getFireMode(LivingEntity)` | `tac_fire_mode` | `AUTO/SEMI/BURST`，`UNKNOWN` 返回 `""` |
| `beginSelfFlashPass()` / `endSelfFlashPass()` | — | 成对设置/清除两个 `isSelf` |
| `isGunItem(ItemStack)` | — | 供 YSM 过滤 `renderStatic` |
| `setExternalGunRender(boolean)` / `isExternalGunRenderActive()` | — | 第一人称归属握手，见 §4 |

比 YSM 旧代码多做的两件事：

1. **不再用无检查转型**。`IGunOperator.fromLivingEntity` 是 `(IGunOperator) entity`，遇到没挂
   TaCZ mixin 的 `LivingEntity`（部分模组的 NPC/假玩家）会抛 `ClassCastException`；
   `YsmCompat` 内部改成 `entity instanceof IGunOperator`，不满足就返回中性值并**只记一次 WARN**。
2. **异常收敛**。枪包索引查询失败（枪包刚重载、id 已失效）时返回 `""`/`false`，
   不把异常抛进 YSM 的动画求值循环。

配置项：`RenderConfig.YSM_COMPAT` → `tacz-client.toml` 的 **`YsmCompat`**，默认 `true`。
它只控制 §4 的握手是否生效；§3 的读状态门面不受开关影响（关掉不会让 YSM 的 molang 报错）。

## 4. 第一人称归属握手（避免双份枪）

场景：YSM 接管了 `FirstPersonHandsAndItemsRenderer#renderPlayerHand` 画自己的手臂/模型，
同时在模型的 `tac_pistol` / `tac_rifle` 定位点上画枪。若它**没有**取消掉 vanilla 的
`submitArmWithItem`，TaCZ 的视模也会照常提交 → 同一帧两把枪。

握手协议（**可选**，没人调用时 TaCZ 行为与本次改动前完全一致）：

```java
// YSM 侧：在这一帧由自己绘制枪之前/之后
YsmCompat.setExternalGunRender(true);
try {
    // ... 自己的手臂/模型/枪的提交 ...
} finally {
    YsmCompat.setExternalGunRender(false);
}
```

TaCZ 侧的消费点：`FirstPersonHandsAndItemsRendererMixin#tacz$submitArmWithAnimatedItem`
在「确认是本方视模、且不是副手、且不是 Iris 半透明遍」之后、真正调用
`geoRenderer.renderFirstPerson(...)` 之前，检查 `YsmCompat.isExternalGunRenderActive()`，
命中就 `return`（不提交）。

三层保险：

- 默认 `false`：没有 YSM 调用时零行为变化（不是「默认关掉某功能」）。
- `isExternalGunRenderActive()` 还要求 `isEnabled()`（装了 YSM 且配置开）。
- 标志在**每个客户端 tick 结束**由 `TaCZFabricClient` 复位，YSM 漏掉 `finally` 最多多抑制一帧视模。

## 5. YSM 侧复原时需要做的事（给 ModernYSM 的迁移清单）

1. 把 `rip/ysm/compat/gun/tacz/**` 从 build 排除里移出，`compat-stubs` 版本换成真实现。
2. `TacCompat.isLoaded()` 改成 `FabricLoader.getInstance().isModLoaded("tacz")`（本仓 Fabric 的
   mod id **仍是 `tacz`**，见 `fabric.mod.json`）。
3. `@ExpectPlatform` 全部按它自己的 AGENTS.md 惯例改成 `XxxImpl` 委托 —— 与 TaCZ 无关。
4. `TacBinding` 的 9 个变量改为调用 `YsmCompat`（逐个替换，语义见 §3 表）。
5. `GunFireReloadEvent` 的 Forge 订阅改成 `...CALLBACK.register(...)`（§2.1）。
6. `TacAnimHandler.handleTaczSound` 里的两行 `isSelf = true` 换成 `beginSelfFlashPass()/endSelfFlashPass()`（可选）。
7. 若第一人称会与 TaCZ 视模重复，按 §4 加握手。
8. **不要**依赖 `lambda$...` 之类的合成名（本仓 AGENTS.md 第 3 条同样适用），只用上表列出的公开 API。

TaCZ 侧已经为「26.3 版 YSM 照着 1.20.1 老代码抄」准备了兼容名：
`mainhandHoldGun` / `getMainhandFireMode` 两个旧名都还在（标 `@Deprecated`），
`Identifier` 就是 1.20.1 的 `ResourceLocation`。

## 6. 验证

```bash
python3 scripts/verify_ysm_compat.py     # 11 项
```

覆盖：

- §2 表里每一个 TaCZ 侧符号仍存在（含旧名、`isSelf` 字段、事件的 `Event<Callback>` 形态）；
- `TaczDynamicItemModel` 确实把 `ItemDisplayContext` 透传到 `AnimateGeoItemRenderer`；
- `YsmCompat` 暴露全部 9 个变量名、不出现无检查转型、异常收敛；
- 握手是可选、每 tick 失效，且守卫位于「普通物品放行之后、geo 提交之前」；
- 配置项存在且默认 `true`。

**这些只是源码/契约检查。** 没有编译、没有 Mixin 应用测试、没有进游戏。

## 7. 待实机验收的矩阵（没有任何一项跑过）

| # | 场景 | 判据 |
|---|---|---|
| 1 | 只装 YSM（无 TaCZ） | 模型/动画不受本次改动影响（我们没改 YSM） |
| 2 | 只装 TaCZ（无 YSM） | 第一人称/第三人称枪行为与改动前逐帧一致（握手默认 false） |
| 3 | 两者都装、YSM 不调用握手 | 跟改动前一样：YSM 模型 + TaCZ 视模枪，可能出现两把枪 → 属 YSM 侧待接 |
| 4 | 两者都装、YSM 调用握手 | 第一人称只有一把枪；切换第三人称/开镜/换弹时不闪不双 |
| 5 | 第三人称：`renderStatic(FIXED)` 画出的枪 | 贴手、朝向正确、无 z-fighting、不随镜头转 |
| 6 | molang：持枪/开火/瞄准/换弹/近战/切枪/开火模式 | 与 1.20.1 YSM 同名变量行为一致（逐动画对照） |
| 7 | 多人：别人的 YSM 模型持枪 | 第三人称枪随模型骨骼，枪口火光/抛壳不错误地「只画自己」 |
| 8 | 断开 YSM 后重进 | `YsmCompat` 不残留握手状态（tick 复位） |

## 8. 边界（不要当成已修复）

- **YSM 侧没恢复 compat，就没有枪械动画。** 本文不构成「YSM 已适配完成」的声明。
- 没有在 26.3 上跑过任何一帧。§4 的握手消费点是 `WrapOperation` 内部的一次早退，
  它依赖「本方视模分支」先被命中；若 YSM 取消了 `submitArmWithItem`，这个分支根本不会执行，
  此时不需要握手（也没有双枪问题）。
- 没验证 Iris 光影下 YSM 自绘 pass 与 TaCZ 掩码/管线是否互相干扰。
- 没验证 YSM 的 `FirstPersonHandsAndItemsRendererMixin` 与本仓同名 mixin 的注入顺序
  （两者都挂 `renderPlayerHand`/`submitArmWithItem` 附近；谁先谁后决定「取消」是否吃掉我们的视模）。
  这条是**最可能出问题的地方**，验收时优先看。
