#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
HOST="$ROOT/build/linux/investment-radar-core-host"
if [[ ! -x "$HOST" ]]; then
  "$ROOT/install.sh"
fi
export IR_NATIVE_HOST="$HOST"
exec "$ROOT/android/gradlew" -p "$ROOT/desktop-compose" --no-daemon run
