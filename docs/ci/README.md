# docs/ci/ —— workflow 暂存区

沙箱 Agent 的凭据没有 `workflow` 权限，推不动 `.github/workflows/`。
所以约定：**Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端
复制到 `.github/workflows/` 同名文件**（编辑现有文件或新建，粘贴、提交即可）。

两边不一致时，**以 `.github/workflows/` 的正式件为准**；上线后本目录的
副本就是正式件的镜像，下次改动继续在这里迭代。

## 当前状态（2026-09-02 实测刷新）

三件**全部已上线**，本目录副本与 `.github/workflows/` 正式件逐字一致（只差空行）：

| 文件 | 状态 | 内容 |
|---|---|---|
| `compile-check.yml` | ✅ 已上线（v4） | 主分支 push + PR 也编译（补合并 commit 的盲区）；日志回推只在 `arena/**`；`concurrency` 取消同分支过期 run |
| `consistency.yml` | ✅ 已上线（v2） | 版本号↔README 守门；`arena/**` 也触发；`paths` 含 `fabric.mod.json` |
| `build.yml` | ✅ 已上线（artifact 名已修） | 全量 `gradlew build` + jar artifact（14 天）+ 三个静态校验（mixin 配置完整性 / en-zh 语言键齐平 / 版本一致性）。artifact 名只用 sha（`github.ref_name` 在 arena 分支带 `/`，是非法字符，首跑 2026-08-31 就是这样红的） |

> 旧版本文字（2026-08-31）曾把 `consistency.yml` 记为「待上线 v2」、`build.yml` 记为
> 「待更新 hotfix」——两件后来都已由维护者上线，本表 2026-09-02 按 `gh api contents`
> 实拉核对后刷新。

## 本线之外的待上线件 → `pending/`

`docs/ci/pending/` 放**目标不在本分支**的待上线件（姊妹分支 `26.1.2` / `1.21.11`
与姊妹仓 `TaCZ_Renovated` 三线），逐分支手动动作清单见
[`INSTALL_MATRIX_20260902.md`](INSTALL_MATRIX_20260902.md)。

## 本线已知的一个待裁定项

`src/main/resources/tacz.compat.acceleratedrendering.mixins.json` 是**孤儿 mixin 配置**
（`fabric.mod.json` 没注册它 ⇒ 永不生效）。1.21.11 线在 R3 已删同名文件，本线还留着
（AR 兼容在 26.2 是 no-op 空壳，`ARCompatImpl` 注释写着等 AR 出 26.2 版再恢复）。
现状不影响任何流程（本线 `build.yml` 没有「注册性」检查）；
**若将来把 1.21.11 那条注册性检查搬到本线，必须先删文件或加白名单，否则 CI 立刻红。**
详见 `INSTALL_MATRIX_20260902.md` §B0-3。
