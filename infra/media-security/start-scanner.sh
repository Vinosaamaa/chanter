#!/bin/sh
set -eu

# /init has already prepared signature volume ownership before invoking this command.
unset CLAMAV_NO_CLAMD
client_gid="${CHANTER_SCANNER_CLIENT_GID:-10001}"
case "$client_gid" in ''|*[!0-9]*) echo "Invalid scanner client group" >&2; exit 1 ;; esac
[ "$client_gid" -ge 1 ] && [ "$client_gid" -le 65535 ]
if ! getent group chanter-media >/dev/null; then
  groupadd --non-unique --gid "$client_gid" chanter-media
fi
[ "$(getent group chanter-media | cut -d: -f3)" = "$client_gid" ]
usermod --append --groups chanter-media clamav
install -d -m 2770 -o clamav -g chanter-media /run/clamav

# Updating during engine startup can miss clamd's notification. Finish first.
freshclam --foreground --stdout
exec /init
