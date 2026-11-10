package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.ui.MenuUi;

public final class Screens {

    private Screens() {
    }

    public static void open(MenuUi ui) {
        GameState.openScreen(new MenuScreen(ui));
    }

    public static boolean isMenuOpen() {
        return GameState.currentScreen() instanceof MenuScreen;
    }
}
