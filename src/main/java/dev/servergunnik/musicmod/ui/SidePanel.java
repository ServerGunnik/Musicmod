package dev.servergunnik.musicmod.ui;

import dev.servergunnik.musicmod.MusicmodClient;
import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.compat.Textures;
import dev.servergunnik.musicmod.core.LyricsProvider;
import dev.servergunnik.musicmod.core.LyricsTracker;
import dev.servergunnik.musicmod.core.MediaInfo;
import dev.servergunnik.musicmod.core.MediaSource;
import dev.servergunnik.musicmod.core.Theme;

import java.util.List;

/**
 * "Now playing" panel shown to the left of the menu: big album cover with a
 * record behind it, title, artist, the line being sung and the next one, an
 * equalizer and the player buttons.
 */
final class SidePanel {

    static final int WIDTH = 150;
    private static final int COVER = 92;

    private static int coverVersion = -1;
    private static boolean hasCover;

    private int x;
    private int controlsY;

    /** Draws the panel with its top-left corner at (x, y). Returns its height. */
    int render(Draw d, int x, int y, int mouseX, int mouseY) {
        this.x = x;
        Theme th = MenuUi.theme();
        LyricsTracker.Snapshot s = MusicmodClient.tracker().snapshot();
        MediaInfo m = s.media();
        MediaSource source = MusicmodClient.tracker().media();
        if (source.coverVersion() != coverVersion) {
            coverVersion = source.coverVersion();
            java.nio.file.Path file = source.coverFile();
            hasCover = file != null && Textures.load("dynamic/cover_big", file);
        }

        // Record peeking out from behind the cover.
        int cx = x + COVER + 6;
        int cy = y + COVER / 2;
        int r = COVER / 2 - 4;
        disc(d, cx, cy, r, 0xFF0E0E12);
        ring(d, cx, cy, r - 6, 0x30FFFFFF);
        ring(d, cx, cy, r - 13, 0x26FFFFFF);
        ring(d, cx, cy, r - 20, 0x30FFFFFF);
        disc(d, cx, cy, 9, th.accentArgb());
        disc(d, cx, cy, 2, 0xFF0E0E12);

        // Cover.
        d.fill(x - 1, y - 1, x + COVER + 1, y + COVER + 1, 0x60000000);
        if (hasCover && m.active()) {
            d.image("dynamic/cover_big", x, y, COVER, COVER);
        } else {
            d.rect(x, y, x + COVER, y + COVER, (th.sung & 0x00FFFFFF) | 0xC0000000, 0);
            d.push(x + COVER / 2f - 12, y + COVER / 2f - 18, 5f);
            d.text("♪", 0, 0, 0xFFFFFFFF, true);
            d.pop();
        }

        int ty = y + COVER + 10;
        if (!m.active()) {
            d.text(MenuUi.tr("musicmod.player.nothing"), x, ty, MenuUi.TEXT, true);
            return ty + 12 - y;
        }

        // Title and artist.
        String title = d.ellipsize(m.title(), Math.round(WIDTH / 1.25f));
        d.push(x, ty, 1.25f);
        d.text(title, 0, 0, 0xFFFFFFFF, true);
        d.pop();
        ty += 14;
        d.text(d.ellipsize(m.artist(), WIDTH), x, ty, 0xFFB8B8C4, false);
        ty += 15;

        // The line being sung, then the next one.
        if (s.status() == LyricsProvider.Status.FOUND && s.hasLine()) {
            String now = s.lineFinished() || s.line().isBlank() ? "♪" : s.line().text();
            ty = paragraph(d, now, x, ty, 0xFFFFFFFF, 3);
            if (s.nextLine() != null && !s.nextLine().isBlank()) {
                ty = paragraph(d, s.nextLine().text(), x, ty + 2, 0xFF7C7C8A, 2);
            }
        } else {
            String key = switch (s.status()) {
                case LOADING -> "musicmod.lyrics.loading";
                case ERROR -> "musicmod.lyrics.error";
                default -> "musicmod.lyrics.not_found";
            };
            ty = paragraph(d, MenuUi.tr(key), x, ty, 0xFF7C7C8A, 2);
        }
        ty += 8;

        // Equalizer.
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 30; i++) {
            int h = m.playing()
                    ? 2 + (int) Math.round(12 * Math.abs(Math.sin(t * (2.2 + (i % 7) * 0.45) + i * 0.9)) * (0.4 + 0.6 * Math.abs(Math.sin(i * 0.37 + t * 0.6))))
                    : 2;
            int bx = x + i * 5;
            d.fill(bx, ty + 14 - h, bx + 3, ty + 14, i * 5 < WIDTH * m.progress() ? th.accentArgb() : 0xFFE6E6F0);
        }
        ty += 22;

        // Buttons: previous, play/pause (white disc), next.
        controlsY = ty + 11;
        boolean enabled = source.status() == MediaSource.Status.RUNNING;
        int hover = buttonAt(mouseX, mouseY);
        d.centered("|◀", x + 10, controlsY - 4, hover == 0 ? th.accentArgb() : 0xFFD0D0DC, false);
        disc(d, x + 40, controlsY, 11, enabled ? (hover == 1 ? th.accentArgb() : 0xFFFFFFFF) : 0xFF8A8A96);
        d.centered(m.playing() ? "||" : "▶", x + 40, controlsY - 4, 0xFF14141A, false);
        d.centered("▶|", x + 70, controlsY - 4, hover == 2 ? th.accentArgb() : 0xFFD0D0DC, false);
        String time = MediaInfo.formatTime(m.positionMs()) + (m.durationMs() > 0 ? " / " + MediaInfo.formatTime(m.durationMs()) : "");
        d.text(time, x + WIDTH - d.textWidth(time), controlsY - 4, 0xFFB8B8C4, false);
        return ty + 24 - y;
    }

    /** Handles a click; true when it hit a button. */
    boolean click(double mx, double my) {
        int b = buttonAt(mx, my);
        if (b < 0) {
            return false;
        }
        MusicmodClient.tracker().media().command(b == 0 ? "previous" : b == 1 ? "playpause" : "next");
        return true;
    }

    private int buttonAt(double mx, double my) {
        if (controlsY == 0 || Math.abs(my - controlsY) > 12) {
            return -1;
        }
        if (Math.abs(mx - (x + 10)) <= 9) {
            return 0;
        }
        if (Math.abs(mx - (x + 40)) <= 12) {
            return 1;
        }
        if (Math.abs(mx - (x + 70)) <= 9) {
            return 2;
        }
        return -1;
    }

    private static int paragraph(Draw d, String text, int x, int y, int color, int maxLines) {
        List<String> lines = Widget.Paragraph.wrap(d, text, WIDTH);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            String line = i == maxLines - 1 && lines.size() > maxLines ? d.ellipsize(lines.get(i) + " ...", WIDTH) : lines.get(i);
            d.text(line, x, y, color, true);
            y += 10;
        }
        return y;
    }

    /** Filled circle, one row at a time. */
    private static void disc(Draw d, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
            d.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    /** Thin circle outline (a groove of the record). */
    private static void ring(Draw d, int cx, int cy, int r, int color) {
        int steps = Math.max(24, r * 6);
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int px = cx + (int) Math.round(Math.cos(a) * r);
            int py = cy + (int) Math.round(Math.sin(a) * r);
            d.fill(px, py, px + 1, py + 1, color);
        }
    }
}
