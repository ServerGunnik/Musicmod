package dev.servergunnik.musicmod.core;

/** All user settings. Saved as JSON in config/musicmod/config.json. */
public final class Config {

    public enum Display { BOTH, SKY, TOP_BAR }

    public enum Highlight { WORD, KARAOKE, LINE }

    // Lyrics
    public boolean lyricsEnabled = true;
    public Display display = Display.BOTH;
    public boolean followView = true;
    public boolean animation = true;
    public boolean nextLine = true;
    public Highlight highlight = Highlight.WORD;
    public int offsetMs = 0;
    public int distance = 30;
    public int height = 12;
    public float skyScale = 4.0f;
    public boolean skyBackground = false;

    // Look
    public Theme theme = Theme.LAVENDER;

    // HUD
    public boolean hudEnabled = true;
    public boolean hudSong = true;
    public boolean hudClock = true;
    public boolean hudClock24h = true;
    public boolean hudFps = true;
    public boolean hudPing = true;
    public boolean hudCoords = true;
    public boolean hudSpeed = true;

    public boolean showSky() {
        return lyricsEnabled && display != Display.TOP_BAR;
    }

    public boolean showTopBar() {
        return lyricsEnabled && display != Display.SKY;
    }

    /** Fixes values that came from a hand-edited or older config file. */
    public Config sanitize() {
        if (display == null) display = Display.BOTH;
        if (highlight == null) highlight = Highlight.WORD;
        if (theme == null) theme = Theme.LAVENDER;
        offsetMs = clamp(offsetMs, -5000, 5000);
        distance = clamp(distance, 8, 96);
        height = clamp(height, -8, 48);
        skyScale = Math.max(0.5f, Math.min(12f, skyScale));
        return this;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
