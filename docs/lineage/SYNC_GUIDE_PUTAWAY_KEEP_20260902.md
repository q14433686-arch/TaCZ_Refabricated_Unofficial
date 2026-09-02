# 收枪（put-away）动画不渲染 · `keep()` 修复的三线移植指导（2026-09-02）

> **状态**：26.2(main) 侧已落码（本分支），**未实机验证**；26.1.2 / 1.21.11 侧为 **OPEN**，
> 补丁已备好并做过 `git apply --check` 干跑（见 §4）。
> **性质**：这是「同代码、同机制」的移植件，不是 `FAMILY_TREE_2026_08_30.md` §1 里那种
> 「物理不可互抄」的 GPU/纪元件。
> 账本行：`HANDOFF_LEDGER.md` #14。

---

## 0. 起因与货源

外部 fork [`Legionoff/TaCZ_Refabricated_Unofficial_GPT_Edition`](https://github.com/Legionoff/TaCZ_Refabricated_Unofficial_GPT_Edition)
的 `26.2(main)` 尖端提交
[`ca2b9fc`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/ca2b9fc5d774cca0ec5fa121f6d01bf9e5f4d0e9)
「Fix TACZ put-away animation rendering on Fabric 26.2」：改一个文件、+5 行（1 行 import + 4 行），
在 `LocalPlayerDraw#doPutAway` 里于 `renderer.tryExit(...)` **之前**补一句

```java
KeepingItemRenderer.getRenderer().keep(lastItem, putAwayTime);
```

维护者裁定：**该修复确实应该应用**。

两条必须知道的货源事实（2026-09-02 实拉核对，非记忆）：

1. **不能 cherry-pick。** 该 fork 那条线以 `015a332 Add files via upload`（2026-08-17 的整仓快照）起头，
   与本仓 `git merge-base ca2b9fc HEAD` **为空**（无共同祖先）。只能语义移植 / `git apply`。
2. **它的基线文件与我们逐字相同。** `ca2b9fc^` 的
   `src/main/java/com/tacz/guns/client/gameplay/LocalPlayerDraw.java`
   与本仓 **三条分支**的同名文件是同一个 blob：`79887cf`
   （`git rev-parse HEAD:… = origin/26.1.2:… = origin/1.21.11:…`，实测三者同哈希）。

---

## 1. 症状与机制（为什么少了这一句就没有收枪动画）

TACZ 的第一人称视模由我们自己的 mixin 接管，选哪把枪来画的链条是：

```
ItemInHandRendererMixin（WrapOperation 包裹手部提交调用）
  → FirstPersonAnimationCompat#getMainRenderStack(player)
    → KeepingItemRenderer#getRenderer().getCurrentItem()
      → keep 窗口内：tacz$KeepItem（旧枪）
      → 窗口外：    mainHandItem（vanilla 字段，早已换成新枪）
  → geoRenderer.renderFirstPerson(... renderStack ...)
```

`LocalPlayerDraw#draw` 的时序是：`doPutAway(lastItem, putAwayTime)` → `doDraw(currentItem, …)`。
`doPutAway` 里 `tryExit` 会 `trigger(INPUT_PUT_AWAY)` 然后 `stateMachine.exit()` +
`setExitingTime(putAwayTime + 50)`；put_away 的播放由 `AnimationController` 继续推进
（`renderFirstPerson` 每帧 `animationStateMachine.update()`），**前提是这把旧枪还在被提交渲染**。

没有人调 `keep()` 时：`getCurrentItem()` 只回落到 vanilla 的 `mainHandItem`（新枪）⇒
旧枪视模一帧都不再提交 ⇒ put_away 无处可画；同时新枪 `needReInit()` 立刻成立
（`!isInitialized() && exitingTime < now`，而新枪是另一台状态机，`exitingTime` 默认 -1）⇒
`tryInit` 直接 `INPUT_DRAW`。观感就是「收枪动画被吞、切枪瞬间完成」。

`keep()` 的实现（三线逐字相同）正是补这个窗口：写 `tacz$KeepItem` + 时间戳 + 时长，
并把 `mainHandItem` 一并写成旧枪；窗口过期后 `getCurrentItem()` 自然回落，
新枪的 `INPUT_DRAW` 才发生 —— 收枪→抬枪的先后次序由此恢复。

## 2. 这不是本仓移植时删掉的：`keep()` 的注释是**继承**来的

实拉核对三处源码，`tryExit` 里那行 `keep(...)` **都是注释状态**：

| 仓库 / 分支 | 文件 | 行 |
|---|---|---|
| `Sh1roCu/TACZ-Refabricated` @ `1.21.1`（直接上游） | `AnimateGeoItemRenderer.java` | 139 |
| `MCModderAnchor/TACZ` @ `1.20.1` | `AnimateGeoItemRenderer.java` | 131 |
| 本仓三线 | `AnimateGeoItemRenderer.java` 153 + `GunItemRendererWrapper.java` 133/153 | — |

所以：**三条分支同病同因**，26.2 并不是特例（`ca2b9fc` 的提交信息写「on Fabric 26.2」
只是因为它在那条分支上做的）。同理，`ItemInHandRendererMixin#tick` 的注入在三线都是
**刻意留空**的（那段 javadoc 已解释：强制写 `mainHandItem`/`mainHandHeight` 会打断切枪动画），
本次修复与它不冲突 —— 我们只在收枪那一刻写一次 `mainHandItem`，不再每 tick 钉死。

## 3. 三线适用性核对表（2026-09-02 逐分支实拉）

| 核对点 | 26.2(main) | 26.1.2 | 1.21.11 |
|---|---|---|---|
| `LocalPlayerDraw.java` blob | `79887cf` | `79887cf`（同） | `79887cf`（同） |
| `KeepingItemRenderer` 接口（`keep` / `getCurrentItem` / `getRenderer`） | 有，逐字相同 | 有，逐字相同 | 有，逐字相同 |
| `ItemInHandRendererMixin implements KeepingItemRenderer` | 有 | 有 | 有 |
| `keep()` 实现（含时间窗守卫 + 写 `mainHandItem`） | 逐字相同 | 逐字相同 | 逐字相同 |
| `@Shadow ItemStack mainHandItem` / `mainHandHeight` / `oMainHandHeight` | 有 | 有 | 有 |
| 消费端 `getMainRenderStack → getCurrentItem` | `FirstPersonAnimationCompat`（三线逐字相同） | 同 | 同 |
| 手部 WrapOperation 用 `getMainRenderStack` 选枪 | `submitHandsWithItems` → `submitArmWithItem` | `renderHandsWithItems` → `renderArmWithItem` | `renderHandsWithItems` → `renderArmWithItem` |
| `AnimateGeoItemRenderer#needReInit` / `#tryExit` | 逐字相同 | 逐字相同 | 逐字相同 |
| `AnimationStateMachine`（`exit` / `setExitingTime`） | 三线无差异（`git diff` 为空） | 同 | 同 |
| **补丁能否直接落** | 已落 | ✅ `git apply --check` 通过 | ✅ `git apply --check` 通过 |

**1.21.11 混淆专项**：本次改动**不新增任何 mixin 目标、字段名或方法引用**——
纯 Java 调用一个本模组自己的接口（`KeepingItemRenderer`），既不进 refmap，
也不涉及 `lambda$xxx$N` 合成名（AGENTS.md §3 的红线）。`docs/verify_mixin_targets.py` /
`docs/verify_shader_imports.py` 的输入没有变化（可选跑，预期无差异）。
Java 21 语法层面也没有新特性（无 pattern matching、无 switch 表达式新增）。

## 4. 补丁与应用方法

已按 `docs/patch/` 现有命名规矩放两份**分支版本号已改写**的补丁
（AGENTS.md §3：跨分支复制必须逐句改版本号；两份的唯一差异就是注释里那一句
`Minecraft 26.1.2` / `Minecraft 1.21.11`）：

```
docs/patch/2026-09-02-putaway-keep-render-26.1.2.patch
docs/patch/2026-09-02-putaway-keep-render-1.21.11.patch
```

两份都在 2026-09-02 用**目标分支的真实 blob** 建临时仓干跑验证过：

```bash
# 复核方式（任意机器）
git show origin/26.1.2:src/main/java/com/tacz/guns/client/gameplay/LocalPlayerDraw.java > /tmp/f.java
# 放进同路径的临时 git 仓后：
git apply --check docs/patch/2026-09-02-putaway-keep-render-26.1.2.patch   # → Checking patch … OK
```

应用（各分支各自执行；本会话的 arena 分支被固定在 26.2 线上，无法代推）：

```bash
git checkout 26.1.2   && git apply docs/patch/2026-09-02-putaway-keep-render-26.1.2.patch
git checkout 1.21.11  && git apply docs/patch/2026-09-02-putaway-keep-render-1.21.11.patch
```

## 5. 落码之后必须实测的项（三线各做一遍，**目前全部未验证**）

沙箱内既没有 Minecraft 工件也没有实机，**本指导不含任何实测结论**；
以下按「同一补丁、不同纪元后果可能不同」排序：

1. **基本项**：切枪（枪→枪、枪→徒手/普通物品、普通物品→枪）能看到旧枪的收枪动画，
   且抬枪动画在其之后开始，不是同帧抢跑。
2. **纪元差异项（唯一真正分支相关的风险）**：keep 窗口内 `getCurrentItem()` 返回**旧枪**，
   它的消费者不止手部渲染——
   `CameraSetupEvent`（4 处）、`FirstPersonRenderGunEvent`，以及
   **26.2 = `ScopePipRenderer`（掩码/PIP 纪元）** vs **26.1.2 / 1.21.11 = `ScopePipRenderState`（深度孔径纪元）**。
   两条纪元的开镜实现不可互抄，故开镜相关表现要各自 A/B：
   开镜中切枪 / 切枪瞬间开镜 / 快速来回切两把带镜枪。
3. **光影（Iris）项**：26.2 的手部相位门禁是 `IRIS_HAND_PHASE_SPLIT_FIX` + `isHandRenderingSolid()`，
   26.1.2 / 1.21.11 是 `shouldRenderInCurrentHandPhase(renderStack)`。
   收枪窗口内旧枪只应提交一遍（不闪烁、不双影）。
4. **快速连切两把枪**：`keep()` 的守卫是 `if (time < tacz$KeepTimeMs) return;` ——
   窗口**未过期时的第二次收枪不会刷新窗口**，第二把枪的收枪动画可能仍不显示。
   这是三线同因同码的既有语义，**本次未改动**（超出 `ca2b9fc` 范围）。
   若实测确认碍眼，改法是三线同时改（例如窗口内改为「延长到新 putAwayTime 的剩余量」），
   并单独记一条账本行，不要顺手在一条分支上改。
5. **状态机未初始化时的收枪**（与上游调用点的细节差）：`ca2b9fc` 把调用点放在 `doPutAway`，
   条件是「lastItem 有 `AnimateGeoItemRenderer`」；上游那两处注释在 `stateMachine.isInitialized()` 之内。
   差异场景 = 刚进世界 / 第三人称下切枪等状态机尚未初始化时收枪：会开出 `putAwayTime` 的窗口
   但没有 put_away 可播（旧枪静止一瞬再切新枪）。若实测觉得碍眼，两种收口任选其一，
   并三线同步：①`keep` 前加 `renderer.getModel(lastItem) != null` 判定；②把调用点搬回 `tryExit` 内。

## 6. 纪律提醒

- **只保留一个 `keep()` 调用点。** 不要把 `AnimateGeoItemRenderer#tryExit`(153) 与
  `GunItemRendererWrapper#tryExit`(133/153) 里那两行注释打开——两处都开会重复调用
  （虽有守卫不致错，但语义重复、且后来者会误判触发时机）。若要改回上游调用点，
  先删 `doPutAway` 里这一句。
- **不动 `gradle.properties`**：本次无版本号变化，故 AGENTS.md §1 的 6 处 README 同步不触发。
  若随某个 hotfix 一起发布，序号按规矩直接接在 `hotfix` 后（`hotfix3`，不加分隔符）。
- **对外文案**：在实机确认前，release notes / issue 回复里只能写成
  「恢复 `keep()` 调用，使收枪动画有视模可画（待实测）」，不得写成已验证的 "fixed"
  （AGENTS.md §2）。
