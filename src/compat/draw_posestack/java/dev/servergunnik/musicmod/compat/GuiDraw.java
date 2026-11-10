package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.api.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Draw implementation for this Minecraft version. */
public final class GuiDraw implements Draw {

    private final GuiGraphics g;
    private final Font font;

    private GuiDraw(GuiGraphics g) {
        this.g = g;
        this.font = Minecraft.getInstance().font;
    }

    public static GuiDraw of(GuiGraphics g) {
        return new GuiDraw(g);
    }

    @Override
    public int width() {
        return g.guiWidth();
    }

    @Override
    public int height() {
        return g.guiHeight();
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public int text(String text, int x, int y, int argb, boolean shadow) {
        g.drawString(font, text, x, y, argb, shadow);
        return x + font.width(text);
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void push(float x, float y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
    }

    @Override
    public void pop() {
        g.pose().popPose();
    }

    @Override
    public void scissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void noScissor() {
        g.disableScissor();
    }
}
