package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.ui.MenuUi;
import net.minecraft.client.Minecraft;

public final class Screens {

    private Screens() {
    }

    public static void open(MenuUi ui) {
        Minecraft.getInstance().setScreen(new MenuScreen(ui));
    }

    public static boolean isMenuOpen() {
        return GameState.currentScreen() instanceof MenuScreen;
    }
}
