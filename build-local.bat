@echo off
setlocal
call "%~dp0gradlew.bat" --no-daemon buildFabricPort
exit /b %ERRORLEVEL%
