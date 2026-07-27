# 历史过程文档归档

这里是移植过程中逐轮产生的 38 份工作记录。

## ⚠️ 阅读前必读

**不要把这些文档当作当前事实的依据。** 原因：

1. **部分核心结论已被后续验证推翻。** 受影响的文件顶部通常带有作废声明
   （例如 `SCOPE_PIP_PLAN.md` 开头的「⛔ 本文核心前提已被推翻」）。
2. **早期文档之间存在互相矛盾。** 它们是不同阶段的认知快照，
   而认知在过程中被多次修正。
3. 部分文档描述的是**当时的计划**，而非最终实现。

**当前事实以这两处为准：**

- `docs/PORTING_NOTES.md` —— 经验总结，只收录已验证结论
- **代码里的注释** —— 关键决策点都写了「为什么这么做 / 为什么不能那样做」

## 为什么还留着

「当初为什么走了弯路」本身有参考价值，尤其是几次方向性错误的**证伪过程**：

- **PIP 方案**（`SCOPE_PIP_PLAN.md` → `SCOPE_PIP_FINDINGS_2026-07-26.md`）
  怎么被逐行 grep 上游源码推翻
- **stencil 替代方案**（`SCOPE_STENCIL_DEBT.md`）为什么会演变成「叠叠乐式 bug」，
  以及最终怎么按上游的分类维度重构掉
- `SCOPE_UPSTREAM_TRUTH_2026-07-27.md` / `SCOPE_UPSTREAM_MECHANISM_2026-07-26.md`
  记录了对上游瞄具机制的逐行精读，可信度相对较高

如果你要做类似的跨版本渲染层移植，这些「错误路径」比结论更有参考意义。

## 文档分类

| 类别 | 文件 |
|---|---|
| 交接/总览 | `HANDOVER.md`、`STAGE1_COMPLETION_REPORT.md` |
| 逐轮进度 | `PROGRESS_ROUND1.md` ~ `PROGRESS_ROUND19.md` |
| 审计 | `AUDIT_REPORT_2026-07-25.md`、`DOC_AUDIT_2026-07-26.md`、`MIXIN_AUDIT_2026-07-27.md`、`SCOPE_CODE_AUDIT_2026-07-27.md` |
| 瞄具机制研究 | `SCOPE_UPSTREAM_MECHANISM_2026-07-26.md`、`SCOPE_UPSTREAM_TRUTH_2026-07-27.md`、`SCOPE_RETICLE_DESIGN_2026-07-26.md` |
| 瞄具方案（含已作废） | `SCOPE_PIP_PLAN.md`、`SCOPE_PIP_FINDINGS_2026-07-26.md`、`SCOPE_IMPL_PLAN_2026-07-26.md`、`SCOPE_MASK_PLAN_2026-07-27.md` |
| 复盘 | `SCOPE_STAGE2_POSTMORTEM_2026-07-27.md`、`SCOPE_STEP2_POSTMORTEM_2026-07-27.md`、`SCOPE_STENCIL_DEBT.md` |
| 早期修复记录 | `CHANGES_ROUND1-2.md`、`FIX_LOG_STAGE1.md`、`FIX_SUMMARY_QUICK.md` |
