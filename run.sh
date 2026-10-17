#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mode=linux
for arg in "$@"; do
    case "$arg" in
        --headless) mode=headless ;;
        -h|--help)
            echo 'Uso: ./run.sh [--headless] [--port PORTA]'
            echo 'Abre a aplicacao ja compilada por ./install.sh.'
            exit 0 ;;
    esac
done
if [[ "$(uname -s)" != Linux ]]; then
    echo 'Este script destina-se ao Linux. Em Windows usa run.bat.' >&2
    exit 1
fi
binary="${IR_BUILD_DIR:-$repo_dir/build/$mode}/investment-radar"
if [[ ! -x "$binary" ]]; then
    if [[ "$mode" == headless ]]; then
        echo 'Primeiro executa ./install.sh --headless.' >&2
    else
        echo 'Primeiro executa ./install.sh.' >&2
    fi
    exit 1
fi
exec "$binary" "$@"
