#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "usage: $0 command [args...]" >&2
  exit 2
fi

java_bin="${CHANTER_JAVA_BIN:-}"
if [ -z "$java_bin" ] && [ "$(uname -s)" = Darwin ] && [ -x /usr/libexec/java_home ]; then
  java_home_21="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [ -n "$java_home_21" ]; then
    export JAVA_HOME="$java_home_21"
    java_bin="$JAVA_HOME/bin/java"
  fi
fi

if [ -z "$java_bin" ] && [ -n "${JAVA_HOME:-}" ]; then
  java_bin="$JAVA_HOME/bin/java"
fi
if [ -z "$java_bin" ]; then
  java_bin="$(command -v java || true)"
fi
if [ -z "$java_bin" ] || [ ! -x "$java_bin" ]; then
  echo "Java 21 is required, but no Java runtime was found. Install JDK 21 and retry." >&2
  exit 1
fi

version_line="$($java_bin -version 2>&1 | head -1)"
version="$(printf '%s\n' "$version_line" | sed -E 's/.*version "([^"]+)".*/\1/')"
major="${version%%.*}"
if [ "$major" = "1" ]; then
  remainder="${version#*.}"
  major="${remainder%%.*}"
fi
if [ "$major" != "21" ]; then
  echo "Java 21 is required; detected $version using $java_bin. Install/select JDK 21 and retry." >&2
  exit 1
fi

if [[ "$java_bin" == */bin/java ]]; then
  export JAVA_HOME="${java_bin%/bin/java}"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

exec "$@"
