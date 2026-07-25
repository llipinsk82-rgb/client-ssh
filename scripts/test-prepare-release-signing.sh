#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
generator="$script_dir/prepare-release-signing.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$tmp_dir"' EXIT

fake_bin="$tmp_dir/bin"
output_dir="$tmp_dir/output"
argv_log="$tmp_dir/keytool-argv.log"
mkdir -p "$fake_bin"

cat > "$fake_bin/keytool" <<'FAKE_KEYTOOL'
#!/usr/bin/env bash
set -euo pipefail
: "${KEYTOOL_ARGV_LOG:?}"
printf '%s\n' '---' >> "$KEYTOOL_ARGV_LOG"
printf '%s\n' "$@" >> "$KEYTOOL_ARGV_LOG"

keystore=""
previous=""
for argument in "$@"; do
  if [[ "$previous" == "-keystore" ]]; then
    keystore="$argument"
  fi
  previous="$argument"
done

if [[ " $* " == *" -genkeypair "* ]]; then
  printf 'test-keystore\n' > "$keystore"
else
  printf 'SHA256: AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA\n'
fi
FAKE_KEYTOOL
chmod +x "$fake_bin/keytool"

store_password='store-password-123456789'
key_password='key-password-12345678901'

printf '%s\n%s\n%s\n%s\n' \
  "$store_password" "$store_password" \
  "$key_password" "$key_password" |
  PATH="$fake_bin:$PATH" KEYTOOL_ARGV_LOG="$argv_log" \
    bash "$generator" --output "$output_dir" >/dev/null

test -f "$output_dir/client-ssh-release.jks"
test -f "$output_dir/client-ssh-release.jks.b64"
test -f "$output_dir/CERTIFICATE_SHA256.txt"
test "$(tr -d '\n\r ' < "$output_dir/CERTIFICATE_SHA256.txt" | wc -c)" -eq 64

if grep -Fq -- "$store_password" "$argv_log" || grep -Fq -- "$key_password" "$argv_log"; then
  echo "ERROR: hasło trafiło do argumentów keytool." >&2
  exit 1
fi

grep -Fxq -- '-storepass:file' "$argv_log"
grep -Fxq -- '-keypass:file' "$argv_log"

mapfile -t password_files < <(grep -E '/\.(storepass|keypass)\.' "$argv_log" || true)
if ((${#password_files[@]} != 3)); then
  echo "ERROR: nieoczekiwana liczba odwołań do plików haseł." >&2
  exit 1
fi
for password_file in "${password_files[@]}"; do
  if [[ -e "$password_file" ]]; then
    echo "ERROR: tymczasowy plik hasła nie został usunięty: $password_file" >&2
    exit 1
  fi
done

if find "$output_dir" -maxdepth 1 -type f \( -name '.storepass.*' -o -name '.keypass.*' \) -print -quit | grep -q .; then
  echo "ERROR: katalog wyjściowy zawiera tymczasowy plik hasła." >&2
  exit 1
fi

echo "OK: generator nie przekazuje haseł w argv i usuwa tymczasowe pliki."
