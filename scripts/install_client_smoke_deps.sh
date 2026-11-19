#!/usr/bin/env bash
# Provision the Ubuntu runner, not a player's machine. A renderer/WSI probe still runs inside Xvfb.
set -euo pipefail
packages=(mesa-vulkan-drivers vulkan-tools x11-utils xauth xvfb libflite1)
if [[ ${TACZ_SMOKE_IRIS:-} == true ]]; then
    packages+=(libgl1-mesa-dri libglx-mesa0 libegl-mesa0 mesa-utils)
fi
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
# The runner already has signed Ubuntu indexes. A regional mirror outage needn't cost a full
# universe index refresh: request just the missing .debs at the canonical archive using those
# exact versions and SHA-256s. apt-helper verifies them before apt's ordinary install. Stale
# indexes (e.g. a superseded update) still fall through to the two normal apt paths below.
prime_archive_cache() (
    local tmp uri dest size hash
    local -a downloads=()
    tmp=$(mktemp -d) || return 1
    trap 'rm -rf "$tmp"' EXIT
    timeout 10s "${primary[@]}" -o Acquire::ForceHash=SHA256 --print-uris install --no-install-recommends -y "${missing[@]}" >"$tmp/plan" || return 1
    while read -r uri dest size hash; do
        [[ $uri == \'* ]] || continue  # ignore the human-readable apt summary
        uri=${uri%\'}; uri=${uri#\'}
        [[ $uri == */ubuntu/pool/* && $dest != */* && $dest != -* && $dest == *.deb && $hash =~ ^SHA256:[[:xdigit:]]{64}$ ]] || return 1
        downloads+=("https://archive.ubuntu.com/ubuntu/pool/${uri#*/ubuntu/pool/}" "$tmp/$dest" "$hash")
    done <"$tmp/plan"
    (( ${#downloads[@]} > 0 )) || return 1
    timeout --kill-after=4s 45s /usr/lib/apt/apt-helper -o Acquire::ForceIPv4=true -o Acquire::Retries=0 \
        download-file "${downloads[@]}" || return 1
    sudo install -m 644 -t /var/cache/apt/archives -- "$tmp"/*.deb || return 1
    echo 'Cached verified client smoke packages from the official pool'
)
prime_archive_cache || echo 'No usable pool prefill; trying the image mirror'
if timeout --kill-after=10s 85s "${primary[@]}" install --no-install-recommends -y "${missing[@]}"; then exit 0; fi
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
Components: main universe
Architectures: $(dpkg --print-architecture)
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
EOF
chmod 644 "$fallback/ubuntu.sources"
apt+=(-o "Dir::Etc::sourcelist=$fallback/ubuntu.sources" -o Dir::Etc::sourceparts=-)
echo '::warning::Image mirror failed or stalled; retrying official Ubuntu archive with bounded budgets'
# If a timeout interrupted dpkg, finish its transaction before another apt invocation.
timeout --kill-after=5s 40s sudo env DEBIAN_FRONTEND=noninteractive dpkg --configure -a
# A fresh archive URI cannot reuse the image mirror's list files. Fetch just binary indexes for the
# native packages (no translations/appstream), leaving enough time to verify a fresh universe list.
timeout --kill-after=5s 180s "${apt[@]}" -o Acquire::Languages=none \
    -o Acquire::IndexTargets::deb::DEP-11::DefaultEnabled=false -o Acquire::IndexTargets::deb::CNF::DefaultEnabled=false update -qq
timeout --kill-after=10s 195s "${apt[@]}" install --no-install-recommends -y "${missing[@]}"
