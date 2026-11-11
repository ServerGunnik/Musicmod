package dev.servergunnik.musicmod.api;

/**
 * A piece of text in one RGB color (used for word-by-word highlighting).
 * {@code now} marks the word being sung at this moment: it is drawn bold and
 * underlined so the place in the line is obvious whatever the colors are.
 */
public record Span(String text, int rgb, boolean now) {

    public Span(String text, int rgb) {
        this(text, rgb, false);
    }
}
