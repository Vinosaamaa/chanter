#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cat >"$TMP_DIR/java17" <<'EOF'
#!/usr/bin/env bash
echo 'openjdk version "17.0.7" 2023-04-18' >&2
EOF
chmod +x "$TMP_DIR/java17"

exit_code=0
output="$(CHANTER_JAVA_BIN="$TMP_DIR/java17" "$ROOT/scripts/java21.sh" true 2>&1)" || exit_code=$?

if [ "$exit_code" -eq 0 ]; then
  echo "FAIL: Java 17 was accepted" >&2
  exit 1
fi
if [[ "$output" != *"Java 21 is required"* ]] || [[ "$output" != *"17.0.7"* ]]; then
  echo "FAIL: incompatible-JDK message was not useful: $output" >&2
  exit 1
fi

echo "ok: incompatible JDK fails early with a useful message"
