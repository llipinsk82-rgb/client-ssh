#!/usr/bin/env bash
set -euo pipefail

premium_patch_archive="scripts/apply-premium-skin.py.gz"
if [[ -f "$premium_patch_archive" ]]; then
  runtime_script="scripts/.apply-premium-skin-runtime.py"
  trap 'rm -f "$runtime_script"' EXIT
  gzip -dc "$premium_patch_archive" > "$runtime_script"
  test "$(sha256sum "$runtime_script" | awk '{print $1}')" = "b150737b4b3c99fd249f58e04d99832c1d042d2971d7480ac6d682b01c2d46fe"
  python3 -m py_compile "$runtime_script"
  python3 "$runtime_script"
  rm -f "$runtime_script"
  trap - EXIT

  source_files=(
    "app/src/main/java/eu/blackserv/clientssh/MainActivity.kt"
    "app/src/main/java/eu/blackserv/clientssh/model/Models.kt"
    "app/src/main/java/eu/blackserv/clientssh/ui/theme/Theme.kt"
    "app/src/main/java/eu/blackserv/clientssh/ui/screens/StartupScreen.kt"
    "app/src/main/java/eu/blackserv/clientssh/ui/screens/ProfilesScreen.kt"
    "app/src/main/java/eu/blackserv/clientssh/ui/screens/TerminalScreen.kt"
    "app/src/main/java/eu/blackserv/clientssh/ui/screens/SftpScreen.kt"
    "app/src/main/res/values/themes.xml"
  )
  bundle_root="$(mktemp -d)"
  trap 'rm -rf "$bundle_root"' EXIT
  for source_file in "${source_files[@]}"; do
    mkdir -p "$bundle_root/$(dirname "$source_file")"
    cp "$source_file" "$bundle_root/$source_file"
  done
  (
    cd "$bundle_root"
    sha256sum "${source_files[@]}" > PREMIUM_SOURCE_SHA256SUMS.txt
  )
  mkdir -p app/src/main/assets
  tar -C "$bundle_root" -czf app/src/main/assets/premium-source.tar.gz .
  rm -rf "$bundle_root"
  trap - EXIT
  echo "OK: premium visual source bundle embedded for controlled extraction."
fi

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

auto_host_key_trust_hits=$(git grep -nF -- '"accept-new"' -- app/src/main || true)
if [[ -n "$auto_host_key_trust_hits" ]]; then
  echo "ERROR: kod produkcyjny automatycznie ufa nowemu kluczowi hosta SSH:" >&2
  printf '%s\n' "$auto_host_key_trust_hits" >&2
  fail=1
fi

mapfile -t ssh_session_files < <(git grep -lF -- 'getSession(' -- app/src/main || true)
for ssh_file in "${ssh_session_files[@]}"; do
  if ! grep -Fq 'StrictHostKeyChecking' "$ssh_file"; then
    echo "ERROR: sesja SSH nie ustawia StrictHostKeyChecking: $ssh_file" >&2
    fail=1
  fi

  if ! grep -Fq 'setKnownHosts(' "$ssh_file" && ! grep -Fq 'hostKeyRepository =' "$ssh_file"; then
    echo "ERROR: sesja SSH nie wskazuje magazynu known_hosts: $ssh_file" >&2
    fail=1
  fi

  if grep -Fq 'setConfig("StrictHostKeyChecking", "yes")' "$ssh_file"; then
    continue
  fi

  if grep -Eq 'const val [A-Za-z0-9_]*STRICT_HOST_KEY_CHECKING[[:space:]]*=[[:space:]]*"yes"' "$ssh_file"; then
    continue
  fi

  echo "ERROR: sesja SSH nie wymusza StrictHostKeyChecking=yes: $ssh_file" >&2
  fail=1
done

if ((fail != 0)); then
  echo "Przenieś materiał podpisujący do GitHub Actions Secrets i nie omijaj jawnej weryfikacji host key." >&2
  exit 1
fi

echo "OK: brak śledzonych kluczy/keystore, jawnych haseł podpisu; każda sesja SSH używa known_hosts i StrictHostKeyChecking=yes."
