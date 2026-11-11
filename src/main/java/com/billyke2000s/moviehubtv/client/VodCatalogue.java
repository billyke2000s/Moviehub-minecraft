package com.billyke2000s.moviehubtv.client;

import com.google.gson.*;
import java.util.*;

/** Pure parser for Pluto's regional, free on-demand catalogue. No paid provider accounts. */
final class VodCatalogue {
    private VodCatalogue() {}
    record Item(String id, String title, boolean series, boolean anime, int season, int episode, String summary) {
        String playbackId() {
            String label = title.replace('|', ' ');
            // The remote packet has a 256-character limit.
            return "vod:pluto:" + id + "|" + label.substring(0, Math.min(100, label.length()));
        }
        String subtitle() { return season >= 0 ? "S" + season + " E" + episode : series ? "Show - Pluto TV" : "Movie - Pluto TV"; }
    }
    static List<Item> catalogue(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Item> out = new LinkedHashMap<>();
        for (JsonElement e : array(root, "categories")) {
            if (!e.isJsonObject()) continue;
            JsonObject cat = e.getAsJsonObject();
            if (bool(cat, "plutoOfficeOnly")) continue;
            boolean anime = anime(text(cat, "name"));
            for (JsonElement row : array(cat, "items")) {
                if (!row.isJsonObject()) continue;
                JsonObject item = row.getAsJsonObject();
                String id = text(item, "_id");
                if (id.isEmpty()) id = text(item, "id");
                String name = text(item, "name");
                String type = text(item, "type");
                if (!validId(id) || name.isBlank() || bool(item, "plutoOfficeOnly")
                        || (!type.equals("movie") && !type.equals("series"))) continue;
                boolean isAnime = anime || anime(text(item, "genre"));
                Item prior = out.get(id);
                if (prior != null) isAnime |= prior.anime;
                String summary = text(item, "summary");
                if (summary.isEmpty()) summary = text(item, "description");
                out.put(id, new Item(id, name, type.equals("series"), isAnime, -1, -1, summary));
            }
        }
        return List.copyOf(out.values());
    }
    static List<Item> episodes(String json, Item series) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray seasons = root.isJsonArray() ? root.getAsJsonArray() : array(root.getAsJsonObject(), "seasons");
        Map<String, Item> out = new LinkedHashMap<>();
        for (JsonElement e : seasons) {
            if (!e.isJsonObject()) continue;
            JsonObject s = e.getAsJsonObject();
            int season = number(s, "number", number(s, "seasonNumber", 0));
            for (JsonElement row : array(s, "episodes")) {
                if (!row.isJsonObject()) continue;
                JsonObject ep = row.getAsJsonObject();
                String id = text(ep, "_id");
                if (id.isEmpty()) id = text(ep, "id");
                if (!validId(id) || bool(ep, "plutoOfficeOnly")) continue;
                int n = number(ep, "number", number(ep, "episodeNumber", 0));
                String name = text(ep, "name");
                out.put(id, new Item(id, series.title + " - " + name, false, series.anime, season, n, text(ep, "description")));
            }
        }
        return out.values().stream().sorted(Comparator.comparingInt(Item::season).thenComparingInt(Item::episode)).toList();
    }
    static boolean validId(String s) { return s != null && s.matches("[0-9a-f]{24}"); }
    static boolean anime(String s) { return s.toLowerCase(Locale.ROOT).matches(".*\\banime\\b.*"); }
    static String normal(String s) { return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    static JsonArray array(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }
    static String text(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : ""; }
    private static boolean bool(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean(); }
    private static int number(JsonObject o, String k, int fallback) { try { return o.has(k) ? o.get(k).getAsInt() : fallback; } catch (Exception e) { return fallback; } }
}
