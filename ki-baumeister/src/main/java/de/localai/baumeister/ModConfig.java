package de.localai.baumeister;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Einstellungen in {@code config/ki_baumeister.json}; fehlende Werte bekommen Standardwerte. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ModConfig instance;

    /** Adresse des lokalen Ollama-Servers. */
    public String ollamaUrl = "http://localhost:11434";
    /** Zuletzt gewähltes Modell (wird beim Öffnen gegen die installierten Modelle geprüft). */
    public String model = "qwen2.5:14b";
    public double temperature = 0.5;
    /** Mindest-Kontextgröße; wird bei langen Gesprächen automatisch erhöht. */
    public int contextSize = 4096;
    public int maxOutputTokens = 2048;
    public int timeoutMinutes = 10;
    /** Wie viele Blöcke pro Server-Tick gesetzt werden (20 Ticks = 1 Sekunde). */
    public int blocksPerTick = 400;
    public int maxHorizontalSize = 64;
    public int maxHeight = 64;
    public int maxVolume = 160_000;
    /** Bereich und Gebäude als Hologramm in der Welt anzeigen. */
    public boolean preview = true;
    public String lastPrompt = "Mittelalterliches Fachwerkhaus mit Steinsockel, zwei Stockwerken, Satteldach, Kamin und Garten";

    public static ModConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("ki_baumeister.json");
    }

    private static ModConfig load() {
        Path path = file();
        ModConfig cfg = null;
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                BaumeisterClient.LOG.warn("Konfiguration {} unlesbar, verwende Standardwerte", path, e);
            }
        }
        if (cfg == null) cfg = new ModConfig();
        cfg.sanitize();
        cfg.save();
        return cfg;
    }

    private void sanitize() {
        ModConfig d = new ModConfig();
        if (ollamaUrl == null || ollamaUrl.isBlank()) ollamaUrl = d.ollamaUrl;
        ollamaUrl = ollamaUrl.trim();
        while (ollamaUrl.endsWith("/")) ollamaUrl = ollamaUrl.substring(0, ollamaUrl.length() - 1);
        if (!ollamaUrl.startsWith("http://") && !ollamaUrl.startsWith("https://")) ollamaUrl = "http://" + ollamaUrl;
        if (model == null) model = "";
        if (lastPrompt == null) lastPrompt = d.lastPrompt;
        temperature = clamp(temperature, 0.0, 2.0);
        contextSize = (int) clamp(contextSize, 2048, 131072);
        maxOutputTokens = (int) clamp(maxOutputTokens, 1024, 32768);
        timeoutMinutes = (int) clamp(timeoutMinutes, 1, 60);
        blocksPerTick = (int) clamp(blocksPerTick, 1, 20000);
        maxHorizontalSize = (int) clamp(maxHorizontalSize, 4, 256);
        maxHeight = (int) clamp(maxHeight, 4, 256);
        maxVolume = (int) clamp(maxVolume, 64, 2_000_000);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BaumeisterClient.LOG.warn("Konfiguration {} konnte nicht gespeichert werden", path, e);
        }
    }
}
