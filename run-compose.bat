@echo off
setlocal
if not exist build\windows\Release\investment-radar-core-host.exe (
  echo Native host not found. Building C++ first...
  call install.bat
  if errorlevel 1 exit /b %errorlevel%
)
set "IR_NATIVE_HOST=%CD%\build\windows\Release\investment-radar-core-host.exe"
call android\gradlew.bat -p desktop-compose --no-daemon run
