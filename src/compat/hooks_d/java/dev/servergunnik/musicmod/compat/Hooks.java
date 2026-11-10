package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.api.Draw;
import dev.servergunnik.musicmod.api.KeyHandle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/** HUD, key and tick registration for this Minecraft / Fabric API version. */
public final class Hooks {

    private Hooks() {
    }

    public static void registerHud(Consumer<Draw> renderer) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("musicmod", "overlay"),
                (g, tick) -> renderer.accept(GuiDraw.of(g)));
    }

    public static KeyHandle registerKey(String name, int key) {
        KeyMapping mapping = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(name, key, category()));
        return mapping::consumeClick;
    }

    private static KeyMapping.Category category;

    private static KeyMapping.Category category() {
        if (category == null) {
            category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("musicmod", "main"));
        }
        return category;
    }

    public static void registerTick(Runnable tick) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick.run());
    }
}
