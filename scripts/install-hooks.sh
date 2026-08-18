#!/usr/bin/env bash
# 安装版本一致性提醒 hook —— 只需运行一次，之后所有分支都生效。
#
#   bash scripts/install-hooks.sh              # 安装 / 升级
#   bash scripts/install-hooks.sh --uninstall  # 卸载
#   bash scripts/install-hooks.sh --force      # 覆盖他人的 pre-commit（自动备份）
#   bash scripts/install-hooks.sh --print      # 只打印将写入的 hook，不落盘
#
# 为什么不用 `git config core.hooksPath .githooks`？
#   因为 .githooks/ 本身是分支内容。切到没有该目录的分支时，hook 文件不存在，
#   提醒会静默失效。装进 hooks 目录则完全脱离分支，不必逐分支复制。
#
# 为什么用 --git-common-dir 而不是 --git-dir？
#   linked worktree 的 --git-dir 是 .git/worktrees/<name>/，hook 只对该 worktree
#   生效。common dir 是主仓库的 .git/，一次安装覆盖全部 worktree。

set -euo pipefail

HOOK_VERSION="2.0"
MARKER="managed-by:scripts/install-hooks.sh"

MODE="install"
FORCE=0
for arg in "$@"; do
  case "$arg" in
    --uninstall) MODE="uninstall" ;;
    --print)     MODE="print" ;;
    --force)     FORCE=1 ;;
    -h|--help)
      sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      printf '未知参数：%s（用 --help 查看用法）\n' "$arg" >&2
      exit 2
      ;;
  esac
done

if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo "错误：当前目录不是 git 仓库。请在仓库内运行。" >&2
  exit 1
fi

# --git-common-dir 覆盖所有 worktree；老版本 git 回退到 --git-dir
COMMON_DIR="$(git rev-parse --git-common-dir 2>/dev/null || git rev-parse --git-dir)"
case "$COMMON_DIR" in
  /*) ;;                                        # 已是绝对路径
  *) COMMON_DIR="$(cd "$COMMON_DIR" && pwd)" ;; # 相对路径转绝对，避免 cd 后失效
esac
HOOK="$COMMON_DIR/hooks/pre-commit"

# ---------- 卸载 ----------
if [[ "$MODE" == "uninstall" ]]; then
  if [[ ! -e "$HOOK" ]]; then
    echo "未安装，无需卸载：$HOOK"
    exit 0
  fi
  if ! grep -q "$MARKER" "$HOOK" 2>/dev/null && [[ $FORCE -eq 0 ]]; then
    echo "拒绝删除：$HOOK 不是本脚本安装的。确认后可加 --force。" >&2
    exit 1
  fi
  rm -f "$HOOK"
  echo "已卸载：$HOOK"
  exit 0
fi

# ---------- 生成 hook 内容 ----------
read -r -d '' HOOK_BODY <<'HOOK_EOF' || true
#!/usr/bin/env sh
# 版本一致性【提醒】——只警告，绝不阻断提交。
# managed-by:scripts/install-hooks.sh
#
# 1. 分步提交（先改 gradle、再补 README）是正常工作方式，不阻止。
#    把关交给合并 / 发布前的 --strict 检查。
# 2. 本文件位于 .git/hooks/，不属于任何分支，切分支照常生效。
# 3. 检查脚本本体只需存在于默认分支；当前分支没有时自动回退读取。
# 4. 校验的是【暂存区内容】而非工作区，所见即所提交。
#
# 临时跳过：SKIP_VERSION_HOOK=1 git commit …   或   git commit --no-verify

[ "${SKIP_VERSION_HOOK:-0}" = "1" ] && exit 0

# 本次提交没碰这两类文件就直接放行（--quiet 有差异时返回 1）
if git diff --cached --quiet --diff-filter=ACMR -- \
     '*gradle.properties' '*README.md' 2>/dev/null; then
  exit 0
fi

TOPLEVEL=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
SCRIPT_REL="scripts/check_release_consistency.sh"

RUNNER=""
TMP=""
if [ -f "$TOPLEVEL/$SCRIPT_REL" ]; then
  RUNNER="$TOPLEVEL/$SCRIPT_REL"
else
  # 当前分支没有检查脚本时，从默认分支取一份临时使用
  for ref in '26.2(main)' 'origin/26.2(main)' 'origin/HEAD'; do
    if git cat-file -e "$ref:$SCRIPT_REL" 2>/dev/null; then
      TMP=$(mktemp "${TMPDIR:-/tmp}/tacz-check.XXXXXX") || exit 0
      if git show "$ref:$SCRIPT_REL" >"$TMP" 2>/dev/null; then
        RUNNER="$TMP"
      else
        rm -f "$TMP"
      fi
      break
    fi
  done
fi
[ -n "$RUNNER" ] || exit 0

# --porcelain 输出稳定的 "FAIL<TAB>来源<TAB>说明"，无需再剥 ANSI 颜色
findings=$(bash "$RUNNER" --staged --porcelain 2>/dev/null | grep '^FAIL' | cut -f2-)
[ -n "$TMP" ] && rm -f "$TMP"

[ -n "$findings" ] || exit 0

# 提醒写 stderr，避免污染 GUI 客户端解析的 stdout
{
  echo ""
  echo "──────────────────────────────────────────────────────────"
  echo " 提醒：README 与 gradle.properties 的版本号目前不一致。"
  echo ""
  printf '%s\n' "$findings" | sed 's/^/   /'
  echo ""
  echo " 若你正分步提交（先改版本号，稍后再补 README），可忽略本提醒。"
  echo " 合并 / 发布前请跑： bash scripts/check_release_consistency.sh --strict"
  echo " 本次跳过： SKIP_VERSION_HOOK=1 git commit …"
  echo " 详见 AGENTS.md 第 1 节。"
  echo "──────────────────────────────────────────────────────────"
  echo ""
} >&2

exit 0
HOOK_EOF

if [[ "$MODE" == "print" ]]; then
  printf '%s\n' "$HOOK_BODY"
  exit 0
fi

# ---------- 安装前的既有 hook 处理 ----------
if [[ -e "$HOOK" ]]; then
  if grep -q "$MARKER" "$HOOK" 2>/dev/null; then
    :  # 我们自己装的，直接升级覆盖
  elif [[ $FORCE -eq 1 ]]; then
    BACKUP="$HOOK.bak.$(date +%Y%m%d%H%M%S)"
    cp -p "$HOOK" "$BACKUP"
    echo "已备份原有 pre-commit → $BACKUP"
  else
    echo "检测到已有的 pre-commit，且不是本脚本安装的：" >&2
    echo "  $HOOK" >&2
    echo "为避免覆盖他人配置，已中止。确认可覆盖请加 --force（会自动备份）。" >&2
    exit 1
  fi
fi

mkdir -p "$COMMON_DIR/hooks"

# 先写临时文件再原子替换，避免写到一半被 git 读到半截脚本
TMP_HOOK="$(mktemp "$COMMON_DIR/hooks/.pre-commit.XXXXXX")"
printf '%s\n' "$HOOK_BODY" >"$TMP_HOOK"
chmod +x "$TMP_HOOK"
mv -f "$TMP_HOOK" "$HOOK"

# 清掉可能残留的、依赖分支内容的旧配置
if [[ "$(git config --get core.hooksPath || true)" == ".githooks" ]]; then
  git config --unset core.hooksPath
  echo "已移除旧的 core.hooksPath=.githooks 设置（它会在缺少该目录的分支上失效）。"
fi

# 装完自检：core.hooksPath 若指向别处，这个 hook 根本不会被调用
ACTIVE_PATH="$(git config --get core.hooksPath || true)"
if [[ -n "$ACTIVE_PATH" ]]; then
  echo "警告：core.hooksPath 当前为 '$ACTIVE_PATH'，git 不会执行刚安装的 hook。" >&2
  echo "      如需生效： git config --unset core.hooksPath" >&2
fi

echo "已安装 (v$HOOK_VERSION)：$HOOK"
echo "作用范围：本仓库全部分支与 worktree。"
echo "卸载：bash scripts/install-hooks.sh --uninstall"
