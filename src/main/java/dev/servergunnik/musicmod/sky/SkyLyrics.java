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
 * Lyrics in the world, as client-only text display entities (depth-tested, so
 * blocks in front hide them).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Pinned</b> (default, "follow view" off): each new line appears where the
 *       player is looking at that moment and then stays put. Looking at a block
 *       puts the text flat on that face (wall, floor or ceiling); looking at the
 *       sky puts it {@code distance} blocks away, facing the spot the player
 *       stood on. It never turns or moves afterwards.</li>
 *   <li><b>Follow view</b>: the text floats in front of the player, follows the
 *       view and always faces the camera.</li>
 * </ul>
 */
public final class SkyLyrics {

    private static final int FADE_TICKS = 6;
    /** Height of one text line in blocks at scale 1 (text displays draw 1 px = 1/40 block). */
    private static final double LINE_HEIGHT = 0.25;
    /** Gap between the text and the block face, so it does not z-fight with the wall. */
    private static final double WALL_GAP = 0.03;

    private WorldText main;
    private WorldText next;
    private Object level;

    private Lyrics.Line shownLine;
    private int lineAge;
    private List<Span> lastMain;
    private List<Span> lastNext;

    // Anchor of the current line: position of the text's bottom centre.
    private boolean anchored;
    private boolean wasPinned;
    private double ax;
    private double ay;
    private double az;
    // Pinned mode: orientation and size chosen when the line appeared.
    private float yaw;
    private float pitch;
    private float pinnedScale = 1f;
    /** Unit vector pointing "down" in the plane of the text (where the next line goes). */
    private double dx;
    private double dy = -1;
    private double dz;

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

        boolean pinned = !c.followView;
        if (pinned != wasPinned) {
            anchored = false; // mode switched in the menu: place the text afresh
            wasPinned = pinned;
        }
        float scale;
        if (pinned) {
            if (!anchored) {
                pin(c);
            }
            scale = pinnedScale;
        } else {
            follow(c);
            scale = c.skyScale;
        }

        float appear = c.animation ? Math.min(1f, lineAge / (float) FADE_TICKS) : 1f;
        float ease = 1f - (1f - appear) * (1f - appear);
        // Follow mode lets new lines rise into place; pinned text must not move at all.
        double rise = pinned ? 0 : (1f - ease) * 0.6 * scale;

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
        place(main, pinned, scale, 0, rise);
        main.setBackground(c.skyBackground ? (c.theme.panel & 0x00FFFFFF) | 0x80000000 : 0);
        main.setOpacity(Math.round(60 + 195 * ease));

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
                place(next, pinned, scale * 0.55f, 0.32 * scale, rise);
                next.setBackground(0);
                next.setOpacity(Math.round(40 + 120 * ease));
            }
        } else if (next != null) {
            next.remove();
            next = null;
            lastNext = null;
        }
    }

    /** Positions one text: {@code below} blocks under the anchor, in the plane of the text. */
    private void place(WorldText t, boolean pinned, float scale, double below, double rise) {
        t.setScale(scale);
        t.setBillboard(!pinned);
        if (pinned) {
            t.setFacing(yaw, pitch);
            t.setPos(ax + dx * below, ay + dy * below, az + dz * below);
        } else {
            t.setPos(ax, ay - rise - below, az);
        }
    }

    /** Pinned mode: fix position, orientation and size from what the player looks at right now. */
    private void pin(Config c) {
        double[] t = GameState.lookTarget(c.distance);
        double px = t[0], py = t[1], pz = t[2];
        double nx = t[3], ny = t[4], nz = t[5];
        boolean hit = t[6] > 0;

        if (hit && ny == 0) {
            // Wall: flat on the face, upright.
            yaw = (float) Math.toDegrees(Math.atan2(-nx, nz));
            pitch = 0;
        } else if (hit) {
            // Floor (ny > 0) or ceiling: flat on the face, readable from where the player stands.
            yaw = GameState.yaw() + 180f;
            pitch = ny > 0 ? -90f : 90f;
        } else {
            // Sky: face exactly back at the player.
            yaw = GameState.yaw() + 180f;
            pitch = -GameState.pitch();
        }

        // "Down" within the text plane (rotate (0,-1,0) by pitch, then by yaw).
        double a = Math.toRadians(pitch);
        double th = Math.toRadians(-yaw);
        dx = -Math.sin(a) * Math.sin(th);
        dy = -Math.cos(a);
        dz = -Math.sin(a) * Math.cos(th);

        // Keep the apparent size similar: close surfaces get smaller text.
        double dist = Math.sqrt(sq(px - GameState.x()) + sq(py - GameState.eyeY()) + sq(pz - GameState.z()));
        pinnedScale = (float) (c.skyScale * Math.max(0.12, Math.min(1.0, dist / Math.max(1, c.distance))));

        // Text grows upward from its anchor: start half a line lower so the line is
        // centred on the spot the player looked at, and lift it off the surface.
        double half = LINE_HEIGHT * pinnedScale / 2;
        double gap = hit ? WALL_GAP : 0;
        ax = px + nx * gap + dx * half;
        ay = py + ny * gap + dy * half;
        az = pz + nz * gap + dz * half;
        anchored = true;
    }

    /** Follow mode: float in front of the player and ease toward the view direction. */
    private void follow(Config c) {
        double rad = Math.toRadians(GameState.yaw());
        double tx = GameState.x() - Math.sin(rad) * c.distance;
        double tz = GameState.z() + Math.cos(rad) * c.distance;
        double ty = GameState.eyeY() + c.height;
        if (!anchored) {
            ax = tx;
            ay = ty;
            az = tz;
            anchored = true;
        } else {
            ax += (tx - ax) * 0.25;
            ay += (ty - ay) * 0.25;
            az += (tz - az) * 0.25;
        }
    }

    private static double sq(double v) {
        return v * v;
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
