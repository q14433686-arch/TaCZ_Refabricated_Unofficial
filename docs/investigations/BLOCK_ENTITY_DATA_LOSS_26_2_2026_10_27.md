# 26.2「代码在、行为不在」与两处必现崩溃：雕像掉枪、标靶主人、标靶头像皮肤、标靶 NPE

- **日期**：2026-10-27（R3-hotfix4 首轮修复；同一版本内二次返工，版本号不变）
- **触发**：玩家实测「破坏放着枪的雕像，枪确实会直接丢失」，并追问「还有多少这种问题」；
  随后又报出「放置改过名的标靶抛 NPE、物品还不被消耗」与「改过名的标靶矿车崩渲染线程」
- **范围**：`src/main/java/com/tacz/guns/block/**`、`block/entity/**`（本分支全部 3 个方块实体）、
  `entity/TargetMinecart`、`client/renderer/block/TargetRenderer`、`client/renderer/entity/TargetMinecartRenderer`
- **状态**：五处已修；两轮 26.2 CI（含离线守卫、全量构建）通过；**未实机验证**（见 §6）

---

## 0. TL;DR

| # | 症状 | 根因 | 状态 |
|---|---|---|---|
| 1 | 破坏放着枪的雕像 → 枪消失 | 26.2 删掉了 `Block#onRemove`，移植时只留注释、**没有**任何替代钩子；而 26.2 **不存在**「方块实体移除时自动清理」这种默认行为 | 已修：覆写 `BlockEntity#preRemoveSideEffects` |
| 2 | 带名字的标靶重启后主人信息丢失（头像皮肤没了） | `saveAdditional` 已改写成扁平键 `owner_uuid`/`owner_name`，`loadAdditional` 的前置判据却仍写着上游的 `Owner`，而该键再没有任何写入 ⇒ 判据恒假 | 已修（首轮对齐键名；二次返工整体换成原版 `profile` 键） |
| 3 | 带名字的标靶头像**永远是默认皮肤** | 服务端缺「名字 → 档案」解析（26.2 无 `SkullBlockEntity.updateGameprofile`），客户端又用只看 UUID 的 `DefaultPlayerSkin` | 已修（§5） |
| 4 | 放置改过名的标靶 → **NPE**，标靶放下了但**物品不被消耗** | `new GameProfile(null, 名字)`；authlib 9 的 `GameProfile` 是 record，id/name/properties 全部 `requireNonNull`。NPE 抛在 `BlockItem#place` 中途，消耗语句还没执行 | 已修（§5.4）：改用原版 `ResolvableProfile` |
| 5 | 改过名的标靶矿车 → **崩渲染线程**（每帧） | 同一个 `new GameProfile(null, 名字)`，但它在 `extractRenderState` 里被渲染线程每帧调用 | 已修（§5.4）：`getOwnerProfile()` 返回 `ResolvableProfile` |
| — | 上一轮的「挖另一半掉两个」 | 手工 `popResource` 与战利品表叠加 | 已修（见 `MULTIBLOCK_DOUBLE_DROP_26_2_2026_10_26.md`） |

案 1–3 的共同点是：**编译通过、运行不报错、数据/行为直接没了**。26.2 的迁移里最贵的
不是编译错误，而是这种静默失效。案 3 的证据链另有一个副产品：**26.2 的档案 API 被彻底换过**
（`GameProfileCache` 类已删除），本文件 §5.2 记录了逐条探针结果，供后续同类移植直接查表。

案 4、5 则是相反的一类：**必现崩溃**，而且根因是同一个「手搓 `GameProfile`」的坏习惯。
它们的价值在于给出了这类坏习惯的**编译器层面**的正确替代物：原版
`ResolvableProfile`（允许「只有名字」的档案）+ 客户端 `PlayerSkinRenderCache`（异步解析皮肤、
失败回落默认皮肤）。修完之后，服务端连一次会话服务查询都不需要 —— 方案比 hotfix4 那版
更短、更可靠，也顺带删掉了 hotfix4 引入的一整套后台线程/重试标志。

---

## 1. 案一：雕像上的枪

### 1.1 证据

- `StatueBlock.java` 原第 160 行只有两条注释：
  `// 26.2: onRemove 已移除，方块实体移除时自动清理` / `// 如需掉落物品，在 StatueBlockEntity.setRemoved() 中处理`；
- 全仓 `grep "setRemoved\|preRemoveSideEffects\|onRemove"` 在方块层**零命中** —— 也就是说
  注释里点名的两个方法**都不存在**；
- `StatueBlock` 的类注释第 199 行同样写着「雕像的物品掉落仍由
  `StatueBlockEntity#setRemoved()` 负责」。三处注释互相印证一个并不存在的东西；
- 玩家实测：破坏雕像，枪消失。

### 1.2 为什么「自动清理」是错的

1.21.x 的 `Block#onRemove` 会在方块被替换时由 `Level` 调用；26.2 把这个方法删了，
而**替代品不是「自动」**：原版所有带内容物的方块（箱子、木桶、潜影盒、酿造台、漏斗……）
都是在 `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` 里把内容物倒出来的。
不覆写它，内容物就随方块实体一起被丢弃 —— 这正是雕像发生的事。

`preRemoveSideEffects` 的调用点覆盖：玩家挖掘（`playerDestroy` → `Level#removeBlock`）、
爆炸（`ServerExplosion`）、活塞顶掉、`/setblock` 覆盖、水流/方块更新间接移除。

### 1.3 修法

```java
@Override
public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    super.preRemoveSideEffects(pos, state);
    if (level != null && !level.isClientSide()) {
        this.dropItem(state);   // 传入 state，不要用 getBlockState()
    }
}
```

三个要点：

1. **必须只在服务端掉**。客户端收到方块更新时也会走方块实体的移除流程，不过滤就是幽灵掉落物。
2. **不能用 `getBlockState()`**取 `FACING`。移除时方块实体的缓存状态可能已经是空气，
   `air.getValue(FACING)` 会抛 `IllegalArgumentException: Cannot get property ... from air`。
   改成由调用方传入状态，并对没有该属性的状态回退 `Direction.NORTH`。
3. **不要在方块战利品表里再写一份**。原版容器之所以能用这条路径，正是因为它们的掉落
   完全不在 loot table 里；两边都写就是双份（上一轮工作台踩的就是这个）。

---

## 2. 案二：标靶主人

### 2.1 证据

`TargetBlockEntity`：

```java
// saveAdditional（修前）
output.putString("owner_uuid", ...);   // 写的是这两个
output.putString("owner_name", ...);

// loadAdditional（修前）
if (!input.getStringOr(OWNER_TAG /* "Owner" */, "").isEmpty()) {   // 判的是"Owner"
    this.owner = new GameProfile(UUID.fromString(input.getStringOr("owner_uuid","")), ...);
}
```

`Owner` 来自上游 1.20.1：那边用 `NbtUtils.writeGameProfile(tag, owner)` 写成**复合标签**，
26.2 的 `ValueOutput` 改写时拆成了扁平键，但**判据没跟着改**。于是判据恒为假，
读取整段被跳过。症状：带名字放置的标靶（`/give` 给一个改名的 `tacz:target`）重启后
头像皮肤变成默认皮肤 / 不显示，`CustomName` 反而正常（它用的是自己的判据）。

### 2.2 修法

- 判据改成实际写入的 `owner_uuid` / `owner_name`；
- **名字-only 的主人也要还原**：`TargetBlock#setPlacedBy` 建的是
  `new GameProfile(null, 名字)`（没有 UUID），旧逻辑若只按 UUID 判空仍会丢；
- 保留旧 `Owner` 键的读取兼容（本分支此前的存档、以及从上游挪过来的存档），并用
  `tryGetString` 兜住「旧存档里 `Owner` 是复合标签、`getStringOr` 类型不匹配会抛异常」；
- UUID 非法不再 `UUID.fromString` 抛异常崩方块实体，改为 `tryParseUuid` 返回 `null`。

> **二次返工（同一版本内）**：上面这套「扁平键 + 服务端补全」已被整体替换 —— 存档改回
> **原版头颅同款**的 `profile` 键 + `ResolvableProfile.CODEC`，服务端不再解析档案。
> 扁平的 `owner_uuid`/`owner_name` 与上游的 `Owner` 只保留**读取**兼容，下次保存即迁移。
> 理由见 §5.3/§5.4：手搓档案的代价是两处必现 NPE + 一套没必要维护的异步补全。

---

## 3. 这次是怎么找的（可复用）

只靠「顺手看一眼」找不全，所以按**失效模式**各跑了一遍静态扫描，全部在
`src/main/java` 全量上执行：

| 扫描 | 方法 | 结果 |
|---|---|---|
| A. 存档键不对称 | 解析每个类里的 `putX/store`（写）与 `getXOr/read`（读）键，做差集 | 只报过 `TargetBlockEntity`（即案二） |
| B. 前置判据指向不存在的键 | 找 `getStringOr("K","").isEmpty()` 形态的判据，检查 `K` 是否被同一个类写入 | 只有案二 |
| C. 常量间接 | 上面两条一开始全是「0 命中」——因为键名都藏在 `private static final String` 里；先做常量内联才照出案二 | — |
| D. 名称像钩子却没 `@Override` 的方法 | 对 `use/useItemOn/onRemove/updateShape/saveAdditional/...` 等 40 余个原版钩子名，检查是否有同名方法缺 `@Override`（＝静默不覆写） | 8 处命中全是自有 `tick()` helper，无问题 |
| E. 注释声称「自动处理 / 由 X 负责」 | grep `自动清/自动掉落/由…负责/无需处理` | 只有雕像那三处（案一） |
| F. 移除了内容物却不处理 | 找出所有持有 `ItemStack` 字段的 `BlockEntity`，检查是否有 `preRemoveSideEffects`/`setRemoved` | 只有 `StatueBlockEntity`（案一） |
| G. `ItemStack.CODEC`（非 OPTIONAL）误用 | 全量 grep | `CraftingHelper`/`InvWrapper`/`RangedWrapper` 三处，前两处有 `isEmpty()` 守卫或只在配方解析用，**无问题** |

A/B/D/F 已固化成脚本（见 §4），其余是本次一次性人工/脚本检查。

---

## 4. 新增的守门脚本

```bash
python3 scripts/verify_block_entity_persistence.py --strict   # 本文件的两个案
python3 scripts/verify_block_drops.py --strict                # 上一轮的双掉落
```

`verify_block_entity_persistence.py` 检查六件事：

1. `loadAdditional` 里当前置判据的键，必须由同一个类的 `saveAdditional` 写入；
2. 存档键读写对称（只写不读／只读不写都报）—— 读取按**整个类**统计（二次返工起），
   因为取值常被抽进 `readXxx()` 辅助方法；
3. 持有 `ItemStack` 的方块实体必须有 `preRemoveSideEffects` 或 `setRemoved`，
   确实不需要掉的用 `// tacz:no-drop-on-remove` 显式豁免；
4. 禁止 `onRemove` 覆写（26.2 已删该 API）；
5. 禁止 `new GameProfile(null, ...)`（案 4/5 的根因模板）——需要「只有名字的主人」时
   用 `ResolvableProfile.createUnresolved(String)`；
6. 禁止在主源码里直接使用 `SkinManager`（皮肤一律走 `PlayerSkinRenderCache`）。

第 5、6 条是二次返工时加的：它们抓的不是「数据丢了」，而是**同一段错误写法换个地方再来一次**
（比如哪天又有人给「旗帜上的图案」手搓一个 `GameProfile`）。两条规则在比对前会先**剥掉注释**，
避免把 Javadoc 里引用的反面示例当成真实调用。

**已用「把两个文件回退到修复前」验证**：脚本报出 3 条错（雕像缺钩子、标靶判据键、
标靶只读不写），恢复修复后 rc=0。当前 3 个方块实体全部通过。

**已挂进 CI**（2026-10-27）：两步 `--strict` 直接写在 `.github/workflows/build.yml` 的
静态校验段（`Block drops wiring` / `Block entity persistence`），`docs/ci/build.yml` 为逐字镜像；
`docs/ci/README.md` 的状态表与「凭据权限」段已同步更新。

---

## 5. 案三：标靶主人档案 / 头像皮肤链路（两轮返工才收口）

### 5.1 证据链（静默失效）

1. 上游在 `setOwner` 里调 `SkullBlockEntity.updateGameprofile`：向会话服务异步换回带
   `textures` 属性的完整档案，客户端据此渲染真皮肤；
2. 26.2 无该 API，移植只剩 `refresh()`（代码里就这么写着）；
3. 渲染侧 `TargetRenderer` 用的是 `DefaultPlayerSkin.get(profile)` —— 它**只按 UUID 挑默认皮肤**，
   从不查会话服务。而标靶主人的档案恰恰是「只有名字、没有 UUID」的
   （`TargetBlock#setPlacedBy` 建的就是 `new GameProfile(null, 名字)`）。

结论：名字-only 档案 + 只看 UUID 的渲染 ⇒ 头像恒为默认皮肤，且**全程无日志**。
这与前两案同属「静默失效」，玩家只会看到「皮肤不对」。

### 5.2 26.2 的档案/皮肤 API（探针逐条确认，两轮）

沙箱里没有可直接读的源码，所以两轮都是**只读探针**：先在临时类里故意写错方法名，让编译器
把真实签名打出来（第一轮）；后来改用一次性 workflow 在 CI 上 `grep` 类名 + `javap` 反汇编，
把结果以文件形式写回分支（第二轮，`build-reports/api-probe.txt`，用完已删）。结论按「有用 / 不存在」分组：

| 写法（第一轮：服务端补全方案） | 结果 |
|---|---|
| `MinecraftServer#getProfileCache()` / `#profileCache()` / `#getSessionService()` | ❌ 不存在 |
| `net.minecraft.server.players.GameProfileCache` | ❌ 整个类已删除 |
| `MinecraftServer#services()` → `Services` | ✅ |
| `Services#nameToIdCache()` → `UserNameToIdResolver` | ✅ |
| `Services#profileResolver()` / `#profileRepository()` | ✅（`ProfileResolver#findProfileByName` ❌；`GameProfileRepository#findProfileByName(String)` ✅ 返回 `Optional<NameAndId>`） |
| `UserNameToIdResolver#get(String)` | ✅ `Optional<NameAndId>`，**阻塞** |
| `MinecraftSessionService#fetchProfile(UUID, boolean)` | ✅ `ProfileResult` |
| `GameProfile#getName()` / `#getProperties()` | ❌ 26.2 是 record 风格：`name()` / `id()` / `properties()` |
| `UUIDUtil.getOrCreatePlayerUUID(GameProfile)` | ❌ 不存在 |
| `Util#backgroundExecutor()` | ✅ |

| 写法（第二轮：原版档案 + 皮肤缓存方案，**最终采用**） | 结果 |
|---|---|
| `net.minecraft.world.item.component.ResolvableProfile` | ✅ 抽象类：`CODEC` / `STREAM_CODEC`、`createResolved(GameProfile)`、`createUnresolved(String)`、`createUnresolved(UUID)`、`partialProfile()`、`name()` → `Optional<String>`、`resolveProfile(ProfileResolver)` |
| `ResolvableProfile#gameProfile()` / `#id()` | ❌ 不存在（别按 1.21.x 的印象写） |
| `net.minecraft.core.component.ProfileContents` | ❌ 整个类不存在 |
| `net.minecraft.client.renderer.PlayerSkinRenderCache` | ✅ **注意包名不是 `client.resources`**：`getOrDefault(ResolvableProfile)` → `RenderInfo`、`createLookup(...)`、`lookup(...)`、`DEFAULT_PLAYER_SKIN_RENDER_TYPE` |
| `PlayerSkinRenderCache$RenderInfo#renderType()` / `#playerSkin()` / `#gameProfile()` | ✅ |
| `BlockEntityRendererProvider.Context#playerSkinRenderCache()` | ✅ 方块实体渲染器直接拿得到 |
| `EntityRendererProvider.Context#getPlayerSkinRenderCache()` | ✅ 实体渲染器直接拿得到 |
| `Minecraft#getPlayerSkinRenderCache()` | ❌ 不存在（只能从上面两个 Context 拿） |
| `SkinManager#createLookup(GameProfile, boolean)` | ✅ 返回 `Supplier<PlayerSkin>`（能编译，但会在渲染线程上阻塞并抛异常，见 §5.3） |
| `ValueOutput#store(String, Codec, T)` / `#storeNullable(String, Codec, T)` | ✅ 26.2 的 `Value*` 支持整段 CODEC 读写 |
| `ValueInput#read(String, Codec)` | ✅ `Optional<T>` |
| `SkullBlockEntity`（原版头颅） | ✅ 用 `ResolvableProfile.CODEC` + `"profile"` 键存档；渲染走 `getOrDefault(profile).renderType()` |

> 探针方法本身值得留档：**`javap` + CI 回写文件**比「改错方法名等编译器报错」快一个数量级
> （一次 3 分钟，能一次确认十几个签名），而且不污染源码。前提是能写 `.github/workflows/`
> ——本仓 2026-10-27 起已授权。

### 5.3 第一轮修法（hotfix4 首轮）：能跑，但把问题搬进了服务端

首轮的做法是：服务端把「只有名字」的档案补全成「名字 + UUID」，客户端换成
`SkinManager#createLookup`。它确实让头像有了真皮肤，但也带来两个后果：

- **多了一整套需要维护的异步逻辑**：`Util.backgroundExecutor()` + `server.execute(...)` +
  `profileResolveAttempted` 一次性标志 + 两种失败分支的 WARN。每增加一个「主人的来源」
  （放置、矿车、指令、老存档迁移）就得再想一次「什么时候补全、补全失败怎么办」。
- **没有解决真正的病根**：病根是「我们自己手搓 `GameProfile`」。而 `GameProfile` 在 authlib 9
  里已经是 record：

  ```java
  public GameProfile(UUID id, String name, PropertyMap properties) {
      Objects.requireNonNull(id,   "Profile ID must not be null");
      Objects.requireNonNull(name, "Profile name must not be null");
      Objects.requireNonNull(properties, "Profile properties must not be null");
      ...
  }
  ```

  也就是说 `new GameProfile(null, 名字)` 是**必抛 NPE**，不是「偶尔」。上一轮只是没碰到
  那两个调用点。

### 5.4 第二轮修法（同一版本内二次返工）：换成原版机制，两个崩溃与静默失效一起消失

#### 5.4.1 案 4：放置改过名的标靶 → NPE + 物品不被消耗

`TargetBlock#setPlacedBy` 里：

```java
GameProfile gameprofile = new GameProfile(null, stack.getHoverName().getString()); // 必抛 NPE
e.setOwner(gameprofile);
e.setCustomName(stack.getHoverName());
e.refresh();
```

`setPlacedBy` 是在 `BlockItem#place` 的**中途**被调用的：方块已经 `setBlock` 完，
而「消耗物品」的语句在它之后。于是 NPE 一抛，结果是：

- 标靶**放下了**（上面的 `world.setBlock(above, ...)` 也执行了）；
- 物品**没被消耗**；
- 异常被 `useItemOn`/`place` 的调用链吞掉，玩家端**没有任何提示**。

这解释了玩家看到的「诡异现象」：方块在、物品也在，还能再放一个。

**修法**：不再构造 `GameProfile`，直接

```java
if (!name.isEmpty()) {
    e.setOwner(ResolvableProfile.createUnresolved(name));   // 原版允许「只有名字」
}
```

#### 5.4.2 案 5：改过名的标靶矿车 → 崩渲染线程

`TargetMinecart#getGameProfile()` 是同一个写法，但它的调用点在
`TargetMinecartRenderer#extractRenderState` —— **客户端每帧、渲染线程上**。所以不是
「偶尔崩」，而是一靠近这辆矿车就崩，且直接崩在渲染线程上（表现是游戏卡死/崩溃）。

**修法**：`getGameProfile()` → `getOwnerProfile()`，返回 `ResolvableProfile`；
按自定义名缓存派生结果（名字变化即失效），每帧调用不再分配对象，也不再可能抛异常。

#### 5.4.3 方块实体：存档格式回归原版

`TargetBlockEntity` 现在只存一个 `ResolvableProfile`，键与 CODEC 都照抄原版头颅：

```java
output.store("profile", ResolvableProfile.CODEC, this.owner);          // save
this.owner = input.read("profile", ResolvableProfile.CODEC).orElse(null); // load
```

于是可以**删掉** hotfix4 的全套服务端补全（`resolveOwnerProfile`、
`profileResolveAttempted`、`setLevel` 钩子、后台线程、`UserNameToIdResolver` 调用）。
UUID 补全改由客户端缓存在需要时异步完成。

#### 5.4.4 客户端：PlayerSkinRenderCache（原版头颅同款）

```java
// 方块（BlockEntityRendererProvider.Context）
this.skinRenderCache = context.playerSkinRenderCache();
// 矿车（EntityRendererProvider.Context）
this.skinRenderCache = ctx.getPlayerSkinRenderCache();

// 渲染时
RenderType type = skinRenderCache.getOrDefault(profile).renderType();
```

它天然是异步的：命中缓存直接返回；未命中先返回**默认皮肤**并在后台按名字/UUID 解析，
解析成功后下一帧自动换成真皮肤。因此：

- 渲染线程上**没有**阻塞查询（对比 hotfix4 的 `createLookup(...).get()`）；
- 解析不到（拼错名字 / 离线服 / 会话服务拒绝）**自动**回落默认皮肤，不需要我们写兜底逻辑；
- 真出了意外（缓存内部抛异常）时退回 `DEFAULT_PLAYER_SKIN_RENDER_TYPE`，
  并在客户端日志留一条 WARN（只报一次，既不静默也不刷屏）。

### 5.5 存档兼容与迁移

| 存档里的旧写法 | 处理 |
|---|---|
| `Owner`（上游 1.20.1 的 `GameProfile` 复合标签 `{Name, Id}`） | 只读兼容，下次保存写成 `profile` |
| `owner_uuid` / `owner_name`（本分支 hotfix4 的扁平键） | 只读兼容，下次保存写成 `profile` |
| `CustomName`（hotfix4 把组件用 JsonOps 编码成**字符串**再存） | 识别 `{"` 开头并重新解析，避免把 JSON 原文当名字显示；保存时改用 `ComponentSerialization.CODEC` |

三者都是「读到就迁移」，不需要玩家做任何操作；读不到/解析失败一律按「没有主人 / 没有自定义名」
处理，不抛异常。名字与 UUID 都在时优先用 `ResolvableProfile.CODEC` 解析成 `Partial`
（JSON `{name, id}` 形态），失败再退化成只用名字、再退化成只用 UUID。

### 5.6 仍然存在的边界

- **离线服**（`online-mode=false`）解析不出正版皮肤，头像就是默认皮肤。这是原版行为
  （原版头颅在离线服同样如此），不是本移植的缺陷；区别是现在**不再有我们自己的**
  服务端 WARN 去解释它 —— 因为不再有我们自己的解析步骤。
- 玩家改名（改名卡）后，老标靶仍按**放置时写下的名字**解析；这与原版头颅一致。
- 解析结果只在本方块实体/实体上生效，不写回物品；挖下来再放会重新解析（客户端有缓存）。

---

## 6. 验证边界

- ✅ `scripts/verify_block_entity_persistence.py --strict`、`scripts/verify_block_drops.py --strict`：通过
- ✅ 26.2 CI：二次返工后 `compile-check` 与 `build` 均 success（`build.yml` 的两步守卫、
  版本一致性、全量构建全绿；一次过，没有出现「探针式反复」）。hotfix4 那轮曾用 `:compileJava`
  反证 `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` 在 26.2 存在且签名一致
- ❌ **未实机**。发布前需验：
  1. 生存/创造破坏放着枪的雕像 → 枪掉在雕像前上方一格，且**只掉一次**；
  2. 爆炸摧毁雕像 → 枪仍在（`survives_explosion` 只影响雕像本体）；
  3. 活塞顶掉 / `/setblock` 覆盖雕像 → 枪仍掉；
  4. 用改名的 `tacz:target` 放置标靶（`/give @s tacz:target[minecraft:custom_name='"Notch"']`）：
     **放置瞬间不得崩溃、物品必须 -1**（案 4 的验收点）；头像应为该玩家皮肤，退出重进存档后仍在；
  5. 同上但名字改成不存在的玩家名 → 头像为默认皮肤（不报错、不崩）；
  6. 生成「改名标靶矿车」（`/summon tacz:target_minecart ~ ~ ~ {CustomName:'"Notch"'}`）并靠近：
     **不得崩溃/卡死**（案 5 的验收点），头像应为该玩家皮肤；
  7. 老存档：分别放一个 hotfix4 存档（`owner_uuid`/`owner_name` 扁平键）与一个上游 1.20.1 存档
     （`Owner` 复合标签），加载后主人与自定义名都在；再保存一次后 NBT 里应只剩 `profile` 键；
  8. 上一轮的双掉落矩阵（见另一份调查记录 §5.1）仍要跑。

## 7. 其他分支

`26.1.2` / `1.21.11` / `26.3` / `26.4` 四条线的 `StatueBlockEntity` 与
`TargetBlockEntity` 与 26.2 修前**逐字相同**（本轮已用 `git show` 逐分支核对），
即这两个 bug 同样存在于那四条线。本分支按纪律不跨分支复制补丁：各线的
`ValueInput/ValueOutput` 细节一致，但需各自编译与实测后再落码。

案 4/5 的**错误写法**同样存在于那四条线（`git grep 'new GameProfile('`：
`26.3`/`26.4` 的 `TargetBlock.java:185`、`TargetMinecart.java:138`；`26.1.2`/`1.21.11` 的
`TargetBlock.java:192`、`TargetMinecart.java:138`）。但**是否必现崩溃取决于各自 authlib 版本**
（`GameProfile` 变成「全字段 `requireNonNull` 的 record」是较新 authlib 的事），
`26.1.2`/`1.21.11` 很可能只是「静默拿到 name-only 档案」。所以：**先在那条线上编译+实测，
再决定移植方式**，不要直接抄本分支的 `ResolvableProfile` 代码（旧线可能还没有这个类）。
