#!/usr/bin/env bash
# ci-proxy-build.sh —— 本地代理编译 + 日志回传（macOS / Linux）
# 用法：bash scripts/ci-proxy-build.sh
# 流程：拉取远端分支 → 本地 ./gradlew compileJava → 把日志提交推回分支
# 用途：配合 Arena 沙箱的 Q-21 编译验证闭环（沙箱无 JVM 与 Maven 网络）。
set -u
BRANCH=$(git rev-parse --abbrev-ref HEAD)
LOG=build-reports/compile-java.log
mkdir -p build-reports

echo "==> 1/4 拉取远端最新代码（origin $BRANCH）"
git pull --ff-only origin "$BRANCH" || { echo "拉取失败：请先处理本地未提交改动"; exit 1; }

echo "==> 2/4 编译（compileJava）"
if [ -f ./gradlew ]; then chmod +x gradlew; fi
./gradlew compileJava --console=plain --stacktrace > "$LOG" 2>&1
CODE=$?

echo "==> 3/4 结果: exit_code=$CODE （日志: $LOG）"
tail -5 "$LOG"

echo "==> 4/4 提交并推回分支"
git add "$LOG"
git commit -m "ci-proxy: compileJava exit=$CODE ($(date '+%Y-%m-%d %H:%M:%S %Z'))" || echo "（无变化可提交）"
git push origin "$BRANCH"

echo "完成。沙箱侧将读取 $LOG 并迭代修复。"
exit $CODE
