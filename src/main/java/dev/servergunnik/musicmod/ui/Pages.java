package dev.servergunnik.musicmod.ui;

import dev.servergunnik.musicmod.MusicmodClient;
import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.core.Config;
import dev.servergunnik.musicmod.core.LyricFont;
import dev.servergunnik.musicmod.core.MediaInfo;
import dev.servergunnik.musicmod.core.Sky;

import java.util.List;

import static dev.servergunnik.musicmod.ui.MenuUi.cfg;
import static dev.servergunnik.musicmod.ui.MenuUi.tr;

/** Contents of each menu tab. */
final class Pages {

    private Pages() {
    }

    static void build(MenuUi.Tab tab, List<Widget> out, MenuUi ui) {
        switch (tab) {
            case OVERVIEW -> overview(out, ui);
            case LYRICS -> lyrics(out, ui);
            case STYLES -> styles(out, ui);
            case HUD -> hud(out, ui);
            case SKIES -> skies(out, ui);
            case SETTINGS -> settings(out, ui);
            case INFO -> info(out);
        }
    }

    private static void overview(List<Widget> out, MenuUi ui) {
        out.add(new Widget.Banner());
        out.add(new Widget.Cards(ui, MenuUi.Tab.SKIES, MenuUi.Tab.LYRICS, MenuUi.Tab.STYLES,
                MenuUi.Tab.HUD, MenuUi.Tab.SETTINGS, MenuUi.Tab.INFO));
        out.add(new Widget.Header("musicmod.section.now_playing"));
        out.add(new NowPlaying());
        out.add(new Widget.Header("musicmod.section.quick"));
        out.add(new Widget.Toggle(ui, "musicmod.opt.lyrics", () -> cfg().lyricsEnabled, v -> cfg().lyricsEnabled = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud", () -> cfg().hudEnabled, v -> cfg().hudEnabled = v));
        out.add(new Widget.Paragraph(() -> tr("musicmod.overview.help"), MenuUi.DIM));
    }

    private static void lyrics(List<Widget> out, MenuUi ui) {
        out.add(new NowPlaying());
        out.add(new Widget.Header("musicmod.section.general"));
        out.add(new Widget.Toggle(ui, "musicmod.opt.lyrics", () -> cfg().lyricsEnabled, v -> cfg().lyricsEnabled = v));
        out.add(new Widget.Header("musicmod.opt.display"));
        out.add(enumGrid(ui, 3, Config.Display.values(), "musicmod.display.", () -> cfg().display, v -> cfg().display = v));
        out.add(new Widget.Header("musicmod.opt.highlight"));
        out.add(enumGrid(ui, 3, Config.Highlight.values(), "musicmod.highlight.", () -> cfg().highlight, v -> cfg().highlight = v));
        out.add(new Widget.Header("musicmod.opt.font"));
        out.add(fontGrid(ui));
        out.add(new Widget.Toggle(ui, "musicmod.opt.next_line", () -> cfg().nextLine, v -> cfg().nextLine = v));
        out.add(new Widget.Slider(ui, "musicmod.opt.offset", -2000, 2000, 50,
                () -> cfg().offsetMs, v -> cfg().offsetMs = v, v -> (v > 0 ? "+" : "") + v + " ms"));

        out.add(new Widget.Header("musicmod.section.sky"));
        out.add(new Widget.Toggle(ui, "musicmod.opt.follow", () -> cfg().followView, v -> cfg().followView = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.animation", () -> cfg().animation, v -> cfg().animation = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.sky_background", () -> cfg().skyBackground, v -> cfg().skyBackground = v));
        out.add(new Widget.Slider(ui, "musicmod.opt.distance", 8, 80, 1,
                () -> cfg().distance, v -> cfg().distance = v, v -> v + " m"));
        out.add(new Widget.Slider(ui, "musicmod.opt.height", -8, 40, 1,
                () -> cfg().height, v -> cfg().height = v, v -> (v > 0 ? "+" : "") + v + " m"));
        out.add(new Widget.Slider(ui, "musicmod.opt.size", 5, 120, 5,
                () -> Math.round(cfg().skyScale * 10), v -> cfg().skyScale = v / 10f, v -> String.format(java.util.Locale.ROOT, "%.1fx", v / 10f)));
    }

    private static void styles(List<Widget> out, MenuUi ui) {
        out.add(new Widget.Header("musicmod.section.look"));
        out.add(new Widget.ThemeGrid(ui));
        out.add(new Widget.Header("musicmod.section.font"));
        out.add(fontGrid(ui));
        out.add(new Widget.Paragraph(() -> tr("musicmod.styles.help"), MenuUi.DIM));
    }

    private static void hud(List<Widget> out, MenuUi ui) {
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud", () -> cfg().hudEnabled, v -> cfg().hudEnabled = v));
        out.add(new Widget.Header("musicmod.section.position"));
        out.add(new Widget.Button("musicmod.btn.move", ui::startMoving));
        out.add(new Widget.Button("musicmod.btn.reset_positions", () -> {
            Config c = cfg();
            c.pillDx = c.pillDy = c.barDx = c.barDy = c.infoDx = c.infoDy = 0;
            ui.changed();
        }));
        out.add(new Widget.Header("musicmod.section.top"));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_song", () -> cfg().hudSong, v -> cfg().hudSong = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_song_time", () -> cfg().hudSongTime, v -> cfg().hudSongTime = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_clock", () -> cfg().hudClock, v -> cfg().hudClock = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_clock24", () -> cfg().hudClock24h, v -> cfg().hudClock24h = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_fps", () -> cfg().hudFps, v -> cfg().hudFps = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_ping", () -> cfg().hudPing, v -> cfg().hudPing = v));
        out.add(new Widget.Header("musicmod.section.bottom"));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_coords", () -> cfg().hudCoords, v -> cfg().hudCoords = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_speed", () -> cfg().hudSpeed, v -> cfg().hudSpeed = v));
    }

    private static void skies(List<Widget> out, MenuUi ui) {
        if (!MusicmodClient.skyRendererPresent()) {
            out.add(new Widget.Paragraph(() -> tr("musicmod.skies.need_mod"), 0xFFE8C547));
        }
        Sky[] skies = Sky.values();
        out.add(new Widget.OptionGrid(3, skies.length,
                i -> skies[i] == Sky.OFF ? tr("musicmod.sky.off") : skies[i].label, null,
                () -> cfg().sky.ordinal(),
                i -> {
                    // Picking a sky applies it right away (the game reloads its resources).
                    cfg().sky = skies[i];
                    MusicmodClient.saveConfig();
                    MusicmodClient.applySky();
                    ui.rebuild(); // the status text below changed
                },
                Pages::paintSky, 30));
        out.add(new Widget.Paragraph(() -> MusicmodClient.skyStatus().isEmpty() ? ""
                : tr("musicmod.sky.status." + MusicmodClient.skyStatus()), 0xFFE8C547));
        out.add(new Widget.Paragraph(() -> tr("musicmod.skies.help"), MenuUi.DIM));
    }

    private static void settings(List<Widget> out, MenuUi ui) {
        out.add(new Widget.Header("musicmod.section.lyrics"));
        out.add(new Widget.Button("musicmod.btn.retry", () -> {
            MediaInfo m = MusicmodClient.tracker().media().current();
            if (m.active()) {
                MusicmodClient.tracker().provider().retry(m);
            }
        }));
        out.add(new Widget.Header("musicmod.section.reset"));
        out.add(new Widget.Button("musicmod.btn.reset", () -> {
            MusicmodClient.resetConfig();
            ui.afterReset();
        }));
        out.add(new Widget.Paragraph(() -> tr("musicmod.settings.help"), MenuUi.DIM));
    }

    private static void info(List<Widget> out) {
        out.add(new Widget.Paragraph(() -> "Musicmod v" + MusicmodClient.version(), MenuUi.TEXT));
        out.add(new Widget.Paragraph(() -> tr("musicmod.info.text"), MenuUi.DIM));
    }

    private static Widget fontGrid(MenuUi ui) {
        LyricFont[] fonts = LyricFont.values();
        return new Widget.OptionGrid(3, fonts.length, i -> fonts[i].label, i -> fonts[i].id,
                () -> cfg().font.ordinal(),
                i -> {
                    cfg().font = fonts[i];
                    ui.changed();
                }, null, 0);
    }

    private static <E extends Enum<E>> Widget enumGrid(MenuUi ui, int cols, E[] values, String keyPrefix,
                                                       java.util.function.Supplier<E> get, java.util.function.Consumer<E> set) {
        return new Widget.OptionGrid(cols, values.length, i -> tr(keyPrefix + values[i].name().toLowerCase()), null,
                () -> get.get().ordinal(),
                i -> {
                    set.accept(values[i]);
                    ui.changed();
                }, null, 0);
    }

    /** Small picture of a sky: its colors from zenith to horizon, with a few stars for night skies. */
    private static void paintSky(Draw d, int index, int x, int y, int w, int h) {
        Sky sky = Sky.values()[index];
        int[] c = sky.preview;
        for (int row = 0; row < h; row++) {
            float t = row / (float) Math.max(1, h - 1) * (c.length - 1);
            int i = Math.min(c.length - 2, (int) t);
            d.fill(x, y + row, x + w, y + row + 1, 0xFF000000 | mix(c[i], c[i + 1], t - i));
        }
        if (sky.stars) {
            int seed = 7 + index * 131;
            for (int k = 0; k < 14; k++) {
                seed = seed * 1103515245 + 12345;
                int sx = x + 1 + ((seed >>> 16) & 0x7FFF) % Math.max(1, w - 2);
                seed = seed * 1103515245 + 12345;
                int sy = y + 1 + ((seed >>> 16) & 0x7FFF) % Math.max(1, h / 2);
                d.fill(sx, sy, sx + 1, sy + 1, 0xE0FFFFFF);
            }
        } else if (sky != Sky.OFF) {
            // Sunsets: a small sun on the horizon.
            d.fill(x + w / 2 - 2, y + h - 6, x + w / 2 + 2, y + h - 2, 0xFFFFE9B0);
        }
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static <E extends Enum<E>> E cycle(E[] values, E current, int dir) {
        int i = (current.ordinal() + dir + values.length) % values.length;
        return values[i];
    }
}
