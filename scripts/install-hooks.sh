#!/usr/bin/env bash
# 安装版本一致性提醒 hook —— 只需运行一次，之后所有分支都生效。
#
#   bash scripts/install-hooks.sh
#
# 为什么不用 `git config core.hooksPath .githooks`？
#   因为 .githooks/ 本身是分支内容。切到没有该目录的分支时，hook 文件不存在，
#   提醒会静默失效。装进 .git/hooks/ 则完全脱离分支，不必逐分支复制。
#
# 卸载：rm .git/hooks/pre-commit

set -euo pipefail

GIT_DIR="$(git rev-parse --git-dir)"
HOOK="$GIT_DIR/hooks/pre-commit"

mkdir -p "$GIT_DIR/hooks"

cat > "$HOOK" <<'HOOK_EOF'
#!/usr/bin/env sh
# 版本一致性【提醒】——只警告，绝不阻断提交。由 scripts/install-hooks.sh 安装。
#
# 1. 分步提交（先改 gradle、再补 README）是正常工作方式，不阻止。
#    把关交给合并 / 发布前的 --strict 检查。
# 2. 本文件位于 .git/hooks/，不属于任何分支，切分支照常生效。
# 3. 检查脚本本体只需存在于默认分支；当前分支没有时自动回退读取。

changed=$(git diff --cached --name-only)
case "$changed" in
  *gradle.properties*|*README.md*) ;;
  *) exit 0 ;;
esac

SCRIPT_REL="scripts/check_release_consistency.sh"
LOG=$(mktemp 2>/dev/null || echo /tmp/_tacz_consistency.log)
TOPLEVEL=$(git rev-parse --show-toplevel 2>/dev/null || echo .)

if [ -f "$TOPLEVEL/$SCRIPT_REL" ]; then
  bash "$TOPLEVEL/$SCRIPT_REL" >"$LOG" 2>&1
else
  SRC=""
  for ref in '26.2(main)' 'origin/26.2(main)' 'HEAD'; do
    if git cat-file -e "$ref:$SCRIPT_REL" >/dev/null 2>&1; then SRC="$ref"; break; fi
  done
  if [ -z "$SRC" ]; then
    rm -f "$LOG"; exit 0
  fi
  TMP=$(mktemp 2>/dev/null || echo /tmp/_tacz_check.sh)
  git show "$SRC:$SCRIPT_REL" > "$TMP" 2>/dev/null
  bash "$TMP" >"$LOG" 2>&1
  rm -f "$TMP"
fi

findings=$(sed 's/\x1b\[[0-9;]*m//g' "$LOG" | grep -E '^[[:space:]]+FAIL')
rm -f "$LOG"

if [ -n "$findings" ]; then
  echo ""
  echo "──────────────────────────────────────────────────────────"
  echo " 提醒：README 与 gradle.properties 的版本号目前不一致。"
  echo ""
  printf '%s\n' "$findings" | sed 's/^/   /'
  echo ""
  echo " 若你正分步提交（先改版本号，稍后再补 README），可忽略本提醒。"
  echo " 合并 / 发布前请跑： bash scripts/check_release_consistency.sh --strict"
  echo " 详见 AGENTS.md 第 1 节。"
  echo "──────────────────────────────────────────────────────────"
  echo ""
fi

exit 0
HOOK_EOF

chmod +x "$HOOK"

# 清掉可能残留的、依赖分支内容的旧配置
if [ "$(git config --get core.hooksPath || true)" = ".githooks" ]; then
  git config --unset core.hooksPath
  echo "已移除旧的 core.hooksPath=.githooks 设置（它会在缺少该目录的分支上失效）。"
fi

echo "已安装：$HOOK"
echo "作用范围：本仓库所有分支。卸载：rm '$HOOK'"
