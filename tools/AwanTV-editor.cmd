@echo off
rem AwanTV-editor.cmd - Version 1.0.0
rem Opens the Awan TV settings editor (AwanTV-editor.ps1) without a console window.
start "" powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0AwanTV-editor.ps1"
