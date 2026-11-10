package dev.servergunnik.musicmod;

import dev.servergunnik.musicmod.api.KeyHandle;
import dev.servergunnik.musicmod.compat.GameState;
import dev.servergunnik.musicmod.compat.Hooks;
import dev.servergunnik.musicmod.compat.Screens;
import dev.servergunnik.musicmod.core.Config;
import dev.servergunnik.musicmod.core.ConfigStore;
import dev.servergunnik.musicmod.core.LyricsProvider;
import dev.servergunnik.musicmod.core.LyricsTracker;
import dev.servergunnik.musicmod.core.MediaSource;
import dev.servergunnik.musicmod.hud.HudOverlay;
import dev.servergunnik.musicmod.sky.SkyLyrics;
import dev.servergunnik.musicmod.ui.MenuUi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class MusicmodClient implements ClientModInitializer {

    public static final String MOD_ID = "musicmod";
    private static final int KEY_M = 77;
    private static final int KEY_NONE = -1;

    private static String version = "dev";
    private static ConfigStore store;
    private static Config config;
    private static LyricsTracker tracker;
    private static HudOverlay hud;
    private static SkyLyrics sky;
    private static KeyHandle menuKey;
    private static KeyHandle toggleKey;

    @Override
    public void onInitializeClient() {
        version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        store = new ConfigStore(dir.resolve("config.json"));
        config = store.load();

        MediaSource media = new MediaSource(dir.resolve("bridge"));
        media.start();
        LyricsProvider provider = new LyricsProvider(dir.resolve("lyrics-cache"),
                "Musicmod/" + version + " (https://github.com/ServerGunnik/Musicmod)");
        tracker = new LyricsTracker(media, provider, MusicmodClient::config);

        hud = new HudOverlay();
        sky = new SkyLyrics();
        Hooks.registerHud(hud::render);
        menuKey = Hooks.registerKey("key.musicmod.menu", KEY_M);
        toggleKey = Hooks.registerKey("key.musicmod.toggle", KEY_NONE);
        Hooks.registerTick(MusicmodClient::tick);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            media.stop();
            provider.shutdown();
        }, "Musicmod-Shutdown"));
    }

    private static void tick() {
        while (menuKey.consumeClick()) {
            if (!Screens.isMenuOpen()) {
                Screens.open(new MenuUi());
            }
        }
        while (toggleKey.consumeClick()) {
            config.lyricsEnabled = !config.lyricsEnabled;
            saveConfig();
        }
        hud.tick();
        sky.tick();
    }

    public static Config config() {
        return config;
    }

    public static LyricsTracker tracker() {
        return tracker;
    }

    public static String version() {
        return version;
    }

    public static void saveConfig() {
        store.save(config);
    }

    public static void resetConfig() {
        config = new Config();
        saveConfig();
    }

    /** Translated string (falls back to the key). */
    public static String tr(String key, Object... args) {
        return GameState.tr(key, args);
    }
}
