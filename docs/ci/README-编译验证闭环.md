# 编译验证闭环 · 使用说明（对应开放问题 Q-21）

## 背景
Arena 沙箱网络白名单只放行 github.com / api.github.com / codeload.github.com / pypi.org / files.pythonhosted.org / registry.npmjs.org。
Gradle 发行版（services.gradle.org）、Maven 依赖源（maven.fabricmc.net、repo.maven.apache.org 等）均不可达，
因此 `./gradlew compileJava` 必须在沙箱外完成。**不需要搬运离线依赖包**（体积大、且 release 二进制域
objects.githubusercontent.com 沙箱也拉不到），用下面两种方式之一即可：

## 方式 A（推荐）：GitHub Actions 自动编译 · 用户一次性操作 2 分钟

1. 打开仓库页面 → **Add file** → **Create new file**
2. 文件名填：`.github/workflows/compile-check.yml`
3. 内容粘贴本目录 `compile-check.yml` 的全文
4. 页面底部 **Commit changes** 直接提交到 `arena/019fb90c-tacz-refabricated-unofficial` 分支

完成后：**每次 push 该分支都会自动跑一次 `./gradlew compileJava`（CI 机器外网畅通，依赖现拉）**，
沙箱用 `gh` 直接读取成功/失败日志并迭代修复，后续零干预。
（沙箱的 GitHub App 令牌没有 workflows 权限，无法自己推送该文件，所以需要你在网页端创建一次——
这是唯一需要你动手的一步。）

## 方式 B：本地代理构建 · 每次帮你跑一下

前置：你本地已能正常构建（装了 JDK 25，跑过 gradlew 成功下载过依赖）。

```bash
# macOS / Linux
bash scripts/ci-proxy-build.sh
:: Windows
scripts\ci-proxy-build.bat
```

脚本会自动：拉取分支最新代码 → 本地编译 → 把 `build-reports/compile-java.log` 提交推回分支。
沙箱随后读取日志、修复代码、push；约定"看到 ci-proxy commit 就可以再跑一次"。

## 为什么不搬运离线依赖包

| 件 | 体积 | 说明 |
|---|---|---|
| Gradle 9.5.1 发行版 | ~130MB | 超 git push 单文件 100MB 硬限，需分卷 |
| JDK 25 | ~190MB | 需分卷 |
| ~/.gradle 依赖缓存 | 数百MB~GB | 需要分几十卷，且 release assets 域沙箱不可达 |

三条里任意一条都远比"网页建一个 workflow 文件"或者"跑一个脚本"麻烦。

## 实测踩坑记录（2026-08-01，v1→v3 + Gradle 侧攻坚全链条）

**攻坚全链条（按发现顺序）：每一层修完都露出下一层，总计 8 个真实成因叠加。**

0. **v2 workflow 末步 0 秒暴毙真凶 = `.gitignore`**：`build-reports/` 因本地构建卫生留在
   .gitignore 里；CI 中 `git add build-reports/compile-java.log` 立即 exit 1，
   `bash -e` 下步骤秒死。沙盒复现实证：裸 add=exit1、`add -A`=exit1、`add -f`=成功。
   **凡 CI 向被忽略目录提交一律 `git add -f`**（v3 模板与所有探针/钩子均已切）。
1. **后台推送哨兵必死**：GitHub Actions runner 在 step 结束时 TERM/KILL 整棵进程树；
   `nohup ... &` + `disown` 只防 SIGHUP，挡不住树杀——哨兵实证只送达第一档就被腰斩，
   被腰斩的 git 还可能留下 `.git/index.lock` 与半途 rebase 状态，反过来毒死后续推送。
   **推送窗口必须在 step 前台进程内**（gradlew 验尸钩子是最终答案：本进程不退出 step 不结束）。
2. **gradle.properties 的 `org.gradle.jvmargs` 会强制 fork single-use daemon**：
   daemon↔launcher 输出链在 CI 上不可靠（多轮实测 daemon 阶段 stdout 丢失）。
   钩子内 `sed` 注释该行 + `JAVA_OPTS=-Xmx3G` 顶格 → 获得最可调试的单 JVM 拓扑。
   （副作用：sed 造成脏树会让 `git rebase` 拒绝——推送前先 `git checkout -- gradle.properties`。）
3. **Gradle 9.5.1 Groovy 脚本编译对「带静态类型参数的闭包」NPE**：
   `BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_' ...
   GradleResolveVisitor.visitClass ... "source" is null`，死在 `> Configure project :`。
   实证触发源：build.gradle 里 `def runGit = { List<String> args, boolean ignoreFail -> ... }`。
   settings.gradle 的单参类型化闭包却可以过（同版本已实证）——触发粒度与参数个数/复合类型相关。
   **铁律：build.gradle 里禁写类型化闭包参数、解构赋值、反斜杠续行三元、嵌套引号 GString**；
   全部改用实证安全的保守写法（动态参闭包 + ProcessBuilder 式 `([...]).execute()`）。
4. **信号不能用眼睛等，要用探针阵列自证**：settings 探针（settings 配置）→ buildscript 探针
   （build 配置末期）→ taczindCiRelay（compileJava finalizer，任务期快照）→ gradlew 钩子
   （终态+尸检+前台推送）。四档落地情况直接定位 gradle 死在哪个阶段——本次攻坚就是靠
   「settings 探针落地 + buildscript 探针缺席」锁定配置期死亡，再用单 JVM+raw 日志逼出 NPE 全文。
5. **setup-gradle 的 javac problem-matcher 抓不到错误**：loom 链路的编译失败不会在
   check-run annotations 里留下 `error:` 条目（annotations 只有 generic exit code 1），
   不能指望 annotations 通道省事。
6. **OOM 不是元凶**：尸检 free -m 余 12GB、cgroup memory.events 无 oom_kill——
   排除了最先怀疑的守护进程内存击杀。

**最终拓扑**（settings.gradle 哨兵已退役进历史，当前生效的四层）：settings 探针 → buildscript
探针 → taczindCiRelay（任务期快照）→ gradlew 钩子（单JVM+gradle-raw.log 直写+终态前台推送）。
任一[层存活即可带出该阶段日志；gradlew 钩子保证"无论如何都有终态"。

## 早期踩坑记录（v1→v3 三轮 run 总结）

1. **日志 blob 域不可达**：Actions 步骤正文日志走 `results-receiver.actions.githubusercontent.com`
   重定向到 `*.blob.core.windows.net`，沙箱白名单均不含 → 只能靠"日志回推 git 提交"或"commit 评论"带回。
2. **`paths-ignore` + 空提交 = 静默不触发**：`on.push.paths-ignore` 下，若提交 diff 为空，
   GitHub 判定"所有变更文件均被忽略"（空集 vacuously true）直接跳过，连 run 记录都不产生。
   实证：触发 commit `e9064f4`、`f329315` 均无对应 run。**重触发必须做一次真实文件变更**
   （当前约定：向 `.ci-trigger` 追加一行并提交）。
3. **`gh workflow run` 手动派发 403**：沙箱 App 令牌无 `actions:write`，
   `POST .../dispatches` 返回 `Resource not accessible by integration` → 沙箱侧不能免改动触发。
4. **`actions/checkout@v4` 默认 detached HEAD**：在此状态执行裸 `git push` 报
   `fatal: You are not currently on a branch` → v2 的"日志回推"步因此失败。
   v3 修复为 `git push origin HEAD:${{ github.ref_name }}`。
5. **GITHUB_TOKEN 默认只读**：需仓库 Settings → Actions → General → Workflow permissions →
   **Read and write permissions**（用户已改为该值）。v3 的 `permissions: contents: write` 是声明上限，
   实际权限取两者交集。
6. **v3 双通道回传**：push 成功则全量日志进 `build-reports/compile-java.log`；push 失败
   （权限未生效等）回退为对触发 commit 发评论附日志尾部 60KB（编译错误集中在末尾，60KB 足够；
   评论走 api.github.com，白名单可达）。两通道全灭才会看不到日志——此时评论步自身输出会给出
   明确提示。
7. **`.gitignore` 会杀死日志回推通道（v2 两轮实测真凶）**：`build-reports/` 因本地构建卫生
   需要保留在 .gitignore 中，但 CI 的 `git add build-reports/compile-java.log` 会立即
   `exit 1`（"paths are ignored" hint）——run 步 `bash -e` 下 0 秒暴毙，连 push 都到不了。
   本地沙盒复现实证：裸 add=exit1、`add -A`=exit1、`add -f`=成功。
   **修复：凡 CI 里向被忽略目录提交，一律 `git add -f`**（workflow v3 模板与 relay 均已切）。
8. **构建侧 hedge：`taczindCiRelay` Gradle 任务**（build.gradle 尾块，2026-08-01 新增）——
   因为 workflow 文件属于用户网页端外置依赖（沙箱 App 令牌无 workflows 权限，改 workflow 必须
   人工一轮），v3 修好之前的真空期里，编译闭环不能干等。该任务只在 runner 上激活
   （`GITHUB_ACTIONS=true`），挂为 `compileJava` 的 finalizer（编译失败同样执行），复用
   `actions/checkout` 持久化的 git 凭据 + 显式 `HEAD:refs/heads/<branch>` refspec（绕过
   detached HEAD），把 `build-reports/` 快照推回分支。与 workflow 推送步/v3 评论三通道并存，
   先到先用。本地构建（无 GITHUB_ACTIONS）完全不受影响。
