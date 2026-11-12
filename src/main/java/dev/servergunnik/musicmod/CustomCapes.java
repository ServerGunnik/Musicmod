package dev.servergunnik.musicmod;

import dev.servergunnik.musicmod.compat.Textures;
import dev.servergunnik.musicmod.core.Images;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Capes the player adds as pictures in config/musicmod/capes. Each picture is
 * converted once into a cape texture + menu icon and loaded as a runtime texture.
 */
public final class CustomCapes {

    /** Prefix of the cape id stored in the config for user capes. */
    public static final String PREFIX = "custom:";

    /** {@code net}: small copy of the texture that is sent to other players (null if it could not be made). */
    public record Cape(String id, String label, String texture, String icon, Path net) {
    }

    private static Path folder;
    private static Path cache;
    private static List<Cape> capes = List.of();

    private CustomCapes() {
    }

    static void init(Path configDir) {
        folder = configDir.resolve("capes");
        cache = configDir.resolve("cache").resolve("capes");
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
        }
    }

    public static Path folder() {
        return folder;
    }

    public static List<Cape> all() {
        return capes;
    }

    /** Texture path of a user cape id, or null when it is not loaded. */
    public static String texture(String id) {
        for (Cape c : capes) {
            if (c.id().equals(id)) {
                return c.texture();
            }
        }
        return null;
    }

    /** The small network copy of a user cape, or null. */
    public static Path networkFile(String id) {
        for (Cape c : capes) {
            if (c.id().equals(id)) {
                return c.net();
            }
        }
        return null;
    }

    /** Folder for pictures received from other players. */
    static Path remoteCache() {
        return cache.resolveSibling("remote");
    }

    /** Reads the folder again and (re)loads every picture. Call on the render thread. */
    public static void reload() {
        List<Cape> out = new ArrayList<>();
        if (folder == null) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            Files.createDirectories(cache);
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
                    continue;
                }
                String label = name.substring(0, name.lastIndexOf('.'));
                String key = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                if (key.isEmpty() || out.size() >= 24) {
                    continue;
                }
                Path tex = cache.resolve(key + ".png");
                Path icon = cache.resolve(key + "_icon.png");
                if (!Images.cape(file, tex, icon)) {
                    continue;
                }
                String texPath = "dynamic/cape_" + key;
                String iconPath = "dynamic/capeicon_" + key;
                if (Textures.load(texPath, tex) && Textures.load(iconPath, icon)) {
                    Path net = cache.resolve(key + "_net.png");
                    out.add(new Cape(PREFIX + name, label, texPath, iconPath,
                            Images.capeForNetwork(tex, net, 512) ? net : null));
                }
            }
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("Musicmod").warn("Musicmod: cannot read user capes ({})", e.toString());
        }
        capes = out;
    }
}
