package com.billyke2000s.moviehubtv.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** The server's playable sources for an on-demand title: a JSON array, chosen source first. */
final class MediaSources {
    private MediaSources() {}

    /** Accepts the array, or a single source object. Anything unreadable gives an empty list. */
    static List<JsonObject> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject()) return List.of(root.getAsJsonObject());
            if (!root.isJsonArray()) return List.of();
            List<JsonObject> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray()) if (e.isJsonObject()) out.add(e.getAsJsonObject());
            return List.copyOf(out);
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
