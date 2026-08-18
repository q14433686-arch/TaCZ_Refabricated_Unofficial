#!/usr/bin/env bash
# Dedicated-server smoke for CI.
# Starts Loom's runServer, waits for a successful "Done (" line, then stops.
# This is NOT a client launch and does not prove first-person / rendering.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

LOG="${RUNNER_TEMP:-/tmp}/tacz-runServer.log"
: >"$LOG"

echo "Starting ./gradlew runServer (log: $LOG)"

set +e
./gradlew runServer --no-daemon --stacktrace >"$LOG" 2>&1 &
PID=$!
set -e

cleanup() {
  if kill -0 "$PID" 2>/dev/null; then
    echo "Stopping Gradle/server pid $PID"
    kill "$PID" 2>/dev/null || true
    # Loom child JVMs
    pkill -P "$PID" 2>/dev/null || true
    sleep 3
    kill -9 "$PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

DEADLINE=$((SECONDS + 720))
while (( SECONDS < DEADLINE )); do
  if ! kill -0 "$PID" 2>/dev/null; then
    wait "$PID" || true
    echo '::error::runServer 在报告 Done 之前就退出了。'
    tail -n 120 "$LOG" || true
    exit 1
  fi
  if grep -E -q 'Done \([0-9.]+s\)!|For help, type "help"' "$LOG"; then
    echo 'Server reached Done — smoke passed.'
    tail -n 20 "$LOG"
    exit 0
  fi
  if grep -E -q 'Failed to start the minecraft server|Exception in thread "main"|Error: Could not find or load main class' "$LOG"; then
    echo '::error::runServer 日志出现启动失败特征。'
    tail -n 160 "$LOG"
    exit 1
  fi
  sleep 5
done

echo '::error::等待 dedicated server Done 超时（12 分钟）。'
tail -n 160 "$LOG"
exit 1
