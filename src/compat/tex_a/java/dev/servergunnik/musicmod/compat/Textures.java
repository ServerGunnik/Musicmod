package dev.servergunnik.musicmod.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Textures loaded from PNG files at runtime (album cover, user capes). Render thread only. */
public final class Textures {

    private Textures() {
    }

    /**
     * Registers the PNG as musicmod:&lt;path&gt; (replacing an earlier texture with that
     * name), so {@code Draw.image(path, ...)} and capes can use it.
     */
    public static boolean load(String path, Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            Minecraft.getInstance().getTextureManager().register(Fonts.id(path), new DynamicTexture(image));
            return true;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("Musicmod").warn("Musicmod: cannot load texture {} ({})", file, e.toString());
            return false;
        }
    }
}
