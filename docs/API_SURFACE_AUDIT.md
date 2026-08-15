# API 表面稳定性审计报告（API Surface Stability Audit）

> 范围：`com.tacz.guns` 核心逻辑类（`client.animation` / `entity.shooter` / `item` / `api.item.gun` / `api.entity` / `api.item`）
> 背景：下游项目 **Touhou Little Maid: Tsumugi** 通过 Mixin 注入弹药来源（`#46`），暴露了合成 lambda 名漂移导致的静默失效问题。
> 状态：**P0 / P1 已完成**（改动清单见文末「附：已完成改动」），P2 与「正式 extension point」为后续建议。

---

## 第一部分：扫描列表

### 类别 1 — 匿名内部类 / lambda（含业务逻辑，可能被下游 Mixin 绑定）

| # | 位置 | 代码片段 | 为什么脆弱 | 建议 |
|---|------|----------|-----------|------|
| 1.1 | `GunAnimationStateContext#hasBulletInBarrel()` L83 | `processGunData((iGun, gunIndex) -> { Bolt boltType = gunData.getBolt(); return boltType != Bolt.OPEN_BOLT && iGun.hasBulletInBarrel(currentGunItem); })` | 编译为 `lambda$hasBulletInBarrel$N`；判断「膛内是否有子弹」是表现层决策，下游若要改动画判断会绑合成名 | 提取 `protected boolean hasBulletInBarrel(IGun, GunData)` 具名方法 |
| 1.2 | `GunAnimationStateContext#getShootInterval()` L104 | `processCameraEntity(entity -> { if (entity instanceof LivingEntity livingEntity) { FireMode fireMode = ...; if (fireMode == BURST) return ...; return gunData.getShootInterval(...); } return 0L; })` | 同上；射击间隔/连发判定逻辑 | 提取 `protected long resolveShootInterval(LivingEntity, FireMode)` |
| 1.3 | `GunAnimationStateContext#getReloadStateType()` L261 | `processCameraEntity(entity -> { if (...) return ...getSynReloadState().getStateType().ordinal(); ... })` | 换弹状态机判定 | 提取具名方法 |
| 1.4 | `GunAnimationStateContext#shouldSlide()` L348 | `processCameraEntity(e -> e.isCrouching() && gunData.canSlide())` | 姿态判定（较低风险） | 提取具名方法 |
| 1.5 | `GunAnimationStateContext#anchorWalkDist()` L355 / `getWalkDist()` L456 | `processCameraEntity(entity -> { if (...) walkDistAnchor = tacz$walkDistance(...); ... })` | 行走距离插值（已有大量注释，含历史 bug），是动画正确性关键 | 提取具名方法 |
| 1.6 | `GunAnimationStateContext#getMaxCharge()` L540 / `getChargeThreshold()` L551 | `processGunData((iGun, gunIndex) -> { var chargeData = ...; ... })` | 蓄力配置读取 | 提取具名方法 |
| 1.7 | `LocalPlayerShoot#doShoot()`（约 L246–L301） | `scheduleAtFixedRate(() -> { ...过热检查/死亡检查/发包/触发动画与声音... }, delay, period, MILLISECONDS)` | **大段连发射击逻辑**整体塞进异步 lambda；跑在非主线程，还嵌套 `tacz$submitAsync`。下游若要 hook 客户端开火几乎只能绑这个合成名 | 提取 `private void onBurstTick(...)` / `private void fireOnce(...)` |
| 1.8 | `ModernKineticGunScriptAPI#shootOnce()`（约 L146–L200） | `CycleTaskHelper.addCycleTask(() -> { ...GunFireEvent / reduceAmmoOnce / handleShootHeat / 生成子弹 / 播放声音... }, period, cycles)` | 服务端连发射击逻辑整体在一个 lambda 内 | 提取 `private boolean runShootCycle()` |
| 1.9 | `LocalPlayerShoot` L37 | `private static final Predicate<IGunOperator> SHOOT_LOCKED_CONDITION = operator -> operator.getSynShootCoolDown() > 0;` | 已具名静态字段，但它同时是状态锁的<b>身份令牌</b>（`lockState` 用引用比较识别），语义不透明 | ✅ 已提取 `public static boolean isShootLocked(IGunOperator)` 并文档化「勿替换字段、改判定请覆写具名方法」的引用比较约定 |
| 1.10 | `ModernKineticGunItem` 多个方法（`startBolt/tickBolt/shoot/startReload/tickReload/interruptReload/tickHeat/doBulletSpread/modifyProperty`） | `Optional.ofNullable(gunIndex.getScript()).map(script -> checkFunction(script.get("...")))...` | Lua 派发胶水，非决策逻辑；下游一般不需要碰（会走脚本），风险低 | P2 可提 `invokeScript(String)` helper |

### 类别 2 — 未暴露、但承担「决策职责」的私有方法

| # | 位置 | 说明 | 脆弱性 | 建议 |
|---|------|------|--------|------|
| 2.1 | `ModernKineticGunItem#defaultReloadFinishing(api, isTactical)`（private） | **真正的「从哪里扣弹药、补多少进弹匣」决策**：`switch(reloadData.getType())` 分 MAGAZINE/FUEL/INVENTORY，各自 `consumeAmmoFromPlayer(needAmmoCount)` + `putAmmoInMagazine(...)`，末尾还有膛内补弹 | 下游想「把弹药来源从玩家背包换成女仆背包」最想覆写的入口，原本 `private` 且与 Lua 脚本默认逻辑绑定，无法干净覆写 | ✅ 已提取 `protected void consumeAmmoForReload(api, FeedType, needAmmoCount, needConsumeAmmo)`；FUEL 与 MAGAZINE/INVENTORY 的消耗语义<b>有意不同</b>，分支保留、不合并 |
| 2.2 | `ModernKineticGunItem#defaultTickBolt(api)`（private） | 拉栓喂弹：`consumeAmmoFromPlayer(1)` 后 `setAmmoInBarrel(true)` | 同 2.1，弹药来源决策 | ✅ 已提取 `protected void feedChamber(ModernKineticGunScriptAPI api)`；背包直读路径经 `extractAmmoFromSource`，弹匣路径语义不同、保留独立分支 |
| 2.3 | `LivingEntityShoot#isChargeProgressReasonable / getMaxReasonableChargeProgress / validateChargeProgress`（private） | 蓄力进度服务端校验（防作弊） | 决策逻辑，且是安全边界；改动可能被下游误绑 | 至少文档化；暂不强求改 |
| 2.4 | `LivingEntityAmmoCheck#needCheckAmmo() / consumesAmmoOrNot()` | 「是否需要检查/消耗弹药」的核心决策（创造模式分支） | 类位于 `entity.shooter`（内部包），但逻辑通过 `IGunOperator` 暴露；属于**半暴露**状态 | P2：文档化或迁到 `api` 包作为正式能力 |

### 类别 3 — 同一逻辑分散多处、无统一入口

| # | 逻辑 | 出现位置 | 危害 |
|---|------|----------|------|
| 3.1 | **「能否开火 / 弹药可用性」五连判定**（`useInventoryAmmo` + `hasAmmoInBarrel` + `hasInventoryAmmo` + `ammoCount` + `noAmmo`） | ① `LivingEntityShoot.shoot()` L112–L121（服务端）② `LocalPlayerShoot.preCheck()` L210–L219（客户端）③ `LivingEntityBolt.bolt()` L57–L64（服务端拉栓）④ `LocalPlayerBolt.bolt()` L54–L61（客户端拉栓）⑤ `ModernKineticGunScriptAPI#reduceAmmoOnce()`（脚本射击） | **最高危**。多处几乎逐行复制；下游替换弹药来源必须全部改到且保持一致性，否则客户端/服务端/拉栓/脚本行为分叉 |
| 3.2 | 弹药扫描循环（`IAmmo` / `IAmmoBox` instanceof 判断） | 上轮已提取到 `AbstractGunItem#hasAmmoInInventory` 并复用（`canReload` / `hasInventoryAmmo` / `ModernKineticGunScriptAPI#hasAmmoToConsume` / `GunAnimationStateContext#hasAmmoToConsumeInEntity`）。仍残留：`AbstractGunItem#findAndExtractInventoryAmmo`（扣除变体，本应不同）、`GunHudOverlay` L239–L242、`GunSmithTableScreen` L229/L244 | 扣除变体是「读 + 改」，与纯读变体职责不同，需保留；HUD/合成台是显示层，风险低 |
| 3.3 | 「-5ms 冷却窗口」魔法数 | 服务端 6 处：`LivingEntityShoot.getShootCoolDown` ×2、`LivingEntityDrawGun.getDrawCoolDown`、`LivingEntityMelee`、`ModernKineticGunScriptAPI.getShootInterval` ×2；客户端 `LocalPlayerShoot.getCoolDown`（无 -5，与服务器不一致） | 同一「冷却窗口」语义在多处各自硬编码，未来统一调整时容易漏改 | ✅ 服务端 6 处收敛为 `ShooterDataHolder.LATENCY_WINDOW_MS`；客户端「无窗口」是<b>既有语义差异</b>，只加注释说明、**没有**强行合并进同一 API，也没有给客户端硬加 -5ms |
| 3.4 | `consumeAmmoFromPlayer` 两个签名 | `LivingEntityShoot#consumeAmmoFromPlayer(int, ItemStack, boolean)`（void）与 `ModernKineticGunScriptAPI#consumeAmmoFromPlayer(int)`（int） | 下游 issue 表格里**两处都列了**，必须双 mixin；内部都走 `findAndExtractInventoryAmmo` / `findAndExtractDummyAmmo`，但入口不统一 |
| 3.5 | 换弹门槛序列（client/server 各一份且已漂移） | `LocalPlayerReload#reload`：`useInventoryAmmo → clientStateLock → 射击后100ms → canReload → 事件 → 发包`；`LivingEntityReload#reload`：`useInventoryAmmo → isReloading → 射击冷却 → 切枪冷却 → isBolting → canReload → 事件 → 发包` | 两套门槛不一致；下游只 mixin 一侧会得到分叉行为 | ✅ 各自提取为 `protected performReload(...)`（客户端另有 `performCancelReload`），门槛差异在 javadoc 里互相注明；**有意保持两条独立 API，不合并** |
| 3.6 | 拉栓动作本体双份 | `LocalPlayerBolt#bolt` / `LivingEntityBolt#bolt`（弹药判定已统一，动作本体仍双份） | 下游 hook 拉栓需要双 mixin | ✅ 各自提取为 `protected performBolt(...)`，差异（服务端有冷却检查、客户端靠状态锁）文档化，保持独立 API |

---

## 第二部分：分级

### P0 — 下游已在用，必须先处理（✅ 上轮已完成）

- `GunAnimationStateContext#hasAmmoToConsume` 内部的合成 lambda `lambda$hasAmmoToConsume$8`
  - 已提取为 `private boolean hasAmmoToConsumeInEntity(Entity)`，并新增 `AbstractGunItem#hasAmmoInInventory(IItemHandler, ItemStack)` 作为稳定扫描入口。
  - 状态：**完成，无需再动**。

### P1 — 同类高风险，大概率下游也在用（✅ 全部完成）

1. **类别 3.1「noAmmo 五连判定」多处重复**（最高优先级）。✅ → `AmmoAvailability` + `AbstractGunItem#checkAmmoAvailability`（方案 A）。
2. **类别 2.1/2.2「弹药来源扣减入口」**（`defaultReloadFinishing` / `defaultTickBolt` / 两个 `consumeAmmoFromPlayer`）。✅ → `consumeAmmoForReload` / `feedChamber`（换弹/拉栓来源）+ `extractAmmoFromSource`（扣减入口，方案 B）。
3. **类别 1.7/1.8 连发射击大 lambda**（`LocalPlayerShoot#doShoot`、`ModernKineticGunScriptAPI#shootOnce`）。✅ → `runBurstTick` / `fireOnce` / `runShootCycle`（方案 C）。
4. **类别 1.1–1.6 `GunAnimationStateContext` 其余业务 lambda**。✅ → 全部具名化（方案 D，见 P1④ 表）。
5. **类别 3.3 + 客户端冷却语义差异**。✅ → `ShooterDataHolder.LATENCY_WINDOW_MS`；客户端无窗口差异只文档化、不合并。
6. **类别 3.5/3.6 换弹/拉栓门槛与动作本体**。✅ → client/server 各提取 `performReload` / `performCancelReload` / `performBolt`，保持两条独立 API 并在 javadoc 互注差异。
7. **类别 1.9 状态锁身份令牌**。✅ → `LocalPlayerShoot#isShootLocked` + 引用比较约定文档化。

### P2 — 低风险但顺手该改

- ~~类别 3.3「-5ms 冷却窗口」魔法数 → 提常量/方法。~~ ✅ 已随 P1⑤ 完成。
- 类别 2.4 `LivingEntityAmmoCheck` → 文档化或迁 `api`（文档化已完成）。
- 类别 1.10 Lua 派发胶水 → 提 `invokeScript` helper。
- 类别 3.2 的 HUD/合成台扫描循环 → 复用 helper（注意 client 侧）。

---

## 第三部分：重构方案（仅 P0/P1，且不改变行为）

> 原则：纯提取/提方法，**不改任何判断顺序与返回值**；不新增依赖；不改类名/包名/mod id；每一处可独立验证（改完单个方法，换弹/开火/动画仍正常）。

### 方案 A：统一「弹药可用性」判定（针对 3.1，多处去重）

新增一个不可变判定结果值对象（放在 `api.entity` 或 `entity.shooter`，不破坏既有类名）：

```java
// 语义稳定、纯数据，便于下游 hook 后返回一致结果
public final class AmmoAvailability {
    public final boolean useInventoryAmmo;
    public final boolean hasAmmoInBarrel;
    public final boolean hasInventoryAmmo;
    public final int ammoCount;
    public boolean isNoAmmo() { return useInventoryAmmo && !hasInventoryAmmo || !useInventoryAmmo && ammoCount < 1; }
}
```

在 `AbstractGunItem`（common 侧，服务端/客户端都能用）加一个具名入口：

```java
/** 稳定的「是否有可射击弹药」判定入口，供下游覆写/替换弹药来源。 */
public AmmoAvailability checkAmmoAvailability(LivingEntity shooter, ItemStack gun) {
    // 逐行等价于 LivingEntityShoot L112-121 / LocalPlayerShoot L210-219 /
    //              LivingEntityBolt L57-64 / LocalPlayerBolt L54-61
}
```

然后把各调用点替换为该方法的调用（保持外层 `if (noAmmo)` / `if (noAmmo) return` 等控制流不变）。
- 服务端 `LivingEntityShoot` / `LivingEntityBolt`：直接调用。
- 客户端 `LocalPlayerShoot.preCheck` / `LocalPlayerBolt.bolt`：调用同一方法（`AbstractGunItem` 在 common 侧，客户端可用），注意保留 `playDrySound` 分支。

> 下游收益：只需覆写/ mixin **一个** `checkAmmoAvailability`，各处判定自动一致。

### 方案 B：弹药来源扣减做成可覆写入口（针对 2.1/2.2/3.4）

在 `AbstractGunItem`（或 `ModernKineticGunItem`）加：

```java
/** 从给定实体扣减 neededAmount 弹药；返回实际扣减数量。默认走背包/虚拟备弹。 */
protected int extractAmmoFromSource(LivingEntity shooter, ItemStack gun, int neededAmount) {
    if (useDummyAmmo(gun)) return findAndExtractDummyAmmo(gun, neededAmount);
    return shooter.tacz$getItemHandler(null)
            .map(cap -> findAndExtractInventoryAmmo(cap, gun, neededAmount))
            .orElse(0);
}
```

然后让两个 `consumeAmmoFromPlayer`（`LivingEntityShoot` 与 `ModernKineticGunScriptAPI`）都委托到 `extractAmmoFromSource`，`defaultReloadFinishing` / `defaultTickBolt` 的扣减也改走该入口。
- 行为不变（内部逻辑一模一样，只是汇聚到一处）。
- 下游可 mixin 覆写 `extractAmmoFromSource` 把来源重定向到女仆背包，**一处**搞定所有扣弹路径。

### 方案 C：连发射击大 lambda 提取（针对 1.7/1.8）

- `LocalPlayerShoot#doShoot`：把 `scheduleAtFixedRate` 的 lambda 体提取为 `private void runBurstTick(IGun, ItemStack, GunData, AtomicInteger count, ...)`，lambda 只做薄转发。
- `ModernKineticGunScriptAPI#shootOnce`：把 `addCycleTask` 的 lambda 体提取为 `private boolean runShootCycle()`。
- 行为不变；下游获得稳定具名方法，且能独立验证（开一枪连发）。

### 方案 D：`GunAnimationStateContext` 其余业务 lambda 提取（针对 1.1–1.6，分批）

每个 `processXxx(...)` 调用点，把匿名 lambda 换成 `this::namedMethod`：

```java
// 例：1.1
protected boolean hasBulletInBarrel(IGun iGun, GunDisplayInstance display) {
    Bolt boltType = gunData.getBolt();
    return boltType != Bolt.OPEN_BOLT && iGun.hasBulletInBarrel(currentGunItem);
}
```

> 注：`processGunData/processGunOperator/processCameraEntity` 这几个 helper 本身已是具名方法（好），脆弱的是**调用点传入的匿名 lambda**。

---

## 第四部分：下游友好度报告（隐性 API 清单）

当前项目里「下游必然会去碰、但没有正式暴露」的隐性 API，按优先级：

1. **弹药可用性判定**（3.1 五连判定）—— 换弹/开火/拉栓各处都要知道「还有没有子弹」。下游最可能碰，目前多处硬编码 → 应提成方案 A 的单一入口。
2. **弹药来源扣减**（2.1/2.2/3.4）—— 「子弹从哪来」是 `#46` 的原始诉求。目前分散在 `defaultReloadFinishing` / `defaultTickBolt` / 两个 `consumeAmmoFromPlayer` → 方案 B。
3. **状态机查询方法**（`GunAnimationStateContext` 的 `hasBulletInBarrel`/`getShootInterval`/`getReloadStateType` 等）—— 枪包 Lua 脚本和下游动画改动都会碰到 → 方案 D（具名方法）。
4. **冷却计算**（`getShootCoolDown` / `getDrawCoolDown` / `getShootInterval`）—— 判定「现在能不能开火」，与 1 强相关，且含散落的 `-5ms` 魔法数。

### 走向「正式版」应优先做成的正式 extension point / event

项目已有成熟的事件系统（`com.tacz.guns.api.event.common.*`：`GunShootEvent` / `GunReloadEvent` / `GunFireEvent` / `GunDrawEvent` / `GunFireSelectEvent` / `GunFinishReloadEvent`）和「接口扩展 + Mixin 实现」模式（`cn.sh1rocu.tacz.api.extension.*`：`IMoveDistTracker` / `IItem` / `IEntityAdditionalSpawnData`）。建议复用这两种既有模式：

- **推荐 A（最贴合 #46）**：新增一个可注册的 `AmmoSourceProvider`（回调接口，模式同 `IMoveDistTracker`），返回「射击者 + 枪械 → 物品处理器」，把 `tacz$getItemHandler(null)` 的默认实现替换为可覆写扩展。下游女仆模组直接注册「女仆背包 → ItemHandler」，无需任何 mixin。
- **推荐 B**：把「能否开火 / 弹药是否可用」做成带结果的事件（类似 `GunShootEvent` 的 cancellable + result），让下游在服务端判定阶段就能注入自定义判定。
- **推荐 C**：把 `defaultReloadFinishing` 的 `switch(FeedType)` 拆成可注册的 `ReloadFeedStrategy`（每个 FeedType 一个实现），既解决 Lua 默认逻辑不可覆写的问题，也为新增供弹类型留口。

三者都是**加法**，不破坏现有公共类名/包名/mod id，也不改变默认行为。

---

## 附：已完成改动

### P0（commit `0950d71`）

- `GunAnimationStateContext#hasAmmoToConsume` → 提取 `hasAmmoToConsumeInEntity(Entity)`（具名，`protected`）。
- 新增 `AbstractGunItem#hasAmmoInInventory(IItemHandler, ItemStack)`（public static，稳定扫描入口）。
- 去重 `canReload` / `hasInventoryAmmo` / `ModernKineticGunScriptAPI#hasAmmoToConsume` / `GunAnimationStateContext#hasAmmoToConsumeInEntity` 四处扫描循环。

### P1①：noAmmo 五连判定去重（方案 A）

- 新增值对象 `com.tacz.guns.api.item.gun.AmmoAvailability`（只读快照 + `isNoAmmoToShoot()` / `isNoAmmoToBolt()` 两个具名判定，分别对应射击路径与拉栓路径的两种既有语义）。
- 新增 `AbstractGunItem#checkAmmoAvailability(IGun, LivingEntity, ItemStack, Bolt, boolean)`（public static，稳定判定入口）。
- 五处内联判定全部收敛到该入口：
  - `LivingEntityShoot.shoot()`（服务端射击）
  - `LocalPlayerShoot.preCheck()`（客户端射击）
  - `LivingEntityBolt.bolt()`（服务端拉栓，走 `isNoAmmoToBolt()`）
  - `LocalPlayerBolt.bolt()`（客户端拉栓，走 `isNoAmmoToBolt()`）
  - `ModernKineticGunScriptAPI#reduceAmmoOnce()`（脚本射击，走 `isNoAmmoToBolt()`）

### P1②：弹药来源扣减入口（方案 B）

- 新增 `AbstractGunItem#extractAmmoFromSource(LivingEntity, ItemStack, int)`（public，稳定扣减入口）。
- `LivingEntityShoot#consumeAmmoFromPlayer` 与 `ModernKineticGunScriptAPI#consumeAmmoFromPlayer` 两个签名均委托到 `extractAmmoFromSource`，各保留自己的前置短路（创造模式判定不变）。

### P1③：连发射击大 lambda 提取（方案 C）

- `LocalPlayerShoot#doShoot`：`scheduleAtFixedRate` 的匿名 lambda → `runBurstTick(...)`；`tacz$submitAsync` 的匿名 lambda → `fireOnce(...)`。
- `ModernKineticGunScriptAPI#shootOnce`：`CycleTaskHelper.addCycleTask` 的匿名 lambda → `runShootCycle(...)`。

### P1④：`GunAnimationStateContext` 业务 lambda 具名化（方案 D）

以下查询方法的匿名 lambda 均提取为 `protected` 具名方法（供下游 mixin/覆写）：

| 原公开方法 | 提取后的具名方法 |
|---|---|
| `hasBulletInBarrel()` | `isBulletInBarrel(IGun, GunDisplayInstance)` |
| `getShootInterval()` | `resolveShootInterval(Entity)` |
| `adjustClientShootInterval(long)` | `adjustClientShootInterval(IClientPlayerGunOperator, long)` |
| `getReloadStateType()` | `resolveReloadStateType(Entity)` |
| `shouldSlide()` | `shouldSlideInEntity(Entity)` |
| `anchorWalkDist()` | `anchorWalkDistInEntity(Entity)` |
| `getWalkDist()` | `resolveWalkDist(Entity)` |
| `getMaxCharge()` | `resolveMaxCharge(IGun, GunDisplayInstance)` |
| `getChargeThreshold()` | `resolveChargeThreshold(IGun, GunDisplayInstance)` |

同时将 `hasAmmoToConsumeInEntity` 由 `private` 提升为 `protected`。

### P1⑤：冷却窗口常量化（3.3）

- 新增 `ShooterDataHolder.LATENCY_WINDOW_MS = 5L`，服务端 6 处 `coolDown - 5` 全部改用它（行为逐位不变）。
- 客户端 `LocalPlayerShoot#getCoolDown` 历史上就不减窗口：只加注释说明差异，**没有**把两端合并进同一个冷却计算 API，也没有给客户端硬加 -5ms。

### P1⑥：换弹门槛具名化（3.5）

- `LivingEntityReload#reload` → `protected void performReload(AbstractGunItem, ItemStack, CommonGunIndex)`。
- `LocalPlayerReload#reload` → `protected void performReload(AbstractGunItem, ItemStack, GunData, GunDisplayInstance)`；`#cancelReload` → `protected void performCancelReload(GunDisplayInstance)`。
- 两端门槛序列有意不同（客户端多 100ms 射击后保护；服务端有冷却/拉栓检查），javadoc 互相注明，**保持独立 API**。

### P1⑦：换弹/拉栓的弹药来源决策具名化（2.1/2.2）

- `ModernKineticGunItem#defaultReloadFinishing` 内联 switch → `protected void consumeAmmoForReload(api, FeedType, needAmmoCount, needConsumeAmmo)`。FUEL 消耗语义与 MAGAZINE/INVENTORY 不同，分支保留不合并。
- `ModernKineticGunItem#defaultTickBolt` 喂弹块 → `protected void feedChamber(ModernKineticGunScriptAPI)`。背包直读路径经 `consumeAmmoFromPlayer` → `extractAmmoFromSource`，下游覆写后者即可覆盖。

### P1⑧：拉栓动作本体具名化（3.6）

- `LivingEntityBolt#bolt` → `protected void performBolt(AbstractGunItem, ItemStack, CommonGunIndex)`。
- `LocalPlayerBolt#bolt` → `protected void performBolt(IGun, ItemStack, GunData, GunDisplayInstance)`（补齐了 `GunDisplayInstance` import）。
- 两端门槛差异（服务端冷却检查 vs 客户端状态锁）文档化，保持独立 API。

### P1⑨：开火状态锁判定具名化（1.9）

- 新增 `public static boolean LocalPlayerShoot#isShootLocked(IGunOperator)`；`SHOOT_LOCKED_CONDITION` 字段改为该方法引用。
- 字段仍是状态锁的<b>身份令牌</b>（`lockedCondition != SHOOT_LOCKED_CONDITION` 引用比较），javadoc 明确：下游改判定请覆写具名方法，**不要**替换字段。

### P2（顺手）

- `LivingEntityAmmoCheck` 增加 javadoc，明确其「半暴露的隐性 API」定位及 `needCheckAmmo` 与 `consumesAmmoOrNot` 的语义区别。
- 「-5ms 冷却窗口」魔法数（类别 3.3）与 `checkFunction` 去重（类别 1.10）暂缓，属纯外观改动，风险收益比低，留待后续。

> 所有改动均为「提取/收敛」性质，不改变任何判断顺序、返回值或副作用；未新增依赖，未改动类名/包名/mod id。每处均可独立验证（换弹/开火/拉栓/动画路径各自等价）。
