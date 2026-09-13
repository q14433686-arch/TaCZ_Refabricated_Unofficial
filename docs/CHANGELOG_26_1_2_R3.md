# 26.1.2 线变更记录（R3 周期）

> 口径：只记**对玩家/维护者可见**的行为变化，每条标证据级别（AGENTS §2）。
> 本分支此前没有 changelog 文件（只有 `UPDATE_REPORT_26_1_2_R1/R2` 的发版报告），
> 本文件从 2026-09-02 这轮跨线同步开始记，格式对齐 `docs/lineage/` 的同步体例。
> 同步过程与处置表：`docs/lineage/SYNC_ROUNDUP_2612_20260902.md`；
> 账本：`docs/lineage/HANDOFF_LEDGER.md` 的 M-1…M-9 行。

---

## 2026-09-13 · 跨线同步轮（1.21.11 `b3a01b1` → 本线）+ 版本号改为 R3-hotfix2

### 修复

1. **第一人称手部错位（全枪械）：中和 vanilla 1.21.9+ 手臂的 `zRot=±0.1`**
   同步自 1.21.11 线 `61ab4a0`，落点 `com.tacz.guns.util.RenderHelper`。
   - **症状**：所有手枪整体偏左、手没握住枪；两把默认双管换弹时手部绑定/动画错位、
     弹药悬浮在手上方。错位量恒定、非常有规律。1.21.1 上游无此问题，
     26.2 / 26.1.2 / 1.21.11 **全分支复现**。
   - **根因**：vanilla 在 1.21.1 → 1.21.9 的渲染重构里给 `AvatarRenderer#renderHand`
     加了 `model.leftArm.zRot = -0.1F` / `model.rightArm.zRot = 0.1F`（约 ±5.7°）；
     而 TACZ 全部枪模的 `righthand_pos` / `lefthand_pos` 都是按 1.21.1 的 `zRot=0`
     姿态 authored 的。手臂网格绕肩部 pivot 凭空多转 ±5.7° ⇒ 手相对枪恒定偏转。
   - **修法**：`RenderHelper#renderFirstPersonArm`（带 `clipToScopeExterior` 的那个重载，
     即真正调用 vanilla 的那个）在每次 vanilla 手部调用之后调用新增的
     `resetFirstPersonArmLean(renderer)`，把 `PlayerModel` 的**两条**手臂 `zRot` 清零。
     依据是本文件此前记过的机制：`submitModelPart` 只拷贝矩阵、`ModelPart` 是活引用，
     旋转要到 `renderHandsWithItems` 末尾的 `renderAllFeatures`（26.1.2 字节码实测 @281）
     才被读取，所以 submit 之后、flush 之前的写入决定最终姿态。
     两条必须一起清：vanilla 每次调用**同时**污染左右两条，双持枪连续提交两次时
     后一次会把前一条重新污染。
   - **为什么不影响 vanilla 物品的手臂**：本方法只在 TACZ 接管 viewmodel 时被调用
     （`ItemInHandRendererMixin#tacz$submitArmWithAnimatedItem` 拦掉 vanilla 的
     `renderArmWithItem` 之后），TACZ 接管的 flush 里没有 vanilla 手臂提交
     （主手接管时副手被拦、TACZ 从不渲染副手）；纯 vanilla 的 flush 走不到这里。
   - **与「第 5 轮：submit 之后绝不能还原 PlayerModel」不矛盾**：第 5 轮禁止的是把
     vanilla **整备好**的 `visible`/pose 还原掉（那会重现手臂残缺）；本轮中和的是
     vanilla **新增**的一笔写入，且只动 `zRot`。同一机制、相反方向，是刻意的。
     两处注释互相指认，避免后来者按第 5 轮的口径把本轮改动"顺手回滚"。
   - **本线适配点**：26.1.2 的 `renderFirstPersonArm` 比 1.21.11 多一个
     `clipToScopeExterior` 重载（镜内裁手），清零那一步与该开关无关，
     因此放在 6 参重载里**无条件**执行；5 参重载只加了一句指向说明。
   - *证据级别：1.21.11 反编译源码逐行确认（`61ab4a0` 已注明 1.21.9/1.21.10/26.1.2
     三条 vanilla 代码相同）+ 那条 commit 在 1.21.11 线过了 CI 编译门；
     本线 = **同形移植**，编译门待 CI 回写、**实机未验**（沙箱无 JDK 与 Loom 缓存）。*

   验收（实机）：第一人称下手与枪贴合、手枪不再整体偏左；两把默认双管换弹时
   手部与弹药对齐；第三人称、vanilla 物品（含盾牌/弓/普通方块）的手臂姿态不变；
   开关 `ScopeArmClip`（镜内裁手）两种状态下各看一次。

### 版本号

2. **`mod_version` → `1.1.8+fabric.26.1.2.R3-hotfix2`**
   命名沿用 R2 那一轮的规矩（`R2` → `R2-hotfix` → `R2-hotfix2`）：hotfix 序号
   **直接接在 `hotfix` 后面**，中间不放 `.` / `-` / `_` 任何分隔符 ——
   TaCZTweaks 按版本号字符串识别本项目，且 SemVer 里多一个 `-` 会把后续内容拽进
   prerelease 段、破坏「`>=1.1.8` 必须成立」这条枪包加载前提。
   该规矩此前只写在 `26.2(main)` 的 `gradle.properties` 注释里，本轮一并补进本线。
   - 同步位置（AGENTS.md §1）：README 顶部版本句 / 「已使用 R3-hotfix2 版本号」提示行 /
     支持环境表「R3-hotfix2 构建使用」与「本 mod」行 / SemVer 说明段，共 5 处；
     `fabric.mod.json` 的 `name` 与 `description`（description 里
     「based on upstream 1.1.8-hotfix」是上游 Forge 基线，未动）；
     `gradle.properties` 注释里三处版本号示例。
     依赖版本未变，故支持环境表的依赖格子与可选集成表不动；
     导航表指向分支 tree，也不动。
   - 顺带修掉一处遗留不一致：`01f7f45d`（R3 → R3-hotfix）只改了 `mod_version` 一行，
     `gradle.properties` 的注释仍停在 `R3`，本轮对齐到 `R3-hotfix2`。
   - *自检：`bash scripts/check_release_consistency.sh` → 通过 6 · 失败 0 · 警告 1
     （警告是 arena 会话分支名推不出 MC 系列，属预期）。脚本本体已支持
     `-hotfix<n>` 后缀，无需再改。*

### 已知未做（登记，不含未宣布的行为变化）

3. **Hold My Items 5.x 兼容旁路：本线不移植（沿用 1.21.11 线的判断）**
   1.21.11 线 `3164deb` 用「HEAD 接管 + cancel」抢在 HMI 的
   `com.holdmylua.source.mixin.safety.JavaMethodMixin` 改写返回值之前，把真实结果交回
   TaCZ 的 lua（HMI 会把没有它私有 `@Safe` 注解的方法返回值一律改成 `nil`，
   Knot 全 JVM 只加载一份 `org.luaj.vm2.lib.jse.JavaMethod`，于是
   `default_state_machine.lua:74` 的 `nil <= 0` 直接崩）。该修复已由维护者
   2026-09-12 在 1.21.11 实机通过。
   其 `docs/HOLD_MY_ITEMS_COMPAT.md` §6.1 明确判断 26.x **不预先移植**：探针健康时
   这套代码什么都不做（零收益），代价却是多一个打进库类的 mixin（不可静态验证的
   运行期面）+ 每条分支重跑 10 项矩阵。本线沿用该判断，**未移植**。
   - 触发条件（满足任一条再做，约 15 分钟）：① 26.x 玩家日志出现同一签名
     （TaCZ lua 报 `attempt to compare/index ... nil`、stdout 有
     `HMI Lua safety layer loaded!`、模组列表含 `holdmyitems`）；② 26.x 的 HMI
     移植版开始被普遍使用。
   - 届时本线的前提已核对：luaj 同为 `include` 的 Figura fork、mixin 配置同名、
     `MixinPlugin` 的 modid 规则一致（包名须保持
     `cn.sh1rocu.tacz.mixin.compat.holdmyitems`，注册进 `tacz.fabric.mixins.json`）、
     接管点是 `renderHandsWithItems` → `renderArmWithItem` 的 `@WrapOperation`
     （26.2 叫 `submitHandsWithItems` → `submitArmWithItem`），TACZ 自渲染时
     确实不调用 `original`。坑：26.x 的 HMI 只有第三方反编译移植，若它换了 modid，
     `MixinPlugin` 的包名第 6 段过滤会失效，需要另开 `compat.<新modid>` 包或改白名单。
4. **`RawOutput.log`（1.21.11 线 `ff9c327`，维护者网页上传）未同步**
   那是 1.21.11 的 intermediary 崩溃日志（`class_9909` / `method_61910` 一类名字），
   不是修复本体，映射也与本线（26.x 非混淆）不符；本线已有自己的 `latest.log`
   与 `build-reports/`（后者由 CI 回写）。

---

## 2026-09-07 · 其他枪包工作台 JEI 无配方 / 合成无结果（**根因已定：误删 RecipeCompat 兼容层**）

1. **恢复旧枪包原版配方兼容层 `RecipeCompat`（移植 26.2，本次移植时误删）**
   26.x 原版 `RecipeManager` 只扫单数 `data/<ns>/recipe/`（注册表常量）；
   1.20.1 / 1.21.1 时代枪包（如 KhanPowder、duyupack）把工作台物品等原版合成配方
   放在复数 `data/<ns>/recipes/`，另有 `loot_tables/`、`tags/blocks|items|...` 等
   复数目录与旧式配方 JSON（`result.item`/`result.nbt`/`{"tag":...}`/`{"item":...}`）。
   26.2 线在 PackResources 层有 `RecipeCompat` 兼容（复数→单数回退映射 + 旧 JSON
   自动转换，仅 `minecraft:*` 类型），本线移植时当作 26.2 专用件删掉 ⇒
   **旧布局枪包的工作台物品在原版合成台/JEI 无配方、合成不出**（维护者单人复现，
   日志证实数据管线本身正常：5 tables / 293 recipes 同步、JEI 二次注册成功）。
   现原样恢复 `RecipeCompat` 及 `DelegatingPackResources` / `PathPackResources`
   的 26.2 版接入。
   *证据：复现 latest.log（维护者上传）+ 26.2/1.21.11/26.1.2 三线原版源码对照 +
   26.1.2 符号逐一核验；本线=与 26.2 逐文件一致、编译门待过、**实机未验**。*

2. **枪包 PackType 按仓库动态设定（`CommonRegistry#onAddPackFinders`，移植 26.2 `810fb04f`）**
   与 26.2 行为对齐。经 26.1.2 原版源码核验在本线为行为无操作（`PackRepository`
   无类型过滤、元数据同源、解析按查询时 type），仅作一致性卫生项保留。
   *证据：26.1.2 原版源码逐类核验。*

2. **`RecipeViewerReloadBridge` 资源重载回退加一次性护栏（对齐 26.2 现版本）**
   同步后刷新 JEI/REI 时，若两个 viewer 的轻量刷新钩子都不可用，整段
   `reloadResourcePacks()` 回退此前没有次数限制，且 `clear()` 不复位
   `reloadInProgress`（断开时若回退仍在进行，重连后 tick 永不再进入）。
   现补齐 `resourceFallbackUsed` 一次性护栏与 `clear()` 完整复位。
   *证据：26.2 现版本对照；本线=同形移植、**实机未验**。*

---

## 2026-09-02 · 跨线同步轮（26.2 `01698440` + 1.21.11 `f53dd13b` → 本线）

### 修复

1. **跨包合成：`tacz:nbt` 自定义材料（来自 26.2 `61345c5`）**
   上游 TACZ 1.21.1+ 的新材料类型 `tacz:nbt`（`forge:nbt` / `forge:partial_nbt` 合并而来），
   本仓移植自 1.20.1 线、从未注册 ⇒ 被社区升级工具 TaCZPackUpgrader 改过的配方**整条解析失败**
   （材料格空白、无法合成；日志里是 `Unknown custom ingredient serializer` / `Not a json array`）。
   现注册 `TaczNbtIngredient`，并把归一化补齐三段：`items` 写成单个字符串时包成数组、
   旧式不带 `type` 的 `{item+nbt}` 改写为 `forge:partial_nbt`（此前 `nbt` 被静默丢弃、
   匹配退化成「任意一把同物品枪都行」）、失败日志带上规范化后的原文。
   *证据：机制与证据=26.2 实机日志 + 上游源码；本线=同形移植、编译门过、**实机未验**。*

2. **配置每次重启被重置（补三处，来自 1.21.11 `cd14a2ac`）**
   - `tacz-pre.toml`（`DefaultPackDebug`，面板 Other 那一格）此前**不在落盘表里** ⇒ 那个开关永远写不回去；
   - 取不到内存配置时**静默跳过**，与「FCAP 还没加载」在日志里长得一样 ⇒ 改为 WARN 点名；
   - 原来直接截断目标文件写，**这一刻掉电/被杀就留下空 TOML（全部配置回默认且不可恢复）**
     ⇒ 改为：文件存在则读入合并（保住注释与键顺序）、不存在才新建，两者都经临时文件 `ATOMIC_MOVE`。
   另外 `/tacz overwrite` 命令绕过面板保存流程，现在改完也显式落盘。
   *证据：三处断点=1.21.11 实机测出；本线=同形移植、**实机未验**。*

3. **配置落盘改走 FCAP 官方保存路径（来自 26.2 `7227ff9`，形状不同）**
   原先只能用 mixin accessor 掏 `childConfig` 自己写文件；现在先试 FCAP 自己的
   `ModConfig#getLoadedConfig()#save()`（写盘 + reloading 回调都是 FCAP 语义），
   不可达时退回原来的显式写回。本线 FCAP 26.1.5 的可见性**未经证实**，
   所以用反射探测：命中/未命中各打一条 `[TACZ] Config persist: …` INFO，实机一眼能看出走了哪条。
   配套：`ModConfigEvents` 回调提到所有 `ConfigRegistry.register` 之前
   （FCAP 在 register 里当场加载配置，晚挂会漏掉最先注册的 pre 那份）。
   *证据：机制=26.2 源码实读；本线反射分支=**未验**（由实机那条 INFO 回答）。*

4. **开镜时远处高模枪恒为立方体（来自 26.2 `0886909`）**
   两道 poly 距离闸门（`MeshMaxRenderDistance` / `MeshWorldFullDetailDistance`）按**裸眼**距离调参，
   而它们每帧只在提交时过一次、镜内那一遍复用同一批节点 ⇒ 4x 镜下 48 格上限观感只剩 12 格。
   现在两道闸门都乘上当前开镜倍率（`1+(Z-1)·aimProgress`），经典整屏变焦与 PIP 同样适用。
   *证据：机制=26.2 实机回报；本线=同形移植、**实机未验**。*

5. **光影下镜内那遍被拒时，内外一起变成 1X 且不自愈（来自 1.21.11 `837924b3`）**
   `worldZoomForcedToOne()` 原先只判「二次渲染开着且没失败」；窄遍被任何原因拒掉时
   （未开 opt-in / Iris 终局钩子不可用 / 隔离前提不满足），世界让位了、镜内又拒绝合成。
   现在加一条 `scopePassRunnable()` 判据：拒掉时退回重投影 / 整屏 FOV 变焦 —— 用户看到的是可用画面。
   *证据：1.21.11 实机；本线=同形移植、**实机未验**。*

6. **镜内裁手：第一人称手臂在目镜孔径内 discard（来自 26.2 `94179d4b`）**
   本线此前裁了枪身 / 配件 / 火光两层 / 镜内文字 / mesh GPU 枪身，**唯独没裁手臂** ——
   起因是 08-30 的同步指导把「裁手/裁字」一刀切判成掩码纪元专属，而本线后来自己把裁字做成了
   （等于翻案一半），裁手没人回头翻。本轮按深度孔径语义复刻：直接复用火光那条裁剪管线。
   注意**两种 PIP 下看不出差别**（合成是一次无深度附着的整片覆写，镜孔内一律盖掉），
   真正在意它的是**经典整屏变焦**路径（PIP 关 / 低倍率 / 光影下未 opt-in / PIP 失败回退 / 过渡帧）。
   详见 `docs/SCOPE_ARM_CLIP_26_1_2_2026_09_02.md`（含实机验收 6 条与未验项）。

   **低倍镜不裁（用户裁定，同批追加）**：闸门再加一条倍率下限
   （`ScopePipMinMagnification`，默认 4×）—— 低倍镜与组合镜的低倍档**不裁手、不裁火光**。
   手臂/火光那一刀本是给镜内放大画面让位，低倍镜阈值以下连 PIP 都不跑，
   挖出来的洞里是没放大的背景，观感就是破图。组合镜按当前档位判定（取 `IGun#getAimingZoom`）。
   **枪身 / 配件一并豁免**（用户追加裁定）：`clipForViewmodel` 换到同一闸门，
   mesh GPU 高模枪身批次（`PolyMeshGpuRenderer`）也加同一条倍率线。
   更正上一版说明里我自己的错误推断：镜片本体在 `AIM_CLIP_START` 之后就已经从可见 body
   移到 invisible depth writer（与倍率无关），所以枪身/配件这一刀与手、火光**同性质** ——
   都是给镜内画面让位，低倍镜没画面可让，裁掉的就是枪身自己。
   本线没有枪口烟这个渲染件，无此项可改。详见文档 §3.1。

### 调整

7. **每帧烘焙额度独立于缓存容量（来自 26.2 的下游审查 A6 采纳）**
   新增 `MeshGpuBakeBudgetPerFrame`（默认 4，1-64）+ 面板条目 + 中英说明。
   原先额度直接取 LRU 容量（`Math.max(4, cap)`）：为省显存把容量调到 1 的用户额度仍被顶到 4，
   而想调大额度的用户得白花显存撑 LRU。默认值与旧行为一致，属保守改动。

8. **镜内文字裁剪加一条 log-once 判据**（1.21.11 建议）
   没有它，「走了掩码」与「回退 vanilla」在屏幕上长得一模一样：
   提交成功时打一次 `[TACZ Scope] In-scope text is now clipped to the ocular aperture mask (N font page group(s)).`

### 工程/CI

9. `docs/ci/` 暂存区三件模板（全量 build + jar artifact 14 天、版本号守门 v2、compile-check v4），
   以及 `scripts/check_release_consistency.sh`（从 `26.2(main)` 镜像，两个 workflow 都要跑它）。
   **需要维护者在网页端上线**：Agent 凭据无 `workflow` 权限。本分支目前只有
   `.github/workflows/compile-check-2612.yml`（v3）是活的。
   本分支工作区跑一致性脚本：通过 6 / 失败 0。

10. `docs/lineage/` 目录制落地本线：家族级四份按副本引入，另建本线同步记录与账本。

### 已知未做（登记，不含未宣布的行为变化）

- 字体页缓存 × 资源重载的清空入口（1.21.11 §2-2）：接线点要挑、只能实机验，隐患已写进 `ScopeTextSubmitter` javadoc。
- 光影下 `MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders` 的默认值三方不一致
  （26.2 与本线默认**开**、1.21.11 默认**关**）：需要一次带光影环境的 A/B 才能关账，本沙箱给不出数据。


---

## PIP 二次渲染中视野内高模枪（手上的不算）不烘焙（2026-09-02，迁移自 01a05db2 / 1.21.11 `237dc153`）

用户回报：开着 `ScopePipRerender`（镜内二次渲染）时，视野里别人的/掉落的/展示台的高模
mesh 枪**在镜内**呈现未烘焙的立方体；关掉二次渲染或退镜后立刻恢复高模。有无光影都复现。
（用户用帧率变化反推「是否吃着烘焙」。）

- **根因**：`PolyMeshGpuRenderer.shouldSubmitGpuWorld()` 里有一条旧的防御性闸门
  `if (isInsideScopeLevelRender()) return false`（沿袭「提交每帧只发生一次」的假设）。
  26.1.2 的事实与此相反 —— `LevelRenderer#renderLevel` **每调用一次就重新提交一遍世界几何**：
  `extractLevel` 每帧只跑一次、产出的是「逐帧状态袋」（`LevelRenderState` 里的实体/方块实体/
  粒子…），真正写进 `SubmitNodeStorage` 的提交发生在**每一遍** render 阶段（镜内那遍跑完才会
  重跑 `extractLevel` 补状态 —— 见 `ScopePipRerender` 中段注释与 `0bf4c482`）。
  于是镜内那遍的提交被拒收 ⇒ 只能回 collector + 顶点预算，超过 `MeshWorldMaxVertices` 的
  高模枪被预算打成裸立方体；主画面那遍 `isInsideScopeLevelRender()` 已为 false，照常走 GPU
  烘焙 —— 正好是「镜内未烘焙、镜外/退镜正常」。
- **修法**（与 1.21.11 `237dc153` 同形，26.1.2 适配）：
  1. `shouldSubmitGpuWorld()` 移除镜内那遍的拒收；`worldSubmitBlocker()` 相应去掉这条原因。
  2. `renderAtWorldFlush()` 镜内那遍**画完也清表**（主遍会重新提交一份；若不清，主遍会把镜内
     旧条目再叠画一遍）。`worldConsumedFrame` 仍只在主画面那遍记录，避免主遍被镜内那遍的
     消费证明误挡（`worldConsumedFrame == frameId` 首消费守卫）。
  3. 新增 log-once：`[TacZMeshLoader] GPU world mesh pass active inside the scope PIP re-render
     pass; drawing N world entries submitted by this pass.`
- 两边机制**不完全一样**（迁移时的唯一实质差异）：1.21.11 的 `renderLevel` 每次调用自带提取；
  26.1.2 是「extractLevel 产状态袋 + 每一遍 render 阶段各自提交」。结论一致，理由不同 ——
  本文件按 26.1.2 的事实重写了那两处注释。
- 证据级别：**静态读码 + 1.21.11 的实机回报**；本线**未做实机验证**（沙箱无运行环境）。
- **残留风险（未验，未一并改）**：若某帧镜内那遍的世界 flush 钩子没跑到（例如
  `RenderSystem.outputColorTextureOverride != null` 这类早退），镜内那遍已登记的世界条目会
  留到主画面那遍、与主遍自己的条目叠画（重影/半透明加倍）。1.21.11 那版同样没有这道保险。
  若要补，做法是在 `renderScopeView` 入口记 `WORLD_DRAWS.size()` 标记、出口把新增部分截断
  （只截本遍新增的，不能整表清 —— 免得误伤提取期就登记的条目）。等你实机确认需不需要。

验收：开 `ScopePipRerender` 后镜内视野中远处高模枪仍是高模（不再是立方体），且日志出现上面
那条 info；关掉二次渲染/退镜后行为不变；有无光影各测一次。
