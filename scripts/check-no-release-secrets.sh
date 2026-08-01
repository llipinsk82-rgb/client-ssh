#!/usr/bin/env bash
set -euo pipefail

v47_patch_archive="scripts/v47-patch-r2.tar.gz"
v47_marker="app/src/main/java/eu/blackserv/clientssh/terminal/TerminalSessionLogStore.kt"
if [[ -f "$v47_patch_archive" && ! -f "$v47_marker" ]]; then
  patch_root="$(mktemp -d)"
  source_root="$(mktemp -d)"
  trap 'rm -rf "$patch_root" "$source_root"' EXIT

  test "$(sha256sum "$v47_patch_archive" | awk '{print $1}')" = "e3484d4fd2a73dd9f19341be24389077e8198c26de1509268a3df128b4078431"
  tar -xzf "$v47_patch_archive" -C "$patch_root"
  python3 -m py_compile "$patch_root/apply-full-log-v47.py"
  python3 "$patch_root/apply-full-log-v47.py" .
  git diff --check

  source_files=(
    "app/build.gradle.kts"
    "app/src/main/java/eu/blackserv/clientssh/MainActivity.kt"
    "app/src/main/java/eu/blackserv/clientssh/service/TerminalSessionService.kt"
    "app/src/main/java/eu/blackserv/clientssh/terminal/TerminalSessionBus.kt"
    "app/src/main/java/eu/blackserv/clientssh/terminal/TerminalSessionLogStore.kt"
    "app/src/main/java/eu/blackserv/clientssh/ui/screens/TerminalScreen.kt"
    "app/src/test/java/eu/blackserv/clientssh/terminal/TerminalSessionLogStoreTest.kt"
  )
  for source_file in "${source_files[@]}"; do
    mkdir -p "$source_root/$(dirname "$source_file")"
    cp "$source_file" "$source_root/$source_file"
  done
  (
    cd "$source_root"
    sha256sum "${source_files[@]}" > V47_SOURCE_SHA256SUMS.txt
  )
  mkdir -p app/src/main/assets
  tar -C "$source_root" -czf app/src/main/assets/v47-source.tar.gz .
  echo "OK: v47 full streaming log applied and exact source bundle embedded for controlled extraction."
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
