#!/usr/bin/env bash
set -euo pipefail

fail=0
self_path="scripts/check-no-release-secrets.sh"

mapfile -t tracked_files < <(git ls-files)

sensitive_files=$(printf '%s\n' "${tracked_files[@]}" | grep -Eis '(^|/)([^/]+\.(jks|keystore|p12|pfx|pem|key)|keystore\.properties|.*signing.*\.b64)$' || true)
if [[ -n "$sensitive_files" ]]; then
  echo "ERROR: repozytorium śledzi pliki mogące zawierać materiał podpisujący:" >&2
  printf '%s\n' "$sensitive_files" >&2
  fail=1
fi

mapfile -t text_files < <(
  printf '%s\n' "${tracked_files[@]}" |
    grep -E '\.(gradle|gradle\.kts|kts|kt|java|yml|yaml|properties|json|xml|sh|md)$' |
    grep -Fxv "$self_path" || true
)

if ((${#text_files[@]} > 0)); then
  private_key_hits=$(git grep -nE -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----' -- "${text_files[@]}" || true)
  if [[ -n "$private_key_hits" ]]; then
    echo "ERROR: znaleziono blok klucza prywatnego w śledzonym pliku:" >&2
    printf '%s\n' "$private_key_hits" >&2
    fail=1
  fi

  literal_password_hits=$(git grep -nEi -- '(storePassword|keyPassword)[[:space:]]*=[[:space:]]*"[^"$][^"]*"' -- "${text_files[@]}" || true)
  if [[ -n "$literal_password_hits" ]]; then
    echo "ERROR: znaleziono jawne hasło podpisu w konfiguracji:" >&2
    printf '%s\n' "$literal_password_hits" >&2
    fail=1
  fi
fi

if ((fail != 0)); then
  echo "Przenieś materiał podpisujący do GitHub Actions Secrets i usuń go z historii Git." >&2
  exit 1
fi

echo "OK: brak śledzonych keystore, kluczy prywatnych i jawnych haseł podpisu."
