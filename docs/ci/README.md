# docs/ci/ —— workflow 暂存区（26.1.2 线）

沙箱 Agent 的凭据没有 `workflow` 权限，推不动 `.github/workflows/`。
所以约定：**Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端
复制到 `.github/workflows/` 同名文件**（编辑现有文件或新建，粘贴、提交即可）。

两边不一致时，**以 `.github/workflows/` 的正式件为准**；上线后本目录的
副本就是正式件的镜像，下次改动继续在这里迭代。

本目录 2026-09-02 从 26.2 线（`arena/01a04e96`）镜像过来，同步记录见
`docs/lineage/SYNC_ROUNDUP_2612_20260902.md` §2。

## 当前状态（2026-09-02 实拉刷新：三件全部已上线）

维护者 2026-09-02 23:07–23:12（+08）在网页端一次性完成四步上线，
本目录副本即正式件的镜像（逐字一致）：

| 文件 | 状态 | 内容 |
|---|---|---|
| `compile-check.yml` | ✅ 已上线（v4，`1519ae59` 更新并改自 `compile-check-2612.yml`，旧件已除 ⇒ 无双跑） | 主分支/PR 也编译（补合并 commit 盲区）、`concurrency` 取消同分支过期 run；日志回推只在 `arena/**`。**未含三条静态检查**（上线的是并入前暂存稿，可选跟进见下） |
| `consistency.yml` | ✅ 已上线（v2，`6016d708`） | 版本号五处一致守门；`arena/**` 也触发；`paths` 含 `fabric.mod.json` |
| `build.yml` | ✅ 已上线（`3ac189a5`） | 全量 `gradlew build` + jar 上传为 artifact（14 天）—— 发链接即可征测；顺带三个**内联**静态校验：mixin 配置完整性、en/zh 语言键齐平、版本一致性。artifact 名只用 sha（`github.ref_name` 在 arena 分支名带 `/`，是非法字符，26.2 首跑就是这样失败的） |
| `INSTALL_MATRIX_20260902.md` | 家族级**副本**（源在 26.2 线 PR #87 `fcd3b4a`） | 六线 CI 现状总表 + 逐分支手动动作（本线 = §B1，**已全部执行**）。副本仅作本线参照，**不要在这里改源内容** |
| `../scripts/check_release_consistency.sh` | 本分支已有（2026-09-02，镜像自 `26.2(main)`） | 上面两个 workflow 都要跑它；CI 跑在工作区，本分支必须有这份文件。脚本本身分支无关 |

## 上线记录（2026-09-02，四步全完成）

| # | 动作 | 结果 |
|---|---|---|
| 1 | 新建 build | `3ac189a5` Create build.yml（逐字 = 本目录稿） |
| 2 | 新建 consistency | `6016d708` Create consistency.yml（逐字 = 本目录稿） |
| 3 | compile-check 升级 v4 | `1519ae59` Update and rename compile-check-2612.yml → compile-check.yml（内容 = 本目录 v4 稿） |
| 4 | 删旧件 | 随 3 的 rename 完成，无双跑 |

上线后 CI 即转绿（`d1b8f2b0` ci-log success）。**注意**：PR 上下文
（`pull_request` 事件）的 check run 呈 `action_required` —— 仓库对 PR run
设了批准门槛，需维护者在 Actions 页点批准；`push` 事件 run 自动跑且绿。

## 剩余可选跟进：compile-check 的三条静态检查

现役 compile-check 是纯 v4（不含静态检查）；`build.yml` 的三个**内联**校验
只覆盖 mixin 配置完整性 / en-zh 语言键齐平 / 版本一致性，以下三条仍无 CI 覆盖：

| 检查 | 脚本 | 现状 |
|---|---|---|
| mixin **注册性**（fabric.mod.json 注册 + 类存在） | `docs/check_mixin_registration.py` | 工作区实跑绿（65/65） |
| lang 超集 + 代码字面量键存在 | `docs/check_lang_keys.py` | 工作区实跑绿（333 字面量全命中） |
| mesh 配置三方齐平（toml↔Cloth↔语言键） | `docs/check_mesh_config_parity.py` | 工作区实跑绿（19/19，**依赖 `ca083b5d` 的键名改名**，不修必红） |

启用 = 把 `docs/publish/ci/CHECKS_TO_APPEND_20260901.md` 里的三个 step
追加进 `.github/workflows/compile-check.yml`（或改本目录暂存稿后照惯例上线）。

## 本地自检（与 CI 同一批静态校验的等价命令）

```bash
bash scripts/check_release_consistency.sh            # 版本一致性（工作区，只报告）
bash scripts/check_release_consistency.sh --strict    # 发布门禁，不一致退出 1
python3 docs/check_mixin_registration.py             # mixin 注册守卫
python3 docs/check_lang_keys.py                      # lang 键守卫
python3 docs/check_mesh_config_parity.py             # mesh 配置 parity
```

2026-09-02 在本分支工作区实跑：一致性 6 通过 / 0 失败（唯一警告是分支名
形态推导，属正常）；三条静态检查全绿（mixin 65/65、lang 超集+333 字面量、
mesh parity 19/19）。

## 历史遗留

`docs/publish/ci/` 是本分支独有的旧暂存路径：`compile-check-2612.yml`（v3
原件，现役的 rename 前身）+ `CHECKS_TO_APPEND_20260901.md`（三条静态检查
待办，**仍开放**，见上节）。新改动一律在本目录迭代。
