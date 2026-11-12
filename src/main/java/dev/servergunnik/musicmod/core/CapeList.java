package dev.servergunnik.musicmod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The bundled capes, from assets/musicmod/textures/cape/index.json (written by tools/gen_capes.py). */
public final class CapeList {

    public record Cape(String id, String label) {
    }

    private static List<Cape> capes;

    private CapeList() {
    }

    public static synchronized List<Cape> all() {
        if (capes == null) {
            List<Cape> out = new ArrayList<>();
            try (InputStream in = CapeList.class.getResourceAsStream("/assets/musicmod/textures/cape/index.json")) {
                if (in != null) {
                    JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                    for (JsonElement e : arr) {
                        out.add(new Cape(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject().get("label").getAsString()));
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("Musicmod").warn("Musicmod: cannot read the cape list", e);
            }
            capes = out;
        }
        return capes;
    }
}
