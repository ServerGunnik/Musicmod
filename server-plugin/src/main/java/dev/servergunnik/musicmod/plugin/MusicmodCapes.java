package dev.servergunnik.musicmod.plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Relays cape choices between players who run the Musicmod client mod.
 *
 * <p>Channel {@code musicmod:cape} (which cape a player wears):
 * <ul>
 *   <li>client -> server: cape id, UTF-8. Empty = none, {@code custom} = the picture sent on the
 *       other channel, anything else = a cape built into the mod;</li>
 *   <li>server -> client: 16 bytes of player UUID, then that player's cape id.</li>
 * </ul>
 *
 * <p>Channel {@code musicmod:cape_img} (pictures of custom capes, PNG, split into chunks):
 * <ul>
 *   <li>client -> server: 1 byte chunk number, 1 byte chunk count, then the chunk;</li>
 *   <li>server -> client: 16 bytes of player UUID, 1 byte chunk number, 1 byte chunk count, chunk.</li>
 * </ul>
 * Pictures are kept in memory only while the player is online. Players without the mod are not
 * affected and receive nothing.
 */
public final class MusicmodCapes extends JavaPlugin implements PluginMessageListener, Listener {

    private static final String CHANNEL = "musicmod:cape";
    private static final String IMAGE_CHANNEL = "musicmod:cape_img";
    private static final String CUSTOM = "custom";
    private static final Pattern CAPE_ID = Pattern.compile("[a-z0-9_]{0,32}");
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    /** A client may change its cape at most this often. */
    private static final long MIN_INTERVAL_MS = 500;
    /** A client may upload a picture at most this often. */
    private static final long MIN_UPLOAD_INTERVAL_MS = 3000;
    /** Chunk size both ways (the client -> server limit of the game is 32767 bytes). */
    private static final int CHUNK = 30000;
    private static final int MAX_CHUNKS = 40;

    private static final class Upload {
        final byte[][] parts;
        int received;

        Upload(int count) {
            parts = new byte[count][];
        }
    }

    private final Map<UUID, String> capes = new ConcurrentHashMap<>();
    private final Map<UUID, byte[]> images = new ConcurrentHashMap<>();
    private final Map<UUID, Upload> uploads = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChange = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpload = new ConcurrentHashMap<>();

    private boolean allowCustom;
    private int maxBytes;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        allowCustom = getConfig().getBoolean("allow-custom-capes", true);
        maxBytes = Math.max(16, Math.min(MAX_CHUNKS * CHUNK / 1024, getConfig().getInt("max-kilobytes", 256))) * 1024;
        for (String channel : new String[]{CHANNEL, IMAGE_CHANNEL}) {
            getServer().getMessenger().registerIncomingPluginChannel(this, channel, this);
            getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Musicmod capes enabled. Custom capes: " + (allowCustom ? "on, up to " + maxBytes / 1024 + " KB" : "off") + ".");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        capes.clear();
        images.clear();
        uploads.clear();
        lastChange.clear();
        lastUpload.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (CHANNEL.equals(channel)) {
            onCape(player, data);
        } else if (IMAGE_CHANNEL.equals(channel)) {
            onImageChunk(player, data);
        }
    }

    /** A player told us which cape they wear. */
    private void onCape(Player player, byte[] data) {
        if (data.length > 64) {
            return;
        }
        String cape = new String(data, StandardCharsets.UTF_8);
        if (!CAPE_ID.matcher(cape).matches() || (CUSTOM.equals(cape) && !allowCustom)) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastChange.put(id, now);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return;
        }
        if (!CUSTOM.equals(cape)) {
            images.remove(id);
            uploads.remove(id);
        }
        if (cape.isEmpty()) {
            capes.remove(id);
        } else {
            capes.put(id, cape);
        }
        broadcast(CHANNEL, id, packet(id, cape));
    }

    /** One chunk of a custom cape picture. When the last one arrives, pass the picture on. */
    private void onImageChunk(Player player, byte[] data) {
        if (!allowCustom || data.length < 3 || data.length > CHUNK + 2) {
            return;
        }
        UUID id = player.getUniqueId();
        int index = data[0] & 0xFF;
        int count = data[1] & 0xFF;
        if (count < 1 || count > MAX_CHUNKS || index >= count || (long) count * CHUNK > maxBytes + CHUNK) {
            return;
        }
        if (index == 0) {
            long now = System.currentTimeMillis();
            Long last = lastUpload.put(id, now);
            if (last != null && now - last < MIN_UPLOAD_INTERVAL_MS) {
                uploads.remove(id);
                return;
            }
            uploads.put(id, new Upload(count));
        }
        Upload upload = uploads.get(id);
        if (upload == null || upload.parts.length != count || upload.parts[index] != null) {
            return;
        }
        upload.parts[index] = java.util.Arrays.copyOfRange(data, 2, data.length);
        if (++upload.received < count) {
            return;
        }
        uploads.remove(id);
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        for (byte[] part : upload.parts) {
            whole.write(part, 0, part.length);
        }
        byte[] image = whole.toByteArray();
        if (image.length > maxBytes || !isPng(image)) {
            return;
        }
        images.put(id, image);
        capes.put(id, CUSTOM);
        byte[] capePacket = packet(id, CUSTOM);
        for (Player other : getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(id)) {
                continue;
            }
            if (other.getListeningPluginChannels().contains(CHANNEL)) {
                other.sendPluginMessage(this, CHANNEL, capePacket);
            }
            sendImage(other, id, image);
        }
    }

    /** A client with the mod has just announced a channel: tell it what everyone wears. */
    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        if (CHANNEL.equals(event.getChannel())) {
            for (Map.Entry<UUID, String> e : capes.entrySet()) {
                if (!e.getKey().equals(player.getUniqueId())) {
                    player.sendPluginMessage(this, CHANNEL, packet(e.getKey(), e.getValue()));
                }
            }
        } else if (IMAGE_CHANNEL.equals(event.getChannel())) {
            for (Map.Entry<UUID, byte[]> e : images.entrySet()) {
                if (!e.getKey().equals(player.getUniqueId())) {
                    sendImage(player, e.getKey(), e.getValue());
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastChange.remove(id);
        lastUpload.remove(id);
        uploads.remove(id);
        images.remove(id);
        if (capes.remove(id) != null) {
            broadcast(CHANNEL, id, packet(id, ""));
        }
    }

    private void broadcast(String channel, UUID owner, byte[] packet) {
        for (Player other : getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(owner) && other.getListeningPluginChannels().contains(channel)) {
                other.sendPluginMessage(this, channel, packet);
            }
        }
    }

    private void sendImage(Player target, UUID owner, byte[] image) {
        if (!target.getListeningPluginChannels().contains(IMAGE_CHANNEL)) {
            return;
        }
        int count = (image.length + CHUNK - 1) / CHUNK;
        for (int i = 0; i < count; i++) {
            int from = i * CHUNK;
            int len = Math.min(CHUNK, image.length - from);
            ByteBuffer buf = ByteBuffer.allocate(18 + len);
            buf.putLong(owner.getMostSignificantBits());
            buf.putLong(owner.getLeastSignificantBits());
            buf.put((byte) i);
            buf.put((byte) count);
            buf.put(image, from, len);
            target.sendPluginMessage(this, IMAGE_CHANNEL, buf.array());
        }
    }

    private static boolean isPng(byte[] data) {
        if (data.length < 64) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] packet(UUID owner, String cape) {
        byte[] name = cape.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(16 + name.length);
        buf.putLong(owner.getMostSignificantBits());
        buf.putLong(owner.getLeastSignificantBits());
        buf.put(name);
        return buf.array();
    }
}
