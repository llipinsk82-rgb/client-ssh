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

expect_rejected() {
  local description="$1"
  local report_content="$2"
  if printf '%s\n' "$report_content" | "$validator" >/dev/null 2>&1; then
    echo "FAIL: zaakceptowano: $description" >&2
    exit 1
  fi
}

expect_rejected "worker=RETRY" "${valid_report/worker=SUCCESS/worker=RETRY}"
expect_rejected "jawne pole host" "${valid_report}"$'\n''host=example.invalid'
expect_rejected "pole host ze spacjami" "${valid_report}"$'\n''  host = example.invalid'
expect_rejected "wariant private-key" "${valid_report}"$'\n''private-key = should-never-appear'
expect_rejected "zduplikowany worker" "${valid_report}"$'\n''worker=SUCCESS'
expect_rejected "zduplikowany status z inną wartością" "${valid_report}"$'\n''status=OFFLINE'
expect_rejected "niepełna diagnostyka workera" "$(printf '%s\n' "$valid_report" | grep -v '^worker_finished=')"

if printf '' | "$validator" >/dev/null 2>&1; then
  echo "FAIL: pusty raport powinien zostać odrzucony" >&2
  exit 1
fi

echo "PASS: verify-health-monitor-report.sh hardened regression suite"
