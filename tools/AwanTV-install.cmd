@echo off
rem AwanTV-install.cmd - Version 1.0.0
rem Double-click to install Awan TV on a TV: runs AwanTV-install.ps1 beside it and keeps the window open.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0AwanTV-install.ps1" %*
echo.
pause
