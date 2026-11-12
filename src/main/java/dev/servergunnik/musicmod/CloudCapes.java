package dev.servergunnik.musicmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.servergunnik.musicmod.compat.Session;
import dev.servergunnik.musicmod.compat.Textures;
import dev.servergunnik.musicmod.core.CapeList;
import dev.servergunnik.musicmod.core.Images;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Capes that work on every server: a small web service (run by the mod's author)
 * remembers which cape each player wears, and the mod asks it about the players
 * nearby. Built-in capes show at once; a cape made from the player's own picture
 * shows to others only after the service's admins approve it.
 */
public final class CloudCapes {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Musicmod");
    private static final String CUSTOM = "custom";
    private static final int POLL_TICKS = 100;           // look around every 5 s
    private static final long KNOWN_TTL_MS = 60_000;     // ask again about a player after a minute
    private static final long RETRY_MS = 30_000;

    private record Known(String cape, String hash, long at) {
    }

    private record Loaded(String hash, Path file) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Musicmod-Capes");
        t.setDaemon(true);
        return t;
    });
    private static final Map<UUID, Known> known = new ConcurrentHashMap<>();
    /** hash -> texture path, once the picture is loaded. */
    private static final Map<String, String> textures = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> downloading = new ConcurrentHashMap<>();
    private static final Queue<Loaded> ready = new ConcurrentLinkedQueue<>();

    private static int ticks;
    private static volatile String sent;
    private static volatile long retryAt;
    private static volatile boolean busy;
    /** State of the player's submitted picture: null (unknown), "none", "pending", "approved", "rejected". */
    private static volatile String customState;
    /** Last message for the menu (a translation key), or "". */
    private static volatile String message = "";

    private CloudCapes() {
    }

    private static String base() {
        String url = MusicmodClient.config().capeServer;
        if (url == null || url.isBlank() || !MusicmodClient.config().cloudCapes) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String customState() {
        return customState;
    }

    public static String message() {
        return message;
    }

    /** Called every client tick (render thread). */
    static void tick(boolean inWorld, String ownCape) {
        Loaded l;
        while ((l = ready.poll()) != null) {
            String path = "dynamic/cloud_" + l.hash();
            if (Textures.load(path, l.file())) {
                textures.put(l.hash(), path);
            }
        }
        String base = base();
        if (base == null || !inWorld) {
            if (!inWorld) {
                known.clear();
            }
            return;
        }

        // 1. Tell the service what we wear (when it changed).
        String own = ownCape == null || ownCape.isEmpty() ? ""
                : ownCape.startsWith(CustomCapes.PREFIX) ? CUSTOM
                : isBuiltIn(ownCape) ? ownCape : "";
        long now = System.currentTimeMillis();
        if (!Objects.equals(sent, own) && !busy && now >= retryAt) {
            busy = true;
            String name = Session.name();
            WORKER.execute(() -> {
                try {
                    JsonObject body = signed(name);
                    if (body != null) {
                        body.addProperty("cape", own);
                        JsonObject res = post(base + "/v1/cape", body);
                        if (res != null && res.has("cape")) {
                            sent = own;
                            customState = res.has("custom") && !res.get("custom").isJsonNull() ? res.get("custom").getAsString() : "none";
                        }
                    }
                } finally {
                    if (!Objects.equals(sent, own)) {
                        retryAt = System.currentTimeMillis() + RETRY_MS;
                    }
                    busy = false;
                }
            });
        }

        // 2. Ask about the players around us.
        if (++ticks % POLL_TICKS != 0) {
            return;
        }
        List<UUID> ask = new ArrayList<>();
        for (UUID id : Session.otherPlayers()) {
            Known k = known.get(id);
            if ((k == null || now - k.at() > KNOWN_TTL_MS) && ask.size() < 100) {
                ask.add(id);
            }
        }
        if (ask.isEmpty()) {
            return;
        }
        for (UUID id : ask) {
            Known old = known.get(id);
            known.put(id, new Known(old == null ? "" : old.cape(), old == null ? null : old.hash(), now));
        }
        WORKER.execute(() -> lookup(base, ask));
    }

    private static void lookup(String base, List<UUID> ask) {
        StringBuilder q = new StringBuilder();
        for (UUID id : ask) {
            q.append(q.isEmpty() ? "" : ",").append(id.toString().replace("-", ""));
        }
        JsonObject res = get(base + "/v1/capes?uuids=" + q);
        if (res == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID id : ask) {
            JsonElement e = res.get(id.toString().replace("-", ""));
            if (e == null || !e.isJsonObject()) {
                known.put(id, new Known("", null, now));
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            String cape = o.has("cape") ? o.get("cape").getAsString() : "";
            String hash = o.has("hash") ? o.get("hash").getAsString() : null;
            if (CUSTOM.equals(cape) && hash != null && hash.matches("[0-9a-f]{32}")) {
                known.put(id, new Known(CUSTOM, hash, now));
                download(base, hash);
            } else {
                known.put(id, new Known(isBuiltIn(cape) ? cape : "", null, now));
            }
        }
    }

    /** Fetches an approved custom cape once, checks it and queues it for loading. */
    private static void download(String base, String hash) {
        if (textures.containsKey(hash) || downloading.putIfAbsent(hash, true) != null) {
            return;
        }
        try {
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(base + "/v1/image/" + hash))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200 || res.body().length > 1024 * 1024) {
                downloading.remove(hash);
                return;
            }
            Path dir = CustomCapes.remoteCache();
            Files.createDirectories(dir);
            Path file = dir.resolve("cloud_" + hash + ".png");
            if (Images.sanitizeCape(res.body(), file)) {
                ready.add(new Loaded(hash, file));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            downloading.remove(hash);
        } catch (Exception e) {
            downloading.remove(hash);
        }
    }

    /** Sends the chosen custom cape to the admins for approval. */
    public static void submit(String customCapeId) {
        String base = base();
        Path picture = CustomCapes.networkFile(customCapeId);
        Path icon = CustomCapes.iconFile(customCapeId);
        if (base == null || picture == null) {
            message = "musicmod.cloud.no_picture";
            return;
        }
        message = "musicmod.cloud.sending";
        String name = Session.name();
        WORKER.execute(() -> {
            try {
                JsonObject body = signed(name);
                if (body == null) {
                    message = "musicmod.cloud.no_account";
                    return;
                }
                body.addProperty("image", Base64.getEncoder().encodeToString(Files.readAllBytes(picture)));
                if (icon != null && Files.exists(icon)) {
                    body.addProperty("preview", Base64.getEncoder().encodeToString(Files.readAllBytes(icon)));
                }
                JsonObject res = post(base + "/v1/submit", body);
                if (res != null && res.has("custom") && !res.get("custom").isJsonNull()) {
                    customState = res.get("custom").getAsString();
                    message = "musicmod.cloud.sent";
                } else {
                    message = "musicmod.cloud.failed";
                }
            } catch (java.io.IOException e) {
                message = "musicmod.cloud.failed";
            }
        });
    }

    /** Asks the service how the player's submitted picture stands (for the menu). */
    public static void refreshState() {
        String base = base();
        if (base == null) {
            return;
        }
        UUID me = Session.uuid();
        WORKER.execute(() -> {
            JsonObject res = get(base + "/v1/me?uuid=" + me.toString().replace("-", ""));
            if (res != null) {
                customState = res.has("custom") && !res.get("custom").isJsonNull() ? res.get("custom").getAsString() : "none";
            }
        });
    }

    /** Texture of the cape another player wears according to the service, or null. */
    public static String textureOf(UUID player) {
        Known k = known.get(player);
        if (k == null || k.cape().isEmpty()) {
            return null;
        }
        if (CUSTOM.equals(k.cape())) {
            return k.hash() == null ? null : textures.get(k.hash());
        }
        return "textures/cape/" + k.cape() + ".png";
    }

    /** Proof of account for a write: {username, serverId} after a "join server" call to Mojang. */
    private static JsonObject signed(String name) {
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        String serverId = HexFormat.of().formatHex(random);
        if (!Session.join(serverId)) {
            return null;
        }
        JsonObject body = new JsonObject();
        body.addProperty("username", name);
        body.addProperty("serverId", serverId);
        return body;
    }

    private static JsonObject get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private static JsonObject post(String url, JsonObject body) {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build());
    }

    private static JsonObject send(HttpRequest request) {
        try {
            HttpResponse<String> res = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                LOG.debug("Musicmod capes service: HTTP {} for {}", res.statusCode(), request.uri().getPath());
                return null;
            }
            JsonElement e = JsonParser.parseString(res.body());
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBuiltIn(String id) {
        for (CapeList.Cape c : CapeList.all()) {
            if (c.id().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
