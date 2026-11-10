package dev.servergunnik.musicmod.core;

/** Custom skies. {@link #id} is the texture in assets/musicmod/skies (null = vanilla sky). */
public enum Sky {
    OFF("Off", null),
    NEBULA("Nebula", "nebula"),
    GALAXY("Galaxy", "galaxy"),
    AURORA("Aurora", "aurora"),
    STARRY("Starry night", "starry"),
    SUNSET("Sunset", "sunset"),
    PINK_SUNSET("Pink sunset", "pink_sunset");

    public final String label;
    public final String id;

    Sky(String label, String id) {
        this.label = label;
        this.id = id;
    }
}
