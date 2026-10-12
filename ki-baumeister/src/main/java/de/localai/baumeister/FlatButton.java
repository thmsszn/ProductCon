package de.localai.baumeister;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Flacher, moderner Button im Stil der Baumeister-Oberfläche. */
final class FlatButton extends AbstractButton {
    enum Style { PRIMARY, SECONDARY, DANGER, SUCCESS, CHIP }

    private final Consumer<InputWithModifiers> action;
    private Style style;
    private boolean selected;

    FlatButton(int x, int y, int w, int h, Component label, Style style, Consumer<InputWithModifiers> action) {
        super(x, y, w, h, label);
        this.style = style;
        this.action = action;
    }

    void setStyle(Style style) { this.style = style; }

    void setSelected(boolean selected) { this.selected = selected; }

    @Override
    public void onPress(InputWithModifiers input) {
        action.accept(input);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = active && isHoveredOrFocused();
        int bg, border, text;
        if (!active) {
            bg = 0xFF18222E;
            border = 0xFF223040;
            text = Ui.FAINT;
        } else {
            switch (style) {
                case PRIMARY -> {
                    bg = hover ? 0xFF06B6D4 : 0xFF0891B2;
                    border = hover ? 0xFF67E8F9 : 0xFF22D3EE;
                    text = 0xFFFFFFFF;
                }
                case SUCCESS -> {
                    bg = hover ? 0xFF16A34A : 0xFF15803D;
                    border = hover ? 0xFF86EFAC : 0xFF4ADE80;
                    text = 0xFFFFFFFF;
                }
                case DANGER -> {
                    bg = hover ? 0xFFDC2626 : 0xFFB91C1C;
                    border = hover ? 0xFFFCA5A5 : 0xFFF87171;
                    text = 0xFFFFFFFF;
                }
                case CHIP -> {
                    bg = selected ? 0xFF123B4A : hover ? 0xFF1F2E40 : 0xFF172331;
                    border = selected ? Ui.ACCENT : hover ? 0xFF3B5168 : 0xFF2A3A4D;
                    text = selected ? 0xFFCFFAFE : hover ? Ui.TEXT : 0xFFB8C5D3;
                }
                default -> {
                    bg = hover ? 0xFF223247 : 0xFF1A2636;
                    border = hover ? 0xFF4B6582 : 0xFF2E4057;
                    text = Ui.TEXT;
                }
            }
        }
        Ui.card(g, x, y, w, h, bg, border);
        if (active && style != Style.CHIP) g.fill(x + 2, y + 1, x + w - 2, y + 2, Ui.withAlpha(0xFFFFFF, hover ? 0x30 : 0x18));
        Font font = Minecraft.getInstance().font;
        String label = Ui.ellipsize(font, message.getString(), w - 6);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 7) / 2, text, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
