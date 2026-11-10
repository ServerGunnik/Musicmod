package dev.servergunnik.musicmod.compat;

import dev.servergunnik.musicmod.api.Span;
import dev.servergunnik.musicmod.api.WorldText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Vector3f;

import java.util.List;

/**
 * Client-only text display entities (never sent to the server). Negative ids keep
 * them clear of real entities.
 */
public final class TextEntities {

    private static final int FULL_BRIGHT = (15 << 4) | (15 << 20);
    private static final byte FLAG_SHADOW = 0x01;
    private static int nextId = -2_000_000;

    private TextEntities() {
    }

    public static WorldText spawn() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return null;
        }
        Display.TextDisplay e = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        e.setId(nextId--);
        SynchedEntityData d = e.getEntityData();
        d.set(Display.DATA_BILLBOARD_RENDER_CONSTRAINTS_ID, Display.BillboardConstraints.CENTER.getId());
        d.set(Display.DATA_BRIGHTNESS_OVERRIDE_ID, FULL_BRIGHT);
        d.set(Display.DATA_VIEW_RANGE_ID, 4.0f);
        d.set(Display.TextDisplay.DATA_LINE_WIDTH_ID, 4000);
        d.set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        d.set(Display.TextDisplay.DATA_STYLE_FLAGS_ID, FLAG_SHADOW);
        e.setPos(mc.player.getX(), mc.player.getEyeY() + 8, mc.player.getZ());
        e.setOldPosAndRot();
        level.addEntity(e);
        return new Handle(e, level);
    }

    private static final class Handle implements WorldText {
        private final Display.TextDisplay e;
        private final ClientLevel level;

        Handle(Display.TextDisplay e, ClientLevel level) {
            this.e = e;
            this.level = level;
        }

        @Override
        public void setText(List<Span> spans) {
            MutableComponent c = Component.empty();
            for (Span s : spans) {
                c.append(Component.literal(s.text()).withStyle(style -> style.withColor(s.rgb())));
            }
            e.getEntityData().set(Display.TextDisplay.DATA_TEXT_ID, c);
        }

        @Override
        public void setPos(double x, double y, double z) {
            e.setOldPosAndRot();
            e.setPos(x, y, z);
        }

        @Override
        public void setScale(float scale) {
            Vector3f v = e.getEntityData().get(Display.DATA_SCALE_ID);
            if (v.x() != scale) {
                e.getEntityData().set(Display.DATA_SCALE_ID, new Vector3f(scale, scale, scale));
            }
        }

        @Override
        public void setOpacity(int opacity) {
            byte b = (byte) Math.max(26, Math.min(255, opacity));
            if (e.getEntityData().get(Display.TextDisplay.DATA_TEXT_OPACITY_ID) != b) {
                e.getEntityData().set(Display.TextDisplay.DATA_TEXT_OPACITY_ID, b);
            }
        }

        @Override
        public void setBackground(int argb) {
            if (e.getEntityData().get(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID) != argb) {
                e.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, argb);
            }
        }

        @Override
        public boolean alive() {
            return !e.isRemoved() && Minecraft.getInstance().level == level;
        }

        @Override
        public void remove() {
            if (!e.isRemoved()) {
                level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
    }
}
