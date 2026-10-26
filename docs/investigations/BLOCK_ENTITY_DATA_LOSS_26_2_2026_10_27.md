# 26.2 方块实体「代码在、行为不在」两连案：雕像掉枪 与 标靶主人

- **日期**：2026-10-27
- **触发**：玩家实测「破坏放着枪的雕像，枪确实会直接丢失」，并追问「还有多少这种问题」
- **范围**：`src/main/java/com/tacz/guns/block/**`、`block/entity/**`（本分支全部 3 个方块实体）
- **状态**：两处已修；新增离线审计脚本；**未实机验证**（见 §6）

---

## 0. TL;DR

| # | 症状 | 根因 | 状态 |
|---|---|---|---|
| 1 | 破坏放着枪的雕像 → 枪消失 | 26.2 删掉了 `Block#onRemove`，移植时只留注释、**没有**任何替代钩子；而 26.2 **不存在**「方块实体移除时自动清理」这种默认行为 | 已修：覆写 `BlockEntity#preRemoveSideEffects` |
| 2 | 带名字的标靶重启后主人信息丢失（头像皮肤没了） | `saveAdditional` 已改写成扁平键 `owner_uuid`/`owner_name`，`loadAdditional` 的前置判据却仍写着上游的 `Owner`，而该键再没有任何写入 ⇒ 判据恒假 | 已修：判据与写入键对齐 + 旧键兼容 + 名字-only 主人还原 |
| — | 上一轮的「挖另一半掉两个」 | 手工 `popResource` 与战利品表叠加 | 已修（见 `MULTIBLOCK_DOUBLE_DROP_26_2_2026_10_26.md`） |

这三件事的共同点：**编译通过、运行不报错、数据/行为直接没了**。26.2 的迁移里最贵的
不是编译错误，而是这种静默失效。

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

`verify_block_entity_persistence.py` 检查四件事：

1. `loadAdditional` 里当前置判据的键，必须由同一个类的 `saveAdditional` 写入；
2. 存档键读写对称（只写不读／只读不写都报）；
3. 持有 `ItemStack` 的方块实体必须有 `preRemoveSideEffects` 或 `setRemoved`，
   确实不需要掉的用 `// tacz:no-drop-on-remove` 显式豁免；
4. 禁止 `onRemove` 覆写（26.2 已删该 API）。

**已用「把两个文件回退到修复前」验证**：脚本报出 3 条错（雕像缺钩子、标靶判据键、
标靶只读不写），恢复修复后 rc=0。当前 3 个方块实体全部通过。

---

## 5. 顺带发现（未改，需实机或反编译确认）

**标靶头像的「名字 → 皮肤」解析可能已经失效。** 上游在
`setOwner` 里调 `SkullBlockEntity.updateGameprofile`（异步向会话服务换 UUID/skin），
本分支 26.2 移植时该 API 已不存在，代码改成只调 `refresh()`：

```java
public void setOwner(@Nullable GameProfile owner) {
    this.owner = owner;
    // 26.2: SkullBlockEntity.updateGameprofile removed. Just refresh directly.
    this.refresh();
}
```

而渲染侧是 `DefaultPlayerSkin.get(owner).body().texturePath()`。**仅从源码无法判定**
一个没有 UUID 的 `GameProfile`（名字-only）在 26.2 的 `DefaultPlayerSkin` 里会拿到
真实皮肤还是默认皮肤 —— 未反编译、未实测，因此**不声称它是 bug**。若实测发现
「带名字的标靶只有默认皮肤」，正确方向是接 26.2 的 profile 解析
（`ResolvableProfile` / `ProfileResolver` 一族）而不是把旧 API 硬塞回来。

---

## 6. 验证边界

- ✅ `scripts/verify_block_entity_persistence.py --strict`、`scripts/verify_block_drops.py --strict`：通过
- ✅ 26.2 CI `:compileJava`：`BUILD SUCCESSFUL`（提交 `25c0adc` 的 compile-check / build 均 success；
  这也同时证明 `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` 在 26.2 确实存在且签名一致）
- ❌ **未实机**。发布前需验：
  1. 生存/创造破坏放着枪的雕像 → 枪掉在雕像前上方一格，且**只掉一次**；
  2. 爆炸摧毁雕像 → 枪仍在（`survives_explosion` 只影响雕像本体）；
  3. 活塞顶掉 / `/setblock` 覆盖雕像 → 枪仍掉；
  4. 用改名的 `tacz:target` 放置标靶 → 退出重进存档，标靶名字与头像仍在；
  5. 上一轮的双掉落矩阵（见另一份调查记录 §5.1）仍要跑。

## 7. 其他分支

`26.1.2` / `1.21.11` / `26.3` / `26.4` 四条线的 `StatueBlockEntity` 与
`TargetBlockEntity` 与 26.2 修前**逐字相同**（本轮已用 `git show` 逐分支核对），
即这两个 bug 同样存在于那四条线。本分支按纪律不跨分支复制补丁：各线的
`ValueInput/ValueOutput` 细节一致，但需各自编译与实测后再落码。
