package dev.servergunnik.musicmod.hud;

import dev.servergunnik.musicmod.MusicmodClient;
import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.api.Span;
import dev.servergunnik.musicmod.compat.GameState;
import dev.servergunnik.musicmod.core.Config;
import dev.servergunnik.musicmod.core.LineColoring;
import dev.servergunnik.musicmod.core.LyricsProvider;
import dev.servergunnik.musicmod.core.LyricsTracker;
import dev.servergunnik.musicmod.core.MediaInfo;
import dev.servergunnik.musicmod.core.Theme;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Screen overlay: the "pill" at the top (clock, song, FPS, ping), the lyrics top
 * bar under it, and coordinates/speed next to the hotbar.
 */
public final class HudOverlay {

    private static final DateTimeFormatter CLOCK_24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CLOCK_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final int TEXT = 0xFFF2F2F2;
    private static final int DIM = 0xFF9A9A9A;

    /** Index into {@link #BOUNDS}. */
    public static final int PILL = 0;
    public static final int BAR = 1;
    public static final int INFO = 2;
    /** Where each element was drawn last frame: {x1, y1, x2, y2} (empty when hidden). */
    public static final int[][] BOUNDS = new int[3][4];
    /** True while the menu's "move HUD" mode is open: every element is shown, with sample content. */
    public static volatile boolean editing;

    private double lastX;
    private double lastZ;
    private boolean hasLast;
    private float speed;

    public void tick() {
        if (!GameState.inWorld()) {
            hasLast = false;
            return;
        }
        double x = GameState.x();
        double z = GameState.z();
        if (hasLast) {
            double perSecond = Math.hypot(x - lastX, z - lastZ) * 20.0;
            // Teleports would spike the number: ignore anything absurd.
            if (perSecond < 200) {
                speed = speed * 0.6f + (float) perSecond * 0.4f;
            }
        }
        lastX = x;
        lastZ = z;
        hasLast = true;
    }

    public void render(Draw d) {
        if (!GameState.inWorld() || GameState.hudHidden()) {
            return;
        }
        Config c = MusicmodClient.config();
        Theme th = c.theme;
        LyricsTracker.Snapshot s = MusicmodClient.tracker().snapshot();
        for (int[] b : BOUNDS) {
            java.util.Arrays.fill(b, 0);
        }
        if (c.hudEnabled) {
            renderPill(d, c, th, s, 4 + c.pillDy);
        }
        if (c.showTopBar() && (s.media().active() || editing)) {
            // Default spot: right under the pill's default spot.
            renderTopBar(d, c, th, s, 4 + d.lineHeight() + 9 + 4 + c.barDy);
        }
        if (c.hudEnabled && (c.hudCoords || c.hudSpeed)) {
            renderInfo(d, c, th);
        }
    }

    private static void bounds(int i, int x1, int y1, int x2, int y2) {
        BOUNDS[i][0] = x1;
        BOUNDS[i][1] = y1;
        BOUNDS[i][2] = x2;
        BOUNDS[i][3] = y2;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }

    // ------------------------------------------------------------------ pill

    private interface Segment {
        int width(Draw d);

        void draw(Draw d, int x, int y);
    }

    private int renderPill(Draw d, Config c, Theme th, LyricsTracker.Snapshot s, int y) {
        List<Segment> segs = new ArrayList<>();
        if (c.hudClock) {
            String time = LocalTime.now().format(c.hudClock24h ? CLOCK_24 : CLOCK_12);
            segs.add(textSegment(time, TEXT));
        }
        MediaInfo m = s.media();
        int songIndex = -1;
        if (c.hudSong && m.active()) {
            songIndex = segs.size();
            String label = m.artist().isEmpty() ? m.title() : m.title() + " – " + m.artist();
            // Where we are in the song: "1:23 / 3:45" (only the elapsed time when the player gives no length).
            String time = !c.hudSongTime ? ""
                    : m.durationMs() > 0 ? MediaInfo.formatTime(m.positionMs()) + " / " + MediaInfo.formatTime(m.durationMs())
                    : MediaInfo.formatTime(m.positionMs());
            segs.add(new Segment() {
                public int width(Draw d) {
                    return 13 + d.textWidth(d.ellipsize(label, 150)) + (time.isEmpty() ? 0 : 6 + d.textWidth(time));
                }

                public void draw(Draw d, int x, int y) {
                    drawBars(d, x, y + d.lineHeight() - 1, m.playing(), th.accentArgb());
                    int end = d.text(d.ellipsize(label, 150), x + 13, y, TEXT, false);
                    if (!time.isEmpty()) {
                        d.text(time, end + 6, y, th.accentArgb(), false);
                    }
                }
            });
        }
        if (c.hudFps) {
            segs.add(textSegment(GameState.fps() + " FPS", TEXT));
        }
        int ping = GameState.ping();
        if (c.hudPing && ping >= 0) {
            segs.add(new Segment() {
                final String label = ping + " ms";

                public int width(Draw d) {
                    return d.textWidth(label) + 10;
                }

                public void draw(Draw d, int x, int y) {
                    d.text(label, x, y, TEXT, false);
                    drawSignal(d, x + d.textWidth(label) + 3, y + d.lineHeight() - 2, ping);
                }
            });
        }
        if (segs.isEmpty()) {
            return y;
        }

        int pad = 7;
        int gap = 11;
        int total = 0;
        int[] widths = new int[segs.size()];
        for (int i = 0; i < segs.size(); i++) {
            widths[i] = segs.get(i).width(d);
            total += widths[i];
        }
        total += gap * (segs.size() - 1) + pad * 2;
        int h = d.lineHeight() + 9;
        int x = clamp((d.width() - total) / 2 + c.pillDx, 0, d.width() - total);
        y = clamp(y, 0, d.height() - h);
        bounds(PILL, x, y, x + total, y + h);
        d.rect(x, y, x + total, y + h, th.panel, (th.border & 0x00FFFFFF) | 0xC0000000);

        int cx = x + pad;
        int ty = y + 4;
        for (int i = 0; i < segs.size(); i++) {
            segs.get(i).draw(d, cx, ty);
            if (i == songIndex && m.durationMs() > 0) {
                // Progress bar under the song, with a marker at the current position.
                int barY = y + h - 3;
                int fillW = Math.round(widths[i] * m.progress());
                d.fill(cx, barY, cx + widths[i], barY + 2, 0x60FFFFFF);
                d.fill(cx, barY, cx + fillW, barY + 2, th.accentArgb());
                d.fill(cx + fillW - 1, barY - 1, cx + fillW + 1, barY + 3, 0xFFFFFFFF);
            }
            cx += widths[i];
            if (i + 1 < segs.size()) {
                d.text("·", cx + gap / 2 - 1, ty, DIM, false);
                cx += gap;
            }
        }
        return y + h;
    }

    private static Segment textSegment(String text, int color) {
        return new Segment() {
            public int width(Draw d) {
                return d.textWidth(text);
            }

            public void draw(Draw d, int x, int y) {
                d.text(text, x, y, color, false);
            }
        };
    }

    /** Four little equalizer bars, animated while music plays. */
    private static void drawBars(Draw d, int x, int baseY, boolean playing, int color) {
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 4; i++) {
            int h = playing ? 2 + (int) Math.round(5 * Math.abs(Math.sin(t * (3.1 + i * 0.9) + i * 1.7))) : 2;
            int bx = x + i * 3;
            d.fill(bx, baseY - h, bx + 2, baseY, color);
        }
    }

    private static void drawSignal(Draw d, int x, int baseY, int ping) {
        int bars = ping < 60 ? 4 : ping < 120 ? 3 : ping < 250 ? 2 : 1;
        int color = ping < 120 ? 0xFF6BE38B : ping < 250 ? 0xFFE8C547 : 0xFFE5534B;
        for (int i = 0; i < 4; i++) {
            int h = 2 + i * 2;
            d.fill(x + i * 2, baseY - h, x + i * 2 + 1, baseY, i < bars ? color : 0x50FFFFFF);
        }
    }

    // --------------------------------------------------------------- top bar

    private void renderTopBar(Draw d, Config c, Theme th, LyricsTracker.Snapshot s, int y) {
        List<Span> spans;
        String font = c.font.id;
        if (editing && s.status() != LyricsProvider.Status.FOUND) {
            spans = List.of(new Span("\u266A ", th.sung), new Span("Musicmod ", th.current), new Span("lyrics", th.upcoming));
        } else if (s.status() == LyricsProvider.Status.FOUND) {
            if (s.hasLine() && !s.lineFinished()) {
                spans = LineColoring.spans(s.line(), s.timeMs(), c.highlight, th);
            } else {
                spans = List.of(new Span("♪ ♪ ♪", th.upcoming));
            }
        } else {
            String key = switch (s.status()) {
                case LOADING -> "musicmod.lyrics.loading";
                case NOT_FOUND -> "musicmod.lyrics.not_found";
                case ERROR -> "musicmod.lyrics.error";
                default -> null;
            };
            if (key == null) {
                return;
            }
            spans = List.of(new Span(MusicmodClient.tr(key), th.upcoming));
        }

        int w = 0;
        for (Span sp : spans) {
            w += d.textWidth(sp.text(), font);
        }
        boolean wordMode = c.highlight == Config.Highlight.REVEAL || c.highlight == Config.Highlight.SINGLE;
        if (wordMode && s.status() == LyricsProvider.Status.FOUND && s.hasLine() && !s.lineFinished()) {
            // Words appear one by one: size the bar for the whole line so it does not jump.
            w = Math.max(w, d.textWidth(s.line().text(), font));
        }
        int maxW = d.width() - 40;
        float scale = w > maxW ? Math.max(0.6f, maxW / (float) w) : 1f;
        int boxW = Math.round(w * scale) + 16;
        int h = d.lineHeight() + 8;
        int x = clamp((d.width() - boxW) / 2 + c.barDx, 0, d.width() - boxW);
        y = clamp(y, 0, d.height() - h);
        bounds(BAR, x, y, x + boxW, y + h);
        d.rect(x, y, x + boxW, y + h, (th.panel & 0x00FFFFFF) | 0xA0000000, 0);

        int spansW = 0;
        for (Span sp : spans) {
            spansW += d.textWidth(sp.text(), font);
        }
        d.push(x + 8, y + 4, scale);
        // A single word sits in the middle of the bar; everything else starts at the left.
        int cx = c.highlight == Config.Highlight.SINGLE ? Math.max(0, (w - spansW) / 2) : 0;
        for (Span sp : spans) {
            d.text(sp.text(), cx, 0, 0xFF000000 | sp.rgb(), true, font);
            cx += d.textWidth(sp.text(), font);
        }
        d.pop();

        if (c.nextLine && s.status() == LyricsProvider.Status.FOUND) {
            var next = s.nextLine();
            if (next != null && !next.isBlank()) {
                String text = d.ellipsize(next.text(), maxW);
                d.push(x + boxW / 2f, y + h + 3, 0.8f);
                d.text(text, -d.textWidth(text, font) / 2, 0, 0xC0000000 | th.upcoming, true, font);
                d.pop();
            }
        }
    }

    // ----------------------------------------------------------- coords/speed

    private void renderInfo(Draw d, Config c, Theme th) {
        StringBuilder sb = new StringBuilder();
        if (c.hudCoords) {
            sb.append((int) Math.floor(GameState.x())).append(' ')
                    .append((int) Math.floor(GameState.y())).append(' ')
                    .append((int) Math.floor(GameState.z()));
        }
        if (c.hudSpeed) {
            if (!sb.isEmpty()) {
                sb.append("  ·  ");
            }
            sb.append(String.format(Locale.ROOT, "%.1f b/s", speed));
        }
        String text = sb.toString();
        int w = d.textWidth(text) + 12;
        int h = d.lineHeight() + 6;
        int x = d.width() / 2 + 91 + 6;
        int y = d.height() - h - 2;
        if (x + w > d.width() - 2) {
            x = d.width() - w - 2;
            y = d.height() - 22 - h - 4;
        }
        x = clamp(x + c.infoDx, 0, d.width() - w);
        y = clamp(y + c.infoDy, 0, d.height() - h);
        bounds(INFO, x, y, x + w, y + h);
        d.rect(x, y, x + w, y + h, th.panel, (th.border & 0x00FFFFFF) | 0x90000000);
        d.text(text, x + 6, y + 3, TEXT, false);
    }
}
