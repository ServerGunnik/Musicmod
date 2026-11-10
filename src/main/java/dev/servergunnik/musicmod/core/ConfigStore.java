package dev.servergunnik.musicmod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigStore {

    private static final Logger LOG = LoggerFactory.getLogger("Musicmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public ConfigStore(Path file) {
        this.file = file;
    }

    public Config load() {
        if (Files.exists(file)) {
            try {
                Config c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Config.class);
                if (c != null) {
                    return c.sanitize();
                }
            } catch (Exception e) {
                LOG.warn("Musicmod: config is broken, using defaults ({})", e.toString());
            }
        }
        return new Config();
    }

    public void save(Config config) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(config.sanitize()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Musicmod: cannot save config ({})", e.toString());
        }
    }
}
