@echo off
setlocal
if exist "%~dp0build\windows\Release\investment-radar.exe" goto launch
echo Primeira utilizacao: a compilar a aplicacao...
call "%~dp0compilar.cmd"
if errorlevel 1 exit /b 1
:launch
start "" "%~dp0build\windows\Release\investment-radar.exe"
exit /b %errorlevel%
