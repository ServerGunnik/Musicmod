package dev.servergunnik.musicmod.core;

/** All user settings. Saved as JSON in config/musicmod/config.json. */
public final class Config {

    public enum Display { BOTH, SKY, TOP_BAR }

    /**
     * WORD/KARAOKE/LINE show the whole line and mark what is sung. REVEAL adds the
     * words one by one as they are sung; SINGLE shows only the word being sung.
     */
    public enum Highlight { WORD, KARAOKE, LINE, REVEAL, SINGLE }

    // Lyrics
    public boolean lyricsEnabled = true;
    public Display display = Display.BOTH;
    public boolean followView = false;
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
    public LyricFont font = LyricFont.MINECRAFT;
    public Sky sky = Sky.OFF;
    /** Stars drift across the sky (a second, rotating layer). */
    public boolean skyAnimated = true;
    /** Id of the chosen cape, "" = none (keeps the player's own cape). */
    public String cape = "";
    /** PvP styles: kind ("totem", "crystal", "obsidian", "anchor") -> style id; missing or "" = vanilla. */
    public java.util.Map<String, String> styles = new java.util.HashMap<>();

    // HUD
    public boolean hudEnabled = true;
    public boolean hudSong = true;
    public boolean hudSongTime = true;
    public boolean hudClock = true;
    public boolean hudClock24h = true;
    public boolean hudFps = true;
    public boolean hudPing = true;
    public boolean hudCoords = true;
    public boolean hudSpeed = true;

    // HUD element positions: offsets in GUI pixels from the default spot (drag them in the menu).
    public int pillDx;
    public int pillDy;
    public int barDx;
    public int barDy;
    public int infoDx;
    public int infoDy;

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
        if (font == null) font = LyricFont.MINECRAFT;
        if (sky == null) sky = Sky.OFF;
        if (cape == null) cape = "";
        if (styles == null) styles = new java.util.HashMap<>();
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
