# docs/ci/ —— workflow 暂存区

**2026-10-27 起，本会话的凭据已具备推送 `.github/workflows/` 的权限**，因此 workflow
改动可以直接提交，并同步一份到本目录。本目录仍保留，作用是**给维护者一个不点开
Actions 就能审的镜像**（review 时看 diff 即可）。

历史约定（2026-09-02 之前，仍适用于没有 `workflow` 权限的会话/姊妹仓）：
Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端复制到
`.github/workflows/` 同名文件。

两边不一致时，**以 `.github/workflows/` 的正式件为准**。

## 当前状态（2026-09-02 实测刷新）

三件**全部已上线**，本目录副本与 `.github/workflows/` 正式件逐字一致（只差空行）：

| 文件 | 状态 | 内容 |
|---|---|---|
| `compile-check.yml` | ✅ 已上线（v4） | 主分支 push + PR 也编译（补合并 commit 的盲区）；日志回推只在 `arena/**`；`concurrency` 取消同分支过期 run |
| `consistency.yml` | ✅ 已上线（v2） | 版本号↔README 守门；`arena/**` 也触发；`paths` 含 `fabric.mod.json` |
| `build.yml` | ✅ 已上线（**2026-10-27 起五个静态校验**） | 全量 `gradlew build` + jar artifact（14 天）+ 五个静态校验（mixin 配置完整性 / en-zh 语言键齐平 / **方块掉落接线** / **方块实体持久化** / 版本一致性）。artifact 名只用 sha（`github.ref_name` 在 arena 分支带 `/`，是非法字符，首跑 2026-08-31 就是这样红的） |

> **2026-10-27 更新**：本会话的凭据已可推送 workflow 改动，`build.yml` 的两个新静态校验
> （`scripts/verify_block_drops.py --strict`、`scripts/verify_block_entity_persistence.py --strict`）
> 已直接写进 `.github/workflows/build.yml` 并同步回本目录的同名副本，逐字一致；
> 原先放在 `pending/refab-26.2/verify-block-scripts.md` 的代拟件已删除（不需要维护者手工粘贴了）。
> 起因见 `docs/investigations/MULTIBLOCK_DOUBLE_DROP_26_2_2026_10_26.md` 与
> `BLOCK_ENTITY_DATA_LOSS_26_2_2026_10_27.md`。

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
