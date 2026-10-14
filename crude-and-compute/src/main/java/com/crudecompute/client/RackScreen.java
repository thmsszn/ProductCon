package com.crudecompute.client;

import com.crudecompute.datacenter.ServerRackBlockEntity;
import com.crudecompute.datacenter.ServerRackBlockEntity.Slot;
import com.crudecompute.hardware.Catalog;
import com.crudecompute.hardware.HardwareSim;
import com.crudecompute.hardware.HwError;
import com.crudecompute.hardware.Kind;
import com.crudecompute.hardware.Part;
import com.crudecompute.hardware.ServerConfig;
import com.crudecompute.hardware.ServerState;
import com.crudecompute.menu.RackMenu;
import com.crudecompute.util.Lang;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * Rack management UI: the rack view shows every U with its server; clicking a server opens the detail view with the
 * installed parts, the compatible catalog, live statistics and the power mode.
 */
public class RackScreen extends AbstractContainerScreen<RackMenu> {
    private static final int BG = 0xF00E1218;
    private static final int PANEL = 0xFF171D26;
    private static final int PANEL2 = 0xFF1E2632;
    private static final int LINE = 0xFF2B3544;
    private static final int ACCENT = 0xFF38BDF8;
    private static final int TEXT = 0xFFD9E1EC;
    private static final int DIM = 0xFF8593A6;
    private static final int OK = 0xFF4ADE80;
    private static final int WARN = 0xFFFBBF24;
    private static final int BAD = 0xFFF87171;

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Component> tooltip = new ArrayList<>();
    private int selU = -1;
    private Kind tab = Kind.CPU;
    private int scrollInstalled;
    private int lastSeq = -1;
    private Component message = Component.empty();
    private int messageColor = DIM;

    public RackScreen(RackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 400;
        this.imageHeight = 244;
    }

    @Nullable
    private ServerRackBlockEntity rack() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof ServerRackBlockEntity r ? r : null;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void hit(int x, int y, int w, int h, Runnable r) {
        hits.add(new Hit(x, y, w, h, r));
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        hits.clear();
        tooltip.clear();
        pollFeedback();
        int x0 = leftPos;
        int y0 = topPos;
        g.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, BG);
        g.fill(x0, y0, x0 + imageWidth, y0 + 20, PANEL);
        g.fill(x0, y0 + 20, x0 + imageWidth, y0 + 21, ACCENT);
        ServerRackBlockEntity rack = rack();
        if (rack == null) {
            g.drawString(font, "…", x0 + 8, y0 + 8, DIM, false);
            return;
        }
        Slot sel = selU >= 0 ? rack.slotStartingAt(selU) : null;
        if (selU >= 0 && sel == null) {
            selU = -1;
        }
        if (sel == null) {
            drawRackView(g, rack, x0, y0, mx, my);
        } else {
            drawServerView(g, rack, sel, x0, y0, mx, my);
        }
        // feedback line
        g.fill(x0, y0 + imageHeight - 18, x0 + imageWidth, y0 + imageHeight, PANEL);
        g.drawString(font, font.plainSubstrByWidth(message.getString(), imageWidth - 16), x0 + 8, y0 + imageHeight - 13, messageColor, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        if (!tooltip.isEmpty()) {
            g.renderComponentTooltip(font, tooltip, mx, my);
        }
    }

    private void pollFeedback() {
        if (menu.lastSeq() != lastSeq) {
            lastSeq = menu.lastSeq();
            HwError err = menu.lastError();
            if (err != null) {
                message = Component.translatable(err.key(), menu.lastArg());
                messageColor = BAD;
            } else if (lastSeq > 0) {
                message = Component.translatable("hw.gui.done");
                messageColor = OK;
            }
        }
    }

    // ------------------------------------------------------------------ rack view
    private void drawRackView(GuiGraphics g, ServerRackBlockEntity rack, int x0, int y0, int mx, int my) {
        g.drawString(font, Component.translatable("hw.gui.rack_title", rack.usedU(), ServerRackBlockEntity.RACK_U), x0 + 8, y0 + 7, TEXT, false);
        int rowH = 13;
        int top = y0 + 28;
        int left = x0 + 8;
        int width = 252;
        for (int u = ServerRackBlockEntity.RACK_U - 1; u >= 0; u--) {
            int ry = top + (ServerRackBlockEntity.RACK_U - 1 - u) * rowH;
            g.fill(left, ry, left + 20, ry + rowH - 1, PANEL);
            g.drawString(font, String.valueOf(u + 1), left + (u + 1 >= 10 ? 3 : 7), ry + 3, DIM, false);
            Slot covering = rack.slotAt(u);
            if (covering == null) {
                g.fill(left + 22, ry, left + width, ry + rowH - 1, 0xFF121820);
            }
        }
        for (Slot s : rack.getSlots()) {
            int topRow = s.u + s.cfg.sizeU - 1;
            int ry = top + (ServerRackBlockEntity.RACK_U - 1 - topRow) * rowH;
            int h = s.cfg.sizeU * rowH - 1;
            boolean hover = over(mx, my, left + 22, ry, width - 22, h);
            g.fill(left + 22, ry, left + width, ry + h, hover ? 0xFF283448 : PANEL2);
            g.fill(left + 22, ry, left + 24, ry + h, statusColor(s.st.status));
            g.drawString(font, s.cfg.sizeU + "U  " + summary(s.cfg), left + 28, ry + 3, TEXT, false);
            if (h > 20) {
                g.drawString(font, Component.translatable("hw.status." + s.st.status).getString() + "  " + Math.round(s.st.temp) + " °C  "
                        + Lang.num(s.st.inputW / 1000.0) + " kW  " + Lang.num(s.st.compute) + " PF", left + 28, ry + 16, DIM, false);
            }
            int u = s.u;
            hit(left + 22, ry, width - 22 - 36, h, () -> {
                selU = u;
                scrollInstalled = 0;
                send(RackMenu.A_SELECT + u);
            });
            smallButton(g, left + width - 34, ry + 1, "▲", mx, my, () -> send(RackMenu.A_MOVE + u * 10 + 1));
            smallButton(g, left + width - 23, ry + 1, "▼", mx, my, () -> send(RackMenu.A_MOVE + u * 10 + 2));
            smallButton(g, left + width - 12, ry + 1, "✖", mx, my, () -> send(RackMenu.A_EJECT + u));
        }
        // right: rack statistics
        int rx = x0 + 270;
        int w = 122;
        g.fill(rx, top, rx + w, top + 14 * rowH - 1, PANEL);
        int y = top + 6;
        g.drawString(font, Component.translatable("hw.gui.rack_stats"), rx + 6, y, ACCENT, false);
        y += 14;
        y = stat(g, rx + 6, y, "hw.gui.power", Lang.num(rack.getPowerDraw() * HardwareSim.WATTS_PER_FE_TICK / 1000.0) + " kW", TEXT);
        y = stat(g, rx + 6, y, "hw.gui.heat", Lang.num(rack.getHeatW() / 1000.0) + " kW", TEXT);
        y = stat(g, rx + 6, y, "hw.gui.inlet", Lang.num(rack.getInlet()) + " °C", TEXT);
        y = stat(g, rx + 6, y, "hw.gui.max_temp", Lang.num(rack.getTemperature()) + " °C", rack.getTemperature() > 80 ? BAD : TEXT);
        y = stat(g, rx + 6, y, "hw.gui.cooling_load", Math.round(rack.getCoolingLoad() * 100) + " %", rack.getCoolingLoad() > 0.9 ? WARN : TEXT);
        y = stat(g, rx + 6, y, "hw.gui.compute", Lang.num(rack.getCompute()) + " PF", TEXT);
        y = stat(g, rx + 6, y, "hw.gui.buffer", Lang.num(rack.getEnergyStored() / 1000.0) + " / " + Lang.num(rack.getEnergyCapacity() / 1000.0) + " kFE", rack.isPowered() ? TEXT : BAD);
        y = stat(g, rx + 6, y, "hw.gui.status", Component.translatable("hw.status." + rack.getStatus()).getString(), statusColor(rack.getStatus()));
        int by = top + 14 * rowH - 22;
        button(g, rx + 6, by, w - 12, 16, Component.translatable("hw.gui.insert_held").getString(), mx, my, ACCENT, () -> send(RackMenu.A_INSERT_HELD));
    }

    private int stat(GuiGraphics g, int x, int y, String key, String value, int color) {
        g.drawString(font, Component.translatable(key), x, y, DIM, false);
        g.drawString(font, value, x, y + 9, color, false);
        return y + 20;
    }

    private String summary(ServerConfig c) {
        Part mb = c.moboPart();
        if (mb == null) {
            return Component.translatable("hw.gui.empty_chassis").getString();
        }
        return c.cpus.size() + "x CPU  " + c.gpus.size() + "x GPU  " + c.ramGB() + " GB  " + c.storageGB() / 1000 + " TB";
    }

    // ------------------------------------------------------------------ server view
    private void drawServerView(GuiGraphics g, ServerRackBlockEntity rack, Slot slot, int x0, int y0, int mx, int my) {
        ServerConfig cfg = slot.cfg;
        ServerState st = slot.st;
        button(g, x0 + 6, y0 + 3, 52, 14, "‹ " + Component.translatable("hw.gui.back").getString(), mx, my, ACCENT, () -> {
            selU = -1;
            send(RackMenu.A_SELECT + 99);
        });
        g.drawString(font, Component.translatable("hw.gui.server_title", cfg.sizeU, slot.u + 1, "$" + String.format("%,d", cfg.totalPrice())),
                x0 + 64, y0 + 7, TEXT, false);

        int top = y0 + 26;
        int bottom = y0 + imageHeight - 22;
        // --- installed parts (left)
        int lx = x0 + 6;
        int lw = 146;
        g.fill(lx, top, lx + lw, bottom, PANEL);
        g.drawString(font, Component.translatable("hw.gui.installed"), lx + 4, top + 3, ACCENT, false);
        int ry = top + 15 - scrollInstalled;
        g.enableScissor(lx, top + 13, lx + lw, bottom);
        for (Kind kind : Kind.values()) {
            List<Part> parts = cfg.parts(kind);
            Map<String, Integer> grouped = new LinkedHashMap<>();
            for (Part p : parts) {
                grouped.merge(p.id, 1, Integer::sum);
            }
            g.drawString(font, Component.translatable(kind.key()).getString(), lx + 4, ry, DIM, false);
            ry += 10;
            if (grouped.isEmpty()) {
                g.drawString(font, "—", lx + 8, ry, 0xFF4A5568, false);
                ry += 10;
            }
            for (Map.Entry<String, Integer> e : grouped.entrySet()) {
                Part p = Catalog.get(e.getKey());
                int lastIndex = lastIndexOf(cfg.list(kind), p.id, kind);
                boolean hover = over(mx, my, lx, ry - 1, lw, 10);
                String label = (e.getValue() > 1 ? e.getValue() + "× " : "") + p.name;
                g.drawString(font, font.plainSubstrByWidth(label, lw - 34), lx + 8, ry, hover ? ACCENT : TEXT, false);
                if (hover && my >= top + 13 && my < bottom) {
                    tooltip.clear();
                    tooltip.add(Component.literal(p.name).withStyle(net.minecraft.ChatFormatting.AQUA));
                    for (String line : p.specLines()) {
                        tooltip.add(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                }
                if (ry > top + 10 && ry < bottom - 8) {
                    int idx = lastIndex;
                    smallButton(g, lx + lw - 12, ry - 1, "✖", mx, my, () -> send(RackMenu.A_REMOVE_PART + kind.ordinal() * 100 + Math.max(0, idx)));
                    if (kind != Kind.MOTHERBOARD && kind != Kind.COOLER) {
                        smallButton(g, lx + lw - 23, ry - 1, "+", mx, my, () -> send(RackMenu.A_INSTALL + Catalog.indexOf(p)));
                    }
                }
                ry += 10;
            }
            ry += 3;
        }
        g.disableScissor();
        int contentH = ry + scrollInstalled - (top + 15);
        int maxScroll = Math.max(0, contentH - (bottom - top - 17));
        scrollInstalled = Math.max(0, Math.min(maxScroll, scrollInstalled));

        // --- catalog (middle)
        int cx = x0 + 156;
        int cw = 122;
        g.fill(cx, top, cx + cw, bottom, PANEL);
        Kind[] kinds = Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            int tx = cx + 3 + (i % 4) * 29;
            int ty = top + 3 + (i / 4) * 12;
            boolean active = kinds[i] == tab;
            Kind k = kinds[i];
            g.fill(tx, ty, tx + 28, ty + 11, active ? ACCENT : PANEL2);
            String label = Component.translatable(k.key() + ".short").getString();
            g.drawString(font, label, tx + 14 - font.width(label) / 2, ty + 2, active ? 0xFF06121C : TEXT, false);
            hit(tx, ty, 28, 11, () -> tab = k);
        }
        int py = top + 30;
        for (Part p : Catalog.BY_KIND.get(tab)) {
            boolean hover = over(mx, my, cx + 2, py - 1, cw - 4, 13);
            ServerConfig trial = cfg.copy();
            boolean installable = trial.install(p) == null;
            g.fill(cx + 2, py - 1, cx + cw - 2, py + 12, hover ? 0xFF283448 : PANEL2);
            g.drawString(font, font.plainSubstrByWidth(p.name, cw - 40), cx + 5, py + 2, installable ? TEXT : 0xFF5B6678, false);
            String price = "$" + shortPrice(p.price);
            g.drawString(font, price, cx + cw - 5 - font.width(price), py + 2, installable ? OK : 0xFF5B6678, false);
            if (hover) {
                tooltip.clear();
                tooltip.add(Component.literal(p.name).withStyle(net.minecraft.ChatFormatting.AQUA));
                for (String line : p.specLines()) {
                    tooltip.add(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                if (!installable) {
                    var problem = cfg.copy().install(p);
                    if (problem != null) {
                        tooltip.add(Component.translatable(problem.error().key(), problem.arg()).withStyle(net.minecraft.ChatFormatting.RED));
                    }
                }
            }
            int index = Catalog.indexOf(p);
            hit(cx + 2, py - 1, cw - 4, 13, () -> send(RackMenu.A_INSTALL + index));
            py += 14;
        }

        // --- statistics + power mode (right)
        int sx = x0 + 283;
        int sw = 111;
        g.fill(sx, top, sx + sw, bottom, PANEL);
        int y = top + 4;
        g.drawString(font, Component.translatable("hw.status." + st.status), sx + 5, y, statusColor(st.status), false);
        if (st.status == ServerState.NO_BOOT && st.bootReason > 0) {
            HwError why = HwError.fromCode(st.bootReason);
            if (why != null) {
                g.drawString(font, font.plainSubstrByWidth(Component.translatable(why.key()).getString(), sw - 8), sx + 5, y + 10, WARN, false);
            }
        }
        y += 22;
        y = bar(g, sx + 5, y, sw - 10, st.cpuUtil, ACCENT, Component.translatable("hw.gui.cpu").getString() + " " + Math.round(st.cpuUtil * 100) + "%");
        y = bar(g, sx + 5, y, sw - 10, st.gpuUtil, 0xFFA78BFA, Component.translatable("hw.gui.gpu").getString() + " " + Math.round(st.gpuUtil * 100) + "%");
        int ramGB = cfg.ramGB();
        y = bar(g, sx + 5, y, sw - 10, st.ramUtil, 0xFF34D399, "RAM " + Math.round(st.ramUtil * ramGB) + "/" + ramGB + " GB");
        float stGB = Math.max(1, cfg.storageGB());
        y = bar(g, sx + 5, y, sw - 10, st.storageUsedGB / stGB, 0xFFF59E0B, Component.translatable("hw.gui.disk").getString() + " " + Lang.num(st.storageUsedGB / 1000.0) + "/" + Lang.num(stGB / 1000.0) + " TB");
        y = bar(g, sx + 5, y, sw - 10, Math.min(1f, st.temp / 105f), st.temp > 85 ? BAD : st.temp > 70 ? WARN : OK, Math.round(st.temp) + " °C");
        y = bar(g, sx + 5, y, sw - 10, cfg.psuRatedW() > 0 ? Math.min(1f, st.inputW / cfg.psuRatedW()) : 0f, WARN,
                Lang.num(st.inputW) + " W / " + cfg.psuRatedW() + " W");
        g.drawString(font, Component.translatable("hw.gui.fan", HardwareSim.fanRpm(cfg, st)), sx + 5, y, DIM, false);
        y += 10;
        g.drawString(font, Component.translatable("hw.gui.compute").getString() + " " + Lang.num(st.compute) + " PF", sx + 5, y, TEXT, false);
        // mode buttons
        int by = bottom - 18;
        for (int m = 0; m < 4; m++) {
            int bx = sx + 4 + m * 26;
            boolean active = cfg.mode == m;
            int mode = m;
            g.fill(bx, by, bx + 25, by + 14, active ? ACCENT : PANEL2);
            String label = Component.translatable("hw.gui.mode." + m).getString();
            g.drawString(font, label, bx + 12 - font.width(label) / 2, by + 3, active ? 0xFF06121C : TEXT, false);
            hit(bx, by, 25, 14, () -> send(RackMenu.A_MODE + mode));
        }
    }

    private static int lastIndexOf(List<String> list, String id, Kind kind) {
        if (kind == Kind.MOTHERBOARD || kind == Kind.COOLER) {
            return 0;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equals(id)) {
                return i;
            }
        }
        return 0;
    }

    private static String shortPrice(int price) {
        return price >= 10000 ? (price / 1000) + "k" : String.format("%,d", price);
    }

    private static int statusColor(int status) {
        return switch (status) {
            case ServerState.ONLINE -> OK;
            case ServerState.THROTTLED, ServerState.NO_BOOT -> WARN;
            case ServerState.OVERHEATED, ServerState.NO_POWER -> BAD;
            default -> 0xFF64748B;
        };
    }

    // ------------------------------------------------------------------ widgets
    private int bar(GuiGraphics g, int x, int y, int w, float frac, int color, String label) {
        g.fill(x, y, x + w, y + 9, 0xFF0C1016);
        g.fill(x, y, x + Math.round(w * Math.max(0f, Math.min(1f, frac))), y + 9, (color & 0x00FFFFFF) | 0x99000000);
        g.drawString(font, font.plainSubstrByWidth(label, w - 2), x + 2, y + 1, TEXT, false);
        return y + 12;
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, int color, Runnable action) {
        boolean hover = over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? color : PANEL2);
        g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + (h - 8) / 2 + 1, hover ? 0xFF06121C : TEXT, false);
        hit(x, y, w, h, action);
    }

    private void smallButton(GuiGraphics g, int x, int y, String label, int mx, int my, Runnable action) {
        boolean hover = over(mx, my, x, y, 10, 10);
        g.fill(x, y, x + 10, y + 10, hover ? ACCENT : 0xFF2A3342);
        g.drawString(font, label, x + 5 - font.width(label) / 2, y + 1, hover ? 0xFF06121C : TEXT, false);
        hit(x, y, 10, 10, action);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                    h.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (selU >= 0 && mx < leftPos + 152) {
            scrollInstalled -= (int) Math.signum(scrollY) * 20;
            scrollInstalled = Math.max(0, scrollInstalled);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
}
