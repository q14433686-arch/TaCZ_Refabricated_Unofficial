# 收枪（put-away）动画不渲染 · `keep()` 修复的三线移植指导（2026-09-02）

> **状态**：26.2(main) 侧已落码，**CI `compileJava` 与全量 `./gradlew build` 均通过**
> （commit `32af402`，[run 33623054002](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/33623054002)，
> 2026-09-02；日志已回推 `build-reports/compile-java.log`），但**实机未验证**；
> 26.1.2 / 1.21.11 侧为 **OPEN**，补丁已备好并在**目标分支的真实 worktree** 上做过
> `git apply --check` + 实落（见 §4）——那两条分支要等各自 CI 编译后才算过第一关。
> **范围**：外部提交 `ca2b9fc` 的那 5 行，**外加两点加固**（窗口守卫语义 + 调用点判定，见 §4bis）——
> 两点加固经维护者裁定与 `ca2b9fc` 同轮落地、三线同步。
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

维护者裁定：**该修复确实应该应用**，并追问 26.1.2 / 1.21.11 能否同样应用 —— 本文即该问题的核对结果。

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

`keep()` 的实现正是补这个窗口：写 `tacz$KeepItem` + 时间戳 + 时长，
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
**刻意留空**的（那段 javadoc 已解释：每 tick 强制写 `mainHandItem`/`mainHandHeight` 会打断切枪动画）；
本次修复与它不冲突 —— 我们只在收枪那一刻写一次 `mainHandItem`，不再每 tick 钉死。

## 3. 三线适用性核对表（2026-09-02 逐分支实拉）

| 核对点 | 26.2(main) | 26.1.2 | 1.21.11 |
|---|---|---|---|
| `LocalPlayerDraw.java` blob | `79887cf` | `79887cf`（同） | `79887cf`（同） |
| `KeepingItemRenderer` 接口（`keep` / `getCurrentItem` / `getRenderer`） | 有，逐字相同 | 有，逐字相同 | 有，逐字相同 |
| `ItemInHandRendererMixin implements KeepingItemRenderer` | 有 | 有 | 有 |
| `keep()` 实现（改前，含时间窗守卫 + 写 `mainHandItem`） | 逐字相同（md5 一致） | 同 | 同 |
| `@Shadow ItemStack mainHandItem` / `mainHandHeight` / `oMainHandHeight` | 有 | 有 | 有 |
| 消费端 `getMainRenderStack → getCurrentItem` | `FirstPersonAnimationCompat`（三线逐字相同） | 同 | 同 |
| 手部 WrapOperation 用 `getMainRenderStack` 选枪 | `submitHandsWithItems` → `submitArmWithItem` | `renderHandsWithItems` → `renderArmWithItem` | `renderHandsWithItems` → `renderArmWithItem` |
| `AnimateGeoItemRenderer#needReInit` 及其上下文区域 | 逐字相同（md5 一致） | 同 | 同 |
| `AnimationStateMachine`（`exit` / `setExitingTime`） | 三线无差异（`git diff` 为空） | 同 | 同 |
| `ItemStack.isSameItemSameComponents`（新守卫要用） | 已在用（5 处文件） | 已在用 | 已在用 |
| **补丁能否直接落** | 已落 | ✅ worktree 实落通过 | ✅ worktree 实落通过 |

**1.21.11 混淆专项**：本次改动**不新增任何 mixin 目标、字段名或方法引用**——
新增的是一个本模组自己的 public 方法（`AnimateGeoItemRenderer#hasInitializedStateMachine`）
与对已有接口方法（`KeepingItemRenderer#keep`）的调用；mixin 侧只改 `@Unique` 方法体，
`@Shadow` 字段与注入点一个没动 ⇒ refmap / intermediary 无涉，
`lambda$xxx$N` 合成名红线（AGENTS.md §3）也不涉及。
`docs/verify_mixin_targets.py` / `docs/verify_shader_imports.py` 的输入没有变化（可选跑，预期无差异）。
Java 21 语法层面没有新特性。

## 4. 补丁与应用方法

两份**分支版本号已改写**的补丁（AGENTS.md §3：跨分支复制必须逐句改版本号；
两份的差异只有注释里那一句 `Minecraft 26.1.2` / `Minecraft 1.21.11`）：

```
docs/patch/2026-09-02-putaway-keep-render-26.1.2.patch     (4 文件 / 125 行)
docs/patch/2026-09-02-putaway-keep-render-1.21.11.patch    (4 文件 / 125 行)
```

覆盖的 4 个文件（两份补丁一致）：

| 文件 | 改动 |
|---|---|
| `client/gameplay/LocalPlayerDraw.java` | `ca2b9fc` 本体：import + `doPutAway` 里带判定的 `keep()` 调用 |
| `client/renderer/item/AnimateGeoItemRenderer.java` | 新增 `hasInitializedStateMachine(ItemStack)`；`tryExit` 里那行注释加「不要打开」的说明 |
| `client/renderer/item/GunItemRendererWrapper.java` | 同上的注释说明（override 版 `tryExit`） |
| `mixin/client/ItemInHandRendererMixin.java` | `keep()` 守卫语义修正（§4bis 第 1 点） |

2026-09-02 的验证方式（**在目标分支的真实 worktree 上做的，不是拍脑袋**）：

```bash
git worktree add --no-checkout /tmp/wt_2612 origin/26.1.2
git -C /tmp/wt_2612 sparse-checkout set --no-cone \
  'src/main/java/com/tacz/guns/client/gameplay/LocalPlayerDraw.java' \
  'src/main/java/com/tacz/guns/client/renderer/item/AnimateGeoItemRenderer.java' \
  'src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java' \
  'src/main/java/com/tacz/guns/mixin/client/ItemInHandRendererMixin.java'
git -C /tmp/wt_2612 checkout
git -C /tmp/wt_2612 apply --check docs/patch/2026-09-02-putaway-keep-render-26.1.2.patch   # → OK
git -C /tmp/wt_2612 apply        docs/patch/2026-09-02-putaway-keep-render-26.1.2.patch    # → 实落成功
# 1.21.11 同法（origin/1.21.11 + …-1.21.11.patch）→ 同样 OK
```

落完后的**等价性核对**（也已实测）：三条分支上
`keep()` 方法体、`hasInitializedStateMachine()` 方法体**逐字相同**；
`LocalPlayerDraw.java` 整文件只差注释里那一个 MC 版本号词
（26.2 / 26.1.2 / 1.21.11）。也就是说三线落地后是**同一份逻辑**。

正式应用（各分支各自执行；本会话的 arena 分支被固定在 26.2 线上，无法代推）：

```bash
git checkout 26.1.2   && git apply docs/patch/2026-09-02-putaway-keep-render-26.1.2.patch
git checkout 1.21.11  && git apply docs/patch/2026-09-02-putaway-keep-render-1.21.11.patch
```

## 4bis. 本仓对 `ca2b9fc` 的两点加固（超出原提交范围，三线同步）

维护者裁定这两点与 `ca2b9fc` 同轮处理。**它们改的是既有语义，因此必须三线一起改**
（补丁已包含），并且都要单独实测。

### 加固 1：`keep()` 的时间窗守卫 —— 从「窗口内一律忽略」改为「最新一次收枪接管」

改前（三线逐字相同，源自上游）：

```java
long time = System.currentTimeMillis() - tacz$KeepTimestamp;
if (time < tacz$KeepTimeMs) { return; }          // ← 窗口未过期就整条忽略
```

后果：连续快速切枪（A→B→C）时，第二次收枪**接管不了**窗口 ——
上一把枪的剩余窗口继续生效，第二把枪的 put_away 一帧都画不出来，
窗口长度还比它需要的短。

改后：

```java
long now = System.currentTimeMillis();
boolean sameKeptItem = tacz$KeepItem != null
        && ItemStack.isSameItemSameComponents(tacz$KeepItem, itemStack);
if (sameKeptItem && now + timeMs <= tacz$KeepTimestamp + tacz$KeepTimeMs) { return; }
```

即：**不同物品 → 接管**（最新一次收枪说了算）；**同一物品且请求更长 → 刷新/延长**；
**同一物品且请求更短 → 忽略**（保留原守卫里唯一良性的那一半：不截断正在播放的动画）。

为什么「接管」是安全的（不会用一个静止视模顶掉正在播放的动画）：由加固 2 的调用点判定保证 ——
只有旧枪的状态机确实初始化过（= 它此前一直在被渲染）才会调 `keep()`。
反过来说，A 的窗口还开着时，B 从没被画过 ⇒ B 的状态机没初始化 ⇒ 加固 2 直接不调 `keep(B)`，
A 的收枪动画播完再切 C。这正是期望行为。

### 加固 2：调用点判定 —— 对齐上游的 `isInitialized()` 语义

`ca2b9fc` 的调用点条件是「`lastItem` 有 `AnimateGeoItemRenderer`」，
比上游那两处注释（在 `stateMachine.isInitialized()` 之内）**宽**。
差异场景 = 旧枪的状态机从未初始化（刚进世界、第三人称下切枪、上一把枪的窗口未过期所以这把从没被画过）：
会开出 `putAwayTime` 的窗口，但窗口里**没有 put_away 可播** ⇒ 旧枪静止一瞬再切新枪，
比不开窗口更难看。

落地方式不是在 `doPutAway` 里重写状态机逻辑，而是把上游那条判定暴露成一个方法
（与 `tryExit` 内部判定**同源**，将来只会一起变）：

```java
// AnimateGeoItemRenderer
public boolean hasInitializedStateMachine(ItemStack stack) {
    var stateMachine = getStateMachine(stack);
    return stateMachine != null && stateMachine.isInitialized();
}

// LocalPlayerDraw#doPutAway
if (renderer.hasInitializedStateMachine(lastItem)) {
    KeepingItemRenderer.getRenderer().keep(lastItem, putAwayTime);
}
renderer.tryExit(lastItem, putAwayTime);
```

注意 `getStateMachine` 在 `GunItemRendererWrapper` 里是 override（按枪取
`GunDisplayInstance#getAnimationStateMachine`），所以枪械走的是**每把枪自己的**状态机，判定是对的。

### 波及面：内置 LRTactical 的近战 / 投掷 / 消耗品也跟着受益（需一并实测）

`doPutAway` 的入口判定是 `instanceof AnimateGeoItemRenderer`，而本仓内置的 LRTactical 三个渲染器
（`MeleeItemRenderer` / `ThrowableItemRendererWrapper` / `ConsumableItemRenderer`，均在
`src/main/java/me/xjqsh/lrtactical/client/renderer/item/`）都继承它、都 override 了
`getStateMachine(stack)`（按各自 display 取状态机）、都**没有** override `tryExit`。
所以：

- 加固 2 的判定对它们同样成立（用的是各自物品的状态机，不会误判）；
- 它们此前**也从来没有 keep 窗口**（同因：上游那行注释），即近战/投掷/消耗品的收招动画
  同样会被新物品顶掉。本次修复顺带覆盖这三族 —— 这是**行为扩大**，不是纯 bug 修复，
  实测清单里必须单独过一遍（§5 第 7 项）。

## 5. 落码之后必须实测的项（三线各做一遍，**目前全部未验证**）

沙箱内既没有 Minecraft 工件也没有实机，**本指导不含任何实测结论**（CI 只证明能编译、能出 jar）；
以下按「同一补丁、不同纪元后果可能不同」排序：

> **可实测构建**：26.2 侧 CI 产物 `TACZ-Refabricated-32af402…`（约 57 MB，保留至 2026-09-16）
> 在 [run 33623054002](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/33623054002)
> 的 Artifacts 区下载，可直接丢进客户端 mods 目录征测。

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
4. **加固 1 的验证**：快速连切三把枪（A→B→C，间隔小于一把枪的 putAwayTime）：
   期望 A 的收枪播完 → C 的抬枪；B 被跳过（因为它从没被画过），**不应**出现
   「静止的 B 挂在手上」或「A 的动画被截断」。再试同一把枪的连续收放（A→B→A），
   期望不出现动画被截短。
5. **加固 2 的验证**：刚进世界的第一次切枪、第三人称下切枪、以及
   「拿着枪掉线重连后立刻切枪」——期望**不**出现旧枪静止一瞬的空窗口。
6. **`mainHandItem` 被写一次的副作用**：与 Viewmodel Changer / Hide Hands / SkyHands
   等同样动 `ItemInHandRenderer` 的模组共存时，收枪窗口内不应出现双手/无手/错位
   （26.2 的 mixin javadoc 已记录这批模组的注入点差异，三线各测一次）。
7. **LRTactical 三族（行为扩大项，见 §4bis 末）**：近战武器、投掷物、消耗品各自
   「用一半切走」时应能看到收招动画播完再切新物品；`getPutAwayTime` 的单位差异
   （近战/消耗品是 tick×50，投掷物本身即毫秒）在窗口长度上应表现正常
   —— 特别是消耗品：它的窗口来自 `data.getPutAwayTime() * 50L`，若实测发现窗口过长/过短，
   问题在单位换算而**不在**本次改动，勿顺手改守卫。

## 6. 纪律提醒

- **只保留一个 `keep()` 调用点。** 不要把 `AnimateGeoItemRenderer#tryExit` 与
  `GunItemRendererWrapper#tryExit` 里那两行注释打开（两处都已加「不要打开」的说明注释）。
  若要改回上游调用点，先删 `doPutAway` 里这一句。
- **加固 1 / 加固 2 改的是既有语义**：任何后续调整（例如把守卫改回「窗口内一律忽略」）
  都必须三线同时改，并在账本另记一行 —— 单分支改会让三线的切枪手感分叉，
  而这类分叉此前极难被发现（三线代码本来逐字相同）。
- **不动 `gradle.properties`**：本次无版本号变化，故 AGENTS.md §1 的 6 处 README 同步不触发。
  若随某个 hotfix 一起发布，序号按规矩直接接在 `hotfix` 后（`hotfix3`，不加分隔符）。
- **对外文案**：在实机确认前，release notes / issue 回复里只能写成
  「恢复 `keep()` 调用并修正其窗口守卫，使收枪动画有视模可画（待实测）」，
  不得写成已验证的 "fixed"（AGENTS.md §2）。
