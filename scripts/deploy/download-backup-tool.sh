#!/usr/bin/env bash
set -euo pipefail
architecture="${1:?Usage: download-backup-tool.sh amd64|arm64 OUTPUT}"
output="${2:?Output directory required}"
case "$architecture" in amd64|arm64) ;; *) echo 'Unsupported backup-tool architecture' >&2; exit 1 ;; esac
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$output"
version="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1])).restic.version' "$root/infra/production/tool-lock.json")"
digest="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1])).restic[process.argv[2]]' "$root/infra/production/tool-lock.json" "$architecture")"
curl --fail --location --retry 3 "https://github.com/restic/restic/releases/download/v$version/restic_${version}_linux_${architecture}.bz2" -o "$output/restic.bz2"
printf '%s  %s\n' "$digest" "$output/restic.bz2" | sha256sum --check -
bzip2 --decompress --stdout "$output/restic.bz2" > "$output/restic"
chmod 755 "$output/restic"
(cd "$output" && sha256sum restic > restic.sha256)
"$output/restic" version
