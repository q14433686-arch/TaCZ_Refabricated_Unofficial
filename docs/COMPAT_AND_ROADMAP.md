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

### 衍生案②（第 27 轮，进行中）：后坐力反馈方向在对角朝向固定左右偏——诊断探针布设

用户回报（`fa2297f` 构建实测）：**开光影时任意朝向后坐力反馈正常；
关光影（vanilla 手部管线）时面向东南/西南/东北/西北四个对角朝向，
后坐力反馈固定严重向左/右偏**。症状指纹：误差∝sin(2θ)，对角最大、正方位为零，
且随渲染管线翻转——与曳光弹案同族（世界系/视系坐标串扰）但非同一处。

**全链静态审计结论（本轮逐项核对源码）**：后坐相关全部通道在构造上都是**朝向无关**的——

1. 摄像机后坐力（`CameraSetupEvent#applyCameraRecoil`）直接加在玩家自身
   `xRot/yRot`（玩家坐标系），无任何朝向输入；
2. yaw 后坐力样条（`GunRecoil#getSplineFunction`）每发 `[min,max]` 均匀随机，
   无朝向输入，无法产生「固定方向」；
3. 手持相机动画（`applyItemInHandCameraAnimation`）为视空间**后乘**四元数，
   对基座内容免疫；
4. 枪口焰/抛壳均在手部**视空间矩阵**内演化；
5. 世界相机动画（`AnimateGeoItemRenderer#applyLevelCameraAnimation`）**存在一处
   已确认的语义错配**：视空间动画四元数被按 **ZYX（航空序）**公式分解欧拉角后
   叠加到世界系欧拉角（yaw 落在最外层世界 Y 轴），而相机复合约定是 **YXZ 内旋序**。
   但其静态误差只随**俯仰角**耦合、与朝向**无关**——与症状指纹不符，
   本轮只记录、**不修复**（避免与待证因的观测混淆）。

静态模型全部干净 + 用户实测存在朝向依赖 ⇒ 静态模型仍有洞，转入运行时取证。
本轮布设 `RecoilDebug` 探针（`config` 客户端节 `RecoilDebug`，默认关）：

| 日志行 | 位置 | 捕获内容 |
|---|---|---|
| `[TACZ RecoilDebug] fire` | `initialCameraRecoil` | 当发 pitch/yaw 样条 8 点包络 + 修正系数 + 朝向 + Iris 状态 |
| `[TACZ RecoilDebug] apply` | `applyCameraRecoil` | 逐帧 dPitch/dYaw 增量、玩家旋转、相机事件角度、Iris 状态 |
| `[TACZ RecoilDebug] levelCam` | `applyLevelCameraAnimation`（基类） | 动画四元数、ZYX 分解结果、事件入值 |
| `[TACZ RecoilDebug] itemCam` | 基类+枪械重载两处 `applyItemInHandCameraAnimation` | 动画四元数 + **叠加前**手部基座 3×3 与平移 |

判读逻辑：`apply` 行对角朝向 ΣdYaw 若仍≈0 ⇒ 摄像机后坐力通道实锤干净，
矛头指向视觉层的第三处写入；`itemCam` 的 `base3x3` 若在 vanilla 管线呈非纯旋转
（分量含缩放/切变、随朝向混合），即锁定 sin(2θ) 来源；Iris 档的对照组
继续豁免则互为印证。上一轮拿用户日志后即按朝向分桶裁决。

**第 27 轮取证分析（用户 19:25 latest.log，15003 行 RecoilDebug）裁决：**

1. **摄像机后坐力本体无罪实锤**：`apply` 行按 8 方位分桶 ΣdYaw/burst
   均值 ∈[−0.12°, +0.08°]、sd≈0.2°，与朝向无关、与光影开关无关；
   开火包络 `yawEnv` 同样各方位零均值随机。玩家旋转写入通道彻底排除。
2. **手部基座无罪实锤**：vanilla `base3x3` 列模偏差 ≤1e-5（纯旋转），
   Iris 基座恒为单位阵；MuzzleSpace 视空间枪口在全部 8 方位
   恒定 ≈(0.0, −0.14, −1.9)。
3. **锁定唯一异常量**：`cameraAnimationObject.rotationQuaternion`。
   - vanilla 下其 x 分量≈幅度×**|sin(玩家yaw)|**：S/N/NW/NE/SE 扇区数百帧
     精确为 0，E/W（±90°）扇区峰值，且连发窗口内（帧相位对齐后）依然成立
     ——不是相位混淆；
   - 而 AK47 `shoot` 动画 camera 轨道**只有纯 z 欧拉键**（x、y 恒 0），
     枪包动画数据无任何 yaw 入口 ⇒ 内容必然在**写入侧**被污染；
   - 该对象同时驱动：① `itemCam` 直接旋转枪体（用户所述「枪体后坐力反馈」
     的直接载体），② `levelCam` 欧拉角叠加到世界相机（E 扇区实测叠加
     yaw≈−0.24°/pitch+0.85°/roll−0.39°，抬升方向偏垂线 ~15°，
     与「固定向左/右偏」的体感吻合）；
   - **开光影时该对象冻结为常量退化态**（整个日志期间恒为同一非单位纯 w 值），
     通道失灵 ⇒ 用户所见即「正常」——光影开关与症状的联动由此闭环；
   - 冷静期该对象呈**指数弛豫回单位四元数**状（w 0.99872→0.99874 逐帧爬升），
     且 |q|<1、vec/w 不匹配——三个已知写入端（clean=单位阵、blend=log/exp/
     normalize、欧拉直写=单位）都不应产生非单位量 ⇒ 另有未知第二写入者或
     混合路径行为异常。
4. **追加写入侧探针（本轮）**：
   - `camWrite`（`CameraRotateListener.update` 每次调用）：输入数组原样 +
     blend 标志 + 写入后四元数 + listener/容器对象身份码（识别并发通道）;
   - `camInit`（`initialValue` 捕获点）：相机节点当前欧拉 + 产出初值；
   - `cleanOrphan`（`cleanCameraAnimationTransform` 调用者核对 +
     30ms 枪械路径心跳，2s 节流，附调用栈）——侦测第二条消费者。
5. 下一轮只需：关光影，原地 5 发 × （正南 / 正东 / 正西） 三组静态扫射，
   换向间不动鼠标。`camWrite` 流将直接回答：x≠0 的帧到底收到了什么输入、
   来自几个通道实例。

---

### 衍生案②·终案（第 27.3~27.5 轮，已破案）：真凶是 `applyAnimationConstraintTransform` 的逐轴约束系数帧混用

**症状终审口径**：「开镜（ADS）+ 开枪时，枪身在四个斜向固定向一侧严重偏移：
东南/西北偏左、东北/西南偏右；正四个朝向、腰射、开任意光影均正常」。

**破案链条**：
1. **正方向三向协议（27.2）**：摄像机动画通道污染被洗清——开枪瞬间通道输入
   在 S/E/W 完全一致（纯 z 欧拉 ±1°，x/y 精确为 0），写入端 blend 逐帧正常；
   疑似「第二消费者」的 cleanOrphan 仅换模型时触发一次，无害。
2. **四斜向镜像协议（27.5，关键证据）**：新增 `gunRoot/viewRoot`、全帧率
   `viewMuzzle`、`sightPos/scopePos`（同一 Bᵀ 转置归一化口径）。四个斜向
   突发中，**枪根/枪口/机瞄/瞄具四个骨骼测点的 view-x 位移同向同量级**，
   且符号完美镜像：SE −0.17、NW −0.02、SW +0.03、NE +0.07——刚性整体横移，
   sin(2θ) 指纹实锤。
3. **机制（终版，经"反修复"反证锁定）**：26.2 vanilla 手部 pass 在 poseStack
   进入物品渲染前就预乘了基座 B=R(q)（view→world 相机基座；Iris 手部 pass 不预乘，
   B≈I；1.21.1 从单位阵开始，B≈I）。决定性事实：`applyAnimationConstraintTransform`
   写入的 m30..m32 槽位，其**上方链在提交（ViewSnapshot→几何）时还会再左乘一次 B**，
   即「最终视图位移 = B · v_written」；而 authored（1.21.1 正确观感）要求
   「最终视图位移 = diag(c) · F · Δ」（F=骨骼链内翻转、Δ=约束骨骼位移差、
   c=(ICA_x−1, ICA_y−1, 1−ICA_z)，AK47 shoot 键位 [0.15,0.05,0.4] ⇒
   c ≈ (−0.85, −0.95, +0.6)，强各向异性，满开镜 weight≈1，腰射 weight≈0）。
   - **老 bug**：原代码 `mulDirection(pose)`（v0 = B·F·Δ）→ 逐轴乘系数
     （v = diag(c)·B·F·Δ）→ 提交时再被 B 带一次 ⇒ 最终 = **B·diag·B**·FΔ
     （注意不是数学共轭：Y 轴旋转让 x/z 之一带负号）。非对角元 = (cx+cz)/2 · sin2θ：
     正方向归零或退化为无横向分量的轴交换，斜向产生按象限对反号的纯横向泄漏
     （0.1~0.2 视图单位 ≈ 2~4°）——两象限对镜像、腰射/光影无感，全部吻合。
   - **错误修复版（d24e604，已回滚语义）**：误将修正写成 v = B·diag·Bᵀ·v0
     （共轭方向写反）⇒ 净效果 = **B²·diag·F·Δ = R(2θ)·authored**：正南/正北
     （2θ=0/360°）恒等正常；正东/正西（2θ=±180°）x/z 符号翻转 → 后坐力"向后怼"、
     换弹跑到右后方；斜向（2θ=±90°）x/z 互换 → 后坐力变纯平移。用户复测报告
     与该 2θ 指纹逐条吻合，反证了「写入槽位上方链 = B」的坐标系结构。
4. **修法（终版，`FirstPersonRenderGunEvent`）**：`mulDirection(pose)` 之后**两次**乘
   Bᵀ：`v = Bᵀ · diag(c) · Bᵀ · v0`（`mulTranspose(baseR)` → 逐轴 `mul(cx,cy,cz)` →
   再 `mulTranspose(baseR)`）。则 v = Bᵀ·diag(c)·F·Δ，提交时被上方链的 B 带回
   = diag(c)·F·Δ = authored，**全朝向与 1.21.1 严格一致**；Iris 下 B≈I 两步均恒等。
   B 由 `GunItemRendererWrapper.copyHandBaseRotation` 提供（手部 pass 入口基座 3×3，
   正交，捕获于 renderFirstPerson 入口、翻转压栈之前）。换弹动画同样驱动
   constraint 骨骼，同路径一并修复。首版修复的方向性错误已按上方第 3 条归档——
   教训：在「写入帧与提交帧差一次左乘」的管线里，基座归一化的乘子方向必须按
   实测朝向指纹校验，而非凭坐标系直觉。
5. 27.4 轮的四个运行时 FX 隔离开关（`DebugDisableMuzzleFlash/Shell/Tracer/
   CameraAnim`）留在配置里——用户逐项排除特效的证据链也是定案的一部分。

**遗留观察项（低优先）**：27.1 轮曾观测 `rotationQuaternion` 含 |sin(yaw)| 比例的
x 分量（幅度 <0.5°、后续轮次未再复现），写入侧偶发机制未明，当前判定与本案
主症状无关（本案幅度 2~4° 且已被完整解释）；长期挂在 w≈0.9987 的非单位稳态
已查明为 fastInvSqrt 归一化误差被 blend 每帧刷新钉住，视觉无感，不修。

**✅ 结案确认（第 28 轮）**：首版修复（d24e604，共轭方向写反）被用户复测打回
——症状变为「仅正南/正北正常；正东/正西后坐力'向后怼'；斜向变纯平移」，
与 R(2θ) 数学指纹逐条吻合，反证了「写入槽位上方链 = B」的坐标系结构。
逆向共轭终版（c975748，`mulTranspose → 逐轴缩放 → mulTranspose`）经用户实测
**八朝向全部正常，确认修复**。

---

### 第 28 轮新问题登记册（按优先级推进中）

#### 案例③：目镜内「黑边（遮光环）被不正确裁切」—— 首判被否证并回退，取证中

- **症状**：开镜时目镜内圈黑色遮光环边缘被裁掉/啃缺口。
- **首判（已被否证）**：曾推断黑环贴图「中心透明」、黑片被自己的掩码投影自裁，
  于是把黑片单独用反向裁剪版提交。**实测回归**：镜片整片变黑盘、甚至溢出镜框
  （用户截图 08:08:52）。
- **否证证据**：全部 33 款瞄具的 ocular UV 区贴图 alpha 实测为 **255（实心深色）**，
  且多数 ocular 是细板条逐片铺满玻璃面（elcan 8 片竖板、lpvo 十字条）。
  因此「透视镜片」成立的必要条件恰恰是**黑片随镜身一起被自己的掩码投影 discard**，
  首判把「工作特性」当成了「bug」修，已回退。
- **修正后真因假设**：被啃的黑环属于 **scope_body 网格的镜框内圈**（非 ocular 板），
  而几何投影掩码可能略大于真实通光孔径 → 把内圈边缘一并裁掉。
  上游用**固定半径圆**（PORTING_NOTES §3.5 记录在案的架构级分歧）。
- **AUG 对照截图补充的关键事实**：问题波及**所有中高倍镜/组合镜高倍组**
  （AUG 自带瞄具最明显：镜框下缘 5~7 点钟方向残留灰色网格块）——
  这些镜种的 ocular 是**板条拼玻璃**（AUG 3 条十字、elcan 8 片竖板、lpvo 细十字），
  几何投影掩码只剩板条区域 → **孔径内的镜身内壁漏裁**（under-clip），
  与筒镜「黑环被啃」（over-clip）是「几何掩码≠真实孔径」的一体两面。
- **第二轮修复（已随本构建发出，带开关）**：`ScopeMaskHullFill=true`（默认开）
  = 凸包填充模式 —— 把激活目镜几何投影的 **2D 凸包**整体涂进掩码
  （板条张开跨度恰好勾勒孔径内接多边形），覆盖面严格不小于板条描摹，
  漏裁类残块必消；`=false` 秒回退旧几何描摹。实现见
  `ScopeMaskRenderer#writeHullFill`（同一投影矩阵 CPU 投影→单调链凸包→
  逆投影退化四边形扇，复用 QUADS 索引，不动管线）。
- **待用户验证/裁决**：开满镜看 ①AUG 与 elcan 类镜片内是否干净；
  ②**各镜种镜框内圈是否被凸包啃出过裁**（凸包可能比孔径略大）——
  若有，开 `ScopeMaskDebug=true` 同帧截图（左上角掩码预览 + 画面同框），
  下一轮按距离场内缩收敛。
- **第 28.4 轮状态更新（用户裁决）**：A/B 对比（HullFill true/false 两张）
  主画面差异目视不可辨；用户决定**案例①暂不深究、挂起**。
  已知待办保留：凸包 UV-读回偶发失败帧自动回退描摹（一次性 WARN，
  正确性无碍）；个别镜种内圈遮光边可能存在少量过裁，`ScopeMaskHullFill=false`
  随时秒退。若日后重启此案，入口开关即该配置。

#### 案例④：目镜内未裁切枪体、配件（镜片里看得见护木/激光盒）—— 已定位，已修

- **症状**：开镜后镜片投影区内仍看得到枪身与其他配件穿过镜面。
- **机制**：「透视瞄具」要求目镜投影内**一切视模像素都 discard**
  （颜色+深度都不写），世界画面才能透出。此前裁剪版 RenderType 只发给
  瞄具镜身；枪身（`GunItemRendererWrapper`）与其余配件（`AttachmentRender`）
  用原版 `entityCutout` 照常在镜内写像素。
- **修法**：新增 `ScopeBodyRenderTypes.clipForViewmodel` 总入口
  （第一人称 & SCOPE_MASK_ENABLE & 无光影 & 当帧掩码非空 & 采样器可用 →
  换裁剪版）：
  - 枪身：`BedrockGunModel.submit` 新增带 `gunTexture` 的重载，
    在瞄具提交登记掩码**之后**再为 `super.submit` 解析类型
    （不能上提到 wrapper —— 那时本帧清单还是空的）；
  - 配件：`AttachmentRender.submitAttachment` 为所有非瞄具配件接同一掩码。
  - 瞄具自身：清单为空时恒等返回，内部 `maskable` 逻辑不变（零交互）。
- **枪口火光大面片层**（第二轮追加）：开火后坐时火光会探进目镜口径，
  给大面片层接入同源裁剪管线 `scope_flash_translucent_clipped`
  （以 vanilla `ENTITY_TRANSLUCENT` 配方逐条照抄——用 ENTITY_SNIPPET 为底
  叠加 ALPHA_CUTOUT/PER_FACE_LIGHTING/SAMPLER1/TRANSLUCENT/不剔除，
  再叠 SCOPE_MASK；`MuzzleFlashRender` 在掩码就绪时才换型）。
  用户 LayerAssignment 裁决实验（辉光层强制裁剪）证实：**镜内火团全部来自
  辉光层，大面片从未失手**（FlashDebug 六分量早已全 true）。
- **枪口火光辉光涡旋层**（第三轮收口，已随本构建发出）：
  逐指令反汇编 `RenderPipelines.<clinit>` 的 ENERGY_SWIRL 段
  （jar ins 914-956）发现 26.2 所谓「折叠」就是 `core/entity` 双 shader +
  `APPLY_TEXTURE_MATRIX` define —— 我们的 scope_body 着色器正是 entity 的
  逐字节拷贝，于是轻车熟路立 `scope_flash_swirl_clipped`：
  MATRICES_FOG_SNIPPET + ALPHA_CUTOUT/EMISSIVE/NO_OVERLAY/
  NO_CARDINAL_LIGHTING/APPLY_TEXTURE_MATRIX/SCOPE_MASK 六 define +
  ADDITIVE 混合 + ENTITY 顶点绑定 + OffsetTextureTransform(1,1)，
  观感与 vanilla swirl 逐状态一致、镜内正常 discard。用户实测：镜内火团
  **彻底消失**。Iris 侧同样挂进 HAND（mode=1）。
- **枪口烟雾**：属于世界粒子 pass，要裁需侵入全局粒子着色器 —— 风险高，
  且上游 1.21.1 是否裁镜内烟雾未核实，维持不动。
- **验证**：开镜后镜片透出纯净世界画面，看不到任何枪体/配件碎片。

#### 案例⑤：NVIDIA + 光影开启时激光改色无效（A 卡 / N 卡无光影正常）—— 结案：光影包不支持彩色自发光（2026-08-11 用户定性）

- **症状**：改装界面里换激光颜色，画面不更新；仅 N 卡 + Iris 光影开启触发。
- **终局（2026-08-11 用户回执）**：**测试员的光影包过于精简、没有彩色自发光
  （colored emissive）支持** —— 不是本 mod 的缺陷，也与显卡厂商/驱动无关。
  与下述调研方向完全一致：顶点色最终是否生效由光影包程序决定。
- **调研与代码审查（2026-08-11，定性依据）**：
  - 网络调研（Iris/Oculus issue 区）：**没有任何「顶点色在 NVIDIA 失效、AMD 兜底」
    的同类记录**。GLSL 顶点色语义与厂商无关；我们对顶点格式的全部元素都写满
    （color/uv/overlay/light/normal 齐），属 GL 规范定义内行为，两厂驱动不应分歧。
    「RGB 写法在光影路径下不对、AMD 兜底」方向证据不足。
  - 相关先例：IrisShaders/Iris#3049 —— 开启任意光影后，模组自定义 emissive
    rendertype 在特定相机角度整片变黑；与厂商无关，根子在 Iris 对自定义/vanilla
    渲染类型的整包替换机制。我们的激光走的正是这类被替换的类型。
  - **代码审查（数据侧清白）**：`LaserColorUtil.getLaserColor` 每帧现读 NBT、无缓存；
    改装写回若坏应**全平台**都坏，与「仅 N卡+光影」矛盾。颜色只走一条通道：
    `submitCustomGeometry` + `entityTranslucentEmissive` + 顶点色。光影开启时
    Iris 用 shader pack 的 hand/entities 程序替换该管线，**顶点色是否参与乘算
    完全由 pack 决定** —— vanilla 原生内容几乎不依赖此类型的顶点色，pack
    漏乘也无人发现。至此全链自洽，与用户定性吻合。
- **LaserDebug 探针处理**：保留（`LaserDebug` 配置键，默认关、常态零噪音），
  日后同类报告可直接开探针二分：日志 RGB 跟着改色变而画面不变 → 光影包侧；
  日志不变 → 数据侧。本案属于前者（已由用户定性，无需日志）。
- **发布文案处理**：Modrinth/CurseForge 的 Known issues 条目已从「N 卡+光影下
  改色无效，原因调查中」改写为「光影包需支持彩色自发光，极简包会保持默认色；
  系光影包限制，与 GPU/驱动无关」。

#### 案例⑥：PAL（Player Animation Library）下切枪一次、第三人称持枪动画整局失效 —— 已修·用户实测确认闭环（第 38 轮，2026-08-11 确认）

- **症状**：安装 player_animation_library 1.2.5 后，初次持枪动画正常；
  只要切枪（无需在第三人称视角），持枪动画本次会话永不恢复；
  小退（重进存档）/大退（重启进程）恢复。该缺陷自很早轮次即存在。
- **根因（PAL 源码级实锤，zigythebird/PlayerAnimationLibrary@main）**：
  1. `PalAnimationManager.stop()` 原先对每个 controller 调
     `replaceAnimationWithFade(standardFadeOut(8), null)`；
  2. PAL `AbstractFadeModifier#canRemove()`：**仅 FADE_IN 完成才返回 true；
     FADE_OUT 恒 false** → fadeOut 完成后【永久残留】在 modifier 链上；
     `AnimationController#tick()` 每 tick 只按 canRemove 摘除 —— 摘不掉它；
  3. `AnimationController#get3DTransform`：链非空则全权交给链首 modifier；
     fadeOut 完成态 progress=0 → alpha=0 → 输出 = 上游（链首=identity）
     ＋ 下游全部动画 × 0 → **controller 永久哑掉**；
  4. controller 由 avatar 工厂在实体重建时才重新生成 —— 小退/大退因此「治好」；
     第三十七轮的「fade 堆积削幅」诊断方向正确，本坐实为更绝对的屏蔽机制。
- **修复（零侵入 PAL）**：stop() 改用 **FADE_IN-to-null**
  （`standardFadeIn(fadeTicks)` + `triggerAnimation(null)`）：
  PAL 会把当前骨骼快照塞进 transitionAnimation，8 tick 内从旧姿势平滑滑入
  identity（=视觉淡出），progress≥1 后 canRemove=true → 下一 tick 自动摘除；
  与新枪动画的 fadeIn 叠加也互相不压制（FADE_IN 不将下游乘 0）。
  `then(null)` 路径已由现网行为验证宽容（旧调用只哑不崩），无新增风险。
- **验证**：装 PAL，持枪第三人称 → 切另一把枪 → 切回/换空手/再持枪，
  动画应始终恢复（收枪淡出观感与之前一致）。
  **用户 2026-08-11 实测确认闭环。**

#### 案例⑦：带模型的「炮弹」与「炮烟」（弹药尾烟）第一人称从眼睛飞出 —— 已回退（维持上游原生行为，第 31–31.3 轮）

- **症状**：带弹药实体模型的弹种（炮弹/榴弹等）与其尾烟，第一人称下飞行起点
  在眼睛处（三条视觉载体中第 26 轮曳光修复只覆盖曳光条带；弹药模型路径与
  `AmmoParticleSpawner` 尾烟路径都按实体位置裸渲，子弹实体服务端出生在眼位）。
- **尝试与教训（三轮，全部已回退）**：
  - 第 31 轮：把曳光的「视图空间枪口偏移 × 当帧相机旋转」锚定数学链照搬到
    弹药模型与尾烟路径（50 格收敛窗口 + `FirstPersonAmmoMuzzleAnchor` 开关）。
  - 第 31.2 轮：低速长寿命弹种在 50 格窗口里全程背着「跟随相机旋转、随距离
    缓缩」的幽灵位移 → 实测「固定在枪口上方」；窗口缩至 2.5 格。
  - 第 31.3 轮：截图实证锚定起点「视角内偏上」——存量 `muzzleRenderOffset`
    纵向分量相对真实视图空间疑似反号（床岩视模 y 翻转惯例）；对 y 取反修正。
    **但该静态偏移向量是在别的渲染语义下采集的，继续借用到这条路径上
    符号/语义风险无法收束，用户判定观感仍怪、决定止损。**
  - **结论：整组回退为上游原生行为**（炮弹/尾烟按实体位置渲染）。此行为与
    上游 1.21.1/26.x 完全一致，非本移植引入的缺陷，作为「已知观感差异」保留。
- **保留的产出**：问题结构分析（三条载体分离、服务端眼位出生为弹道标准起点）
  与「借用曳光锚定向量到非曳光路径」的反例教训；若未来重启，正确入口是
  从**射击当帧的第一人称视模世界矩阵**现场解算出膛口位置（事件时点采集），
  而不是复用为曳光目的存量缓存向量。

#### 案例⑧：第一人称「臂+枪整体」平移方向随玩家朝向旋转 + 后坐力随朝向「过分向下压」——（2026-08-11 立案，2026-08-12 结案：根因 = 写回翻号 Q 游离于共轭之外，修复 = mode 3「Ŵ·D·Wᵀ·v0」26.1.2/26.2 双线在体验证通过）

- **症状指纹（用户六朝向实测，原话要点）**：
  | 朝向 | 换弹时整体平移 | 备注 |
  |---|---|---|
  | 北 | 偏左 | 手臂、枪体作为一个整体 |
  | 南 | ~正常（或偏下，看不太出来） | 唯一接近正常的朝向 |
  | 西 | 偏右 | |
  | 东 | 偏左 | 与西**异号** |
  | 仰视 | 左上 + **后方**（枪整体往后移） | 有深度分量 |
  | 俯视 | **后方** | |

  后坐力：正北开枪无明显问题；其他朝向都「过分向下压」，**正南最重**。
  **开启 Iris 光影时以上问题全部消失**（现场环境：iris 1.11.2+mc26.2 / sodium 0.9.1+mc26.2，
  用户上传 latest.log 实证）。
  用户怀疑：本移植修换弹/后坐力时的遗留（第 26/27.x 轮、d24e604/c975748 一系）。

- **静态审计已排除（不信任注释，全部以代码/字节码/数值验证为准）**：
  - 相机后坐力通道：`CameraSetupEvent.applyCameraRecoil` 只对玩家自身 `setXRot/setYRot`
    做增量，是自身坐标系操作，**数学上不可能**随朝向偏置。
  - 普通骨骼动画：床岩动画撰写在模型空间，天然锁视角，无朝向自由变量。
  - 第一人称渲染链的**右乘局部复合**：26.2 官方 jar 字节码确认
    `GameRenderer.renderItemInHand` 开头 `pose.mulPose(viewRotationMatrix.invert())`、
    `modelViewStack.mul(viewRotationMatrix)`（viewRotation = `Camera#getViewRotationMatrix`
    = R(camera.rotation().conjugate())，view→world 的逆），提交时 C·base=I ——
    **静帧下任何纯右乘变换都不可能产生朝向相关平移**。
  - `applyAnimationConstraintTransform` 现状（c975748 版「两次 mulTranspose+diag」）
    经 numpy 逐朝向数值拟合：**注定 N/S 双干净、E/W 同号**——与用户指纹
    （N 偏左、S 净、E/W 异号、俯仰有深度分量）**不符**。即：现状约束公式不是
    本案载体（无论它自身还剩多少理论残余），另有第三处写入/错位未覆盖。
  - 上一轮（c975748）自称修复的 R(2θ) 指纹（N/S 好、E/W 后怼）恰恰是全对称型，
    本案指纹是**单频率型**（仅一处朝向干净），两者结构性矛盾 → 说明当前线上症状
    不是那条约束链的遗留态，疑似另有世界量写入点或未采样的基座分歧。

- **在查嫌疑**：① 摄像机动画双消费链（`applyLevelCameraAnimation` 把按 ZYX 分解的
  euler 加到世界相机 vs `applyItemInHandCameraAnimation` 直接把四元数 mulPose 进手栈，
  两者的消费帧参考系若存在不完全抵消，残余随俯仰耦合）；② `handBasePose` 采集时刻
  与约束函数读取时刻之间可能夹了 `BeforeRenderHandEvent` 的 q_cam（提交时 C 不含它）；
  ③ 某个尚未发现的绝对槽位写入。

- **第一轮取证结论（17:50 日志，249 条 Case08 + 8455 条逐帧 gunRoot，离线数值分析）**：
  - **基座机制在体证实**：日志 B（手部入口基座 3x3）与理论 `X(pitch)·Y(yaw−180°)`
    吻合（maxerr≈0.02，含 bob 噪声）；`MV·B ≈ I`（中位偏差 0.009）——vanilla 手部
    pass「入口基座 = 视图→世界、下行 modelView = 世界→视图」的模型**在体成立**。
    也就是说稳态下 `C·B=I`，纯右乘复合确实不可能产生朝向依赖 —— 泄漏必然来自
    **非右乘的绝对写入或跨帧状态**。
  - **约束位移帧在换弹腰射期间根本不执行**（weight = aiming×(1−refit) ≈ 0），
    彻底证伪「约束公式 = 换弹载体」。
  - **在体实录破锁（决定性）**：同一次换弹进程中（t=64127..64129，玩家从 yaw+88°
    转向 +149°），枪根视图坐标从 **z=+1.013（相机身后一整格！）** 甩到
    (−1.086, +0.219, −0.314) 再到 (−0.872, +0.256, −0.707)——同动画相位（|d|≈0.06）
    跨朝向对照 z 在 ±1 间翻转。相机锁定不是「近真误差」，是**成建制失效**，
    且幅度与玩家的「后方」体感完全一致。
  - 自由游玩数据的相位混杂限制：换弹逐帧相位无法用 |d| 精确对齐 → 需受控协议。

- **本轮动作（取证先行，按 D4 铁律先日志后改动）**：
  已在三处布点（`RecoilDebug=true` 门禁）：
  1. Case08 探针（`applyAnimationConstraintTransform` 内 Δ/v0/v3 + B′/P/MV 全矩阵）；
  2. **chainP1**（renderFirstPerson 内「基座捕获后、定位/约束前」逐帧链位姿，
     与链末端 gunRoot 成对 → 直接裁决泄漏注入点在上游基座段还是定位/约束段）；
  3. **RELOAD_START 标记**（`LocalPlayerReload.doReload`，毫秒级相位零点）；
     gunRoot 探针追加毫秒时间戳。
  **受控测试协议 v2**（约 90 秒）：关光影、持 AK、腰射（不按右键瞄准）——
  ①正对 N、停，换弹一次，静置 3 秒；②转向 W 停稳，换弹，静置 3 秒；③S、E 同法；
  ④抬头朝天换弹一次、低头朝地换弹一次；⑤最后每个方位各打 2 发（取后坐段）。
  全程别按瞄准。上传新 latest.log。

- **第二轮取证结论（18:18 日志，严格六朝向腰射协议 v2，14426 行 Case08 族探针，numpy 离线对齐）**：

  为什么先要腰射（回答用户疑问）：病灶被用户一句「不开镜不会有这些现象」精确限定——
  全链唯二以 `aimingProgress` 为门控的写入是 ①`applyAnimationConstraintTransform`
  （weight = aiming×(1−refit)）②`applyFirstPersonPositioningTransform` 的瞄准 lerp 权重。
  腰射对照组 = 直接验证「门控关闭时全链逐位干净」，从而把嫌疑区间从整条渲染链
  收缩到这两条门控路径；顺便产出干净的相位基准指纹，供 ADS 组做差分。
  本轮数据把这条逻辑钉死了：

  1. **探针纪律核验通过**：6 个 RELOAD_START 毫秒标记全部标准落位
     （N 177.3° → W 89.3° → S 0.6° → E −86.4° → 仰 −90° → 俯 +90°）；
     9 次开火 `aimMod ≡ 1.0000` ⇒ aimingProgress=0，腰射确证（aimMod = 1−aim+aim/√zoom）。
  2. **腰射换弹轨迹四朝向逐位一致**：gunRoot viewRoot 与 chainP1 以 RELOAD_START 为
     相位零点、50ms 分箱，N/W/S/E 两两差异 p95 ≤ **0.0006** 视图块（六窗口 68 箱全满）——
     基座/lag逆转/摄像机动画/定位链在腰射下彻底锁死。「不开镜无现象」获定量确认，
     **此前拟议的摄像机动画双消费嫌疑在腰射条件下被在体证伪**（若有泄漏，chainP1 必然漂移）。
  3. **决定性：约束写入权重 w 在全部 398 个 Case08 样本中 ≡ 0.0000**——腰射时
     m30..m32 绝对槽位写入零执行。该路径与「症状 ADS 限定」互为镜像：
     它是把手臂+枪体作为整体平移的**唯一一把有物理能力的扳机**（唯一能写出 O(0.1)
     屏幕平移的帧敏感槽位），ADS 辑拿即为裁决。
  4. 代数核验（为 ADS 推演铺路）：v0=P·d 中位误差 0.0002 ✓；
     v3 ≈ Bᵀ·diag(c)·Bᵀ·v0 最小二乘拟合 c≈(−0.64,−0.57,+0.44)，与 AK constraint
     [0.15,0.05,0.4] 折算的 ICA 系数 (−0.85,−0.95,+0.6) 同号同量级 ✓。
  5. **事前推演（同样本残差法，消相位混杂）**：若现状公式在 w=1 下执行，
     相对「同俯仰正南」的额外屏幕平移为 N:(−0.085, ~0, −0.095) 左+后 ✓ 合指纹；
     S:≈0 ✓；但 E:(+0.112, …) 向右、W:(−0.014, …) 弱左——**E/W 与实测指纹镜像相反**，
     且 y 分量全朝向 ≈0，解释不了「过分向下压」。⇒ 即便 ADS 下它执行，
     现状公式也大概率不是（至少不是完整的）载体——不赌公式，ADS 辑拿看
     chainP1/gunRoot 分段漂移实况后再定性。
  6. **垂直朝向异常（活线索，直指「仰头/俯视→枪跑到后方」子症状）**：
     俯视换弹窗口内（reload#5，pitch +90°）约束 d 模长暴涨至 **0.6–1.5 块**
     （常态 ~0.05），且这些帧出现 `v0 = Pᵀ·d` 的转置性关系（非垂直帧 v0=P·d
     精确成立）；colNormDev ≤ 0.0034 已排除矩阵剪切。嫌疑指向
     `MathUtil.getEulerAngles` 万向锁支路或某处 ±90° 奇异支路。待 ADS 垂直样本复核。

- **测试协议 v3（ADS 辑拿版，约 2 分钟）**：光影保持关、`RecoilDebug=true` 不动、持 AK——
  ①正对**北**：按住右键开镜 1 秒 → **全程按住右键不放** → 按 R 换弹 → 换弹动画结束
  后再稳 1 秒 → 松右键 → 静置 3 秒；②**西、南、东**各重复同一套；
  ③**抬头朝天、低头朝地**各一套同样的「开镜换弹」；④四方位各**开镜点射 2 发**
  （两发间隔约 1 秒，全程保持开镜）。打完上传 latest.log。
  （若开镜时按 R 被强制退镜，照做即可——探针会如实记录 aiming 衰减段的 w 轨迹，
  症状出现的每一帧都会被捕获。）

- **第三轮取证结论（19:25 ADS 辑拿日志，614 条约束采样 + 19511 帧 gunRoot/chainP1，w≡+1 全程保持）**：

  探针纪律：7 次开火 aimMod≡0.8671 = 1/√zoom（zoom≈1.33），确认全程满开镜；
  换弹期间 w 轨迹全程 +1.00（本包 ADS 换弹不强制退镜）——写入路径满功率在押。

  1. **二分段裁决**：chainP1（基座捕获＋lag逆转＋Z180 之后）四朝向两两差异 ≡ 0.0000
     ——基座/lag/摄像机动画段**在 ADS 下也逐位干净**，彻底出列；
     而 gunRoot（链末端）跨朝向差异中位 0.01~0.026、p95 0.02~0.05（腰射对照 0.0006，
     放大 40–80 倍）——**注入点锁死在 applyFirstPersonGunTransform 段内**。
     泄漏为 ADS 限定，与用户体感 1:1。
  2. **约束位移写入无罪释放（推翻第二轮的波形指纹拟合）**：以实测 B/v0/v3 + 拟合
     c=(−0.74,−0.94,+0.84)（与 AK constraint [0.15,0.05,0.4] 折算 (−0.85,−0.95,+0.6) 同号同量级）
     计算「屏幕上写入效应 − Iris authored 观感」：四水平朝向偏差中位 ≈ **0.0005**、均值 ≈ 0.0003
     ——现状公式在锁视角代数下逐位等于 authored。c975748「终版」公式本身是对的。
  3. **「过分向下压」排除唯一平移类通道**：开火 ±0.9s 内写入的 y 分量
     N/S/W/E = +0.004/+0.004/+0.004/+0.010，全为**向上**且量级毫米级、朝向间无差异
     ——下压绝非约束位移写入。后坐相机样条 pitchEnv 四朝向逐位相同（已核），
     同样排除相机通道 ⇒ 下压载体只能是**刚性旋转**（绕非视轴的小角度旋转在屏幕上
     表现为「压」），见下条。
  4. **锁残差在体实录（总嫌疑 No.1）**：R = modelView × 捕获基座(3x3)——
     hip-S 桶 |R−I| 中位 0.0001（锁死 ✓）；**ADS 下全朝向 R̄ = 1.4°~2.1° 的成建制旋转矩阵**
     （正交误差 ≤0.0015，确为纯旋转），且旋转轴在视图系随朝向翻转
     （N(−0.50,−0.55,−0.67) vs S(+0.52,−0.69,+0.50)：x/z 反号、y 恒定 ⇒ 世界系固定轴）。
     含义：**开镜后整枪图像被一个绕世界固定轴的小角度刚体旋转——视线旋转 90°，
     该旋转在屏幕上的投影方向也跟着转 ≈「整枪平移方向随玩家朝向旋转」；
     世界轴带强 −y 分量 ⇒ 部分朝向屏幕上呈现纯俯仰 ⇒「向下压」**。
     与指纹的各向异性、ADS 限定、Iris 豁免（Iris 手部 pass 基座≈I，R 恒等于 I）逐条吻合。
     结构成因候选（代码级，待相位精确数据定锤）：世界相机侧 euler ZYX-add vs
     手部侧四元数 mulPose 的**消费栈不同构**（26.2 vanilla 手部 pass 基座=V⁻¹ 预乘而
     modelView=V 的结构使任何一侧的消费顺序/分解差异都以 MV·B≠I 显形；
     `debugRecoilItemCam/levelCam` 两探针已在押两侧逐帧四元数）。
  5. **F=Bᵀ·P（局部链 3x3）现 {N,S}≈X(π) 型 / {E,W}≈Z(π)掰轴型 / {UP,DN} 三块结构**
     （桶间矩阵范数差 ~2.6，远超桶内相位混杂 0.03 量级）——局部链内容在 ADS 下
     携带朝向信息，**机制未明**；chainP2 中段探针（定位后/约束前）将劈开归因。
  6. 垂直奇异帧复核：pitch≈±90° 时 87 条样本中 31 条 v0=P·d 失效，其中 4 条呈
     v0=Pᵀ·d 转置关系，伴随 |d| 暴涨（0.6~1.5 块）——欧拉分解/万向锁嫌疑仍在押，
     与「仰头/俯视→枪跑到视野后方」子症状对口。

- **本轮动作（探针 v3，仍零行为改动，全量 RecoilDebug 门禁）**：
  ①Case08/itemCam/levelCam 全部追加毫秒戳（消相位混杂的最后障碍）；
  ②**chainP2**（applyFirstPersonPositioningTransform 之后、约束写入之前）：
  落 B′ 归一化平移 + B′ᵀ·P 局部 3x3 → 劈开「定位段/约束段」；
  ③gunRoot 追加 **lockAng/lockAxis**（modelView×基座 的轴角分解，逐帧同 ms）——
  可直接验证「R 把整枪图像朝各朝向各转多少」与 gunRoot 漂移、用户指纹的相位级对应。
  **测试协议 v4**（与 v3 完全相同，约 2 分钟）：六朝向开镜换弹 + 四朝向开镜点射 2 发。

- **第四轮取证 = 定谳（20:25 协议 v4 日志，探针 v3 全量毫秒对齐）+ 第 28 轮修复落地**：

  **决定性证据（三条全部逐帧相位精确）**：
  1. **幅度**：lockAng(t) ≡ itemCam 摄像机动画角度，线性拟合 **k=1.000±0.003,
     corr=0.998**（N/N'/E/S/W 全部窗口逐帧成立）——R(modelView×基座)的角=
     摄像机动画旋转的**全量**，不是 mult 阻尼差（0.133）也不是半量。
  2. **轴**：R 的旋转轴经 facing 重建到世界系后，跨朝向同相位配对夹角
     中位 **6.5°**（与 N/N′ 同朝向测量噪声底 6.5° 完全一致；视图系夹角则 86°~162°）
     ——**世界系固定轴**，逐帧吻合。
  3. **腰射对照**（18:18 协议 v2 数据复核）：腰射换弹窗口内 R 残差 ≈0.5·q
     （中位 1.0° vs q 2.1°），用户无感；ADS 下残差=全量且 zoom≈1.33 放大画面
     ——同一病灶、开镜越阈。此前「hip 时 R=I」的印象只来自 t≈静置、q≈0 的帧。

  **根因（机制级）**：26.2 手部渲染链中，摄像机动画经 CameraMixin 的
  euler 叠加入 camera.quaternion 后随世界视图矩阵进入 modelView；
  而手部基座链没有收到与之匹配的世界系补偿分量（BeforeRenderHandEvent
  的消费与捕获基座链对不上）⇒ R=MV·B≡q_world(t)，整枪图像被绕世界系轴
  刚性旋转；玩家转身时该轴的屏幕投影随之旋转 ⇒「臂枪整体随朝向平移」、
  轴带俯仰分量 ⇒「部分朝向向下压」。Iris 手部 pass 基座≈I 且 modelView 与基座
  天然互逆 ⇒ R≡I ⇒「开光影全部正常」⇒ **Iris 观感 = 本案正确参照系**。

  **修复（第 28 轮 · 掩码类铁律：并行开关、可秒回退）**：
  `GunItemRendererWrapper.renderFirstPerson` 在捕获基座后左乘 `C=(B·MV)⁻¹`
  ——屏幕像变为 `MV·(C·B·X·p) = X·p`，即恒等于 authored 局部内容，与朝向解耦，
  逐位复刻 Iris/上游观感；Iris 激活时恒等 no-op；开销为每帧 3 次 4x4 乘法。
  开关：`config/tacz-client.toml` → `HandViewLockFix`（默认 true；false=旧行为）。
  探针保持开启：修复后 lockAng 应恒≈0°（探针自证生效），
  chainP2/gunRoot 跨朝向漂移应回到腰射基线量级（≤0.0006）。
  **待用户验证**：六朝向开镜换弹 + 开镜点射复测；日志探针读数将直接判决。
  遗留观察项（独立存疑，不受本轮修复影响）：垂直朝向帧 `v0=Pᵀ·d` 转置奇异 +
  约束 |d| 暴涨（0.6~1.5 块），若「仰头/俯视跑后方」在修复后仍存，
  下一轮单独追 `MathUtil.getEulerAngles` 万向锁支路。

- **第五轮取证（用户回执「没解决」+ 21:09 协议 v5 日志，修复版在体实测）：
  锁视角修复确证生效，但抓到修复自身的读取污染 + 真实剩余泄漏段被劈开**：
  1. **修复在体铁证**：`lockAng` 中位 0.03~0.06°（修复前 ADS 恒 1.4~2.1°），
     约束探针离线验算 `MV·B=I` 精确成立——第 28 轮公式与代码路径无误。
  2. **新妖怪 = 缩放突发（修复读取污染）**：2880/7400 帧（39%）`lockAng≈7.0569°`
     钉死成平台值、轴逐帧乱跳——非旋转指纹。逐帧矩阵对账实锤：
     约束探针 B 与 MV 的关系为 **B = s·MVᵀ（s≈0.9933 / 0.99495），MV·B 非对角元
     恒 0**——是均匀缩放被轴角公式误读成「旋转」；gunRoot `colNormDev=0.0067`、
     chainP2 的 F 矩阵对角出现 s²=0.9866，逐位互洽。代数上 B′≡MVcap⁻¹ 恒成立，
     故结论唯一：**fix 读取 modelView 顶部的瞬间，顶部携带 ~0.5-0.7% 均匀缩放，
     同帧约束/gunRoot 两次后读均为纯旋转**；v4（修复前）同协议全无 7.0569 平台
     （0/352 帧）⇒ 缩放不是摄像机动画/原生现象，而是 fix 读取位点遭遇的污染。
     无论绘制帧顶部最终取哪个版本，旋转分部逐位一致 ⇒ 绘制像至多差 0.7% 均匀
     缩放（远低于可见阈值）⇒ **非用户可见症状的成因，但会污染写入链归一化**。
  3. **真实剩余泄漏段被劈开**（跨朝向同相位 50ms 分箱对比）：
     chainP1（基座/lag 段）中位 0.001 ≈ 噪声底（≤0.0001）✓ 干净；
     **chainP2（定位段输出）中位 0.021（平持）/0.275（竖直）、gunRoot（链末，含约束）
     0.071/0.86**——泄漏注入点在「定位段 + 约束段」，与基座无关。
     注：协议噪声底已逼近本测量（早期相位内容速度 ~0.1 块/50ms，且 N/S 平持两次
     実测俯仰角差 1.2°，可解释 ~0.024 量级）⇒ 跨窗口相位对比的分辨率到此为止，
     下一轮证据必须来自锁内读取对账（lockCap↔gunRoot mv dump）+ 现场 A/B。
  4. **约束通道**：开镜换弹全程 w≡1，|d| 中位 0.03~0.44、峰值 0.62~0.66
     （竖直窗口并无更糟）——竖直馈点比 v4 收敛，但 0.65 块量级若错位即肉眼可见。
- **第 29 轮处置（已随本次提交落地）**：mvNow 3x3 列归一化（仅动缩放、旋转分部
  不动；并行开关 `HandViewLockNormalize` 默认 true，可单独秒回退）+ `lockCap`
  探针（三列模长>0.002 偏差全量落档，含完整矩阵与平移列以指认污染源 pass）+
  gunRoot 探针追加全量 mv/列模长（与 lockCap 同帧对照，判决「同一调用内顶部被
  改写」假设）。**待用户验证②**：若观感仍错，做一次 A/B——`HandViewLockFix=false`
  对比 true，一句话回执 + 新日志即可让下一轮一锤定音。

- **第 30 轮：用户指认回归时点（「四方向斜向后坐力修复引入」）+ 提交考古 +
  坐标模型自我清算 + 处置转向**：
  1. **考古判决**：自 08-01 端口合并以来，凡触碰第一人称姿态/相机/约束数学的提交
     仅 `d24e604`（斜向修复 v1）与 `c975748`（终版，仅一行：第二个 mul→mulTranspose），
     加之前同日的 `a2838e4`（入口基座捕获，供枪口归一化，当时已被用户实测确认）。
     CameraMixin（摄像机动画入相机）、AnimateGeoItemRenderer（动画倍率）自 08-01
     起**零改动** ⇒ 用户的回归指认精确命中唯一变元（三明治补偿）。
  2. **代数核查**：三明治 `v=Bᵀ·diag·Bᵀ·v0` 在「写入槽位的带回乘子=入口基座 B」
     （该前提由 v1 版 R(2θ) 朝向指纹的用户复测逐条证实）下逐帧精确成立 ⇒ 公式层
     无法定罪；但同一前提与「链渲染乘子=Bᵀ（稳态游玩正常）」并在同帧写入与顶点
     上**结构性不相容**，说明自 08-10 沿用的整个坐标框架模型存在内部矛盾——
     **本轮不再押注任何公式，改为给出秒级对照实验**。
  3. **锁视角修复（第 28~29 轮）自我证伪、默认回退**：三重独立证据——用户实测
     「没解决」；第 26 轮枪口归一化早已实证「26.2 手部 pass 的 RenderSystem
     modelView 仅为兼容保留、内容不受控」（当时正是弃用它才定案，本轮却重蹈覆辙）；
     第 29 轮探针又抓到该读取携带 ~0.7% 缩放污染 ⇒ 锁建立在错误的矩阵上，
     `HandViewLockFix` 默认回退为 false（开关保留，供 A/B 复现实验）。
  4. **契约恢复**：新增 `handBasePoseEntry` 入口原始快照；`copyHandBaseRotation`
     （约束三明治消费）与 `cacheMuzzlePosition`（枪口归一化）一律回读入口快照，
     无论锁开关与否，下游契约与「四方向修复定版且被用户实测过」的状态逐位一致。
  5. **A/B 开关**：`ConstraintBaseCompensate`（默认 true=定版三明治）；false 时
     约束位移逐位回到修复前原版公式（diag·v0 直写），用于验证用户指认的回归。
  **待用户 A/B 判决矩阵**（均为观感答题，无需日志）：A=默认（锁关+补偿开）；
  B=仅补偿关（斜偏预计回来，观察「整体随朝向转」是否随之消失）；C=仅锁开
  （验证锁是否根本无感）；D=两者皆关（修复前夜纯态）。每格一句话即可。
  另请确认时间线：症状是斜向修复当夜（8/10 23:03 版）即现，还是 8/11 早间
  构建才现（二者之间考古仅见镜内掩码/火光裁切，零姿态数学改动）。

- **第 31 轮（定案）——用户在场 A/B 实测结果 + 终版修复重写**：
  1. **B 格结果（实测定罪）**：`ConstraintBaseCompensate=false`（plain 原版公式）
     后，「整体随朝向转 / 竖直跑后方 / 后坐过压」**全部消失**（顺带证实修复前
     基线：正朝向与竖直全正常，仅四方向斜向有后坐力侧漏）⇒ **第 30 轮登记的三明治
     补偿即本案病灶注入源，用户指认成立**。
  2. **C 格结果（锁方向证伪+副作用实录）**：`HandViewLockFix=true` 下症状同样消失，
     但出现两个新问题 —— 斜向后坐力方向错（当时 v30 已把基座契约回切入口快照，
     与锁后链帧不一致所致）+ **「枪口指向性过强」**（整枪被刚性钉死视角、随准星
     瞬动，失去自然延滞）⇒ **锁方向永久弃用**（开关存档，默认 false）。
  3. **重写的终版修复（mode 2 · 姿态帧共轭，本仓库默认）**：
     `v = P_post · diag(c) · P_preᵀ · v0`。依据链：plain（mode 0）在今天全部朝向、
     竖直、携摄像机动画的换弹全程观感都正确 ⇒ 槽位带回乘子满足 X·P_post=I（至多
     差产生斜向 sin2φ 泄漏的小残差）；authored 要求视图位移 = diag(c)·F_pre·Δ；
     同时满足两者、且不读任何外部矩阵（B 与 modelView 均已被实测证伪——后者两次）
     的写法唯一：用**当前姿态自身**做共轭。各向异性系数被 Fold 进姿态帧内部，
     与朝向/基座结构性解耦 ⇒ 斜向泄漏同型消除且不产生新的帧混用。
     Iris 手部 pass 基座≈I（三档逐位等价）⇒ 恒按 mode 0 执行，保持参照零介入。
  4. **配置收口**：`ConstraintCompensateMode` ∈ {0=plain, 1=三明治存档, 2=姿态帧共轭}
     默认 2；老布尔 `ConstraintBaseCompensate=false` 兼容映射强制落 0。
  **待用户验证**：四方向 + 正朝向 + 竖直的开镜换弹/点射——报「整体还转不转 /
  斜向还漏不漏 / 枪口跟手感」。若有任何异样，`ConstraintCompensateMode=0`
  即刻回到今日实测定案的 plain 状态。

- **第 32 轮：mode-2 首测被配置陷阱拦截——用户回报「不转/漏/自然」实为 mode 0 复测**：
  1. **用户回报**：配置 `ConstraintCompensateMode=2`，三问答卷「整体不转 /
     斜向后坐力仍漏 / 枪口跟手自然」。这三项拼起来正是 **mode 0 plain 的已知
     指纹**——整体不随朝向转 ✓（B 格实测结论复现）；四方向斜向侧漏 ✓（= 本案
     最原始的病灶，plain 下必然复现）；无锁无刚性 → 跟手自然 ✓。若 mode 2
     真在运行，不会出现与 plain 逐位相同的画像。
  2. **原因自查（代码定罪，非用户操作失误）**：用户配置文件里同时留着上一轮
     A/B 设置的 `ConstraintBaseCompensate=false`；第 31 轮为「兼容既有设置」
     写的 legacy 映射（老布尔优先、false 强制落 mode 0）把用户显式设置的
     `ConstraintCompensateMode=2` **静默否决**——mode 2 从未真正运行。映射规则
     虽写进了配置注释，但用户没有任何理由预期上一轮实验的残留键会反过来
     否决新档位键；优先级设计本身就是错的。
  3. **修正（本仓库默认已含）**：档位判定**唯一信源化**——只认
     `ConstraintCompensateMode`∈{0,1,2}（越界值回落 2）；`ConstraintBaseCompensate`
     保留注册但代码零读取（旧文件原样加载）。另加每进程一次性生效档播报日志
     `[TACZ Case08] ConstraintCompensateMode effective=… (config=…, irisHandActive=…)`，
     不共享日志也能在 latest.log 一眼自查「这一局跑的到底是哪一档」。
  **待用户复测（本次 mode 2 将真正生效）**：原三问不变——①整体还随朝向转吗
  ②斜向后坐力还漏吗 ③枪口跟手感自然吗。任何异样 `ConstraintCompensateMode=0`
  秒回 plain（已被两次实测确认的全消态）。

- **第 33 轮：mode 2 在体否决——「①转 / ②不漏 / ③不自然」，默认回落 plain**：
  1. **用户实测**（自行把旧布尔置 true 使 mode 2 真实生效，免去复测轮次）：
     ①「整体随朝向转」**复现**；②四方向斜向后坐力侧漏**已消除**；
     ③枪口跟手**不自然**。⇒ 姿态帧共轭与 Bᵀ 三明治**双双归档**：
     两枚「换帧写 diag」的公式修复都被实证会把本案主症状带回来；
     ②同时证明「各向异性归位 authored 帧可消斜向漏」这一半的思路有效。
  2. **处置（止损优先）**：`ConstraintCompensateMode` 默认值改为 **0（plain）**——
     用户两次全场实测确认的「本案症状全消」态；已知代价 = 8/10 前就存在的
     四方向斜向后坐力侧漏原样回归（用户认可的可忍受基线；老配置文件里已
     写死的 2 不会随默认值变化，需手动改 0）。档位 1/2 保留注册供存档复现，
     代码里标注 REJECTED。
  3. **方法论记录**：坐标框架模型已两次在体自相矛盾（第 30 轮的代数不相容 +
     本轮 mode 2 理论预测「authored 即全朝向一致」与实测「转」直接冲突）——
     **不再从脑内模型推导第三枚公式**。下一轮先做机制判别问诊（转 = 真随朝向
     摆动还是固定视野偏移；不自然 = 指向性过强 / 发僵 / 方向怪），拿到指纹
     再决定是修 carrier 假设还是接受 plain 为终态。

- **第 34 轮（收口）：机制判别两问定案 + 挂起为「已知遗留」**：
  1. **用户判别答卷**：mode 2 的「转」= **与 8/11 病状同一套**（随朝向摆动
     指纹，非固定视野偏移）；「不自然」= **与锁视角同款的指向性过强**
     （枪口瞬钉准星、延滞被吃掉），非动画发僵亦非方向错位。
  2. **机制定案**：两答互为投影——三枚被否决修复（三明治 Bᵀ / 锁 modelView /
     姿态共轭 P）的共同指纹 = **都把某种随视角逐帧运动的参考系烙进了写入**：
     静态投影 = 「整枪随朝向转」，动态投影 = 「指向性过强」。而 plain（mode 0）
     是唯一不含任何逐帧视角成分的形态，也恰是唯一两次全场实测全干净的形态。
  3. **不可修性论证（本案证据边界）**：正确修复必须借助某种参考系信息，而 26.2
     手部 pass 里所有可读矩阵均已**在体逐个证伪**——入口基座 B（mode 1 否决）、
     RenderSystem modelView（锁否决；且第 26 轮枪口一案实证「仅兼容保留不受控」、
     第 29 轮再实证 ~0.7% 缩放污染）、当前姿态帧 P（mode 2 否决）。可用信息
     不存在 ⇒ 四斜向侧漏在现有证据条件下**从该函数内部不可修**；继续推导第四枚
     公式只是重复同一错误。
  4. **终态处置**：`ConstraintCompensateMode` 默认 = 0（plain = 本案全症状全消态，
     即相对已发布 Beta-3-Hotfix 的回退点）；四方向斜向侧漏**挂起为已知遗留问题**
     （同案例③ ScopeMaskHullFill=false 的止损惯例），未来若有可信的 26.2 手部
     pass 帧数据或上游链结构变化再重启。发布文档已补次回 Changelog 草稿
     （`docs/publish/{Modrinth,CurseForge}.md` §⑥），向用户宣告 8/10 斜向修复
     的回退及原因、并登记斜向侧漏为已知问题。
  5. **用户操作遗项**：老配置文件里被写死的 `ConstraintCompensateMode = 2`
     不会随新默认值变化，需手动改 0（或删除该键让其重生成）；废弃键
     `ConstraintBaseCompensate` 保留与否均无效果。

- **第 35 轮：26.1.2 移植线同症在体修复成功，Q/C 洞见回流（用户提供其结论），
  转写为本仓 mode 3 待命**：
  1. **隔壁的关键发现（一句话）**：本案从来不只是「第二次该乘 B 还是 Bᵀ」——
     代码里乘的系数 D=(ICAx−1, ICAy−1, 1−ICAz) 与槽位写回 (−x,−y,+z) 合并后
     的 C=(1−ICAx, 1−ICAy, 1−ICAz) 才是真正的 authored 系数；写回里藏着
     Q=diag(−1,−1,+1)，而 **Q 与旋转不可交换**。此前所有修复形态（26.2 否决的
     mode 1/2、锁，及经 PORT_SYNC §4-B2 带给 26.1.2 的同款三明治）共轭的都
     只有 D，Q 每次被留在写入帧 ⇒ 纯偏航出二倍角/象限指纹、偏航×俯仰组合
     出竖直后方/过压——与本案例全部实测指纹逐条吻合。隔壁的验证链：
     authored = Wᵀ·v0 → C·authored → world = W·constrained → 槽位（不带翻号），
     带回后 = C·authored，全朝向精确。
  2. **本链转写（保持我们槽位写回旧约定，即写回仍带 Q）**：写入向量
     v = Q·W·C·Wᵀ·v0 = **Ŵ·D·Wᵀ·v0**（Ŵ = Q·W·Q；W = mulDirection 当帧姿态
     3x3，即前文的 P_pre；左右两侧同用这一张帧）。系数行 D 原样沿用，
     唯一改动 = 左半姿态帧换成其 Q 共轭。纯偏航/纯俯仰下 Ŵ = Wᵀ，形态上
     等价于「c975748 三明治把入口基座快照换成写入当帧活姿态」——此前
     三版被拒形态之外的第四种排列，从未测过。
  3. **处置**：实现为 `ConstraintCompensateMode=3`（范围扩至 0..3），
     **默认仍是 0**——本案三条教训都是「先默认后被实测打脸」，26.2 在体
     验证通过前不翻默认；Iris 手部 pass 恒走 mode 0（基座≈I 时各档
     逐位一致），不受影响。生效档播报日志会打印 effective=3 供自查。
  4. **诚实存疑点**（已在 PORT_SYNC 附录 B 向隔壁请求核对）：隔壁实现尚未
     推送到远端，转写基于其文字公式；两处可能与其实现有出入——①写回槽位
     符号（他们是否连 m30−=/m31−=/m32+= 一起改了）；②其 B 的实际取值位点
     （写入当帧姿态栈 3x3，还是入口捕获基座）。若复测不过，先取回其原版
     实现逐位比对再改，不再自行变形。
  **待用户复测**：`ConstraintCompensateMode = 3` 后原三问——①整体还随朝向
  转吗 ②四斜向后坐力还漏吗 ③枪口跟手感自然吗。答「不转、不漏、自然」
  ⇒ 翻默认为 3、案例⑧ 正式结案、发布文案 §⑥ 改写为「修复」而非「回退」；
  有任何一项不过 ⇒ 回 0，维持第 34 轮挂起态，等隔壁实现推送后再逐位核对。

- **第 36 轮（结案）：mode 3 在体三项全过——用户答卷「不转、不漏、自然」**：
  1. **裁决结果**：`ConstraintCompensateMode=3` 下 ①整体不随朝向转 ✓、
     ②四方向斜向后坐力侧漏消除 ✓、③枪口跟手自然 ✓——26.1.2 的在体验证
     形态在 26.2 完美复现，案例⑧ 正式结案。
  2. **定案根因（终版，两仓通用）**：`applyAnimationConstraintTransform` 的
     系数 D=(ICAx−1, ICAy−1, 1−ICAz) 与槽位写回 (−x, −y, +z) 合并后的
     C=Q·D（Q=diag(−1,−1,+1)）才是真正的 authored 系数；**Q 与旋转不可交换**。
     26.2/26.1.2 vanilla 手部 pass 预乘随朝向的相机基座（Iris 不预乘）后：
     plain（不共轭）= Wᵀ·C·W 型泄漏，纯偏航二倍角/象限横向侧漏（8/10 前老 bug）；
     只共轭 D 而把 Q 留在写入帧的各形态 = Q×旋转不可交换残差直接烙进结果
     ⇒ 整枪随朝向转 / 竖直跑后方 / 后坐过压（本案指纹，26.2 实测三版全灭）。
     修复 = **Ŵ·D·Wᵀ·v0**（Ŵ=Q·W·Q，W=写入当帧姿态 3x3）：把含 Q 的完整 C
     放回姿态帧内共轭，下游带回后 = C·authored，全朝向与 1.21.1 一致。
     Iris 基座≈I，各档逐位等价，恒走 mode 0。
  3. **落地**：`ConstraintCompensateMode` 默认翻 3（含配置默认值与代码内
     null/越界回落）；mode 0 = 秒回退档保留，mode 1/2 归档勿用；废弃键
     `ConstraintBaseCompensate` 维持零读取。发布文案 §⑥ 已由「回退」稿
     改写为「修复」稿；PORT_SYNC 附录 B 更新为「26.2 在体复现通过」，
     保留向隔壁索取原版实现做逐位对齐的请求（防两仓未来分叉）。
  4. **跨仓教训（沉淀）**：本案横跨 26.2/26.1.2 两线共 12 轮 3 版公式试错，
     终点认识来自**邻链在体修复**而非本链内推——PORT_SYNC 双向通道首次
     完成「修复回流」闭环；凡渲染数学类疑难，跨链症状同源案件优先互查
     对方在体结论，再决定本链实验。

#### 案例⑨：开镜时物理目镜框（ocular_ring）内环被掩码啃掉 —— 26.1.2 邻链回流适配（2026-08-12 立案，当日落地待复测）

- **立案来源**：26.1.2 移植线在体定位+修复（commit `0b7c4cd` / PR #39，用户提供）。
  上游 1.21.1 对 `ocular_ring` 有明确特殊路径：它是**独立骨骼的实体件**
  （物理目镜框/黑色内圈），写模板前以 `GL_ALWAYS` 单独绘制，**不是**孔径/遮光几何；
  默认枪包 **14 个中高倍镜全部包含该骨骼**，故病灶逐镜统一出现。
- **26.2 同源病灶（掩码架构形态）**：本仓代码从未收集 `ocular_ring` —— 它随整个
  配件树走 `super.submit`，开镜掩码激活时与镜身共用裁剪版 RenderType ⇒ 凡与目镜
  投影在屏幕上重叠的环形像素被掩码 discard。这正是**案例③ 遗留已知问题**
  「hull 略大于真实孔径、镜框内圈边缘被啃」的真正构成之一（hull 偏大只是加剧
  因子，主因是物理环根本不该进裁剪批）。
- **处置（默认开，开关 `ScopeOcularRingFix`）**：开镜掩码激活（`maskable==true`）时
  把 `ocularRingPart` 从主提交摘除（`visible=false`，`capturePart` 剪枝语义下连
  子树一并摘除，finally 必还原），主提交结束后用**未裁剪的原版 RenderType** 经
  `BedrockRenderSnapshot.captureSubtree` 重画含子树的完整几何；父链遍历-套用写法
  与 `registerOcularMaskGeometry` 同构（captureSubtree 调用约定，矩阵与主渲染一致）。
  - 邻链步骤 → 本架构等价物对照：「按完整骨骼变换冻结独立 snapshot」= 同构
    父链遍历 + captureSubtree；「depth cleanup 后 order 1 重画/reticle 顺延 2」
    = 本架构无 order 概念，按提交顺序：镜身 → 目镜框 → 准星（目镜框为 opaque
    cutout，深度测试下顺序不敏感）；「重新写入镜框深度防水/雾/透明粒子」
    = 本架构普通 cutout 天然回写深度，免费成立、无需额外步骤。
  - 不影响面：无 `ocular_ring` 骨骼的第三方模型、腰射、第三人称、Iris 回退
    路径（maskable=false 时开关不生效，完全走旧路径）。
- **开关**：`ScopeOcularRingFix`（默认 true；false 即整体回到旧行为）。
- **待用户复测**（沿邻链建议清单）：ACOG（acog_ta31）、AUG 自带镜（aug_default）、
  LPVO（lpvo_1_6，含子骨骼）、8x（standard_8x，含子骨骼）、HAMR/Vudu 双视组——
  看点：镜框内圈在开镜全程是否完整实黑、无被啃缺口，且镜内透视与准星无回归。
  一句「正常/有图」即可裁决；若有任何异样 `ScopeOcularRingFix=false` 秒回退。
  - **第二轮（同日，用户补充指纹后追加）**：用户指认「这个 bug 很早就有了——低倍镜
    （包括组合镜的低倍组）的边的内部某些部分被错误裁切」。代码级事实核查闭环：
    ① 上游逐行事实（archive/SCOPE_UPSTREAM_TRUTH §4）：`renderSight` 无任何圆形
    INVERT 模板、`scope_body` 无条件绘制；`renderBoth` 只对 `ocular_scope*` 组走
    筒镜逻辑 ⇒ 上游对 sight 通道从来不做镜身裁切，所以我们 r34 时代文档才写着
    「sight 不剔除镜身、目镜恒掏空」语义一致。② 我们掩码架构落地时 `maskable`
    写的是 flat 条件 `!ocularParts.isEmpty()` ⇒ sight 通道（纯红点 + 组合镜低倍
    组）也被卷进镜身裁剪；红点模型没有 `ocular_ring` 骨骼，被啃的是普通镜身几何
    的内框/边缘——正是用户描述。③ sight 目镜由 `shouldDrawOcularBlackout` 恒隐藏
    （恒掏空=透视窗，证于案例③ 33 款贴图 alpha=255 取证链），故撤裁无次生影响。
    **修复**：`maskable && ScopeSightClipFix && !activeGroupIsScope()` ⇒ maskable=false
    （组合镜随当前通道切换；查不到分组信息时维持旧行为，与既有「宁可多画」原则
    一致）。开关 `ScopeSightClipFix`（默认开；false 秒回退旧行为）。
    邻链注意：26.1.2 的 `apertureActive` 是同款 flat 条件 ⇒ 其深度孔径对 sight
    通道大概率留有同款慢性病灶（ring 修复只摘了倍镜的环），已在 PORT_SYNC 附录 C
    反流给他们建议同款 gating。
  - **合并复测清单（两个开关都默认开）**：纯红点/全息各款 + retro_2x / qmk152 类
    低倍镜 + ACOG、AUG 自带镜 + LPVO@1x 与筒镜组、8x 筒镜 + HAMR/Vudu 两个视组
    各切一遍——看点：低倍/红点通道窗框完整无缺口、倍镜通道黑环完整、两通道
    镜内透视与准星均无回归。`ScopeOcularRingFix=false`、`ScopeSightClipFix=false`
    可各自独立秒回退定位。
  - **第三轮（同日，复测回归修正）**：用户复测「低倍好了，但组合镜高倍组与 AUG
    默认镜**失去一切裁剪痕迹**」⇒ 第二轮判别器误伤排查。根因 = **把命名当物理属性**：
    第二轮直接读 `ocularIsScopeByIndex`，而它的语义只是「节点名带不带
    `ocular_scope` 前缀」——纯倍镜（AUG 默认/ACOG/lpvo，乃至 8x 之外几乎全部）
    的目镜名是普通 `ocular`，映射恒 false ⇒ 被误判「非筒镜」误关裁剪。
    数据全表核实（display json）：纯筒镜单 flag「scope:true」（acog/aug/8x/lpvo/
    scout/qmk152/contender/elcan/1873/98k/retro…）；纯红点「sight:true」；真组合镜
    双 flag（hamr/vudu/mk5hd，views=[2,1]/[2,1]/[2,2,1]）；另有 8x views=[1,1]、
    lpvo views=[2,2] 这类「flag 与命名不同构」实例。**修正**：判别输入换成上游
    三分支同源的「双 flag + views 当前通道」——纯筒镜恒裁、纯红点恒不裁、组合镜
    看当前 channels 组映射、映射缺失回退 true（保守维持旧行为）。开关与语义不变。
  - **第三轮后复测矩阵（两开关均默认开）**：①纯红点/全息：不裁、窗框完整（理应
    与第二轮一致）；②AUG 默认/ACOG/8x/lpvo 高倍：恢复裁剪（镜内透光）、黑环
    完整；③hamr/vudu/mk5hd：红点组不裁、筒镜组恢复裁剪；④elcan/views=[2,2] 的
    低倍视组属筒镜通道 = 维持裁剪（其内环靠第一轮 ocular_ring 修复保完整）。

#### 案例⑩：PAL 趴姿（TACZ 强制 Pose.SWIMMING）消退后动画状态污染 —— 26.1.2 邻链修复 1:1 直贴（2026-08-12 立案，当日移植，待用户复测）

- **案例⑨ 状态先记**：第 1~3 轮（ocular_ring 独立路径 / sight 通道撤裁 /
  判别器 flag 化纠错）用户复测 **PASS**，案例⑨ 结案（因端口沿用合并条目，
  不再单开结案行）。
- **邻链修复（commit `e43a3a9d` / PR #39，在体验证）**：PAL 1.2.5 的 fade 以
  「携带离场骨骼变换快照的 modifier」实现；TACZ 趴姿片段与站姿片段坐标轴
  不同、手臂偏移差异大 ⇒ 趴姿 fade 快照跨过趴→站边界滞留后，后续 draw/fade
  会拿旧快照当起点，反复循环还会**累积**旧欧拉旋转，只能切第一/第三人称
  之类无关渲染重置模型才解。修复内容（逐字直贴，PR 基线与 26.2 该文件
  **逐字节相同**，`git apply` 零冲突）：
  1. 新增 `discardProneTransitionOnStand`：WeakHashMap 追踪每玩家趴姿态，
     `play()`/`stopAll()`/切枪三入口在**趴→站边界**对四个 controller
     执行「只删 AbstractFadeModifier + stopTriggeredAnimation + stop +
     forceAnimationReset」——刻意**不动** ROTATION 的 SafeAdjustmentModifier
     （保住 rotation adjustment、不引回初始化 NPE），不碰未跨边界的普通 fade；
  2. `playNamed` 的「同 clip 抑制」加 `controller.isActive()` 门控——已停止
     但记录同 clip 的 controller 可以重播（开火/换弹等一次性动画可重启）；
  3. 切枪不再对 ROTATION 层做 stop（只 lower/loop-upper/once-upper，8 tick
     安全 fade-in-to-null 路径不变）——对齐旧 PlayerAnimator 契约。
  PAL 版本两侧同为 1.2.5（我们 `gradle.properties: player_animation_lib=1.2.5`），
  API 面一致，直贴不存在版本风险。
- **不适用条目记录**：同 PR 的 `6c0d004`（preserve visible depth during cleanup）
  是深度孔径架构专属（ScopeDepthCopyState/ScopeRenderTypes/scope_depth_cleanup.fsh），
  26.2 掩码架构无对应物，**不移植**。
- **待用户复测**（沿邻链协议，两条路径各 5~10 次）：
  1. 持枪趴下 → 按趴姿键站起 → 切枪/重新持枪；
  2. 持枪趴下 → 切到非枪物品站起 → 再切回枪；
  并确认 ①不再需要切第一/第三人称来清污染 ②开火/换弹/近战/固定手型/普通
  移动动画全部正常。一句 PASS/异常即可。
- **第 1 轮在体裁决（用户）：「人家修好了，你没修好」** —— 26.1.2 构建按
  上述协议通过，26.2 构建（含第 1 轮 1:1 移植）仍复现。同码不同效。
- **第 2 轮：同码不同效逐文件取证 + tick 级观测点 + 全链路探针**
  - **取证**（两侧 commit 级比对）：PalAnimationManager 全字节相同；
    PAL 依赖同为 modrinth 1.2.5；InnerThirdPersonManager /
    HumanoidModelMixin / PlayerModelMixin / PlayerAnimatorCompat /
    ItemInHandRendererMixin、趴姿系统（LocalPlayerCrawl/LivingEntityCrawl/
    PlayerMixin）全部 zero-diff；两侧全树 PAL/probe 接触面只有邻居多一个
    Iris 手部相位谓词（其深度架构专属，本案无关）。**结论：差异不在
    TACZ/PAL 代码，只能在 vanilla 26.2 的渲染驱动时机。**
  - **机制性解释（最可信）**：第 1 轮修复的边界观测全靠渲染驱动
    （`HumanoidModel.setupAnim → InnerThirdPersonManager → play/stopAll`）
    或切枪事件。而 26.2 第一人称下本地玩家本体不渲染、手部渲染的
    `AvatarRenderState` 恒 `ageInTicks == 0`（PlayerModelMixin 第 0 帧守卫
    直接 return）⇒ **第一人称全程对本地玩家不产生任何 play()/stopAll()
    调用**，`LAST_PRONE_STATE` 从未被写入过 `true`，趴→站边界恒观测不到，
    复位永不触发（切到非枪物品后同样无实体渲染驱动）。26.2 的机制性修复：
    **`PalAnimationManager.init()` 内补一个 `END_CLIENT_TICK` 观测**
    （本地玩家非空才读姿势与配置），与渲染路径共享同一张
    LAST_PRONE_STATE——非边界 tick 只是一次幂等 map put；边界复位
    从此「下一 tick 必然发生」，不再依赖「恰好有渲染/切枪」。
    开关 `PalProneTickObserver`（默认开，false 秒回第 1 轮形态）。
  - **同码不同效定位探针（r2，临时）**：init 标记行
    `[TACZ Case10] PAL prone-exit fix probe r2 loaded`（日志没这行 =
    测的 jar 不含修复）；`observe`/`transition` 行只在首次见到玩家与
    趴姿翻转瞬间各打一条，附 source（tick/play/stopAll/gunDraw）与
    playerHash；趴→站边界打 `edgeReset applied` + 四个 controller 的
    复位前快照（active/triggered/curAnimHash）。三岔口判定：
    ① 连标记行都没有 → jar 陈旧；② 有标记行但趴起全程无 transition →
    观测链确实喂不进状态；③ transition/edgeReset 齐全画面仍污染 →
    复位在 26.2 的 PAL 1.2.5 上不生效，带 controller 明细再查下一段。
  - 用户协议：构建后跑原两条复测路径（含趴/起/切枪各 2~3 次即可），
    把日志里所有含 `[TACZ Case10]` 的行连画面结论一起回传。
