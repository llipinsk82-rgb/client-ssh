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
for key in "${required_keys[@]}"; do
  grep -Eq "^${key}=" <<<"$content" || {
    echo "FAIL: brak pola ${key}" >&2
    exit 1
  }
done

worker="$(sed -n 's/^worker=//p' <<<"$content" | tail -n1)"
status="$(sed -n 's/^status=//p' <<<"$content" | tail -n1)"
history_records="$(sed -n 's/^history_records=//p' <<<"$content" | tail -n1)"
profile="$(sed -n 's/^profile=//p' <<<"$content" | tail -n1)"

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

if grep -Eiq '^(host|hostname|user|username|login|password|passphrase|private_key|key|secret|token)=' <<<"$content"; then
  echo "FAIL: raport zawiera potencjalnie wrażliwe pole" >&2
  exit 1
fi

if grep -Eq '^worker_(started|finished|detail)=' <<<"$content"; then
  grep -Eq '^worker_started=.+$' <<<"$content" || { echo "FAIL: pusty worker_started" >&2; exit 1; }
  grep -Eq '^worker_finished=.+$' <<<"$content" || { echo "FAIL: pusty worker_finished" >&2; exit 1; }
fi

echo "PASS: Health Monitor worker=${worker}, status=${status}, history_records=${history_records}, profile=${profile}"
