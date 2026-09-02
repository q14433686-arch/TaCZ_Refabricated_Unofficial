# docs/ci/ —— workflow 暂存区（本分支副本）

沙箱 Agent 的凭据没有 `workflow` 权限，推不动 `.github/workflows/`。所以沿用 26.2 侧的约定：
**Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端复制到 `.github/workflows/` 同名文件**
（编辑现有文件或新建 → 粘贴 → 提交）。两边不一致时，**以 `.github/workflows/` 的正式件为准**；
上线后本目录的副本就是正式件的镜像，下次改动继续在这里迭代。

本分支实测确认：Agent **推不动**正式件 —— 试着改 `.github/workflows/compile-check.yml` 时远端直接
拒绝：`refusing to allow a GitHub App to create or update workflow ... without 'workflows'
permission`（整个 push 一起失败，不是只忽略那个文件）。所以下面三条**全部**只是暂存件，
上线前 `.github/workflows/` 里跑的仍是旧版。

## 当前清单（2026-08-31，R3）

| 文件 | 状态 | 内容 |
|---|---|---|
| `compile-check.yml` | **待上线（替换 `.github/workflows/compile-check.yml`）** | 补主分支 `1.21.11` push + PR 触发；日志回推**只**在 arena/**；`concurrency` 取消过期 run |
| `build.yml` | **待上线（新建 `.github/workflows/build.yml`）** | 全量 `gradlew build` + jar 上传为 artifact（14 天）；外加五个静态校验：mixin 配置完整性、mixin 配置注册性（孤儿配置）、en/zh 语言键齐平、`[mesh_loader]` 配置↔Cloth↔语言键齐平（调 `docs/check_mesh_config_parity.py`）、版本一致性。artifact 名只用 sha（arena 分支名带 `/` 是非法 artifact 字符——26.2 侧首跑就是这样红的） |
| `consistency.yml` | **待上线（新建 `.github/workflows/consistency.yml`）** | AGENTS.md §1 的守门：只碰 `gradle.properties` / `README.md` / `fabric.mod.json` 时也触发；arena 分支一起守（等合并后再守就晚了）。脚本本体按 §1 只在默认分支，本流程自带「取不到就跳过」的回退 |

上线动作：GitHub 网页 → 仓库 → `.github/workflows/` → 对应文件 →
Edit（或 Add file → Create new file）→ 粘贴本目录同名文件全文 → 提交到目标分支。

**五个静态校验在写入时已对本分支源码树本地跑过，全绿**（2026-08-31）。法线/绕序那次往 `MeshyConfig` 加了 3 项，就是这条校验把「Cloth 少一条 / 默认值写歪」这类错误挡在编译之前的；脚本本体做过四类错误的注入实测（默认值 / 字段绑错 / 范围收窄 / 缺 `.desc` 语言键 ⇒ 全部准确报出）。
其中「mixin 配置注册性」这条当场抓出并清掉了一个历史遗留：
`tacz.compat.acceleratedrendering.mixins.json` 在 1.21.11 分支上没有对应的 mixin/plugin 类、
也没被任何 `fabric.mod.json` 注册（本分支的 AR 兼容是 `ARCompat` 空壳，理由写在该类注释里），
属于永不生效的孤儿配置，已删。

## 2026-09-02 跨线 CI 盘点

六线现状与逐分支上线动作见 [`INSTALL_MATRIX_20260902.md`](INSTALL_MATRIX_20260902.md)。
本分支相关的待办集中在 `pending/refab-1.21.11/`：

- `verify-mixin-targets-portable.patch` 已应用到本分支的 `docs/verify_mixin_targets.py`，把沙箱专属的 Java/Gradle 路径改为 `JAVA_HOME`、`PATH` 与 `GRADLE_USER_HOME`。
- `build-yml-verify-steps.md` 记录将 mixin 目标和 shader import 校验追加到 `build.yml` 的位置，以及为什么上传步骤应使用 `if: always()`。
- 本目录的 `build.yml`、`compile-check.yml`、`consistency.yml` 仍需维护者手动复制到 `.github/workflows/`；沙箱凭据没有 `workflows` 权限，无法直接上线 workflow。

`pending/TaCZ_Renovated/` 是姊妹仓 TaCZ_Renovated 三条 NeoForge 分支的代拟模板，不要复制到本仓 Fabric 分支。
