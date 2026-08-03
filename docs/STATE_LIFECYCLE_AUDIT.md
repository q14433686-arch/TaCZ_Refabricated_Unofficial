# 状态生命周期审计 —— 「玩家实体被换掉」这一族 bug

**日期**：2026-08-03
**触发**：连续两个同族 bug 落地后，用户问「还飘着多少朵这样的乌云」。
本文是对该族群的一次**系统性穷举**，不是抽样。

---

## 0. 这一族 bug 长什么样

三个已修的案例，根因是同一个模式：

| bug | 攥住的旧东西 | 症状 |
|---|---|---|
| 持枪重进同一存档打不出子弹 | 客户端 `oldHotbarSelected`（旧槽位号） | 有动画无实弹 |
| ~~跨维度换弹不生效~~ | ~~服务端 `currentGunItem`~~ | **误判，已回退**：跨维度不换实例，supplier 一直有效；该「修复」反而导致跨维度后短时间无法用枪。真因待查 |
| （同上）登出 hook 漏路径 | `clearClientLevel` 只覆盖一条退出路径 | 上面那条的成因 |

**共同模式**：

> 某个**跨世界存活**的状态，攥着一个**随玩家实体重建而失效**的旧对象或旧值；
> 而负责清理它的那条路径，要么不存在，要么在 26.2 下不再被执行。

**为什么难发现**：编译通过、不崩、日志无异常，只在特定操作序列下暴露，
且往往能被「切一次枪」「reload 一下」这类操作意外掩盖。

---

## 1. 玩家实体会被重建的全部时机

这是判断「什么会失效」的基准。26.2 下 `ServerPlayer` / `LocalPlayer` 被换新实例的场景：

| 时机 | 服务端 | 客户端 |
|---|---|---|
| 死亡重生 | **换实例**：`PlayerList#respawn` → `new ServerPlayer` → `restoreFrom` | `Minecraft#player` 换实例 |
| **跨维度** | **不换实例**：`ServerPlayer#teleport(TeleportTransition)` 只调 `setServerLevel` | `Minecraft#player` **换实例** |
| 退出再进入 | 换实例（全新 `ServerPlayer`） | 换实例 |
| 被踢/断线重连 | 换实例 | 换实例 |

> **⚠️ 本表曾经写错，并直接导致了一次错误修复。**
> 早先版本把「跨维度」也归到 `restoreFrom` 一栏，据此在
> `ServerPlayerMixin#restoreFrom` 里清空了 `currentGunItem`，
> 结果既没修好原问题，又造成「跨维度后一段时间无法操作枪械」的回归。
>
> 字节码事实是：
> * 跨维度走 `ServerPlayer#teleport`，**同一个 `ServerPlayer` 实例**，
>   `restoreFrom` 根本不参与；
> * `restoreFrom` **只**由 `PlayerList#respawn` 调用；
> * `Player#inventory` 是 `private final`，构造期定型。
>
> **注意服务端与客户端并不对称**：跨维度时客户端 `LocalPlayer` 会换新实例，
> 服务端 `ServerPlayer` 却不会。用「客户端玩家换了实例」去推断
> 「服务端对象也失效了」是错的 —— 上次正是栽在这里。

**可靠的通用信号**：比对 `Minecraft#player`（客户端）/ 注入 `restoreFrom`（服务端）。
这两个信号**覆盖上述所有情形**，且不依赖任何会随版本改名的事件。

> **反面教训**：不要依赖「登出事件」做清理。26.2 有两条独立退出路径
> （`clearClientLevel` 与 `Minecraft#disconnect(Screen,ZZ)`），
> 只 hook 一条就会漏掉玩家最常用的「退出到标题」。

---

## 2. 穷举结果

### 2.1 服务端 `ShooterDataHolder`（每玩家状态）

逐字段核对：**唯一持有对象引用的是 `currentGunItem`（`Supplier<ItemStack>`）**，
其余全是 `long/float/boolean/enum` 等值类型，`initialData()` 已覆盖。

- `currentGunItem` **不应**在 `ServerPlayerMixin#restoreFrom` 处清空（曾这么做过，已回退，理由见 §1 的警告框）。
- 重生时新 `ServerPlayer` 的 supplier 由客户端补发的 draw 重建，已有机制足够。
- **结论：该类无需额外干预。**

> 注意 `initialData()` **有意不清** `currentGunItem` —— 它在别处被调用时
> 语义是「重置临时动作状态」。清理放在 mixin 侧才精准对应「实体被换掉」。

### 2.2 已注册 mixin 的注入点有效性

对 **47 个已注册 mixin、83 个 `method=` 注入目标**逐一对照 26.2 字节码：

```
所有注入目标均在 26.2 字节码中找到 ✓
```

唯一报出的 `BedrockPartMixin -> BedrockPart` 是**误报**：
它注入的是 mod 自有类（不在 vanilla jar 内），且由 `ARCompatMixinPlugin` 条件加载。

- **结论：不存在「注入点悄悄失效」的乌云。**

> 该检查已脚本化，可在每次大版本升级后重跑，见 §4。

### 2.3 客户端 static 可变状态

全量扫描 `src/main/java` 下所有 `static` 非 `final` 字段，
过滤出类型可能持有 MC 对象的 **17 处**，逐个判定：

| 类别 | 实例 | 判定 |
|---|---|---|
| 注册表 | `ModBlocks.*_BE`、`ModEntities.*`、`TargetMinecart.TYPE` | 无害，本就该长生 |
| 单例 | `SyncedEntityData.INSTANCE`、`PlayerAnimatorAssetManager.INSTANCE` | 无害 |
| 已用弱引用 | `InventoryEvent.lastPlayer`、`RefreshClonePlayerDataEvent.lastPlayer`、`ModCapabilities.lastClientPlayer` | 正确写法 |
| 哑元 | `BedrockModel.dummyModel` | 不可变占位 |
| **自愈型缓存** | `GunHudOverlay.cacheMaxAmmoCount`、`HeatBarOverlay.heatScale`、`KillAmountOverlay.killAmount`、`FirstPersonRenderGunEvent.currentViewIndex`、`GunItemRendererWrapper.lastModel`、`LocalPlayerSprint.stopSprint`、`SoundPlayManager.tmpSoundInstance` | **低风险**，见下 |
| 已修 | `InventoryEvent.oldHotbarSelected / oldHotbarSelectItem` | 已加实例更换检测 |

**「自愈型」的判定标准**：该值在下一帧/下一次动作时会被**当前状态无条件重算或覆盖**，
残留最多影响一帧观感，不会卡死。例如 `heatScale` 每帧朝目标值收敛、
`cacheMaxAmmoCount` 每 50ms 重算、`lastModel` 只用于 `!=` 比较后立即覆盖。

对照之下，`oldHotbarSelected` 之所以是真 bug，是因为它**参与相等性判断且判断结果决定要不要发包**——
一旦误判成「没变」，就再也没有第二次机会，属于**吸收态**。

> **筛查心法**：`static` 状态危险与否，看它是「每帧被覆盖」还是
> 「参与一次性分支判断」。后者才是乌云。

### 2.4 `DelayedTask`

全局唯一使用者是 `RefreshClonePlayerDataEvent`，捕获的是局部变量 `current`，
任务执行后即从队列移除。即便跨世界残留，最坏后果是对一个旧玩家多调一次
`initialData()`——无副作用。**排除。**

---

## 3. 结论

**这一族已经筛干净了。** 具体地：

> **前提**：以下结论建立在 §1 那张（已修正的）时机表上。
> 该表的第一版写错过一栏，直接导致了一次错误修复 ——
> **任何基于「某某时机会重建某某对象」的推断，都必须先用字节码验证那个前提本身。**

- 服务端每玩家状态：唯一的对象引用已清理；
- mixin 注入点：83/83 有效；
- 客户端 static：除已修的 2 个外，其余均为无害或自愈型。

**不代表代码里没有别的 bug**，但「攥着旧玩家对象」这个特定形态，
按上述三个维度已无遗漏。

---

## 4. 复发预防：注入点体检脚本

大版本升级后重跑，可一次性发现所有失效的 mixin 注入点：

```bash
python3 tools/audit_mixin_targets.py
```

原理：解析 `*.mixins.json` 取得已注册 mixin → 读每个 mixin 的
`@Mixin(X.class)` 与 `method="..."` → 对照 `minecraft-merged-*.jar`
的字节码核验方法名/描述符是否存在。

**这类检查必须脚本化**：83 个注入点靠人工核对不现实，
而漏掉任何一个的表现都是「静默失效」——正是最难查的那种。
