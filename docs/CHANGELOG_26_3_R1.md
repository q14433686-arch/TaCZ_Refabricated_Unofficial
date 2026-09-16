# 26.3 R1 — Changelog

**环境行**：Minecraft 26.3 · Fabric Loader 0.19.5+ · Java 25+ · Fabric API 0.160.6+26.3 · Forge Config API Port 26.3.0+

> 本文件遵循 `AGENTS.md` §2 的措辞纪律：
> - 源码级、编译级能证明的改动，如实描述；
> - 未经实机验证的项明确标注「未实测」；
> - 禁用/绕开 ≠ 修复；
> - 不把别人在 issue 里贴的分析写成「本仓修复」。

---

## 概要

26.3 R1 是本仓面向 Minecraft 26.3（"Wilderness Bound" drop，2026-09-15 发布）的首个构建。
它是一次**版本基线 bump + 依赖刷新**的 release，完整继承 26.2 线 R3-hotfix2 的功能面
（内置 TML 高模加载与第一人称 GPU 烘焙、镜内掩码/镜内 PIP、LRTactical 框架、
可替换弹药源 API、Carry On 2.11 工作台兼容、JEI/REI Ammo Query、FCAP 配置持久化修复、
zRot=±0.1 第一人称手部对齐修正等）。

26.3 → 26.2 的 Fabric API 变更（CompostingChanceRegistry / FuelRegistry /
FabricPotionBrewingBuilder / StrippableBlockRegistry / TillableBlockRegistry /
FlattenableBlockRegistry 移除、`enableColoredVanillaFluidNames` → `getColoredName`、
BlockTransformerHelper / FluidFlowCallback 新增）经全局 grep 确认，**本仓源码未使用任何
被移除的 API**，因此 26.3 适配不需要代码改写，仅做版本坐标、fabric.mod.json 与文档同步。

## 版本基线变更

| 项目 | 26.2 R3-hotfix2 | 26.3 R1 |
|---|---|---|
| Minecraft | 26.2 | **26.3** |
| Fabric Loader | 0.19.3+ | **0.19.5+** |
| Fabric API | 0.155.2+26.2 | **0.160.6+26.3** |
| Loom | 1.17-SNAPSHOT | 1.17-SNAPSHOT（不变；官方覆盖 26.3） |
| Gradle wrapper | 9.5.1 | **9.6.0** |
| Forge Config API Port | 26.2.1 | **26.3.0**（26.3.x 分支） |
| Cloth Config | 26.2.155 | **26.3.158** |
| JEI | 30.13.0.86 | **31.0.0.5**（Fabric 26.3 **beta**，2026-09-16） |
| Mod Menu | 20.0.1 | **21.0.1-beta.1**（26.3 **beta**） |
| REI | 26.2.820 | 26.2.820（暂无 26.3 构建，compileOnly 沿用） |
| Zoomify | 2.16.1+26.2 | 2.16.1+26.2（暂无 26.3 构建，compileOnly 沿用） |
| Shoulder Surfing | 26.2-5.0.7+fabric | 26.2-5.0.7+fabric（暂无 26.3 构建，compileOnly 沿用） |
| PAL（player-animation-library） | 1.2.5 | 1.2.5（API 稳定，compileOnly 不变） |

`fabric.mod.json` 声明：
- `minecraft: ~26.3`（覆盖全部 26.3.x 小版本）；
- `fabricloader: >=0.19.5`；
- `forgeconfigapiport: >=26.3.0`。

`build.gradle` 新增 `loom.ignoreDependencyLoomVersionValidation=true`，以允许 Zoomify/REI/
Shoulder Surfing/PAL 这些 compileOnly 依赖在其 26.3 构建发布前继续使用 26.2 坐标
（它们是可选依赖、不打进 jar、运行期由玩家自行安装对应版本；详见
[`pending_dependency_updates.md`](pending_dependency_updates.md)）。

## 代码改动

- 本仓无 26.3 必需的 API 迁移代码（被移除的 Fabric API 入口均无引用；
  Vanilla `DataComponents.SWING_ANIMATION`/`MAP_COLOR` 移除、number_provider 拆分、
  block transformers、dynamic registries 等 26.3 重构点在 TaCZ 业务源码中均无调用）。
- `src/main/resources/tacz.accesswidener` 注释更新到 26.3，并复核 5 个 accessible 目标
  在 26.3 字节码中签名/可见性未变。
- 构建脚本注释统一改写为 26.3；排除块（AR/Controllable/KubeJS/Optifine/KosmX 旧 animation 包）
  经 Modrinth 2026-09-16 复核仍无 26.3 构建，继续保持临时排除。

## 文档改动

- 根 `README.md` 6 处强制同步点（`AGENTS.md` §1）已刷新：
  1. 顶部版本句 → 26.3 / `1.1.8+fabric.26.3.R1`
  2. 提示行 → "本分支源码当前使用 **26.3 R1** 版本号"
  3. §1 支持环境表全量更新；新增 R1 可选依赖表（标注 beta 状态）
  4. §2 新增 R1 段落
  5. 导航表新增 26.3 工作分支一行
  6. §6 SemVer 说明段示例改为 `+fabric.26.3.R1`
- 新建本文件 (`docs/CHANGELOG_26_3_R1.md`) 与 `docs/pending_dependency_updates.md`
  跟踪上游可选依赖的 26.3 发布进度。
- `docs/PORT_PLAN_26_3.md` 留存为本次升版的决策记录。

## 已知状态与待跟进项

- **未执行的实机矩阵**（环境缺 JDK 25 下载链路，本次未跑 `./gradlew build/runClient/runServer`）：
  - 编译通过性
  - Mixin apply 日志核查
  - 客户端进世界 / 开枪 / 开镜冒烟
  - 服务端到 `Done!` 冒烟
  - 光影下 Iris scope-mask bridge 日志行确认
- **可选依赖上游更新跟踪**：见 [`pending_dependency_updates.md`](pending_dependency_updates.md)。
  在 Zoomify/REI/Shoulder Surfing 等发布 26.3 构建后，去掉
  `loom.ignoreDependencyLoomVersionValidation` 并 bump 版本号即可。
- **JEI/Mod Menu 的 beta 状态**：两者在 2026-09-16 仍处 26.3 beta；
  等 stable 发布后需要做一次小版本 bump（届时应只改 `gradle.properties` 一行，不涉及代码）。

## 语义版本说明

`1.1.8+fabric.26.3.R1` 保留 `1.1.8` 作为 SemVer 核心以保持所有现有枪包的 `>=1.1.8`
版本谓词判定成立（详见 `gradle.properties` 头部注释），不使用 `-` 号连接的 prerelease 段。
R 序号在新 Minecraft drop 重启，后续 hotfix 直接接在 `R1-hotfixN` 之后（`N` 前**不加分隔符**，
与 TaCZTweaks 字符串识别约定保持一致）。
