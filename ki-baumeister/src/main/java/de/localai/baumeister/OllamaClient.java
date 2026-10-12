package de.localai.baumeister;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Schlanker Client für die lokale Ollama-API (https://github.com/ollama/ollama/blob/main/docs/api.md). */
public final class OllamaClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(4))
        .executor(BaumeisterClient.EXECUTOR)
        .build();

    private OllamaClient() {}

    /** Eine Chat-Nachricht ({@code role} = system, user oder assistant). */
    public record Message(String role, String content) {}

    /** Fehler mit einer für Spieler verständlichen Meldung. */
    public static final class OllamaException extends RuntimeException {
        public OllamaException(String message) { super(message); }
    }

    public static CompletableFuture<String> version(String baseUrl) {
        return get(baseUrl, "/api/version", Duration.ofSeconds(4))
            .thenApply(json -> json.get("version").getAsString());
    }

    /** Namen aller lokal installierten Modelle, alphabetisch sortiert. */
    public static CompletableFuture<List<String>> models(String baseUrl) {
        return get(baseUrl, "/api/tags", Duration.ofSeconds(6)).thenApply(json -> {
            List<String> names = new ArrayList<>();
            JsonArray arr = json.getAsJsonArray("models");
            if (arr != null) {
                for (JsonElement el : arr) names.add(el.getAsJsonObject().get("name").getAsString());
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        });
    }

    private static CompletableFuture<JsonObject> get(String baseUrl, String path, Duration timeout) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout).GET().build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new OllamaException("Ungültige Ollama-Adresse: " + baseUrl));
        }
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).handle((resp, err) -> {
            if (err != null) throw new CompletionException(friendly(unwrap(err), baseUrl, null));
            if (resp.statusCode() != 200) throw new OllamaException("Ollama antwortet mit HTTP " + resp.statusCode());
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        });
    }

    /**
     * Startet eine gestreamte Chat-Anfrage. {@code format} ist ein JSON-Schema, an das sich das Modell
     * halten muss (Structured Outputs, Ollama ≥ 0.5).
     */
    public static Generation chat(ModConfig cfg, String model, List<Message> messages, JsonObject format) {
        Generation gen = new Generation();
        BaumeisterClient.EXECUTOR.execute(() -> gen.run(cfg, model, messages, format));
        return gen;
    }

    /** Laufende Anfrage: Fortschritt kann von jedem Thread gelesen werden, {@link #cancel()} bricht ab. */
    public static final class Generation {
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private volatile boolean cancelled;
        private volatile InputStream stream;
        private volatile int contentChars;
        private volatile int thinkingChars;
        private volatile int elements;
        private volatile boolean truncated;
        private final long startedAt = System.currentTimeMillis();

        public CompletableFuture<String> result() { return result; }
        public int contentChars() { return contentChars; }
        public int thinkingChars() { return thinkingChars; }
        /** Anzahl bereits empfangener Bauelemente (grob, für die Fortschrittsanzeige). */
        public int elements() { return elements; }
        /** {@code true}, wenn die Ausgabe wegen des Token-Limits abgeschnitten wurde. */
        public boolean truncated() { return truncated; }
        public boolean isThinking() { return contentChars == 0 && thinkingChars > 0; }
        public long elapsedMillis() { return System.currentTimeMillis() - startedAt; }

        public void cancel() {
            cancelled = true;
            InputStream s = stream;
            if (s != null) {
                try { s.close(); } catch (IOException ignored) {}
            }
            result.completeExceptionally(new CancellationException("Abgebrochen"));
        }

        private void run(ModConfig cfg, String model, List<Message> messages, JsonObject format) {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.addProperty("stream", true);
                body.addProperty("keep_alive", "15m");
                body.add("format", format);
                JsonArray msgs = new JsonArray();
                int chars = 0;
                for (Message m : messages) {
                    JsonObject o = new JsonObject();
                    o.addProperty("role", m.role());
                    o.addProperty("content", m.content());
                    msgs.add(o);
                    chars += m.content().length();
                }
                body.add("messages", msgs);
                JsonObject options = new JsonObject();
                options.addProperty("temperature", cfg.temperature);
                options.addProperty("num_predict", cfg.maxOutputTokens);
                options.addProperty("num_ctx", contextFor(cfg, chars));
                body.add("options", options);

                HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.ollamaUrl + "/api/chat"))
                    .timeout(Duration.ofMinutes(cfg.timeoutMinutes))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
                HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                stream = resp.body();
                if (cancelled) { stream.close(); return; }
                if (resp.statusCode() != 200) {
                    String err;
                    try (InputStream in = resp.body()) { err = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
                    throw httpError(resp.statusCode(), err, model);
                }
                result.complete(readStream(resp.body()));
            } catch (Throwable t) {
                if (cancelled) result.completeExceptionally(new CancellationException("Abgebrochen"));
                else result.completeExceptionally(friendly(t, cfg.ollamaUrl, model));
            }
        }

        private String readStream(InputStream in) throws IOException {
            StringBuilder content = new StringBuilder(8192);
            int scanFrom = 0;
            int count = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled) throw new CancellationException("Abgebrochen");
                    if (line.isBlank()) continue;
                    JsonObject chunk = JsonParser.parseString(line).getAsJsonObject();
                    if (chunk.has("error")) throw new OllamaException("Ollama: " + chunk.get("error").getAsString());
                    JsonObject msg = chunk.getAsJsonObject("message");
                    if (msg != null) {
                        if (msg.has("thinking") && msg.get("thinking").isJsonPrimitive()) {
                            thinkingChars += msg.get("thinking").getAsString().length();
                        }
                        if (msg.has("content") && msg.get("content").isJsonPrimitive()) {
                            content.append(msg.get("content").getAsString());
                            contentChars = content.length();
                            int idx;
                            while ((idx = content.indexOf("\"type\"", scanFrom)) >= 0) {
                                count++;
                                scanFrom = idx + 6;
                            }
                            scanFrom = Math.max(scanFrom, content.length() - 6);
                            elements = count;
                        }
                    }
                    if (chunk.has("done") && chunk.get("done").getAsBoolean()) {
                        JsonElement reason = chunk.get("done_reason");
                        truncated = reason != null && reason.isJsonPrimitive() && "length".equals(reason.getAsString());
                        break;
                    }
                }
            }
            return content.toString();
        }
    }

    /** Kontext so groß wählen, dass Eingabe (≈ 3 Zeichen pro Token) und Antwort hineinpassen. */
    private static int contextFor(ModConfig cfg, int inputChars) {
        int needed = inputChars / 3 + cfg.maxOutputTokens + 512;
        int ctx = Math.max(cfg.contextSize, needed);
        ctx = (ctx + 2047) / 2048 * 2048;
        return Math.min(ctx, 65536);
    }

    private static OllamaException httpError(int status, String body, String model) {
        String detail = body;
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            if (o.has("error")) detail = o.get("error").getAsString();
        } catch (Exception ignored) {}
        if (status == 404 && detail.contains("not found")) {
            return new OllamaException("Modell „" + model + "“ ist nicht installiert. In der Konsole: ollama pull " + model);
        }
        if (detail.length() > 160) detail = detail.substring(0, 160) + "…";
        return new OllamaException("Ollama-Fehler (HTTP " + status + "): " + detail);
    }

    private static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t.getClass() == RuntimeException.class) && t.getCause() != null) t = t.getCause();
        return t;
    }

    private static RuntimeException friendly(Throwable t, String baseUrl, String model) {
        t = unwrap(t);
        if (t instanceof OllamaException oe) return oe;
        if (t instanceof CancellationException ce) return ce;
        if (t instanceof HttpConnectTimeoutException || t instanceof ConnectException) {
            return new OllamaException("Ollama nicht erreichbar unter " + baseUrl + " – läuft Ollama? (ollama serve)");
        }
        if (t instanceof HttpTimeoutException) {
            return new OllamaException("Zeitüberschreitung – das Modell braucht zu lange. Kleineres Modell oder mehr Zeit in der Konfiguration.");
        }
        String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        return new OllamaException("Verbindungsfehler: " + msg);
    }
}
