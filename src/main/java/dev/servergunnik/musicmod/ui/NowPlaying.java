package dev.servergunnik.musicmod.ui;

import dev.servergunnik.musicmod.MusicmodClient;
import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.compat.Textures;
import dev.servergunnik.musicmod.core.LyricsTracker;
import dev.servergunnik.musicmod.core.MediaInfo;
import dev.servergunnik.musicmod.core.MediaSource;
import dev.servergunnik.musicmod.core.Theme;

/** "Now playing" card: cover placeholder, title, artist, lyrics status, progress, controls. */
final class NowPlaying extends Widget {

    private static final String[] BUTTONS = {"|◀", "▶", "▶|"};
    private static final String[] COMMANDS = {"previous", "playpause", "next"};

    // Shared by every card: the cover is one runtime texture.
    private static int coverVersion = -1;
    private static boolean hasCover;

    int measure(Draw d, int width) {
        return 46;
    }

    private int buttonX(int i) {
        return x + w - 6 - (3 - i) * 20;
    }

    void render(Draw d, double mx, double my, boolean hovered) {
        Theme th = MenuUi.theme();
        LyricsTracker.Snapshot s = MusicmodClient.tracker().snapshot();
        MediaInfo m = s.media();
        d.rect(x, y, x + w, y + h, 0x1EFFFFFF, 0x30FFFFFF);

        // Album cover (a note on the theme color when the player gives no picture).
        MediaSource source = MusicmodClient.tracker().media();
        if (source.coverVersion() != coverVersion) {
            coverVersion = source.coverVersion();
            java.nio.file.Path file = source.coverFile();
            hasCover = file != null && Textures.load("dynamic/cover", file);
        }
        if (hasCover && m.active()) {
            d.image("dynamic/cover", x + 6, y + 6, 28, 28);
        } else {
            d.rect(x + 6, y + 6, x + 34, y + 34, (th.sung & 0x00FFFFFF) | 0x90000000, 0);
            d.push(x + 14, y + 12, 2f);
            d.text("♪", 0, 0, 0xFFFFFFFF, true);
            d.pop();
        }

        int tx = x + 40;
        int maxText = w - 40 - 72;
        if (!m.active()) {
            d.text(MenuUi.tr("musicmod.player.nothing"), tx, y + 7, MenuUi.TEXT, false);
            small(d, d.ellipsize(statusHint(), Math.round(maxText / 0.75f)), tx, y + 19, MenuUi.DIM);
        } else {
            d.text(d.ellipsize(m.title(), maxText), tx, y + 6, MenuUi.TEXT, false);
            small(d, d.ellipsize(m.artist(), Math.round(maxText / 0.75f)), tx, y + 16, MenuUi.DIM);
            String status;
            int color;
            switch (s.status()) {
                case FOUND -> {
                    status = MenuUi.tr("musicmod.lyrics.synced");
                    color = 0xFF6BE38B;
                }
                case LOADING -> {
                    status = MenuUi.tr("musicmod.lyrics.loading");
                    color = 0xFFE8C547;
                }
                case ERROR -> {
                    status = MenuUi.tr("musicmod.lyrics.error");
                    color = 0xFFE5534B;
                }
                default -> {
                    status = MenuUi.tr("musicmod.lyrics.not_found");
                    color = 0xFFE5534B;
                }
            }
            String time = MediaInfo.formatTime(m.positionMs()) + " / " + MediaInfo.formatTime(m.durationMs());
            small(d, "● " + status, tx, y + 25, color);
            small(d, time, tx + smallWidth(d, "● " + status) + 8, y + 25, MenuUi.DIM);
        }

        // Progress
        int barY = y + h - 6;
        d.fill(x + 6, barY, x + w - 6, barY + 2, 0x30FFFFFF);
        if (m.active() && m.durationMs() > 0) {
            d.fill(x + 6, barY, x + 6 + Math.round((w - 12) * m.progress()), barY + 2, th.accentArgb());
        }

        // Controls
        boolean enabled = MusicmodClient.tracker().media().status() == MediaSource.Status.RUNNING;
        for (int i = 0; i < 3; i++) {
            int bx = buttonX(i);
            boolean hov = enabled && mx >= bx && mx < bx + 18 && my >= y + 10 && my < y + 26;
            d.rect(bx, y + 10, bx + 18, y + 26, hov ? th.accentArgb() : 0x26FFFFFF, 0);
            String label = i == 1 && m.playing() ? "||" : BUTTONS[i];
            d.centered(label, bx + 9, y + 14, enabled ? (hov ? 0xFF111111 : MenuUi.TEXT) : MenuUi.DIM, false);
        }
    }

    private static String statusHint() {
        return switch (MusicmodClient.tracker().media().status()) {
            case RUNNING -> MenuUi.tr("musicmod.player.hint");
            case STARTING -> MenuUi.tr("musicmod.player.starting");
            case UNSUPPORTED_OS -> MenuUi.tr("musicmod.player.unsupported");
            case FAILED -> MenuUi.tr("musicmod.player.failed");
        };
    }

    boolean click(double mx, double my, int button) {
        if (my < y + 10 || my >= y + 26) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            int bx = buttonX(i);
            if (mx >= bx && mx < bx + 18) {
                MusicmodClient.tracker().media().command(COMMANDS[i]);
                return true;
            }
        }
        return false;
    }
}
