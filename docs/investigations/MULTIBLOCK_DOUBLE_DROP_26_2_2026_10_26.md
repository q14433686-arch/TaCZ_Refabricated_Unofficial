# 26.2 多方块工作台「挖另一半掉两个」调查与修复

- **日期**：2026-10-26
- **适用分支**：`26.2(main)` / 本线所有派生分支
- **症状**：生存模式下放置带多个碰撞箱（格子）的方块——即 B 工作台
  `tacz:gun_smith_table`、`tacz:workbench_b`（2×1×1，foot + head）与 C 工作台
  `tacz:workbench_c`（1×2×1，lower + upper）——然后去挖**另一半**，一次破坏会爆出
  **两个**工作台物品。26.3 线无此问题。
- **状态**：代码已改，**未编译、未实机验证**（沙箱内无 JDK/游戏 jar，见 §5）。

---

## 1. 结论（一句话）

`AbstractGunSmithTableBlock#playerWillDestroy` 在对「非根半边」破坏时**手工 `popResource`
了一份克隆物品**，而原版在另一半上本来就会走一次战利品表 —— 两条掉落叠加成两份。
删除这段手工掉落即可，掉落全部交回战利品表。

---

## 2. 26.2 上的掉落到底是怎么产生的

26.2 的 `Block#updateOrDestroy`（邻居 `updateShape` 返回空气时被调用）实际执行的是

```
level.destroyBlock(pos, /* dropResources = */ (flags & UPDATE_SUPPRESS_DROPS) == 0, null, …)
```

玩家正常挖掘时下发的标志位是 `Block.UPDATE_ALL`（3），**没有**
`UPDATE_SUPPRESS_DROPS`（32），因此「被连带清掉的另一半」会照常跑它自己的战利品表。
原版高花、床、门都是靠这条路径做到「挖哪一半都只掉一份」。

我们的两份战利品表 `data/tacz/loot_table/blocks/workbench_b.json` /
`workbench_c.json` 用 `block_state_property` 把产出锁在**根**半边
（`part=foot` / `half=lower`），并用 `copy_custom_data` 从根的方块实体拷 `BlockId`。
于是 26.2 原本的路径是：

| 挖哪一半 | 被挖的半边的战利品表 | 另一半（经 `updateShape → AIR → destroyBlock`） | 原本应有 | 修复前实际 |
|---|---|---|---|---|
| 根（foot / lower） | 产出 1 份（带 `BlockId`） | 非根，条件不满足 → 0 | **1** | 1 |
| 非根（head / upper） | 条件不满足 → 0 | 根，产出 1 份（此时根的方块实体还在，`BlockId` 拷得到） | **1** | **2**（多出的那份来自手工 `popResource`） |

关键点：非根半边被打掉后，**根本不需要**在 `playerWillDestroy` 里补掉落 —— 根半边的
战利品表掉落是由原版邻接更新触发的，且能正常拿到 `BlockId`。

---

## 3. 26.3 为什么没事（同源修复）

26.3 线做战利品表 schema 迁移时顺手删掉了同一段手工掉落：

- commit `021b546`（26.3）`loot(26.3): migrate block loot tables to the 26.3 schema; drop duplicate manual half-drop`
- 其移植指南 §2.8 明写：「附带修了一个**与版本无关的老 bug**：…额外 `popResource`
  一个克隆物品，与根半边的战利品表叠加 → 挖上半/头部爆出两个工作台。**你们 26.2 线
  大概率也有这个双掉落。**」

本次即按该结论在 26.2 线对齐。

---

## 4. 本次改动

### 4.1 `AbstractGunSmithTableBlock`：删除手工半边掉落

删掉 `playerWillDestroy` 里对非根半边的 `popResource(getCloneItemStack(...))`，
方法本身一并移除（`B`/`C` 的 `super.playerWillDestroy(...)` 于是直接落到
`BlockBehaviour`），并在类注释里写清掉落语义与「不要在这里补 `popResource`」。

### 4.2 `GunSmithTableBlockB`：创造模式的半边抑制判据反了（顺带修正）

原代码注释写的是「抑制创造模式下摧毁 **head** 方块时 **foot** 的掉落」，
但判据写的是 `bedPart == BedPart.FOOT`（照抄原版 `BedBlock` 的写法，
而原版床的 loot 条件在 `head`，我们的在 `foot`，方向正好相反）。后果：

- 创造模式挖 **foot**（本来就是产出半边，创造模式不触发 `playerDestroy`）：无掉落 —— 多此一举；
- 创造模式挖 **head**：foot 经 `updateShape → AIR → destroyBlock(drop=true)` **掉出 1 个工作台** ——
  这才是需要抑制的方向，却没抑制。

改为判 `bedPart == BedPart.HEAD`（并把被抑制的对侧改成 FOOT），与 C 工作台的写法
（`half == UPPER` 抑制 `LOWER`，因 C 的 loot 条件在 `lower`）保持一致。
`GunSmithTableBlockC` 原判据方向本来是对的，未改。

### 4.3 为什么**不**照抄 26.3 的战利品表 JSON

26.3 把战利品表 JSON 的键名整体改了（`conditions → condition`、`functions → modifier`、
`function/condition → type`、`block_state_property → match_block`，并建议补
`random_sequence`）。`RecordCodecBuilder` **忽略未知键**，所以把 26.3 格式的文件放进
26.2 会静默失效（产出条件与 `copy_custom_data` 全丢 → 掉落变紫黑无名物品）。

本分支的 6 份表仍是 **26.2 认的旧写法**（`conditions`/`functions`/`block_state_property`），
本次**不动**。若日后确认 26.2 也已改名，则整个 schema 迁移要单独做（26.3 commit
`021b546` 里那套 JSON 可作蓝本，另需给枪包的 `tacz_loot_injectors` 加运行期迁移）。

---

## 5. 验证边界

**已通过**

- 26.2 CI `:compileJava`：`BUILD SUCCESSFUL`（提交 `db62d25` 的
  `compile-check` / `build` 两条都 success，日志见 `build-reports/compile-java.log`）。
- 离线守卫：`python3 scripts/verify_block_drops.py --strict` 通过（并已用「故意注入一条
  `popResource`」验证过它确实会报错）。

**未做**

- **未实机验证**：沙箱内没有可运行的游戏环境，掉落数量、`BlockId`、创造模式行为都需实机确认；
- §2 中 `updateOrDestroy` 的语义依据是 26.3 线已实测的结论（其注释与调查记录
  `SCOPE_26_3_VS_26_2_DELTA_ANALYSIS_2026_09_20.md` §十四）与本分支同款代码路径，
  不是本次独立反编译所得。

### 5.1 实机验收矩阵（发布前必须跑）

| 场景 | 期望 |
|---|---|
| 生存，放 `workbench_c`，挖 lower | 掉 1 个 `workbench_c` |
| 生存，放 `workbench_c`，挖 upper | 掉 1 个 `workbench_c`（**回归点**） |
| 生存，放 `workbench_b`，挖 foot | 掉 1 个 `workbench_b` |
| 生存，放 `workbench_b`，挖 head | 掉 1 个 `workbench_b`（**回归点**） |
| 生存，放 `gun_smith_table`（同 B 逻辑），挖两半各一次 | 各掉 1 个 |
| 生存，挖枪包工作台（带 `BlockId`）的任一半 | 掉 1 个，且放下后仍是同一把枪包工作台（不是紫黑无名物品） |
| 创造，B/C 各挖两半 | **都不掉**物品实体 |
| 生存，放 `statue` / `target`，挖上下各一次 | 各掉 1 个（这两块本来就没有手工掉落，属对照项） |

### 5.2 若实机仍然掉两个，怎么查

1. **看掉落物外观/名字**：若其中一份贴图紫黑、名字是 `block.tacz.workbench_b`
   （而不是枪包里的索引名），说明 `copy_custom_data` 没生效 → 26.2 的 loot schema
   其实也已改名，需要按 §4.3 做整表迁移，本修复不足以解决；
2. `grep -n "popResource" src/main/java/com/tacz/guns/block -r`：除
   `StatueBlockEntity`（雕像上的枪）外不应再有任何调用；
3. 反编译 `Block#updateOrDestroy` 确认 26.2 是否仍按 `(flags & UPDATE_SUPPRESS_DROPS) == 0`
   传 `dropResources=true`；若这条路径在 26.2 已不产出，则非根半边会变成「掉 0 个」，
   需要另找产出路径（而不是把 `popResource` 加回来）。

---

## 6. 其他分支提示

`26.1.2` / `1.21.11` 两条分支大概率也有同一段手工掉落（该代码来自上游 1.21.1 分支，
且 26.3 的结论是「与版本无关的老 bug」）。本分支按要求**不跨分支复制补丁**：那两条线
的掉落链路、战利品表文件名与 schema 均需各自核对后再落码。
