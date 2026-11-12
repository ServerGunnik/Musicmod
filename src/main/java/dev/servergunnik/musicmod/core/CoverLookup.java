package dev.servergunnik.musicmod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Finds an album cover by artist and title with the public iTunes Search API
 * (no key or account). Used only when the media player gives no picture.
 */
public final class CoverLookup {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private CoverLookup() {
    }

    /** Downloads the cover picture to {@code target}. False when nothing was found. */
    public static boolean download(String artist, String title, Path target) {
        try {
            String term = URLEncoder.encode((LyricsProvider.primaryArtist(artist) + " " + LyricsProvider.cleanTitle(title)).trim(),
                    StandardCharsets.UTF_8);
            HttpRequest search = HttpRequest.newBuilder(URI.create(
                            "https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + term))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = HTTP.send(search, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                return false;
            }
            JsonArray results = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("results");
            if (results == null || results.isEmpty()) {
                return false;
            }
            JsonObject first = results.get(0).getAsJsonObject();
            if (!first.has("artworkUrl100")) {
                return false;
            }
            // The API returns a 100 px picture; the same address serves bigger sizes.
            String url = first.get("artworkUrl100").getAsString().replace("100x100", "300x300");
            HttpResponse<Path> img = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(target));
            return img.statusCode() / 100 == 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
