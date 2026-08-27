# 从姊妹仓 TaCZ_Renovated `26.2` 分支同步（2026-08-27）

> 写给下一任 AGENT 与审阅者。本文记录**同步了什么、为什么这么同步、以及刻意没同步什么**。
> 每一条都写清了判定依据，避免下次有人把「没同步」误读成「漏了」。

## 0. 同步源与目标

| | 值 |
|---|---|
| 姊妹仓 | `q14433686-arch/TaCZ_Renovated`（NeoForge 重实现） |
| 姊妹分支 / tip | `26.2` @ `8b24fda23d4139628f34129a9313e020b56dd883`（2026-08-27） |
| 本仓分支 / 基线 | `26.2(main)` @ `7f6d1bf2ebf3cdc24c06f9401529f6064e6f0047` |
| 两仓关系 | **无共同祖先**（`git merge-base` 为空）—— 只能按语义逐文件对照，不能 `git cherry-pick` |
| 加载器差异 | 本仓 Fabric（`cn.sh1rocu.tacz` 垫片 + mixin + Fabric API）；姊妹仓 NeoForge（事件 + AT + `DeferredRegister`） |
| Minecraft | 两边同为 26.2，vanilla API 面相同；混淆状态相同（均未混淆） |

姊妹仓 26.2 分支自 `26.2_R1` tag 以来的全部代码增量集中在 PR #18（`8431e68`→`bd7bae5`），
逐条判定见下。

**验证状态（如实声明）**：本沙箱无 JDK、无 Maven/Fabric 仓库网络（只有 GitHub 可达），
**无法执行 Gradle 编译，更无法实机**。因此本文所有条目都是**源码级同步**，
不构成实机 PASS。实际执行的静态检查见 §5。

---

## 1. 已同步（按主题）

### A. 预燃（cook）与引信超时 —— 官方 LRTactical 0.4.3 语义

来源：`c3acff70`（阈值）+ `5f6b9e71`（引信，姊妹仓 `daf2df53` 回退时**明确保留**的那一半）。

| 文件 | 改动 | 语义 |
|---|---|---|
| `item/ThrowableItem#onUseTick` | `prepare + life*0.9` → `prepare + life` | 手上预燃的时长与内容包 `life_time` 对齐 |
| `entity/ThrowableItemEntity#tick` | `tickCount >= life && life > 0` → `life >= 0 && tickCount >= life` | 源码层面消除「温雷进度条满 → 扔出后永不爆」：`onThrow` 把剩余引信夹到 `0`，旧判定把 `0` 当「永不超时」（姊妹仓用户实测过该症状；本仓为同一份代码，未实机复验） |
| `client/overlay/UsingProgressOverlay` | 红条分母 `life*0.9F` → `life` | 与上面同分母，否则进度条与引爆点错位 |
| `item/ThrowableItem#onThrow` | 只加注释 | 记录 `0 = 立刻炸`、`-1 = 永不超时` 的约定 |

**为什么这三处必须一起改**：`0` 是「满预燃」的产物，`-1` 是遥控物（本仓
`data/lrtactical/index/throwable/test_c4.json` 就是 `life_time: -1`）。
只改 `ThrowableItem` 会让满预燃手雷落地后永不自爆；只改实体则 C4 会被超时吃掉。
两仓的判定现在逐字一致。

### B. 烟雾粒子按环境光采样

来源：`c3acff70`。`client/particle/SmokeCloudParticle#getLightCoords`
由「恒返回 `0xF000F0`（全亮）」改为「采环境光，天光/块光各自下限 2，两者都 ≤2 时扫六个邻格取最大值」。

**26.2 API 已核对**（本地 `.gradle/loom-cache` 的 26.2 jar 字节码，非记忆）：
`getBrightness(LightLayer, BlockPos)I` **不在 `Level` 上声明**，是
`net/minecraft/world/level/BlockAndLightGetter` 的默认方法，经
`LevelAccessor → LevelReader` 继承到 `ClientLevel`；参数顺序是**先 `LightLayer` 后 `BlockPos`**。
`Particle` 上的方法名确实是 `getLightCoords(F)I` 且为 `protected`；
`Particle.level/x/y/z` 均为 `protected`。

### C. 移动输入只发给近战（静止拉栓抖动）

来源：`ce0d2458`。`client/event/LrTickAnimationEvent#tickAnimation(Minecraft)`
由「所有 LR 物品」收窄成「只有 `MeleeItemRenderer`」。

官方手雷脚本把取消拔销写成 `trigger("idle")` / `input == "idle"`，与
`GunAnimationConstant.INPUT_IDLE` 的字面量相同。**本仓自带的
`default_grenade_state_machine.lua` 有同样的 `idle` 分支**，所以这个 bug 在 Fabric 侧同样存在。
第三人称的 `visualUpdate` 路径**不收窄**（它不发移动输入，且要覆盖三类物品）。

### D. `display_offset` 与 `entity_transform`

来源：`c3acff70` + `305bed10`（后者是姊妹仓自己补的 record 组件漏项，本仓一次写全）。

- 新增 `client/resource/display/DisplayTransform.java`：解析与施加。
  `entity_transform` 默认值为 `Z 90° + (-0.3, 0.15, 0)`；JSON 里的 `translation` 乘 1/16 再夹 ±5、
  `scale` 夹 ±4 —— **默认值与 JSON 量纲不一致是官方契约，照抄不「统一」**。
  不用 26.2 的 `ItemTransform#apply`（它内部会再 `translate(-0.5,-0.5,-0.5)`，摆飞行实体会偏半格），
  改按 1.20.1 `apply(false, pose)` 的顺序手写：平移 → `rotationXYZ` → 缩放
  （`rotationXYZ` 已在本地 26.2 jar 常量池确认存在，vanilla `ItemTransform` 自己就用它）。
- `MeleeDisplayInstance` / `ThrowableDisplayInstance`：field + getter + record 组件 + `create()` 四处齐改。
- `MeleeItemRenderer` / `ThrowableItemRendererWrapper`：在 `transforms` **之后**、模型原点平移**之前**施加偏移。
- `ThrowableEntityRenderer`：有 display 时套 `entity_transform`，没有内容包时**保持原占位姿态不变**。
- `LrClientAssetsManager.GSON` 注册 TACZ 的 `Vector3fSerializer`（它同时是
  `JsonDeserializer` + `JsonSerializer`），否则 `display_offset` 只能靠反射碰运气。

**一处刻意保留的不对称**：display 分支的偏航用 `Axis.YP`，占位分支用 `Axis.YN`。
这与姊妹仓逐字一致（内容包路径下模型经 `ThrowableItemRendererWrapper` 做了 Z180 翻转，朝向相反）。
姊妹仓把整批 0.4.3 标为「未实机验证」，本仓也无实机条件，故**原样同步、不擅自改符号**；
若日后实机发现飞行朝向反了，两仓要一起改（代码里已就地标注）。

### E. 消耗品 Bedrock/Lua 渲染通道（官方 0.4.3）

来源：`c3acff70`。本仓此前**只有消耗品的服务端半边**：`ConsumableItem` 效果结算、
`data/lrtactical/index/consumable/*`、甚至 `assets/lrtactical/scripts/consumable_state_machine.lua`
都已就位，但没有渲染通道 —— 那份 Lua 一直是死代码。

新增：`api/animation/ConsumableAnimationStateContext`、
`client/resource/display/ConsumableDisplayInstance`、
`client/resource/manager/ConsumableDisplayManager`、
`client/renderer/item/ConsumableItemRenderer`、
`assets/lrtactical/models/item/consumable_dynamic.json`。

改动：`LrClientAssetsManager`（第三类 display + path-only 回退）、
`LrTacticalAPI#getConsumableDisplay`、`HasCustomDisplayProperty`（第三条通道）、
`ConsumableItem implements IItem`、`ModEntitiesRender#registerItemRenderers`、
`LrTickAnimationEvent#isLrAnimatedItem`、`assets/lrtactical/items/consumable.json`（condition 分流）。

**没装内容包时行为与同步前完全一致**：`items/consumable.json` 用
`lrtactical:has_custom_display` 分流，条件为假走原版占位模型，新渲染器不会被调用。

### F. 近战连击计数 —— 本仓的运行期缺陷（不是新功能）

**这是本次同步里唯一「本仓已有 bug、姊妹仓有正确实现」的条目。**

本仓的 `default_melee_state_machine.lua` 第 86 行调用
`context:getActionCount("attack_left")` 做连击动画取模，而
`BaseAnimationStateContext` 从来没有这个方法（Lua 文件与姊妹仓**逐字节相同**，
`git diff` 为空）。LuaJ 查不到 Java 成员时返回 `NIL`，`NIL` 被当函数调用抛 `LuaError`；
调用链 `MeleeAttackKeys → AnimateGeoItemRenderer#triggerAnimation →
AnimationStateMachine#trigger → Lua` 上**没有任何 catch**（逐文件确认过）。

**这是源码级推导，不是实机观测**：本沙箱无法运行游戏，所以「按左键轻击会抛 LuaError」
是按调用链推出来的结论，尚未经实机复现。可确定的是 Lua 与 Java 的方法名不匹配这一点
（脚本文件与姊妹仓逐字节相同，Java 侧无该方法）。

同步来源：`d58f432d`（姊妹仓 26.2 前滚 LR 层时就带着它）。
新增 `CombatProperties` 的 `actionCounts`（`EnumMap`，切武器时清零，`preAttack` 门禁之后自增）
与 `BaseAnimationStateContext#getActionCount(String)`。

新增守护脚本 `scripts/verify_lr_lua_context_api.py`（见 §5），
它能在不编译的前提下把这类「Lua 调了 Java 没有的方法」查出来。

---

## 2. 刻意**没有**同步（附判定依据）

| 姊妹仓内容 | 不同步的理由 |
|---|---|
| `5f6b9e71` 的 Iris 高倍目镜裁剪 + ADS `xBob` 缩放 | 姊妹仓自己 `daf2df53` **回退**了，并在 `docs/records/SCOPE_IRIS_VIEWLAG_AUDIT_20260826.md` 写成「禁止再试」的负结果。该文档还记录：本仓 `b88cb11` 早就试过同类 bob 缩放并撤回。**两边都失败过的方案不要互相搬运。** |
| 官方 0.4.3「TACZ 第三人称枪口锁」的豁免 | 官方那条 bug 的病根是 LR 的 `AdjustmentYRotModifier` 旋转层，**两仓都没有这一层**，bug 不可能触发。姊妹仓自己的结论也是 NO-GO。 |
| 近战第三人称 `player_animator` 集成 | 需要内容包提供 player_animator 文件、体积大、可能与 TACZ 的 PAL 抢轨道。姊妹仓列为「下一轮」，本仓同此判断。 |
| `flash_shield`、原作 ARR 美术/音效 | 两仓一致的战略遗弃（授权问题）。 |
| `ConsumableItem#tacz$onEntitySwing → true` | **加载器语义不同，照抄会引入回归。** 姊妹仓没有对应 mixin（其 `ILrItemExtension` 注释写明该 default 不会被调用），那份实现是死代码；本仓 `cn.sh1rocu.tacz.mixin.common.LivingEntityMixin#tacz$swingHand` 真的接了这个钩子，返回 `true` 会在 `LivingEntity#swing` HEAD 直接 `ci.cancel()`。消耗品的 Lua 脚本**没有任何 attack 分支**（只有 `start_use`/`stop_use`），接管挥臂等于「拿着药品左键什么动画都没有」。已在 `ConsumableItem` 里就地写明。 |
| `ConsumableDisplayManager` 不带 reload 依赖声明 | 反过来：本仓**必须**加 `getFabricDependencies()`。NeoForge 靠注册顺序弱保证，Fabric 不声明就会与 TACZ 的模型/动画/脚本加载器并行跑，`create()` 同步取资源时全数拿到 `null`，且因并行调度而**偶发**。 |
| `FirstPersonAnimationCompat#shouldVanillaRenderArms()` | 姊妹仓新增但在其全仓**无任何调用点**（`git grep` 确认）；本仓 Punchy mixin 用的是 `shouldUseTaczRenderer`。不搬死代码。 |
| `latest.log`（`8b24fda2`，785 行） | 运行日志，非源码。本仓历史上专门提交过 `Delete latest.log`。 |
| NeoForge 专有接线：`LrClientEvents` / `LrClientBridge` / `ILrItemExtension` / `RegisterItemModelsEvent` / `neoforge.mods.toml` / `tacz.punchy.mixins.json` | 本仓已有各自的 Fabric 等价物（`TaCZFabricClient`、`IItem` 垫片、`LrDynamicItemModel.registerType()`、`fabric.mod.json`、`tacz.fabric.mixins.json` + `MixinPlugin`）。 |

## 3. 已确认**无需**同步（本仓已有等价实现）

同步前逐条核对过，避免重复劳动：

- **Punchy 第一人称让位**（姊妹 `8431e685`）：四个 mixin 的注入体与本仓**逐字相同**
  （仅包名与 javadoc 不同）。方向其实是反的 —— 本仓 `67d5107`（2026-08-12）在先，
  姊妹仓 2026-08-25 把它前滚到 NeoForge。
- **`ServerMessageGunDraw` 用 `OPTIONAL_STREAM_CODEC`**（姊妹 `8bd3845d` 第 1 条）：本仓已是。
- **`CommonAssetsManager` 把 `AttachmentsTagManager` / `RecipeFilterManager` 纳入网络同步**
  （同上第 2 条）：本仓的 `register(...)` 助手本来就把它们加进 `listeners`，缺口不存在。
- **`IrisCompat` 把 "already assigned" 视为成功**（同上第 3 条）：本仓已有。
- **`IMoveDistTracker`（重建 26.2 已删的 `walkDistO`）**：本仓在
  `cn.sh1rocu.tacz.api.extension.IMoveDistTracker` + `mixin/common/EntityMixin`，已接线。
- **tooltip 自定义描述**（0.4.3 契约：灰色 / 宽 300 / >3 行折叠 / Shift 展开）：
  本仓 `AbstractClientItemTooltip` 已实现，本轮不动代码。
- **`Item#getName` 专服崩溃修复**：本仓 `docs/DEDICATED_SERVER_GETNAME_AUDIT_2026_08_21.md`
  与 `docs/PORT_GETNAME_FIX_TO_1211_AND_2612_2026_08_22.md` 已覆盖。

## 4. 与姊妹仓保持的刻意差异（同语义、不同实现）

| 位置 | 姊妹仓 | 本仓 | 原因 |
|---|---|---|---|
| `ConsumableAnimationStateContext` | `extends ItemAnimationStateContext`，重写 `currentItem/using/usingTick` | `extends BaseAnimationStateContext`，只加 `using/usingTick` | 与本仓 `ThrowableAnimationStateContext` 同一条已记录的决定：上游把同组字段在平行类里各写一遍属于重复定义。**Lua 只按方法名调用，可见面完全一致。** |
| display manager 注册 | `AddClientReloadListenersEvent` | `IdentifiableResourceReloadListener` + `getFabricDependencies()` | 加载器差异 |
| 物品自定义渲染器出口 | `ILrItemExtension#getCustomRenderer` | `cn.sh1rocu.tacz.api.extension.IItem#getCustomRenderer` | 加载器差异 |
| `tacz$onEntitySwing`（消耗品） | 有（死代码） | **无**（见 §2） | 本仓该钩子是活的 |

## 5. 本次实际执行的检查

| 检查 | 命令 | 结果 |
|---|---|---|
| Lua 状态机调用的 context 方法是否都有 Java 实现 | `python3 scripts/verify_lr_lua_context_api.py --strict` | 同步前：`default_melee_state_machine.lua` 报 `getActionCount` 缺失，退出 1；同步后：5 个脚本全 OK，退出 0 |
| 新增/改动资源的 JSON 合法性 | `python3 -c "json.load(...)"`（consumable.json / consumable_dynamic.json / melee.json） | 全部 OK；回退模型 `models/item/consumable.json` 存在 |
| 改动的 22 个 `.java` 里所有本仓内部 import 是否可解析 | 临时脚本（比对 `src/main/java` 下的实际路径） | 0 处无法解析 |
| 新渲染器的覆写签名是否与既有 `MeleeItemRenderer` 一致 | 两个文件的方法签名逐行 diff | 除换行外无差异 |
| 22 个 `.java` 的括号配平 / 冲突标记 / javadoc 内误写 `*/` 提前闭合注释 | 自写脚本（先剥离注释与字符串再计数） | 首轮查出 `ConsumableAnimationStateContext` 的 javadoc 里 `isInput*/` 会提前闭合注释 → **已修**；复跑 0 问题 |
| vanilla 26.2 API 面（`getBrightness` 归属与参数序、`Particle#getLightCoords` 可见性、`BlockPos#containing/relative`、`Direction#values`、`PoseStack#translate/scale/mulPose`、`Mth#DEG_TO_RAD`、`rotationXYZ` 存在性） | 自写 class 文件解析器读本地 `.gradle/loom-cache/.../minecraft-merged-6f7fc6e6bc-26.2.jar` | 全部与代码写法一致 |
| **Gradle 编译 / 实机** | —— | **未执行**：沙箱无 JDK，且 Maven Central / Fabric maven / Debian 源均不可达（仅 GitHub 可达）。这是本次同步最大的未覆盖面。 |

## 6. 下一任要做的事

1. **先编译**。本轮改动涉及新增 4 个类与 17 个文件改动，编译是第一道门。
2. **实机验收清单**（按用户可感知的优先级）：
   - 温雷：按住到进度条满 → 扔出 → 应当**立刻爆**（旧行为：永不爆）；
   - C4：投出后**不**自动爆，用起爆器手动引爆；
   - 烟雾弹：暗处烟幕应当「看得见但不自发光」；
   - 手雷：站立不动时拔销动画**不再抖动**；
   - 装了 LR 内容包：左键轻击不再抛 `LuaError`，且 `melee_1/melee_2` 交替；
   - 消耗品：装了内容包时第一人称走 Bedrock/Lua，没装时与同步前**逐帧一致**；
   - `display_offset` / `entity_transform`：内容包写了就应当生效，飞行手雷朝向见 §1-D 的标注。
3. 若实机推翻了 §1-D 的 `YP/YN`，**两仓同步修改**，别只改一边。
4. `gradle.properties` 的 `mod_version` **本轮未动**（同步不等于发布）。
   若要发布，按 `AGENTS.md` §1 同步 README 全部 6 处并跑
   `bash scripts/check_release_consistency.sh --strict`。
