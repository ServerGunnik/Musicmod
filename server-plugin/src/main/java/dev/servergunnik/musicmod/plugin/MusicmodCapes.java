package dev.servergunnik.musicmod.plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Relays cape choices between players who run the Musicmod client mod.
 *
 * <p>Channel {@code musicmod:cape}:
 * <ul>
 *   <li>client -> server: the id of the cape the player chose, UTF-8, empty = none;</li>
 *   <li>server -> client: 16 bytes of player UUID followed by that player's cape id (UTF-8, empty = none).</li>
 * </ul>
 * Only ids of capes built into the mod are relayed (letters, digits, underscore), never pictures.
 * Players without the mod are not affected and receive nothing.
 */
public final class MusicmodCapes extends JavaPlugin implements PluginMessageListener, Listener {

    private static final String CHANNEL = "musicmod:cape";
    private static final Pattern CAPE_ID = Pattern.compile("[a-z0-9_]{0,32}");
    /** A client may change its cape at most this often. */
    private static final long MIN_INTERVAL_MS = 500;

    private final Map<UUID, String> capes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChange = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Musicmod capes enabled (channel " + CHANNEL + ").");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        capes.clear();
        lastChange.clear();
    }

    /** A player told us which cape they wear. */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!CHANNEL.equals(channel) || data.length > 64) {
            return;
        }
        String cape = new String(data, StandardCharsets.UTF_8);
        if (!CAPE_ID.matcher(cape).matches()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastChange.put(player.getUniqueId(), now);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return;
        }
        if (cape.isEmpty()) {
            capes.remove(player.getUniqueId());
        } else {
            capes.put(player.getUniqueId(), cape);
        }
        broadcast(player.getUniqueId(), cape);
    }

    /** A client with the mod has just announced the channel: tell it about everyone's cape. */
    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (!CHANNEL.equals(event.getChannel())) {
            return;
        }
        Player player = event.getPlayer();
        for (Map.Entry<UUID, String> e : capes.entrySet()) {
            if (!e.getKey().equals(player.getUniqueId())) {
                player.sendPluginMessage(this, CHANNEL, packet(e.getKey(), e.getValue()));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastChange.remove(id);
        if (capes.remove(id) != null) {
            broadcast(id, "");
        }
    }

    private void broadcast(UUID owner, String cape) {
        byte[] packet = packet(owner, cape);
        for (Player other : getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(owner) && other.getListeningPluginChannels().contains(CHANNEL)) {
                other.sendPluginMessage(this, CHANNEL, packet);
            }
        }
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
