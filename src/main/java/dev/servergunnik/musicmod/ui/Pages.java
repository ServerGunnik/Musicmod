package dev.servergunnik.musicmod.ui;

import dev.servergunnik.musicmod.MusicmodClient;
import dev.servergunnik.musicmod.core.Config;
import dev.servergunnik.musicmod.core.MediaInfo;

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
            case SKIES -> skies(out);
            case SETTINGS -> settings(out, ui);
            case INFO -> info(out);
        }
    }

    private static void overview(List<Widget> out, MenuUi ui) {
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
        out.add(new Widget.Choice(ui, "musicmod.opt.display",
                () -> tr("musicmod.display." + cfg().display.name().toLowerCase()),
                dir -> cfg().display = cycle(Config.Display.values(), cfg().display, dir)));
        out.add(new Widget.Choice(ui, "musicmod.opt.highlight",
                () -> tr("musicmod.highlight." + cfg().highlight.name().toLowerCase()),
                dir -> cfg().highlight = cycle(Config.Highlight.values(), cfg().highlight, dir)));
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
        out.add(new Widget.Paragraph(() -> tr("musicmod.styles.help"), MenuUi.DIM));
    }

    private static void hud(List<Widget> out, MenuUi ui) {
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud", () -> cfg().hudEnabled, v -> cfg().hudEnabled = v));
        out.add(new Widget.Header("musicmod.section.top"));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_song", () -> cfg().hudSong, v -> cfg().hudSong = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_clock", () -> cfg().hudClock, v -> cfg().hudClock = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_clock24", () -> cfg().hudClock24h, v -> cfg().hudClock24h = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_fps", () -> cfg().hudFps, v -> cfg().hudFps = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_ping", () -> cfg().hudPing, v -> cfg().hudPing = v));
        out.add(new Widget.Header("musicmod.section.bottom"));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_coords", () -> cfg().hudCoords, v -> cfg().hudCoords = v));
        out.add(new Widget.Toggle(ui, "musicmod.opt.hud_speed", () -> cfg().hudSpeed, v -> cfg().hudSpeed = v));
    }

    private static void skies(List<Widget> out) {
        out.add(new Widget.Paragraph(() -> tr("musicmod.skies.soon"), MenuUi.TEXT));
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

    static <E extends Enum<E>> E cycle(E[] values, E current, int dir) {
        int i = (current.ordinal() + dir + values.length) % values.length;
        return values[i];
    }
}
