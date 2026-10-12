package de.localai.baumeister;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Farbpalette und Zeichenhilfen für die Oberfläche (alle Farben ARGB). */
final class Ui {
    static final int PANEL = 0xF20E141B;
    static final int PANEL_BORDER = 0xFF2B3A4A;
    static final int HEADER_TOP = 0xFF16324A;
    static final int HEADER_BOTTOM = 0xFF0E1B29;
    static final int CARD = 0xFF162130;
    static final int CARD_BORDER = 0xFF26364A;
    static final int TEXT = 0xFFE6EDF5;
    static final int MUTED = 0xFF8EA0B5;
    static final int FAINT = 0xFF5B6B7E;
    static final int ACCENT = 0xFF22D3EE;
    static final int ACCENT_DARK = 0xFF0E7490;
    static final int GREEN = 0xFF4ADE80;
    static final int AMBER = 0xFFFBBF24;
    static final int RED = 0xFFF87171;
    static final int VIOLET = 0xFFA78BFA;

    private Ui() {}

    /** Rechteck mit abgerundeten Ecken (Radius 2). */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w < 4 || h < 4) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + 2, x + 1, y + h - 2, color);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
    }

    /** Abgerundetes Rechteck mit 1-px-Rahmen. */
    static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        round(g, x, y, w, h, border);
        round(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    /** Horizontaler Farbverlauf (in 4-px-Streifen). */
    static void hGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int left, int right) {
        for (int i = 0; i < w; i += 4) {
            float t = w <= 1 ? 0 : i / (float) (w - 1);
            g.fill(x + i, y, Math.min(x + w, x + i + 4), y + h, lerp(left, right, t));
        }
    }

    static int lerp(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
            | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | (color & 0xFFFFFF);
    }

    /** Fortschrittsbalken; {@code fraction < 0} zeigt einen animierten, unbestimmten Balken. */
    static void progress(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
        round(g, x, y, w, h, 0xFF1B2838);
        if (fraction >= 0) {
            int fw = Math.round(w * Math.min(1f, fraction));
            if (fw > 0) {
                g.fill(x, y, x + fw, y + h, color);
                g.fill(x, y, x + fw, y + 1, withAlpha(0xFFFFFF, 0x40));
            }
        } else {
            int seg = Math.max(12, w / 4);
            long t = System.currentTimeMillis() / 8;
            int start = (int) (t % (w + seg)) - seg;
            int s = Math.max(x, x + start), e = Math.min(x + w, x + start + seg);
            if (e > s) {
                hGradient(g, s, y, e - s, h, withAlpha(color, 0x60), color);
            }
        }
    }

    /** Text auf maximale Breite kürzen (mit „…“). */
    static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String dots = "…";
        int limit = maxWidth - font.width(dots);
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) > limit) end--;
        return text.substring(0, end) + dots;
    }

    /** Kleiner Statuspunkt. */
    static void dot(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 5, color);
        g.fill(x, y + 1, x + 5, y + 4, color);
    }

    static String compact(int n) {
        if (n >= 10_000) return (n / 1000) + "k";
        if (n >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", n / 1000f);
        return Integer.toString(n);
    }
}
