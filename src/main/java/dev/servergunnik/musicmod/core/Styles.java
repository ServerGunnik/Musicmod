package dev.servergunnik.musicmod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PvP styles: recolored textures for a few items/blocks, listed in
 * assets/musicmod/styles/index.json (written by tools/gen_styles.py).
 */
public final class Styles {

    /** The things that can be restyled, in menu order. */
    public static final String[] KINDS = {"totem", "crystal", "obsidian", "anchor"};

    public record Style(String id, String label, int color, Map<String, String> files) {
    }

    private static Map<String, List<Style>> index;

    private Styles() {
    }

    /** Styles of one kind (empty when the index cannot be read). */
    public static synchronized List<Style> of(String kind) {
        if (index == null) {
            index = load();
        }
        return index.getOrDefault(kind, List.of());
    }

    /** Files to put in the resource pack for the selection (kind -> style id, "" = vanilla). */
    public static Map<String, String> files(Map<String, String> selection) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String kind : KINDS) {
            String chosen = selection.get(kind);
            if (chosen == null || chosen.isEmpty()) {
                continue;
            }
            for (Style s : of(kind)) {
                if (s.id().equals(chosen)) {
                    out.putAll(s.files());
                }
            }
        }
        return out;
    }

    private static Map<String, List<Style>> load() {
        Map<String, List<Style>> out = new LinkedHashMap<>();
        try (InputStream in = Styles.class.getResourceAsStream("/assets/musicmod/styles/index.json")) {
            if (in == null) {
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String kind : root.keySet()) {
                List<Style> list = new ArrayList<>();
                JsonObject styles = root.getAsJsonObject(kind);
                for (String id : styles.keySet()) {
                    JsonObject o = styles.getAsJsonObject(id);
                    Map<String, String> files = new LinkedHashMap<>();
                    JsonObject f = o.getAsJsonObject("files");
                    for (String target : f.keySet()) {
                        files.put(target, f.get(target).getAsString());
                    }
                    list.add(new Style(id, o.get("label").getAsString(),
                            Integer.parseInt(o.get("color").getAsString().substring(1), 16), files));
                }
                out.put(kind, list);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("Musicmod").warn("Musicmod: cannot read the styles index", e);
        }
        return out;
    }
}
