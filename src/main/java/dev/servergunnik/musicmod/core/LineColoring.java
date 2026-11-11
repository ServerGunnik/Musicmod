package dev.servergunnik.musicmod.core;

import dev.servergunnik.musicmod.api.Span;

import java.util.ArrayList;
import java.util.List;

/** Turns a lyric line + current time into colored spans (word / karaoke / line highlight). */
public final class LineColoring {

    private LineColoring() {
    }

    public static List<Span> spans(Lyrics.Line line, long timeMs, Config.Highlight mode, Theme theme) {
        List<Span> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        if (line.isBlank() || line.words().isEmpty()) {
            out.add(new Span("♪", theme.upcoming));
            return out;
        }
        if (mode == Config.Highlight.LINE) {
            out.add(new Span(line.text(), timeMs < line.endMs() ? theme.current : theme.sung));
            return out;
        }
        List<Lyrics.Word> words = line.words();
        if (mode == Config.Highlight.REVEAL) {
            // Only the words that have started; the newest one is highlighted.
            for (int i = 0; i < words.size(); i++) {
                Lyrics.Word w = words.get(i);
                if (timeMs < w.startMs()) {
                    break;
                }
                if (i > 0) {
                    add(out, " ", theme.sung);
                }
                boolean newest = i + 1 >= words.size() || timeMs < words.get(i + 1).startMs();
                add(out, w.text(), newest ? theme.current : theme.sung, newest);
            }
            return out;
        }
        if (mode == Config.Highlight.SINGLE) {
            // Just the word being sung (it stays until the next word starts).
            Lyrics.Word shown = null;
            for (Lyrics.Word w : words) {
                if (timeMs >= w.startMs()) {
                    shown = w;
                }
            }
            if (shown != null) {
                out.add(new Span(shown.text(), theme.current, true));
            }
            return out;
        }
        for (int i = 0; i < words.size(); i++) {
            Lyrics.Word w = words.get(i);
            String sep = i + 1 < words.size() ? " " : "";
            if (timeMs >= w.endMs()) {
                add(out, w.text() + sep, theme.sung);
            } else if (timeMs < w.startMs()) {
                add(out, w.text() + sep, theme.upcoming);
            } else if (mode == Config.Highlight.KARAOKE) {
                float f = (timeMs - w.startMs()) / (float) Math.max(1, w.endMs() - w.startMs());
                int cut = Math.max(0, Math.min(w.text().length(), Math.round(f * w.text().length())));
                // The word being sung: the part already sung is bright, the rest still dim,
                // and the whole word is marked as "now".
                add(out, w.text().substring(0, cut), theme.current, true);
                add(out, w.text().substring(cut), theme.upcoming, true);
                add(out, sep, theme.upcoming);
            } else {
                add(out, w.text(), theme.current, true);
                add(out, sep, theme.upcoming);
            }
        }
        return out;
    }

    /** Same line, all in one color (for the "next line" preview). */
    public static List<Span> plain(Lyrics.Line line, int rgb) {
        List<Span> out = new ArrayList<>();
        if (line != null && !line.isBlank()) {
            out.add(new Span(line.text(), rgb));
        }
        return out;
    }

    private static void add(List<Span> out, String text, int rgb) {
        add(out, text, rgb, false);
    }

    private static void add(List<Span> out, String text, int rgb, boolean now) {
        if (text.isEmpty()) {
            return;
        }
        Span last = out.isEmpty() ? null : out.get(out.size() - 1);
        if (last != null && last.rgb() == rgb && last.now() == now) {
            out.set(out.size() - 1, new Span(last.text() + text, rgb, now));
        } else {
            out.add(new Span(text, rgb, now));
        }
    }
}
