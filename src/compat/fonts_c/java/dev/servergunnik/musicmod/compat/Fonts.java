package dev.servergunnik.musicmod.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.FontDescription;

/** Applies one of the mod's bundled fonts (assets/musicmod/font/&lt;name&gt;.json) to text. */
public final class Fonts {

    private Fonts() {
    }

    /** {@code font} is the file name without extension; null or "default" keeps Minecraft's font. */
    public static Style apply(Style style, String font) {
        if (font == null || font.isEmpty() || font.equals("default")) {
            return style;
        }
        Identifier id = Identifier.fromNamespaceAndPath("musicmod", font);
        return style.withFont(new FontDescription.Resource(id));
    }

    public static MutableComponent text(String text, String font) {
        return Component.literal(text).withStyle(s -> apply(s, font));
    }
}
