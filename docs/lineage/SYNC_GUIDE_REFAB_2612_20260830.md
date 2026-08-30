# 同步指导：refab 26.1.2（双向汇合点，进度最低）（2026-08-30）

> 维护者裁定：26.1.2 是**双向同步**——PIP（深度孔径 Step-3）从 1.21.11 来，
> 其他改动从 26.2 侧来。它同时是全家族进度最低的分支：
> scope 目录只有 9 个文件（1.21.11 有 11，052b2 有 13），无任何 PIP。
> 本文按依赖顺序排好三波工作，每波给机制、锚点与已核实的差异点。

## 0. 现状底账（全部实测）

- 尖端 `e7db2c2`（08-27，R2-hotfix2），与 1.21.11 共同祖先 `6c409ee`，
  之后两边各自开花：26.1.2 独有 22 提交、1.21.11 独有 47 提交——
  但**大宗内容是同一批工作在两边的分别实现**（LR 0.4.3、CarryOn、
  recipe-viewers、getName common 化、耳鸣资源……主题逐条对得上）。
  真正的结构性差距只在渲染：见下。
- 渲染 API 同代：26.1.2 也是 `SubmitNodeCollector` 世代（13 个文件引用，已核实），
  与 1.21.11（25 处引用）**同纪元同架构**——这就是为什么它是 1.21.11 的
  天然下游，而不是 26.2 的。
- `arena/01a05170` 已完成 final-overlay 回搬（151 行文档 + 15 文件，08-30），
  待实测并入。**并入后** `ScopeDepthCopyState` 与 1.21.11 逐字归零
  （已 diff 核实），`BedrockAttachmentModel` 剩 41 行差异（1.21.11 侧
  Step1-3 期间的 PIP 配套改动——正是第 2 波要搬的东西的一部分）。
- 无 `compile-check.yml`——第 0 件事就是补上（052b2 的那份可直接抄，
  它已在 1.21.11 工具链上跑通）。

## 1. 第一波：把地基砌平（依赖顺序不可换）

1. **补 CI**：从 `arena/01a052b2` 抄 `.github/workflows/compile-check.yml`。
   没有它后面每一步都在盲飞。
2. **实测并合并 `arena/01a05170`**（final-overlay 延后重画）。
   这是后续 PIP 移植的**前置**：052b2 的 Step-3 改动叠在 1.21.11 的
   final-overlay 之上（`ScopeFinalOverlayState` 在 052b2 的改动文件清单里），
   26.1.2 没有这层就接不住。验证矩阵在分支自带的
   `SCOPE_FINAL_OVERLAY_BACKPORT_26_1_2_2026_08_30.md` 里。
   姊妹仓同工作（renov 051b1）可对表但别抄代码——他们是 NeoForge。
3. **检视动画两连修**（26.2 侧 `4aa8d7b`+`12d6f3c`）：26.1.2 的
   `stopAnimation` 基线与 26.2 逐字相同、`getTransitionTo` 存在（均已核实），
   纯平移。这条与 PIP 无依赖，可与 2 并行。

## 2. 第二波：从 1.21.11 同步深度孔径 PIP（Step-3 重投影版）

**取货点**：等 052b2 按其同步指导（`SYNC_GUIDE_REFAB_1211_20260830.md` §0）
收尾并入 `1.21.11` 长期分支后，从 merge commit 取——**不要**现在就从活分支拿，
052b2 还欠一轮 TEMP 剥离，现在拿会把诊断代码一起搬走。

**货物清单**（052b2 相对 1.21.11 的实测差集，剔除 TEMP 后）：

| 组件 | 内容 | 26.1.2 侧接口现状（已核实） |
|---|---|---|
| `ScopePipRenderState.java`（~909 行，剔 TEMP 前） | Step-3 主体：屏幕空间重投影 + 合成 | 新文件，无冲突 |
| `ScopePipDepthDebug.java` | 品红诊断层（保留，实测利器） | 新文件 |
| `ScopePipRerender.java` + 相关配置 | **按 1.21.11 的暂停裁定处置**：若 052b2 收尾选了「保留但锁默认」，照搬；选了 revert 就没这项 | 新文件 |
| `scope_pip.fsh` / `scope_pip_debug.*` | 片元着色器 | 新文件 |
| `BedrockAttachmentModel` 增量 | 镜片几何捕获点 | 有 41 行既有差异（第一波并入 05170 后测得），**必须手工合，不能整文件覆盖** |
| `GameRendererMixin` / `CameraSetupEvent` 增量 | 抓帧与 FOV 门禁注入点 | CameraSetupEvent 两分支基线 0 差异（已核实），直接套 diff；GameRendererMixin 有既有差异，手工合 |
| `RenderConfig` + `RenderClothConfig` + lang | 8-10 项 `SCOPE_PIP_*` 配置 | 三文件基线 0 差异（已核实），直接套 diff |
| `IrisFinalScopeOverlayMixin` 增量 | final-overlay 与 PIP 的时序衔接 | 第一波并入后才存在，注意以 05170 的版本为底 |

**三个已知雷区**（052b2 用实机迭代踩出来的，别再踩一遍）：
1. FOV 门禁必须用**当前 tick 的插值 aiming progress**，否则退镜 POV 跳变
   （052b2 的 `44c634d`/`0bb077c`/`4971374` 三连修，搬的时候确认拿的是终版）;
2. 抓帧源与合成层次（`de076d2`/`b1f34b0`：「镜内外都 1x」与图层错序的修复）;
3. 光影包默认拒绝（`SCOPE_PIP_ALLOW_SHADER_PACKS` 默认 false）——
   深度孔径 PIP 在 Iris 下同样没有可靠的深度语义，这正是 1.21.11 暂停 rerender
   的同一堵墙，26.1.2 不要幻想绕过。

**验证**：052b2 的三份 `SCOPE_PIP_DEPTH_1211_STEP{1,2,3}_20260830.md` 就是
现成的验收剧本，逐份重放即可。

## 3. 第三波：26.2 侧的其余适用项（可与第二波并行）

- **适用**：检视动画修复（已列入第一波 3）。
- **按需**：meshloader collector 安全子集——同 1.21.11 指导 §1.2 的结论：
  接口面在（26.1.2 的 `BedrockAttachmentModel` 有 5 处 `submitCustomGeometry`），
  GPU 层不可搬（`BindGroupLayouts` 引用数 0），默认不做，有枪包需求再议。
- **不适用**：镜内裁手/裁字（掩码纪元专属）、PIP 倍率闸门（随第二波 PIP 一起来，
  052b2 的 `SCOPE_PIP_MIN_MAGNIFICATION` 已含）、遮光环重画（第一波已含更完整版）、
  PR#82 帧率修复（rerender 专属）。

## 4. 汇总：26.1.2 的完整补课路线

```
第0步  补 compile-check.yml            ← 抄 052b2
第1步  实测+并入 05170 (final-overlay)  ← 已完成待验，最快的一步
第2步  动画两连修                       ← 26.2 侧平移，半天
第3步  等 052b2 收尾并入 1.21.11        ← 依赖外部
第4步  深度孔径 PIP 整包搬运             ← 本文 §2，最大的一块
第5步  更新 HANDOFF_LEDGER              ← DONE 回执
```

到第 5 步做完，26.1.2 与 1.21.11 的渲染面差距归零（双方都停在 Step-3 + 
final-overlay + 各自的 rerender 处置态），此后两分支可以互为镜像同步，
不再有「进度最低」一说。
