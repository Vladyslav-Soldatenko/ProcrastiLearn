#!/usr/bin/env bash
set -euo pipefail

if (($# < 1)); then
  printf 'usage: %s LOGCAT_FILE [gradle arguments...]\n' "$0" >&2
  exit 2
fi

logcat_file=$1
shift
logcat_pid=''

cleanup() {
  local status=$?
  trap - EXIT
  if [[ -n "$logcat_pid" ]]; then
    kill "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || true
  fi
  nohup bash -c '
    sleep 30
    pkill -TERM -x crashpad_handler || true
    sleep 5
    pkill -KILL -x crashpad_handler || true
  ' </dev/null >/dev/null 2>&1 &
  exit "$status"
}
trap cleanup EXIT

adb logcat -c
adb logcat -v threadtime > "$logcat_file" &
logcat_pid=$!

./gradlew connectedDebugAndroidTest "$@"
