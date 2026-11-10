package dev.servergunnik.musicmod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes the chosen sky as a resource pack in the OptiFine sky format
 * (assets/minecraft/optifine/sky/world0/sky1.properties + texture). A sky mod
 * that reads this format (Skyboxify) then draws it. The pack lives in
 * resourcepacks/{@value #FOLDER}.
 */
public final class SkyPack {

    private static final Logger LOG = LoggerFactory.getLogger("Musicmod");
    public static final String FOLDER = "musicmod_sky";
    /** Id of the pack in the game's resource pack list. */
    public static final String PACK_ID = "file/" + FOLDER;

    /** pack.mcmeta for game versions before 1.21.9 (single pack_format number). */
    private static final String MCMETA_LEGACY = """
            {
              "pack": {
                "description": "Musicmod sky (needs the Skyboxify mod)",
                "pack_format": 46,
                "supported_formats": {"min_inclusive": 34, "max_inclusive": 64}
              }
            }
            """;

    /** pack.mcmeta for 1.21.9 and newer (min_format / max_format). */
    private static final String MCMETA_MODERN = """
            {
              "pack": {
                "description": "Musicmod sky (needs the Skyboxify mod)",
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

    private SkyPack() {
    }

    /** Writes the pack for {@code sky}. Returns false when it could not be written. */
    public static boolean write(Path resourcePacksDir, Sky sky, String mcVersion) {
        if (sky == null || sky.id == null) {
            return false;
        }
        Path pack = resourcePacksDir.resolve(FOLDER);
        Path skyDir = pack.resolve("assets/minecraft/optifine/sky/world0");
        try (InputStream in = SkyPack.class.getResourceAsStream("/assets/musicmod/skies/" + sky.id + ".png")) {
            if (in == null) {
                LOG.warn("Musicmod: sky texture {} is missing from the jar", sky.id);
                return false;
            }
            Files.createDirectories(skyDir);
            Files.writeString(pack.resolve("pack.mcmeta"), legacyMeta(mcVersion) ? MCMETA_LEGACY : MCMETA_MODERN, StandardCharsets.UTF_8);
            Files.writeString(skyDir.resolve("sky1.properties"), PROPERTIES, StandardCharsets.UTF_8);
            Files.copy(in, skyDir.resolve("sky1.png"), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.warn("Musicmod: cannot write the sky pack ({})", e.toString());
            return false;
        }
    }
}
