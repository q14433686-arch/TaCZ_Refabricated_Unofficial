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
