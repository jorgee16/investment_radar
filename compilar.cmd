@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-windows.ps1"
set "ir_build_result=%errorlevel%"
if not "%ir_build_result%"=="0" (
    echo.
    echo A compilacao falhou. Copia o erro acima para o podermos analisar.
    rem Keep failures visible when launched by double-click in Explorer.
    if not defined IR_BUILD_NONINTERACTIVE pause
)
exit /b %ir_build_result%
