#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
desktop=ON
skip_deps=false
for arg in "$@"; do
    case "$arg" in
        --headless) desktop=OFF ;;
        --skip-system-deps) skip_deps=true ;;
        -h|--help)
            echo 'Uso: ./install.sh [--headless] [--skip-system-deps]'
            echo 'Prepara dependencias, compila e executa os testes. Depois: ./run.sh'
            exit 0 ;;
        *) echo "Opcao desconhecida: $arg" >&2; exit 2 ;;
    esac
done
if [[ "$(uname -s)" != Linux ]]; then
    echo 'Este script destina-se ao Linux. Em Windows usa install.bat.' >&2
    exit 1
fi
jobs="${IR_BUILD_JOBS:-2}"
if [[ ! "$jobs" =~ ^[1-9][0-9]*$ ]]; then
    echo 'IR_BUILD_JOBS deve ser um inteiro positivo.' >&2
    exit 2
fi
if [[ "$desktop" == ON ]]; then mode=linux; else mode=headless; fi
build_dir="${IR_BUILD_DIR:-$repo_dir/build/$mode}"
if ! $skip_deps; then
    missing=false
    for tool in c++ cmake ninja git pkg-config; do
        command -v "$tool" >/dev/null 2>&1 || missing=true
    done
    modules=(openssl sqlite3)
    if [[ "$desktop" == ON ]]; then modules+=(gtk+-3.0 webkit2gtk-4.1); fi
    if ! command -v pkg-config >/dev/null 2>&1 || ! pkg-config --exists "${modules[@]}"; then
        missing=true
    fi
    if $missing; then
        if ! command -v apt-get >/dev/null 2>&1; then
            echo 'Instala C++20, CMake 3.22+, Ninja, Git, pkg-config, OpenSSL e SQLite (headers).' >&2
            if [[ "$desktop" == ON ]]; then echo 'Para desktop: GTK3 e WebKit2GTK 4.1 (headers).' >&2; fi
            echo 'A instalacao automatica de dependencias suporta Debian/Ubuntu.' >&2
            exit 1
        fi
        packages=(build-essential cmake ninja-build git pkg-config libssl-dev libsqlite3-dev)
        if [[ "$desktop" == ON ]]; then packages+=(libgtk-3-dev libwebkit2gtk-4.1-dev); fi
        elevate=()
        if (( EUID != 0 )); then
            if ! command -v sudo >/dev/null 2>&1; then
                echo 'Faltam dependencias do sistema e sudo nao esta disponivel.' >&2
                exit 1
            fi
            elevate=(sudo)
        fi
        "${elevate[@]}" apt-get update
        "${elevate[@]}" apt-get install -y --no-install-recommends "${packages[@]}"
    fi
fi
cmake -S "$repo_dir" -B "$build_dir" -G Ninja -DCMAKE_BUILD_TYPE=Release -DIR_BUILD_DESKTOP="$desktop"
cmake --build "$build_dir" --parallel "$jobs"
ctest --test-dir "$build_dir" --output-on-failure
echo 'Compilacao e testes concluidos.'
if [[ "$desktop" == ON ]]; then
    echo 'Para abrir: ./run.sh'
else
    echo 'Para iniciar a API: ./run.sh --headless'
fi
