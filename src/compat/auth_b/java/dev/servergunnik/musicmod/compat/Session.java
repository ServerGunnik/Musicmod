package dev.servergunnik.musicmod.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The player's Minecraft account and the players around, for the online capes service. */
public final class Session {

    private Session() {
    }

    public static String name() {
        return Minecraft.getInstance().getUser().getName();
    }

    public static UUID uuid() {
        return Minecraft.getInstance().getUser().getProfileId();
    }

    /**
     * Proves to a third party that this is the real account: the same "join server"
     * call the game makes when entering an online-mode server. The capes service then
     * asks Mojang whether this player joined {@code serverId}. The access token goes
     * only to Mojang. Blocks on the network: call from a background thread.
     */
    public static boolean join(String serverId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.services().sessionService().joinServer(mc.getUser().getProfileId(), mc.getUser().getAccessToken(), serverId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** UUIDs of the other players in the world (render thread). */
    public static List<UUID> otherPlayers() {
        Minecraft mc = Minecraft.getInstance();
        List<UUID> out = new ArrayList<>();
        if (mc.level == null) {
            return out;
        }
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p != mc.player) {
                out.add(p.getUUID());
            }
        }
        return out;
    }
}
