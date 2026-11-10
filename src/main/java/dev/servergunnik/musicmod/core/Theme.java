package dev.servergunnik.musicmod.core;

/** Color presets for lyrics, HUD and menu. Colors are RGB (menu/panel colors ARGB). */
public enum Theme {
    LAVENDER("Lavender", 0xD4B8FF, 0xA77BF3, 0x8C84A6, 0xEB15111F, 0xFF7B57D1),
    MINT("Mint", 0x8CFFAE, 0x47D97A, 0x93AD9C, 0xEB0E1712, 0xFF2F9E5D),
    ICE("Ice", 0xC6F2FF, 0x6CCBF2, 0x8CA3AF, 0xEB0E151C, 0xFF3E8DBF),
    ROYAL("Royal", 0xFFDC7A, 0xE8B23C, 0xA89C82, 0xEB17120A, 0xFFC08A2A),
    SUNSET("Sunset", 0xFFC09A, 0xFF7F66, 0xB09189, 0xEB1C110F, 0xFFD45A45),
    DREAMY("Dreamy", 0xFFC4EE, 0xF27CCB, 0xAE97A8, 0xEB1C101A, 0xFFC4559F),
    CLEAR("Clear", 0xFFFFFF, 0xD8D8D8, 0x8E8E8E, 0xCC000000, 0xFF5A5A5A);

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
