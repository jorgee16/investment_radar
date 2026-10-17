#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export IR_CONFIG_DIR="${IR_CONFIG_DIR:-/workspace/.investment-radar-runtime}"
mkdir -p "$IR_CONFIG_DIR"
# Foreground process: use the task runner/nohup to keep it running when needed.
exec "$repo_dir/build/investment-radar" --headless --port "${IR_PORT:-4173}"
