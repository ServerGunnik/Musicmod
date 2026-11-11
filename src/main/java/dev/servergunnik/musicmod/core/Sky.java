package dev.servergunnik.musicmod.core;

/**
 * Custom skies. {@link #id} is the texture in assets/musicmod/skies (null = vanilla sky).
 * {@link #preview} are four colors from zenith to horizon for the tile in the menu.
 */
public enum Sky {
    OFF("Off", null, false, 0x5A8CFF, 0x78A7FF, 0x9CC0FF, 0xC4DBFF),
    NEBULA("Nebula", "nebula", true, 0x120735, 0x2B1166, 0x7A2FC0, 0x4A1F8F),
    GALAXY("Galaxy", "galaxy", true, 0x03040F, 0x0D1433, 0xB9B4E6, 0x0D1433),
    AURORA("Aurora", "aurora", true, 0x020912, 0x04131F, 0x3DFFA0, 0x06202A),
    STARRY("Starry night", "starry", true, 0x050A25, 0x0A1640, 0x12245C, 0x1A3170),
    SUNSET("Sunset", "sunset", false, 0x1A1D4A, 0x8A4A9C, 0xFF6F6F, 0xFF9A4A),
    PINK_SUNSET("Pink sunset", "pink_sunset", false, 0x6A78D0, 0xC79BE0, 0xFF9EC4, 0xFFC2A8);

    public final String label;
    public final String id;
    public final boolean stars;
    public final int[] preview;

    Sky(String label, String id, boolean stars, int... preview) {
        this.label = label;
        this.id = id;
        this.stars = stars;
        this.preview = preview;
    }
}
