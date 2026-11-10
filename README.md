# Musicmod 🎵

Client-side Fabric mod: the lyrics of the song you are playing (Spotify, YouTube in the browser, …) appear
**word by word in the sky** and in a **top bar**, plus a clean HUD (now playing, clock, FPS, ping,
coordinates, speed). Menu: **M** (rebindable in Controls).

- Music is read from **Windows Media Controls** (no Spotify login, no API key). Windows only for now.
- Lyrics come from **[LRCLIB](https://lrclib.net)** (free) and are cached in `config/musicmod/lyrics-cache`.
- Minecraft **1.21.4 – 1.21.11, 26.1, 26.2, 26.3** (one jar per version).

## Build

```
./gradlew :1.21.11:dist     # one version  -> build/dist/1.21.11.jar
./gradlew buildAll          # every version -> build/dist/<mc>.jar
```

CI builds all versions on every push and puts the jars on the `builds` branch
(`versions/<mc>.jar`, ready for the Discord bot's `data/mods/musicmod/versions/`).

## Layout

- `src/main/java` – shared code (lyrics, sync, HUD, sky lyrics, menu). No Minecraft classes except through `compat`.
- `src/compat/<layer>/java` – the small parts that differ between Minecraft versions
  (`GuiDraw`, `MenuScreen`, `Hooks`, `TextEntities`, `GameState`). `versions/<mc>/gradle.properties`
  lists which layers each version uses.
