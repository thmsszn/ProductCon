**Neu in 1.2.0 – freie Grundrisse und viel mehr Details:**
- **Freier Bauplan:** Die KI beschreibt die Form (runde/eckige Baukörper, gestapelt) – Burgen mit Ecktürmen, Langhäuser, Rundbauten mit Flügeln entstehen wie beschrieben. Ohne Angabe wechselt der Grundriss (L, T, U, Kreuz, Langhaus, gestuft …), „Variante“ ändert die Grundstruktur.
- **Details:** Veranda mit Pflanzkästen und Laternen, mehrfarbige Dächer, Gauben, Kamine, echte Treppen, Außentreppe/Balkon, auskragende Obergeschosse, Zinnen und Strebepfeiler bei Steinbauten.
- **Moderne Villen** mit mehreren versetzten Ebenen, Terrassen, Glasgeländern, Außentreppe und Pool.
- **Gärten** mit verschiedenen Bäumen, Beeten, Teich, Bänken, Wegen.
- **Neue Stile:** Wikinger, Gotik, Steampunk, Orientalisch.

**Neu in 1.1.0 – echte Gebäude statt Blockhaufen:**
- Die KI ist jetzt der *Architekt*: Sie wählt Gebäudetyp, Stil, Stockwerke, Dach, Materialien und Räume, eine neue Architektur-Engine baut daraus ein komplettes Gebäude wie aus einem Schematic: Sockel, Fachwerk, Fenster mit Läden, Dach mit Überstand, Kamin, Treppenhaus, eingerichtete Räume, Veranda, Garten.
- Haus, Hütte, Herrenhaus, Taverne, Scheune, moderne Villa, Turm, Leuchtturm, Burg, Kirche und Tempel in 10 Stilen.
- **Baumeister-Axt**: Linksklick auf einen Block = Ecke 1, Rechtsklick = Ecke 2, Rechtsklick in die Luft = Menü („⚒ Axt holen“ im Menü oder Kreativ-Inventar).
- Farbiges **Hologramm** des Gebäudes in der Welt, **Variante** (andere Details ohne KI), **Schematic-Export** (.schem für WorldEdit/Litematica).
- Funktioniert auch ohne Ollama (Stichwort-Erkennung) und deutlich schneller, weil die KI nur noch einen kurzen Entwurf schreibt.

**Am einfachsten: `KI-Baumeister-Komplettpaket.zip`** unten bei *Assets* herunterladen, entpacken und `Installieren.bat` doppelklicken.
Das Skript richtet das Fabric-Profil im Minecraft Launcher ein, kopiert KI-Baumeister und Fabric API in den `mods`-Ordner
und installiert bei Bedarf Ollama samt passendem KI-Modell. Ist Ollama schon installiert, wird nur das Modell geprüft.

Nur die Mod: `ki-baumeister-…jar` in den `mods`-Ordner legen (benötigt **Minecraft 26.3**, **Fabric Loader ≥ 0.19.5**, **Fabric API**, **Ollama**).

Im Spiel: Einzelspielerwelt öffnen, **K** drücken, mit der Baumeister-Axt zwei Ecken setzen, Bauwerk beschreiben, **✦ KI-Entwurf**, Hologramm prüfen, **▶ Bauen** – oder **↶ Rückgängig**.

Vor großen Bauten eine Sicherung der Welt anlegen. Ausführliche Anleitung: `ANLEITUNG.txt` im Komplettpaket.
