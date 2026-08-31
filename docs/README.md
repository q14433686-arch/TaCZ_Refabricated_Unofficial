# docs/ 索引 —— 本分支（refab 1.21.11）每份文档是干什么的

> 首刊 2026-08-31（R3），对齐 26.2 侧同日那轮文档清理的**判据**：
> 「这份文档描述的是『现在』还是『当时』？」—— 描述现在 → 现行参考；
> 描述当时（取证、审计、交接）→ 归档/调查类。
>
> **本分支刻意没有做 26.2 那样的目录迁移**（`investigations/`、`archive/`、`patch/` 等，见他们那边的 `docs/README.md`）。
> 原因只有一条：跨分支的账本与指导文档是按**根路径**互相引用的
> （例如 26.2 分支上的 `docs/lineage/SYNC_GUIDE_REFAB_1211_20260830.md` 直接写 `docs/SCOPE_PIP_RERENDER_…`；那份文档只存在于 `arena/01a04e96`，本分支没有副本，读它用 `gh api ...?ref=arena/01a04e96-…`），
> 搬动会让别的分支的引用全部变幽灵。所以约定：**新文档进 `lineage/`，存量文件不动**。
> 若维护者决定统一目录，那是一次独立提交（连同所有分支的引用一起改）。

## 1. 现行参考（描述当前代码状态，滞后了就要改）

| 文件 | 内容 |
|---|---|
| `MESH_LOADER.md` | 内置 TML（mesh 高模加载 + GPU 静态烘焙第 0/1/2/3 步）的当前状态、14 项配置、边界、验证清单 |
| `CHANGELOG_1_21_11.md` | 本分支相对 26.1.2 的变更记录，按交付轮次倒序；**R3 段在顶部追加** |
| `AMMO_SOURCE_API.md` | 下游模组替换实体弹药源的公共 API |
| `CARRYON_COMPAT.md` | Carry On 工作台兼容 |
| `verify_mixin_targets.py` | 校验所有 mixin 目标与 `@Inject` 处理函数签名（需要 loom 合并 jar；沙箱里会报 `Loom merged jar not found`） |
| `verify_shader_imports.py` | 校验自定义 shader 的 `#moj_import` 目标真实存在（同上） |

## 2. `lineage/` —— 跨分支/跨仓同步的唯一现行入口

| 文件 | 内容 |
|---|---|
| `lineage/HANDOFF_LEDGER.md` | 同步账本（本分支副本）：上游指向本分支的行的核对结果 + 本分支新开的行（L-1…L-5） |
| `lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md` | 给 26.1.2 的 TML/GPU 移植指导（含 26.1.2 必须先实测的 Q1-Q6） |

## 3. 取证与审计（完结即归档，不再更新；引用前先看文件头状态）

| 文件 | 内容 |
|---|---|
| `TML_GPU_FEASIBILITY_1211_20260831.md` | GPU 静态烘焙在 1.21.11 上的可行性论证与逐条 javap |
| `TML_GPU_STEP2_HANDFLUSH_20260831.md` | 第 2 步（光影手部 flush）与第 3 步（世界语境）的取证链：§1-§3 手部、§4 世界（4.1 事实 / 4.2 设计 / 4.3 实机结论） |
| `TML_GPU_PROBE_TOOL_20260831.md` | `scripts/mesh_render_probe.gradle` 的用法：沙箱没有 JDK 时，怎么靠 CI 的 javap 通道核实成员名与时机 |
| `REVIEW_UPSTREAM_TML_GPU_262_20260831.md` | 对 26.2 GPU 层的只读审查（A1-A9），含「哪些能互相印证、哪些不可跨纪元照抄」 |
| `SCOPE_PIP_DEPTH_1211_STEP1_20260830.md`、`SCOPE_PIP_DEPTH_1211_STEP2_20260830.md`、`SCOPE_PIP_DEPTH_1211_STEP3_20260830.md` | 深度版 PIP 三步的实现记录 |
| `SCOPE_PIP_RERENDER_1211_PORT_PLAN_20260830.md` | **STATUS: DECLINED**（维护者 2026-08-30 裁定）；本体保留作重启起点 |
| `PORT_1_21_11_PHASE1.md` / `PORT_1_21_11_PHASE2.md` | 从 26.1.2 移植到 1.21.11 的构建迁移与错误族定位记录 |
| `SYNC_LR_043_1_21_11_2026_08_26.md` | LR 0.4.3 战术同步回流的记录 |
| `EXPLICIT_GAPS_AUDIT_R12.md`、`UPSTREAM_GAPS_AND_TODO_AUDIT_26_1_2.md`、`API_STABILITY_P2_AND_PORTING_PLAN.md`、`RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md`、`UPDATE_REPORT_26_1_2_R1.md` | 更早期（26.1.2 纪元 / R1-R2）的审计与报告，多数结论已被后续代码取代，只作历史依据 |

## 4. 移植手册（写给「把本分支功能搬去别的分支」的人）

`PORT_R2_TO_26_1_2.md`、`PORT_R2_TO_26_2_MAIN.md`（均在 `docs/` 根） —— 把 R2 功能分别移植到 26.1.2 / 26.2(main)
的独立执行手册。TML/GPU 那条线请优先看 `lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md`
（比这两份新，且带实机结论）。

## 5. `ci/` 与 `publish/`

| 目录 | 内容 |
|---|---|
| `ci/` | workflow **暂存区**：沙箱 token 没有 `workflows` 权限，Agent 把改动写在这里，维护者复制到 `.github/workflows/` 同名文件。当前清单见 `ci/README.md` |
| `publish/` | 三站发布文案（CurseForge / Modrinth / MC百科）与规则出处。注意其中的硬要求：**项目名不能带版本号** |

## 6. 新增文档时的规矩

1. 现行参考（会滞后的那种）→ 放 `docs/` 根；带日期的取证/审计 → 文件名带 `_YYYYMMDD`，放根目录即可（本分支不搬存量）。
2. 跨分支交接、指导、账本 → `docs/lineage/`。
3. 写完回填本索引表；若这条文档改变了某个「已实机 PASS / 待实机」的判定，同时改
   `MESH_LOADER.md` 的状态块与 `CHANGELOG_1_21_11.md`（AGENTS.md §2：不得声称未验证的东西）。
