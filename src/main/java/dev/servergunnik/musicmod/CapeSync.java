package dev.servergunnik.musicmod;

import dev.servergunnik.musicmod.compat.Net;
import dev.servergunnik.musicmod.compat.Textures;
import dev.servergunnik.musicmod.core.CapeList;
import dev.servergunnik.musicmod.core.Images;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

/**
 * Shares capes with other Musicmod players on servers that run the MusicmodCapes
 * plugin. A built-in cape travels as its id; a cape made from the player's own
 * picture travels as a small PNG, split into chunks.
 */
public final class CapeSync {

    private static final String CUSTOM = "custom";
    private static final Pattern CAPE_ID = Pattern.compile("[a-z0-9_]{1,32}");
    /** The plugin ignores cape changes that come faster than every 500 ms. */
    private static final long SEND_INTERVAL_MS = 800;
    /** The plugin accepts a new picture at most every 3 s. */
    private static final long UPLOAD_INTERVAL_MS = 3500;
    private static final int CHUNK = 30000;
    private static final int MAX_CHUNKS = 40;

    private record Incoming(UUID player, byte[] image) {
    }

    private static final Map<UUID, String> others = new ConcurrentHashMap<>();
    /** Texture path of the custom cape received for a player. */
    private static final Map<UUID, String> remoteTextures = new ConcurrentHashMap<>();
    private static final Map<UUID, byte[][]> assembling = new ConcurrentHashMap<>();
    private static final Queue<Incoming> finished = new ConcurrentLinkedQueue<>();

    private static boolean ready;
    private static String sent;
    private static long lastSend;
    private static long lastUpload;

    private CapeSync() {
    }

    static void init() {
        try {
            Net.init(CapeSync::receiveCape, CapeSync::receiveImage);
            ready = true;
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("Musicmod").warn("Musicmod: cape sharing is off ({})", t.toString());
        }
    }

    /** Server message: 16 bytes of player UUID, then that player's cape id ("" = none). */
    private static void receiveCape(byte[] data) {
        if (data.length < 16 || data.length > 16 + 64) {
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        UUID id = new UUID(buf.getLong(), buf.getLong());
        String cape = new String(data, 16, data.length - 16, StandardCharsets.UTF_8);
        if (cape.isEmpty()) {
            others.remove(id);
        } else if (CAPE_ID.matcher(cape).matches() && others.size() < 2000) {
            others.put(id, cape);
        }
        if (!CUSTOM.equals(cape)) {
            remoteTextures.remove(id);
            assembling.remove(id);
        }
    }

    /** Server message: 16 bytes of player UUID, chunk number, chunk count, chunk of a PNG. */
    private static void receiveImage(byte[] data) {
        if (data.length < 19 || data.length > 18 + CHUNK) {
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        UUID id = new UUID(buf.getLong(), buf.getLong());
        int index = data[16] & 0xFF;
        int count = data[17] & 0xFF;
        if (count < 1 || count > MAX_CHUNKS || index >= count) {
            return;
        }
        byte[][] parts = assembling.get(id);
        if (index == 0 || parts == null || parts.length != count) {
            if (assembling.size() > 64 && !assembling.containsKey(id)) {
                return;
            }
            parts = new byte[count][];
            assembling.put(id, parts);
        }
        parts[index] = java.util.Arrays.copyOfRange(data, 18, data.length);
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            if (part == null) {
                return; // still waiting for chunks
            }
            whole.write(part, 0, part.length);
        }
        assembling.remove(id);
        finished.add(new Incoming(id, whole.toByteArray()));
    }

    /** Called every client tick (render thread): uploads our cape and loads received pictures. */
    static void tick(boolean inWorld, String ownCape) {
        if (!ready) {
            return;
        }
        if (!inWorld) {
            others.clear();
            remoteTextures.clear();
            assembling.clear();
            finished.clear();
            sent = null;
            return;
        }
        loadReceived();

        // What the server should know: "" (none), a built-in id, or a custom picture.
        Path picture = ownCape != null && ownCape.startsWith(CustomCapes.PREFIX) ? CustomCapes.networkFile(ownCape) : null;
        String own;
        if (picture != null) {
            long stamp = 0;
            try {
                stamp = Files.getLastModifiedTime(picture).toMillis();
            } catch (java.io.IOException ignored) {
            }
            own = CUSTOM + "|" + picture.getFileName() + "|" + stamp;
        } else {
            own = ownCape == null || !isBuiltIn(ownCape) ? "" : ownCape;
        }
        long now = System.currentTimeMillis();
        if (Objects.equals(sent, own) || now - lastSend < SEND_INTERVAL_MS) {
            return;
        }
        if (picture == null) {
            lastSend = now;
            if (Net.send(false, own.getBytes(StandardCharsets.UTF_8))) {
                sent = own;
            }
        } else if (now - lastUpload >= UPLOAD_INTERVAL_MS) {
            lastSend = now;
            if (upload(picture)) {
                lastUpload = now;
                sent = own;
            }
        }
    }

    /** Tells the server we wear a custom cape, then sends the picture in chunks. */
    private static boolean upload(Path picture) {
        byte[] image;
        try {
            image = Files.readAllBytes(picture);
        } catch (java.io.IOException e) {
            return false;
        }
        int count = (image.length + CHUNK - 1) / CHUNK;
        if (count < 1 || count > MAX_CHUNKS || !Net.send(false, CUSTOM.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            int from = i * CHUNK;
            int len = Math.min(CHUNK, image.length - from);
            byte[] packet = new byte[2 + len];
            packet[0] = (byte) i;
            packet[1] = (byte) count;
            System.arraycopy(image, from, packet, 2, len);
            if (!Net.send(true, packet)) {
                return false;
            }
        }
        return true;
    }

    /** Checks each received picture, writes a clean copy and loads it as a texture. */
    private static void loadReceived() {
        Incoming in;
        while ((in = finished.poll()) != null) {
            try {
                Path dir = CustomCapes.remoteCache();
                Files.createDirectories(dir);
                String key = in.player().toString().replace("-", "");
                Path file = dir.resolve(key + ".png");
                String texture = "dynamic/remote_" + key;
                if (Images.sanitizeCape(in.image(), file) && Textures.load(texture, file)) {
                    remoteTextures.put(in.player(), texture);
                }
            } catch (java.io.IOException | RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger("Musicmod").warn("Musicmod: cannot load a received cape ({})", e.toString());
            }
        }
    }

    /** Texture of the cape another player wears, or null. */
    public static String textureOf(UUID player) {
        String cape = others.get(player);
        if (cape == null) {
            return null;
        }
        if (CUSTOM.equals(cape)) {
            return remoteTextures.get(player);
        }
        return isBuiltIn(cape) ? "textures/cape/" + cape + ".png" : null;
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
