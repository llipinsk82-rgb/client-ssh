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

  if ! grep -Fq 'setKnownHosts(' "$ssh_file"; then
    echo "ERROR: sesja SSH nie wskazuje trwałego magazynu known_hosts: $ssh_file" >&2
    fail=1
  fi

  if grep -Fq 'setConfig("StrictHostKeyChecking", "yes")' "$ssh_file"; then
    :
  elif grep -Eq 'const val [A-Za-z0-9_]*STRICT_HOST_KEY_CHECKING[[:space:]]*=[[:space:]]*"yes"' "$ssh_file"; then
    :
  else
    echo "ERROR: sesja SSH nie wymusza StrictHostKeyChecking=yes: $ssh_file" >&2
    fail=1
  fi
done

host_trust_file="app/src/main/java/eu/blackserv/clientssh/ssh/HostKeyTrust.kt"
sftp_file="app/src/main/java/eu/blackserv/clientssh/sftp/SftpClient.kt"
telemetry_file="app/src/main/java/eu/blackserv/clientssh/health/JschSshTelemetryTransport.kt"

if ! grep -Fq 'sshHostKeyAlias(displayHost, port)' "$host_trust_file"; then
  echo "ERROR: terminal nie rozdziela kluczy hosta według host:port." >&2
  fail=1
fi
if ! grep -Fq 'PortScopedHostKeyRepository' "$sftp_file"; then
  echo "ERROR: SFTP nie rozdziela kluczy hosta według host:port." >&2
  fail=1
fi
if ! grep -Fq 'PortScopedHostKeyRepository' "$telemetry_file"; then
  echo "ERROR: telemetria nie rozdziela kluczy hosta według host:port." >&2
  fail=1
fi

if ((fail != 0)); then
  echo "Przenieś materiał podpisujący do GitHub Actions Secrets i nie omijaj jawnej weryfikacji host key." >&2
  exit 1
fi

echo "OK: brak śledzonych kluczy/keystore; wszystkie sesje używają known_hosts, StrictHostKeyChecking=yes oraz tożsamości host:port."
