# docs/ci/ —— workflow 暂存区

**2026-10-27 起，本会话的凭据已具备推送 `.github/workflows/` 的权限**，因此 workflow
改动可以直接提交，并同步一份到本目录。本目录仍保留，作用是**给维护者一个不点开
Actions 就能审的镜像**（review 时看 diff 即可）。

历史约定（2026-09-02 之前，仍适用于没有 `workflow` 权限的会话/姊妹仓）：
Agent 把 workflow 改动写到本目录，维护者在 GitHub 网页端复制到
`.github/workflows/` 同名文件。

两边不一致时，**以 `.github/workflows/` 的正式件为准**。

## 26.2 工作分支的验收流程

三个通用工作流在本目录保留同名镜像；下表描述的是工作分支，不代表已经合入默认分支。Actions 的日志和归档才是实际执行记录。

| 工作流 | 在 26.2 上检查什么 |
|---|---|
| [`compile-check.yml`](compile-check.yml) | Java 25 编译；失败诊断写入对应 Check，不再往测试分支推日志提交。 |
| [`consistency.yml`](consistency.yml) | `check_release_consistency.sh --strict`：版本号与 README 不一致时失败，不只是打印报告。 |
| [`build.yml`](build.yml) | `gradlew build`（含专服 GameTest、JUnit）、成品 JAR 清单与散列、Mesa 普通客户端；另验方块掉落／存档、资源、语言键与版本一致性。二进制归档保留 14 天。 |

回移专用流程直接看正式件：[成品专服与 TCP 客户端](../../.github/workflows/distribution-smoke.yml)、[动作／瞄具／粒子／光照](../../.github/workflows/client-suites.yml)、[Iris](../../.github/workflows/iris-smoke.yml)、[JEI / REI](../../.github/workflows/viewer-smoke.yml)、[PAL](../../.github/workflows/animation-smoke.yml)、[Controllable](../../.github/workflows/controller-smoke.yml)。各自有路径过滤；文档提交不必重新生成全部二进制。测试 SHA、匹配的产物和未测范围统一见[交接记录](../lineage/HANDOFF_LEDGER.md#tacz-12-262)。不要把 26.2 依赖或用例直接复制给其它接收线。

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
