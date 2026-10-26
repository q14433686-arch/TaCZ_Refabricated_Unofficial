# docs/ci/ —— workflow 暂存区

本目录保存 `.github/workflows/` 的镜像。修改工作流时同步两边；
部分沙箱凭据可能没有 `workflow` 权限，若推送被拒，需由维护者在 GitHub 网页端
更新 `.github/workflows/` 同名文件。

两边不一致时，**以 `.github/workflows/` 的正式件为准**；工作区的改动不等于已上线。

## 26.3 分支增量（2026-10-26 UTC）

本次移植在 `build.yml` 中加入 26.3 方块与标靶接线静态检查、守卫反向测试与隔离桩行为测试；
三份正式工作流的 push 分支列表均包含 `26.3`，`build.yml` 和 `consistency.yml` 的版本检查
使用 `--strict`，不一致时真正失败。本目录副本同步更新。
会话分支代码提交 `13d3fff` 的 [build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40407976635)、
[compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40407976378) 和
[Version consistency](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40407976824)
均已通过。后续提交增强了同一套隔离测试：编译真实 `TargetBlock` 并调用其放置回调，
模拟回调后的物品消耗及皮肤缓存的待解析/回退/成功状态。代码提交 `faf60a4`（当时临时编号为
`R1-hotfix4`，现已恢复 `R1-hotfix3`）的
[build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40410535903)、
[compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40410535916) 和
[Version consistency](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40410536028)
均已通过；构建包含 jar artifact。合并到 `26.3` 前不代表目标分支已上线这些改动；隔离桩测试不能代替游戏实测。

## 之前的上线状态（2026-09-02 实测刷新）

当时三件**全部已上线**，本目录副本与 `.github/workflows/` 正式件一致（只差空行）：

| 文件 | 状态 | 内容 |
|---|---|---|
| `compile-check.yml` | 基线已上线；本次增量待合并 | 主分支 push + PR 也编译；日志回推只在 `arena/**`；本次增加 `26.3` push 触发 |
| `consistency.yml` | 基线已上线；本次增量待合并 | 版本号↔README 守门；`arena/**` 也触发；本次加 `26.3` 触发和严格退出码 |
| `build.yml` | 基线已上线；本次增量待合并 | 全量 `gradlew build` + jar artifact（14 天）+ mixin / 语言 / 版本检查；本次另加 26.3 方块守卫与隔离桩测试。artifact 名只用 sha（`github.ref_name` 在 arena 分支带 `/`，是非法字符） |

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
