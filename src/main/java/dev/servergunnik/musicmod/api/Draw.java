package dev.servergunnik.musicmod.api;

/**
 * Minimal 2D drawing surface. Each Minecraft version provides its own
 * implementation (compat.GuiDraw), so the HUD and the menu are written once.
 * Colors are ARGB.
 */
public interface Draw {
    int width();

    int height();

    void fill(int x1, int y1, int x2, int y2, int argb);

    /** Draws text, returns the x after the text. */
    int text(String text, int x, int y, int argb, boolean shadow);

    int textWidth(String text);

    int lineHeight();

    /** Translates to (x, y) and scales; undo with {@link #pop()}. */
    void push(float x, float y, float scale);

    void pop();

    void scissor(int x1, int y1, int x2, int y2);

    void noScissor();

    default void rect(int x1, int y1, int x2, int y2, int fill, int border) {
        fill(x1 + 1, y1, x2 - 1, y2, fill);
        fill(x1, y1 + 1, x1 + 1, y2 - 1, fill);
        fill(x2 - 1, y1 + 1, x2, y2 - 1, fill);
        if ((border >>> 24) != 0) {
            fill(x1 + 1, y1, x2 - 1, y1 + 1, border);
            fill(x1 + 1, y2 - 1, x2 - 1, y2, border);
            fill(x1, y1 + 1, x1 + 1, y2 - 1, border);
            fill(x2 - 1, y1 + 1, x2, y2 - 1, border);
        }
    }

    default int centered(String text, int cx, int y, int argb, boolean shadow) {
        return text(text, cx - textWidth(text) / 2, y, argb, shadow);
    }

    default String ellipsize(String text, int maxWidth) {
        if (textWidth(text) <= maxWidth) {
            return text;
        }
        String dots = "...";
        int end = text.length();
        while (end > 0 && textWidth(text.substring(0, end) + dots) > maxWidth) {
            end--;
        }
        return text.substring(0, end).stripTrailing() + dots;
    }
}
