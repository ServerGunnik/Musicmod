package dev.servergunnik.musicmod.ui;

import dev.servergunnik.musicmod.CustomCapes;
import dev.servergunnik.musicmod.MusicmodClient;
import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.core.CapeList;
import dev.servergunnik.musicmod.core.Config;
import dev.servergunnik.musicmod.core.LyricFont;
import dev.servergunnik.musicmod.core.MediaInfo;
import dev.servergunnik.musicmod.core.Sky;
import dev.servergunnik.musicmod.core.Styles;

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
        // Capes: tiles with a picture of the design. Only you see your cape.
        List<CapeList.Cape> capes = CapeList.all();
        List<CustomCapes.Cape> mine = CustomCapes.all();
        out.add(new Widget.Header("musicmod.section.cape"));
        int total = 1 + capes.size() + mine.size();
        out.add(new Widget.OptionGrid(5, total,
                i -> i == 0 ? tr("musicmod.cape.none")
                        : i <= capes.size() ? capes.get(i - 1).label() : mine.get(i - 1 - capes.size()).label(), null,
                () -> {
                    for (int i = 0; i < capes.size(); i++) {
                        if (capes.get(i).id().equals(cfg().cape)) {
                            return i + 1;
                        }
                    }
                    for (int i = 0; i < mine.size(); i++) {
                        if (mine.get(i).id().equals(cfg().cape)) {
                            return i + 1 + capes.size();
                        }
                    }
                    return 0;
                },
                i -> {
                    cfg().cape = i == 0 ? "" : i <= capes.size() ? capes.get(i - 1).id() : mine.get(i - 1 - capes.size()).id();
                    ui.changed();
                },
                (d, i, x, y, w, h) -> {
                    int iw = h * 40 / 64;   // the design is 40x64
                    int ix = x + (w - iw) / 2;
                    if (i == 0) {
                        d.rect(ix, y, ix + iw, y + h, 0x30FFFFFF, 0x80FFFFFF);
                        d.centered("\u00D7", x + w / 2, y + h / 2 - 4, MenuUi.DIM, false);
                    } else if (i <= capes.size()) {
                        d.image("textures/cape/" + capes.get(i - 1).id() + "_icon.png", ix, y, iw, h);
                    } else {
                        d.image(mine.get(i - 1 - capes.size()).icon(), ix, y, iw, h);
                    }
                }, 48));
        out.add(new Widget.Button("musicmod.btn.reload_capes", () -> {
            CustomCapes.reload();
            ui.rebuild();
        }));
        out.add(new Widget.Paragraph(() -> tr("musicmod.cape.help") + "\n" + tr("musicmod.cape.custom") + " "
                + CustomCapes.folder(), MenuUi.DIM));
        // PvP styles: recolored textures, one grid per item/block.
        for (String kind : Styles.KINDS) {
            List<Styles.Style> list = Styles.of(kind);
            if (list.isEmpty()) {
                continue;
            }
            out.add(new Widget.Header("musicmod.style." + kind));
            out.add(new Widget.OptionGrid(4, list.size() + 1,
                    i -> i == 0 ? tr("musicmod.style.default") : list.get(i - 1).label(), null,
                    () -> {
                        String chosen = cfg().styles.getOrDefault(kind, "");
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).id().equals(chosen)) {
                                return i + 1;
                            }
                        }
                        return 0;
                    },
                    i -> {
                        cfg().styles.put(kind, i == 0 ? "" : list.get(i - 1).id());
                        MusicmodClient.saveConfig();
                        MusicmodClient.applySky();
                        ui.rebuild();
                    },
                    (d, i, x, y, w, h) -> {
                        // Swatch in the style's color (a crossed box for the vanilla look).
                        int cx = x + w / 2;
                        if (i == 0) {
                            d.rect(cx - 7, y + 1, cx + 7, y + h - 1, 0x30FFFFFF, 0x80FFFFFF);
                            d.centered("\u00D7", cx, y + h / 2 - 4, MenuUi.DIM, false);
                        } else {
                            int col = list.get(i - 1).color();
                            d.rect(cx - 7, y + 1, cx + 7, y + h - 1, 0xFF000000 | col, 0xFF000000 | mix(col, 0xFFFFFF, 0.45f));
                            d.fill(cx - 5, y + 3, cx - 1, y + 6, 0xFF000000 | mix(col, 0xFFFFFF, 0.6f));
                        }
                    }, 18));
        }
        out.add(new Widget.Paragraph(() -> MusicmodClient.skyStatus().isEmpty() ? ""
                : tr("musicmod.pack.status." + MusicmodClient.skyStatus()), 0xFFE8C547));
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
                Pages::paintSky, 44));
        out.add(new Widget.Toggle(ui, "musicmod.opt.sky_animated", () -> cfg().skyAnimated, v -> {
            cfg().skyAnimated = v;
            MusicmodClient.saveConfig();
            MusicmodClient.applySky();
        }));
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
        out.add(new Widget.Toggle(ui, "musicmod.opt.cover_online", () -> cfg().coverOnline, v -> {
            cfg().coverOnline = v;
            dev.servergunnik.musicmod.core.MediaSource.onlineCovers = v;
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

    /** Thumbnail of a sky (rendered by tools/gen_skies.py), with a thin frame. */
    private static void paintSky(Draw d, int index, int x, int y, int w, int h) {
        Sky sky = Sky.values()[index];
        d.image("textures/gui/sky/" + (sky.id == null ? "off" : sky.id) + ".png", x, y, w, h);
        d.fill(x, y, x + w, y + 1, 0x40000000);
        d.fill(x, y + h - 1, x + w, y + h, 0x40000000);
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
