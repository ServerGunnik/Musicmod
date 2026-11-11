package dev.servergunnik.musicmod.ui;

import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.core.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Menu widgets. Coordinates are in content space (see {@link MenuUi}). */
abstract class Widget {

    int x;
    int y;
    int w;
    int h;

    abstract int measure(Draw d, int width);

    abstract void render(Draw d, double mx, double my, boolean hovered);

    boolean click(double mx, double my, int button) {
        return false;
    }

    boolean draggable() {
        return false;
    }

    void drag(double mx, double my) {
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static void rowBackground(Draw d, Widget wd, boolean hovered) {
        d.rect(wd.x, wd.y, wd.x + wd.w, wd.y + wd.h, hovered ? 0x2AFFFFFF : 0x16FFFFFF, 0x22FFFFFF);
    }

    static void small(Draw d, String text, int x, int y, int color) {
        d.push(x, y, 0.75f);
        d.text(text, 0, 0, color, false);
        d.pop();
    }

    static int smallWidth(Draw d, String text) {
        return Math.round(d.textWidth(text) * 0.75f);
    }

    // ------------------------------------------------------------------------

    /** Section caption, e.g. "• General". */
    static final class Header extends Widget {
        private final String key;

        Header(String key) {
            this.key = key;
        }

        int measure(Draw d, int width) {
            return 11;
        }

        void render(Draw d, double mx, double my, boolean hovered) {
            Theme th = MenuUi.theme();
            d.text("• " + MenuUi.tr(key), x + 1, y + 2, th.accentArgb(), false);
        }
    }

    /** On/off switch with label and description. */
    static final class Toggle extends Widget {
        private final String key;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private final MenuUi ui;

        Toggle(MenuUi ui, String key, BooleanSupplier get, Consumer<Boolean> set) {
            this.ui = ui;
            this.key = key;
            this.get = get;
            this.set = set;
        }

        int measure(Draw d, int width) {
            return 25;
        }

        void render(Draw d, double mx, double my, boolean hovered) {
            rowBackground(d, this, hovered);
            d.text(MenuUi.tr(key), x + 6, y + 4, MenuUi.TEXT, false);
            small(d, d.ellipsize(MenuUi.tr(key + ".desc"), Math.round((w - 44) / 0.75f)), x + 6, y + 15, MenuUi.DIM);
            boolean on = get.getAsBoolean();
            int sx = x + w - 28;
            int sy = y + 8;
            Theme th = MenuUi.theme();
            d.rect(sx, sy, sx + 20, sy + 10, on ? th.accentArgb() : 0x50FFFFFF, 0);
            int kx = on ? sx + 11 : sx + 1;
            d.rect(kx, sy + 1, kx + 8, sy + 9, 0xFFFFFFFF, 0);
        }

        boolean click(double mx, double my, int button) {
            set.accept(!get.getAsBoolean());
            ui.changed();
            return true;
        }
    }

    /** Value picker: left click = next, right click = previous. */
    static final class Choice extends Widget {
        private final String key;
        private final Supplier<String> value;
        private final Consumer<Integer> step;
        private final MenuUi ui;

        Choice(MenuUi ui, String key, Supplier<String> value, Consumer<Integer> step) {
            this.ui = ui;
            this.key = key;
            this.value = value;
            this.step = step;
        }

        int measure(Draw d, int width) {
            return 25;
        }

        void render(Draw d, double mx, double my, boolean hovered) {
            rowBackground(d, this, hovered);
            String v = value.get();
            int vw = d.textWidth(v) + 22;
            d.text(MenuUi.tr(key), x + 6, y + 4, MenuUi.TEXT, false);
            small(d, d.ellipsize(MenuUi.tr(key + ".desc"), Math.round((w - vw - 16) / 0.75f)), x + 6, y + 15, MenuUi.DIM);
            int bx = x + w - vw - 6;
            Theme th = MenuUi.theme();
            d.rect(bx, y + 6, x + w - 6, y + 19, 0x30FFFFFF, (th.border & 0x00FFFFFF) | 0xA0000000);
            d.text("‹", bx + 4, y + 9, MenuUi.DIM, false);
            d.text(v, bx + 11, y + 9, th.accentArgb(), false);
            d.text("›", x + w - 13, y + 9, MenuUi.DIM, false);
        }

        boolean click(double mx, double my, int button) {
            step.accept(button == 1 ? -1 : 1);
            ui.changed();
            return true;
        }
    }

    /** Integer slider. */
    static final class Slider extends Widget {
        private final String key;
        private final int min;
        private final int max;
        private final int stepSize;
        private final IntSupplier get;
        private final IntConsumer set;
        private final IntFunction<String> format;
        private final MenuUi ui;

        Slider(MenuUi ui, String key, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<String> format) {
            this.ui = ui;
            this.key = key;
            this.min = min;
            this.max = max;
            this.stepSize = step;
            this.get = get;
            this.set = set;
            this.format = format;
        }

        int measure(Draw d, int width) {
            return 31;
        }

        private int trackX1() {
            return x + 6;
        }

        private int trackX2() {
            return x + w - 6;
        }

        void render(Draw d, double mx, double my, boolean hovered) {
            rowBackground(d, this, hovered);
            Theme th = MenuUi.theme();
            String v = format.apply(get.getAsInt());
            d.text(MenuUi.tr(key), x + 6, y + 4, MenuUi.TEXT, false);
            d.text(v, x + w - 6 - d.textWidth(v), y + 4, th.accentArgb(), false);
            small(d, d.ellipsize(MenuUi.tr(key + ".desc"), Math.round((w - 12) / 0.75f)), x + 6, y + 14, MenuUi.DIM);
            int ty = y + 24;
            float f = (get.getAsInt() - min) / (float) (max - min);
            int kx = trackX1() + Math.round(f * (trackX2() - trackX1()));
            d.fill(trackX1(), ty, trackX2(), ty + 2, 0x40FFFFFF);
            d.fill(trackX1(), ty, kx, ty + 2, th.accentArgb());
            d.rect(kx - 3, ty - 3, kx + 3, ty + 5, 0xFFFFFFFF, 0);
        }

        boolean click(double mx, double my, int button) {
            if (my < y + 19) {
                // Clicking the label row nudges the value (right click = down).
                apply(get.getAsInt() + (button == 1 ? -stepSize : stepSize));
                return false;
            }
            drag(mx, my);
            return true;
        }

        boolean draggable() {
            return true;
        }

        void drag(double mx, double my) {
            double f = (mx - trackX1()) / (double) (trackX2() - trackX1());
            f = Math.max(0, Math.min(1, f));
            apply((int) Math.round(min + f * (max - min)));
        }

        private void apply(int raw) {
            int v = Math.round((raw - min) / (float) stepSize) * stepSize + min;
            v = Math.max(min, Math.min(max, v));
            if (v != get.getAsInt()) {
                set.accept(v);
                ui.changed();
            }
        }
    }

    /** Grid of color presets, each drawn in its own colors. */
    static final class ThemeGrid extends Widget {
        private static final int COLS = 3;
        private static final int CELL_H = 26;
        private final MenuUi ui;

        ThemeGrid(MenuUi ui) {
            this.ui = ui;
        }

        int measure(Draw d, int width) {
            int rows = (Theme.values().length + COLS - 1) / COLS;
            return rows * (CELL_H + 4) - 4;
        }

        private int cellW() {
            return (w - (COLS - 1) * 4) / COLS;
        }

        void render(Draw d, double mx, double my, boolean hovered) {
            Theme sel = MenuUi.theme();
            Theme[] all = Theme.values();
            for (int i = 0; i < all.length; i++) {
                Theme t = all[i];
                int cx = x + (i % COLS) * (cellW() + 4);
                int cy = y + (i / COLS) * (CELL_H + 4);
                boolean hov = mx >= cx && mx < cx + cellW() && my >= cy && my < cy + CELL_H;
                d.rect(cx, cy, cx + cellW(), cy + CELL_H, t.panel, t == sel ? t.accentArgb() : hov ? 0x80FFFFFF : 0x30FFFFFF);
                d.centered(t.label, cx + cellW() / 2, cy + 5, 0xFF000000 | t.current, true);
                // three dots: sung / current / upcoming
                int dx = cx + cellW() / 2 - 10;
                d.fill(dx, cy + 18, dx + 5, cy + 21, 0xFF000000 | t.sung);
                d.fill(dx + 8, cy + 18, dx + 13, cy + 21, 0xFF000000 | t.current);
                d.fill(dx + 16, cy + 18, dx + 21, cy + 21, 0xFF000000 | t.upcoming);
            }
        }

        boolean click(double mx, double my, int button) {
            Theme[] all = Theme.values();
            int col = (int) ((mx - x) / (cellW() + 4));
            int row = (int) ((my - y) / (CELL_H + 4));
            int i = row * COLS + col;
            if (col >= 0 && col < COLS && i >= 0 && i < all.length) {
                MenuUi.cfg().theme = all[i];
                ui.changed();
            }
            return true;
        }
    }

    /** Draws a small picture inside a grid tile. */
    interface Painter {
        void paint(Draw d, int index, int x, int y, int w, int h);
    }

    /**
     * Grid of tiles to pick one option from (click = select), like the theme grid.
     * Each tile can show its label in its own font and an optional picture above it.
     */
    static final class OptionGrid extends Widget {
        private final int cols;
        private final int count;
        private final IntFunction<String> label;
        private final IntFunction<String> fontOf;
        private final IntSupplier selected;
        private final IntConsumer select;
        private final Painter painter;
        private final int pictureH;

        OptionGrid(int cols, int count, IntFunction<String> label, IntFunction<String> fontOf,
                   IntSupplier selected, IntConsumer select, Painter painter, int pictureH) {
            this.cols = cols;
            this.count = count;
            this.label = label;
            this.fontOf = fontOf;
            this.selected = selected;
            this.select = select;
            this.painter = painter;
            this.pictureH = painter == null ? 0 : pictureH;
        }

        private int cellH() {
            return 18 + pictureH;
        }

        private int cellW() {
            return (w - (cols - 1) * 4) / cols;
        }

        int measure(Draw d, int width) {
            int rows = (count + cols - 1) / cols;
            return rows * (cellH() + 4) - 4;
        }

        void render(Draw d, double mx, double my, boolean hovered) {
            Theme th = MenuUi.theme();
            int sel = selected.getAsInt();
            for (int i = 0; i < count; i++) {
                int cx = x + (i % cols) * (cellW() + 4);
                int cy = y + (i / cols) * (cellH() + 4);
                boolean hov = mx >= cx && mx < cx + cellW() && my >= cy && my < cy + cellH();
                boolean on = i == sel;
                d.rect(cx, cy, cx + cellW(), cy + cellH(),
                        on ? (th.sung & 0x00FFFFFF) | 0x66000000 : hov ? 0x33FFFFFF : 0x1AFFFFFF,
                        on ? th.accentArgb() : hov ? 0x80FFFFFF : 0x30FFFFFF);
                if (painter != null) {
                    painter.paint(d, i, cx + 2, cy + 2, cellW() - 4, pictureH);
                }
                String font = fontOf == null ? null : fontOf.apply(i);
                String text = label.apply(i);
                int tw = d.textWidth(text, font);
                int max = cellW() - 6;
                int ty = cy + pictureH + 5;
                if (tw > max) {
                    // Too long for the tile: shrink the label instead of cutting it.
                    float sc = max / (float) tw;
                    d.push(cx + cellW() / 2f - max / 2f, ty + 4 * (1 - sc), sc);
                    d.text(text, 0, 0, on ? 0xFFFFFFFF : 0xFFD6D6D6, false, font);
                    d.pop();
                } else {
                    d.text(text, cx + (cellW() - tw) / 2, ty, on ? 0xFFFFFFFF : 0xFFD6D6D6, false, font);
                }
            }
        }

        boolean click(double mx, double my, int button) {
            int col = (int) ((mx - x) / (cellW() + 4));
            int row = (int) ((my - y) / (cellH() + 4));
            int i = row * cols + col;
            if (col >= 0 && col < cols && i >= 0 && i < count && i != selected.getAsInt()) {
                select.accept(i);
            }
            return true;
        }
    }

    /** Wrapped paragraph of dim text. */
    static final class Paragraph extends Widget {
        private final Supplier<String> text;
        private final int color;
        private List<String> lines = List.of();

        Paragraph(Supplier<String> text, int color) {
            this.text = text;
            this.color = color;
        }

        int measure(Draw d, int width) {
            lines = wrap(d, text.get(), width - 4);
            return lines.size() * 10 + 1;
        }

        void render(Draw d, double mx, double my, boolean hovered) {
            lines = wrap(d, text.get(), w - 4);
            int ly = y + 1;
            for (String l : lines) {
                d.text(l, x + 2, ly, color, false);
                ly += 10;
            }
        }

        static List<String> wrap(Draw d, String text, int width) {
            List<String> out = new ArrayList<>();
            for (String para : text.split("\n")) {
                StringBuilder line = new StringBuilder();
                for (String word : para.split(" ")) {
                    String tryLine = line.isEmpty() ? word : line + " " + word;
                    if (d.textWidth(tryLine) > width && !line.isEmpty()) {
                        out.add(line.toString());
                        line = new StringBuilder(word);
                    } else {
                        line = new StringBuilder(tryLine);
                    }
                }
                out.add(line.toString());
            }
            return out;
        }
    }

    /** Full-width button. */
    static final class Button extends Widget {
        private final String key;
        private final Runnable action;
        private long flashUntil;

        Button(String key, Runnable action) {
            this.key = key;
            this.action = action;
        }

        int measure(Draw d, int width) {
            return 20;
        }

        void render(Draw d, double mx, double my, boolean hovered) {
            Theme th = MenuUi.theme();
            boolean flash = System.currentTimeMillis() < flashUntil;
            d.rect(x, y, x + w, y + h, flash ? th.accentArgb() : hovered ? 0x40FFFFFF : 0x22FFFFFF, (th.border & 0x00FFFFFF) | 0xC0000000);
            d.centered(MenuUi.tr(key), x + w / 2, y + 6, flash ? 0xFF111111 : MenuUi.TEXT, false);
        }

        boolean click(double mx, double my, int button) {
            action.run();
            flashUntil = System.currentTimeMillis() + 350;
            return true;
        }
    }
}
