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
import dev.servergunnik.musicmod.core.Sky;
import dev.servergunnik.musicmod.core.ModPack;
import dev.servergunnik.musicmod.core.Styles;
import dev.servergunnik.musicmod.hud.HudOverlay;
import dev.servergunnik.musicmod.sky.SkyLyrics;
import dev.servergunnik.musicmod.ui.MenuUi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class MusicmodClient implements ClientModInitializer {

    public static final String MOD_ID = "musicmod";

    private static String version = "dev";
    private static ConfigStore store;
    private static Config config;
    private static LyricsTracker tracker;
    private static HudOverlay hud;
    private static SkyLyrics sky;
    private static KeyHandle menuKey;
    private static KeyHandle toggleKey;
    private static boolean capesLoaded;

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

        CustomCapes.init(dir);
        hud = new HudOverlay();
        sky = new SkyLyrics();
        Hooks.registerHud(hud::render);
        menuKey = Hooks.registerKey("key.musicmod.open_menu", true);
        toggleKey = Hooks.registerKey("key.musicmod.toggle_lyrics", false);
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
        if (!capesLoaded && GameState.inWorld()) {
            // User capes are runtime textures: load them once the game can draw.
            capesLoaded = true;
            CustomCapes.reload();
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

    private static String skyStatus = "";

    /**
     * Writes the sky resource pack for the configured sky and switches it on or off in
     * the game. The outcome ("on", "off", "missing", "write_failed", "error") is kept
     * for the menu, see {@link #skyStatus()}.
     */
    public static void applySky() {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("Musicmod");
        Sky sky = config.sky;
        String mc = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        java.util.Map<String, String> styleFiles = Styles.files(config.styles);
        boolean want = sky.id != null || !styleFiles.isEmpty();
        boolean written = want && ModPack.write(FabricLoader.getInstance().getGameDir().resolve("resourcepacks"), sky, config.skyAnimated, styleFiles, mc);
        try {
            skyStatus = want && !written ? "write_failed" : GameState.applySkyPack(ModPack.PACK_ID, written);
        } catch (RuntimeException e) {
            skyStatus = "error";
            log.warn("Musicmod: cannot switch the sky pack", e);
        }
        log.info("Musicmod pack: sky {}, styles {} -> {} (Minecraft {}, Skyboxify {})", sky, config.styles, skyStatus, mc,
                skyRendererPresent() ? "installed" : "NOT installed");
    }

    /** Result of the last {@link #applySky()} call, or "" when it has not run. */
    public static String skyStatus() {
        return skyStatus;
    }

    /** Texture (path in the mod's namespace) of the cape chosen in the menu, or null for none. */
    public static String capeTexture() {
        Config c = config;
        if (c == null || c.cape == null || c.cape.isEmpty()) {
            return null;
        }
        if (c.cape.startsWith(CustomCapes.PREFIX)) {
            return CustomCapes.texture(c.cape);
        }
        return "textures/cape/" + c.cape + ".png";
    }

    public static boolean skyRendererPresent() {
        return FabricLoader.getInstance().isModLoaded("skyboxify");
    }

    /** Translated string (falls back to the key). */
    public static String tr(String key, Object... args) {
        return GameState.tr(key, args);
    }
}
