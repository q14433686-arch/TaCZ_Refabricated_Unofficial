# 跨分支/跨仓 Handoff 登记账本（refab 侧副本）

> 用法见 `SYNC_GOVERNANCE_PROPOSAL.md` §4-§5。状态：
> `OPEN`（待认领）/ `CLAIMED(分支)`（进行中）/ `DONE(commit)` / `DECLINED(原因)`。
> 姊妹仓 TaCZ_Renovated 建议放一份镜像，两边各自更新自己认领的行。
> 首刊 2026-08-30，状态为当日实测回填。

| # | handoff / 计划文档 | 方向 | 状态 | 备注 |
|---|---|---|---|---|
| 1 | renov `docs/handoff/HANDOFF_26_1_2_CATCHUP_20260830.md`（final-overlay 补课包 + 1678 行蓝本 diff + PORT_CONTRACT） | renov 26.2 → renov 26.1.2 | **DONE**(`caeb9e2` @ renov arena/01a051b1) | 分支已完成待实测/并入 26.1.2 |
| 2 | （同工作 refab 侧，无独立 handoff，直接开工） | refab 1.21.11 → refab 26.1.2 | **DONE**(`9a4e71f` @ refab arena/01a05170) | 附文档 `SCOPE_FINAL_OVERLAY_BACKPORT_26_1_2_2026_08_30.md`；待实测/并入 |
| 3 | renov `docs/handoff/HANDOFF_DEPTH_PIP_1_21_11_20260830.md`（1.21.11 深度版 PIP 设计） | renov 26.2 → renov 1.21.11 | **OPEN** | 维护者决定：renov 1.21.11 等 refab 1.21.11 定稿后一并同步 |
| 4 | renov `docs/handoff/HANDOFF_SCOPE_MASK_ORDER_INDEPENDENCE.md` + `scope-mask-order-independence.patch` | renov 26.2 → refab 26.2 | **需核对** | 疑似已被 refab `26.2(main)` 的 `SCOPE_MASK_ORDER_INDEPENDENCE_2026_08_28.md` 一轮覆盖；下次触碰掩码代码时核对后改 DONE/DECLINED |
| 5 | refab `docs/SCOPE_PIP_RERENDER_1211_PORT_PLAN_20260830.md`（rerender 跨纪元移植计划） | refab 26.2(main) → refab 1.21.11 | **CLAIMED**(arena/01a052b2) | TEMP 字节码诊断第 7 轮；是否继续受治理提案 §1 裁决影响 |
| 6 | refab `docs/HANDOFF_26_2_AUDIT_TO_26_1_2_2026_08_12.md` 等两份 08-12 存量 | refab 26.2 → refab 26.1.2 | **需核对** | 08-12 的老 handoff，26.1.2 后续有 R2 发版，可能已消化；清账时核对 |
