#!/usr/bin/env bash
# Reproduce the API/core development workflow without modifying the OS or checkout.
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tools_dir=/workspace/.venvs/investment-radar-tools
sqlite_dir=/workspace/.local/sqlite
apt_dir=/workspace/.apt
python3 -m venv "$tools_dir"
"$tools_dir/bin/python" -m pip install --disable-pip-version-check cmake==3.31.6 ninja==1.11.1.3
export PATH="$tools_dir/bin:$PATH"
if [[ ! -f "$sqlite_dir/usr/include/sqlite3.h" ]]; then
    mkdir -p "$apt_dir/lists/partial" "$apt_dir/cache/archives/partial" "$sqlite_dir"
    cat > "$apt_dir/investment-radar.sources.list" <<'SOURCES'
deb [signed-by=/usr/share/keyrings/debian-archive-keyring.gpg] https://deb.debian.org/debian trixie main
SOURCES
    apt_options=(-o "Dir::Etc::sourcelist=$apt_dir/investment-radar.sources.list" -o Dir::Etc::sourceparts=- -o "Dir::State::Lists=$apt_dir/lists" -o "Dir::Cache=$apt_dir/cache" -o Debug::NoLocking=1)
    apt-get "${apt_options[@]}" update
    (cd "$sqlite_dir" && apt-get "${apt_options[@]}" download libsqlite3-dev=3.46.1-7+deb13u2 && dpkg-deb -x libsqlite3-dev_*.deb .)
fi
cmake -S "$repo_dir" -B "$repo_dir/build" -G Ninja -DCMAKE_BUILD_TYPE=Debug -DIR_BUILD_DESKTOP=OFF -DSQLite3_INCLUDE_DIR="$sqlite_dir/usr/include" -DSQLite3_LIBRARY=/usr/lib/x86_64-linux-gnu/libsqlite3.so.0
cmake --build "$repo_dir/build" -j 2
ctest --test-dir "$repo_dir/build" --output-on-failure
python3 "$repo_dir/scripts/smoke.py" "$repo_dir/build/investment-radar"
node --check "$repo_dir/shared/web/app.js"
