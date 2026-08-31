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
| L-7 | `_illuminated` 骨骼恒烘 (block=15, sky=15) ⇒ 光影包里「遮不住太阳/月亮」：本分支加了 `MeshPolyIlluminatedRealSky`（仅光影下换真实 sky） | 1.21.11 → 26.2 与 26.1.2 | **OPEN（只改 poly 层，待实机）** | 数字在三个分支的 `PolyMeshModel` 与 `BedrockPart#render` 里逐字相同 ⇒ 同源；本分支刻意只覆盖 poly 层，因为立方体层影响所有枪包与所有准星点、配置该归 `ClientConfig`。**建议 26.2 一次把两层收进同一个键**（见 `REVIEW_UPSTREAM_TML_GPU_262_20260831.md` 的 A10 续集）。维护者实机前，这条对本分支也是「静态修复」；判别法在 `docs/MESH_LOADER.md` §5.8 末段。**同日更新（L-8）**：维护者报的现象不是这条，键已退回**默认 false**（代码与推导保留，见 §5.8 的定性块）；给 26.2 的「默认 true」建议一并作废 |
| L-6 | `core/PolyMesh.java` 的镜像绕序 / 枪包法线缺陷（A10）：`docs/MESH_LOADER.md` §5.7 的修复 + 三个配置开关 | 1.21.11 → 26.2（`arena/01a04e96`）**与** 26.1.2（`arena/01a05170`） | **PARTIAL（绕序那一半被实机否证并退回；法线两项保留）** | 上游 26.2 `587763c` 的该文件与本分支改前**逐字相同** ⇒ 同一缺陷；26.1.2 目前还没有 meshloader，等它整包取文件时会自动带上本修复，所以它那边要守的是「别把 `FORCE_FLAT_SHADING` 恢复成常量、别删 `normals` 解析」。本分支只做到静态 + CI 编译 ⇒ **随后维护者实机回包，把这一条推翻了一半**（同枪包同光影，拿 Forge 原版对照）： 「关掉绕序反转」那格才对 ⇒ `MeshPolyMirrorReverseWinding` 已退回默认 **false**（2026-08-31 晚）。 留下的两条与绕序无关：退化面不写零法线、`MeshPolyPreferPackNormals`。矩阵与结论见 §5.7（已回填）； **要闭合正反面不自洽，得先决定剔除策略**（`entityCutout` vs `entityCutoutNoCull`）或从数据反推绕序， 本分支两条都没做。26.2 那边请把「反转绕序」这条建议作废，只搬上面那两条 |
| L-8b | 光影下退回 collector 之后显形的绕序问题：`MeshPolyMirrorReverseWinding`（A10 那轮的默认 true）与 `RenderTypes.entityCutout` 的背面剔除相互抵消不掉 ⇒ 朝外的面被剔掉，高模枪近乎全黑。本分支已按「同枪包同光影 vs Forge 原版」的对照图退回默认 false，`§5.7` 矩阵随之回填 | 1.21.11 → 26.2 **与** 26.1.2 | **DONE（本分支，默认退回）/ OPEN（正反面不自洽仍未闭合）** | 静态可证的部分：本仓 collector 的顶点数据与上游 `587763c` 逐字相同，只差发射绕序那一位 ⇒ 关掉即与原版等价。仍未闭合的是「`gl_FrontFacing` 与朝外法线相反」这个不自洽本身：要修得先决定 `entityCutout` vs `entityCutoutNoCull`（行为改动）或从数据反推绕序，本分支**都没做**，也不想再交付一次纯推导的修复。`entityCutout` 是否剔背面在沙箱内核不了（无 Loom jar），只有实机对照这一条间接证据 |
| L-8 | 光影下「枪身挡住太阳/月亮那块继承天体自发光」：A/B/C 判别已跑完 —— A（`MeshPolyInShadow`）**无效**、B（关掉 `MeshGpuUnderShaders` + `MeshGpuWorldUnderShaders`）**有效**，第一/第三人称与展示台一致 ⇒ 根因在我们自开的 GPU pass；本分支已把这两键退回 false，并修掉 `resolveLightmap` 的一次性闩锁（光影下取不到 lightmap 改为整条拒收，不再降级到带 `EMISSIVE` define 的管线） | 1.21.11 → 26.2（`arena/01a04e96`）**与** 26.1.2（`arena/01a05170`） | **PASS（两键默认关的语境，2026-08-31 维护者复测）/ OPEN（代码修法本身仍待带光影开键自证）** | 26.2 那边形态相同：`MeshPolyInShadow` 同样默认 false、`resolveLightmap` 同款闩锁、且「光影下走 GPU」是默认开 ⇒ 现象应当更明显。要求回给：① 老日志里搜 `lightmap` —— 那行 WARN 出现过 ⇒ A12 就是成因，照抄两条修法即可；从没出现过 ⇒ 未排除的是「自建管线 MRT / color target 集合与 `ENTITY_CUTOUT` 不一致」。26.1.2 别把这两个键当 true 移植（`SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md` §1.4）。判别与修法全文：`docs/MESH_LOADER.md` §5.9 / §5.10、`docs/REVIEW_UPSTREAM_TML_GPU_262_20260831.md` A11（已否证）/ A12 |
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
