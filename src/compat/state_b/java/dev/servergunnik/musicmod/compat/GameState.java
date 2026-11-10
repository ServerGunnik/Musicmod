package dev.servergunnik.musicmod.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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

    public static float pitch() {
        return mc().player.getXRot();
    }

    /**
     * What the player looks at within {@code max} blocks:
     * {x, y, z, normalX, normalY, normalZ, hitBlock(1/0)}. Without a block hit the
     * point is {@code max} blocks ahead and the normal is zero.
     */
    public static double[] lookTarget(double max) {
        HitResult hit = mc().player.pick(max, 1.0f, false);
        Vec3 p = hit.getLocation();
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult b) {
            Direction d = b.getDirection();
            return new double[]{p.x(), p.y(), p.z(), d.getStepX(), d.getStepY(), d.getStepZ(), 1};
        }
        return new double[]{p.x(), p.y(), p.z(), 0, 0, 0, 0};
    }

    /** Width of the text in font pixels (one pixel is 1/40 block in a text display at scale 1). */
    public static int textWidth(String text, String font) {
        return mc().font.width(Fonts.text(text, font));
    }

    /** True when no block is in the way between the two points. */
    public static boolean clear(double x1, double y1, double z1, double x2, double y2, double z2) {
        HitResult hit = mc().level.clip(new ClipContext(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2),
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc().player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** Enables or disables a resource pack by id and reloads resources when something changed. */
    public static void applySkyPack(String packId, boolean enabled) {
        PackRepository repo = mc().getResourcePackRepository();
        repo.reload();
        boolean was = repo.getSelectedIds().contains(packId);
        if (enabled) {
            repo.addPack(packId);
        } else {
            repo.removePack(packId);
        }
        boolean now = repo.getSelectedIds().contains(packId);
        if (was != now) {
            mc().options.updateResourcePacks(repo); // saves the list and reloads
        } else if (now) {
            mc().reloadResourcePacks();             // same pack, new texture
        }
    }

    public static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }
}
