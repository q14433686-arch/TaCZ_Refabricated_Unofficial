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
timeout 30s vulkaninfo --summary 2>&1 | tee "$RUNNER_TEMP/tacz-vulkan.log"
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
if [[ ${GITHUB_ACTIONS:-} == true ]]; then args+=(--init-script scripts/ci-central.init.gradle); fi
if [[ ${TACZ_SMOKE_PAL:-} == true ]]; then args+=(-PtaczPalSmoke); fi
if [[ ${TACZ_SMOKE_CONTROLLER:-} == true ]]; then args+=(-PtaczControllerSmoke); fi
if [[ ${TACZ_SMOKE_IRIS:-} == true ]]; then args+=(-PtaczIrisSmoke); fi
[[ -z ${TACZ_IRIS_COLOR:-} ]] || args+=("-PtaczIrisColor=$TACZ_IRIS_COLOR")
case ${TACZ_SMOKE_VIEWER:-} in
    jei|rei) args+=("-PtaczViewerSmoke=$TACZ_SMOKE_VIEWER");;
    '') ;;
    *) echo 'Expected jei or rei' >&2; exit 2;;
esac
timeout -k 20s 10m ./gradlew "${args[@]}" runClientGameTest --console=plain --stacktrace 2>&1 | tee "$RUNNER_TEMP/tacz-client.log"
if [[ -n ${TACZ_IRIS_COLOR:-} ]]; then
    grep -Fq 'PORT_REIMAGINED: complete' "$run/logs/latest.log"
    for marker in 'removed caster residual=' 'stock locator changed=' 'same stock white strip texel' 'timed exposure held'; do
        grep -Fq "PORT_REIMAGINED: $marker" "$run/logs/latest.log"
    done
    grep -Fq 'material=energy_swirl' "$run/logs/latest.log"
    # Avoid the Checks per-step notice cap hiding the final verdict: count/material/colour first.
    grep -E 'PORT_REIMAGINED: (metal |skin |ground |stock locator|same stock|removed caster)|PORT_SCOPE_PARTICLE:' "$run/logs/latest.log" | sed 's/^/::notice::/'
    grep -E 'PORT_REIMAGINED: (shadow |.*caster bounds|Complementary)|program match for tacz:pipeline/(illuminated|muzzle)' "$run/logs/latest.log"
    exit
fi
if [[ ${TACZ_SMOKE_PAL:-} == true ]]; then
    grep -F 'PORT_PAL: re-entry support arm' "$run/logs/latest.log" | sed 's/^/::notice::/'
    grep -F 'PORT_PAL: offscreen' "$run/logs/latest.log" | sed 's/^/::notice::/'
    exit
fi
# Require both material paths, not just a generic "rendered" message. Frame data stays in the job artifact.
if [[ -z ${TACZ_SMOKE_VIEWER:-} && ${TACZ_SMOKE_CONTROLLER:-} != true ]]; then
    grep -F 'PORT_HUD_PROFILES: AK stand/sneak/zero-cache radii=' "$run/logs/latest.log" | sed 's/^/::notice::/'
    grep -F 'PORT_VIEW_TUNING: live muzzle delta=' "$run/logs/latest.log" | sed 's/^/::notice::/'
    for backing in dark light; do
        grep -Fq "PORT_HAND_MATERIALS: $backing six visible" "$run/logs/latest.log"
    done
    for angle in 35 125; do
        for body in cutout translucent_mirror; do
            grep -Fq "PORT_BONE_LIGHT: $body yaw=$angle plain" "$run/logs/latest.log"
        done
    done
    for sample in add_rear emissive_rear; do
        test -s "$(find "$run/screenshots" -name "*tacz_particle_pass_${sample}.png" -print -quit)"
    done
    for sample in first F5 tracked; do grep -Fq "PORT_MUZZLE_SCALE: $sample" "$run/logs/latest.log"; done
    grep -Fq 'PORT_MUZZLE_ANCHOR:' "$run/logs/latest.log"
    grep -Fq 'PORT_MUZZLE_PIXELS:' "$run/logs/latest.log"
    grep -E 'PORT_MUZZLE(_SCALE|_PIXELS|_ANCHOR)?:' "$run/logs/latest.log" | sed 's/^/::notice::/'
    grep -Fq 'PORT_PARTICLE_PASS: rear' "$run/logs/latest.log"
    grep -Fq 'PORT_CONSTRAINT: ADS pixels' "$run/logs/latest.log"
    grep -Fq 'PORT_BILLBOARD: pitched' "$run/logs/latest.log"
    grep -E 'PORT_HAND_MATERIALS:|PORT_BILLBOARD:|PORT_CONSTRAINT:|PORT_BONE_LIGHT:|PORT_PARTICLE_PASS:' "$run/logs/latest.log" | sed 's/^/::notice::/'
fi
if [[ ${TACZ_SMOKE_IRIS:-} == true ]]; then
    grep -Fq 'PORT_IRIS: active shader pack' "$run/logs/latest.log"
    grep -Fq 'PORT_LENS: XL50 restored' "$run/logs/latest.log"
    grep -Fq 'PORT_CORE:' "$run/logs/latest.log"
    grep -Fq 'PORT_IRIS: thermal and new-item regression passed' "$run/logs/latest.log"
    grep -E 'PORT_IRIS:|PORT_LENS:|PORT_CORE:|PORT_WORLD_TRACER:|PORT_FRAGMENTS:|PORT_SCOPE_PARTICLE:|PORT_MATERIALS:|PORT_SNOWSTORM:' "$run/logs/latest.log" | sed 's/^/::notice::/'
    exit
fi
if [[ ${TACZ_SMOKE_CONTROLLER:-} == true ]]; then
    grep -F 'PORT_CONTROLLER:' "$run/logs/latest.log" | sed 's/^/::notice::/'
    exit
fi
if [[ -n ${TACZ_SMOKE_VIEWER:-} ]]; then
    grep -Fq "PORT_VIEWER: $TACZ_SMOKE_VIEWER showed the reloaded gun; both query directions changed" "$run/logs/latest.log"
    verdict=$(grep -Eo 'PORT_VIEWER: [[:digit:]]+ of [[:digit:]]+ central samples show the recipe panel against the world control' "$run/logs/latest.log")
    printf '::notice::%s: %s\n' "$TACZ_SMOKE_VIEWER" "$verdict"
    test -s "$(find "$run/screenshots" -name "*tacz_viewer_$TACZ_SMOKE_VIEWER.png" -print -quit)"
    exit
fi
grep -E 'PORT_RACK_PREVIEW:|PORT_RACK_LOAD:|PORT_OUTLINE:' "$run/logs/latest.log" | sed 's/^/::notice::/'
for frame in tacz_{rack,case,corner,vanilla_quad,mesh}_{empty,ghost,placed} tacz_hover_{en_us,zh_cn}_{scope_xm157,scope_mk5hd,scope_standard_8x,less_flash,more_flash,gun_display_case} tacz_hand_filter_{empty,cleared} tacz_held_default_gun tacz_held_variant tacz_default_refit tacz_range_readout tacz_display_wall tacz_xm157_{entity,control,restored,occluded} \
        tacz_lens_pip_1x tacz_lens_{classic,pip}_{bare,optical,hot,wall,restored,tagged}; do
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
verdict=$(grep -Eo 'PORT_LENS_SCALE: stripe 1x=[[:digit:]]+ px; classic bare/optical=[[:digit:]]+/[[:digit:]]+ px; PIP bare/optical=[[:digit:]]+/[[:digit:]]+ px' "$run/logs/latest.log")
printf '::notice::%s\n' "$verdict"

printf '::notice::%s\n' "$(grep -F 'PORT_SHOT: DB-2 bare/cancel/ground/air impulses and both positions' "$run/logs/latest.log")"
grep -Fq 'PORT_SHOT: DB-2 bare/cancel/ground/air impulses and both positions' "$run/logs/latest.log"

grep -Fq 'PORT_MOVEMENT: empty-hand crawl, disabled exit, pose ownership and airborne ADS option passed' "$run/logs/latest.log"

grep -Fq 'PORT_MUZZLE: verified mounted/unmounted' "$run/logs/latest.log"
printf '::notice::%s\n' "$(grep -F 'PORT_MUZZLE:' "$run/logs/latest.log")"

grep -Fq 'PORT_FEEDBACK: back-mounted toggle, actual ordinary-hurt render hook and same-level impact delivery controls passed' "$run/logs/latest.log"
printf '::notice::%s\n' "$(grep -F 'PORT_FEEDBACK:' "$run/logs/latest.log")"

grep -Fq "PORT_OPTIC_INSTALL:" "$run/logs/latest.log"
printf '::notice::%s\n' "$(grep -F 'PORT_OPTIC_INSTALL:' "$run/logs/latest.log")"
grep -Fq 'PORT_CONFIG:' "$run/logs/latest.log"
printf '::notice::%s\n' "$(grep -F 'PORT_CONFIG:' "$run/logs/latest.log")"

grep -Fq 'PORT_CORE:' "$run/logs/latest.log"
printf '::notice::%s\n' "$(grep -F 'PORT_CORE:' "$run/logs/latest.log")"

for marker in PORT_RACK_PREVIEW: PORT_OUTLINE: PORT_MATERIALS: PORT_WORLD_TRACER: PORT_FRAGMENTS: PORT_HOVER: PORT_RACK_LOAD:; do
    grep -F "$marker" "$run/logs/latest.log" | sed 's/^/::notice::/'
done
