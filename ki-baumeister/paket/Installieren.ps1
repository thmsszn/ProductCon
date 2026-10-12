#Requires -Version 5.1
<#
    KI-Baumeister – Komplett-Installation für Windows

    Richtet ein:
      1. Fabric-Profil für Minecraft im offiziellen Launcher (ohne extra Java-Installation)
      2. Mods: KI-Baumeister + Fabric API
      3. Ollama (lokale KI) + ein passendes Modell

    Start: Doppelklick auf "Installieren.bat".
#>
param(
    [string]$MinecraftDir = (Join-Path $env:APPDATA '.minecraft'),
    [switch]$SkipOllama,
    [switch]$NonInteractive,
    [string]$Model = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$PackageDir = $PSScriptRoot
$OllamaUrl = 'http://localhost:11434'

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

function Test-OllamaServer {
    try {
        $null = Invoke-RestMethod -Uri "$OllamaUrl/api/version" -TimeoutSec 3
        return $true
    } catch {
        return $false
    }
}

function Find-Ollama {
    $cmd = Get-Command ollama -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'),
        (Join-Path $env:ProgramFiles 'Ollama\ollama.exe')
    )
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    return $null
}

function Stop-WithMessage([string]$Text) {
    Write-Fail $Text
    exit 1
}

# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '  KI-Baumeister – Installation' -ForegroundColor White
Write-Host '  ============================' -ForegroundColor White

$manifestPath = Join-Path $PackageDir 'paket.json'
if (-not (Test-Path $manifestPath)) { Stop-WithMessage "paket.json fehlt. Bitte das ZIP vollständig entpacken und Installieren.bat aus dem entpackten Ordner starten." }
$pkg = Read-JsonFile $manifestPath
Write-Host "  Minecraft $($pkg.minecraft) · Fabric Loader $($pkg.loader) · Fabric API $($pkg.fabricApi) · KI-Baumeister $($pkg.modVersion)"

# --- 1. Minecraft-Launcher prüfen -------------------------------------------
Write-Step 'Minecraft-Ordner prüfen'
$profilesPath = Join-Path $MinecraftDir 'launcher_profiles.json'
if (-not (Test-Path $profilesPath)) {
    Stop-WithMessage "Kein Minecraft-Launcher gefunden ($profilesPath). Bitte den offiziellen Minecraft Launcher einmal starten, anmelden und wieder schließen. Danach Installieren.bat erneut ausführen."
}
Write-Ok $MinecraftDir

if (-not $NonInteractive) {
    $launcher = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(MinecraftLauncher|Minecraft)$' }
    if ($launcher) {
        Write-Note 'Der Minecraft Launcher ist geöffnet. Er kann das neue Profil beim Schließen überschreiben.'
        Read-Host '    Bitte den Launcher jetzt schließen und dann Enter drücken'
    }
}

# --- 2. Fabric-Profil -------------------------------------------------------
Write-Step "Fabric-Profil für Minecraft $($pkg.minecraft) einrichten"
$profileId = $pkg.profileId
$versionDir = Join-Path $MinecraftDir "versions\$profileId"
New-Item -ItemType Directory -Force -Path $versionDir | Out-Null
Copy-Item -Force (Join-Path $PackageDir "fabric\$profileId.json") (Join-Path $versionDir "$profileId.json")
# Wie der offizielle Fabric-Installer: leere Platzhalter-JAR neben der Versions-JSON.
$dummyJar = Join-Path $versionDir "$profileId.jar"
if (-not (Test-Path $dummyJar)) { New-Item -ItemType File -Path $dummyJar | Out-Null }
Write-Ok "Version $profileId"

$backup = "$profilesPath.vor-ki-baumeister.bak"
if (-not (Test-Path $backup)) { Copy-Item $profilesPath $backup }
$profiles = Read-JsonFile $profilesPath
if (-not $profiles.PSObject.Properties['profiles'] -or $null -eq $profiles.profiles) {
    Set-JsonProperty $profiles 'profiles' ([pscustomobject]@{})
}
$now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'")
$iconPath = Join-Path $PackageDir 'fabric\icon.png'
$icon = 'Furnace'
if (Test-Path $iconPath) { $icon = 'data:image/png;base64,' + [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($iconPath)) }
$profileKey = 'ki-baumeister-fabric'
$existing = $profiles.profiles.PSObject.Properties[$profileKey]
if ($existing) {
    $entry = $existing.Value
    Set-JsonProperty $entry 'lastVersionId' $profileId
    Set-JsonProperty $entry 'lastUsed' $now
    Set-JsonProperty $entry 'icon' $icon
} else {
    $entry = [pscustomobject]@{
        name          = "KI-Baumeister (Fabric $($pkg.minecraft))"
        type          = 'custom'
        created       = $now
        lastUsed      = $now
        icon          = $icon
        lastVersionId = $profileId
    }
    $profiles.profiles | Add-Member -NotePropertyName $profileKey -NotePropertyValue $entry
}
Save-Utf8NoBom $profilesPath ($profiles | ConvertTo-Json -Depth 64)
Write-Ok "Launcher-Profil „KI-Baumeister (Fabric $($pkg.minecraft))“ (Sicherung: $(Split-Path -Leaf $backup))"

# --- 3. Mods ----------------------------------------------------------------
Write-Step 'Mods kopieren'
$modsDir = Join-Path $MinecraftDir 'mods'
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
$newJars = Get-ChildItem -Path (Join-Path $PackageDir 'mods') -Filter '*.jar'
$newNames = $newJars | ForEach-Object { $_.Name }
$oldDir = Join-Path $modsDir '_alte-versionen'

# Ältere Versionen von KI-Baumeister und Fabric API beiseitelegen (doppelte Mods verhindern den Start).
$outdated = Get-ChildItem -Path $modsDir -Filter '*.jar' -File | Where-Object {
    ($_.Name -like 'ki-baumeister-*.jar' -or $_.Name -like 'fabric-api-*.jar') -and ($newNames -notcontains $_.Name)
}
if ($outdated) {
    New-Item -ItemType Directory -Force -Path $oldDir | Out-Null
    foreach ($f in $outdated) {
        Move-Item -Force -Path $f.FullName -Destination (Join-Path $oldDir $f.Name)
        Write-Note "Alte Version verschoben: $($f.Name) -> mods\_alte-versionen"
    }
}

$foreign = Get-ChildItem -Path $modsDir -Filter '*.jar' -File | Where-Object {
    $_.Name -notlike 'ki-baumeister-*.jar' -and $_.Name -notlike 'fabric-api-*.jar'
}
if ($foreign) {
    Write-Note "Im mods-Ordner liegen weitere Mods ($(@($foreign).Count)). Mods für andere Minecraft-Versionen verhindern den Start von $($pkg.minecraft)."
    $foreign | ForEach-Object { Write-Host "      - $($_.Name)" }
    if (Ask-YesNo 'Diese Mods in den Ordner mods\_deaktiviert verschieben?' $false) {
        $disabledDir = Join-Path $modsDir '_deaktiviert'
        New-Item -ItemType Directory -Force -Path $disabledDir | Out-Null
        foreach ($f in $foreign) { Move-Item -Force -Path $f.FullName -Destination (Join-Path $disabledDir $f.Name) }
        Write-Ok 'Verschoben – zurückholen: Dateien einfach wieder in den mods-Ordner legen.'
    }
}

foreach ($jar in $newJars) {
    Copy-Item -Force -Path $jar.FullName -Destination (Join-Path $modsDir $jar.Name)
    Write-Ok $jar.Name
}

# --- 4. Ollama + Modell -----------------------------------------------------
if ($SkipOllama) {
    Write-Step 'Ollama übersprungen (-SkipOllama)'
} else {
    Write-Step 'Ollama (lokale KI) einrichten'
    $ollama = Find-Ollama
    if (-not $ollama) {
        Write-Note 'Ollama ist nicht installiert.'
        if (Ask-YesNo 'Ollama jetzt herunterladen und installieren? (ca. 1,6 GB)' $true) {
            $setup = Join-Path $env:TEMP 'OllamaSetup.exe'
            $sources = @('https://ollama.com/download/OllamaSetup.exe',
                         'https://github.com/ollama/ollama/releases/latest/download/OllamaSetup.exe')
            $downloaded = $false
            foreach ($src in $sources) {
                try {
                    Write-Host "    Lade $src ..."
                    if (Get-Command Start-BitsTransfer -ErrorAction SilentlyContinue) {
                        Start-BitsTransfer -Source $src -Destination $setup -DisplayName 'Ollama herunterladen'
                    } else {
                        Invoke-WebRequest -Uri $src -OutFile $setup -UseBasicParsing
                    }
                    $downloaded = $true
                    break
                } catch {
                    Write-Note "Download fehlgeschlagen: $($_.Exception.Message)"
                }
            }
            if ($downloaded) {
                Write-Host '    Das Ollama-Setup startet jetzt – bitte durchklicken und danach hier weitermachen.'
                Start-Process -FilePath $setup -Wait
                Remove-Item -Force $setup -ErrorAction SilentlyContinue
                $ollama = Find-Ollama
            }
        }
    }

    if (-not $ollama) {
        Write-Note 'Ollama nicht gefunden. Später manuell installieren: https://ollama.com/download – danach Installieren.bat erneut starten.'
    } else {
        Write-Ok "Ollama: $ollama"
        if (-not (Test-OllamaServer)) {
            Write-Host '    Starte Ollama ...'
            Start-Process -FilePath $ollama -ArgumentList 'serve' -WindowStyle Hidden
            for ($i = 0; $i -lt 30 -and -not (Test-OllamaServer); $i++) { Start-Sleep -Seconds 1 }
        }
        if (-not (Test-OllamaServer)) {
            Write-Note 'Ollama antwortet nicht. Bitte Ollama einmal über das Startmenü starten und Installieren.bat erneut ausführen.'
        } else {
            $installed = @()
            try { $installed = @((Invoke-RestMethod -Uri "$OllamaUrl/api/tags" -TimeoutSec 10).models | ForEach-Object { $_.name }) } catch {}

            if (-not $Model) {
                $ramGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
                if ($ramGb -ge 24) { $Model = 'qwen2.5:14b' } else { $Model = 'qwen2.5:7b' }
                Write-Host "    Arbeitsspeicher: $ramGb GB -> empfohlenes Modell: $Model"
                Write-Host '      qwen2.5:7b  (ca. 4,7 GB) – für PCs mit 8–16 GB RAM'
                Write-Host '      qwen2.5:14b (ca. 9 GB)   – schönere Bauwerke, ab 16 GB RAM / 8 GB Grafikspeicher'
                if (-not $NonInteractive) {
                    $choice = Read-Host "    Modell wählen (Enter = $Model, oder Namen eingeben, z.B. qwen2.5:14b)"
                    if (-not [string]::IsNullOrWhiteSpace($choice)) { $Model = $choice.Trim() }
                }
            }

            if ($installed -contains $Model) {
                Write-Ok "Modell $Model ist bereits installiert."
            } elseif (Ask-YesNo "Modell $Model jetzt herunterladen?" $true) {
                & $ollama pull $Model
                if ($LASTEXITCODE -ne 0) { Write-Note "Herunterladen von $Model fehlgeschlagen. Später in einer Konsole: ollama pull $Model" }
                else { Write-Ok "Modell $Model installiert." }
            }

            # Modell in der Mod-Konfiguration eintragen
            $configDir = Join-Path $MinecraftDir 'config'
            New-Item -ItemType Directory -Force -Path $configDir | Out-Null
            $configPath = Join-Path $configDir 'ki_baumeister.json'
            if (Test-Path $configPath) {
                $cfg = Read-JsonFile $configPath
                Set-JsonProperty $cfg 'model' $Model
            } else {
                $cfg = [pscustomobject]@{ model = $Model }
            }
            Save-Utf8NoBom $configPath ($cfg | ConvertTo-Json -Depth 8)
            Write-Ok "Mod nutzt Modell $Model (config\ki_baumeister.json)"
        }
    }
}

# --- Fertig -----------------------------------------------------------------
Write-Host ''
Write-Host '  Fertig!' -ForegroundColor Green
Write-Host "  1. Minecraft Launcher öffnen und unten links das Profil „KI-Baumeister (Fabric $($pkg.minecraft))“ wählen."
Write-Host '  2. Spielen – beim ersten Start lädt der Launcher Minecraft und Fabric automatisch.'
Write-Host '  3. Einzelspielerwelt öffnen (vorher Sicherung anlegen) und K drücken.'
Write-Host ''
exit 0
