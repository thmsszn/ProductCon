package com.crudecompute.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Shared look of the datacenter screens: dark panels with a thin border, pills, bars, buttons. */
final class UiKit {
    static final int BG = 0xF00B0F14;
    static final int HEADER_TOP = 0xFF1B2330;
    static final int HEADER_BOTTOM = 0xFF131A24;
    static final int PANEL = 0xFF151B24;
    static final int PANEL2 = 0xFF1C2431;
    static final int PANEL3 = 0xFF263143;
    static final int BORDER = 0xFF2B3748;
    static final int ACCENT = 0xFF38BDF8;
    static final int ACCENT_DIM = 0xFF1E5F80;
    static final int TEXT = 0xFFDCE4EF;
    static final int DIM = 0xFF8593A6;
    static final int FAINT = 0xFF4A5568;
    static final int OK = 0xFF4ADE80;
    static final int WARN = 0xFFFBBF24;
    static final int BAD = 0xFFF87171;
    static final int DARK_TEXT = 0xFF06121C;

    private UiKit() {
    }

    static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Window background with a gradient header and an accent line under it. */
    static void window(GuiGraphics g, int x, int y, int w, int h, int headerH) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
        g.fill(x, y, x + w, y + h, BG);
        g.fillGradient(x, y, x + w, y + headerH, HEADER_TOP, HEADER_BOTTOM);
        g.fill(x, y + headerH, x + w, y + headerH + 1, ACCENT);
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hover, boolean active, boolean enabled) {
        int fill = !enabled ? 0xFF161C25 : active ? ACCENT : hover ? PANEL3 : PANEL2;
        g.fill(x, y, x + w, y + h, enabled && (hover || active) ? ACCENT_DIM : BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        int color = !enabled ? FAINT : active ? DARK_TEXT : TEXT;
        g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + (h - 8) / 2 + 1, color, false);
    }

    /** Rounded-looking status chip. Returns its width. */
    static int pill(GuiGraphics g, Font font, int x, int y, String text, int color) {
        int w = font.width(text) + 10;
        g.fill(x + 1, y, x + w - 1, y + 12, (color & 0x00FFFFFF) | 0x33000000);
        g.fill(x, y + 1, x + w, y + 11, (color & 0x00FFFFFF) | 0x33000000);
        g.fill(x + 3, y + 5, x + 6, y + 8, color);
        g.drawString(font, text, x + 8, y + 2, color, false);
        return w;
    }

    /** Horizontal meter with a label inside. */
    static void bar(GuiGraphics g, Font font, int x, int y, int w, int h, float frac, int color, String label) {
        g.fill(x, y, x + w, y + h, 0xFF0A0E13);
        int filled = Math.round((w - 2) * Math.max(0f, Math.min(1f, frac)));
        if (filled > 0) {
            g.fillGradient(x + 1, y + 1, x + 1 + filled, y + h - 1, (color & 0x00FFFFFF) | 0xCC000000, (color & 0x00FFFFFF) | 0x66000000);
        }
        if (label != null) {
            g.drawString(font, font.plainSubstrByWidth(label, w - 4), x + 3, y + (h - 8) / 2 + 1, TEXT, false);
        }
    }

    static void icon(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
    }

    static void iconScaled(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    static String clip(Font font, String text, int width) {
        return font.plainSubstrByWidth(text, width);
    }
}
