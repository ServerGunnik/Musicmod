package dev.servergunnik.musicmod.sky;

import dev.servergunnik.musicmod.MusicmodClient;
import dev.servergunnik.musicmod.api.Span;
import dev.servergunnik.musicmod.api.WorldText;
import dev.servergunnik.musicmod.compat.GameState;
import dev.servergunnik.musicmod.compat.TextEntities;
import dev.servergunnik.musicmod.core.Config;
import dev.servergunnik.musicmod.core.LineColoring;
import dev.servergunnik.musicmod.core.LyricsProvider;
import dev.servergunnik.musicmod.core.LyricsTracker;
import dev.servergunnik.musicmod.core.Lyrics;

import java.util.List;
import java.util.Objects;

/**
 * Lyrics floating in the sky, in front of the player. Uses client-only text
 * display entities, so they are lit, depth-tested (blocks in front hide them)
 * and always face the camera.
 */
public final class SkyLyrics {

    private static final int FADE_TICKS = 6;

    private WorldText main;
    private WorldText next;
    private Object level;

    private Lyrics.Line shownLine;
    private int lineAge;
    private List<Span> lastMain;
    private List<Span> lastNext;

    private boolean anchored;
    private double ax;
    private double ay;
    private double az;

    public void tick() {
        Config c = MusicmodClient.config();
        if (!GameState.inWorld()) {
            clear();
            level = null;
            return;
        }
        if (GameState.levelToken() != level) {
            // New world/dimension: the old entities belong to the old level.
            clear();
            level = GameState.levelToken();
        }
        LyricsTracker.Snapshot s = c.showSky() ? MusicmodClient.tracker().snapshot() : null;
        if (s == null || !s.media().active() || s.status() != LyricsProvider.Status.FOUND || !s.hasLine()) {
            clear();
            return;
        }

        Lyrics.Line line = s.line();
        boolean gap = s.lineFinished() || line.isBlank();
        if (line != shownLine) {
            shownLine = line;
            lineAge = 0;
            if (!c.followView) {
                anchored = false; // a new line appears where the player looks now
            }
        } else {
            lineAge++;
        }

        updateAnchor(c);
        float appear = c.animation ? Math.min(1f, lineAge / (float) FADE_TICKS) : 1f;
        float ease = 1f - (1f - appear) * (1f - appear);
        float scale = c.skyScale;
        double rise = (1f - ease) * 0.6 * scale;

        // Current line.
        List<Span> spans = gap
                ? List.of(new Span("♪", c.theme.upcoming))
                : LineColoring.spans(line, s.timeMs(), c.highlight, c.theme);
        main = ensure(main);
        if (main == null) {
            return;
        }
        if (!Objects.equals(spans, lastMain)) {
            main.setText(spans);
            lastMain = spans;
        }
        main.setScale(scale);
        main.setBackground(c.skyBackground ? (c.theme.panel & 0x00FFFFFF) | 0x80000000 : 0);
        main.setOpacity(Math.round(60 + 195 * ease));
        main.setPos(ax, ay - rise, az);

        // Next line, smaller and dimmer, under the current one.
        Lyrics.Line nl = s.nextLine();
        if (c.nextLine && nl != null && !nl.isBlank()) {
            List<Span> ns = LineColoring.plain(nl, c.theme.upcoming);
            next = ensure(next);
            if (next != null) {
                if (!Objects.equals(ns, lastNext)) {
                    next.setText(ns);
                    lastNext = ns;
                }
                next.setScale(scale * 0.55f);
                next.setBackground(0);
                next.setOpacity(Math.round(40 + 120 * ease));
                next.setPos(ax, ay - rise - 0.32 * scale, az);
            }
        } else if (next != null) {
            next.remove();
            next = null;
            lastNext = null;
        }
    }

    private void updateAnchor(Config c) {
        double yaw = Math.toRadians(GameState.yaw());
        double tx = GameState.x() - Math.sin(yaw) * c.distance;
        double tz = GameState.z() + Math.cos(yaw) * c.distance;
        double ty = GameState.eyeY() + c.height;
        if (!anchored) {
            ax = tx;
            ay = ty;
            az = tz;
            anchored = true;
        } else if (c.followView) {
            ax += (tx - ax) * 0.25;
            ay += (ty - ay) * 0.25;
            az += (tz - az) * 0.25;
        }
    }

    private WorldText ensure(WorldText t) {
        if (t != null && t.alive()) {
            return t;
        }
        lastMain = t == main ? null : lastMain;
        lastNext = t == next ? null : lastNext;
        return TextEntities.spawn();
    }

    private void clear() {
        if (main != null) {
            main.remove();
            main = null;
        }
        if (next != null) {
            next.remove();
            next = null;
        }
        lastMain = null;
        lastNext = null;
        shownLine = null;
        anchored = false;
    }
}
