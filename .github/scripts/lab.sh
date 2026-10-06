#!/usr/bin/env bash
#
# lab.sh — start and stop the Mock Lab (the system under test) for one CI job.
#
# Usage (from the repo root):
#   .github/scripts/lab.sh start    # build the jar, start it in the background, wait until ready
#   .github/scripts/lab.sh stop     # stop the lab started by "start" (safe if none is running)
#
# Settings (environment variables):
#   LAB_PORT           port the lab listens on (default 8080; local checks use 18080
#                      so they never touch an engineer's own lab on 8080)
#   LAB_READY_SECONDS  how long "start" waits for readiness (default 60)
#   LAB_SKIP_BUILD=1   reuse the existing jar instead of rebuilding it (handy locally)
#
# Why poll /health instead of "sleep 20": the lab answers /health with 200 only after
# its seed data has loaded (spec 002), so "200" means "ready for checks" — no guessing.
# The pid and start-up log live under mock-lab-app/target/ so CI can keep the log
# as evidence and "stop" can find the process again in a later step.
#
# Portable between macOS bash 3.2 and Ubuntu bash 5.

set -euo pipefail

LAB_DIR="mock-lab-app"
PID_FILE="$LAB_DIR/target/lab.pid"
LOG_FILE="$LAB_DIR/target/lab-startup.log"
LAB_PORT="${LAB_PORT:-8080}"
LAB_READY_SECONDS="${LAB_READY_SECONDS:-60}"

# Stop the lab recorded in the pid file: polite SIGTERM first, SIGKILL after 10 s.
stop_lab() {
  if [ ! -f "$PID_FILE" ]; then
    echo "No lab was running"
    return 0
  fi
  local pid
  pid="$(cat "$PID_FILE")"
  rm -f "$PID_FILE"
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "No lab was running"
    return 0
  fi
  kill "$pid" 2>/dev/null || true
  local waited=0
  while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt 10 ]; do
    sleep 1
    waited=$((waited + 1))
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "Lab did not stop within 10 s; forcing it"
    kill -9 "$pid" 2>/dev/null || true
  fi
  echo "Lab stopped"
}

# Report a failed start the way GitHub Actions shows it (an annotation on the run page),
# print the end of the lab's own log, clean up, and fail the step.
fail_start() {
  echo "::error title=Lab did not start::$1"
  echo "--- last 50 lines of $LOG_FILE ---"
  tail -n 50 "$LOG_FILE" || true
  stop_lab
  exit 1
}

start_lab() {
  if [ "${LAB_SKIP_BUILD:-}" != "1" ]; then
    echo "Building the Mock Lab jar"
    (cd "$LAB_DIR" && ./mvnw -B -ntp -q package -DskipTests)
  fi

  echo "Starting the Mock Lab on :$LAB_PORT"
  mkdir -p "$LAB_DIR/target"
  # nohup + & : run in the background and survive the end of this shell/step.
  nohup java -jar "$LAB_DIR/target/mock-lab-app.jar" --server.port="$LAB_PORT" \
    > "$LOG_FILE" 2>&1 &
  echo "$!" > "$PID_FILE"
  local pid
  pid="$(cat "$PID_FILE")"

  local attempt=0
  while [ "$attempt" -lt "$LAB_READY_SECONDS" ]; do
    # Fail fast: no point waiting 60 s for a process that already died (e.g. port in use).
    if ! kill -0 "$pid" 2>/dev/null; then
      fail_start "Lab process exited during start-up — see $LOG_FILE"
    fi
    if curl -sf --max-time 2 "http://localhost:$LAB_PORT/health" > /dev/null; then
      echo "Lab ready on :$LAB_PORT after ${attempt}s"
      return 0
    fi
    sleep 1
    attempt=$((attempt + 1))
  done
  fail_start "Mock Lab not ready on :$LAB_PORT within ${LAB_READY_SECONDS} s — see $LOG_FILE"
}

case "${1:-}" in
  start) start_lab ;;
  stop) stop_lab ;;
  *)
    echo "Usage: $0 start|stop" >&2
    exit 2
    ;;
esac
