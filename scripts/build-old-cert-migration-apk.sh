#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Użycie:
  scripts/build-old-cert-migration-apk.sh \
    --keystore /bezpieczna/sciezka/stary.jks \
    --alias STARY_ALIAS \
    (--reference-apk /sciezka/do/zainstalowanej-aplikacji.apk | --adb-reference) \
    [--output DIR]

Buduje lokalny APK migracyjny, podpisuje go starym certyfikatem i porównuje
certyfikat z APK faktycznie zainstalowanym na telefonie. Artefakt służy wyłącznie
do aktualizacji starej instalacji i wykonania zaszyfrowanego eksportu profili.

Skrypt:
  - nigdy nie kopiuje JKS do repozytorium,
  - nie przekazuje haseł w argv ani zmiennych środowiskowych,
  - używa plików haseł 600 i usuwa je przez trap,
  - wymaga czystego drzewa Git,
  - zapisuje commit źródłowy, fingerprint i SHA-256 artefaktu,
  - nie wysyła APK ani klucza do GitHub Actions lub GitHub Releases.

Opcje:
  --keystore PATH       Stary JKS poza repozytorium.
  --alias NAME          Alias starego klucza.
  --reference-apk PATH  APK pobrany z aktualnie zainstalowanej starej aplikacji.
  --adb-reference       Pobierz bazowy APK z podłączonego urządzenia przez adb.
  --output DIR          Katalog wyjściowy poza repo; domyślnie
                        ~/.client-ssh/migration-build.
  -h, --help            Pokaż pomoc.
EOF
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Brak wymaganego polecenia: $1"
}

find_build_tool() {
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then
    command -v "$name"
    return
  fi
  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  [[ -n "$sdk_root" ]] || fail "Brak $name i ANDROID_SDK_ROOT/ANDROID_HOME."
  local found
  found="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name "$name" -print 2>/dev/null | sort -V | tail -n 1)"
  [[ -n "$found" ]] || fail "Nie znaleziono $name w Android SDK."
  printf '%s\n' "$found"
}

normalize_fingerprint() {
  tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}

apk_fingerprint() {
  local apk="$1"
  "$apksigner" verify --verbose --print-certs "$apk" \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
    | head -n 1 \
    | normalize_fingerprint
}

keystore_fingerprint() {
  keytool -list -v \
    -keystore "$keystore" \
    -storepass:file "$store_password_file" \
    -alias "$alias_name" \
    | sed -n 's/^[[:space:]]*SHA256: //p' \
    | head -n 1 \
    | normalize_fingerprint
}

keystore=""
alias_name=""
reference_apk=""
adb_reference=false
output_dir="${HOME}/.client-ssh/migration-build"
store_password_file=""
key_password_file=""
tmp_dir=""
store_password=""
store_password_repeat=""
key_password=""
key_password_repeat=""

cleanup() {
  store_password=""
  store_password_repeat=""
  key_password=""
  key_password_repeat=""
  [[ -n "$store_password_file" ]] && rm -f -- "$store_password_file"
  [[ -n "$key_password_file" ]] && rm -f -- "$key_password_file"
  [[ -n "$tmp_dir" ]] && rm -rf -- "$tmp_dir"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keystore) keystore="${2:?brak wartości dla --keystore}"; shift 2 ;;
    --alias) alias_name="${2:?brak wartości dla --alias}"; shift 2 ;;
    --reference-apk) reference_apk="${2:?brak wartości dla --reference-apk}"; shift 2 ;;
    --adb-reference) adb_reference=true; shift ;;
    --output) output_dir="${2:?brak wartości dla --output}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) fail "Nieznana opcja: $1" ;;
  esac
done

[[ -n "$keystore" ]] || fail "Podaj --keystore."
[[ -n "$alias_name" ]] || fail "Podaj --alias."
if [[ "$adb_reference" == true && -n "$reference_apk" ]]; then
  fail "Użyj dokładnie jednej opcji: --reference-apk albo --adb-reference."
fi
if [[ "$adb_reference" == false && -z "$reference_apk" ]]; then
  fail "Podaj --reference-apk albo --adb-reference."
fi

for command in bash env git gradle keytool sha256sum mktemp stat realpath sed grep find sort tr head install; do
  require_command "$command"
done
[[ "$adb_reference" == false ]] || require_command adb

apksigner="$(find_build_tool apksigner)"
zipalign="$(find_build_tool zipalign)"
aapt="$(find_build_tool aapt)"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || fail "Uruchom skrypt wewnątrz repozytorium."
cd "$repo_root"
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || fail "Drzewo Git nie jest czyste."
[[ ! -e app/signing/client-ssh-release.jks ]] || fail "Usuń lokalny JKS release z drzewa roboczego przed buildem migracyjnym."
bash scripts/check-no-release-secrets.sh >/dev/null

keystore="$(realpath "$keystore")"
[[ -f "$keystore" ]] || fail "Nie znaleziono JKS: $keystore"
repo_real="$(realpath "$repo_root")"
case "$keystore" in
  "$repo_real"/*) fail "Stary JKS musi znajdować się poza repozytorium." ;;
esac

keystore_mode="$(stat -c '%a' "$keystore")"
keystore_mode_value=$((8#$keystore_mode))
(( (keystore_mode_value & 077) == 0 )) || fail "Uprawnienia JKS są zbyt szerokie ($keystore_mode). Ustaw chmod 600."

output_dir="$(realpath -m "$output_dir")"
case "$output_dir" in
  "$repo_real"|"$repo_real"/*) fail "Katalog wyjściowy musi znajdować się poza repozytorium." ;;
esac
umask 077
mkdir -p "$output_dir"
chmod 700 "$output_dir"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/client-ssh-migration.XXXXXX")"
chmod 700 "$tmp_dir"

if [[ "$adb_reference" == true ]]; then
  mapfile -t device_paths < <(adb shell pm path eu.blackserv.clientssh 2>/dev/null | tr -d '\r' | sed -n 's/^package://p')
  ((${#device_paths[@]} > 0)) || fail "Nie znaleziono zainstalowanego pakietu eu.blackserv.clientssh."
  base_path=""
  for candidate in "${device_paths[@]}"; do
    if [[ "$candidate" == */base.apk ]]; then
      base_path="$candidate"
      break
    fi
  done
  [[ -n "$base_path" ]] || base_path="${device_paths[0]}"
  reference_apk="$tmp_dir/installed-old.apk"
  adb pull "$base_path" "$reference_apk" >/dev/null
else
  reference_apk="$(realpath "$reference_apk")"
fi
[[ -f "$reference_apk" ]] || fail "Nie znaleziono referencyjnego APK."

reference_badging="$($aapt dump badging "$reference_apk" | head -n 1)"
reference_package="$(printf '%s\n' "$reference_badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
[[ "$reference_package" == "eu.blackserv.clientssh" ]] || fail "Referencyjny APK ma inny package: $reference_package"
reference_fingerprint="$(apk_fingerprint "$reference_apk")"
[[ "$reference_fingerprint" =~ ^[0-9a-f]{64}$ ]] || fail "Nie udało się odczytać certyfikatu referencyjnego APK."

read -r -s -p "Hasło starego magazynu JKS: " store_password
echo
read -r -s -p "Powtórz hasło magazynu: " store_password_repeat
echo
[[ "$store_password" == "$store_password_repeat" ]] || fail "Hasła magazynu nie są identyczne."
read -r -s -p "Hasło starego klucza: " key_password
echo
read -r -s -p "Powtórz hasło klucza: " key_password_repeat
echo
[[ "$key_password" == "$key_password_repeat" ]] || fail "Hasła klucza nie są identyczne."

store_password_file="$(mktemp "$tmp_dir/.storepass.XXXXXX")"
key_password_file="$(mktemp "$tmp_dir/.keypass.XXXXXX")"
printf '%s\n' "$store_password" > "$store_password_file"
printf '%s\n' "$key_password" > "$key_password_file"
chmod 600 "$store_password_file" "$key_password_file"
store_password=""
store_password_repeat=""
key_password=""
key_password_repeat=""

jks_fingerprint="$(keystore_fingerprint)"
[[ "$jks_fingerprint" =~ ^[0-9a-f]{64}$ ]] || fail "Nie udało się odczytać certyfikatu starego JKS."
[[ "$jks_fingerprint" == "$reference_fingerprint" ]] || fail "Stary JKS nie odpowiada certyfikatowi zainstalowanej aplikacji."

commit_sha="$(git rev-parse HEAD)"
short_sha="${commit_sha:0:12}"
version_name="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)/\1/p' app/build.gradle.kts | head -n 1)"
[[ -n "$version_name" && -n "$version_code" ]] || fail "Nie udało się odczytać wersji aplikacji."

# Celowo odcinamy wszystkie zmienne nowego podpisu. Gradle ma zbudować APK unsigned.
env \
  -u CLIENT_SSH_RELEASE_STORE_PASSWORD \
  -u CLIENT_SSH_RELEASE_KEY_ALIAS \
  -u CLIENT_SSH_RELEASE_KEY_PASSWORD \
  gradle :app:clean :app:assembleRelease --stacktrace --console=plain

mapfile -t unsigned_candidates < <(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*unsigned*.apk' -print)
((${#unsigned_candidates[@]} == 1)) || fail "Oczekiwano dokładnie jednego unsigned APK; znaleziono ${#unsigned_candidates[@]}."
unsigned_apk="${unsigned_candidates[0]}"
aligned_apk="$tmp_dir/client-ssh-migration-aligned.apk"
signed_apk="$tmp_dir/client-ssh-migration-signed.apk"

"$zipalign" -P 16 -f 4 "$unsigned_apk" "$aligned_apk"
"$zipalign" -c -P 16 4 "$aligned_apk" >/dev/null

"$apksigner" sign \
  --ks "$keystore" \
  --ks-key-alias "$alias_name" \
  --ks-pass "file:$store_password_file" \
  --key-pass "file:$key_password_file" \
  --out "$signed_apk" \
  "$aligned_apk"

"$apksigner" verify --verbose --print-certs "$signed_apk" >/dev/null
signed_fingerprint="$(apk_fingerprint "$signed_apk")"
[[ "$signed_fingerprint" == "$reference_fingerprint" ]] || fail "Podpis APK nie odpowiada starej zainstalowanej aplikacji."

signed_badging="$($aapt dump badging "$signed_apk" | head -n 1)"
signed_package="$(printf '%s\n' "$signed_badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
signed_version_code="$(printf '%s\n' "$signed_badging" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")"
signed_version_name="$(printf '%s\n' "$signed_badging" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")"
[[ "$signed_package" == "eu.blackserv.clientssh" ]] || fail "Gotowy APK ma nieprawidłowy package."
[[ "$signed_version_code" == "$version_code" ]] || fail "Gotowy APK ma nieprawidłowy versionCode."
[[ "$signed_version_name" == "$version_name" ]] || fail "Gotowy APK ma nieprawidłowy versionName."

artifact_name="client-ssh-${version_name}-migration-old-cert-${short_sha}.apk"
artifact_path="$output_dir/$artifact_name"
[[ ! -e "$artifact_path" ]] || fail "Artefakt już istnieje: $artifact_path"
install -m 600 "$signed_apk" "$artifact_path"
(
  cd "$output_dir"
  sha256sum "$artifact_name" > SHA256SUMS.txt
)
printf '%s\n' "$signed_fingerprint" > "$output_dir/CERTIFICATE_SHA256.txt"
printf '%s\n' "$commit_sha" > "$output_dir/SOURCE_COMMIT.txt"
cat > "$output_dir/README-MIGRATION.txt" <<EOF_NOTICE
Client SSH migration build

Package: eu.blackserv.clientssh
Version: $version_name ($version_code)
Source commit: $commit_sha

Ten APK jest podpisany starym, skompromitowanym certyfikatem wyłącznie po to,
aby zaktualizować istniejącą starą instalację i wykonać zaszyfrowany eksport
profili. Nie publikuj go jako release i nie udostępniaj publicznie.

Po utworzeniu i sprawdzeniu backupu przejdź do APK podpisanego nowym certyfikatem.
EOF_NOTICE
chmod 600 "$output_dir"/*

printf '\nGOTOWE: %s\n' "$artifact_path"
printf 'Commit:  %s\n' "$commit_sha"
printf 'SHA-256 zapisano w: %s/SHA256SUMS.txt\n' "$output_dir"
printf 'Nie publikuj artefaktu i nie usuwaj starej aplikacji przed sprawdzonym eksportem.\n'
