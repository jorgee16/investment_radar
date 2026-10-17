$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Run-Checked { param([scriptblock]$Command) & $Command; if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" } }
if (-not (Get-Command cmake -ErrorAction SilentlyContinue)) { throw 'Instala Visual Studio 2022: Desktop development with C++, Windows SDK e CMake tools.' }
if (-not $env:VCPKG_ROOT) {
    $env:VCPKG_ROOT = Join-Path (Get-Location) '.tools/vcpkg'
    if (-not (Test-Path $env:VCPKG_ROOT)) {
        Run-Checked { git clone https://github.com/microsoft/vcpkg $env:VCPKG_ROOT }
        Run-Checked { git -C $env:VCPKG_ROOT checkout a5f736224e85b32e25a8ffc52562333180396642 }
    }
}
if (-not (Test-Path "$env:VCPKG_ROOT/vcpkg.exe")) { Run-Checked { & "$env:VCPKG_ROOT/bootstrap-vcpkg.bat" -disableMetrics } }
Run-Checked { cmake --preset windows }
Run-Checked { cmake --build --preset windows }
Run-Checked { ctest --preset windows }
Write-Host 'Build: build/windows/Release/investment-radar.exe'
Write-Host 'Para iniciar: .\build\windows\Release\investment-radar.exe'
