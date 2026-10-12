# KI-Baumeister – Minecraft 26.3 · Fabric · Ollama

Beschreibe ein Bauwerk in eigenen Worten – eine **lokale KI (Ollama)** entwirft den Bauplan,
du prüfst die Vorschau direkt in der Welt und baust mit einem Klick. Alles läuft auf deinem PC,
es werden keine Daten ins Internet geschickt.

## Funktionen

- **Taste `K`** öffnet das Menü (änderbar unter *Optionen → Steuerung → KI-Baumeister*).
- **Baubereich** über zwei Ecken: Block anschauen und „Ecke 1/2 setzen“ (bis 96 Blöcke weit,
  sonst die eigene Position). Optional eigene Tasten für die Ecken belegen.
- **Vorschau in der Welt**: leuchtender Rahmen um den Bereich, der fertige Plan erscheint als
  grüne Partikel-Wolke – so siehst du vor dem Bauen, was entsteht.
- **Die Vorderseite zeigt automatisch zu dir** (Eingang, Treppen, Türen werden mitgedreht).
- **Bauelemente statt Einzelblöcke**: Die KI plant mit Quadern, Räumen, Wänden, Walmdächern und
  runden Türmen. Das ist viel schneller und ergibt deutlich schönere Gebäude.
- **„Anpassen“**: Plan nachträglich ändern lassen, z.B. *„Dach aus Kupfer, zweites Stockwerk“*.
- **Live-Fortschritt** beim Erzeugen (Elemente, Zeichen, Sekunden) mit Abbrechen-Knopf.
- **Materialliste** mit Item-Symbolen und Mengen.
- **Flüssiges Bauen** über mehrere Ticks (keine Ruckler), Zäune/Scheiben/Treppen verbinden sich
  korrekt, Türen und Betten werden vollständig gesetzt.
- **Rückgängig** für die letzten 5 Bauten. Truhen und andere Blöcke mit Inhalt werden nie
  überschrieben; gefährliche Blöcke (TNT, Lava, Befehlsblöcke …) sind gesperrt.
- **„Bereich leeren“**: entfernt auf Wunsch Erde, Bäume usw. im Baubereich.
- Modellauswahl aus allen lokal installierten Ollama-Modellen, Verbindungsanzeige im Menü.

## Installation

1. **Fabric Loader** für Minecraft **26.3** installieren: <https://fabricmc.net/use/installer/>
2. **Fabric API** (für 26.3) in den `mods`-Ordner legen: <https://modrinth.com/mod/fabric-api>
3. `ki-baumeister-<version>.jar` ebenfalls in den `mods`-Ordner legen.
4. **Ollama** installieren: <https://ollama.com/download> und ein Modell laden, z.B.:
   ```powershell
   ollama pull qwen2.5:14b
   ```
   Kleinere PCs: `qwen2.5:7b` oder `llama3.1:8b`. Gute Ergebnisse liefern auch `qwen3:14b` und `gemma3:12b`.
5. Minecraft mit dem Fabric-Profil starten, Einzelspielerwelt öffnen, `K` drücken.

Benötigt: Minecraft 26.3, Fabric Loader ≥ 0.19.5, Fabric API, Java 25 (bringt der Minecraft-Launcher mit),
Ollama ≥ 0.5.

## Bedienung

1. Auf einen Bodenblock schauen → **Ecke 1 setzen**, zur gegenüberliegenden Ecke (auch höher) schauen → **Ecke 2 setzen**.
2. Bauwerk beschreiben oder eine Vorlage (*Haus, Modern, Turm, Burg …*) anklicken.
3. **✦ KI-Plan erstellen** (oder `Strg+Enter`). Die Vorschau erscheint in der Welt.
4. Gefällt’s? **▶ Bauen**. Sonst Text ändern und **Anpassen** oder neu erstellen.
5. Danebengegangen? **↶ Rückgängig**.

Tipp: Das Menü pausiert das Spiel nicht – du kannst es schließen und beim Bauen zusehen;
der Fortschritt steht in der Aktionsleiste.

## Einstellungen

`config/ki_baumeister.json` (wird beim ersten Start angelegt):

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `ollamaUrl` | `http://localhost:11434` | Adresse des Ollama-Servers (auch im Netzwerk möglich) |
| `model` | `qwen2.5:14b` | Modell (im Menü umschaltbar) |
| `temperature` | `0.5` | Kreativität der KI (0 – 2) |
| `contextSize` | `8192` | Mindest-Kontext; wird bei Bedarf automatisch vergrößert |
| `maxOutputTokens` | `8192` | Maximale Antwortlänge |
| `timeoutMinutes` | `10` | Zeitlimit pro Anfrage |
| `blocksPerTick` | `400` | Baugeschwindigkeit (20 Ticks = 1 Sekunde) |
| `maxHorizontalSize` / `maxHeight` / `maxVolume` | `64` / `64` / `160000` | Grenzen für den Baubereich |
| `clearArea` | `false` | Bereich vor dem Bauen leeren |
| `preview` | `true` | Vorschau in der Welt |

## Selbst bauen

```bash
cd ki-baumeister
./gradlew build        # Windows: gradlew.bat build
```

Die JAR liegt danach in `build/libs/`. Benötigt JDK 25. Auf GitHub baut der Workflow
*„KI-Baumeister Mod (JAR) bauen“* die JAR bei jedem Push automatisch.

## Grenzen

- Funktioniert in **Einzelspielerwelten** (und als LAN-Host), nicht auf fremden Servern.
- Vor großen Bauten eine **Sicherung der Welt** anlegen. Rückgängig stellt Blöcke wieder her,
  aber keine Inhalte von Behältern, die erst durch den Bau entstanden sind.
- Die Qualität hängt vom Modell ab: größere Modelle planen deutlich schöner.
