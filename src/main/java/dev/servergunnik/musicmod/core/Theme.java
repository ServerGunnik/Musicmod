package dev.servergunnik.musicmod.core;

/** Color presets for lyrics, HUD and menu. Colors are RGB (menu/panel colors ARGB). */
public enum Theme {
    LAVENDER("Lavender", 0xFFFFFF, 0xB58CFF, 0x6F6A82, 0xEB15111F, 0xFF7B57D1),
    MINT("Mint", 0xFFFFFF, 0x47E07E, 0x66786C, 0xEB0E1712, 0xFF2F9E5D),
    ICE("Ice", 0xFFFFFF, 0x6CCBF2, 0x65757F, 0xEB0E151C, 0xFF3E8DBF),
    ROYAL("Royal", 0xFFFFFF, 0xF0B93C, 0x7A7160, 0xEB17120A, 0xFFC08A2A),
    SUNSET("Sunset", 0xFFFFFF, 0xFF7F66, 0x80696A, 0xEB1C110F, 0xFFD45A45),
    DREAMY("Dreamy", 0xFFFFFF, 0xF27CCB, 0x7C6A77, 0xEB1C101A, 0xFFC4559F),
    CLEAR("Clear", 0xFFFFFF, 0xBEBEBE, 0x6E6E6E, 0xCC000000, 0xFF5A5A5A);

    public final String label;
    /** Word being sung right now. */
    public final int current;
    /** Words already sung; also the accent color of the UI. */
    public final int sung;
    /** Words still to come. */
    public final int upcoming;
    public final int panel;
    public final int border;

    Theme(String label, int current, int sung, int upcoming, int panel, int border) {
        this.label = label;
        this.current = current;
        this.sung = sung;
        this.upcoming = upcoming;
        this.panel = panel;
        this.border = border;
    }

    public int accentArgb() {
        return 0xFF000000 | sung;
    }
}
