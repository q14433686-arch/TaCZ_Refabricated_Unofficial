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
| 13 | 用户回报「二次渲染时视野内高模枪在镜内不烘焙」（1211 `237dc153` / 2612 `db360639` 已按「每遍各自提交」修好） | refab 1.21.11 + refab 26.1.2 → refab 26.2(main) | **DECLINED**（行为改动）+ **DONE**（观测点，本线） | 26.2 的世界提交只在 extract 阶段发生一次、镜内那遍复用同一批提交节点（六条证据见 `MESH_LOADER.md` §5.2-bis 第 13 项）⇒ 姊妹线那条镜内闸门在本线不可达，删它无收益；照抄「镜内画完清表」会让主画面那遍拿到空表 = 开镜时镜外世界 mesh 枪整层消失。只移植 log-once 观测点（镜内首次画上世界表 / 镜内提交被拒），并把自定义 pass 首画日志改报真实表名。本线实机未验 |
