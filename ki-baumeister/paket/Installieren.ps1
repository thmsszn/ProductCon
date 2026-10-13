#Requires -Version 5.1
<#
    KI-Baumeister – Komplett-Installation für Windows

    Richtet ein:
      1. Ein eigenes Launcher-Profil "KI-Baumeister (Fabric <Version>)" mit eigenem Spielordner
         (%APPDATA%\.minecraft\ki-baumeister) – andere Profile und Mods bleiben unberührt.
      2. Mods: KI-Baumeister + Fabric API
      3. Ollama (lokale KI) + ein passendes Modell

    Start: Doppelklick auf "Installieren.bat".
#>
param(
    [string]$MinecraftDir = (Join-Path $env:APPDATA '.minecraft'),
    [string]$GameDir = '',
    [switch]$SkipOllama,
    [switch]$NonInteractive,
    [string]$Model = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$PackageDir = $PSScriptRoot
# Ollama lauscht standardmäßig nur auf IPv4; "localhost" würde unter PS 5.1 erst ::1 probieren.
$OllamaUrl = 'http://127.0.0.1:11434'
$MinOllamaVersion = [version]'0.5.0'
if (-not $GameDir) { $GameDir = Join-Path $MinecraftDir 'ki-baumeister' }

function Write-Step([string]$Text) { Write-Host ''; Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text) { Write-Host "    OK: $Text" -ForegroundColor Green }
function Write-Note([string]$Text) { Write-Host "    Hinweis: $Text" -ForegroundColor Yellow }
function Write-Fail([string]$Text) { Write-Host "    FEHLER: $Text" -ForegroundColor Red }

function Ask-YesNo([string]$Question, [bool]$Default) {
    if ($NonInteractive) { return $Default }
    $suffix = if ($Default) { '[J/n]' } else { '[j/N]' }
    $answer = Read-Host "    $Question $suffix"
    if ([string]::IsNullOrWhiteSpace($answer)) { return $Default }
    $a = $answer.Trim().ToLowerInvariant()
    return ($a.StartsWith('j') -or $a.StartsWith('y'))
}

function Save-Utf8NoBom([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function Read-JsonFile([string]$Path) {
    $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    return ($text | ConvertFrom-Json)
}

function Set-JsonProperty($Object, [string]$Name, $Value) {
    if ($Object.PSObject.Properties[$Name]) { $Object.$Name = $Value }
    else { $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value }
}

function Stop-WithMessage([string]$Text) {
    Write-Fail $Text
    exit 1
}

function Get-OllamaVersion {
    try { return [string](Invoke-RestMethod -Uri "$OllamaUrl/api/version" -TimeoutSec 5).version } catch { return $null }
}

function Find-Ollama {
    $cmd = Get-Command ollama -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'),
        (Join-Path $env:ProgramFiles 'Ollama\ollama.exe')
    )
    foreach ($c in $candidates) { if ($c -and (Test-Path -LiteralPath $c)) { return $c } }
    return $null
}

function Start-Ollama([string]$Exe) {
    # Bevorzugt die Ollama-App (Symbol im Infobereich), sonst den Server direkt.
    # Eigenes Arbeitsverzeichnis, damit der entpackte Paketordner nicht gesperrt bleibt.
    $dir = Split-Path -Parent $Exe
    $app = Join-Path $dir 'ollama app.exe'
    if (Test-Path -LiteralPath $app) { Start-Process -FilePath $app -WorkingDirectory $dir }
    else { Start-Process -FilePath $Exe -ArgumentList 'serve' -WorkingDirectory $dir -WindowStyle Hidden }
}

function Get-OllamaModels {
    # Nur lokale Text-Modelle (keine Cloud- oder Embedding-Modelle).
    try {
        $tags = Invoke-RestMethod -Uri "$OllamaUrl/api/tags" -TimeoutSec 10
    } catch {
        Write-Note "Modellliste nicht abrufbar: $($_.Exception.Message)"
        return @()
    }
    $list = @()
    foreach ($m in @($tags.models)) {
        if ($null -eq $m) { continue }
        $name = [string]$m.name
        if ($m.PSObject.Properties['remote_host'] -or $m.PSObject.Properties['remote_model']) { continue }
        if ($name -match 'embed' -or $name -match '(-|:)cloud$') { continue }
        $list += [pscustomobject]@{ Name = $name; Size = [double]$m.size }
    }
    return $list
}

function Resolve-ModelName($Models, [string]$Name) {
    # Liefert den exakten Namen aus /api/tags ("qwen2.5" -> "qwen2.5:latest", Groß-/Kleinschreibung egal).
    if ([string]::IsNullOrWhiteSpace($Name)) { return $null }
    $wanted = if ($Name.Contains(':')) { $Name } else { "${Name}:latest" }
    $hit = @($Models | Where-Object { $_.Name -eq $wanted -or $_.Name -eq $Name }) | Select-Object -First 1
    if ($hit) { return $hit.Name }
    return $null
}

function Save-Download([string]$Url, [string]$Target) {
    # Erst BITS (mit Fortschritt), bei Problemen Invoke-WebRequest.
    if (Get-Command Start-BitsTransfer -ErrorAction SilentlyContinue) {
        try {
            Start-BitsTransfer -Source $Url -Destination $Target -DisplayName 'KI-Baumeister: Download'
            return
        } catch {
            Write-Note "BITS-Download fehlgeschlagen ($($_.Exception.Message)), versuche es anders ..."
        }
    }
    Invoke-WebRequest -Uri $Url -OutFile $Target -UseBasicParsing
}

function Move-Outdated([string]$Dir, [string[]]$Patterns, [string[]]$Keep, [string]$Label) {
    # Verschiebt passende JARs (außer $Keep) nach <Dir>\_alte-versionen.
    if (-not (Test-Path -LiteralPath $Dir)) { return }
    $old = @(Get-ChildItem -LiteralPath $Dir -Filter '*.jar' -File | Where-Object {
        $n = $_.Name
        (@($Patterns | Where-Object { $n -like $_ }).Count -gt 0) -and ($Keep -notcontains $n)
    })
    if (-not $old) { return }
    $target = Join-Path $Dir '_alte-versionen'
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    foreach ($f in $old) {
        Move-Item -Force -LiteralPath $f.FullName -Destination (Join-Path $target $f.Name)
        Write-Note "$Label verschoben: $($f.Name) -> $(Split-Path -Leaf $Dir)\_alte-versionen"
    }
}

# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '  KI-Baumeister – Installation' -ForegroundColor White
Write-Host '  ============================' -ForegroundColor White

$manifestPath = Join-Path $PackageDir 'paket.json'
if (-not (Test-Path -LiteralPath $manifestPath)) { Stop-WithMessage 'paket.json fehlt. Bitte das ZIP vollständig entpacken und Installieren.bat aus dem entpackten Ordner starten.' }
$pkg = Read-JsonFile $manifestPath
Write-Host "  Minecraft $($pkg.minecraft) · Fabric Loader $($pkg.loader) · Fabric API $($pkg.fabricApi) · KI-Baumeister $($pkg.modVersion)"
$profileName = "KI-Baumeister (Fabric $($pkg.minecraft))"

# --- 1. Minecraft-Launcher prüfen -------------------------------------------
Write-Step 'Minecraft-Launcher prüfen'
# Der klassische Launcher nutzt launcher_profiles.json, der Launcher aus dem Microsoft Store / der Xbox-App
# launcher_profiles_microsoft_store.json. Wie der offizielle Fabric-Installer: in alle vorhandenen eintragen.
$profileFiles = @(@('launcher_profiles.json', 'launcher_profiles_microsoft_store.json') |
    ForEach-Object { Join-Path $MinecraftDir $_ } |
    Where-Object { Test-Path -LiteralPath $_ })
if ($profileFiles.Count -eq 0) {
    Stop-WithMessage "Kein Minecraft-Launcher gefunden ($MinecraftDir). Bitte den offiziellen Minecraft Launcher einmal starten, anmelden und wieder schließen. Danach Installieren.bat erneut ausführen."
}
Write-Ok $MinecraftDir

if (-not $NonInteractive) {
    while (Get-Process -Name 'MinecraftLauncher', 'Minecraft' -ErrorAction SilentlyContinue) {
        Write-Note 'Der Minecraft Launcher ist geöffnet. Er würde das neue Profil beim Schließen wieder überschreiben.'
        $a = Read-Host '    Bitte den Launcher schließen und Enter drücken (oder "weiter" eingeben, um trotzdem fortzufahren)'
        if ($a -and $a.Trim().ToLowerInvariant().StartsWith('weiter')) { break }
    }
}

# --- 2. Fabric-Version + Launcher-Profil ------------------------------------
Write-Step "Fabric-Profil für Minecraft $($pkg.minecraft) einrichten"
$profileId = [string]$pkg.profileId
$versionDir = Join-Path $MinecraftDir "versions\$profileId"
New-Item -ItemType Directory -Force -Path $versionDir | Out-Null
Copy-Item -Force -LiteralPath (Join-Path $PackageDir "fabric\$profileId.json") -Destination (Join-Path $versionDir "$profileId.json")
# Wie der offizielle Fabric-Installer: leere Platzhalter-JAR neben der Versions-JSON.
$dummyJar = Join-Path $versionDir "$profileId.jar"
if (-not (Test-Path -LiteralPath $dummyJar)) { New-Item -ItemType File -Path $dummyJar | Out-Null }
Write-Ok "Version $profileId"

New-Item -ItemType Directory -Force -Path $GameDir | Out-Null
$now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'")
$iconPath = Join-Path $PackageDir 'fabric\icon.png'
$icon = 'Furnace'
if (Test-Path -LiteralPath $iconPath) { $icon = 'data:image/png;base64,' + [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($iconPath)) }
$profileKey = 'ki-baumeister-fabric'

foreach ($profilesPath in $profileFiles) {
    $backup = "$profilesPath.vor-ki-baumeister.bak"
    if (-not (Test-Path -LiteralPath $backup)) { Copy-Item -LiteralPath $profilesPath -Destination $backup }
    try {
        $profiles = Read-JsonFile $profilesPath
    } catch {
        Stop-WithMessage "$(Split-Path -Leaf $profilesPath) ist beschädigt und wurde nicht verändert. Launcher einmal starten/schließen und erneut versuchen."
    }
    if ($null -eq $profiles) { $profiles = [pscustomobject]@{} }
    if (-not $profiles.PSObject.Properties['profiles'] -or $null -eq $profiles.profiles) {
        Set-JsonProperty $profiles 'profiles' ([pscustomobject]@{})
    }
    $existing = $profiles.profiles.PSObject.Properties[$profileKey]
    if ($existing) {
        $entry = $existing.Value
    } else {
        $entry = [pscustomobject]@{ created = $now }
        $profiles.profiles | Add-Member -NotePropertyName $profileKey -NotePropertyValue $entry
    }
    Set-JsonProperty $entry 'name' $profileName
    Set-JsonProperty $entry 'type' 'custom'
    Set-JsonProperty $entry 'lastUsed' $now
    Set-JsonProperty $entry 'icon' $icon
    Set-JsonProperty $entry 'lastVersionId' $profileId
    Set-JsonProperty $entry 'gameDir' $GameDir
    # Erst in eine Temp-Datei schreiben, dann ersetzen: bricht nichts halb geschrieben ab.
    $tmp = "$profilesPath.ki-baumeister.tmp"
    Save-Utf8NoBom $tmp ($profiles | ConvertTo-Json -Depth 64)
    Move-Item -Force -LiteralPath $tmp -Destination $profilesPath
    Write-Ok "Profil '$profileName' in $(Split-Path -Leaf $profilesPath) (Sicherung: $(Split-Path -Leaf $backup))"
}
Write-Ok "Eigener Spielordner: $GameDir"

# Einstellungen (Sprache, Tasten, Grafik) aus dem normalen Minecraft übernehmen.
$sharedOptions = Join-Path $MinecraftDir 'options.txt'
$gameOptions = Join-Path $GameDir 'options.txt'
if ((Test-Path -LiteralPath $sharedOptions) -and -not (Test-Path -LiteralPath $gameOptions)) {
    Copy-Item -LiteralPath $sharedOptions -Destination $gameOptions
    Write-Ok 'Einstellungen (Sprache, Tasten, Grafik) übernommen'
}

# --- 3. Mods ----------------------------------------------------------------
Write-Step 'Mods einrichten'
$modsDir = Join-Path $GameDir 'mods'
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
$newJars = @(Get-ChildItem -LiteralPath (Join-Path $PackageDir 'mods') -Filter '*.jar' -File)
$newNames = @($newJars | ForEach-Object { $_.Name })

try {
    # Ältere Versionen im KI-Baumeister-Ordner beiseitelegen (doppelte Mods verhindern den Start).
    Move-Outdated $modsDir @('ki-baumeister-*.jar', 'fabric-api-*.jar') $newNames 'Alte Version'
    # Eine früher von Hand in .minecraft\mods gelegte KI-Baumeister-Version stört dort andere Profile.
    Move-Outdated (Join-Path $MinecraftDir 'mods') @('ki-baumeister-*.jar') @() 'Alte KI-Baumeister-Version aus .minecraft\mods'

    $foreign = @(Get-ChildItem -LiteralPath $modsDir -Filter '*.jar' -File | Where-Object { $newNames -notcontains $_.Name })
    if ($foreign.Count -gt 0) {
        Write-Note "Im KI-Baumeister-Ordner liegen weitere Mods ($($foreign.Count)). Mods für andere Minecraft-Versionen verhindern den Start."
        $foreign | ForEach-Object { Write-Host "      - $($_.Name)" }
        if (Ask-YesNo 'Diese Mods in den Unterordner mods\_deaktiviert verschieben?' $false) {
            $disabledDir = Join-Path $modsDir '_deaktiviert'
            New-Item -ItemType Directory -Force -Path $disabledDir | Out-Null
            foreach ($f in $foreign) { Move-Item -Force -LiteralPath $f.FullName -Destination (Join-Path $disabledDir $f.Name) }
            Write-Ok 'Verschoben – zurückholen: Dateien einfach wieder in den mods-Ordner legen.'
        }
    }

    foreach ($jar in $newJars) {
        Copy-Item -Force -LiteralPath $jar.FullName -Destination (Join-Path $modsDir $jar.Name)
        Write-Ok $jar.Name
    }
} catch {
    Stop-WithMessage "Mods konnten nicht eingerichtet werden: $($_.Exception.Message) – läuft Minecraft noch? Bitte schließen und erneut starten."
}

# Vorhandene Welten optional kopieren (Originale bleiben unverändert).
$sharedSaves = Join-Path $MinecraftDir 'saves'
$gameSaves = Join-Path $GameDir 'saves'
$worlds = @()
if (Test-Path -LiteralPath $sharedSaves) { $worlds = @(Get-ChildItem -LiteralPath $sharedSaves -Directory) }
$ownWorlds = @()
if (Test-Path -LiteralPath $gameSaves) { $ownWorlds = @(Get-ChildItem -LiteralPath $gameSaves -Directory) }
if ($worlds.Count -gt 0 -and $ownWorlds.Count -eq 0) {
    Write-Note "Das KI-Baumeister-Profil hat eigene Welten. Im normalen Minecraft hast du $($worlds.Count) Welt(en)."
    if (Ask-YesNo 'Diese Welten in das KI-Baumeister-Profil KOPIEREN? (Originale bleiben unverändert)' $false) {
        New-Item -ItemType Directory -Force -Path $gameSaves | Out-Null
        foreach ($w in $worlds) {
            Copy-Item -Recurse -Force -LiteralPath $w.FullName -Destination (Join-Path $gameSaves $w.Name)
            Write-Ok "Welt kopiert: $($w.Name)"
        }
    }
}

# --- 4. Ollama + Modell -----------------------------------------------------
$configPath = Join-Path $GameDir 'config\ki_baumeister.json'
if ($SkipOllama) {
    Write-Step 'Ollama übersprungen (-SkipOllama)'
} else {
    Write-Step 'Ollama (lokale KI) einrichten'
    $ollama = Find-Ollama
    if (-not $ollama) {
        Write-Note 'Ollama ist nicht installiert.'
        if (Ask-YesNo 'Ollama jetzt herunterladen und installieren? (ca. 1,6 GB)' $true) {
            $setup = Join-Path $env:TEMP 'OllamaSetup.exe'
            $downloaded = $false
            foreach ($src in @('https://ollama.com/download/OllamaSetup.exe',
                               'https://github.com/ollama/ollama/releases/latest/download/OllamaSetup.exe')) {
                try {
                    Write-Host "    Lade $src ..."
                    Save-Download $src $setup
                    $downloaded = $true
                    break
                } catch {
                    Write-Note "Download fehlgeschlagen: $($_.Exception.Message)"
                }
            }
            if ($downloaded) {
                Write-Host '    Das Ollama-Setup startet jetzt – bitte durchklicken und danach hier weitermachen.'
                # Nur auf das Setup warten, nicht auf die danach gestartete Ollama-App.
                $proc = Start-Process -FilePath $setup -PassThru
                $proc.WaitForExit()
                Remove-Item -Force -LiteralPath $setup -ErrorAction SilentlyContinue
                $ollama = Find-Ollama
            }
        }
    }

    if (-not $ollama) {
        Write-Note 'Ollama nicht gefunden. Später manuell installieren: https://ollama.com/download – danach Installieren.bat erneut starten.'
    } else {
        Write-Ok "Ollama: $ollama"
        if (-not (Get-OllamaVersion)) {
            Write-Host '    Starte Ollama ...'
            Start-Ollama $ollama
            for ($i = 0; $i -lt 30 -and -not (Get-OllamaVersion); $i++) { Start-Sleep -Seconds 1 }
        }
        $versionText = Get-OllamaVersion
        if (-not $versionText) {
            Write-Note 'Ollama antwortet nicht. Bitte Ollama einmal über das Startmenü starten und Installieren.bat erneut ausführen.'
        } else {
            $parsed = $null
            if ([version]::TryParse(($versionText -replace '[^0-9.].*$', ''), [ref]$parsed) -and $parsed -lt $MinOllamaVersion) {
                Write-Note "Ollama $versionText ist zu alt (mindestens $MinOllamaVersion nötig). Bitte aktualisieren: https://ollama.com/download"
            } else {
                Write-Ok "Ollama-Version $versionText läuft"
            }

            $models = @(Get-OllamaModels)
            $ramGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
            if ($models.Count -gt 0) {
                Write-Host '    Bereits installierte Modelle:'
                foreach ($m in $models) { Write-Host ('      - {0} ({1:N1} GB)' -f $m.Name, ($m.Size / 1GB)) }
            }

            if (-not $Model -and (Test-Path -LiteralPath $configPath)) {
                # Bereits in der Mod eingestelltes Modell beibehalten, wenn es installiert ist.
                try {
                    $configured = Resolve-ModelName $models ([string](Read-JsonFile $configPath).model)
                    if ($configured) { $Model = $configured }
                } catch { Write-Note 'config\ki_baumeister.json ist unlesbar.' }
            }

            if (-not $Model) {
                # Vorhandenes, passendes Modell bevorzugen (gleiche Reihenfolge wie in der Mod), sonst eines empfehlen.
                $maxBytes = [math]::Max(4GB, $ramGb * 0.6GB)
                foreach ($prefix in @('qwen3', 'qwen2.5', 'gemma3', 'llama3.1', 'llama3', 'mistral')) {
                    $hit = $models | Where-Object { $_.Name -like "$prefix*" -and $_.Size -ge 3GB -and $_.Size -le $maxBytes } |
                        Sort-Object Size -Descending | Select-Object -First 1
                    if ($hit) { $Model = $hit.Name; break }
                }
                $recommended = if ($ramGb -ge 24) { 'qwen2.5:14b' } else { 'qwen2.5:7b' }
                if (-not $Model) { $Model = $recommended }
                Write-Host "    Arbeitsspeicher: $ramGb GB -> Vorschlag: $Model"
                Write-Host '      Download-Empfehlungen: qwen2.5:7b (ca. 4,7 GB, ab 8 GB RAM) oder qwen2.5:14b (ca. 9 GB, schönere Bauwerke, ab 24 GB RAM)'
                if (-not $NonInteractive) {
                    $choice = Read-Host "    Modell wählen (Enter = $Model, oder Modellnamen eingeben)"
                    $c = if ($choice) { ($choice.Trim() -replace '^ollama\s+(pull|run)\s+', '').Trim() } else { '' }
                    if ($c -and $c -notmatch '^(j|ja|y|yes|ok|n|nein|\d+)$') {
                        if ($c -match '^[A-Za-z0-9][A-Za-z0-9._/\-]*(:[A-Za-z0-9._\-]+)?$') { $Model = $c }
                        else { Write-Note "'$c' ist kein gültiger Modellname – verwende $Model." }
                    }
                }
            }

            $resolved = Resolve-ModelName $models $Model
            if ($resolved) {
                $Model = $resolved
                Write-Ok "Modell $Model ist installiert."
            } elseif (Ask-YesNo "Modell $Model ist noch nicht installiert. Jetzt herunterladen?" $true) {
                & $ollama pull $Model
                $pullExit = $LASTEXITCODE
                $models = @(Get-OllamaModels)
                $resolved = Resolve-ModelName $models $Model
                if ($pullExit -eq 0 -and $resolved) { $Model = $resolved; Write-Ok "Modell $Model installiert." }
                else { Write-Note "Herunterladen von $Model fehlgeschlagen. Später in einer Konsole: ollama pull $Model" }
            }

            if ($resolved) {
                # Exakten Modellnamen in der Mod-Konfiguration eintragen (andere Einstellungen bleiben erhalten).
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $configPath) | Out-Null
                $cfg = $null
                if (Test-Path -LiteralPath $configPath) {
                    try { $cfg = Read-JsonFile $configPath } catch { Write-Note 'config\ki_baumeister.json war unlesbar und wird neu angelegt.' }
                }
                if ($null -eq $cfg) { $cfg = [pscustomobject]@{} }
                Set-JsonProperty $cfg 'model' $Model
                Save-Utf8NoBom $configPath ($cfg | ConvertTo-Json -Depth 8)
                Write-Ok "Mod nutzt Modell $Model"
            } else {
                Write-Note 'Kein Modell eingetragen – die Mod wählt beim Start automatisch ein installiertes Modell.'
            }
        }
    }
}

# --- Fertig -----------------------------------------------------------------
Write-Host ''
Write-Host '  Fertig!' -ForegroundColor Green
Write-Host "  1. Minecraft Launcher öffnen und unten links das Profil '$profileName' wählen."
Write-Host '  2. Spielen – beim ersten Start lädt der Launcher Minecraft und Fabric automatisch.'
Write-Host '  3. Einzelspielerwelt öffnen (vorher Sicherung anlegen) und K drücken.'
Write-Host "  Spielordner des Profils: $GameDir"
Write-Host ''
exit 0
