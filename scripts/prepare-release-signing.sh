#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Użycie: scripts/prepare-release-signing.sh [--upload] [--repo OWNER/REPO] [--output DIR]

Generuje nowy klucz Android release poza repozytorium, wyświetla fingerprint
SHA-256 i opcjonalnie zapisuje wymagane GitHub Actions Secrets przez GitHub CLI.

Hasła są pobierane wyłącznie interaktywnie i nie są przekazywane w argumentach
procesów. Generator używa tymczasowych plików z uprawnieniami 600 i usuwa je
niezależnie od wyniku.

Opcje:
  --upload          Wyślij sekrety przez `gh secret set` po wygenerowaniu klucza.
  --repo OWNER/REPO Repozytorium dla --upload (domyślnie z `gh repo view`).
  --output DIR      Prywatny katalog wyjściowy poza repozytorium.
  -h, --help        Pokaż pomoc.
EOF
}

missing_command() {
  local command="$1"
  echo "Brak wymaganego polecenia: $command" >&2
  case "$command" in
    keytool)
      echo "Debian/Ubuntu: sudo apt update && sudo apt install -y openjdk-17-jdk" >&2
      ;;
    base64|mktemp)
      echo "Debian/Ubuntu: sudo apt update && sudo apt install -y coreutils" >&2
      ;;
    gh)
      echo "Zainstaluj GitHub CLI i wykonaj: gh auth login" >&2
      ;;
  esac
  exit 69
}

upload=false
repo=""
output_dir="${HOME}/.client-ssh/signing"
store_password_file=""
key_password_file=""
store_password=""
store_password_repeat=""
key_password=""
key_password_repeat=""

cleanup_sensitive_data() {
  if [[ -n "$store_password_file" ]]; then
    rm -f -- "$store_password_file"
  fi
  if [[ -n "$key_password_file" ]]; then
    rm -f -- "$key_password_file"
  fi
  store_password=""
  store_password_repeat=""
  key_password=""
  key_password_repeat=""
}
trap cleanup_sensitive_data EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --upload) upload=true; shift ;;
    --repo) repo="${2:?brak wartości dla --repo}"; shift 2 ;;
    --output) output_dir="${2:?brak wartości dla --output}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Nieznana opcja: $1" >&2; usage >&2; exit 64 ;;
  esac
done

for command in keytool base64 mktemp; do
  command -v "$command" >/dev/null || missing_command "$command"
done

if [[ "$upload" == true ]]; then
  command -v gh >/dev/null || missing_command gh
  gh auth status >/dev/null
  if [[ -z "$repo" ]]; then
    repo="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
  fi
fi

umask 077
mkdir -p "$output_dir"
chmod 700 "$output_dir"

keystore="$output_dir/client-ssh-release.jks"
alias_name="client-ssh-release"

if [[ -e "$keystore" ]]; then
  echo "Plik już istnieje: $keystore" >&2
  echo "Nie nadpisuję istniejącego klucza." >&2
  exit 73
fi

read -r -s -p "Hasło magazynu (min. 16 znaków): " store_password
echo
read -r -s -p "Powtórz hasło magazynu: " store_password_repeat
echo
[[ "$store_password" == "$store_password_repeat" ]] || {
  echo "Hasła magazynu nie są identyczne." >&2
  exit 65
}
[[ ${#store_password} -ge 16 ]] || {
  echo "Hasło magazynu jest za krótkie." >&2
  exit 65
}

read -r -s -p "Hasło klucza (min. 16 znaków): " key_password
echo
read -r -s -p "Powtórz hasło klucza: " key_password_repeat
echo
[[ "$key_password" == "$key_password_repeat" ]] || {
  echo "Hasła klucza nie są identyczne." >&2
  exit 65
}
[[ ${#key_password} -ge 16 ]] || {
  echo "Hasło klucza jest za krótkie." >&2
  exit 65
}

store_password_file="$(mktemp "$output_dir/.storepass.XXXXXX")"
key_password_file="$(mktemp "$output_dir/.keypass.XXXXXX")"
printf '%s\n' "$store_password" > "$store_password_file"
printf '%s\n' "$key_password" > "$key_password_file"
chmod 600 "$store_password_file" "$key_password_file"

keytool -genkeypair \
  -keystore "$keystore" \
  -storetype JKS \
  -storepass:file "$store_password_file" \
  -alias "$alias_name" \
  -keypass:file "$key_password_file" \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 10000 \
  -dname "CN=Client SSH, OU=BlackServ, O=BlackServ, C=PL" \
  -noprompt

fingerprint="$(
  keytool -list -v \
    -keystore "$keystore" \
    -storepass:file "$store_password_file" \
    -alias "$alias_name" |
    sed -n 's/^[[:space:]]*SHA256: //p' |
    head -n 1 |
    tr -d ':[:space:]' |
    tr '[:upper:]' '[:lower:]'
)"

[[ "$fingerprint" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Nie udało się odczytać fingerprintu SHA-256." >&2
  exit 70
}

keystore_b64="$output_dir/client-ssh-release.jks.b64"
base64 < "$keystore" | tr -d '\n' > "$keystore_b64"
chmod 600 "$keystore" "$keystore_b64"

cat > "$output_dir/CERTIFICATE_SHA256.txt" <<EOF_CERT
$fingerprint
EOF_CERT
chmod 600 "$output_dir/CERTIFICATE_SHA256.txt"

if [[ "$upload" == true ]]; then
  gh secret set CLIENT_SSH_RELEASE_KEYSTORE_B64 --repo "$repo" < "$keystore_b64"
  printf '%s' "$store_password" | gh secret set CLIENT_SSH_RELEASE_STORE_PASSWORD --repo "$repo"
  printf '%s' "$alias_name" | gh secret set CLIENT_SSH_RELEASE_KEY_ALIAS --repo "$repo"
  printf '%s' "$key_password" | gh secret set CLIENT_SSH_RELEASE_KEY_PASSWORD --repo "$repo"
  printf '%s' "$fingerprint" | gh secret set CLIENT_SSH_RELEASE_CERT_SHA256 --repo "$repo"
  echo "Sekrety zapisano w repozytorium: $repo"
fi

cleanup_sensitive_data

cat <<EOF_DONE

Gotowe.
Klucz:       $keystore
Fingerprint: $fingerprint
Base64:      $keystore_b64

Wykonaj co najmniej dwie zaszyfrowane kopie zapasowe katalogu:
$output_dir

Nie dodawaj żadnego z tych plików do Git ani do publicznych załączników.
EOF_DONE
