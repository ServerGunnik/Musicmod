package dev.servergunnik.musicmod.core;

/**
 * Glues the media source and the lyrics provider together and answers "what is
 * being sung right now". Call {@link #snapshot} once per frame/tick.
 */
public final class LyricsTracker {

    public record Snapshot(MediaInfo media, LyricsProvider.Status status, Lyrics lyrics, int lineIndex, long timeMs) {

        public boolean hasLine() {
            return lyrics != null && lineIndex >= 0 && lineIndex < lyrics.lines().size();
        }

        public Lyrics.Line line() {
            return hasLine() ? lyrics.lines().get(lineIndex) : null;
        }

        public Lyrics.Line nextLine() {
            if (lyrics == null) {
                return null;
            }
            int i = lineIndex + 1;
            return i >= 0 && i < lyrics.lines().size() ? lyrics.lines().get(i) : null;
        }

        /** True when the current line already ended (instrumental gap before the next one). */
        public boolean lineFinished() {
            Lyrics.Line l = line();
            return l == null || timeMs >= l.endMs();
        }
    }

    private static final Snapshot EMPTY = new Snapshot(MediaInfo.NONE, LyricsProvider.Status.IDLE, null, -1, 0);

    private final MediaSource media;
    private final LyricsProvider provider;
    private final java.util.function.Supplier<Config> config;

    public LyricsTracker(MediaSource media, LyricsProvider provider, java.util.function.Supplier<Config> config) {
        this.media = media;
        this.provider = provider;
        this.config = config;
    }

    public MediaSource media() {
        return media;
    }

    public LyricsProvider provider() {
        return provider;
    }

    public Snapshot snapshot() {
        MediaInfo m = media.current();
        if (!m.active()) {
            return EMPTY;
        }
        LyricsProvider.Result r = provider.get(m);
        long t = m.positionMs() + config.get().offsetMs;
        int idx = r.lyrics() == null ? -1 : r.lyrics().lineAt(t);
        return new Snapshot(m, r.status(), r.lyrics(), idx, t);
    }
}
