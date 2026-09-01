# docs/ci/ —— workflow 暂存区（26.1.2 线）

沙箱 Agent 的凭据没有 `workflow` 权限，推不动 `.github/workflows/`。
所以约定：**Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端
复制到 `.github/workflows/` 同名文件**（编辑现有文件或新建，粘贴、提交即可）。

两边不一致时，**以 `.github/workflows/` 的正式件为准**；上线后本目录的
副本就是正式件的镜像，下次改动继续在这里迭代。

本目录 2026-09-02 从 26.2 线（`arena/01a04e96`）镜像过来，同步记录见
`docs/lineage/SYNC_ROUNDUP_2612_20260902.md` §2。

## 当前清单（2026-09-02）

| 文件 | 26.1.2 线状态 | 内容 |
|---|---|---|
| `compile-check.yml` | **v3 已上线（`.github/workflows/compile-check-2612.yml`）；本副本是 v4，待维护者替换** | v3 = Java 25 + Actions 编译 + Contents API 把日志回推 `build-reports/compile-java.log`（沙箱可读）。v4 补：主分支/PR 也编译（合并 commit 此前从没编过）、`concurrency` 取消同分支过期 run；日志回推仍只在 `arena/**` |
| `consistency.yml` | **待上线 v2** | 版本号五处一致守门；`arena/**` 也触发（版本号改动都在工作分支，合并后才守门就晚了）；`paths` 含 `fabric.mod.json` |
| `build.yml` | **待上线** | 全量 `gradlew build` + jar 上传为 artifact（14 天）—— 发链接即可征测；顺带三个静态校验：mixin 配置完整性、en/zh 语言键齐平、版本一致性。artifact 名**只用 sha**（`github.ref_name` 在 arena 分支名带 `/`，是非法字符，26.2 首跑就是这样失败的） |
| `../scripts/check_release_consistency.sh` | **本分支已补**（2026-09-02，镜像自 `26.2(main)`） | 上面两个 workflow 都要跑它；AGENTS.md §1 说它可以只存在于默认分支（hook 会自动回落读取），但 **CI 跑在工作区，本分支必须有这份文件**。脚本本身分支无关（三条分支都能查、也能 `--branch` 指定） |

## 上线动作

GitHub 网页 → 仓库 → `.github/workflows/` → 对应文件 →
Edit（或 Add file → Create new file）→ 粘贴本目录同名文件全文 → commit。

本分支当前已上线的只有 `compile-check-2612.yml`（v3）；
`build.yml` 与 `consistency.yml` 需要维护者首次创建。

## 本地自检（本目录三个静态校验的等价命令）

```bash
bash scripts/check_release_consistency.sh            # 版本一致性（工作区，只报告）
bash scripts/check_release_consistency.sh --strict    # 发布门禁，不一致退出 1
```

2026-09-02 在本分支工作区跑过：通过 6 · 失败 0（唯一警告是分支名形态推导，属正常）。
