#!/usr/bin/env bash
set -euo pipefail

guard="scripts/check-no-release-secrets.sh"
target="app/src/main/java/eu/blackserv/clientssh/sftp/SftpClient.kt"
tmp_dir="$(mktemp -d)"
original="$tmp_dir/SftpClient.kt"
log_file="$tmp_dir/guard.log"

cp "$target" "$original"
cleanup() {
  cp "$original" "$target"
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

expect_guard_failure() {
  local label="$1"
  if bash "$guard" >"$log_file" 2>&1; then
    echo "ERROR: guard zaakceptował niebezpieczny przypadek: $label" >&2
    cat "$log_file" >&2
    exit 1
  fi
}

sed -i 's/SFTP_STRICT_HOST_KEY_CHECKING = "yes"/SFTP_STRICT_HOST_KEY_CHECKING = "no"/' "$target"
expect_guard_failure "StrictHostKeyChecking=no"
cp "$original" "$target"

sed -i '/setKnownHosts(knownHostsFile.absolutePath)/d' "$target"
expect_guard_failure "brak magazynu known_hosts"
cp "$original" "$target"

bash "$guard" >/dev/null

echo "OK: guard odrzuca niebezpieczną politykę host key i brak known_hosts."
