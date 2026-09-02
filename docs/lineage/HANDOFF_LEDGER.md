> **本文件是 26.2 线账本的副本 + 26.1.2 线本轮的追加行**（2026-09-02 同步轮）。
> 家族级 12 行（#1-#12）是 26.2 侧原文，未改写；本线这一轮的处置追加在文末
> 「26.1.2 线追加」段，行号 M-1…M-8。哪条线改了家族级结论由那条线改，本线只改自己的 M 行。

# 跨分支/跨仓 Handoff 登记账本（refab 侧副本）

> 用法见 `SYNC_GOVERNANCE_PROPOSAL.md` §4-§5。状态：
> `OPEN`（待认领）/ `CLAIMED(分支)`（进行中）/ `DONE(commit)` / `DECLINED(原因)`。
> 姊妹仓 TaCZ_Renovated 建议放一份镜像，两边各自更新自己认领的行。
> 首刊 2026-08-30，状态为当日实测回填。
>
> **2026-08-31 归档说明**：08-30 之前的旧 handoff/同步文档（原 `docs/handoff/`
> 四件套、`docs/` 根下的 08-12 / 08-22 移植清单）已移入本目录 `superseded/`。
> 旧件内容未必失效，但**是否还要做、做没做，一律以本账本为准**。

| # | handoff / 计划文档 | 方向 | 状态 | 备注 |
|---|---|---|---|---|
| 1 | renov `docs/handoff/HANDOFF_26_1_2_CATCHUP_20260830.md`（final-overlay 补课包 + 1678 行蓝本 diff + PORT_CONTRACT） | renov 26.2 → renov 26.1.2 | **DONE**(`caeb9e2` @ renov arena/01a051b1) | 分支已完成待实测/并入 26.1.2 |
| 2 | （同工作 refab 侧，无独立 handoff，直接开工） | refab 1.21.11 → refab 26.1.2 | **DONE**(`9a4e71f` @ refab arena/01a05170) | 附文档 `SCOPE_FINAL_OVERLAY_BACKPORT_26_1_2_2026_08_30.md`；待实测/并入。**08-31 增补**：该分支已再完成动画两连修（`19d22b8`+`a36ed59`）与 CI 补装（`034ac77`/`c0362ea`）——26.1.2 指导第一波全清 |
| 3 | renov `docs/handoff/HANDOFF_DEPTH_PIP_1_21_11_20260830.md`（1.21.11 深度版 PIP 设计） | renov 26.2 → renov 1.21.11 | **OPEN** | 维护者决定：renov 1.21.11 等 refab 1.21.11 定稿后一并同步 |
| 4 | renov `docs/handoff/HANDOFF_SCOPE_MASK_ORDER_INDEPENDENCE.md` + `scope-mask-order-independence.patch` | renov 26.2 → refab 26.2 | **需核对** | 疑似已被 refab `26.2(main)` 的 `investigations/SCOPE_MASK_ORDER_INDEPENDENCE_2026_08_28.md` 一轮覆盖；下次触碰掩码代码时核对后改 DONE/DECLINED |
| 5 | refab `docs/SCOPE_PIP_RERENDER_1211_PORT_PLAN_20260830.md`（rerender 跨纪元移植计划） | refab 26.2(main) → refab 1.21.11 | **DECLINED**（2026-08-30 维护者裁定） | Iris 兼容需拆现有渲染逻辑重写离屏渲染，成本不可接受 → 不定期暂停。收尾动作见 `SYNC_GUIDE_REFAB_1211_20260830.md` §0 |
| 6 | refab `docs/lineage/superseded/HANDOFF_26_2_AUDIT_TO_26_1_2_2026_08_12.md` 等两份 08-12 存量 | refab 26.2 → refab 26.1.2 | **需核对** | 08-12 的老 handoff，26.1.2 后续有 R2 发版，可能已消化；清账时核对 |
| 7 | refab `docs/lineage/SYNC_GUIDE_REFAB_1211_20260830.md`（1.21.11 暂停收尾 + 可同步项） | refab 26.2 → refab 1.21.11 | **DONE**(1211 R3 定稿 @ `ab11a84`) | 1211 已实质完成收尾并反超：第 0-3 步全实机 PASS、发布自己的审查与同步文档。后续以 1211 自己的文档为准 |
| 8 | refab `docs/lineage/SYNC_GUIDE_REFAB_2612_20260830.md`（26.1.2 双向补课路线） | refab 26.2 + refab 1.21.11 → refab 26.1.2 | **CLAIMED(01a05170)** 第一波 DONE | CI/动画修/final-overlay 全清（08-31 核实）；下一步=接收 TML 整线，货源文档见行 #10 |
| 9 | refab `docs/lineage/SYNC_GUIDE_RENOV_262_20260830.md`（renov 主线 04ea3 补差清单） | refab 26.2 → renov 26.2(04ea3) | **OPEN** | 动画修最优先；PIP 终态对表（防 PR#82 泄漏 bug 潜伏）；裁手/裁字；meshloader 二段式 |
| 10 | 1211 侧 `docs/lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md`（TML 整线 1211→2612） | refab 1.21.11 → refab 26.1.2 | **OPEN** | 26.1.2 接收 TML 的唯一货源（同纪元）；前置已全清可立刻开工。26.2 侧补拿三件（LRU/延迟释放/额度）以两仓 R3 终态为准 |
| 11 | 1211 侧 `docs/REVIEW_UPSTREAM_TML_GPU_262_20260831.md`（对 26.2 GPU 层的 10 条审查） | refab 1.21.11 → refab 26.2(main) | **DONE**(`bb6fcb6` @ 01a04e96) | 7 采纳 2 驳回（A4/A9 以 Iris 26.2 源码证据）1 条其审查基线过时；处置表 `MESH_LOADER.md` §5.2-ter；绕序/RealSky 默认关随 1211 实机结论定稿 |
| 12 | 本仓 `docs/lineage/SYNC_ROUNDUP_R3_20260831.md`（R3 定稿轮全家族总纲） | 全家族 | **现行** | 四线进度底账 + 移植主次 + 工作流同步清单 + 旧指导时效标注；R3 后同步以此为索引 |


---

## 26.1.2 线追加（2026-09-02 同步轮，取货自 26.2 `01698440` 与 1.21.11 `f53dd13b`）

方向列写法：`A → B` 表示货从 A 流到 B。状态：`已落地待实机` / `登记未做` / `不适用（理由）`。

| # | 项 | 方向 | 状态 | 备注 |
|---|---|---|---|---|
| M-1 | `tacz:nbt` 自定义材料（上游 1.21.1+）：新增 `TaczNbtIngredient` + 注册 + 归一化三段（`items` 字符串→数组、无 type 的 `{item+nbt}`→`forge:partial_nbt`、`catch LinkageError` + 规范化形态日志） | 26.2 `61345c5`/`7227ff9` → 26.1.2 | **已落地待实机**（`f8f5ed9c`） | 1.21.11 线同样没有这一件（已核实其 `util/forge` 下只有 Partial/Strict）⇒ 本线这一份是现成的第三份货，回话时一并给 |
| M-2 | FCAP 落盘桥：`ModConfig#getLoadedConfig()#save()` | 26.2 `7227ff9` → 26.1.2 | **已落地待实机**（形状不同） | v26.1.5 的可见性未证实 ⇒ 反射探测，命中走 FCAP 官方路径、未命中退回 Accessor + 合并原子写；两条各打一条 INFO，实机用日志确认走了哪条 |
| M-3 | `ModConfigEvents` 回调必须挂在所有 `ConfigRegistry.register` 之前 | 26.2 `7227ff9` → 26.1.2 | **已落地待实机** | pre 那份最先注册，晚挂正好漏掉它 |
| M-4 | 配置落盘三处补齐：pre 那份纳入落盘表 / 不再静默跳过 / 合并写 + `ATOMIC_MOVE` | 1.21.11 `cd14a2ac` → 26.1.2 | **已落地待实机** | 原实现 `newBufferedWriter` 先截断目标文件，掉电即毁配置；`OverwriteCommand` 也补了显式落盘 |
| M-5 | `worldZoomForcedToOne()` 加 `scopePassRunnable()` 判据 | 1.21.11 `837924b3` → 26.1.2 | **已落地待实机** | 1.21.11 原注明「26.1.2 无此项」；本线判据用 `shaderRerenderAllowed()`（本线无 `shaderIsolateSafe`） |
| M-6 | meshloader 两道距离闸门乘开镜倍率 | 26.2 `0886909` → 26.1.2 | **已落地待实机** | 倍率源是本线自己的 `ScopePipRenderState#currentDetailZoom()`（本线无 `ScopePipRenderer`）；失败兜底 1.0，scope 线不连坐 mesh |
| M-7 | 每帧烘焙额度与 LRU 容量解耦（独立键 `MeshGpuBakeBudgetPerFrame`，默认 4） | 26.2（下游审查 A6 采纳）→ 26.1.2 | **已落地待实机** | 本线原为 `Math.max(4, cap)`；新默认 4 与旧行为一致，属保守改动；已同步 Cloth 条目与中英语言键 |
| M-8 | 字体页缓存 × 资源重载的清空入口 | 1.21.11 `SYNC_CHECKLIST` §2-2 → 26.1.2 | **登记未做** | 接线点要挑、又只能靠实机验证；隐患已写进 `ScopeTextSubmitter` javadoc，不在本轮做一个未验的运行期改动 |
| M-9 | 镜内裁手：第一人称手臂在目镜孔径内 discard | 26.2 `94179d4b` → 26.1.2（深度孔径复刻） | **已落地待实机** | 直接复用本线火光裁剪管线 `flashTranslucentClipped`（`entityTranslucent` + `MASK_OUTSIDE`，`affectsCrumbling/sortOnUpload` 本就齐备、已登记 Iris `HAND_TRANSLUCENT`）；手臂 RenderType 由 `AvatarRenderer` 内部选定 ⇒ 用 `SubmitNodeCollector` 动态代理原地替换。详见 `docs/SCOPE_ARM_CLIP_26_1_2_2026_09_02.md`：两种 PIP 下合成会覆盖手臂（看不出差别），真缺口在经典整屏变焦路径。**追加（用户裁定）：低倍镜不裁** —— 闸门再加倍率下限
          （`ScopePipMinMagnification` 默认 4×），组合镜按当前档位；**枪身/配件一并豁免**（`clipForViewmodel` + mesh GPU 批次）：镜片本体在 AIM_CLIP_START
          后已移出可见 body，这一刀与手/火光同性质 = 给镜内画面让位，非挖透镜片 |
| M-10 | PIP 二次渲染中视野内高模枪（手上的不算）不烘焙 | 1.21.11 线 `237dc153`（分支 `arena/01a05db2`）→ 26.1.2 | **已落地待实机** |
  `shouldSubmitGpuWorld()` 移除 `isInsideScopeLevelRender()` 拒收 + `renderAtWorldFlush()` 镜内那遍画完也清表
  （`worldConsumedFrame` 仍只记主遍）+ log-once。**机制与 1.21.11 不同**：那边是 `renderLevel` 每次调用自带提取，
  这边是「`extractLevel` 产状态袋 + 每一遍 render 阶段各自提交」；结论同、理由不同，注释已按 26.1.2 重写 |
| M-11 | 收枪 put-away 动画恢复：`doPutAway` 补 `keep()`（唯一现行调用点）+ `keep()` 守卫改「最新一次收枪接管」+ isInitialized 判定暴露给调用点（`hasInitializedStateMachine`） | 26.2 线 PR #87（`arena/01a061a4`，语义移植 fork `ca2b9fc` + 维护者裁定两点加固；补丁 `docs/patch/2026-09-02-putaway-keep-render-26.1.2.patch`）→ 26.1.2 | **已落地待实机**（`6a4c21c2`） | 三线同因：tryExit 里的 keep 行是继承自上游的注释 ⇒ 没人调 keep ⇒ 旧枪视模一帧不再提交、put_away 被吞。**行为扩大**：内置 LRTactical 三族（Melee / Throwable / Consumable）一并获得 keep 窗口（都不 override tryExit）。完整论证 / 三线核对表 / 实机清单见 26.2 线 `docs/lineage/SYNC_GUIDE_PUTAWAY_KEEP_20260902.md`；1.21.11 线有对应补丁（`-1.21.11.patch`），待该线会话落地。本线 mixin 侧只改 `@Unique` 方法体，refmap / intermediary 无涉 |
| M-12 | 六线 CI 盘点 → 本线上线：三件暂存稿核对（与 26.2 线逐字一致、仅头部注释差异）+ 本线 `docs/ci/README.md` 刷新 + `AGENTS.md` §1 模板路径修正（`docs/publish/ci/` → `docs/ci/`）+ 家族级 `INSTALL_MATRIX_20260902.md` 按副本引入 + **mesh parity 红线修正**（M-7 面板条目的语言键名未跟随 toml 键蛇形，`mesh_gpu_bake_budget`→`mesh_gpu_bake_budget_per_frame`，6 处改名：Java 2 处 + en_us 2 处 + zh_cn 2 处，显示文本不变） | 26.2 线 PR #87 `fcd3b4a`（`docs/ci/INSTALL_MATRIX_20260902.md` §B1）→ 26.1.2 | **已上线**（维护者 2026-09-02 23:07–23:12 网页端四步全做：`3ac189a5` build / `6016d708` consistency / `1519ae59` compile-check v4 + 旧件 rename 删除，逐字 = `556dfea2` 时点的暂存稿；上线后 CI 即绿 `d1b8f2b0`） | 上线后本线即有 jar 产物（此前六线里仅 refab 26.2 有）；version↔README 守门生效。**两条尾巴**：① 现役 compile-check 是并入前稿 ⇒ 三条静态检查（mixin 注册性 / lang 超集+字面量 / mesh parity）**未进 CI**，可选跟进 = `docs/publish/ci/CHECKS_TO_APPEND_20260901.md` 三 step 追加；mesh parity 的前置红线已由 `ca083b5d` 修掉（不修则追加后首跑必红；26.2 线矩阵的预演清单没含 mesh parity 这项，其「首跑预期绿」在三检查状态下依赖本次改名）。② PR 上下文的 check run 呈 `action_required`（仓库对 PR run 设批准门槛），push run 自动且绿。**build 首跑（产 jar）待观察**：本线第一个全量构建 run 在本会话 rebase 推送触发，产物见该 run Artifacts（14 天保留） |

### 本线判定为不适用 / 观望的（不占 M 号，理由见 `SYNC_ROUNDUP_2612_20260902.md` §2.2-§2.3）

- **不适用**：26.2 的掩码纪元一族（`ScopePipRenderer` / `ScopeMask*` / `ScopeBodyRenderTypes` / `scope_text.*`）、**「家族不适用 ≠ 语义不适用」：其中的『镜内裁手』本轮已复刻，见 M-9**）、
  `prepare()` 世代的三个 mixin、`RenderConfig` 里 26.2 独有的 29 个键、`99b15b2`（方向反了：26.2 从本线搬的）。
- **观望**：`IlluminatedLights`（与本线 `MeshPolyIlluminatedRealSky` 是否同一机制的两种形态未核）；
  R3 配置默认值定稿（本线与 26.2 都是默认开，1.21.11 是默认关并要实测数据 —— 三方唯一还开着的判定分歧，本沙箱给不出数据）。
