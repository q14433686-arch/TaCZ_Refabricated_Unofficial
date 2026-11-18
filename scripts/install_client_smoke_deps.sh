#!/usr/bin/env bash
# Provision the Ubuntu runner, not a player's machine. A renderer/WSI probe still runs inside Xvfb.
set -euo pipefail
packages=(mesa-vulkan-drivers vulkan-tools x11-utils xauth xvfb libflite1)
missing=()
for package in "${packages[@]}"; do
    if [[ $(dpkg-query -W -f='${Status}' "$package" 2>/dev/null || true) != 'install ok installed' ]]; then
        missing+=("$package")
    fi
done
if (( ${#missing[@]} == 0 )); then echo 'Client smoke packages already installed'; exit 0; fi
printf 'Missing client smoke packages: %s\n' "${missing[*]}"
apt=(sudo env DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=0 -o Acquire::ForceIPv4=true -o Acquire::http::Timeout=10 -o Acquire::https::Timeout=10 -o DPkg::Lock::Timeout=30)
# Use image-provided indexes first; cap the WHOLE attempt, not just each connection.
# A runner's regional mirror can accept connections but stall each package sequentially.
primary=("${apt[@]}")
if [[ -f /etc/apt/sources.list.d/ubuntu.sources ]]; then
    primary+=(-o Dir::Etc::sourcelist=/etc/apt/sources.list.d/ubuntu.sources -o Dir::Etc::sourceparts=-)
fi
if timeout --kill-after=10s 180s "${primary[@]}" install --no-install-recommends -y "${missing[@]}"; then exit 0; fi
# Don't rewrite the runner's global sources or touch unrelated PPAs. Official Ubuntu archive
# has updates/security pockets too. Total budgets below stay inside the workflow's 10min step.
. /etc/os-release
[[ ${ID:-} == ubuntu && ${VERSION_CODENAME:-} =~ ^[a-z]+$ ]] || { echo 'Official archive fallback requires Ubuntu' >&2; exit 1; }
fallback=$(mktemp -d)
trap 'rm -rf "$fallback"' EXIT
chmod 755 "$fallback"
cat > "$fallback/ubuntu.sources" <<EOF
Types: deb
URIs: https://archive.ubuntu.com/ubuntu/
Suites: $VERSION_CODENAME $VERSION_CODENAME-updates $VERSION_CODENAME-security
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
EOF
chmod 644 "$fallback/ubuntu.sources"
apt+=(-o "Dir::Etc::sourcelist=$fallback/ubuntu.sources" -o Dir::Etc::sourceparts=-)
echo '::warning::Image mirror failed or stalled; retrying official Ubuntu archive with bounded budgets'
# If a timeout interrupted dpkg, finish its transaction before another apt invocation.
timeout --kill-after=5s 40s sudo env DEBIAN_FRONTEND=noninteractive dpkg --configure -a
timeout --kill-after=5s 60s "${apt[@]}" update -qq
timeout --kill-after=10s 240s "${apt[@]}" install --no-install-recommends -y "${missing[@]}"
