package de.localai.baumeister;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Hauptfenster des KI-Baumeisters (Taste K). */
public final class BuilderScreen extends net.minecraft.client.gui.screens.Screen {
    private static final String[][] PRESETS = {
        {"Haus", "Mittelalterliches Fachwerkhaus mit Steinsockel, zwei Stockwerken, Satteldach, Kamin, Veranda, Fensterläden und Garten"},
        {"Villa", "Moderne Villa mit großen Glasfronten, Flachdach-Terrasse, Pool und offenem Wohnbereich"},
        {"Turm", "Runder Zauberturm aus Stein mit spitzem Kegeldach, Wendeltreppe und Bibliothek"},
        {"Burg", "Burg mit Mauern, Ecktürmen, Torhaus, Innenhof und Bergfried"},
        {"Kirche", "Steinkirche mit Glockenturm, Buntglasfenstern und Kirchenbänken"},
        {"Tempel", "Antiker Tempel aus Quarz mit Säulen, Treppenstufen und Giebeldach"},
        {"Japan", "Japanisches Haus mit Walmdach, weißen Wänden, dunklem Holz und Kirschbäumen"},
    };

    private static final int PAD = 10;

    private int px, py, pw, ph, colW, leftX, rightX, bodyY;
    private MultiLineEditBox promptBox;
    private FlatButton modelButton;
    private FlatButton axeButton;
    private FlatButton variantButton;
    private FlatButton saveButton;
    private FlatButton previewToggle;
    private FlatButton generateButton;
    private FlatButton refineButton;
    private FlatButton buildButton;
    private FlatButton undoButton;

    public BuilderScreen() {
        super(Component.literal("KI-Baumeister"));
    }

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.get();
        pw = Math.min(width - 16, 460);
        ph = Math.min(height - 12, 282);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        colW = (pw - PAD * 3) / 2;
        leftX = px + PAD;
        rightX = leftX + colW + PAD;
        bodyY = py + 34;

        String previous = promptBox != null ? promptBox.getValue() : cfg.lastPrompt;
        int promptH = Math.max(36, Math.min(62, ph - 220));
        promptBox = MultiLineEditBox.builder()
            .setX(leftX)
            .setY(bodyY + 11)
            .setPlaceholder(Component.literal("Was soll gebaut werden? z.B. Leuchtturm mit Wendeltreppe …"))
            .build(font, colW, promptH, Component.literal("Bauauftrag"));
        promptBox.setCharacterLimit(800);
        promptBox.setValue(previous);
        addRenderableWidget(promptBox);

        int chipY = bodyY + 11 + promptH + 4;
        int cx = leftX;
        for (String[] preset : PRESETS) {
            int w = font.width(preset[0]) + 10;
            if (cx + w > leftX + colW) break;
            String text = preset[1];
            FlatButton chip = new FlatButton(cx, chipY, w, 14, Component.literal(preset[0]), FlatButton.Style.CHIP,
                in -> promptBox.setValue(text));
            chip.setTooltip(Tooltip.create(Component.literal(text)));
            addRenderableWidget(chip);
            cx += w + 3;
        }

        int modelY = chipY + 30;
        modelButton = addRenderableWidget(new FlatButton(leftX, modelY, colW - 20, 18, Component.empty(),
            FlatButton.Style.SECONDARY, in -> cycleModel(in.hasShiftDown() ? -1 : 1)));
        modelButton.setTooltip(Tooltip.create(Component.literal("Klick: nächstes Modell · Shift+Klick: vorheriges")));
        FlatButton refresh = addRenderableWidget(new FlatButton(leftX + colW - 18, modelY, 18, 18, Component.literal("↻"),
            FlatButton.Style.SECONDARY, in -> Session.refreshOllama()));
        refresh.setTooltip(Tooltip.create(Component.literal("Ollama-Verbindung und Modelle neu laden")));

        int toggleY = modelY + 24;
        int half = (colW - 4) / 2;
        axeButton = addRenderableWidget(new FlatButton(leftX, toggleY, half, 16, Component.literal("⚒ Axt holen"),
            FlatButton.Style.CHIP, in -> Session.giveAxe()));
        axeButton.setTooltip(Tooltip.create(Component.literal(
            "Baumeister-Axt: Linksklick auf einen Block = Ecke 1, Rechtsklick = Ecke 2, Rechtsklick in die Luft = Menü")));
        previewToggle = addRenderableWidget(new FlatButton(leftX + half + 4, toggleY, colW - half - 4, 16, Component.empty(),
            FlatButton.Style.CHIP, in -> {
                cfg.preview = !cfg.preview;
                cfg.save();
            }));
        previewToggle.setTooltip(Tooltip.create(Component.literal("Rahmen und Hologramm des Gebäudes in der Welt anzeigen")));

        int cornerY = bodyY + 11;
        int cornerW = (colW - 26) / 2;
        FlatButton c1 = addRenderableWidget(new FlatButton(rightX, cornerY, cornerW, 18, Component.literal("Ecke 1 setzen"),
            FlatButton.Style.SECONDARY, in -> Session.captureCorner(true)));
        FlatButton c2 = addRenderableWidget(new FlatButton(rightX + cornerW + 4, cornerY, cornerW, 18, Component.literal("Ecke 2 setzen"),
            FlatButton.Style.SECONDARY, in -> Session.captureCorner(false)));
        Tooltip cornerTip = Tooltip.create(Component.literal("Setzt die Ecke auf den Block im Fadenkreuz (bis 96 Blöcke), sonst auf deine Position."));
        c1.setTooltip(cornerTip);
        c2.setTooltip(cornerTip);
        FlatButton clear = addRenderableWidget(new FlatButton(rightX + colW - 18, cornerY, 18, 18, Component.literal("✕"),
            FlatButton.Style.SECONDARY, in -> Session.clearCorners()));
        clear.setTooltip(Tooltip.create(Component.literal("Ecken und Plan verwerfen")));

        int cardLabelY = bodyY + 68;
        int sw = font.width("Schematic") + 10, vw = font.width("Variante") + 10;
        saveButton = addRenderableWidget(new FlatButton(rightX + colW - sw, cardLabelY, sw, 12, Component.literal("Schematic"),
            FlatButton.Style.CHIP, in -> Session.saveSchematic()));
        saveButton.setTooltip(Tooltip.create(Component.literal("Als .schem speichern (WorldEdit, Litematica) – Ordner „schematics“")));
        variantButton = addRenderableWidget(new FlatButton(rightX + colW - sw - vw - 3, cardLabelY, vw, 12, Component.literal("Variante"),
            FlatButton.Style.CHIP, in -> Session.variant()));
        variantButton.setTooltip(Tooltip.create(Component.literal("Gleicher Entwurf, andere Details (sofort, ohne KI)")));

        int actionY = py + ph - 28;
        int aw = pw - PAD * 2;
        int gap = 4;
        int[] weights = {26, 18, 20, 20, 16};
        int total = 0;
        for (int wgt : weights) total += wgt;
        int[] widths = new int[weights.length];
        int used = 0;
        for (int i = 0; i < weights.length; i++) {
            widths[i] = i == weights.length - 1 ? aw - gap * (weights.length - 1) - used
                : (aw - gap * (weights.length - 1)) * weights[i] / total;
            used += widths[i];
        }
        int ax = px + PAD;
        generateButton = addRenderableWidget(new FlatButton(ax, actionY, widths[0], 20, Component.empty(), FlatButton.Style.PRIMARY,
            in -> onGenerate(false)));
        generateButton.setTooltip(Tooltip.create(Component.literal("Der KI-Architekt entwirft das Gebäude, die Engine baut es (Strg+Enter)")));
        ax += widths[0] + gap;
        refineButton = addRenderableWidget(new FlatButton(ax, actionY, widths[1], 20, Component.literal("Anpassen"),
            FlatButton.Style.SECONDARY, in -> onGenerate(true)));
        refineButton.setTooltip(Tooltip.create(Component.literal(
            "Den Entwurf mit dem Text im Eingabefeld ändern, z.B. „drei Stockwerke, Dach aus Kupfer, mit Balkon“")));
        ax += widths[1] + gap;
        buildButton = addRenderableWidget(new FlatButton(ax, actionY, widths[2], 20, Component.empty(), FlatButton.Style.SUCCESS,
            in -> {
                if (BuildManager.progress().running()) Session.stopBuild();
                else Session.build();
            }));
        ax += widths[2] + gap;
        undoButton = addRenderableWidget(new FlatButton(ax, actionY, widths[3], 20, Component.literal("Rückgängig"),
            FlatButton.Style.SECONDARY, in -> Session.undo()));
        undoButton.setTooltip(Tooltip.create(Component.literal("Den letzten Bau zurücksetzen (bis zu 5 Schritte)")));
        ax += widths[3] + gap;
        addRenderableWidget(new FlatButton(ax, actionY, widths[4], 20, Component.literal("Schließen"),
            FlatButton.Style.SECONDARY, in -> onClose()));

        setInitialFocus(promptBox);
        if (Session.ollamaOnline == null || !Boolean.TRUE.equals(Session.ollamaOnline)) Session.refreshOllama();
        updateWidgets();
    }

    private void onGenerate(boolean refine) {
        if (Session.generation != null) {
            Session.cancelGeneration();
            return;
        }
        String prompt = promptBox.getValue();
        ModConfig cfg = ModConfig.get();
        if (!refine) {
            cfg.lastPrompt = prompt;
            cfg.save();
        }
        Session.generate(prompt, cfg.model, refine);
        updateWidgets();
    }

    private void cycleModel(int dir) {
        List<String> models = Session.models;
        if (models.isEmpty()) {
            Session.refreshOllama();
            return;
        }
        ModConfig cfg = ModConfig.get();
        int i = models.indexOf(cfg.model);
        cfg.model = models.get(Math.floorMod(i + dir, models.size()));
        cfg.save();
        updateWidgets();
    }

    private void updateWidgets() {
        ModConfig cfg = ModConfig.get();
        boolean generating = Session.generation != null;
        BuildManager.Progress progress = BuildManager.progress();
        boolean singleplayer = minecraft.getSingleplayerServer() != null;

        String model = cfg.model.isBlank() ? "kein Modell" : cfg.model;
        modelButton.setMessage(Component.literal("◆ " + model + (Session.models.size() > 1 ? "  ▾" : "")));
        variantButton.active = Session.result != null && !generating;
        saveButton.active = Session.result != null;
        previewToggle.setMessage(Component.literal("Vorschau: " + (cfg.preview ? "An" : "Aus")));
        previewToggle.setSelected(cfg.preview);

        generateButton.setMessage(Component.literal(generating ? "■ Abbrechen" : "✦ KI-Entwurf"));
        generateButton.setStyle(generating ? FlatButton.Style.DANGER : FlatButton.Style.PRIMARY);
        refineButton.active = !generating && Session.canRefine();
        buildButton.setMessage(Component.literal(progress.running() ? "■ Stopp" : "▶ Bauen"));
        buildButton.setStyle(progress.running() ? FlatButton.Style.DANGER : FlatButton.Style.SUCCESS);
        buildButton.active = singleplayer && (progress.running() || (Session.result != null && !generating));
        undoButton.active = singleplayer && !progress.running() && BuildManager.undoAvailable() > 0;
        undoButton.setMessage(Component.literal(BuildManager.undoAvailable() > 1
            ? "↶ Rückgängig (" + BuildManager.undoAvailable() + ")" : "↶ Rückgängig"));
    }

    @Override
    public void tick() {
        updateWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && event.hasControlDown()) {
            onGenerate(event.hasShiftDown());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Panel mit Schatten und Kopfzeile
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0x40000000);
        Ui.card(g, px, py, pw, ph, Ui.PANEL, Ui.PANEL_BORDER);
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + 26, Ui.HEADER_TOP, Ui.HEADER_BOTTOM);
        Ui.hGradient(g, px + 1, py + 26, pw - 2, 1, Ui.ACCENT, Ui.VIOLET);

        g.item(new ItemStack(Items.BRICKS), px + 7, py + 5);
        g.text(font, Component.literal("KI-Baumeister").withStyle(ChatFormatting.BOLD), px + 28, py + 6, Ui.TEXT, true);
        g.text(font, "lokale KI · Ollama", px + 28, py + 16, Ui.MUTED, false);
        drawOllamaBadge(g);

        drawLeftColumn(g);
        drawRightColumn(g);
        drawStatus(g);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawOllamaBadge(GuiGraphicsExtractor g) {
        String label;
        int color;
        if (Session.ollamaChecking) {
            label = "Verbinde …";
            color = Ui.AMBER;
        } else if (Boolean.TRUE.equals(Session.ollamaOnline)) {
            label = "Ollama " + Session.ollamaVersion + " · " + Session.models.size() + " Modelle";
            color = Ui.GREEN;
        } else if (Boolean.FALSE.equals(Session.ollamaOnline)) {
            label = "Ollama offline";
            color = Ui.RED;
        } else {
            label = "Ollama …";
            color = Ui.MUTED;
        }
        int w = font.width(label) + 16;
        int x = px + pw - w - 8, y = py + 7;
        Ui.card(g, x, y, w, 13, 0xFF0F1A26, Ui.withAlpha(color, 0x90));
        Ui.dot(g, x + 5, y + 4, color);
        g.text(font, label, x + 12, y + 3, Ui.TEXT, false);
    }

    private void sectionLabel(GuiGraphicsExtractor g, String text, int x, int y) {
        g.text(font, text, x, y, Ui.MUTED, false);
    }

    private void drawLeftColumn(GuiGraphicsExtractor g) {
        sectionLabel(g, "BAUAUFTRAG", leftX, bodyY);
        g.text(font, "Strg+Enter", leftX + colW - font.width("Strg+Enter"), bodyY, Ui.FAINT, false);
        sectionLabel(g, "MODELL", leftX, modelButton.getY() - 11);
    }

    private void drawRightColumn(GuiGraphicsExtractor g) {
        sectionLabel(g, "BAUBEREICH", rightX, bodyY);
        int y = bodyY + 34;
        cornerLine(g, "①", Session.cornerA, rightX, y);
        cornerLine(g, "②", Session.cornerB, rightX + colW / 2, y);
        y += 12;
        if (Session.cornerA != null && Session.cornerB != null && minecraft.player != null) {
            BuildRegion r = BuildRegion.of(Session.cornerA, Session.cornerB, minecraft.player.position());
            String err = Session.validateRegion();
            String size = r.sizeX() + " × " + r.sizeY() + " × " + r.sizeZ() + "  ·  " + Session.fmtInt(r.volume()) + " Raumblöcke";
            g.text(font, Ui.ellipsize(font, size, colW), rightX, y, err == null ? Ui.TEXT : Ui.RED, false);
            y += 10;
            g.text(font, Ui.ellipsize(font, err != null ? err : "Front zeigt nach " + r.frontName() + " (zu dir)", colW),
                rightX, y, err == null ? Ui.MUTED : Ui.RED, false);
        } else {
            g.text(font, "Schau auf einen Block und setze", rightX, y, Ui.MUTED, false);
            g.text(font, "zwei gegenüberliegende Ecken.", rightX, y + 10, Ui.MUTED, false);
        }

        int cardY = bodyY + 70;
        sectionLabel(g, "ENTWURF", rightX, cardY);
        cardY += 11;
        int cardH = axeButton.getY() + axeButton.getHeight() - cardY;
        Ui.card(g, rightX, cardY, colW, cardH, Ui.CARD, Ui.CARD_BORDER);
        PlanCompiler.Result result = Session.result;
        int tx = rightX + 6, ty = cardY + 6, tw = colW - 12;
        if (result == null) {
            String hint = Session.generation != null ? "Die KI arbeitet …" : "Noch kein Entwurf. Beschreibe links dein Gebäude und klicke „KI-Entwurf“.";
            g.textWithWordWrap(font, Component.literal(hint), tx, ty, tw, Ui.MUTED);
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(result.spec().summary()), tw);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), tx, ty + i * 10, Ui.TEXT, false);
        }
        ty += Math.min(2, lines.size()) * 10 + 2;
        g.text(font, Ui.ellipsize(font, Session.describe(result.spec()), tw), tx, ty, Ui.VIOLET, false);
        ty += 10;
        String stats = Session.fmtInt(result.solidBlocks()) + " Blöcke · " + result.voxels().w + "×" + result.voxels().h + "×"
            + result.voxels().d;
        g.text(font, Ui.ellipsize(font, stats, tw), tx, ty, Ui.ACCENT, false);
        ty += 12;

        // Materialliste mit Item-Icons
        int ix = tx;
        for (PlanCompiler.Material m : result.materials()) {
            if (ix + 18 > tx + tw || ty + 16 > cardY + cardH) break;
            Block block = m.block();
            if (block.asItem() == Items.AIR) continue;
            ItemStack stack = new ItemStack(block);
            g.item(stack, ix, ty);
            g.itemDecorations(font, stack, ix, ty, Ui.compact(m.count()));
            ix += 19;
        }
    }

    private void cornerLine(GuiGraphicsExtractor g, String label, net.minecraft.core.BlockPos pos, int x, int y) {
        g.text(font, label, x, y, pos != null ? Ui.AMBER : Ui.FAINT, false);
        g.text(font, Ui.ellipsize(font, pos != null ? Session.fmt(pos) : "nicht gesetzt", colW / 2 - 14), x + 11, y,
            pos != null ? Ui.TEXT : Ui.FAINT, false);
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        int y = py + ph - 52;
        int x = px + PAD, w = pw - PAD * 2;
        Ui.card(g, x, y, w, 20, 0xFF101A25, Ui.CARD_BORDER);

        OllamaClient.Generation gen = Session.generation;
        BuildManager.Progress progress = BuildManager.progress();
        String text;
        int color;
        float fraction = 0f;
        boolean bar = false;
        if (gen != null) {
            long secs = gen.elapsedMillis() / 1000;
            text = gen.isThinking() ? "KI denkt nach … " + secs + " s"
                : gen.contentChars() == 0 ? "Warte auf Ollama (Modell wird geladen) … " + secs + " s"
                : "KI-Architekt schreibt den Entwurf … " + Ui.compact(gen.contentChars()) + " Zeichen · " + secs + " s";
            color = Ui.ACCENT;
            fraction = -1f;
            bar = true;
        } else if (progress.running()) {
            text = progress.message() + " " + Math.round(progress.fraction() * 100) + " %  (" + Session.fmtInt(progress.processed())
                + " / " + Session.fmtInt(progress.total()) + ")";
            color = progress.undo() ? Ui.AMBER : Ui.GREEN;
            fraction = progress.fraction();
            bar = true;
        } else if (!progress.message().isEmpty() && Session.tone() == Session.Tone.BUSY) {
            text = progress.message();
            color = Ui.GREEN;
        } else {
            text = Session.status();
            color = switch (Session.tone()) {
                case OK -> Ui.GREEN;
                case WARN -> Ui.AMBER;
                case ERROR -> Ui.RED;
                case BUSY -> Ui.ACCENT;
                default -> Ui.MUTED;
            };
        }
        Ui.dot(g, x + 6, y + 7, color);
        g.text(font, Ui.ellipsize(font, text, w - 20), x + 15, y + 6, Ui.TEXT, false);
        if (bar) Ui.progress(g, x + 1, y + 17, w - 2, 2, fraction, color);
        else if (minecraft.getSingleplayerServer() == null) {
            g.text(font, "Nur Einzelspieler", x + w - font.width("Nur Einzelspieler") - 6, y + 6, Ui.AMBER, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModConfig cfg = ModConfig.get();
        if (promptBox != null && !promptBox.getValue().isBlank()) {
            cfg.lastPrompt = promptBox.getValue();
            cfg.save();
        }
        super.onClose();
    }
}
