@echo off
rem AwanTV-restore.cmd - Version 1.0.0
rem Double-click to undo AwanTV-install: asks whether to remove the app too, then runs AwanTV-restore.ps1.
choice /c YN /m "Also remove the Awan TV app from the TV"
if errorlevel 2 (set U=) else (set U=-Uninstall)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0AwanTV-restore.ps1" %U% %*
echo.
pause
