# docs/ci/ —— workflow 暂存区（26.1.2 线）

沙箱 Agent 的凭据没有 `workflow` 权限，推不动 `.github/workflows/`。
所以约定：**Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端
复制到 `.github/workflows/` 同名文件**（编辑现有文件或新建，粘贴、提交即可）。

两边不一致时，**以 `.github/workflows/` 的正式件为准**；上线后本目录的
副本就是正式件的镜像，下次改动继续在这里迭代。

本目录 2026-09-02 从 26.2 线（`arena/01a04e96`）镜像过来，同步记录见
`docs/lineage/SYNC_ROUNDUP_2612_20260902.md` §2。

## 当前状态（2026-09-02 按 26.2 线六线 CI 盘点刷新）

本线现役 **只有** `compile-check-2612.yml`（v3：仅 `arena/**` 触发、无 concurrency、
无 PR/主分支 ⇒ 合并 commit 从没被编过）。三件待上线稿都在本目录，
**已按本分支适配、只差维护者粘贴**：

| 文件 | 26.1.2 线状态 | 内容 |
|---|---|---|
| `compile-check.yml` | **v4 待上线**（并入替换现役 v3） | 主分支/PR 也编译（补合并 commit 盲区）、`concurrency` 取消同分支过期 run；日志回推仍只在 `arena/**`；**已并入三条静态检查**（mixin 注册 / lang 齐平 / mesh 配置 parity，来源 `docs/publish/ci/CHECKS_TO_APPEND_20260901.md`） |
| `consistency.yml` | **待上线 v2** | 版本号五处一致守门；`arena/**` 也触发（版本号改动都在工作分支，合并后才守门就晚了）；`paths` 含 `fabric.mod.json` |
| `build.yml` | **待上线** | 全量 `gradlew build` + jar 上传为 artifact（14 天）—— 发链接即可征测；顺带三个静态校验：mixin 配置完整性、en/zh 语言键齐平、版本一致性。artifact 名**只用 sha**（`github.ref_name` 在 arena 分支名带 `/`，是非法字符，26.2 首跑就是这样失败的） |
| `INSTALL_MATRIX_20260902.md` | 家族级**副本**（源在 26.2 线 PR #87 `fcd3b4a`） | 六线 CI 现状总表 + 逐分支手动动作（本线 = §B1）+ 首跑预期 + 复核命令。副本仅作本线参照，**不要在这里改源内容** |
| `../scripts/check_release_consistency.sh` | **本分支已补**（2026-09-02，镜像自 `26.2(main)`） | 上面两个 workflow 都要跑它；AGENTS.md §1 说它可以只存在于默认分支（hook 会自动回落读取），但 **CI 跑在工作区，本分支必须有这份文件**。脚本本身分支无关（三条分支都能查、也能 `--branch` 指定） |

## 上线动作（维护者，四步；26.2 线盘点结论见 `INSTALL_MATRIX_20260902.md` §B1）

GitHub 网页 → 仓库 → `.github/workflows/` → 对应文件 →
Edit（或 Add file → Create new file）→ 粘贴本目录文件全文 → commit 到本分支。

| # | 动作 | 源（本目录） | 目标 |
|---|---|---|---|
| 1 | 新建 build | `build.yml` | `.github/workflows/build.yml` |
| 2 | 新建 consistency | `consistency.yml` | `.github/workflows/consistency.yml` |
| 3 | 新建 compile-check v4 | `compile-check.yml` | `.github/workflows/compile-check.yml` |
| 4 | **删掉旧件** | — | `.github/workflows/compile-check-2612.yml`（打开该文件 → 右上 `…` → Delete file） |

⚠️ **动作 3 与 4 必须一次做完**：两个文件的 `name:` 都是 `compile-check`，
并存会每轮 push 跑两遍编译、回推两条 `ci-log`（配额翻倍 + 历史噪音）。

**首跑预期：绿。** 26.2 线盘点时已对本分支真实树预演（矩阵 §5）：mixin 全部注册且类
存在、`tacz`+`lrtactical` 的 en_us↔zh_cn 齐平、一致性脚本已在位；2026-09-02 本会话
又在本工作区把**三条静态检查全跑了一遍、全绿**（mixin 65/65、lang 超集+333 字面量
键全命中、mesh parity 19/19）。其中 mesh parity 需要先修一处：M-7 面板条目的语言键名
未跟随 toml 键蛇形（`mesh_gpu_bake_budget` → `mesh_gpu_bake_budget_per_frame`，
6 处改名、显示文本不变），不修则首跑必红。Java 25。
（真实 Actions 首跑尚未发生 —— 这是沙箱给不出的验证。）

## 本地自检（与 CI 同一批静态校验的等价命令）

```bash
bash scripts/check_release_consistency.sh            # 版本一致性（工作区，只报告）
bash scripts/check_release_consistency.sh --strict    # 发布门禁，不一致退出 1
python3 docs/check_mixin_registration.py             # mixin 注册守卫
python3 docs/check_lang_keys.py                      # lang 键守卫
python3 docs/check_mesh_config_parity.py             # mesh 配置 parity
```

2026-09-02 在本分支工作区跑过 `check_release_consistency.sh`：通过 6 · 失败 0
（唯一警告是分支名形态推导，属正常）。

## 历史遗留

`docs/publish/ci/` 是本分支独有的旧暂存路径：`compile-check-2612.yml`（v3 原件，
与现役逐字一致）+ `CHECKS_TO_APPEND_20260901.md`（三条静态检查待办，**2026-09-02
已并入本目录 `compile-check.yml`**）。新改动一律在本目录迭代。
