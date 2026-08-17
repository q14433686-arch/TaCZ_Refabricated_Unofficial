#!/usr/bin/env bash
# 三分支版本一致性自检。
# 用法：
#   bash scripts/check_release_consistency.sh            # 检查本地工作区所在分支
#   bash scripts/check_release_consistency.sh --all      # 检查远端全部三条分支
#   bash scripts/check_release_consistency.sh --links    # 额外校验导航表链接可达性
#
# 退出码 0 = 通过；1 = 发现不一致，必须修复后才能发布。

set -uo pipefail

FAIL=0
BASE_URL="https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial"
BRANCHES=('26.2(main)' '26.1.2' '1.21.11')

red()  { printf '\033[31m%s\033[0m\n' "$*"; }
grn()  { printf '\033[32m%s\033[0m\n' "$*"; }
ylw()  { printf '\033[33m%s\033[0m\n' "$*"; }

fail() { red "  FAIL: $*"; FAIL=1; }
pass() { grn "  ok:   $*"; }

# ---------- 单分支检查 ----------
# $1 = 人类可读的分支名; $2 = 读取 gradle.properties 的命令; $3 = 读取 README.md 的命令
check_branch() {
  local name="$1" gradle readme
  gradle="$(eval "$2" 2>/dev/null)"
  readme="$(eval "$3" 2>/dev/null)"

  echo "=== $name ==="
  if [[ -z "$gradle" || -z "$readme" ]]; then
    fail "$name: 无法读取 gradle.properties 或 README.md"
    return
  fi

  local mod_version rel
  mod_version="$(grep -E '^mod_version=' <<<"$gradle" | head -1 | cut -d= -f2 | tr -d '[:space:]')"
  if [[ -z "$mod_version" ]]; then
    fail "$name: gradle.properties 缺少 mod_version"
    return
  fi
  # 1.1.8+fabric.26.1.2.R2 -> R2
  rel="${mod_version##*.}"
  echo "  mod_version = $mod_version  (release = $rel)"

  # 1) README 正文里出现的完整版本号必须与 mod_version 完全一致
  local found_versions
  found_versions="$(grep -oE '1\.[0-9]+\.[0-9]+\+fabric\.[0-9.]+\.R[0-9]+' <<<"$readme" | sort -u)"
  if [[ -z "$found_versions" ]]; then
    fail "$name: README 中找不到任何 '1.x.x+fabric.<mc>.R<n>' 版本号"
  elif [[ "$(wc -l <<<"$found_versions")" -gt 1 ]]; then
    fail "$name: README 中出现多个不同版本号：$(tr '\n' ' ' <<<"$found_versions")"
  elif [[ "$found_versions" != "$mod_version" ]]; then
    fail "$name: README 版本号 '$found_versions' != gradle.properties '$mod_version'"
  else
    pass "README 版本号与 gradle.properties 一致"
  fi

  # 2) 「仓库源码已使用 R? 版本号」提示行
  local hint
  hint="$(grep -oE '已使用 R[0-9]+ 版本号' <<<"$readme" | grep -oE 'R[0-9]+' | sort -u)"
  if [[ -z "$hint" ]]; then
    ylw "  warn: README 缺少「仓库源码已使用 R? 版本号」提示行"
  elif [[ "$hint" != "$rel" ]]; then
    fail "$name: 提示行写 '$hint'，但 mod_version 是 '$rel'"
  else
    pass "「已使用 $rel 版本号」提示行一致"
  fi

  # 3) 「R? 构建使用」之类的表格描述
  local tbl
  tbl="$(grep -oE 'R[0-9]+ 构建使用' <<<"$readme" | grep -oE 'R[0-9]+' | sort -u)"
  if [[ -n "$tbl" && "$tbl" != "$rel" ]]; then
    fail "$name: 支持环境表写 '$tbl 构建使用'，但 mod_version 是 '$rel'"
  elif [[ -n "$tbl" ]]; then
    pass "支持环境表的 $rel 标注一致"
  fi

  # 4) 版本导航表必须存在，且三条分支链接齐全
  if ! grep -q '选择你的 Minecraft 版本' <<<"$readme"; then
    fail "$name: README 缺少「选择你的 Minecraft 版本」导航表"
  else
    local miss=0
    grep -q 'tree/26\.2%28main%29' <<<"$readme" || { fail "$name: 导航表缺 26.2 分支链接（或括号未转义成 %28main%29）"; miss=1; }
    grep -q 'tree/26\.1\.2'        <<<"$readme" || { fail "$name: 导航表缺 26.1.2 分支链接"; miss=1; }
    grep -q 'tree/1\.21\.11'       <<<"$readme" || { fail "$name: 导航表缺 1.21.11 分支链接"; miss=1; }
    [[ $miss -eq 0 ]] && pass "导航表三条分支链接齐全"
  fi

  # 5) 跨分支复制粘贴残留：README 正文提到的端口版本要和本分支一致
  case "$name" in
    *1.21.11*)
      grep -q '本 Fabric[[:space:]]*$' <<<"$readme" >/dev/null 2>&1 || true
      if grep -qE '视为本 Fabric\s*26\.x 端口|本 Fabric 26\.x 端口' <<<"$(tr -d '\n' <<<"$readme")"; then
        fail "$name: 正文出现「本 Fabric 26.x 端口」——跨分支复制粘贴残留"
      else
        pass "无 26.x 复制残留"
      fi
      ;;
  esac

  # 6) 英文定位句（供搜索命中）
  if grep -q 'Unofficial Fabric port of TaCZ' <<<"$readme"; then
    pass "英文定位句存在"
  else
    fail "$name: README 顶部缺少英文定位句 'Unofficial Fabric port of TaCZ ...'"
  fi
}

# ---------- 链接可达性 ----------
check_links() {
  echo "=== 导航表链接可达性 ==="
  local tags
  tags="$(git tag -l 2>/dev/null)"
  local urls=(
    "$BASE_URL/tree/26.2%28main%29"
    "$BASE_URL/tree/26.1.2"
    "$BASE_URL/tree/1.21.11"
  )
  # 从各分支 README 里抓 release tag 链接一并校验
  local t
  for t in $(git show 'origin/26.2(main):README.md' 2>/dev/null \
             | grep -oE 'releases/tag/[A-Za-z0-9._-]+' | sort -u); do
    urls+=("$BASE_URL/${t}")
  done

  local u code
  for u in "${urls[@]}"; do
    code="$(curl -s -o /dev/null -w '%{http_code}' -L "$u" 2>/dev/null)"
    if [[ "$code" == "200" ]]; then
      pass "$code $u"
    else
      fail "$code $u"
    fi
  done
}

MODE="${1:-}"

if [[ "$MODE" == "--all" || "$MODE" == "--links" ]]; then
  for b in "${BRANCHES[@]}"; do
    git fetch -q origin "+refs/heads/$b:refs/remotes/origin/$b" 2>/dev/null
    check_branch "origin/$b" \
      "git show 'origin/$b:gradle.properties'" \
      "git show 'origin/$b:README.md'"
  done
  [[ "$MODE" == "--links" ]] && check_links
else
  cur="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
  check_branch "工作区 ($cur)" "cat gradle.properties" "cat README.md"
fi

echo
if [[ $FAIL -ne 0 ]]; then
  red "版本一致性检查未通过。发布前必须修复上述条目。"
  red "参考 docs/publish/RELEASE_CHECKLIST.md 与 docs/README_<分支>.md。"
  exit 1
fi
grn "全部通过。"
