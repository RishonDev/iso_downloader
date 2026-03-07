@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0make-native-image.ps1"
exit /b %errorlevel%
