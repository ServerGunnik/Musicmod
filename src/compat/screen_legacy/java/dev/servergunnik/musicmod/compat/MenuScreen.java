package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.ui.MenuUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Screen wrapper around {@link MenuUi} (1.21.4 - 1.21.8 input API). */
final class MenuScreen extends Screen {

    private final MenuUi ui;

    MenuScreen(MenuUi ui) {
        super(Component.literal("Musicmod"));
        this.ui = ui;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ui.render(GuiDraw.of(g), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // MenuUi dims the screen itself.
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return ui.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return ui.mouseDragged(x, y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return ui.mouseReleased() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return ui.mouseScrolled(vertical);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ui.onClose();
        super.removed();
    }
}
