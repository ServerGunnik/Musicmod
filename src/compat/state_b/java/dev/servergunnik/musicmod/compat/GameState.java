package dev.servergunnik.musicmod.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;

/** Read-only game state the HUD and sky lyrics need. */
public final class GameState {

    private GameState() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static boolean inWorld() {
        return mc().player != null && mc().level != null;
    }

    public static boolean hudHidden() {
        // 26.2+: the HUD is not drawn at all while the GUI is hidden (F1).
        return false;
    }

    public static Object currentScreen() {
        return mc().gui.screen();
    }

    public static void openScreen(Screen screen) {
        mc().gui.setScreen(screen);
    }

    public static Object levelToken() {
        return mc().level;
    }

    public static int fps() {
        return mc().getFps();
    }

    /** Ping in ms, or -1 in singleplayer / unknown. */
    public static int ping() {
        ClientPacketListener conn = mc().getConnection();
        Player p = mc().player;
        if (conn == null || p == null || mc().isLocalServer()) {
            return -1;
        }
        PlayerInfo info = conn.getPlayerInfo(p.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    public static double x() {
        return mc().player.getX();
    }

    public static double y() {
        return mc().player.getY();
    }

    public static double z() {
        return mc().player.getZ();
    }

    public static double eyeY() {
        return mc().player.getEyeY();
    }

    public static float yaw() {
        return mc().player.getYRot();
    }

    public static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }
}
