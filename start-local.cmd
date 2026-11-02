@echo off
title OrangeJD - Local Startup
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start_local.ps1" -StartDocker -OpenBrowser
if errorlevel 1 (
    echo Startup failed. Read the error above.
    pause
    exit /b 1
)
pause
