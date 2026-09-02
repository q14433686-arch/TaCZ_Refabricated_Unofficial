# CI 上线总清单 · 六条线逐分支手动动作（2026-09-02 实测盘点）

> **本文回答的问题**：「构建出 jar 的 CI 是不是只有 26.2 那条线有？剩下五条线我分别要手动放什么文件进去？」
> **结论**：是。产 jar 的是 `.github/workflows/build.yml`，**全家六条线里只有 refab `26.2(main)` 装了它**；
> 其余五条线要么只有 compile-check（只跑 `compileJava`，不产 jar），要么连 v4 触发器都没补。
> 五条线里，**refab 两条的文件已经躺在各自分支的 `docs/ci/` 暂存区，只差你去粘贴**；
> **renov 三条什么都没准备过，本轮已代拟好**，放在本仓 `docs/ci/pending/TaCZ_Renovated/`。
>
> 所有「现役 / 暂存 / 缺失」状态都是 2026-09-02 用 `gh api contents` 与 `git ls-tree`
> **逐分支实拉核对**的，不是记忆；静态校验还在各分支真实文件上**预演**过（§5）。

---

## 0. 先答疑：jar 是谁构建的，谁能下载

* **不是我构建的。** 沙箱里没有 Minecraft 工件、没有 JDK 依赖缓存，跑不了 `gradlew`。
  我做的只是：推分支 → GitHub Actions 跑 `.github/workflows/build.yml` →
  我用 `gh api` 读回结果（`build-reports/compile-java.log` 是 compile-check 回推的日志）。
* **产物在哪**：Actions run 页面右下角 Artifacts。本轮那次是
  [run 33623054002](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/33623054002)
  的 `TACZ-Refabricated-32af402…`（57 MB，`retention-days: 14`）。
* **门槛**：Actions artifact **需要登录 GitHub 账号才能下载**（公开仓也一样）。
  所以「发个链接给玩家/征测者直接下」这件事，artifact 只对**有账号的人**成立；
  要对匿名公网开放，得把 jar 挂到 **Release 资产**上（§4 的可选件 `release.yml`）。
* **为什么别的线没有 jar**：workflow 文件是**按分支生效**的 —— push 事件只会跑
  「被推的那个分支上存在的 workflow」。所以每条线都要各自装一份，装一次即可。

---

## 1. 六条线现状总表（2026-09-02 实拉）

| 线 | 仓库 | `.github/workflows/` 现役 | 暂存区（待上线稿） | 能产 jar？ |
|---|---|---|---|---|
| **refab 26.2(main)** | `TaCZ_Refabricated_Unofficial` | `build.yml`、`compile-check.yml`(v4)、`consistency.yml`(v2) + 3 个 ISSUE_TEMPLATE | `docs/ci/`（README + 三件镜像） | ✅ 唯一一条 |
| **refab 26.1.2** | 同上 | **只有** `compile-check-2612.yml`(v3：仅 `arena/**`、无 concurrency、无 PR/主分支) | `docs/ci/`（README + `build.yml` + `compile-check.yml` v4 + `consistency.yml`，均已按本分支适配）<br>`docs/publish/ci/`（`compile-check-2612.yml` + `CHECKS_TO_APPEND_20260901.md`） | ❌ |
| **refab 1.21.11** | 同上 | **只有** `compile-check.yml`(旧版：仅 `arena/**`、无 concurrency) | `docs/ci/`（README + `build.yml`(Java 21、5 项静态校验、consistency 自带回退) + `compile-check.yml` v4 + `consistency.yml`） | ❌ |
| **renov 26.2** | `TaCZ_Renovated`（NeoForge） | `compile-check.yml`(v3+paths-ignore)、`consistency.yml`(`branches: ["**"]` + PR + `--strict`) | `docs/ci/`（README + `compile-check.yml` 待上线版 + **`changelog.yml` 待上线**） | ❌ |
| **renov 26.1.2** | 同上 | `compile-check-2612.yml`、`consistency.yml` | **无 `docs/ci/`** | ❌ |
| **renov 1.21.11** | 同上 | `compile-check.yml`(Java 21)、`consistency.yml` | **无 `docs/ci/`** | ❌ |

三个共性缺口：

1. **`build.yml` 五条线全缺** ⇒ 全家族只有一条线能「发链接征测」。
2. **compile-check 的 v4 升级四条线没跟上**（refab 26.1.2 / refab 1.21.11 / renov 三线）：
   主分支 push 与 PR 不编译 ⇒ **合并 commit 从来没人编过**（26.2 侧就是发现这个盲区才做的 v4）；
   且没有 `concurrency` ⇒ 连发 push 时白烧配额。
3. **consistency 只有 renov 三线 + refab 26.2 是严的**：refab 26.1.2 / 1.21.11 的
   `consistency.yml` 还躺在暂存区没上线，那两条线现在改版本号不同步 README **不会红**。

---

## 2. 逐分支手动动作清单

> 上线手法全家族统一（沙箱 token 无 `workflows` 权限，只能你动手）：
> GitHub 网页 → 切到**目标分支** → `.github/workflows/` → `Add file → Create new file`（或点开现有文件 `Edit`）
> → 粘贴下文指的源文件全文 → commit **到该分支**。
> ⚠️ 一个 workflow 只在「它存在于被推分支」时才生效，所以六条线要各粘一次，不能只在默认分支放。

### B0 · refab `26.2(main)`（本线，无缺失，三个小尾巴）

| # | 事项 | 处置 |
|---|---|---|
| 1 | `docs/ci/README.md` 的「当前待上线清单（2026-08-31）」已过期：写着 `consistency.yml` 待上线 v2、`build.yml` 待修 artifact 名，**实际两件都已上线且已修** | 本轮已改（见本 PR 提交） |
| 2 | `AGENTS.md` §4 写「CI 模板位于 `docs/publish/ci/consistency.yml`」——本分支实际路径是 `docs/ci/consistency.yml`（`docs/publish/ci/` 只在 26.1.2 分支存在，是历史遗留） | 本轮已改 AGENTS.md 那一句 |
| 3 | **孤儿 mixin 配置**：`src/main/resources/tacz.compat.acceleratedrendering.mixins.json` 没有被 `fabric.mod.json` 注册 ⇒ 永不生效。1.21.11 线在 R3 已把同名文件**删掉**（其 `docs/ci/README.md` 有记录），26.2 线还留着 | **需你裁定**：26.2 的 AR 兼容是 no-op 空壳（`ARCompatImpl` 注释写着「等 acceleratedrendering 出 26.2 版再恢复」），且 `ARCompatMixinPlugin` 也在。两个选项：①照 1.21.11 删掉，等 AR 移植时连配置一起回来；②保留，但**将来给 26.2 加「mixin 注册性」检查时必须把它写进白名单**，否则 CI 立刻红。现状不影响任何流程（26.2 的 `build.yml` 没有注册性检查） |

### B1 · refab `26.1.2`（4 个动作，文件都已在该分支暂存区）

| # | 动作 | 源（该分支内） | 目标 |
|---|---|---|---|
| 1 | 新建 build | `docs/ci/build.yml` | `.github/workflows/build.yml` |
| 2 | 新建 consistency | `docs/ci/consistency.yml` | `.github/workflows/consistency.yml` |
| 3 | 升级 compile-check 到 v4 | `docs/ci/compile-check.yml` | `.github/workflows/compile-check.yml`（新建） |
| 4 | **删掉旧件** | — | `.github/workflows/compile-check-2612.yml` |

⚠️ **动作 3 与 4 必须一起做**：两个文件的 `name:` 都是 `compile-check`，
并存会每轮 push 跑两遍编译、回推两条 `ci-log` 提交（配额翻倍 + 历史噪音）。
删文件也在网页端做（打开该文件 → 右上 `…` → `Delete file`）。

可选（该分支自己已经写好待办）：按 `docs/publish/ci/CHECKS_TO_APPEND_20260901.md`
把三条静态检查追加进 compile-check —— 脚本 `docs/check_mixin_registration.py` /
`docs/check_lang_keys.py` / `docs/check_mesh_config_parity.py` **都已在该分支**，零依赖。

**首跑预期：绿。** 已对该分支真实树预演（§5）：7 个 mixin json 全部注册且类都存在、
`tacz`+`lrtactical` 的 en_us↔zh_cn 齐平、`scripts/check_release_consistency.sh` 已在该分支
（其 `docs/ci/README.md` 记载 2026-09-02 已镜像过去）。Java 25。

### B2 · refab `1.21.11`（4 个动作 + 1 个前置补丁）

| # | 动作 | 源 | 目标 |
|---|---|---|---|
| 0 | **前置**：把 `docs/verify_mixin_targets.py` 改成可移植 | 本仓 `docs/ci/pending/refab-1.21.11/verify-mixin-targets-portable.patch` | 该分支 `docs/verify_mixin_targets.py` |
| 1 | 新建 build（并按片段追加两条运行期校验） | 该分支 `docs/ci/build.yml` + 本仓 `docs/ci/pending/refab-1.21.11/build-yml-verify-steps.md` | `.github/workflows/build.yml` |
| 2 | 新建 consistency | 该分支 `docs/ci/consistency.yml` | `.github/workflows/consistency.yml` |
| 3 | 升级 compile-check 到 v4（**同名替换**，无双跑风险） | 该分支 `docs/ci/compile-check.yml` | `.github/workflows/compile-check.yml` |
| 4 | 建议：镜像一致性脚本本体 | `26.2(main)` 的 `scripts/check_release_consistency.sh` | 该分支同路径 |

* 动作 0 不做也能上线 build.yml（暂存稿的静态检查不含那两个 verify 脚本），
  但那样 AGENTS.md §3 的「编译通过不等于运行期安全」在 CI 上就仍是空白 ——
  该分支为此崩过 5 次。补丁已在该分支真实 worktree 上 `git apply --check` + 实落验证过。
* 动作 4 不做也不会红：该分支暂存的 `build.yml` 与 `consistency.yml` 都带
  「本分支没有就从默认分支 `git show` 取，取不到就 `::notice::` 跳过」的回退。
  但回退版**跳过一次就没人发现**，所以建议直接镜像（脚本分支无关，26.1.2 线已这么做）。
* **首跑预期**：静态检查绿（7 个 mixin json 全注册、lang 齐平）；`build` 会跑 `remapJar`
  ⇒ 明显慢于 compile-check；`verify_mixin_targets.py` 首跑耗时未知（逐类 `javap`），
  建议第一轮先 `continue-on-error: true` 观察。Java **21**（不是 25）。

### B3 / B4 / B5 · renov `26.2` / `26.1.2` / `1.21.11`（NeoForge，三条线动作相同）

源文件本轮已代拟好，在**本仓**（不是 renov 仓）：
`docs/ci/pending/TaCZ_Renovated/<MC>/build.yml` 与 `…/compile-check.yml`。

| # | 动作 | 源（本仓路径） | 目标（renov 仓对应分支） |
|---|---|---|---|
| 1 | **新建 build**（这是 renov 全家族第一次有 jar 产物） | `docs/ci/pending/TaCZ_Renovated/<MC>/build.yml` | `.github/workflows/build.yml` |
| 2 | 升级 compile-check 到 v4 | `docs/ci/pending/TaCZ_Renovated/<MC>/compile-check.yml` | `.github/workflows/compile-check.yml` |
| 3 | **26.1.2 线额外**：删旧件 | — | `.github/workflows/compile-check-2612.yml`（与 B1 同理，避免双跑） |
| 4 | **26.2 线额外**：把它自己暂存的 `changelog.yml` 建起来 | renov 仓 `docs/ci/changelog.yml` | `.github/workflows/changelog.yml` |

`<MC>` = `26.2` / `26.1.2` / `1.21.11`。三份 build.yml 的差异只有 Java（25/25/21）、
artifact 名里的 MC 版本、触发分支名，以及 1.21.11 的 `libs/` 说明——已按分支写好，直接整份粘贴。

**NeoForge 专属注意（与 refab 侧不通用，逐条按 renov 真实树核对过）**：

* 构建插件是 `net.neoforged.moddev` 2.0.144，**没有 Loom ⇒ 没有 `remapJar`**；
  `archivesName = mod_id`，产物仍在 `build/libs/*.jar`。
* 「mixin 注册性」检查**不能**照抄 refab 版（那边读 `fabric.mod.json`）：
  renov 的 `neoforge.mods.toml` 由 `generateModMetadata` 生成、checkout 时不存在，
  所以代拟版读的是模板 `src/main/templates/META-INF/neoforge.mods.toml`。
* `gradle.properties` 里 `org.gradle.jvmargs=-Xmx512M`：`compileJava` 够用（现役 compile-check 三线都是
  `== job status: success ==`），但 `build` 还要 JarJar / sourcesJar。
  **首跑若 OOM，把该行提到 `-Xmx2G`** ——这是 renov 侧唯一的实质未知数。
* `1.21.11` 线的 `build.gradle` 在**配置期**就会因缺 `libs/PlayerAnimationLibNeoforge-*.jar` /
  `libs/controllable-neoforge-*.jar` 抛 `GradleException`；实拉确认这 5 个 jar **已提交在仓库里** ⇒
  CI 不需要下载步骤。（若将来改成不提交，必须在 checkout 之后补下载，否则连配置都过不去。）
* renov 的 `consistency.yml` 已经是 `branches: ["**"]` + PR + `--strict`，比 refab 侧更严 ⇒ **不用动**。
* **首跑预期：静态检查全绿**（预演结果见 §5），build 成败取决于上面那条内存余量。

---

## 3. 一次装完之后的家族状态

| 能力 | 装前 | 装后 |
|---|---|---|
| 有可下载的 jar 产物 | 1 / 6 条线 | 6 / 6 |
| 合并 commit 会被编译（主分支 push + PR 触发） | 2 / 6（refab 26.2、renov consistency 除外） | 6 / 6 |
| 版本号↔README 一致性守门 | 4 / 6（refab 26.2 + renov 三线） | 6 / 6 |
| 连发 push 自动取消过期 run（省配额） | 1 / 6 | 6 / 6 |
| 运行期安全校验（mixin 目标 / shader import）进 CI | 0 / 6 | 1 / 6（refab 1.21.11；这条只有混淆线需要） |

---

## 4. 可选增强（本轮**没有**代拟，需要你点头再做）

1. **`release.yml`（打 tag 自动构建并把 jar 附到 Release）**
   解决 §0 的下载门槛：artifact 要登录，Release 资产匿名可下。
   触发 `on: push: tags: ['*_R*', '*_HOTFIX*']`，跑 `gradlew build` 后
   `softprops/action-gh-release` 附 `build/libs/*.jar`。
   六条线各一份；发布纪律照 `docs/publish/RELEASE_CHECKLIST.md`（首行环境行等）。
2. **renov 侧的 `changelog.yml` 反向移植到 refab**：renov 26.2 暂存区里那个
   CHANGELOG 草稿生成器（`workflow_dispatch` + `since_ref` + `version` + 可选回推 `docs/records/`）
   refab 侧没有对应件；refab 的 release notes 现在全靠手写。
3. **artifact 保留期**：现在统一 `retention-days: 14`。若要给 NV 卡征测者更长的窗口，
   只对长期分支（非 `arena/**`）提到 30/60 天即可，工作分支维持 14 天省配额。

---

## 5. 证据与复核命令（都是 2026-09-02 实跑）

```bash
# 现役 workflow 清单（六条线）
for b in "26.2(main)" 26.1.2 1.21.11; do
  echo "[refab $b]"; gh api "repos/q14433686-arch/TaCZ_Refabricated_Unofficial/contents/.github/workflows?ref=$b" --jq '.[].name'
done
for b in 26.2 26.1.2 1.21.11; do
  echo "[renov $b]"; gh api "repos/q14433686-arch/TaCZ_Renovated/contents/.github/workflows?ref=$b" --jq '.[].name'
done

# 暂存区清单
git ls-tree -r --name-only origin/26.1.2  | grep -E '^docs/(ci|publish/ci)/'
git ls-tree -r --name-only origin/1.21.11 | grep -E '^docs/ci/'
gh api "repos/q14433686-arch/TaCZ_Renovated/contents/docs/ci?ref=26.2" --jq '.[].name'

# 静态校验预演（refab 三线：mixin 完整性/注册性 + lang 齐平 + 一致性脚本存在性）
#   结果：26.2 → 1 条（孤儿 AR 配置，见 B0-3）；26.1.2 → 0；1.21.11 → 0，但缺 check_release_consistency.sh
# renov 三线：mixin 完整性 7/7、templates 注册性 0 孤儿 0 缺失、lang tacz+lrtactical 齐平
```

* refab 三线预演用的是纯 git plumbing（`git ls-tree -r` + `git show <ref>:<path>`），
  不需要工作树，可在任意机器复现。
* renov 三线预演用 `gh api` 逐文件拉取真实内容比对（7 个 mixin json + 各自类文件 +
  4 个 lang json + `templates/neoforge.mods.toml`）。
* 代拟的 6 个 YAML：`yaml.safe_load` 通过；内嵌 python 片段 `compile()` 通过，
  并做过正反两组功能实测（详见 `pending/README.md`）。
* **未验证**：任何一条 workflow 的真实 Actions 首跑；renov `gradlew build` 的内存余量；
  refab 1.21.11 的 `verify_mixin_targets.py` 在 Actions 上的耗时。
