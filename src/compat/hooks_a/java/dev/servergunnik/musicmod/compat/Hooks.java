package dev.servergunnik.musicmod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.api.KeyHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.function.Consumer;

/** HUD, key and tick registration for this Minecraft / Fabric API version. */
public final class Hooks {

    private Hooks() {
    }

    public static void registerHud(Consumer<Draw> renderer) {
        HudRenderCallback.EVENT.register((g, tick) -> renderer.accept(GuiDraw.of(g)));
    }

    public static KeyHandle registerKey(String name, int key) {
        KeyMapping mapping = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(name, InputConstants.Type.KEYSYM, key, "key.categories.musicmod"));
        return mapping::consumeClick;
    }

    public static void registerTick(Runnable tick) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick.run());
    }
}
