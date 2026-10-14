package com.crudecompute.client;

import static com.crudecompute.client.UiKit.ACCENT;
import static com.crudecompute.client.UiKit.BAD;
import static com.crudecompute.client.UiKit.DARK_TEXT;
import static com.crudecompute.client.UiKit.DIM;
import static com.crudecompute.client.UiKit.FAINT;
import static com.crudecompute.client.UiKit.OK;
import static com.crudecompute.client.UiKit.PANEL2;
import static com.crudecompute.client.UiKit.PANEL3;
import static com.crudecompute.client.UiKit.TEXT;
import static com.crudecompute.client.UiKit.WARN;
import static com.crudecompute.client.UiKit.over;

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
import com.crudecompute.registry.ModItems;
import com.crudecompute.util.Lang;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Rack management. Three views: the rack (every U with a drawn server faceplate), the power view (supply, load per
 * server, efficiency, history) and the server detail with installed parts, a searchable catalog of the parts you
 * carry (or all of them in creative), live statistics, power mode and load-shedding priority.
 */
public class RackScreen extends AbstractContainerScreen<RackMenu> {
    private enum Sort { PRICE, PERFORMANCE, NAME }

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private static final int HISTORY = 60;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Component> tooltip = new ArrayList<>();
    private final Map<String, ItemStack> stacks = new LinkedHashMap<>();
    private final int[] histDraw = new int[HISTORY];
    private final int[] histHeat = new int[HISTORY];
    private EditBox search;
    private boolean energyTab;
    private int selU = -1;
    private Kind tab = Kind.CPU;
    private Sort sort = Sort.PRICE;
    private boolean onlyCompatible;
    private int scrollInstalled;
    private int scrollCatalog;
    private int lastSeq = -1;
    private Component message = Component.empty();
    private int messageColor = DIM;
    private int sampleTick;

    public RackScreen(RackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 430;
        this.imageHeight = 258;
    }

    @Override
    protected void init() {
        super.init();
        String keep = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + 168, topPos + 54, 148, 12, Component.translatable("hw.gui.search"));
        search.setHint(Component.translatable("hw.gui.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setMaxLength(24);
        search.setValue(keep);
        search.setResponder(s -> scrollCatalog = 0);
        search.visible = false;
        addRenderableWidget(search);
    }

    /** Showcase automation: jump straight to a server's detail page. */
    void showcaseSelect(int u) {
        energyTab = false;
        selU = u;
        send(RackMenu.A_SELECT + u);
    }

    /** Showcase automation: show the power overview. */
    void showcaseEnergy() {
        selU = -1;
        energyTab = true;
        send(RackMenu.A_SELECT + 99);
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

    private ItemStack stackOf(Part p) {
        return stacks.computeIfAbsent(p.id, id -> new ItemStack(ModItems.partItem(p)));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ServerRackBlockEntity rack = rack();
        if (rack != null && ++sampleTick % 20 == 0) {
            System.arraycopy(histDraw, 1, histDraw, 0, HISTORY - 1);
            System.arraycopy(histHeat, 1, histHeat, 0, HISTORY - 1);
            histDraw[HISTORY - 1] = Math.round(rack.getPowerDraw() * (float) HardwareSim.WATTS_PER_FE_TICK);
            histHeat[HISTORY - 1] = Math.round(rack.getHeatW());
        }
    }

    // ------------------------------------------------------------------ frame
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        hits.clear();
        tooltip.clear();
        pollFeedback();
        int x0 = leftPos;
        int y0 = topPos;
        UiKit.window(g, x0, y0, imageWidth, imageHeight, 24);
        ServerRackBlockEntity rack = rack();
        search.visible = false;
        if (rack == null) {
            g.drawString(font, "…", x0 + 8, y0 + 9, DIM, false);
            return;
        }
        Slot sel = selU >= 0 ? rack.slotStartingAt(selU) : null;
        if (selU >= 0 && sel == null) {
            selU = -1;
        }
        if (sel == null) {
            drawHeader(g, rack, x0, y0, mx, my);
            if (energyTab) {
                drawEnergyView(g, rack, x0, y0, mx, my);
            } else {
                drawRackView(g, rack, x0, y0, mx, my);
            }
        } else {
            drawServerView(g, rack, sel, x0, y0, mx, my);
        }
        g.fill(x0, y0 + imageHeight - 18, x0 + imageWidth, y0 + imageHeight, 0xFF10151C);
        g.fill(x0, y0 + imageHeight - 18, x0 + imageWidth, y0 + imageHeight - 17, UiKit.BORDER);
        g.drawString(font, UiKit.clip(font, message.getString(), imageWidth - 16), x0 + 8, y0 + imageHeight - 13, messageColor, false);
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

    private void drawHeader(GuiGraphics g, ServerRackBlockEntity rack, int x0, int y0, int mx, int my) {
        g.drawString(font, Component.translatable("hw.gui.title"), x0 + 10, y0 + 8, TEXT, false);
        tabButton(g, x0 + 92, y0 + 5, 52, Component.translatable("hw.gui.tab.rack").getString(), !energyTab, mx, my, () -> energyTab = false);
        tabButton(g, x0 + 147, y0 + 5, 60, Component.translatable("hw.gui.tab.energy").getString(), energyTab, mx, my, () -> energyTab = true);
        int px = x0 + imageWidth - 8;
        String status = Component.translatable("hw.status." + rack.getStatus()).getString();
        int w = font.width(status) + 10;
        UiKit.pill(g, font, px - w, y0 + 6, status, statusColor(rack.getStatus()));
        String kw = Lang.num(rack.getPowerDraw() * HardwareSim.WATTS_PER_FE_TICK / 1000.0) + " kW";
        int w2 = font.width(kw) + 10;
        UiKit.pill(g, font, px - w - 4 - w2, y0 + 6, kw, WARN);
    }

    private void tabButton(GuiGraphics g, int x, int y, int w, String label, boolean active, int mx, int my, Runnable action) {
        boolean hover = over(mx, my, x, y, w, 15);
        UiKit.button(g, font, x, y, w, 15, label, hover, active, true);
        hit(x, y, w, 15, action);
    }

    // ------------------------------------------------------------------ rack view
    private void drawRackView(GuiGraphics g, ServerRackBlockEntity rack, int x0, int y0, int mx, int my) {
        int rowH = 14;
        int top = y0 + 32;
        int left = x0 + 8;
        int width = 282;
        UiKit.panel(g, left - 3, top - 3, width + 6, ServerRackBlockEntity.RACK_U * rowH + 5, 0xFF0F141B);
        for (int u = ServerRackBlockEntity.RACK_U - 1; u >= 0; u--) {
            int ry = top + (ServerRackBlockEntity.RACK_U - 1 - u) * rowH;
            g.fill(left, ry, left + 18, ry + rowH - 1, 0xFF1A212B);
            String num = String.valueOf(u + 1);
            g.drawString(font, num, left + 9 - font.width(num) / 2, ry + 3, FAINT, false);
            if (rack.slotAt(u) == null) {
                g.fill(left + 20, ry, left + width, ry + rowH - 1, 0xFF0C1116);
                for (int k = 0; k < 6; k++) {
                    g.fill(left + 24 + k * 44, ry + 5, left + 38 + k * 44, ry + 6, 0xFF151B24);
                }
            }
        }
        for (Slot s : rack.getSlots()) {
            int topRow = s.u + s.cfg.sizeU - 1;
            int ry = top + (ServerRackBlockEntity.RACK_U - 1 - topRow) * rowH;
            int h = s.cfg.sizeU * rowH - 1;
            int fx = left + 20;
            int fw = width - 20;
            boolean hover = over(mx, my, fx, ry, fw - 38, h);
            drawFaceplate(g, s, fx, ry, fw, h, hover);
            int u = s.u;
            hit(fx, ry, fw - 38, h, () -> {
                selU = u;
                scrollInstalled = 0;
                scrollCatalog = 0;
                send(RackMenu.A_SELECT + u);
            });
            if (hover || over(mx, my, fx + fw - 38, ry, 38, h)) {
                int by = ry + Math.max(1, (h - 11) / 2);
                smallButton(g, fx + fw - 36, by, "▲", mx, my, () -> send(RackMenu.A_MOVE + u * 10 + 1));
                smallButton(g, fx + fw - 24, by, "▼", mx, my, () -> send(RackMenu.A_MOVE + u * 10 + 2));
                smallButton(g, fx + fw - 12, by, "✖", mx, my, () -> send(RackMenu.A_EJECT + u));
            }
            if (hover) {
                tooltip.clear();
                tooltip.add(Component.literal(s.cfg.sizeU + "U – " + summary(s.cfg)).withStyle(ChatFormatting.AQUA));
                tooltip.add(Component.translatable("hw.status." + s.st.status).withStyle(Style.of(statusColor(s.st.status))));
                tooltip.add(Component.literal(Math.round(s.st.temp) + " °C | " + Lang.num(s.st.inputW) + " W | " + Lang.num(s.st.compute) + " PF").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("hw.gui.click_open").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        // right column
        int rx = x0 + 298;
        int w = 124;
        int h = ServerRackBlockEntity.RACK_U * rowH + 5;
        UiKit.panel(g, rx, top - 3, w, h);
        int y = top + 3;
        g.drawString(font, Component.translatable("hw.gui.rack_stats", rack.usedU(), ServerRackBlockEntity.RACK_U), rx + 6, y, ACCENT, false);
        y += 13;
        UiKit.bar(g, font, rx + 6, y, w - 12, 11, rack.usedU() / (float) ServerRackBlockEntity.RACK_U, ACCENT, rack.usedU() + " / " + ServerRackBlockEntity.RACK_U + " U");
        y += 16;
        y = stat(g, rx + 6, y, "hw.gui.power", Lang.num(rack.getPowerDraw() * HardwareSim.WATTS_PER_FE_TICK / 1000.0) + " kW", TEXT);
        y = stat(g, rx + 6, y, "hw.gui.heat", Lang.num(rack.getHeatW() / 1000.0) + " kW", TEXT);
        y = stat(g, rx + 6, y, "hw.gui.inlet", Lang.num(rack.getInlet()) + " °C", TEXT);
        y = stat(g, rx + 6, y, "hw.gui.max_temp", Lang.num(rack.getTemperature()) + " °C", rack.getTemperature() > 80 ? BAD : TEXT);
        y = stat(g, rx + 6, y, "hw.gui.compute", Lang.num(rack.getCompute()) + " PF", OK);
        float fill = rack.getEnergyStored() / (float) Math.max(1, rack.getEnergyCapacity());
        g.drawString(font, Component.translatable("hw.gui.buffer"), rx + 6, y, DIM, false);
        UiKit.bar(g, font, rx + 6, y + 10, w - 12, 11, fill, fill < 0.15f ? BAD : OK, Math.round(fill * 100) + " %");
        int by = top - 3 + h - 20;
        boolean hover = over(mx, my, rx + 6, by, w - 12, 15);
        UiKit.button(g, font, rx + 6, by, w - 12, 15, Component.translatable("hw.gui.insert_held").getString(), hover, false, true);
        hit(rx + 6, by, w - 12, 15, () -> send(RackMenu.A_INSERT_HELD));
        if (rack.getShedCount() > 0) {
            g.drawString(font, UiKit.clip(font, Component.translatable("hw.gui.shed", rack.getShedCount()).getString(), w - 12), rx + 6, by - 11, BAD, false);
        }
    }

    /** A server front panel: ears with status LED, vents, drive bays, PSU slots and a short label. */
    private void drawFaceplate(GuiGraphics g, Slot s, int x, int y, int w, int h, boolean hover) {
        int base = hover ? 0xFF2C3646 : 0xFF222A37;
        g.fill(x, y, x + w, y + h, 0xFF05080C);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFF323E50 : base, hover ? 0xFF222B39 : 0xFF181E28);
        g.fill(x + 1, y + 1, x + 4, y + h - 1, statusColor(s.st.status));
        // ears
        g.fill(x + 4, y + 1, x + 8, y + h - 1, 0xFF3A4556);
        // vents
        int vx = x + w - 112;
        for (int k = 0; k < 8; k++) {
            g.fill(vx + k * 3, y + 3, vx + k * 3 + 1, y + h - 3, 0xFF0D1218);
        }
        // drive bays
        int bays = Math.min(s.cfg.sizeU == 1 ? 4 : s.cfg.sizeU == 2 ? 8 : 12, s.cfg.storage.size() + 1);
        long t = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        for (int k = 0; k < bays; k++) {
            int bx = vx + 30 + (k % 6) * 12;
            int by = y + 3 + (k / 6) * 5;
            if (by + 4 > y + h - 2) {
                break;
            }
            g.fill(bx, by, bx + 10, by + 3, 0xFF0D1218);
            boolean active = k < s.cfg.storage.size() && s.st.status == ServerState.ONLINE && ((t / 3 + k * 7) % 5) < 2;
            g.fill(bx + 1, by + 1, bx + 3, by + 2, k < s.cfg.storage.size() ? (active ? 0xFF46C8FF : 0xFF1C4A60) : 0xFF22262E);
        }
        // GPU marker
        if (!s.cfg.gpus.isEmpty()) {
            g.fill(vx + 106 - 14, y + 3, vx + 106, y + 6, 0xFF76B900);
        }
        String label = s.cfg.sizeU + "U " + summary(s.cfg);
        g.drawString(font, UiKit.clip(font, label, vx - x - 14), x + 11, y + 3, TEXT, false);
        if (h > 20) {
            String sub = Component.translatable("hw.status." + s.st.status).getString() + "  " + Math.round(s.st.temp) + " °C  " + Lang.num(s.st.inputW) + " W";
            g.drawString(font, UiKit.clip(font, sub, vx - x - 14), x + 11, y + 15, statusColor(s.st.status), false);
        }
        if (h > 34) {
            UiKit.bar(g, font, x + 11, y + h - 12, vx - x - 26, 7, Math.min(1f, s.st.temp / 105f), s.st.temp > 85 ? BAD : s.st.temp > 70 ? WARN : OK, null);
        }
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
        String gpu = c.gpus.isEmpty() ? "" : c.gpus.size() + "x GPU  ";
        return c.cpus.size() + "x CPU  " + gpu + c.ramGB() + " GB";
    }

    // ------------------------------------------------------------------ energy view
    private void drawEnergyView(GuiGraphics g, ServerRackBlockEntity rack, int x0, int y0, int mx, int my) {
        int top = y0 + 32;
        int left = x0 + 8;
        double totalW = rack.getPowerDraw() * HardwareSim.WATTS_PER_FE_TICK;
        // left: draw per server
        UiKit.panel(g, left, top, 250, 200);
        g.drawString(font, Component.translatable("hw.gui.draw_per_server"), left + 7, top + 6, ACCENT, false);
        List<Slot> order = new ArrayList<>(rack.getSlots());
        order.sort(Comparator.comparingDouble((Slot s) -> -s.st.inputW));
        double maxW = 1;
        for (Slot s : order) {
            maxW = Math.max(maxW, s.st.inputW);
        }
        int y = top + 20;
        for (Slot s : order) {
            if (y > top + 186) {
                break;
            }
            String name = "U" + (s.u + 1) + " " + s.cfg.sizeU + "U";
            g.drawString(font, name, left + 7, y + 1, DIM, false);
            UiKit.bar(g, font, left + 52, y, 150, 10, (float) (s.st.inputW / maxW), statusColor(s.st.status), Lang.num(s.st.inputW) + " W");
            String prio = Component.translatable("hw.gui.prio." + s.cfg.priority).getString();
            g.drawString(font, prio, left + 208, y + 1, s.cfg.priority == 0 ? OK : s.cfg.priority == 2 ? WARN : DIM, false);
            if (!s.powered) {
                g.drawString(font, "⚠", left + 240, y + 1, BAD, false);
            }
            y += 14;
        }
        if (order.isEmpty()) {
            g.drawString(font, Component.translatable("hw.gui.no_servers"), left + 7, top + 24, FAINT, false);
        }
        // right: supply
        int rx = x0 + 264;
        int rw = 158;
        UiKit.panel(g, rx, top, rw, 98);
        g.drawString(font, Component.translatable("hw.gui.supply"), rx + 7, top + 6, ACCENT, false);
        float fill = rack.getEnergyStored() / (float) Math.max(1, rack.getEnergyCapacity());
        g.drawString(font, Component.translatable("hw.gui.buffer"), rx + 7, top + 19, DIM, false);
        UiKit.bar(g, font, rx + 7, top + 29, rw - 14, 11, fill, fill < 0.15f ? BAD : OK,
                Lang.num(rack.getEnergyStored() / 1000.0) + " / " + Lang.num(rack.getEnergyCapacity() / 1000.0) + " kFE");
        String runtime = totalW <= 0 ? "∞" : formatSeconds(rack.getEnergyStored() / (totalW / HardwareSim.WATTS_PER_FE_TICK) / 20.0);
        g.drawString(font, Component.translatable("hw.gui.runtime", runtime), rx + 7, top + 45, TEXT, false);
        g.drawString(font, Component.translatable("hw.gui.power").getString() + ": " + Lang.num(totalW / 1000.0) + " kW", rx + 7, top + 58, TEXT, false);
        g.drawString(font, Component.translatable("hw.gui.psu_loss").getString() + ": " + Lang.num(rack.getPsuLossW()) + " W", rx + 7, top + 70,
                rack.getPsuLossW() > totalW * 0.1 ? WARN : TEXT, false);
        double eff = totalW > 0 ? (totalW - rack.getPsuLossW()) / totalW : 0;
        g.drawString(font, Component.translatable("hw.gui.efficiency").getString() + ": " + Math.round(eff * 100) + " %", rx + 7, top + 82, eff < 0.88 && totalW > 0 ? WARN : TEXT, false);
        if (rack.getShedCount() > 0) {
            g.drawString(font, UiKit.clip(font, Component.translatable("hw.gui.shed", rack.getShedCount()).getString(), rw - 14), rx + 7, top + 94, BAD, false);
        }
        // history
        UiKit.panel(g, rx, top + 102, rw, 98);
        g.drawString(font, Component.translatable("hw.gui.history"), rx + 7, top + 108, ACCENT, false);
        g.fill(rx + 84, top + 110, rx + 90, top + 114, OK);
        g.fill(rx + 120, top + 110, rx + 126, top + 114, WARN);
        int px = rx + 8;
        int py = top + 124;
        int pw = rw - 16;
        int ph = 66;
        int max = 1000;
        for (int i = 0; i < HISTORY; i++) {
            max = Math.max(max, Math.max(histDraw[i], histHeat[i]));
        }
        for (int i = 0; i <= 2; i++) {
            g.fill(px, py + ph - ph * i / 2, px + pw, py + ph - ph * i / 2 + 1, UiKit.BORDER);
        }
        g.drawString(font, Lang.num(max / 1000.0) + " kW", px + 2, py - 1, FAINT, false);
        series(g, px, py, pw, ph, histHeat, max, WARN);
        series(g, px, py, pw, ph, histDraw, max, OK);
    }

    private static String formatSeconds(double s) {
        if (s > 5999) {
            return ">99 min";
        }
        int sec = (int) s;
        return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }

    private void series(GuiGraphics g, int px, int py, int pw, int ph, int[] data, int max, int color) {
        int prev = -1;
        for (int i = 0; i < data.length; i++) {
            int sx = px + i * pw / data.length;
            int ex = px + (i + 1) * pw / data.length;
            int yy = py + ph - Math.round(ph * Math.min(data[i], max) / (float) max);
            if (prev >= 0) {
                g.fill(sx, Math.min(prev, yy), sx + 1, Math.max(prev, yy) + 1, color);
            }
            g.fill(sx, yy, Math.max(sx + 1, ex), yy + 1, color);
            prev = yy;
        }
    }

    // ------------------------------------------------------------------ server view
    private static String capacity(Kind kind, ServerConfig c) {
        Part mb = c.moboPart();
        if (mb == null && kind != Kind.PSU && kind != Kind.COOLER) {
            return "";
        }
        return switch (kind) {
            case CPU -> c.cpus.size() + "/" + mb.cpuSockets;
            case RAM -> c.rams.size() + "/" + mb.ramSlots;
            case GPU, NETWORK -> (c.gpus.size() + c.nics.size()) + "/" + mb.pcie + " PCIe";
            case STORAGE -> c.storage.size() + " (M.2 " + mb.m2 + " · " + c.sataBays() + " bay)";
            case PSU -> c.psus.size() + "/2";
            default -> "";
        };
    }

    private void drawServerView(GuiGraphics g, ServerRackBlockEntity rack, Slot slot, int x0, int y0, int mx, int my) {
        ServerConfig cfg = slot.cfg;
        ServerState st = slot.st;
        boolean back = over(mx, my, x0 + 6, y0 + 5, 54, 15);
        UiKit.button(g, font, x0 + 6, y0 + 5, 54, 15, "‹ " + Component.translatable("hw.gui.back").getString(), back, false, true);
        hit(x0 + 6, y0 + 5, 54, 15, () -> {
            selU = -1;
            send(RackMenu.A_SELECT + 99);
        });
        g.drawString(font, Component.translatable("hw.gui.server_title", cfg.sizeU, slot.u + 1), x0 + 68, y0 + 8, TEXT, false);
        String price = "$" + String.format(Locale.ROOT, "%,d", cfg.totalPrice());
        g.drawString(font, price, x0 + imageWidth - 10 - font.width(price), y0 + 8, 0xFF4ADE80, false);
        String status = Component.translatable("hw.status." + st.status).getString();
        UiKit.pill(g, font, x0 + imageWidth - 14 - font.width(price) - font.width(status) - 10, y0 + 6, status, statusColor(st.status));

        int top = y0 + 30;
        int bottom = y0 + imageHeight - 22;
        drawInstalled(g, cfg, x0 + 6, top, 154, bottom, mx, my);
        drawCatalog(g, cfg, x0 + 164, top, 160, bottom, mx, my);
        drawStats(g, cfg, st, x0 + 328, top, 96, bottom, mx, my);
    }

    private void drawInstalled(GuiGraphics g, ServerConfig cfg, int lx, int top, int lw, int bottom, int mx, int my) {
        UiKit.panel(g, lx, top, lw, bottom - top);
        g.drawString(font, Component.translatable("hw.gui.installed"), lx + 6, top + 4, ACCENT, false);
        int ry = top + 17 - scrollInstalled;
        g.enableScissor(lx + 1, top + 15, lx + lw - 1, bottom - 1);
        for (Kind kind : Kind.values()) {
            List<Part> parts = cfg.parts(kind);
            Map<String, Integer> grouped = new LinkedHashMap<>();
            for (Part p : parts) {
                grouped.merge(p.id, 1, Integer::sum);
            }
            String cap = capacity(kind, cfg);
            g.drawString(font, UiKit.clip(font, Component.translatable(kind.key()).getString(), lw - 16 - font.width(cap)), lx + 6, ry, DIM, false);
            g.drawString(font, cap, lx + lw - 6 - font.width(cap), ry, FAINT, false);
            ry += 10;
            if (grouped.isEmpty()) {
                g.drawString(font, "—", lx + 10, ry + 2, FAINT, false);
                ry += 12;
            }
            for (Map.Entry<String, Integer> e : grouped.entrySet()) {
                Part p = Catalog.get(e.getKey());
                int idx = lastIndexOf(cfg.list(kind), p.id, kind);
                boolean visible = ry > top + 8 && ry < bottom - 16;
                boolean hover = visible && over(mx, my, lx + 2, ry, lw - 4, 17);
                if (hover) {
                    g.fill(lx + 2, ry, lx + lw - 2, ry + 17, PANEL2);
                    tooltip.clear();
                    partTooltip(p, null, false);
                }
                UiKit.icon(g, stackOf(p), lx + 4, ry);
                String label = (e.getValue() > 1 ? e.getValue() + "× " : "") + p.name;
                g.drawString(font, UiKit.clip(font, label, lw - 56), lx + 23, ry + 5, hover ? ACCENT : TEXT, false);
                if (visible) {
                    smallButton(g, lx + lw - 13, ry + 3, "✖", mx, my, () -> send(RackMenu.A_REMOVE_PART + kind.ordinal() * 100 + Math.max(0, idx)));
                    if (kind != Kind.MOTHERBOARD && kind != Kind.COOLER) {
                        smallButton(g, lx + lw - 25, ry + 3, "+", mx, my, () -> send(RackMenu.A_INSTALL + Catalog.indexOf(p)));
                    }
                }
                ry += 18;
            }
            ry += 4;
        }
        g.disableScissor();
        int contentH = ry + scrollInstalled - (top + 17);
        scrollInstalled = Math.max(0, Math.min(Math.max(0, contentH - (bottom - top - 20)), scrollInstalled));
    }

    private List<Part> filteredCatalog(ServerConfig cfg) {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Part> list = new ArrayList<>();
        for (Part p : Catalog.BY_KIND.get(tab)) {
            if (!q.isEmpty() && !p.name.toLowerCase(Locale.ROOT).contains(q) && !String.join(" ", p.specLines()).toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            if (onlyCompatible && cfg.copy().install(p) != null) {
                continue;
            }
            list.add(p);
        }
        Comparator<Part> cmp = switch (sort) {
            case PRICE -> Comparator.comparingInt(p -> p.price);
            case PERFORMANCE -> Comparator.comparingDouble((Part p) -> -performance(p));
            case NAME -> Comparator.comparing(p -> p.name);
        };
        list.sort(cmp);
        return list;
    }

    private static double performance(Part p) {
        return switch (p.kind) {
            case CPU -> p.cores * p.ghz;
            case GPU -> p.perf;
            case RAM -> p.capacityGB * (p.mhz / 1000.0);
            case STORAGE -> p.speedMBs;
            case NETWORK -> p.gbps;
            case PSU -> p.ratedW * p.eff;
            case COOLER -> p.coolW;
            case MOTHERBOARD -> p.pcie * 10 + p.ramSlots;
        };
    }

    private void drawCatalog(GuiGraphics g, ServerConfig cfg, int cx, int top, int cw, int bottom, int mx, int my) {
        UiKit.panel(g, cx, top, cw, bottom - top);
        Kind[] kinds = Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            int tx = cx + 3 + i * 19;
            int ty = top + 3;
            Kind k = kinds[i];
            boolean active = k == tab;
            boolean hover = over(mx, my, tx, ty, 18, 18);
            g.fill(tx, ty, tx + 18, ty + 18, active ? UiKit.ACCENT_DIM : hover ? PANEL3 : PANEL2);
            if (active) {
                g.fill(tx, ty + 17, tx + 18, ty + 18, ACCENT);
            }
            UiKit.icon(g, stackOf(Catalog.BY_KIND.get(k).get(Catalog.BY_KIND.get(k).size() - 1)), tx + 1, ty + 1);
            hit(tx, ty, 18, 18, () -> {
                tab = k;
                scrollCatalog = 0;
            });
            if (hover) {
                tooltip.clear();
                tooltip.add(Component.translatable(k.key()).withStyle(ChatFormatting.AQUA));
            }
        }
        // search + filters
        g.fill(cx + 3, top + 24, cx + cw - 3, top + 38, 0xFF0A0E13);
        search.setX(cx + 7);
        search.setY(top + 26);
        search.setWidth(cw - 14);
        search.visible = true;
        boolean ov = over(mx, my, cx + 3, top + 41, 62, 13);
        UiKit.button(g, font, cx + 3, top + 41, 62, 13, (onlyCompatible ? "✔ " : "") + Component.translatable("hw.gui.only_compat").getString(), ov, onlyCompatible, true);
        hit(cx + 3, top + 41, 62, 13, () -> {
            onlyCompatible = !onlyCompatible;
            scrollCatalog = 0;
        });
        boolean os = over(mx, my, cx + 68, top + 41, cw - 71, 13);
        UiKit.button(g, font, cx + 68, top + 41, cw - 71, 13, Component.translatable("hw.gui.sort").getString() + ": " + Component.translatable("hw.gui.sort." + sort.name().toLowerCase(Locale.ROOT)).getString(), os, false, true);
        hit(cx + 68, top + 41, cw - 71, 13, () -> sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length]);

        List<Part> list = filteredCatalog(cfg);
        int rowH = 22;
        int listTop = top + 57;
        int visibleRows = (bottom - listTop - 2) / rowH;
        scrollCatalog = Math.max(0, Math.min(Math.max(0, list.size() - visibleRows), scrollCatalog));
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
        for (int i = 0; i < visibleRows && i + scrollCatalog < list.size(); i++) {
            Part p = list.get(i + scrollCatalog);
            int py = listTop + i * rowH;
            var problem = cfg.copy().install(p);
            int owned = minecraft != null && minecraft.player != null ? RackMenu.countInInventory(minecraft.player, p) : 0;
            boolean usable = problem == null && (owned > 0 || creative);
            boolean hover = over(mx, my, cx + 3, py, cw - 12, rowH - 1);
            g.fill(cx + 3, py, cx + cw - 9, py + rowH - 1, hover ? PANEL3 : PANEL2);
            g.fill(cx + 3, py, cx + 5, py + rowH - 1, problem != null ? BAD : usable ? OK : WARN);
            UiKit.icon(g, stackOf(p), cx + 8, py + 3);
            g.drawString(font, UiKit.clip(font, p.name, cw - 66), cx + 27, py + 3, problem == null ? TEXT : FAINT, false);
            List<String> spec = p.specLines();
            g.drawString(font, UiKit.clip(font, spec.get(0), cw - 66), cx + 27, py + 12, DIM, false);
            String right = creative ? "∞" : "×" + owned;
            g.drawString(font, right, cx + cw - 12 - font.width(right), py + 3, owned > 0 || creative ? OK : FAINT, false);
            String pr = "$" + shortPrice(p.price);
            g.drawString(font, pr, cx + cw - 12 - font.width(pr), py + 12, FAINT, false);
            if (hover) {
                tooltip.clear();
                partTooltip(p, problem, true);
                tooltip.add(Component.translatable("hw.gui.owned", creative ? "∞" : String.valueOf(owned)).withStyle(owned > 0 || creative ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            int index = Catalog.indexOf(p);
            hit(cx + 3, py, cw - 12, rowH - 1, () -> send(RackMenu.A_INSTALL + index));
        }
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("hw.gui.nothing"), cx + 8, listTop + 6, FAINT, false);
        }
        // scrollbar
        if (list.size() > visibleRows) {
            int trackH = visibleRows * rowH;
            int thumb = Math.max(10, trackH * visibleRows / list.size());
            int ty = listTop + (trackH - thumb) * scrollCatalog / Math.max(1, list.size() - visibleRows);
            g.fill(cx + cw - 7, listTop, cx + cw - 4, listTop + trackH, 0xFF0A0E13);
            g.fill(cx + cw - 7, ty, cx + cw - 4, ty + thumb, UiKit.ACCENT_DIM);
        }
    }

    private void partTooltip(Part p, @Nullable com.crudecompute.hardware.HwError.Problem problem, boolean showHint) {
        tooltip.add(Component.literal(p.name).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(p.kind.key()).withStyle(ChatFormatting.DARK_AQUA));
        List<String> lines = p.specLines();
        for (int i = 0; i < lines.size() - 1; i++) {
            tooltip.add(Component.literal(lines.get(i)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("$" + String.format(Locale.ROOT, "%,d", p.price)).withStyle(ChatFormatting.DARK_GREEN));
        if (problem != null) {
            tooltip.add(Component.translatable(problem.error().key(), problem.arg()).withStyle(ChatFormatting.RED));
        } else if (showHint) {
            tooltip.add(Component.translatable("hw.gui.click_install").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private void drawStats(GuiGraphics g, ServerConfig cfg, ServerState st, int sx, int top, int sw, int bottom, int mx, int my) {
        UiKit.panel(g, sx, top, sw, bottom - top);
        int y = top + 5;
        if (st.status == ServerState.NO_BOOT && st.bootReason > 0) {
            HwError why = HwError.fromCode(st.bootReason);
            if (why != null) {
                g.drawString(font, UiKit.clip(font, "⚠ " + Component.translatable(why.key()).getString(), sw - 10), sx + 5, y, WARN, false);
            }
        } else {
            g.drawString(font, Component.translatable("hw.gui.live"), sx + 5, y, ACCENT, false);
        }
        y += 13;
        UiKit.bar(g, font, sx + 5, y, sw - 10, 10, st.cpuUtil, ACCENT, Component.translatable("hw.gui.cpu").getString() + " " + Math.round(st.cpuUtil * 100) + "%");
        y += 12;
        UiKit.bar(g, font, sx + 5, y, sw - 10, 10, st.gpuUtil, 0xFFA78BFA, Component.translatable("hw.gui.gpu").getString() + " " + Math.round(st.gpuUtil * 100) + "%");
        y += 12;
        int ramGB = cfg.ramGB();
        UiKit.bar(g, font, sx + 5, y, sw - 10, 10, st.ramUtil, 0xFF34D399, "RAM " + Math.round(st.ramUtil * ramGB) + "G");
        y += 12;
        float stGB = Math.max(1, cfg.storageGB());
        UiKit.bar(g, font, sx + 5, y, sw - 10, 10, st.storageUsedGB / stGB, 0xFFF59E0B, Component.translatable("hw.gui.disk").getString() + " " + Math.round(st.storageUsedGB / stGB * 100) + "%");
        y += 12;
        UiKit.bar(g, font, sx + 5, y, sw - 10, 10, Math.min(1f, st.temp / 105f), st.temp > 85 ? BAD : st.temp > 70 ? WARN : OK, Math.round(st.temp) + " °C");
        y += 12;
        UiKit.bar(g, font, sx + 5, y, sw - 10, 10, cfg.psuRatedW() > 0 ? Math.min(1f, st.inputW / cfg.psuRatedW()) : 0f, WARN, Lang.num(st.inputW) + " W");
        y += 14;
        g.drawString(font, UiKit.clip(font, Component.translatable("hw.gui.fan", HardwareSim.fanRpm(cfg, st)).getString(), sw - 8), sx + 5, y, DIM, false);
        y += 10;
        g.drawString(font, UiKit.clip(font, Component.translatable("hw.gui.efficiency").getString() + " " + Math.round(st.psuEff * 100) + "%", sw - 8), sx + 5, y, DIM, false);
        y += 10;
        g.drawString(font, Lang.num(st.compute) + " PF", sx + 5, y, OK, false);
        // priority + mode
        int by = bottom - 70;
        g.drawString(font, Component.translatable("hw.gui.prio"), sx + 5, by, DIM, false);
        for (int m = 0; m < 3; m++) {
            int bx = sx + 4 + m * 30;
            int prio = m;
            boolean active = cfg.priority == m;
            UiKit.button(g, font, bx, by + 10, 29, 13, Component.translatable("hw.gui.prio." + m).getString(), over(mx, my, bx, by + 10, 29, 13), active, true);
            hit(bx, by + 10, 29, 13, () -> send(RackMenu.A_PRIORITY + prio));
        }
        g.drawString(font, Component.translatable("hw.gui.mode"), sx + 5, by + 27, DIM, false);
        for (int m = 0; m < 4; m++) {
            int bx = sx + 4 + (m % 2) * 45;
            int byy = by + 37 + (m / 2) * 15;
            int mode = m;
            boolean active = cfg.mode == m;
            UiKit.button(g, font, bx, byy, 44, 13, Component.translatable("hw.gui.mode." + m).getString(), over(mx, my, bx, byy, 44, 13), active, true);
            hit(bx, byy, 44, 13, () -> send(RackMenu.A_MODE + mode));
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
        return price >= 10000 ? (price / 1000) + "k" : String.format(Locale.ROOT, "%,d", price);
    }

    private static int statusColor(int status) {
        return switch (status) {
            case ServerState.ONLINE -> OK;
            case ServerState.THROTTLED, ServerState.NO_BOOT -> WARN;
            case ServerState.OVERHEATED, ServerState.NO_POWER -> BAD;
            default -> 0xFF64748B;
        };
    }

    private void smallButton(GuiGraphics g, int x, int y, String label, int mx, int my, Runnable action) {
        boolean hover = over(mx, my, x, y, 11, 11);
        g.fill(x, y, x + 11, y + 11, hover ? ACCENT : 0xFF2A3342);
        g.drawString(font, label, x + 6 - font.width(label) / 2, y + 2, hover ? DARK_TEXT : TEXT, false);
        hit(x, y, 11, 11, action);
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
        if (selU >= 0) {
            int dir = (int) -Math.signum(scrollY);
            if (mx < leftPos + 162) {
                scrollInstalled = Math.max(0, scrollInstalled + dir * 20);
            } else if (mx < leftPos + 326) {
                scrollCatalog = Math.max(0, scrollCatalog + dir);
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (search != null && search.visible && search.isFocused() && key != 256) {
            search.keyPressed(key, scan, modifiers);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private static final class Style {
        private Style() {
        }

        static net.minecraft.network.chat.Style of(int rgb) {
            return net.minecraft.network.chat.Style.EMPTY.withColor(rgb & 0xFFFFFF);
        }
    }
}
