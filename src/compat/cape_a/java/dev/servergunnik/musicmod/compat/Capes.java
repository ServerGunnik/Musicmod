package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.MusicmodClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Puts Musicmod capes on players: the one chosen in the menu on the local player,
 * and on other players the cape the MusicmodCapes server plugin reported for them. The cape texture is a normal mod asset
 * (assets/musicmod/textures/cape/&lt;id&gt;.png).
 */
public final class Capes {

    private record Cached(String cape, PlayerSkin out) {
    }

    /** Skin with cape, per original skin object (skins are long-lived, one per player). */
    private static final java.util.Map<PlayerSkin, Cached> CACHE = new java.util.IdentityHashMap<>();

    private Capes() {
    }

    /** Returns the skin with a Musicmod cape, or null to leave the skin alone. */
    public static PlayerSkin apply(Object player, PlayerSkin skin) {
        if (skin == null) {
            return null;
        }
        // Our own cape comes from the menu; other players' capes from the server plugin.
        String cape = player == Minecraft.getInstance().player
                ? MusicmodClient.capeTexture()
                : player instanceof Entity e ? MusicmodClient.capeTextureOf(e.getUUID()) : null;
        if (cape == null) {
            return null;
        }
        Cached hit = CACHE.get(skin);
        if (hit != null && hit.cape().equals(cape)) {
            return hit.out();
        }
        if (CACHE.size() > 512) {
            CACHE.clear();
        }
        var texture = Fonts.id(cape);
        PlayerSkin out = new PlayerSkin(skin.texture(), skin.textureUrl(), texture, skin.elytraTexture(), skin.model(), skin.secure());
        CACHE.put(skin, new Cached(cape, out));
        return out;
    }
}
