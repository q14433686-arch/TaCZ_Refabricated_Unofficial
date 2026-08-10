# 两项待决事项：附属包原版配方兼容 / LRTactical 移植考察

> 本文只收录**已核对过的事实**（26.2 字节码、枪包实包、上游仓库），
> 结论与推测分开标注。

---

## 一、附属包的「自定义工作台 + 原版格式配方」怎么兼容

### 1.1 问题现状

实测样本：`GunpowderRevolution v1.2.7` 的 `data/hamster/recipes/oldworkbench.json`
（该包 68 条配方里**唯一**一条原版格式配方，其余 67 条走 TACZ 自建通道，已可用）。

它想做的事：合成一个**带 `BlockId` 的 `tacz:workbench_b`**，
即「附属包自定义的工作台方块」。

TACZ 的自定义工作台机制本身没问题 —— `BlockItemDataAccessor.BLOCK_ID = "BlockId"`
存在 `CUSTOM_DATA` 里，这条链路我们是通的。**卡住的纯粹是配方 JSON 格式。**

### 1.2 逐项差异（均经 26.2 字节码确认）

| 旧写法（1.20 / Forge） | 26.2 要求 | 依据 |
|---|---|---|
| `result.item` | **`result.id`** | `ItemStackTemplate.MAP_CODEC` 的 `lambda$static$0` 字节码：`Item.CODEC.fieldOf("id")` |
| `result.nbt` | **`result.components`** | 同上：`DataComponentPatch.CODEC.optionalFieldOf("components", EMPTY)`；`nbt` 字段已不存在 |
| `key: {"tag":"minecraft:logs"}` | **`key: "#minecraft:logs"`** | `Ingredient.CODEC` 只接受字符串/字符串数组 |
| `key: {"item":"minecraft:stick"}` | **`key: "minecraft:stick"`** | 同上 |
| `data/<ns>/recipes/` | **`data/<ns>/recipe/`** | `RecipeManager.RECIPE_LISTER = FileToIdConverter.registry(Registries.RECIPE)`，目录名取自 `ResourceKey.identifier().getPath()` = `"recipe"`，**常量，不可扩展** |

> `forge:ingots/iron` → `c:ingots/iron` 不是必须的 —— 我们有 `forge/tags/item` 兼容层。

### 1.3 目标形态（已验证 JSON 合法、字段名与字节码一致）

```json
{
  "type": "minecraft:crafting_shaped",
  "result": {
    "id": "tacz:workbench_b",
    "components": {
      "minecraft:custom_data": { "BlockId": "hamster:oldworkbench" }
    }
  },
  "pattern": ["SSC", "IBI", "LLL"],
  "key": {
    "L": "#minecraft:logs",
    "I": "#c:ingots/iron",
    "B": "minecraft:iron_block",
    "S": "minecraft:stick",
    "C": "minecraft:flint"
  }
}
```

### 1.4 为什么「改我们的代码」这条路基本走不通

历史上我们靠 `ShapedRecipeMixin` 注入 `ShapedRecipe#itemStackFromJson` 来支持 `nbt`
字段。**该注入点在 26.2 不存在** —— 配方体系已全面 codec 化，
`ShapedRecipe` 只剩 `MAP_CODEC` / `STREAM_CODEC` / `SERIALIZER`，
**没有任何 JSON 手工解析入口可供 hook**。
（该 mixin 已在仓库中标注为永久废弃，仅留作历史参考。）

也就是说要在**原版通道**上做兼容，只剩三种手段，代价都不小：

| 方案 | 做法 | 代价 |
|---|---|---|
| **A. mixin 改 `RecipeManager#prepare`** | 让它额外扫 `recipes/` | **影响全服所有 mod 的配方加载**，风险最高 |
| **B. 资源层路径重映射** | 挂载枪包时包一层 `PackResources`，把 `recipes/**` 映射成 `recipe/**` | 见下，**有污染问题** |
| **C. 预处理转换** | 加载前把旧格式 JSON 就地转成新格式 | 只解决格式，不解决目录；且要写一个 mini 转换器 |

**关于 B 的污染问题**（用户此前已质疑，结论是**确实存在**）：

`PackResources#listResources` 是按目录枚举的。把 `recipes/**` 整体映射成 `recipe/**`，
那 67 条 `tacz:gun_smith_table_crafting` 会**一起**被塞给 vanilla `RecipeManager`。
它们此刻已经走我们自己的通道加载过一遍 —— 等于**同一批配方在两套系统各存一份**；
而且它们的 `result` 是 `{"type":"ammo","id":...}` 这种私有格式，
vanilla `Recipe.CODEC` 解析不了，**每条报一次错**。
（这正是 `TableRecipeManager` 当初要按 `type` 过滤的原因 —— 历史上曾一次性吞掉 1585 条原版配方。）

要加门禁就得在 `listResources` 阶段逐文件读内容判 `type`，
意味着每次资源重载都要多读一遍全部文件 —— **为 1 条配方付这个代价不划算。**

### 1.5 建议（2026-07-30 已实现代码兼容层）

**旧结论（已过时）：** 曾推荐“不改代码，改为文档+工具”路线，理由是占比低、代码方案有污染。

**新实现（本仓库已落地）：**

针对用户反馈“附属包原版配方不可用，工作台合成道具不可识别”，已在资源包层实现兼容：

- 新增 `cn.sh1rocu.tacz.util.RecipeCompat`，自动完成：
  - `result.item` → `result.id`
  - `result.nbt` → `result.components.minecraft:custom_data`
  - `key: {"tag":"minecraft:logs"}` → `"#minecraft:logs"`
  - `key: {"item":"minecraft:stick"}` → `"minecraft:stick"`
  - `forge:` 前缀 → `c:` 前缀兼容
  - 仅对 `type: minecraft:*` 的原版配方生效，`tacz:gun_smith_table_crafting` 等自定义类型保持原样
- 在 `PathPackResources` 与 `DelegatingPackResources` 两层拦截：
  - `getResource`：若请求 `recipe/xxx` 不存在，自动回退到 `recipes/xxx`（旧目录），并对旧格式做即时转换
  - `listResources`：当查询 `recipe` 时，额外把 `recipes/` 旧目录的内容映射为 `recipe/`，同时过滤掉非原版配方，避免污染 vanilla `RecipeManager`（此前文档所述 B 方案污染问题的根因）
- 这样既解决了目录单复数问题，又解决了格式问题，且：
  - 不影响 `TableRecipeManager` 对自定义工作台配方的已有兼容（它本身已同时扫描 `recipe/` 与 `recipes/`）
  - 对 vanilla `RecipeManager`，自定义配方会被过滤掉（通过预读 JSON 判断 `type`），不会刷屏报错

> **效果**：`GunpowderRevolution v1.2.7` 的 `oldworkbench.json` 等单个原版配方现在可被识别，无需手动改包；旧布局包（`recipes/`）中的原版配方也会被自动映射到 `recipe/`。

> **仍建议文档**：在 `README` 的枪包适配章节保留 1.2 差异表，引导新包直接使用新格式，避免长期依赖兼容层。

---

## 二、LesRaisins Tactical Equipements 移植考察

仓库：`LesRaisins-Studios/LesRaisins-Tactical-Equipements`
（**只有 `1.20.1` 一个分支**，无 1.21.x）

### 2.1 规模

| 项 | 值 |
|---|---|
| Java 文件 | 149 |
| 代码行数 | **11,804** |
| 资源文件 | 208（6.4 MB） |

对比：只有 TACZ 本体的零头。

### 2.2 ✅ 最大的利好：地基已经铺好

它引用的 **39 个 `com.tacz.guns.*` 类，在我们仓库里 39 个全部存在**
（逐个核对，那 3 个 "MISS" 是静态导入造成的误报，`GunModelConstant` 确实在）。

包括这些硬核的：`BedrockAnimatedModel`、`LuaAnimationStateMachine`、
`AnimateGeoItemRenderer`、`BeforeRenderHandEvent`、`KeepingItemRenderer`、
`SlotModel`、`BedrockPart`。

### 2.3 工作量分布

| 模块 | 文件 | 行数 | 难度 |
|---|---|---|---|
| client | 28 | 2,710 | 中（26.2 渲染管线变更） |
| item | 24 | 2,521 | 低 |
| api | 20 | 1,486 | 低 |
| entity | 7 | 1,233 | 低 |
| network | 14 | 790 | 中（`SimpleChannel` → Fabric payload） |
| **capability** | 5 | **438** | **高（Fabric 无此概念）** |
| mixin | 8 | 405 | 中 |
| compat | 10 | 629 | **可跳过**（jei/cloth/player_animator） |

Forge 耦合面：`net.minecraftforge` 出现在 **78 个文件**，但绝大多数是机械替换
（`@SubscribeEvent` 17 个、`DeferredRegister` 7 个）。
我们 `api/event/` 下已有 **17 个事件垫片**、`util/forge/` 下已有 **`LazyOptional` 垫片**可直接复用。

### 2.4 ⚠️ 真正的阻塞点：许可证

```
Code: GNU GPL 3.0
Art Assets: All Rights Reserved      ← 问题在这
```

`src/main/resources` 里 **51 个 png + 68 个 ogg（6.4 MB）是 All Rights Reserved** ——
手雷模型、贴图、音效全在里面。

**代码可以合法移植，美术资源不能直接搬。** 移植出来没有资源，
就是一堆紫黑块（和 Tarkov 那个包一个下场）。

> **这是先决条件，不是技术问题。** 必须先联系 LesRaisins Studio 拿授权。
> 这一步没过，写多少代码都白搭。

### 2.5 其他发现

- `compileOnly("com.maydaymemory:mae")` **声明了但全仓一次都没 import** → 死依赖，直接删
- 依赖 `simplebedrockmodel`（`libs/` 里带了 jar）→ 需确认 26.2 有无对应版本
- `playeranimator` / `jei` / `cloth` 都在 `compat/`（629 行）→ **第一版可整个跳过**
- 跨度是 `1.20.1 → 26.2`，比我们之前的 `1.21.1 → 26.2` **多背一段**

### 2.6 参照物：已存在的 1.21.1 NeoForge 移植（`Nahiyus512` fork）

`Nahiyus512/LesRaisins-Tactical-Equipements-1.21.1`，默认分支 `neoforge1.21.1`。
**这是一份已完成的 `1.20.1 → 1.21.1` 移植，可当作「哪些地方要改」的地图。**

#### 关于它的授权处理 —— 不构成先例

| 项 | 上游 1.20.1 | 该 fork |
|---|---|---|
| 美术资源 | 51 png + 68 ogg（6.4 M） | **51 png + 68 ogg（6.3 M），一个没删** |
| `Art Assets: All Rights Reserved` | 有 | **原样保留在自己的 README 里** |

即：**它一边在 README 里写着 "Art Assets: All Rights Reserved"，
一边把这些资源打包分发到了 Modrinth 与 CurseForge。**
除非私下另有授权（README 无任何说明），否则这在严格意义上是自相矛盾的。

> **结论：不能拿它当"别人能做我们也能做"的依据。**
> 我们仍按原计划走「代码开源 + 不打包资源 + 纯前置框架」路线。

它**署名做得规范**，值得借鉴：顶部醒目 `Unofficial`、
"do not report issues of this port to the original developers"、
单列 Port Notice（原仓库链接 / 原作者 / 维护者）、保留原 Authors 与 Credits。

#### ✅ 真正有价值的技术情报

**1. capability → attachment 的迁移路径（最重要）**

1.20.1 的 5 个 capability 文件，在 1.21.1 里变成 3 个 + 一个 `ModCapabilities`：

```java
// 1.20.1 Forge —— Provider + LazyOptional
player.getCapability(CustomItemCoolDownsProvider.CAPABILITY)
      .ifPresent(CustomItemCoolDowns::tick);

// 1.21.1 NeoForge —— attachment，直接取值
player.getData(ModCapabilities.CUSTOM_COOLDOWN).tick();
```

被**删掉**的正是两个 `*Provider.java`（`CombatPropertiesProvider`、
`CustomItemCoolDownsProvider`）—— 即 `ICapabilityProvider` + `LazyOptional`
那套样板整个消失，数据类本身（`CombatProperties` / `CustomItemCoolDowns`）**几乎不用动**。

> **这对我们是重大利好。** 此前评估里 capability（438 行）被标为「难度高」，
> 现在看**难度大幅下降**：
> - NeoForge attachment 与 Fabric 的 attachment 概念**几乎一一对应**；
> - 更关键的是，**我们自己已经解决过同一个问题** ——
>   `CapabilityRegistry` 的注释写着「26.2: CCA 已移除，改用
>   `DataHolderCapabilityProvider` 内置的 WeakHashMap 存储」，
>   即本仓库已有一套可复用的「按实体挂数据」的现成方案。
>
> 也就是说：**照着 fork 的 attachment 化改法，套用我们已有的 DataHolder 模式即可**，
> 不需要从零设计。

**2. 可直接跳过的模块（fork 已验证「删掉也能跑」）**

该 fork 删除了 18 个文件、新增 6 个，净 149 → 137。被删的里有一整块是
`compat/player_animator/`（6 个文件）+ 相关 mixin（`PlayerAnimatorAssetManagerMixin`、
`MinecraftMixin`）+ 网络消息（`SMeleeAnimationSync`、`SResetMeleeSyncMessage`）
+ `IPAAssetManager`。

→ **印证了此前判断：`compat/` 第一版可整个跳过**，且已有实例证明跳过后功能仍成立。

同时删掉的还有 `BackstabEnchantment`（附魔系统改动大）、
`MeleeAnimationStateContext`、两个 event 类。

**3. 新增的 6 个文件 = 新版本必须补的东西**

`ClientModEvents`、`BlindnessOverlay`、`LrJeiSubtype`（替换 `LrSubType`）、
`ModCapabilities`、`ItemAccessor`（mixin accessor）、`ResourceLocationSerializer`。

→ 其中 `ItemAccessor` 与 `ResourceLocationSerializer` 提示：
**新版本对 `Item` 私有字段访问、以及 `ResourceLocation` 的序列化方式有变**，
这两点我们在 26.2 上大概率也要处理。

**4. 枪包配方兼容的现成方案**

其 README 明写：

> *Recipes in other gun packs are broken by default. You need to use the
> **gun pack upgrader mod developed by MUKSC** to upgrade those packs...*

→ 直接对应本文**第一节**的问题。他们的解法正是本文推荐的「工具路线」，
且社区已有现成工具（MUKSC 的 TaCZ Pack Upgrader）。
**若要做转换器，应先研究该工具，而非从零写。**

### 2.7 建议路径（据上述情报修订）

```
0. 【已明确】不打包美术资源，走纯前置框架路线
   —— LRTactical 架构天然支持（内容全由 index/* 数据驱动，
      代码仅注册 5 个空壳物品；空索引不崩，只需修标签页硬编码图标）
1. 以 Nahiyus512 fork 作为「改动地图」，而非从 1.20.1 原版直接起步
   —— 它已经把 1.20.1 -> 1.21.1 这段路走完了
2. 最小闭环：entity + item 跑通「一颗能扔的手雷」
3. capability -> attachment：照 fork 的改法，套用本仓库既有的 DataHolder 模式
4. compat/ 跳过（fork 已验证可删）
```

**不建议一上来全量翻译 137/149 个文件。**

---

## 三、关于 TacZ:Arcana

用户提到它开源。但从此前排查看：**Forge 专有 + All Rights Reserved 授权 + 闭源解密逻辑**
（CurseForge 页面标注 License 为 All Rights Reserved）。
即便移植，加密格式与密钥也不公开。

**优先级明显低于 LRTactical，且法律风险更高。**

---

## 四、LRTactical 移植：已知的「缺失反馈层」（非 bug）

用户实测反馈「蓄力动作与手雷初速度/加速度对不上，蓄力时间和这两者没有对应」。
**逐条比对上游源码后确认：这不是移植 bug，而是两件事。**

### 4.1 上游本来就没有「蓄力影响初速度」

grep 上游全仓（1.21.1 fork）确认：

- 初速度只有一处来源：`ThrowableType#createEntity` 里
  `entity.shootFromRotation(entity, xRot, yRot, 0.0F, initialSpeed, 1.0F)`，
  其中 `initialSpeed` 直接取自数据包的 `initial_speed`；
- 唯一的修正项是**潜行**：`initialSpeed *= CROUCHING_INIT_SPEED_PERCENT`（默认 0.5）；
- 全仓 `getTicksUsingItem()` 的 9 处调用**没有任何一处**参与速度计算 ——
  它只用于：预燃扣血（`life`）、是否达到 `prepare_time`、以及 HUD 进度条。

也就是说，**手雷不是弓**：按住不会蓄力增程。按住的唯一效果是
「预燃」——`onThrow` 里 `life -= (ticksUsingItem - prepareTime)`，
即按得越久，飞出去后**越快爆炸**（引信变短），而不是飞得越远。

> 若确实想要「蓄力增程」，那是**新增特性**而非修复，
> 需要改 `ThrowableType#createEntity` 的签名把 `ticksUsingItem` 传进去。
> 本移植定位是忠实移植，故**不擅自加**。

### 4.2 真正缺的是 HUD 反馈（`UsingProgressOverlay`）

上游有一个 `client/overlay/UsingProgressOverlay`，在准星下方画一条 32px 进度条：

- 白条 = 拔销进度（`ticksUsingItem / getMaxUsingTick`，即 `prepare_time`）；
- 红条 = 预燃进度（`(ticksUsingItem - prepareTime) / life_time`），
  满格后还会用 `sin` 做闪烁警告；
- 潜行时额外画一个箭头图标，提示「当前是减速投掷」。

**这个类尚未移植**，所以玩家完全看不到蓄力状态 ——
主观上就会觉得「蓄力没有任何反馈 / 和初速度对不上」。

未移植的原因：26.2 的 HUD 层是 `Hud#extractRenderState` + `GuiGraphicsExtractor`
（与 1.21.1 的 `GuiGraphics` 完全不同），且上游那个 overlay 依赖
原作的箭头贴图（All Rights Reserved，本移植不打包）。
按「一次只引入一个变量」的原则，留待客户端 HUD 层单独一轮处理。

**结论**：4.1 是预期行为（与上游一致），4.2 是待补的反馈层。
两者都不影响手雷的实际功能。

---

## 五、近战武器子系统：移植工作量评估（调研结论，尚未动工）

回答「近战武器还没做，移植大概要做什么」。**本节只记录已核对过的事实**，
凡未验证的一律标注为待查。

### 5.1 范围：14 个文件、约 1700 行，外加一个此前未列入清单的依赖包

| 模块 | 文件 | 行数 | 备注 |
|---|---|---|---|
| 数据层 | `item/melee/{MeleeWeaponType,MeleeWeaponData,CombatData}` | 206 | 纯 POJO + 一个自定义 Deserializer |
| 索引 | `item/index/MeleeWeaponIndex` | 193 | |
| 加载器 | `resource/manager/MeleeIndexManager` | 77 | 可照抄已完成的 `ThrowableIndexManager` |
| API | `api/item/IMeleeWeapon` | 248 | **改动最大**，见 5.2 |
| API | `api/melee/{MeleeAction,AttackResult}` | ~30 | 纯枚举 |
| **碰撞** | `api/collision/{ITargetFilter,ConeFilter,RayFilter,OBBFilter,OBB}` | **421** | **此前漏列**，见 5.3 |
| 物品 | `item/MeleeItem` | 227 | |
| 状态 | `capability/CombatProperties` | 250 | 连招/冷却状态机，依赖网络层 |
| 网络 | `network/message/{CMeleeAttackRequest,CPrepareMeleeAttack}` | 142 | **C2S**，本模块首次出现 |
| 客户端渲染 | `client/renderer/item/MeleeItemRenderer` + `display/*` | 341 | 依赖 TACZ 动画管线，建议**跳过** |

> **注意**：此前 `docs` 与提交信息里把近战列为「3 个文件」（`CombatProperties`/
> `IMeleeWeapon`/`MeleeAction`），**那个估计是错的** —— 实际还牵连
> `api/collision/` 整个包（421 行几何代码）与两个 C2S 包。

### 5.2 26.2 破坏性变更（已逐条对字节码核实）

`IMeleeWeapon#performAttack` 是重灾区，**几乎每一行都要改**：

| 上游写法 | 26.2 实际 | 影响 |
|---|---|---|
| `Entity#hurt(...)` 返回 `boolean` | 返回 **`void`**；判定入口是 `hurtServer(ServerLevel,DamageSource,float)->boolean` | 上游靠返回值判断「是否真的打中」来决定要不要击退/附魔后效，**逻辑要重构** |
| `registryAccess().registryOrThrow(...)` | 改名 **`lookupOrThrow`** | 取附魔 Holder 的整条链要重写 |
| `Registry#getHolderOrThrow` | 已无此名，改用 `getOrThrow`/`get(ResourceKey)->Optional<Holder.Reference>` | 同上 |
| `igniteForSeconds(int)` | **`igniteForSeconds(float)`** | 火焰附加 |
| `CommonHooks.onPlayerAttackTarget` | **NeoForge 专有，Fabric 无** | 直接删（本仓库无等价事件） |
| `CommonHooks.fireCriticalHit` + `CriticalHitEvent` | **NeoForge 专有** | 跳劈暴击需**自行实现**原版公式 |
| `EnchantmentHelper.getEnchantmentLevel` | 仍在，签名 `(Holder<Enchantment>, LivingEntity)` | 取 Holder 的方式变了（见上） |
| `IClientItemExtensions` | **NeoForge 专有** | 客户端渲染扩展，随渲染层一起跳过 |
| `ToolAction` / `ToolActions` | NeoForge 专有 | **上游自己已注释掉**，无需处理 |
| `PacketDistributor.sendToServer` | Fabric 用 `ClientPlayNetworking.send` | 已有现成模式 |

### 5.3 `api/collision/` —— 唯一需要「真正从零写」的部分

`CombatData` 的 `hitbox` 字段是 `ITargetFilter`，有三种实现：
`cone`（锥形）/ `ray`（射线）/ `obb`（有向包围盒）。

- `ITargetFilter` / `ConeFilter` / `RayFilter` 用的都是原版 `Vec3`/`AABB`/`ClipContext`，
  **26.2 均无变化**，属于纯几何代码，可以近乎逐行照搬；
- `OBB`（137 行）是自实现的分离轴定理，**不依赖任何 MC API**，可原样搬运；
- 但 `ITargetFilter.Deserializer` 是**按 `type` 字段分派的多态反序列化**，
  必须在 `CommonAssetsManager.GSON` 上注册 —— 与已完成的 `Identifier` 适配器同一处。

> **好消息**：本仓库 TACZ 侧已有一份**26.2 上跑通的锥形近战判定**
> （`ModernKineticGunItem#doMelee`，含视角向量、锥角判定、`hasLineOfSight` 遮挡检查、
> `causeFoodExhaustion`），与 `ConeFilter` 语义高度重合。
> 按 PORTING_NOTES 第 9 节「照抄同仓库已有写法」的原则，
> **`ConeFilter` 应当以它为准，而不是照搬上游**。

### 5.4 建议的实施顺序（每步一个变量，可独立验证）

1. **几何层**：`api/collision/*` 5 个文件 + Gson 适配器注册 —— 无 MC API 风险，先行落地；
2. **数据层**：`MeleeWeaponData`/`CombatData`/`MeleeWeaponType`/`MeleeWeaponIndex`/`MeleeIndexManager`
   —— 照抄已完成的投掷物同名结构，**顺带接上本轮新建的网络同步通道**（`ServerMessageSyncLrPack`
   已经是「一个包带一张表」的结构，加第二张表即可，不必新建包）；
3. **物品层**：`MeleeItem` + `IMeleeWeapon`（先只做**服务端直接判定**的简化版，
   跳过 `CombatProperties` 连招状态机）—— 此时应能「拿刀左键打死怪」；
4. **状态机 + C2S**：`CombatProperties` + 两个请求包 —— 解锁连招、蓄力、位移；
5. **渲染/动画**：依赖 TACZ `AnimateGeoItemRenderer` 与原作受限美术资源，
   **建议长期跳过**（与投掷物的处理一致，用原版物品模型）。

**第 1–3 步是「能用」的最小闭环**，约 900 行；第 4 步再加 400 行。

### 5.5 两个必须提前决策的点

1. **暴击怎么办**：上游依赖 NeoForge 的 `CriticalHitEvent`。Fabric 无等价事件，
   要么自行实现原版跳劈公式（1.5 倍），要么**直接不做暴击**。
   建议前者，但需明确这是「行为近似」而非「等价移植」。
2. **`Entity#hurt` 返回 void 之后**，「是否真的造成伤害」无法直接得知。
   需要改用 `hurtServer`（仅服务端，返回 `boolean`）——
   而近战判定本来就应当在服务端做，因此可行，但**上游那套
   「客户端索敌 → 服务端执行」的分工要重新对齐**，不能照搬。

> 以上均为**代码阅读 + 字节码核对**的结论，**尚未编译验证**。
> 实施时仍须遵守 PORTING_NOTES 9.1/9.4：逐个 API 核对完整签名与内部调用序列。

---

## 六、剩余 4 种投掷物：移植工作量评估（调研结论，尚未动工）

已完成 `explode`（爆炸雷）。剩下 `sticky` / `smoke` / `stun` / `effect_cloud`。
**本节只记录已核对过的事实**，26.2 变更均经字节码确认。

### 6.1 总览：难度差异极大，不该按同一优先级处理

| 类型 | 实体行数 | 新增外部依赖 | 难度 | 建议 |
|---|---|---|---|---|
| `sticky` 粘性雷 | 242 | **无**（继承已移植的 `GrenadeEntity`，复用 `ExplodeThrowableData`） | ★☆☆ | **优先做** |
| `smoke` 烟雾弹 | 55 | 自定义粒子（1 个 `SimpleParticleType` + 1 个粒子渲染类） | ★★☆ | 次之 |
| `effect_cloud` 效果云 | 151 + 86 | 需额外移植 `SpEffectCloudEntity`（继承原版 `AreaEffectCloud`） | ★★☆ | 次之 |
| `stun` 闪光弹 | 95 | **自定义状态效果 ×2 + 客户端渲染/音频层** | ★★★ | **见 6.4，需先决策** |

### 6.2 `sticky`（粘性雷）—— 唯一没有新依赖的

`StickyGrenadeEntity extends GrenadeEntity`，数据也复用 `ExplodeThrowableData`
（`StickyType#createEntity` 与已移植的 `ExplodeType` 逐行几乎相同）。
新增的只是「粘附」逻辑：命中实体/方块后固定位置、跟随被粘的实体。

已知 26.2 变更（与已踩过的坑同类）：
- `EntityType.Builder` 三处：`setShouldReceiveVelocityUpdates` 已移除、
  `setTrackingRange/setUpdateInterval` → `clientTrackingRange/updateInterval`、
  `build(String)` → `build(ResourceKey)`；
- 它 import 了 `ModSounds`（原作受限音效），本移植不打包 → 删掉该行即可，
  与 `ThrowableItemEntity` 里已处理的 `GRENADE_BOUNCE` 同样处理。

> **这是 4 种里唯一可以「当天做完当天测」的**，建议单独一轮。

### 6.3 `smoke` / `effect_cloud` —— 需要新增基础设施，但都是标准 API

**`smoke`**：实体本身只有 55 行（tick 到 40 时刷粒子）。真正的工作量在
**自定义粒子**：需要 `BuiltInRegistries.PARTICLE_TYPE` 注册 +
客户端 `ParticleFactory`。本仓库已有先例（`ParticleFactories.registerParticles()`），
照抄即可。已确认 `SimpleParticleType(boolean)` 构造在 26.2 未变。

**`effect_cloud`**：除实体外还要移植 `SpEffectCloudEntity`（继承原版 `AreaEffectCloud`）。
已确认 26.2 的 setter 基本齐全（`setRadius/setDuration/setWaitTime/
setRadiusPerTick/setOwner/addEffect`），但有一处变更：

| 上游 | 26.2 |
|---|---|
| `cloud.setParticle(...)` | **`setCustomParticle(ParticleOptions)`**（字节码确认，另有 `DATA_PARTICLE`/`getParticle`） |

另需注意 `MobEffectInstance` 的构造参数是 **`Holder<MobEffect>`** 而非裸 `MobEffect`
（与近战里 `ItemAttributeModifiers` 要 `Holder<Attribute>` 是同一类变更）。

上游还依赖一个 S2C 包 `SSplashParticle`（喷溅粒子）——
可复用本模块已有的 `ServerMessageSyncLrPack` 所在的网络层，加一个包即可；
或者退化为服务端直接 `ServerLevel#sendParticles`（已确认签名，两个 boolean 版本）。

### 6.4 `stun`（闪光弹）—— **必须先做决策，否则做了等于没做**

实体本身只有 95 行，逻辑不复杂（按距离+视线夹角算致盲时长）。
**但它的实际效果 100% 依赖客户端表现层**：

- `ModEffects.BLIND` / `DEAFENED` 这两个 `MobEffect` **本身是空壳**
  （`HarmfulEffect` 全类只有 10 行，就一个构造函数，**没有任何逻辑**）；
- 真正「致盲」的是 `client/overlay/BlindnessOverlay`（往屏幕糊一层白/黑）；
- 真正「耳鸣」的是 `client/audio/SoundHandler` + `StunRingingSound`
  （拦截所有音效音量 + 播放耳鸣音）。

也就是说：**只移植实体和效果注册，玩家扔出闪光弹后除了图标什么都不会发生。**

三个选项：

1. **连客户端表现层一起做**（推荐）。致盲遮罩不难 ——
   26.2 HUD 层虽换成 `Hud#extractRenderState` + `GuiGraphicsExtractor`，
   但本仓库已有 5 个 overlay 的现成写法可抄（`GunHudOverlay` 等）。
   耳鸣音效属原作受限素材，**不打包**，可降级为「只有致盲、无耳鸣」，
   或用原版音效替代（需明确这是行为近似）。
2. **只做服务端逻辑，明确标注"需内容包自行提供表现层"**。
   代价是自带示例闪光弹会显得"坏掉了"。
3. **暂时跳过**，优先级排在 `sticky`/`smoke`/`effect_cloud` 之后。

> 无论选哪个，都**不应该默默地只移植一半** —— 那正是 PORTING_NOTES 9.1
> 说的「职责没人接手的静默失效」。

### 6.5 共通的 26.2 变更清单（4 种都要改）

1. **`EntityType.Builder` 三处**（同 6.2，每个实体都要改）；
2. **每个新实体都必须注册渲染器**，否则客户端一进视野就 NPE 崩溃 ——
   这个坑第 5 步已经踩过一次（`GrenadeEntity` 忘了注册渲染器导致「扔出即崩」）；
3. **每个新实体类型都要在 `ModEntities` 显式注册**，且 `init()` 必须被调用
   （Fabric 无 `DeferredRegister`，类加载惰性）；
4. **新 type 要在 `ModCustomTypes` 注册**，否则数据包里写了该 type 会报
   "Unknown throwable type"；
5. 若新增 `MobEffect`/`ParticleType`，同样需要显式 `Registry.register`。

### 6.6 建议顺序

```
第 1 轮：sticky            —— 无新依赖，验证「新增一种投掷物」的完整链路
第 2 轮：smoke + 自定义粒子 —— 顺带把粒子基础设施建起来
第 3 轮：effect_cloud      —— 复用第 2 轮的粒子设施
第 4 轮：stun              —— 视 6.4 的决策而定，可能连带做 HUD 层
```

第 1 轮同时充当「回归测试」：如果新增一种投掷物的链路是通的，
后面三种就只是重复同样的步骤 + 各自的特有依赖。

---

## 七、渲染/动画层审计：能不能照搬 TACZ 的动作类？

**结论：能，而且比预期好 —— 基础设施在 26.2 上是活的、可用的。**
唯一的阻碍不是技术，而是**美术资源授权**。

### 7.1 上游渲染层的依赖，全部存在于本仓库

上游 `MeleeItemRenderer` 依赖 11 个 TACZ 类。逐个核查本仓库
`src/main/java/com/tacz/` 的结果：

| TACZ 类 | 本仓库 | 说明 |
|---|---|---|
| `AnimateGeoItemRenderer` | ✅ | 动画渲染器基类 |
| `BedrockAnimatedModel` | ✅ | Bedrock 模型 + 动画 |
| `SlotModel` / `BedrockPart` | ✅ | |
| `FunctionalBedrockPart` / `IFunctionalRenderer` | ✅ | 部件级自定义渲染 |
| `ModelRendererWrapper` | ✅ | |
| `LeftHandRender` / `RightHandRender` | ✅ | 手臂绑定 |
| `GunModelConstant` | ✅ | `LEFTHAND_POS_NODE` 等常量名<b>未变</b> |
| `LuaAnimationStateMachine` | ✅ | Lua 动画状态机 |
| `BeforeRenderHandEvent` | ✅ | |

**11 / 11 全部存在**，且关键签名与上游一致（均已核对）：
- `AnimateGeoItemRenderer<M extends BedrockAnimatedModel, CTX extends ItemAnimationStateContext>`
  —— 泛型上界与上游相同；
- 只有两个抽象方法要实现：`initContext(ItemStack, Player, float)` 与
  `updateContext(CTX, ItemStack, Player, float)`；
- `BedrockAnimatedModel(BedrockModelPOJO, BedrockVersion)` 构造未变。

### 7.2 这条管线在 26.2 上**确实在跑**（不是死代码）

这一点比「类存在」更重要。核查链路：

```
AbstractGunItem#getCustomRenderer()        (第 375 行)
    -> GunItemRendererWrapper.INSTANCE.get()
    -> extends AnimateGeoItemRenderer      (唯一子类)
```

接入点是 `cn.sh1rocu.tacz.api.extension.IItem#getCustomRenderer()`，
返回 Fabric 的 `BuiltinItemRendererRegistry.DynamicItemRenderer`。
**枪械的第一人称动画已经过实测**，说明整条 Bedrock 动画管线在 26.2 上是通的。

> 对比：上游用 NeoForge 的 `IClientItemExtensions.of(stack).getCustomRenderer()`，
> 本仓库已把这个接入点换成了 Fabric 版的 `IItem#getCustomRenderer()`。
> **这正是移植 `MeleeItemRenderer` 时唯一需要改的接口调用**。

### 7.3 那么真正的阻碍是什么

**只有一个：模型与动画文件本身。**

`AnimateGeoItemRenderer` 要工作，需要内容包提供：
- Bedrock 模型（`.geo.json`）
- 动画文件（`.animation.json`）
- 贴图
- `MeleeDisplayInstance`（上游的 display 配置，含 `sounds` / 模型路径 / 动画状态机脚本）

这些在上游全部属于 **`Art Assets: All Rights Reserved`**，本移植不打包。
也就是说：**代码照搬得过来，但没有素材可渲染。**

### 7.4 结论与建议

原先「渲染层长期跳过」的判断，理由写的是「依赖 TACZ 动画管线」——
**这个理由是错的**，管线本身完全可用。正确的理由是「无美术资源」。

这个区别很重要，因为它改变了结论：

| | 原判断 | 审计后 |
|---|---|---|
| 技术可行性 | 以为要重写管线 | **照搬即可，只改一处接口调用** |
| 对纯前置框架的价值 | 无 | **有** —— 内容包作者需要它 |
| 建议 | 长期跳过 | **值得做**，见下 |

**建议做法**：移植 `MeleeItemRenderer` / `ThrowableItemRendererWrapper`
与对应的 `*DisplayInstance` / `*DisplayManager`，
但**不打包任何美术资源**。效果是：
- 没装内容包时 → 沿用现在的原版物品模型（与当前行为一致，不退化）；
- 装了内容包 → 内容包自带的 geo/animation 能真正生效。

这与本移植「纯前置框架」的定位一致 —— 框架该提供**能力**，
内容由第三方提供。反之若不移植渲染层，
内容包作者即便做好了模型也**无法使用**，框架就是残缺的。

**工作量估算**（未含 stun 的 overlay）：
`MeleeItemRenderer` 180 行 + `ThrowableItemRendererWrapper` 183 行
+ `CustomBedrockModel` 72 行 + `JumpSwayUtil` 65 行
+ 3 个 `DisplayInstance`（~370 行）+ 3 个 `DisplayManager`（~105 行）
≈ **975 行**，但绝大部分是<b>逐行照搬</b>，主要改动为
`ResourceLocation`→`Identifier`、`IClientItemExtensions`→`IItem#getCustomRenderer`。

> **注意**：`DisplayManager` 读的是 `assets/` 下的资源包路径，
> 与已完成的 `index/*` （`data/` 下）是<b>两套不同的加载通道</b>。
> 这一点在动手前需要单独核对 26.2 的客户端资源重载 API
> （本仓库 `ClientAssetsManager` 有现成写法可抄）。

### 7.5 【实施后修正】审计低估了工作量：管线本身也变了

上面 7.4 的结论「**照搬即可，只改一处接口调用**」，实施后证明是**错的**。
11 个依赖类确实全都在，但**本仓库的 `AnimateGeoItemRenderer` 已被 26.2 重写过一轮**，
方法签名与渲染模型都不同。这是「只查类名存在与否、没查方法签名」的又一次翻车，
与 PORTING_NOTES 9.6「存在 ≠ 能用」是同一类错误 —— 只是这次的粒度从
「access flag」上升到了「整个渲染模型」。

**实际差异清单**（全部以本仓库 26.2 代码为准）：

| 上游 1.21.1 | 本仓库 26.2 | 照抄的后果 |
|---|---|---|
| `MultiBufferSource bufferSource` | `SubmitNodeCollector collector` | 编译失败 |
| `model.render(...)` | `model.submit(...)` | **编译通过但什么都不画** —— `render` 已是标 `@Deprecated` 的空实现 |
| `RenderType.entityCutout` | `RenderTypes.entityCutout`（类名多 s，包也变） | 编译失败 |
| `client.renderer.block.model.ItemTransforms` | `client.resources.model.cuboid.ItemTransforms` | 编译失败 |
| `transform.apply(false, poseStack)` | `apply(isLeftHand, poseStack.last())`，且内部**自带** `translate(-0.5,-0.5,-0.5)` | 模型偏移 + 左手镜像错误 |
| `new ItemTransforms.Deserializer()` | **构造器已降为包级私有** | 编译失败 → 改用仓库既有的 `BlockTransformParser` |
| `EntityRenderer<T>` | `EntityRenderer<T, S extends EntityRenderState>`，`render` 拆成 `extractRenderState` + `submit` | 编译失败 |
| `getTextureLocation(entity)` | **已从 `EntityRenderer` 移除** | 编译失败 |
| `itemRenderer.renderStatic(...)` | `ItemModelResolver#updateForTopItem` + `ItemStackRenderState#submit` | 编译失败 |
| NeoForge `RenderHandEvent` 驱动第一人称 | `ItemInHandRendererMixin#submitArmWithItem` 拦截驱动 | 第一人称完全不走自定义渲染 |
| `SimpleParticleType`/`@EventBusSubscriber` 注册 display | Fabric 无事件总线，需显式 `ResourceManagerHelper` 注册 + `getFabricDependencies` 排序 | display 偶发加载失败 |

**结论修正**：这条经验应记为 ——
> 「依赖类是否存在」只能证明**不需要重写**，不能证明**可以照搬**。
> 判断工作量必须比对**方法签名**，且要特别留意「签名没变、语义变了」
> （如 `render` 变空实现）这类**不报错的**改动。

### 7.6 实施记录（本轮完成）

已移植（约 1600 行，含注释）：

| 文件 | 说明 |
|---|---|
| `api/animation/{BaseAnimationStateContext,ThrowableAnimationStateContext}` | Lua 脚本可见的上下文 API |
| `client/renderer/model/CustomBedrockModel` | `1p_effect` / `entity_hide` 两类可见性开关 |
| `client/renderer/JumpSwayUtil` | 起跳/落地摆动 |
| `client/renderer/item/{MeleeItemRenderer,ThrowableItemRendererWrapper}` | 主渲染器 |
| `client/renderer/item/LrDynamicItemModel` | 客户端物品模型桥接 |
| `client/renderer/item/HasCustomDisplayProperty` | 「有无内容包」的条件属性 |
| `client/renderer/entity/ThrowableEntityRenderer` | 飞行中的手雷（朝向 + `entity_hide`） |
| `client/resource/**` | display 加载通道（2 个 Instance + 2 个 Manager + 资源管理器） |
| `client/event/LrTickAnimationEvent` | idle/walk/run 状态推进 |
| `client/audio/ICustomSoundSupplier` | display 音效映射 |

**未打包任何美术资源**（上游为 `Art Assets: All Rights Reserved`）。
没装内容包时经 `minecraft:condition` 回退到原版占位模型，行为与本轮之前完全一致。

**顺带修掉的 3 个上游 bug**（均在移植时逐行核对发现）：

1. **`DisplayInstance` 少了 `else`** —— legacy（`format_version 1.10.0`）模型
   先按 LEGACY 建一次、立刻被 NEW 覆盖，旧格式模型全部解析错误。
2. **`JumpSwayUtil` 的 `partialTicks` 可为 0** —— `(posY - yOld) / 0` 产生 `NaN`，
   NaN 参与比较恒 false 导致两个 clamp 分支都进不去，会**永久污染**后续所有帧。
   且原式展开后 lerp 与除法互相抵消，本就是无效运算，直接用等价的 `getY() - yOld`。
3. **`MeleeItemRenderer#updateContext` 从不调 `setCurrentItem`** ——
   Lua 侧 `getStackCount()` 永远看到 `EMPTY`。

---

## 八、投掷物移植完成情况（五种类型全部就绪）

| 类型 | 状态 | 备注 |
|---|---|---|
| `explode` 爆炸雷 | ✅ | |
| `sticky` 粘性雷 | ✅ | 继承 `GrenadeEntity`，与 explode 共用数据 |
| `smoke` 烟雾弹 | ✅ | 自建粒子设施，贴图复用原版 `generic_0..7` |
| `effect_cloud` 效果云 | ✅ | 持续云 + 一次性喷溅两种形态 |
| `stun` 闪光弹 | ✅ | 致盲 + 消声；**耳鸣音效待素材** |

### 8.1 闪光弹消声：一个比上游更简单的实现

上游 `SoundHandler` 用**反射 + 每 tick 遍历所有播放中的声音**逐个改音量，
还要维护「原始音量表」以便恢复，并依赖两个 NeoForge 专有事件。

本移植发现更好的切入点：字节码确认
`SoundEngine#calculateVolume(SoundInstance)` 内部**直接转调**
`calculateVolume(float, SoundSource)`，后者是**所有音效音量的单一收敛点**。

```java
@ModifyReturnValue(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"))
private float lrtactical$applyDeafen(float original, float volume, SoundSource source) {
    return original * DeafenState.getVolumeFactor(source);
}
```

一个注入点解决全部问题，且**无反射、无需恢复原值、无需特判 TickableSoundInstance**。

> **可复用的经验**：遇到「要批量修改某类数值」的需求时，
> 先找**该数值的计算收敛点**，往往一处注入就够，
> 比在消费端逐个拦截可靠得多。

### 8.2 待补：耳鸣音效

消声（压低其他声音）与耳鸣（播放持续音）是**两件独立的事**，
目前只做了前者 —— 它已是「被震聋」的主要感受来源。

音效到位后的补法很小：
1. 把 `.ogg` 放到 `assets/lrtactical/sounds/`；
2. 建 `assets/lrtactical/sounds.json` 索引；
3. 加一个 `TickableSoundInstance`，在 `DEAFENED` 存在时播放、
   音量随剩余时长衰减（可参考上游 `StunRingingSound`，41 行）。

**格式要求**：Minecraft 只认 **`.ogg`（Vorbis）**，`.mp3`/`.wav` 会加载失败；
且耳鸣音最好**可无缝循环**（首尾衔接无爆音），因为要按致盲时长持续播放。

### 8.3 耳鸣音效已接入（素材来源与处理记录）

音源由用户提供（Freesound 公开素材）。原始为
**48 kHz / 单声道 / 32-bit float / 20.04 秒 WAV**。

**Minecraft 只接受 OGG Vorbis**，`.wav`/`.mp3` 会加载失败，因此必须转换。
沙盒内无 `ffmpeg`/`oggenc`，改用 PyPI 的 `soundfile`（libsndfile 绑定）：

```python
import soundfile as sf, numpy as np
data, sr = sf.read(src, dtype='float32', always_2d=True)
mono = data.mean(axis=1)
seg = mono[mid : mid + 8*sr]                 # 取中段，避开原素材淡入淡出
t = np.linspace(0, np.pi/2, fade)            # 等功率交叉淡化（线性会在中点掉音量）
seg[:fade] = seg[:fade]*np.sin(t) + tail*np.cos(t)
sf.write(out, seg/peak*0.707, sr, format='OGG', subtype='VORBIS')
```

结果：**3.8 MB → 28.5 KB**，7.50 秒，首尾差 0.161 → **0.033**（接缝无爆音）。

> **原始 WAV 不入库** —— 3.8 MB 的中间产物没有保留价值，
> 成品 OGG 已在 `assets/lrtactical/sounds/`。本节记录处理参数，
> 便于日后换素材时复现。

**一个必须处理的自噬问题**：耳鸣声是在 `DEAFENED` 期间播放的，
若被消声 mixin 一并压低，就成了「什么都听不见」而非「耳朵在响」。
为此把注入点从 `calculateVolume(float, SoundSource)` 换到
`calculateVolume(SoundInstance)` —— 后者能拿到实例以判断是否豁免，
且**仍是同一个收敛点**（前者就是被后者转调的）。

---

## 九、曳光弹位置问题（2026-07-30 修复验证）

**症状**：第一人称下曳光弹起点不从枪口射出，而是固定在世界某一处，或与枪口有明显偏移；转视角后仍偏。

**根因梳理**（已逐轮核对上游 1.21.1 `EntityBulletRenderer`）：

1. **第 9 轮**：摄像机旋转被硬编码为 0，导致“旋转->平移->反旋转”退化为未旋转坐标系平移，起点固定。已修为 `Minecraft.getInstance().gameRenderer.mainCamera()`。
2. **第 10 轮后**：仍有偏移。排查发现 `muzzleRenderOffset` 本是摄像机局部坐标，上游在非 Iris 下直接平移（实体已在视图空间），Iris 下才做 YN/XN 旋转。移植版曾尝试用 `Camera#rotation()` 预烘焙为世界坐标，实测引入新的漂移（quaternion 与 Euler 顺序/符号不一致，且 26.2 实体仍在视图空间）。
3. **渲染时序**：`LevelRenderer` 先渲染实体、后渲染手部（`ItemInHandRenderer`），子弹第一帧读到的 `muzzleRenderOffset` 是上一帧的枪口位置，会有 1 帧延迟。但该延迟在 50 格线性衰减下影响有限。

**本轮修复**（`EntityBulletRenderer.java`）：

- 回退到上游原始逻辑：缓存原始 `muzzleRenderOffset`（不做 `rotate(camera.rotation())`），缓存开火时刻的 `camera.xRot()/yRot()`，`offsetReducer = (50 - disToEye)/50` 线性。
- Iris/Sulkan 均走旋转分支：`YN(y+180) + XN(x) -> translate -> XP(x) + YP(y+180)`，与上游一致；非光影直接平移。
- 保留 `energySwirl` 渲染类型与满亮 block light（Alpha 2 已修复）。

> **实测建议**：静止连续开火，观察起点是否始终偏同一处（坐标系问题）或仅第一发偏（时序问题）。若 ADS 横移时偏移过高，可把 50 改为 35~40 线性，而非 12 二次（后者起点虽对，但快速贴回弹道，主观上仍感觉“从胸口出来”）。

**已知限制**：子弹实体生成位置仍在眼睛下方 0.1（上游设计，弹道需与准星一致），视觉上从枪口出来仅是第一人称的偏移补偿，第三人称无补偿（上游原生表现）。

---

## 十、三症状同案终审：26.2 手部 pass 基座旋转 + 基岩模型法线二乘（2026-08-10）

本轮把三个「修了几轮没修掉」的历史问题放在一起从 26.2 jar 字节码层面重新推导，
结论：**问题①③同根因（跨 pass 坐标系污染），问题②独立（法线二次变换）**。
文档此前各轮的相互矛盾结论（第 24 轮「世界向量」/第 25 轮「视图空间常量」）
均以本节 26.2 字节码事实为准。

### 26.2 底层渲染变换事实（全部经反汇编确认）

- `Camera.extractRenderState` → `CameraRenderState.viewRotationMatrix =
  R(camera.rotation().conjugate())`（world→view；`rotation()` 本身是 view→world，
  `Camera#setRotation` 内 `rotationYXZ(PI - yaw, -pitch, 0)`，`FORWARDS.rotate(rotation)`）。
- `LevelRenderer.render(...)`：开头 `modelViewStack.pushMatrix(); mul(viewRotationMatrix)`，
  实体提交只做 `translate(entityPos - cameraPos)`——**实体 pass 的 PoseStack 平移是世界轴**，
  相机旋转在绘制时由 modelView 统一施加（与第 25 轮实测 `poseBefore ≡ bulletPos - eye` 吻合）。
- `GameRenderer.renderLevel` 尾部 `renderItemInHand(cameraState, tickDelta, viewRotationMatrix)`：
  先 `RenderSystem.setProjectionMatrix(hud3dProjection(hudFov…))` 再调用。其方法体开头：
  ```java
  poseStack.mulPose(new Matrix4f(viewRotationMatrix).invert()); // 手部 PoseStack 基座 = view→world
  modelViewStack.pushMatrix(); mul(viewRotationMatrix);          // modelView = world→view
  ```
  两个互相抵消，所以所有手持物**画出来都对**——但 `submitHandsWithItems` 里只有两条
  `0.1` 系数的 bob `mulPose`（第 25 轮看到的就是这两条，于是漏了更外层的基座），
  **手部 pass 内的 pose.last().pose() 不再是 1.21.1 的纯视图空间**。
- Iris 开光影后手部改由其 `HandRenderer` 直接调 `ItemInHandRenderer.renderHandsWithItems`，
  **绕过 `GameRenderer.renderItemInHand`**，没有这段基座预乘——这是
  「vanilla 异常、光影下正常」这一症状签名的来源。

### 问题① 曳光弹起点不锁枪口 & 问题③ 开火弹道视觉固定向左/右偏（同根因）

- `GunItemRendererWrapper.cacheMuzzlePosition` 在手部 pass 里读
  `pose.last().pose().m30/31/32`：vanilla 26.2 下读到的是 **R(q)·v（世界轴）**，
  不是上游 1.21.1 的视图空间 v（`latest.log` 中 `liveMuzzle` 随 yaw 在 ±1.8 间正弦摆动即为此）。
- `EntityBulletRenderer`（实体 pass，世界轴 pose）再 `rotate(camera.rotation())` 一次 →
  实际平移量是 **R(q)²·v**，枪口起点按二倍朝向角偏转：
  面南 yaw=0 误差 0；斜向 1.26 格；正东西 2.33 格；面北 3.30 格（已按
  v≈(0.16,−0.19,−1.64) 数值模拟，与「斜向固定严重向左/右偏」吻合；
  深度方向误差在视觉上不易归为左右偏，所以用户报告集中在斜向）。
- 开相机后坐力走的是 `player.setXRot/setYRot` 样条（方向无关、与光影管线无关），
  数学上不可能产生「斜向固定偏、光影下正常」——问题③是问题①在开火窗口期的观感。
- **修法（采集端归一化 · 自校正版）**：在 `renderFirstPerson` 入口记录基座矩阵 B
  （vanilla 下 B≈R(q)；Iris 手部 pass 下 B≈I），`cacheMuzzlePosition` 用
  `Bᵀ · (m30..m32 − B.m30..m32)`（B 正交 → 逆 = 转置）把采集位移解算回纯视图空间，
  恢复 `muzzleRenderOffset` 的上游不变量。
  B 与枪口矩阵共享同一条「基座预乘 + bob + 伤害后仰」前缀，因此该还原与管线内部
  状态完全无关，vanilla / Iris / 其他 shader 管线同时正确，无需分支。
  ⚠️ 教训：首版曾用 `RenderSystem.getModelViewMatrixCopy()`（假定其为 W2V）做还原，
  实测无效 —— 26.2 的绘制矩阵经 SubmitNodeCollector / DynamicTransforms 下发，
  RenderSystem 的 modelView 栈仅是兼容残留，内容不能作信源。
  FOV 比值 `tan(itemFov/2)/tan(levelFov/2)` 改乘在**还原后的视图空间 z** 上
  （旧代码乘的是 `pose.m32()`，在 vanilla 26.2 下那是世界轴 Z，开镜补偿的方向就是错的）。
  诊断：`RenderConfig.TRACER_DEBUG` 下每条 `[TACZ MuzzleSpace]` 节流日志会打印
  基座位移、原始/还原后枪口位移、以及手部 pass 内 modelView 的真实 3x3，
  用 yaw≈45°/135° 各打一发即可实证管线行为。

### 问题② 枪身阴影方向不对 / 水平视线枪身过暗（独立根因）

- `BedrockCubeBox.compile` / `BedrockCubePerFace.compile` 先手工
  `vector3f.mul(pose.normal())` 变换法线，再调用 `setNormal(pose, nx, ny, nz)`——
  而 26.2 的 `VertexConsumer#setNormal(Pose,FFF)` 是默认方法，内部
  `pose.transformNormal(x,y,z)` **再变换一次**（字节码确认），法线被施加 N²。
- 效果：`renderFirstPerson` 的 `ZP(180°)` 翻转到法线上被抵消（R·R=I），
  枪身「上表面」在光照计算里实际朝下——`entityCutout` 的两个平行光
  （视角空间固定方向，+y 分量）在水平视线下都照不到顶面 → **视平线高度枪身过暗**；
  且 N 含相机基座 R(q)，未抵消部分使明暗朝向随视角漂移 → **阴影位置不对**。
  vanilla 26.2 `ModelPart$Cube.compile` 只做一次 `pose.transformNormal` 后写裸值，已对齐之。
- **修法**：手工变换一次后直接 `setNormal(nx, ny, nz)`（裸值重载）。

### 验证要点

- TracerDebug（`RenderConfig.TRACER_DEBUG`）下 `globalMuzzle` 现在应在任意 yaw 下
  保持视图空间常量（约 `(0.16, -0.19, -1.6~1.9)`），`poseAfterOffset` 应落在
  `muzzle` 世界位置附近；斜向扫射时弹道视觉不再整体侧甩。
- 水平视线与仰/俯视下枪身顶面亮度应一致；切光影前后枪身明暗不变。

### 实测验证（2026-08-10，a2838e4 构建的 latest.log，vanilla 路径 irisHand=false 全程）

- **渲染侧**：745 条 `[TACZ TracerDebug]` 全部满足 `fpWorldOffset = R(q)·globalMuzzle`
  （最大偏差 0.0067，纯四位小数舍入）——`camera.rotation()` 直接 rotate 的路径符合设计。
- **采集侧（旧 bug 判据）**：旧构建（25 轮调试版）tick=0 的 `muzzleAnchor` 随 yaw 正弦旋转：
  yaw≈−90° → (+1.95, …)；yaw≈−5° → z≈+1.6；yaw≈+85° → (−2.1, …)；yaw≈+189° → (+0.47, −1.56)，
  x 分量活动域 **−2.20 .. +2.18**。新构建全部 745 行、全朝向、含开火/瞄准各动画态：
  `globalMuzzle.x ∈ [−0.13, +0.28]`、`y ∈ [−0.30, −0.13]`、`z ∈ [−1.68, −1.14]`——
  世界轴旋转特征完全消失，剩余散布为动画态族（腰射 z≈−1.53 / fov 动态 −1.32 /
  瞄准 (0.016, −0.136, −1.159)），同族值跨 360° yaw 与四个斜向逐一核对一致。
- **绝对位置交叉验证**：对每条 `[TACZ MuzzleSpace]` 用当帧相机做 `R(q)ᵀ·rawMuzzle`
  （= 该帧枪口在屏幕上的真实视图位置），平稳帧与采集端写出的 `viewMuzzle`
  吻合到小数点后三–四位（err ≤ 0.007）；连射 roll / 快速甩枪帧偏差 0.08–0.22 属预期
  （日志的 `camera=(xRot,yRot)` 不含 CameraMixin 的 ZP roll，且 bob 滞后会进基座 B 的转置归一化）。
  ［注：曾有脚本误用 R(q)·rawMuzzle 复核，误差恰为 4.2 左右——那正是旧 bug 的
  二倍旋转方向，反向印证了根因判定。］
- **modelView 实证**：手部 pass 内 `RenderSystem.getModelViewMatrixCopy()` 实测 == R(q)
  （view→world，平稳帧与相机重建值逐元素吻合）——坐实了「26.2 的 RenderSystem
  modelView 栈内容不可作为坐标信源」，也解释了首版（modelView 剥离）为何无效。

### 衍生案（第 26 轮追加）：曳光弹"整串随朝向缓慢左右摆"——生成包坐标取整

用户在锚点修复后回报：曳光弹西向"大体正确"，北/西北整体偏右、南/东南→东渐偏左，
即随 yaw 平滑正弦摆动的**残留偏置**。排查手法与结论：

- 日志三通道（锚点 `globalMuzzle` 全朝向恒定；`fpWorldOffset ≡ R(q)·globalMuzzle`；
  条带朝向 `finalPose − poseAfterOffset ≡ −velocity`）全部干净 → 渲染侧再无残留，
  矛头转向**弹体实体本身的出生位置**。
- 将 113 发 tick=0 子弹的 `bulletPos − eye` 按 yaw 分桶：**世界轴下恒为
  (-0.309, -0.620, -0.755)** —— 恰为玩家站立点块内小数部分的相反数，即
  客户端收到的子弹出生坐标＝**整块对齐的整数坐标**。
- 字节码实锤，26.2 的 `ClientboundAddEntityPacket.<init>(Entity, int, BlockPos)` 已改为：
  ```java
  this(entity.getId(), entity.getUUID(),
       pos.getX(), pos.getY(), pos.getZ(),   // int → double 强转，取整！
       entity.getXRot(), entity.getYRot(), entity.getType(), data,
       entity.getDeltaMovement(), entity.getYHeadRot());
  ```
  1.21.1 时代该构造器 x/y/z 取自实体精确坐标、BlockPos 只是附属元数据字段；
  26.2 直接拿 BlockPos 顶替 x/y/z（Packet 已无独立 BlockPos 字段，画/展示框的
  客户端处理器就把 x/y/z 当贴块坐标读）。移植库 `IEntityAdditionalSpawnData.
  getEntitySpawningPacket(entity)` 沿用 1.21.1 习惯写法
  `new ClientboundAddEntityPacket(entity, 0, entity.blockPosition())`，在 26.2 下
  静默变成**出生坐标量化 bug**：客户端子弹从脚下方块负角起飞，出生锚点与瞄准眼线
  相差一个与朝向无关的世界轴常向量 → 屏幕投影 `offset·right_world` 随 yaw 正弦，
  正对/背对该向量方位时"回正"——与报告的偏右/偏左/回正方位族谱吻合
  （具体零相位随站立点块内小数变，用户换点测试相位即变）。
  服务端弹道/命中不受影响（全部由服务端精确坐标模拟）；客户端整条预测轨迹
  受影响。（26.2 原生走 `ServerEntity.getPositionBase()` 的两参构造器不受影响。）
- **修复**：`IEntityAdditionalSpawnData.getEntitySpawningPacket(Entity)` 改用公开
  全参构造器显式传 `entity.getX/getY/getZ()` 精确 double。同一调用点的
  `ThrowableItemEntity`（投掷物）一并治愈。
- 诊断增强：TracerDebug 日志新增 `bulletRot=(lerpY,lerpX)`（条带朝向来源的实体
  rot 插值），便于今后一行核对"朝向==速度反向"。

### 用户实测确认（2026-08-10，`fa2297f` 构建）

用户在上一步修复的基础上重新打包进游戏实测，回报**"你成功的修复了这个问题"**——
至此 §十 开案的三症状全部闭环：

| # | 症状 | 根因 | 修复提交 | 状态 |
|---|---|---|---|---|
| ① | 曳光弹渲染位置不固定至枪口处 | 26.2 手部 pass 基座即 view→world 旋转 R(q)，再乘相机旋转等于双重旋转；锚点随 yaw 正弦漂移 | `a2838e4` 入口基座矩阵转置自校正 + FOV 补偿 | ✅ 日志验证 + 用户确认 |
| ② | 枪身阴影方向不对 / 平视过暗 | 基岩模型渲染法线被姿态矩阵与法线矩阵二乘 | `BedrockCubeBox` / `BedrockCubePerFace` 单次法线变换 | ✅ 用户确认（更早轮次） |
| ③ | 曳光弹整串随朝向缓慢左右摆（"后坐力固定偏向"的视觉本体） | 26.2 生成包构造器改用 `BlockPos` 整数坐标顶替 x/y/z，客户端子弹出生点被量化取整 | `fa2297f` 全参构造器传精确坐标 | ✅ 用户确认（本轮） |

遗留的可选收尾（均非缺陷）：Iris 光影开启档（`irisHand=true` 手部 pass 走 Iris
自绘管线）尚未实机回归，建议在光影开启后快速打一梭子确认观感一致；
ADS 高倍率瞄具下曳光弹感观也建议顺带扫一眼。`TRACER_DEBUG` 系列日志默认关闭，
保留无碍，发布前如需精简可整体摘除。
