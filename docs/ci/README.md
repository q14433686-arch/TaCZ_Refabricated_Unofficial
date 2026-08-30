# docs/ci/ —— workflow 暂存区

沙箱 Agent 的凭据没有 `workflow` 权限，推不动 `.github/workflows/`。
所以约定：**Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端
复制到 `.github/workflows/` 同名文件**（编辑现有文件或新建，粘贴、提交即可）。

两边不一致时，**以 `.github/workflows/` 的正式件为准**；上线后本目录的
副本就是正式件的镜像，下次改动继续在这里迭代。

## 当前待上线清单（2026-08-31）

| 文件 | 状态 | 内容 |
|---|---|---|
| `compile-check.yml` | ✅ 已上线（c48cf9c，2026-08-31） | 补主分支/PR 编译盲区；日志回推仍只在 arena/**；加 concurrency 取消过期 run |
| `consistency.yml` | **待上线 v2**（正式件是 v1） | 补 arena/** 触发（版本号改动都在工作分支，合并后才守门就晚了）；paths 加 fabric.mod.json |
| `build.yml` | **待更新 hotfix**（正式件 7f472d2 首跑失败） | 全量 `gradlew build` + jar 上传为 artifact（14 天）——NV 征测/实测直接发 Actions 链接；顺带 mixin 配置完整性、en/zh 语言键齐平、版本一致性三个静态校验。**首跑 Upload jars 步失败：artifact 名用了 `github.ref_name`，arena 分支名带 `/` 是非法字符——本目录副本已改为只用 sha，请把 Upload jars 段同步到正式件** |

上线动作：GitHub 网页 → 仓库 → `.github/workflows/` → 对应文件 →
Edit（或 Add file → Create new file）→ 粘贴本目录同名文件全文 → commit 到
`arena/01a04e96-tacz-refabricated-unofficial` 分支（或按当时工作流程提交）。

三个静态校验在写入时已对当前源码树本地验证过全绿（2026-08-31）。
