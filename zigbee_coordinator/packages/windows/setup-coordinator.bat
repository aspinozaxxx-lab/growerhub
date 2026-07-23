@echo off
chcp 65001 >nul
setlocal EnableExtensions
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-coordinator.ps1" %*
set "EXITCODE=%ERRORLEVEL%"
if not "%~1"=="--no-pause" pause
exit /b %EXITCODE%
