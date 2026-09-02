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
| 13 | 用户回报「二次渲染时视野内高模枪在镜内不烘焙」（1211 `237dc153` / 2612 `db360639` 已修） | refab 1.21.11 + refab 26.1.2 → refab 26.2(main) | **DONE**（本线，待实测） | 09-01 首次裁定「不适用」被**本仓哨兵日志在用户实机 latest.log 打印**推翻：26.2 的 extract 只产提交节点，「画节点」那一步每遍 render 各跑一次 ⇒ 镜内那遍确实重新提交，与姊妹线同因。已删镜内拒收 + 镜内画完即清表（两遍各自提交/消费）。证据表与错判记录见 `MESH_LOADER.md` §5.2-bis 第 13 项 |
| 14 | 本仓 `docs/lineage/SYNC_GUIDE_PUTAWAY_KEEP_20260902.md`（收枪动画 `keep()` 修复 + 两点加固的三线移植） | 外部 fork `ca2b9fc` → refab 26.2 / 26.1.2 / 1.21.11 | **26.2 已落码（CI 编译+全量构建通过 `32af402`，实机待测）；26.1.2 + 1.21.11 = OPEN** | 无共同 git 祖先，不能 cherry-pick；`LocalPlayerDraw.java` 三线同 blob `79887cf`，机制（`KeepingItemRenderer`/`getMainRenderStack`/mixin `keep()`/`needReInit` 区域）三线逐字同构 ⇒ 属「同代码同机制」移植。**范围 = `ca2b9fc` 5 行 + 两点加固**（`keep()` 守卫改为「最新一次收枪接管」、调用点加 `hasInitializedStateMachine` 判定以对齐上游 `isInitialized()` 语义），加固改既有语义故三线必须同步。两份 4 文件补丁 `docs/patch/2026-09-02-putaway-keep-render-{26.1.2,1.21.11}.patch` 已在**目标分支真实 worktree** 上 `git apply --check` + 实落通过，落完三线逻辑逐字相同（仅注释里 MC 版本号一词之差）；1.21.11 不新增 mixin 目标/`@Shadow`，refmap 无涉。**实测项见指导 §5（开镜消费者分属两纪元；加固 1/2 各有专项）** |
| 15 | 本仓 `docs/ci/INSTALL_MATRIX_20260902.md` + `docs/ci/pending/`（六线 CI 上线总清单与代拟件） | refab 26.2 → refab 26.1.2 / refab 1.21.11 / renov 三线 | **OPEN（全部需维护者网页端上线，Agent 无 `workflows` 权限）** | 盘点结论：产 jar 的 `build.yml` **六线里只有 refab 26.2 装了**；compile-check 的 v4 升级四条线没跟上（合并 commit 从没被编过）；refab 26.1.2 / 1.21.11 的三件已躺在各自 `docs/ci/` 只差粘贴（26.1.2 必须**同时删掉** `compile-check-2612.yml`，否则两个 `name: compile-check` 并存双跑）；renov 三线毫无准备 ⇒ 本轮代拟 6 个 YAML（NeoForge/ModDevGradle 版：注册性检查改读 `src/main/templates/META-INF/neoforge.mods.toml`、Java 25/25/21、1.21.11 的 `libs/*.jar` 已提交故无需下载步骤）+ refab 1.21.11 的 `verify_mixin_targets.py` **可移植化补丁**（`/usr/lib/jvm/...`、`/home/user/.gradle` 两条沙箱硬编码 → `JAVA_HOME`/`GRADLE_USER_HOME`，不打就不能挂 CI）与两条运行期校验的追加片段。静态校验已对六条线真实文件预演（refab 26.2 查出 1 条孤儿 mixin 配置待裁定；其余全绿），代拟件过了 `yaml.safe_load` + 内嵌 python `compile()` + 正反功能实测。**真实 Actions 首跑未验证**；renov `gradlew build` 在 `-Xmx512M` 下的余量是最大未知数 |
