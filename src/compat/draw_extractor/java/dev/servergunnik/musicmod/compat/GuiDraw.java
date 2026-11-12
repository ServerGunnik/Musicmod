package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.api.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draw implementation for this Minecraft version. */
public final class GuiDraw implements Draw {

    private final GuiGraphicsExtractor g;
    private final Font font;

    private GuiDraw(GuiGraphicsExtractor g) {
        this.g = g;
        this.font = Minecraft.getInstance().font;
    }

    public static GuiDraw of(GuiGraphicsExtractor g) {
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
        g.text(font, text, x, y, argb, shadow);
        return x + font.width(text);
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int text(String text, int x, int y, int argb, boolean shadow, String fontName) {
        Component c = Fonts.text(text, fontName);
        g.text(font, c, x, y, argb, shadow);
        return x + font.width(c);
    }

    @Override
    public int textWidth(String text, String fontName) {
        return font.width(Fonts.text(text, fontName));
    }

    @Override
    public void image(String path, int x, int y, int w, int h) {
        // Texture size = box size, so the whole picture is stretched into the box.
        g.blit(RenderPipelines.GUI_TEXTURED, Fonts.id(path), x, y, 0f, 0f, w, h, w, h);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void push(float x, float y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
    }

    @Override
    public void pop() {
        g.pose().popMatrix();
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
