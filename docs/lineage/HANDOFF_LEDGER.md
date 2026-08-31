# 跨分支/跨仓 Handoff 登记账本（refab **1.21.11** 分支副本）

> 用法见 **26.2 分支上的** `docs/lineage/SYNC_GOVERNANCE_PROPOSAL.md` §4-§5（那份以及下面几份
> `SYNC_GUIDE_*` 只存在于 `arena/01a04e96`，本分支没有副本 —— 读法：
> `gh api -H "Accept: application/vnd.github.raw" "repos/q14433686-arch/TaCZ_Refabricated_Unofficial/contents/<路径>?ref=arena/01a04e96-tacz-refabricated-unofficial"`）。状态：
> `OPEN`（待认领）/ `CLAIMED(分支)`（进行中）/ `DONE(commit)` / `DECLINED(原因)` / `需核对`。
> 本文件是 `arena/01a04e96`（26.2）上同名账本的**分支副本**：各行状态由本分支回填，
> 上游那份不动；两边不一致时，**涉及 1.21.11 的行以本副本为准**，其余以正式账本为准。
> 本副本首刊 2026-08-31（R3 轮）。

## 1. 上游账本里指向本分支的行（核对结果）

| # | handoff / 计划文档 | 方向 | 状态 | 本分支核对（2026-08-31） |
|---|---|---|---|---|
| 7 | **26.2 上的** `docs/lineage/SYNC_GUIDE_REFAB_1211_20260830.md`（1.21.11 暂停收尾 + 可同步项） | 26.2 → 1.21.11 | **DONE**（本轮收口，见下） | §0.1 TEMP 剥离 → 探针已固化成 `scripts/mesh_render_probe.gradle`（默认不接入，`-PmeshProbe` 显式开），`build.gradle` 里不再有任何 TEMP 块；§0.2 `docs/SCOPE_PIP_RERENDER_1211_PORT_PLAN_20260830.md` 顶部 `STATUS: DECLINED` 已在；§0.3 `SCOPE_PIP_RERENDER` 默认 false 已在；§1.1 检视动画两连修（`4aa8d7b`+`12d6f3c`）**已在**（`AnimationStateContext#stopAnimation` 的出生序号判据、`ObjectAnimationRunner.SPAWN_COUNTER`、`AnimationStateMachine#trigger` 的栈式快照三处逐一核到，无需搬）；§1.2 的结论被本分支推翻，见 §2 |
| 5 | `docs/SCOPE_PIP_RERENDER_1211_PORT_PLAN_20260830.md` | 26.2(main) → 1.21.11 | **DECLINED**（维护者 2026-08-30 裁定） | 本分支已带 `STATUS: DECLINED` 头；半成品按裁定 (a) 保留、默认锁 false |
| 8 | **26.2 上的** `docs/lineage/SYNC_GUIDE_REFAB_2612_20260830.md` 第 4 步「等 1.21.11 收口」 | 26.1.2 ← 本分支 | **READY** | 本分支 R3 已把 TML 第 0/1/2/3 步连同实机结论与注入点事实整理成 `SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md`，26.1.2 可以按它开工 |

## 2. 本分支新开的行

| # | 事项 | 方向 | 状态 | 备注 |
|---|---|---|---|---|
| L-1 | `docs/lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md`：内置 TML 第 0/1/2/3 步整包 + 五条不变量 + 26.1.2 需先实测的 Q1-Q7 | 1.21.11 → 26.1.2（`arena/01a05170`） | **OPEN** | 26.1.2 目前 meshloader 文件数为 0（`git ls-tree` 核实），所以是「先第 0 步、再往上叠」，不是打 GPU 补丁。要求回给：Q1-Q7 实测答案 + 世界路径两条主验收项的实机结论 |
| L-2 | `docs/REVIEW_UPSTREAM_TML_GPU_262_20260831.md`：对 26.2 GPU 层的静态审查（A1-A9） | 1.21.11 → 26.2（`arena/01a04e96`） | **OPEN** | 只读审查、无运行期断言。A1（渲染目标硬绑）与 A2（异常时关总闸 + 回写配置）建议优先；A4 需要他们实机对表才能定性 |
| L-3 | 上游 §1.2「GPU 烘焙层不可搬到 1.21.11（硬依赖 26.2 的 `RenderPipeline.builder`/`BindGroupLayouts`）」 | 26.2 → 1.21.11 | **OVERTURNED** | 1.21.11 上确有 `RenderPipeline.builder` 路线（本分支两条路都在用），第 1/2/3 步已实机 PASS；「不可搬」的说法请上游从指导文档里改掉。真正不可搬的是**方向相反**的三样：`PreparedFrame#executeSolid`、`RenderType#prepare()`、`PreparedRenderType#drawFromBuffer`（1.21.11 CI javap 核实不存在） |
| L-4 | CI 对齐：`compile-check` v4（主分支/PR 触发 + concurrency + 只在 arena/** 回推日志） | 26.2 → 1.21.11 | **待维护者粘贴** | 三份都只到了 `docs/ci/`：Agent 凭据推不动 `.github/workflows/`（GitHub App 无 `workflows` 权限，push 被远端整体拒绝，实测 2026-08-31）。26.2 侧若已知这条限制，请把 `docs/ci/README.md` 的措辞同步成「三份全是暂存件」 |
| L-7 | `_illuminated` 骨骼恒烘 (block=15, sky=15) ⇒ 光影包里「遮不住太阳/月亮」：本分支加了 `MeshPolyIlluminatedRealSky`（仅光影下换真实 sky） | 1.21.11 → 26.2 与 26.1.2 | **OPEN（只改 poly 层，待实机）** | 数字在三个分支的 `PolyMeshModel` 与 `BedrockPart#render` 里逐字相同 ⇒ 同源；本分支刻意只覆盖 poly 层，因为立方体层影响所有枪包与所有准星点、配置该归 `ClientConfig`。**建议 26.2 一次把两层收进同一个键**（见 `REVIEW_UPSTREAM_TML_GPU_262_20260831.md` 的 A10 续集）。维护者实机前，这条对本分支也是「静态修复」；判别法在 `docs/MESH_LOADER.md` §5.8 末段 |
| L-6 | `core/PolyMesh.java` 的镜像绕序 / 枪包法线缺陷（A10）：`docs/MESH_LOADER.md` §5.7 的修复 + 三个配置开关 | 1.21.11 → 26.2（`arena/01a04e96`）**与** 26.1.2（`arena/01a05170`） | **OPEN（修复已在本分支，待光影实机）** | 上游 26.2 `587763c` 的该文件与本分支改前**逐字相同** ⇒ 同一缺陷；26.1.2 目前还没有 meshloader，等它整包取文件时会自动带上本修复，所以它那边要守的是「别把 `FORCE_FLAT_SHADING` 恢复成常量、别删 `normals` 解析」。本分支只做到静态 + CI 编译，**没有**光影实机：判定矩阵与回退开关见 §5.7，谁跑了请回填 |
| L-5 | 1.21.11 孤儿 mixin 配置 `tacz.compat.acceleratedrendering.mixins.json` | 本分支 | **DONE** | 新增的「mixin 配置注册性」静态校验当场抓出：该 json 无对应 mixin/plugin 类、且不在任何 `fabric.mod.json` 的 `mixins` 数组里（本分支 AR 兼容走 `ARCompat` 空壳，理由写在该类注释）。已删该文件。26.2 侧同名配置**有**对应类（`BedrockPartMixin` 存在），所以这条只适用于 1.21.x 纪元 |

## 3. 版本号状态

| 分支 | mod_version | README 一致性 |
|---|---|---|
| 26.2（`arena/01a04e96`） | `1.1.8+fabric.26.2.R3` | 由他们自己回填 |
| **1.21.11（本分支）** | `1.1.8+fabric.1.21.11.R3` | ✅ `bash scripts/check_release_consistency.sh --strict` 通过（6 ok / 0 fail / 1 warn：arena 分支名不是 MC 系列，属预期跳过） |
| 26.1.2（`arena/01a05170`） | `1.1.8+fabric.26.1.2.R2-hotfix2` | 待他们 bump |

> 本分支 R3 = 把 `-hotfix2` 后缀去掉、直接进 R3，与 26.2 侧 `5bb13af` 的做法一致
> （规矩见 `gradle.properties` 注释与 `check_release_consistency.sh` 头部：序号直接接在
> `R<n>` 后，`-hotfix<n>` 只在真正发补丁时用）。
