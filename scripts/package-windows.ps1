param(
    [string]$BuildDirectory = 'build/windows',
    [string]$OutputDirectory = 'dist/windows'
)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Este pacote destina-se ao Windows.' }
$repoDirectory = Split-Path $PSScriptRoot -Parent
function Absolute-TaskPath([string]$Value) {
    if ([IO.Path]::IsPathRooted($Value)) { return [IO.Path]::GetFullPath($Value) }
    return [IO.Path]::GetFullPath((Join-Path $repoDirectory $Value))
}
$buildPath = Absolute-TaskPath $BuildDirectory
$outputPath = Absolute-TaskPath $OutputDirectory
$cmakeCommand = Get-Command cmake -CommandType Application -ErrorAction SilentlyContinue
if ($cmakeCommand) {
    $cmakeExecutable = $cmakeCommand.Source
} else {
    # Reuse the CMake found by install.bat even from an ordinary terminal.
    $cachePath = Join-Path $buildPath 'CMakeCache.txt'
    if (Test-Path -LiteralPath $cachePath) {
        $entry = Select-String -LiteralPath $cachePath -Pattern '^CMAKE_COMMAND:INTERNAL=(.+)$' | Select-Object -First 1
        if ($entry) { $cmakeExecutable = $entry.Matches[0].Groups[1].Value }
    }
    if (-not $cmakeExecutable -or -not (Test-Path -LiteralPath $cmakeExecutable)) {
        throw 'Executa install.bat antes de gerar o pacote.'
    }
}
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$stagingPath = Join-Path ([IO.Path]::GetTempPath()) ('ir-package-' + [guid]::NewGuid().ToString('N'))
$packageName = 'investment-radar-windows-x64'
$applicationPath = Join-Path $stagingPath $packageName
try {
    New-Item -ItemType Directory -Path $stagingPath | Out-Null
    & $cmakeExecutable --install $buildPath --config Release --prefix $applicationPath --component Runtime
    if ($LASTEXITCODE -ne 0) { throw 'Falhou a preparacao do pacote CMake.' }
    foreach ($relative in @('investment-radar.exe', 'assets/index.html', 'assets/app.js',
                            'assets/styles.css', 'vcruntime140.dll', 'msvcp140.dll', 'README.md')) {
        if (-not (Test-Path -LiteralPath (Join-Path $applicationPath $relative) -PathType Leaf)) {
            throw "Falta um ficheiro necessario no pacote: $relative"
        }
    }
    $archivePath = Join-Path $outputPath ($packageName + '.zip')
    $temporaryArchive = Join-Path $stagingPath ($packageName + '.zip')
    Compress-Archive -LiteralPath $applicationPath -DestinationPath $temporaryArchive
    Move-Item -LiteralPath $temporaryArchive -Destination $archivePath -Force
    $hash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath ($archivePath + '.sha256') -Encoding ASCII -Value ($hash + '  ' + [IO.Path]::GetFileName($archivePath))
    Write-Host "Pacote: $archivePath"
} finally {
    if (Test-Path -LiteralPath $stagingPath) { Remove-Item -LiteralPath $stagingPath -Recurse -Force }
}
