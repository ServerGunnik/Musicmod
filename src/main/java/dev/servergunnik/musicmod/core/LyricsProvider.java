package dev.servergunnik.musicmod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Fetches synced lyrics from LRCLIB (https://lrclib.net) — free, no API key.
 * Results (including "nothing found") are cached in memory and on disk so a song
 * is looked up only once.
 */
public final class LyricsProvider {

    private static final Logger LOG = LoggerFactory.getLogger("Musicmod");
    private static final String API = "https://lrclib.net/api";
    private static final String NOT_FOUND = "#musicmod:not-found";
    private static final Pattern NOISE = Pattern.compile(
            "\\s*([(\\[]\\s*(feat\\.?|ft\\.?|with|prod\\.?)[^)\\]]*[)\\]]|-\\s*(\\d{4}\\s*)?(remaster(ed)?|live|radio edit|single version|mono|stereo)[^-]*$|[(\\[][^)\\]]*(remaster(ed)?|version|edit|live)[^)\\]]*[)\\]])",
            Pattern.CASE_INSENSITIVE);

    public enum Status { IDLE, LOADING, FOUND, NOT_FOUND, ERROR }

    public record Result(Status status, Lyrics lyrics) {
        static final Result LOADING = new Result(Status.LOADING, null);
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Musicmod-Lyrics");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Result> memory = new ConcurrentHashMap<>();
    private final Path cacheDir;
    private final String userAgent;

    public LyricsProvider(Path cacheDir, String userAgent) {
        this.cacheDir = cacheDir;
        this.userAgent = userAgent;
    }

    /** Returns the cached result, or LOADING while a lookup runs in the background. */
    public Result get(MediaInfo track) {
        String key = track.trackKey();
        Result r = memory.get(key);
        if (r != null) {
            return r;
        }
        memory.put(key, Result.LOADING);
        CompletableFuture.supplyAsync(() -> load(track), executor)
                .whenComplete((res, err) -> {
                    if (err != null) {
                        LOG.warn("Lyrics lookup failed for {}: {}", key, err.toString());
                        memory.put(key, new Result(Status.ERROR, null));
                    } else {
                        memory.put(key, res);
                    }
                });
        return Result.LOADING;
    }

    /** Forget a song so the next {@link #get} asks LRCLIB again. */
    public void retry(MediaInfo track) {
        memory.remove(track.trackKey());
        try {
            Files.deleteIfExists(cacheFile(track.trackKey()));
        } catch (IOException ignored) {
        }
    }

    private Result load(MediaInfo track) {
        String key = track.trackKey();
        Path file = cacheFile(key);
        try {
            if (Files.exists(file)) {
                String cached = Files.readString(file, StandardCharsets.UTF_8);
                if (cached.startsWith(NOT_FOUND)) {
                    return new Result(Status.NOT_FOUND, null);
                }
                return found(cached, track);
            }
        } catch (IOException e) {
            LOG.debug("Unreadable lyrics cache {}", file, e);
        }

        try {
            String lrc = fetch(track);
            writeCache(file, lrc == null ? NOT_FOUND : lrc);
            return lrc == null ? new Result(Status.NOT_FOUND, null) : found(lrc, track);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("LRCLIB request failed: {}", e.toString());
            return new Result(Status.ERROR, null);
        }
    }

    private static Result found(String lrc, MediaInfo track) {
        Lyrics lyrics = Lyrics.parse(lrc, track.durationMs());
        return lyrics.isEmpty() ? new Result(Status.NOT_FOUND, null) : new Result(Status.FOUND, lyrics);
    }

    private String fetch(MediaInfo track) throws IOException, InterruptedException {
        String artist = primaryArtist(track.artist());
        String title = track.title();

        // 1) Exact match (title + artist [+ album + duration]).
        StringBuilder q = new StringBuilder("/get?track_name=").append(enc(title)).append("&artist_name=").append(enc(artist));
        if (!track.album().isBlank()) {
            q.append("&album_name=").append(enc(track.album()));
        }
        if (track.durationMs() > 0) {
            q.append("&duration=").append(Math.round(track.durationMs() / 1000.0));
        }
        String synced = syncedFrom(request(q.toString()));
        if (synced != null) {
            return synced;
        }

        // 2) Search with a cleaned-up title ("Song - Remastered 2011 (feat. X)" -> "Song").
        String clean = cleanTitle(title);
        JsonElement found = request("/search?track_name=" + enc(clean) + "&artist_name=" + enc(artist));
        String best = bestSearchHit(found, track.durationMs());
        if (best != null) {
            return best;
        }
        // 3) Free-text search as a last resort.
        return bestSearchHit(request("/search?q=" + enc(clean + " " + artist)), track.durationMs());
    }

    private JsonElement request(String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + pathAndQuery))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 404) {
            return null;
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + res.statusCode());
        }
        return JsonParser.parseString(res.body());
    }

    private static String syncedFrom(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        JsonElement s = o.get("syncedLyrics");
        return s != null && !s.isJsonNull() && !s.getAsString().isBlank() ? s.getAsString() : null;
    }

    private static String bestSearchHit(JsonElement el, long durationMs) {
        if (el == null || !el.isJsonArray()) {
            return null;
        }
        JsonArray arr = el.getAsJsonArray();
        String best = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement e : arr) {
            String synced = syncedFrom(e);
            if (synced == null) {
                continue;
            }
            double diff = 0;
            JsonElement d = e.getAsJsonObject().get("duration");
            if (durationMs > 0 && d != null && !d.isJsonNull()) {
                diff = Math.abs(d.getAsDouble() - durationMs / 1000.0);
            }
            if (diff < bestDiff && diff <= 8) {
                bestDiff = diff;
                best = synced;
            }
        }
        return best;
    }

    static String primaryArtist(String artist) {
        // Spotify reports "A, B" or "A feat. B" — LRCLIB usually only knows A.
        String a = artist.split(",|;| feat\\. | ft\\. | & ")[0].trim();
        return a.isEmpty() ? artist : a;
    }

    static String cleanTitle(String title) {
        String t = NOISE.matcher(title).replaceAll("").trim();
        return t.isEmpty() ? title : t;
    }

    private Path cacheFile(String key) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return cacheDir.resolve(HexFormat.of().formatHex(h) + ".lrc");
        } catch (Exception e) {
            return cacheDir.resolve(Integer.toHexString(key.hashCode()) + ".lrc");
        }
    }

    private void writeCache(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.debug("Cannot write lyrics cache {}", file, e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
