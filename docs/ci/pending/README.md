# docs/ci/pending/ —— 给**别的分支 / 别的仓**准备的待上线件

`docs/ci/` 根下是**本分支（26.2(main)）自己**的 workflow 镜像；
本目录放的是**目标不在本分支**的待上线件：姊妹分支（`26.1.2` / `1.21.11`）
与姊妹仓（`TaCZ_Renovated`，NeoForge）要装的东西。

为什么不直接推到目标分支：①沙箱 Agent 的 token 没有 `workflows` 权限
（`refusing to allow a GitHub App to create or update workflow …`，refab 1.21.11 与
renov 26.2 都实测被拒过）；②本会话的 arena 分支被固定在 26.2 线上，
不允许推别的分支。所以统一在这里代拟，由维护者在网页端粘到目标分支。

## 目录

| 路径 | 目标 | 内容 |
|---|---|---|
| `TaCZ_Renovated/{26.2,26.1.2,1.21.11}/build.yml` | renov 三条线，新建 `.github/workflows/build.yml` | 全量 `gradlew build` + jar artifact（14 天）+ 四项静态校验（NeoForge 版：注册性检查读 `src/main/templates/META-INF/neoforge.mods.toml`） |
| `TaCZ_Renovated/{26.2,26.1.2,1.21.11}/compile-check.yml` | renov 三条线，替换现役 compile-check | v4 升级：补主分支 push + PR 触发、`concurrency`、日志回推只在 `arena/**` |
| `refab-1.21.11/verify-mixin-targets-portable.patch` | refab `1.21.11` 分支的 `docs/verify_mixin_targets.py` | 把两条沙箱硬编码路径（`/usr/lib/jvm/...`、`/home/user/.gradle`）改成 `JAVA_HOME` / `GRADLE_USER_HOME` 可移植版 —— **不打这个补丁就不能挂 CI** |
| `refab-1.21.11/build-yml-verify-steps.md` | refab `1.21.11` 分支已暂存的 `docs/ci/build.yml` | 追加两条运行期安全校验（mixin 目标 / shader import）的 YAML 片段 + 插入位置 + `Upload jars` 改 `if: always()` 的理由 |

## 已做过的验证（2026-09-02）

- 六个 YAML：`yaml.safe_load` 解析通过，触发器/并发/步骤结构逐项打印核对。
- 内嵌的三个 python 片段：`compile()` 语法通过；「mixin 完整性」「lang 齐平」在 refab 26.2
  真实工作树上实跑 rc=0；renov 专属的「注册性」片段在合成树上做过正反两测
  （孤儿配置 + 声明缺文件 → rc=1 并各报一条；一致时 → rc=0）。
- renov 三条线的静态校验**预演全绿**：用 `gh api` 拉真实文件比对——
  mixin 完整性 7/7 通过、`templates/neoforge.mods.toml` 声明与磁盘 0 孤儿 0 缺失、
  `tacz` + `lrtactical` 的 en_us↔zh_cn 键齐平。
- `verify-mixin-targets-portable.patch`：在 `origin/1.21.11` 的 sparse worktree 上
  `git apply --check` + 实落通过，打完的文件 `compile()` 通过，
  且在无 MC jar 的环境下给出可读退出信息（证明路径解析逻辑真的在跑）。
- **未验证**：任何一条 workflow 的真实 Actions 首跑（沙箱跑不了 Actions）。
  renov 侧 `./gradlew build` 的内存余量是最大未知数（见上线清单 §B3-B5）。

上线总清单与逐分支动作见 [`../INSTALL_MATRIX_20260902.md`](../INSTALL_MATRIX_20260902.md)。
