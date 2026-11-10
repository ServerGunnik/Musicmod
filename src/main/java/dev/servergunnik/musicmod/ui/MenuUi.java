package dev.servergunnik.musicmod.ui;

import dev.servergunnik.musicmod.MusicmodClient;
import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.core.Config;
import dev.servergunnik.musicmod.core.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * The Musicmod menu (Right Shift). Version independent: it only draws through
 * {@link Draw} and receives mouse input from the version's Screen wrapper.
 */
public final class MenuUi {

    public enum Tab { OVERVIEW, LYRICS, STYLES, HUD, SKIES, SETTINGS, INFO }

    static final int W = 384;
    static final int H = 236;
    static final int SIDEBAR = 104;
    static final int CX = SIDEBAR + 10;
    static final int CY = 36;
    static final int CW = W - SIDEBAR - 18;
    static final int CH = H - CY - 8;
    static final int TEXT = 0xFFF0F0F0;
    static final int DIM = 0xFF9B9B9B;

    private static Tab lastTab = Tab.OVERVIEW;

    private Tab tab = lastTab;
    private final List<Widget> widgets = new ArrayList<>();
    private int contentHeight;
    private double scroll;
    private float scale = 1f;
    private float ox;
    private float oy;
    private Widget dragging;
    private boolean dirty;
    private boolean needsLayout = true;

    public MenuUi() {
        rebuild();
    }

    static Config cfg() {
        return MusicmodClient.config();
    }

    static Theme theme() {
        return cfg().theme;
    }

    static String tr(String key) {
        return MusicmodClient.tr(key);
    }

    void changed() {
        dirty = true;
    }

    void rebuild() {
        widgets.clear();
        Pages.build(tab, widgets, this);
        needsLayout = true;
    }

    private void layout(Draw d) {
        int y = 0;
        for (Widget w : widgets) {
            w.x = 0;
            w.y = y;
            w.w = CW;
            w.h = w.measure(d, CW);
            y += w.h + 4;
        }
        contentHeight = Math.max(0, y - 4);
        needsLayout = false;
        clampScroll();
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - CH)));
    }

    // ---------------------------------------------------------------- render

    public void render(Draw d, int mouseX, int mouseY) {
        scale = Math.min(1f, Math.min((d.width() - 16) / (float) W, (d.height() - 16) / (float) H));
        ox = (d.width() - W * scale) / 2f;
        oy = (d.height() - H * scale) / 2f;
        double lx = (mouseX - ox) / scale;
        double ly = (mouseY - oy) / scale;
        Theme th = theme();
        if (needsLayout) {
            layout(d);
        }

        d.fill(0, 0, d.width(), d.height(), 0x70000000);

        d.push(ox, oy, scale);
        d.rect(0, 0, W, H, th.panel, th.border);
        d.fill(1, 1, SIDEBAR, H - 1, 0x22FFFFFF);
        d.fill(SIDEBAR, 1, SIDEBAR + 1, H - 1, (th.border & 0x00FFFFFF) | 0x80000000);

        // Logo
        d.rect(8, 8, 26, 26, th.accentArgb(), 0);
        d.push(12, 10, 1.6f);
        d.text("♪", 0, 0, 0xFF1A1A1A, false);
        d.pop();
        d.text("Musicmod", 31, 9, TEXT, true);
        d.push(31, 19, 0.75f);
        d.text(tr("musicmod.tagline"), 0, 0, DIM, false);
        d.pop();

        // Tabs
        int ty = 36;
        for (Tab t : Tab.values()) {
            boolean sel = t == tab;
            boolean hov = lx >= 6 && lx < SIDEBAR - 6 && ly >= ty && ly < ty + 18;
            if (sel) {
                d.rect(6, ty, SIDEBAR - 6, ty + 18, (th.sung & 0x00FFFFFF) | 0x55000000, th.accentArgb());
            } else if (hov) {
                d.rect(6, ty, SIDEBAR - 6, ty + 18, 0x22FFFFFF, 0);
            }
            d.text(icon(t), 12, ty + 5, sel ? th.accentArgb() : DIM, false);
            d.text(tr("musicmod.tab." + t.name().toLowerCase()), 24, ty + 5, sel ? TEXT : 0xFFC8C8C8, false);
            ty += 20;
        }
        d.push(10, H - 14, 0.75f);
        d.text("v" + MusicmodClient.version(), 0, 0, DIM, false);
        d.pop();

        // Header
        String key = "musicmod.tab." + tab.name().toLowerCase();
        d.text(tr(key), CX, 10, TEXT, true);
        d.push(CX, 21, 0.75f);
        d.text(tr(key + ".desc"), 0, 0, DIM, false);
        d.pop();
        d.fill(CX, CY - 4, CX + CW, CY - 3, 0x30FFFFFF);
        d.pop();

        // Content (clipped + scrolled)
        int sx1 = Math.round(ox + CX * scale);
        int sy1 = Math.round(oy + CY * scale);
        int sx2 = Math.round(ox + (CX + CW) * scale);
        int sy2 = Math.round(oy + (CY + CH) * scale);
        d.scissor(sx1, sy1, sx2, sy2);
        d.push(ox + CX * scale, oy + (float) (CY - scroll) * scale, scale);
        double cmx = lx - CX;
        double cmy = ly - CY + scroll;
        boolean inContent = lx >= CX && lx < CX + CW && ly >= CY && ly < CY + CH;
        for (Widget w : widgets) {
            if (w.y + w.h < scroll - 4 || w.y > scroll + CH + 4) {
                continue;
            }
            boolean hov = inContent && w.contains(cmx, cmy);
            w.render(d, cmx, cmy, hov);
        }
        d.pop();
        d.noScissor();

        // Scrollbar
        if (contentHeight > CH) {
            d.push(ox, oy, scale);
            int trackX = CX + CW + 3;
            float frac = CH / (float) contentHeight;
            int barH = Math.max(12, Math.round(CH * frac));
            int barY = CY + Math.round((float) (scroll / (contentHeight - CH)) * (CH - barH));
            d.fill(trackX, CY, trackX + 2, CY + CH, 0x20FFFFFF);
            d.fill(trackX, barY, trackX + 2, barY + barH, th.accentArgb());
            d.pop();
        }
    }

    private static String icon(Tab t) {
        return switch (t) {
            case OVERVIEW -> "⌂";
            case LYRICS -> "♫";
            case STYLES -> "✦";
            case HUD -> "▣";
            case SKIES -> "☁";
            case SETTINGS -> "⚙";
            case INFO -> "i";
        };
    }

    // ----------------------------------------------------------------- input

    public boolean mouseClicked(double mx, double my, int button) {
        double lx = (mx - ox) / scale;
        double ly = (my - oy) / scale;
        if (lx < 0 || ly < 0 || lx >= W || ly >= H) {
            return false;
        }
        int ty = 36;
        for (Tab t : Tab.values()) {
            if (lx >= 6 && lx < SIDEBAR - 6 && ly >= ty && ly < ty + 18) {
                if (t != tab) {
                    tab = t;
                    lastTab = t;
                    scroll = 0;
                    rebuild();
                }
                return true;
            }
            ty += 20;
        }
        if (lx >= CX && lx < CX + CW && ly >= CY && ly < CY + CH) {
            double cmx = lx - CX;
            double cmy = ly - CY + scroll;
            for (Widget w : widgets) {
                if (w.contains(cmx, cmy) && w.click(cmx, cmy, button)) {
                    if (w.draggable()) {
                        dragging = w;
                    }
                    return true;
                }
            }
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my) {
        if (dragging == null) {
            return false;
        }
        dragging.drag((mx - ox) / scale - CX, (my - oy) / scale - CY + scroll);
        return true;
    }

    public boolean mouseReleased() {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double amount) {
        scroll -= amount * 18;
        clampScroll();
        return true;
    }

    /** Saves settings if anything changed (called when the screen closes). */
    public void onClose() {
        if (dirty) {
            MusicmodClient.saveConfig();
            dirty = false;
        }
    }

    void afterReset() {
        dirty = false;
        rebuild();
    }
}
