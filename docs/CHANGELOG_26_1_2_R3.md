# 26.1.2 线变更记录（R3 周期）

> 口径：只记**对玩家/维护者可见**的行为变化，每条标证据级别（AGENTS §2）。
> 本分支此前没有 changelog 文件（只有 `UPDATE_REPORT_26_1_2_R1/R2` 的发版报告），
> 本文件从 2026-09-02 这轮跨线同步开始记，格式对齐 `docs/lineage/` 的同步体例。
> 同步过程与处置表：`docs/lineage/SYNC_ROUNDUP_2612_20260902.md`；
> 账本：`docs/lineage/HANDOFF_LEDGER.md` 的 M-1…M-8 行。

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

### 调整

6. **每帧烘焙额度独立于缓存容量（来自 26.2 的下游审查 A6 采纳）**
   新增 `MeshGpuBakeBudgetPerFrame`（默认 4，1-64）+ 面板条目 + 中英说明。
   原先额度直接取 LRU 容量（`Math.max(4, cap)`）：为省显存把容量调到 1 的用户额度仍被顶到 4，
   而想调大额度的用户得白花显存撑 LRU。默认值与旧行为一致，属保守改动。

7. **镜内文字裁剪加一条 log-once 判据**（1.21.11 建议）
   没有它，「走了掩码」与「回退 vanilla」在屏幕上长得一模一样：
   提交成功时打一次 `[TACZ Scope] In-scope text is now clipped to the ocular aperture mask (N font page group(s)).`

### 工程/CI

8. `docs/ci/` 暂存区三件模板（全量 build + jar artifact 14 天、版本号守门 v2、compile-check v4），
   以及 `scripts/check_release_consistency.sh`（从 `26.2(main)` 镜像，两个 workflow 都要跑它）。
   **需要维护者在网页端上线**：Agent 凭据无 `workflow` 权限。本分支目前只有
   `.github/workflows/compile-check-2612.yml`（v3）是活的。
   本分支工作区跑一致性脚本：通过 6 / 失败 0。

9. `docs/lineage/` 目录制落地本线：家族级四份按副本引入，另建本线同步记录与账本。

### 已知未做（登记，不含未宣布的行为变化）

- 字体页缓存 × 资源重载的清空入口（1.21.11 §2-2）：接线点要挑、只能实机验，隐患已写进 `ScopeTextSubmitter` javadoc。
- 光影下 `MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders` 的默认值三方不一致
  （26.2 与本线默认**开**、1.21.11 默认**关**）：需要一次带光影环境的 A/B 才能关账，本沙箱给不出数据。
