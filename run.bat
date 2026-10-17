@echo off
setlocal
if not exist "%~dp0build\windows\Release\investment-radar.exe" goto missing
"%~dp0build\windows\Release\investment-radar.exe" %*
set "ir_run_result=%errorlevel%"
if not "%ir_run_result%"=="0" if not defined IR_NONINTERACTIVE pause
exit /b %ir_run_result%
:missing
echo Primeiro executa install.bat para preparar e compilar a aplicacao.
if not defined IR_NONINTERACTIVE pause
exit /b 1
