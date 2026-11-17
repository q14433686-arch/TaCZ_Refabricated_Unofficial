#!/usr/bin/env bash
# Run *inside* Xvfb. A client that stalls before Fabric's first test tick cannot hit waitFor's timeout.
set -euo pipefail
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
            timeout 12s jcmd "$pid" Thread.print -l >"$diag/threads-$sample-$pid.txt" || true
        done < <(jcmd -l | awk '/devlaunchinjector[.]Main|KnotClient|net[.]minecraft[.]client[.]main[.]Main/ {print $1}')
        if command -v xwininfo >/dev/null; then xwininfo -root -tree >"$diag/windows-$sample.txt" || true; fi
    done
}
probe & probe_pid=$!
trap 'kill "$probe_pid" 2>/dev/null || true' EXIT
# Leave time for Gradle/GLFW teardown and evidence upload before the enclosing job-step timeout.
timeout -k 20s 10m ./gradlew -PtaczClientSmoke runClientGameTest --console=plain --stacktrace 2>&1 | tee "$RUNNER_TEMP/tacz-client.log"
for frame in tacz_held_default_gun tacz_held_variant; do
    test -s "$(find "$run/screenshots" -name "*${frame}.png" -print -quit)"
done
