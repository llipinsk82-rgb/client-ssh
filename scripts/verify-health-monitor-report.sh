#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Użycie: $0 [RAPORT.txt|-]" >&2
  echo "Bez argumentu lub z '-' raport jest czytany ze standardowego wejścia." >&2
  exit 64
}

[[ $# -le 1 ]] || usage
report="${1:--}"

if [[ "$report" == "-" ]]; then
  content="$(tr -d '\r')"
else
  [[ -f "$report" ]] || { echo "FAIL: brak pliku raportu: $report" >&2; exit 66; }
  content="$(tr -d '\r' < "$report")"
fi

[[ -n "$content" ]] || { echo "FAIL: pusty raport" >&2; exit 1; }

required_keys=(profile enabled worker status last_check last_success failures history_records)
optional_keys=(worker_started worker_finished worker_detail)

field_count() {
  local key="$1"
  grep -Ec "^[[:space:]]*${key}[[:space:]]*=" <<<"$content" || true
}

field_value() {
  local key="$1"
  sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//p" <<<"$content"
}

for key in "${required_keys[@]}"; do
  count="$(field_count "$key")"
  [[ "$count" -eq 1 ]] || {
    echo "FAIL: pole ${key} musi wystąpić dokładnie raz (jest: ${count})" >&2
    exit 1
  }
done

for key in "${optional_keys[@]}"; do
  count="$(field_count "$key")"
  [[ "$count" -le 1 ]] || {
    echo "FAIL: pole ${key} nie może występować wielokrotnie" >&2
    exit 1
  }
done

worker="$(field_value worker)"
status="$(field_value status)"
history_records="$(field_value history_records)"
profile="$(field_value profile)"

[[ "$worker" == "SUCCESS" ]] || {
  echo "FAIL: worker zakończył się wynikiem ${worker:-BRAK}" >&2
  exit 1
}
[[ "$status" == "ONLINE" || "$status" == "OFFLINE" ]] || {
  echo "FAIL: status po teście nie jest rozstrzygnięty: ${status:-BRAK}" >&2
  exit 1
}
[[ "$history_records" =~ ^[0-9]+$ && "$history_records" -ge 1 ]] || {
  echo "FAIL: historia nie zawiera pomiaru: ${history_records:-BRAK}" >&2
  exit 1
}
[[ "$profile" =~ ^[0-9a-fA-F]+$ ]] || {
  echo "FAIL: identyfikator profilu nie jest hashem" >&2
  exit 1
}

if grep -Eiq '^[[:space:]]*(host|hostname|user|username|login|password|passphrase|private[ _-]?key|key|secret|token)[[:space:]]*=' <<<"$content"; then
  echo "FAIL: raport zawiera potencjalnie wrażliwe pole" >&2
  exit 1
fi

worker_started_count="$(field_count worker_started)"
worker_finished_count="$(field_count worker_finished)"
worker_detail_count="$(field_count worker_detail)"
if (( worker_started_count + worker_finished_count + worker_detail_count > 0 )); then
  [[ "$worker_started_count" -eq 1 && -n "$(field_value worker_started)" ]] || {
    echo "FAIL: brak lub pusty worker_started" >&2
    exit 1
  }
  [[ "$worker_finished_count" -eq 1 && -n "$(field_value worker_finished)" ]] || {
    echo "FAIL: brak lub pusty worker_finished" >&2
    exit 1
  }
fi

echo "PASS: Health Monitor worker=${worker}, status=${status}, history_records=${history_records}, profile=${profile}"
