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

## 实测踩坑记录（2026-08-01，v1→v3 三轮 run 总结）

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
