$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

function Run-Checked {
    param([scriptblock]$Command)
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" }
}

if ($env:OS -ne 'Windows_NT') { throw 'Este script destina-se ao Windows.' }

# Locate the standalone Build Tools as well as full Visual Studio installations.
# This runs inside an ordinary VS Code terminal; no developer shell is required.
$providedVcpkgRoot = $env:VCPKG_ROOT
$vsWhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vsWhere)) {
    throw 'Instala os Build Tools 2022: Desenvolvimento para desktop com C++, Windows SDK e CMake. Nao precisas do Visual Studio completo.'
}
$vsPath = & $vsWhere -latest -products '*' -version '[17.0,18.0)' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or -not $vsPath) {
    throw 'Nao encontrei as ferramentas C++ de 2022. No instalador dos Build Tools, seleciona Desenvolvimento para desktop com C++.'
}
$devShell = Join-Path $vsPath 'Common7\Tools\Launch-VsDevShell.ps1'
if (-not (Test-Path -LiteralPath $devShell)) { throw 'A instalacao dos Build Tools esta incompleta: falta o Developer PowerShell.' }
Write-Host 'A preparar automaticamente as ferramentas C++...'
& $devShell -Arch amd64 -HostArch amd64 -SkipAutomaticLocation

# Some installations do not add the bundled CMake to PATH in an ordinary terminal.
if (-not (Get-Command cmake -ErrorAction SilentlyContinue)) {
    $cmakeBin = Join-Path $vsPath 'Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin'
    if (-not (Test-Path -LiteralPath (Join-Path $cmakeBin 'cmake.exe'))) {
        throw 'Adiciona Ferramentas CMake do C++ para Windows no instalador dos Build Tools.'
    }
    $env:PATH = "$cmakeBin;$env:PATH"
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Instala o Git for Windows e volta a abrir o VS Code.' }

# Do not accidentally select a read-only vcpkg bundled with Visual Studio.
if ($providedVcpkgRoot) {
    $env:VCPKG_ROOT = $providedVcpkgRoot
} else {
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
Write-Host ''
Write-Host 'Compilacao e testes concluidos.'
Write-Host 'Build: build/windows/Release/investment-radar.exe'
Write-Host 'Para abrir a aplicacao: .\iniciar.cmd'
