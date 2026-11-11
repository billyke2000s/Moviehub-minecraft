package com.billyke2000s.moviehubtv.catalogue;

import com.billyke2000s.moviehubtv.network.CataloguePayload;
import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** TMDB catalogue and resolver identities. Only this server class handles the API credential. */
public final class TmdbGateway {
    private TmdbGateway() {}
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, Cached> CACHE = Collections.synchronizedMap(new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String, Cached> e) { return size() > 128; }
    });
    private record Cached(long at, String json) {}
    public static void clear() { CACHE.clear(); }

    static JsonObject get(String path, String key) throws Exception {
        Cached cached = CACHE.get(path);
        if (cached != null && System.currentTimeMillis() - cached.at < 3600000)
            return JsonParser.parseString(cached.json).getAsJsonObject();
        String url = "https://api.themoviedb.org/3/" + path + (path.contains("?") ? "&" : "?") + "language=en-GB";
        var request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(20));
        if (key.matches("[a-fA-F0-9]{32}")) url += "&api_key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        else request.header("Authorization", "Bearer " + key);
        var response = HTTP.send(request.uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var in = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("TMDB HTTP " + response.statusCode());
            byte[] bytes = in.readNBytes(1048577);
            if (bytes.length > 1048576) throw new IllegalStateException("TMDB response limit");
            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            CACHE.put(path, new Cached(System.currentTimeMillis(), json));
            return data;
        }
    }

    public static JsonObject catalogue(CataloguePayload.Request q, String key) throws Exception {
        int page = Math.max(1, Math.min(500, q.page()));
        JsonObject out = new JsonObject();
        JsonArray items = new JsonArray();
        int pages = 1;
        String route = q.route();
        if (route.equals("movies") || route.equals("shows") || route.equals("anime")) {
            List<String> types = route.equals("anime") ? List.of("tv", "movie")
                    : List.of(route.equals("movies") ? "movie" : "tv");
            for (String type : types) {
                String path;
                if (!q.query().isBlank()) path = "search/" + type + "?include_adult=false&query="
                        + URLEncoder.encode(q.query(), StandardCharsets.UTF_8);
                else if (route.equals("anime")) path = "discover/" + type
                        + "?include_adult=false&with_genres=16&with_original_language=ja&sort_by=popularity.desc";
                else path = type + "/popular?include_adult=false";
                JsonObject data = get(path + "&page=" + page, key);
                pages = Math.max(pages, integer(data, "total_pages", 1));
                for (JsonElement row : array(data, "results")) {
                    if (!row.isJsonObject()) continue;
                    JsonObject item = row.getAsJsonObject();
                    if (item.has("adult") && item.get("adult").getAsBoolean()) continue;
                    if (route.equals("anime") && !q.query().isBlank()
                            && (!text(item, "original_language").equals("ja") || !array(item, "genre_ids").contains(new JsonPrimitive(16)))) continue;
                    items.add(item(item, type));
                }
            }
        } else if (route.equals("movie") || route.equals("tv")) {
            items.add(item(get(route + "/" + positive(q.id()), key), route));
        } else if (route.equals("seasons")) {
            JsonObject data = get("tv/" + positive(q.id()), key);
            for (JsonElement row : array(data, "seasons")) {
                JsonObject season = row.getAsJsonObject();
                JsonObject item = item(season, "season");
                item.addProperty("id", q.id());
                item.addProperty("season", integer(season, "season_number", 0));
                item.addProperty("subtitle", integer(season, "episode_count", 0) + " episodes");
                if (text(item, "poster").isEmpty()) item.addProperty("poster", text(data, "poster_path"));
                items.add(item);
            }
        } else if (route.equals("episodes")) {
            if (q.season() < 0 || q.season() > 999) throw new IllegalArgumentException();
            JsonObject data = get("tv/" + positive(q.id()) + "/season/" + q.season(), key);
            for (JsonElement row : array(data, "episodes")) {
                JsonObject ep = row.getAsJsonObject();
                JsonObject item = item(ep, "episode");
                item.addProperty("id", q.id());
                item.addProperty("season", q.season());
                item.addProperty("episode", integer(ep, "episode_number", 1));
                item.addProperty("subtitle", "S" + q.season() + " E" + integer(ep, "episode_number", 1));
                items.add(item);
            }
        } else throw new IllegalArgumentException("Unknown catalogue route");
        out.add("items", items);
        out.addProperty("page", page);
        out.addProperty("pages", Math.min(500, pages));
        return out;
    }

    static JsonObject identity(String type, int id, int season, int episode, String key) throws Exception {
        if (!type.equals("movie") && !type.equals("tv")) throw new IllegalArgumentException();
        JsonObject data = get(type + "/" + positive(id) + "?append_to_response=external_ids", key);
        JsonObject info = new JsonObject();
        info.addProperty("title", text(data, type.equals("movie") ? "title" : "name"));
        info.addProperty("localTitle", text(data, type.equals("movie") ? "original_title" : "original_name"));
        String date = text(data, type.equals("movie") ? "release_date" : "first_air_date");
        info.addProperty("year", date.length() >= 4 ? date.substring(0, 4) : "");
        info.addProperty("imdb", data.has("external_ids") ? text(data.getAsJsonObject("external_ids"), "imdb_id") : text(data, "imdb_id"));
        info.addProperty("tmdb", id);
        info.addProperty("season", type.equals("movie") ? -1 : season);
        info.addProperty("episode", type.equals("movie") ? -1 : episode);
        info.addProperty("http", false);
        info.addProperty("maxQuality", 2160);
        info.addProperty("runtimeSeconds", integer(data, "runtime", 0) * 60);
        info.addProperty("poster", text(data, "poster_path"));
        if (type.equals("tv")) {
            if (season < 0 || season > 999 || episode < 1 || episode > 10000) throw new IllegalArgumentException();
            JsonObject ep = get("tv/" + id + "/season/" + season + "/episode/" + episode, key);
            info.addProperty("episodeTitle", text(ep, "name"));
            info.addProperty("premiered", text(ep, "air_date"));
            info.addProperty("runtimeSeconds", integer(ep, "runtime", 0) * 60);
        }
        return info;
    }

    public static JsonObject item(JsonObject data, String kind) {
        JsonObject item = new JsonObject();
        item.addProperty("id", integer(data, "id", 0));
        item.addProperty("kind", kind);
        String title = text(data, "title");
        if (title.isEmpty()) title = text(data, "name");
        item.addProperty("title", title);
        String poster = text(data, "poster_path");
        if (poster.isEmpty()) poster = text(data, "still_path");
        item.addProperty("poster", poster);
        item.addProperty("overview", cut(text(data, "overview"), 350));
        String date = text(data, kind.equals("movie") ? "release_date" : "first_air_date");
        item.addProperty("subtitle", (date.length() >= 4 ? date.substring(0, 4) + " - " : "") + (kind.equals("tv") ? "Show" : "Movie"));
        return item;
    }
    static int positive(int n) { if (n < 1) throw new IllegalArgumentException(); return n; }
    public static String text(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : ""; }
    public static int integer(JsonObject o, String k, int d) { try { return o.has(k) ? o.get(k).getAsInt() : d; } catch (Exception e) { return d; } }
    public static JsonArray array(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }
    private static String cut(String s, int max) { return s.substring(0, Math.min(max, s.length())); }
}
