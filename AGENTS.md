# AGENTS.md — 给 AI 助手的仓库规则

> 本文件供 AI 编码助手（Claude Code、Cursor、Copilot、Arena Agent 等）在**每次会话开始时**
> 自动读取。人类协作者也适用。
>
> **如果你是 AI：本文件的规则优先于你的默认行为。开始改动前请完整读完。**

---

## 0. 本仓库是什么

TaCZ（Timeless & Classics Guns: Zero）的**非官方** Fabric 移植，GPL-3.0。
上游是 [Sh1roCu/TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)（1.21.1 Fabric），
原始项目是 [MCModderAnchor/TACZ](https://github.com/MCModderAnchor/TACZ)。

**本仓库有三条并行分支，这是所有规则的根源：**

| 分支 | Minecraft | Java | 混淆 |
|---|---|---|---|
| `26.2(main)`（默认） | 26.2 | **25** | 否 |
| `26.1.2` | 26.1.2 | **25** | 否 |
| `1.21.11` | 1.21.11 | **21** | **是（Loom remap 模式）** |

---

## 1. 【强制】改版本号 = 必须同步改 README

**这是本仓库最容易出错、也最容易被遗忘的一条。已经真实发生过两次。**

只要你改动了任一分支的 `gradle.properties` 中的 `mod_version` 或任何依赖版本，
**必须在同一次改动中**同步更新该分支根 `README.md` 的以下位置：

1. 顶部「本分支当前源码版本为 **`1.1.8+fabric.<mc>.R<n>`**」
2. 「> 仓库源码已使用 **R<n>** 版本号」提示行
3. §1 支持环境表里的「**R<n>** 构建使用 ...」与「本 mod」行
4. 依赖版本变了的话，支持环境表对应格子（Fabric API / Forge Config API Port / Java）
5. 「选择你的 Minecraft 版本」导航表中指向新 Release tag 的链接
6. SemVer 说明段里的 `+fabric.<mc>.R<n>` 构建元数据示例

### 允许分步提交，但不允许分支「停在不一致状态」

先改 `gradle.properties`、下一个 commit 再补 README，是**完全正常**的工作方式，
工具链不会阻止你。约束只有一条：

> **在该分支被合并 / 发布之前，必须回到一致状态。**

**自检命令：**

```bash
bash scripts/check_release_consistency.sh            # 工作区，只报告（恒退出 0）
bash scripts/check_release_consistency.sh --all      # 远端三条分支
bash scripts/check_release_consistency.sh --strict   # 发布门禁，不一致则退出 1
```

日常改动用默认模式（不打断节奏）；**合并前与发布前用 `--strict`**，
它返回非 0 就说明还没收尾，此时**不得声称任务完成，也不得发布**。

三道自动防线（互为补充）：

| 层 | 行为 | 用途 |
|---|---|---|
| `AGENTS.md`（本文件） | AI 会话开始时自动读取 | 让规则被看见 |
| `.githooks/pre-commit` | **只提醒，永不阻断** | 分步提交途中不忘事 |
| CI（`--strict`） | PR 上打红叉 | 合并前的真正门禁 |

启用本地 hook（**整个仓库只需运行一次，所有分支通用**）：

```bash
bash scripts/install-hooks.sh
```

它把 hook 装进 `.git/hooks/`，该目录不属于任何分支，切分支照常生效。
检查脚本本体也只需存在于默认分支 `26.2(main)`——当前分支没有时，hook 会自动
从默认分支读取。**因此不需要把 `scripts/` 复制到每条分支。**

> 早期版本用 `git config core.hooksPath .githooks`，但 `.githooks/` 本身是分支内容，
> 切到没有该目录的分支后提醒会静默失效，已弃用。

CI 模板位于 `docs/publish/ci/consistency.yml`，需由仓库所有者复制到
`.github/workflows/consistency.yml`（AI 助手的 token 通常无 `workflows` 权限，无法代劳）。

### 历史事故（不要重蹈覆辙）

- `26.1.2` 分支 README 曾同时写着 R1（正文、提示行、环境表）和 R2（导航表、gradle），
  同一页面自相矛盾。
- `1.21.11` 分支 README 的 Arcana 段落曾写成「不能视为本 Fabric **26.x** 端口的受支持内容」，
  是从 26.x 分支复制后忘记改。**跨分支复制 README 段落时必须逐句检查版本号与分支名。**

---

## 2. 【强制】不得声称未实际实现的东西

写 CHANGELOG、Release notes、README，或起草任何对外沟通（尤其是给上游的 issue / PR）时：

- **区分「绕开」与「修复」。** 禁用一个兼容层、跳过一段逻辑、加一个默认关闭的开关，
  都**不能**写成 "fixed"。
  例：`ARCompat.shouldAccelerate()` 直接 `return false`，这是**禁用**加速渲染兼容，
  不是修复它的冲突。
- **区分「本仓库的工作」与「别人在 issue 里贴的分析」。** 文件名相同不代表是同一份工作。
  声称修复了某个 upstream issue 之前，先确认分析的实际作者：
  ```bash
  gh issue view <n> -R Sh1roCu/TACZ-Refabricated --json comments \
    -q '.comments[]|.author.login'
  ```
- 涉及兼容性、实测结果、性能数据的表述，**没有实际验证过就不要写**，
  或明确标注为「未执行的实机矩阵」。

### 历史事故

给上游 issue 的草稿曾写「我这边有 #55 高倍镜黑屏的分析与修复，可以开 PR」。
实际上那份分析是报告者 beibeigao-ops 本人发的，而本仓库对加速渲染是直接禁用。
若照此发出，上游一查即穿。

---

## 3. 分支纪律

- **不要跨分支复制文件后不做适配。** 三条分支的 Java 版本、混淆状态、依赖版本、
  瞄具实现（26.x 是掩码/stencil 路径，1.21.11 是深度孔径路径）都不同。
- `docs/README_26_1_2.md` 与 `docs/README_1_21_11.md` 是**对应分支根 README 的替换版**，
  改动 `26.2(main)` 的 README 结构时，这两份也要同步。
- 1.21.11 是混淆版本：mixin 目标**不得**使用 `lambda$xxx$N` 这类 javac 合成名，
  必须用 intermediary 的 `method_NNNNN`。该分支提供了校验脚本：
  ```bash
  python3 docs/verify_mixin_targets.py
  python3 docs/verify_shader_imports.py
  ```
  **编译通过不等于运行期安全**——该分支移植期间崩过 5 次，每次都能编译通过。

---

## 4. 发布流程

完整清单见 [`docs/publish/RELEASE_CHECKLIST.md`](docs/publish/RELEASE_CHECKLIST.md)。
发布前**必须**跑通：

```bash
bash scripts/check_release_consistency.sh --links
```

Release 正文首行必须是环境行（MC + 加载器 + Java + Fabric API + Forge Config API Port）。

---

## 5. 对外沟通

- 玩家反馈一律引导到**本仓库** Issue，不要引导到 TaCZ 或 LRTactical 原作者处。
- 上游 [Sh1roCu/TACZ-Refabricated#57](https://github.com/Sh1roCu/TACZ-Refabricated/issues/57)
  是维护者置顶的本项目入口，措辞已协调好，改动前请谨慎。
- 许可：代码 GPL-3.0；默认枪包资源 **CC BY-NC-ND 4.0**（ND 意味着**改动后再分发受限**，
  这是 Modrinth 发布的前置障碍）；MAE 为 MIT。
  详见 [`LICENSES.md`](LICENSES.md) 与
  [`docs/publish/DISCOVERABILITY_CHECKLIST.md`](docs/publish/DISCOVERABILITY_CHECKLIST.md) §4。

---

## 6. 会话结束前的自检

在向用户报告「完成」之前，逐条确认：

- [ ] 若动过 `gradle.properties` 版本 → 已同步 README 全部 6 处，且自检脚本通过
- [ ] 若写过 CHANGELOG / release notes / 对外文案 → 无未经验证的修复声明
- [ ] 若跨分支复制过内容 → 已逐句核对版本号、分支名、Java 版本
- [ ] 若改动涉及 1.21.11 → 已考虑混淆映射，必要时跑过两个 verify 脚本
