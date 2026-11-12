package dev.servergunnik.musicmod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Writes the mod's resource pack into resourcepacks/{@value #FOLDER}: the chosen
 * sky in the OptiFine sky format (drawn by the Skyboxify mod) and the chosen PvP
 * style textures (plain texture replacements, no other mod needed).
 */
public final class ModPack {

    private static final Logger LOG = LoggerFactory.getLogger("Musicmod");
    public static final String FOLDER = "musicmod_sky";
    /** Id of the pack in the game's resource pack list. */
    public static final String PACK_ID = "file/" + FOLDER;

    /** pack.mcmeta for game versions before 1.21.9 (single pack_format number). */
    private static final String MCMETA_LEGACY = """
            {
              "pack": {
                "description": "Musicmod: sky and PvP styles",
                "pack_format": 46,
                "supported_formats": {"min_inclusive": 34, "max_inclusive": 64}
              }
            }
            """;

    /** pack.mcmeta for 1.21.9 and newer (min_format / max_format). */
    private static final String MCMETA_MODERN = """
            {
              "pack": {
                "description": "Musicmod: sky and PvP styles",
                "min_format": 65,
                "max_format": 9999
              }
            }
            """;

    /** True for 1.21.0 - 1.21.8, which still use the old pack.mcmeta layout. */
    static boolean legacyMeta(String mcVersion) {
        if (mcVersion == null || !mcVersion.startsWith("1.")) {
            return false;
        }
        String[] parts = mcVersion.split("[.+-]");
        try {
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return minor < 21 || (minor == 21 && patch <= 8);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static final String PROPERTIES = """
            # Written by Musicmod. Change the sky in the Musicmod menu (Skies tab).
            source=./sky1.png
            blend=alpha
            rotate=false
            """;

    private ModPack() {
    }

    /**
     * Rewrites the pack from scratch. {@code styleFiles} maps a path inside
     * assets/minecraft (e.g. "textures/block/obsidian.png") to a file under
     * /assets/musicmod/styles in the jar. Returns true when the pack has content
     * and was written; false when there is nothing to show or writing failed.
     */
    public static boolean write(Path resourcePacksDir, Sky sky, Map<String, String> styleFiles, String mcVersion) {
        boolean hasSky = sky != null && sky.id != null;
        if (!hasSky && styleFiles.isEmpty()) {
            return false;
        }
        Path pack = resourcePacksDir.resolve(FOLDER);
        try {
            deleteTree(pack.resolve("assets"));
            Files.createDirectories(pack);
            Files.writeString(pack.resolve("pack.mcmeta"), legacyMeta(mcVersion) ? MCMETA_LEGACY : MCMETA_MODERN, StandardCharsets.UTF_8);
            if (hasSky) {
                Path skyDir = pack.resolve("assets/minecraft/optifine/sky/world0");
                Files.createDirectories(skyDir);
                Files.writeString(skyDir.resolve("sky1.properties"), PROPERTIES, StandardCharsets.UTF_8);
                copy("/assets/musicmod/skies/" + sky.id + ".png", skyDir.resolve("sky1.png"));
            }
            for (Map.Entry<String, String> e : styleFiles.entrySet()) {
                Path target = pack.resolve("assets/minecraft").resolve(e.getKey());
                Files.createDirectories(target.getParent());
                copy("/assets/musicmod/styles/" + e.getValue(), target);
            }
            return true;
        } catch (IOException e) {
            LOG.warn("Musicmod: cannot write the resource pack ({})", e.toString());
            return false;
        }
    }

    private static void copy(String resource, Path target) throws IOException {
        try (InputStream in = ModPack.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing from the jar: " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
