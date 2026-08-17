# 发布检查清单（每次发布新版本必做）

> **用法**：每次发新版本时，在 Release 对应的 tracking issue 里粘贴本文件的
> 「§0 速用版」勾选框，逐条打勾。**未全部打勾不发 Release。**
>
> 本清单的存在理由：本仓库有 **三条并行分支**（`26.2(main)` / `26.1.2` / `1.21.11`），
> 三份 README、三套依赖版本、三个 Release tag。任何一处漏改，玩家看到的就是错的版本号
> 或死链，而从上游置顶 issue #57 过来的新玩家第一眼就会撞上。

---

## §0 速用版（复制到 issue 里勾选）

```markdown
### 发布前
- [ ] 三分支 gradle.properties 的 mod_version 已更新到本次版本
- [ ] 三份根 README 的「本分支当前源码版本」与实际 mod_version 一致
- [ ] 三份根 README 的「R? 版本号」提示行与实际一致
- [ ] 三份根 README 的支持环境表（MC/Loader/Java/Fabric API/Config Port）与 gradle.properties 一致
- [ ] 版本导航表存在于全部三份 README，且指向本次新 tag
- [ ] 导航表 6 条链接全部 HTTP 200（见 §3 脚本）
- [ ] `bash scripts/check_release_consistency.sh --all --strict` 退出码为 0
- [ ] CHANGELOG 已写，且未声称任何未实际实现的修复

### 发布时
- [ ] 三个 Release 的 tag 命名遵循 `<MC版本>_R<N>`
- [ ] 三个 Release 标题含英文与 MC 版本关键词
- [ ] 每个 Release 正文首行写清 MC + 加载器 + 两项硬依赖版本
- [ ] jar 文件名可辨识 MC 版本，未上传错分支产物

### 发布后
- [ ] 导航表已指向新 tag 并再次跑通 §3 链接校验
- [ ] Releases 页 Latest 标记落在预期版本上
```

---

## §1 三分支一致性（最容易漏）

三条分支的元数据必须各自自洽。**不要跨分支复制粘贴后不改**——
本仓库已经真实发生过两次这类错误：

1. `1.21.11` 分支 README 的 Arcana 段落曾写成「不能视为本 Fabric **26.x** 端口的
   受支持内容」，是从 26.x 复制来忘了改（已在 `docs/README_1_21_11.md` 中修正）。
2. `26.1.2` 分支 README 顶部写「本分支当前源码版本为 `1.1.8+fabric.26.1.2.**R1**`」
   与「仓库源码已使用 **R1** 版本号」，但 `gradle.properties` 里是
   `mod_version=1.1.8+fabric.26.1.2.**R2**`，且导航表指向 `26.1.2_R2`。
   **同一页面内 R1 与 R2 并存，需要修正为 R2。**

### 各分支基准值（截至 2026-08-17 核对）

| 项目 | `26.2(main)` | `26.1.2` | `1.21.11` |
|---|---|---|---|
| `minecraft_version` | 26.2 | 26.1.2 | 1.21.11 |
| `loader_version` | 0.19.3 | 0.19.3 | 0.19.3 |
| `fabric_version` | 0.155.2+26.2 | 0.155.2+26.1.2 | 0.141.6+1.21.11 |
| `mod_version` | 1.1.8+fabric.26.2.R2 | 1.1.8+fabric.26.1.2.R2 | 1.1.8+fabric.1.21.11.R2 |
| Forge Config API Port | 26.2.1+ | 见该分支 README | 21.11.1+ |
| **Java** | **25** | **25** | **21** |
| 混淆 | 否 | 否 | **是（Loom remap 模式）** |

> Java 版本和混淆状态是三分支最大的差异点，改 README 支持环境表时最容易照抄错。

### 需要逐分支核对的文件

- `gradle.properties` — `mod_version` 及全部依赖版本
- 根 `README.md` — 顶部版本句、「R? 版本号」提示行、§1 支持环境表、版本导航表
- `docs/` 下对应的 changelog

---

## §2 README 版本导航表

三份根 README 都必须包含下面这张表，**且每次发版后更新 tag**。
只有「本页面对应 **X** 分支」一行随分支变化。

```markdown
### 选择你的 Minecraft 版本 / Pick your Minecraft version

| Minecraft | 源码分支 | 最新 Release |
|---|---|---|
| **26.2** | [`26.2(main)`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.2%28main%29) | [`26.2_R2`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases/tag/26.2_R2) |
| **26.1.2** | [`26.1.2`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.1.2) | [`26.1.2_R2`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases/tag/26.1.2_R2) |
| **1.21.11** | [`1.21.11`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/1.21.11) | [`1.21.11_R2`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases/tag/1.21.11_R2) |
```

> ⚠️ `26.2(main)` 的括号必须转义成 `26.2%28main%29`，否则 Markdown 链接会被截断。

---

## §3 链接校验（发布前后各跑一次）

> **只需安装一次**：`bash scripts/install-hooks.sh`（装进 `.git/hooks/`，
> 所有分支通用）。脚本本体只需存在于默认分支 `26.2(main)`，
> **不必复制到 `26.1.2` 和 `1.21.11`**。
>
> **分步改动不受影响**：日常提交时 `pre-commit` 只提醒不阻断，
> 默认模式的脚本也恒返回 0。**合并前 / 发布前**改用 `--strict`，
> 它返回非 0 即代表尚未收尾。

先跑版本一致性门禁：

```bash
bash scripts/check_release_consistency.sh --strict        # 工作区
bash scripts/check_release_consistency.sh --all --strict  # 远端三条分支
```

再跑链接可达性。在任意分支 checkout 下执行，全部返回 200 才算通过：

```bash
BASE=https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial
R_262=26.2_R2; R_2612=26.1.2_R2; R_12111=1.21.11_R2   # 改成本次新 tag

for u in \
  "$BASE/tree/26.2%28main%29" \
  "$BASE/tree/26.1.2" \
  "$BASE/tree/1.21.11" \
  "$BASE/releases/tag/$R_262" \
  "$BASE/releases/tag/$R_2612" \
  "$BASE/releases/tag/$R_12111"; do
  printf "%s  %s\n" "$(curl -s -o /dev/null -w '%{http_code}' "$u")" "$u"
done
```

顺带校验三分支 README 与 gradle.properties 的版本号是否自洽：

```bash
for b in '26.2(main)' 26.1.2 1.21.11; do
  echo "=== $b ==="
  git show "origin/$b:gradle.properties" | grep -E "^mod_version"
  git show "origin/$b:README.md" | grep -oE "1\.1\.8\+fabric\.[0-9.]+\.R[0-9]+" | sort -u
  git show "origin/$b:README.md" | grep -oE "已使用 R[0-9]+ 版本号"
done
```

三行输出的 R 号必须一致。**不一致就是 §1 那类错误。**

---

## §4 Release 页面规范

**tag 命名**：`<MC版本>_R<N>`，例如 `26.2_R3`、`1.21.11_R3`。保持现有格式不要变。

**标题**：现有的 `26.2的mod文件_R2` 对中文玩家可读，但英文玩家和搜索引擎命中不到。
建议改成双语：

```
TaCZ Refabricated 26.2 R3 — Minecraft 26.2 Fabric / 26.2的mod文件_R3
```

**正文首行**必须是环境行，让玩家不点开就知道装不装得上：

```markdown
**Minecraft 26.2 · Fabric Loader 0.19.3+ · Java 25+ · Fabric API 0.155.2+26.2 · Forge Config API Port 26.2.1+（硬依赖）**

> 非官方社区移植，非 TaCZ 官方发布。问题请提交本仓库 Issue，勿提交给 TaCZ / LRTactical 原作者。
```

1.21.11 的环境行注意换成 **Java 21+**、`0.141.6+1.21.11`、`21.11.1+`。

**产物核对**：确认上传的 jar 来自对应分支，文件名能辨识 MC 版本。
三分支 `archives_base_name` 都是 `TACZ-Refabricated`，只靠文件名前缀区分不了分支，
传错的后果是玩家装上直接崩，且崩溃日志看不出是版本装错。

---

## §5 内容真实性（新增，源自一次真实事故）

写 CHANGELOG、Release notes 或对外沟通（尤其是给上游的 issue / PR）时：

- **不要声称未实际实现的修复。** 曾有一版给上游的 issue 草稿写了「我这边有 #55
  高倍镜黑屏的分析与修复，可以开 PR」，实际上那份分析是报告者本人发的，而本仓库对
  加速渲染是 `ARCompat.shouldAccelerate()` 直接 `return false` 的**禁用**，不是修复。
- **区分「绕开」与「修复」。** 禁用一个兼容层、跳过一段逻辑、加一个开关默认关闭，
  都不能写成 fixed。
- **区分「本仓库的工作」与「他人在 issue 里贴的分析」。** 文件名相同不代表是同一份工作。
- 声称修复了某个上游 issue 前，用 `gh issue view <n> -R <repo> --json comments` 确认
  分析的实际作者。

---

## §6 一次性事项（做完即可，不必每次重复）

- [ ] 仓库 About → Description 与 Topics（当前 Topics 为空，最影响站内搜索）
      文本见 [`DISCOVERABILITY_CHECKLIST.md`](DISCOVERABILITY_CHECKLIST.md) §2
- [ ] `.github/ISSUE_TEMPLATE/` 三个模板文件（bug / compat / config）
- [ ] Modrinth 发布前解决默认枪包 CC BY-NC-ND 的 ND/NC 前提，见
      [`DISCOVERABILITY_CHECKLIST.md`](DISCOVERABILITY_CHECKLIST.md) §4
