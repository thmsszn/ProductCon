@echo off
REM ProductCon - Bilder auf diese Datei ziehen oder doppelklicken (verarbeitet den Ordner "input").
cd /d "%~dp0"

set PY=python
where python >nul 2>nul || set PY=py

%PY% -c "import PIL, numpy" >nul 2>nul || (
    echo Installiere benoetigte Pakete ...
    %PY% -m pip install -r requirements.txt
)

%PY% -m productcon %*
echo.
pause
