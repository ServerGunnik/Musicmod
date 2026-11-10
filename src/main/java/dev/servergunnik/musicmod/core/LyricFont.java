package dev.servergunnik.musicmod.core;

/** Fonts for the lyrics. {@link #id} is the font file in assets/musicmod/font (null = Minecraft's own). */
public enum LyricFont {
    MINECRAFT("Minecraft", null),
    POPPINS("Poppins", "poppins"),
    MONTSERRAT("Montserrat", "montserrat"),
    KANIT("Kanit", "kanit"),
    FREDOKA("Fredoka", "fredoka");

    public final String label;
    public final String id;

    LyricFont(String label, String id) {
        this.label = label;
        this.id = id;
    }
}
