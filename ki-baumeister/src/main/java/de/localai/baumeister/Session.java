package de.localai.baumeister;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
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

    private record OllamaInfo(String version, List<String> models) {}

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
        BlockPos pos;
        HitResult hit = mc.player.pick(96.0, 1.0f, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) pos = bhr.getBlockPos();
        else pos = mc.player.blockPosition();
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
        if (cornerA == null || cornerB == null) return "Bitte zuerst beide Ecken setzen.";
        ModConfig cfg = ModConfig.get();
        BuildRegion r = BuildRegion.of(cornerA, cornerB, Minecraft.getInstance().player.position());
        if (r.sizeX() > cfg.maxHorizontalSize || r.sizeZ() > cfg.maxHorizontalSize || r.sizeY() > cfg.maxHeight) {
            return "Bereich zu groß: maximal " + cfg.maxHorizontalSize + "×" + cfg.maxHeight + "×" + cfg.maxHorizontalSize + ".";
        }
        if (r.volume() > cfg.maxVolume) return "Bereich zu groß: maximal " + fmtInt(cfg.maxVolume) + " Raumblöcke.";
        if (r.sizeX() < 3 || r.sizeZ() < 3 || r.sizeY() < 3) return "Bereich zu klein: mindestens 3×3×3.";
        return null;
    }

    public static void generate(String prompt, String model, boolean refine) {
        Minecraft mc = Minecraft.getInstance();
        if (generation != null || mc.player == null) return;
        if (prompt == null || prompt.isBlank()) {
            setStatus(Tone.WARN, refine ? "Beschreibe, was am Plan geändert werden soll." : "Beschreibe zuerst, was gebaut werden soll.");
            return;
        }
        if (model == null || model.isBlank()) {
            setStatus(Tone.ERROR, "Kein Ollama-Modell gewählt. In der Konsole z.B.: ollama pull qwen2.5:14b");
            return;
        }
        refine = refine && canRefine();
        BuildRegion region;
        List<OllamaClient.Message> messages = new ArrayList<>();
        if (refine) {
            region = result.region();
            messages.addAll(conversation);
            messages.add(new OllamaClient.Message("user", PlanPrompt.refinement(prompt, region)));
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

        ModConfig cfg = ModConfig.get();
        OllamaClient.Generation gen = OllamaClient.chat(cfg, model, messages, PlanPrompt.schema());
        generation = gen;
        boolean clear = cfg.clearArea;
        boolean refining = refine;
        setStatus(Tone.BUSY, refine ? "KI passt den Plan an …" : "KI entwirft den Bauplan …");

        gen.result()
            .thenApplyAsync(text -> new Outcome(text, PlanCompiler.compile(PlanParser.parse(text), region, clear)),
                BaumeisterClient.EXECUTOR)
            .whenComplete((res, err) -> mc.execute(() -> {
                if (generation != gen) return;
                generation = null;
                if (err != null) {
                    Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                    if (cause instanceof CancellationException) setStatus(Tone.INFO, "Abgebrochen.");
                    else {
                        setStatus(Tone.ERROR, cause.getMessage() != null ? cause.getMessage() : cause.toString());
                        BaumeisterClient.LOG.warn("Bauplan-Erzeugung fehlgeschlagen", cause);
                    }
                    return;
                }
                String text = res.text();
                PlanCompiler.Result compiled = res.compiled();
                if (compiled.solidBlocks() == 0) {
                    setStatus(Tone.ERROR, "Der Plan enthält keine setzbaren Blöcke. Bitte erneut versuchen.");
                    return;
                }
                result = compiled;
                // Nur System, (ergänzten) Auftrag und den neuesten Plan behalten, damit der Kontext klein bleibt.
                OllamaClient.Message request = messages.get(1);
                if (refining) request = new OllamaClient.Message("user", request.content() + "\nAlso: " + prompt.trim());
                conversation = List.of(messages.get(0), request, new OllamaClient.Message("assistant", text));
                StringBuilder msg = new StringBuilder(refining ? "Plan angepasst: " : "Plan fertig: ")
                    .append(fmtInt(compiled.solidBlocks())).append(" Blöcke aus ")
                    .append(compiled.plan().elements().size()).append(" Elementen. Prüfe die Vorschau und klicke „Bauen“.");
                Tone t = Tone.OK;
                if (gen.truncated()) {
                    msg.append(" Hinweis: Antwort war zu lang und wurde gekürzt.");
                    t = Tone.WARN;
                }
                if (!compiled.rejected().isEmpty()) {
                    msg.append(" Ignoriert: ").append(String.join(", ", compiled.rejected().stream().limit(3).toList()));
                    t = Tone.WARN;
                }
                setStatus(t, msg.toString());
            }));
    }

    public static void cancelGeneration() {
        if (generation != null) generation.cancel();
    }

    /** Nach Änderung von „Bereich leeren“ den vorhandenen Plan neu berechnen. */
    public static void recompile() {
        if (result == null) return;
        result = PlanCompiler.compile(result.plan(), result.region(), ModConfig.get().clearArea);
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
                models = List.copyOf(res.models());
                ModConfig cfg = ModConfig.get();
                if (models.isEmpty()) {
                    if (generation == null) setStatus(Tone.WARN, "Ollama läuft, aber es ist kein Modell installiert: ollama pull qwen2.5:14b");
                } else if (!models.contains(cfg.model)) {
                    cfg.model = pickModel(models);
                    cfg.save();
                }
            }));
    }

    private static String pickModel(List<String> installed) {
        for (String pref : PREFERRED_MODELS) {
            for (String m : installed) if (m.toLowerCase(Locale.ROOT).startsWith(pref)) return m;
        }
        return installed.getFirst();
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
