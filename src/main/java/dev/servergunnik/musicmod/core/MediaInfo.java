package dev.servergunnik.musicmod.core;

/**
 * Snapshot of what the system media player is doing. Positions are extrapolated
 * from the moment the snapshot was taken, so {@link #positionMs()} keeps moving
 * between updates while the track is playing.
 */
public record MediaInfo(
        String app,
        String title,
        String artist,
        String album,
        long durationMs,
        long basePositionMs,
        boolean playing,
        long sampledAtNanos) {

    public static final MediaInfo NONE = new MediaInfo("", "", "", "", 0, 0, false, 0);

    public boolean active() {
        return !title.isEmpty();
    }

    /** Stable identity of the track (used to detect song changes and cache lyrics). */
    public String trackKey() {
        return (artist + " — " + title).toLowerCase(java.util.Locale.ROOT).trim();
    }

    public long positionMs() {
        long pos = basePositionMs;
        if (playing) {
            pos += (System.nanoTime() - sampledAtNanos) / 1_000_000L;
        }
        if (durationMs > 0) {
            pos = Math.min(pos, durationMs);
        }
        return Math.max(0, pos);
    }

    public float progress() {
        return durationMs <= 0 ? 0f : Math.min(1f, positionMs() / (float) durationMs);
    }

    public static String formatTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }
}
