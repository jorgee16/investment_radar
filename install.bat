@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-windows.ps1"
set "ir_build_result=%errorlevel%"
if not "%ir_build_result%"=="0" (
    echo.
    echo A instalacao ou compilacao falhou. Copia o erro acima para o podermos analisar.
    if not defined IR_NONINTERACTIVE if not defined IR_BUILD_NONINTERACTIVE pause
)
exit /b %ir_build_result%
