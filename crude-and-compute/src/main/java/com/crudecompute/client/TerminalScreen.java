package com.crudecompute.client;

import com.crudecompute.datacenter.AITerminalBlockEntity;
import com.crudecompute.menu.TerminalMenu;
import com.crudecompute.util.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/** Datacenter dashboard: live energy balance, thermal overview, compute and model training with real-time charts. */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int BG = 0xF00E1218;
    private static final int PANEL = 0xFF171D26;
    private static final int LINE = 0xFF2B3544;
    private static final int ACCENT = 0xFF38BDF8;
    private static final int TEXT = 0xFFD9E1EC;
    private static final int DIM = 0xFF8593A6;
    private static final int OK = 0xFF4ADE80;
    private static final int WARN = 0xFFFBBF24;
    private static final int BAD = 0xFFF87171;

    private int ejectX;
    private int ejectY;

    public TerminalScreen(TerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 400;
        this.imageHeight = 244;
    }

    @Nullable
    private AITerminalBlockEntity terminal() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof AITerminalBlockEntity t ? t : null;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x0 = leftPos;
        int y0 = topPos;
        UiKit.window(g, x0, y0, imageWidth, imageHeight, 24);
        g.drawString(font, Component.translatable("dash.title"), x0 + 10, y0 + 8, TEXT, false);
        AITerminalBlockEntity t = terminal();
        if (t == null) {
            return;
        }
        float balance = t.getSolarKw() - t.getConsumptionKw();
        boolean hasBattery = t.getBatteryCapacity() > 0;
        float soc = hasBattery ? t.getBatteryStored() / (float) t.getBatteryCapacity() : 0f;

        // KPI cards
        int cw = 94;
        int cy = y0 + 28;
        card(g, x0 + 6, cy, cw, "dash.solar", Lang.num(t.getSolarKw()) + " kW", t.getPanels() + " " + Component.translatable("dash.panels").getString(), OK);
        card(g, x0 + 6 + (cw + 4), cy, cw, "dash.consumption", Lang.num(t.getConsumptionKw()) + " kW",
                Component.translatable("dash.cooling_part", Lang.num(t.getCoolingKw())).getString(), WARN);
        card(g, x0 + 6 + 2 * (cw + 4), cy, cw, "dash.balance", (balance >= 0 ? "+" : "") + Lang.num(balance) + " kW",
                Component.translatable(balance >= 0 ? "dash.surplus" : "dash.deficit").getString(), balance >= 0 ? OK : BAD);
        card(g, x0 + 6 + 3 * (cw + 4), cy, cw, "dash.battery", hasBattery ? Math.round(soc * 100) + " %" : "—",
                hasBattery ? Lang.num(t.getBatteryStored() / 1000.0) + " kFE" : Component.translatable("dash.no_battery").getString(),
                soc < 0.15f && hasBattery ? BAD : ACCENT);

        // charts
        int chartY = cy + 46;
        chart(g, x0 + 6, chartY, 194, 70, "dash.chart_power", t.getHistProd(), OK, t.getHistCons(), BAD, "kW", 10f);
        chart(g, x0 + 204, chartY, 190, 70, "dash.chart_temp", t.getHistTemp(), WARN, null, 0, "°C", 10f);

        // bottom: datacenter + training
        int by = chartY + 78;
        UiKit.panel(g, x0 + 6, by, 194, 62);
        g.drawString(font, Component.translatable("dash.datacenter"), x0 + 11, by + 4, ACCENT, false);
        g.drawString(font, Component.translatable("dash.racks", t.getRacks(), t.getGpus()), x0 + 11, by + 16, TEXT, false);
        g.drawString(font, Component.translatable("dash.compute", Lang.num(t.getComputeValue())), x0 + 11, by + 26, TEXT, false);
        g.drawString(font, Component.translatable("dash.pue", t.getPue() > 0 ? String.format(java.util.Locale.ROOT, "%.2f", t.getPue()) : "—"), x0 + 11, by + 36, TEXT, false);
        g.drawString(font, Component.translatable("dash.temps", Lang.num(t.getMaxTemp()), Lang.num(t.getInletAvg())), x0 + 11, by + 46, t.getMaxTemp() > 80 ? BAD : TEXT, false);

        UiKit.panel(g, x0 + 204, by, 190, 62);
        g.drawString(font, Component.translatable("dash.training"), x0 + 209, by + 4, ACCENT, false);
        int percent = t.getProgressPercent();
        String state = percent < 0 ? Component.translatable("dash.no_drive").getString()
                : percent >= 100 ? Component.translatable("dash.done").getString() : Component.translatable("dash.running", percent).getString();
        g.drawString(font, state, x0 + 209, by + 16, percent >= 100 ? OK : TEXT, false);
        g.fill(x0 + 209, by + 30, x0 + 389, by + 38, 0xFF0C1016);
        if (percent > 0) {
            g.fill(x0 + 209, by + 30, x0 + 209 + 180 * Math.min(100, percent) / 100, by + 38, percent >= 100 ? OK : ACCENT);
        }
        ejectX = x0 + 209;
        ejectY = by + 43;
        boolean can = percent >= 0;
        boolean hover = can && mx >= ejectX && mx < ejectX + 90 && my >= ejectY && my < ejectY + 14;
        g.fill(ejectX, ejectY, ejectX + 90, ejectY + 14, !can ? 0xFF1A212B : hover ? ACCENT : 0xFF2A3342);
        String label = Component.translatable("dash.eject").getString();
        g.drawString(font, label, ejectX + 45 - font.width(label) / 2, ejectY + 3, hover ? 0xFF06121C : can ? TEXT : 0xFF5B6678, false);
    }

    private void card(GuiGraphics g, int x, int y, int w, String titleKey, String value, String sub, int color) {
        UiKit.panel(g, x, y, w, 42);
        g.fill(x + 1, y + 1, x + 3, y + 41, color);
        g.drawString(font, Component.translatable(titleKey), x + 7, y + 4, DIM, false);
        g.drawString(font, value, x + 7, y + 16, color, false);
        g.drawString(font, font.plainSubstrByWidth(sub, w - 10), x + 7, y + 30, DIM, false);
    }

    /** Line chart of up to two series (values in tenths). */
    private void chart(GuiGraphics g, int x, int y, int w, int h, String titleKey, int[] a, int colorA, int[] b, int colorB, String unit, float scale) {
        UiKit.panel(g, x, y, w, h);
        g.drawString(font, Component.translatable(titleKey), x + 5, y + 3, ACCENT, false);
        int px = x + 24;
        int py = y + 14;
        int pw = w - 30;
        int ph = h - 22;
        int max = 1;
        for (int v : a) {
            max = Math.max(max, v);
        }
        if (b != null) {
            for (int v : b) {
                max = Math.max(max, v);
            }
        }
        max = (int) (Math.ceil(max / scale / 5.0) * 5 * scale);
        max = Math.max(max, (int) (5 * scale));
        for (int i = 0; i <= 2; i++) {
            int gy = py + ph - ph * i / 2;
            g.fill(px, gy, px + pw, gy + 1, LINE);
            String label = Lang.num(max / scale * i / 2.0);
            g.drawString(font, label, px - 3 - font.width(label), gy - 4, DIM, false);
        }
        g.drawString(font, unit, x + w - 4 - font.width(unit), y + 3, DIM, false);
        series(g, px, py, pw, ph, a, max, colorA);
        if (b != null) {
            series(g, px, py, pw, ph, b, max, colorB);
        }
    }

    private void series(GuiGraphics g, int px, int py, int pw, int ph, int[] data, int max, int color) {
        int n = data.length;
        int prevY = -1;
        for (int i = 0; i < n; i++) {
            int sx = px + i * pw / n;
            int ex = px + (i + 1) * pw / n;
            int yy = py + ph - Math.round(ph * Math.min(data[i], max) / (float) max);
            if (prevY >= 0) {
                g.fill(sx, Math.min(prevY, yy), sx + 1, Math.max(prevY, yy) + 1, color);
            }
            g.fill(sx, yy, Math.max(sx + 1, ex), yy + 1, color);
            prevY = yy;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        AITerminalBlockEntity t = terminal();
        if (button == 0 && t != null && t.getProgressPercent() >= 0 && mx >= ejectX && mx < ejectX + 90 && my >= ejectY && my < ejectY + 14) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TerminalMenu.A_EJECT);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
