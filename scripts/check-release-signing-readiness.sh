#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Użycie: scripts/check-release-signing-readiness.sh [--dependencies-only] [--repo OWNER/REPO]

Sprawdza gotowość środowiska do wygenerowania i wysłania klucza release.
Nie odczytuje ani nie wyświetla wartości sekretów.

Opcje:
  --dependencies-only  Sprawdź tylko lokalne zależności: keytool, base64, mktemp i gh.
  --repo OWNER/REPO    Repozytorium GitHub (domyślnie llipinsk82-rgb/client-ssh).
  -h, --help           Pokaż pomoc.
EOF
}

dependencies_only=false
repo="llipinsk82-rgb/client-ssh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dependencies-only) dependencies_only=true; shift ;;
    --repo) repo="${2:?brak wartości dla --repo}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Nieznana opcja: $1" >&2; usage >&2; exit 64 ;;
  esac
done

missing=0
for command in keytool base64 mktemp gh; do
  if command -v "$command" >/dev/null 2>&1; then
    printf 'OK   %-8s %s\n' "$command" "$(command -v "$command")"
  else
    printf 'BRAK %-8s\n' "$command" >&2
    missing=1
  fi
done

if [[ $missing -ne 0 ]]; then
  cat >&2 <<'EOF'

Debian/Ubuntu:
  sudo apt update
  sudo apt install -y openjdk-17-jdk coreutils gh
EOF
  exit 69
fi

if [[ "$dependencies_only" == true ]]; then
  echo "OK: lokalne zależności są dostępne."
  exit 0
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "BRAK: aktywnego logowania GitHub CLI." >&2
  echo "Uruchom: gh auth login" >&2
  exit 77
fi

echo "OK: GitHub CLI jest zalogowany."

if ! gh repo view "$repo" >/dev/null 2>&1; then
  echo "BRAK: dostępu do repozytorium $repo." >&2
  exit 77
fi

echo "OK: dostęp do repozytorium $repo."

required=(
  CLIENT_SSH_RELEASE_KEYSTORE_B64
  CLIENT_SSH_RELEASE_STORE_PASSWORD
  CLIENT_SSH_RELEASE_KEY_ALIAS
  CLIENT_SSH_RELEASE_KEY_PASSWORD
  CLIENT_SSH_RELEASE_CERT_SHA256
)

mapfile -t present < <(gh secret list --repo "$repo" --json name --jq '.[].name' | sort)
missing_secrets=()
for name in "${required[@]}"; do
  if printf '%s\n' "${present[@]}" | grep -Fxq "$name"; then
    echo "OK: secret $name"
  else
    echo "BRAK: secret $name"
    missing_secrets+=("$name")
  fi
done

if [[ ${#missing_secrets[@]} -gt 0 ]]; then
  echo
  echo "Środowisko lokalne jest gotowe, ale brakuje ${#missing_secrets[@]} sekretów."
  echo "Uruchom generator:"
  echo "  bash scripts/prepare-release-signing.sh --upload --repo $repo"
  exit 3
fi

echo
echo "GOTOWE: zależności, autoryzacja, dostęp do repo i komplet nazw sekretów są poprawne."
