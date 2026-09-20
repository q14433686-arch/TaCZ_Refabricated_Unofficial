# docs/ 索引 —— 每个目录/文件是干什么的

> 首刊 2026-08-31（R3 文档清理轮）。新增文档时请把它放进正确的目录并回填本表。
> 判断标准只有一条：**这份文档描述的是「现在」还是「当时」？**
> 描述现在 → 根目录；描述当时（调查、交接、已被取代的结论）→ 对应存档目录。

## 根目录 —— 现行参考（描述当前代码状态，滞后了就要改）

> 2026-09-21 归档轮（26.3 R1 实机验证完成后）：26.2 专属的 release notes / 专题记录移入 `archive/`，
> 26.2 诊疗长文移入 `investigations/`。根目录只留对 26.3 线仍然成立的文档。

| 文件 | 内容 |
|---|---|
| `CHANGELOG_26_3_R1.md` | **26.3 R1 变更清单**（✅ 实机 / 🔧 仅编译 逐条标注；未验证项列在末尾） |
| `MESH_LOADER.md` | 内置 TML（mesh 高模加载 + GPU 静态烘焙）的当前状态、配置、边界 |
| `PORTING_NOTES.md` | 26.2 移植经验总结（字节码级验证过的方法论，26.3 仍适用；26.3 具体差异见 lineage 移植指南） |
| `AMMO_SOURCE_API.md` | 可替换弹药 API 表面 |
| `CARRYON_COMPAT.md` | Carry On 工作台兼容（26.3 上 Carry On 尚无构件，接线保留） |
| `README_26_1_2.md` | **26.1.2 分支根 README 的替换蓝本**（AGENTS.md §3：改 README 结构时同步它） |

## `investigations/` —— 日期型调查/审计记录（完结即入，不再更新）

文件名带日期的专题记录：bug 取证链、性能方向盘点、一次性审计。
结论可能已被后续代码推翻，**引用前先看文件头的状态标注**。
（2026-08-31 从根目录迁入 12 份；此前平铺在 `docs/` 根下。）

26.3 线三件：`PORT_26_3_FEASIBILITY_2026_09_02.md`（可行性，pre-1 时代）→ `PORT_26_3_PLAN_2026_09_17.md`
（执行计划 + 依赖矩阵）→ `SCOPE_26_3_VS_26_2_DELTA_ANALYSIS_2026_09_20.md`（渲染差异 + §8–§17 全部实机定案：
裁剪、崩溃、JEI、掉落、配方、高模、专服）。
`COMPAT_AND_ROADMAP_26_2_HISTORY.md` 是 26.2 线的逐轮兼容诊疗长文（2026-09-21 自根目录迁入）。

## `lineage/` —— 跨分支/跨仓同步的**唯一现行入口**

| 文件 | 内容 |
|---|---|
| `HANDOFF_LEDGER.md` | **同步账本（单一事实源）**：所有跨分支交接的状态（OPEN/DONE/DECLINED） |
| `SYNC_ROUNDUP_R3_20260831.md` | **R3 定稿轮总纲（当前索引起点）**：四线进度底账、移植主次、工作流同步、旧指导时效标注 |
| `FAMILY_TREE_2026_08_30.md` | 六分支谱系实测 |
| `SYNC_GOVERNANCE_PROPOSAL.md` | 同步治理原则 |
| `SYNC_GUIDE_REFAB_1211/2612_*.md`、`SYNC_GUIDE_RENOV_262_*.md` | 三份 08-30 同步指导（时效标注见 ROUNDUP §3） |
| `PORT_GUIDE_26_3_FOR_RENOVATED_NEOFORGE_20260921.md` | **26.3 移植指南（给姊妹 NeoForge 仓）**：26.2→26.3 全部 130 文件改动按 Mojang / Iris / 行为 bug 分类，逐条标 NeoForge 对应做法与实机状态（账本 #18） |
| `SYNC_GUIDE_PUTAWAY_KEEP_20260902.md` | 收枪（put-away）动画 `keep()` 修复的三线移植指导：机制核对表、两份分支补丁、落码后必测项（账本 #14） |
| `superseded/` | **08-30 之前的旧 handoff/同步件**（原 `docs/handoff/` 四件套、08-12/08-22 的移植清单等 9 份）。内容未必失效，但**状态一律以账本为准**，不要按旧件直接开工 |

## `publish/` —— 发布相关

| 文件 | 内容 |
|---|---|
| `RELEASE_CHECKLIST.md` | 发布检查单（consistency.yml 失败提示指向这里） |
| `DISCOVERABILITY_CHECKLIST.md` | 三站可发现性与许可红线 |
| `README.md` + `CurseForge.md` / `Modrinth.md` / `MCMOD.md` | 三站发布文案与站规依据 |

## `ci/` —— workflow 暂存区

沙箱凭据无 workflow 权限，`.github/workflows/` 由维护者手动上线。
**此目录是本分支正式件的镜像**（2026-09-02 实测：三件全部已上线且逐字一致）；
与正式件不一致时以 `.github/workflows/` 为准。
`pending/` 子目录放**目标在别的分支/别的仓**的待上线件（refab 1.21.11 的两个 verify 脚本件、
TaCZ_Renovated 三线的 build/compile-check）；六条线的逐分支手动动作清单见
`INSTALL_MATRIX_20260902.md`。

## `archive/` —— 历史（只增不改）

- 2026-07 移植期：R1 之前的进度轮记、审计与设计文档。
- 2026-09-21 迁入的 26.2 线专属件：`CHANGELOG_26_2_R2.md`（26.2 R2/R3 release notes）、
  `FIRST_PERSON_ANIMATION_COMPAT_26_2.md`、`LRTACTICAL_FEEDBACK_LAYER_26_2.md`、
  `mac-shader-transparency-test.md`（Mac/Iris 透明问题测试说明，26.2 已 PASS 关案）。

## `patch/` —— 跨分支补丁文件

配合旧 handoff 使用的 `.patch`；状态同样以 `lineage/HANDOFF_LEDGER.md` 为准。
