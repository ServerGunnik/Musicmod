package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.MusicmodClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Puts the cape chosen in the menu on the local player. Client-side only: other
 * players do not see it. The cape texture is a normal mod asset
 * (assets/musicmod/textures/cape/&lt;id&gt;.png).
 */
public final class Capes {

    private static String cachedCape;
    private static PlayerSkin cachedFrom;
    private static PlayerSkin cachedOut;

    private Capes() {
    }

    /** Returns the skin with our cape, or null to leave the skin alone. */
    public static PlayerSkin apply(Object player, PlayerSkin skin) {
        String cape = MusicmodClient.capeTexture();
        if (cape == null || skin == null || player != Minecraft.getInstance().player) {
            return null;
        }
        if (skin == cachedFrom && cape.equals(cachedCape)) {
            return cachedOut;
        }
        var texture = Fonts.id(cape);
        cachedOut = new PlayerSkin(skin.texture(), skin.textureUrl(), texture, skin.elytraTexture(), skin.model(), skin.secure());
        cachedFrom = skin;
        cachedCape = cape;
        return cachedOut;
    }
}
