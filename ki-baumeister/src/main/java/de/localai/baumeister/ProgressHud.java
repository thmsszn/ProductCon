package de.localai.baumeister;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Kleines Fortschritts-Panel oben links, solange das Menü geschlossen ist. */
final class ProgressHud {
    private static final long FINISHED_MILLIS = 6000;

    private static boolean wasBusy;
    private static String finishedText;
    private static int finishedColor;
    private static long finishedUntil;

    private ProgressHud() {}

    /** Client-Tick: Abschlussmeldung merken, sobald KI oder Bau fertig sind. */
    static void tick() {
        BuildManager.Progress p = BuildManager.progress();
        boolean busy = p.running() || Session.generation != null;
        if (wasBusy && !busy) {
            boolean buildMessage = !p.message().isEmpty() && Session.tone() == Session.Tone.BUSY;
            finishedText = buildMessage ? p.message() : Session.status();
            finishedColor = buildMessage ? Ui.GREEN : switch (Session.tone()) {
                case OK -> Ui.GREEN;
                case WARN -> Ui.AMBER;
                case ERROR -> Ui.RED;
                default -> Ui.ACCENT;
            };
            finishedUntil = System.currentTimeMillis() + FINISHED_MILLIS;
        }
        wasBusy = busy;
    }

    static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() instanceof BuilderScreen) return;
        BuildManager.Progress p = BuildManager.progress();
        OllamaClient.Generation gen = Session.generation;
        String text;
        int color;
        float fraction;
        if (gen != null) {
            long secs = gen.elapsedMillis() / 1000;
            text = gen.contentChars() == 0 ? "✦ KI denkt nach … " + secs + " s"
                : "✦ KI entwirft Bauplan … " + gen.elements() + " Elemente · " + secs + " s";
            color = Ui.ACCENT;
            fraction = -1f;
        } else if (p.running()) {
            text = (p.undo() ? "↶ " : "▶ ") + p.message() + " " + Math.round(p.fraction() * 100) + " %";
            color = p.undo() ? Ui.AMBER : Ui.GREEN;
            fraction = p.fraction();
        } else if (finishedText != null && System.currentTimeMillis() < finishedUntil) {
            text = finishedText;
            color = finishedColor;
            fraction = 1f;
        } else {
            return;
        }
        Font font = mc.font;
        int maxW = Math.min(260, g.guiWidth() - 12);
        text = Ui.ellipsize(font, text, maxW - 14);
        int w = Math.max(120, font.width(text) + 14);
        int x = 6, y = 6;
        Ui.card(g, x, y, w, 20, 0xE00E141B, Ui.withAlpha(color, 0xA0));
        Ui.dot(g, x + 5, y + 6, color);
        g.text(font, text, x + 12, y + 5, Ui.TEXT, false);
        Ui.progress(g, x + 3, y + 15, w - 6, 2, fraction, color);
    }
}
