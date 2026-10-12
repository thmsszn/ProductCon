@echo off
rem KI-Baumeister - Komplett-Installation (Fabric-Profil, Mods, Ollama + Modell)
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Installieren.ps1" %*
echo.
pause
