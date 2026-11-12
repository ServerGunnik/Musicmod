package dev.servergunnik.musicmod;

import dev.servergunnik.musicmod.compat.Net;
import dev.servergunnik.musicmod.core.CapeList;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Shares capes with other Musicmod players on servers that run the MusicmodCapes
 * plugin. Only the id of a built-in cape travels over the network; capes made
 * from the player's own pictures stay local.
 */
public final class CapeSync {

    private static final Pattern CAPE_ID = Pattern.compile("[a-z0-9_]{1,32}");
    /** The plugin ignores changes that come faster than every 500 ms. */
    private static final long SEND_INTERVAL_MS = 800;

    private static final Map<UUID, String> others = new ConcurrentHashMap<>();
    private static boolean ready;
    private static String sent;
    private static long lastSend;

    private CapeSync() {
    }

    static void init() {
        try {
            Net.init(CapeSync::receive);
            ready = true;
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("Musicmod").warn("Musicmod: cape sharing is off ({})", t.toString());
        }
    }

    /** Server message: 16 bytes of player UUID, then that player's cape id ("" = none). */
    private static void receive(byte[] data) {
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
    }

    /** Called every client tick: keeps the server informed about our own cape. */
    static void tick(boolean inWorld, String ownCape) {
        if (!ready) {
            return;
        }
        if (!inWorld) {
            others.clear();
            sent = null;
            return;
        }
        // Only built-in capes can be shown to others.
        String own = ownCape == null || ownCape.startsWith(CustomCapes.PREFIX) || !isBuiltIn(ownCape) ? "" : ownCape;
        long now = System.currentTimeMillis();
        if (!Objects.equals(sent, own) && now - lastSend >= SEND_INTERVAL_MS) {
            lastSend = now;
            if (Net.send(own.getBytes(StandardCharsets.UTF_8))) {
                sent = own;
            }
        }
    }

    /** Texture of the cape another player wears, or null. */
    public static String textureOf(UUID player) {
        String cape = others.get(player);
        return cape != null && isBuiltIn(cape) ? "textures/cape/" + cape + ".png" : null;
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
