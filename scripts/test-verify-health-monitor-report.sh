#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
validator="$root/scripts/verify-health-monitor-report.sh"
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

valid_report='profile=0a1b2c3d
enabled=true
worker=SUCCESS
worker_started=2026-07-25T10:00:00Z
worker_finished=2026-07-25T10:00:01Z
worker_detail=TCP probe completed
status=ONLINE
last_check=2026-07-25T10:00:01Z
last_success=2026-07-25T10:00:01Z
failures=0
history_records=1'

printf '%s\n' "$valid_report" > "$tmpdir/valid.txt"

"$validator" "$tmpdir/valid.txt" | grep -q '^PASS:'
printf '%s\n' "$valid_report" | "$validator" | grep -q '^PASS:'
printf '%s\n' "$valid_report" | "$validator" - | grep -q '^PASS:'

if printf '%s\n' "${valid_report/worker=SUCCESS/worker=RETRY}" | "$validator" >/dev/null 2>&1; then
  echo "FAIL: RETRY powinien zostać odrzucony" >&2
  exit 1
fi

if printf '%s\nhost=example.invalid\n' "$valid_report" | "$validator" >/dev/null 2>&1; then
  echo "FAIL: raport z hostem powinien zostać odrzucony" >&2
  exit 1
fi

if printf '' | "$validator" >/dev/null 2>&1; then
  echo "FAIL: pusty raport powinien zostać odrzucony" >&2
  exit 1
fi

echo "PASS: verify-health-monitor-report.sh file/stdin regression suite"
