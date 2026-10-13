@echo off
rem KI-Baumeister - Komplett-Installation (Fabric-Profil, Mods, Ollama + Modell)
if not exist "%~dp0Installieren.ps1" goto nichtentpackt
if not exist "%~dp0paket.json" goto nichtentpackt
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Installieren.ps1" %*
echo.
pause
exit /b

:nichtentpackt
echo.
echo   Bitte das ZIP zuerst entpacken:
echo   Rechtsklick auf die ZIP-Datei - "Alle extrahieren..." - dann im entpackten
echo   Ordner "Installieren.bat" doppelklicken.
echo.
pause
