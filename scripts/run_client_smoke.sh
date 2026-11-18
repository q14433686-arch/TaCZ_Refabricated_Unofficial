#!/usr/bin/env bash
# Run *inside* Xvfb. A client that stalls before Fabric's first test tick cannot hit waitFor's timeout.
set -euo pipefail
# Match the pinned Fabric API 26.3 client tests: a real Mesa Vulkan software device with a window surface.
# Ubuntu Mesa releases use both lvp_icd.json and lvp_icd.x86_64.json.
shopt -s nullglob
icds=(/usr/share/vulkan/icd.d/lvp_icd*.json)
if (( ${#icds[@]} == 0 )); then echo 'Missing Mesa Lavapipe ICD' >&2; exit 1; fi
export VK_DRIVER_FILES="${icds[0]}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-$RUNNER_TEMP/tacz-xdg}"
install -d -m 700 "$XDG_RUNTIME_DIR"
timeout 30s vulkaninfo --summary | tee "$RUNNER_TEMP/tacz-vulkan.log"
grep -Eq 'VK_KHR_(xlib|xcb)_surface' "$RUNNER_TEMP/tacz-vulkan.log"
grep -Fq 'PHYSICAL_DEVICE_TYPE_CPU' "$RUNNER_TEMP/tacz-vulkan.log"
run=build/run/clientGameTest
diag="$run/diagnostics"
probe() {
    local delay sample pid
    for delay in 120 180; do
        sleep "$delay"
        mkdir -p "$diag"
        sample=$(date -u +%H%M%S)
        # Only the game JVM, not Gradle daemons (and never dump the launcher's full command line).
        while read -r pid; do
            local dump="$diag/threads-$sample-$pid.txt"
            timeout 12s jcmd "$pid" Thread.print -l >"$dump" || true
            # A modal backend error cannot be acknowledged in CI. Fail the run, don't silently dismiss it.
            if grep -Fq 'com.mojang.blaze3d.platform.MessageBox.error(' "$dump"; then
                echo 'Minecraft is blocked in its native error dialog; see client log and thread dump' >&2
                kill -TERM "$pid" || true
            fi
        done < <(jcmd -l | awk '/devlaunchinjector[.]Main|KnotClient|net[.]minecraft[.]client[.]main[.]Main/ {print $1}')
        if command -v xwininfo >/dev/null; then xwininfo -root -tree >"$diag/windows-$sample.txt" || true; fi
    done
}
probe & probe_pid=$!
trap 'kill "$probe_pid" 2>/dev/null || true' EXIT
# Leave time for Gradle/SDL teardown and evidence upload before the enclosing job-step timeout.
args=(-PtaczClientSmoke)
case ${TACZ_SMOKE_VIEWER:-} in
    jei|rei) args+=("-PtaczViewerSmoke=$TACZ_SMOKE_VIEWER");;
    '') ;;
    *) echo 'Expected jei or rei' >&2; exit 2;;
esac
timeout -k 20s 10m ./gradlew "${args[@]}" runClientGameTest --console=plain --stacktrace 2>&1 | tee "$RUNNER_TEMP/tacz-client.log"
if [[ -n ${TACZ_SMOKE_VIEWER:-} ]]; then
    grep -Fq "PORT_VIEWER: $TACZ_SMOKE_VIEWER showed the reloaded gun; both query directions changed" "$run/logs/latest.log"
    verdict=$(grep -Eo 'PORT_VIEWER: [[:digit:]]+ of [[:digit:]]+ central samples show the recipe panel against the world control' "$run/logs/latest.log")
    printf '::notice::%s: %s\n' "$TACZ_SMOKE_VIEWER" "$verdict"
    test -s "$(find "$run/screenshots" -name "*tacz_viewer_$TACZ_SMOKE_VIEWER.png" -print -quit)"
    exit
fi
for frame in tacz_held_default_gun tacz_held_variant tacz_default_refit tacz_range_readout tacz_display_wall tacz_xm157_{entity,control,restored,occluded}; do
    test -s "$(find "$run/screenshots" -name "*${frame}.png" -print -quit)"
done
grep -Fq 'PORT_CATALOG: client loaded 229 indexes' "$run/logs/latest.log"
grep -Fq 'PORT_ACTION: tracking client observed settled fire/reload once' "$run/logs/latest.log"
grep -Fq 'PORT_CREATIVE: open search refreshed' "$run/logs/latest.log"
# These assertions concern timing and persisted/network state; a rendered frame cannot prove them.
grep -Fq 'PORT_SMOKE_CLIENT: integrated pause/resume and both pack clocks passed' "$run/logs/latest.log"
grep -Fq 'PORT_SMOKE_CLIENT: reopened same save; new snapshot, one action, saved displays and survival storage transfer passed' "$run/logs/latest.log"
# A successful pixel verdict is available through the Checks API even when artifact storage is inaccessible.
verdict=$(grep -Eo 'PORT_OPTIC: XM157 showed [[:digit:]]+ restored yellow pixels on entity, zero draws behind wall' "$run/logs/latest.log")
printf '::notice::%s\n' "$verdict"
verdict=$(grep -Eo 'PORT_LENS: XL50 restored [[:digit:]]+ classic / [[:digit:]]+ PIP heat pixels; wall and live tag veto cold' "$run/logs/latest.log")
printf '::notice::%s\n' "$verdict"
