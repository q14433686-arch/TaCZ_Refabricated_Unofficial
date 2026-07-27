# 未注册 Mixin 全量核查（r38 重做 / r39 执行）

> ## r39 执行进度（按 §F 顺序）
>
> | # | 项目 | 结果 | 未注册数 |
> |---|---|---|---|
> | 1 | `ExplosionMixin` | ✅ **删除**，改用官方 `onExplosionHit` | 12 → 11 |
> | 2 | `LootTableMixin` | ⚠️ **r39 实测崩溃 → r40 重做后重新注册** | 11 → 10 |
> | 3 | `tweakeroo.ItemMixin` | ✅ **删除**（注册必崩 + 功能已被取代） | 10 → 9 |
> | 4 | `ClipContextMixin` | ⏸ **主动跳过**，见下 |
>
> ## r42 收尾（客户端测试 PASS 后）
>
> 原则：**有价值就修，没价值且有风险就不动。** 未注册 **9 → 7**，
> 且剩下的 7 个已全部定性，无一需要再动。
>
> | Mixin | 处置 | 依据 |
> |---|---|---|
> | `ClientPacketListenerMixin` | ✅ **功能恢复，mixin 删除** | 改用轮询检测 `Minecraft#player` 实例替换，零 mixin |
> | `ArmorStandMixin` | ✅ **删除（冗余）** | 同一功能已由已注册的 `AbstractMinecartMixin` 覆盖 |
> | `HumanoidModelMixin` | 📌 标注永久废弃 | render state 拿不到实体，重写成本极高、收益极小 |
> | `SoundEngineMixin` + `ChannelAccessHandleMixin` | 📌 标注「可修但无收益」 | 真名已查到并记录在类注释，但下游事件零消费者 |
> | `ShapedRecipeMixin` | 📌 标注永久废弃 | 功能已被原版 `ItemStackTemplate#components` 取代 |
> | `MinecraftAccessor` / `carryon.ConfigLoaderMixin` / `ClipContextMixin` | 📌 维持不动 | 前两者所在模块已被 build.gradle exclude；后者本 mod 内永不触发 |
>
> **本轮三个关键发现：**
>
> 1. **`ArmorStandMixin` 是纯冗余**。反编译 `ArmorStand.lambda$static$0` 看到
>    `RIDABLE_MINECARTS` 谓词实现就是
>    `e instanceof AbstractMinecart m && m.isRideable()` ——
>    而 `isRideable` 正是**已注册**的 `AbstractMinecartMixin` 的注入点。
>    1.21.1 需要两处补丁，26.2 一处就够了。
>
> 2. **`ShapedRecipeMixin` 的功能被官方实现了**。26.2 配方结果类型换成
>    `ItemStackTemplate`（record），自带 `components: DataComponentPatch` 字段
>    —— 「结果里带自定义数据」原版 codec 已原生支持，
>    不再需要 Forge 式的 `nbt` 字段补丁。
>    （注意这意味着数据包里旧的 `"nbt":` 写法需迁移为 `"components":`。）
>
> 3. **重生刷新不需要 mixin**。原功能只是「玩家实例被换掉后延迟 10 tick
>    调一次 `initialData()`」——**本就不需要精确时机**
>    （原实现自己还要再 `DelayedTask` 延迟 10 tick，因为事件触发时背包未同步）。
>    而驱动 `DelayedTask` 的 tick 回调**本来就已注册**，
>    在里面加一次引用比较即可，零新增开销、零 mixin。
>
> **一条通用经验**：这批「失效 mixin」里，真正需要重写的只有 `ExplosionMixin`
> 和 `LootTableMixin` 两个；其余要么被原版新 API 取代（`onExplosionHit`、
> `ItemStackTemplate#components`、`isRideable`），要么根本不需要 mixin
> （轮询即可），要么下游没人用。**先问「这功能现在还需要 mixin 吗」，
> 往往比直接改注入点更省事、也更耐版本升级。**
>
> **第 1 项的结论比预期好**：不需要重写 mixin。反汇编
> `ServerExplosion#interactWithBlocks` 发现它调的是
> `BlockState.onExplosionHit(ServerLevel,BlockPos,Explosion,BiConsumer)`，
> 而这是 `BlockBehaviour` 上的 **protected 可覆写方法**，原版已有 9 个方块在用
> （`DoorBlock`/`BellBlock`/`BeehiveBlock`/`AbstractCandleBlock` 等）。
> 26.2 已官方提供该扩展点 → 直接在 `StatueBlock` 覆写，删掉 mixin 与
> `IBlockExtension` 接口。**少一个 mixin 就少一处版本升级会断的地方。**
>
> ### ⚠️ 第 2 项：r39 判断错误，r40 已重做（务必读）
>
> **r39 我说「TODO 只对了一半，不需要搬到加载路径」——这是错的，用户实测崩溃：**
> ```
> IllegalStateException: Missing registry: ResourceKey[minecraft:root / minecraft:loot_table]
>   at RegistryAccess.lookupOrThrow → LootTableInjectorModifier.resolveId
>   at BlockBehaviour#getDrops → Block#dropResources → WaterFluid#beforeDestroyingBlock
> ```
> 挖方块、甚至**水流冲毁方块**都会触发，直接崩服务端。
>
> **我的方法论错误**：r39 只核对了「`MinecraftServer#registryAccess()` 这个方法存在」，
> 就断定链路可用。但「方法存在」与「**这个注册表在运行时真的在里面**」是两回事。
> 26.2 的 `RegistryLayer` 分四层：`STATIC` / `WORLDGEN` / `DIMENSIONS` / `RELOADABLE`。
> `server.registryAccess()` 只覆盖前三层，而战利品表随数据包重载，属于
> **`RELOADABLE` 层**，由 `MinecraftServer#reloadableRegistries()` →
> `ReloadableServerRegistries.Holder` 单独持有。
> **那句 TODO 是完全正确的，是我推翻错了。**
>
> **r40 的重做方案**（三层防护）：
> 1. **改用 RELOADABLE 层**：`server.reloadableRegistries().lookup()`；
> 2. **反查改正查**：该层只给 `HolderLookup.Provider`，没有由值反查 key 的接口
>    （`HolderLookup` 上的 `key()` 返回的是注册表自身的 key）。
>    虽可用 `listElements()` 全表遍历，但那是 O(n)。
>    改为掉头利用「**我们本来就知道要注入哪些表**」——
>    `LootInjectionManager#injections` 正是以目标表 ID 为键的 Map，
>    新增 `getInjectionTargets()` 暴露键集，拿这个候选集去正查比对实例。
>    默认枪包只有 1 个目标（`minecraft:chests/spawn_bonus_chest`）；
> 3. **整体 try-catch 兜底**：本方法位于方块掉落主干路径，
>    战利品注入是锦上添花的功能，绝不该把主流程带崩。
>    任何异常只记一次日志并返回原始掉落。
>
> 另外用 `NOT_A_TARGET` 哨兵缓存「查过且不是目标」的结果 ——
> 否则 `computeIfAbsent` 映射到 null 等同「不存在」，
> 每次非目标方块掉落都会重跑一遍候选集遍历（最热路径上的无谓开销）。
>
> `ID_CACHE` 的强引用泄漏修复保留（`HashMap` → `synchronizedMap(WeakHashMap)`，
> 战利品表每次 `/reload` 都会整体重建）。
>
> **第 4 项为什么跳过**：`ClipContextMixin` 目标与注入点都在，能注册，
> 但全仓两处 `new ClipContext` —— 一处传 `this`（永不 null）、
> 一处走直接收 `CollisionContext` 的另一重载（不经过注入点）——
> **本 mod 内一次都不会触发**。注册它只会给本轮实机测试凭空多加一个变量，
> 却测不出任何差别。等有实际需求（例如兼容其他 mod 传 null）再启用。

---



**日期**：2026-07-27（第 38 轮）
**方法**：对当前 HEAD 重新扫描，逐个成员对 26.2 字节码核对（`.scratch/chk.py`），
不沿用任何历史结论。

---

## 0. 现状

`@Mixin` 类 **53** 个，已注册 **41**，未注册 **12**。

> 比上一版审计（15 个）少 3 个：`ItemStackMixin`（r27 删）、
> `RenderTargetMixin`、`AbstractSliderButtonAccessor`（r33 删）。

| 分级 | 数量 | 含义 |
|---|---|---|
| A. 可直接注册 | 1 | 目标+注入点全部核对通过 |
| B. 改注入点即可 | 3 | 目标在，只是 Yarn 中间名要换成真名 |
| C. 需重写 | 4 | 目标 API 在 26.2 已重构 |
| D. 条件加载 | 2 | 第三方 mod 目标 |
| E. 永久废弃 | 2 | 目标概念已不存在 |

**一个重要前提**：这 12 个所依赖的**我方代码全部还在**
（`IBlockExtension` / `IMinecart` / `LootTableInjectorModifier` /
`CraftingHelper` 等均有 2~7 处引用）。
所以这些是「目标 API 变了」，不是「功能已废弃」——修复有意义。

---

## A. 可直接注册（1 个）

### `common.ClipContextMixin`
- 目标 `ClipContext.<init>` ✅
- 注入点 `INVOKE CollisionContext.of(Entity)` ✅（`<init>` 偏移 8 确认）

**但收益为零**，不建议单独为它开一轮测试：全仓只有两处 `new ClipContext`——
`EntityKineticBullet:313` 传 `this`（永不 null）、
`ProjectileExplosion:161` 走的是直接收 `CollisionContext` 的**另一个重载**，
根本不经过这个注入点。它纯粹是给其他 mod 传 null 兜底
（26.2 的 `CollisionContext.of` 里 `Objects.requireNonNull` 会先 NPE）。

> 结论：可以顺手注册，但**测不出任何差别**，不适合作为验证变量。

---

## B. 只需把 Yarn 中间名换成真名（3 个）

本项目历史上混用了 Yarn 名（`method_xxxxx`），这是这批失效的共同原因。
**26.2 的真名已全部查到**：

| Mixin | 原注入点 | 26.2 真名 |
|---|---|---|
| `client.ChannelAccessHandleMixin` | `method_19737` ❌ | **`lambda$execute$0`**`(Consumer)V` ✅ |
| `client.SoundEngineMixin` | `play` ✅ / `method_19757` ❌ | `play` 存在；lambda 是 **`lambda$play$1`**`(ChannelHandle,SoundBuffer)V` 与 **`lambda$play$3`**`(ChannelHandle,AudioStream)V` |
| `common.ArmorStandMixin` | `method_6918` ❌ | ArmorStand 里**查无对应方法**，且见下方矿车问题 |

`ChannelAccess$ChannelHandle` 类、`execute(Consumer)V`、`channel` 字段均确认存在。

> ⚠️ **但这两个音效 mixin 值不值得修，要先想清楚**：
> 它们最终驱动的是 `PlaySoundSourceEvent`，而该事件在全仓
> **零消费者**（只有 `ChannelAccessHandleMixin` 自己 `invoker().post(...)`）。
> 修好也没有任何功能变化。建议**降到最低优先级**或直接标废弃。

`ArmorStandMixin` 的情况更糟，见 §C。

---

## C. 目标已重构，需重写（4 个）

### C1. `common.ExplosionMixin` — 类已拆分
26.2 的 `net.minecraft.world.level.Explosion` **已变成接口**
（`extends Object`、**零字段**、方法全是 `level()`/`radius()`/`center()` 这类访问器）。
- `finalizeExplosion` ❌ 不存在
- `@Shadow public Level level` ❌ 接口无字段

真正干活的实现类是新增的 **`ServerExplosion`**，相关方法：
```
explode()I
calculateExplodedPositions()List
interactWithBlocks(List)V          ← 方块交互在这里
lambda$interactWithBlocks$0(List,ItemStack,BlockPos)V
```
**重写方向**：`@Mixin(ServerExplosion.class)` + 注入 `interactWithBlocks`。
我方的 `IBlockExtension#tacz$onBlockExploded` 还在（3 处引用），接口不用动。

### C2. `common.ArmorStandMixin` — 矿车体系整体重构
- `method_6918` ❌
- `AbstractMinecart` **换包**：`world.entity.vehicle` → **`world.entity.vehicle.minecart`**
- `getMinecartType()` ❌ 不存在，`AbstractMinecart$Type` 枚举也没了
  （26.2 拆成 `NewMinecartBehavior`/`OldMinecartBehavior`）

整个 `@Expression("?.getMinecartType() == RIDEABLE")` 失去意义，需按新的
behavior 体系重新设计。我方 `IMinecart#tacz$canBeRidden` 仍在（4 处引用）。

### C3. `common.ShapedRecipeMixin` — 配方已 codec 化
`itemStackFromJson` ❌ 不存在。26.2 配方全面 codec 化，不再有 JSON 手工解析入口。
该 mixin 的目的是让配方支持 `nbt` 字段，需改为在 codec 层扩展，
或确认 `CraftingHelper`（7 处引用）在新体系下的落点。

### C4. `common.LootTableMixin` — **上一版审计此处有误，本轮更正**

> 上一版说「26.2 实际是 `getRandomItems(LootParams,long,Consumer)V`，
> 原签名已不存在，`@ModifyReturnValue` 对 void 无意义」。
> **这是错的。** 实测 `LootTable` 同时有 9 个重载，
> 其中 **`getRandomItems(LootContext)ObjectArrayList` 确实存在** ✅，
> 与 mixin 声明逐字匹配。

所以它**不是签名不符**，而是**逻辑本身会崩**——
`LootTableInjectorModifier` 自己的文件头就写着 TODO：

```
In 26.2 loot tables are resolved by HolderGetter/LootContext and are not
available from ServerLevel.registryAccess(), which caused crashes while
blocks dropped items.
```

即 `doApply` 里 `registryAccess().lookupOrThrow(Registries.LOOT_TABLE)` 反查 ID
的做法在 26.2 会导致**挖方块掉落时崩溃**。
**重写方向**：改到战利品表**加载路径**注入（那时 `ResourceKey` 还在手上），
而不是在掉落生成时反查。

---

## D. 第三方 mod 目标（2 个）

| Mixin | 状态 |
|---|---|
| `compat.carryon.ConfigLoaderMixin` | Carry On 无 26.2 版（最高 1.21.11）。**且已被 build.gradle 第 276 行 exclude**，源码根本不参与编译 |
| `compat.tweakeroo.ItemMixin` | Tweakeroo 目标，未 exclude |

现成范式已经有了：`MixinPlugin#shouldApplyMixin` 会对
`cn.sh1rocu.tacz.mixin.compat.*` 取包名第 6 段做 `isModLoaded` 判断。
也就是说**只要注册进去，未装对应 mod 时会自动跳过**，不会崩。

> `tweakeroo.ItemMixin` 依赖 `IItem#tacz$getMaxStackSize`。注意 r34 之后
> 弹药堆叠已改由 `DataComponents.MAX_STACK_SIZE` 实现，
> 这个 mixin 的意义需要重新评估（可能已冗余）。

---

## E. 建议永久废弃（2 个）

| Mixin | 理由 |
|---|---|
| `accessor.MinecraftAccessor` | `Minecraft.pausePartialTick` ❌ 不存在。26.2 用 `DeltaTracker`（`getDeltaTracker()` + `getGameTimeDeltaPartialTick(boolean)`）。**且其唯一使用者 `AdjustmentYRotModifier` 位于 `compat/playeranimator/animation/**`，已被 build.gradle 第 270 行整体 exclude** → 当前根本编译不到 |
| `client.ClientPacketListenerMixin` | 目标 `handleRespawn` ✅ 存在，但注入点 `ClientLevel#addPlayer` ❌ —— 对 `ClientLevel` 核对无此方法（近似只有 `players`/`addParticle`/`playPlayerSound`），`handleRespawn` 反汇编里也无任何 `addPlayer` 调用 |

`ClientPacketListenerMixin` 驱动的是 `ClientPlayerNetworkEvent.CLONE` →
`RefreshClonePlayerDataEvent`（已在 `TaCZFabricClient:99` 注册），
即**重生后枪械数据刷新当前失效**。这个功能有实际价值，
但要修得先找到 26.2 新的玩家实体替换时机 —— 属于「重新设计」而非「改注入点」。

---

## F. 建议处理顺序

按「**收益 ÷ 风险**」排，而不是按编号：

1. **`ExplosionMixin` → 重写到 `ServerExplosion#interactWithBlocks`**
   目标类已定位、我方接口完好，是这批里唯一「查清了就能动手」且有真实功能的。
2. **`LootTableMixin` → 改到加载路径注入**
   功能价值高（枪械战利品注入），但要先解掉 `LootTableInjectorModifier` 的反查崩溃。
3. **`compat.tweakeroo.ItemMixin` → 先确认是否已被 r34 的组件方案取代**
   若冗余就删，不冗余则直接注册（`MixinPlugin` 已能条件加载）。
4. **`ClipContextMixin` → 可随手注册**，但明确它测不出差别。
5. `ShapedRecipeMixin` / `ArmorStandMixin` → 需重新设计，排后。
6. `SoundEngineMixin` + `ChannelAccessHandleMixin` → **真名已查到**，
   但下游事件零消费者，修了没用；建议标注「已查明，暂不修」。
7. `MinecraftAccessor` / `carryon.ConfigLoaderMixin` → 标永久废弃
   （二者所在模块均已被 build.gradle exclude）。

---

## 附：本轮核验方法

```bash
# 1) 扫出未注册项（对当前 HEAD，不看历史文档）
python3 - <<'PY'
import json,os,glob,re
reg=set()
for f in glob.glob('src/main/resources/*.mixins.json'):
    j=json.load(open(f)); pkg=j.get('package','')
    for k in ('mixins','client','server'):
        for m in j.get(k,[]): reg.add(pkg+'.'+m)
for root,_,files in os.walk('src/main/java'):
    for fn in files:
        if fn.endswith('.java'):
            p=os.path.join(root,fn)
            if re.search(r'^\s*@Mixin\b', open(p,encoding='utf-8').read(), re.M):
                c=p.replace('src/main/java/','').replace('/','.')[:-5]
                if c not in reg: print(c)
PY

# 2) 逐成员核对 26.2 字节码（含近似名建议，用于找改名后的真名）
./venv/bin/python chk.py <<'EOF'
net.minecraft.world.level.Explosion|m:finalizeExplosion|f:level
EOF
```

**教训沿用 r33**：核对必须到**描述符**级别，
「类在 + 方法名在」不等于能注入（`HumanoidModelMixin` 就栽在这）。
本轮所有 ❌ 均附了近似名，便于定位改名目标。

---

## 附二：r41 —— 描述符还不够，泛型要看 `Signature` 属性

用户跑 `gradlew build` 后报出编译错误：

```
不兼容的类型: Optional<? extends RegistryLookup<T>> 与 Optional<RegistryLookup<LootTable>> 不一致
    Optional<HolderLookup.RegistryLookup<LootTable>> registry = lookup.lookup(Registries.LOOT_TABLE);
```

**根因是本文一直在用的核验手段有盲区**：字节码的 `descriptor` **擦除了泛型**，
所以看上去毫无问题：

```
lookup (Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;
```

而真正的泛型信息在 **`Signature` 属性**里：

```
<T:Ljava/lang/Object;>(Lnet/minecraft/resources/ResourceKey<+Lnet/minecraft/core/Registry<+TT;>;>;)
    Ljava/util/Optional<+Lnet/minecraft/core/HolderLookup$RegistryLookup<TT;>;>;
                       ↑ 这个 + 就是 ? extends，赋给不带通配符的 Optional<...> 必然失败
```

`.scratch/chk.py` 与 `mcq.py` 此前**只读 descriptor**，
因此对「方法在不在、参数类型对不对」有效，对**泛型形态**完全无能为力。

**改进**：涉及泛型 API 时改用

```python
m.get_signature()   # javatools 的 MethodInfo 提供，读 Signature 属性
```

本次据此发现 `lookupOrThrow` 更适合：它的签名是
`<T> RegistryLookup<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>>)`
—— 返回值**不带通配符**，`T` 直接由参数推断为 `LootTable`，
既避开协变赋值问题，也避开 `var` 推断出 capture 类型后
再调 `get(ResourceKey<LootTable>)` 的二次不匹配。
它在注册表缺失时抛异常，但已被 `doApply` 的整体 try-catch 兜住。

> **更普遍的提醒**：本仓库此前所有标注「未编译验证」的改动，
> 都可能存在同类泛型问题 —— 描述符核对通过 ≠ 能编译。
> 沙盒无 JDK，这类问题只能靠用户侧 `gradlew build` 暴露。
