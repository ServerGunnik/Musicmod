package dev.servergunnik.musicmod.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-synced lyrics parsed from LRC. Supports plain LRC ({@code [mm:ss.xx] line})
 * and enhanced LRC with per-word stamps ({@code [00:12.00] <00:12.00> word <00:12.40> word}).
 * When a line has no per-word stamps the words get times spread over the line by
 * their length (see {@link #distributeWords}).
 */
public final class Lyrics {

    public record Word(String text, long startMs, long endMs) {
    }

    public record Line(long startMs, long endMs, String text, List<Word> words) {
        public boolean isBlank() {
            return text.isBlank();
        }
    }

    private static final Pattern LINE_STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_STAMP = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET_TAG = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);

    /** Longest time a line stays "current" when the next line is far away. */
    private static final long MAX_LINE_MS = 8_000;

    private final List<Line> lines;
    private final boolean wordSynced;

    private Lyrics(List<Line> lines, boolean wordSynced) {
        this.lines = Collections.unmodifiableList(lines);
        this.wordSynced = wordSynced;
    }

    public List<Line> lines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** True when the source had real per-word timestamps (enhanced LRC). */
    public boolean wordSynced() {
        return wordSynced;
    }

    /** Index of the line being sung at {@code timeMs}, or -1 before the first line. */
    public int lineAt(long timeMs) {
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).startMs() <= timeMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    public static Lyrics parse(String lrc, long trackDurationMs) {
        if (lrc == null || lrc.isBlank()) {
            return new Lyrics(List.of(), false);
        }
        long offset = 0;
        Matcher off = OFFSET_TAG.matcher(lrc);
        if (off.find()) {
            offset = Long.parseLong(off.group(1));
        }

        record Raw(long start, String body) {
        }
        List<Raw> raws = new ArrayList<>();
        for (String rawLine : lrc.split("\\r?\\n")) {
            Matcher m = LINE_STAMP.matcher(rawLine);
            List<Long> stamps = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                stamps.add(toMs(m.group(1), m.group(2), m.group(3)));
                end = m.end();
            }
            if (stamps.isEmpty()) {
                continue;
            }
            String body = rawLine.substring(end).trim();
            for (long s : stamps) {
                // LRC offset: positive means lyrics appear earlier.
                raws.add(new Raw(Math.max(0, s - offset), body));
            }
        }
        raws.sort((a, b) -> Long.compare(a.start(), b.start()));

        boolean anyWordStamps = false;
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < raws.size(); i++) {
            Raw r = raws.get(i);
            long next = i + 1 < raws.size() ? raws.get(i + 1).start() : (trackDurationMs > r.start() ? trackDurationMs : r.start() + MAX_LINE_MS);
            long end = Math.min(next, r.start() + MAX_LINE_MS);
            List<Word> words = parseWordStamps(r.body(), r.start(), end);
            String text;
            if (words != null) {
                anyWordStamps = true;
                text = String.join(" ", words.stream().map(Word::text).toList());
            } else {
                text = r.body();
                words = distributeWords(text, r.start(), end);
            }
            lines.add(new Line(r.start(), end, text, words));
        }
        return new Lyrics(lines, anyWordStamps);
    }

    private static List<Word> parseWordStamps(String body, long lineStart, long lineEnd) {
        Matcher m = WORD_STAMP.matcher(body);
        List<Long> stamps = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        while (m.find()) {
            stamps.add(toMs(m.group(1), m.group(2), m.group(3)));
            int textEnd = body.length();
            int save = m.end();
            if (m.find()) {
                textEnd = m.start();
            }
            texts.add(body.substring(save, textEnd).trim());
            m.region(textEnd, body.length());
        }
        if (stamps.isEmpty()) {
            return null;
        }
        List<Word> words = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).isEmpty()) {
                continue; // a trailing stamp only marks where the previous chunk ends
            }
            long s = Math.max(lineStart, stamps.get(i));
            long e = i + 1 < stamps.size() ? stamps.get(i + 1) : lineEnd;
            // A chunk can hold several words between two stamps: split it evenly.
            words.addAll(distributeWords(texts.get(i), s, Math.max(s + 1, e)));
        }
        return words.isEmpty() ? null : words;
    }

    /**
     * Spreads a line's time across its words. Each word weighs its letter count
     * plus a constant (short words still take time to sing), and the singing is
     * assumed to end a bit before the next line starts.
     */
    public static List<Word> distributeWords(String text, long start, long end) {
        String[] parts = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
        List<Word> out = new ArrayList<>(parts.length);
        if (parts.length == 0) {
            return out;
        }
        long span = Math.max(1, end - start);
        // Leave a short gap at the end of long lines (breath before the next line).
        long sung = span > 2_500 ? (long) (span * 0.85) : span;
        double[] weights = new double[parts.length];
        double total = 0;
        for (int i = 0; i < parts.length; i++) {
            int letters = 0;
            for (int c = 0; c < parts[i].length(); c++) {
                if (Character.isLetterOrDigit(parts[i].charAt(c))) {
                    letters++;
                }
            }
            weights[i] = 2.0 + letters;
            total += weights[i];
        }
        double t = start;
        for (int i = 0; i < parts.length; i++) {
            double d = sung * weights[i] / total;
            long ws = Math.round(t);
            long we = i == parts.length - 1 ? start + sung : Math.round(t + d);
            out.add(new Word(parts[i], ws, Math.max(ws + 1, we)));
            t += d;
        }
        return out;
    }

    private static long toMs(String min, String sec, String frac) {
        long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
        if (frac != null) {
            if (frac.length() == 1) {
                ms += Long.parseLong(frac) * 100;
            } else if (frac.length() == 2) {
                ms += Long.parseLong(frac) * 10;
            } else {
                ms += Long.parseLong(frac);
            }
        }
        return ms;
    }
}
