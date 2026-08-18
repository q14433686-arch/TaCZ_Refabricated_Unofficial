#!/usr/bin/env bash
# 三分支版本一致性自检。
#
# 用法：
#   bash scripts/check_release_consistency.sh              # 检查工作区所在分支
#   bash scripts/check_release_consistency.sh --staged     # 检查暂存区内容（pre-commit 用）
#   bash scripts/check_release_consistency.sh --all        # 检查远端全部三条分支
#   bash scripts/check_release_consistency.sh --branch X   # 只查某一条分支
#   bash scripts/check_release_consistency.sh --links      # 附加：校验导航表链接可达性
#   bash scripts/check_release_consistency.sh --strict      # 发布门禁：不一致即退出码 1
#   bash scripts/check_release_consistency.sh --porcelain  # 机器可读输出，供 hook / CI 消费
#
# 组合示例：
#   ... --all --links --strict     # 发布前完整门禁
#   ... --staged --porcelain       # hook 模式，只吐 FAIL 行
#
# 退出码：
#   默认（无 --strict）恒为 0——只报告，不阻断。分步提交属正常工作方式。
#   加 --strict 时，发现不一致返回 1；参数错误返回 2。
#
# 可用环境变量覆盖：
#   TACZ_BASE_URL        仓库地址
#   TACZ_BRANCHES        分支列表，逗号分隔
#   NO_COLOR=1           禁用颜色（非 TTY 时自动禁用）

set -uo pipefail

BASE_URL="${TACZ_BASE_URL:-https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial}"
IFS=',' read -r -a BRANCHES <<<"${TACZ_BRANCHES:-26.2(main),26.1.2,1.21.11}"
DEFAULT_BRANCH="${BRANCHES[0]}"

MODE="worktree"
ONE_BRANCH=""
DO_LINKS=0
STRICT=0
PORCELAIN=0

usage() { sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all)       MODE="all" ;;
    --staged)    MODE="staged" ;;
    --branch)    MODE="one"; ONE_BRANCH="${2:-}"; shift ;;
    --branch=*)  MODE="one"; ONE_BRANCH="${1#*=}" ;;
    --links)     DO_LINKS=1 ;;
    --strict)    STRICT=1 ;;
    --porcelain) PORCELAIN=1 ;;
    --no-color)  NO_COLOR=1 ;;
    -h|--help)   usage; exit 0 ;;
    *) printf '未知参数：%s（用 --help 查看用法）\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

if [[ "$MODE" == "one" && -z "$ONE_BRANCH" ]]; then
  echo "--branch 需要一个分支名。" >&2
  exit 2
fi

# ---------- 输出（非 TTY / NO_COLOR / --porcelain 时自动降级为纯文本） ----------
if [[ -n "${NO_COLOR:-}" || $PORCELAIN -eq 1 || ! -t 1 ]]; then
  C_RED=""; C_GRN=""; C_YLW=""; C_DIM=""; C_RST=""
else
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YLW=$'\033[33m'
  C_DIM=$'\033[2m';  C_RST=$'\033[0m'
fi

N_PASS=0; N_FAIL=0; N_WARN=0
CUR_SCOPE=""

say()  { [[ $PORCELAIN -eq 1 ]] || printf '%s\n' "$*"; }
pass() { N_PASS=$((N_PASS+1)); [[ $PORCELAIN -eq 1 ]] || printf '  %sok:%s   %s\n' "$C_GRN" "$C_RST" "$*"; }
warn() {
  N_WARN=$((N_WARN+1))
  if [[ $PORCELAIN -eq 1 ]]; then printf 'WARN\t%s\t%s\n' "$CUR_SCOPE" "$*"
  else printf '  %swarn:%s %s\n' "$C_YLW" "$C_RST" "$*"; fi
}
fail() {
  N_FAIL=$((N_FAIL+1))
  if [[ $PORCELAIN -eq 1 ]]; then printf 'FAIL\t%s\t%s\n' "$CUR_SCOPE" "$*"
  else printf '  %sFAIL:%s %s\n' "$C_RED" "$C_RST" "$*"; fi
}

# ---------- 读取文件：不用 eval，来源统一抽象 ----------
# $1 = 来源（worktree / index / <git-ref>），$2 = 仓库内相对路径
read_source() {
  case "$1" in
    worktree) cat -- "$TOPLEVEL/$2" 2>/dev/null ;;
    index)    git show ":$2" 2>/dev/null ;;
    *)        git show "$1:$2" 2>/dev/null ;;
  esac
}

# 26.2(main) -> 26.2 ; origin/26.1.2 -> 26.1.2
branch_series() {
  local n="${1##*/}"
  n="${n%%(*}"
  printf '%s' "$n"
}

TOPLEVEL="$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

# ---------- 单个来源的检查 ----------
# $1 = 展示名  $2 = 读取来源  $3 = 期望的 mc 系列（可空）
check_source() {
  local name="$1" src="$2" series="${3:-}"
  local gradle readme
  CUR_SCOPE="$name"

  gradle="$(read_source "$src" gradle.properties)"
  readme="$(read_source "$src" README.md)"

  say "=== $name ==="
  if [[ -z "$gradle" ]]; then fail "$name: 读不到 gradle.properties"; return; fi
  if [[ -z "$readme" ]]; then fail "$name: 读不到 README.md"; return; fi

  local mod_version rel
  mod_version="$(grep -E '^[[:space:]]*mod_version[[:space:]]*=' <<<"$gradle" \
                 | head -1 | cut -d= -f2- | tr -d '[:space:]')"
  if [[ -z "$mod_version" ]]; then fail "$name: gradle.properties 缺少 mod_version"; return; fi

  # 1.1.8+fabric.26.1.2.R2 -> R2
  rel="${mod_version##*.}"
  say "  ${C_DIM}mod_version = $mod_version  (release = $rel)${C_RST}"

  # 0) 版本号本身要合法，且与所在分支的 mc 系列匹配
  if [[ ! "$mod_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+\+fabric\.[0-9.]+\.R[0-9]+$ ]]; then
    fail "$name: mod_version '$mod_version' 不符合 x.y.z+fabric.<mc>.R<n> 格式"
  elif [[ -n "$series" && "$mod_version" != *"+fabric.$series."* ]]; then
    fail "$name: mod_version 是 '$mod_version'，与所在分支的 MC 系列 '$series' 不匹配"
  else
    pass "mod_version 格式与分支系列一致"
  fi

  # 1) README 正文出现的完整版本号必须唯一，且与 mod_version 相同
  local found n
  found="$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+\+fabric\.[0-9.]+\.R[0-9]+' <<<"$readme" | sort -u)"
  n="$(grep -c . <<<"$found")"
  if [[ -z "$found" ]]; then
    fail "$name: README 中找不到任何 'x.y.z+fabric.<mc>.R<n>' 版本号"
  elif [[ "$n" -gt 1 ]]; then
    fail "$name: README 出现多个版本号：$(tr '\n' ' ' <<<"$found")"
  elif [[ "$found" != "$mod_version" ]]; then
    fail "$name: README 写 '$found'，gradle.properties 是 '$mod_version'"
  else
    pass "README 版本号与 gradle.properties 一致"
  fi

  # 2) 「仓库源码已使用 R? 版本号」提示行
  local hint
  hint="$(grep -oE '已使用 R[0-9]+ 版本号' <<<"$readme" | grep -oE 'R[0-9]+' | sort -u)"
  if [[ -z "$hint" ]]; then
    warn "README 缺少「仓库源码已使用 R? 版本号」提示行"
  elif [[ "$(grep -c . <<<"$hint")" -gt 1 ]]; then
    fail "$name: 提示行出现多个 release：$(tr '\n' ' ' <<<"$hint")"
  elif [[ "$hint" != "$rel" ]]; then
    fail "$name: 提示行写 '$hint'，但 mod_version 是 '$rel'"
  else
    pass "「已使用 $rel 版本号」提示行一致"
  fi

  # 3) 「R? 构建使用」表格描述
  local tbl
  tbl="$(grep -oE 'R[0-9]+ 构建使用' <<<"$readme" | grep -oE 'R[0-9]+' | sort -u)"
  if [[ -n "$tbl" && "$tbl" != "$rel" ]]; then
    fail "$name: 支持环境表写 '$tbl 构建使用'，但 mod_version 是 '$rel'"
  elif [[ -n "$tbl" ]]; then
    pass "支持环境表的 $rel 标注一致"
  fi

  # 4) 版本导航表必须存在，且各分支链接齐全
  if ! grep -q '选择你的 Minecraft 版本' <<<"$readme"; then
    fail "$name: README 缺少「选择你的 Minecraft 版本」导航表"
  else
    local b miss=0 esc
    for b in "${BRANCHES[@]}"; do
      # 括号需转义为 %28 %29；点号在正则里转义
      esc="$(printf '%s' "$b" | sed -e 's/(/%28/g' -e 's/)/%29/g' -e 's/\./\\./g')"
      if ! grep -qE "tree/$esc" <<<"$readme"; then
        fail "$name: 导航表缺 $b 分支链接（括号须转义成 %28…%29）"
        miss=1
      fi
    done
    [[ $miss -eq 0 ]] && pass "导航表 ${#BRANCHES[@]} 条分支链接齐全"
  fi

  # 5) 跨分支复制粘贴残留：正文不该出现别的 MC 系列的自称
  if [[ -n "$series" ]]; then
    local flat other other_esc hit=0
    flat="$(tr -d '\n' <<<"$readme")"
    for other in "${BRANCHES[@]}"; do
      other="$(branch_series "$other")"
      [[ "$other" == "$series" ]] && continue
      other_esc="${other//./\\.}"
      if grep -qE "本 Fabric ${other_esc}(\.x)? ?端口" <<<"$flat"; then
        fail "$name: 正文出现「本 Fabric $other 端口」——跨分支复制粘贴残留"
        hit=1
      fi
    done
    [[ $hit -eq 0 ]] && pass "无其它分支的复制残留"
  fi

  # 6) 英文定位句（供搜索命中）
  if grep -q 'Unofficial Fabric port of TaCZ' <<<"$readme"; then
    pass "英文定位句存在"
  else
    fail "$name: README 顶部缺少英文定位句 'Unofficial Fabric port of TaCZ ...'"
  fi
}

# ---------- 链接可达性 ----------
check_links() {
  CUR_SCOPE="links"
  say "=== 导航表链接可达性 ==="
  if ! command -v curl >/dev/null 2>&1; then
    warn "未找到 curl，跳过链接检查"
    return
  fi

  local urls=() b t
  for b in "${BRANCHES[@]}"; do
    urls+=("$BASE_URL/tree/$(printf '%s' "$b" | sed -e 's/(/%28/g' -e 's/)/%29/g')")
  done
  # 默认分支 README 里出现的 release tag 一并校验
  for t in $(read_source "origin/$DEFAULT_BRANCH" README.md \
             | grep -oE 'releases/tag/[A-Za-z0-9._-]+' | sort -u); do
    urls+=("$BASE_URL/$t")
  done

  local u code
  for u in "${urls[@]}"; do
    code="$(curl -sS -o /dev/null -w '%{http_code}' -L \
              --max-time 15 --retry 1 --retry-delay 1 "$u" 2>/dev/null)"
    case "$code" in
      200)     pass "$code $u" ;;
      429|000) warn "$code $u（限流或网络不可达，非内容问题）" ;;
      *)       fail "链接不可达 $code $u" ;;
    esac
  done
}

# ---------- 调度 ----------
case "$MODE" in
  all)
    # 一次性抓取所有需要的分支，避免循环内反复 fetch
    fetch_args=()
    for b in "${BRANCHES[@]}"; do fetch_args+=("+refs/heads/$b:refs/remotes/origin/$b"); done
    git fetch -q origin "${fetch_args[@]}" 2>/dev/null || \
      say "${C_YLW}提示：fetch 失败，使用本地已有的 origin/* 快照。${C_RST}"
    for b in "${BRANCHES[@]}"; do
      check_source "origin/$b" "origin/$b" "$(branch_series "$b")"
    done
    ;;
  one)
    git fetch -q origin "+refs/heads/$ONE_BRANCH:refs/remotes/origin/$ONE_BRANCH" 2>/dev/null || true
    ref="$ONE_BRANCH"
    git cat-file -e "$ref:README.md" 2>/dev/null || ref="origin/$ONE_BRANCH"
    check_source "$ref" "$ref" "$(branch_series "$ONE_BRANCH")"
    ;;
  staged)
    cur="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo HEAD)"
    check_source "暂存区 ($cur)" index "$(branch_series "$cur")"
    ;;
  *)
    cur="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo HEAD)"
    check_source "工作区 ($cur)" worktree "$(branch_series "$cur")"
    ;;
esac

[[ $DO_LINKS -eq 1 ]] && check_links

# ---------- 汇总 ----------
if [[ $PORCELAIN -eq 1 ]]; then
  printf 'SUMMARY\t%d\t%d\t%d\n' "$N_PASS" "$N_FAIL" "$N_WARN"
  [[ $STRICT -eq 1 && $N_FAIL -gt 0 ]] && exit 1
  exit 0
fi

say ""
say "通过 $N_PASS · 失败 $N_FAIL · 警告 $N_WARN"

if [[ $N_FAIL -gt 0 ]]; then
  if [[ $STRICT -eq 1 ]]; then
    printf '%s版本一致性检查未通过（--strict）。发布前必须修复上述条目。%s\n' "$C_RED" "$C_RST"
    printf '%s参考 docs/publish/RELEASE_CHECKLIST.md 与 docs/README_<分支>.md。%s\n' "$C_RED" "$C_RST"
    exit 1
  fi
  printf '%s发现不一致（见上）。若你正分步改动，可稍后再补齐。%s\n' "$C_YLW" "$C_RST"
  printf '%s发布前请跑： bash scripts/check_release_consistency.sh --all --links --strict%s\n' "$C_YLW" "$C_RST"
  exit 0
fi

printf '%s全部通过。%s\n' "$C_GRN" "$C_RST"
exit 0
