package de.localai.baumeister;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import de.localai.baumeister.arch.Architect;
import de.localai.baumeister.arch.BuildingSpec;
import de.localai.baumeister.arch.Palette;
import de.localai.baumeister.arch.SchematicWriter;
import de.localai.baumeister.arch.Voxels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Zustand und Aktionen auf dem Client-Thread: Ecken, KI-Anfrage, Bauplan, Bauen/Rückgängig. */
public final class Session {
    public enum Tone { INFO, OK, WARN, ERROR, BUSY }

    /** Bevorzugte Modelle, falls das konfigurierte Modell nicht installiert ist. */
    private static final String[] PREFERRED_MODELS = {"qwen3", "qwen2.5-coder", "qwen2.5", "gemma3", "llama3.1", "llama3", "mistral"};

    public static BlockPos cornerA;
    public static BlockPos cornerB;
    public static OllamaClient.Generation generation;
    public static PlanCompiler.Result result;
    /** Zuletzt gebauter Plan (dafür wird keine Partikel-Vorschau mehr gezeigt). */
    public static PlanCompiler.Result built;
    private static List<OllamaClient.Message> conversation;
    private static String status = "Bereit. Setze zwei Ecken und beschreibe dein Bauwerk.";
    private static Tone tone = Tone.INFO;

    public static boolean ollamaChecking;
    public static Boolean ollamaOnline;
    public static String ollamaVersion = "";
    public static List<String> models = List.of();

    private Session() {}

    private record Outcome(String text, PlanCompiler.Result compiled) {}

    private record OllamaInfo(String version, List<OllamaClient.ModelInfo> models) {}

    public static String status() { return status; }
    public static Tone tone() { return tone; }
    public static boolean canRefine() { return conversation != null && result != null; }

    public static void setStatus(Tone t, String message) {
        tone = t;
        status = message;
    }

    /** Ecke an den anvisierten Block (bis 96 Blöcke) setzen, sonst an die Füße des Spielers. */
    public static void captureCorner(boolean first) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        HitResult hit = mc.player.pick(96.0, 1.0f, false);
        BlockPos pos = hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
            ? bhr.getBlockPos() : mc.player.blockPosition();
        setCorner(first, pos);
    }

    /** Ecke setzen (auch von der Baumeister-Axt aufgerufen). */
    public static void setCorner(boolean first, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (first) cornerA = pos;
        else cornerB = pos;
        if (result != null && cornerA != null && cornerB != null) {
            BuildRegion now = BuildRegion.of(cornerA, cornerB, mc.player.position());
            if (!now.sameArea(result.region())) {
                result = null;
                conversation = null;
            }
        }
        setStatus(Tone.INFO, (first ? "Ecke 1" : "Ecke 2") + " gesetzt: " + fmt(pos) + sizeHint());
    }

    public static void clearCorners() {
        cornerA = null;
        cornerB = null;
        result = null;
        conversation = null;
        setStatus(Tone.INFO, "Ecken gelöscht.");
    }

    private static String sizeHint() {
        if (cornerA == null || cornerB == null) return "";
        BuildRegion r = BuildRegion.of(cornerA, cornerB, Minecraft.getInstance().player.position());
        return "  →  " + r.sizeX() + "×" + r.sizeY() + "×" + r.sizeZ();
    }

    /** Fehlermeldung, falls der Bereich fehlt oder zu groß ist, sonst {@code null}. */
    public static String validateRegion() {
        if (cornerA == null || cornerB == null) return "Bitte zuerst beide Ecken setzen (Baumeister-Axt oder Knöpfe).";
        ModConfig cfg = ModConfig.get();
        BuildRegion r = BuildRegion.of(cornerA, cornerB, Minecraft.getInstance().player.position());
        if (r.sizeX() > cfg.maxHorizontalSize || r.sizeZ() > cfg.maxHorizontalSize || r.sizeY() > cfg.maxHeight) {
            return "Bereich zu groß: maximal " + cfg.maxHorizontalSize + "×" + cfg.maxHeight + "×" + cfg.maxHorizontalSize + ".";
        }
        if (r.volume() > cfg.maxVolume) return "Bereich zu groß: maximal " + fmtInt(cfg.maxVolume) + " Raumblöcke.";
        if (r.sizeX() < 5 || r.sizeZ() < 5 || r.sizeY() < 5) return "Bereich zu klein: mindestens 5×5×5 für ein Gebäude.";
        return null;
    }

    /**
     * Lässt die KI einen Entwurf (Typ, Stil, Geschosse, Materialien …) wählen und baut daraus mit der
     * Architektur-Engine das Gebäude. Ist Ollama nicht erreichbar, entsteht der Entwurf aus Stichworten.
     */
    public static void generate(String prompt, String model, boolean refine) {
        Minecraft mc = Minecraft.getInstance();
        if (generation != null || mc.player == null) return;
        if (prompt == null || prompt.isBlank()) {
            setStatus(Tone.WARN, refine ? "Beschreibe, was am Entwurf geändert werden soll." : "Beschreibe zuerst, was gebaut werden soll.");
            return;
        }
        refine = refine && canRefine();
        BuildRegion region;
        List<OllamaClient.Message> messages = new ArrayList<>();
        if (refine) {
            region = result.region();
            messages.addAll(conversation);
            messages.add(new OllamaClient.Message("user", PlanPrompt.refinement(prompt)));
        } else {
            String error = validateRegion();
            if (error != null) {
                setStatus(Tone.WARN, error);
                return;
            }
            region = BuildRegion.of(cornerA, cornerB, mc.player.position());
            messages.add(new OllamaClient.Message("system", PlanPrompt.system()));
            messages.add(new OllamaClient.Message("user", PlanPrompt.request(prompt, region)));
        }
        if (model == null || model.isBlank() || Boolean.FALSE.equals(ollamaOnline)) {
            // Ohne KI: Entwurf aus Stichworten
            BuildingSpec spec = Architect.fromKeywords(prompt);
            result = compile(spec, region);
            setStatus(Tone.WARN, "Ollama nicht erreichbar – Entwurf aus Stichworten erstellt: " + describe(spec) + ".");
            return;
        }

        ModConfig cfg = ModConfig.get();
        OllamaClient.Generation gen = OllamaClient.chat(cfg, model, messages, BuildingSpec.schema());
        generation = gen;
        boolean refining = refine;
        setStatus(Tone.BUSY, refine ? "Der KI-Architekt überarbeitet den Entwurf …" : "Der KI-Architekt entwirft das Gebäude …");

        gen.result()
            .thenApplyAsync(text -> {
                BuildingSpec spec = BuildingSpec.parse(text);
                return new Outcome(text, compile(spec, region));
            }, BaumeisterClient.EXECUTOR)
            .whenComplete((res, err) -> mc.execute(() -> {
                if (generation != gen) return;
                generation = null;
                if (err != null) {
                    Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                    if (cause instanceof CancellationException) {
                        setStatus(Tone.INFO, "Abgebrochen.");
                        return;
                    }
                    BaumeisterClient.LOG.warn("KI-Entwurf fehlgeschlagen, nutze Stichworte", cause);
                    if (!refining) {
                        BuildingSpec spec = Architect.fromKeywords(prompt);
                        result = compile(spec, region);
                        setStatus(Tone.WARN, "KI-Fehler (" + cause.getMessage() + ") – Entwurf aus Stichworten: " + describe(spec) + ".");
                    } else {
                        setStatus(Tone.ERROR, cause.getMessage() != null ? cause.getMessage() : cause.toString());
                    }
                    return;
                }
                result = res.compiled();
                // Nur System, (ergänzten) Auftrag und den neuesten Entwurf behalten.
                OllamaClient.Message request = messages.get(1);
                if (refining) request = new OllamaClient.Message("user", request.content() + "\nAlso: " + prompt.trim());
                conversation = List.of(messages.get(0), request, new OllamaClient.Message("assistant", res.text()));
                setStatus(Tone.OK, (refining ? "Entwurf angepasst: " : "Entwurf fertig: ") + describe(result.spec()) + " – "
                    + fmtInt(result.solidBlocks()) + " Blöcke. Prüfe das Hologramm und klicke „Bauen“.");
            }));
    }

    /** Baut den Entwurf mit der Architektur-Engine und rechnet ihn in Minecraft-Blöcke um. */
    private static PlanCompiler.Result compile(BuildingSpec spec, BuildRegion region) {
        Voxels vox = Architect.generate(spec, region.localWidth(), region.localHeight(), region.localDepth(), PlanCompiler::exists);
        return PlanCompiler.compile(spec, vox, region);
    }

    /** Gleicher Entwurf, andere Variante (Zufall für Details, Pflanzen, Einrichtung). */
    public static void variant() {
        if (result == null || generation != null) return;
        BuildingSpec spec = result.spec().withSeed(result.spec().seed() * 31 + 17);
        result = compile(spec, result.region());
        setStatus(Tone.OK, "Neue Variante: " + describe(spec) + ".");
    }

    public static String describe(BuildingSpec spec) {
        return typeName(spec.type()) + ", " + styleName(spec.style()) + ", " + spec.floors()
            + (spec.floors() == 1 ? " Geschoss" : " Geschosse");
    }

    public static String typeName(BuildingSpec.Type t) {
        return switch (t) {
            case HOUSE -> "Wohnhaus";
            case COTTAGE -> "Hütte";
            case MANSION -> "Herrenhaus";
            case TAVERN -> "Taverne";
            case BARN -> "Scheune";
            case MODERN_HOUSE -> "Moderne Villa";
            case TOWER -> "Turm";
            case LIGHTHOUSE -> "Leuchtturm";
            case CASTLE -> "Burg";
            case TEMPLE -> "Tempel";
            case CHURCH -> "Kirche";
            case CUSTOM -> "Gebäude";
        };
    }

    public static String styleName(Palette.Style s) {
        return switch (s) {
            case MEDIEVAL -> "mittelalterlich";
            case RUSTIC -> "rustikal";
            case NORDIC -> "nordisch";
            case FANTASY -> "Fantasy";
            case VICTORIAN -> "viktorianisch";
            case JAPANESE -> "japanisch";
            case DESERT -> "Wüste";
            case MODERN -> "modern";
            case CLASSICAL -> "antik";
            case STONE -> "Stein";
            case VIKING -> "Wikinger";
            case GOTHIC -> "gotisch";
            case STEAMPUNK -> "Steampunk";
            case ORIENTAL -> "orientalisch";
        };
    }

    public static void cancelGeneration() {
        if (generation != null) generation.cancel();
    }

    /** Speichert den aktuellen Entwurf als Sponge-Schematic (.schem) für WorldEdit/Litematica. */
    public static void saveSchematic() {
        if (result == null) {
            setStatus(Tone.WARN, "Erst einen Entwurf erstellen.");
            return;
        }
        PlanCompiler.Result r = result;
        String name = r.spec().type().id() + "-" + r.spec().style().name().toLowerCase(Locale.ROOT) + "-"
            + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("schematics");
        java.nio.file.Path file = dir.resolve("ki-baumeister-" + name + ".schem");
        BaumeisterClient.EXECUTOR.execute(() -> {
            try {
                java.nio.file.Files.createDirectories(dir);
                try (var out = java.nio.file.Files.newOutputStream(file)) {
                    SchematicWriter.write(r.voxels(), net.minecraft.SharedConstants.WORLD_VERSION, r.spec().summary(), out);
                }
                Minecraft.getInstance().execute(() -> setStatus(Tone.OK, "Gespeichert: schematics/" + file.getFileName()));
            } catch (Exception e) {
                BaumeisterClient.LOG.warn("Schematic speichern fehlgeschlagen", e);
                Minecraft.getInstance().execute(() -> setStatus(Tone.ERROR, "Speichern fehlgeschlagen: " + e.getMessage()));
            }
        });
    }

    /** Gibt dem Spieler eine Baumeister-Axt (Einzelspieler). */
    public static void giveAxe() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            setStatus(Tone.WARN, "Die Axt gibt es per Knopf nur im Einzelspieler – im Kreativ-Inventar unter „Werkzeuge“.");
            return;
        }
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.getInventory().add(new ItemStack(BaumeisterMod.BAUMEISTER_AXT));
        });
        setStatus(Tone.OK, "Baumeister-Axt erhalten: Linksklick = Ecke 1, Rechtsklick = Ecke 2.");
    }

    public static void build() {
        Minecraft mc = Minecraft.getInstance();
        if (result == null) {
            setStatus(Tone.WARN, "Erst einen Bauplan erstellen.");
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            setStatus(Tone.ERROR, "Bauen geht nur in Einzelspielerwelten (bzw. als LAN-Host).");
            return;
        }
        if (BuildManager.progress().running()) {
            setStatus(Tone.WARN, "Es läuft bereits ein Bauauftrag.");
            return;
        }
        ResourceKey<Level> dim = mc.player.level().dimension();
        List<PlanCompiler.Placement> placements = result.placements();
        int perTick = ModConfig.get().blocksPerTick;
        server.execute(() -> BuildManager.startBuild(dim, placements, perTick));
        built = result;
        setStatus(Tone.BUSY, "Bau gestartet – " + fmtInt(placements.size()) + " Positionen.");
    }

    public static void stopBuild() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(BuildManager::stop);
    }

    public static void undo() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        if (BuildManager.undoAvailable() == 0) {
            setStatus(Tone.WARN, "Nichts zum Rückgängigmachen.");
            return;
        }
        int perTick = ModConfig.get().blocksPerTick;
        server.execute(() -> BuildManager.startUndo(perTick));
        built = null;
        setStatus(Tone.BUSY, "Mache den letzten Bau rückgängig …");
    }

    /** Ollama-Version und installierte Modelle abfragen (asynchron). */
    public static void refreshOllama() {
        if (ollamaChecking) return;
        ollamaChecking = true;
        Minecraft mc = Minecraft.getInstance();
        String url = ModConfig.get().ollamaUrl;
        OllamaClient.version(url)
            .thenCompose(v -> OllamaClient.models(url).thenApply(list -> new OllamaInfo(v, list)))
            .whenComplete((res, err) -> mc.execute(() -> {
                ollamaChecking = false;
                if (err != null) {
                    ollamaOnline = false;
                    Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                    if (generation == null) setStatus(Tone.ERROR, cause.getMessage());
                    return;
                }
                ollamaOnline = true;
                ollamaVersion = res.version();
                models = res.models().stream().map(OllamaClient.ModelInfo::name).toList();
                ModConfig cfg = ModConfig.get();
                if (models.isEmpty()) {
                    if (generation == null) setStatus(Tone.WARN, "Ollama läuft, aber es ist kein Modell installiert: ollama pull qwen2.5:7b");
                } else if (!models.contains(cfg.model)) {
                    cfg.model = pickModel(res.models());
                    cfg.save();
                }
            }));
    }

    /**
     * Wählt ein passendes installiertes Modell: bevorzugte Familien zuerst, davon das größte,
     * das noch gut in den Arbeitsspeicher passt (≥ 3 GB, ≤ 60 % RAM).
     */
    private static String pickModel(List<OllamaClient.ModelInfo> installed) {
        long maxBytes = Math.max(4L << 30, (long) (totalMemoryBytes() * 0.6));
        for (String pref : PREFERRED_MODELS) {
            OllamaClient.ModelInfo best = null;
            for (OllamaClient.ModelInfo m : installed) {
                if (!m.name().toLowerCase(Locale.ROOT).startsWith(pref)) continue;
                if (m.size() < (3L << 30) || m.size() > maxBytes) continue;
                if (best == null || m.size() > best.size()) best = m;
            }
            if (best != null) return best.name();
        }
        for (String pref : PREFERRED_MODELS) {
            for (OllamaClient.ModelInfo m : installed) {
                if (m.name().toLowerCase(Locale.ROOT).startsWith(pref)) return m.name();
            }
        }
        return installed.getFirst().name();
    }

    private static long totalMemoryBytes() {
        try {
            if (java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean os) {
                return os.getTotalMemorySize();
            }
        } catch (Throwable ignored) {
            // Nicht jede Java-Laufzeit liefert den Gesamtspeicher.
        }
        return 16L << 30;
    }

    public static void reset() {
        cancelGeneration();
        generation = null;
        cornerA = null;
        cornerB = null;
        result = null;
        conversation = null;
        setStatus(Tone.INFO, "Bereit. Setze zwei Ecken und beschreibe dein Bauwerk.");
    }

    public static String fmt(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    public static String fmtInt(long n) {
        return String.format(Locale.GERMANY, "%,d", n);
    }
}
