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
apt=(sudo apt-get -o Acquire::Retries=1 -o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20 -o DPkg::Lock::Timeout=45)
# Use image-provided indexes first. Only refresh if they are missing or stale, and don't wait on unrelated PPAs.
if [[ -f /etc/apt/sources.list.d/ubuntu.sources ]]; then
    apt+=(-o Dir::Etc::sourcelist=/etc/apt/sources.list.d/ubuntu.sources -o Dir::Etc::sourceparts=-)
fi
if ! "${apt[@]}" install --no-install-recommends -y "${missing[@]}"; then
    "${apt[@]}" update -qq
    "${apt[@]}" install --no-install-recommends -y "${missing[@]}"
fi
