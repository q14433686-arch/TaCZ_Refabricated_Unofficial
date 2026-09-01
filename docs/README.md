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
| `MESH_LOADER.md` | 内置 TML（mesh 高模加载 + GPU 静态烘焙第 0/1/2/3 步 + 光影下法线/绕序 §5.7 + 自发光部件天空光 §5.8 + 光影下「继承天体自发光」的判别与否证 §5.9/§5.10 + **§6 未修 BUG 记录：poly 绕序 × 背面剔除** （症状原话 / Complementary Unbound 环境 / 四步复现 / 三层来源 / 三个候选修法）的当前状态、18 项配置、边界、验证清单 |
| `CHANGELOG_1_21_11.md` | 本分支相对 26.1.2 的变更记录，按交付轮次倒序；**R3 段在顶部追加**（R3 段末尾已含「第四轮」：光影下两个 GPU 开关退回默认关 + EMISSIVE 闩锁修复） |
| `AMMO_SOURCE_API.md` | 下游模组替换实体弹药源的公共 API |
| `CARRYON_COMPAT.md` | Carry On 工作台兼容 |
| `verify_mixin_targets.py` | 校验所有 mixin 目标与 `@Inject` 处理函数签名（需要 loom 合并 jar；沙箱里会报 `Loom merged jar not found`） |
| `verify_shader_imports.py` | 校验自定义 shader 的 `#moj_import` 目标真实存在（同上） |

## 2. `lineage/` —— 跨分支/跨仓同步的唯一现行入口

| 文件 | 内容 |
|---|---|
| `lineage/HANDOFF_LEDGER.md` | 同步账本（本分支副本）：上游指向本分支的行的核对结果 + 本分支新开的行（L-1…L-9，含那条**未修 bug 的立项行** L-8b、对 26.1.2 移植的复核行 L-9） |
| `lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md` | 给 26.1.2 的 TML/GPU 移植指导：默认值警告（§1.4）、**必须他们自己决定的未修 bug（§1.6）**、六条不变量、Q1-Q10。**⚠ 2026-09-01：他们已移植并提交，本篇「他们尚未开始」类前提已过期，以复核篇为准** |
| `lineage/SYNC_REVIEW_2612_TML_PORT_20260901.md` | 复核篇（指导的回执）：按代码核 26.1.2 已提交的 TML 移植 @ `79a6391` —— 3 条 P0（mixin 漏注册 ⇒ 世界 GPU 表静默失效、lang 曾被整文件覆盖、孤儿 AR compat 配置）+ P1 卫生项 + 他们回的 Q8/Q9/Q10 + 请他们回我们的 5 项，每条附可复算命令 |
| `lineage/SCOPE_TEXT_SHOW_1211_20260901.md` | 镜内 `text_show`（MK5/MK5HD 弹药计数）的**两条叠加根因**与修法：① `functionalTasks` 在瞄具深度孔径路径上没被 flush（⇒ 完全不出现）；② `PapiManager` 用 `I18n.get` 把查表当格式化（⇒ 出现但是 `Format error:`；26.2 的 `ec51f556` 同源，同形两处 tooltip 一并收）；含与 26.1.2 补丁的两处差别、三仓分布表、javap 取证、**尚未跑**的四格实机剧本与两条日志判据 |
| `lineage/SYNC_REVIEW_2612_PIP_BACKPORT_20260901.md` | 评估 26.1.2 那轮 PIP 回移植（`0a77ef52`…`8aca7374`）对本分支的可用性：可加项只有 3 条 —— A 窄遍后的状态重提取/清提交节点（我们的类注释里挂着的欠账）、B 镜内文字掩码裁剪（`ScopeTextSubmitter`+`maskedText`，唯一能让"贴边不溢出"成立的解）、C 隔帧渲染 interval；其余是回搬我们自己；含判定 A/B 所需的 javap 探针与逐项成本/验收 |
| `lineage/SYNC_CHECKLIST_1211_TO_2612_PIP_20260901.md` | 发给 26.1.2 的同步清单（本分支 → 他们）：镜内文字掩码裁剪在本世代实机 PASS 的可用结论、建议他们补的三件小事（log-once 判据 / 重载剧本 / fail-closed 失败模式）、我方三条"不必对齐"判定（重提取防护、FCAP 配置落盘世代边界、隔帧 interval）、我方请回 7 项的逐项关闭复查，以及我方反过来向他们索取的"世界 GPU 消费点四点位表"（关系我方仍未修的"反光/高光偏一侧"） |
| `lineage/SYNC_REVIEW_2612_RENDER_LINE_20260901.md` | ★ 26.1.2 渲染线（`7562abcb`→`5c45a787`，42 个真提交）→ 1.21.11 的**逐条甄别记录**：移植 G1 mesh 目镜裁剪 / G2 掩码周期帧戳 / G3 手部坐过镜内窄遍 / G4 PIP 显示阈与重投影倍率渐变；否决 `b9f9db7`；不移植光影隔离大件（本线无 voxy/sodium 且仍 B1 硬拒）与三条"我方已等价"的；纠出对方同步文本把 `03a807e` 列为待搬的错误；回答他们 §6 的五点；含跨世代 API 改写清单与两处踩坑（阈值只搬半套会不自洽、冲突合并必须同轮跑编译门） |
| `lineage/SCOPE_PIP_SHADER_ISOLATION_PORT_2612_20260901.md` | ★ 光影下二次渲染的**时域隔离**在本线怎么落地：`Iris#getCurrentDimension` 维度替换 → Iris 单建一套管线（切断"上一帧被推进两次"）；含他们的件↔我方落点对照表、Sodium/Voxy 两条通道为何本批不搬与解锁条件、软注入命中与否的**日志自检测别法**、首次开镜卡顿/显存/下界 fallback 三项已知取舍、实机判别清单 5 条 |
| `lineage/SYNC_CHECKLIST_1211_NEOFORGE_SISTER_20260901.md` | ★ 发给**姊妹项目 TaCZ_Renovated 的 NeoForge `1.21.11` 分支**的同步清单（2026-09-01，按对方 tip `e3d9dd5c` 逐文件读码实测）：镜内 `text_show` 两个根因在对方线上都还在（`BedrockRenderSnapshot#submitFunctionalTasks` 无调用点、`PapiManager` 用 `I18n.get`）⇒ P0-a/P0-b 施工点 + 判据；`TextShowRender` 无掩码裁剪 ⇒ P1 全套文件映射 + 本世代四个渲染 API 事实（含"必须注册壳纹理"这条与 26.1.2 的偏离）；§5 明确**别同步**的（PIP 两键、FCAP、全部 TML/mesh GPU 项、我方 CI 状态）；§6 可直接抄我方哪几个提交 |

## 3. 取证与审计（完结即归档，不再更新；引用前先看文件头状态）

| 文件 | 内容 |
|---|---|
| `TML_GPU_FEASIBILITY_1211_20260831.md` | GPU 静态烘焙在 1.21.11 上的可行性论证与逐条 javap |
| `TML_GPU_STEP2_HANDFLUSH_20260831.md` | 第 2 步（光影手部 flush）与第 3 步（世界语境）的取证链：§1-§3 手部、§4 世界（4.1 事实 / 4.2 设计 / 4.3 实机结论） |
| `TML_GPU_PROBE_TOOL_20260831.md` | `scripts/mesh_render_probe.gradle` 的用法：沙箱没有 JDK 时，怎么靠 CI 的 javap 通道核实成员名与时机 |
| `REVIEW_UPSTREAM_TML_GPU_262_20260831.md` | 对 26.2 GPU 层的只读审查（A1-A12，其中 A11 已被实机否证、A10 的一半被本仓自己否证），含「哪些能互相印证、哪些不可跨纪元照抄」 |
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
