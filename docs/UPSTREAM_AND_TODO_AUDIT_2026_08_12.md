# 上游对照 + TODO 真伪审计（2026-08-12）

> 回答两件事：① 上游有没有我们一直忽略的小功能/内容；② 仓内 TODO/注释哪些是真债、哪些是过时谎言。
>
> **方法**：官方 `MCModderAnchor/TACZ@1.20.1`（b43eb84，2026-05-24）与
> `Sh1roCu/TACZ-Refabricated@1.21.1` 逐文件差集 + 关键类 raw 对照；
> 仓内 TODO 不采信字面，一律对调用链 / 上游同源行。
> 案例①~⑩诊疗史仍以 `COMPAT_AND_ROADMAP.md` 为准，本文不重复。

---

## 0. 一句话结论

- **TACZ 本体没有漏移植的完整功能。** 用户点名的「枪械经验等级」是上游自己没做完的半成品，官方 / Sh1roCu / 本仓三方同一套空壳。
- **本移植侧玩家可见的未解问题，只剩 PAL 趴姿退出后第三人称手臂错形**（案例⑩，已按指示挂起）。
- 仓内标着 TODO 的条目，**绝大多数是上游原文原样拷过来的**；本仓自己加的 TODO 要么是「等外部 Mod 出 26.2」，要么是 LRTactical 已知小缺口。
- 这次新抓到 **一条本仓自己写错的注释**（`dropAllAmmo` 声称已改 LivingEntity / 已退膛内弹），已当场改回与官方一致的 TODO。

---

## 1. 枪械经验等级 —— 不是漏做，是上游半成品

用户感觉「等级可能没做」是对的，但责任不在本移植。

### 1.1 三仓同一套空壳（逐文件 raw 对照）

| 件 | 官方 1.20.1 | Sh1roCu 1.21.1 | 本仓 |
|---|---|---|---|
| `ModernKineticGunItem.getLevel/getExp/getMaxLevel` | **三个都 `return 0`** | 同 | 同 |
| `GunLevelManager` 类 | **全仓不存在** | 不存在 | 不存在 |
| `ServerMessageLevelUp` Toast 块 | 注释「TODO 在完成了枪械升级逻辑后，解封下面的代码」+ 整段封印 | 同 | 同（字面逐行相同） |
| `GunLevelUpToast` | 官方自己写「这个类没有实际使用，先不管了」，`render` 整段注释掉 | — | 我们把 26.2 的 `extractRenderState` 写完了，但发包方永不调用，等于死类 |
| NBT 键 `GunLevelExp` + `IGun` 读写 API | 有（只读，全仓零写入方） | 同 | 同 |
| Tooltip「等级」行 | 有。因 `getMaxLevel()==0` 且 `level==0`，**恒显示 `0 (MAX)`** | 同 | 同 |

全仓 `setExp` / `addExp` / `putInt(GUN_EXP_TAG, …)` **零命中** —— 经验从来不会涨。网络包 `s2c_levelup` 注册了，但没有任何发送点。

### 1.2 玩家能看到什么

Tooltip 永远写着紫色的 `0 (MAX)`。这不是 bug，是上游把 UI 先铺好、逻辑一直没填。
官方 Forge 正式版也是这个样子。

**处置：跟上游对齐，不擅自发明一套升级公式。** 若将来上游解封，再按他们的 `GunLevelManager` 对搬。

---

## 2. 文件差集：官方有、我们没有的，全是平台层

`com.tacz.guns` Java：官方 652 / Shiro 653 / 本仓 693（多出来的全是 26.2 渲染/Iris/PAL 适配）。

### 2.1 官方有、本仓无（11 个）

| 文件 | 是什么 | 本仓等价物 |
|---|---|---|
| `client/event/FirstPersonRenderEvent.java` | Forge/Fabric `RenderHandEvent` 驱动第一人称 | `ItemInHandRendererMixin`（26.2 手部 pass 改了，事件入口没了） |
| `client/init/ParticleFactoryRegistry.java` | Forge 粒子工厂注册 | `ParticleFactories.registerParticles()` |
| `compat/oculus/*` | Forge Oculus | `compat/iris/*` |
| `config/PreLoadModConfig.java` | Forge 早期配置 | `PreLoadConfig` |
| `init/ModLootModifiers.java` | Forge global loot modifier 注册 | `LootTableInjectorModifier`（第 39 轮已解封） |
| `network/IMessage.java` / `LoginIndexHolder` / handshake 三件 | Forge 握手通道 | `AcknowledgeC2SPacket` + `SyncedEntityDataMappingS2CPacket` |

没有一条是「少了一个玩法系统」。

### 2.2 Shiro 1.21.1 有、本仓无（3 个）

| 文件 | 说明 |
|---|---|
| `FirstPersonRenderEvent` / `PreLoadModConfig` | 同上，平台层 |
| `crafting/NBTIngredient.java`（id = `tacz:nbt`） | Shiro 自建的 Fabric 自定义 Ingredient。本仓对应的是 `forge:partial_nbt` + `forge:nbt`（`PartialNBTIngredient` / `StrictNBTIngredient`，已在 `TaCZFabric` 注册，且 `GunSmithTableIngredient` 会把旧 Forge JSON 改写成 Fabric 写法） |

`tacz:nbt` 在默认枪包配方里 **零使用**。第三方 Forge 包走的是 `forge:partial_nbt`，我们已经接了。
唯一的理论缺口：有人专门给 Shiro 1.21.1 写了 `{"type":"tacz:nbt",...}` 的 Fabric 包 —— 目前没见过样本。若出现，给 `PartialNBTIngredient` 再挂一个 `tacz:nbt` 别名即可，不必新写类。

### 2.3 默认枪包内容

官方 `data/tacz/data/guns/` 与本仓枪种清单一致（含 2026-05 才进正式版的 `m9a4`）。
`hk416d → hk416a5` 那次提交改的是模型/动画文件名，**枪 id 仍是 `hk416d`**（官方 index 仍叫 `hk416d.json`），不是漏同步。
弹药盒「右键插不进格子时吞子弹」的 `insertedCount <= 0` 守卫，本仓与官方 2026-05-24 修法逐行相同。
蓄力（`ChargeData` + `ClientMessagePlayerShoot.chargeProgress` + Lua API）在役。

官方 1.1.8 之后的提交（弹药造价、m4a1/glock17 音效、扩容换弹再启用）全是默认枪包资源微调，不是新系统；本仓枪包与 1.1.8-hotfix 基线对齐，这些属于「要不要再跟一次资源」而不是「功能没移植」。

---

## 3. TODO 真伪表（人工核实）

### 3.1 上游原文，三仓相同 —— 继承债，不动

| 位置 | 字面 | 核实 |
|---|---|---|
| `ModernKineticGunItem.getLevel/getExp/getMaxLevel` | `return 0`（无 TODO 字，但是空壳） | ✅ 真·上游半成品，见 §1 |
| `ServerMessageLevelUp:56` | 「完成升级逻辑后解封」 | ✅ 真。`GunLevelManager` 不存在，解封会编译失败 |
| `LivingEntityShoot:270` | 「需要检查是否有更简单的消耗背包弹药方法」 | ✅ 上游原文。实现就是从 ScriptAPI 复制的那份，能用 |
| `LocalPlayerShoot:290` | 「todo 需要检查」+ 异步上主线程播声音 | ✅ 上游原文。注释自己已经写明原因（防 CME），不是未完成 |
| `PreventsHotbarEvent:15` | 「todo 需要测试行为」 | ✅ 上游原文。`GuiMixin` 在役调用；合成台/改装界面关热键栏，行为与官方一致 |
| `ModernKineticGunScriptAPI:843` | 「测试检查 enum 能否直接给 lua」 | ✅ 上游原文。旁边已经有 `getBolt()` 返回 enum，`getBoltByInt()` 是兼容层 |
| `AnimationConstant:8` | `// todo` + 空类 | ✅ 上游原文。类从未被填过 |
| `EntityKineticBullet:439` | 「暴击判定（不是爆头）需要输出 flag」 | ✅ 上游原文。从未实现。爆头倍率是另一条已完成的路径 |
| `api/client/animation/interpolator/Spline.java` | 官方是空 TODO；本仓改成了线性 lerp 兜底 | ⚠️ 官方仍是空实现。本仓比上游多走了一步（线性），**仍不是真 Catmull-Rom**。Bedrock 动画实际走 LINEAR/STEP/SLERP，这条路径几乎不触发 |

### 3.2 本仓相对上游的合理差异

| 位置 | 字面 | 核实 |
|---|---|---|
| `EntityKineticBullet:388 MaybeMultipartEntity` | `// TODO` + `PartEntity` 注释掉 | ✅ **真差异，不是忘了写。** 官方用 Forge `PartEntity#getParent()`；Fabric 26.2 无此类型。打末影龙时按部件自身记账，普通生物无感。与 `PENDING_AUDIT` A5 一致 |
| `AbstractGunItem.dropAllAmmo` | 本仓曾写「已改 LivingEntity / 已退膛内弹」 | **❌ 注释撒谎。** Shiro/官方仍是 `//TODO 操作对象不应该是 Player…枪膛内的子弹也要退`。本仓签名仍是 `Player`，只读 `getCurrentAmmoCount`，从不碰 `hasBulletInBarrel`。**已改回与官方一致的 TODO**（本次） |
| `GunLevelUpToast` | 官方 `render` 整段注释；本仓实现了 26.2 `extractRenderState` | 无功能差异：发包方封印，Toast 永不出现 |

### 3.3 等外部条件（不是本仓债）

| 位置 | 核实 |
|---|---|
| `compat/ar/*`、`BeamRenderer` AR 路径 | ✅ AR 无 26.2 构建，`LOADED` 强制 false。激光走普通路径在役 |
| `compat/controllable/ControllableCompat` | ✅ Controllable 无 26.2 |
| `api/event/common/KubeJSGunEventPoster` | ✅ KubeJS 无 26.2 |
| `RenderHelper` `BufferUploader removed` / stencil no-op | ✅ 26.2 无 stencil。`enable/disableItemEntityStencilTest` 零调用方（`PENDING_AUDIT` C3） |

### 3.4 LRTactical（已知、已在 COMPAT §八 / PENDING_AUDIT A2/A3）

| 位置 | 核实 | 玩家影响 |
|---|---|---|
| `CustomItemCoolDowns.onCooldownStarted/Ended` | ✅ 真。无 `SCustomCoolDownMessage` | 客户端物品栏不显示冷却遮罩；服务端判定正确 |
| `ThrowableItemEntity` 弹跳/死亡音效 | ✅ 真。ARR 素材不打包 | 弹跳=方块脚步声 |
| `GrenadeEntity` 震屏 / `destroyMultiplier` | ✅ 真。无自定义爆炸包；原版 `Level#explode` 无倍率参数 | 无震屏；破坏倍率配置无效 |
| `ThrowableIndexManager`「暂未实现网络同步」 | 需单独再核一次现网同步通道（`ServerMessageSyncLrPack` 后来加过表）。**不当成新发现**，沿用既有文档口径 | 专用服务器客户端拿不到索引 —— 若现网通道已补，这条注释就过时了。本次未再追这条调用链 |

### 3.5 已更正过的假 TODO（上次审计，本次复验仍成立）

`TextShowRender` 旧 `render()` 上的「文字未实现」——假的，`extract()` 在役。
`LootTableInjectorModifier` 旧「26.2 取不到 registry」——假的，第 39 轮已解封。

---

## 4. 还剩什么是「我们的问题」

按「玩家能感知、且是本移植引入或本移植该扛」过滤：

| # | 项 | 状态 |
|---|---|---|
| **PAL 案例⑩** | 切枪第三人称动画脏（不是持续错形） | **2026-08-12 已改切枪路径**（`onDraw` 不再 fade-to-null 叠快照），待用户复测 |
| PAL 案例⑥ | 切枪后第三人称动画整局失效 | **已修，用户确认闭环** |
| LRTactical A2/A3/A3b | 冷却遮罩 / 专属音效 / 震屏 | 附属层小缺口，不是 TACZ 本体。要做就同一条网络通道一批做 |
| Tooltip `0 (MAX)` | 上游半成品的可见残留 | 跟上游对齐。若觉得碍眼，只能等上游解封或单独加「max==0 时藏这一行」——那是产品决定，不是修复 |
| 案例③ 镜框凸包啃边 | 用户挂起 | `ScopeMaskHullFill=false` 秒退 |
| 案例⑦ 炮弹/炮烟从眼位飞 | 用户定：回退为上游原生 | 非缺陷 |

**没有第三条「漏掉的上游巧思」需要现在动手。**

---

## 5. 这次顺手改的源码

只改了一处错误注释，零行为变化：

- `AbstractGunItem.dropAllAmmo`：删掉「已改 LivingEntity / 已退膛内弹」的假完成声明，恢复与官方/Shiro 一致的 TODO，并写明为什么那两条都还没做。

---

## 6. 建议

1. **PAL 案例⑩** 继续挂起，等新的在体证据（录屏 / 可复现条件变化）。
2. **枪械等级** 不要做。做了就是发明，不是移植。
3. **LRTactical 冷却同步** 仍是唯一「小、真、玩家能看见」的可选项；不是阻塞发布的东西。
4. 仓内其余 TODO 保持原样 —— 它们是上游的备忘录，擦掉会让下次对照更难。
