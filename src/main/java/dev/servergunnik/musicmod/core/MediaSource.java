package dev.servergunnik.musicmod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Reads "now playing" from the operating system. On Windows this runs a small
 * PowerShell bridge (assets/musicmod/media/smtc.ps1) that talks to Windows Media
 * Controls — no Spotify account, API key or login needed. Works with Spotify and
 * with anything else that shows up in the Windows volume flyout.
 */
public final class MediaSource {

    private static final Logger LOG = LoggerFactory.getLogger("Musicmod");

    public enum Status { STARTING, RUNNING, UNSUPPORTED_OS, FAILED }

    private final Path workDir;
    private volatile Status status = Status.STARTING;
    private volatile MediaInfo current = MediaInfo.NONE;
    private volatile Process process;
    private volatile boolean stopped;
    private int restarts;

    // Fallback clock for players whose timeline never moves.
    private String clockTrack = "";
    private long clockBaseMs;
    private long clockSampledNanos;
    private boolean clockPlaying;
    private boolean timelineBroken;

    // Album cover of the current track, converted to a small PNG.
    private String coverSource = "";
    private volatile Path coverFile;
    private volatile int coverVersion;

    public MediaSource(Path workDir) {
        this.workDir = workDir;
    }

    public Status status() {
        return status;
    }

    public MediaInfo current() {
        return current;
    }

    /** Changes whenever the cover changes (new picture, or none for this track). */
    public int coverVersion() {
        return coverVersion;
    }

    /** PNG of the current album cover, or null when the player gives none. */
    public Path coverFile() {
        return coverFile;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public void start() {
        if (!isWindows()) {
            status = Status.UNSUPPORTED_OS;
            LOG.info("Musicmod: reading the media player is only supported on Windows for now.");
            return;
        }
        Thread t = new Thread(this::runLoop, "Musicmod-Media");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        stopped = true;
        Process p = process;
        if (p != null) {
            p.destroy();
        }
    }

    /** "playpause", "next" or "previous". */
    public void command(String cmd) {
        if (status != Status.RUNNING) {
            return;
        }
        try {
            Files.writeString(workDir.resolve("command.txt"), cmd, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.debug("Cannot send media command", e);
        }
    }

    private void runLoop() {
        while (!stopped) {
            long started = System.currentTimeMillis();
            try {
                runOnce();
            } catch (Exception e) {
                LOG.warn("Musicmod media bridge stopped: {}", e.toString());
            }
            if (stopped) {
                return;
            }
            current = MediaInfo.NONE;
            if (System.currentTimeMillis() - started > 60_000) {
                restarts = 0;
            }
            if (++restarts > 5) {
                status = Status.FAILED;
                LOG.warn("Musicmod: media bridge keeps crashing, giving up.");
                return;
            }
            sleep(2000L * restarts);
        }
    }

    private void runOnce() throws IOException {
        Files.createDirectories(workDir);
        Path script = workDir.resolve("smtc.ps1");
        try (InputStream in = MediaSource.class.getResourceAsStream("/assets/musicmod/media/smtc.ps1")) {
            if (in == null) {
                throw new IOException("smtc.ps1 missing from the mod jar");
            }
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        Path commandFile = workDir.resolve("command.txt");
        Files.deleteIfExists(commandFile);

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(),
                "-ParentPid", Long.toString(ProcessHandle.current().pid()),
                "-CommandFile", commandFile.toString(),
                "-CoverDir", workDir.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        process = p;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!stopped && (line = reader.readLine()) != null) {
                line = line.strip();
                if (line.startsWith("﻿")) {
                    line = line.substring(1);
                }
                if (!line.startsWith("{")) {
                    continue;
                }
                try {
                    handle(JsonParser.parseString(line).getAsJsonObject());
                } catch (RuntimeException e) {
                    LOG.debug("Bad media line: {}", line);
                }
            }
        } finally {
            p.destroy();
        }
    }

    private void handle(JsonObject o) {
        if (o.has("log")) {
            LOG.info("Musicmod media bridge: {}", o.get("log").getAsString());
            return;
        }
        if (o.has("error")) {
            status = Status.FAILED;
            LOG.warn("Musicmod: Windows media controls unavailable ({})", o.get("error").getAsString());
            stopped = true;
            return;
        }
        status = Status.RUNNING;
        restarts = 0;
        if (!o.has("active") || !o.get("active").getAsBoolean()) {
            current = MediaInfo.NONE;
            return;
        }
        updateCover(str(o, "cover"));
        String title = str(o, "title");
        String artist = str(o, "artist");
        if (title.isEmpty()) {
            current = MediaInfo.NONE;
            return;
        }
        long now = System.nanoTime();
        boolean playing = o.get("playing").getAsBoolean();
        long duration = Math.max(0, o.get("durationMs").getAsLong());
        long position = Math.max(0, o.get("positionMs").getAsLong());
        long updated = o.get("updatedMs").getAsLong();
        long psNow = o.get("nowMs").getAsLong();

        String key = (artist + "|" + title).toLowerCase(Locale.ROOT);
        if (!key.equals(clockTrack)) {
            clockTrack = key;
            clockBaseMs = 0;
            clockSampledNanos = now;
            clockPlaying = playing;
            timelineBroken = false;
        } else if (clockPlaying != playing) {
            clockBaseMs += clockPlaying ? (now - clockSampledNanos) / 1_000_000L : 0;
            clockSampledNanos = now;
            clockPlaying = playing;
        }
        long clockPos = clockBaseMs + (clockPlaying ? (now - clockSampledNanos) / 1_000_000L : 0);

        long reported = position;
        if (playing && updated > 0 && psNow >= updated) {
            reported += psNow - updated;
        }
        // Some players never update the timeline (always 0, or nonsense). Then fall back
        // to our own clock that starts when the song changes.
        boolean valid = duration > 0 && reported <= duration + 2000;
        if (!valid || (playing && position == 0 && clockPos > 4000)) {
            timelineBroken = true;
        } else if (position > 0) {
            timelineBroken = false;
        }
        long base = timelineBroken ? clockPos : Math.min(reported, duration);

        MediaInfo prev = current;
        if (prev.active() && prev.trackKey().equals(new MediaInfo("", title, artist, "", 0, 0, false, 0).trackKey())
                && prev.playing() == playing && Math.abs(prev.positionMs() - base) < 350) {
            // Close enough to our own extrapolation: keep it, avoids jitter.
            return;
        }
        current = new MediaInfo(str(o, "app"), title, artist, str(o, "album"), duration, base, playing, now);
        maybeFetchCoverOnline(key, title, artist);
    }

    /** Look covers up on the internet (iTunes Search) when the player gives none. */
    public static volatile boolean onlineCovers = true;

    private String onlineKey = "";
    private long onlineKeySince;
    private boolean onlineTried;

    /**
     * When the player has not given a cover a few seconds into a track, look the
     * cover up by artist and title (once per track, in the background).
     */
    private void maybeFetchCoverOnline(String key, String title, String artist) {
        long now = System.currentTimeMillis();
        if (!key.equals(onlineKey)) {
            onlineKey = key;
            onlineKeySince = now;
            onlineTried = false;
        }
        if (onlineTried || !onlineCovers || coverFile != null || now - onlineKeySince < 2500) {
            return;
        }
        onlineTried = true;
        int versionAtStart = coverVersion;
        Thread t = new Thread(() -> {
            Path raw = workDir.resolve("cover_online.img");
            Path target = workDir.resolve("cover_online_" + (versionAtStart & 1) + ".png");
            if (CoverLookup.download(artist, title, raw) && Images.cover(raw, target, 256)) {
                // Only use it if nothing changed meanwhile (same track, still no cover).
                if (key.equals(onlineKey) && coverVersion == versionAtStart) {
                    coverFile = target;
                    coverVersion++;
                    LOG.info("Musicmod: album cover for '{}' taken from the internet", title);
                }
            } else {
                LOG.info("Musicmod: no album cover found for '{}'", title);
            }
        }, "Musicmod-Cover");
        t.setDaemon(true);
        t.start();
    }

    /** The bridge reports the file it saved the cover to ("" = none yet). Convert it once per change. */
    private void updateCover(String source) {
        if (source.equals(coverSource)) {
            return;
        }
        coverSource = source;
        Path out = null;
        if (!source.isEmpty()) {
            Path target = workDir.resolve("cover_" + (coverVersion & 1) + ".png");
            if (Images.cover(Path.of(source), target, 256)) {
                out = target;
            }
        }
        coverFile = out;
        coverVersion++;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().trim() : "";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
