# API 稳定性 P2 取舍与 26.x 同步执行手册

> **执行状态（2026-08-16）：已被后续 R2 手册取代。** 本文保留 P2 取舍、API 不变量和早期
> P0/P1 差异分析作为历史设计记录；其中“待批准/不得夹带 P2”、仅同步两个提交、无需版本
> bump 等执行指令已经过时。P2-min、Carry On、Ammo Query 与 viewer 刷新桥现已在 1.21.11
> R2 源基线完成。实际移植必须分别使用
> [`PORT_R2_TO_26_1_2.md`](PORT_R2_TO_26_1_2.md) 和
> [`PORT_R2_TO_26_2_MAIN.md`](PORT_R2_TO_26_2_MAIN.md)，不要混用本文旧命令和提交边界。

> 日期：2026-08-16
>
> 分析基线：`1.21.11` = `4a983253b557c4d6c6cd9a7159aaec8e4a2cdbc8`、
> `26.1.2` = `6c409eea0cfe01e070d0ed3c921b63a7a96cb50d`、
> `26.2(main)` = `99b472a6a8e1438f22a29abe8b3804b349cb5dfd`
>
> 待同步提交：`28aa9bb`（弹药源 API）和 `ce9d4b2`（P0/P1 稳定行为钩子）
>
> 关联：Issue #46、PR #48

本文给后续 Agent 两类明确指令：

1. 判断 P2 是否实施以及实施边界；
2. 把已经通过审查的弹药源 API 与 P0/P1 从 `1.21.11` 同步到 `26.1.2` 和
   `26.2(main)`，同时保留各分支原有行为。

本文不是 P2 的实施授权。除非任务明确批准 P2，否则只执行“同步 P0/P1”章节。

---

## 1. 结论

### 1.1 P2 值得做，但只建议做 `P2-min`

P2 不应成为合并弹药源 API/P0/P1 的前置条件。当前最合理的边界是：

- **实施**：把 `ModernKineticGunItem` / `ModernKineticGunScriptAPI` 的重复 Lua 函数解析与
  cycle-task lambda 提为具名 helper；这只是派发胶水具名化，不改变脚本 fallback。
- **文档化**：`defaultTickReload`、`defaultTickHeat`、`LivingEntityAmmoCheck` 和服务端 charge
  校验组；必要的 protected 暴露度已在 `ce9d4b2` 完成，不再扩大可见性。
- **明确拒绝统一**：HUD 计数、工作台方向性过滤、client/common 近战数据读取。
- **有消费者后再审**：其余动画、inspect、draw、fire-select、aim 与近战动作 lambda。
- **不属于行为不变 P2**：给 `AmmoSource` 增加退弹/返还能力、统一客户端/服务端规则、
  改冷却或防作弊策略、修正历史协议字符串。这些必须另开设计任务。

这样可以消除仍有实际稳定性收益的低风险合成入口，同时不为了形式上的“去重”把显示层、
工作台、客户端注册表和服务端 gameplay 错误地耦合到同一 API。

### 1.2 26.x 同步应是两个独立 PR，且不得夹带 P2

- `26.1.2`：本次涉及的 11 个既有 Java 文件与 `1.21.11` 基线**逐字节相同**，两份源代码
  patch 均可直接应用。
- `26.2(main)`：第一份源代码 patch 可直接应用；第二份只有
  `LocalPlayerShoot.java` 一处三方冲突。必须保留 26.2 的线程说明，同时采用重构后的
  `applyClientFireEffects(...)` 调用。
- 两个目标分支的 README、构建文件和发布文档不同；不要完整 cherry-pick 两个提交，
  不要复制 `docs/CHANGELOG_1_21_11.md`。

---

## 2. 不可破坏的不变量

所有 P2 或跨分支同步都必须满足：

1. 不新增依赖，不改公共类名、包名或 mod id。
2. 不改变原调用路径、判断顺序、返回值、短路条件和副作用顺序。
3. 不合并客户端与服务端事务；它们已有意保留不同门槛。
4. 弹药查询必须只读，弹药消耗仍由权威游戏逻辑执行。
5. dummy ammo、creative、无限弹药、`FeedType` 和各 `Bolt` 类型的优先级不变。
6. `LocalPlayerShoot.SHOOT_LOCKED_CONDITION` 必须继续是同一个静态单例对象；
   `isShootLockCondition` 必须继续使用 `==` 判断身份。
7. `GunAnimationConstant.INPUT_BOLT` 必须继续为历史协议值 **`"blot"`**，不得“纠正”为
   `"bolt"`。三个基线分支当前都使用 `"blot"`。
8. 不把 javac 生成的 `lambda$...` 名称重新定义为兼容 API。
9. 26.2 的 `LocalPlayerReload` 调试探针必须保留，并继续仅受 `RECOIL_DEBUG` 门禁控制。
10. 不顺手修改 26.2 已记录但尚未修复的 draw cooldown、跨维度操作或其他审计项。

---

## 3. P2 八组复核与边界

本次按原 API 表面审计的候选收敛为八组。建议状态中的“实施”均指**另开提交/PR并获授权后**，
不是与 P0/P1 同步一起做。

| 组 | 原审计候选 | 结论 | 允许的最小改动 |
|---|---|---|---|
| P2-1 | `ModernKineticGunItem` / `ModernKineticGunScriptAPI` 的 Lua 派发胶水 | **实施** | 提取具名 helper；保留每个调用点自己的 fallback、异常和返回值语义 |
| P2-2 | `ModernKineticGunItem#defaultTickReload` 暴露度 | **实施（仅文档）** | `ce9d4b2` 已从 private 校正为 protected；P2 只补默认 fallback 契约 javadoc |
| P2-3 | `ModernKineticGunItem#defaultTickHeat` 暴露度 | **实施（仅文档）** | `ce9d4b2` 已从 private 校正为 protected；P2 只补无脚本时的时序/副作用 javadoc |
| P2-4 | `LivingEntityAmmoCheck` 半暴露弹药策略 | **实施（仅文档）** | 补类与两方法的语义说明；不迁包、不改 public 签名、不并入 source registry |
| P2-5 | `LivingEntityShoot` charge 校验组 | **实施（仅文档）** | `ce9d4b2` 已提为 protected；补服务端安全边界说明，不再放宽或改算法 |
| P2-6 | `GunHudOverlay` 弹药扫描 | **拒绝** | 保留现有具名私有计数逻辑；不复用 gameplay 的 boolean availability API |
| P2-7 | `GunSmithTableScreen` 物品扫描 | **拒绝** | 保留方向性配方/配件过滤；不塞入 ammo source registry |
| P2-8 | `LocalPlayerMelee` / `LivingEntityMelee` 配件数据读取 | **拒绝** | 保留 Client/Common 两套索引读取；不制造跨侧共享 helper |

### 3.1 P2-min 的实际代码范围

#### P2-1：Lua 派发具名化

允许在 `ModernKineticGunItem` 提取类似
`protected Optional<LuaFunction> resolveScriptFunction(CommonGunIndex, String)` 的 helper，替换
`start_bolt`、`tick_bolt`、`shoot`、`start_reload`、`tick_reload`、`interrupt_reload`、
`tick_heat`、`calcSpread` 与 `modifyProperty` 的重复两步胶水。

允许在 `ModernKineticGunScriptAPI` 提取同构 helper，并把 `safeAsyncTask` 的循环 lambda 薄转发到
类似 `protected boolean runLuaCycleTask(LuaFunction)` 的具名方法。

必须保持：

- `checkFunction` 对 function、nil 和错误类型的现有语义；
- 每个调用点当前使用的 `orElse`、`orElseGet`、`ifPresent` 或 `ifPresentOrElse`；
- Lua 调用参数、调用次数、异常传播和 `modifyProperty` 的 catch/fallback；
- `CycleTaskHelper.addCycleTask` 的 delay、period、cycles 与返回 boolean 的终止语义。

不得把一个统一 fallback 塞进 helper，也不得把 helper 改成事件或新的注册表。

#### P2-2 / P2-3：默认 reload 与 heat

`defaultTickReload(...)` 和 `defaultTickHeat(...)` 已在通过审查的 P1 提交中变为 protected，P2
不再修改可见性或方法体，只补英文 javadoc，明确它们仅在对应 Lua 函数不存在时执行。

- reload：不得移动 state type/countdown 计算、feeding/finishing 边界或弹药副作用；
- heat：不得合并 `tickLocked`/`tickNormal`，不得改变 `System.currentTimeMillis()` 的调用位置。

#### P2-4：`LivingEntityAmmoCheck`

只补英文 javadoc，明确：

- `needCheckAmmo()` 决定是否把“必须有弹药”作为动作门槛；
- `consumesAmmoOrNot()` 决定动作成功后是否实际扣弹；
- creative 与 `GunConfig.CREATIVE_PLAYER_CONSUME_AMMO` 的历史差异是有意行为；
- 类虽然位于内部 shooter 包，但经 `IGunOperator` 被 gameplay 使用，签名不得随意漂移。

不得让该类直接查询 `AmmoSourceRegistry`。source registry 负责“从哪里查询/消耗”，本类负责
“当前实体是否需要查询/消耗”，职责不同。

#### P2-5：服务端 charge 校验

`isChargeProgressReasonable`、`getMaxReasonableChargeProgress`、
`getChargeProgressAfterLastFire`、`getChargeElapsedMillis`、`validateChargeProgress` 已由 P1
变为 protected。P2 只增加安全边界 javadoc：这些方法处理服务端接收的客户端蓄力数据，覆写者
不得放宽 finite、最小阈值、最大可达进度或网络抖动容差。

不要再改 public，不新增“跳过校验”开关，不移动 `GunShootEvent` 前后的校验位置，也不要为了
与客户端共享代码而改服务端时间源。此处也不能退回 private：P1 已经把其作为稳定具名入口
发布，回退可见性会成为 API 破坏。

### 3.2 三组“有意保留”

#### P2-6：HUD

HUD 是**计数**语义：需要累加数量，并处理创造弹药箱显示封顶；它遍历原版客户端
`Inventory`。`AmmoSource.hasAmmo` 是 gameplay 的布尔可用性契约，不能提供 HUD 所需精确总数。
结论：保留 `GunHudOverlay` 现有具名私有方法。若未来要显示第三方库存数量，应另行设计可选的
count/display API，不能通过反复调用 consume 或改变当前 `AmmoSource` 契约实现。

#### P2-7：工作台

工作台过滤包含方向性 `isAmmoOfGun(stack, result)` / `isAmmoOfGun(result, stack)` 与配件匹配，
不是实体 gameplay 弹药查询。结论：保留 `GunSmithTableScreen` 现有具名私有逻辑，不接入
`AmmoSourceRegistry`，也不和 HUD 扫描合并。

#### P2-8：近战

客户端与服务端虽然形状相似，但分别读取 Client 和 Common attachment index。结论：保留
`LocalPlayerMelee#getMeleeData` 与 `LivingEntityMelee#getMeleeData` 两份具名方法；强行共享会
把显示层与服务端数据注册表耦合起来。

### 3.3 额外扫描项不是本轮 P2

`GunAnimationStateContext` 的其他动画查询、`LocalPlayerInspect`、draw/fire-select/aim 及近战
动作内部仍可找到 lambda，但没有已知下游正在绑定，且 P0/P1 已经覆盖 Issue #46、开火、换弹、
拉栓和关键 walk-distance 入口。不要为了“清零 lambda”继续扩大兼容面。

若以后有明确消费者，再按单类单提交处理：只提取本地 protected 方法，不跨类统一 inspect 与
reload，不改 draw scheduler/主线程切换，不合并客户端与服务端 aim/melee。该后续工作不得追补
到本次 P2-min 或 26.x 同步 PR。

### 3.4 P2-min 的 Agent 执行清单

只有任务明确批准 P2 后才执行，并拆成两个可独立审查的提交：

1. `Name Lua script dispatch helpers`：只改 `ModernKineticGunItem.java` 和
   `ModernKineticGunScriptAPI.java`；
2. `Document fallback and server validation hooks`：只改 `ModernKineticGunItem.java`、
   `LivingEntityAmmoCheck.java` 和 `LivingEntityShoot.java` 的 javadoc。

P2-min 不应产生 HUD、工作台、melee、animation、network、resource 或 build 文件 diff。静态检查：

```bash
git diff --check
rg -n 'resolveScriptFunction|runLuaCycleTask' \
  src/main/java/com/tacz/guns/item/ModernKineticGunItem.java \
  src/main/java/com/tacz/guns/item/ModernKineticGunScriptAPI.java
rg -n 'Optional\.ofNullable\(gunIndex\.getScript\(\)\)' \
  src/main/java/com/tacz/guns/item/ModernKineticGunItem.java \
  src/main/java/com/tacz/guns/item/ModernKineticGunScriptAPI.java || true
# 按上文要求恰好生成两个 P2 提交时：
git diff --name-only "$(git rev-parse HEAD~2)"...HEAD
```

最后一条命令的允许列表只有上述四个不同文件；若 P2 实际不是恰好两个提交，应改用开始实施前
记录的完整基线 SHA。

P2 运行回归至少覆盖：脚本函数存在、函数为 nil、字段为错误 Lua 类型、`modifyProperty`
抛异常 fallback、cycle callback 返回 true/false、无脚本 reload、普通/过热冷却、合法/非法
charge progress。P2 先在一个分支完成审查，再以独立 PR 同步到其余维护分支；不得追补进已审查
的 P0/P1 提交，也不得假定 P2 patch 在 26.2 的专属注释上可无冲突应用。

---

## 4. 三分支差异

### 4.1 构建与映射

| 项目 | `1.21.11` | `26.1.2` | `26.2(main)` |
|---|---|---|---|
| Minecraft | 1.21.11 | 26.1.2 | 26.2 |
| Java | 21 | 25 | 25 |
| Fabric API | 0.141.6+1.21.11 | 0.155.2+26.1.2 | 0.155.2+26.2 |
| Loom | `net.fabricmc.fabric-loom-remap` 1.17.19 | `net.fabricmc.fabric-loom` 1.17-SNAPSHOT | 同 26.1.2 |
| 映射 | 混淆版本；官方 Mojang mappings、legacy Mixin AP、refmap | 26.1+ 非混淆；无 mappings 依赖 | 同 26.1.2 |
| mod 依赖配置 | `modImplementation` 等，需要 remap | `implementation` 等 | 同 26.1.2 |

本次新增 API 和钩子**不增加 mixin、access widener、网络 payload 或资源协议**；涉及的
`LivingEntity`、`Entity`、`ItemStack`、Fabric `Event/EventFactory` 与项目内部类型，在三个
基线中的源码签名相同。因此映射差异影响构建工具链和下游 Mixin 写法，但不要求改本次 Java
实现的包名、方法名或参数。

对下游的含义：优先调用/注入本次具名 API，不要把 1.21.11 的 intermediary 名、26.x 的
`lambda$...` 名或某一分支 refmap 目标复制到另一分支。

### 4.2 本次涉及 Java 文件的差异

`1.21.11` 与 `26.1.2`：两个提交涉及的以下 11 个既有 Java 文件全部相同：

- `AbstractGunItem.java`；
- `GunAnimationStateContext.java`；
- `InventoryEvent.java`；
- `LocalPlayerBolt.java`、`LocalPlayerReload.java`、`LocalPlayerShoot.java`；
- `LivingEntityBolt.java`、`LivingEntityReload.java`、`LivingEntityShoot.java`；
- `ModernKineticGunItem.java`、`ModernKineticGunScriptAPI.java`。

`26.2(main)` 相对 `1.21.11`，本次范围内仅六个文件不同：

| 文件 | 差异性质 | 同步要求 |
|---|---|---|
| `AbstractGunItem.java` | `dropAllAmmo` 边界说明注释 | 保留 26.2 注释 |
| `LocalPlayerReload.java` | 9 行 `RECOIL_DEBUG` 换弹探针，属于可执行日志代码 | 必须保留在重命名后的 reload 动画方法内 |
| `LocalPlayerShoot.java` | burst scheduler 主线程跳转说明改写 | 保留 26.2 注释；采用新的具名调用 |
| `LivingEntityShoot.java` | `consumeAmmoFromPlayer` javadoc | 保留 26.2 javadoc |
| `ModernKineticGunItem.java` | dormant progression 说明 | 保留 26.2 注释 |
| `ModernKineticGunScriptAPI.java` | `getBoltByInt` Lua ABI 说明 | 保留 26.2 注释 |

除 `LocalPlayerReload` 的 gated 日志探针外，其余五处都是注释差异。第一份 source patch 在
26.2 基线上已静态验证可直接应用；第二份只有 `LocalPlayerShoot.java` 冲突。

### 4.3 文档差异

- `docs/CHANGELOG_1_21_11.md` 在两个 26.x 分支不存在，且内容明确只描述 1.21.11；禁止复制。
- 三个 README 都是分支专用文档，版本、Java、依赖、渲染说明不同；只能手工加入同义 API
  小节，禁止用 1.21.11 README 覆盖。
- `docs/AMMO_SOURCE_API.md` 主体可复用，但 Compatibility notice 明确写了 `1.21.11`；复制后
  必须改为当前目标分支的 changelog/release notes，再把目标 README 链接到它。
- 本次同步不需要改 `build.gradle`、`settings.gradle`、`gradle.properties`、
  `fabric.mod.json` 或 mixin JSON，也不应顺手 bump 版本。

目标 README 可按原有标题层级手工加入以下版本中立正文：

```markdown
### 下游弹药源与稳定行为入口

下游模组可通过 `AmmoSourceRegistry.EVENT` 注册实体自有弹药源；首个返回非 `null`
的 provider 生效，无匹配时仍回退实体原有物品栏。注册、查询与消费契约见
[`docs/AMMO_SOURCE_API.md`](docs/AMMO_SOURCE_API.md)。

开火、换弹、拉栓与动画路径另提供 protected 具名方法，供下游避免绑定 javac 合成名称。
`lambda$...` 等编译器生成方法不是兼容 API。
```

---

## 5. 同步前置条件与提交策略

1. PR #48 应先完成审查；若尚未合并，26.x PR 标为 Draft 并声明依赖精确提交
   `28aa9bb`、`ce9d4b2`。
2. `26.2(main)` 与 `26.1.2` 各使用自己的工作分支和 PR；不得把两个目标合并成一个 PR。
3. 每个目标保留两个实现提交：
   - `Add replaceable ammunition source API`；
   - `Expose stable gameplay behavior hooks`。
4. 文档可以随第一提交加入；第二提交的钩子清单写入目标分支的 release notes/README 或 PR
   说明。不要为了保持提交 SHA 相同而牺牲分支文档正确性。
5. P2-min 必须是后续独立 PR，不能加入上述两个同步提交。

下面的命令假设 Agent 已在**目标分支自己的工作分支**，工作区干净。不要在正式目标分支上
直接提交。

```bash
git status --short
git fetch origin \
  arena/01a0086f-tacz-refabricated-unofficial \
  1.21.11 26.1.2 '26.2(main)'

SOURCE_BASE=4a983253b557c4d6c6cd9a7159aaec8e4a2cdbc8
AMMO_COMMIT=28aa9bb
HOOK_COMMIT=ce9d4b2

# 必须验证输入没有漂移
test "$(git rev-parse "$AMMO_COMMIT")" = \
  28aa9bba37b78fb8d9bca0769e9996863a55c707
test "$(git rev-parse "$HOOK_COMMIT")" = \
  ce9d4b2f9384e1030b53db58f8944f7f4d1839f2
git show --no-patch --oneline "$AMMO_COMMIT" "$HOOK_COMMIT"

# 只生成 Java 实现 patch；故意排除 README 与 1.21.11 changelog
git diff --binary "$SOURCE_BASE" "$AMMO_COMMIT" -- src/main/java \
  > /tmp/tacz-ammo-source-api.patch
git diff --binary "$AMMO_COMMIT" "$HOOK_COMMIT" -- src/main/java \
  > /tmp/tacz-stable-hooks.patch
```

> 上面的完整 `28aa9bb` SHA 校验若因短 SHA 解析或远端更新不成立，应查看提交后重新钉住，
> 不得忽略并从移动中的分支头生成 patch。文末静态检查会再次确认实际 diff。

---

## 6. `26.1.2` 执行步骤

基线必须是 `6c409eea0cfe01e070d0ed3c921b63a7a96cb50d` 或明确包含它的最新目标提交。
如果目标分支已前进，先重新做第 4 节差异检查，不要假定仍然零冲突。

### 6.1 弹药源 API

```bash
git merge-base --is-ancestor \
  6c409eea0cfe01e070d0ed3c921b63a7a96cb50d HEAD
git apply --check /tmp/tacz-ammo-source-api.patch
git apply --index /tmp/tacz-ammo-source-api.patch

git show "$AMMO_COMMIT:docs/AMMO_SOURCE_API.md" > docs/AMMO_SOURCE_API.md
python3 - <<'PY'
from pathlib import Path
path = Path("docs/AMMO_SOURCE_API.md")
text = path.read_text()
old = "called out in the\n1.21.11 changelog/release notes."
new = "called out in this branch's\nchangelog/release notes."
assert old in text
path.write_text(text.replace(old, new))
PY
git add docs/AMMO_SOURCE_API.md
# 手工给 26.1.2 README 增加“下游弹药源 API”小节和文档链接；不要覆盖整文件
git add README.md

git diff --cached --check
git commit -m "Add replaceable ammunition source API"
```

README 文案必须写 26.1.2，不得声称使用 Java 21、remap Loom 或 1.21.11 release notes。

### 6.2 P0/P1 稳定钩子

```bash
git apply --check /tmp/tacz-stable-hooks.patch
git apply --index /tmp/tacz-stable-hooks.patch
git diff --cached --check
git commit -m "Expose stable gameplay behavior hooks"
```

在所钉基线上，两份 source patch 已用独立临时 index 顺序执行并通过 `git apply --check`。
若这里出现冲突，说明目标头已变化；停止并按方法级语义合并，不使用 `--reject` 后盲目继续。

---

## 7. `26.2(main)` 执行步骤

基线必须是 `99b472a6a8e1438f22a29abe8b3804b349cb5dfd` 或明确包含它的最新目标提交。

### 7.1 弹药源 API

```bash
git merge-base --is-ancestor \
  99b472a6a8e1438f22a29abe8b3804b349cb5dfd HEAD
git apply --check /tmp/tacz-ammo-source-api.patch
git apply --index /tmp/tacz-ammo-source-api.patch

git show "$AMMO_COMMIT:docs/AMMO_SOURCE_API.md" > docs/AMMO_SOURCE_API.md
python3 - <<'PY'
from pathlib import Path
path = Path("docs/AMMO_SOURCE_API.md")
text = path.read_text()
old = "called out in the\n1.21.11 changelog/release notes."
new = "called out in this branch's\nchangelog/release notes."
assert old in text
path.write_text(text.replace(old, new))
PY
git add docs/AMMO_SOURCE_API.md
# 手工给 26.2 README 增加同义小节；保留 26.2/Java 25/依赖与渲染说明
git add README.md

git diff --cached --check
git commit -m "Add replaceable ammunition source API"
```

第一份 source patch 在所钉 26.2 基线上可以直接应用；不要因为相关文件有注释差异就整文件
替换为 1.21.11 版本。

### 7.2 P0/P1 稳定钩子及唯一冲突

```bash
# 预期 --check 只报告 LocalPlayerShoot.java 不适用
git apply --check /tmp/tacz-stable-hooks.patch || true

# 三方应用；预期只有 LocalPlayerShoot.java 进入冲突状态
git apply --3way --index /tmp/tacz-stable-hooks.patch

git diff --name-only --diff-filter=U
# 预期：src/main/java/com/tacz/guns/client/gameplay/LocalPlayerShoot.java
```

解决 `LocalPlayerShoot` 时，采用 P0/P1 重构后的方法划分，并把主线程跳转处合并为：

```java
// 此处处于ScheduledExecutorService的线程中，而下面的动画状态机、
// 声音和事件都是客户端主线程状态；如果直接调用会和主线程并发修改集合。
// 必须经由Minecraft本身的事件循环提交，
// 让射击副作用回到客户端主线程。
((BlockableEventLoopAccessor) Minecraft.getInstance()).tacz$submitAsync(
        () -> applyClientFireEffects(display, mainHandItem, gunData));
```

不得把事件、动画或声音搬回 scheduler 线程，也不得删除
`runClientShootCycle(...)` / `applyClientFireEffects(...)` 的具名边界。

```bash
git add src/main/java/com/tacz/guns/client/gameplay/LocalPlayerShoot.java

test -z "$(git diff --name-only --diff-filter=U)"
rg -n 'RenderConfig\.RECOIL_DEBUG|TACZ Case08|RELOAD_START' \
  src/main/java/com/tacz/guns/client/gameplay/LocalPlayerReload.java
rg -n '<<<<<<<|=======|>>>>>>>' src/main/java || true

git diff --cached --check
git commit -m "Expose stable gameplay behavior hooks"
```

若冲突文件不止一个，或 `LocalPlayerReload` 探针消失，立即停止；这表示目标基线已漂移，必须
重新比较目标文件，不能照本文旧冲突结论强行提交。

---

## 8. 同步后的静态验收

### 8.1 文件/API 形状

```bash
# 新 API 三文件必须存在
test -f src/main/java/com/tacz/guns/api/item/ammo/AmmoSource.java
test -f src/main/java/com/tacz/guns/api/item/ammo/AmmoSourceProvider.java
test -f src/main/java/com/tacz/guns/api/item/ammo/AmmoSourceRegistry.java

# fallback 与核心调用点
rg -n 'AmmoSourceRegistry\.(hasAmmo|consumeAmmo)' src/main/java/com/tacz/guns
rg -n 'getAmmoSource\(' \
  src/main/java/com/tacz/guns/api/item/ammo/AmmoSourceRegistry.java

# P0 和关键 P1 具名入口
rg -n 'protected boolean hasAmmoToConsumeInEntity' \
  src/main/java/com/tacz/guns/client/animation/statemachine/GunAnimationStateContext.java
rg -n 'runClientShootCycle|applyClientFireEffects|isShootLockCondition' \
  src/main/java/com/tacz/guns/client/gameplay/LocalPlayerShoot.java
rg -n 'shootInternal|reloadWithIndex|boltWithIndex' \
  src/main/java/com/tacz/guns/entity/shooter
rg -n 'runShootCycle|spawnProjectiles|handleShootHeatWithScript' \
  src/main/java/com/tacz/guns/item/ModernKineticGunScriptAPI.java

# 历史协议与 Predicate 身份约束
grep -F 'INPUT_BOLT = "blot"' \
  src/main/java/com/tacz/guns/client/animation/statemachine/GunAnimationConstant.java
grep -F 'SHOOT_LOCKED_CONDITION = LocalPlayerShoot::isShootLockActive' \
  src/main/java/com/tacz/guns/client/gameplay/LocalPlayerShoot.java
grep -F 'return condition == SHOOT_LOCKED_CONDITION;' \
  src/main/java/com/tacz/guns/client/gameplay/LocalPlayerShoot.java

# 不应残留冲突或旧 walk helper
test -z "$(rg -l '<<<<<<<|=======|>>>>>>>' src/main/java || true)"
test -z "$(rg -l 'tacz\$walkDistance' src/main/java || true)"
# 在 26.1.2 工作分支执行：
git diff --check 6c409eea0cfe01e070d0ed3c921b63a7a96cb50d...HEAD
# 在 26.2 工作分支执行：
git diff --check 99b472a6a8e1438f22a29abe8b3804b349cb5dfd...HEAD
```

还应人工查看 `git diff --stat`：不应出现 build、mapping、mixin、资源、网络或大批无关文件。

### 8.2 编译

26.x 要求 JDK 25。具备正确环境的 Agent/CI 至少执行：

```bash
java -version
./gradlew compileJava --no-daemon
./gradlew test --no-daemon          # 若分支存在可执行测试
./gradlew build --no-daemon
```

如果完整 build 因已知内存约束失败，应保存完整日志并至少完成 `compileJava`；不能把依赖下载
失败、工具链缺失或 OOM 写成“代码编译通过”。当前撰写本文的环境没有对应 JDK，因此这里只
完成了 Git/source 级静态验证。

### 8.3 行为回归矩阵

两个目标分支分别验证，不得用一个分支的运行结果替代另一个：

| 类别 | 必测项 |
|---|---|
| provider 选择 | 无 provider fallback；provider 返回 `null` fallback；多个 provider 首个非 null 胜出 |
| 查询 | 普通 `IAmmo`；`IAmmoBox`；自定义库存；`hasAmmo` 无写入 |
| 消耗 | 请求 0/负数；部分供应；足量供应；provider 越界返回值被 clamp |
| 弹药箱 | 扣至 0 后 ammo id 重置为 `DefaultAssets.EMPTY_AMMO_ID` |
| 特殊弹药 | dummy ammo；creative；无限 reload；inventory feed；fuel；magazine |
| bolt | OPEN_BOLT、CLOSED_BOLT、MANUAL_ACTION；膛内有/无弹 |
| shoot | 单发、连发、蓄力；客户端预测与服务端权威结果一致；过热中止 |
| reload | 开始、取消、完成、战术换弹；客户端/服务端门槛仍各自独立 |
| animation | dry fire、shoot、reload、cancel reload、bolt；P0 状态机弹药查询 |
| fallback 优先级 | 自定义 source 只替换实体库存，不越过 dummy/creative/infinite 的原短路 |
| 26.2 专项 | 开启 `RECOIL_DEBUG` 后仍打印 `TACZ Case08 RELOAD_START`；关闭时不新增日志 |

建议增加一个最小下游测试 mod：同一 provider 在客户端和服务端注册，使用不实现 TaCZ
`IItemHandler` 的自定义库存，证明高层 `AmmoSource` 契约没有退化成内部 handler 耦合。

---

## 9. PR 验收与禁止事项

每个 26.x PR 描述必须包含：

- 来源提交 `28aa9bb`、`ce9d4b2` 和 PR #48；
- 目标基线 SHA；
- 分支差异及手工冲突处理；
- 实际执行的编译/运行测试，未执行项明确写出；
- API 文档链接和“synthetic lambda 不是 API”的兼容声明。

禁止：

- full cherry-pick 后用 1.21.11 README/changelog 覆盖目标文档；
- force push 正式目标分支；
- 在同步 PR 中加入 P2、bug fix、版本 bump、依赖升级或格式化；
- 用整文件复制解决 26.2 冲突；
- 把 `AmmoSource` 改为 TaCZ `IItemHandler` provider；下游库存类型可能不兼容；
- 改 provider 顺序、消费 clamp、ammo box 清空重置或 fallback 行为；
- 替换状态锁 Predicate 对象；
- 修正 `"blot"`。

推荐合并顺序：PR #48（1.21.11）→ `26.2(main)` 同步 PR → `26.1.2` 同步 PR →
单独评审 P2-min。若维护策略要求维护分支先发版，可以交换两个 26.x PR 的合并顺序，但二者
仍须分别验证，且 API 签名必须一致。
