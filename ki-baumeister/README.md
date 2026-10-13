# KI-Baumeister – Minecraft 26.3 · Fabric · Ollama

Beschreibe ein Bauwerk in eigenen Worten – eine **lokale KI (Ollama)** entwirft den Bauplan,
du prüfst die Vorschau direkt in der Welt und baust mit einem Klick. Alles läuft auf deinem PC,
es werden keine Daten ins Internet geschickt.

## Funktionen

- **Echte Gebäude statt Blockhaufen**: Die KI ist der *Architekt* – sie wählt Gebäudetyp, Stil,
  Stockwerke, Dachform, Materialien, Räume und Extras. Eine eingebaute Architektur-Engine setzt den
  Entwurf dann wie ein Schematic-Bauer um: Steinsockel, Fachwerk mit Eckpfosten und Balken,
  Fenster mit Fensterbänken und Läden, Sattel-/Walm-/Flach-/Kegeldach mit Überstand und Giebel,
  Kamin mit Rauch, Treppenhaus, eingerichtete Räume (Küche, Schlafzimmer, Bibliothek …),
  Veranda, Balkon, Laternen, Zaun, Garten, Bäume und Pool.
- **Gebäudetypen**: Haus, Hütte, Herrenhaus, Taverne, Scheune, moderne Villa (versetzte Kuben, Glasfronten,
  Dachterrasse, Pool), Turm, Leuchtturm, Burg (Ringmauer, Ecktürme, Torhaus, Bergfried), Kirche (Glockenturm,
  Chor, Buntglas, Kirchhof), antiker Tempel bzw. japanischer Schrein mit Torii – in 10 Stilen (mittelalterlich,
  rustikal, modern, Stein, Wüste, japanisch, nordisch, viktorianisch, antik, Fantasy). Jeder Typ passt sich der
  Größe des Bauplatzes an, von der Kapelle bis zur Kathedrale.
- **Baumeister-Axt** (Kreativ-Inventar *Werkzeuge* oder Knopf „⚒ Axt holen“):
  **Linksklick** auf einen Block = Ecke 1, **Rechtsklick** auf einen Block = Ecke 2,
  **Rechtsklick in die Luft** = Menü öffnen. Die Axt baut nichts ab.
- **Taste `K`** öffnet das Menü (änderbar unter *Optionen → Steuerung → KI-Baumeister*). Für die Ecken
  gibt es dort auch eigene, frei belegbare Tasten.
- **Hologramm-Vorschau**: Rahmen um den Baubereich und das geplante Gebäude als farbiges,
  halbtransparentes Hologramm in der Welt – so siehst du vor dem Bauen genau, was entsteht.
- **Die Vorderseite zeigt automatisch zu dir** (Eingang, Treppen, Türen werden mitgedreht).
- **„Variante“**: gleicher Entwurf, andere Details – sofort, ohne die KI erneut zu fragen.
- **„Anpassen“**: Entwurf ändern lassen, z.B. *„drei Stockwerke, Dach aus Kupfer, mit Balkon“*.
- **Schematic-Export**: „Schematic“ speichert das Gebäude als `.schem` (Sponge v2) im Ordner
  `schematics` des Spielordners – nutzbar mit WorldEdit, FastAsyncWorldEdit und Litematica.
- **Funktioniert auch ohne KI**: Ist Ollama nicht erreichbar, erkennt die Engine Stichworte wie
  *Burg*, *Turm*, *Kirche* oder *modern* und baut trotzdem ein vollständiges Gebäude.
- **Materialliste** mit Item-Symbolen und Mengen, **Live-Fortschritt** mit Abbrechen-Knopf.
- **Flüssiges Bauen** über mehrere Ticks (keine Ruckler), Zäune/Scheiben/Treppen verbinden sich
  korrekt, Türen und Betten werden vollständig gesetzt. Gelände im Baubereich wird automatisch freigeräumt.
- **Rückgängig** für die letzten 5 Bauten. Truhen und andere Blöcke mit Inhalt werden nie
  überschrieben; gefährliche Blöcke (TNT, Lava, Befehlsblöcke …) sind gesperrt.
- Modellauswahl aus allen lokal installierten Ollama-Modellen, Verbindungsanzeige im Menü.

## Installation

### Am einfachsten: Komplettpaket (Windows)

`KI-Baumeister-Komplettpaket.zip` aus dem GitHub-Release herunterladen, entpacken und **`Installieren.bat`**
doppelklicken. Das Skript

- legt im offiziellen Minecraft Launcher (klassisch und Microsoft-Store-Version) das Profil
  „KI-Baumeister (Fabric 26.3)“ an – mit eigenem Spielordner `%APPDATA%\.minecraft\ki-baumeister`,
  damit andere Profile und Mods unberührt bleiben (keine extra Java-Installation nötig),
- übernimmt Sprache/Tasten/Grafik-Einstellungen und kopiert auf Wunsch vorhandene Welten,
- legt KI-Baumeister und Fabric API in den mods-Ordner des Profils (alte Versionen werden beiseitegelegt),
- prüft Ollama: Ist es schon installiert, wird ein vorhandenes passendes Modell genutzt oder eines vorgeschlagen
  (`qwen2.5:7b` / `qwen2.5:14b` je nach Arbeitsspeicher); fehlt Ollama, wird es auf Wunsch installiert.

Danach im Launcher das Profil wählen und „Spielen“.

### Manuell

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

1. Bereich festlegen: mit der **Baumeister-Axt** einen Bodenblock links anklicken (Ecke 1) und die
   gegenüberliegende Ecke – gern auch höher – rechts anklicken (Ecke 2). Alternativ im Menü
   „Ecke 1/2 setzen“ (Block im Fadenkreuz). Mindestgröße 5 × 5 × 5, für Häuser ideal ca. 20 × 20 × 20.
2. Bauwerk beschreiben oder eine Vorlage (*Haus, Villa, Turm, Burg, Kirche, Tempel, Japan*) anklicken.
3. **✦ KI-Entwurf** (oder `Strg+Enter`). Das Hologramm erscheint in der Welt.
4. Gefällt's? **▶ Bauen**. Sonst **Variante**, Text ändern und **Anpassen** oder neu entwerfen.
5. Danebengegangen? **↶ Rückgängig**.

Tipp: Das Menü pausiert das Spiel nicht – du kannst es schließen und beim Bauen zusehen;
der Fortschritt steht oben links.

## Einstellungen

`config/ki_baumeister.json` (wird beim ersten Start angelegt):

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `ollamaUrl` | `http://localhost:11434` | Adresse des Ollama-Servers (auch im Netzwerk möglich) |
| `model` | `qwen2.5:14b` | Modell (im Menü umschaltbar) |
| `temperature` | `0.5` | Kreativität der KI (0 – 2) |
| `contextSize` | `4096` | Mindest-Kontext; wird bei Bedarf automatisch vergrößert |
| `maxOutputTokens` | `2048` | Maximale Antwortlänge |
| `timeoutMinutes` | `10` | Zeitlimit pro Anfrage |
| `blocksPerTick` | `400` | Baugeschwindigkeit (20 Ticks = 1 Sekunde) |
| `maxHorizontalSize` / `maxHeight` / `maxVolume` | `64` / `64` / `160000` | Grenzen für den Baubereich |
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
- Die KI entscheidet über Typ, Stil und Ausstattung; größere Modelle treffen die Beschreibung genauer.
  Die Bauqualität selbst kommt von der Engine und ist bei jedem Modell gleich.
